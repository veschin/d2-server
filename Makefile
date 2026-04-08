# Default port, can be overridden e.g., `make DP_PORT=9090 docker-run`
DP_PORT ?= 3000
CONTAINER_NAME = d2server-app

.PHONY: run test uberjar docker-build docker-export docker-run docker-stop compose-up compose-down compose-test

run:
	clj -M:run

test:
	clj -M:test

uberjar:
	clj -T:build uber

docker-build:
	docker build -t d2server .

docker-export: docker-build
	@docker save d2server | gzip > /tmp/d2server.tar.gz
	@echo "Exported to /tmp/d2server.tar.gz ($$(du -h /tmp/d2server.tar.gz | cut -f1))"

# Runs the container, tests endpoints with httpie, and then stops the container.
# Requires httpie to be installed (https://httpie.io/docs/cli/installation)
docker-run:
	@echo "--- Starting container [$(CONTAINER_NAME)] for testing ---"
	@docker run -d --rm --name $(CONTAINER_NAME) -p $(DP_PORT):3000 d2server
	@echo "Waiting for server to initialize..."
	@sleep 5

	@echo "\n--- Testing Health Endpoint ---"
	@http --check-status --timeout=10 GET http://localhost:$(DP_PORT)/ \
		&& echo "\nHealth check OK" || (echo "\nHealth check FAILED"; make docker-stop; exit 1)

	@echo "\n--- Testing Render SVG ---"
	@http --check-status --timeout=10 GET "http://localhost:$(DP_PORT)/svg?d2=a -> b" \
		&& echo "\n/svg OK" || (echo "\n/svg FAILED"; make docker-stop; exit 1)

	@echo "\n--- Testing Render PNG ---"
	@http --check-status --timeout=30 --download GET "http://localhost:$(DP_PORT)/png?d2=a -> b" \
		&& echo "\n/png OK" || (echo "\n/png FAILED"; make docker-stop; exit 1)

	@echo "\n--- Testing Format ---"
	@http --check-status --timeout=10 GET "http://localhost:$(DP_PORT)/format?d2=a->b" \
		&& echo "\n/format OK" || (echo "\n/format FAILED"; make docker-stop; exit 1)

	@echo "\n--- All tests passed, stopping container ---"
	@make docker-stop

# Utility target to stop the container if it's left running
docker-stop:
	@echo "Stopping and removing container [$(CONTAINER_NAME)]..."
	@docker stop $(CONTAINER_NAME) >/dev/null 2>&1 && docker rm $(CONTAINER_NAME) >/dev/null 2>&1 || echo "Container not found or already stopped."

# Docker Compose targets
compose-up:
	@echo "Starting services with Docker Compose..."
	@docker compose up -d
	@echo "Service started: http://localhost:$(DP_PORT)"

compose-down:
	@echo "Stopping Docker Compose services..."
	@docker compose down

compose-test:
	@echo "--- Starting services for testing ---"
	@docker compose up -d
	@echo "Waiting for service to initialize..."
	@sleep 10

	@echo "\n--- Testing Health Endpoint ---"
	@http --check-status --timeout=10 GET http://localhost:$(DP_PORT)/ \
		&& echo "\nHealth check OK" || (echo "\nHealth check FAILED"; make compose-down; exit 1)

	@echo "\n--- Testing Render SVG ---"
	@http --check-status --timeout=10 GET "http://localhost:$(DP_PORT)/svg?d2=a -> b" \
		&& echo "\n/svg OK" || (echo "\n/svg FAILED"; make compose-down; exit 1)

	@echo "\n--- Testing Render PNG ---"
	@http --check-status --timeout=30 --download GET "http://localhost:$(DP_PORT)/png?d2=a -> b" \
		&& echo "\n/png OK" || (echo "\n/png FAILED"; make compose-down; exit 1)

	@echo "\n--- Testing Format ---"
	@http --check-status --timeout=10 GET "http://localhost:$(DP_PORT)/format?d2=a->b" \
		&& echo "\n/format OK" || (echo "\n/format FAILED"; make compose-down; exit 1)

	@echo "\n--- All tests passed, stopping services ---"
	@make compose-down

