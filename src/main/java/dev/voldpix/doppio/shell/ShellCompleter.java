package dev.voldpix.doppio.shell;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;
import java.util.Set;

public class ShellCompleter implements Completer {
    private static final Set<String> REQUEST_COMMANDS = Set.of("run", "preview", "show", "edit", "format", "check", "rm");
    private static final List<String> COMMANDS = List.of(
        "help",
        "exit",
        "quit",
        "ls",
        "list",
        "gen",
        "edit",
        "show",
        "preview",
        "run",
        "body",
        "save",
        "format",
        "check",
        "rm",
        "seed",
        "env",
        "editor",
        "projects",
        "project",
        "clean",
        "doctor"
    );
    private static final List<String> SEED_COMMANDS = List.of("list", "edit", "gen", "rm");
    private static final List<String> ENV_COMMANDS = List.of("list", "use", "clear", "edit", "gen");
    private static final List<String> EDITOR_COMMANDS = List.of("show", "use", "clear");

    private final DoppioShell shell;
    private final ShellRequestResolver requestResolver;

    public ShellCompleter(DoppioShell shell) {
        this(shell, new ShellRequestResolver());
    }

    public ShellCompleter(DoppioShell shell, ShellRequestResolver requestResolver) {
        this.shell = shell;
        this.requestResolver = requestResolver;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        if (line.wordIndex() == 0) {
            COMMANDS.forEach(command -> candidates.add(new Candidate(command)));
            return;
        }

        var words = line.words();
        if (words.isEmpty() || shell.session() == null) {
            return;
        }

        var command = words.getFirst();
        if (REQUEST_COMMANDS.contains(command) && line.wordIndex() == 1) {
            completeRequests(candidates);
        } else if ("seed".equals(command)) {
            completeSeed(words, line.wordIndex(), candidates);
        } else if ("env".equals(command)) {
            completeEnv(words, line.wordIndex(), candidates);
        } else if ("editor".equals(command)) {
            completeEditor(words, line.wordIndex(), candidates);
        }
    }

    private void completeRequests(List<Candidate> candidates) {
        try {
            requestResolver.all(shell.session().projectRoot())
                .forEach(candidate -> {
                    candidates.add(new Candidate(candidate.relativePath().toString()));
                    var stem = stem(candidate.relativePath().getFileName().toString());
                    candidates.add(new Candidate(stem));
                });
        } catch (Exception e) {
            // Completion should never interrupt shell input.
        }
    }

    private void completeSeed(List<String> words, int wordIndex, List<Candidate> candidates) {
        if (wordIndex == 1) {
            SEED_COMMANDS.forEach(command -> candidates.add(new Candidate(command)));
        } else if (wordIndex == 2 && words.size() > 1 && List.of("edit", "rm").contains(words.get(1))) {
            candidates.add(new Candidate("default"));
            completeEnvNames(candidates);
        }
    }

    private void completeEnv(List<String> words, int wordIndex, List<Candidate> candidates) {
        if (wordIndex == 1) {
            ENV_COMMANDS.forEach(command -> candidates.add(new Candidate(command)));
        } else if (wordIndex == 2 && words.size() > 1 && List.of("use", "edit").contains(words.get(1))) {
            completeEnvNames(candidates);
        }
    }

    private void completeEditor(List<String> words, int wordIndex, List<Candidate> candidates) {
        if (wordIndex == 1) {
            EDITOR_COMMANDS.forEach(command -> candidates.add(new Candidate(command)));
        } else if (wordIndex == 2 && words.size() > 1 && "use".equals(words.get(1))) {
            List.of("nano", "vim", "vi", "code -w").forEach(command -> candidates.add(new Candidate(command)));
        }
    }

    private void completeEnvNames(List<Candidate> candidates) {
        var envsDir = shell.session().doppioDirectory().resolve("envs");
        if (!java.nio.file.Files.isDirectory(envsDir)) {
            return;
        }
        try (var files = java.nio.file.Files.walk(envsDir, 1)) {
            files.filter(java.nio.file.Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".seed"))
                .map(name -> name.substring(0, name.length() - ".seed".length()))
                .forEach(name -> candidates.add(new Candidate(name)));
        } catch (Exception e) {
            // Completion should never interrupt shell input.
        }
    }

    private String stem(String fileName) {
        var dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}
