package dev.voldpix.doppio.http;

import dev.voldpix.doppio.model.DoppioResponse;
import dev.voldpix.doppio.model.PreparedRequest;

import java.time.Duration;

public interface HttpTransport {
    DoppioResponse execute(PreparedRequest request, Duration timeout) throws TransportException;
}
