# Performance Evidence

Performance evidence is empirical and environment-specific. The Milestone 7 report in this directory is the authoritative repository record for local load scenarios, query-plan review, and observed limitations.

Do not copy a benchmark number into portfolio prose without its command, fixture, date, environment, and result. The release-candidate report intentionally makes no new production-capacity claim.

Recommended local workflow:

1. Start the disposable Compose environment.
2. Seed only the documented fixture.
3. Run the checked-in load or recovery script.
4. Preserve the output with the environment description.
5. Record failures and limitations as well as passes.

Performance results do not establish hosted availability, production throughput, or an SLO.
