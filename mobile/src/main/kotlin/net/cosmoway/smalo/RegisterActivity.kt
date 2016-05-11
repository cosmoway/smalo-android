package net.cosmoway.smalo

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException

class RegisterActivity : Activity(), View.OnClickListener {

    // Nsd Manager.
    private var mNsdManager: NsdManager? = null
    // Name of Host.
    private var mHost: String? = null
    // My UUID.
    private var mId: String? = null
    // UI.
    private var mEditText: EditText? = null
    private var mButton: Button? = null

    companion object {
        private val TAG_APP = "RegisterActivity"
        private val TAG_NSD = "NSD"
        private val SERVICE_TYPE = "_xdk-app-daemon._tcp."
        private val MY_SERVICE_NAME = "smalo"
        private val URL = "https://smalo.cosmoway.net:8443/api/v1/devices"
        private val TYPE = MediaType.parse("application/json; charset=utf-8")
        //val MY_SERVICE_NAME = "smalo-dev"
        val MY_SERVICE_UUID = "51a4a738-62b8-4b26-a929-3bbac2a5ce7c"
        val MY_APP_NAME = "SMALO"
    }

    private fun getRequest(url: String, json: String) {
        object : AsyncTask<Void?, Void?, String?>() {
            override fun doInBackground(vararg params: Void?): String? {
                val body: RequestBody = RequestBody.create(TYPE, json);
                var result: String
                // リクエストオブジェクトを作って
                val request: Request = Request.Builder().url(url).post(body).build()

                // クライアントオブジェクトを作って
                val client: OkHttpClient = OkHttpClient()

                // リクエストして結果を受け取って
                try {
                    val response = client.newCall(request).execute()
                    result = response.body().string()
                } catch (e: IOException) {
                    e.printStackTrace()
                    return "Connection Error"
                }
                // 返す
                Log.d(TAG_APP, result)
                return result
            }

            override fun onPostExecute(result: String?) {
                val layout: View = findViewById(R.id.layout_register)
                if (result != null) {
                    Log.d("RegisterActivity", result)
                    if (result.equals("204 No Content")) {
                        Snackbar.make(layout, "正常に登録されました。", Snackbar.LENGTH_SHORT).show()
                        val intent: Intent = Intent(this@RegisterActivity, MobileActivity::class.java)
                        startActivity(intent)
                        // mIsUnlocked = true
                    } else if (result.equals("400")) {
                        Snackbar.make(layout, "予期せぬエラーが発生致しました。\n開発者に御問合せ下さい。"
                                , Snackbar.LENGTH_SHORT).show()
                    } else if (result.equals("403")) {
                        Snackbar.make(layout, "認証に失敗致しました。\nシステム管理者に登録を御確認下さい。"
                                , Snackbar.LENGTH_SHORT).show()
                    } else if (result.equals("Connection Error")) {
                        Snackbar.make(layout, "通信処理が正常に終了されませんでした。\n通信環境を御確認下さい。"
                                , Snackbar.LENGTH_SHORT).show()
                    }
                    // makeNotification(result)
                }
            }
        }.execute()
    }

    fun ensureSystemServices() {
        mNsdManager = getSystemService(Service.NSD_SERVICE) as NsdManager
        /*if (nsdManager == null) {
            return
        }*/
    }

    private fun startDiscovery() {
        mNsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, MyDiscoveryListener())
    }

    private fun stopDiscovery() {
        mNsdManager?.stopServiceDiscovery(MyDiscoveryListener())
    }

    private inner class MyDiscoveryListener : NsdManager.DiscoveryListener {
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.i(TAG_NSD, String.format("Service found serviceInfo=%s", serviceInfo))
            if (serviceInfo.serviceType.equals(SERVICE_TYPE)) {
                mNsdManager?.resolveService(serviceInfo, MyResolveListener())
            }
        }

        override fun onDiscoveryStarted(serviceType: String) {
            Log.i(TAG_NSD, String.format("Discovery started serviceType=%s", serviceType))
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG_NSD, String.format("Discovery stopped serviceType=%s", serviceType))
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.i(TAG_NSD, String.format("Service lost serviceInfo=%s", serviceInfo))
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG_NSD, String.format("Failed to start discovery serviceType=%s, errorCode=%d",
                    serviceType, errorCode))
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG_NSD, String.format("Failed to stop discovery serviceType=%s, errorCode=%d",
                    serviceType, errorCode))
        }
    }

    private inner class MyResolveListener : NsdManager.ResolveListener {
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.i(TAG_NSD, String.format("Service resolved serviceInfo=%s", serviceInfo.host))
            if (serviceInfo.serviceName == MY_SERVICE_NAME) {
                mHost = serviceInfo.host.toString()
            }
        }

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG_NSD, String.format("Failed to resolve serviceInfo=%s, errorCode=%d",
                    serviceInfo, errorCode))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        mEditText = findViewById(R.id.editText_name) as EditText?
        mButton = findViewById(R.id.button_register) as Button?
        mButton?.setOnClickListener(this)

        // TODO: 名前解決
        Log.d(TAG_NSD, "beforeEnsure")
        //ensureSystemServices()
        Log.d(TAG_NSD, "ensured")
    }

    override fun onResume() {
        super.onResume()
        if (mHost == null) {
            //startDiscovery()
        }
    }

    override fun onClick(v: View?) {
        if (v == mButton) {
            // TODO: 名前及びUUIDの送信処理。(URL確定次第入れる。)
            // TODO:端末固有識別番号読出
            val sp = PreferenceManager.getDefaultSharedPreferences(this)
            mId = sp?.getString("saveId", null)
            if (mId == null) {
                Log.d("id", "null")
                // 端末固有識別番号取得
                // mId = UUID.randomUUID().toString()
                mId = "2df60388-e96e-4945-93d0-a4836ee75a3c" //ando test
                sp?.edit()?.putString("saveId", mId)?.apply()
            }
            Log.d("id", mId)
            val json: String = "{\"uuid\":\"$mId\",\"name\":\"${mEditText?.text.toString()}\"}"

            if (mId != null && mEditText?.text.toString() != "") {
                //getRequest(URL, json)
            }
            val intent: Intent = Intent(this, MobileActivity::class.java)
            startActivity(intent)
        }
    }
}
