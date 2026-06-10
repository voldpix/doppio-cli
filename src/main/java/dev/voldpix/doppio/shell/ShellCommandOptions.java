package dev.voldpix.doppio.shell;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

import java.util.ArrayList;
import java.util.List;

public record ShellCommandOptions(List<String> args, String envName) {
    public ShellCommandOptions {
        args = List.copyOf(args);
    }

    public static ShellCommandOptions parse(List<String> args) throws DoppioException {
        var remaining = new ArrayList<String>();
        String env = null;
        for (var i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            if ("--env".equals(arg)) {
                if (i + 1 >= args.size() || args.get(i + 1).isBlank()) {
                    throw new DoppioException(ErrorKind.PARSE, "--env requires a value");
                }
                env = args.get(++i);
            } else if (arg.startsWith("--env=")) {
                env = arg.substring("--env=".length());
                if (env.isBlank()) {
                    throw new DoppioException(ErrorKind.PARSE, "--env requires a value");
                }
            } else {
                remaining.add(arg);
            }
        }
        return new ShellCommandOptions(remaining, env);
    }
}
