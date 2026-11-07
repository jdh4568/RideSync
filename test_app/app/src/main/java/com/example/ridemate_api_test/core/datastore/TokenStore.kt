package com.example.ridemate_api_test.core.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.example.ridemate_api_test.feature.auth.domain.model.AuthCredentials
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface TokenStore {
    suspend fun save(credentials: AuthCredentials)
    suspend fun clear()
}

/** No read/restore path: automatic login is intentionally outside this stage. */
class KeystoreTokenStore(context: Context) : TokenStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "login-session.enc"))
    private val lock = Mutex()
    private val alias = "ridesync.login.session.v1"

    override suspend fun save(credentials: AuthCredentials) = withContext(Dispatchers.IO) {
        lock.withLock {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = (keyStore.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore",
            ).apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            }.generateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
            val plain = JSONObject().put("accessToken", credentials.accessToken)
                .put("userId", credentials.user.id).put("nickname", credentials.user.nickname)
                .toString().toByteArray(Charsets.UTF_8)
            val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
            val output = file.startWrite()
            try {
                output.write(cipher.iv.size)
                output.write(cipher.iv)
                output.write(encrypted)
                file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock {
            // Destroy the decryption key as well as the non-backed-up ciphertext.
            try { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } }
            finally { file.delete() }
            check(!file.baseFile.exists())
        }
    }
}
