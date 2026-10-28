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
package inetsoft.web.admin.ai.identities;

import java.util.regex.Pattern;

/**
 * The characters Enterprise Manager's identity editors refuse, mirrored here so the admin-chat
 * plan never creates a name EM could not save. EM enforces them only in the browser
 * ({@code web/projects/shared/util/form-validators.ts}); the server has no check of its own.
 * Keep the sets below in step with that file.
 */
final class IdentityNameRules {
   /** {@code validUserName}: user name (a period is allowed, for email-style names). */
   static final Pattern USER_NAME = Pattern.compile("[~`!#%^*=\\[\\]\\\\;,/{}|\":<>?()]");

   /** {@code validGroupName}: group name and role name (parentheses and period allowed). */
   static final Pattern GROUP_ROLE_NAME = Pattern.compile("[~`!#%^*=\\[\\]\\\\;,/{}|\":<>?]");

   /**
    * {@code containsDashboardSpecialCharsForName}: user alias and organization name (the user-name
    * set plus the period).
    */
   static final Pattern ALIAS_OR_ORG_NAME =
      Pattern.compile("[~`!#%^*=\\[\\]\\\\;,./{}|\":<>?()]");

   /** {@code validOrgID}: the allow-list {@code ^[a-zA-Z0-9-]*$}. */
   static final Pattern ORG_ID = Pattern.compile("[^a-zA-Z0-9-]");

   private IdentityNameRules() {
   }

   /** Refuses {@code value} when it holds a character {@code forbidden} matches. */
   static void require(String field, String value, Pattern forbidden, String description) {
      if(value == null) {
         return;
      }

      StringBuilder found = new StringBuilder();
      forbidden.matcher(value).results().map(r -> r.group()).distinct()
         .forEach(c -> found.append(found.length() > 0 ? " " : "").append(c));

      if(found.length() > 0) {
         throw new IllegalArgumentException(
            field + ": must not contain " + description + " (found: " + found +
            ") -- Enterprise Manager refuses such a value");
      }
   }

   static void requireUserName(String field, String value) {
      require(field, value, USER_NAME, "any of ~ ` ! # % ^ * = [ ] \\ ; , / { } | \" : < > ? ( )");
   }

   static void requireGroupOrRoleName(String field, String value) {
      require(field, value, GROUP_ROLE_NAME,
              "any of ~ ` ! # % ^ * = [ ] \\ ; , / { } | \" : < > ?");
   }

   static void requireAliasOrOrgName(String field, String value) {
      require(field, value, ALIAS_OR_ORG_NAME,
              "any of ~ ` ! # % ^ * = [ ] \\ ; , . / { } | \" : < > ? ( )");
   }

   static void requireOrgId(String field, String value) {
      require(field, value, ORG_ID, "anything but letters, digits and \"-\"");
   }
}
