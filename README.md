# Doppio

Doppio is a console-first HTTP request runner. It is aimed at projects where request files should live with the codebase, stay readable in a terminal, and run without opening a GUI client.

The core unit is a `.dopo` file under `.doppio/requests`. Doppio resolves requests by shorthand, so a file at `.doppio/requests/auth/login.dopo` can be run as:

```bash
doppio run auth/login
```

The `.dopo` extension is optional for request shorthands inside a Doppio project, including `run`, `show`, and `rm`. Standalone request files outside a project must use the full filename.

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
doppio gen auth/login
doppio gen users/me --method GET --bearer
doppio gen auth/login --body form -H X-Client=doppio -q source=doppio
doppio list
doppio ls
doppio show auth/login
doppio run test
doppio run auth/login --save
doppio clean
doppio rm auth/login
```

`doppio gen` creates editable request placeholders under `.doppio/requests`, and `doppio show` inspects metadata, method, URL, headers, query params, local variables, and body type without executing HTTP.

`doppio gen` defaults to `POST` with a JSON body. `GET` and `DELETE` default to no body, while `POST`, `PUT`, and `PATCH` default to JSON unless `--body none|json|text|csv|form` is provided. `--bearer` adds `Authorization=Bearer {{TOKEN}}`, and `-H/--header` plus `-q/--query` add request directives to the generated file.

`--save` writes the rendered run report next to the resolved request file as `<request-name>-<epochMillis>.txt`. `doppio clean` removes those generated report files under `.doppio/requests`. `doppio rm` moves request files to `.doppio/trash` instead of deleting them outright.

## Build

```bash
mvn test
mvn -DskipTests clean package
java -jar target/doppio-1.0-SNAPSHOT.jar --help
```

## Native Build

```bash
scripts/build-native.sh
dist/native/doppio --help
```

The native build uses Docker with the GraalVM native-image community container. The script packages the Maven jar, builds a Linux native executable inside Docker, and copies it to `dist/native/doppio`. The `dist/` folder is ignored because it is a generated build artifact.

Docker must be installed and running before invoking the script.

## GitHub Actions Native Build

The `Native Build` workflow is manual-only. Trigger it from the GitHub Actions tab to build native executables on standard Ubuntu and macOS runners and download the uploaded artifacts:

- `doppio-linux-x64`
- `doppio-macos`

Each artifact contains a `.tar.gz`; extract it and run `./doppio --help`.

Public repositories can use standard GitHub-hosted runners for free, but artifact storage still counts against GitHub Actions storage limits. The workflow keeps artifacts for 7 days.

The implementation uses Java `HttpClient`, picocli, and a small pipeline of parser, template, body, preparation, transport, and formatter steps. That keeps the next native-image/GraalVM pass straightforward.
