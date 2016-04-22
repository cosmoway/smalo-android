package net.cosmoway.smalo

import android.os.Bundle
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.altbeacon.beacon.*
import org.altbeacon.beacon.startup.BootstrapNotifier
import org.altbeacon.beacon.startup.RegionBootstrap
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

// BeaconServiceクラス
class MyBeaconService : Service(), BeaconConsumer, BootstrapNotifier, RangeNotifier,
        MonitorNotifier {

    // BGで監視するiBeacon領域
    private var mRegionBootstrap: RegionBootstrap? = null
    // iBeacon検知用のマネージャー
    private var mBeaconManager: BeaconManager? = null
    // UUID設定用
    private var mId: String? = null
    // iBeacon領域
    private var mRegion: Region? = null
    private var mHost: String? = null

    private var mState: String? = null
    // Nsd Manager
    private var mNsdManager: NsdManager? = null
    private var mIsDiscoveryStarted: Boolean = false
    // Flag of Unlock
    private var mIsUnlocked: Boolean = false
    // Wakelock
    private var mWakeLock: PowerManager.WakeLock? = null

    private var mHashValue: String? = null


    companion object {
        val TAG_BEACON = org.altbeacon.beacon.service.BeaconService::class.java.simpleName
        val TAG_NSD = "NSD"
        val SERVICE_TYPE = "_xdk-app-daemon._tcp."
        val MY_SERVICE_NAME = "smalo"
        //val MY_SERVICE_NAME = "smalo-dev"
        val MY_SERVICE_UUID = "51a4a738-62b8-4b26-a929-3bbac2a5ce7c"
        //val MY_SERVICE_UUID = "dddddddddddddddddddddddddddddddd"
        val MY_APP_NAPE = "ＳＭＡＬＯ"
    }

    private fun toEncryptedHashValue(algorithmName: String, value: String): String {
        var md: MessageDigest? = null
        try {
            md = MessageDigest.getInstance(algorithmName)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

        val sb: StringBuilder = StringBuilder()
        md!!.update(value.toByteArray())
        for (b in md.digest()) {
            val hex = String.format("%02x", b)
            sb.append(hex)
        }
        return sb.toString()
    }

    private fun getRequest(url: String) {
        object : AsyncTask<Void?, Void?, String?>() {
            override fun doInBackground(vararg params: Void?): String? {
                var result: String? = null
                // リクエストオブジェクトを作って
                val request: Request = Request.Builder().url(url).get().build()

                // クライアントオブジェクトを作って
                val client: OkHttpClient = OkHttpClient()

                // リクエストして結果を受け取って
                try {
                    val response = client.newCall(request).execute()
                    result = response.body().string()
                } catch (e: IOException) {
                    e.printStackTrace()
                    return "Connection Error"
                }
                // 返す
                Log.d(TAG_BEACON, result)
                return result
            }

            override fun onPostExecute(result: String?) {
                if (result != null) {
                    if (result == "locked" || result == "unlocked" || result == "unknown" || result == "200 OK") {
                        makeNotification(result)
                        if (result == "200 OK") {
                            getRequest("http:/$mHost:10080/api/locks/status/$mHashValue")
                            val uri: Uri = RingtoneManager
                                    .getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            val ringtone: Ringtone = RingtoneManager
                                    .getRingtone(applicationContext, uri)
                            ringtone.play()
                        } else {
                            sendBroadCastToMainActivity(result)
                        }
                    } else {
                        makeNotification(result)
                    }
                }
            }
        }.execute()
    }

    private fun makeNotification(title: String) {

        val builder = NotificationCompat.Builder(applicationContext)
        builder.setSmallIcon(R.mipmap.ic_launcher)

        // ノーティフィケションを生成した時のインテントを作成する
        val notificationIntent = Intent(this@MyBeaconService, Notification::class.java)
        val contentIntent = PendingIntent.getActivity(this@MyBeaconService, 0,
                notificationIntent, 0)

        builder.setContentTitle(title) // 1行目
        if (title == "locked") {
            builder.setContentText("施錠されております。")
        } else if (title == "unlocked") {
            builder.setContentText("解錠されております。")
        } else if (title == "unknown") {
            builder.setContentText("鍵の状態が判りませんでした。")
        } else if (title == "Connection Error") {
            builder.setContentText("通信処理が正常に終了されませんでした。\n通信環境を御確認下さい。")
        } else if (title.indexOf("400") != -1) {
            builder.setContentText("予期せぬエラーが発生致しました。\n開発者に御問合せ下さい。")
        } else if (title.indexOf("403") != -1) {
            builder.setContentText("認証に失敗致しました。\nシステム管理者に登録を御確認下さい。")
        } else if (title == "Enter Region") {
            builder.setContentText("領域に入りました。")
        } else if (title == "Exit Region") {
            builder.setContentText("領域から出ました。")
        }
        builder.setContentIntent(contentIntent)
        builder.setTicker("ＳＭＡＬＯ") // 通知到着時に通知バーに表示(4.4まで)
        // 5.0からは表示されない

        val manager = NotificationManagerCompat.from(applicationContext)
        manager.notify(1, builder.build())

    }

    fun ensureSystemServices() {
        mNsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        /*if (nsdManager == null) {
            return
        }*/
    }

    private fun startDiscovery() {
        mNsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, MyDiscoveryListener())
    }

    private fun stopDiscovery() {
        if (mIsDiscoveryStarted)
            mNsdManager?.stopServiceDiscovery(MyDiscoveryListener())
    }

    private inner class MyDiscoveryListener : NsdManager.DiscoveryListener {
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.i(TAG_NSD, String.format("Service found serviceInfo=%s", serviceInfo))
            if (serviceInfo.serviceType.equals(SERVICE_TYPE) &&
                    serviceInfo.serviceName == MY_SERVICE_NAME) {
                mNsdManager?.resolveService(serviceInfo, MyResolveListener())
            }
        }

        override fun onDiscoveryStarted(serviceType: String) {
            mIsDiscoveryStarted = true
            Log.i(TAG_NSD, String.format("Discovery started serviceType=%s", serviceType))
        }

        override fun onDiscoveryStopped(serviceType: String) {
            mIsDiscoveryStarted = false
            Log.i(TAG_NSD, String.format("Discovery stopped serviceType=%s", serviceType))
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            mIsDiscoveryStarted = false
            Log.i(TAG_NSD, String.format("Service lost serviceInfo=%s", serviceInfo))
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG_NSD, String.format("Failed to start discovery serviceType=%s, errorCode=%d",
                    serviceType, errorCode))
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG_NSD, String.format("Failed to stop discovery serviceType=%s, errorCode=%d",
                    serviceType, errorCode))
        }
    }

    private inner class MyResolveListener : NsdManager.ResolveListener {
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.i(TAG_NSD, String.format("Service resolved serviceInfo=%s", serviceInfo.host))
            if (serviceInfo.serviceName == MY_SERVICE_NAME) {
                mHost = serviceInfo.host.toString()
                //stopDiscovery()
            }
        }

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG_NSD, String.format("Failed to resolve serviceInfo=%s, errorCode=%d",
                    serviceInfo, errorCode))
        }
    }

    private fun sendBroadCastToMainActivity(state: String) {
        Log.d(TAG_BEACON, "sendBroadCastToMainActivity,$state")
        val broadcastIntent: Intent = Intent()
        broadcastIntent.putExtra("state", state)
        broadcastIntent.action = "UPDATE_ACTION"
        baseContext.sendBroadcast(broadcastIntent)
    }

    private fun sendBroadCastToWidget(message: String) {
        Log.d(TAG_BEACON, "created")
        val broadcastIntent: Intent = Intent()
        broadcastIntent.putExtra("message", message)
        broadcastIntent.action = "UPDATE_WIDGET"
        baseContext.sendBroadcast(broadcastIntent)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG_BEACON, "created")
        //BTMのインスタンス化
        mBeaconManager = BeaconManager.getInstanceForApplication(this)
        mIsUnlocked = false

        //Parserの設定
        val IBEACON_FORMAT: String = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
        mBeaconManager?.beaconParsers?.add(BeaconParser().setBeaconLayout(IBEACON_FORMAT))

        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        // 端末固有識別番号読出
        mId = sp.getString("SaveString", null)
        if (mId == null) {
            Log.d("id", "null")
            // 端末固有識別番号取得
            mId = UUID.randomUUID().toString()
            //mId = "2df60388-e96e-4945-93d0-a4836ee75a3c"
            // 端末固有識別番号記憶
            sp.edit().putString("SaveString", mId).apply()
        }
        val identifier: Identifier = Identifier.parse(MY_SERVICE_UUID)
        Log.d("id", mId)
        // Beacon名の作成
        val beaconId = this@MyBeaconService.packageName
        // major, minorの指定はしない
        mRegion = Region(beaconId, identifier, null, null)
        //mRegion = Region(beaconId, null, null, null)
        mRegionBootstrap = RegionBootstrap(this, mRegion)
        // iBeacon領域を監視(モニタリング)するスキャン間隔を設定
        //mBeaconManager?.setBackgroundScanPeriod(1000)
        mBeaconManager?.setBackgroundBetweenScanPeriod(1000)
        //mBeaconManager?.setForegroundScanPeriod(1000)
        mBeaconManager?.setForegroundBetweenScanPeriod(1000)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag")
        mWakeLock?.acquire()
        Log.d(TAG_BEACON, "beforeEnsureSystemServices")
        ensureSystemServices()
        Log.d(TAG_BEACON, "ensuredSystemServices")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            var key: String? = intent.extras?.getString(MainActivity.KEY);
            if (key != null && key != "") {
                Log.d(TAG_BEACON, key)
                getRequest("http:/$mHost:10080/api/locks/$key/$mHashValue")
            }
        }
        if (mHost == null) {
            mIsDiscoveryStarted = false
            startDiscovery()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG_BEACON, "destroy")
        try {
            // レンジング停止
            mBeaconManager?.stopRangingBeaconsInRegion(mRegion)
            mBeaconManager?.stopMonitoringBeaconsInRegion(mRegion)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        mWakeLock?.release()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

        googleApiClient = GoogleApiClient.Builder(this).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build()
        googleApiClient!!.connect()
    }

    //データを更新
    private fun sendDataByMessageApi(message: String) {

        Log.d("サービス", "動いた")
        Thread(Runnable {
            Log.d("サービス", "ラン")
            val nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await()
            for (node in nodes.nodes) {
                Log.d("サービス", "フォー")
                Wearable.MessageApi.sendMessage(googleApiClient, node.id, "/data_comm", message.toByteArray())
            }
        }).start()
    }

    override fun onMessageReceived(messageEvents: MessageEvent?) {
        if (messageEvents!!.path == "/data_comm2") {
            mMessage = String(messageEvents.data)
            Log.d(mMessage, "受け取ったメッセージ")

            //取得した内容によって処理
            if (mMessage == "getState" || mMessage == "wakeState") {
                //TODO 鍵の情報の取得　wearに状態を表示させるための処理
                //TODO doorStateに結果を代入
                Log.d("データ", "送信")
                sendDataByMessageApi(mState as String)
            } else if (mMessage == "stateUpdate") {
                if (mState == "open" || mState == "close") {
                    //TODO 解錠施錠要求の送信　処理結果をwearに返す
                    //TODO doorStateに結果を代入
                    sendDataByMessageApi(mState as String)
                }
            } else {
                Log.d("要求", "通ってない")
            }
        }
    }

    override fun onConnected(bundle: Bundle?) {

        Log.d("onConnected", "実行")
    }

    override fun onConnectionSuspended(i: Int) {
        Log.d("Suspended", "実行")
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.d("Failed", "実行")
    }
}
