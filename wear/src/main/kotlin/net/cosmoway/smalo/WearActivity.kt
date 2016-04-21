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
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeApi
import com.google.android.gms.wearable.Wearable

class WearActivity : Activity(), MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    private val TAG = "ウェア"
    private var textView: TextView? = null

    private var googleApiClient: GoogleApiClient? = null
    private var button: Button? = null
    private var message: Int = 0
    private val wakeState = 0
    private val getState = 1
    private val stateUpdate = 2
    internal val unknown = 10
    internal val close = 11
    internal val open = 12
    private var doorState = unknown

    internal var shareText = "OK"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.round_activity_wear)

        textView = findViewById(R.id.text) as TextView

        button = findViewById(R.id.wearButton) as Button
        button!!.setOnClickListener(this)

        googleApiClient = GoogleApiClient.Builder(this).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build()

        //データ更新をするメソッドを呼ぶ
        sendDataByMessageApi(wakeState.toString())
    }

    override fun onResume() {
        super.onResume()
        googleApiClient!!.connect()
        sendDataByMessageApi(wakeState.toString())
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        if (googleApiClient != null && googleApiClient!!.isConnected) {
            Wearable.MessageApi.removeListener(googleApiClient, this)
            googleApiClient!!.disconnect()
            //Log.d(TAG, "onPause");
        }
    }

    override fun onConnected(bundle: Bundle?) {
        Log.d(TAG, "onConnected")
        Wearable.MessageApi.addListener(googleApiClient, this)
    }

    override fun onConnectionSuspended(i: Int) {
        Log.d(TAG, "onConnectionSuspended")
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.e(TAG, "onConnectionFailed: " + connectionResult)
    }

    override fun onClick(viewHolder: View) {
        if (viewHolder == button) {
            if (doorState == unknown) {
                Log.d(TAG, "鍵確認")
                //データを更新するメソッドを呼ぶ
                sendDataByMessageApi(getState.toString())
            } else if (doorState == close || doorState == open) {
                Log.d(TAG, "開閉要求")
                sendDataByMessageApi(stateUpdate.toString())
            }
        }
    }

    //データを更新
    private fun sendDataByMessageApi(message: String) {
        Thread(Runnable {
            val nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await()
            for (node in nodes.nodes) {
                Wearable.MessageApi.sendMessage(googleApiClient, node.id, "/data_comm2", message.toByteArray())
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
                message = Integer.parseInt(String(messageEvents.data))
                textView!!.text = "" + message
                doorState = message
                Log.d("" + message, "動いた")
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
