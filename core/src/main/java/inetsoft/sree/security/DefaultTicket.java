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

/**
 * Default implementation of a ticket. It stores user ID and password.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DefaultTicket implements java.io.Serializable {
    /**
    * Create a ticket from roles.
    * @param name user name.
    * @param roles an array of roles
    * @param groups an array of groups
    */
   public DefaultTicket(IdentityID name, IdentityID[] roles, String[] groups) {
      this.name =  name;
      this.roles = roles;
      this.groups = groups;
   }

   /**
    * Create a ticket from roles.
    * @param name user name.
    * @param roles an array of roles
    * @param groups an array of groups
    */
   public DefaultTicket(IdentityID name, IdentityID[] roles, String[] groups, String orgID) {
      this.name =  name;
      this.roles = roles;
      this.groups = groups;
      this.orgID = orgID;
   }

   /**
    * Create a ticket from an user id and a password.
    * @param name user name.
    * @param passwd password.
    */
   public DefaultTicket(IdentityID name, String passwd) {
      this.name = (name == null) ? new IdentityID("",null) : name;
      this.passwd = (passwd == null) ? "" : passwd;
   }

   /**
    * Create a ticket from a string representation. The string should be
    * the user name and password separated by a colon.
    */
   public static DefaultTicket parse(String str) {
      String uid = null, passwd = null;
      int idx = str == null ? -1 : str.indexOf(':');

      if(idx < 0) {
         return new DefaultTicket(new IdentityID("", null), null);
      }

      if(IdentityID.KEY_DELIMITER.equals(":")) {
         int idx2 = str.substring(idx).indexOf(IdentityID.KEY_DELIMITER);

         if(idx2 > 0) {
            String idName = str.substring(0,idx);
            String idOrg = str.substring(idx+1,idx2);
            String pw = str.substring(idx2+1);
            return new DefaultTicket(new IdentityID(idName, idOrg), pw);
         }
      }

      uid = str.substring(0, idx);
      str = str.substring(idx + 1);
      idx = str.lastIndexOf(':');
      if(idx > 0) {
         passwd = str.substring(0, idx);
      }
      else {
         passwd = str;
      }

      return new DefaultTicket(new IdentityID(uid, OrganizationManager.getCurrentOrgName()), passwd);
   }

   /**
    * Get user name.
    */
   public IdentityID getName() {
      return name;
   }

   /**
    * Get password.
    */
   public String getPassword() {
      return passwd;
   }

   /**
    * Get roles.
    */
   public IdentityID[] getRoles() {
      return roles;
   }

   /**
    * Get groups.
    */
   public String[] getGroups() {
      return groups;
   }

   /**
    * Get organization ID.
    */
   public String getOrgID() {
      return orgID;
   }

   @Override
   public int hashCode() {
      int code = name.hashCode() + passwd.hashCode() + roles.hashCode() +
                 groups.hashCode() + orgID.hashCode();

      if(userObj != null) {
         code += userObj.hashCode();
      }

      return code;
   }

   @Override
   public boolean equals(Object obj) {
      if(obj instanceof DefaultTicket) {
         DefaultTicket tik = (DefaultTicket) obj;

         return name.equals(tik.name) &&
            (passwd == null && tik.passwd == null ||
             passwd != null && tik.passwd != null &&
             passwd.equals(tik.passwd)) &&
            (roles == null && tik.roles == null ||
             roles != null && tik.roles != null &&
             roles.equals(tik.roles)) &&
            (groups == null && tik.groups == null ||
             groups != null && tik.groups != null &&
             groups.equals(tik.groups)) &&
            (orgID == null && tik.orgID == null ||
             orgID != null && tik.orgID != null &&
             orgID.equals(tik.orgID)) &&
            (userObj == null && tik.userObj == null || userObj != null &&
             tik.userObj != null && userObj.equals(tik.userObj));
      }

      return false;
   }

   @Override
   public String toString() {
      return name.toString();
   }

   private IdentityID name;
   private String passwd;
   private Object userObj;
   private IdentityID[] roles;
   private String[] groups;
   private String orgID;
}
