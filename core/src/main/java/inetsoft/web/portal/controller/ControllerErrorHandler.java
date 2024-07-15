/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller;

import inetsoft.sree.security.SecurityException;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;
import inetsoft.util.log.LogManager;
import inetsoft.web.GlobalExceptionHandler;
import org.apache.commons.io.FileExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that maps thrown exceptions to HTTP status codes.
 */
@ControllerAdvice(basePackages = { "inetsoft.web.portal" })
public class ControllerErrorHandler extends ResponseEntityExceptionHandler {
   /**
    * Handles file not found exceptions.
    */
   @ExceptionHandler(FileNotFoundException.class)
   @ResponseStatus(HttpStatus.NOT_FOUND)
   public void handleFileNotFoundException(FileNotFoundException e) {
      log("Resource not found", e, LogLevel.WARN);
   }

   /**
    * Handles file exists exceptions.
    */
   @ExceptionHandler(FileExistsException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   public void handleFileExistsException(FileExistsException e) {
      log("A resource with the specified name already exists", e, LogLevel.WARN);
   }

   /**
    * Handles security exceptions.
    */
   @ExceptionHandler(SecurityException.class)
   @ResponseStatus(HttpStatus.FORBIDDEN)
   public void handleSecurityException(Exception e) {
      log("Unauthorized access", e, LogLevel.WARN);
   }

   @ExceptionHandler(MessageException.class)
   public ResponseEntity<Map<String, String>> handleMessageException(MessageException e) {
      MessageException thrown = e.isDumpStack() ? e : null;
      LogManager.getInstance().logException(LOG, e.getLogLevel(), e.getMessage(), thrown);

      Map<String, String> payload = new HashMap<>();
      payload.put("error", "messageException");
      payload.put("message", e.getMessage());
      return new ResponseEntity<>(payload, null, HttpStatus.INTERNAL_SERVER_ERROR);
   }

   /**
    * Handles general exceptions that are not handled by other methods in this
    * class.
    */
   @ExceptionHandler(Exception.class)
   @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
   public ResponseEntity<String> handleException(Exception e) {
      if(GlobalExceptionHandler.isClientAbortException(e.getClass().getName())) {
         LOG.debug("Client closed connection", e);
      }
      else {
         LOG.error("Unable to process request", e);
      }

      return new ResponseEntity<>(e.getMessage(), null,
                                HttpStatus.INTERNAL_SERVER_ERROR);
   }

   /**
    * Prints a log message.
    *
    * @param message the log message.
    * @param e       the thrown exception.
    * @param level   the log level.
    */
   private void log(String message, Exception e, LogLevel level) {
      // for common exceptions like file not found, file exists, and
      // unauthorized access, stack traces are not important and we shouldn't
      // pollute the log with them
      if(LogManager.getInstance().isDebugEnabled(LOG.getName())) {
         LogManager.getInstance().logException(LOG, level, message, e);
      }
      else {
         LogManager.getInstance().logException(LOG, level, message + ": " + e.getMessage(), null);
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(ControllerErrorHandler.class);
}
