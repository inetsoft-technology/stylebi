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
 * This class defines the group.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
@JsonSerialize(using = FSGroup.Serializer.class)
@JsonDeserialize(using = FSGroup.Deserializer.class)
public class FSGroup extends Group implements XMLSerializable {
   /**
    * Constructor.
    */
   public FSGroup() {
      super();
   }

   /**
    * Constructor.
    */
   public FSGroup(IdentityID groupIdentity) {
      super(groupIdentity);
   }

   /**
    * Constructor.
    *
    * @param groupIdentity group's name.
    * @param locale        group's locale.
    * @param groups        parent groups.
    * @param roles         roles assigned to this group.
    */
   public FSGroup(IdentityID groupIdentity, String locale, String[] groups, IdentityID[] roles) {
      super(groupIdentity, locale, groups, roles);
   }

   /**
    * Set roles assigned to the group.
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
    * Set the locale of the group.
    */
   public void setLocale(String locale) {
      this.locale = locale;
   }

   /**
    * Set the assigned organization ID.
    */
   public void setOrganization(String organization) {
      this.organization = organization;
   }

   /**
    * Write xml element representation to a print writer.
    * @param writer the specified print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<FSGroup class=\"" + getClass().getName() + "\">");
      writer.print("<name><![CDATA[" + name + "]]></name>");

      if(organization != null) {
         writer.print("<organization><![CDATA[" + organization + "]]></organization>");
      }

      if(locale != null) {
         writer.print("<locale><![CDATA[" + locale + "]]></locale>");
      }

      writer.print("<roles>");

      for(int i = 0; i < roles.length; i++) {
         writer.print("<role><![CDATA[" + roles[i].convertToKey() + "]]></role>");
      }

      writer.print("</roles>");
      writer.print("<groups>");

      for(int i = 0; i < groups.length; i++) {
         writer.print("<group><![CDATA[" + groups[i] + "]]></group>");
      }

      writer.print("</groups>");
      writer.print("</FSGroup>");
   }

   /**
    * Parse xml element representation.
    * @param tag the specified xml element representation.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Element elem = Tool.getChildNodeByTagName(tag, "name");
      name = Tool.getValue(elem);

      elem = Tool.getChildNodeByTagName(tag, "organization");

      if(elem != null) {
         organization = Tool.getValue(elem);
      }

      elem = Tool.getChildNodeByTagName(tag, "locale");

      if(elem != null) {
         locale = Tool.getValue(elem);
      }

      elem = Tool.getChildNodeByTagName(tag, "roles");
      NodeList list = Tool.getChildNodesByTagName(elem, "role");
      roles = new IdentityID[list.getLength()];

      for(int i = 0; i < list.getLength(); i++) {
         roles[i] = IdentityID.getIdentityIDFromKey(Tool.getValue((Element) list.item(i)));
      }

      elem = Tool.getChildNodeByTagName(tag, "groups");
      list = Tool.getChildNodesByTagName(elem, "group");
      groups = new String[list.getLength()];

      for(int i = 0; i < list.getLength(); i++) {
         groups[i] = Tool.getValue((Element) list.item(i));
      }
   }

   static final class Serializer extends StdSerializer<FSGroup> {
      Serializer() {
         super(FSGroup.class);
      }

      @Override
      public void serialize(FSGroup value, JsonGenerator gen, SerializerProvider provider)
         throws IOException
      {
         gen.writeStartObject();
         writeContent(value, gen);
         gen.writeEndObject();
      }

      @Override
      public void serializeWithType(FSGroup value, JsonGenerator gen,
                                    SerializerProvider serializers, TypeSerializer typeSer)
         throws IOException
      {
         WritableTypeId typeId = typeSer.typeId(value, JsonToken.START_OBJECT);
         typeSer.writeTypePrefix(gen, typeId);
         writeContent(value, gen);
         typeSer.writeTypeSuffix(gen, typeId);
      }

      private void writeContent(FSGroup value, JsonGenerator gen) throws IOException {
         gen.writeStringField("className", value.getClass().getName());
         gen.writeStringField("name", value.name);
         gen.writeStringField("locale", value.name);
         gen.writeStringField("organization", value.organization);
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

   static final class Deserializer extends StdDeserializer<FSGroup> {
      Deserializer() {
         super(FSGroup.class);
      }

      @Override
      public FSGroup deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
         ObjectNode root = p.getCodec().readTree(p);

         String className = root.get("className").asText(null);
         FSGroup value;

         if(className == null) {
            value = new FSGroup();
         }
         else {
            try {
               value = (FSGroup) Class.forName(className).getConstructor().newInstance();
            }
            catch(Exception e) {
               throw new JsonMappingException(
                  p, "Failed to create instance of group class " + className, e);
            }
         }

         value.name = root.get("name").asText(null);

         JsonNode orgNode = root.get("organization");
         value.organization = orgNode == null ? null : orgNode.asText(null);
         JsonNode node = root.get("roles");

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
