/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest;

import inetsoft.util.CoreTool;
import org.apache.http.client.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Date;
import java.util.concurrent.Callable;

/**
 * Class that handles http error responses for Rest datasources
 * Override handleError to implement datasource-specific error handling
 */
public class RestErrorHandler {

   /**
    * Run a method that should have its http error response handled
    * @param fn Method to run
    * @param <T> Method's return type
    * @return Message telling if execution should continue and the result of the method
    * @throws Exception Unhandled exception
    */
   public final <T> RestErrorResponse<T> catchError(Callable<T> fn) throws Exception {
      try {
         T returnValue = fn.call();
         return new RestErrorResponse<>(true, returnValue);
      }
      catch(RestResponseException ex) {
         return handleError(ex, fn);
      }
   }

   public final RestErrorResponse catchError(ErrorHandledMethod fn) throws Exception {
      Callable wrapperFunction = () -> {
         fn.call();
         return null;
      };

      try {
         Object returnValue = wrapperFunction.call();
         return new RestErrorResponse(true, returnValue);
      }
      catch(RestResponseException ex) {
         return handleError(ex, wrapperFunction);
      }
   }

   /**
    * Handle an exception caused by an http error response
    * @param ex The exception to handle. Includes the http response for the error.
    * @param fn The function that caused the error.
    *
    * @return if the execution of the query runner should continue
    *
    * @throws Exception if we do not want to handle the exception here
    */
   protected <T> RestErrorResponse<T> handleError(RestResponseException ex, Callable<T> fn) throws Exception {
      RestErrorResponse<T> handlerResponse;

      handlerResponse = handleRetryAfter(ex, fn);

      if(handlerResponse.shouldContinue()) {
         return handlerResponse;
      }

      logError("Error executing Rest query: " + ex.getMessage(), ex);
      handlerResponse = new RestErrorResponse<>(false, null);
      return handlerResponse;
   }

   /**
    * Log an error message and show it to the user
    * @param errorMsg Message to show to the user
    * @param ex Exception to log
    */
   protected void logError(String errorMsg, RestResponseException ex) {
      CoreTool.addUserMessage(errorMsg);

      if(LOG.isDebugEnabled()) {
         LOG.error("Failed to run query.", ex);
      }
      else {
         LOG.error("Failed to run query: " + ex);
      }
   }

   /**
    * Common method of handling 429 errors. Response must contain a Retry-After header.
    * If the Retry-After timeout is too long, it will be ignored.
    * @param ex Exception to handle
    * @param fn Method to retry
    * @param <T> Method's return type
    * @return If retry was successful and the method's return value
    * @throws Exception Unhandled Exception
    */
   protected  <T> RestErrorResponse<T> handleRetryAfter(RestResponseException ex, Callable<T> fn) throws Exception {
      RestErrorResponse<T> handlerResponse = new RestErrorResponse<>(false, null);
      HttpResponse httpResponse = ex.getResponse();
      boolean success = false;

      if(httpResponse.getResponseStatusCode() != 429 ||
         httpResponse.getResponseHeaderValue("Retry-After") == null)
      {
         return handlerResponse;
      }

      int retryAfter = parseRetryAfter(httpResponse.getResponseHeaderValue("Retry-After"));

      //Do not retry if the timeout is too long
      while(!handlerResponse.shouldContinue() && retryAfter < RETRY_AFTER_TIMEOUT) {
         try {
            Thread.sleep(retryAfter * 1000);
            T returnValue = fn.call();
            handlerResponse = new RestErrorResponse<>(true, returnValue);
         }
         catch(RestResponseException ex2) {
            httpResponse = ex2.getResponse();

            if(httpResponse.getResponseStatusCode() == 429 &&
               httpResponse.getResponseHeaderValue("Retry-After") != null)
            {
               retryAfter = parseRetryAfter(httpResponse.getResponseHeaderValue("Retry-After"));
            }
            else {
               break;
            }
         }
      }

      return handlerResponse;
   }

   private int parseRetryAfter(String response) {
      int retryAfter = RETRY_AFTER_TIMEOUT; //retry will fail if value is not changed

      try {
         retryAfter = Integer.parseInt(response);
      }
      catch(NumberFormatException e) {
         Date retryDate = DateUtils.parseDate(response);
         Date currentDate = new Date();

         if(retryDate != null) {
            long timeDiff = (retryDate.getTime() - currentDate.getTime()) / 1000;

            if(timeDiff < RETRY_AFTER_TIMEOUT) {
               retryAfter = (int) timeDiff;
            }
         }

      }

      return retryAfter;
   }

   @FunctionalInterface
   public interface ErrorHandledMethod {
      void call() throws Exception;
   }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   protected final int RETRY_AFTER_TIMEOUT = 30;
}
