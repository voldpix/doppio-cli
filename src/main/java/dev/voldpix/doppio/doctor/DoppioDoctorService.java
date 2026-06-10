package dev.voldpix.doppio.doctor;

import dev.voldpix.doppio.check.DoppioCheckService;
import dev.voldpix.doppio.env.DoppioEnvironment;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.pipeline.DoppioProjectResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

public class DoppioDoctorService {
    private final DoppioProjectResolver projectResolver;
    private final DoppioCheckService checkService;

    public DoppioDoctorService(DoppioCheckService checkService) {
        this(new DoppioProjectResolver(), checkService);
    }

    public DoppioDoctorService(DoppioProjectResolver projectResolver, DoppioCheckService checkService) {
        this.projectResolver = projectResolver;
        this.checkService = checkService;
    }

    public DoctorReport inspect(Path workingDirectory, Map<String, String> environment) {
        return inspect(workingDirectory, environment, DoppioEnvironment.none());
    }

    public DoctorReport inspect(
        Path workingDirectory,
        Map<String, String> environment,
        DoppioEnvironment selectedEnvironment
    ) {
        selectedEnvironment = selectedEnvironment == null ? DoppioEnvironment.none() : selectedEnvironment;
        var cwd = workingDirectory.toAbsolutePath().normalize();
        var findings = new ArrayList<DoctorFinding>();
        var doppioDir = projectResolver.findDoppioDirectory(cwd);

        if (doppioDir == null) {
            findings.add(fail("project", "No .doppio project found. Run `doppio init` first."));
            return new DoctorReport(null, findings);
        }

        var projectDir = doppioDir.toAbsolutePath().normalize();
        findings.add(pass("project", "Project found: " + projectDir));

        var defaultSeed = projectDir.resolve("default.seed");
        var localSeed = projectDir.resolve("local.seed");
        if (Files.isRegularFile(defaultSeed)) {
            findings.add(pass("seed", "default.seed found"));
            if (Files.exists(localSeed)) {
                findings.add(warn("seed", "local.seed also exists and is ignored while default.seed is present"));
            }
        } else if (Files.isRegularFile(localSeed)) {
            findings.add(warn("seed", "default.seed missing; legacy local.seed fallback is being used"));
        } else {
            findings.add(warn("seed", "No seed file found; only OS environment variables are available"));
        }

        var selectedEnvAvailable = checkEnvironments(projectDir, selectedEnvironment, findings);

        var requestsDir = projectDir.resolve("requests");
        if (!Files.isDirectory(requestsDir)) {
            findings.add(fail("requests", "requests folder not found: " + requestsDir));
            return new DoctorReport(projectDir, findings);
        }
        findings.add(pass("requests", "requests folder found"));

        var requestCount = countRequests(requestsDir, findings);
        if (requestCount == 0) {
            findings.add(warn("requests", "No .dopo request files found"));
        } else if (requestCount > 0) {
            findings.add(pass("requests", requestCount + " request file(s) found"));
        }

        if (selectedEnvAvailable) {
            checkRequests(cwd, environment, selectedEnvironment, findings);
        }
        return new DoctorReport(projectDir, findings);
    }

    private boolean checkEnvironments(
        Path projectDir,
        DoppioEnvironment selectedEnvironment,
        ArrayList<DoctorFinding> findings
    ) {
        var envsDir = projectDir.resolve("envs");
        if (!Files.isDirectory(envsDir)) {
            findings.add(warn("env", "envs folder not found; create one with `doppio gen --env dev`"));
            if (selectedEnvironment.selected()) {
                findings.add(fail("env", "Selected env not found: " + selectedEnvironment.name()));
                return false;
            }
            return true;
        }

        var envNames = environmentNames(envsDir, findings);
        if (envNames.isEmpty()) {
            findings.add(pass("env", "envs folder found; no env files yet"));
        } else {
            findings.add(pass("env", envNames.size() + " env file(s) found: " + String.join(", ", envNames)));
        }

        if (selectedEnvironment.selected()) {
            var envFile = envsDir.resolve(selectedEnvironment.fileName());
            if (Files.isRegularFile(envFile)) {
                findings.add(pass("env", "Selected env found: " + selectedEnvironment.name()));
                return true;
            }
            findings.add(fail("env", "Selected env not found: " + selectedEnvironment.name() + " (" + envFile + ")"));
            return false;
        }

        return true;
    }

    private java.util.List<String> environmentNames(Path envsDir, ArrayList<DoctorFinding> findings) {
        try (var files = Files.walk(envsDir, 1)) {
            return files
                .filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(".seed"))
                .map(file -> file.getFileName().toString())
                .map(name -> name.substring(0, name.length() - ".seed".length()))
                .sorted(Comparator.naturalOrder())
                .toList();
        } catch (IOException e) {
            findings.add(fail("env", "Unable to list env files: " + e.getMessage()));
            return java.util.List.of();
        }
    }

    private long countRequests(Path requestsDir, ArrayList<DoctorFinding> findings) {
        try (var files = Files.walk(requestsDir)) {
            return files
                .filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(".dopo"))
                .count();
        } catch (IOException e) {
            findings.add(fail("requests", "Unable to count request files: " + e.getMessage()));
            return -1;
        }
    }

    private void checkRequests(
        Path workingDirectory,
        Map<String, String> environment,
        DoppioEnvironment selectedEnvironment,
        ArrayList<DoctorFinding> findings
    ) {
        try {
            var summary = checkService.check(null, workingDirectory, environment, selectedEnvironment);
            if (summary.hasFailures()) {
                findings.add(fail("check", summary.failedCount() + " request file(s) failed validation"));
                summary.results().stream()
                    .filter(result -> result.failed())
                    .forEach(result -> findings.add(fail("check", result.displayPath() + " - " + result.message())));
            } else {
                findings.add(pass("check", summary.validCount() + " request file(s) validated"));
            }
        } catch (DoppioException e) {
            findings.add(fail("check", e.getMessage()));
        }
    }

    private DoctorFinding pass(String check, String message) {
        return new DoctorFinding(DoctorSeverity.PASS, check, message);
    }

    private DoctorFinding warn(String check, String message) {
        return new DoctorFinding(DoctorSeverity.WARN, check, message);
    }

    private DoctorFinding fail(String check, String message) {
        return new DoctorFinding(DoctorSeverity.FAIL, check, message);
    }
}
