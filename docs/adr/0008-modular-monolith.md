# ADR-0008 - One modular monolith, not microservices

- **Status:** accepted
- **Date:** 2026-11-09

## Context
The site has about 15 features (games, puzzles, payments, ...). It must run on a 1 GB free-tier machine and be easy to understand when coming back to it after months.

## Decision
- **One Spring Boot application,** with one top-level package per feature.
- **Features communicate** through services and Spring events, never through another feature's repositories.
- **ArchUnit tests** enforce this (`ArchitectureTest`).

## Consequences
- One process to deploy, debug and monitor, and transactions are simple.
- The boundaries are real, so a feature can be split out later if it ever needs to scale alone.

## Alternatives considered
- **Microservices:** they need far more RAM and operations work, plus network calls between features, for no benefit at this size.
