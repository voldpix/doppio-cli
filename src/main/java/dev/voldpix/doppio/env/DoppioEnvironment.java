package dev.voldpix.doppio.env;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

import java.util.regex.Pattern;

public record DoppioEnvironment(String name) {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    public static DoppioEnvironment none() {
        return new DoppioEnvironment(null);
    }

    public static DoppioEnvironment of(String name) throws DoppioException {
        if (name == null || name.isBlank()) {
            return none();
        }

        var trimmed = name.trim();
        if (isDefaultName(trimmed)) {
            return none();
        }
        if (!VALID_NAME.matcher(trimmed).matches()) {
            throw new DoppioException(
                ErrorKind.SEED,
                "Seed name must use only letters, numbers, underscores, or dashes: " + name
            );
        }
        return new DoppioEnvironment(trimmed);
    }

    public static boolean isDefaultName(String name) {
        return name != null && "default".equalsIgnoreCase(name.trim());
    }

    public boolean selected() {
        return name != null && !name.isBlank();
    }

    public String fileName() {
        return selected() ? name + ".seed" : null;
    }
}
