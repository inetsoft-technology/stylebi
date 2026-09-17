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
package inetsoft.web.admin.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * {@code RebuildDependenciesStatus} contains the status of a rebuild of the dependency graph.
 */
@Schema(description = "The status of a rebuild of the dependency graph.")
public class RebuildDependenciesStatus {
   /**
    * Gets the token for the rebuild task.
    *
    * @return the token.
    */
   @NotNull
   @Schema(description = "The token for the rebuild task.", example = "1234abcd")
   public String getToken() {
      return token;
   }

   /**
    * Sets the token for the rebuild task.
    *
    * @param token the token.
    */
   public void setToken(String token) {
      this.token = token;
   }

   /**
    * Gets a flag that indicates if the rebuild task is complete.
    *
    * @return {@code true} if complete or {@code false} if not.
    */
   @NotNull
   @Schema(
      description = "A flag that indicates if the rebuild task is complete.",
      example = "true")
   public boolean isComplete() {
      return complete;
   }

   /**
    * Gets a flag that indicates if the rebuild task is complete.
    *
    * @param complete {@code true} if complete or {@code false} if not.
    */
   public void setComplete(boolean complete) {
      this.complete = complete;
   }

   /**
    * Gets a flag that indicates if the rebuild task failed.
    *
    * @return {@code true} if the rebuild failed or {@code false} if it succeeded.
    */
   @NotNull
   @Schema(
      description = "A flag that indicates if the rebuild task failed.",
      example = "false")
   public boolean isFailed() {
      return failed;
   }

   /**
    * Sets a flag that indicates if the rebuild task failed.
    *
    * @param failed {@code true} if the rebuild failed or {@code false} if it succeeded.
    */
   public void setFailed(boolean failed) {
      this.failed = failed;
   }

   /**
    * Gets the error message for the failed rebuild task, if any.
    *
    * @return the error message.
    */
   @Schema(
      description = "The error message for the failed rebuild task, if any.",
      example = "error message")
   public String getError() {
      return error;
   }

   /**
    * Sets the error message for the failed rebuild task, if any.
    *
    * @param error the error message.
    */
   public void setError(String error) {
      this.error = error;
   }

   private String token;
   private boolean complete;
   private boolean failed;
   private String error;
}
