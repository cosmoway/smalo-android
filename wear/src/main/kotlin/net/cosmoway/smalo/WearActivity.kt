package net.cosmoway.smalo

import android.app.Activity
import android.os.Bundle
import android.support.wearable.view.WatchViewStub
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

class WearActivity : Activity(), MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    private val TAG = "ウェア"

    private var mApiClient: GoogleApiClient? = null
    private var mButton: Button? = null
    private var mLinearLayout: LinearLayout? = null
    private var mMessage: String? = null
    private val wakeState = 0
    private val getState = 1
    private val stateUpdate = 2
    internal val unknown = 10
    internal val close = 11
    internal val open = 12
    private var mState = "unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear)

        val stub = findViewById(R.id.watch_view_stub) as WatchViewStub
        stub.setOnLayoutInflatedListener { watchViewStub ->
            mLinearLayout = watchViewStub.findViewById(R.id.ll) as LinearLayout
            mButton = watchViewStub.findViewById(R.id.btn_wear) as Button
            mButton!!.setOnClickListener(this@WearActivity)
        }

        mApiClient = GoogleApiClient.Builder(this).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build()

        //データ更新をするメソッドを呼ぶ
        sendDataByMessageApi("getState")
    }

    override fun onResume() {
        super.onResume()
        mApiClient?.connect()
        //sendDataByMessageApi(getState.toString())
        sendDataByMessageApi("getState")
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        if (mApiClient != null && mApiClient!!.isConnected) {
            Wearable.MessageApi.removeListener(mApiClient, this)
            mApiClient!!.disconnect()
            //Log.d(TAG, "onPause");
        }
    }

    override fun onConnected(bundle: Bundle?) {
        Log.d(TAG, "onConnected")
        Wearable.MessageApi.addListener(mApiClient, this)
    }

    override fun onConnectionSuspended(i: Int) {
        Log.d(TAG, "onConnectionSuspended")
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.e(TAG, "onConnectionFailed: " + connectionResult)
    }

    override fun onClick(viewHolder: View) {
        if (viewHolder == mButton) {
            if (mState == "unknown") {
                Log.d(TAG, "サーチ中")
            } else if (mState.equals("unlocked") || mState.equals("locked")) {
                Log.d(TAG, "開閉要求")
                sendDataByMessageApi("stateUpdate")
                if (mState == "unlocked") {
                    mLinearLayout!!.setBackgroundResource(R.drawable.shape_yellow)
                } else if (mState == "locked") {
                    mLinearLayout!!.setBackgroundResource(R.drawable.shape_blue)
                }
            }
        }
    }

    //データを更新
    private fun sendDataByMessageApi(message: String) {
        Thread(Runnable {
            val nodes = Wearable.NodeApi.getConnectedNodes(mApiClient).await()
            for (node in nodes.nodes) {
                // メッセージをモバイルに渡す。
                Wearable.MessageApi
                        .sendMessage(mApiClient, node.id, "/data_comm2", message.toByteArray())
            }
        }).start()
        //        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/data_wear");
        //        putDataMapReq.getDataMap().putInt("key_wear", text);
        //        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        //        Wearable.DataApi.putDataItem(googleApiClient, putDataReq);
        //Log.d(TAG, "データ送信");
    }

    override fun onMessageReceived(messageEvents: MessageEvent) {
        Log.d(TAG, "レシーブ")
        if (messageEvents.path == "/data_comm") {
            Log.d(TAG, "パスOK")
            runOnUiThread {
                mMessage = String(messageEvents.data)
                mState = mMessage as String
                Log.d(mMessage, "動いた")
                if (mMessage == "close") {
                    mButton!!.setBackgroundResource(R.drawable.smalo_close_button)
                } else if (mMessage == "open") {
                    mButton!!.setBackgroundResource(R.drawable.smalo_open_button)
                }

                //ボタンの後ろの丸を消す
                mLinearLayout!!.setBackgroundResource(R.drawable.shape_clear)
            }
        }
    }

    //    @Override
    //    public void onDataChanged(DataEventBuffer dataEvents) {
    //        Log.d(TAG,"onDataChanged");
    //        for(DataEvent event : dataEvents){
    //            if(event.getType() == DataEvent.TYPE_CHANGED){
    //                Log.d(TAG,"onDataChanged2");
    //                DataItem item = event.getDataItem();
    //                if(item.getUri().getPath().equals("/data_handheld")){
    //                    final DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
    //                    message = dataMap.getInt("key_handheld");
    //                    textView.setText(""+message);
    //                    doorState = message;
    //                    Log.d(TAG,"動いた");
    //                }
    //            }else if(event.getType() == DataEvent.TYPE_DELETED){
    //
    //            }
    //        }
    //    }
}
