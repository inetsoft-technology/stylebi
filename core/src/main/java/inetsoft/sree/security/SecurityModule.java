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

import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRepository;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;

/**
 * This class authenticates end users with a principal and credential. The
 * principal is a <code>SRPrincipal</code>, which implements <code>java.
 * security.Principal</code>, and wraps the user id and a secure id in it. The
 * credential is an <code>Object</code>, which may be an instance of a
 * <code>DefaultTicket</code> or <code>Certificate</code>, or any other object.
 *
 * @author Helen Chen
 * @version 5.1, 9/20/2003
 */

public class SecurityModule {
   /**
    * Get <code>SecurityModule</code> instacne
    *
    * @param parent object invoking this class
    * @param engine repository server proxy.
    * @param handlercls handles all the gui
    * @return a <code>SecurityModule</code> object
    */
   public static SecurityModule getSecurityModule(Object parent,
                                                  RepletRepository engine,
                                                  String handlercls)
         throws Exception
   {
      return new SecurityModule(parent, engine, handlercls);
   }

   /**
    * Create a <code>SecurityModule</code> object
    *
    * @param parent object invoking this class
    * @param engine repository server proxy.
    * @param handlercls handles all the gui
    */
   public SecurityModule(Object parent, RepletRepository engine,
                         String handlercls) throws Exception {
      this.parent = parent;
      this.engine = engine;
      this.handler = (SecurityGui) Class.forName(handlercls).newInstance();
   }

   /**
    * Check if login is cancelled.
    */
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * Set the default user to show on the login dialog.
    */
   public void setDefaultUser(String user) {
      handler.setDefaultUser(user);
   }

   /**
    * Set the default password to show on the login dialog.
    */
   public void setDefaultPassword(String passwd) {
      handler.setDefaultPassword(passwd);
   }

   /**
    * Authenticate the end user
    *
    * @return true if authenticated successfully
    */
   public boolean login() {
      Object credential = handler.login(parent);

      if(credential == null) {
         cancelled = true;
         return false;
      }

      cancelled = false;

      IdentityID user = new IdentityID(ClientInfo.ANONYMOUS, OrganizationManager.getCurrentOrgName());
      String passwd = "";

      if(credential instanceof DefaultTicket) {
         user = ((DefaultTicket) credential).getName();
         passwd = ((DefaultTicket) credential).getPassword();
      }

      String addr = null;

      try {
         addr = Tool.getIP();
      }
      catch(Exception e) {
         LOG.error("Failed to get local IP address", e);
      }

      try {
         principal = AuthenticationService.getInstance().authenticate(
            new ClientInfo(user, addr), credential);
      }
      catch(Exception e) {
         LOG.error("An error occurred while trying to authenticate user: " + user, e);
      }

      return principal != null;
   }

   /**
    * Authenticate the end user
    *
    * @return true if vpm login successfully
    */
   public Principal vpmLogin() {
      Object credential = handler.vpmLogin(parent);

      if(credential == null) {
         cancelled = true;
         return null;
      }

      cancelled = false;

      if(credential instanceof DefaultTicket) {
         IdentityID user = ((DefaultTicket) credential).getName();
         IdentityID[] roles = ((DefaultTicket) credential).getRoles();
         String[] groups = ((DefaultTicket) credential).getGroups();
         String orgID = ((DefaultTicket) credential).getOrgID();
         principal = new SRPrincipal(user, roles, groups, orgID, 0L);
      }

      return principal;
   }

   /**
    * Change the user password
    *
    * @param principal represents an entity
    * @return true if the password change succeeded
    */
   public boolean changePassword(Principal principal) {
      try {
         String passwd = handler.changePassword(parent);

         if(passwd != null) {
            engine.changePassword(principal, passwd);
            return true;
         }
      }
      catch(Exception e) {
         LOG.error("Failed to change the password for user " + principal, e);
      }

      return false;
   }

   /**
    * This method is called if the authentication fails, it cleans up all
    * the state that was originally saved.
    */
   public void abort() {
      principal = null;
   }

   /**
    * Log the user out of the system
    *
    * @param principal represents an entity
    */
   public void logout(Principal principal) {
      try {
         if(handler.logout(parent)) {
            AuthenticationService.getInstance().logout(principal);
            this.principal = null;
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to log out user: " + principal, e);
      }
   }

   /**
    * Get the <code>SRPrincipal</code> instance bound to this client
    *
    * @return a <code>SRPrincipal</code> object
    */
   public Principal getPrincipal() {
      return principal;
   }

   /**
    * Set the <code>SRPrincipal</code> instance bound to this client
    *
    * @param principal represents an entity
    */
   public void setPrincipal(Principal principal) {
      this.principal = principal;
   }

   private Object parent;
   private RepletRepository engine;
   private SecurityGui handler;
   private Principal principal;
   private boolean cancelled = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(SecurityModule.class);
}