/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.security.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * ApiError describes a server-side error during an API call.
 */
public class ApiError {
   /**
    * Code for a generic, otherwise unspecified, error.
    */
   public static final int ERROR_GENERIC = 0x00;

   /**
    * Code for an error caused by an unauthorized access to a resource.
    */
   public static final int ERROR_UNAUTHORIZED = 0x01;

   /**
    * Code for an error caused by a request for a resource that does not exist.
    */
   public static final int ERROR_MISSING_RESOURCE = 0x02;

   /**
    * Code for an error to create a resource that already exists.
    */
   public static final int ERROR_EXISTING_RESOURCE = 0x03;

   /**
    * Code for an error caused by a request for a resource that is an invalid target.
    */
   public static final int INVALID_TARGET_RESOURCE = 0x04;

   /**
    * Creates a new instance of {@code ApiError}.
    */
   public ApiError() {
   }

   /**
    * Creates a new instance of {@code ApiError}.
    *
    * @param code    the error code.
    * @param type    the error type.
    * @param message the error message.
    */
   public ApiError(int code, String type, String message) {
      setCode(code);
      setType(type);
      setMessage(message);
   }

   /**
    * Creates a new instance of {@code ApiError}.
    *
    * @param code  the error code.
    * @param cause the cause of the error.
    */
   public ApiError(int code, Throwable cause) {
      this(code, cause.getClass().getSimpleName(), String.valueOf(cause.getMessage()));
   }

   /**
    * Gets the code that identifies the type of error.
    *
    * @return the error code.
    */
   @NotNull
   @Schema(description = "The code that identifies the type of error.")
   public int getCode() {
      return code;
   }

   /**
    * Sets the code that identifies the type of error.
    *
    * @param code the error code.
    */
   public void setCode(int code) {
      this.code = code;
   }

   /**
    * Gets a string that describes the type of error.
    *
    * @return the error type.
    */
   @NotNull
   @Schema(description = "A string that describes the type of error.")
   public String getType() {
      return type;
   }

   /**
    * Sets a string that describes the type of error.
    *
    * @param type the error type.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Get the error message.
    *
    * @return the error message.
    */
   @NotNull
   @Schema(description = "The error message.")
   public String getMessage() {
      return message;
   }

   /**
    * Set the error message.
    *
    * @param message the error message.
    */
   public void setMessage(String message) {
      this.message = message;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ApiError apiError = (ApiError) o;
      return code == apiError.code &&
         Objects.equals(type, apiError.type) &&
         Objects.equals(message, apiError.message);
   }

   @Override
   public int hashCode() {
      return Objects.hash(code, type, message);
   }

   @Override
   public String toString() {
      return "ApiError{" +
         "code=" + code +
         ", type='" + type + '\'' +
         ", message='" + message + '\'' +
         '}';
   }

   private int code;
   private String type;
   private String message;
}
