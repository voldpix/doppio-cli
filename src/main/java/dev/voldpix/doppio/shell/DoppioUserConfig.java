package dev.voldpix.doppio.shell;

public record DoppioUserConfig(String editorCommand) {
    public DoppioUserConfig {
        editorCommand = editorCommand == null || editorCommand.isBlank() ? null : editorCommand.trim();
    }

    public static DoppioUserConfig empty() {
        return new DoppioUserConfig(null);
    }

    public DoppioUserConfig withEditorCommand(String command) {
        return new DoppioUserConfig(command);
    }
}
