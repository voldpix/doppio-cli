# Doppio

Doppio is a console-first HTTP request runner. Request files live with the repo, stay readable in a terminal, and can be run by humans, CI, or coding agents without opening a GUI client.

The core unit is a `.dopo` file under `.doppio/requests`. Doppio resolves requests by shorthand, so `.doppio/requests/auth/login.dopo` can be executed as:

```bash
doppio run auth/login
```

The `.dopo` extension is optional inside a Doppio project for `run`, `show`, `preview`, and `rm`. Standalone request files outside a project must use the full filename.

## Quick Start

```bash
doppio init
doppio gen --env dev
doppio gen auth/login --method POST --body json --bearer
doppio ls
doppio shell --env dev
doppio doctor --env dev
doppio preview auth/login --env dev
doppio run auth/login --env dev
```

For a machine-readable agent workflow:

```bash
doppio ls
doppio ls --json
doppio doctor --env dev
doppio check --env dev
doppio show auth/login --json
doppio preview auth/login --env dev --json
doppio run auth/login --env dev --json
```

`doppio docs` prints the built-in syntax and command reference.

## Project Layout

```text
.doppio/
  default.seed
  envs/
    dev.seed
    staging.seed
  requests/
    test.dopo
    auth/
      login.dopo
```

`doppio init` creates `.doppio/default.seed`, `.doppio/envs/`, `.doppio/requests/example.dopo`, and `.doppio/requests/test.dopo`, then prints the full `.doppio` path as a tree.

`default.seed` stores default variables as `KEY=value`:

```text
BASE_URL=https://api.example.com
TOKEN=secret-token
EMAIL="me@example.com"
```

Blank lines and `#` comments are allowed. Whitespace around keys and values is trimmed. Matching single or double quotes around values are stripped.

Variable precedence is:

```text
@var in the request file > selected .doppio/envs/<name>.seed > .doppio/default.seed > OS environment
```

Create env files with:

```bash
doppio gen --env dev
doppio gen --env staging
```

Then use them for variable hydration:

```bash
doppio preview auth/login --env dev
doppio run auth/login --env dev
doppio check --env dev
doppio doctor --env dev
```

## Request DSL

```text
@name Login user
@var EMAIL=user@example.com
@expect status=200
@expect header Content-Type contains json
@expect body contains token
POST {{BASE_URL}}/auth/login
-h Content-Type=application/json
-h Authorization=Bearer {{TOKEN}}
-q source=doppio

<json|
{
  "email": "{{EMAIL}}",
  "password": "{{PASSWORD}}"
}
|>
```

Supported request directives:

```text
GET|POST|PUT|PATCH|DELETE https://example.com/path
-h key=value
header key=value
-q key=value
query key=value
```

Supported metadata before the request line:

```text
@name Human readable request name
@var KEY=value
@expect status=200
@expect header Content-Type contains json
@expect body contains token
```

Only `#` starts a comment. `@name` and `@var` are metadata, not scripting hooks, and must appear before the request line.

## Body Blocks

```text
<| ... |>       JSON by default
<json| ... |>   application/json
<text| ... |>   text/plain; charset=utf-8
<csv| ... |>    text/csv; charset=utf-8
<form| ... |>   application/x-www-form-urlencoded
```

Form bodies use line-based `key=value` content:

```text
<form|
email=user@example.com
remember=true
|>
```

Blank lines and `#` comments inside body blocks are ignored. Doppio adds the default `Content-Type` for typed bodies unless the request already sets one.

## Commands

```bash
doppio init
doppio docs
doppio shell
doppio shell --env dev
doppio shell --project /path/to/project
doppio gen --env dev
doppio list
doppio ls
doppio show auth/login
doppio preview auth/login --env dev
doppio run auth/login --env dev
doppio format
doppio format auth/login
doppio format auth
doppio check --env dev
doppio check auth/login --env dev
doppio check auth --env dev
doppio doctor --env dev
doppio run auth/login --save
doppio clean
doppio rm auth/login
```

`doppio list` and `doppio ls` print the full `.doppio` path on top and then a request tree without exposing the internal `requests` folder as part of the shorthand.

`doppio show` inspects the raw request file without hydration or network execution. It is useful before editing a request.

`doppio preview` hydrates variables, validates the body, prepares the final URL, headers, query params, and body, then stops before HTTP execution.

`doppio run` executes the request and prints request details, response status, elapsed time, response headers, and response body. Non-2xx HTTP responses still print response details and exit with failure.

`--env <name>` is supported by `run`, `preview`, `check`, and `doctor`. It loads `.doppio/envs/<name>.seed` after `default.seed` and before request-local `@var` values. `show`, `format`, `list`, `rm`, `clean`, and `docs` do not need env selection.

`doppio format` formats `.dopo` files. With no target it formats every request under `.doppio/requests`. With a target it accepts the same project shorthand style, so `doppio format auth/login`, `doppio format auth/login.dopo`, and `doppio format auth` work inside a Doppio project.

`doppio check` validates `.dopo` files without executing HTTP. It uses the same shorthand behavior as `format`, so it can check all requests, a folder, or one file. It catches parse errors, missing variables, invalid URLs, invalid JSON/form bodies, and malformed expectations.

`doppio doctor` inspects the current Doppio project and prints pass/warn/fail findings for project discovery, seed files, the requests folder, request count, and no-network request validation. It is the fastest sanity check when a repo has been cloned or generated by an agent.

`--save` writes the rendered run report next to the resolved request file as `<request-name>-<epochMillis>.txt`. `doppio clean` removes generated report files under `.doppio/requests`. `doppio rm` moves request files to `.doppio/trash` instead of deleting them outright.

## Interactive Shell

`doppio shell` opens a command-driven REPL for working from one terminal instead of switching between separate list, edit, and run windows.

```bash
doppio shell
doppio shell --env dev
doppio shell --project /Users/me/project
```

On startup it prints the resolved `.doppio` path and then prompts as `doppio:[default]>` or `doppio:[dev]>`. The prompt, env state, edit paths, and run result are colorized unless `NO_COLOR` is set. Shell history is stored in `~/.config/doppio/history`. Recent project paths are stored in `~/.config/doppio/status.json` so running `doppio shell` outside a project can offer a picker. If no project can be found it exits with:

```text
No Doppio project found. Navigate to a Doppio project or run doppio init first.
```

Request commands accept the same shorthand as the CLI. Stem lookup also works, so `run login` can find `.doppio/requests/auth/login.dopo`. If more than one request matches, the shell asks you to choose.

```text
doppio:[default]> ls
doppio:[default]> gen auth/login --method POST --body json --bearer
doppio:[default]> edit auth/login
doppio:[default]> preview login
doppio:[default]> run login
doppio:[default]> body
doppio:[default]> save
doppio:[default]> format auth
doppio:[default]> check auth --env dev
doppio:[default]> rm auth/login
```

Seed and env helpers are available inside the same session:

```text
doppio:[default]> seed list
doppio:[default]> seed edit default
doppio:[default]> seed gen dev
doppio:[default]> seed edit dev
doppio:[default]> env use dev
doppio:[dev]> run login
doppio:[dev]> env clear
```

Editor helpers let you configure one terminal workflow once:

```text
doppio:[default]> editor show
doppio:[default]> editor use nano
doppio:[default]> editor use "code -w"
doppio:[default]> editor clear
```

Editor precedence is `DOPPIO_EDITOR`, then `~/.config/doppio/config.json`, then `$VISUAL`, then `$EDITOR`, then detected `nano`, `vim`, or `vi` from `PATH`. GUI editors should usually include a wait flag, for example `code -w`. If no editor is available, the shell prints the exact setup commands and keeps running. Multiple shells can point at the same project; V1 does not add project locking.

## Checks And Expectations

Expectations are small response checks declared before the request line:

```text
@expect status=200
@expect header Content-Type contains json
@expect body contains "token"
```

They run after HTTP execution. A non-2xx response still fails as before, and a 2xx response also fails when any expectation fails. Body checks treat the response body as plain text for now; nested JSON extraction can come later without changing the command flow.

`doppio check` is intentionally no-network. It validates the request file shape and body processing, but it does not evaluate response expectations because there is no response yet.

## Formatting

The formatter is intentionally conservative. It validates before writing and reports changed, unchanged, and failed files.

```bash
doppio format
doppio format auth/login
doppio format auth
```

JSON bodies are pretty-printed with 2-space indentation. Full-line `#` comments inside JSON keep their relative position, which lets you comment out actual JSON lines while testing request variants:

```text
<json|
{
  # "username": "{{USERNAME}}",
  "password": "{{PASSWORD}}"
}
|>
```

Inline JSON comments are not supported in this pass. Text and CSV bodies have trailing whitespace trimmed. Form bodies preserve full-line comments and normalize `key=value` spacing.

## Generation Shortcuts

`doppio gen` creates editable request placeholders under `.doppio/requests`.

```bash
doppio gen auth/login
doppio gen --env dev
doppio gen users/me --method GET --bearer
doppio gen auth/login --method POST --body form --bearer
doppio gen auth/login --body form -H X-Client=doppio -q source=doppio
doppio gen export/users --body csv
doppio gen notes/ping --body text
doppio gen jobs/start --method POST --body none
doppio gen auth/login --from-curl "curl -H 'Accept: application/json' https://api.example.com/auth/me"
```

Defaults:

```text
no --method means POST
GET and DELETE default to no body
POST, PUT, and PATCH default to JSON
--bearer adds Authorization=Bearer {{TOKEN}}
-H/--header adds -h lines
-q/--query adds -q lines, including key=value or flag-style query params
```

`--from-curl` is intentionally basic in this pass. It supports common curl examples with URL, `--url`, `-X/--request`, `-H/--header`, `-d/--data/--data-raw/--data-binary`, and ignores common transport flags such as `--location` and `--silent`. It extracts URL query params into `-q` lines and maps simple JSON, text, and URL-encoded form bodies into typed Doppio body blocks. Multipart, cookies, shell expansion, config files, auth helpers, and advanced curl flags fail clearly instead of generating a misleading request.

## JSON Output

Use JSON output when a script or coding agent needs stable fields instead of terminal formatting:

```bash
doppio ls --json
doppio show auth/login --json
doppio preview auth/login --env dev --json
doppio run auth/login --env dev --json
```

Recommended agent flow:

```bash
doppio ls
doppio ls --json
doppio doctor --env dev
doppio check --env dev
doppio show auth/login --json
doppio preview auth/login --env dev --json
doppio run auth/login --env dev --json
```

`ls --json` reports request names, paths, and parse markers. `show --json` reports raw request structure and local variables. `preview --json` reports the final hydrated request without network execution. `run --json` includes response status, headers, body, duration, and failure status for non-2xx responses.

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

## GitHub Release Build

Pushing a version tag creates a GitHub Release and attaches native macOS and Linux archives:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The release workflow builds both native executables with GraalVM, verifies `./doppio --help`, packages each executable with a small README, and uploads the archives as release assets.

The implementation uses Java `HttpClient`, picocli, and a small pipeline of parser, template, body, preparation, transport, and formatter steps. That keeps the next native-image/GraalVM pass straightforward.
