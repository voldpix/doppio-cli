package dev.voldpix.doppio.shell;

import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShellCompleterTest {
    @TempDir
    Path tempDir;

    @Test
    void formatAndCheckCompleteRecipeFilesAndFolders() throws Exception {
        var shell = shellWithSession();
        request("auth/login.dopo");
        request("users/me.dopo");
        var completer = new ShellCompleter(shell);

        assertThat(completions(completer, "format", 1))
            .contains("auth/login.dopo", "login", "auth/", "users/");
        assertThat(completions(completer, "check", 1))
            .contains("users/me.dopo", "me", "auth/", "users/");
    }

    @Test
    void fileOpsCompleteRecipeSourceOnly() throws Exception {
        var shell = shellWithSession();
        request("auth/login.dopo");
        var completer = new ShellCompleter(shell);

        assertThat(completions(completer, "mv", 1)).contains("auth/login.dopo", "login");
        assertThat(completions(completer, "cp", 1)).contains("auth/login.dopo", "login");
        assertThat(completions(completer, "rename", 1)).contains("auth/login.dopo", "login");
        assertThat(completions(completer, "mv", 2)).isEmpty();
    }

    private DoppioShell shellWithSession() throws Exception {
        Files.createDirectories(tempDir.resolve(".doppio/recipes"));
        Files.writeString(tempDir.resolve(".doppio/default.seed"), "");
        var shell = new DoppioShell(
            tempDir,
            Map.of("NO_COLOR", "1"),
            null,
            null,
            new DoppioStatusStore(tempDir.resolve("config")),
            new PrintWriter(new StringWriter(), true),
            new PrintWriter(new StringWriter(), true)
        );
        Field session = DoppioShell.class.getDeclaredField("session");
        session.setAccessible(true);
        session.set(shell, new ShellSession(tempDir, tempDir.resolve(".doppio"), null));
        return shell;
    }

    private void request(String relativePath) throws Exception {
        var path = tempDir.resolve(".doppio/recipes").resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "GET https://example.com");
    }

    private List<String> completions(ShellCompleter completer, String command, int wordIndex) {
        var candidates = new ArrayList<Candidate>();
        completer.complete(null, new TestParsedLine(command, wordIndex), candidates);
        return candidates.stream()
            .map(Candidate::value)
            .toList();
    }

    private record TestParsedLine(String command, int wordIndex) implements ParsedLine {
        @Override
        public String word() {
            return "";
        }

        @Override
        public int wordCursor() {
            return 0;
        }

        @Override
        public List<String> words() {
            return wordIndex == 0 ? List.of(command) : List.of(command, "");
        }

        @Override
        public String line() {
            return command + " ";
        }

        @Override
        public int cursor() {
            return line().length();
        }
    }
}
