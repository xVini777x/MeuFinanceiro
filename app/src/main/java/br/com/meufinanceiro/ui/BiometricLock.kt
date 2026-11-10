package br.com.meufinanceiro.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import br.com.meufinanceiro.ui.theme.Bg
import br.com.meufinanceiro.ui.theme.BgSoft
import br.com.meufinanceiro.ui.theme.Border
import br.com.meufinanceiro.ui.theme.Danger
import br.com.meufinanceiro.ui.theme.Mint
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted

/**
 * Controla quando o app precisa pedir a biometria de novo.
 *
 * O bloqueio vale ao abrir o app e também quando ele volta do segundo plano
 * depois de um tempo de tolerância (para não pedir biometria ao alternar
 * rapidamente entre apps, nem ao voltar do fluxo do banco).
 */
class LockController {

    var locked by mutableStateOf(false)
        private set

    private var backgroundAt = 0L
    private var wentToBackground = false

    fun start(lockEnabled: Boolean, biometricAvailable: Boolean, restoredLocked: Boolean?) {
        locked = restoredLocked ?: (lockEnabled && biometricAvailable)
    }

    /** Mantém o estado coerente quando o usuário liga/desliga a opção. */
    fun syncSetting(lockEnabled: Boolean, biometricAvailable: Boolean) {
        if (!lockEnabled || !biometricAvailable) locked = false
    }

    fun onBackground() {
        if (locked) return
        backgroundAt = SystemClock.elapsedRealtime()
        wentToBackground = true
    }

    fun onForeground(lockEnabled: Boolean, biometricAvailable: Boolean) {
        if (!wentToBackground) return
        wentToBackground = false
        if (!lockEnabled || !biometricAvailable) {
            locked = false
            return
        }
        if (SystemClock.elapsedRealtime() - backgroundAt >= LOCK_GRACE_MS) locked = true
    }

    fun unlock() {
        locked = false
    }

    companion object {
        const val LOCK_GRACE_MS = 60_000L
    }
}

enum class BiometricStatus { AVAILABLE, NONE_ENROLLED, UNSUPPORTED }

object BiometricAuth {

    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

    fun status(activity: Activity): BiometricStatus = when (
        BiometricManager.from(activity).canAuthenticate(authenticators())
    ) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
        else -> BiometricStatus.UNSUPPORTED
    }

    fun isAvailable(activity: Activity): Boolean = status(activity) == BiometricStatus.AVAILABLE

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(friendlyError(errorCode, errString.toString()))
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        prompt.authenticate(info)
    }

    private fun friendlyError(code: Int, fallback: String): String = when (code) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED,
        -> "Desbloqueio cancelado."
        BiometricPrompt.ERROR_LOCKOUT ->
            "Muitas tentativas. Aguarde 30 segundos ou use o PIN do aparelho."
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
            "Biometria bloqueada. Use o PIN do aparelho para entrar."
        BiometricPrompt.ERROR_NO_BIOMETRICS ->
            "Nenhuma biometria cadastrada neste aparelho."
        BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE ->
            "Sensor de biometria indisponível no momento."
        BiometricPrompt.ERROR_TIMEOUT -> "Tempo esgotado. Tente novamente."
        else -> fallback.ifBlank { "Não foi possível confirmar sua biometria." }
    }
}

/** Encontra a FragmentActivity mesmo quando o contexto é um wrapper. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun LockScreen(modifier: Modifier = Modifier, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var message by remember { mutableStateOf<String?>(null) }
    var prompting by remember { mutableStateOf(false) }

    val request = {
        val host = activity
        if (host != null && !prompting) {
            prompting = true
            message = null
            BiometricAuth.authenticate(
                activity = host,
                title = "Meu Financeiro",
                subtitle = "Confirme sua biometria para entrar",
                onSuccess = {
                    prompting = false
                    onUnlocked()
                },
                onError = { text ->
                    prompting = false
                    message = text
                },
            )
        }
    }

    // Abre o leitor automaticamente ao aparecer a tela de bloqueio.
    LaunchedEffect(Unit) { request() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg, BgSoft))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Mint.copy(alpha = 0.10f), Color.Transparent),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(Surface1)
                    .border(1.dp, Border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    tint = Mint,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                text = "MEU FINANCEIRO",
                color = TextFaint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Aplicativo bloqueado",
                color = TextMain,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Use sua biometria para acessar seus dados.",
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = request,
                enabled = activity != null && !prompting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Fingerprint, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (prompting) "Aguardando..." else "Desbloquear",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
            if (message != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message.orEmpty(),
                    color = Danger,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Se a biometria não funcionar, use o PIN do aparelho.",
                color = TextFaint,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
