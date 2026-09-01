# P6 — Security / Storage / Recovery

## Goal
Protect sensitive local data and survive crashes/upgrades.

## Execution Packages
1. Data classification & encryption ADR.
2. Keystore hardware-backed AES-256-GCM encryption (`KeystoreHelper`, `KeystoreVaultEncryptor`).
3. Crash-safe canonical file commit before DB write.
4. Direct Boot boundary review (`DirectBootVaultManager` DE -> CE migration).
5. Explicit Room migrations & test suite.
6. Backup rules and sensitive log redaction.
7. Delete cascade and staging cleanup.

## Rules
- no custom crypto
- no keys in source/assets/BuildConfig/logs
- file commit before DB success
- no destructive DB fallback.

## Exit Gate
```text
[x] keys protected
[x] encryption path verified
[x] migration suite
[x] backup rules
[x] crash/low-storage tests
[x] recovery deterministic
[x] delete cascade
[x] no plaintext secret logging
```
