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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
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
import java.util.*;

/**
 * This class defines the organization.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
@JsonSerialize(using = FSOrganization.Serializer.class)
@JsonDeserialize(using = FSOrganization.Deserializer.class)
public class FSOrganization extends Organization implements XMLSerializable {
   /**
    * Constructor.
    */
   public FSOrganization() {
      super();
   }

   /**
    * Constructor.
    */
   public FSOrganization(String name) {
      super(name);
   }

   /**
    * Constructor.
    * @param name user's name.
    * @param locale user's locale.
    */
   public FSOrganization(String name, String id, String[] members, String locale) {
      super(name, id, members, locale, true);
   }

   /**
    * Constructor.
    */

   /**
    * Set user's active.
    */
   public void setActive(boolean active) {
      this.active = active;
   }

   /**
    * Set the locale of the user.
    * @param locale This user's locale.
    */
   public void setLocale(String locale) {
      this.locale = locale;
   }

   public void setTheme(String theme) {
      this.theme = theme;
   }


   /**
    * Write xml element representation to a print writer.
    * @param writer the specified print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<FSOrganization class=\"" + getClass().getName() + "\">");
      writer.print("<name><![CDATA[" + name + "]]></name>");
      writer.print("<id><![CDATA[" + id + "]]></id>");
      writer.print("<active><![CDATA[" + active + "]]></active>");

      if(locale != null) {
         writer.print("<locale><![CDATA[" + locale + "]]></locale>");
      }

      if(theme != null) {
         writer.print("<theme><![CDATA[" + theme + "]]></theme>");
      }

      writer.println("</FSOrganization>");
   }

   /**
    * Parse xml element representation.
    * @param tag the specified xml element representation.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      name = Tool.getValue(Tool.getChildNodeByTagName(tag, "name"));
      id = Tool.getValue(Tool.getChildNodeByTagName(tag, "id"));
      Element node = Tool.getChildNodeByTagName(tag, "locale");
      theme = Tool.getValue(Tool.getChildNodeByTagName(tag, "theme"));

      if(node != null) {
         locale = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(tag, "active");
      active = node == null || Boolean.parseBoolean(Tool.getValue(node));

      Element elem = Tool.getChildNodeByTagName(tag, "roles");
      NodeList list = Tool.getChildNodesByTagName(elem, "role");
   }

   private String[] parseList(NodeList list) {
      String[] values = new String[list.getLength()];

      for(int i = 0; i < list.getLength(); i++) {
         values[i] = Tool.getValue(list.item(i));
      }

      return values;
   }

   static final class Serializer extends StdSerializer<FSOrganization> {
      public Serializer() {
         super(FSOrganization.class);
      }

      @Override
      public void serialize(FSOrganization value, JsonGenerator gen, SerializerProvider provider)
         throws IOException
      {
         gen.writeStartObject();
         writeContent(value, gen);
         gen.writeEndObject();
      }

      @Override
      public void serializeWithType(FSOrganization value, JsonGenerator gen, SerializerProvider serializers,
                                    TypeSerializer typeSer) throws IOException
      {
         WritableTypeId typeId = typeSer.typeId(value, JsonToken.START_OBJECT);
         typeSer.writeTypePrefix(gen, typeId);
         writeContent(value, gen);
         typeSer.writeTypeSuffix(gen, typeId);
      }

      private void writeContent(FSOrganization value, JsonGenerator gen) throws IOException {
         gen.writeStringField("className", value.getClass().getName());
         gen.writeStringField("name", value.name);
         gen.writeStringField("id", value.id);
         gen.writeBooleanField("active", value.active);
         gen.writeStringField("locale", value.locale);
         gen.writeStringField("theme", value.theme);
         gen.writeArrayFieldStart("roles");

         gen.writeEndArray();
         gen.writeObjectFieldStart("properties");

         if(value.properties != null) {
            for(Map.Entry<String, String> entry : value.properties.entrySet()) {
               gen.writeStringField(entry.getKey(), entry.getValue());
            }
         }

         gen.writeEndObject();
      }
   }

   static final class Deserializer extends StdDeserializer<FSOrganization> {
      Deserializer() {
         super(FSOrganization.class);
      }

      @Override
      public FSOrganization deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
         ObjectNode root = p.getCodec().readTree(p);

         String className = root.get("className").asText(null);
         FSOrganization value;

         if(className == null) {
            value = new FSOrganization();
         }
         else {
            try {
               value = (FSOrganization) Class.forName(className).getConstructor().newInstance();
            }
            catch(Exception e) {
               throw new JsonMappingException(
                  p, "Failed to create instance of user class " + className, e);
            }
         }

         value.name = root.get("name").asText(null);
         value.id = root.get("id").asText(null);
         value.active = root.get("active").asBoolean(false);
         value.locale = root.get("locale").asText(null);
         value.theme = root.get("theme").asText(null);
         JsonNode node = root.get("properties");

         if(node != null && node.isObject()) {
            ObjectNode objectNode = (ObjectNode)node;
            Iterator<String> fieldsIterator = objectNode.fieldNames();

            while(fieldsIterator.hasNext()) {
               String propertyName = fieldsIterator.next();
               String propertyValue = objectNode.get(propertyName).asText();

               if(!Tool.isEmptyString(propertyValue) && !Tool.isEmptyString(propertyValue)) {
                  value.setProperty(propertyName, propertyValue);
               }
            }
         }

         return value;
      }
   }
}
