# Partial Sharing System - Visual Flow Diagrams

## 🎨 System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    BLOCKCHAIN HEALTH WALLET                     │
│                   Merkle Tree Partial Sharing                   │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
│  OWNER DEVICE    │      │   BLOCKCHAIN     │      │ RECEIVER DEVICE  │
│  (Android App)   │◄────►│  + IPFS + API    │◄────►│  (Android App)   │
└──────────────────┘      └──────────────────┘      └──────────────────┘
        │                          │                          │
        ▼                          ▼                          ▼
  Select Attributes          Store Merkle Root         Scan/Download
  Build Merkle Tree         Grant/Revoke Access       Verify Proofs
  Generate Proofs           Enforce Expiry            Display Attributes
  Share (QR/Blockchain)     Manage Permissions        Confirm Authenticity
```

## 🌳 Merkle Tree Structure

```
Example: Personal Info with 11 attributes

                        ROOT (Merkle Root)
                     [Stored on Blockchain]
                    ┌──────────┴──────────┐
                   H5                     H6
          ┌────────┴────────┐     ┌──────┴──────┐
         H3                 H4    H5             H6
      ┌───┴───┐         ┌───┴──┐ ┌──┴──┐     ┌──┴──┐
     H1       H2       H3      H4 ...  ... ...   ...
   ┌─┴─┐   ┌─┴─┐    ┌─┴─┐  ┌─┴─┐
  L1  L2  L3  L4  L5  L6  L7  L8  L9  L10  L11

  │   │   │   │   │   │   │   │   │    │    │
  ▼   ▼   ▼   ▼   ▼   ▼   ▼   ▼   ▼    ▼    ▼
 Name DoB Gen Blood Addr Phone Email EmCon EmPhn Allerg Chronic

Each Leaf = SHA256("attributeName:attributeValue")
Each Node = SHA256(leftChild + rightChild)

To verify "Name":
 ✓ Compute: SHA256("fullName:John Doe") = L1
 ✓ Need: L2, H2, H6 (proof path)
 ✓ Compute up to root
 ✓ Compare with blockchain root
```

## 🔐 Complete Implementation
All files created and ready to use! See IMPLEMENTATION_COMPLETE.md for full details.
