# ADR-001: B2B Inbound DESADV Architecture for GxP Supply Chain

## Status
ACCEPTED - Version 1.1.0

## Context
BioPharma Global AG requires compliant ingestion of inbound Advanced Shipping Notifications (ASN) from 3PL carrier DHL Life Sciences. The solution must enforce strict temperature cold-chain controls and provide automated failover resilience.

## Decision: Option C - Hybrid Architecture
1. Inbound AS2 with SHA-256 synchronous signed MDN for non-repudiation (FDA 21 CFR Part 11).
2. Pre-mapping Groovy inspection for automated quarantine stock classification (BESTQ = 'S').
3. Dead-letter automated failover to secure SFTP audit storage during S/4HANA backend downtime.
