package net.cosmoway.smalo

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import android.preference.PreferenceManager
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.NotificationCompat
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.altbeacon.beacon.*
import org.altbeacon.beacon.startup.BootstrapNotifier
import org.altbeacon.beacon.startup.RegionBootstrap
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

// BeaconServiceクラス
class MyService : WearableListenerService(), BeaconConsumer, BootstrapNotifier, RangeNotifier,
        MonitorNotifier, MyWebSocketClient.MyCallbacks, MobileActivity.Callback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private var mActivity: MobileActivity? = null

    // BGで監視するiBeacon領域
    private var mRegionBootstrap: RegionBootstrap? = null
    // iBeacon検知用のマネージャー
    private var mBeaconManager: BeaconManager? = null
    // iBeacon領域
    private var mRegion: Region? = null
    private var mHost: String? = null
    private var mState: String? = null
    // Wakelock
    private var mWakeLock: PowerManager.WakeLock? = null
    // MyWebSocketClient
    private var mWebSocketClient: MyWebSocketClient? = null

    private var mIsBackground: Boolean? = null
    private var mApiClient: GoogleApiClient? = null
    private var mIsUnlocked: Boolean? = null
    private var mReceivedMessageFromWear: String? = null
    private var mHashValue: String? = null
    // Nsd Manager
    private var mNsdManager: NsdManager? = null
    // UUID設定用
    private var mId: String? = null      //タイマタスククラス
    private var mHandler: Handler = Handler();   //UI Threadへのpost用ハンドラ


    companion object {
        val TAG_SERVICE = "MyService"
        private val TAG_API = "API"
        val TAG_NSD = "NSD"
        val SERVICE_TYPE = "_xdk-app-daemon._tcp."
        val MY_SERVICE_NAME = "smalo"
        //val MY_SERVICE_NAME = "smalo-dev"
        val MY_SERVICE_UUID = "51a4a738-62b8-4b26-a929-3bbac2a5ce7c"
        val MY_APP_NAME = "SMALO"
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
                var result: String
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
                Log.d(TAG_SERVICE, result)
                return result
            }

            override fun onPostExecute(result: String?) {
                if (result != null) {
                    if (result.equals("locked") || result.equals("unlocked") || result.equals("unknown")) {
                        sendDataByMessageApi(result)
                        sendBroadCast(result)
                        mState = result
                        if (result == "locked" && mIsUnlocked == false && mIsBackground == true) {
                            //TODO:開処理リクエスト。
                            //getRequest("http:/$mHost:10080/api/locks/unlocking/$mHashValue")
                        }
                    } else {
                        if (result.equals("200 OK")) {
                            sendDataByMessageApi(result)
                            sendBroadCast(result)
                            val uri: Uri = RingtoneManager
                                    .getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            val ringtone: Ringtone = RingtoneManager
                                    .getRingtone(applicationContext, uri)
                            ringtone.play()
                            mIsUnlocked = true
                        }
                        makeNotification(result)
                    }
                }
            }
        }.execute()
    }

    // TODO: 状態。
    override fun lock() {
        sendBroadCast("locked")
    }

    override fun unlock() {
        sendBroadCast("unlocked")
    }

    override fun unknown() {
        sendBroadCast("unknown")
    }

    override fun connectionOpen() {
        sendJson("{\"uuid\":\"$mId\"}")
    }


    private fun makeNotification(title: String) {
        val builder = NotificationCompat.Builder(applicationContext)
        builder.setSmallIcon(R.mipmap.smalo_icon)

        // ノーティフィケションを生成した時のインテントを作成する
        val notificationIntent = Intent(this, Notification::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0)

        builder.setContentTitle(title) // 1行目
        if (title.equals("Connection Error")) {
            builder.setContentText("通信処理が正常に終了されませんでした。\n通信環境を御確認下さい。")
        } else if (title.indexOf("400") != -1) {
            builder.setContentText("予期せぬエラーが発生致しました。\n開発者に御問合せ下さい。")
        } else if (title.indexOf("403") != -1) {
            builder.setContentText("認証に失敗致しました。\nシステム管理者に登録を御確認下さい。")
        } else if (title.equals("Enter Region")) {
            builder.setContentText("領域に入りました。")
        } else if (title.equals("Exit Region")) {
            builder.setContentText("領域から出ました。")
        }
        builder.setContentIntent(contentIntent)
        builder.setTicker(MY_APP_NAME) // 通知到着時に通知バーに表示(4.4まで)
        // 5.0からは表示されない

        val manager = NotificationManagerCompat.from(applicationContext)
        manager.notify(1, builder.build())
    }

    fun ensureSystemServices() {
        mNsdManager = getSystemService(Service.NSD_SERVICE) as NsdManager
        /*if (nsdManager == null) {
            return
        }*/
    }

    private fun startDiscovery() {
        mNsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, MyDiscoveryListener())
    }

    private fun stopDiscovery() {
        mNsdManager?.stopServiceDiscovery(MyDiscoveryListener())
    }

    private inner class MyDiscoveryListener : NsdManager.DiscoveryListener {
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.i(TAG_NSD, String.format("Service found serviceInfo=%s", serviceInfo))
            if (serviceInfo.serviceType.equals(SERVICE_TYPE)) {
                mNsdManager?.resolveService(serviceInfo, MyResolveListener())
            }
        }

        override fun onDiscoveryStarted(serviceType: String) {
            Log.i(TAG_NSD, String.format("Discovery started serviceType=%s", serviceType))
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG_NSD, String.format("Discovery stopped serviceType=%s", serviceType))
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
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
            }
        }

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG_NSD, String.format("Failed to resolve serviceInfo=%s, errorCode=%d",
                    serviceInfo, errorCode))
        }
    }

    // TODO:状態
    private fun sendBroadCast(state: String) {
        Log.d(TAG_SERVICE, "sendBroadCastToMainActivity,$state")
        val broadcastIntent: Intent = Intent()
        broadcastIntent.putExtra("state", state)
        broadcastIntent.action = "UPDATE_ACTION"
        baseContext.sendBroadcast(broadcastIntent)
    }

    // TODO:MM値
    private fun sendBroadcast(major: String, minor: String) {
        Log.d(TAG_SERVICE, "sendBroadcast")
        val broadcastIntent: Intent = Intent()
        broadcastIntent.putExtra("major", major)
        broadcastIntent.putExtra("minor", minor)
        broadcastIntent.action = "UPDATE_ACTION"
        baseContext.sendBroadcast(broadcastIntent)
    }

    override fun onCreate() {
        super.onCreate()
        connectIfNeeded()
        Log.d(TAG_SERVICE, "created")
        mIsUnlocked = false
        mIsBackground = true

        // TODO:端末固有識別番号読出
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        mId = sp?.getString("saveId", null)
        if (mId == null) {
            Log.d("id", "null")
            // 端末固有識別番号取得
            // mId = UUID.randomUUID().toString()
            mId = "2df60388-e96e-4945-93d0-a4836ee75a3c" //ando

            sp?.edit()?.putString("saveId", mId)?.apply()
        }

        mActivity = MobileActivity()
        mActivity?.setCallback(this)
        Log.d("id", mId)
        //sendJson("{\"uuid\":\"$mId\"}")
        //BTMのインスタンス化
        /*mBeaconManager = BeaconManager.getInstanceForApplication(this)

        //Parserの設定
        val IBEACON_FORMAT: String = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
        mBeaconManager?.beaconParsers?.add(BeaconParser().setBeaconLayout(IBEACON_FORMAT))
        val identifier: Identifier = Identifier.parse(MY_SERVICE_UUID)

        // Beacon名の作成
        val beaconId = this@MyService.packageName
        // major, minorの指定はしない
        mRegion = Region(beaconId, identifier, null, null)
        //mRegion = Region(beaconId, null, null, null)
        mRegionBootstrap = RegionBootstrap(this, mRegion)
        // iBeacon領域を監視(モニタリング)するスキャン間隔を設定
        mBeaconManager?.setBackgroundScanPeriod(3000)
        mBeaconManager?.setBackgroundBetweenScanPeriod(1000)
        mBeaconManager?.setForegroundScanPeriod(3000)
        mBeaconManager?.setForegroundBetweenScanPeriod(1000)*/

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag")
        mWakeLock?.acquire()

        // APIクライアント初期化
        mApiClient = GoogleApiClient
                .Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build()
        mApiClient?.connect()

        // TODO: 名前解決
        Log.d(TAG_SERVICE, "beforeEnsure")
        //ensureSystemServices()
        Log.d(TAG_SERVICE, "ensured")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG_SERVICE, "Command Started")
        // in foreground.
        val extra: String? = intent?.extras?.getString("extra");
        if (extra.equals("lock") || extra.equals("unlock")) {
            sendJson("{\"command\":\"$extra\"}")
            //getRequest("http:/$mHost:10080/api/locks/$extra/$mHashValue")
        }

        if (mHost == null) {
            //startDiscovery()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG_SERVICE, "destroy")
        /*try {
            // レンジング停止
            mBeaconManager?.stopRangingBeaconsInRegion(mRegion)
            mBeaconManager?.stopMonitoringBeaconsInRegion(mRegion)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        mBeaconManager = null*/
        mWakeLock?.release()
        disconnect()
    }

    //Beaconサービスの接続と開始
    override fun onBeaconServiceConnect() {
        //領域監視の設定
        /*mBeaconManager?.setMonitorNotifier(this)
        try {
            // ビーコン情報の監視を開始
            mBeaconManager?.startMonitoringBeaconsInRegion(mRegion)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }*/
    }

    // 領域進入
    override fun didEnterRegion(region: Region) {
        Log.d(TAG_SERVICE, "Enter Region")
        makeNotification("Enter Region")

        // レンジング開始
        try {
            mBeaconManager?.startRangingBeaconsInRegion(region)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        //Beacon情報の取得
        mBeaconManager?.setRangeNotifier(this)
    }

    // 領域退出
    override fun didExitRegion(region: Region) {
        // レンジング停止
        try {
            Log.d(TAG_SERVICE, "Exit Region")
            mBeaconManager?.stopRangingBeaconsInRegion(region)
            makeNotification("Exit Region")
            disconnect()
            mIsUnlocked = false
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    override fun didRangeBeaconsInRegion(beacons: MutableCollection<Beacon>?, region: Region?) {
        beacons?.forEach { beacon ->
            // ログの出力
            Log.d("Beacon", "UUID:" + beacon.id1 + ", major:" + beacon.id2 + ", minor:" + beacon.id3
                    + ", Distance:" + beacon.distance + "m"
                    + ", RSSI:" + beacon.rssi + ", txPower:" + beacon.txPower)

            sendBroadcast(beacon.id2.toString(), beacon.id3.toString())
            val major: String = beacon.id2.toString()
            val minor: String = beacon.id3.toString()
            Log.d(TAG_SERVICE, "Host:$mHost")
            if (mHost != null && beacon.distance != -1.0
                    && beacon.id1.toString() == MY_SERVICE_UUID) {
                Log.d(TAG_SERVICE, "major:$major, minor:$minor")
                // TODO:解錠リクエスト
                sendJson("{\"command\":\"unlock\"}")
                /*mHashValue = toEncryptedHashValue("SHA-256", "$mId|$major|$minor")
                getRequest("http:/$mHost:10080/api/locks/status/$mHashValue")*/
            } else {
                disconnect()
            }
        }
    }

    // 領域に対する状態が変化
    override fun didDetermineStateForRegion(i: Int, region: Region) {
        Log.d(TAG_SERVICE, "Determine State: " + i)
    }

    //データを更新
    private fun sendDataByMessageApi(message: String) {
        Thread(Runnable {
            Log.d(TAG_API, "run")
            val nodes = Wearable.NodeApi.getConnectedNodes(mApiClient).await()
            for (node in nodes.nodes) {
                Log.d(TAG_API, "sendMessageToWear:$message")
                Wearable.MessageApi
                        .sendMessage(mApiClient, node.id, "/data_comm", message.toByteArray())
            }
        }).start()
    }

    // TODO: ウェアからのメッセージを受け取った時。
    override fun onMessageReceived(messageEvents: MessageEvent?) {
        if (messageEvents?.path == "/data_comm2") {
            mReceivedMessageFromWear = String(messageEvents!!.data)
            Log.d(TAG_SERVICE, "receivedMessage: $mReceivedMessageFromWear")

            //TODO: 取得した内容に応じ処理
            // TODO:問い合わせ要求時
            if (mReceivedMessageFromWear.equals("getState")) {
                //TODO: 鍵の情報の取得
                Log.d(TAG_SERVICE, "getState")
                if (mState != null) {
                    sendBroadCast(mState as String)
                    sendDataByMessageApi(mState as String)
                } else {
                    sendBroadCast("unknown")
                    sendDataByMessageApi("unknown")
                }
                // TODO:解錠施錠要求時
            } else if (mReceivedMessageFromWear.equals("stateUpdate")) {
                Log.d(TAG_API, "locking")
                //TODO: 今のステートに応じて処理する。Wearに結果返すのは解錠施錠時。
                if (mState.equals("locked")) {
                    //TODO:開処理リクエスト。
                    Log.d("鍵", "あける");
                    sendJson("{\"command\":\"unlock\"}")
                } else if (mState.equals("unlocked")) {
                    //TODO:閉処理リクエスト。
                    Log.d("鍵", "しめる");
                    sendJson("{\"command\":\"lock\"}")
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

    override fun onUnLocking() {
        Log.d(TAG_API, "unlocking")
        sendJson("{\"command\":\"unlock\"}")
    }

    override fun onLocking() {
        Log.d(TAG_API, "unlocking")
        sendJson("{\"command\":\"lock\"}")
    }

    override fun onConnecting() {
        sendJson("{\"uuid\":\"$mId\"}")
    }

    private fun sendJson(json: String) {
        Log.d(TAG_SERVICE, "sendJson")
        connectIfNeeded()
        Log.d(TAG_SERVICE, mWebSocketClient?.isOpen.toString())
        if (mWebSocketClient?.isOpen as Boolean) {
            mWebSocketClient?.send(json)
        }
    }

    private fun connectIfNeeded() {
        Log.d(TAG_SERVICE, "connectIfNeeded")
        if (mWebSocketClient == null || (mWebSocketClient as MyWebSocketClient).isClosed) {
            Log.d(TAG_SERVICE, "connect")
            mWebSocketClient = MyWebSocketClient.newInstance()
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, SecureRandom());
            mWebSocketClient?.setWebSocketFactory(DefaultSSLWebSocketClientFactory(sslContext))

            mWebSocketClient?.setCallbacks(this@MyService)
            mWebSocketClient?.connect()
        }
    }

    private fun disconnect() {
        Log.d(TAG_SERVICE, "disconnect")
        if (mWebSocketClient != null) {
            (mWebSocketClient as MyWebSocketClient).close()
            mWebSocketClient = null
        }
    }
}
