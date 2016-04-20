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
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast


class MainActivity : Activity(), View.OnClickListener {

    //スリープモードからの復帰の為のフラグ定数
    private val FLAG_KEYGUARD = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
    private var mReceiver: SesameBroadcastReceiver? = null
    private var mIntentFilter: IntentFilter? = null
    private var mStartButton: Button? = null
    private var mStopButton: Button? = null
    private var mLockButton: ImageButton? = null
    private var mAnimatorSet1: AnimatorSet? = null
    private var mAnimatorSet2: AnimatorSet? = null
    private var mAnimatorSet3: AnimatorSet? = null
    private var mOval1: ImageView? = null
    private var mOval2: ImageView? = null
    private var mOval3: ImageView? = null

    // サービスから値を受け取ったら動かしたい内容を書く
    /*private val updateHandler = object : Handler() {
         override fun handleMessage(msg: Message) {

             val bundle = msg.data
         }
     }*/

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

    private fun animationStart() {
        mAnimatorSet1?.start()
        mAnimatorSet2?.start()
        mAnimatorSet3?.start()
    }

    private fun animationEnd() {
        mAnimatorSet1?.end()
        mAnimatorSet2?.end()
        mAnimatorSet3?.end()
    }

    private fun findViews() {
        mOval1 = findViewById(R.id.oval1) as ImageView
        mOval2 = findViewById(R.id.oval2) as ImageView
        mOval3 = findViewById(R.id.oval3) as ImageView
        mStartButton = findViewById(R.id.btn_start) as Button
        mStopButton = findViewById(R.id.btn_stop) as Button
        mLockButton = findViewById(R.id.btn_lock) as ImageButton
    }

    private fun setOnClickListeners() {
        mStartButton?.setOnClickListener(this)
        mStopButton?.setOnClickListener(this)
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

    fun onUnLock() {
        mLockButton?.setImageResource(R.drawable.smalo_open_button)
        animationEnd()
    }

    fun onLock() {
        mLockButton?.setImageResource(R.drawable.smalo_close_button)
        animationEnd()
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

        //permission check
        val wifiManager: WifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiManager.isWifiEnabled == false) {
            wifiManager.isWifiEnabled = true
        }

        val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter.isEnabled == false) {
            adapter.enable()
        }

        mReceiver = SesameBroadcastReceiver()
        mIntentFilter = IntentFilter()
        (mIntentFilter as IntentFilter).addAction("UPDATE_ACTION")
        registerReceiver(mReceiver, mIntentFilter)

        //(mReceiver as SesameBroadcastReceiver).registerHandler(updateHandler)
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(FLAG_KEYGUARD)
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(FLAG_KEYGUARD)
        //permission check
        if (ActivityCompat.checkSelfPermission(this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this@MainActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetoothの利用が許可されておりません。", Toast.LENGTH_LONG).show()
            return
        }

        //startService(Intent(this@MainActivity, SesameBeaconService::class.java))
        //stopService(Intent(this, SesameBeaconService::class.java))
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
        if (v == mStartButton) {
            Log.d("Button", "Start")
            startService(Intent(this, SesameBeaconService::class.java))
            animationStart()
        } else if (v == mStopButton) {
            Log.d("Button", "Stop")
            stopService(Intent(this, SesameBeaconService::class.java))
            animationEnd()
        } else if (v == mLockButton) {
            Log.d("Button", "Lock")
            animationStart()
        }
    }
}
