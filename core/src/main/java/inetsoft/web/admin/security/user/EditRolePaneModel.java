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
package inetsoft.web.admin.security.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableEditRolePaneModel.class)
@JsonDeserialize(as = ImmutableEditRolePaneModel.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface EditRolePaneModel extends EntityModel {
   @Value.Default
   default boolean defaultRole() { return false; }

   @Value.Default
   @Nullable
   default String organization() {return null;}

   boolean isSysAdmin();

   boolean isOrgAdmin();

   @Value.Default
   default boolean enterprise() {
      return false;
   }

   @Value.Default
   @Nullable
   default String description() { return ""; }

   static EditRolePaneModel.Builder builder() {
      return new EditRolePaneModel.Builder();
   }

   final class Builder extends ImmutableEditRolePaneModel.Builder {
   }
}
