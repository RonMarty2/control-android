package com.rnd.remoto.network.androidtv

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Persistent client identity (RSA keypair + self-signed certificate) used to pair with and
 * reconnect to Android TV devices.
 *
 * This intentionally does NOT use AndroidKeyStore: Keystore-backed RSA keys presented as a TLS
 * client certificate hit a BoringSSL/Conscrypt "RSA routines: internal error" on some devices
 * (observed on a Xiaomi phone talking to an Android TV box's remote service). Using a plain
 * software keypair, persisted as a PKCS12 file in app-private storage, sidesteps that.
 */
object AndroidTvIdentity {
    private const val KEYSTORE_FILE = "androidtv_client_identity.p12"
    private const val KEYSTORE_PASSWORD = "remoto-atv"
    private const val KEY_ALIAS = "client"

    private lateinit var appContext: Context
    private var cachedPrivateKey: PrivateKey? = null
    private var cachedCertificate: X509Certificate? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun ensureIdentity() {
        if (cachedPrivateKey != null && cachedCertificate != null) return
        val file = File(appContext.filesDir, KEYSTORE_FILE)
        val ks = KeyStore.getInstance("PKCS12")

        if (file.exists()) {
            file.inputStream().use { ks.load(it, KEYSTORE_PASSWORD.toCharArray()) }
        } else {
            ks.load(null)
            val (privateKey, certificate) = generateSelfSignedIdentity()
            ks.setKeyEntry(KEY_ALIAS, privateKey, KEYSTORE_PASSWORD.toCharArray(), arrayOf(certificate))
            file.outputStream().use { ks.store(it, KEYSTORE_PASSWORD.toCharArray()) }
        }

        cachedPrivateKey = ks.getKey(KEY_ALIAS, KEYSTORE_PASSWORD.toCharArray()) as PrivateKey
        cachedCertificate = ks.getCertificate(KEY_ALIAS) as X509Certificate
    }

    private fun generateSelfSignedIdentity(): Pair<PrivateKey, X509Certificate> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val now = System.currentTimeMillis()
        val subject = X500Name("CN=ControlRemoto")
        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now),
            Date(now - 24L * 60 * 60 * 1000),
            Date(now + 20L * 365 * 24 * 60 * 60 * 1000),
            subject,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(certBuilder.build(signer))
        return keyPair.private to certificate
    }

    fun clientCertificate(): X509Certificate {
        ensureIdentity()
        return cachedCertificate!!
    }

    /** (modulus, publicExponent) of our own client certificate's RSA public key. */
    fun clientModulusAndExponent(): Pair<BigInteger, BigInteger> {
        val key = clientCertificate().publicKey as RSAPublicKey
        return key.modulus to key.publicExponent
    }

    /** SSLContext presenting our client cert; does not validate the server's certificate,
     * matching the reference implementation (trust is established via the PIN exchange, not TLS). */
    fun buildSslContext(): SSLContext {
        ensureIdentity()
        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null)
            setKeyEntry(KEY_ALIAS, cachedPrivateKey, KEYSTORE_PASSWORD.toCharArray(), arrayOf(cachedCertificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, KEYSTORE_PASSWORD.toCharArray())

        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, arrayOf<TrustManager>(trustManager), null)
        return sslContext
    }
}
