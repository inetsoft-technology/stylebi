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

import inetsoft.web.security.auth.ApiError;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

/**
 * {@code ResourceExistsError} contains information about an HTTP 409 error.
 */
@Validated
@Schema(description = "Error returned when an attempt is made to create a resource that already exists.")
public class ResourceExistsError extends ApiError {
   public ResourceExistsError() {
   }

   public ResourceExistsError(String type, String message) {
      super(ERROR_EXISTING_RESOURCE, type, message);
   }

   public ResourceExistsError(Throwable cause) {
      super(ERROR_EXISTING_RESOURCE, cause);
   }

   @Override
   @NotNull
   @Schema(
      description = "The code that identifies the type of error.",
      example = "3")
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
      example = "A resource with the specified location already exists.")
   public String getMessage() {
      return super.getMessage();
   }
}
