SHELL := /bin/sh

.PHONY: help bootstrap infra-up infra-down dev test verify contracts smoke seed demo

help:
	@echo "MarketFlow development targets"
	@echo "  bootstrap   verify tools and create a local .env from safe examples"
	@echo "  infra-up    start the complete Milestone 0 local stack"
	@echo "  infra-down  stop the local stack without deleting volumes"
	@echo "  dev         run the sample service outside Docker"
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

test:
	./mvnw -B clean verify

contracts:
	./scripts/validate-contracts.sh

verify: test contracts
	docker compose config --quiet

smoke:
	./scripts/smoke-infra.sh

seed:
	@echo "No business fixtures exist in Milestone 0. This target becomes active with business services."

demo: smoke

