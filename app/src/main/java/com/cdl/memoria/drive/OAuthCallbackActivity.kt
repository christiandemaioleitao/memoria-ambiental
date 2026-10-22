package com.cdl.memoria.drive

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.cdl.memoria.util.Settings
import kotlin.concurrent.thread

/** Captures memoria://oauth redirect, exchanges the code for tokens. */
class OAuthCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        val settings = Settings(this)
        val code = uri?.getQueryParameter("code")
        val error = uri?.getQueryParameter("error")
        when {
            error != null -> toast("Erro de autorização: $error")
            code == null -> toast("Redirecionamento inválido")
            else -> {
                thread {
                    val ok = OAuthHelper.exchangeCode(this, settings, code)
                    runOnUiThread {
                        toast(if (ok) "Google Drive conectado!" else "Falha ao trocar código por token")
                        finish()
                    }
                }
                return
            }
        }
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
