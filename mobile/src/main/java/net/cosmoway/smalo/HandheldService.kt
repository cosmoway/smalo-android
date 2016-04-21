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

    internal var message: Int = 0
    internal val wakeState = 0
    internal val getState = 1
    internal val stateUpdate = 2
    internal val unknown = 10
    internal val close = 11
    internal val open = 12
    //TODO 動作確認のために初期設定close 実装時はunknownにする
    internal var doorState: Int = 0

    override fun onCreate() {
        super.onCreate()
        Log.d("スマホサービス", "onCreate")

        doorState = close
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
        //        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/data_handheld");
        //        putDataMapReq.getDataMap().putInt("key_handheld", text);
        //        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        //        Wearable.DataApi.putDataItem(googleApiClient, putDataReq);
        //        Log.d("スマホサービス", "データ送信");
    }

    override fun onMessageReceived(messageEvents: MessageEvent?) {
        if (messageEvents!!.path == "/data_comm2") {
            message = Integer.parseInt(String(messageEvents.data))
            Log.d("" + message, "受け取ったメッセージ")

            //取得した内容によって処理
            if (message == getState || message == wakeState) {
                //TODO 鍵の情報の取得　wearに状態を表示させるための処理
                //TODO doorStateに結果を代入
                Log.d("データ", "送信")
                sendDataByMessageApi(doorState.toString())
            } else if (message == stateUpdate) {
                if (doorState != unknown) {
                    //TODO 解錠施錠要求の送信　処理結果をwearに返す
                    //TODO doorStateに結果を代入
                    //                    if(doorState == close){
                    //                        Log.d("鍵","あけた");
                    //                        doorState = open;
                    //                    }else if(doorState == open){
                    //                        Log.d("鍵","しめた");
                    //                        doorState = close;
                    //                    }
                    sendDataByMessageApi(doorState.toString())
                }
            } else {
                Log.d("要求", "通ってない")
            }
        }
    }
    //
    //    @Override
    //    public void onDataChanged(DataEventBuffer dataEvents){
    //
    //        Log.d("スマホサービス","onDataChanged");
    //        for(DataEvent event : dataEvents){
    //            if(event.getType() == DataEvent.TYPE_CHANGED){
    //                DataItem item = event.getDataItem();
    //                if(item.getUri().getPath().equals("/data_wear")){
    //                    final DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
    //                    message = dataMap.getInt("key_wear");
    //                    //Toast.makeText(this,text,Toast.LENGTH_SHORT).show();
    //                    Log.d(""+message,"受け取ったメッセージ");
    //
    //                    //取得した内容によって処理
    //                    if(message == getState || message == wakeState){
    //                        //TODO 鍵の情報の取得
    //                        Log.d("データ","送信");
    //                        sendDataByDataApi(doorState);
    //                    }else if(message == stateUpdate){
    //                        Log.d("解錠処理","送信");
    //                        if(doorState != unknown) {
    //                            //TODO 解錠施錠要求の送信
    //                            if(doorState == close){
    //                                doorState = open;
    //                            }else if(doorState == open){
    //                                doorState = close;
    //                            }
    //                            sendDataByDataApi(doorState);
    //                        }
    //                    }else{
    //                        Log.d("要求","通ってない");
    //                    }
    //                }
    //            }else if(event.getType() == DataEvent.TYPE_DELETED){
    //
    //            }
    //        }
    //    }

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
