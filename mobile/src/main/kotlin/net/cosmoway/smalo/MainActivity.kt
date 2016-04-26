package net.cosmoway.smalo

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast

class MainActivity : Activity(), View.OnClickListener {

    private var mReceiver: MyBroadcastReceiver? = null
    private var mState: String? = null
    private var mIntentFilter: IntentFilter? = null
    private var mLockButton: ImageButton? = null
    private var mIsLocked: Boolean? = null
    private var mAnimatorSet1: AnimatorSet? = null
    private var mAnimatorSet2: AnimatorSet? = null
    private var mAnimatorSet3: AnimatorSet? = null
    private var mAnimatorSet4: AnimatorSet? = null
    private var mAnimatorSet5: AnimatorSet? = null
    private var mOval1: ImageView? = null
    private var mOval2: ImageView? = null
    private var mOval3: ImageView? = null
    private var mOval4: ImageView? = null
    private var mOval5: ImageView? = null

    companion object {
        //スリープモードからの復帰の為のフラグ定数
        private val FLAG_KEYGUARD =
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        private val REQUEST_PERMISSION = 1
        private val TAG = "MainActivity"
        val TAG_NSD = "NSD"
        val SERVICE_TYPE = "_xdk-app-daemon._tcp."
        val MY_SERVICE_NAME = "smalo"
        //val MY_SERVICE_NAME = "smalo-dev"
        //val MY_SERVICE_UUID = "dddddddddddddddddddddddddddddddd"

        val MY_APP_NAME = "ＳＭＡＬＯ"
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
                        } else if (result == "unlocked" || (result == "200 OK" && mIsLocked == true)) {
                            mIsLocked = false
                            Log.d(TAG, "message:UL")
                            animationEnd()
                            mLockButton?.setImageResource(R.drawable.smalo_open_button)
                            mLockButton?.isEnabled = true
                        } else if (result == "unknown") {
                            Log.d(TAG, "message:UK")
                            mLockButton?.setImageResource(R.drawable.smalo_search_icon)
                            mLockButton?.isEnabled = false
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
        mNsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, MyDiscoveryListener())
    }

    private fun stopDiscovery() {
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
                //stopDiscovery()
            }
        }

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG_NSD, String.format("Failed to resolve serviceInfo=%s, errorCode=%d",
                    serviceInfo, errorCode))
        }
    }

    private fun animationStart() {
        Log.d(TAG, "animStart")
        mAnimatorSet1?.start()
        mAnimatorSet2?.start()
        mAnimatorSet3?.start()
        mAnimatorSet4?.start()
        mAnimatorSet5?.start()
    }

    private fun animationEnd() {
        Log.d(TAG, "animEnd")
        mAnimatorSet1?.end()
        mAnimatorSet2?.end()
        mAnimatorSet3?.end()
        mAnimatorSet4?.end()
        mAnimatorSet5?.end()
    }

    //TODO: サービスからブロードキャストされ、値を受け取った時に動かしたい内容を書く。
    private val updateHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            val bundle = msg.data
            val result = bundle.getString("state")
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
        }
    }

    private fun requestLocationPermission() {
        // TODO: 位置情報サーヴィス を利用可能か
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
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

    private fun requestAccessStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
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
        mAnimatorSet1 = AnimatorInflater.loadAnimator(this, R.animator.anim_oval) as AnimatorSet;
        mAnimatorSet2 = AnimatorInflater.loadAnimator(this, R.animator.anim_oval) as AnimatorSet;
        mAnimatorSet3 = AnimatorInflater.loadAnimator(this, R.animator.anim_oval) as AnimatorSet;
        mAnimatorSet4 = AnimatorInflater.loadAnimator(this, R.animator.anim_oval) as AnimatorSet;
        mAnimatorSet5 = AnimatorInflater.loadAnimator(this, R.animator.anim_oval) as AnimatorSet;
        (mAnimatorSet1 as AnimatorSet).setTarget(mOval1);
        (mAnimatorSet2 as AnimatorSet).setTarget(mOval2);
        (mAnimatorSet3 as AnimatorSet).setTarget(mOval3);
        (mAnimatorSet4 as AnimatorSet).setTarget(mOval4);
        (mAnimatorSet5 as AnimatorSet).setTarget(mOval5);
        (mAnimatorSet1 as AnimatorSet).startDelay = 0;
        (mAnimatorSet2 as AnimatorSet).startDelay = 800;
        (mAnimatorSet3 as AnimatorSet).startDelay = 1600;
        (mAnimatorSet4 as AnimatorSet).startDelay = 2400;
        (mAnimatorSet5 as AnimatorSet).startDelay = 3200;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "Created")

        findViews()
        setOnClickListeners()
        setAnimators()
        animationStart()

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE非対応端末です", Toast.LENGTH_SHORT).show();
            finish();
        }

        requestLocationPermission()
        requestAccessStoragePermission()
        requestBatteryPermission()

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
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(FLAG_KEYGUARD)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Resumed")
        window.addFlags(FLAG_KEYGUARD)
        val intent: Intent = Intent(this, MyBeaconService::class.java)
        intent.putExtra("timer", "start")
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destroyed")
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
        if (mAnimatorSet4 != null) {
            mAnimatorSet4?.end()
            mAnimatorSet4 = null
        }
        if (mAnimatorSet5 != null) {
            mAnimatorSet5?.end()
            mAnimatorSet5 = null
        }
        val intent: Intent = Intent(this, MyCommunicationService::class.java)
        intent.putExtra("id", mId)
        startService(intent)
    }

    override fun onClick(v: View?) {
        if (v == mLockButton) {
            Log.d("Button", "Lock")
            if (mState != null) {
                if (mState == "locked") {
                    val intent: Intent = Intent(this, MyBeaconService::class.java)
                    intent.putExtra("key", "unlocking")
                    startService(intent)
                } else if (mState == "unlocked") {
                    val intent: Intent = Intent(this, MyBeaconService::class.java)
                    intent.putExtra("key", "locking")
                    startService(intent)
                }
            }
            animationStart()
        }
    }
}
