# Module Overview

This repository is split into reusable platform modules and Detox-specific
application modules. The goal is to keep generic client and backend logic
separate from the deployable app assembly for Detox@Home.

## Modules

- `DetoxClient`
  Shared Java client/API module. It contains the reusable client classes,
  models, sync logic, and request handling used to talk to the backend.

- `DetoxClientExtensions`
  Detox-specific additions on top of `DetoxClient`. This is where
  deployment-specific client configuration lives, such as mobile-app
  registration and project-specific client wiring.

- `DetoxRemoteCareManagerLibrary`
  Shared library code used by the backend modules. This module contains common
  support code that is broader than only service endpoints, so `Library` is a
  better description than the earlier `ServiceLib` name.

- `DetoxRemoteCareManagerService`
  The reusable backend/service implementation. It contains the service logic,
  controllers, integration tests, and shared web assets that make up the core
  Remote Care Manager backend behavior.

- `DetoxRemoteCareManagerApp`
  The deployable application layer. This module packages the actual Spring Boot
  application, configuration, Docker setup, compose files, and other
  environment-specific assembly needed to run the Detox Remote Care Manager.

## Dependency Flow

The module graph is roughly:

```text
DataAccessObjects
  -> DetoxClient
     -> DetoxRemoteCareManagerLibrary
        -> DetoxRemoteCareManagerService
           -> DetoxRemoteCareManagerApp

DetoxClient
  -> DetoxClientExtensions
     -> DetoxRemoteCareManagerApp
```

## Practical Meaning

- Edit `DetoxClient` for generic API/client changes.
- Edit `DetoxClientExtensions` when the client behavior is Detox-specific.
- Edit `DetoxRemoteCareManagerLibrary` for shared backend support code.
- Edit `DetoxRemoteCareManagerService` for reusable service behavior.
- Edit `DetoxRemoteCareManagerApp` for deployment, branding, configuration, and
  application assembly.

## Historical Context

This layout comes from an earlier SenSeeAct-derived structure. Some Java
package names, class names, artifact names, and comments still use that older
terminology. The module names in this repository were renamed to better
reflect their current Detox Remote Care Manager roles while keeping the deeper
codebase stable.

For reference, the module renames are:

- `SenSeeActClient` -> `DetoxClient`
- `ExampleSenSeeActClient` -> `ExampleDetoxClient` -> `DetoxClientExtensions`
- `SenSeeActServiceLib` -> `DetoxRemoteCareManagerServiceLib` ->
  `DetoxRemoteCareManagerLibrary`
- `SenSeeActService` -> `DetoxRemoteCareManagerService`
- `ExampleSenSeeActService` -> `ExampleDetoxRemoteCareManagerService` ->
  `DetoxRemoteCareManagerApp`
