# Deployment rollback procedure

This procedure applies to the local Helm/Kubernetes profile and is safe to rehearse without a
cloud account.

1. Stop promotion and record the release, image digest, migration version, and correlation IDs.
2. Inspect readiness, liveness, application error rate, outbox age, broker lag, and smoke output.
3. If the schema is compatible, roll back the Helm release to the last known-good revision:
   `helm rollback marketflow <revision> --wait`.
4. If using Kustomize, restore the previously reviewed manifest/image digest and apply it.
5. Do not roll back an irreversible migration. Use the service owner's forward-recovery migration.
6. Re-run health, smoke, and contract checks before resuming traffic.
7. Preserve logs and event evidence, then document the incident and corrective action.
