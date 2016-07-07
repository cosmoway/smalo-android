package net.cosmoway.smalo

import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import okhttp3.*
import java.io.IOException
import java.util.*

class RegisterActivity : AppCompatActivity(), View.OnClickListener {

    // My UUID.
    private var mId: String? = null
    // UI.
    private var mEditText: EditText? = null
    private var mButton: Button? = null

    companion object {
        private val TAG_APP = "RegisterActivity"
        private val MY_SERVICE_NAME = "smalo"
        // private val MY_SERVICE_NAME = "smalo-dev"
        //private val URL = "https://smalo.cosmoway.net:8443/api/v1/devices"
        private val URL = "https://smalo.cosmoway.net/api/v1/devices"
        private val TYPE = MediaType.parse("application/json; charset=utf-8")
    }

    private fun showSnackBar(msg: String) {
        val layout: View = findViewById(R.id.layout_register) as View
        val snackBar: Snackbar = Snackbar.make(layout, msg, Snackbar.LENGTH_SHORT)
        val textView: TextView = snackBar.view
                .findViewById(android.support.design.R.id.snackbar_text) as TextView
        textView.setTextColor(Color.WHITE)
        snackBar.show()
    }

    private fun getRequest(url: String, json: String) {
        object : AsyncTask<Void?, Void?, String?>() {
            override fun doInBackground(vararg params: Void?): String? {
                val body: RequestBody = RequestBody.create(TYPE, json)
                val result: String
                // リクエストオブジェクトを作って
                val request: Request = Request
                        .Builder()
                        .header(MY_SERVICE_NAME, "Content-Type: application/json")
                        .url(url)
                        .post(body)
                        .build()
                val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .allEnabledTlsVersions()
                        .allEnabledCipherSuites()
                        .build()

                // クライアントオブジェクトを作って
                val client: OkHttpClient = OkHttpClient.Builder()
                        .connectionSpecs(Collections.singletonList(spec))
                        .build()

                // リクエストして結果を受け取って
                try {
                    val response = client.newCall(request).execute()
                    result = response.code().toString()
                } catch (e: IOException) {
                    e.printStackTrace()
                    return "Connection Error"
                }
                // 返す
                Log.d(TAG_APP, result)
                return result
            }

            override fun onPostExecute(result: String?) {
                if (result != null) {
                    Log.d("RegisterActivity", result)
                    if (result.equals("204")) {
                        showSnackBar("正常に登録されました。")
                        val intent: Intent = Intent(this@RegisterActivity,
                                MobileActivity::class.java)
                        intent.putExtra(MobileActivity.EXTRA_BOOT_STATE, 1)
                        intent.putExtra(MobileActivity.EXTRA_UUID, mId)
                        startActivity(intent)
                        finish()
                    } else if (result.equals("400")) {
                        showSnackBar("予期せぬエラーが発生致しました。\n開発者に御問合せ下さい。")
                    } else if (result.equals("404")) {
                        showSnackBar("サーバーが見つかりませんでした。\n開発者に御問合せ下さい。")
                    } else if (result.equals("500")) {
                        showSnackBar("サーバー内部でエラーが発生致しました。\n開発者に御問合せ下さい。")
                    } else if (result.equals("Connection Error")) {
                        showSnackBar("通信処理が正常に終了されませんでした。\n通信環境を御確認下さい。")
                    }
                }
            }
        }.execute()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        mEditText = findViewById(R.id.editText_name) as EditText?
        mButton = findViewById(R.id.button_register) as Button?
        mButton?.setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onClick(v: View?) {
        if (v == mButton) {
            // TODO: 名前及びUUIDの送信処理。
            // TODO:端末固有識別番号読出
            val sp = PreferenceManager.getDefaultSharedPreferences(this)
            mId = sp?.getString("saveId", null)
            if (mId == null) {
                Log.d("id", "null")
                // 端末固有識別番号取得
                mId = UUID.randomUUID().toString()
                sp?.edit()?.putString("saveId", mId)?.apply()
            }
            Log.d("id", mId)
            val json: String = "{\"uuid\":\"$mId\",\"name\":\"${mEditText?.text.toString()}\"}"

            if (mId != null && mEditText?.text.toString() != "") {
                getRequest(URL, json)
            }
        }
    }
}
