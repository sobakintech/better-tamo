package lt.bettertamo.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class SchoolRole(val id: String, val title: String, val subtitle: String, val studentId: String = "")

@Serializable
data class SchoolSession(val token: String, val personId: String, val name: String, val legacyRole: Int, val roles: List<SchoolRole> = emptyList(), val selectedRole: String? = null) {
    val scope get() = "$personId:${selectedRole.orEmpty()}"
    override fun toString() = "SchoolSession([redacted])"
}

internal class SealedFile(context: Context, name: String) {
    private val file = android.util.AtomicFile(java.io.File(context.noBackupFilesDir, name))
    private val alias = "better-tamo-session-v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): String? {
        if (!file.baseFile.exists()) return null
        val bytes = file.readFully()
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    fun write(text: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    fun clear() = file.delete()
}

class SessionVault(context: Context) {
    private val file = SealedFile(context, "school-session.enc")
    fun read(): SchoolSession? = file.read()?.let { Json.decodeFromString(it) }
    fun save(session: SchoolSession) = file.write(Json.encodeToString(session))
    fun clear() = file.clear()
}
