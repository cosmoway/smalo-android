package net.cosmoway.smalo

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.*
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.NotificationCompat
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

class MainActivity : Activity(), View.OnClickListener {

    private var mReceiver: MyBroadcastReceiver? = null
    private var mState: String? = null
    private var mHost: String? = null
    private var mHashValue: String? = null
    // Nsd Manager
    private var mNsdManager: NsdManager? = null
    // UUID設定用
    private var mId: String? = null
    // MM
    private var mMajor: String? = null
    private var mMinor: String? = null

    private var mIsLocked: Boolean? = null
    private var mIntentFilter: IntentFilter? = null
    private var mLockButton: ImageButton? = null
    private var mAnimatorSet1: AnimatorSet? = null
    private var mAnimatorSet2: AnimatorSet? = null
    private var mAnimatorSet3: AnimatorSet? = null
    private var mOval1: ImageView? = null
    private var mOval2: ImageView? = null
    private var mOval3: ImageView? = null
    private var mOval4: ImageView? = null
    private var mOval5: ImageView? = null
    private var mainTimer: Timer? = null                    //タイマー用
    private var mainTimerTask: MainTimerTask? = null        //タイマタスククラス
    private val mHandler = Handler()   //UI Threadへのpost用ハンドラ

    companion object {
        //スリープモードからの復帰の為のフラグ定数
        private val FLAG_KEYGUARD =
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        private val REQUEST_PERMISSION = 1
        private val TAG = "MainActivity"
        private val MY_APP_NAME = "ＳＭＡＬＯ"
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
                Log.d(TAG, result)
                return result
            }

            override fun onPostExecute(result: String?) {
                if (result != null) {
                    if (result == "locked" || result == "unlocked" || result == "unknown" || result == "200 OK") {
                        makeNotification(result)
                        if (result == "200 OK") {
                            val uri: Uri = RingtoneManager
                                    .getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            val ringtone: Ringtone = RingtoneManager
                                    .getRingtone(applicationContext, uri)
                            ringtone.play()
                        } else if (result == "locked" || result == "unlocked" || result == "unknown") {
                            mState = result
                        }
                        if (result == "locked" || (result == "200 OK" && mIsLocked == false)) {
                            mIsLocked = true
                            Log.d(TAG, "message:L")
                            animationEnd()
                            mLockButton?.setImageResource(R.drawable.smalo_close_button)
                            mLockButton?.isEnabled = true
                            mOval4?.visibility = View.VISIBLE
                        } else if (result == "unlocked" || (result == "200 OK" && mIsLocked == true)) {
                            mIsLocked = false
                            Log.d(TAG, "message:UL")
                            animationEnd()
                            mLockButton?.setImageResource(R.drawable.smalo_open_button)
                            mLockButton?.isEnabled = true
                            mOval5?.visibility = View.VISIBLE
                        } else if (result == "unknown") {
                            Log.d(TAG, "message:UK")
                            mLockButton?.setImageResource(R.drawable.smalo_search_icon)
                            mLockButton?.isEnabled = false
                            mOval4?.visibility = View.GONE
                            mOval5?.visibility = View.GONE

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
        builder.setSmallIcon(R.mipmap.smalo_icon)

        // ノーティフィケションを生成した時のインテントを作成する
        val notificationIntent = Intent(this, Notification::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0,
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
        mNsdManager?.discoverServices(MyBeaconService.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, MyDiscoveryListener())
    }

    private fun stopDiscovery() {
        mNsdManager?.stopServiceDiscovery(MyDiscoveryListener())
    }

    private inner class MyDiscoveryListener : NsdManager.DiscoveryListener {
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.i(MyBeaconService.TAG_NSD, String.format("Service found serviceInfo=%s", serviceInfo))
            if (serviceInfo.serviceType.equals(MyBeaconService.SERVICE_TYPE) &&
                    serviceInfo.serviceName == MyBeaconService.MY_SERVICE_NAME) {
                mNsdManager?.resolveService(serviceInfo, MyResolveListener())
            }
        }

        override fun onDiscoveryStarted(serviceType: String) {
            Log.i(MyBeaconService.TAG_NSD, String.format("Discovery started serviceType=%s", serviceType))
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(MyBeaconService.TAG_NSD, String.format("Discovery stopped serviceType=%s", serviceType))
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.i(MyBeaconService.TAG_NSD, String.format("Service lost serviceInfo=%s", serviceInfo))
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(MyBeaconService.TAG_NSD, String.format("Failed to start discovery serviceType=%s, errorCode=%d",
                    serviceType, errorCode))
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(MyBeaconService.TAG_NSD, String.format("Failed to stop discovery serviceType=%s, errorCode=%d",
                    serviceType, errorCode))
        }
    }

    private inner class MyResolveListener : NsdManager.ResolveListener {
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.i(MyBeaconService.TAG_NSD, String.format("Service resolved serviceInfo=%s", serviceInfo.host))
            if (serviceInfo.serviceName == MyBeaconService.MY_SERVICE_NAME) {
                mHost = serviceInfo.host.toString()
                //stopDiscovery()
            }
        }

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w(MyBeaconService.TAG_NSD, String.format("Failed to resolve serviceInfo=%s, errorCode=%d",
                    serviceInfo, errorCode))
        }
    }

    private fun animationStart() {
        Log.d(TAG, "animStart")
        mOval4?.visibility = View.GONE
        mOval5?.visibility = View.GONE
        mAnimatorSet1?.start()
        mAnimatorSet2?.start()
        mAnimatorSet3?.start()
    }

    private fun animationEnd() {
        Log.d(TAG, "animEnd")
        mAnimatorSet1?.end()
        mAnimatorSet2?.end()
        mAnimatorSet3?.end()
    }

    // サービスから値を受け取った時に動かしたい内容を書く
    private val updateHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            val bundle = msg.data
            mMajor = bundle.getString("major")
            mMinor = bundle.getString("minor")
            Log.d(TAG, "message:$mMajor,$mMinor")
            mHashValue = toEncryptedHashValue("SHA-256", "$mId|$mMajor|$mMinor")
        }

    }

    private fun requestLocationPermission() {
        // 位置情報サーヴィス を利用可能か
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            //許可を求めるダイアログを表示します。
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 0)
        }
    }

    private fun requestBatteryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                        .setTitle("確認")
                        .setMessage("本アプリが正常に動作する為には、電池の最適化の解除が必要です。"
                                + "\nなお、最適化状態時は、本アプリの動作に影響が発生します。")
                        .setPositiveButton("OK") { dialog, which ->
                            val intent = Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:" + packageName)
                            startActivityForResult(intent, REQUEST_PERMISSION)
                        }.show()
            }
        }
    }

    private fun findViews() {
        mOval1 = findViewById(R.id.oval1) as ImageView
        mOval2 = findViewById(R.id.oval2) as ImageView
        mOval3 = findViewById(R.id.oval3) as ImageView
        mOval4 = findViewById(R.id.oval4) as ImageView
        mOval5 = findViewById(R.id.oval5) as ImageView
        mLockButton = findViewById(R.id.btn_lock) as ImageButton
        mLockButton?.isEnabled = false
    }

    private fun setOnClickListeners() {
        mLockButton?.setOnClickListener(this)
    }

    private fun setAnimators() {
        mAnimatorSet1 = AnimatorInflater.loadAnimator(this, R.animator.anim_oval1) as AnimatorSet;
        mAnimatorSet2 = AnimatorInflater.loadAnimator(this, R.animator.anim_oval2) as AnimatorSet;
        mAnimatorSet3 = AnimatorInflater.loadAnimator(this, R.animator.anim_oval3) as AnimatorSet;
        (mAnimatorSet1 as AnimatorSet).setTarget(mOval1);
        (mAnimatorSet2 as AnimatorSet).setTarget(mOval2);
        (mAnimatorSet3 as AnimatorSet).setTarget(mOval3);
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViews()
        setOnClickListeners()
        setAnimators()

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE非対応端末です", Toast.LENGTH_SHORT).show();
            finish();
        }

        requestLocationPermission()
        requestAccessStoragePermission()
        requestBatteryPermission()

        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        // 端末固有識別番号読出
        mId = sp.getString("SaveString", null)
        if (mId == null) {
            Log.d("id", "null")
            // 端末固有識別番号取得
            //mId = UUID.randomUUID().toString()
            mId = "2df60388-e96e-4945-93d0-a4836ee75a3c" //Ando
            //mId = "0648eb1d-e429-434a-a16d-12216b3f0701" //GS6
            //mId = "d83d617a-e76c-4d33-ae99-0b6ea843129e" //KN6
            // 端末固有識別番号記憶
            sp.edit().putString("SaveString", mId).apply()
        }

        //permission check
        val wifiManager: WifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiManager.isWifiEnabled == false) {
            wifiManager.isWifiEnabled = true
        }

        val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter.isEnabled == false) {
            adapter.enable()
        }

        mReceiver = MyBroadcastReceiver()
        mIntentFilter = IntentFilter()
        (mIntentFilter as IntentFilter).addAction("UPDATE_ACTION")
        registerReceiver(mReceiver, mIntentFilter)

        (mReceiver as MyBroadcastReceiver).registerHandler(updateHandler)

        Log.d(TAG, "beforeEnsureSystemServices")
        ensureSystemServices()
        Log.d(TAG, "ensuredSystemServices")

        //タイマーインスタンス生成
        mainTimer = Timer()
        //タスククラスインスタンス生成
        mainTimerTask = MainTimerTask()
        //タイマースケジュール設定＆開始
        mainTimer?.schedule(mainTimerTask, 10000, 2000)
    }

    private fun requestAccessStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            //許可を求めるダイアログを表示します。
            AlertDialog.Builder(this)
                    .setTitle("確認")
                    .setMessage("通知音を鳴らすには、\n"
                            + "本アプリに対する記憶装置へのアクセス許可発行が必要です。")
                    .setPositiveButton("OK") { dialog, which ->
                        ActivityCompat.requestPermissions(this,
                                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                    }.show()
        }
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(FLAG_KEYGUARD)
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(FLAG_KEYGUARD)
        val intent: Intent = Intent(this, MyBeaconService::class.java)
        startService(intent)
        if (mHost == null) {
            startDiscovery()
        }
        animationStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mReceiver)
        if (mAnimatorSet1 != null) {
            mAnimatorSet1?.end()
            mAnimatorSet1 = null
        }
        if (mAnimatorSet2 != null) {
            mAnimatorSet2?.end()
            mAnimatorSet2 = null
        }
        if (mAnimatorSet3 != null) {
            mAnimatorSet3?.end()
            mAnimatorSet3 = null
        }
    }

    override fun onClick(v: View?) {
        if (v == mLockButton) {
            Log.d("Button", "Lock")
            if (mState != null) {
                if (mState == "locked") {
                    getRequest("http:/$mHost:10080/api/locks/unlocking/$mHashValue")
                } else if (mState == "unlocked") {
                    getRequest("http:/$mHost:10080/api/locks/locking/$mHashValue")
                }
            }
            animationStart()
        }
    }
    // run()に定周期で処理したい内容を記述。
    inner class MainTimerTask : TimerTask() {
        override fun run() {
            mHandler.post {
                getRequest("http:/$mHost:10080/api/locks/status/$mHashValue")
            }
        }
    }
}
