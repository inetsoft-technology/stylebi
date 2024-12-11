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

import inetsoft.sree.internal.HttpXMLSerializable;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import inetsoft.web.admin.security.IdentityModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class defines the identity info.
 *
 * @version 8.5, 6/29/2006
 * @author InetSoft Technology Corp
 */
public class IdentityInfo implements HttpXMLSerializable {
   /**
    * Construct.
    */
   public IdentityInfo() {
      emails = new ArrayList<>();
      members = new ArrayList<>();
      roles = new ArrayList<>();
   }

   public IdentityInfo(Identity identity) {
      this(identity, SecurityEngine.getSecurity().getSecurityProvider());
   }

   /**
    * Construct.
    */
   public IdentityInfo(Identity identity, AuthenticationProvider provider) {
      this();
      try {
         if(provider == null) {
            provider = SecurityEngine.getSecurity().getSecurityProvider();
         }

         identityID = identity.getIdentityID();
         type = identity.getType();

         if(type == Identity.USER) {
            User user = (User) identity;
            password = user.getPassword();
            passwordAlgorithm = user.getPasswordAlgorithm();
            passwordSalt = user.getPasswordSalt();
            appendPasswordSalt = user.isAppendPasswordSalt();
            active = user.isActive();
            locale = user.getLocale();
            emails = Arrays.asList(user.getEmails());
            alias = user.getAlias();
            String[] groups = user.getGroups();
            Arrays.sort(groups);

            members = new ArrayList<>();


            for(String group : groups) {
               members.add(IdentityModel.builder()
                              .type(Identity.GROUP)
                              .identityID(new IdentityID(group, user.getOrganizationID()))
                              .build());
            }

            IdentityID[] roles0 = user.getRoles();
            Arrays.sort(roles0);
            roles = new ArrayList<>();

            for(IdentityID id : roles0) {
               roles.add(IdentityModel.builder()
                            .type(Identity.ROLE)
                            .identityID(id)
                            .build());
            }
         }
         else if(type == Identity.GROUP) {
            Group group = (Group) identity;
            locale = group.getLocale();
            Identity[] identities = Arrays.stream(provider.getGroupMembers(identityID)).filter(id -> id.getOrganizationID().equals(identity.getOrganizationID())).toArray(Identity[]::new);

            Arrays.sort(identities, new IdentityComparator());
            members = new ArrayList<>();

            for(Identity member : identities) {
               members.add(IdentityModel.builder()
                              .type(member.getType() == Identity.USER ?
                                       Identity.USER : Identity.GROUP)
                              .identityID(member.getIdentityID())
                              .build());
            }

            IdentityID[] roles0 = group.getRoles();
            Arrays.sort(roles0);
            roles = new ArrayList<>();

            for(IdentityID id : roles0) {
               roles.add(IdentityModel.builder()
                            .type(Identity.ROLE)
                            .identityID(id)
                            .build());
            }
         }
         else if(type == Identity.ORGANIZATION) {
            Organization organization = (Organization) identity;
            locale = organization.getLocale();

            String[] identitiesNames = provider.getOrganizationMembers(identityID.orgID);
            AuthenticationProvider finalProvider = provider;
            List<IdentityModel> identities = Arrays.stream(identitiesNames)
                  .map(n ->
                          IdentityModel.builder()
                     .type((finalProvider.getUser(new IdentityID(n, identityID.orgID)) == null) ?
                              (finalProvider.getGroup(new IdentityID(n, identityID.orgID)) == null) ?
                        Identity.ROLE : Identity.GROUP : Identity.USER)
                     .identityID(new IdentityID(n, identityID.orgID))
                     .build())
                  .collect(Collectors.toList());

            members = new ArrayList<>();

            for(IdentityModel member : identities) {
               members.add(IdentityModel.builder()
                              .type(member.type())
                              .identityID(member.identityID())
                              .build());
            }

            IdentityID[] roles0 = organization.getRoles();
            Arrays.sort(roles0);
            roles = new ArrayList<>();

            for(IdentityID id : roles0) {
               roles.add(IdentityModel.builder()
                            .type(Identity.ROLE)
                            .identityID(id)
                            .build());
            }
         }
         else {
            Role role = (Role) identity;
            desc = role.getDescription();
            defaultRole = role.isDefaultRole();
            sysAdmin = (role instanceof FSRole) && ((FSRole) role).isSysAdmin();
            orgAdmin = (role instanceof FSRole) && ((FSRole) role).isOrgAdmin();

            Identity[] identities = provider.getRoleMembers(identityID);

            Arrays.sort(identities, new IdentityComparator());
            members = new ArrayList<>();

            for(Identity member : identities) {
               members.add(IdentityModel.builder()
                              .type(member.getType() == Identity.USER ?
                                       Identity.USER : member.getType() == Identity.GROUP ?
                                 Identity.GROUP : Identity.ORGANIZATION)
                              .identityID(member.getIdentityID())
                              .build());
            }

            IdentityID[] roles0 = role.getRoles();
            Arrays.sort(roles0);
            roles = new ArrayList<>();

            for(IdentityID id : roles0) {
               roles.add(IdentityModel.builder()
                            .type(Identity.ROLE)
                            .identityID(id)
                            .build());
            }
         }
      }
      catch(Exception exc) {
         LOG.error("Failed to create info object for identity: " + identity, exc);
      }
   }

   /**
    * Identity's comparator.
    */
   class IdentityComparator implements Comparator<Identity> {
      @Override
      public int compare(Identity o1, Identity o2) {
         if(getScore(o1) != getScore(o2)) {
            return getScore(o1) - getScore(o2);
         }
         else {
            return o1.getName().compareTo(o2.getName());
         }
      }

      private int getScore(Identity o1) {
         return o1 instanceof User ? 3 : o1 instanceof Group ? 1 : 2;
      }
   }

   /**
    * Get name.
    */
   public IdentityID getIdentityID() {
      return identityID;
   }

   /**
    * Get type.
    */
   public int getType() {
      return type;
   }

   /**
    * Get active.
    */
   public boolean isActive() {
      return active;
   }

   /**
    * Get alias.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Get roles assigned to the identity.
    */
   public IdentityID[] getRoles() {
      boolean isMultiTenant = SUtil.isMultiTenant();

      return roles.stream()
         .map( r -> r.identityID())
         .filter(r -> isMultiTenant ||
                      !SecurityEngine.getSecurity().getSecurityProvider().isOrgAdministratorRole(r))
         .toArray(IdentityID[]::new);

   }

   /**
    * Get members of the identity.
    */
   public List<IdentityModel> getMembers() {
      return this.members;
   }

   public void addMember(IdentityModel member) {
      members.add(member);
   }

   /**
    * Get identity of the info.
    */
   public Identity getIdentity() {
      switch(type) {
      case Identity.USER:
         FSUser user = new FSUser(identityID);
         user.setEmails(emails.toArray(new String[0]));
         user.setLocale(locale);
         user.setActive(active);
         user.setAlias(alias);

         if(password != null) {
            user.setPassword(password);
         }

         if(passwordAlgorithm != null) {
            user.setPasswordAlgorithm(passwordAlgorithm);
         }

         if(passwordSalt != null) {
            user.setPasswordSalt(passwordSalt);
         }

         user.setAppendPasswordSalt(appendPasswordSalt);
         user.setRoles(getRoles());
         String[] groups = new String[getMembers().size()];

         for(int i = 0; i < getMembers().size(); i++) {
            groups[i] = getMembers().get(i).identityID().name;
         }

         user.setGroups(groups);

         return user;
      case Identity.GROUP:
         FSGroup group = new FSGroup(identityID);
         group.setLocale(locale);
         group.setRoles(getRoles());

         return group;
      default:
         FSRole role = new FSRole(identityID, desc);
         role.setRoles(Tool.remove(getRoles(), identityID));
         role.setDefaultRole(this.defaultRole);
         role.setSysAdmin(this.sysAdmin);
         return role;
      }
   }

   public String getLocale() {
      return locale;
   }

   /**
    * Write xml element representation to a print writer.
    * @param writer the specified print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<identityInfo>");
      writer.print("<name><![CDATA[" + byteEncode(identityID.convertToKey()) + "]]></name>");
      writer.print("<active><![CDATA[" + active + "]]></active>");
      writer.print("<type>" + type + "</type>");
      writer.print("<editable>" + editable + "</editable>");

      if(alias != null) {
         writer.print("<alias><![CDATA[" + byteEncode(alias) +
                         "]]></alias>");
      }

      if(locale != null) {
         writer.print("<locale><![CDATA[" + byteEncode(locale) +
                         "]]></locale>");
      }

      if(password != null) {
         String algorithm = passwordAlgorithm == null ? "none" : passwordAlgorithm;
         writer.format("<password algorithm=\"%s\"><![CDATA[%s]]></password>", algorithm, password);

         if(passwordSalt != null) {
            writer.format(
               "<passwordSalt append=\"%s\">%s</passwordSalt>",
               appendPasswordSalt, passwordSalt);
         }
      }

      if(desc != null) {
         writer.print("<description><![CDATA[" + byteEncode(desc) +
                         "]]></description>");
      }

      writer.print("<defaultRole><![CDATA[" + byteEncode(String.valueOf(defaultRole)) +
                      "]]></defaultRole>");

      writer.print("<sysAdmin><![CDATA[" + byteEncode(String.valueOf(sysAdmin)) +
                      "]]></sysAdmin>");

      writer.print("<emails>");

      for(String email : emails) {
         writer.print("<email><![CDATA[" + byteEncode(email) + "]]></email>");
      }

      writer.print("</emails>");
      writer.print("<roles>");

      for(IdentityModel role : roles) {
         writer.print("<role>");
         writer.print("<type><![CDATA[" + role.type() + "]]></type>");
         writer.print("<name><![CDATA[" + byteEncode(role.identityID().convertToKey()) + "]]></name>");
         writer.print("</role>");
      }

      writer.print("</roles>");
      writer.print("<members>");

      for(IdentityModel member : members) {
         writer.print("<member>");
         writer.print("<type><![CDATA[" + member.type() + "]]></type>");
         writer.print("<name><![CDATA[" + byteEncode(member.identityID().convertToKey()) + "]]></name>");
         writer.print(
            "<parentNode><![CDATA[" + byteEncode(member.parentNode()) + "]]></parentNode>");
         writer.print("</member>");
      }

      writer.print("</members>");
      writer.print("</identityInfo>");
   }

   /**
    * Parse xml element representation.
    * @param elem the specified xml element representation.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "name");
      identityID = IdentityID.getIdentityIDFromKey(byteDecode(Tool.getValue(node)));

      node = Tool.getChildNodeByTagName(elem, "active");
      active = node == null || Boolean.parseBoolean(Tool.getValue(node));

      if((node = Tool.getChildNodeByTagName(elem, "type")) != null) {
         type = Integer.parseInt(Tool.getValue(node));
      }

      if((node = Tool.getChildNodeByTagName(elem, "alias")) != null) {
         alias = byteDecode(Tool.getValue(node));
      }

      if((node = Tool.getChildNodeByTagName(elem, "locale")) != null) {
         locale = byteDecode(Tool.getValue(node));
      }

      if((node = Tool.getChildNodeByTagName(elem, "password")) != null) {
         password = byteDecode(Tool.getValue(node));
         passwordAlgorithm = Tool.getAttribute(node, "algorithm");

         if((node = Tool.getChildNodeByTagName(elem, "passwordSalt")) != null) {
            passwordSalt = Tool.getValue(node);
            appendPasswordSalt = "true".equals(Tool.getAttribute(node, "append"));
         }
         else {
            passwordSalt = null;
            appendPasswordSalt = false;
         }
      }

      if((node = Tool.getChildNodeByTagName(elem, "description")) != null) {
         desc = byteDecode(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "defaultRole");
      defaultRole = Boolean.parseBoolean(byteDecode(Tool.getValue(node)));

      node = Tool.getChildNodeByTagName(elem, "sysAdmin");
      sysAdmin = Boolean.parseBoolean(byteDecode(Tool.getValue(node)));

      node = Tool.getChildNodeByTagName(elem, "emails");
      NodeList list = Tool.getChildNodesByTagName(node, "email");
      emails = new ArrayList<>();

      for(int i = 0; i < list.getLength(); i++) {
         emails.add(byteDecode(Tool.getValue(list.item(i))));
      }

      node = Tool.getChildNodeByTagName(elem, "roles");
      list = Tool.getChildNodesByTagName(node, "role");
      roles = new ArrayList<>();

      for(int i = 0; i < list.getLength(); i++) {
         Element roleNode = (Element) list.item(i);
         roles.add(IdentityModel.builder()
                      .type(Integer.parseInt(Tool.getValue(
                         Tool.getChildNodeByTagName(roleNode, "type"))))
                      .identityID(IdentityID.getIdentityIDFromKey(byteDecode(Tool.getValue(
                         Tool.getChildNodeByTagName(roleNode, "name")))))
                      .build());
      }

      node = Tool.getChildNodeByTagName(elem, "members");
      list = Tool.getChildNodesByTagName(node, "member");
      members = new ArrayList<>();

      for(int i = 0; i < list.getLength(); i++) {
         Element memberNode = (Element) list.item(i);
         members.add(IdentityModel.builder()
                        .type(Integer.parseInt(Tool.getValue(
                           Tool.getChildNodeByTagName(memberNode, "type"))))
                        .identityID(IdentityID.getIdentityIDFromKey(byteDecode(Tool.getValue(
                           Tool.getChildNodeByTagName(memberNode, "name")))))
                        .parentNode(byteDecode(Tool.getValue(
                           Tool.getChildNodeByTagName(memberNode, "parentNode"))))
                        .build());
      }
   }

   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source source string.
    * @return encoded string.
    */
   @Override
   public String byteEncode(String source) {
      return isEncode ? Tool.byteEncode(source) : source;
   }

   /**
    * Convert the encoded string to the original unencoded string.
    * @param encString a string encoded using the byteEncode method.
    * @return original string.
    */
   @Override
   public String byteDecode(String encString) {
      return isEncode ? Tool.byteDecode(encString) : encString;
   }

   /**
    * Check if this object should encoded when writing.
    * @return <code>true</code> if should encoded, <code>false</code> otherwise.
    */
   @Override
   public boolean isEncoding() {
      return isEncode;
   }

   /**
    * Set encoding flag.
    * @param encoding true to encode.
    */
   @Override
   public void setEncoding(boolean encoding) {
      isEncode = encoding;
   }

   /**
    * Set this identity is editable.
    */
   public void setEditable(boolean editable) {
      this.editable = editable;
   }

   /**
    * Check if this identity is editable or not.
    */
   public boolean isEditable() {
      return editable;
   }

   public boolean isDefaultRole() {
      return defaultRole;
   }

   public void setDefaultRole(boolean defaultRole) {
      this.defaultRole = defaultRole;
   }

   public boolean isSysAdmin() {
      return sysAdmin;
   }

   public void setSysAdmin(boolean sysAdmin) {
      this.sysAdmin = sysAdmin;
   }

   public boolean isOrgAdmin() {
      return orgAdmin;
   }

   public void setOrgAdmin(boolean orgAdmin) {
      this.orgAdmin = orgAdmin;
   }

   private IdentityID identityID;
   private int type;
   private String locale;
   private String password;
   private String passwordAlgorithm;
   private String passwordSalt;
   private boolean appendPasswordSalt;
   private String desc;
   private List<String> emails;
   private List<IdentityModel> members;
   private List<IdentityModel> roles;
   private List<IdentityModel> organizations;
   private boolean isEncode;
   private boolean active;
   private boolean defaultRole;
   private boolean sysAdmin;
   private boolean orgAdmin;
   private String alias = null;
   private boolean editable = true;

   private static final Logger LOG =
      LoggerFactory.getLogger(IdentityInfo.class);
}
