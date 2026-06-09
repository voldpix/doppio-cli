package dev.voldpix.doppio;

import dev.voldpix.doppio.cli.DoppioCommand;

public class Doppio {
    public static void main(String[] args) {
        System.exit(DoppioCommand.commandLine().execute(args));
    }
}
