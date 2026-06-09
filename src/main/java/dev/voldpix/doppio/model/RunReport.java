package dev.voldpix.doppio.model;

public record RunReport(PreparedRequest request, DoppioResponse response) {
    public boolean isSuccess() {
        return response.isSuccess();
    }
}
