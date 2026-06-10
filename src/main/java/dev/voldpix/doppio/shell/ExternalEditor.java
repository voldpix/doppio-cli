package dev.voldpix.doppio.shell;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class ExternalEditor {
    private final ShellCommandParser parser;

    public ExternalEditor() {
        this(new ShellCommandParser());
    }

    public ExternalEditor(ShellCommandParser parser) {
        this.parser = parser;
    }

    public void open(Path file, Map<String, String> environment) throws DoppioException {
        var editor = editor(environment);
        if (editor == null || editor.isBlank()) {
            throw new DoppioException(ErrorKind.FILE, "No editor configured. Set VISUAL or EDITOR.");
        }

        var command = new java.util.ArrayList<>(parser.parse(editor));
        if (command.isEmpty()) {
            throw new DoppioException(ErrorKind.FILE, "No editor configured. Set VISUAL or EDITOR.");
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

    private String editor(Map<String, String> environment) {
        var visual = environment.get("VISUAL");
        if (visual != null && !visual.isBlank()) {
            return visual;
        }
        return environment.get("EDITOR");
    }
}
