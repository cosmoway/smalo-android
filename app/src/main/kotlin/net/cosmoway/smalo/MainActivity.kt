package net.cosmoway.smalo

import android.Manifest
import android.app.ListActivity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ListView


class MainActivity : ListActivity() {

    //スリープモードからの復帰の為のフラグ定数
    private val FLAG_KEYGUARD = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
    private var mReceiver: SesameBroadcastReceiver? = null
    private var mIntentFilter: IntentFilter? = null

    // サービスから値を受け取ったら動かしたい内容を書く
    private val updateHandler = object : Handler() {
        override fun handleMessage(msg: Message) {

            val bundle = msg.data
            val message: Array<String> = bundle.getStringArray("state")
            val lv: ListView = findViewById(R.id.list1) as ListView
            val adapter: ArrayAdapter<String> = ArrayAdapter(this@MainActivity,
                    android.R.layout.simple_list_item_1, message)
            listAdapter = adapter
            lv.adapter
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE未対応端末です", Toast.LENGTH_SHORT).show();
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

        (mReceiver as SesameBroadcastReceiver).registerHandler(updateHandler)
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

        startService(Intent(this, SesameBeaconService::class.java))
        //stopService(Intent(this, SesameBeaconService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mReceiver)
    }
}
