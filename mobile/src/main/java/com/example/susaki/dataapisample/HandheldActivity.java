package com.example.susaki.dataapisample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.gms.wearable.Wearable;

public class HandheldActivity extends AppCompatActivity implements DataApi.
        DataListener, GoogleApiClient.ConnectionCallbacks , GoogleApiClient.OnConnectionFailedListener{

    GoogleApiClient googleApiClient = null;
    TextView textView;
    private String text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_handheld);

        textView = (TextView)findViewById(R.id.text);

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
            Wearable.DataApi.removeListener(googleApiClient,this);
            googleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d("TAG", "onConnected");
        Wearable.DataApi.addListener(googleApiClient,this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("TAG", "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e("TAG", "onConnectionFailed: " + connectionResult);
    }

    //データが変わった際に呼ばれる
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for(DataEvent event : dataEvents){
            if(event.getType() == DataEvent.TYPE_CHANGED){
                DataItem item = event.getDataItem();
                if(item.getUri().getPath().equals("/data_comm")){
                    final DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            text = dataMap.getString("key_data");
                            textView.setText(text);
                        }
                    });
                }
            }else if(event.getType() == DataEvent.TYPE_DELETED){

            }
        }
    }
}
