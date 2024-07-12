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
package inetsoft.web.admin.security.user;

import inetsoft.sree.security.IdentityID;
import inetsoft.web.admin.security.IdentityModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public interface EntityModel {
   String name();

   @Value.Default
   default boolean root() {
      return false;
   }

   @Value.Default
   default List<IdentityID> identityNames() {
      return new ArrayList<>();
   }

   @Value.Default
   default List<IdentityModel> members() {
      return new ArrayList<>();
   }

   @Value.Default
   default List<IdentityID> roles() {
      return new ArrayList<>();
   }

   @Value.Default
   default List<IdentityModel> permittedIdentities() {
      return new ArrayList<>();
   }

   @Value.Default
   default boolean editable() {
      return true;
   }

   @Value.Default
   default String oldName() {
      return "";
   }

   @Nullable
   String theme();
}
