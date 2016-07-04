package net.cosmoway.smalo;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;

public class Smalo extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LeakCanary.install(this);
    }
}
