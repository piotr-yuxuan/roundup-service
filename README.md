# `com.github.piotr-yuxuan/service-template`

## Design choices

### Choice of numeric type

~I initially picked up FLOAT and it was fine~… no, of course I was
conscious that inexact, variable-precision data types are recipe for
disaster when handling money. I contemplated using both DECIMAL(12,0)
and BIGINT in PostgreSQL:

[PostgreSQL DECIMAL(12,0)](https://www.postgresql.org/docs/current/datatype-numeric.html)
- Type: exact numeric with fixed precision and scale
- Storage size: varies dynamically based on the number of digits; typically requires more storage than integer types.
- Value range: from −999,999,999,999 to +999,999,999,999 (12 digits).

[PostgreSQL BIGINT](https://www.postgresql.org/docs/current/datatype-numeric.html)
 - Type: signed 64-bit integer
 - Storage size: 8 bytes
 - Value range: from −9,223,372,036,854,775,808 to +9,223,372,036,854,775,807

After careful comparison of Java numeric type API and datatypes, I've
chosen to settles for long integers, as this precise type has
conveniently the same value range as =BIGINT=.

[Java long](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html)
 - Type: signed 64-bit two's complement integer
 - Storage size: 8 bytes
 - Value range: from −9,223,372,036,854,775,808 to +9,223,372,036,854,775,807

If business required that we use so-called banker's rounding, then I
would redesign the code around the use
`java.math.RoundingMode/HALF_EVEN`.

## Getting started

- Run tests
``` zsh
clojure -X:test/env:test/run
```

- Build the Docker image
``` zsh
VERSION=$(cat resources/service-template.version | tr -d '\n\r')

docker buildx build \
  --build-arg VERSION=${VERSION} \
  --build-context m2repo=$HOME/.m2/ \
  --tag localhost/com.github.piotr-yuxuan.service-template:${VERSION} \
  .
```

- Run it as a Docker container
``` zsh
docker run \
  --network service-template_default \
  -p 3000:3000 \
  localhost/com.github.piotr-yuxuan.service-template:$(cat resources/service-template.version | tr -d '\n\r') \
  --db-hostname postgres
```

Find the network in `docker network ls`. Any option appended at the
end of the command line above is passed down to the uberjar. Remove
the `--help` to get it running instead of just displaying the CLI help
and exit.

## Development

If you don't have Clojure toolchain installed on your machine,
consider opening a REPL from within a vanilla Docker image and mount
your local repository as well as the code of this project. The
following command launches a repl on `localhost:5555`.

``` zsh
docker run \
  -it \
  --rm \
  -p 5555:5555 \
  -v "$HOME/.m2":/root/.m2 \
  -v "$PWD":/usr/src/app \
  -w /usr/src/app \
  clojure:temurin-24-alpine \
  clojure -M:repl
```

The last line is the `bash` command, and can be replaced by `bash` for
an interactive shell.

Depending on your specifics, you may need to set a hostname
`host.docker.internal` to access a service, even maybe to record this
hostname in `/etc/hosts`. See
[documentation](https://docs.docker.com/desktop/features/networking/#i-want-to-connect-from-a-container-to-a-service-on-the-host).

A couple of dependencies are described in `docker-compose.yml`: a
PostgreSQL database, monitoring tools like Grafana with provisioned
dashboards. Launch them with:

``` zsh
docker compose up
```

This launches multiple services related to PostgreSQL, monitoring, and
visualisation. Below are instructions to connect to each service
either via CLI or through a web browser.

### Postgres database

- Browser URL: http://localhost:5431
- CLI connection (from your local machine if `psql` is installed):

``` zsh
psql -h localhost -p 5432 -U user -d database
```

Default credentials are:
- User: user
- Password: password
- Database: database

From a repl you may create a new migration with:

``` clojure
(migratus/create config "create-user")
```

### Prometheus (monitoring and alerting)

- Browser URL: http://localhost:9090

### Grafana (dashboard visualisation)

- Browser URL: http://localhost:3001

Grafana visualises the metrics collected by Prometheus with dashboards
configured for PostgreSQL. Anonymous login is enabled with Admin
rights for ease of access.
