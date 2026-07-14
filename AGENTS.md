# AGENTS.md

> A root Router for agents task instructions. This file will only explain details about 'Common Prerequisites',
> 'Origin Source Router' and 'Common Mistake Prevention'.


## Common Prerequisites
- About local dev infrastructure and how to run the project locally, read [docs/local-dev.md](docs/local-dev.md).
- Review and Test must be passed before finishing any task.
  - Only Review may be ignored if the user wish to open a PR forcefully, but should leave a comments about the pr is created without review is accepted.

## Origin Source Router

| About                                                | Source                 |
|------------------------------------------------------|------------------------|
| Any Tech Stack or Dependency                         | `build.gradle`         |
| Product Goal, Business Rules, Acceptance Criterias   | `./docs/PRD.md`        |
| Api Architecture                                    | `./docs/ARCHITECTURE.md` |
| Entities, Entities' tuples, Contraints and Relations | `./docs/ERD.md`        |
| HTTP API Contract                                    | `./docs/API.md`        |
| API, DTO, Exception, Idempotency and Logging Rules   | `./docs/CONVENTION.md` |


## Common Mistake Prevention
- You must write any .md documentation in English, but all logs and outputs to console must be written in Korean.
