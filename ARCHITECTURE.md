# API Architecture

## Overview

This API uses a three-layer architecture. Each domain feature owns its controller, service, repository, entity, and DTO packages so that related code stays together.

```text
Client
  -> Controller layer
  -> Service layer
  -> Repository layer
  -> Database or external persistence store
```

Dependencies must flow only in this direction. A repository must not depend on a service or controller, and an entity must not depend on controller, service, or repository code.

## Package Hierarchy

All domain code is organized feature-first under the application's base package:

```text
dev.nbcsparta.assignment.nbccoffeeordersystem
├── domain
│   ├── menu
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   ├── order
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   └── user
│       ├── controller
│       ├── service
│       ├── repository
│       ├── entity
│       └── dto
└── global
    ├── config
    ├── exception
    └── response
```

`global` contains cross-cutting technical concerns only. Domain-specific code must remain in its domain package.

## Layer Responsibilities

### Controller

- Defines HTTP endpoints and handles request validation and HTTP-specific concerns.
- Converts requests to DTOs and returns the standard API response.
- Delegates business work to a service or facade.
- Does not access repositories or implement business rules.

### Service

- Implements business rules for one domain responsibility.
- Uses exactly one directly related repository. For example, `MenuService` uses `MenuRepository` and `UserService` uses `UserRepository`.
- Owns transaction boundaries for its domain operation when the operation is not coordinated by a facade.
- Does not depend on controllers or unrelated repositories.

### Repository

- Encapsulates persistence access for its related entity or aggregate.
- Is called only by its owning service.
- Contains persistence queries, not business orchestration.

### Entity

- Represents the persisted domain model and its intrinsic state and behavior.
- Is not returned directly from an API endpoint.

### DTO

- Defines request and response data at the API boundary.
- Is separate from entities so persistence details are not exposed to clients.
- Follows the DTO and response rules in [docs/CONVENTION.md](docs/CONVENTION.md).

## Repository Ownership and Facades

A domain service must not inject or call more than one repository. If a use case needs data or mutations from multiple repositories, introduce a facade in the relevant domain's `service` package.

The facade coordinates the required single-repository services. It does not access repositories directly and does not duplicate their domain rules. The controller calls the facade for the cross-domain use case.

```text
OrderController
  -> OrderFacade
      -> UserService  -> UserRepository
      -> MenuService  -> MenuRepository
      -> OrderService -> OrderRepository
```

For example, creating an order may require validating a menu, deducting user points, and saving an order. `OrderFacade` coordinates those operations, while `MenuService`, `UserService`, and `OrderService` each retain ownership of exactly one related repository.

## Implementation Rules

- Prefer one service per domain responsibility and one repository per service.
- Name a facade after the coordinated use case, such as `OrderFacade` or `OrderPlacementFacade`.
- Keep facade methods focused on application-level orchestration.
- Keep request/response DTOs in the owning domain's `dto` package; request and response subpackages may be added when the domain grows.
- Do not bypass the service layer by injecting a repository into a controller or facade.
