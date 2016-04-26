package net.cosmoway.smalo

import android.Manifest
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
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
        val intent: Intent = Intent(this, MyBeaconService::class.java)
        intent.putExtra("timer", "stop")
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
