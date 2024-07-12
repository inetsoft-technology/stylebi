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
package inetsoft.web.security.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

/**
 * {@code UnauthorizedAccessError} contains information about an HTTP 401 error.
 */
@Validated
@Schema(description = "Error returned when an unauthorized access to a resource was attempted.")
public class UnauthorizedAccessError extends ApiError {
   public UnauthorizedAccessError() {
   }

   public UnauthorizedAccessError(String type, String message) {
      super(ERROR_UNAUTHORIZED, type, message);
   }

   public UnauthorizedAccessError(Throwable cause) {
      super(ERROR_UNAUTHORIZED, cause);
   }

   @Override
   @NotNull
   @Schema(
      description = "The code that identifies the type of error.",
      example = "1")
   public int getCode() {
      return super.getCode();
   }

   @Override
   @NotNull
   @Schema(
      description = "A string that describes the type of error.",
      example = "UnauthorizedAccessException")
   public String getType() {
      return super.getType();
   }

   @Override
   @NotNull
   @Schema(
      description = "The error message.",
      example = "You lack permission to access the requested resource.")
   public String getMessage() {
      return super.getMessage();
   }
}
