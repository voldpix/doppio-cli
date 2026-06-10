package dev.voldpix.doppio.cli;

import picocli.CommandLine.Command;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

@Command(name = "docs", mixinStandardHelpOptions = true, description = "Print Doppio syntax and command reference.")
public class DocsCommand implements Callable<Integer> {
    private static final String DOCS = """
        Doppio Docs

        What Doppio Is
          Doppio is a repo-native HTTP client for humans, CI, and coding agents.
          Request files live in .doppio/recipes and use the .dopo extension.

        Project Layout
          .doppio/
            default.seed
            seeds/
              dev.seed
            recipes/
              example.dopo
              auth/login.dopo

        Seed Files
          .doppio/default.seed uses KEY=value lines.
          Blank lines and # comments are allowed.
          Matching single or double quotes around values are stripped.

          Example:
            BASE_URL=https://api.example.com
            TOKEN=secret-token
            EMAIL="me@example.com"
            API_URL={{BASE_URL}}/v1

          Seed values can reference other seed values with {{KEY}}.
          default.seed resolves against itself.
          Selected seed overlays resolve against default.seed plus their own values.
          OS environment variables are not used while resolving seed files.

        Variables
          Use {{KEY}} placeholders in URLs, headers, query params, and bodies.
          Precedence: request @var > selected seed overlay > default.seed > OS environment.

          Example:
            @var EMAIL=me@example.com
            GET {{BASE_URL}}/users/me
            -h Authorization=Bearer {{TOKEN}}

        Seed Overlays
          doppio gen --env dev creates .doppio/seeds/dev.seed.
          Use --env with run, preview, check, and doctor:
            doppio preview auth/login --env dev
            doppio run auth/login --env dev
            doppio check --env dev
            doppio doctor --env dev

        Request Files
          @name Login user
          @var EMAIL=me@example.com
          @expect status=200
          @expect header Content-Type contains json
          @expect body contains token
          POST {{BASE_URL}}/auth/login
          -h Content-Type=application/json
          -q source=doppio

          <json|
          {
            "email": "{{EMAIL}}",
            "password": "{{PASSWORD}}"
          }
          |>

        Body Blocks
          <| ... |>       JSON by default
          <json| ... |>   application/json
          <text| ... |>   text/plain; charset=utf-8
          <csv| ... |>    text/csv; charset=utf-8
          <form| ... |>   application/x-www-form-urlencoded, key=value lines

        Comments
          # starts a comment outside body blocks.
          Full-line # comments are ignored in JSON and form bodies.
          Text and CSV bodies preserve literal # lines.

        Common Commands
          doppio init
          doppio shell
          doppio shell --env dev
          doppio shell --project /path/to/project
          doppio gen --env dev
          doppio gen auth/login
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

        Interactive Shell
          doppio shell opens a command-driven REPL for one-terminal request work.
          On startup it prints the resolved .doppio path and prompts as:
            doppio:[default]>
            doppio:[dev]>

          The shell uses JLine for arrows, history, and local tab completion.
          The prompt, seed state, edit paths, and run result are colorized unless NO_COLOR is set.
          History is stored in ~/.config/doppio/history.
          Recent projects are stored in ~/.config/doppio/status.json.
          User shell config is stored in ~/.config/doppio/config.json.

          Request lookup supports exact shorthand and stem-only matching:
            run auth/login
            run login

          If multiple requests match, the shell asks for a numbered choice.
          Running shell outside a project uses the recent project picker when available.
          If no project is found, it prints:
            No Doppio project found. Navigate to a Doppio project or run doppio init first.

          Shell request commands:
            ls | list
            gen <request> [options]
            edit [request]
            show [request]
            preview [request] [--env NAME]
            run [request] [--env NAME]
            body
            save
            format [request-or-folder]
            check [request-or-folder] [--env NAME]
            rm [request]

          Shell seed commands:
            seed list
            seed use dev
            seed clear
            seed edit default
            seed gen dev
            seed edit dev
            seed rm dev
            config editor show
            config editor use nano
            config editor use "code -w"
            config editor clear

          Editor precedence:
            DOPPIO_EDITOR > ~/.config/doppio/config.json > VISUAL > EDITOR > nano/vim/vi from PATH.
          GUI editors should usually include a wait flag, for example code -w.
          If no editor is available, the shell prints setup commands and keeps running.

        Generation Shortcuts
          doppio gen users/me --method GET --bearer
          doppio gen auth/login --method POST --body json --bearer
          doppio gen auth/login --method POST --body form --bearer
          doppio gen auth/login --body form -H X-Client=doppio -q source=doppio
          doppio gen export/users --body csv
          doppio gen notes/ping --body text
          doppio gen jobs/start --method POST --body none
          doppio gen auth/login --from-curl "curl -H 'Accept: application/json' https://api.example.com/auth/me"

          Defaults:
            no --method means POST
            GET and DELETE default to no body
            POST, PUT, and PATCH default to JSON
            --bearer adds Authorization=Bearer {{TOKEN}}

          Curl import:
            --from-curl supports common curl examples with URL, --url, -X/--request,
            -H/--header, and -d/--data/--data-raw/--data-binary.
            It maps simple JSON, text, and URL-encoded form bodies into Doppio body blocks.
            Advanced curl flags, multipart, cookies, auth helpers, and shell expansion are not imported.

        JSON Output For Agents
          doppio ls --json
          doppio show auth/login --json
          doppio preview auth/login --env dev --json
          doppio run auth/login --env dev --json

          ls lists request names, paths, and parse markers.
          show reads request structure before hydration.
          preview hydrates variables and prepares the final request without network execution.
          run executes the request and includes response status, headers, body, and duration.

        Formatting
          doppio format formats all .dopo files under .doppio/recipes.
          doppio format auth/login formats one request; .dopo is optional in a project.
          doppio format auth formats every .dopo file under that recipe folder.
          JSON bodies are pretty-printed and full-line # comments keep their position.
          Inline JSON comments are not supported; comment out whole lines instead.

        Checks And Expectations
          @expect status=200 checks the response HTTP status.
          @expect header Content-Type contains json checks a response header value.
          @expect body contains token checks the response body as plain text.
          Expectations run after HTTP execution and make doppio run fail when they fail.
          doppio check validates request files without executing HTTP or evaluating response expectations.

        Notes For Coding Agents
          Prefer list or ls first to discover available requests.
          Use show before editing a request file.
          Use doctor after cloning or generating a project to catch setup and request issues.
          Use check before run when validating generated or edited request files.
          Use preview --json before run --json when changing variables, headers, query params, or bodies.
          Non-2xx HTTP responses still print output and exit with failure.
        """;

    private final PrintWriter out;

    public DocsCommand(PrintWriter out) {
        this.out = out;
    }

    @Override
    public Integer call() {
        out.print(DOCS);
        out.flush();
        return 0;
    }
}
