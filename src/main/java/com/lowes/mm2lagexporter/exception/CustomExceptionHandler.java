package com.lowes.mm2lagexporter.exception;

import com.lowes.mm2lagexporter.model.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * To Handle Exceptions occurred in this whole application.
 */
@ControllerAdvice
@Slf4j
public class CustomExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * To handle Common Exception.
     *
     * @param ex Exception
     * @return Object
     */
    @ExceptionHandler(value = {Exception.class})
    public ResponseEntity<Object> handleException(Exception ex) {
        log.error("Exception with Exporter-api: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(new ErrorResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value()), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * To Handle Custom Exception MMLagExporterException.
     *
     * @param ex MMLagExporterException
     * @return Object
     */
    @ExceptionHandler(value = {MMLagExporterException.class})
    public ResponseEntity<Object> handleMMLagExporterException(MMLagExporterException ex) {
        log.error("MMLagExporterException with Exporter-api: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(new ErrorResponse(ex.getMessage(), ex.getCode().value()), ex.getCode());
    }
}
