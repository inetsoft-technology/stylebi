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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.util.Identity;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * A skeletal implementation of an authentication provider.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public abstract class AbstractAuthenticationProvider
   implements AuthenticationProvider
{
   /**
    * Gets a list of all users in the system.
    *
    * @return a list of users.
    */
   @Override
   public IdentityID[] getUsers() {
      return new IdentityID[0];
   }

   /**
    * Get a user by name.
    *
    * @param userIdentity the unique identifier of the user.
    *
    * @return the User object that encapsulates the properties of the user.
    */
   @Override
   public User getUser(IdentityID userIdentity) {
      return null;
   }

   /**
    * Get a list of all emails for a user.
    *
    * @param userIdentity the unique identifier for the user.
    *
    * @return list of emails.
    *
    * @deprecated use {@link inetsoft.sree.security.User#getEmails()} instead.
    */
   @Deprecated
   @Override
   public String[] getEmails(IdentityID userIdentity) {
      final String[] result;
      User u = getUser(userIdentity);

      if(u != null ) {
         result = u.getEmails();
      }
      else {
         result = new String[0];
      }

      return result;
   }

   /**
    * Get a group by name.
    *
    * @param groupIdentity the name of the group.
    *
    * @return the named group or <code>null</code> if no such group exists.
    */
   @Override
   public Group getGroup(IdentityID groupIdentity) {
      return null;
   }

   /**
    * Get a list of all users in a group.
    *
    * @param groupIdentity the name of the group, if the group is null
    *                      returns users belongs no group.
    *
    * @return list of users
    */
   @Override
   public IdentityID[] getUsers(IdentityID groupIdentity) {
      return new IdentityID[0];
   }

   /**
    * Get a list of all users not in any group except INDIVIDUAL.
    *
    * @return list of users
    */
   @Override
   public IdentityID[] getIndividualUsers() {
      return new IdentityID[0];
   }

   /**
    * Get a list of all roles in the system.
    *
    * @return list of roles.
    */
   @Override
   public IdentityID[] getRoles() {
      return new IdentityID[0];
   }

   /**
    * Get a list of all roles bound to specific user.
    *
    * @param roleIdentity the unique identifier for the user.
    *
    * @return list of roles.
    */
   @Override
   public IdentityID[] getRoles(IdentityID roleIdentity) {
      return new IdentityID[0];
   }

   /**
    * Get a role object from the role ID.
    *
    * @param roleIdentity the roleIdentity of the role.
    *
    * @return the named role object of <code>null</code> if no such role exists.
    */
   @Override
   public Role getRole(IdentityID roleIdentity) {
      return new Role(roleIdentity);
   }

   /**
    * Check whether the role exist.
    *
    * @param roleIdentity role
    */
   protected boolean existRole(IdentityID roleIdentity) {
      if(roleIdentity == null) {
         return false;
      }

      IdentityID[] roles = getRoles();

      if(roles == null) {
         return false;
      }

      return Arrays.asList(roles).contains(roleIdentity);
   }

   /**
    * Get a list of all groups defined in the system. If groups are nested,
    * only the top level groups will be returned.
    *
    * @return list of groups.
    */
   @Override
   public IdentityID[] getGroups() {
      return new IdentityID[0];
   }

   /**
    * Find the concrete identity in this security provider.
    * @return the identity found in this security provider, <tt>null</tt>
    * otherwise.
    */
   @Override
   public final Identity findIdentity(Identity identity) {
      if(identity.getType() == Identity.USER) {
         return getUser(identity.getIdentityID());
      }
      else if(identity.getType() == Identity.GROUP) {
         return getGroup(identity.getIdentityID());
      }
      else if(identity.getType() == Identity.ROLE) {
         return getRole(identity.getIdentityID());
      }
      else {
         throw new RuntimeException("Unsupported identity found: " + identity);
      }
   }

   @Override
   public String getProviderName() {
      return providerName;
   }

   @Override
   public void setProviderName(String providerName) {
      this.providerName = providerName;
   }

   public boolean isUseCredential() {
      return useCredential;
   }

   public void setUseCredential(boolean useCredential) {
      this.useCredential = useCredential;
   }

   public String getSecretId() {
      return secretId;
   }

   public void setSecretId(String secretId) {
      this.secretId = secretId;
   }

   /**
    * Gets the configured cache interval.
    *
    * @return the cache interval in milliseconds or {@code -1} if the cache is disabled.
    */
   protected long getCacheInterval() {
      long interval = -1L;

      if("true".equals(SreeEnv.getProperty("security.cache"))) {
         try {
            interval = Long.parseLong(SreeEnv.getProperty("security.cache.interval"));
         }
         catch(Exception e) {
            LoggerFactory.getLogger(getClass()).warn("Invalid security cache interval", e);
         }
      }

      return interval;
   }

   protected void loadCredential() {
      loadCredential(getSecretId());
   }

   protected void loadCredential(String secretId) {
   }

   private String providerName;
   private boolean useCredential;
   private String secretId;
}
