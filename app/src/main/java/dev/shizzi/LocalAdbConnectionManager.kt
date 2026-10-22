package dev.shizzi

import android.content.Context
import android.os.Build
import android.sun.security.x509.CertAndKeyGen
import android.sun.security.x509.X500Name
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

class LocalAdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {
    private val identityDir = File(context.noBackupFilesDir, IDENTITY_DIR).apply { mkdirs() }
    private val privateKeyFile = File(identityDir, PRIVATE_KEY_FILE)
    private val certificateFile = File(identityDir, CERTIFICATE_FILE)
    private val identity = loadOrCreateIdentity()

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
    }

    override fun getPrivateKey(): PrivateKey = identity.privateKey
    override fun getCertificate(): Certificate = identity.certificate
    override fun getDeviceName(): String = "Shizzi"

    private fun loadOrCreateIdentity(): Identity {
        if (privateKeyFile.isFile && certificateFile.isFile) {
            runCatching {
                val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                    PKCS8EncodedKeySpec(privateKeyFile.readBytes()),
                )
                val certificate = certificateFile.inputStream().use {
                    CertificateFactory.getInstance("X.509").generateCertificate(it)
                }
                return Identity(privateKey, certificate)
            }.onFailure {
                privateKeyFile.delete()
                certificateFile.delete()
            }
        }

        val generator = CertAndKeyGen("RSA", "SHA512withRSA").apply {
            setRandom(SecureRandom())
            generate(RSA_KEY_BITS)
        }
        val certificate = generator.getSelfCertificate(
            X500Name("CN=Shizzi Local ADB"),
            Date(System.currentTimeMillis() - CLOCK_SKEW_MS),
            CERTIFICATE_VALIDITY_SECONDS,
        )
        val identity = Identity(generator.privateKey, certificate)
        privateKeyFile.writeBytes(identity.privateKey.encoded)
        certificateFile.writeBytes(identity.certificate.encoded)
        return identity
    }

    private data class Identity(val privateKey: PrivateKey, val certificate: Certificate)

    companion object {
        const val IDENTITY_VERSION = 2

        @Volatile private var instance: LocalAdbConnectionManager? = null

        fun get(context: Context): LocalAdbConnectionManager =
            instance ?: synchronized(this) {
                instance ?: LocalAdbConnectionManager(context.applicationContext).also { instance = it }
            }

        fun reset(context: Context) {
            synchronized(this) {
                runCatching { instance?.close() }
                instance = null
                File(context.applicationContext.noBackupFilesDir, IDENTITY_DIR).deleteRecursively()
            }
        }

        private const val IDENTITY_DIR = "local_adb_identity"
        private const val PRIVATE_KEY_FILE = "adb_private_key.pk8"
        private const val CERTIFICATE_FILE = "adb_certificate.der"
        private const val RSA_KEY_BITS = 2048
        private const val CONNECTION_TIMEOUT_SECONDS = 12L
        private const val CLOCK_SKEW_MS = 60_000L
        private const val CERTIFICATE_VALIDITY_SECONDS = 10L * 365L * 24L * 60L * 60L
    }
}
