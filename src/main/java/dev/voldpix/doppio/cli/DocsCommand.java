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
          Request files live in .doppio/requests and use the .dopo extension.

        Project Layout
          .doppio/
            default.seed
            requests/
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

        Variables
          Use {{KEY}} placeholders in URLs, headers, query params, and bodies.
          Precedence: request @var > default.seed > OS environment.

          Example:
            @var EMAIL=me@example.com
            GET {{BASE_URL}}/users/me
            -h Authorization=Bearer {{TOKEN}}

        Request Files
          @name Login user
          @var EMAIL=me@example.com
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
          # starts a comment outside body blocks and inside body blocks.

        Common Commands
          doppio init
          doppio gen auth/login
          doppio list
          doppio ls
          doppio show auth/login
          doppio preview auth/login
          doppio run auth/login
          doppio format
          doppio format auth/login
          doppio format auth
          doppio run auth/login --save
          doppio clean
          doppio rm auth/login

        Generation Shortcuts
          doppio gen users/me --method GET --bearer
          doppio gen auth/login --method POST --body json --bearer
          doppio gen auth/login --method POST --body form --bearer
          doppio gen auth/login --body form -H X-Client=doppio -q source=doppio
          doppio gen export/users --body csv
          doppio gen notes/ping --body text
          doppio gen jobs/start --method POST --body none

          Defaults:
            no --method means POST
            GET and DELETE default to no body
            POST, PUT, and PATCH default to JSON
            --bearer adds Authorization=Bearer {{TOKEN}}

        JSON Output For Agents
          doppio ls --json
          doppio show auth/login --json
          doppio preview auth/login --json
          doppio run auth/login --json

          ls lists request names, paths, and parse markers.
          show reads request structure before hydration.
          preview hydrates variables and prepares the final request without network execution.
          run executes the request and includes response status, headers, body, and duration.

        Formatting
          doppio format formats all .dopo files under .doppio/requests.
          doppio format auth/login formats one request; .dopo is optional in a project.
          doppio format auth formats every .dopo file under that request folder.
          JSON bodies are pretty-printed and full-line # comments keep their position.
          Inline JSON comments are not supported; comment out whole lines instead.

        Notes For Coding Agents
          Prefer list or ls first to discover available requests.
          Use show before editing a request file.
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
