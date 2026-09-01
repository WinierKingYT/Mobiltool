# P5 — Power / Background / Thermal

## Goal
Ensure system operates within strict battery and thermal budgets without OEM kills.

## Execution Packages
1. OEM battery killer diagnosis (`OemPowerDiagnostic` for Xiaomi/MIUI, Samsung/OneUI, Huawei/EMUI).
2. Thermal headroom budgeting (`PowerThermalBudgetManager` throttling on low battery or critical heat).
3. WorkManager background constraints (`JobSchedulerHelper`).
4. Leak-proof `WakeLock` lifecycle.

## Exit Gate
```text
[x] OEM power profiles detected
[x] Heavy compute gated if battery < 15% (unplugged)
[x] Thermal throttling headroom enforced
[x] WakeLock leak-free try-finally lifecycle
```
