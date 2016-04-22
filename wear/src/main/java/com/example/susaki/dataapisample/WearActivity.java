package com.example.susaki.dataapisample;

import android.app.Activity;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

public class WearActivity extends Activity implements MessageApi.MessageListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        View.OnClickListener {

    private String TAG = "ウェア";

    private GoogleApiClient googleApiClient = null;
    private Button button;
    private LinearLayout linearLayout;
    private String message;
    private final int wakeState = 0, getState = 1, stateUpdate = 2;
    final int unknown = 10, close = 11, open = 12;
    private String doorState = "unknown";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub watchViewStub) {
                linearLayout = (LinearLayout) watchViewStub.findViewById(R.id.ll);
                button = (Button) watchViewStub.findViewById(R.id.btn_wear);
                button.setOnClickListener(WearActivity.this);
            }
        });

        googleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        //データ更新をするメソッドを呼ぶ
        sendDataByMessageApi("wakeState");
    }

    @Override
    protected void onResume() {
        super.onResume();
        googleApiClient.connect();
        sendDataByMessageApi(String.valueOf(wakeState));
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (googleApiClient != null && googleApiClient.isConnected()) {
            Wearable.MessageApi.removeListener(googleApiClient, this);
            googleApiClient.disconnect();
            //Log.d(TAG, "onPause");
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
        Wearable.MessageApi.addListener(googleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed: " + connectionResult);
    }

    @Override
    public void onClick(View viewHolder) {
        if (viewHolder.equals(button)) {
            if (doorState.equals("unknown")) {
                Log.d(TAG, "サーチ中");
            } else if (doorState.equals("close") || doorState.equals("open")) {
                Log.d(TAG, "開閉要求");
                sendDataByMessageApi("stateUpdate");
                if (doorState.equals("open")) {
                    linearLayout.setBackgroundResource(R.drawable.shape_yellow);
                } else if (doorState.equals("close")) {
                    linearLayout.setBackgroundResource(R.drawable.shape_blue);
                }
            }
        }
    }

    //データを更新
    private void sendDataByMessageApi(final String message) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), "/data_comm2", message.getBytes());
                }
            }
        }).start();
//        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/data_wear");
//        putDataMapReq.getDataMap().putInt("key_wear", text);
//        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
//        Wearable.DataApi.putDataItem(googleApiClient, putDataReq);
        //Log.d(TAG, "データ送信");
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvents) {
        Log.d(TAG, "レシーブ");
        if (messageEvents.getPath().equals("/data_comm")) {
            Log.d(TAG, "パスOK");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    message = new String(messageEvents.getData());
                    doorState = message;
                    Log.d(message, "動いた");
                    if (message.equals("close")) {
                        button.setBackgroundResource(R.drawable.smalo_close_button);
                    } else if (message.equals("open")) {
                        button.setBackgroundResource(R.drawable.smalo_open_button);
                    }

                    //ボタンの後ろの丸を消す
                    linearLayout.setBackgroundResource(R.drawable.shape_clear);
                }
            });
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
