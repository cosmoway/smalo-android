package com.example.susaki.dataapisample;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;


/**
 * Created by susaki on 16/04/18.
 */
public class HandheldService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient googleApiClient = null;

    int message;
    final int wakeState = 0 , getState = 1 , stateUpdate = 2;
    final int unknown = 10 , close = 11 ,open = 12;
    //TODO 動作確認のために初期設定close 実装時はunknownにする
    int doorState;

    @Override
    public void onCreate(){
        super.onCreate();
        Log.d("スマホサービス","onCreate");

        doorState = close;
        Log.d("ステート","初期化");

        googleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();
    }
    //データを更新
    private void sendDataByMessageApi(final String message) {

        Log.d("サービス","動いた");
        new Thread(new Runnable(){
            @Override
            public void run(){
                Log.d("サービス","ラン");
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                for(Node node : nodes.getNodes()){
                    Log.d("サービス","フォー");
                    Wearable.MessageApi.sendMessage(googleApiClient , node.getId(),"/data_comm",message.getBytes());
                }
            }
        }).start();
//        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/data_handheld");
//        putDataMapReq.getDataMap().putInt("key_handheld", text);
//        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
//        Wearable.DataApi.putDataItem(googleApiClient, putDataReq);
//        Log.d("スマホサービス", "データ送信");
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvents){
        if(messageEvents.getPath().equals("/data_comm2")){
            message = Integer.parseInt(new String(messageEvents.getData()));
            Log.d(""+message,"受け取ったメッセージ");

            //取得した内容によって処理
            if(message == getState || message == wakeState){
                //TODO 鍵の情報の取得
                Log.d("データ","送信");
                sendDataByMessageApi(String.valueOf(doorState));
            }else if(message == stateUpdate){
                if(doorState != unknown) {
                    //TODO 解錠施錠要求の送信
                    if(doorState == close){
                        Log.d("鍵","あけた");
                        doorState = open;
                    }else if(doorState == open){
                        Log.d("鍵","しめた");
                        doorState = close;
                    }
                    sendDataByMessageApi(String.valueOf(doorState));
                }
            }else{
                Log.d("要求","通ってない");
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

    @Override
    public void onConnected(Bundle bundle) {

        Log.d("onConnected","実行");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("Suspended","実行");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d("Failed","実行");
    }
}
