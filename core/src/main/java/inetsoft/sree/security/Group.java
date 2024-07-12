/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
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
 * This class defines the group.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
public class Group extends AbstractIdentity {
   /**
    * Constructor.
    */
   public Group() {
      this(null);
   }

   /**
    * Constructor.
    */
   public Group(IdentityID groupIdentity) {
      this(groupIdentity, null, new String[0], new IdentityID[0]);
   }

   /**
    * Constructor.
    * @param groupIdentity group's identity object.
    * @param locale group's locale.
    * @param groups parent groups.
    * @param roles roles assigned to this group.
    */
   public Group(IdentityID groupIdentity, String locale, String[] groups, IdentityID[] roles) {
      super();
      this.name = groupIdentity == null ? null : groupIdentity.name;
      this.organization =  groupIdentity == null ? null : groupIdentity.organization;
      this.roles = roles;
      this.locale = locale;
      this.groups = groups;
   }

   /**
    * Get the name of the group.
    */
   @Override
   public String getName() {
      return this.name;
   }

   /**
    * Get organization ID assigned to the group.
    */
   @Override
   public String getOrganization() {
      return this.organization;
   }

   /**
    * Get roles assigned to the group.
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
    * Get the locale of the group.
    */
   public String getLocale() {
      return this.locale;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         IdentityID[] croles = new IdentityID[roles.length];
         System.arraycopy(roles, 0, croles, 0, roles.length);

         String[] cgroups = new String[groups.length];
         System.arraycopy(groups, 0, cgroups, 0, groups.length);

         Group group = new Group(new IdentityID(name, organization), locale, cgroups, croles);
         return group;
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
      return Identity.GROUP;
   }

   @Override
   public IdentityID getIdentityID() {
      return new IdentityID(name, organization);
   }

   /**
    * Create one user.
    */
   @Override
   public XPrincipal create() {
      XPrincipal principal = null;
      String addr = null;

      try {
         addr = Tool.getIP();
      }
      catch(Exception ex) {
         // ignore it
      }

      return SUtil.getPrincipal(this, addr, false);
   }

   /**
    * Get a string representation of this object.
    */
   @Override
   public String toString() {
      return "Group[" + name + "]";
   }

   protected String name;
   protected String organization;
   protected IdentityID[] roles = new IdentityID[0];
   protected String[] groups = new String[0];
   protected String locale;

   private static final Logger LOG =
      LoggerFactory.getLogger(Group.class);
}