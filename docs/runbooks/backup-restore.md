# Backup and restore runbook

## Local backup

1. Start the disposable Compose environment.
2. Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\backup\backup-postgres.ps1`.
3. Store the output directory outside the repository and protect it with local filesystem access
   controls. The manifest contains checksums but no credentials.
4. Do not commit dumps, manifests containing local paths, or generated reports.

## Restore validation

1. Run `validate-postgres-restore.ps1 -BackupDirectory <backup-directory>`.
2. The script creates a disposable PostgreSQL container per dump and restores with `pg_restore`.
3. Compare migration versions, core row counts, order/inventory invariants, and outbox records.
4. Rebuild Search from Catalog data and run the local smoke suite.
5. Remove only the disposable restore containers created by the script.

Redis carts/rate limits and OpenSearch documents are rebuildable. Kafka replay and notification
outbox recovery must be validated separately. This is a local recovery demonstration, not a
production backup or disaster-recovery guarantee.
