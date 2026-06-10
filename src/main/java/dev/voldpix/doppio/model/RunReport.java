package dev.voldpix.doppio.model;

import dev.voldpix.doppio.expect.ExpectationReport;

import java.nio.file.Path;

public record RunReport(
    Path requestFile,
    PreparedRequest request,
    DoppioResponse response,
    ExpectationReport expectations
) {
    public RunReport(Path requestFile, PreparedRequest request, DoppioResponse response) {
        this(requestFile, request, response, ExpectationReport.empty());
    }

    public RunReport {
        expectations = expectations == null ? ExpectationReport.empty() : expectations;
    }

    public boolean isSuccess() {
        return response.isSuccess() && expectations.isSuccess();
    }
}
