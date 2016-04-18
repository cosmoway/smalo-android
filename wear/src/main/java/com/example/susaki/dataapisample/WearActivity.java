package com.example.susaki.dataapisample;

import android.app.Activity;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class WearActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    private String TAG = "ウェア";
    private TextView mTextView;

    private GoogleApiClient googleApiClient = null;
    private Button button;
    private int i = 0;

    String editText = "OK";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.round_activity_wear);

        button = (Button) findViewById(R.id.wearButton);
        button.setOnClickListener(this);

        googleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        //データ更新をするメソッドを呼ぶ
        sendDataByDataApi(editText);
    }

    @Override
    protected void onResume() {
        super.onResume();
        googleApiClient.connect();
        //Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
            //Log.d(TAG, "onPause");
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
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
            i++;
            Log.d(TAG, "ボタン実行");
            //データを更新するメソッドを呼ぶ
            sendDataByDataApi(editText + i);
        }
    }

    //データを更新
    private void sendDataByDataApi(String text) {
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/data_comm");
        putDataMapReq.getDataMap().putString("key_data", text);
        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        Wearable.DataApi.putDataItem(googleApiClient, putDataReq);
        //Log.d(TAG, "データ送信");
    }
}
