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
package inetsoft.uql.rest;

/**
 * Response from the RestErrorHandler.
 * It tells if execution should continue and the return value of the handled method.
 * @param <T> Type of the method's return value.
 */
public class RestErrorResponse<T> {
   public RestErrorResponse(boolean continueExecution, T returnValue) {
      this.continueExecution = continueExecution;
      this.returnValue = returnValue;
   }

   public boolean shouldContinue() {
      return continueExecution;
   }

   public T getValue() {
      return  returnValue;
   }

   public T getHandledResult() {
      if(continueExecution) {
         return returnValue;
      }
      else {
         return null;
      }
   }

   public int getHandledIntResult() {
      if(continueExecution) {
         return (Integer) returnValue;
      }
      else {
         return 0;
      }
   }

   public boolean getHandledBoolResult() {
      if(continueExecution) {
         return (Boolean) returnValue;
      }
      else {
         return false;
      }
   }

   private final boolean continueExecution;
   private final T returnValue;
}
