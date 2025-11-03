# QR Code Implementation Guide

## Adding QR Code Support for WalletConnect

To display the WalletConnect pairing URI as a QR code, follow these steps:

## Step 1: Add ZXing Dependency

Add to `app/build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies
    
    // QR Code generation
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
```

## Step 2: Create QR Dialog Layout

Create `app/src/main/res/layout/dialog_qr_code.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp"
    android:background="@color/surface">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Scan QR Code"
        android:textSize="24sp"
        android:textStyle="bold"
        android:textColor="@color/text_primary"
        android:gravity="center"
        android:layout_marginBottom="16dp" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Open your wallet app and scan this QR code to connect"
        android:textSize="14sp"
        android:textColor="@color/text_secondary"
        android:gravity="center"
        android:layout_marginBottom="24dp" />

    <ImageView
        android:id="@+id/ivQrCode"
        android:layout_width="300dp"
        android:layout_height="300dp"
        android:layout_gravity="center"
        android:background="@color/white"
        android:padding="16dp"
        android:layout_marginBottom="24dp" />

    <TextView
        android:id="@+id/tvWalletUri"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="wc:..."
        android:textSize="10sp"
        android:textColor="@color/text_hint"
        android:maxLines="2"
        android:ellipsize="middle"
        android:gravity="center"
        android:layout_marginBottom="24dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnCopyUri"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Copy URI"
            android:textColor="@color/primary"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"
            app:strokeColor="@color/primary"
            android:layout_marginEnd="8dp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnCancel"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Cancel"
            android:textColor="@color/white"
            android:backgroundTint="@color/text_secondary"
            android:layout_marginStart="8dp" />
    </LinearLayout>

</LinearLayout>
```

## Step 3: Create QR Code Helper Class

Create `app/src/main/java/com/example/blockchain_based_health_wallet/QrCodeHelper.kt`:

```kotlin
package com.example.blockchain_based_health_wallet

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeHelper {
    
    fun generateQrCode(text: String, width: Int = 800, height: Int = 800): Bitmap? {
        return try {
            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height)
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
```

## Step 4: Update LoginActivity

Add this method to `LoginActivity.kt`:

```kotlin
private fun showQrCodeDialog(uri: String) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_qr_code, null)
    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .setCancelable(true)
        .create()

    val ivQrCode = dialogView.findViewById<ImageView>(R.id.ivQrCode)
    val tvWalletUri = dialogView.findViewById<TextView>(R.id.tvWalletUri)
    val btnCopyUri = dialogView.findViewById<MaterialButton>(R.id.btnCopyUri)
    val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)

    // Generate and display QR code
    val qrBitmap = QrCodeHelper.generateQrCode(uri)
    if (qrBitmap != null) {
        ivQrCode.setImageBitmap(qrBitmap)
    } else {
        Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
    }

    // Display URI
    tvWalletUri.text = uri

    // Copy URI button
    btnCopyUri.setOnClickListener {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("WalletConnect URI", uri)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "URI copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // Cancel button
    btnCancel.setOnClickListener {
        dialog.dismiss()
    }

    dialog.show()
}
```

## Step 5: Add Imports

Add these imports to `LoginActivity.kt`:

```kotlin
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.ImageView
import com.google.android.material.button.MaterialButton
```

## Step 6: Use QR Code Dialog

In the `connectWallet()` method, when you get the pairing URI:

```kotlin
// After creating the pairing and getting the URI
val uri = "wc:..." // The actual URI from WalletConnect

withContext(Dispatchers.Main) {
    hideProgress()
    showQrCodeDialog(uri) // Show QR code instead of toast
}
```

## Step 7: Alternative - Deep Link Buttons

Add popular wallet deep link buttons to `activity_login.xml`:

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:layout_marginTop="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Or connect with:"
        android:textSize="14sp"
        android:textColor="@color/text_secondary"
        android:layout_marginBottom="12dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center">

        <ImageButton
            android:id="@+id/btnMetaMask"
            android:layout_width="64dp"
            android:layout_height="64dp"
            android:src="@drawable/ic_metamask"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:scaleType="fitCenter"
            android:padding="8dp" />

        <ImageButton
            android:id="@+id/btnTrustWallet"
            android:layout_width="64dp"
            android:layout_height="64dp"
            android:src="@drawable/ic_trust_wallet"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:scaleType="fitCenter"
            android:padding="8dp"
            android:layout_marginStart="16dp" />

        <ImageButton
            android:id="@+id/btnRainbow"
            android:layout_width="64dp"
            android:layout_height="64dp"
            android:src="@drawable/ic_rainbow"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:scaleType="fitCenter"
            android:padding="8dp"
            android:layout_marginStart="16dp" />
    </LinearLayout>
</LinearLayout>
```

## Step 8: Deep Link Handler

Add this method to `LoginActivity.kt`:

```kotlin
private fun openWalletApp(walletPackage: String, uri: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(uri)
        intent.`package` = walletPackage
        
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // Wallet not installed, open Play Store
            val playStoreIntent = Intent(Intent.ACTION_VIEW)
            playStoreIntent.data = Uri.parse("market://details?id=$walletPackage")
            startActivity(playStoreIntent)
        }
    } catch (e: Exception) {
        Toast.makeText(this, "Failed to open wallet: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// Usage:
findViewById<ImageButton>(R.id.btnMetaMask).setOnClickListener {
    openWalletApp("io.metamask", wcUri)
}

findViewById<ImageButton>(R.id.btnTrustWallet).setOnClickListener {
    openWalletApp("com.wallet.crypto.trustapp", wcUri)
}

findViewById<ImageButton>(R.id.btnRainbow).setOnClickListener {
    openWalletApp("me.rainbow", wcUri)
}
```

## Common Wallet Package Names

| Wallet | Package Name |
|--------|--------------|
| MetaMask | io.metamask |
| Trust Wallet | com.wallet.crypto.trustapp |
| Rainbow | me.rainbow |
| Coinbase Wallet | org.toshi |
| Argent | im.argent.contractwalletclient |
| Zerion | io.zerion.android |

## Testing

1. **With Real Wallet:**
   - Install MetaMask or Trust Wallet on your device
   - Click "Connect Wallet" in your app
   - Scan QR code or tap wallet button
   - Approve connection in wallet
   - Verify session in app

2. **Without Wallet (Development):**
   - QR code should still display
   - Copy URI should work
   - Cancel should close dialog

## Resources Needed

You'll need wallet logo icons:
- `drawable/ic_metamask.png`
- `drawable/ic_trust_wallet.png`
- `drawable/ic_rainbow.png`

Download from official wallet brand assets or use placeholder icons.

---

**Note:** Once you implement QR codes and deep links, users will have multiple ways to connect:
1. Scan QR code with any wallet
2. Tap wallet button for direct deep link
3. Copy URI manually if needed
