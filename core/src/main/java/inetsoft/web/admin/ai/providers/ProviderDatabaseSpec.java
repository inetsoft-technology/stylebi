/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The field set for {@code chain: "authentication"}, {@code providerType: "DATABASE"} creates/
 * updates (bug 76716). Field names mirror
 * {@link inetsoft.web.admin.security.DatabaseAuthenticationProviderModel} one for one so
 * {@link ProviderChangesetApplyService} can build that model directly -- the same one-for-one
 * mirroring convention {@link ProviderLdapSpec} already uses for LDAP.
 *
 * <p>Carried on {@link ProviderChangeRequest#getDatabaseSpec()}, a field separate from
 * {@link ProviderChangeRequest#getSpec()} (which stays {@link ProviderLdapSpec}-typed) rather than
 * a generic/union spec type -- Jackson binds {@code spec} to a single concrete class, so a second,
 * type-specific field is the minimal change that lets both shapes deserialize strictly (an
 * unrecognized field in either DTO is dropped, not silently misrouted into the other one).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderDatabaseSpec {
   public String getDriver() { return driver; }
   public void setDriver(String v) { this.driver = v; }

   public String getUrl() { return url; }
   public void setUrl(String v) { this.url = v; }

   /** Default {@code true} -- mirrors {@link inetsoft.web.admin.security.DatabaseAuthenticationProviderModel#requiresLogin()}'s
    * own default. Orthogonal to {@link #getUseCredential()}: this controls whether a login is
    * required to query the database at all, not which of secretId/user+password supplies it. */
   public Boolean getRequiresLogin() { return requiresLogin; }
   public void setRequiresLogin(Boolean v) { this.requiresLogin = v; }

   /** Default {@code false}. {@code true} requires {@link #getSecretId()}, not
    * {@link #getUser()}/{@link #getPassword()} -- cross-validated, not silently ignored, the same
    * discipline {@link ProviderLdapSpec#getUseCredential()} uses. */
   public Boolean getUseCredential() { return useCredential; }
   public void setUseCredential(Boolean v) { this.useCredential = v; }

   /** Opaque vault pointer, not secret-classified itself -- echoed freely. */
   public String getSecretId() { return secretId; }
   public void setSecretId(String v) { this.secretId = v; }

   /** The DATABASE analog of {@link ProviderLdapSpec#getAdminID()} -- named {@code user}, not
    * {@code adminID}, matching {@code DatabaseAuthenticationProviderModel.user()} exactly. */
   public String getUser() { return user; }
   public void setUser(String v) { this.user = v; }

   /** Never echoed back in a plan/audit/hash-mismatch response -- only a presence token. */
   public String getPassword() { return password; }
   public void setPassword(String v) { this.password = v; }

   public String getHashAlgorithm() { return hashAlgorithm; }
   public void setHashAlgorithm(String v) { this.hashAlgorithm = v; }

   public String getUserQuery() { return userQuery; }
   public void setUserQuery(String v) { this.userQuery = v; }

   public String getUserListQuery() { return userListQuery; }
   public void setUserListQuery(String v) { this.userListQuery = v; }

   public String getGroupListQuery() { return groupListQuery; }
   public void setGroupListQuery(String v) { this.groupListQuery = v; }

   public String getGroupUsersQuery() { return groupUsersQuery; }
   public void setGroupUsersQuery(String v) { this.groupUsersQuery = v; }

   public String getRoleListQuery() { return roleListQuery; }
   public void setRoleListQuery(String v) { this.roleListQuery = v; }

   public String getUserRolesQuery() { return userRolesQuery; }
   public void setUserRolesQuery(String v) { this.userRolesQuery = v; }

   public String getUserRoleListQuery() { return userRoleListQuery; }
   public void setUserRoleListQuery(String v) { this.userRoleListQuery = v; }

   public String getOrganizationListQuery() { return organizationListQuery; }
   public void setOrganizationListQuery(String v) { this.organizationListQuery = v; }

   public String getOrganizationNameQuery() { return organizationNameQuery; }
   public void setOrganizationNameQuery(String v) { this.organizationNameQuery = v; }

   public String getOrganizationMembersQuery() { return organizationMembersQuery; }
   public void setOrganizationMembersQuery(String v) { this.organizationMembersQuery = v; }

   public String getOrganizationRolesQuery() { return organizationRolesQuery; }
   public void setOrganizationRolesQuery(String v) { this.organizationRolesQuery = v; }

   public String getUserEmailsQuery() { return userEmailsQuery; }
   public void setUserEmailsQuery(String v) { this.userEmailsQuery = v; }

   public Boolean getAppendSalt() { return appendSalt; }
   public void setAppendSalt(Boolean v) { this.appendSalt = v; }

   public List<String> getSysAdminRoles() { return sysAdminRoles; }
   public void setSysAdminRoles(List<String> v) { this.sysAdminRoles = v; }

   public List<String> getOrgAdminRoles() { return orgAdminRoles; }
   public void setOrgAdminRoles(List<String> v) { this.orgAdminRoles = v; }

   private String driver;
   private String url;
   private Boolean requiresLogin;
   private Boolean useCredential;
   private String secretId;
   private String user;
   private String password;
   private String hashAlgorithm;
   private String userQuery;
   private String userListQuery;
   private String groupListQuery;
   private String groupUsersQuery;
   private String roleListQuery;
   private String userRolesQuery;
   private String userRoleListQuery;
   private String organizationListQuery;
   private String organizationNameQuery;
   private String organizationMembersQuery;
   private String organizationRolesQuery;
   private String userEmailsQuery;
   private Boolean appendSalt;
   private List<String> sysAdminRoles;
   private List<String> orgAdminRoles;
}
