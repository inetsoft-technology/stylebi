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
import org.jetbrains.annotations.NotNull;

/**
 * {@code SecurityEnabledFlag} contains a flag indicating if security is enabled or disabled.
 */
@Schema(description = "A flag indicating if security is enabled or disabled.")
public class SecurityEnabledFlag {
   /**
    * Gets a flag that indicates if security is enabled.
    *
    * @return {@code true} if enabled or {@code false} if disabled.
    */
   @Schema(
      description = "A flag indicating if security is enabled.",
      example = "true"
   )
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * Sets a flag that indicates if security is enabled.
    *
    * @param enabled {@code true} if enabled or {@code false} if disabled.
    */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   private boolean enabled;
}
