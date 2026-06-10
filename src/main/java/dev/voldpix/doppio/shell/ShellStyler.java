package dev.voldpix.doppio.shell;

import java.nio.file.Path;
import java.util.Map;

public class ShellStyler {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";

    private final boolean ansi;

    public ShellStyler(Map<String, String> environment) {
        this(!environment.containsKey("NO_COLOR"));
    }

    public ShellStyler(boolean ansi) {
        this.ansi = ansi;
    }

    public String prompt(String environmentName) {
        return style("doppio", BOLD + CYAN)
            + ":"
            + style("[" + environmentName + "]", GREEN)
            + style(">", BOLD)
            + " ";
    }

    public String header(String text) {
        return style(text, BOLD);
    }

    public String success(String text) {
        return style(text, GREEN);
    }

    public String failure(String text) {
        return style(text, RED);
    }

    public String warning(String text) {
        return style(text, YELLOW);
    }

    public String command(String text) {
        return style(text, MAGENTA);
    }

    public String env(String text) {
        return style(text, GREEN);
    }

    public String method(String text) {
        return style(text, BOLD + BLUE);
    }

    public String url(String text) {
        return style(text, CYAN);
    }

    public String path(Path path) {
        return path(path.toString());
    }

    public String path(String path) {
        return style(path, CYAN);
    }

    public String muted(String text) {
        return style(text, GRAY);
    }

    public String result(String text, boolean success) {
        return success ? success(text) : failure(text);
    }

    private String style(String text, String code) {
        return ansi ? code + text + RESET : text;
    }
}
