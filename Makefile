SHELL := /bin/zsh

.PHONY: help backend-run backend-test backend-check frontend-install frontend-dev frontend-build up down

help:
	@echo "Available targets:"
	@echo "  backend-run       Run backend app"
	@echo "  backend-test      Run backend tests"
	@echo "  backend-check     Run backend quality gates"
	@echo "  frontend-install  Install frontend dependencies"
	@echo "  frontend-dev      Run frontend dev server"
	@echo "  frontend-build    Build frontend"

backend-run:
	cd backend && ./gradlew bootRun

backend-test:
	cd backend && ./gradlew test

backend-check:
	cd backend && ./gradlew spotlessCheck spotbugsMain test jacocoTestCoverageVerification -x spotlessApply

frontend-install:
	cd frontend && pnpm install

frontend-dev:
	cd frontend && pnpm dev

frontend-build:
	cd frontend && pnpm build
