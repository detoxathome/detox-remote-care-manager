# Local Build And Start

This guide describes how to build, start, inspect, and collect logs from the
Detox Remote Care Manager development environment on a local workstation.

The structure mirrors the practical sections from the original SenSeeAct local
setup documentation, but uses the renamed Detox Remote Care Manager modules,
services, URLs, and Docker project names.

## Contents

- [Build and start](#build-and-start)
- [Test](#test)
- [Manage](#manage)
- [Docker Desktop](#docker-desktop)
- [Logs](#logs)
- [Enter a container](#enter-a-container)
- [Copy log-files to your workstation](#copy-log-files-to-your-workstation)
- [Stop](#stop)

## Build And Start

From the deployable app module:

```sh
cd DetoxRemoteCareManagerApp
./gradlew webBuild dockerDevBuild dockerDevStart
```

The development Docker setup exposes the web application and supporting
services on local ports. The defaults come from `gradle.properties` in the app
module; if that file does not exist yet, copy `gradle.sample.properties` and
fill in the local secrets before starting.

```sh
cd DetoxRemoteCareManagerApp
cp gradle.sample.properties gradle.properties
```

If Docker reports old `senseeact-*` orphan containers after the rename, stop the
old project once:

```sh
docker compose -p senseeact down --remove-orphans
```

## Test

After `dockerDevStart` succeeds, open the Detox Remote Care Manager web
application:

- Web application: [http://localhost:10000/detoxrcm/](http://localhost:10000/detoxrcm/)
- Backend base URL: [http://localhost:10000/servlets/detoxrcm/](http://localhost:10000/servlets/detoxrcm/)

The default admin account is configured through `ssaconfigAdminEmail` and
`ssaconfigAdminPassword` in
`DetoxRemoteCareManagerApp/gradle.properties`.

## Manage

Useful local management links after the development stack is running:

- Detox Remote Care Manager: [http://localhost:10000/detoxrcm/](http://localhost:10000/detoxrcm/)
- Swagger UI: [http://localhost:10000/servlets/detoxrcm/swagger-ui/index.html](http://localhost:10000/servlets/detoxrcm/swagger-ui/index.html)
- OpenAPI JSON: [http://localhost:10000/servlets/detoxrcm/v3/api-docs](http://localhost:10000/servlets/detoxrcm/v3/api-docs)
- phpMyAdmin: [http://localhost:10001/](http://localhost:10001/)
- Tomcat manager: [http://localhost:10002/manager/html](http://localhost:10002/manager/html)
- MariaDB: `localhost:10004`

Tomcat manager credentials come from `dockerTomcatUser` and
`dockerTomcatPassword` in `DetoxRemoteCareManagerApp/gradle.properties`.
phpMyAdmin uses the MariaDB root password from `ssaconfigMysqlRootPassword`.

## Docker Desktop

In Docker Desktop, look for the `detoxrcm` Compose project. With the default
configuration it contains:

- `detoxrcm-web`
- `detoxrcm-tomcat`
- `detoxrcm-db`
- `phpmyadmin`

If port `10000` is already allocated, an old development stack is usually still
running. Stop the current Detox project with:

```sh
cd DetoxRemoteCareManagerApp
./gradlew dockerStop
```

If the conflict comes from a pre-rename SenSeeAct stack, remove it with:

```sh
docker compose -p senseeact down --remove-orphans
```

## Logs

Run these commands from the repository root.

Follow all development containers:

```sh
docker compose -p detoxrcm -f DetoxRemoteCareManagerApp/compose-dev.yaml logs -f
```

Follow one service:

```sh
docker compose -p detoxrcm -f DetoxRemoteCareManagerApp/compose-dev.yaml logs -f detoxrcm-tomcat
docker compose -p detoxrcm -f DetoxRemoteCareManagerApp/compose-dev.yaml logs -f detoxrcm-web
docker compose -p detoxrcm -f DetoxRemoteCareManagerApp/compose-dev.yaml logs -f detoxrcm-db
```

The application log directory is configured with `dockerLogDir` in
`DetoxRemoteCareManagerApp/gradle.properties`.

## Enter A Container

Open a shell in the Tomcat container:

```sh
docker exec -it detoxrcm-detoxrcm-tomcat-1 sh
```

Open a shell in the web container:

```sh
docker exec -it detoxrcm-detoxrcm-web-1 sh
```

Open a MariaDB client session:

```sh
docker exec -it detoxrcm-detoxrcm-db-1 mariadb -uroot -p
```

## Copy Log-files To Your Workstation

Run these commands from the repository root.

Copy the Detox Remote Care Manager log directory from the Tomcat container to a
local directory:

```sh
mkdir -p ./detoxrcm-log-files
docker cp detoxrcm-detoxrcm-tomcat-1:/var/log/detoxrcm ./detoxrcm-log-files/
```

You can also export the current Docker Compose logs to a single file:

```sh
docker compose -p detoxrcm -f DetoxRemoteCareManagerApp/compose-dev.yaml logs --no-color > ./detoxrcm-log-files/docker-compose.log
```

## Stop

Stop the local development stack:

```sh
cd DetoxRemoteCareManagerApp
./gradlew dockerStop
```
