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
package inetsoft.sree.security;

import inetsoft.sree.internal.SUtil;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class defines the user.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
public class User extends AbstractIdentity {
   /**
    * Constructor.
    */
   public User() {
      this(null);
   }

   /**
    * Constructor.
    */
   public User(IdentityID userIdentity) {
      this(userIdentity, new String[0], new String[0], new IdentityID[0], "", "", true);
   }

   /**
    * Constructor.
    *
    * @param userIdentity user's name.
    * @param emails       user's emails.
    * @param groups       parent groups.
    * @param roles        roles assigned to the user.
    * @param locale       user's locale.
    */
   public User(IdentityID userIdentity, String[] emails, String[] groups, IdentityID[] roles, String locale,
               String password)
   {
      super();

      this.emails = emails;
      this.groups = groups;
      this.locale = locale;
      this.name = userIdentity == null ? null : userIdentity.name;
      this.organizationID = userIdentity == null ? null : userIdentity.orgID;
      this.roles = roles;
      this.password = password;
   }

   /**
    * Constructor.
    */
   public User(IdentityID userIdentity, String[] emails, String[] groups, IdentityID[] roles,
               String locale, String password, boolean active) {
      this(userIdentity, emails, groups, roles, locale, password);

      this.active = active;
   }

   /**
    * Constructor.
    */
   public User(IdentityID userIdentity, String[] emails, String[] groups, IdentityID[] roles,
               String locale, String password, boolean active, String alias) {
      this(userIdentity, emails, groups, roles, locale, password, active);
      this.alias = alias;
   }

   /**
    * Constructor.
    */
   public User(IdentityID userIdentity, String[] emails, String[] groups, IdentityID[] roles, String locale,
               String password, String passwordAlgorithm, String passwordSalt,
               boolean appendPasswordSalt, boolean active, String alias)
   {
      this(userIdentity, emails, groups, roles, locale, password, passwordAlgorithm,
           passwordSalt, appendPasswordSalt, active, alias, null);
   }

   /**
    * Constructor.
    */
   public User(IdentityID userIdentity, String[] emails, String[] groups, IdentityID[] roles, String locale,
               String password, String passwordAlgorithm, String passwordSalt,
               boolean appendPasswordSalt, boolean active, String alias, String googleSSOId)
   {
      this(userIdentity, emails, groups, roles, locale, password, active, alias);
      this.passwordAlgorithm = passwordAlgorithm;
      this.passwordSalt = passwordSalt;
      this.appendPasswordSalt = appendPasswordSalt;
      this.googleSSOId = googleSSOId;
   }

   /**
    * Get the name of the user.
    */
   @Override
   public String getName() {
      return this.name;
   }

   /**
    * Get the active of the user.
    */
   public boolean isActive() {
      return this.active;
   }

   /**
    * Get the emails of the user.
    */
   public String[] getEmails() {
      return this.emails;
   }

   /**
    * Get roles assigned to the user.
    */
   @Override
   public IdentityID[] getRoles() {
      return this.roles;
   }

   /**
    * Get parent groups.
    */
   @Override
   public String[] getGroups() {
      return this.groups;
   }

   /**
    * Get organization assigned to the user.
    */
   public String getOrganizationID() {
      return organizationID == null ? Organization.getDefaultOrganizationID() : organizationID;
   }

   /**
    * Get the locale of the user.
    */
   public String getLocale() {
      return this.locale;
   }

   /**
    * Get user's password.
    */
   public String getPassword() {
      return this.password;
   }

   /**
    * Get the id of the Google sso sign up  user.
    *
    * @return
    */
   public String getGoogleSSOId() {
      return googleSSOId;
   }

   public void setGoogleSSOId(String googleId) {
      this.googleSSOId = googleId;
   }

   /**
    * Gets the algorithm used to hash the password.
    *
    * @return the hash algorithm name.
    */
   public String getPasswordAlgorithm() {
      return passwordAlgorithm;
   }

   /**
    * Gets the salt that was added to the clear text password prior to applying the hash algorithm.
    *
    * @return the password salt.
    */
   public String getPasswordSalt() {
      return passwordSalt;
   }

   /**
    * Gets a flag that indicates if the salt was appended or prepended to the clear text password.
    *
    * @return {@code true} if the salt is appended to the password; {@code false} if the salt is
    *         prepended to the password.
    */
   public boolean isAppendPasswordSalt() {
      return appendPasswordSalt;
   }

   /**
    * Get user's alias.
    */
   public String getAlias() {
      return this.alias;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         String[] cemails = new String[emails.length];
         System.arraycopy(emails, 0, cemails, 0, emails.length);

         IdentityID[] croles = new IdentityID[roles.length];
         System.arraycopy(roles, 0, croles, 0, roles.length);

         String[] cgroups = new String[groups.length];
         System.arraycopy(groups, 0, cgroups, 0, groups.length);

         return new User(new IdentityID(name, getOrganizationID()), cemails, cgroups, croles, locale, password, passwordAlgorithm,
                         passwordSalt, appendPasswordSalt, active, alias);
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Get the type of the identity.
    */
   @Override
   public int getType() {
      return Identity.USER;
   }

   @Override
   public IdentityID getIdentityID() {
      return new IdentityID(name, organizationID);
   }

   /**
    * Create one user.
    */
   @Override
   public XPrincipal create() {
      String addr = null;

      try {
         addr = Tool.getIP();
      }
      catch(Exception ex) {
         // ignore it
      }

      return SUtil.getPrincipal(this, addr, true);
   }

   /**
    * Get a string representation of this object.
    */
   @Override
   public String toString() {
      return "User[" + name + "]";
   }

   protected String name;
   protected String organizationID;
   protected String[] emails;
   protected IdentityID[] roles;
   protected String[] groups;
   protected String locale;
   protected String password;
   protected String googleSSOId;
   protected String passwordAlgorithm;
   protected String passwordSalt;
   protected boolean appendPasswordSalt;
   protected boolean active;
   protected String alias = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(User.class);
}
