package net.cosmoway.smalo

import android.app.Activity
import android.os.Bundle
import android.support.wearable.view.WatchViewStub
import android.util.Log
import android.view.View
import android.widget.ImageButton
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

class WearActivity : Activity(), MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    private val TAG = "WearActivity"

    private var mApiClient: GoogleApiClient? = null
    private var mButton: ImageButton? = null
    private var mMessage: String? = null
    private var mState = "unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear)

        val stub = findViewById(R.id.watch_view_stub) as WatchViewStub
        stub.setOnLayoutInflatedListener { watchViewStub ->
            mButton = watchViewStub.findViewById(R.id.btn_wear) as ImageButton
            mButton?.setOnClickListener(this)
        }

        mApiClient = GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build()

        //データ更新をするメソッドを呼ぶ
        sendDataByMessageApi("wakeState")
    }

    override fun onResume() {
        super.onResume()
        mApiClient?.connect()
        sendDataByMessageApi("getState")
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        if (mApiClient != null && mApiClient?.isConnected as Boolean) {
            Wearable.MessageApi.removeListener(mApiClient, this)
            mApiClient?.disconnect()
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
                Log.d(TAG, "onSearch")
            } else if (mState.equals("unlocked") || mState.equals("locked")) {
                Log.d(TAG, "onRequire")
                sendDataByMessageApi("stateUpdate")
                if (mState.equals("unlocked")) {
                    mButton?.setBackgroundResource(R.drawable.shape_yellow)
                } else if (mState.equals("locked")) {
                    mButton?.setBackgroundResource(R.drawable.shape_blue)
                }
            }
        }
    }

    //データを更新
    private fun sendDataByMessageApi(message: String) {
        Thread(Runnable {
            val nodes = Wearable.NodeApi.getConnectedNodes(mApiClient).await()
            for (node in nodes.nodes) {
                Wearable.MessageApi.sendMessage(mApiClient, node.id, "/data_comm2", message.toByteArray())
            }
        }).start()
    }

    override fun onMessageReceived(messageEvents: MessageEvent) {
        if (messageEvents.path == "/data_comm") {
            runOnUiThread {
                mMessage = String(messageEvents.data)
                mState = mMessage as String
                if (mMessage.equals("locked")) {
                    mButton?.isClickable = true
                    mButton?.setImageResource(R.drawable.smalo_close_button)
                } else if (mMessage.equals("unlocked")) {
                    mButton?.isClickable = true
                    mButton?.setImageResource(R.drawable.smalo_open_button)
                } else if (mMessage.equals("unknown")) {
                    mButton?.isClickable = false
                    mButton?.setImageResource(R.drawable.smalo_search_icon)
                }
                //ボタンの後ろの丸を消す
                mButton?.setBackgroundResource(R.drawable.shape_clear)
            }
        }
    }
}
