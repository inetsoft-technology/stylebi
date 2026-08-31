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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The field set for {@code chain: "authentication"}, {@code providerType: "LDAP"} creates
 * (01-spec.md section 11) -- the only provider type in this cut with any configuration beyond a
 * name (FILE has none). Field names mirror {@link inetsoft.web.admin.security.LdapAuthenticationProviderModel}
 * one for one so {@link ProviderChangesetApplyService} can build that model directly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderLdapSpec {
   /** {@code "ACTIVE_DIRECTORY"} or {@code "GENERIC"}, required. */
   public String getLdapServer() { return ldapServer; }
   public void setLdapServer(String v) { this.ldapServer = v; }

   public String getProtocol() { return protocol; }
   public void setProtocol(String v) { this.protocol = v; }

   public String getHostName() { return hostName; }
   public void setHostName(String v) { this.hostName = v; }

   public Integer getHostPort() { return hostPort; }
   public void setHostPort(Integer v) { this.hostPort = v; }

   public String getRootDN() { return rootDN; }
   public void setRootDN(String v) { this.rootDN = v; }

   /** Default {@code false}. {@code true} requires {@link #getSecretId()}, not
    * {@link #getAdminID()}/{@link #getPassword()} -- cross-validated, not silently ignored
    * (01-spec.md section 11). */
   public Boolean getUseCredential() { return useCredential; }
   public void setUseCredential(Boolean v) { this.useCredential = v; }

   public String getAdminID() { return adminID; }
   public void setAdminID(String v) { this.adminID = v; }

   /** Never echoed back in a plan/audit/hash-mismatch response -- only a presence token
    * (01-spec.md section 9). */
   public String getPassword() { return password; }
   public void setPassword(String v) { this.password = v; }

   /** Opaque vault pointer, not secret-classified itself (01-spec.md section 9) -- echoed freely. */
   public String getSecretId() { return secretId; }
   public void setSecretId(String v) { this.secretId = v; }

   public String getUserFilter() { return userFilter; }
   public void setUserFilter(String v) { this.userFilter = v; }

   public String getUserBase() { return userBase; }
   public void setUserBase(String v) { this.userBase = v; }

   public String getUserAttr() { return userAttr; }
   public void setUserAttr(String v) { this.userAttr = v; }

   public String getMailAttr() { return mailAttr; }
   public void setMailAttr(String v) { this.mailAttr = v; }

   public String getGroupFilter() { return groupFilter; }
   public void setGroupFilter(String v) { this.groupFilter = v; }

   public String getGroupBase() { return groupBase; }
   public void setGroupBase(String v) { this.groupBase = v; }

   public String getGroupAttr() { return groupAttr; }
   public void setGroupAttr(String v) { this.groupAttr = v; }

   public String getRoleFilter() { return roleFilter; }
   public void setRoleFilter(String v) { this.roleFilter = v; }

   public String getRoleBase() { return roleBase; }
   public void setRoleBase(String v) { this.roleBase = v; }

   public String getRoleAttr() { return roleAttr; }
   public void setRoleAttr(String v) { this.roleAttr = v; }

   public String getUserRoleFilter() { return userRoleFilter; }
   public void setUserRoleFilter(String v) { this.userRoleFilter = v; }

   public String getRoleRoleFilter() { return roleRoleFilter; }
   public void setRoleRoleFilter(String v) { this.roleRoleFilter = v; }

   public String getGroupRoleFilter() { return groupRoleFilter; }
   public void setGroupRoleFilter(String v) { this.groupRoleFilter = v; }

   public Boolean getStartTls() { return startTls; }
   public void setStartTls(Boolean v) { this.startTls = v; }

   public Boolean getSearchTree() { return searchTree; }
   public void setSearchTree(Boolean v) { this.searchTree = v; }

   public List<String> getSysAdminRoles() { return sysAdminRoles; }
   public void setSysAdminRoles(List<String> v) { this.sysAdminRoles = v; }

   private String ldapServer;
   private String protocol;
   private String hostName;
   private Integer hostPort;
   private String rootDN;
   private Boolean useCredential;
   private String adminID;
   private String password;
   private String secretId;
   private String userFilter;
   private String userBase;
   private String userAttr;
   private String mailAttr;
   private String groupFilter;
   private String groupBase;
   private String groupAttr;
   private String roleFilter;
   private String roleBase;
   private String roleAttr;
   private String userRoleFilter;
   private String roleRoleFilter;
   private String groupRoleFilter;
   private Boolean startTls;
   private Boolean searchTree;
   private List<String> sysAdminRoles;
}
