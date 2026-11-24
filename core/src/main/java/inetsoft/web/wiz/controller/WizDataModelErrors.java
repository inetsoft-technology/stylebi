/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.controller;

import inetsoft.uql.service.DataSourceStorageException;
import inetsoft.util.MessageException;
import org.apache.commons.io.FileExistsException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Turns the business outcomes of a data model route into the {@code 200 {ok:false, ...}} body.
 *
 * <p>{@link #run} converts {@code FileNotFoundException}, {@code FileExistsException},
 * {@code MessageException} and {@link DmFailure}; it lets {@code SecurityException} (a 403) and
 * {@code IllegalArgumentException} (a 400) pass so {@code WizControllerErrorHandler} maps them.
 * A 422 is never used: the advice maps it to {@code UnsupportedDatasourceException}.</p>
 */
final class WizDataModelErrors {
   private WizDataModelErrors() {
   }

   private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(WizDataModelErrors.class);

   /** A route body that may throw. */
   @FunctionalInterface
   interface Body<T> {
      T call() throws Exception;
   }

   /** Runs the body; a business outcome becomes the {@code Failure} it describes. */
   static Object run(Body<?> body) throws Exception {
      try {
         return body.call();
      }
      catch(DmFailure failure) {
         return failure.toBody();
      }
      catch(FileNotFoundException e) {
         return WizDataModelWire.Failure.of(WizDataModelWire.NOT_FOUND, e.getMessage());
      }
      catch(FileExistsException e) {
         return WizDataModelWire.Failure.of(WizDataModelWire.DUPLICATE_NAME, e.getMessage());
      }
      catch(DataSourceStorageException e) {
         // the message is written by the server (the object path and plain text), not by a driver
         LOG.error("A data model operation could not be saved", e);
         return WizDataModelWire.Failure.of(WizDataModelWire.STORAGE_UNAVAILABLE, e.getMessage());
      }
      catch(MessageException e) {
         return WizDataModelWire.Failure.of(WizDataModelWire.UNKNOWN, e.getMessage());
      }
      catch(SecurityException | IllegalArgumentException | UnsupportedOperationException |
            inetsoft.sree.security.SecurityException e)
      {
         // mapped to 403, 400 and 501 by WizControllerErrorHandler
         throw e;
      }
      catch(Exception e) {
         // any other exception is a server failure whose text can carry a credential (a driver
         // or storage message with a URL password): never returned as a business message
         LOG.error("A data model operation failed", e);

         if(isStorageFailure(e)) {
            return WizDataModelWire.Failure.of(
               WizDataModelWire.STORAGE_UNAVAILABLE, "the server's storage failed or did not " +
               "respond in time, so the operation may not have completed; check the result " +
               "and retry");
         }

         return WizDataModelWire.Failure.of(
            WizDataModelWire.UNKNOWN, "the data model operation failed on the server; " +
            "see the server log");
      }
   }

   private static boolean isStorageFailure(Throwable e) {
      for(Throwable t = e; t != null; t = t.getCause()) {
         if(t instanceof IOException || t instanceof TimeoutException) {
            return true;
         }
      }

      return false;
   }

   /** An internal business-outcome exception: reason, optional field, message. */
   static final class DmFailure extends RuntimeException {
      DmFailure(String reason, String message) {
         this(reason, null, message, null, null);
      }

      DmFailure(String reason, String field, String message) {
         this(reason, field, message, null, null);
      }

      DmFailure(String reason, String field, String message, List<WizDataModelWire.Err> errors,
                String existingKind)
      {
         super(message);
         this.reason = reason;
         this.field = field;
         this.errors = errors;
         this.existingKind = existingKind;
      }

      String reason() {
         return reason;
      }

      String field() {
         return field;
      }

      WizDataModelWire.Failure toBody() {
         return new WizDataModelWire.Failure(
            false, reason, getMessage(), field, errors, existingKind);
      }

      private final String reason;
      private final String field;
      private final List<WizDataModelWire.Err> errors;
      private final String existingKind;
   }
}
