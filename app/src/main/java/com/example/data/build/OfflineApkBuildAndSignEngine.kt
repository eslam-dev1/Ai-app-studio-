package com.example.data.build

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.example.model.GeneratedFile
import com.example.model.StudioProject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class OfflineKeystoreArtifact(
    val keyAlias: String,
    val storePassword: String,
    val commonName: String,
    val keystoreBytes: ByteArray,
    val sha256Fingerprint: String,
    val certificate: X509Certificate,
    val privateKey: PrivateKey? = null
)

typealias KeystoreInfo = OfflineKeystoreArtifact

data class KeystoreInjectionResult(
    val updatedProject: StudioProject,
    val keystoreInfo: OfflineKeystoreArtifact
)

data class SignatureVerificationResult(
    val isVerified: Boolean = true,
    val v1Verified: Boolean = true,
    val v2Verified: Boolean = true,
    val v3Verified: Boolean = true,
    val sha256Fingerprint: String = "",
    val errors: List<String> = emptyList()
)

typealias ApkSignResult = SignatureVerificationResult

object OfflineApkBuildAndSignEngine {
    const val KEYSTORE_PROJECT_PATH = "signing-release.p12"
    const val KEYSTORE_PROPS_PATH = "keystore.properties"
    const val OFFLINE_BUILD_SCRIPT_PATH = "offline-build-sign.sh"
    const val DEFAULT_KEY_ALIAS = "release"
    const val DEFAULT_KEY_PASSWORD = "studio-offline-sign"

    fun generatePkcs12Keystore(
        keyAlias: String = DEFAULT_KEY_ALIAS,
        storePassword: String = DEFAULT_KEY_PASSWORD,
        commonName: String = "AI App Studio"
    ): OfflineKeystoreArtifact {
        val keyPairGen = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }
        val keyPair = keyPairGen.generateKeyPair()
        val certificate = generateSelfSignedCertificate(keyPair, commonName)

        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, storePassword.toCharArray())
            setKeyEntry(keyAlias, keyPair.private, storePassword.toCharArray(), arrayOf(certificate))
        }

        val baos = ByteArrayOutputStream()
        keyStore.store(baos, storePassword.toCharArray())
        val keystoreBytes = baos.toByteArray()

        val sha256 = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        val fingerprint = sha256.joinToString(":") { "%02X".format(it) }

        return OfflineKeystoreArtifact(
            keyAlias = keyAlias,
            storePassword = storePassword,
            commonName = commonName,
            keystoreBytes = keystoreBytes,
            sha256Fingerprint = fingerprint,
            certificate = certificate,
            privateKey = keyPair.private
        )
    }

    fun loadPkcs12Keystore(
        keystoreBytes: ByteArray,
        keyAlias: String = DEFAULT_KEY_ALIAS,
        storePassword: String = DEFAULT_KEY_PASSWORD
    ): OfflineKeystoreArtifact? {
        return runCatching {
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                load(ByteArrayInputStream(keystoreBytes), storePassword.toCharArray())
            }
            val cert = keyStore.getCertificate(keyAlias) as? X509Certificate ?: return null
            val privateKey = keyStore.getKey(keyAlias, storePassword.toCharArray()) as? PrivateKey

            val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            val fingerprint = sha256.joinToString(":") { "%02X".format(it) }
            val cn = cert.subjectX500Principal.name
                .split(",")
                .firstOrNull { it.trim().startsWith("CN=") }
                ?.substringAfter("CN=")
                ?: "AI App Studio"

            OfflineKeystoreArtifact(
                keyAlias = keyAlias,
                storePassword = storePassword,
                commonName = cn,
                keystoreBytes = keystoreBytes,
                sha256Fingerprint = fingerprint,
                certificate = cert,
                privateKey = privateKey
            )
        }.getOrNull()
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return runCatching {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }.getOrElse {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    private fun decodeBase64(content: String): ByteArray {
        return runCatching {
            java.util.Base64.getDecoder().decode(content)
        }.getOrElse {
            android.util.Base64.decode(content, android.util.Base64.DEFAULT)
        }
    }

    fun inspectProjectKeystore(project: StudioProject): OfflineKeystoreArtifact? {
        val file = project.files.firstOrNull { it.path == KEYSTORE_PROJECT_PATH } ?: return null
        val props = project.files.firstOrNull { it.path == KEYSTORE_PROPS_PATH }?.content.orEmpty()
        val alias = Regex("""keyAlias\s*=\s*([^\r\n]+)""").find(props)?.groupValues?.get(1)?.trim() ?: DEFAULT_KEY_ALIAS
        val password = Regex("""storePassword\s*=\s*([^\r\n]+)""").find(props)?.groupValues?.get(1)?.trim() ?: DEFAULT_KEY_PASSWORD

        val rawBytes = runCatching {
            decodeBase64(file.content.removePrefix("[BINARY_BASE64]"))
        }.getOrNull() ?: file.content.toByteArray(Charsets.ISO_8859_1)

        return loadPkcs12Keystore(rawBytes, alias, password)
    }

    fun applyOfflineSigningConfigToProject(
        project: StudioProject,
        keyAlias: String = DEFAULT_KEY_ALIAS,
        storePassword: String = DEFAULT_KEY_PASSWORD,
        commonName: String = project.appName
    ): KeystoreInjectionResult {
        val existingKeystore = inspectProjectKeystore(project)
        val artifact = existingKeystore ?: generatePkcs12Keystore(
            keyAlias = keyAlias,
            storePassword = storePassword,
            commonName = commonName
        )

        val existingFiles = project.files.toMutableList()

        // 1. Keystore binary file stored as Base64 in project files
        val base64Content = "[BINARY_BASE64]" + encodeBase64(artifact.keystoreBytes)
        val kIdx = existingFiles.indexOfFirst { it.path == KEYSTORE_PROJECT_PATH }
        val keystoreFile = GeneratedFile(path = KEYSTORE_PROJECT_PATH, content = base64Content)
        if (kIdx >= 0) existingFiles[kIdx] = keystoreFile else existingFiles.add(keystoreFile)

        // 2. keystore.properties
        val propsContent = buildString {
            appendLine("storeFile=../signing-release.p12")
            appendLine("storePassword=${artifact.storePassword}")
            appendLine("keyAlias=${artifact.keyAlias}")
            appendLine("keyPassword=${artifact.storePassword}")
        }
        val pIdx = existingFiles.indexOfFirst { it.path == KEYSTORE_PROPS_PATH }
        val propsFile = GeneratedFile(path = KEYSTORE_PROPS_PATH, content = propsContent)
        if (pIdx >= 0) existingFiles[pIdx] = propsFile else existingFiles.add(propsFile)

        // 3. offline-build-sign.sh
        val scriptContent = buildString {
            appendLine("#!/usr/bin/env bash")
            appendLine("set -euo pipefail")
            appendLine("echo 'Executing offline Android Gradle release build and APK signing…'")
            appendLine("gradle :app:assembleRelease --offline")
        }
        val sIdx = existingFiles.indexOfFirst { it.path == OFFLINE_BUILD_SCRIPT_PATH }
        val scriptFile = GeneratedFile(path = OFFLINE_BUILD_SCRIPT_PATH, content = scriptContent)
        if (sIdx >= 0) existingFiles[sIdx] = scriptFile else existingFiles.add(scriptFile)

        // 4. Update app/build.gradle.kts idempotently
        val gIdx = existingFiles.indexOfFirst { it.path == "app/build.gradle.kts" }
        if (gIdx >= 0) {
            val originalGradle = existingFiles[gIdx].content
            if (!originalGradle.contains("signing-release.p12")) {
                val signingBlock = """
  signingConfigs {
    create("release") {
      storeFile = file("../signing-release.p12")
      storePassword = "${artifact.storePassword}"
      keyAlias = "${artifact.keyAlias}"
      keyPassword = "${artifact.storePassword}"
    }
  }
"""
                var updated = originalGradle
                if (updated.contains("buildTypes {")) {
                    updated = updated.replaceFirst("buildTypes {", "$signingBlock\n  buildTypes {")
                } else if (updated.contains("android {")) {
                    updated = updated.replaceFirst("android {", "android {\n$signingBlock")
                }
                if (!updated.contains("signingConfig = signingConfigs.getByName(\"release\")")) {
                    updated = updated.replaceFirst(
                        "release {",
                        "release {\n      signingConfig = signingConfigs.getByName(\"release\")"
                    )
                }
                existingFiles[gIdx] = existingFiles[gIdx].copy(content = updated)
            }
        }

        return KeystoreInjectionResult(
            updatedProject = project.copy(files = existingFiles),
            keystoreInfo = artifact
        )
    }

    fun signApkOffline(
        inputApk: File,
        outputApk: File,
        keystore: OfflineKeystoreArtifact
    ): SignatureVerificationResult {
        if (!inputApk.exists() || inputApk.length() == 0L) {
            return SignatureVerificationResult(
                isVerified = false,
                errors = listOf("Input APK does not exist or is empty: ${inputApk.absolutePath}")
            )
        }

        return try {
            val privateKey = keystore.privateKey ?: run {
                val ks = KeyStore.getInstance("PKCS12").apply {
                    load(ByteArrayInputStream(keystore.keystoreBytes), keystore.storePassword.toCharArray())
                }
                ks.getKey(keystore.keyAlias, keystore.storePassword.toCharArray()) as PrivateKey
            }

            val signerConfig = ApkSigner.SignerConfig.Builder(
                keystore.keyAlias,
                privateKey,
                listOf(keystore.certificate)
            ).build()

            val signer = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(inputApk)
                .setOutputApk(outputApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()

            signer.sign()

            val verifier = ApkVerifier.Builder(outputApk).build()
            val vResult = verifier.verify()

            SignatureVerificationResult(
                isVerified = vResult.isVerified,
                v1Verified = vResult.isVerifiedUsingV1Scheme,
                v2Verified = vResult.isVerifiedUsingV2Scheme,
                v3Verified = vResult.isVerifiedUsingV3Scheme,
                sha256Fingerprint = keystore.sha256Fingerprint,
                errors = vResult.errors.map { it.toString() }
            )
        } catch (ex: Exception) {
            SignatureVerificationResult(
                isVerified = false,
                v1Verified = false,
                v2Verified = false,
                v3Verified = false,
                errors = listOf(ex.localizedMessage ?: "Signing error")
            )
        }
    }

    fun verifyApkSignature(apkFile: File): SignatureVerificationResult {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return SignatureVerificationResult(isVerified = false, errors = listOf("APK file not found"))
        }
        return try {
            val verifier = ApkVerifier.Builder(apkFile).build()
            val result = verifier.verify()
            val certs = result.signerCertificates
            val fp = certs.firstOrNull()?.let { cert ->
                MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }
            }.orEmpty()

            SignatureVerificationResult(
                isVerified = result.isVerified,
                v1Verified = result.isVerifiedUsingV1Scheme,
                v2Verified = result.isVerifiedUsingV2Scheme,
                v3Verified = result.isVerifiedUsingV3Scheme,
                sha256Fingerprint = fp,
                errors = result.errors.map { it.toString() }
            )
        } catch (ex: Exception) {
            SignatureVerificationResult(isVerified = false, errors = listOf(ex.localizedMessage ?: "Verification error"))
        }
    }

    fun buildOfflineKotlinRepairPrompt(file: GeneratedFile, diagnostics: List<Any>): String {
        return buildString {
            appendLine("Repair Kotlin/Compose compilation issues in ${file.path}:")
            for (diag in diagnostics) {
                appendLine("- $diag")
            }
            appendLine("Ensure all braces match and imports are resolved.")
        }
    }

    fun buildOfflineKotlinRepairPrompt(file: GeneratedFile, errorText: String): String {
        return "Repair Kotlin/Compose compilation issues in ${file.path}: $errorText"
    }

    fun extractRepairedKotlinIfCleaner(originalFile: GeneratedFile, rawModelResponse: String): GeneratedFile? {
        val snippet = if (rawModelResponse.contains("```")) {
            val match = Regex("""```(?:kotlin)?\s*([\s\S]*?)```""").find(rawModelResponse)
            match?.groupValues?.get(1)?.trim() ?: ""
        } else {
            rawModelResponse.trim()
        }

        if (snippet.isBlank() || snippet.count { it == '{' } != snippet.count { it == '}' }) {
            return null
        }

        val originalUnbalanced = originalFile.content.count { it == '{' } != originalFile.content.count { it == '}' }
        if (originalUnbalanced || snippet.contains("package ") || snippet.contains("@Composable")) {
            return originalFile.copy(content = snippet)
        }
        return null
    }

    private fun generateSelfSignedCertificate(keyPair: KeyPair, commonName: String): X509Certificate {
        val now = Date(System.currentTimeMillis() - 60_000L)
        val expire = Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000L)

        val sha256withRsaOid = Der.sequence(Der.oid("1.2.840.113549.1.1.11"), Der.nullValue())
        val name = Der.sequence(Der.set(Der.sequence(Der.oid("2.5.4.3"), Der.utf8String(commonName))))
        val validity = Der.sequence(Der.utcTime(now), Der.utcTime(expire))
        val subjectPublicKey = keyPair.public.encoded

        val serialNumber = Der.integer(BigInteger.valueOf(System.currentTimeMillis()))
        val tbsCertificate = Der.sequence(
            serialNumber,
            sha256withRsaOid,
            name,
            validity,
            name,
            subjectPublicKey
        )

        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(tbsCertificate)
        }.sign()

        val certDer = Der.sequence(tbsCertificate, sha256withRsaOid, Der.bitString(sig))
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
    }

    private object Der {
        fun sequence(vararg contents: ByteArray): ByteArray {
            val total = contents.sumOf { it.size }
            val baos = ByteArrayOutputStream()
            baos.write(0x30)
            writeLen(baos, total)
            for (c in contents) baos.write(c)
            return baos.toByteArray()
        }

        fun set(vararg contents: ByteArray): ByteArray {
            val total = contents.sumOf { it.size }
            val baos = ByteArrayOutputStream()
            baos.write(0x31)
            writeLen(baos, total)
            for (c in contents) baos.write(c)
            return baos.toByteArray()
        }

        fun integer(value: BigInteger): ByteArray {
            val b = value.toByteArray()
            val baos = ByteArrayOutputStream()
            baos.write(0x02)
            writeLen(baos, b.size)
            baos.write(b)
            return baos.toByteArray()
        }

        fun oid(oidStr: String): ByteArray {
            val parts = oidStr.split(".").map { it.toInt() }
            val body = ByteArrayOutputStream()
            body.write(parts[0] * 40 + parts[1])
            for (i in 2 until parts.size) writeVarInt(body, parts[i])
            val b = body.toByteArray()
            val baos = ByteArrayOutputStream()
            baos.write(0x06)
            writeLen(baos, b.size)
            baos.write(b)
            return baos.toByteArray()
        }

        fun nullValue(): ByteArray = byteArrayOf(0x05, 0x00)

        fun utf8String(str: String): ByteArray {
            val b = str.toByteArray(Charsets.UTF_8)
            val baos = ByteArrayOutputStream()
            baos.write(0x0C)
            writeLen(baos, b.size)
            baos.write(b)
            return baos.toByteArray()
        }

        fun utcTime(date: Date): ByteArray {
            val sdf = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val b = sdf.format(date).toByteArray(Charsets.US_ASCII)
            val baos = ByteArrayOutputStream()
            baos.write(0x17)
            writeLen(baos, b.size)
            baos.write(b)
            return baos.toByteArray()
        }

        fun bitString(bytes: ByteArray): ByteArray {
            val baos = ByteArrayOutputStream()
            baos.write(0x03)
            writeLen(baos, bytes.size + 1)
            baos.write(0x00)
            baos.write(bytes)
            return baos.toByteArray()
        }

        private fun writeLen(out: OutputStream, len: Int) {
            if (len < 128) {
                out.write(len)
            } else if (len < 256) {
                out.write(0x81)
                out.write(len)
            } else {
                out.write(0x82)
                out.write(len ushr 8)
                out.write(len and 0xFF)
            }
        }

        private fun writeVarInt(out: OutputStream, value: Int) {
            if (value < 128) {
                out.write(value)
            } else {
                val buf = mutableListOf<Int>()
                var v = value
                buf.add(v and 0x7F)
                v = v ushr 7
                while (v > 0) {
                    buf.add((v and 0x7F) or 0x80)
                    v = v ushr 7
                }
                buf.reversed().forEach { out.write(it) }
            }
        }
    }
}
