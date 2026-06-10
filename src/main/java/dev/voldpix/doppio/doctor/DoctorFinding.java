package dev.voldpix.doppio.doctor;

public record DoctorFinding(
    DoctorSeverity severity,
    String check,
    String message
) {
}
