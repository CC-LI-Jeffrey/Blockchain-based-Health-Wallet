package com.fyp.blockchainhealthwallet

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptionValidationTest {

    @Test
    fun roundTripDataEncryptionDecryption_succeeds() {
        val plainText = "{\"patient\":\"alice\",\"diagnosis\":\"flu\",\"ts\":1712040000}"
        val key = EncryptionHelper.generateAESKey()

        val encryptedBase64 = EncryptionHelper.encryptDataWithKey(plainText, key)
        val encryptedBytes = android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)
        val decrypted = EncryptionHelper.decryptBytesWithKey(encryptedBytes, key)

        assertTrue("Encrypted output should not be empty", encryptedBase64.isNotEmpty())
        assertTrue("Encrypted output should differ from plaintext", encryptedBase64 != plainText)
        assertTrue("Decrypted text must match original plaintext", decrypted == plainText)
    }

    @Test
    fun decryptWithWrongKey_failsOrMismatches() {
        val plainText = "{\"record\":\"medication\",\"dose\":\"100mg\",\"freq\":\"daily\"}"
        val correctKey = EncryptionHelper.generateAESKey()
        val wrongKey = EncryptionHelper.generateAESKey()

        val encryptedBase64 = EncryptionHelper.encryptDataWithKey(plainText, correctKey)
        val encryptedBytes = android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)

        val result = runCatching {
            EncryptionHelper.decryptBytesWithKey(encryptedBytes, wrongKey)
        }

        if (result.isSuccess) {
            assertNotEquals("Wrong-key decrypt must not reproduce plaintext", plainText, result.getOrThrow())
        } else {
            assertTrue("Wrong-key decrypt should throw on invalid padding/ciphertext", true)
        }
    }

    @Test
    fun tamperedCiphertext_isRejectedOrMismatches() {
        val plainText = "{\"report\":\"xray\",\"hospital\":\"central\",\"ok\":true}"
        val key = EncryptionHelper.generateAESKey()

        val encryptedBase64 = EncryptionHelper.encryptDataWithKey(plainText, key)
        val tampered = android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)

        // Skip IV (first 16 bytes) and flip one ciphertext byte.
        tampered[20] = (tampered[20].toInt() xor 0x01).toByte()

        val result = runCatching {
            EncryptionHelper.decryptBytesWithKey(tampered, key)
        }

        if (result.isSuccess) {
            assertNotEquals("Tampered ciphertext must not decrypt to original plaintext", plainText, result.getOrThrow())
        } else {
            assertTrue("Tampered ciphertext should fail decryption", true)
        }
    }

    @Test
    fun fileEncryptionDecryption_roundTripIntegrity() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val inputFile = File(context.cacheDir, "enc_input_test.txt")
        val encryptedFile = File(context.cacheDir, "enc_output_test.bin")
        val decryptedFile = File(context.cacheDir, "dec_output_test.txt")

        val originalBytes = "Medical payload for file-level encryption validation.".toByteArray(Charsets.UTF_8)
        inputFile.writeBytes(originalBytes)

        val key = EncryptionHelper.generateAESKey()
        EncryptionHelper.encryptFile(inputFile, encryptedFile, key)
        EncryptionHelper.decryptFile(encryptedFile, decryptedFile, key)

        val decryptedBytes = decryptedFile.readBytes()
        assertArrayEquals("Decrypted file bytes must equal original bytes", originalBytes, decryptedBytes)

        inputFile.delete()
        encryptedFile.delete()
        decryptedFile.delete()
    }
}
