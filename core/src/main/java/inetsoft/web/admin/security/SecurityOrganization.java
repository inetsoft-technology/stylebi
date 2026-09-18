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

import java.util.List;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

/**
 * {@code CreateScheduleTaskRequest} contains the properties of a security organization.
 */
@Validated
@Schema(description = "The properties of a security organization.")
public class SecurityOrganization {
   /**
    * Gets the name of the organization.
    *
    * @return the organization name.
    */
   @NotNull
   @Schema(description = "The name of the organization.", example = "organization0")
   public String getName() {
      return name;
   }

   /**
    * Sets the name of the organization.
    *
    * @param name the organization name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the id of the organization.
    *
    * @return the organization id.
    */
   @NotNull
   @Schema(description = "The ID  of the organization.", example = "organization0")
   public String getId() {
      return id;
   }

   /**
    * Sets the id of the organization.
    *
    * @param id the organization name.
    */
   public void setId(String id) {
      this.id = id;
   }

   /**
    * Gets the locale used by the organization.
    *
    * @return the locale used by the organization.
    */
   @Schema(description = "The locale used by the organization.", example = "")
   public String getLocale() {
      return locale;
   }

   /**
    * Sets the local used by the organization.
    *
    * @param locale locale theme used by the organization.
    */
   public void setLocale(String locale) {
      this.locale = locale;
   }

   /**
    * Gets the theme used by the organization.
    *
    * @return the theme used by the organization.
    */
   @Schema(description = "The theme used by the organization.", example = "")
   public String getTheme() {
      return theme;
   }

   /**
    * Sets the theme used by the organization.
    *
    * @param theme the theme used by the organization.
    */
   public void setTheme(String theme) {
      this.theme = theme;
   }

   /**
    * Gets the users and groups that will be members of this organization.
    *
    * @return the user members of this organization.
    */
   @Schema(description = "The list of users that will be members of the organization.",
      example = "[ \"user0\", \"user1\" ]")
   public List<String> getMemberUsers() {
      return memberUsers;
   }

   /**
    * Gets the users that will be members of this organization.
    *
    * @param memberUsers the user members of this organization.
    */
   public void setMemberUsers(List<String> memberUsers) {
      this.memberUsers = memberUsers;
   }

   /**
    * Gets the groups that will be members of this organization.
    *
    * @return the groups.
    */
   @Schema(description = "The list of groups that will be members of the organization.",
      example = "[ \"group2\", \"group3\" ]")
   public List<String> getMemberGroups() {
      return memberGroups;
   }

   /**
    * Gets the group that will be members of this organization.
    *
    * @param memberGroups the group members of this organization.
    */
   public void setMemberGroups(List<String> memberGroups) {
      this.memberGroups = memberGroups;
   }

   /**
    * Gets the roles to be assigned to the organization.
    *
    * @return the roles.
    */
   @Schema(description = "A list of roles to assign to this organization.",
      example = "[ \"Designer\", \"Advanced\" ]")
   public List<String> getRoles() {
      return roles;
   }

   /**
    * Sets the roles to be assigned to the organization.
    *
    * @param roles the roles.
    */
   public void setRoles(List<String> roles) {
      this.roles = roles;
   }

   /**
    * Gets the organization-scoped property overrides. These write into the same global
    * server-property store the Properties area manages, namespaced per-organization -- not inert
    * metadata.
    *
    * @return the properties.
    */
   @Schema(description = "Organization-scoped property overrides, written into the shared global " +
      "server-property namespace (the same store the Properties area manages), not inert " +
      "per-organization metadata.")
   public List<PropertyModel> getProperties() {
      return properties;
   }

   /**
    * Sets the organization-scoped property overrides.
    *
    * @param properties the properties.
    */
   public void setProperties(List<PropertyModel> properties) {
      this.properties = properties;
   }

   /**
    * Sets the identities with admin permission over the organization
    *
    * @return the identities.
    */
   @Schema(description = "A list of identities with admin permission over the organization.")
   public AdminIdentities getAdminIdentities() {
      return adminIdentities;
   }

   /**
    * Sets the identities with admin permission over the organization
    *
    * @param adminIdentities the identities.
    */
   public void setAdminIdentities(AdminIdentities adminIdentities) {
      this.adminIdentities = adminIdentities;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      SecurityOrganization that = (SecurityOrganization) o;

      return name.equals(that.name) &&
         (theme == null || theme.equals(that.theme)) && (locale == null || locale.equals(that.locale)) &&
         (memberUsers == null || memberUsers.equals(that.memberUsers)) &&
         (memberGroups == null || memberGroups.equals(that.memberGroups)) &&
         (roles == null || roles.equals(that.roles)) &&
         (adminIdentities == null || adminIdentities.equals(that.adminIdentities)) &&
         (properties == null || properties.equals(that.properties));
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, theme, locale, memberUsers,
                          memberGroups, roles, adminIdentities, properties);
   }

   @Override
   public String toString() {
      return "SecurityOrganizations{" +
         "name='" + name + '\'' +
         ", ID='" + id + '\'' +
         ", theme='" + theme + '\'' +
         ", locale='" + locale + '\'' +
         ", memberUsers=" + memberUsers +
         ", memberGroups=" + memberGroups +
         ", roles=" + roles +
         ", adminIdentities=" + adminIdentities +
         ", properties=" + properties +
         '}';
   }

   /**
    * Gets the default password to assign to users when cloning from an existing organization.
    * Never populated on read; always null in GET responses.
    *
    * @return the default password.
    */
   @Schema(description = "The default password assigned to cloned users when copying from an existing organization. " +
      "Required when copyFromOrgID is provided. Must be 8-72 characters and contain uppercase, lowercase, a digit, and a special character.",
      example = "P@ssw0rd!")
   public String getDefaultPassword() {
      return defaultPassword;
   }

   /**
    * Sets the default password to assign to users when cloning from an existing organization.
    *
    * @param defaultPassword the default password.
    */
   public void setDefaultPassword(String defaultPassword) {
      this.defaultPassword = defaultPassword;
   }

   private String name;
   private String id;
   private String locale;
   private String theme;
   private List<String> memberUsers;
   private List<String> memberGroups;
   private List<String> roles;
   private AdminIdentities adminIdentities;
   private String defaultPassword;
   private List<PropertyModel> properties;
}
