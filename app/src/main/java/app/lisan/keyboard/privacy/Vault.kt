package app.lisan.keyboard.privacy

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Explicitly saved user data. Call on Dispatchers.IO; never log decrypted content. */
class Vault private constructor(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "lisan-vault-v1"))
    private val alias = "lisan-local-vault-v1"
    private fun ks() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(): SecretKey = (ks().getKey(alias, null) as? SecretKey)
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    private fun read(): JSONObject {
        if (!file.baseFile.exists()) return JSONObject()
        val all = file.readFully()
        require(all.size in 29..3_000_000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0,12)))
        return JSONObject(cipher.doFinal(all,12,all.size - 12).toString(Charsets.UTF_8))
    }
    private fun write(j: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val stream = file.startWrite()
        try {
            stream.write(cipher.iv)
            stream.write(cipher.doFinal(j.toString().toByteArray(Charsets.UTF_8)))
            file.finishWrite(stream)
        } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    @Synchronized fun items(group: String): List<JSONObject> = try {
        read().optJSONArray(group)?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty()
    } catch (_: Exception) { emptyList() }
    @Synchronized fun update(group: String, rule: (List<JSONObject>) -> List<JSONObject>) {
        val data = read()
        val a = data.optJSONArray(group)
        val old = if (a == null) emptyList() else List(a.length()) { a.getJSONObject(it) }
        data.put(group, JSONArray(rule(old).take(50)))
        write(data)
    }
    @Synchronized fun clear() { file.delete(); ks().deleteEntry(alias) }
    companion object {
        @Volatile private var instance: Vault? = null
        fun get(c: Context) = instance ?: synchronized(this) { instance ?: Vault(c).also { instance = it } }
    }
}
