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
package inetsoft.web.admin.ai.providers;

import inetsoft.web.admin.security.*;

import java.util.List;
import java.util.TreeSet;

/**
 * Canonical string projections used two ways (01-spec.md section 5): the *whole-chain*, order-
 * sensitive projection that feeds the plan hash (never stored in a {@code PlanChange} -- see
 * {@link ProviderChangePlanService}'s own hash method), and the *narrower*, per-provider DTO
 * projection that feeds {@code PlanChange.currentValue}/{@code proposedValue} and the audit
 * before/after value. Password is never included as literal content (section 9) -- only a
 * presence/absence token, so a plan whose only change is a different password still perturbs the
 * hash without exposing the value or its length; {@code secretId} is not secret-classified and is
 * projected verbatim.
 */
final class ProviderProjection {
   private ProviderProjection() {
   }

   /**
    * The whole-chain projection (section 5): name + "|" + type, in list order, no sort step --
    * order is semantic (the resolution order {@code isSystemAdministratorRole} depends on, section
    * 4), not an implementation artifact to normalize away. {@code withLdapFlag} folds the LDAP flag
    * into "type" for the authentication chain (the authorization chain has no equivalent
    * distinction available from {@link SecurityProviderStatus} alone, section 5).
    */
   static String projectChain(List<SecurityProviderStatus> providers, boolean withLdapFlag) {
      StringBuilder sb = new StringBuilder();

      for(SecurityProviderStatus p : providers) {
         sb.append(p.name()).append('|')
            .append(withLdapFlag ? (p.ldap() ? "LDAP" : "OTHER") : "-").append(';');
      }

      return sb.toString();
   }

   static String projectFileProvider(String name) {
      StringBuilder sb = new StringBuilder();
      append(sb, "name", name);
      append(sb, "type", "FILE");
      return sb.toString();
   }

   /** Used for a create's {@code proposedValue} (no live object yet) and mirrors
    * {@link #projectLdapModel} field-for-field so the hash is comparable across preview and apply. */
   static String projectLdapSpec(String name, ProviderLdapSpec spec) {
      StringBuilder sb = new StringBuilder();
      append(sb, "name", name);
      append(sb, "type", "LDAP");
      append(sb, "ldapServer", spec.getLdapServer());
      append(sb, "protocol", spec.getProtocol());
      append(sb, "hostName", spec.getHostName());
      append(sb, "hostPort", String.valueOf(spec.getHostPort()));
      append(sb, "rootDN", spec.getRootDN());
      append(sb, "useCredential", String.valueOf(Boolean.TRUE.equals(spec.getUseCredential())));
      append(sb, "adminID", spec.getAdminID());
      append(sb, "secretId", spec.getSecretId());
      append(sb, "password", isBlank(spec.getPassword()) ? "pw:unset" : "pw:set");
      append(sb, "userFilter", spec.getUserFilter());
      append(sb, "userBase", spec.getUserBase());
      append(sb, "userAttr", spec.getUserAttr());
      append(sb, "mailAttr", spec.getMailAttr());
      append(sb, "groupFilter", spec.getGroupFilter());
      append(sb, "groupBase", spec.getGroupBase());
      append(sb, "groupAttr", spec.getGroupAttr());
      append(sb, "roleFilter", spec.getRoleFilter());
      append(sb, "roleBase", spec.getRoleBase());
      append(sb, "roleAttr", spec.getRoleAttr());
      append(sb, "userRoleFilter", spec.getUserRoleFilter());
      append(sb, "roleRoleFilter", spec.getRoleRoleFilter());
      append(sb, "groupRoleFilter", spec.getGroupRoleFilter());
      append(sb, "startTls", String.valueOf(Boolean.TRUE.equals(spec.getStartTls())));
      append(sb, "searchTree", String.valueOf(Boolean.TRUE.equals(spec.getSearchTree())));
      appendSorted(sb, "sysAdminRoles", spec.getSysAdminRoles());
      return sb.toString();
   }

   /** Used for a create's {@code proposedValue} (no live object yet) and mirrors
    * {@link #projectDatabaseModel} field-for-field so the hash is comparable across preview and
    * apply -- the same pairing convention {@link #projectLdapSpec}/{@link #projectLdapModel}
    * already establishes for LDAP (bug 76716). */
   static String projectDatabaseSpec(String name, ProviderDatabaseSpec spec) {
      StringBuilder sb = new StringBuilder();
      append(sb, "name", name);
      append(sb, "type", "DATABASE");
      append(sb, "driver", spec.getDriver());
      append(sb, "url", spec.getUrl());
      append(sb, "requiresLogin", String.valueOf(spec.getRequiresLogin() == null ||
                                                 spec.getRequiresLogin()));
      append(sb, "useCredential", String.valueOf(Boolean.TRUE.equals(spec.getUseCredential())));
      append(sb, "secretId", spec.getSecretId());
      append(sb, "user", spec.getUser());
      append(sb, "password", isBlank(spec.getPassword()) ? "pw:unset" : "pw:set");
      append(sb, "hashAlgorithm", spec.getHashAlgorithm());
      append(sb, "userQuery", spec.getUserQuery());
      append(sb, "userListQuery", spec.getUserListQuery());
      append(sb, "groupListQuery", spec.getGroupListQuery());
      append(sb, "groupUsersQuery", spec.getGroupUsersQuery());
      append(sb, "roleListQuery", spec.getRoleListQuery());
      append(sb, "userRolesQuery", spec.getUserRolesQuery());
      append(sb, "userRoleListQuery", spec.getUserRoleListQuery());
      append(sb, "organizationListQuery", spec.getOrganizationListQuery());
      append(sb, "organizationNameQuery", spec.getOrganizationNameQuery());
      append(sb, "organizationMembersQuery", spec.getOrganizationMembersQuery());
      append(sb, "organizationRolesQuery", spec.getOrganizationRolesQuery());
      append(sb, "appendSalt", String.valueOf(Boolean.TRUE.equals(spec.getAppendSalt())));
      append(sb, "userEmailsQuery", spec.getUserEmailsQuery());
      // Plain join, not appendSorted -- DatabaseAuthenticationProviderModel stores sysAdminRoles/
      // orgAdminRoles as a single ", "-joined String (unlike LdapAuthenticationProviderModel's
      // String[]), and projectDatabaseModel projects that stored string verbatim; joining the same
      // way here keeps this function's output directly comparable to projectDatabaseModel's, the
      // same "hash's unit and verification's unit can differ, but the two projections should still
      // read the same way" intent projectLdapSpec/projectLdapModel already establish.
      append(sb, "sysAdminRoles", spec.getSysAdminRoles() == null ? null :
            String.join(", ", spec.getSysAdminRoles()));
      append(sb, "orgAdminRoles", spec.getOrgAdminRoles() == null ? null :
            String.join(", ", spec.getOrgAdminRoles()));
      return sb.toString();
   }

   /** The read-back model always carries {@code Util.PLACEHOLDER_PASSWORD} for password, never the
    * real value (section 9) -- projected as a presence token identically to {@link #projectLdapSpec},
    * not as literal placeholder text, so the two are comparable across preview/apply. */
   static String projectLdapModel(String name, LdapAuthenticationProviderModel m) {
      StringBuilder sb = new StringBuilder();
      append(sb, "name", name);
      append(sb, "type", "LDAP");
      append(sb, "ldapServer", String.valueOf(m.ldapServer()));
      append(sb, "protocol", m.protocol());
      append(sb, "hostName", m.hostName());
      append(sb, "hostPort", String.valueOf(m.hostPort()));
      append(sb, "rootDN", m.rootDN());
      append(sb, "useCredential", String.valueOf(m.useCredential()));
      append(sb, "adminID", m.adminID());
      append(sb, "secretId", m.secretId());
      append(sb, "password", isBlank(m.password()) ? "pw:unset" : "pw:set");
      append(sb, "userFilter", m.userFilter());
      append(sb, "userBase", m.userBase());
      append(sb, "userAttr", m.userAttr());
      append(sb, "mailAttr", m.mailAttr());
      append(sb, "groupFilter", m.groupFilter());
      append(sb, "groupBase", m.groupBase());
      append(sb, "groupAttr", m.groupAttr());
      append(sb, "roleFilter", m.roleFilter());
      append(sb, "roleBase", m.roleBase());
      append(sb, "roleAttr", m.roleAttr());
      append(sb, "userRoleFilter", m.userRoleFilter());
      append(sb, "roleRoleFilter", m.roleRoleFilter());
      append(sb, "groupRoleFilter", m.groupRoleFilter());
      append(sb, "startTls", String.valueOf(Boolean.TRUE.equals(m.startTls())));
      append(sb, "searchTree", String.valueOf(m.searchTree()));
      appendSorted(sb, "sysAdminRoles", m.sysAdminRoles() == null ? null : List.of(m.sysAdminRoles()));
      return sb.toString();
   }

   /** {@code currentValue}/{@code proposedValue} for either chain (section 5/6/8) -- {@code null}
    * if the model itself is {@code null} (never expected once existence has been confirmed via the
    * chain list, but defensive rather than NPE-prone, section 2). Used two ways with two different
    * type populations: delete's {@code currentValue} only ever sees FILE/LDAP (this area's own
    * delete-target restriction refuses DATABASE/CUSTOM before this is reached, section 1, see
    * 04-build-java.md), but duplicate's {@code proposedValue}/apply-time {@code afterProjection}
    * (bug 76602) keeps the SOURCE provider's own type, which can be any of the four -- {@code
    * resolveDuplicate} deliberately applies no type restriction (see its own javadoc). Every type
    * gets its own field-by-field branch (bug 76655 -- DATABASE/CUSTOM used to fall through to
    * {@link #projectFileProvider}, silently mislabeling the type and discarding every
    * DATABASE/CUSTOM-specific field on the audit/plan record for a real, accepted call); a
    * type-specific model that is unexpectedly {@code null} (should not happen once providerType is
    * set, but not assumed) still gets its OWN type in the projection, via
    * {@link #projectMissingSpec}, rather than silently defaulting to FILE. */
   static String projectAuthenticationProvider(AuthenticationProviderModel model) {
      if(model == null) {
         return null;
      }

      switch(model.providerType()) {
      case LDAP:
         return model.ldapProviderModel() != null
            ? projectLdapModel(model.providerName(), model.ldapProviderModel())
            : projectMissingSpec(model.providerName(), SecurityProviderType.LDAP);
      case DATABASE:
         return model.dbProviderModel() != null
            ? projectDatabaseModel(model.providerName(), model.dbProviderModel())
            : projectMissingSpec(model.providerName(), SecurityProviderType.DATABASE);
      case CUSTOM:
         return model.customProviderModel() != null
            ? projectCustomModel(model.providerName(), model.customProviderModel())
            : projectMissingSpec(model.providerName(), SecurityProviderType.CUSTOM);
      default:
         return projectFileProvider(model.providerName());
      }
   }

   /** {@code currentValue}/{@code proposedValue} for a DATABASE provider (bug 76655) -- field-by-
    * field, mirroring {@link #projectLdapModel}'s convention; password is a {@code pw:set}/
    * {@code pw:unset} presence token, never literal (section 9). */
   static String projectDatabaseModel(String name, DatabaseAuthenticationProviderModel m) {
      StringBuilder sb = new StringBuilder();
      append(sb, "name", name);
      append(sb, "type", "DATABASE");
      append(sb, "driver", m.driver());
      append(sb, "url", m.url());
      append(sb, "requiresLogin", String.valueOf(m.requiresLogin()));
      append(sb, "useCredential", String.valueOf(m.useCredential()));
      append(sb, "secretId", m.secretId());
      append(sb, "user", m.user());
      append(sb, "password", isBlank(m.password()) ? "pw:unset" : "pw:set");
      append(sb, "hashAlgorithm", m.hashAlgorithm());
      append(sb, "userQuery", m.userQuery());
      append(sb, "userListQuery", m.userListQuery());
      append(sb, "groupListQuery", m.groupListQuery());
      append(sb, "groupUsersQuery", m.groupUsersQuery());
      append(sb, "roleListQuery", m.roleListQuery());
      append(sb, "userRolesQuery", m.userRolesQuery());
      append(sb, "userRoleListQuery", m.userRoleListQuery());
      append(sb, "organizationListQuery", m.organizationListQuery());
      append(sb, "organizationNameQuery", m.organizationNameQuery());
      append(sb, "organizationMembersQuery", m.organizationMembersQuery());
      append(sb, "organizationRolesQuery", m.organizationRolesQuery());
      append(sb, "appendSalt", String.valueOf(m.appendSalt()));
      append(sb, "userEmailsQuery", m.userEmailsQuery());
      append(sb, "sysAdminRoles", m.sysAdminRoles());
      append(sb, "orgAdminRoles", m.orgAdminRoles());
      return sb.toString();
   }

   /** {@code currentValue}/{@code proposedValue} for a CUSTOM provider (bug 76655). */
   static String projectCustomModel(String name, CustomProviderModel m) {
      StringBuilder sb = new StringBuilder();
      append(sb, "name", name);
      append(sb, "type", "CUSTOM");
      append(sb, "className", m.className());
      append(sb, "jsonConfiguration", m.jsonConfiguration());
      return sb.toString();
   }

   /** Projects a model whose {@code providerType} discriminator names a type-specific spec
    * ({@code ldapProviderModel}/{@code dbProviderModel}/{@code customProviderModel}) that is
    * unexpectedly {@code null} -- should not happen once a provider actually exists, but defensive
    * rather than NPE-prone (section 2), and deliberately NOT the FILE catch-all: the type is still
    * reported accurately, just flagged as missing its configuration, so this never again reads as
    * "this is a FILE provider" (bug 76655). */
   private static String projectMissingSpec(String name, SecurityProviderType type) {
      StringBuilder sb = new StringBuilder();
      append(sb, "name", name);
      append(sb, "type", type.name());
      append(sb, "error", "missing " + type + " provider model");
      return sb.toString();
   }

   /** {@code currentValue}/{@code proposedValue}, authorization chain -- same two-caller split as
    * {@link #projectAuthenticationProvider}: delete's {@code currentValue} only ever sees FILE
    * (this area's own delete-target restriction excludes CUSTOM), but duplicate's {@code
    * proposedValue}/apply-time {@code afterProjection} keeps the source's own type, which can be
    * CUSTOM ({@link AuthorizationProviderModel} has no LDAP/DATABASE, only FILE/CUSTOM) -- fixed
    * alongside {@link #projectAuthenticationProvider} (bug 76655, same root cause: this used to
    * fall through to {@link #projectFileProvider} unconditionally, mislabeling a duplicated CUSTOM
    * authorization provider's type in its audit/plan record exactly like the authentication side
    * did for DATABASE/CUSTOM). */
   static String projectAuthorizationProvider(AuthorizationProviderModel model) {
      if(model == null) {
         return null;
      }

      if(model.providerType() == SecurityProviderType.CUSTOM) {
         return model.customProviderModel() != null
            ? projectCustomModel(model.providerName(), model.customProviderModel())
            : projectMissingSpec(model.providerName(), SecurityProviderType.CUSTOM);
      }

      return projectFileProvider(model.providerName());
   }

   private static void append(StringBuilder sb, String field, String value) {
      sb.append(field).append('=').append(value == null ? "" : value).append(';');
   }

   private static void appendSorted(StringBuilder sb, String field, List<String> values) {
      TreeSet<String> sorted = values == null ? new TreeSet<>() : new TreeSet<>(values);
      sb.append(field).append('=').append(String.join(",", sorted)).append(';');
   }

   private static boolean isBlank(String s) {
      return s == null || s.trim().isEmpty();
   }
}
