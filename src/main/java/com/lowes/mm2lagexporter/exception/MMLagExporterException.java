package com.lowes.mm2lagexporter.exception;

import org.springframework.http.HttpStatus;

/**
 * Custom  Exception for this Application.
 */
public class MMLagExporterException extends Exception {

    private final HttpStatus code;

    public MMLagExporterException(HttpStatus code) {
        super();
        this.code = code;
    }

    public MMLagExporterException(String message, Throwable cause, HttpStatus code) {
        super(message, cause);
        this.code = code;
    }

    public MMLagExporterException(String message, HttpStatus code) {
        super(message);
        this.code = code;
    }

    public MMLagExporterException(Throwable cause, HttpStatus code) {
        super(cause);
        this.code = code;
    }

    public HttpStatus getCode() {
        return this.code;
    }
}
