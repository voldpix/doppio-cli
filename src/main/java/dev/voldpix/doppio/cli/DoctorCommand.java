package dev.voldpix.doppio.cli;

import dev.voldpix.doppio.env.DoppioEnvironment;
import dev.voldpix.doppio.doctor.DoctorFinding;
import dev.voldpix.doppio.doctor.DoppioDoctorService;
import dev.voldpix.doppio.model.DoppioException;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "doctor", mixinStandardHelpOptions = true, description = "Inspect Doppio project health.")
public class DoctorCommand implements Callable<Integer> {
    @Option(names = "--env", paramLabel = "NAME", description = "Inspect a selected .doppio/seeds/NAME.seed.")
    private String envName;

    private final Path workingDirectory;
    private final Map<String, String> environment;
    private final DoppioDoctorService doctorService;
    private final PrintWriter out;

    public DoctorCommand(
        Path workingDirectory,
        Map<String, String> environment,
        DoppioDoctorService doctorService,
        PrintWriter out
    ) {
        this.workingDirectory = workingDirectory;
        this.environment = Map.copyOf(environment);
        this.doctorService = doctorService;
        this.out = out;
    }

    @Override
    public Integer call() {
        DoppioEnvironment selectedEnvironment;
        try {
            selectedEnvironment = DoppioEnvironment.of(envName);
        } catch (DoppioException e) {
            out.println("Doppio Doctor");
            out.println();
            out.println("Project: (not checked)");
            out.println("Pass: 0");
            out.println("Warn: 0");
            out.println("Fail: 1");
            out.println();
            out.printf("%-4s %-10s %s%n", "FAIL", "seed", e.getMessage());
            out.flush();
            return 1;
        }
        var report = doctorService.inspect(workingDirectory, environment, selectedEnvironment);
        out.println("Doppio Doctor");
        out.println();
        out.println("Project: " + (report.projectDirectory() == null ? "(not found)" : report.projectDirectory()));
        if (selectedEnvironment.selected()) {
            out.println("Seed: " + selectedEnvironment.name());
        }
        out.println("Pass: " + report.passCount());
        out.println("Warn: " + report.warnCount());
        out.println("Fail: " + report.failCount());
        out.println();
        report.findings().forEach(this::printFinding);
        out.flush();
        return report.hasFailures() ? 1 : 0;
    }

    private void printFinding(DoctorFinding finding) {
        out.printf("%-4s %-10s %s%n", finding.severity(), finding.check(), finding.message());
    }
}
