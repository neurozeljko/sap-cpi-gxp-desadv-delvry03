# Enterprise Integration Architecture: GxP B2B DESADV to S/4HANA DELVRY03

[![SAP Integration Suite](https://img.shields.io/badge/SAP%20Integration%20Suite-Cloud%20Integration-0a85ea)](https://community.sap.com/topics/cloud-integration)
[![SAP S/4HANA](https://img.shields.io/badge/SAP%20S%2F4HANA-DELVRY03%20IDoc-orange)](https://help.sap.com)
[![Compliance](https://img.shields.io/badge/GxP-FDA%2021%20CFR%20Part%2011%20%7C%20EU%20Annex%2011-success)](https://www.fda.gov)
[![Protocol](https://img.shields.io/badge/Protocol-AS2%20%7C%20EDIFACT%20D.01B-blueviolet)](https://unece.org)
[![Security](https://img.shields.io/badge/Security-SHA--256%20Signed%20MDN-red)](https://tools.ietf.org/html/rfc4130)

## Executive Summary

This enterprise integration project demonstrates the architecture, implementation, and regulatory compliance validation for inbound B2B Advanced Shipping Notifications (ASN) within a global life sciences and biopharmaceutical supply chain landscape (**BioPharma Global AG**).

The architecture securely ingests serialized electronic despatch advices (**UN/EDIFACT D.01B DESADV**) transmitted by 3PL logistics carriers (**DHL Life Sciences**) via cryptographic **AS2**, executes automated cold-chain telemetry validation via custom Groovy inspection pipelines, quarantines compromised clinical batches (`BESTQ = 'S'`), transforms validated structures into SAP S/4HANA standard delivery IDocs (`DELVRY03`), and guarantees business continuity via an automated **SFTP dead-letter queue (DLQ)** exception fallback.

---

## Architecture Overview

### High-Level Topology

```text
[DHL Life Sciences 3PL (GLN: 4098765000012)]
  │
  │ EDIFACT D.01B DESADV (AS2 / HTTPS)
  │ Synchronous Signed MDN (SHA-256 MIC)
  ▼
[SAP Integration Suite (Cloud Integration Runtime)]
  ├── Security & Audit Layer:
  │    └── Content Modifier (GxP_CorrelationId, GxP_CarrierID, GxP_Timestamp)
  │
  ├── Transformation & Validation Pipeline:
  │    ├── EDIFACT to XML Splitter / Converter
  │    ├── Groovy Cold-Chain Gatekeeper (ValidateColdChainQuarantine.groovy)
  │    │    ├── Compliant (+2°C to +8°C): Unrestricted Stock Posting
  │    │    └── Temperature Excursion: Dynamic Quarantine Flag Injection (BESTQ = 'S')
  │    └── Graphical Message Mapping (DESADV_D01B to DELVRY03)
  │
  ├── Outbound Delivery Dispatch:
  │    └── IDoc Adapter (To SAP S/4HANA Private Cloud)
  │
  └── Exception Subprocess (High-Availability Failover):
       ├── Error Trigger (S/4HANA Timeout / Schema Rejection)
       ├── GxP Audit Log Enrichment
       └── SFTP Fallback Adapter (Quarantine Repository: /gxp/backup/failed_asns/)
```

---

## Technical Specifications

| Component | Technical Implementation | Architectural Justification |
| :--- | :--- | :--- |
| **Inbound Interface** | AS2 Inbound Adapter (PKCS#7 / X.509) | Non-repudiation of receipt for FDA/EMA audits via synchronous SHA-256 cryptographic signed MDN |
| **Audit Context** | Content Modifier (`CM_GxP_Audit_Headers`) | Injects immutable ALCOA+ audit metadata (Carrier GLN, Message GUID, UTC ISO Timestamp) |
| **Pipeline Transform 1** | EDI to XML Converter (EDIFACT D.01B) | Converts EDIFACT flat syntax to normalized XML DOM for XPath evaluation and graphical mapping |
| **Cold-Chain Gatekeeper** | Groovy 3.x (`ValidateColdChainQuarantine.groovy`) | Validates `MEA+TEMP` sensors; flags excursions into quarantine inventory (`E1EDL24-BESTQ = 'S'`) |
| **Pipeline Transform 2** | Graphical Message Mapping (`MM_DESADV_to_DELVRY03`) | Comprehensive structural transformation from EDIFACT DESADV to SAP standard delivery IDoc |
| **Outbound Interface** | IDoc Adapter (Basic Type: `DELVRY03`) | Standard transactional delivery document posting in SAP S/4HANA without core modification |
| **Dead-Letter Failover** | SFTP Adapter (`sftp.dhl-pharma-logistics.com`) | Zero message loss guarantee during SAP maintenance windows; persists unposted ASNs to secure audit storage |
| **Regulatory Validation** | FDA 21 CFR Part 11 / EU Annex 11 | Immutable ALCOA+ audit metadata attached to every transaction (`GxP_Audit_Headers`) |

---

## GxP Regulatory Compliance Framework (21 CFR Part 11 / EU Annex 11)

Operating within life sciences requires adherence to strict computer system validation (**CSV**) principles:

1. **Non-Repudiation (AS2 Signed MDN):**
   * Uses cryptographically signed Message Disposition Notifications (MDN) with SHA-256 MIC algorithms.
   * Proves that the exact batch shipping manifest transmitted by the carrier was received without alteration.

2. **Cold-Chain Excursion Governance (`BESTQ = 'S'`):**
   * Pre-validation via custom Groovy script inspects incoming temperature sensor telemetry against batch master limits (standard cold chain: +2.0°C to +8.0°C).
   * Any recorded temperature breach automatically populates the SAP IDoc inventory stock category field `E1EDL24-BESTQ` with value `'S'` (Blocked / Quarantine stock), preventing unauthorized automated release into commercial inventory.

3. **GxP Audit Headers & Traceability (ALCOA+):**
   * Custom audit properties attached to exchange properties:
     * `GxP_CorrelationId`: SAP CPI Message GUID.
     * `GxP_CarrierID`: `4098765000012`.
     * `GxP_Timestamp`: UTC ISO-8601 transmission stamp.
     * `GxP_BatchStatus`: Inspection outcome flag.

4. **Dead-Letter Resiliency & Exception Subprocess:**
   * If S/4HANA backend encounters downtime, network timeout, or schema rejection, the `Exception Subprocess` captures the raw original payload along with error context.
   * Automatically dispatches the failed payload via SFTP to a secure, compliant audit repository for reconciliation (`FAILED_DESADV_${date:now:yyyyMMdd_HHmmss}.edi`), guaranteeing **zero loss of electronic shipment data**.

---

## Message Mapping Details (DESADV to DELVRY03)

| EDIFACT DESADV Segment | Target S/4HANA IDoc DELVRY03 Field | Business Semantic |
| :--- | :--- | :--- |
| `BGM+351+ASN_NUM+9'` | `EDI_DC40-MESTYP = 'DESADV'`<br>`E1EDL20-VBELN` | Inbound Delivery Manifest Reference |
| `DTM+137:20261029:102'` | `E1EDL20-LFDAT` | Scheduled Delivery Date |
| `NAD+CZ+4098765000012'` | `E1EDL20-E1EDL21-PARTNER_ID` | Consignor / Carrier (DHL GLN) |
| `NAD+CN+7612345000018'` | `E1EDL20-E1EDL21-PARTNER_ID` | Consignee (BioPharma Global AG GLN) |
| `LIN+1++MATERIAL_SKU:EN'` | `E1EDL24-MATNR` | Pharmaceutical Material SKU |
| `QTY+12:5000:PCE'` | `E1EDL24-LFIMG` | Inbound Shipped Quantity |
| `PIA+1+BATCH_LOT_NUMBER:BT'` | `E1EDL24-CHARG` | Pharmaceutical Batch / Lot ID |
| `MEA+TEMP+MAX+CEL:8.5'` | `E1EDL24-BESTQ = 'S'` | Inventory Quarantine Status (via Groovy) |

---

## Repository Structure

```text
├── README.md                                          # Comprehensive architectural documentation
├── ADR-001-B2B-DESADV-Architecture.md                 # Architecture Decision Record
├── DELVRY03.xsd                                       # S/4HANA Delivery IDoc XML Schema
├── DESADV_D01B.xsd                                    # EDIFACT D.01B XML Schema
├── DESADV_Test_Input.xml                              # Tested XML payload
├── sample_DESADV_D01B.edi                             # Raw EDIFACT D.01B message
├── ValidateColdChainQuarantine.groovy                 # Groovy cold-chain telemetry validator
└── IF_GxP_Inbound_DESADV_to_S4HANA_DELVRY03.zip       # Exported SAP CPI iFlow package
```
