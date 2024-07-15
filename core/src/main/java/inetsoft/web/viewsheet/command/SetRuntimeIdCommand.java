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
package inetsoft.web.viewsheet.command;

import java.util.Set;

/**
 * Command used to notify the client of the viewsheet runtime identifier.
 *
 * @since 12.3
 */
public class SetRuntimeIdCommand implements ViewsheetCommand {
   /**
    * Creates a new instance of <tt>SetRuntimeIdCommand</tt>.
    */
   public SetRuntimeIdCommand() {
   }

   /**
    * Creates a new instance of <tt>SetRuntimeIdCommand</tt>.
    *
    * @param runtimeId the runtime identifier.
    */
   public SetRuntimeIdCommand(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   /**
    * Creates a new instance of {@code SetRuntimeIdCommand}.
    *
    * @param runtimeId   the runtime identifier.
    * @param permissions the viewsheet permissions.
    */
   public SetRuntimeIdCommand(String runtimeId, Set<String> permissions) {
      this.runtimeId = runtimeId;
      this.permissions = permissions;
   }

   /**
    * Gets the viewsheet runtime identifier.
    *
    * @return the runtime identifier.
    */
   public String getRuntimeId() {
      return runtimeId;
   }

   /**
    * Sets the viewsheet runtime identifier.
    *
    * @param runtimeId the runtime identifier.
    */
   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   /**
    * Gets the viewsheet permissions.
    *
    * @return the viewsheet permissions.
    */
   public Set<String> getPermissions() {
      return permissions;
   }

   /**
    * Sets the viewsheet permissions.
    *
    * @param permissions the viewsheet permissions.
    */
   public void setPermissions(Set<String> permissions) {
      this.permissions = permissions;
   }

   private String runtimeId;
   private Set<String> permissions;
}
