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
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Virtual authentication module.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public class VirtualAuthenticationProvider
   extends AbstractEditableAuthenticationProvider
{
   /**
    * Creates a new instance of VirtualAuthenticationProvider.
    */
   public VirtualAuthenticationProvider() {
      load();
      system = new FSUser(new IdentityID(XPrincipal.SYSTEM, null));
      system.setRoles(new IdentityID[] { new IdentityID("Administrator", Organization.getDefaultOrganizationName())});
      anonymous = new FSUser(new IdentityID(XPrincipal.ANONYMOUS, Organization.getDefaultOrganizationName()));
   }

   /**
    * Get a list of all roles in the system.
    *
    * @return list of roles.
    */
   @Override
   public IdentityID[] getRoles() {
      return new IdentityID[] {new IdentityID("Everyone", Organization.getDefaultOrganizationName()),
                           new IdentityID("Administrator", Organization.getDefaultOrganizationName())};
   }

   /**
    * Check the authentication of specific entity.
    *
    * @param userIdentity the unique identification of the user.
    * @param ticket       a wrapper for some secure message, such as the user ID
    *                     and password.
    *
    * @return <code>true</code> if the authentication succeeded.
    */
   @Override
   public boolean authenticate(IdentityID userIdentity, Object ticket) {
      if(ticket == null) {
         LOG.error("Ticket is null: cannot authenticate.");
         return false;
      }

      if(!(ticket instanceof DefaultTicket)) {
         ticket = DefaultTicket.parse(ticket.toString());
      }

      IdentityID userid = ((DefaultTicket) ticket).getName();
      String passwd = ((DefaultTicket) ticket).getPassword();

      if(userid == null || userid.name.length() == 0 || passwd == null ||
         passwd.length() == 0)
      {
         return false;
      }

      String algorithm =
         admin.getPasswordAlgorithm() == null ? "MD5" : admin.getPasswordAlgorithm();
      return Objects.equals(userIdentity.name, admin.getName()) &&
         Objects.equals(userid.name, admin.getName()) &&
         Tool.checkHashedPassword(
            admin.getPassword(), passwd, algorithm, admin.getPasswordSalt(),
            admin.isAppendPasswordSalt());
   }

   /**
    * Get a list of all users in the system.
    *
    * @return list of users.
    */
   @Override
   public IdentityID[] getUsers() {
      return new IdentityID[]{new IdentityID("admin", Organization.getDefaultOrganizationName()),
                              new IdentityID(XPrincipal.ANONYMOUS, Organization.getDefaultOrganizationName())};
   }

   /**
    * Get a list of all Organizations in the system.
    *
    * @return list of Organizations.
    */
   @Override
   public String[] getOrganizations() {
      return new String[]{Organization.getDefaultOrganizationName()};
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
      if("admin".equals(userIdentity.name)) {
         return admin;
      }

      if(XPrincipal.SYSTEM.equals(userIdentity.name)) {
         return system;
      }

      if(XPrincipal.ANONYMOUS.equals(userIdentity.name)) {
         return anonymous;
      }

      return null;
   }

   @Override
   public Organization getOrganization(String name) {
      if(Organization.getDefaultOrganizationName().equals(name)) {
         return Organization.getDefaultOrganization();
      }
      else {
         return null;
      }
   }

   @Override
   public String getOrgId(String name) {
      return getOrganization(name).getId();
   }

   @Override
   public String getOrgNameFromID(String id) {
      for(String org : getOrganizations()) {
         if(getOrganization(org).getId().equalsIgnoreCase(id)) {
            return org;
         }
      }
      return null;
   }

   @Override
   public boolean isVirtual() {
      return true;
   }

   /**
    * Tear down the security provider.
    */
   @Override
   public void tearDown() {
      dmgr.clear();
   }

   /**
    * Save the virtual_security.xml file.
    */
   private void save() {
      DataSpace space = DataSpace.getDataSpace();
      dmgr.removeChangeListener(space, null, fileName, changeListener);

      try(DataSpace.Transaction tx = space.beginTransaction();
          OutputStream out = tx.newStream(null, fileName))
      {
         PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
         writer.print("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
         writer.print("<virtualSecurityProvider>\n");
         admin.writeXML(writer);
         writer.print("</virtualSecurityProvider>\n");
         writer.flush();
         tx.commit();
      }
      catch(Throwable ex) {
         LOG.error("Failed to save virtual security file", ex);
      }
      finally {
         long ts = System.currentTimeMillis();
         dmgr.addChangeListener(space, null, fileName, changeListener);
      }
   }

   /**
    * Load the virtual_security.xml file.
    */
   private void load() {
      DataSpace space = DataSpace.getDataSpace();

      if(!space.exists(null, fileName)) {
         admin = new FSUser(new IdentityID("admin", Organization.getDefaultOrganizationName()));
         SUtil.setPassword(admin, "admin");
         admin.setRoles(new IdentityID[] { new IdentityID("Administrator", null) });
         save();

         return;
      }

      try(InputStream istream = space.getInputStream(null, fileName)) {
         dmgr.addChangeListener(space, null, fileName, changeListener);
         Document doc = Tool.parseXML(istream);
         Element root = doc.getDocumentElement();
         admin = new FSUser();
         admin.parseXML(Tool.getChildNodeByTagName(root, "FSUser"));
      }
      catch(Exception ex) {
         LOG.error("Failed to load virtual security file: " + fileName, ex);
      }
   }

   /**
    * Add a user to the system.
    *
    * @param user the user to add.
    */
   @Override
   public void addUser(User user) {
      if(user.getName().equals("admin")) {
         admin = (FSUser) user;
         save();
      }
   }

   private DataChangeListener changeListener = new DataChangeListener() {
      @Override
      public void dataChanged(DataChangeEvent e) {
         LOG.debug(e.toString());

         if((System.currentTimeMillis() - lastRefresh) > 7000) {
            lastRefresh = System.currentTimeMillis();
         }
      }
   };

   @Override
   public String getProviderName() {
      return getClass().getSimpleName();
   }

   @Override
   public void setProviderName(String providerName) {
   }

   private String fileName = "virtual_security.xml";
   private long lastRefresh = 0;
   private DataChangeListenerManager dmgr = new DataChangeListenerManager();
   private FSUser admin;
   FSUser system;
   private FSUser anonymous;

   private static final Logger LOG = LoggerFactory.getLogger(VirtualAuthenticationProvider.class);
}
