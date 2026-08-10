# Milestone 7 hardening architecture notes

Hardening preserves the existing bounded-context and data-ownership boundaries. It adds evidence,
not product capabilities: local load profiles, controlled dependency failures, backup/restore
procedures, alerts, and release documentation.

The local profile remains single-node and free. PostgreSQL is authoritative per service; Redis and
OpenSearch are rebuildable/read-side infrastructure; Kafka and RabbitMQ are durable integration
boundaries with outbox/inbox protection. No new shared database, gateway bypass, provider adapter,
or external SaaS dependency is introduced by hardening.

The final release report must explicitly document the current absence of a frontend, API Gateway,
and standalone Audit service rather than implying those components exist.
