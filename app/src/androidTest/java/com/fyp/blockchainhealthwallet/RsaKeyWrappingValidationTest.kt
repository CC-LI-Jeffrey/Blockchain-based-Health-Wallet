package com.fyp.blockchainhealthwallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fyp.blockchainhealthwallet.blockchain.EncryptionHelper
import com.fyp.blockchainhealthwallet.blockchain.RSAHelper
import com.fyp.blockchainhealthwallet.wallet.WalletManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RsaKeyWrappingValidationTest {

    private fun setWalletAddressForTest(address: String) {
        val field = WalletManager::class.java.getDeclaredField("_walletAddress")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(WalletManager) as MutableStateFlow<String?>
        flow.value = address
    }

    @Test
    fun keyWrapping_correctRecipientPrivateKey_succeeds() {
        val recipientAddress = "0x1111111111111111111111111111111111111111"
        setWalletAddressForTest(recipientAddress)

        val recipientPublicKey = RSAHelper.generateKeyPair()
        val originalAesKey = EncryptionHelper.generateAESKey()

        val wrappedKey = RSAHelper.encryptKeyWithPublicKey(originalAesKey, recipientPublicKey)
        val unwrappedKey = RSAHelper.decryptKeyWithPrivateKey(wrappedKey)

        assertArrayEquals(
            "Unwrapped key must equal original AES key bytes",
            originalAesKey.encoded,
            unwrappedKey.encoded
        )
    }

    @Test
    fun keyWrapping_wrongRecipientPrivateKey_fails() {
        val recipientA = "0x2222222222222222222222222222222222222222"
        val recipientB = "0x3333333333333333333333333333333333333333"

        // Create recipient A keypair and wrap with A public key.
        setWalletAddressForTest(recipientA)
        val pubA = RSAHelper.generateKeyPair()
        val aesKey = EncryptionHelper.generateAESKey()
        val wrappedForA = RSAHelper.encryptKeyWithPublicKey(aesKey, pubA)

        // Switch to recipient B keypair and attempt unwrap with wrong private key.
        setWalletAddressForTest(recipientB)
        RSAHelper.generateKeyPair()

        val result = runCatching {
            RSAHelper.decryptKeyWithPrivateKey(wrappedForA)
        }

        assertTrue(
            "Unwrap with wrong recipient private key must fail",
            result.isFailure
        )
    }
}
