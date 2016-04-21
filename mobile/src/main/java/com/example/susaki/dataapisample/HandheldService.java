package com.example.susaki.dataapisample;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;


/**
 * Created by susaki on 16/04/18.
 */
public class HandheldService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient googleApiClient = null;

    String message;
    final int wakeState = 0 , getState = 1 , stateUpdate = 2;
    final int unknown = 10 , close = 11 ,open = 12;
    //TODO 動作確認のために初期設定close 実装時はunknownにする
    String doorState;

    @Override
    public void onCreate(){
        super.onCreate();
        Log.d("スマホサービス","onCreate");

        doorState = "open";
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
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvents){
        if(messageEvents.getPath().equals("/data_comm2")){
            message = new String(messageEvents.getData());
            Log.d(message,"受け取ったメッセージ");

            //取得した内容によって処理
            if(message.equals("getState") || message.equals("wakeState")){
                //TODO 鍵の情報の取得　wearに状態を表示させるための処理
                //TODO doorStateに結果を代入
                Log.d("データ","送信");
                sendDataByMessageApi(doorState);
            }else if(message.equals("stateUpdate")){
                if(doorState.equals("open") || doorState.equals("close")) {
                    //TODO 解錠施錠要求の送信　処理結果をwearに返す
                    //TODO doorStateに結果を代入
                    sendDataByMessageApi(doorState);
                }
            }else{
                Log.d("要求","通ってない");
            }
        }
    }

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
