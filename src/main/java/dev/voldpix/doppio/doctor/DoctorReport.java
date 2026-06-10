package dev.voldpix.doppio.doctor;

import java.nio.file.Path;
import java.util.List;

public record DoctorReport(Path projectDirectory, List<DoctorFinding> findings) {
    public DoctorReport {
        findings = List.copyOf(findings);
    }

    public long passCount() {
        return findings.stream().filter(finding -> finding.severity() == DoctorSeverity.PASS).count();
    }

    public long warnCount() {
        return findings.stream().filter(finding -> finding.severity() == DoctorSeverity.WARN).count();
    }

    public long failCount() {
        return findings.stream().filter(finding -> finding.severity() == DoctorSeverity.FAIL).count();
    }

    public boolean hasFailures() {
        return failCount() > 0;
    }
}
