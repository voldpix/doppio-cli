package dev.voldpix.doppio.doctor;

import dev.voldpix.doppio.check.DoppioCheckService;
import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.pipeline.DoppioProjectResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

        checkRequests(cwd, environment, findings);
        return new DoctorReport(projectDir, findings);
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

    private void checkRequests(Path workingDirectory, Map<String, String> environment, ArrayList<DoctorFinding> findings) {
        try {
            var summary = checkService.check(null, workingDirectory, environment);
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
