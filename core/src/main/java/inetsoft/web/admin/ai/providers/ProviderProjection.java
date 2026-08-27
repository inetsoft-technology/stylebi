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

   /** {@code currentValue} for a delete, either chain (section 5/6/8) -- {@code null} if the model
    * itself is {@code null} (never expected once existence has been confirmed via the chain list,
    * but defensive rather than NPE-prone, section 2). Only FILE/LDAP are ever passed here --
    * DATABASE/CUSTOM targets are refused before this is reached (section 1, this area's own
    * delete-target restriction, see 04-build-java.md). */
   static String projectAuthenticationProvider(AuthenticationProviderModel model) {
      if(model == null) {
         return null;
      }

      if(model.providerType() == SecurityProviderType.LDAP && model.ldapProviderModel() != null) {
         return projectLdapModel(model.providerName(), model.ldapProviderModel());
      }

      return projectFileProvider(model.providerName());
   }

   /** {@code currentValue} for a delete, authorization chain -- only FILE is ever passed here (this
    * area's own delete-target restriction excludes CUSTOM the same as DATABASE/CUSTOM on the
    * authentication side). */
   static String projectAuthorizationProvider(AuthorizationProviderModel model) {
      if(model == null) {
         return null;
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
