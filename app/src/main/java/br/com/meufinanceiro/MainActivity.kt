package br.com.meufinanceiro

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import br.com.meufinanceiro.data.AmountPrivacy
import br.com.meufinanceiro.pluggy.PluggyOAuthRelay
import br.com.meufinanceiro.ui.AppViewModel
import br.com.meufinanceiro.ui.BiometricAuth
import br.com.meufinanceiro.ui.BiometricStatus
import br.com.meufinanceiro.ui.FinanceApp
import br.com.meufinanceiro.ui.LockController
import br.com.meufinanceiro.ui.LockScreen
import br.com.meufinanceiro.ui.theme.FinanceTheme

class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private val lock = LockController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        lock.start(
            lockEnabled = viewModel.state.value.biometricLock,
            biometricAvailable = BiometricAuth.isAvailable(this),
            restoredLocked = savedInstanceState?.getBoolean(STATE_LOCKED),
        )

        setContent {
            val state by viewModel.state.collectAsState()
            LaunchedEffect(state.biometricLock) {
                updateWindowSecurity(state.biometricLock)
            }

            FinanceTheme {
                if (lock.locked) {
                    LockScreen(onUnlocked = lock::unlock)
                } else {
                    FinanceApp(
                        viewModel = viewModel,
                        onBiometricLockChange = ::requestBiometricLock,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        lock.onForeground(
            lockEnabled = viewModel.state.value.biometricLock,
            biometricAvailable = BiometricAuth.isAvailable(this),
        )
    }

    override fun onStop() {
        super.onStop()
        // Privacidade: ao sair do app, os valores voltam a ficar ocultos.
        AmountPrivacy.hide()
        // Enquanto o widget da Pluggy está aberto (autorização no banco), o
        // usuário sai do app de propósito: não faz sentido bloquear.
        val current = viewModel.state.value
        if (current.biometricLock && current.connectToken == null) lock.onBackground()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCKED, lock.locked)
    }

    /**
     * Liga/desliga o desbloqueio por biometria.
     * Ativar exige autenticar antes; desativar também pede autenticação para
     * evitar que terceiros desativem a proteção no aparelho desbloqueado.
     */
    private fun requestBiometricLock(enabled: Boolean) {
        if (!enabled) {
            val status = BiometricAuth.status(this)
            if (status == BiometricStatus.AVAILABLE) {
                BiometricAuth.authenticate(
                    activity = this,
                    title = "Desativar desbloqueio",
                    subtitle = "Confirme sua biometria para desativar a proteção",
                    onSuccess = {
                        viewModel.setBiometricLock(false)
                        lock.syncSetting(lockEnabled = false, biometricAvailable = true)
                        Toast.makeText(this, "Desbloqueio por biometria desativado.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { message ->
                        if (!message.contains("cancelado", ignoreCase = true)) {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    },
                )
            } else {
                viewModel.setBiometricLock(false)
                lock.syncSetting(lockEnabled = false, biometricAvailable = true)
                Toast.makeText(this, "Desbloqueio por biometria desativado.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val status = BiometricAuth.status(this)
        if (status != BiometricStatus.AVAILABLE) {
            when (status) {
                BiometricStatus.NONE_ENROLLED -> {
                    Toast.makeText(
                        this,
                        "Cadastre uma biometria nas configurações do aparelho para ativar o desbloqueio.",
                        Toast.LENGTH_LONG,
                    ).show()
                    openBiometricEnrollment()
                }
                BiometricStatus.UNSUPPORTED -> {
                    Toast.makeText(
                        this,
                        "Este aparelho não tem leitor de biometria disponível.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                BiometricStatus.AVAILABLE -> return
            }
            return
        }

        BiometricAuth.authenticate(
            activity = this,
            title = "Ativar desbloqueio",
            subtitle = "Confirme sua biometria para proteger o app",
            onSuccess = {
                viewModel.setBiometricLock(true)
                Toast.makeText(this, "Desbloqueio por biometria ativado.", Toast.LENGTH_SHORT).show()
            },
            onError = { message ->
                if (!message.contains("cancelado", ignoreCase = true)) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    private fun openBiometricEnrollment() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                }
                startActivity(intent)
            } else {
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    /** Evita que os dados do app vazem na visualização de multitarefa recente. */
    private fun updateWindowSecurity(biometricLock: Boolean) {
        if (biometricLock) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val data = uri.toString()
        if (!data.startsWith("meufinanceiro://")) return

        val itemId = uri.getQueryParameter("itemId")
            ?: uri.getQueryParameter("item_id")
            ?: uri.getQueryParameter("id")

        // Garante que o item fique salvo mesmo se o app/WebView tiver sido encerrado.
        viewModel.saveOAuthItem(itemId)

        if (PluggyOAuthRelay.deliver(data)) {
            // O widget continua aberto e vai concluir sozinho ao receber o itemId.
            viewModel.onOAuthDelivered()
        } else {
            // Sem WebView ativo: fecha o fluxo e sincroniza com o que estiver salvo.
            viewModel.onOAuthReturn()
        }
    }

    private companion object {
        const val STATE_LOCKED = "state_locked"
    }
}
