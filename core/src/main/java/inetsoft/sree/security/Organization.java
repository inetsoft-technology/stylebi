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
      this(null);
   }

   /**
    * Constructor.
    */
   public Organization(String name) {
      this(name, "", true);
   }

   /**
    * Constructor.
    * @param name organization's name.
    * @param locale organization's locale.
    */
   public Organization(String name, String locale, boolean active)
   {
      super();

      this.locale = locale;
      this.name = name;
      this.active = active;

      properties = new HashMap<>();
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

      properties = new HashMap<>();
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
      return new IdentityID(this.name, this.name);
   }

   /**
    * get organization id
    * @return id
    */
   public String getId() {return this.id;}

   public String getOrganizationID() {return this.id;}

   /**
    * set organization id
    * @param id, the new id for the organization
    */
   public void setId(String id) {this.id = id;}

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

   public HashMap<String, String> getProperties() {
      return this.properties;
   }

   public void setProperties(HashMap<String, String> properties) {
      this.properties = properties;
   }

   /**
    * Get the locale of the organization.
    */
   public String getLocale() {
      return this.locale;
   }

   public String getTheme() {
      return theme;
   }

   public String getProperty(String name) {
      if(this.properties != null) {
         return this.properties.get(name);
      }
      return null;
   }

   public void setProperty(String name, String value) {
      this.properties.put(name, value);
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
         newOrg.properties = new HashMap<>(properties);

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

   public static String getTemplateOrganizationName() {return templateOrganizationName;}
   public static String getTemplateOrganizationID() {return templateOrganizationID;}

   public static Organization getDefaultOrganization() {
      Organization defaultOrg = new Organization(defaultOrganizationName);
      defaultOrg.setId(defaultOrganizationID);

      return defaultOrg;
   }

   public static String getRootOrgRoleName(Principal principal) {
      return  new IdentityID(Catalog.getCatalog(principal).getString("Organization Roles"), OrganizationManager.getCurrentOrgName()).convertToKey();
   }

   public static String getRootRoleName(Principal principal) {
      return  new IdentityID(Catalog.getCatalog(principal).getString("Roles"), OrganizationManager.getCurrentOrgName()).convertToKey();
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
   protected HashMap<String, String> properties; // null properties, needs to be filled properly

   protected static String defaultOrganizationName = "Host Organization";
   protected static String defaultOrganizationID = "host-org";

   protected static String selfOrganizationName = "Self Organization";
   protected static String selfOrganizationID = "SELF";

   protected static String templateOrganizationName = "Organization Template";
   protected static String templateOrganizationID = "template-org";

   private static final Logger LOG =
      LoggerFactory.getLogger(Organization.class);
}
