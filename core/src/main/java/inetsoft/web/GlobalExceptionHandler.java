/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;
import java.util.*;

/**
 * A class which allows for handling the logging of errors of any controller
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
   /**
    * Handles general exceptions that are not handled by other methods in this
    * class.
    */
   @ExceptionHandler(IOException.class)
   @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
   public void handleException(Exception e) {
      boolean suppressedConnectionException = Arrays.stream(e.getSuppressed())
         .map(t -> t.getClass().getName())
         .anyMatch(CLIENT_ABORT_EXCEPTIONS::contains);
      Throwable cause = e.getCause();
      boolean causedByClientAbort = isClientAbortException(e.getClass().getName()) ||
         (cause != null && isClientAbortException(cause.getClass().getName()));

      String message = e.getMessage() == null ? "" : e.getMessage();

      if(causedByClientAbort || suppressedConnectionException ||
         message.contains("Connection reset by peer"))
      {
         LOG.debug("Client closed connection", e);
      }
      else {
         LOG.error("Unable to process request", e);
      }
   }

//   @ExceptionHandler(MaxUploadSizeExceededException.class)
//   public ResponseEntity<Map<String, String>> handleMaxUploadSizeException(MaxUploadSizeExceededException e) {
//      String msg = e.getMessage();
//      Map<String, String> payload = new HashMap<>();
//      payload.put("error", "messageException");
//
//      long csvmax = Long.parseLong(SreeEnv.getProperty("csv.import.max", "0"));
//      long excelmax = Long.parseLong(SreeEnv.getProperty("excel.import.max", "0"));
//      long maxsize = Math.min(csvmax, excelmax);
//
//      if(maxsize > 0) {
//         try {
//            long sizeM = maxsize / 1024 / 1024;
//            msg = Catalog.getCatalog().getString("common.csvmax", sizeM + "M");
//         }
//         catch(Exception ignored) {
//            // ignored
//         }
//      }
//
//      payload.put("message", msg);
//      return new ResponseEntity<>(payload, null, HttpStatus.INTERNAL_SERVER_ERROR);
//   }

   public static boolean isClientAbortException(String className) {
      return CLIENT_ABORT_EXCEPTIONS.contains(className);
   }

   private static final Set<String> CLIENT_ABORT_EXCEPTIONS = new HashSet<>(
      Arrays.asList("org.apache.catalina.connector.ClientAbortException",
                    "org.eclipse.jetty.io.EofException"));
   private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
}
