package dev.voldpix.doppio.shell;

import dev.voldpix.doppio.model.RunReport;

import java.nio.file.Path;

public class ShellSession {
    private Path projectRoot;
    private Path doppioDirectory;
    private String environmentName;
    private RunReport lastRunReport;

    public ShellSession(Path projectRoot, Path doppioDirectory, String environmentName) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.doppioDirectory = doppioDirectory.toAbsolutePath().normalize();
        this.environmentName = environmentName;
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path doppioDirectory() {
        return doppioDirectory;
    }

    public String environmentName() {
        return environmentName;
    }

    public void environmentName(String environmentName) {
        this.environmentName = environmentName;
    }

    public String promptEnvironment() {
        return environmentName == null || environmentName.isBlank() ? "default" : environmentName;
    }

    public RunReport lastRunReport() {
        return lastRunReport;
    }

    public void lastRunReport(RunReport lastRunReport) {
        this.lastRunReport = lastRunReport;
    }

    public void switchProject(Path projectRoot, Path doppioDirectory) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.doppioDirectory = doppioDirectory.toAbsolutePath().normalize();
        this.lastRunReport = null;
    }
}
