package net.cosmoway.smalo

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

class WearActivity : Activity(), MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    private val TAG = "Wear"
    private var mText: TextView? = null

    private var mApiClient: GoogleApiClient? = null
    private var mButton: Button? = null
    //private var message: Int = 0
    private var message: String? = "getState"
    private val getState: Int = 0
    private val stateUpdate: Int = 2
    internal val unknown: Int = 10
    internal val close: Int = 11
    internal val open: Int = 12
    //private var doorState = unknown
    private var mState: String? = "unknown"

    internal var shareText = "OK"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.round_activity_wear)

        mText = findViewById(R.id.text) as TextView

        mButton = findViewById(R.id.wearButton) as Button
        mButton?.setOnClickListener(this)

        mApiClient = GoogleApiClient
                .Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build()

        // データ更新をするメソッドを呼ぶ。
        //sendDataByMessageApi(getState.toString())
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
            if (mState.equals("unknown")) {
                Log.d(TAG, "不活性")
            } else if (mState.equals("unlocked") || mState.equals("locked")) {
                Log.d(TAG, "開閉要求")
                sendDataByMessageApi("stateUpdate".toString())
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

    // モバイルから値を受け取った時走る。
    override fun onMessageReceived(messageEvents: MessageEvent) {
        Log.d(TAG, "レシーブ")
        if (messageEvents.path.equals("/data_comm")) {
            Log.d(TAG, "パスOK")
            runOnUiThread {
                //message = Integer.parseInt(String(messageEvents.data))
                mState = String(messageEvents.data)
                /*textView?.text = "" + message
                doorState = message
                Log.d("" + message, "動いた")*/
                mText?.text = "" + mState
                Log.d(TAG, "動いた, $mState")
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
