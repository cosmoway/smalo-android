package net.cosmoway.smalo

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException

class MyWebSocketClient(serverURI: URI) : WebSocketClient(serverURI) {

    interface MyCallbacks {
        fun connectionOpen()

        fun error()

        fun onStateChange(str: String?)
    }

    private var mCallbacks: MyCallbacks? = null

    fun setCallbacks(myCallbacks: MyCallbacks) {
        mCallbacks = myCallbacks
    }

    override fun onOpen(handshakeData: ServerHandshake) {
        Log.i(TAG, "Connected")
        mCallbacks?.connectionOpen()
    }

    override fun onMessage(message: String) {
        Log.d(TAG, "Massage: " + message)
        var json: JSONObject? = null
        try {
            json = JSONObject(message)
        } catch (e: JSONException) {
            e.printStackTrace()
            Log.e(TAG, "Error:$e")
        }

        val str: String?

        try {
            if (json != null) {
                str = json.getString("state")
                mCallbacks?.onStateChange(str)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            Log.e(TAG, "Error:$e")
        }

    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        Log.i(TAG, "Connection suspended: " + reason)
    }

    override fun onError(ex: Exception) {
        Log.w(TAG, "Connection failed:" + ex.message)
        mCallbacks?.error()
    }

    val isOpen: Boolean
        get() = connection.isOpen

    val isClosed: Boolean
        get() = connection.isClosed

    companion object {

        private val TAG = "MyWebSocketClient"

        fun newInstance(): MyWebSocketClient {
            var uri: URI? = null
            try {
                //uri = URI("wss://echo.websocket.org")
                //uri = URI("wss://smalo.cosmoway.net:8443")
                uri = URI("wss://smalo.cosmoway.net")
            } catch (e: URISyntaxException) {
                e.printStackTrace()
            }
            return MyWebSocketClient(uri!!)
        }
    }
}
