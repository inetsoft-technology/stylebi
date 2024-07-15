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
package inetsoft.web.admin;

import inetsoft.web.security.auth.ApiError;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

/**
 * {@code MissingResourceError} contains information about an HTTP 404 error.
 */
@Validated
@Schema(description = "Error returned when the requested resource does not exist.")
public class MissingResourceError extends ApiError {
   public MissingResourceError() {
   }

   public MissingResourceError(String type, String message) {
      super(ERROR_MISSING_RESOURCE, type, message);
   }

   public MissingResourceError(Throwable cause) {
      super(ERROR_MISSING_RESOURCE, cause);
   }

   @Override
   @NotNull
   @Schema(
      description = "The code that identifies the type of error.",
      example = "2")
   public int getCode() {
      return super.getCode();
   }

   @Override
   @NotNull
   @Schema(
      description = "A string that describes the type of error.",
      example = "MissingResourceException")
   public String getType() {
      return super.getType();
   }

   @Override
   @NotNull
   @Schema(
      description = "The error message.",
      example = "The requested resource was not found.")
   public String getMessage() {
      return super.getMessage();
   }
}
