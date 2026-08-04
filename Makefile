SHELL := /bin/sh

.PHONY: help bootstrap infra-up infra-down dev dev-identity dev-seller test verify contracts smoke seed demo

help:
	@echo "MarketFlow development targets"
	@echo "  bootstrap   verify tools and create a local .env from safe examples"
	@echo "  infra-up    start the complete MarketFlow local stack"
	@echo "  infra-down  stop the local stack without deleting volumes"
	@echo "  dev         run the sample service outside Docker"
	@echo "  dev-identity run the Identity service outside Docker"
	@echo "  dev-seller  run the Seller service outside Docker"
	@echo "  test        run Maven tests and quality gates"
	@echo "  verify      run build, contract, and Compose validation"
	@echo "  smoke       verify health and observability endpoints"

bootstrap:
	./scripts/bootstrap.sh

infra-up:
	docker compose up -d --wait

infra-down:
	docker compose down

dev:
	./mvnw -pl services/sample-service spring-boot:run

dev-identity:
	./mvnw -pl services/identity-service spring-boot:run

dev-seller:
	./mvnw -pl services/seller-service spring-boot:run

test:
	./mvnw -B clean verify

contracts:
	./scripts/validate-contracts.sh

verify: test contracts
	docker compose config --quiet

smoke:
	./scripts/smoke-infra.sh

seed:
	@echo "No credentials or privileged users are seeded. Follow the Identity runbook."

demo: smoke
