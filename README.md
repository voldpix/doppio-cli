# Doppio

Doppio is a console-first HTTP request runner. It is aimed at projects where request files should live with the codebase, stay readable in a terminal, and run without opening a GUI client.

The core unit is a `.dopo` file under `.doppio/requests`. Doppio resolves requests by shorthand, so a file at `.doppio/requests/auth/login.dopo` can be run as:

```bash
doppio run auth/login.dopo
```

## Project Layout

```text
.doppio/
  local.seed
  requests/
    test.dopo
    auth/
      login.dopo
```

`local.seed` stores default variables as `KEY=value`. Request-local `@var` values override seed values, and seed values override the OS environment.

## Request DSL

```text
@name Login user
@var EMAIL=user@example.com
POST {{BASE_URL}}/auth/login
-h Content-Type=application/json
-q source=doppio

<json|
{
  "email": "{{EMAIL}}",
  "password": "{{PASSWORD}}"
}
|>
```

Supported body blocks:

```text
<| ... |>       # JSON by default
<json| ... |>
<text| ... |>
<csv| ... |>
<form| ... |>   # key=value lines, sent as application/x-www-form-urlencoded
```

Only `#` starts a comment. `@name` and `@var` are metadata, not scripting hooks, and must appear before the request line.

## Commands

```bash
doppio init
doppio list
doppio run test.dopo
doppio run auth/login.dopo --save
doppio clean
```

`--save` writes the rendered run report next to the resolved request file as `<request-name>-<epochMillis>.txt`. `doppio clean` removes those generated report files under `.doppio/requests`.

## Build

```bash
mvn test
mvn -DskipTests package
java -jar target/doppio-1.0-SNAPSHOT.jar --help
```

The implementation uses Java `HttpClient`, picocli, and a small pipeline of parser, template, body, preparation, transport, and formatter steps. That keeps the next native-image/GraalVM pass straightforward.
