package net.cosmoway.smalo

import android.app.Notification
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.RemoteException
import android.preference.PreferenceManager
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.NotificationCompat
import android.util.Log
import android.widget.RemoteViews
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import org.altbeacon.beacon.*
import org.altbeacon.beacon.startup.BootstrapNotifier
import org.altbeacon.beacon.startup.RegionBootstrap
import org.java_websocket.client.DefaultSSLWebSocketClientFactory
import javax.net.ssl.SSLContext

class MyService : WearableListenerService(), BeaconConsumer, BootstrapNotifier, RangeNotifier,
        MonitorNotifier, MyWebSocketClient.MyCallbacks, MobileActivity.Callback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    // BGで監視するiBeacon領域
    private var mRegionBootstrap: RegionBootstrap? = null
    // iBeacon検知用のマネージャー
    private var mBeaconManager: BeaconManager? = null
    // iBeacon領域
    private var mRegion: Region? = null
    private var mState: String? = null
    // Wakelock
    private var mWakeLock: PowerManager.WakeLock? = null
    // MyWebSocketClient
    private var mWebSocketClient: MyWebSocketClient? = null
    private var mIsBackground: Boolean? = null
    private var mIsEnterRegion: Boolean? = null
    private var mApiClient: GoogleApiClient? = null
    private var mIsUnlocked: Boolean? = null
    private var mReceivedMessageFromWear: String? = null
    // UUID設定用
    private var mId: String? = null


    companion object {
        private val TAG_SERVICE = "MyService"
        private val TAG_API = "API"
        private val MY_SERVICE_UUID = "51a4a738-62b8-4b26-a929-3bbac2a5ce7c"
        // FIXME: `FLAG_START`,`FLAG_STOP` の名前変更。MyService 単独で分かる名前に
        val FLAG_START = "start"
        val FLAG_STOP = "stop"
        val ACTION_UPDATE = "net.cosmoway.smalo.action.UPDATE"
        // FIXME: 名前の変更をすべき(lock, unlock を受け取るもの)
        val EXTRA_EXTRA = "extra"
        val EXTRA_UUID = "uuid"
        val EXTRA_STATE = "state"
        val COMMAND_LOCK = "lock"
        val COMMAND_UNLOCK = "unlock"
    }

    override fun connectionOpen() {
        sendUuid(mId)
    }

    override fun onConnected(bundle: Bundle?) {
        Log.i(TAG_API, "onConnected")
    }

    override fun onConnectionSuspended(i: Int) {
        Log.i(TAG_API, "Suspended")
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.i(TAG_API, "Failed")
    }

    override fun onStateChange(str: String?) {
        sendBroadcast(str as String)
        sendDataByMessageApi(str)
        mState = str
        Log.i(TAG_SERVICE, mState)
        when (str) {
            "locked" -> {
                if (mIsUnlocked == null) {
                    mIsUnlocked = false
                }
            }
            "unlocked" -> {
                if (mIsBackground == true) {
                    disconnect()
                    if (mIsUnlocked == true) {
                        ringTone()
                    } else {
                        mIsUnlocked = true
                    }
                }
            }
        }
        // AppWidgetの画面更新
        val widgetViews: RemoteViews = RemoteViews(packageName, R.layout.widget_layout)
        widgetViews.setTextViewText(R.id.info, str)
        val widget: ComponentName = ComponentName(this, MyWidgetProvider::class.java)
        val manager: AppWidgetManager = AppWidgetManager.getInstance(this)
        manager.updateAppWidget(widget, widgetViews)
    }

    override fun onUnLocking() {
        Log.i(TAG_SERVICE, "unlocking")
        sendUnlockSignal()
    }

    override fun onLocking() {
        Log.i(TAG_SERVICE, "locking")
        sendLockSignal()
    }

    override fun onConnecting() {
        Log.i(TAG_SERVICE, "connecting")
        sendUuid(mId)
    }

    override fun error(ex: Exception) {
        Log.i(TAG_SERVICE, "error:${ex.message}")
        if (!ex.message.equals("ssl == null"))
            connectIfNeeded()
    }

    private fun sendUuid(uuid: String?) {
        uuid?.let {
            sendJson("{\"uuid\":\"$it\"}")
        }
    }

    private fun sendLockSignal() {
        sendJson("{\"command\":\"lock\"}")
    }

    private fun sendUnlockSignal() {
        sendJson("{\"command\":\"unlock\"}")
    }

    private fun sendJson(json: String) {
        Log.i(TAG_SERVICE, "sendJson")
        connectIfNeeded()
        Log.i(TAG_SERVICE, mWebSocketClient?.isOpen.toString())
        if (mWebSocketClient?.isOpen as Boolean) {
            mWebSocketClient?.send(json)
        }
    }

    private fun ringTone() {
        val uri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val mp: MediaPlayer = MediaPlayer.create(baseContext, uri)
        mp.isLooping = false
        mp.seekTo(0)
        mp.start()
        mp.setOnCompletionListener({ mp.stop() })
    }


    private fun makeNotification(title: String) {
        val builder = NotificationCompat.Builder(applicationContext)
        builder.setSmallIcon(R.mipmap.smalo_icon)

        // ノーティフィケションを生成した時のインテントを作成する
        val notificationIntent = Intent(this, Notification::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0)

        builder.setContentTitle(title) // 1行目
        // FIXME: タイトルの文字で判断するのはバグを生む危険有り（タイトルを変えたとき、ここも変更する必要があることに気付けるか）
        if (title.equals("Enter Region")) {
            builder.setContentText("領域に入りました。")
        } else if (title.equals("Exit Region")) {
            builder.setContentText("領域から出ました。")
        }
        builder.setContentIntent(contentIntent)
        builder.setTicker(getString(R.string.app_name)) // 通知到着時に通知バーに表示(4.4まで)
        // 5.0からは表示されない

        val manager = NotificationManagerCompat.from(applicationContext)
        manager.notify(1, builder.build())
    }

    // TODO:状態
    private fun sendBroadcast(state: String) {
        Log.i(TAG_SERVICE, "sendBroadCastToMainActivity,$state")
        val broadcastIntent = Intent()
        broadcastIntent.putExtra(MyService.EXTRA_STATE, state)
        broadcastIntent.action = MyService.ACTION_UPDATE
        baseContext.sendBroadcast(broadcastIntent)
    }

    private fun connectIfNeeded() {
        Log.i(TAG_SERVICE, "connectIfNeeded")
        if ((mWebSocketClient == null || (mWebSocketClient as MyWebSocketClient).isClosed)
                && isConnected()) {
            Log.i(TAG_SERVICE, "connect")
            mWebSocketClient = MyWebSocketClient.newInstance()


            //val sslContext = SSLContext.getInstance("TLS")
            //sslContext.init(null, null, null)
            val sslContext = SSLContext.getDefault()
            mWebSocketClient?.setWebSocketFactory(DefaultSSLWebSocketClientFactory(sslContext))

            mWebSocketClient?.setCallbacks(this)
            mWebSocketClient?.connect()
        }
    }

    private fun disconnect() {
        Log.i(TAG_SERVICE, "disconnect")
        mWebSocketClient?.close()
        mWebSocketClient = null
    }

    private fun isConnected(): Boolean {
        if (!NetworkManager.isConnected(this)) {
            return false
        }
        return true
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG_SERVICE, "created")

        //BTMのインスタンス化
        mBeaconManager = BeaconManager.getInstanceForApplication(this)
        //Parserの設定
        val IBEACON_FORMAT: String = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
        mBeaconManager?.beaconParsers?.add(BeaconParser().setBeaconLayout(IBEACON_FORMAT))
        val identifier: Identifier = Identifier.parse(MY_SERVICE_UUID)
        // Beacon名の作成
        val beaconId = this.packageName
        // major, minorの指定はしない
        mRegion = Region(beaconId, identifier, null, null)
        mRegionBootstrap = RegionBootstrap(this, mRegion)
        // iBeacon領域を監視(モニタリング)するスキャン間隔を設定
        // TODO: iBeacon領域監視をする時間
        mBeaconManager?.setBackgroundScanPeriod(1000)
        // TODO: iBeacon領域監視を休む時間
        mBeaconManager?.setBackgroundBetweenScanPeriod(19000)
        mBeaconManager?.setForegroundScanPeriod(1000)
        mBeaconManager?.setForegroundBetweenScanPeriod(3000)
        mBeaconManager?.setBackgroundMode(true)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        /*mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
                or PowerManager.ACQUIRE_CAUSES_WAKEUP
                or PowerManager.ON_AFTER_RELEASE, "MyWakelockTag")
        mWakeLock?.acquire()*/
        // APIクライアント初期化
        mApiClient = GoogleApiClient
                .Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build()
        mApiClient?.connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG_SERVICE, "Command Started")

        val uuid = intent?.getStringExtra(MyService.EXTRA_UUID)
        // TODO: UUID読出
        if (uuid != null) {
            Log.i(TAG_SERVICE, uuid)
        }

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        mId = sp?.getString("saveId", null)
        if (mId == null) {
            Log.i(TAG_SERVICE, "uuid:null")
            // 端末固有識別番号取得
            mId = uuid
            sp?.edit()?.putString("saveId", mId)?.apply()
        }
        Log.i(TAG_SERVICE, "uuid:$mId")

        // in foreground.
        val extra: String? = intent?.getStringExtra(MyService.EXTRA_EXTRA)
        if (extra.equals(MyService.COMMAND_LOCK)) {
            sendLockSignal()
        } else if (extra.equals(MyService.COMMAND_UNLOCK)) {
            sendUnlockSignal()
        } else if (extra.equals(FLAG_START)) {
            mIsBackground = false
            disconnect()
            connectIfNeeded()
        } else if (extra.equals(FLAG_STOP) || mIsBackground == null && getRegionState() == 0) {
            mIsBackground = true
        }
        if (mId == null) {
            stopSelf()
        }
        val notification: Notification = Notification()
        notification.iconLevel = 0
        startForeground(1, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG_SERVICE, "destroy")
        try {
            // レンジング停止
            mBeaconManager?.stopRangingBeaconsInRegion(mRegion)
            // mBeaconManager?.stopMonitoringBeaconsInRegion(mRegion)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        mBeaconManager = null
        //mWakeLock?.release()
        disconnect()
        setRegionState(-1)
        stopForeground(true)
    }

    // TODO: Beaconサービスの接続と開始
    override fun onBeaconServiceConnect() {
        //領域監視の設定
        mBeaconManager?.setMonitorNotifier(this)
        try {
            // ビーコン情報の監視を開始
            // mBeaconManager?.startMonitoringBeaconsInRegion(mRegion)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    // TODO: 領域進入
    override fun didEnterRegion(region: Region) {
        Log.i(TAG_SERVICE, "Enter Region")
        mBeaconManager?.setBackgroundMode(false)
        makeNotification("Enter Region")
        disconnect()
        connectIfNeeded()
        setRegionState(1)
        if (mIsBackground == true) {
            mIsEnterRegion = true
        }

        // レンジング開始
        try {
            mBeaconManager?.startRangingBeaconsInRegion(region)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        //Beacon情報の取得
        mBeaconManager?.setRangeNotifier(this)
    }

    // TODO: 領域退出
    override fun didExitRegion(region: Region) {
        // TODO: レンジング停止
        try {
            Log.i(TAG_SERVICE, "Exit Region")
            mBeaconManager?.stopRangingBeaconsInRegion(region)
            makeNotification("Exit Region")
            if (mIsBackground == true) {
                disconnect()
                mIsUnlocked = null
            } else if (mIsBackground == null) {
                disconnect()
                mIsBackground = true
            }
            mIsEnterRegion = false
            mBeaconManager?.setBackgroundMode(true)
            setRegionState(0)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    override fun didRangeBeaconsInRegion(beacons: MutableCollection<Beacon>?, region: Region?) {
        beacons?.forEach { beacon ->
            // ログの出力
            Log.i("Beacon", "UUID:" + beacon.id1 + ", Distance:" + beacon.distance + "m"
                    + ", RSSI:" + beacon.rssi + ", txPower:" + beacon.txPower)
            Log.i(TAG_SERVICE, "state: ${mIsBackground.toString()}, enter:${mIsEnterRegion.toString()}, stateInt:${getRegionState().toString()}")
            if (beacon.id1.toString() == MY_SERVICE_UUID && mIsBackground == true) {
                if (beacon.distance != -1.0 && mIsUnlocked == false && mIsEnterRegion == true) {
                    // TODO:解錠リクエスト
                    sendUnlockSignal()
                    mIsUnlocked = true
                } else if (mIsUnlocked == null) {
                    connectIfNeeded()
                }
            }
        }
    }

    private fun setRegionState(regionState: Int) {
        val sp = getSharedPreferences("pref", Context.MODE_PRIVATE);
        val editor = sp.edit();
        editor.putInt("RegionState", regionState).apply()
    }

    private fun getRegionState(): Int {
        val sp = getSharedPreferences("pref", Context.MODE_PRIVATE);
        val regionState = sp.getInt("RegionState", -1);
        return regionState
    }

    // 領域に対する状態が変化
    override fun didDetermineStateForRegion(i: Int, region: Region) {
        Log.i(TAG_SERVICE, "Determine State: " + i)
    }

    // TODO: ウェアのデータを更新
    private fun sendDataByMessageApi(message: String) {
        Thread(Runnable {
            Log.i(TAG_API, "run")
            val nodes = Wearable.NodeApi.getConnectedNodes(mApiClient).await()
            for (node in nodes.nodes) {
                Log.i(TAG_API, "sendMessageToWear:$message")
                Wearable.MessageApi
                        .sendMessage(mApiClient, node.id, "/data_comm", message.toByteArray())
            }
        }).start()
    }

    // TODO: ウェアからのメッセージを受け取った時。
    override fun onMessageReceived(messageEvents: MessageEvent?) {
        if (messageEvents?.path == "/data_comm2") {
            mReceivedMessageFromWear = String(messageEvents!!.data)
            Log.i(TAG_SERVICE, "receivedMessage: $mReceivedMessageFromWear")

            // TODO: 取得した内容に応じ処理
            // TODO: 問い合わせ要求時
            if (mReceivedMessageFromWear.equals("getState")) {
                //TODO: 鍵の情報の取得
                Log.i(TAG_SERVICE, "getState")
                if (mState != null) {
                    sendBroadcast(mState as String)
                    sendDataByMessageApi(mState as String)
                } else {
                    sendBroadcast("unknown")
                    sendDataByMessageApi("unknown")
                }
                // TODO: 解錠施錠要求時
            } else if (mReceivedMessageFromWear.equals("stateUpdate")) {
                Log.i(TAG_API, "locking")
                //TODO: 今のステートに応じて処理する。Wearに結果返すのは解錠施錠時。
                if (mState.equals("locked")) {
                    //TODO: 開処理リクエスト。
                    Log.i(TAG_SERVICE, "unlocking")
                    sendUnlockSignal()
                } else if (mState.equals("unlocked")) {
                    //TODO:閉処理リクエスト。
                    Log.i(TAG_SERVICE, "locking")
                    sendLockSignal()
                }
            } else {
                Log.i("要求", "通ってない")
            }
        }
    }
}
