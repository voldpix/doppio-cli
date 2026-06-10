package dev.voldpix.doppio.model;

import java.util.regex.Pattern;

public final class HeaderNames {
    private static final Pattern FIELD_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    private HeaderNames() {
    }

    public static boolean isValid(String name) {
        return name != null && FIELD_NAME.matcher(name).matches();
    }
}
