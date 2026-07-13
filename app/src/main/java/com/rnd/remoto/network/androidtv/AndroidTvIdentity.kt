package com.rnd.remoto.network.androidtv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * Persistent client identity (RSA keypair + self-signed certificate) used to pair with and
 * reconnect to Android TV devices, backed by AndroidKeyStore so the private key never leaves it.
 */
object AndroidTvIdentity {
    // v2: bumped alias so devices that generated a key before signature paddings were set
    // (causing "RSA routines:OPENSSL_internal:internal error" during the TLS handshake)
    // get a fresh, correctly-configured key instead of reusing the broken one.
    private const val ALIAS = "remoto_androidtv_client_v2"
    private const val KEYSTORE = "AndroidKeyStore"

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun ensureKeyPair() {
        val ks = keyStore()
        if (ks.containsAlias(ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE)
        val notAfter = Date(System.currentTimeMillis() + 20L * 365 * 24 * 60 * 60 * 1000)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512
            )
            .setSignaturePaddings(
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                KeyProperties.SIGNATURE_PADDING_RSA_PSS
            )
            .setKeySize(2048)
            .setCertificateSubject(X500Principal("CN=ControlRemoto"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(Date(0))
            .setCertificateNotAfter(notAfter)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    fun clientCertificate(): X509Certificate {
        ensureKeyPair()
        return keyStore().getCertificate(ALIAS) as X509Certificate
    }

    /** (modulus, publicExponent) of our own client certificate's RSA public key. */
    fun clientModulusAndExponent(): Pair<BigInteger, BigInteger> {
        val key = clientCertificate().publicKey as RSAPublicKey
        return key.modulus to key.publicExponent
    }

    /** SSLContext presenting our client cert; does not validate the server's certificate,
     * matching the reference implementation (trust is established via the PIN exchange, not TLS). */
    fun buildSslContext(): SSLContext {
        ensureKeyPair()
        val ks = keyStore()
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, null)

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
