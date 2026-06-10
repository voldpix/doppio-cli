package dev.voldpix.doppio.shell;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public class ExternalEditor {
    private static final String NO_EDITOR_MESSAGE = "No editor configured. Run `editor use nano`, `editor use \"code -w\"`, or set DOPPIO_EDITOR, VISUAL, or EDITOR.";
    private static final java.util.List<String> FALLBACK_EDITORS = java.util.List.of("nano", "vim", "vi");

    private final ShellCommandParser parser;

    public ExternalEditor() {
        this(new ShellCommandParser());
    }

    public ExternalEditor(ShellCommandParser parser) {
        this.parser = parser;
    }

    public void open(Path file, Map<String, String> environment) throws DoppioException {
        open(file, environment, DoppioUserConfig.empty());
    }

    public void open(Path file, Map<String, String> environment, DoppioUserConfig config) throws DoppioException {
        var editor = resolve(environment, config)
            .orElseThrow(() -> new DoppioException(ErrorKind.FILE, NO_EDITOR_MESSAGE));

        var command = new java.util.ArrayList<>(parser.parse(editor.command()));
        if (command.isEmpty()) {
            throw new DoppioException(ErrorKind.FILE, NO_EDITOR_MESSAGE);
        }
        command.add(file.toAbsolutePath().normalize().toString());

        try {
            var process = new ProcessBuilder(command)
                .inheritIO()
                .start();
            var exit = process.waitFor();
            if (exit != 0) {
                throw new DoppioException(ErrorKind.FILE, "Editor exited with status " + exit + ": " + command.getFirst());
            }
        } catch (IOException e) {
            throw new DoppioException(ErrorKind.FILE, "Unable to start editor: " + command.getFirst(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DoppioException(ErrorKind.FILE, "Editor was interrupted", e);
        }
    }

    public Optional<ResolvedEditor> resolve(Map<String, String> environment, DoppioUserConfig config) {
        var doppioEditor = configured("DOPPIO_EDITOR", environment.get("DOPPIO_EDITOR"));
        if (doppioEditor.isPresent()) {
            return doppioEditor;
        }
        if (config != null && config.editorCommand() != null && !config.editorCommand().isBlank()) {
            return Optional.of(new ResolvedEditor(config.editorCommand(), "~/.config/doppio/config.json"));
        }
        var visual = configured("VISUAL", environment.get("VISUAL"));
        if (visual.isPresent()) {
            return visual;
        }
        var editor = configured("EDITOR", environment.get("EDITOR"));
        return editor.isPresent() ? editor : fallback(environment);
    }

    private Optional<ResolvedEditor> configured(String source, String command) {
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedEditor(command.trim(), source));
    }

    private Optional<ResolvedEditor> fallback(Map<String, String> environment) {
        for (var fallback : FALLBACK_EDITORS) {
            if (existsOnPath(fallback, environment)) {
                return Optional.of(new ResolvedEditor(fallback, "PATH fallback"));
            }
        }
        return Optional.empty();
    }

    private boolean existsOnPath(String executable, Map<String, String> environment) {
        var path = environment.get("PATH");
        if (path == null || path.isBlank()) {
            return false;
        }
        for (var directory : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (directory.isBlank()) {
                continue;
            }
            var candidate = Path.of(directory).resolve(executable);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }
}
