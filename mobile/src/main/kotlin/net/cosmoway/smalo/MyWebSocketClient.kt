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
        fun lock()

        fun unlock()

        fun unknown()
    }

    private var mCallbacks: MyCallbacks? = null

    fun setCallbacks(myCallbacks: MyCallbacks) {
        mCallbacks = myCallbacks
    }

    override fun onOpen(handshakeData: ServerHandshake) {
        Log.i(TAG, "Connected")
    }

    override fun onMessage(message: String) {
        Log.d(TAG, "Massage: " + message)
        var json: JSONObject? = null
        try {
            json = JSONObject(message)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        val str: String?

        try {
            if (json != null) {
                str = json.getString("state")
                if (str != null) {
                    when (str) {
                        "lock" -> mCallbacks!!.lock()
                        "unlock" -> mCallbacks!!.unlock()
                        "unknown" -> mCallbacks!!.unknown()
                    }
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        Log.i(TAG, "Connection suspended: " + reason)
    }

    override fun onError(ex: Exception) {
        Log.w(TAG, "Connection failed:" + ex.message)
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
                // TODO:サーバーURL変える。wss。
                uri = URI("ws://furufuru-ball.herokuapp.com")
            } catch (e: URISyntaxException) {
                e.printStackTrace()
            }

            return MyWebSocketClient(uri)
        }
    }
}
