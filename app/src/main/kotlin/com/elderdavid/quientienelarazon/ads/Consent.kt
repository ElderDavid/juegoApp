package com.elderdavid.quientienelarazon.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Consentimiento para anuncios con el User Messaging Platform de Google. El formulario solo aparece
 * donde la ley lo exige (por ejemplo, Espacio Económico Europeo o ciertos estados de EE. UU.) y
 * únicamente si el mensaje está configurado en AdMob → Privacidad y mensajes.
 */
class AdsConsent(context: Context) {
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(context.applicationContext)

    private val _canRequestAds = MutableStateFlow(info.canRequestAds())

    /** true cuando ya se pueden pedir anuncios (con el consentimiento que haya dado el usuario). */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(isPrivacyOptionsRequired())

    /** true si hay que ofrecer en Ajustes la opción de cambiar el consentimiento. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** Actualiza el estado y muestra el formulario si hace falta. Se llama en cada arranque. */
    fun gather(activity: Activity, onDone: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    // Solo el código: el mensaje no lleva datos personales, pero no hace falta más.
                    if (error != null) Log.w(TAG, "Formulario de consentimiento no disponible (código ${error.errorCode})")
                    publish()
                    onDone()
                }
            },
            { error ->
                Log.w(TAG, "No se pudo actualizar el consentimiento (código ${error.errorCode})")
                publish()
                onDone()
            },
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Opciones de privacidad no disponibles (código ${error.errorCode})")
            publish()
        }
    }

    private fun publish() {
        _canRequestAds.value = info.canRequestAds()
        _privacyOptionsRequired.value = isPrivacyOptionsRequired()
    }

    private fun isPrivacyOptionsRequired() =
        info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    private companion object {
        const val TAG = "AdsConsent"
    }
}
