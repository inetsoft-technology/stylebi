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

import inetsoft.sree.security.*;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

/**
 * {@code CreateScheduleTaskRequest} contains the properties of a security user.
 */
@Validated
@Schema(description = "The properties of a security user.")
public class SecurityUser {
   /**
    * Gets the alias of the user.
    *
    * @return the user alias.
    */
   @Schema(description = "The alias of the user.")
   public String getAlias() {
      return alias;
   }

   /**
    * Sets the alias of the user.
    *
    * @param alias the user alias.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Gets the locale of the user.
    *
    * @return the user locale.
    */
   @Schema(description = "The locale of the user.")
   public String getLocale() {
      return locale;
   }

   /**
    * Sets the locale of the user.
    *
    * @param locale the user locale.
    */
   public void setLocale(String locale) {
      this.locale = locale;
   }

   /**
    * Gets the theme used by the user.
    *
    * @return the theme used by the user.
    */
   @Schema(description = "The theme used by the user.")
   public String getTheme() {
      return theme;
   }

   /**
    * Sets the theme used by the user.
    *
    * @param theme the theme used by the user.
    */
   public void setTheme(String theme) {
      this.theme = theme;
   }

   /**
    * Gets if the user is active
    *
    * @return true if the user is active
    */
   @Schema(description = "Flag indicating if the user is active.", example = "true")
   public boolean isActive() {
      return active;
   }

   /**
    * Sets if the user is active
    *
    * @param active true if the user is active
    */
   public void setActive(boolean active) {
      this.active = active;
   }

   /**
    * Gets the email addresses of the user.
    *
    * @return the email addresses.
    */
   @Schema(description = "The list of the user's email addresses.",
      example = "[ \"sales@example.com\", \"marketing@example.com\" ]")
   public List<String> getEmails() {
      return emails;
   }

   /**
    * Sets the email addresses of the user.
    *
    * @param emails the email addresses.
    */
   public void setEmails(List<String> emails) {
      this.emails = emails;
   }

   /**
    * Gets the groups the user will be a member of.
    *
    * @return the groups.
    */
   @Schema(description = "The list of groups that the user will be a member of.",
      example = "[ \"group0\", \"group1\" ]")
   public List<String> getGroups() {
      return groups;
   }

   /**
    * Sets the groups the user will be a member of.
    *
    * @param groups the groups.
    */
   public void setGroups(List<String> groups) {
      this.groups = groups;
   }

   /**
    * Gets the roles to be assigned to the user.
    * Does not need to include default user roles, as these will always be added.
    *
    * @return the roles.
    */
   @ArraySchema(arraySchema = @Schema(description = "A list of roles to assign to the new user. " +
      "Does not need to include default user roles, as these will always be added.",
      example = "[{\"name\":\"role0\",\"orgID\":\"organization0\"}]"))
   public List<IdentityID> getRoles() {
      return roles;
   }

   /**
    * Sets the roles to be assigned to the user.
    *
    * @param roles the roles.
    */
   public void setRoles(List<IdentityID> roles) {
      this.roles = roles;
   }

   /**
    * Sets the identities with admin permission over the user
    *
    * @return the identities.
    */
   @Schema(description = "A list of identities with admin permission over the user.")
   public AdminIdentities getAdminIdentities() {
      return adminIdentities;
   }

   /**
    * Sets the identities with admin permission over the user
    *
    * @param adminIdentities the identities.
    */
   public void setAdminIdentities(AdminIdentities adminIdentities) {
      this.adminIdentities = adminIdentities;
   }

   /**
    * Gets the password for the user.
    *
    * @return the password.
    */
   @Schema(description = "The password for the user. Must be 8-72 characters and contain at least " +
      "one uppercase letter, one lowercase letter, one digit, and one special character.")
   public String getPassword() {
      return password;
   }

   /**
    * Sets the password for the user.
    *
    * @param password the password.
    */
   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * get the identityID for the user
    * @return identityID of the user
    */
   @Schema(
      description = "The user's identity id.",
      implementation = UserIdentityID.class,
      example = "{\"name\":\"user0\",\"orgID\":\"organization0\"}"
   )
   public IdentityID getIdentityID() {
      return this.identityID;
   }

   /**
    * set the identityID for the user
    * @param id the id to set this user
    */
   public void setIdentityID(IdentityID id) {
      this.identityID = id;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      SecurityUser that = (SecurityUser) o;

      return identityID.name.equals(that.identityID.name) &&
         (alias == null || alias.equals(that.alias)) &&
         (locale == null || locale.equals(that.locale)) &&
         (theme == null || theme.equals(that.theme)) &&
         (emails == null || emails.equals(that.emails)) &&
         (groups == null || groups.equals(that.groups)) &&
         (roles == null || roles.equals(that.roles)) &&
         (adminIdentities == null || adminIdentities.equals(that.adminIdentities));
   }

   @Override
   public int hashCode() {
      return Objects.hash(identityID.name, alias, locale, theme, emails, groups, roles, adminIdentities);
   }

   @Override
   public String toString() {
      return "SecurityUser{" +
         "name='" + (identityID == null ? "null" : identityID.name) + '\'' +
         ", orgID='" + (identityID == null ? "null" : identityID.orgID) + '\'' +
         ", alias='" + alias + '\'' +
         ", locale='" + locale + '\'' +
         ", theme='" + theme + '\'' +
         ", active=" + theme +
         ", emails=" + emails +
         ", groups=" + groups +
         ", roles=" + roles +
         ", adminIdentities=" + adminIdentities +
         '}';
   }

   private String password;
   private IdentityID identityID;
   private String alias;
   private String locale;
   private String theme;
   private boolean active = true;
   private List<String> emails;
   private List<String> groups;
   private List<IdentityID> roles;
   private AdminIdentities adminIdentities;
}
