# Production Deploy

This guide describes how to build, transfer, start, inspect, and stop a
production Detox Remote Care Manager deployment without Jenkins.

The deployable application lives in `DetoxRemoteCareManagerApp`. Production
configuration is read from `DetoxRemoteCareManagerApp/gradle.properties` during
the Docker build and written into the packaged application as
`service.properties`.

## Contents

- [Prepare configuration](#prepare-configuration)
- [Build](#build)
- [Transfer](#transfer)
- [Start](#start)
- [Verify](#verify)
- [Logs](#logs)
- [Update](#update)
- [Stop](#stop)

## Prepare Configuration

Create or update the production Gradle properties file on the machine that will
run the build:

```sh
cd DetoxRemoteCareManagerApp
cp gradle.sample.properties gradle.properties
```

Fill in the production values in `gradle.properties`. Do not commit this file.
It contains secrets and deployment-specific values.

For production, set the mobile environment explicitly:

```properties
ssaconfigMobileEnvironment=production
```

This is the value used by the middleware to identify the deployment as
production. It also enables production-only behaviour such as the general
Healthchecks.io middleware ping. Local, development, custom, empty, or
unconfigured environments keep that ping disabled unless
`ssaconfigHealthchecksGeneralPingEnabled=true` is set explicitly.

Common production values to review:

```properties
ssaconfigBaseUrl=https://example.org/servlets/detoxrcm
ssaconfigWebUrl=https://example.org/detoxrcm
ssaconfigMobileEnvironment=production
ssaconfigMobileApiBaseUrl=https://example.org/servlets/detoxrcm/v6.1.0
ssaconfigDataDir=/var/log/detoxrcm

dockerProject=detoxrcm
dockerTag=...
dockerLogDir=/var/log/detoxrcm
```

The build also needs Docker and Tomcat manager credentials in
`gradle.properties`, plus `google-application-credentials.json` in
`DetoxRemoteCareManagerApp`.

To check the value that Gradle will use:

```sh
./gradlew -q properties --property ssaconfigMobileEnvironment
```

## Build

Run the production Docker build from the app module:

```sh
cd DetoxRemoteCareManagerApp
./gradlew dockerBuild buildDockerCompose
```

`dockerBuild` builds the production Tomcat and web images using the configured
`dockerTag`. `buildDockerCompose` writes the production Compose bundle to:

```text
DetoxRemoteCareManagerApp/build/docker-compose
```

## Transfer

If the build runs on the production server, you can skip this section.

If the build runs on another machine, save the images and copy both the image
archive and Compose bundle to the production server:

```sh
cd DetoxRemoteCareManagerApp
dockerTag=$(grep "^dockerTag=" gradle.properties | tr -d "\r\n" | sed -e "s/^dockerTag=//")
docker save -o detoxrcm_docker_images.tar \
	detoxrcm-tomcat:$dockerTag detoxrcm-web:$dockerTag \
	phpmyadmin:fpm-alpine mariadb:11

scp detoxrcm_docker_images.tar user@production-host:~
scp -r build/docker-compose user@production-host:~
```

## Start

On the production server, load the image archive if it was transferred:

```sh
docker load --input detoxrcm_docker_images.tar
```

Then start the stack:

```sh
cd docker-compose
docker compose -p detoxrcm up -d
```

If `dockerProject` is not `detoxrcm`, use that configured project name instead
of `detoxrcm`.

## Verify

Check that all containers are running:

```sh
docker compose -p detoxrcm ps
```

Open the web application and backend status pages:

- Web application: `https://example.org/detoxrcm/`
- Backend base URL: `https://example.org/servlets/detoxrcm/`
- Public status page: `https://example.org/detoxrcm/status`

Confirm the generated configuration contains the production environment:

```sh
docker exec detoxrcm-detoxrcm-tomcat-1 \
	grep "^mobileEnvironment=" "/usr/local/tomcat/webapps/servlets#detoxrcm/WEB-INF/classes/service.properties"
```

Expected output:

```text
mobileEnvironment=production
```

## Logs

Follow all production containers:

```sh
cd docker-compose
docker compose -p detoxrcm logs -f
```

Follow the Tomcat application log:

```sh
docker compose -p detoxrcm logs -f detoxrcm-tomcat
```

The application log directory on the host is configured by `dockerLogDir` in
`gradle.properties`.

## Update

For a new production release:

1. Update the code on the build machine.
2. Review `DetoxRemoteCareManagerApp/gradle.properties`.
3. Run `./gradlew dockerBuild buildDockerCompose`.
4. Transfer and load the new images if the build happened elsewhere.
5. Run `docker compose -p detoxrcm up -d` from the production Compose directory.
6. Verify the containers, web app, logs, and status page.

Docker Compose will recreate containers whose image tag or configuration
changed while keeping named volumes such as MariaDB data.

## Stop

Stop the production stack from the production Compose directory:

```sh
cd docker-compose
docker compose -p detoxrcm down
```

This stops and removes the containers, but keeps the named volumes unless you
explicitly remove them.
