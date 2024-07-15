/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
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
 * This class defines the role.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
public class Role extends AbstractIdentity {
   /**
    * Constructor.
    */
   public Role() {
      this(null);
   }

   /**
    * Constructor.
    *
    * @param roleIdentity the specified role's name.
    */
   public Role(IdentityID roleIdentity) {
      this(roleIdentity, new IdentityID[0]);
   }

   /**
    * Constructor.
    *
    * @param roleIdentity the specified role's name.
    * @param desc         the specified description.
    */
   public Role(IdentityID roleIdentity, String desc) {
      this(roleIdentity);

      if(!"".equals(desc)) {
         this.desc = desc;
      }   }

   /**
    * Constructor.
    *
    * @param roleIdentity the specified role's name.
    * @param roles        the parent roles.
    */
   public Role(IdentityID roleIdentity, IdentityID[] roles) {
      super();

      if(roleIdentity != null) {
         this.name = roleIdentity.name;
         this.organization = roleIdentity.organization;
      }

      this.roles = roles;
   }

   /**
    * Get the name of the role.
    */
   @Override
   public String getName() {
      return this.name;
   }

   /**
    * Get the roles of the role inherit from.
    */
   @Override
   public IdentityID[] getRoles() {
      return this.roles;
   }

   /**
    * Get organization ID assigned to the user.
    */
   @Override
   public String getOrganization() {
      return this.organization;
   }

   /**
    * Get role description.
    */
   public String getDescription() {
      return desc;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         IdentityID[] croles = new IdentityID[roles.length];
         System.arraycopy(roles, 0, croles, 0, roles.length);

         Role role = new Role(new IdentityID(name, organization), croles);
         return role;
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
      return Identity.ROLE;
   }

   @Override
   public IdentityID getIdentityID() {
      return new IdentityID(name, organization);
   }

   /**
    * Get the default role boolean property
    * @return boolean
    */
   public boolean isDefaultRole() {
      return this.defaultRole;
   }

   /**
    * Set the default role boolean property
    * @param defaultRole
    */
   public void setDefaultRole(boolean defaultRole) {
      this.defaultRole = defaultRole;
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
      return "Role[" + name + "]";
   }

   protected String name;
   protected String organization;
   protected IdentityID[] roles;
   protected String desc;
   protected boolean defaultRole;

   private static final Logger LOG =
      LoggerFactory.getLogger(Role.class);
}
