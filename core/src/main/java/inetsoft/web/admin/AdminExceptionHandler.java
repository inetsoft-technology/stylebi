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
package inetsoft.web.admin;

import inetsoft.util.MessageException;
import inetsoft.util.log.LogManager;
import inetsoft.web.GlobalExceptionHandler;
import inetsoft.web.security.auth.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * @hidden
 */
@ControllerAdvice(basePackages = { "inetsoft.web.admin" })
public class AdminExceptionHandler {
   /**
    * Error handler for a request for a missing resource.
    */
   @ExceptionHandler(MissingResourceException.class)
   @ResponseBody
   @ResponseStatus(HttpStatus.NOT_FOUND)
   @ApiResponses({
      @ApiResponse(
         responseCode = "404",
         description = "The requested resource was not found.")
   })
   public MissingResourceError handleMissingResource(MissingResourceException e) {
      return new MissingResourceError(e);
   }

   /**
    * Error handler for a request for an invalid resource target.
    */
   @ExceptionHandler(InvalidResourceException.class)
   @ResponseBody
   @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
   @ApiResponses({
      @ApiResponse(
         responseCode = "405",
         description = "The requested resource was not a valid target for the request.")
   })
   public InvalidResourceError handleInvalidResource(InvalidResourceException e) {
      return new InvalidResourceError(e);
   }

   /**
    * Error handler for a request to create an existing resource.
    */
   @ExceptionHandler(ResourceExistsException.class)
   @ResponseBody
   @ResponseStatus(HttpStatus.CONFLICT)
   @ApiResponses({
      @ApiResponse(
         responseCode = "409",
         description = "The resource already exists.")
   })
   public ResourceExistsError handleExistingResource(ResourceExistsException e) {
      return new ResourceExistsError(e);
   }

   /**
    * Error handler for removed endpoints.
    */
   @ExceptionHandler(UnsupportedOperationException.class)
   @ResponseBody
   @ResponseStatus(HttpStatus.GONE)
   @ApiResponses({
      @ApiResponse(
         responseCode = "410",
         description = "The endpoint has been removed."
      )
   })
   public GenericError handleUnsupportedOperation(UnsupportedOperationException e) {
      return new GenericError(e);
   }

   /**
    * Generic error handler.
    */
   @ExceptionHandler(Exception.class)
   @ResponseBody
   @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
   @ApiResponses({
      @ApiResponse(
         responseCode = "500",
         description = "An error occurred on the server while processing the request.")
   })
   public GenericError handleException(Exception e) {
      return (e instanceof MessageException) ?
         handleMessageException((MessageException) e) : handleGenericException(e);
   }

   private GenericError handleGenericException(Exception e) {
      Throwable cause = e.getCause();
      boolean isClientAbortException =
         GlobalExceptionHandler.isClientAbortException(e.getClass().getName());
      boolean causedByClientAbortException = cause != null &&
         GlobalExceptionHandler.isClientAbortException(cause.getClass().getName());

      if(isClientAbortException || causedByClientAbortException) {
         LOG.debug("Client closed connection", e);
      }
      else {
         LOG.error("Unexpected error", e);
      }

      return new GenericError(e);
   }

   private GenericError handleMessageException(MessageException e) {
      MessageException thrown = e.isDumpStack() ? e : null;
      LogManager.getInstance().logException(LOG, e.getLogLevel(), e.getMessage(), thrown);
      return new GenericError(e);
   }

   private static final Logger LOG = LoggerFactory.getLogger(AdminExceptionHandler.class);
}
