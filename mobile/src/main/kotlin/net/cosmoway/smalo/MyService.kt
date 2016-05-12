package net.cosmoway.smalo

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.RemoteException
import android.preference.PreferenceManager
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.NotificationCompat
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import org.altbeacon.beacon.*
import org.altbeacon.beacon.startup.BootstrapNotifier
import org.altbeacon.beacon.startup.RegionBootstrap
import org.java_websocket.client.DefaultSSLWebSocketClientFactory
import java.security.SecureRandom
import javax.net.ssl.SSLContext

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
    // UUID設定用
    private var mId: String? = null


    companion object {
        private val TAG_SERVICE = "MyService"
        private val TAG_API = "API"
        //private val MY_SERVICE_NAME = "smalo-dev"
        private val MY_SERVICE_UUID = "51a4a738-62b8-4b26-a929-3bbac2a5ce7c"
        private val MY_APP_NAME = "SMALO"
    }

    // TODO: 状態。
    override fun lock() {
        mIsUnlocked = false
        mState = "locked"
        sendBroadCast("locked")
    }

    override fun unlock() {
        mIsUnlocked = true
        mState = "unlocked"
        sendBroadCast("unlocked")
    }

    override fun unknown() {
        mState = "unknown"
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
        if (title.equals("Enter Region")) {
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

    // TODO:状態
    private fun sendBroadCast(state: String) {
        Log.d(TAG_SERVICE, "sendBroadCastToMainActivity,$state")
        val broadcastIntent: Intent = Intent()
        broadcastIntent.putExtra("state", state)
        broadcastIntent.action = "UPDATE_ACTION"
        baseContext.sendBroadcast(broadcastIntent)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG_SERVICE, "created")
        mIsBackground = true

        mActivity = MobileActivity()
        mActivity?.setCallback(this)
        //sendJson("{\"uuid\":\"$mId\"}")
        //BTMのインスタンス化
        mBeaconManager = BeaconManager.getInstanceForApplication(this)

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
        mBeaconManager?.setForegroundBetweenScanPeriod(1000)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "MyWakelockTag")
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

        var uuid = intent?.getStringExtra("uuid")
        // TODO:端末固有識別番号読出
        if (uuid != null) {
            Log.d(TAG_SERVICE, uuid)
        }

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        mId = sp?.getString("saveId", null)
        if (mId == null) {
            Log.d(TAG_SERVICE, "uuid:null")
            // 端末固有識別番号取得
            // mId = UUID.randomUUID().toString()
            mId = uuid
            sp?.edit()?.putString("saveId", mId)?.apply()
        }
        Log.d(TAG_SERVICE, "uuid:$mId")

        // in foreground.
        val extra: String? = intent?.getStringExtra("extra");
        if (extra.equals("lock") || extra.equals("unlock")) {
            sendJson("{\"command\":\"$extra\"}")
        } else if (extra.equals("start")) {
            ///mIsBackground = false
            disconnect()
            //sendBroadCast(mState as String)
            connectIfNeeded()
        } else if (extra.equals("stop")) {
            mIsBackground = true
        }
        val notification: Notification = Notification();
        notification.iconLevel = 0;
        startForeground(1, notification);
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG_SERVICE, "destroy")
        try {
            // レンジング停止
            mBeaconManager?.stopRangingBeaconsInRegion(mRegion)
            mBeaconManager?.stopMonitoringBeaconsInRegion(mRegion)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        mBeaconManager = null
        mWakeLock?.release()
        disconnect()
        stopForeground(true)
    }

    //Beaconサービスの接続と開始
    override fun onBeaconServiceConnect() {
        //領域監視の設定
        mBeaconManager?.setMonitorNotifier(this)
        try {
            // ビーコン情報の監視を開始
            mBeaconManager?.startMonitoringBeaconsInRegion(mRegion)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    // 領域進入
    override fun didEnterRegion(region: Region) {
        Log.d(TAG_SERVICE, "Enter Region")
        /*if (mIsUnlocked == false && mIsBackground == true) {
            // TODO:解錠リクエスト
            sendJson("{\"command\":\"unlock\"}")
            mIsBackground = false
            sendBroadCast("okki")
        }*/
        makeNotification("Enter Region")
        disconnect()
        connectIfNeeded()

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
            if (mIsBackground == true) {
                disconnect()
            }
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

            val major: String = beacon.id2.toString()
            val minor: String = beacon.id3.toString()
            if (beacon.distance != -1.0 && beacon.id1.toString() == MY_SERVICE_UUID
                    && mIsUnlocked == false && mIsBackground == true) {
                Log.d(TAG_SERVICE, "major:$major, minor:$minor")
                // TODO:解錠リクエスト
                sendJson("{\"command\":\"unlock\"}")
                mIsBackground = false
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

            // TODO: 取得した内容に応じ処理
            // TODO: 問い合わせ要求時
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
                // TODO: 解錠施錠要求時
            } else if (mReceivedMessageFromWear.equals("stateUpdate")) {
                Log.d(TAG_API, "locking")
                //TODO: 今のステートに応じて処理する。Wearに結果返すのは解錠施錠時。
                if (mState.equals("locked")) {
                    //TODO: 開処理リクエスト。
                    Log.d(TAG_SERVICE, "unlocking");
                    sendJson("{\"command\":\"unlock\"}")
                } else if (mState.equals("unlocked")) {
                    //TODO:閉処理リクエスト。
                    Log.d(TAG_SERVICE, "locking");
                    sendJson("{\"command\":\"lock\"}")
                }
            } else {
                Log.d("要求", "通ってない")
            }
        }
    }

    override fun onConnected(bundle: Bundle?) {
        Log.d(TAG_API, "onConnected")
    }

    override fun onConnectionSuspended(i: Int) {
        Log.d(TAG_API, "Suspended")
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.d(TAG_API, "Failed")
    }

    override fun onUnLocking() {
        Log.d(TAG_SERVICE, "unlocking")
        sendJson("{\"command\":\"unlock\"}")
    }

    override fun onLocking() {
        Log.d(TAG_SERVICE, "locking")
        sendJson("{\"command\":\"lock\"}")
    }

    override fun onConnecting() {
        Log.d(TAG_SERVICE, "connecting")
        sendJson("{\"uuid\":\"$mId\"}")
    }

    override fun error() {
        Log.d(TAG_SERVICE, "error")
        connectIfNeeded()
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
        mWebSocketClient?.close()
        mWebSocketClient = null
    }
}

