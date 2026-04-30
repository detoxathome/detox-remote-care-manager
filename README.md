# Detox Remote Care Manager

This repository contains the care professional-facing remote care management system for the Detox@Home project.

It is responsible for configuring, coordinating, and delivering remote care to clients using the Detox@Home mobile application. The system acts as the central layer between care professionals, researchers, and the client-facing app, and integrates with Ons APIs.

## Purpose

The Detox Remote Care Manager enables care professionals and researchers to:
	•	Link client devices to individuals in Ons
	•	Configure and manage self-monitoring tasks for clients
	•	Coordinate remote care delivery through the Detox@Home app
	•	Synchronize task definitions and configurations with client devices

It serves as the control and coordination layer of the Detox@Home ecosystem, complementing the patient-facing application.

## Relation to other components

The Detox@Home system consists of two main parts:
	•	detox-patient-prototype
The patient-facing Android and WearOS application where clients interact with the digital guide and perform self-monitoring.
	•	detox-remote-care-manager (this repository)
The care professional-facing system where remote care is configured, managed, and connected to clinical systems (Ons).

Together, these form a remote care setup in which care professionals configure care, and clients receive and interact with that care in their daily environment.

## Documentation

- [Local build and start guide](docs/local-build-and-start.md)
- [Module overview](docs/module-overview.md)

# Origin: SenSeeAct

This repository is based on a fork of the SenSeeAct platform, originally developed by Roessingh Research and Development￼.

While SenSeeAct provides the technical foundation (secure backend, mobile/web integration, and data handling), this repository has been adapted and extended specifically for the Detox@Home context.

More info about SenSeeAct: https://www.senseeact.com/
