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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.AuthenticationProvider;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import inetsoft.sree.security.ldap.*;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableAuthenticationProviderModel.class)
@JsonDeserialize(as = ImmutableAuthenticationProviderModel.class)
public abstract class AuthenticationProviderModel {
   public abstract String providerName();
   @Nullable
   public abstract Boolean dbProviderEnabled();
   @Nullable
   public abstract Boolean customProviderEnabled();

   @Value.Default
   public  boolean ldapProviderEnabled() {
      return true;
   }

   @JsonProperty("providerType")
   public abstract SecurityProviderType providerType();

   @Nullable
   public abstract LdapAuthenticationProviderModel ldapProviderModel();

   @Nullable
   public abstract DatabaseAuthenticationProviderModel dbProviderModel();

   @Nullable
   public abstract CustomProviderModel customProviderModel();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableAuthenticationProviderModel.Builder {
      public Builder ldapProviderModel(LdapAuthenticationProvider provider) {
         LdapAuthenticationProviderModel.Builder builder = LdapAuthenticationProviderModel.builder()
            .ldapServer(provider instanceof ADAuthenticationProvider ?
                           SecurityProviderType.ACTIVE_DIRECTORY : SecurityProviderType.GENERIC)
            .protocol(provider.getProtocol())
            .hostName(provider.getHost())
            .hostPort(provider.getPort())
            .rootDN(provider.getRootDn())
            .adminID(provider.getLdapAdministrator())
            .password(provider.getPassword())
            .userFilter(provider.getUserSearch())
            .userBase(provider.getUserBase())
            .userAttr(provider.getUserAttribute())
            .mailAttr(provider.getMailAttribute())
            .roleAttr(provider.getRoleAttribute())
            .groupFilter(provider.getGroupSearch())
            .groupBase(provider.getGroupBase())
            .groupAttr(provider.getGroupAttribute())
            .roleFilter(provider.getRoleSearch())
            .roleBase(provider.getRoleBase())
            .userRoleFilter(provider.getUserRolesSearch())
            .searchTree(provider.isSearchSubtree())
            .sysAdminRoles(String.join(",", provider.getSystemAdministratorRoles()));

         if(provider instanceof GenericLdapAuthenticationProvider genericProvider) {
            builder
               .userRoleFilter(provider.getUserRolesSearch())
               .groupRoleFilter(genericProvider.getGroupRolesSearch())
               .roleRoleFilter(genericProvider.getRoleRolesSearch())
               .startTls(genericProvider.isStartTls());
         }

         return ldapProviderModel(builder.build());
      }

      public Builder dbProviderModel(DatabaseAuthenticationProvider provider) {
         return dbProviderModel(
            DatabaseAuthenticationProviderModel.builder()
               .driver(provider.getDriver())
               .url(provider.getUrl())
               .requiresLogin(provider.isRequiresLogin())
               .user(provider.getDbUser())
               .password(provider.getDbPassword())
               .hashAlgorithm(provider.getHashAlgorithm())
               .userQuery(provider.getUserQuery())
               .userListQuery(provider.getUserListQuery())
               .groupListQuery(provider.getGroupListQuery())
               .roleListQuery(provider.getRoleListQuery())
               .organizationListQuery(provider.getOrganizationListQuery())
               .organizationIdQuery(provider.getOrganizationIdQuery())
               .organizationMembersQuery(provider.getOrganizationMembersQuery())
               .organizationRolesQuery(provider.getOrganizationRolesQuery())
               .groupUsersQuery(provider.getGroupUsersQuery())
               .userRolesQuery(provider.getUserRolesQuery())
               .userEmailsQuery(provider.getUserEmailsQuery())
               .appendSalt(provider.isAppendSalt())
               .sysAdminRoles(String.join(",", provider.getSystemAdministratorRoles()))
               .orgAdminRoles(String.join(",", provider.getOrgAdministratorRoles()))
               .build());
      }

      public Builder customProviderModel(AuthenticationProvider provider, ObjectMapper mapper) {
         JsonNode configNode = provider.writeConfiguration(mapper);
         String jsonConfiguration;

         try {
            jsonConfiguration =
               mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configNode);
         }
         catch(JsonProcessingException e) {
            throw new RuntimeException("Failed to write configuration JSION", e);
         }

         return customProviderModel(
            CustomProviderModel.builder()
               .className(provider.getClass().getName())
               .jsonConfiguration(jsonConfiguration)
               .build()
         );
      }
   }
}
