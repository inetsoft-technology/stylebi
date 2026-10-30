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
package inetsoft.web;

import inetsoft.util.Tool;
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
      boolean causedByClientAbort = isClientAbort(e);

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

   /**
    * Determines if an exception was caused by the client closing the connection, by class only.
    * Walks the whole cause chain (and suppressed exceptions), so a wrapper such as Spring's
    * AsyncRequestNotUsableException around a ClientAbortException is recognized. A wrapper alone
    * is not enough. This does not look at messages, so it is safe to use anywhere.
    */
   public static boolean isClientAbort(Throwable e) {
      return isClientAbort(e, false, Collections.newSetFromMap(new IdentityHashMap<>()));
   }

   /**
    * Like {@link #isClientAbort(Throwable)}, and also treats an IOException whose message is the
    * JDK/OS text for a closed connection ("Broken pipe", "Connection reset", ...) as an abort.
    * Only use where the failing step is known to be writing to the client response.
    */
   public static boolean isClientAbortOnWrite(Throwable e) {
      return isClientAbort(e, true, Collections.newSetFromMap(new IdentityHashMap<>()));
   }

   private static boolean isClientAbort(Throwable e, boolean byMessage, Set<Throwable> seen) {
      if(e == null || !seen.add(e)) {
         return false;
      }

      if(isClientAbortException(e.getClass().getName()) ||
         byMessage && e instanceof IOException && Tool.isBrokenPipeMessage(e.getMessage()))
      {
         return true;
      }

      if(isClientAbort(e.getCause(), byMessage, seen)) {
         return true;
      }

      for(Throwable suppressed : e.getSuppressed()) {
         if(isClientAbort(suppressed, byMessage, seen)) {
            return true;
         }
      }

      return false;
   }

   public static boolean isCacheStoppedException(Throwable e) {
      while(e != null) {
         if(CACHE_STOPPED_EXCEPTION.equals(e.getClass().getName())) {
            return true;
         }

         e = e.getCause();
      }

      return false;
   }

   private static final String CACHE_STOPPED_EXCEPTION =
      "org.apache.ignite.internal.processors.cache.CacheStoppedException";
   private static final Set<String> CLIENT_ABORT_EXCEPTIONS = new HashSet<>(
      Arrays.asList("org.apache.catalina.connector.ClientAbortException",
                    "org.eclipse.jetty.io.EofException"));
   private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
}
