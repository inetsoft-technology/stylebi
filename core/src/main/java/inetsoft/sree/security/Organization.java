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
import inetsoft.uql.util.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.HashMap;

/**
 * This class defines the organization.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
public class Organization extends AbstractIdentity {
   /**
    * Constructor.
    */
   public Organization() {
      this((String) null);
   }

   /**
    * Constructor.
    */
   public Organization(String id) {
      this(id, "", true);
   }

   /**
    * Constructor.
    */
   public Organization(IdentityID id) {
      this(id.orgID, "", true);
      this.name = id.name;
   }

   /**
    * Constructor.
    * @param id organization's id.
    * @param locale organization's locale.
    */
   public Organization(String id, String locale, boolean active)
   {
      super();

      this.locale = locale;
      this.id = id;
      this.active = active;
   }

   /**
    * Constructor.
    * @param name organization's name.
    * @param locale organization's locale.
    */
   public Organization(String name, String id, String[] members, String locale, boolean active)
   {
      super();

      this.locale = locale;
      this.name = name;
      this.id = id;
      this.members = members;
      this.active = active;
   }


   /**
    * Get the name of the organization.
    */
   @Override
   public String getName() {
      return this.name;
   }

   /**
    * Get the identityID of the organization.
    */
   @Override
   public IdentityID getIdentityID() {
      return new IdentityID(this.name, this.id);
   }

   /**
    * get organization id
    * @return id
    */
   public String getId() {return this.id;}

   public String getOrganizationID() {return this.id;}

   /**
    * set organization name
    * @param name, the new name for the organization
    */
   public void setName(String name) {this.name = name;}

   /**
    * Get the active of the organization.
    */
   public boolean isActive() {
      return this.active;
   }

   /**
    * Get members assigned to this organization
    */
   public String[] getMembers() {return this.members;}

   /**
    * set members assigned to this organization
    */
   public void setMembers(String[] members) {this.members = members;}

   /**
    * Get the locale of the organization.
    */
   public String getLocale() {
      return this.locale;
   }

   public String getTheme() {
      return theme;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         String[] cmembers = new String[members.length];
         System.arraycopy(members, 0, cmembers, 0, members.length);
         Organization newOrg = new Organization(name, id, cmembers, locale, active);

         return newOrg;
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
      return Identity.ORGANIZATION;
   }


   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof Organization)) {
         return false;
      }

      Organization organization = (Organization) obj;

      return getName() != null && getName().equals(organization.getName()) &&
         getId() != null && getId().equals(organization.getId());
   }

   /**
    * Create one organization.
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

   public static String getDefaultOrganizationName() {return defaultOrganizationName;}
   public static String getDefaultOrganizationID() {return defaultOrganizationID;}

   public static String getSelfOrganizationName() {return selfOrganizationName;}
   public static String getSelfOrganizationID() {return selfOrganizationID;}

   public static Organization getDefaultOrganization() {
      Organization defaultOrg = new Organization(defaultOrganizationID);
      defaultOrg.setName(defaultOrganizationName);

      return defaultOrg;
   }

   public static String getRootOrgRoleName(Principal principal) {
      return new IdentityID("Organization Roles",
                            OrganizationManager.getInstance().getCurrentOrgID()).convertToKey();
   }

   public static String getRootRoleName(Principal principal) {
      return new IdentityID("Roles",
                            OrganizationManager.getInstance().getCurrentOrgID()).convertToKey();
   }

   /**
    * Get a string representation of this object.
    */
   @Override
   public String toString() {
      return "Organization[" + name + "]";
   }

   protected String name;
   protected String id;
   protected String[] members;
   protected String locale;
   protected String theme;
   protected boolean active;

   protected static String defaultOrganizationName = "Host Organization";
   protected static String defaultOrganizationID = "host-org";

   protected static String selfOrganizationName = "Self Organization";
   protected static String selfOrganizationID = "SELF";

   private static final Logger LOG =
      LoggerFactory.getLogger(Organization.class);
}
