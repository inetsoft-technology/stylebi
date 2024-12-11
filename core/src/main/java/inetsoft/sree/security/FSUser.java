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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * This class defines the user.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
@JsonSerialize(using = FSUser.Serializer.class)
@JsonDeserialize(using = FSUser.Deserializer.class)
public class FSUser extends User implements XMLSerializable {
   /**
    * Constructor.
    */
   public FSUser() {
      super();
   }

   /**
    * Constructor.
    */
   public FSUser(IdentityID userIdentity) {
      super(userIdentity);
   }

   /**
    * Constructor.
    *
    * @param userIdentity user's name.
    * @param emails       user's emails.
    * @param groups       parent groups.
    * @param roles        roles assigned to the user.
    * @param locale       user's locale.
    */
   public FSUser(IdentityID userIdentity, String[] emails, String[] groups, IdentityID[] roles,
                 String locale, String password) {
      super(userIdentity, emails, groups, roles, locale, password, true);
   }

   /**
    * Constructor.
    */
   public FSUser(IdentityID userIdentity, String[] emails, String[] groups, IdentityID[] roles,
                 String locale, String password, boolean active) {
      super(userIdentity, emails, groups, roles, locale, password, active);
   }

   /**
    * Constructor.
    */
   public FSUser(IdentityID userIdentity, String[] emails, String[] groups, IdentityID[] roles,
                 String locale, String password, boolean active, String alias) {
      super(userIdentity, emails, groups, roles, locale, password, active, alias);
   }

   /**
    * Constructor.
    */
   public FSUser(IdentityID userIdentity, String[] emails, String[] groups, IdentityID[] roles, String locale,
                 String password, String passwordAlgorithm, String passwordSalt,
                 boolean appendPasswordSalt, boolean active, String alias)
   {
      super(userIdentity, emails, groups, roles, locale, password, passwordAlgorithm, passwordSalt,
            appendPasswordSalt, active, alias);
   }

   /**
    * Constructor.
    */
   public FSUser(IdentityID userIdentity, String[] emails, String[] groups, IdentityID[] roles, String locale,
                 String password, String passwordAlgorithm, String passwordSalt,
                 boolean appendPasswordSalt, boolean active, String alias, String googleSSOId)
   {
      super(userIdentity, emails, groups, roles, locale, password, passwordAlgorithm, passwordSalt,
            appendPasswordSalt, active, alias, googleSSOId);
   }

   /**
    * Set user's password.
    */
   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * Sets the algorithm used to hash the password.
    *
    * @param passwordAlgorithm the hash algorithm name.
    */
   public void setPasswordAlgorithm(String passwordAlgorithm) {
      this.passwordAlgorithm = passwordAlgorithm;
   }

   /**
    * Sets the salt that was added to the clear text password prior to applying the hash algorithm.
    *
    * @param passwordSalt the password salt.
    */
   public void setPasswordSalt(String passwordSalt) {
      this.passwordSalt = passwordSalt;
   }

   /**
    * Sets a flag that indicates if the salt was appended or prepended to the clear text password.
    *
    * @param appendPasswordSalt {@code true} if the salt is appended to the password; {@code false}
    *                           if the salt is prepended to the password.
    */
   public void setAppendPasswordSalt(boolean appendPasswordSalt) {
      this.appendPasswordSalt = appendPasswordSalt;
   }

   /**
    * Set user's active.
    */
   public void setActive(boolean active) {
      this.active = active;
   }

   /**
    * Set the emails of the user.
    * @param emails The emails of the user.
    */
   public void setEmails(String[] emails) {
      this.emails = emails;
   }

   /**
    * Set roles assigned to the user.
    * @param roles The roles of the user in.
    */
   public void setRoles(IdentityID[] roles) {
      this.roles = roles;
   }

   /**
    * Set parent groups.
    */
   public void setGroups(String[] groups) {
      this.groups = groups;
   }

   /**
    * Set the assigned organization ID.
    */
   public void setOrganization(String organization) {
      this.organizationID = organization;
   }

   /**
    * Set the locale of the user.
    * @param locale This user's locale.
    */
   public void setLocale(String locale) {
      this.locale = locale;
   }

   /**
    * Set the alias of the user.
    * @param alias This user's alias.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Write xml element representation to a print writer.
    * @param writer the specified print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<FSUser class=\"" + getClass().getName() + "\">");
      writer.print("<name><![CDATA[" + name + "]]></name>");
      writer.print("<active><![CDATA[" + active + "]]></active>");

      if(!Tool.isEmptyString(googleSSOId)) {
         writer.print("<googleSSOId><![CDATA[" + googleSSOId + "]]></googleSSOId>");
      }

      if(organizationID != null) {
         writer.print("<organization><![CDATA[" + organizationID + "]]></organization>");
      }

      if(locale != null) {
         writer.print("<locale><![CDATA[" + locale + "]]></locale>");
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

      if(alias != null) {
         writer.print("<alias><![CDATA[" + alias + "]]></alias>");
      }

      writer.print("<emails>");

      for(String email : emails) {
         writer.print("<email><![CDATA[" + email + "]]></email>");
      }

      writer.print("</emails>");
      writer.print("<roles>");

      for(IdentityID role : roles) {
         writer.print("<role><![CDATA[" + role.convertToKey() + "]]></role>");
      }

      writer.print("</roles>");
      writer.print("<groups>");

      for(String group : groups) {
         writer.print("<group><![CDATA[" + group + "]]></group>");
      }

      writer.print("</groups>");
      writer.println("</FSUser>");
   }

   /**
    * Parse xml element representation.
    * @param tag the specified xml element representation.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      name = Tool.getValue(Tool.getChildNodeByTagName(tag, "name"));
      Element node = Tool.getChildNodeByTagName(tag, "organization");

      if(node != null) {
         organizationID = Tool.getValue(node);
      }
      else {
         organizationID = Organization.getDefaultOrganizationID();
      }

      node = Tool.getChildNodeByTagName(tag, "locale");

      if(node != null) {
         locale = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(tag, "active");
      active = node == null || Boolean.parseBoolean(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(tag, "googleSSOId");

      if(node != null) {
         googleSSOId = Tool.getValue(node);
      }

      if((node = Tool.getChildNodeByTagName(tag, "password")) != null) {
         password = Tool.getValue(node);
         passwordAlgorithm = Tool.getAttribute(node, "algorithm");

         if(passwordAlgorithm == null) {
            passwordAlgorithm = "MD5"; // NOSONAR
         }

         if((node = Tool.getChildNodeByTagName(tag, "passwordSalt")) != null) {
            passwordSalt = Tool.getValue(node);
            appendPasswordSalt = "true".equals(Tool.getAttribute(node, "append"));
         }
         else {
            passwordSalt = null;
            appendPasswordSalt = false;
         }
      }

      if((node = Tool.getChildNodeByTagName(tag, "alias")) != null) {
         alias = Tool.getValue(node);
      }

      Element elem = Tool.getChildNodeByTagName(tag, "emails");
      NodeList list = Tool.getChildNodesByTagName(elem, "email");
      emails = parseList(list);

      elem = Tool.getChildNodeByTagName(tag, "roles");
      list = Tool.getChildNodesByTagName(elem, "role");
      roles = parseListIdentityIds(list);

      elem = Tool.getChildNodeByTagName(tag, "groups");
      list = Tool.getChildNodesByTagName(elem, "group");
      groups = parseList(list);
   }

   private String[] parseList(NodeList list) {
      String[] values = new String[list.getLength()];

      for(int i = 0; i < list.getLength(); i++) {
         values[i] = Tool.getValue(list.item(i));
      }

      return values;
   }

   private IdentityID[] parseListIdentityIds(NodeList list) {
      IdentityID[] values = new IdentityID[list.getLength()];

      for(int i = 0; i < list.getLength(); i++) {
         values[i] = IdentityID.getIdentityIDFromKey(Tool.getValue(list.item(i)));
      }

      return values;
   }

   static final class Serializer extends StdSerializer<FSUser> {
      public Serializer() {
         super(FSUser.class);
      }

      @Override
      public void serialize(FSUser value, JsonGenerator gen, SerializerProvider provider)
         throws IOException
      {
         gen.writeStartObject();
         writeContent(value, gen);
         gen.writeEndObject();
      }

      @Override
      public void serializeWithType(FSUser value, JsonGenerator gen, SerializerProvider serializers,
                                    TypeSerializer typeSer) throws IOException
      {
         WritableTypeId typeId = typeSer.typeId(value, JsonToken.START_OBJECT);
         typeSer.writeTypePrefix(gen, typeId);
         writeContent(value, gen);
         typeSer.writeTypeSuffix(gen, typeId);
      }

      private void writeContent(FSUser value, JsonGenerator gen) throws IOException {
         gen.writeStringField("className", value.getClass().getName());
         gen.writeStringField("name", value.name);
         gen.writeBooleanField("active", value.active);
         gen.writeStringField("locale", value.locale);
         gen.writeStringField("googleSSOId", value.googleSSOId);
         gen.writeStringField("organization", value.organizationID);

         if(value.password != null) {
            String algorithm = value.passwordAlgorithm == null ? "none" : value.passwordAlgorithm;
            gen.writeObjectFieldStart("password");
            gen.writeStringField("algorithm", algorithm);
            gen.writeStringField("password", value.password);

            if(value.passwordSalt != null) {
               gen.writeBooleanField("appendSalt", value.appendPasswordSalt);
               gen.writeStringField("salt", value.passwordSalt);
            }
            else {
               gen.writeBooleanField("appendSalt", false);
               gen.writeNullField("salt");
            }

            gen.writeEndObject();
         }
         else {
            gen.writeNullField("password");
         }

         gen.writeStringField("alias", value.alias);
         gen.writeArrayFieldStart("emails");

         for(String email : value.emails) {
            gen.writeString(email);
         }

         gen.writeEndArray();
         gen.writeArrayFieldStart("roles");

         for(IdentityID role : value.roles) {
            gen.writeString(role.convertToKey());
         }

         gen.writeEndArray();
         gen.writeArrayFieldStart("groups");

         for(String group : value.groups) {
            gen.writeString(group);
         }

         gen.writeEndArray();
      }
   }

   static final class Deserializer extends StdDeserializer<FSUser> {
      Deserializer() {
         super(FSUser.class);
      }

      @Override
      public FSUser deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
         ObjectNode root = p.getCodec().readTree(p);

         String className = root.get("className").asText(null);
         FSUser value;

         if(className == null) {
            value = new FSUser();
         }
         else {
            try {
               value = (FSUser) Class.forName(className).getConstructor().newInstance();
            }
            catch(Exception e) {
               throw new JsonMappingException(
                  p, "Failed to create instance of user class " + className, e);
            }
         }

         value.name = root.get("name").asText(null);
         value.active = root.get("active").asBoolean(false);
         value.locale = root.get("locale").asText(null);
         JsonNode googleSSOIdNode = root.get("googleSSOId");
         value.googleSSOId = googleSSOIdNode == null ? null : googleSSOIdNode.asText(null);
         JsonNode organization = root.get("organization");
         value.organizationID = organization == null ? null : organization.asText(null);
         JsonNode node = root.get("password");

         if(node.isNull()) {
            value.passwordAlgorithm = null;
            value.password = null;
            value.appendPasswordSalt = false;
            value.passwordSalt = null;
         }
         else {
            ObjectNode password = (ObjectNode) node;
            value.passwordAlgorithm = password.get("algorithm").asText("MD5");
            value.password = password.get("password").asText(null);
            value.appendPasswordSalt = password.get("appendSalt").asBoolean(false);
            value.passwordSalt = password.get("salt").asText(null);
         }

         value.alias = root.get("alias").asText(null);
         node = root.get("emails");

         if(node != null && node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            List<String> emails = new ArrayList<>();

            for(JsonNode email : array) {
               emails.add(email.asText(null));
            }

            value.emails = emails.toArray(new String[0]);
         }

         node = root.get("roles");

         if(node != null && node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            List<IdentityID> roles = new ArrayList<>();

            for(JsonNode role : array) {
               roles.add(IdentityID.getIdentityIDFromKey(role.asText(null)));
            }

            value.roles = roles.toArray(new IdentityID[0]);
         }

         node = root.get("groups");

         if(node != null && node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            List<String> groups = new ArrayList<>();

            for(JsonNode group : array) {
               groups.add(group.asText(null));
            }

            value.groups = groups.toArray(new String[0]);
         }

         return value;
      }
   }
}
