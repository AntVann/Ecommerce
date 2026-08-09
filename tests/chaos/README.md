# Local failure and recovery drills

Milestone 7 chaos tests are intentionally bounded to the local Compose stack. They stop one
dependency or service at a time, observe the expected degraded behavior, restart it, and wait for
readiness. They never delete volumes or databases.

Run from a disposable local environment:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\chaos-local.ps1
```

The fake payment provider and fake email provider have deterministic timeout, duplicate, transient,
and permanent-failure unit/integration scenarios in their service test suites. Record their test
commands and recovery evidence in the Milestone 7 completion report.
