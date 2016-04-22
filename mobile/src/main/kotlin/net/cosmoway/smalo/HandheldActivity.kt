package net.cosmoway.smalo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log

class HandheldActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("onCreate", "実行")
        this.setContentView(R.layout.activity_handheld)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
}
