package com.elderdavid.quientienelarazon.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pide a Credential Manager un token de Google. Se le pasa a Google el SHA-256 del nonce y a
 * Supabase el nonce original, que así puede comprobar que el token se pidió para esta sesión.
 * [activityContext] debe ser una Activity: el selector de cuentas se muestra sobre ella.
 */
suspend fun requestGoogleToken(activityContext: Context, webClientId: String): GoogleCredentialResult {
    if (webClientId.isBlank()) return GoogleCredentialResult.Unavailable("Google no está configurado en esta compilación.")
    val rawNonce = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
        .joinToString("") { "%02x".format(it) }

    val option = GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
        .setNonce(hashedNonce)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    return try {
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleCredentialResult.Success(GoogleToken(google.idToken, rawNonce))
        } else {
            GoogleCredentialResult.Unavailable("Respuesta de credencial inesperada.")
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleCredentialResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleCredentialResult.Unavailable("No hay cuentas de Google en este teléfono.")
    } catch (e: GetCredentialException) {
        GoogleCredentialResult.Unavailable("No se pudo abrir el inicio de sesión de Google.")
    }
}
