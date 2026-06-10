package dev.voldpix.doppio.shell;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

public class DoppioConfigStore {
    private static final Pattern EDITOR_PATTERN = Pattern.compile("\"editor\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");

    private final Path configDirectory;
    private final Path configFile;

    public DoppioConfigStore(Path configDirectory) {
        this.configDirectory = configDirectory.toAbsolutePath().normalize();
        this.configFile = this.configDirectory.resolve("config.json");
    }

    public static DoppioConfigStore userDefault() {
        return new DoppioConfigStore(Path.of(System.getProperty("user.home"), ".config", "doppio"));
    }

    public Path configDirectory() {
        return configDirectory;
    }

    public DoppioUserConfig read() {
        if (!Files.isRegularFile(configFile)) {
            return DoppioUserConfig.empty();
        }

        try {
            var content = Files.readString(configFile);
            var matcher = EDITOR_PATTERN.matcher(content);
            if (matcher.find()) {
                return new DoppioUserConfig(unescape(matcher.group(1)));
            }
            return DoppioUserConfig.empty();
        } catch (IOException | RuntimeException e) {
            return DoppioUserConfig.empty();
        }
    }

    public void write(DoppioUserConfig config) {
        try {
            Files.createDirectories(configDirectory);
            var tmp = configDirectory.resolve("config.json.tmp");
            Files.writeString(tmp, toJson(config));
            try {
                Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // Editor config should never block an active shell session.
        }
    }

    private String toJson(DoppioUserConfig config) {
        var editor = config == null ? null : config.editorCommand();
        if (editor == null || editor.isBlank()) {
            return "{\n  \"version\": 1\n}\n";
        }
        return "{\n  \"version\": 1,\n  \"editor\": \"" + escape(editor) + "\"\n}\n";
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescape(String value) {
        var result = new StringBuilder();
        var escaping = false;
        for (var i = 0; i < value.length(); i++) {
            var ch = value.charAt(i);
            if (escaping) {
                result.append(ch);
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else {
                result.append(ch);
            }
        }
        if (escaping) {
            result.append('\\');
        }
        return result.toString();
    }
}
