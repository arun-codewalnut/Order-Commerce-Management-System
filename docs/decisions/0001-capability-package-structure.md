# 1. Capability Package Structure

Status: Accepted
Date: 2026-09-05
Deciders: Engineering

## Context

The application was organized by technical layer, which allowed controllers to access entities and repositories directly and made the order workflow depend on every business area. Issue #1 asks for a maintainable project architecture.

## Decision

Organize business code by capability. Each capability owns `api`, `web`, and `internal` boundaries. Persistence types stay in `internal`; HTTP adapters stay in `web`; shared framework-neutral support remains under `shared`.

The initial capability roots are `customer`, `catalog`, `inventory`, `order`, `payment`, and `notification`. Application composition is isolated under `bootstrap`.

## Why

The owning capability is selected by the data and business decision it owns. This follows the boundary ladder and makes the delete test practical: a capability can be removed without deleting unrelated technical layers.

## Consequences

- New cross-capability dependencies must use a published `api` contract.
- Entities and repositories are no longer available through global packages.
- HTTP and persistence representations can evolve independently.
- Existing endpoints retain their URL paths during the structural migration.

## What We Rejected

We rejected keeping global `controller`, `service`, `repository`, and `entity` packages because that structure hides ownership and permits accidental coupling between business areas.
