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
 * {@code RepairRepositoryFoldersStatus} contains the response from repairing the repository
 * folders.
 */
@Schema(description = "The response from repairing the repository folders.")
public class RepairRepositoryFoldersStatus {
   /**
    * Gets the token for the repair task.
    *
    * @return the token.
    */
   @NotNull
   @Schema(description = "The token for the repair task.", example = "1234abcd")
   public String getToken() {
      return token;
   }

   /**
    * Sets the token for the repair task.
    *
    * @param token the token.
    */
   public void setToken(String token) {
      this.token = token;
   }

   /**
    * Gets a flag that indicates if the repair task is complete.
    *
    * @return {@code true} if complete of {@code false} if not.
    */
   @NotNull
   @Schema(description = "A flag that indicates if the rebuild task is complete.", example = "true")
   public boolean isComplete() {
      return complete;
   }

   /**
    * Sets a flag that indicates if the repair task is complete.
    *
    * @param complete {@code true} if complete of {@code false} if not.
    */
   public void setComplete(boolean complete) {
      this.complete = complete;
   }

   /**
    * Gets a flag that indicates if the repair task failed.
    *
    * @return {@code true} if the repair failed or {@code false} if it succeeded.
    */
   @NotNull
   @Schema(description = "A flag that indicates if the repair task failed.", example = "false")
   public boolean isFailed() {
      return failed;
   }

   /**
    * Sets a flag that indicates if the repair task failed.
    *
    * @param failed {@code true} if the repair failed or {@code false} if it succeeded.
    */
   public void setFailed(boolean failed) {
      this.failed = failed;
   }

   /**
    * Gets the error for the failed repair task, if any.
    *
    * @return the error message.
    */
   @Schema(
      description = "The error message for the failed repair task, if any.",
      example = "error message")
   public String getError() {
      return error;
   }

   /**
    * Sets the error for the failed repair task, if any.
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
