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
         this.organizationID = roleIdentity.orgID;
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
   public String getOrganizationID() {
      return this.organizationID;
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
      Role role = (Role) super.clone();
      role.name = name;
      role.organizationID = organizationID;
      role.desc = desc;
      role.defaultRole = defaultRole;

      if(roles != null) {
         role.roles = (IdentityID[]) Tool.clone(roles);
      }

      return role;
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
      return new IdentityID(name, organizationID);
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
   protected String organizationID;
   protected IdentityID[] roles;
   protected String desc;
   protected boolean defaultRole;

   private static final Logger LOG =
      LoggerFactory.getLogger(Role.class);
}
