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
package inetsoft.web.admin.security;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

/**
 * {@code UserPassword} contains the password for a user.
 */
@Validated
@Schema(description = "The password for a user.")
public class UserPassword {
   /**
    * Creates a new instance of {@code UserPassword}.
    */
   public UserPassword() {
   }

   /**
    * Gets the user's password.
    *
    * @return the clear-text password.
    */
   @NotNull
   @Schema(description = "The new, clear-text password for the user.", example = "success123")
   public String getPassword() {
      return password;
   }

   /**
    * Sets the user's password.
    *
    * @param password the clear-text password.
    */
   public void setPassword(String password) {
      this.password = password;
   }

   private String password;
}
