package net.cosmoway.smalo

import android.Manifest
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast


class MobileActivity : Activity(), View.OnClickListener {

    interface Callback {
        fun onConnecting()

        fun onUnLocking()

        fun onLocking()
    }

    private var mReceiver: MyBroadcastReceiver? = null
    private var mCallback: Callback? = null
    private var mState: String? = null
    private var mIntentFilter: IntentFilter? = null
    private var mLockButton: ImageButton? = null
    private var mBackground: LinearLayout? = null
    private var mAnimatorSet1: AnimatorSet? = null
    private var mAnimatorSet2: AnimatorSet? = null
    private var mAnimatorSet3: AnimatorSet? = null
    private var mAnimatorSet4: AnimatorSet? = null
    private var mAnimatorSet5: AnimatorSet? = null
    private var mAnimatorSet6: AnimatorSet? = null
    private var mOval1: ImageView? = null
    private var mOval2: ImageView? = null
    private var mOval3: ImageView? = null
    private var mOval4: ImageView? = null
    private var mOval5: ImageView? = null

    companion object {
        private val REQUEST_PERMISSION = 1
        private val TAG = "MainActivity"
        private val MY_APP_NAME = "SMALO"
        private val PREFERENCE_INIT = 0;
        private val PREFERENCE_BOOTED = 1;
    }

    fun setCallback(callback: Callback) {
        mCallback = callback
    }

    private fun animationStart() {
        Log.i(TAG, "animStart")
        mAnimatorSet1?.start()
        mAnimatorSet2?.start()
        mAnimatorSet3?.start()
        mAnimatorSet4?.start()
        mAnimatorSet5?.start()
    }

    private fun buttonAnimationStart() {
        Log.i(TAG, "animStart")
        mAnimatorSet6?.start()
    }

    private fun animationEnd() {
        Log.i(TAG, "animEnd")
        mAnimatorSet1?.end()
        mAnimatorSet2?.end()
        mAnimatorSet3?.end()
        mAnimatorSet4?.end()
        mAnimatorSet5?.end()
    }

    private fun setState(state: Int) {
        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sp.edit().putInt("InitState", state).apply();
        Log.i(TAG, state.toString());
    }

    private fun setId(uuid: String) {
        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sp.edit().putString("uuid", uuid).apply();
        Log.i(TAG, uuid);
    }

    //データ読み出し
    private fun getState(): Int {
        // 読み込み
        val state: Int;
        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        state = sp.getInt("InitState", PREFERENCE_INIT);
        //ログ表示
        Log.i(TAG, "state:$state");
        return state;
    }

    //データ読み出し
    private fun getId(): String {
        // 読み込み
        val uuid: String;
        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        uuid = sp.getString("uuid", null);
        //ログ表示
        Log.i(TAG, "UUID:$uuid");
        return uuid;
    }

    //TODO: サービスからブロードキャストされ、値を受け取った時に動かしたい内容を書く。
    private val updateHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            val bundle = msg.data
            mState = bundle.getString("state")
            if (mState.equals("locked")) {
                makeNotification("施錠されました。")
                Log.i(TAG, "message:L")
                setColor(R.drawable.bg_grad_main, R.drawable.oval)
                mLockButton?.isClickable = true
                animationEnd()
                mLockButton?.setImageResource(R.drawable.smalo_close_button)
                mLockButton?.isEnabled = true
            } else if (mState.equals("unlocked")) {
                makeNotification("解錠されました。")
                Log.i(TAG, "message:UL")
                setColor(R.drawable.bg_grad_unlocked, R.drawable.oval_unlocked)
                mLockButton?.isClickable = true
                animationEnd()
                mLockButton?.setImageResource(R.drawable.smalo_open_button)
                mLockButton?.isEnabled = true
            } else if (mState.equals("unknown")) {
                Log.i(TAG, "message:UK")
                setColor(R.drawable.bg_grad_main, R.drawable.oval)
                mLockButton?.isClickable = false
                animationEnd()
                mLockButton?.setImageResource(R.drawable.smalo_search_icon)
                mLockButton?.isEnabled = false
                animationStart()
            }
        }
    }

    private fun setColor(bg: Int, oval: Int) {
        mBackground?.setBackgroundResource(bg)
        mOval1?.setImageResource(oval)
        mOval2?.setImageResource(oval)
        mOval3?.setImageResource(oval)
        mOval4?.setImageResource(oval)
        mOval5?.setImageResource(oval)
    }


    private fun makeNotification(message: String) {
        val builder = NotificationCompat.Builder(applicationContext)
        builder.setSmallIcon(R.mipmap.smalo_icon)

        // ノーティフィケションを生成した時のインテントを作成する
        val notificationIntent = Intent(this, Notification::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0)

        builder.setContentTitle(MY_APP_NAME) // 1行目
        builder.setContentText(message)
        builder.setContentIntent(contentIntent)
        builder.setTicker(MY_APP_NAME) // 通知到着時に通知バーに表示(4.4まで)
        // 5.0からは表示されない

        val manager = NotificationManagerCompat.from(applicationContext)
        manager.notify(1, builder.build())
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
                        .setMessage(MY_APP_NAME + "が正常に動作する為には、電池の最適化の解除が必要です。\n" +
                                "OKボタンを押すと、アプリ設定画面に移動します。\n" +
                                "最適化を解除する場合、「バッテリー」をタップし、設定を変更して下さい。" +
                                "なお、最適化状態時は、" + MY_APP_NAME + "の動作に影響が発生します。")
                        .setPositiveButton("OK") { dialog, which ->
                            val intent: Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri: Uri = Uri.fromParts("package", getPackageName(), null)
                            intent.data = uri
                            startActivity(intent)
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
                            + MY_APP_NAME + "に対する記憶装置へのアクセス許可発行が必要です。")
                    .setPositiveButton("OK") { dialog, which ->
                        ActivityCompat.requestPermissions(this,
                                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                    }.show()
        }
    }

    private fun findViews() {
        mBackground = findViewById(R.id.layout_mobile) as LinearLayout
        mOval1 = findViewById(R.id.oval1) as ImageView
        mOval2 = findViewById(R.id.oval2) as ImageView
        mOval3 = findViewById(R.id.oval3) as ImageView
        mOval4 = findViewById(R.id.oval4) as ImageView
        mOval5 = findViewById(R.id.oval5) as ImageView
        mLockButton = findViewById(R.id.btn_lock) as ImageButton
        mLockButton?.isEnabled = false
    }

    private fun setAnimators() {
        mAnimatorSet1 = AnimatorInflater.loadAnimator(this, R.animator.anim_search) as AnimatorSet;
        mAnimatorSet2 = AnimatorInflater.loadAnimator(this, R.animator.anim_search) as AnimatorSet;
        mAnimatorSet3 = AnimatorInflater.loadAnimator(this, R.animator.anim_search) as AnimatorSet;
        mAnimatorSet4 = AnimatorInflater.loadAnimator(this, R.animator.anim_search) as AnimatorSet;
        mAnimatorSet5 = AnimatorInflater.loadAnimator(this, R.animator.anim_search) as AnimatorSet;
        mAnimatorSet6 = AnimatorInflater.loadAnimator(this, R.animator.anim_ontap_button) as AnimatorSet;
        (mAnimatorSet1 as AnimatorSet).setTarget(mOval1);
        (mAnimatorSet2 as AnimatorSet).setTarget(mOval2);
        (mAnimatorSet3 as AnimatorSet).setTarget(mOval3);
        (mAnimatorSet4 as AnimatorSet).setTarget(mOval4);
        (mAnimatorSet5 as AnimatorSet).setTarget(mOval5);
        (mAnimatorSet6 as AnimatorSet).setTarget(mOval1);
        (mAnimatorSet1 as AnimatorSet).startDelay = 0;
        (mAnimatorSet2 as AnimatorSet).startDelay = 800;
        (mAnimatorSet3 as AnimatorSet).startDelay = 1600;
        (mAnimatorSet4 as AnimatorSet).startDelay = 2400;
        (mAnimatorSet5 as AnimatorSet).startDelay = 3200;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "この端末は、" + MY_APP_NAME + "に対応しておりません。",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        mReceiver = MyBroadcastReceiver()
        mIntentFilter = IntentFilter()
        (mIntentFilter as IntentFilter).addAction("UPDATE_ACTION")
        registerReceiver(mReceiver, mIntentFilter)
        mReceiver?.registerHandler(updateHandler)
        Log.i(TAG, "Created")
        val state: Int? = intent?.getIntExtra("bootState", 0)
        if (state == PREFERENCE_BOOTED) {
            setState(state)
        }
        Log.i(TAG, "State:${getState()}")
        if (getState() == PREFERENCE_BOOTED) {
            setContentView(R.layout.activity_mobile)
            mState = "unknown"
            findViews()
            mLockButton?.setOnClickListener(this)
            setAnimators()
            animationStart()

            requestLocationPermission()
            requestAccessStoragePermission()
            requestBatteryPermission()

            val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter.isEnabled == false) {
                adapter.enable()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "Stopped")
        if (getState() == PREFERENCE_BOOTED) {
            val intent: Intent = Intent(this, MyService::class.java)
            intent.putExtra("extra", "stop")
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "Resumed")
        Log.i(TAG, getState().toString())
        when (getState()) {
            PREFERENCE_INIT -> {
                val intent: Intent = Intent(this, RegisterActivity::class.java)
                startActivity(intent)
                finish()
            }
            PREFERENCE_BOOTED -> {
                if (intent.getStringExtra("uuid") != null) {
                    val uuid: String = intent.getStringExtra("uuid")
                    if (!uuid.isNullOrEmpty()) {
                        setId(uuid)
                    }
                }
                val intent: Intent = Intent(this, MyService::class.java)
                intent.putExtra("extra", "start")
                if (!getId().isNullOrEmpty()) {
                    intent.putExtra("uuid", getId())
                }
                startService(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Destroyed")
        unregisterReceiver(mReceiver)
        mAnimatorSet1?.end()
        mAnimatorSet1 = null
        mAnimatorSet2?.end()
        mAnimatorSet2 = null
        mAnimatorSet3?.end()
        mAnimatorSet3 = null
        mAnimatorSet4?.end()
        mAnimatorSet4 = null
        mAnimatorSet5?.end()
        mAnimatorSet5 = null
        mAnimatorSet6?.end()
        mAnimatorSet6 = null
    }

    override fun onClick(v: View?) {
        if (v == mLockButton) {
            if (mState != null) {
                animationEnd()
                Log.i(TAG, mCallback.toString())
                if (mState.equals("locked")) {
                    val intent: Intent = Intent(this, MyService::class.java)
                    intent.putExtra("extra", "unlock")
                    startService(intent)
                } else if (mState.equals("unlocked")) {
                    val intent: Intent = Intent(this, MyService::class.java)
                    intent.putExtra("extra", "lock")
                    startService(intent)
                }
            }
            buttonAnimationStart()
        }
    }
}