package net.cosmoway.smalo

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.preference.PreferenceManager
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.NotificationCompat
import android.util.Log
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

// BeaconServiceクラス
class BleService : Service(), BluetoothAdapter.LeScanCallback {

    private var mStatus = BleStatus.DISCONNECTED
    private var mHandler: Handler? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothLeScanner: BluetoothLeScanner? = null
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mSp: SharedPreferences? = null;
    private var mScanCallback: ScanCallback? = null
    private var mReceivedMessage: String? = null

    // UUID設定用
    private var mId: String? = null
    // Wakelock
    private var mWakeLock: PowerManager.WakeLock? = null


    companion object {
        /**
         * BLE 機器スキャンタイムアウト (ミリ秒)
         */
        private val SCAN_PERIOD: Long = 10000
        /**
         * 検索対象機器の機器名
         */
        private val DEVICE_NAME = "smalo"
        /**
         * 検索対象機器のサービスUUID
         */
        private val SERVICE_UUID = "00002801-0000-1000-8000-00805f9b34fb"
        /**
         * 検索対象のキャラクタリスティックUUID
         */
        private val DEVICE_BUTTON_SENSOR_CHARACTERISTIC_UUID =
                //"00003333-0000-1000-8000-00805f9b34fb" // 書き込み用
                //"13333333-3333-3333-3333-333333330003" // 読み取り用
                "13333333-3333-3333-3333-333333330001" // 通知用
        /**
         * キャラクタリスティック設定UUID
         */
        private val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"

        private val TAG = "BLESample"
    }

    // 暗号化メソッド
    private fun toEncryptedHashValue(algorithmName: String, value: String): String {
        var md: MessageDigest? = null
        try {
            md = MessageDigest.getInstance(algorithmName)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

        val sb: StringBuilder = StringBuilder()
        md?.update(value.toByteArray())
        for (b in md!!.digest()) {
            val hex = String.format("%02x", b)
            sb.append(hex)
        }
        return sb.toString()
    }

    // ノーティフィケーションを生成するメソッド。11020で仕様に応じて修正する。
    private fun makeNotification(title: String) {

        val builder = NotificationCompat.Builder(applicationContext)
        builder.setSmallIcon(R.mipmap.ic_launcher)

        // ノーティフィケションを生成した時のインテントを作成する
        val notificationIntent = Intent(this@BleService, Notification::class.java)
        val contentIntent = PendingIntent.getActivity(this@BleService, 0,
                notificationIntent, 0)

        builder.setContentTitle(title.toString()) // 1行目
        if (title == "Open") {
            builder.setContentText("解錠されました。")
        } else if (title == "Close") {
            builder.setContentText("施錠されました。")
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
        builder.setTicker("スマロ") // 通知到着時に通知バーに表示(4.4まで)
        // 5.0からは表示されない

        val manager = NotificationManagerCompat.from(applicationContext)
        manager.notify(1, builder.build())

    }

    // 表示する値をアクティビティに投げる
    private fun sendBroadCastToMainActivity(state: Array<String>) {
        Log.d(TAG, "sendBroadCastToMainActivity")
        val broadcastIntent: Intent = Intent()
        broadcastIntent.putExtra("state", state)
        broadcastIntent.action = "UPDATE_ACTION"
        baseContext.sendBroadcast(broadcastIntent)
    }

    private fun sendBroadCastToWidget(message: String) {
        Log.d(TAG, "created")
        val broadcastIntent: Intent = Intent()
        broadcastIntent.putExtra("message", message)
        broadcastIntent.action = "UPDATE_WIDGET"
        baseContext.sendBroadcast(broadcastIntent)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "created")
        mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager?.adapter
        // 受け取ったメッセージの箱
        mSp = PreferenceManager.getDefaultSharedPreferences(this@BleService)
        // 受け取ったメッセージ読出
        mReceivedMessage = mSp?.getString("SaveMessage", null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBluetoothLeScanner = mBluetoothAdapter?.bluetoothLeScanner
            mScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (DEVICE_NAME == result.device.name) {
                        setStatus(BleStatus.DEVICE_FOUND)
                        // 省電力のためスキャンを停止する
                        stopScanByBleScanner()
                        // スキャン中に見つかったデバイスに接続を試みる.
                        // 第三引数には接続後に呼ばれるBluetoothGattCallbackを指定する.
                        result.device.connectGatt(applicationContext, false, mBluetoothGattCallback)
                        val msg = "name =" + result.device.name +
                                ", bondState = " + result.device.bondState +
                                ", address = " + result.device.address +
                                ", type" + result.device.type +
                                ", uuid = " + Arrays.toString(result.device.uuids)
                        Log.d("Scan", msg)

                        val list: Array<String> = arrayOf(result.device.name.toString(),
                                result.device.bondState.toString(), result.device.address.toString(),
                                result.device.type.toString(), Arrays.toString(result.device.uuids))
                        sendBroadCastToMainActivity(list)
                    }
                }

                override fun onBatchScanResults(results: List<ScanResult>) {
                    super.onBatchScanResults(results)
                }

                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                }
            }
        }

        // 端末固有識別番号の箱
        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        // 端末固有識別番号読出
        mId = sp.getString("SaveString", null)
        if (mId == null) {
            Log.d("id", "null")
            // 端末固有識別番号取得
            mId = UUID.randomUUID().toString()
            // 端末固有識別番号記憶
            sp.edit().putString("SaveString", mId).apply()
        }
        Log.d("id", mId)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag")
        mWakeLock?.acquire()

        connect()
        mHandler = Handler()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "startCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "destroy")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopScanByBleScanner()
        } else {
            mBluetoothAdapter?.stopLeScan(this)
        }

        disconnect()
        mWakeLock?.release()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * BLE機器を検索する
     */
    private fun connect() {
        mHandler?.postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                stopScanByBleScanner()
            } else {
                mBluetoothAdapter?.stopLeScan(this)
            }
            if (BleStatus.SCANNING == mStatus) {
                setStatus(BleStatus.SCAN_FAILED)
            }
        }, SCAN_PERIOD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopScanByBleScanner()
            startScanByBleScanner()
        } else {
            mBluetoothAdapter!!.stopLeScan(this)
            mBluetoothAdapter!!.startLeScan(this)
        }
        setStatus(BleStatus.SCANNING)
    }


    /**
     * BLE 機器との接続を解除する
     */
    private fun disconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopScanByBleScanner()
        } else {
            mBluetoothAdapter?.stopLeScan(this)
        }
        if (mBluetoothGatt != null) {
            mBluetoothGatt!!.close()
            mBluetoothGatt = null
            setStatus(BleStatus.CLOSED)
        }
    }

    private fun startScanByBleScanner() {
        mBluetoothLeScanner = mBluetoothAdapter?.bluetoothLeScanner
        // デバイスの検出.
        mBluetoothLeScanner?.startScan(mScanCallback)
    }

    private fun stopScanByBleScanner() {
        // デバイスの検出.
        mBluetoothLeScanner?.stopScan(mScanCallback)
    }

    override fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray) {
        Log.d(TAG, "device found:" + device.name)
        if (DEVICE_NAME == device.name) {
            setStatus(BleStatus.DEVICE_FOUND)
            // 省電力のためスキャンを停止する
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                stopScanByBleScanner()
            } else {
                mBluetoothAdapter?.stopLeScan(this)
            }
            // GATT接続を試みる
            mBluetoothGatt = device.connectGatt(this, false, mBluetoothGattCallback)
            val msg = "name =" + device.name + ", bondState = " + device.bondState +
                    ", address = " + device.address + ", type" + device.type +
                    ", uuid = " + Arrays.toString(device.uuids)
            Log.d("Scan", msg)
            val list: Array<String> = arrayOf(device.name.toString(), device.bondState.toString(),
                    device.address.toString(), device.type.toString(), Arrays.toString(device.uuids))
            sendBroadCastToMainActivity(list)
        }
    }

    private val mBluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange:$status->$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // GATTへ接続成功
                // サービスを検索する
                Log.d(TAG, "connection: success.")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setStatus(BleStatus.DISCONNECTED)
                // GATT通信から切断された
                Log.d(TAG, "connection: disconnected.")
                mBluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered received:" + status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID.fromString(SERVICE_UUID))
                //val service = gatt.getService(null)
                if (service == null) {
                    // サービスが見つからなかった
                    setStatus(BleStatus.SERVICE_NOT_FOUND)
                    Log.d(TAG, "onServicesDiscovered service:Not Found.")
                } else {
                    // サービスを見つけた
                    setStatus(BleStatus.SERVICE_FOUND)
                    val characteristic: BluetoothGattCharacteristic
                            = service.getCharacteristic(UUID
                            .fromString(DEVICE_BUTTON_SENSOR_CHARACTERISTIC_UUID))
                    Log.d(TAG, "onServicesDiscovered characteristic:" + characteristic)

                    if (characteristic == null) {
                        // キャラクタリスティックが見つからなかった
                        setStatus(BleStatus.CHARACTERISTIC_NOT_FOUND)
                        Log.d(TAG, "onServicesDiscovered characteristic:null")
                    } else {
                        // キャラクタリスティックを見つけた
                        Log.d(TAG, "onServicesDiscovered characteristic:" + characteristic.uuid)
                        // 値書き込み
                        /*var value: String
                        if (mReceivedMessage == "Open") {
                            value = "Close"
                        } else if (mReceivedMessage == "Close") {
                            value = "Open"
                        } else {
                            value = "Unknown"
                        }
                        characteristic.setValue(value.toString())
                        Log.d(TAG, value.toString())
                        gatt.writeCharacteristic(characteristic)*/

                        // 値読み取り
                        // gatt.readCharacteristic(characteristic)

                        // 通知
                        // Notification を要求する
                        val registered = gatt.setCharacteristicNotification(characteristic, true)

                        // Characteristic の Notification 有効化
                        val descriptor: BluetoothGattDescriptor = characteristic.getDescriptor(UUID
                                .fromString(CLIENT_CHARACTERISTIC_CONFIG))
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)

                        if (registered) {
                            // Characteristics通知設定完了
                            Log.d(TAG, "Notification success")
                            setStatus(BleStatus.NOTIFICATION_REGISTERED)
                        } else {
                            Log.d(TAG, "Notification failed")
                            setStatus(BleStatus.NOTIFICATION_REGISTER_FAILED)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt,
                                          characteristic: BluetoothGattCharacteristic,
                                          status: Int) {
            Log.d(TAG, "GATTStatus:" + status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onCharacteristicRead: " + characteristic.getStringValue(0))
                // 受け取ったメッセージ取得
                mReceivedMessage = characteristic.getStringValue(0)
                // 受け取ったメッセージ記憶
                mSp?.edit()?.putString("SaveMessage", mReceivedMessage)?.apply()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt,
                                             characteristic: BluetoothGattCharacteristic) {
            Log.d(TAG, "onCharacteristicChanged")
            Log.d(TAG, "onCharacteristicRead: " + characteristic.getStringValue(0))
            makeNotification(characteristic.getStringValue(0))
        }
    }

    private fun setStatus(status: BleStatus) {
        mStatus = status
    }

    private enum class BleStatus {
        DISCONNECTED, SCANNING, SCAN_FAILED, DEVICE_FOUND, SERVICE_NOT_FOUND, SERVICE_FOUND,
        CHARACTERISTIC_NOT_FOUND, NOTIFICATION_REGISTERED, NOTIFICATION_REGISTER_FAILED, CLOSED;

    }
}