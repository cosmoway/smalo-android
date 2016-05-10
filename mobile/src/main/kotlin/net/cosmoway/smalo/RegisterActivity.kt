package net.cosmoway.smalo

import android.app.Activity
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class RegisterActivity : Activity(), View.OnClickListener {

    private var mEditText: EditText? = null
    private var mButton: Button? = null

    private fun getRequest(url: String) {
        object : AsyncTask<Void?, Void?, String?>() {
            override fun doInBackground(vararg params: Void?): String? {
                var result: String
                // リクエストオブジェクトを作って
                val request: Request = Request.Builder().url(url).get().build()

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
                Log.d(MyService.TAG_SERVICE, result)
                return result
            }

            override fun onPostExecute(result: String?) {
                val layout: View = findViewById(R.id.layout_register)
                if (result != null) {
                    Log.d("RegisterActivity", result)
                    if (result.equals("200 OK")) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        mEditText = findViewById(R.id.editText_name) as EditText?
        mButton = findViewById(R.id.button_register) as Button?
        mButton?.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        if (v == mButton) {
            // TODO: 名前及びUUIDの送信処理。(URL確定次第入れる。)
            //getRequest(url)
            val intent: Intent = Intent(this, MobileActivity::class.java)
            startActivity(intent)
        }
    }
}