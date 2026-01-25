# QR Code Sharing Implementation

## Overview
Implemented a comprehensive QR code sharing mechanism that allows users to conveniently share and receive health records using QR codes instead of manually entering Share IDs.

## Features Implemented

### 1. **For Sharers (Record Owners)**
- **QR Code Generation**: After creating a share on the blockchain, users can generate a QR code containing the share information
- **QR Code Display**: Custom activity displays the QR code with share details (Share ID, Record Type, Expiry Date)
- **Save QR Code**: Users can save the QR code to their device gallery (Pictures/HealthWallet folder)
- **Share QR Code**: Users can share the QR code image via other apps (WhatsApp, Email, etc.)
- **Show QR Code Button**: Added to ShareRecordDetailActivity for easy access

### 2. **For Receivers**
- **QR Code Scanner**: Custom camera-based QR scanner activity
- **Automatic Share Import**: Scans QR code and automatically extracts share information
- **Validation**: Validates the QR code is a Health Wallet share and checks recipient address
- **Scan QR Code Button**: Added to import share dialog in ReceivedRecordsActivity

## Technical Implementation

### QR Code Data Format
QR codes contain JSON data with the following structure:
```json
{
  "type": "HEALTH_WALLET_SHARE",
  "shareId": "share_12345",
  "recipientAddress": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
  "recordType": "MEDICATION_RECORDS",
  "expiryDate": "2024-12-31",
  "timestamp": 1234567890
}
```

### Libraries Used
- **ZXing Core 3.5.3**: For QR code generation and encoding
- **ZXing Android Embedded 4.3.0**: For QR code scanning with camera
- **AndroidX AppCompat**: For compatibility across Android versions

### Components Created

#### 1. QRCodeDisplayActivity
**File**: `app/src/main/java/com/fyp/blockchainhealthwallet/QRCodeDisplayActivity.kt`

**Features**:
- Generates 512x512 QR code bitmap using ZXing QRCodeWriter
- Displays QR code with share metadata
- Save functionality using MediaStore API
- Share functionality using FileProvider
- Material Design UI matching app theme

**Key Methods**:
- `generateQRCode()`: Creates QR bitmap from JSON data
- `saveQRCodeToGallery()`: Saves QR to Pictures/HealthWallet
- `shareQRCode()`: Shares QR via system share sheet

#### 2. QRScannerActivity
**File**: `app/src/main/java/com/fyp/blockchainhealthwallet/QRScannerActivity.kt`

**Features**:
- Camera permission handling
- Continuous QR code scanning
- QR_CODE format only (no barcodes)
- Returns scanned data to calling activity
- Custom viewfinder with laser animation

**Key Methods**:
- `startScanning()`: Initiates barcode detection
- `onRequestPermissionsResult()`: Handles camera permission
- `barcodeResult()`: Processes scanned QR code

#### 3. ShareRecordDetailActivity Updates
**Changes**:
- Added "Show QR Code" button to action buttons
- `showQRCode()` method launches QRCodeDisplayActivity with share data
- Passes share ID, recipient address, record type, and expiry date

#### 4. ReceivedRecordsActivity Updates
**Changes**:
- Added "Scan QR Code" button to import share dialog (neutral button)
- `startQRScanner()` launches QRScannerActivity using ActivityResultLauncher
- `handleQRCodeResult()` parses and validates scanned JSON data
- Modern Activity Result API instead of deprecated onActivityResult

### Layout Files Created

#### 1. activity_qr_code_display.xml
- Card-based layout with info card and QR display card
- 280dp x 280dp ImageView for QR code
- Share metadata: Share ID, Record Type, Expiry Date
- Three MaterialButtons: Save QR Code, Share QR Code, Done

#### 2. activity_qr_scanner.xml
- Full-screen DecoratedBarcodeView for camera preview
- "Scan QR Code" header text with translucent background
- Black background for better contrast

#### 3. custom_barcode_scanner.xml
- Custom viewfinder with laser animation
- Customized colors matching app theme
- Possible result points visualization

### Configuration Updates

#### 1. AndroidManifest.xml
**Permissions Added**:
- `android.permission.CAMERA`: Required for QR scanning

**Activities Added**:
- `QRCodeDisplayActivity`: Label "Share QR Code"
- `QRScannerActivity`: Label "Scan QR Code", portrait orientation

#### 2. build.gradle.kts
**Dependencies Added**:
```kotlin
implementation("com.google.zxing:core:3.5.3")
implementation("com.journeyapps:zxing-android-embedded:4.3.0") {
    isTransitive = false
}
implementation("androidx.appcompat:appcompat:1.7.0")
```

#### 3. colors.xml
**Scanner Colors Added**:
- `zxing_custom_viewfinder_mask`: Semi-transparent overlay
- `zxing_custom_viewfinder_laser`: Primary color laser
- `zxing_custom_result_view`: Dark result overlay
- `zxing_custom_possible_result_points`: Primary color points

## User Flow

### Sharer Flow
1. User creates a share from ShareRecordsActivity
2. Opens ShareRecordDetailActivity to view share details
3. Clicks "Show QR Code" button
4. QRCodeDisplayActivity displays the QR code
5. User can:
   - Save QR code to gallery for later
   - Share QR code via messaging apps
   - Show QR code to receiver in person

### Receiver Flow
1. User opens ReceivedRecordsActivity
2. Clicks floating action button to import share
3. Dialog appears with "Scan QR Code" button
4. QRScannerActivity opens with camera
5. User scans the sharer's QR code
6. App automatically validates and extracts share information
7. Share is imported if validation passes
8. User can view the received record

## Benefits

1. **User Convenience**: No need to manually copy and paste Share IDs
2. **Error Reduction**: Eliminates typos in Share ID entry
3. **Speed**: Instant sharing by scanning QR code
4. **Flexibility**: Can save QR codes for asynchronous sharing
5. **Security**: Validates recipient address to prevent unauthorized access
6. **Multi-Channel Sharing**: Can share QR via WhatsApp, email, etc.

## Security Considerations

1. **Recipient Validation**: QR code includes recipient address, validated before import
2. **Type Validation**: Ensures QR code is specifically a "HEALTH_WALLET_SHARE"
3. **Blockchain Security**: Share access is still controlled by smart contract
4. **Encryption**: Data encryption remains unchanged - QR only contains share metadata, not actual health data
5. **Expiry Check**: QR code includes expiry date for share validity

## Testing Checklist

- [ ] Generate QR code from share detail page
- [ ] Save QR code to gallery
- [ ] Share QR code via messaging apps
- [ ] Scan QR code with camera
- [ ] Validate correct share import
- [ ] Test recipient address validation
- [ ] Test invalid QR code handling
- [ ] Test camera permission denial
- [ ] Test QR code expiry validation

## Future Enhancements

1. **Batch Sharing**: Generate single QR for multiple records
2. **Dynamic QR**: QR codes that update when share is modified
3. **NFC Support**: Add NFC tap-to-share as alternative
4. **QR Analytics**: Track when QR codes are scanned
5. **Custom QR Design**: Add logo or custom styling to QR codes
6. **Offline Support**: Allow QR generation/scanning without internet

## Files Modified/Created

### Created Files:
- `app/src/main/java/com/fyp/blockchainhealthwallet/QRCodeDisplayActivity.kt`
- `app/src/main/java/com/fyp/blockchainhealthwallet/QRScannerActivity.kt`
- `app/src/main/res/layout/activity_qr_code_display.xml`
- `app/src/main/res/layout/activity_qr_scanner.xml`
- `app/src/main/res/layout/custom_barcode_scanner.xml`

### Modified Files:
- `app/build.gradle.kts` - Added ZXing dependencies
- `app/src/main/AndroidManifest.xml` - Added camera permission and activities
- `app/src/main/res/values/colors.xml` - Added scanner colors
- `app/src/main/java/com/fyp/blockchainhealthwallet/ShareRecordDetailActivity.kt` - Added "Show QR Code" button
- `app/src/main/res/layout/activity_share_record_detail.xml` - Added button to layout
- `app/src/main/java/com/fyp/blockchainhealthwallet/ReceivedRecordsActivity.kt` - Added QR scanning capability

## Conclusion

The QR code sharing implementation provides a modern, convenient, and secure way for users to share and receive health records. It significantly improves the user experience by eliminating manual Share ID entry while maintaining the security guarantees of the blockchain-based system.
