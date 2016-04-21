package com.example.susaki.dataapisample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

public class HandheldActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks , GoogleApiClient.OnConnectionFailedListener , OnClickListener{

    GoogleApiClient googleApiClient;
    TextView textView;
    private String text;
    private Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("onCreate","実行");
        this.setContentView(R.layout.activity_handheld);

        textView = (TextView)findViewById(R.id.text);
        button = (Button)findViewById(R.id.handheldButton);
        button.setOnClickListener(this);

        googleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onResume(){
        super.onResume();
        googleApiClient.connect();
    }

    @Override
    protected void onPause(){
        super.onPause();
        if(googleApiClient != null && googleApiClient.isConnected()){
            googleApiClient.disconnect();
        }
    }

    //検証用ボタン　あとで消す
    @Override
    public void onClick(View v){
        Log.d("アクティビティ","ボタン");
        if (v.equals(button)){
            Log.d("アクティビティ","ボタン押した");
            sendDataByMessageApi("11");
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d("TAG", "onConnected");
        sendDataByMessageApi("11");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("TAG", "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e("TAG", "onConnectionFailed: " + connectionResult);
    }
    //データを更新
    void sendDataByMessageApi(final String message) {
        Log.d("アクティビティ","メッセージ送信");
        new Thread(new Runnable(){
            @Override
            public void run(){
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                for(Node node : nodes.getNodes()){
                    Wearable.MessageApi.sendMessage(googleApiClient , node.getId() , "/data_comm" , message.getBytes());
                }
            }
        }).start();
//        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/data_wear");
//        putDataMapReq.getDataMap().putInt("key_wear", text);
//        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
//        Wearable.DataApi.putDataItem(googleApiClient, putDataReq);
        //Log.d(TAG, "データ送信");
    }
}
