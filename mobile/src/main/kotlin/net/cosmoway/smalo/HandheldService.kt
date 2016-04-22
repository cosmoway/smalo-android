package net.cosmoway.smalo

import android.os.Bundle
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService


/**

 * Created by susaki on 16/04/18.
 */
class HandheldService : WearableListenerService(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private var googleApiClient: GoogleApiClient? = null

    private var mMessage: String? = null
    internal val wakeState = 0
    internal val getState = 1
    internal val stateUpdate = 2
    internal val unknown = 10
    internal val close = 11
    internal val open = 12
    //TODO 動作確認のために初期設定close 実装時はunknownにする
    private var mState: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("スマホサービス", "onCreate")

        mState = "open"
        Log.d("ステート", "初期化")

        googleApiClient = GoogleApiClient.Builder(this).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build()
        googleApiClient!!.connect()
    }

    //データを更新
    private fun sendDataByMessageApi(message: String) {

        Log.d("サービス", "動いた")
        Thread(Runnable {
            Log.d("サービス", "ラン")
            val nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await()
            for (node in nodes.nodes) {
                Log.d("サービス", "フォー")
                Wearable.MessageApi.sendMessage(googleApiClient, node.id, "/data_comm", message.toByteArray())
            }
        }).start()
    }

    override fun onMessageReceived(messageEvents: MessageEvent?) {
        if (messageEvents!!.path == "/data_comm2") {
            mMessage = String(messageEvents.data)
            Log.d(mMessage, "受け取ったメッセージ")

            //取得した内容によって処理
            if (mMessage == "getState" || mMessage == "wakeState") {
                //TODO 鍵の情報の取得　wearに状態を表示させるための処理
                //TODO doorStateに結果を代入
                Log.d("データ", "送信")
                sendDataByMessageApi(mState as String)
            } else if (mMessage == "stateUpdate") {
                if (mState == "open" || mState == "close") {
                    //TODO 解錠施錠要求の送信　処理結果をwearに返す
                    //TODO doorStateに結果を代入
                    sendDataByMessageApi(mState as String)
                }
            } else {
                Log.d("要求", "通ってない")
            }
        }
    }

    override fun onConnected(bundle: Bundle?) {

        Log.d("onConnected", "実行")
    }

    override fun onConnectionSuspended(i: Int) {
        Log.d("Suspended", "実行")
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.d("Failed", "実行")
    }
}
