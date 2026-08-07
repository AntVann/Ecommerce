# Local infrastructure

`docker-compose.yml` provides the complete local environment through Milestone 3, including the
identity, seller, catalog, inventory, search, cart, and order services. Durable services retain
separate PostgreSQL databases, while Cart owns an explicitly versioned Redis namespace. Image
versions are explicit so local and CI behavior does not change silently. Local credentials are
development-only placeholders loaded from an ignored `.env` file.

SeaweedFS supplies the S3-compatible emulator because the MinIO community repository was archived
in April 2026. Business services depend on an object-storage port, not this local implementation.

The stack is intentionally single-node and disables transport security inside the isolated Docker
network. Production-style security, replicas, managed stores, and Kubernetes resources belong to
Milestone 6.
