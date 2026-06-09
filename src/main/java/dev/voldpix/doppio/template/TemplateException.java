package dev.voldpix.doppio.template;

import dev.voldpix.doppio.model.DoppioException;
import dev.voldpix.doppio.model.ErrorKind;

public class TemplateException extends DoppioException {
    public TemplateException(String message) {
        super(ErrorKind.TEMPLATE, message);
    }
}
