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
package inetsoft.sree.portal;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * {@code CustomTheme} contains information about an installed custom theme.
 */
public final class CustomTheme implements XMLSerializable {
   /**
    * Gets the display name of the theme.
    *
    * @return the theme name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the display name of the theme.
    *
    * @param name the theme name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the identifier of the theme.
    *
    * @return the theme identifier.
    */
   public String getId() {
      return id;
   }

   /**
    * Sets the identifier of the theme.
    *
    * @param id the theme identifier.
    */
   public void setId(String id) {
      this.id = id;
   }

   /**
    * Sets the orgID of the organization the theme belongs to if not global scoped.
    *
    * @return the orgID if not a global theme
    */
   public String getOrgID() {
      return orgID;
   }

   /**
    * Sets the orgID of the organization the theme belongs to.
    *
    * @param orgID the orgID of the organization
    */
   public void setOrgID(String orgID) {
      this.orgID = orgID;
   }

   /**
    * Gets the path to the theme JAR file in the data space.
    *
    * @return the JAR file path.
    */
   public String getJarPath() {
      return jarPath;
   }

   /**
    * Sets the path to the theme JAR file in the data space.
    *
    * @param jarPath the JAR file path.
    */
   public void setJarPath(String jarPath) {
      this.jarPath = jarPath;
   }

   /**
    * Gets whether EM is in dark mode
    *
    * @return true if em is in dark mode else false.
    */
   public boolean isEMDark() {
      return emDark;
   }

   /**
    * Sets EM dark mode value
    *
    * @param emDark em dark mode value
    */
   public void setEMDark(boolean emDark) {
      this.emDark = emDark;
   }

   /**
    * Get portal's script editor style
    *
    * @return script editor style
    */
   public ScriptTheme getPortalScript() {
      return portalScript;
   }

   /**
    * Set portal's script editor style
    *
    * @param portalScript
    */
   public void setPortalScript(ScriptTheme portalScript) {
      this.portalScript = portalScript;
   }

   /**
    * Get em's script editor style
    *
    * @return script editor style
    */
   public ScriptTheme getEmScript() {
      return emScript;
   }

   /**
    * Set em's script editor style
    *
    * @param emScript
    */
   public void setEmScript(ScriptTheme emScript) {
      this.emScript = emScript;
   }

   /**
    * Gets the users that are assigned to this theme.
    *
    * @return the user list.
    */
   public List<String> getUsers() {
      if(users == null) {
         users = new ArrayList<>();
      }

      return users;
   }

   /**
    * Sets the users that are assigned to this theme.
    *
    * @param users the user list.
    */
   public void setUsers(List<String> users) {
      this.users = users;
   }

   /**
    * Gets the organizations that are assigned to this theme.
    *
    * @return the org list.
    */

   public List<String> getOrganizations() {
      if(organizations == null) {
         organizations = new ArrayList<>();
      }

      return organizations;
   }
   /**
    * Sets the organizations that are assigned to this theme.
    *
    * @param orgs the organization list.
    */
   public void setOrganizations(List<String> orgs) {
      this.organizations = orgs;
   }

   /**
    * Gets the groups that are assigned to this theme.
    *
    * @return the group list.
    */
   public List<String> getGroups() {
      if(groups == null) {
         groups = new ArrayList<>();
      }

      return groups;
   }

   /**
    * Sets the groups that are assigned to this theme.
    *
    * @param groups the group list.
    */
   public void setGroups(List<String> groups) {
      this.groups = groups;
   }

   /**
    * Gets the roles that are assigned to this theme.
    *
    * @return the role list.
    */
   public List<String> getRoles() {
      if(roles == null) {
         roles = new ArrayList<>();
      }

      return roles;
   }

   /**
    * Sets the roles that are assigned to this theme.
    *
    * @param roles the role list.
    */
   public void setRoles(List<String> roles) {
      this.roles = roles;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<customTheme>");

      if(name != null) {
         writer.format("<name><![CDATA[%s]]></name>%n", name);
      }

      if(id != null) {
         writer.format("<id><![CDATA[%s]]></id>%n", id);
      }

      if(orgID != null) {
         writer.format("<orgID><![CDATA[%s]]></orgID>%n", orgID);
      }

      if(jarPath != null) {
         writer.format("<jarPath><![CDATA[%s]]></jarPath>%n", jarPath);
      }

      writer.format("<emDark><![CDATA[%s]]></emDark>%n", emDark);

      if(portalScript != null) {
         writer.format("<portalScript><![CDATA[%s]]></portalScript>%n", portalScript.name());
      }

      if(emScript != null) {
         writer.format("<emScript><![CDATA[%s]]></emScript>%n", emScript.name());
      }

      writer.println("<identities>");

      if(users != null) {
         for(String identity : users) {
            writer.format(
               "<identity type=\"user\"><![CDATA[%s]]></identity>%n", Tool.byteEncode(identity));
         }
      }

      if(groups != null) {
         for(String identity : groups) {
            writer.format(
               "<identity type=\"group\"><![CDATA[%s]]></identity>%n", Tool.byteEncode(identity));
         }
      }

      if(roles != null) {
         for(String identity : roles) {
            writer.format(
               "<identity type=\"role\"><![CDATA[%s]]></identity>%n", Tool.byteEncode(identity));
         }
      }

      if(organizations != null) {
         for(String identity : organizations) {
            writer.format(
               "<identity type=\"organization\"><![CDATA[%s]]></identity>%n", Tool.byteEncode(identity));
         }
      }

      writer.println("</identities>");
      writer.println("</customTheme>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      name = Tool.getChildValueByTagName(tag, "name");
      id = Tool.getChildValueByTagName(tag, "id");
      orgID = Tool.getChildValueByTagName(tag, "orgID");
      jarPath = Tool.getChildValueByTagName(tag, "jarPath");
      emDark = "true".equals(Tool.getChildValueByTagName(tag, "emDark"));

      String script = Tool.getChildValueByTagName(tag, "portalScript");
      portalScript = script != null ? ScriptTheme.valueOf(script) : ScriptTheme.ECLIPSE;

      script = Tool.getChildValueByTagName(tag, "emScript");
      emScript = script != null ? ScriptTheme.valueOf(script) : ScriptTheme.ECLIPSE;

      users = new ArrayList<>();
      groups = new ArrayList<>();
      roles = new ArrayList<>();
      organizations = new ArrayList<>();
      Element element = Tool.getChildNodeByTagName(tag, "identities");

      if(element != null) {
         NodeList nodes = Tool.getChildNodesByTagName(element, "identity");

         for(int i = 0; i < nodes.getLength(); i++) {
            Element identity = (Element) nodes.item(i);
            String type = Tool.getAttribute(identity, "type");
            String name = Tool.getValue(identity);

            if(name != null && !name.isEmpty()) {
               if("group".equals(type)) {
                  groups.add(name);
               }
               else if("role".equals(type)) {
                  roles.add(name);
               }
               else if("organization".equals(type)) {
                  organizations.add(name);
               }
               else {
                  users.add(name);
               }
            }
         }
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      CustomTheme that = (CustomTheme) o;
      return Objects.equals(name, that.name) &&
         Objects.equals(id, that.id) &&
         Objects.equals(jarPath, that.jarPath) &&
         emDark == that.emDark &&
         portalScript == that.portalScript &&
         emScript == that.emScript &&
         Objects.equals(users, that.users) &&
         Objects.equals(groups, that.groups) &&
         Objects.equals(roles, that.roles);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, id, jarPath, emDark, portalScript, emScript,
                          users, groups, roles);
   }

   @Override
   public String toString() {
      return "CustomTheme{" +
         "name='" + name + '\'' +
         ", id='" + id + '\'' +
         ", orgID='" + orgID + '\'' +
         ", jarPath='" + jarPath + '\'' +
         ", emDark='" + emDark + '\'' +
         ", portalScript='" + portalScript + '\'' +
         ", emScript='" + emScript + '\'' +
         ", users=" + users +
         ", groups=" + groups +
         ", roles=" + roles +
         '}';
   }

   private String name;
   private String id;
   private String orgID;
   private String jarPath;
   private boolean emDark;
   private ScriptTheme portalScript;
   private ScriptTheme emScript;
   private List<String> users;
   private List<String> groups;
   private List<String> roles;
   private List<String> organizations;

   public enum ScriptTheme {
      ECLIPSE("codemirror-eclipse.css"), DARCULA("codemirror-darcula.css"),
      MATERIAL_PALENIGHT("codemirror-material-palenight.css");

      private String fileName;

      ScriptTheme(String fileName) {
         this.fileName = fileName;
      }

      public String getFileName() {
         return fileName;
      }
   }
}
