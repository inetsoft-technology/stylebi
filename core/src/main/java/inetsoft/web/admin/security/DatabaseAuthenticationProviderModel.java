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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableDatabaseAuthenticationProviderModel.class)
@JsonDeserialize(as = ImmutableDatabaseAuthenticationProviderModel.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface DatabaseAuthenticationProviderModel {
   String driver();

   String url();

   @Value.Default
   default boolean requiresLogin() { return true; }

   @Value.Default
   default boolean useCredential() { return false; }

   @Nullable
   String secretId();

   @Nullable
   String user();

   @Nullable
   String password();

   String hashAlgorithm();

   @Nullable
   String userQuery();

   @Nullable
   String groupListQuery();

   @Nullable
   String userListQuery();

   @Nullable
   String groupUsersQuery();

   @Nullable
   String roleListQuery();

   @Nullable
   String userRolesQuery();

   @Nullable
   String userRoleListQuery();

   @Nullable
   String organizationListQuery();

   @Nullable
   String organizationNameQuery();

   @Nullable
   String organizationMembersQuery();

   @Nullable
   String organizationRolesQuery();

   @Value.Default
   default boolean appendSalt() {
      return false;
   }

   @Nullable
   String userEmailsQuery();

   @Nullable
   String sysAdminRoles();

   @Nullable
   String orgAdminRoles();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableDatabaseAuthenticationProviderModel.Builder {
   }
}