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
 * This class defines the FSRole.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
@JsonSerialize(using = FSRole.Serializer.class)
@JsonDeserialize(using = FSRole.Deserializer.class)
public class FSRole extends Role implements XMLSerializable {
   /**
    * Constructor.
    */
   public FSRole() {
      super();
   }

   /**
    * Constructor.
    * @param roleIdentity the specified role's name/organization identity.
    */
   public FSRole(IdentityID roleIdentity) {
      super(roleIdentity);
   }

   /**
    * Constructor.
    *
    * @param roleIdentity the specified role's name/organization identity.
    * @param roles        the parent roles.
    */
   public FSRole(IdentityID roleIdentity, IdentityID[] roles) {
      super(roleIdentity, roles);
   }

   /**
    * Constructor.
    *
    * @param roleIdentity the specified role's name/organization identity.
    * @param roles the specified role's roles.
    * @param description the specified role's description.
    */
   public FSRole(IdentityID roleIdentity, IdentityID[] roles, String description) {
      super(roleIdentity, roles);
      this.desc = description;
   }

   /**
    * Constructor.
    *
    * @param roleIdentity the specified role's name/organization identity.
    * @param desc         the specified description.
    */
   public FSRole(IdentityID roleIdentity, String desc) {
      super(roleIdentity, desc);
   }

   /**
    * Set the assigned organization name.
    */
   public void setOrganization(String organization) {
      this.organization = organization;
   }

   /**
    * Set the roles of the role.
    * @param roles the roles of the role.
    */
   public void setRoles(IdentityID[] roles) {
      this.roles = roles;
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

   public void setDesc(String desc) {this.desc = desc;}

   /**
    * Write xml element representation to a print writer.
    * @param writer the specified print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<FSRole class=\"" + getClass().getName() + "\">");
      writer.print("<name><![CDATA[" + name + "]]></name>");

      if(desc != null) {
         writer.print("<description><![CDATA[" + desc + "]]></description>");
      }

      if(organization != null) {
         writer.print("<organization><![CDATA[" + organization + "]]></organization>");
      }

      writer.print("<defaultRole><![CDATA[" + defaultRole + "]]></defaultRole>");

      writer.print("<sysAdmin><![CDATA[" + sysAdmin + "]]></sysAdmin>");

      writer.print("<orgAdmin><![CDATA[" + orgAdmin + "]]></orgAdmin>");

      writer.print("<roles>");

      for(int i = 0; i < roles.length; i++) {
         writer.print("<role><![CDATA[" + roles[i].convertToKey() + "]]></role>");
      }

      writer.print("</roles>");
      writer.print("</FSRole>");
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

      elem = Tool.getChildNodeByTagName(tag, "description");
      desc = elem == null ? null : Tool.getValue(elem);

      elem = Tool.getChildNodeByTagName(tag, "defaultRole");
      defaultRole = Boolean.parseBoolean(Tool.getValue(elem));

      elem = Tool.getChildNodeByTagName(tag, "orgAdmin");
      orgAdmin = Boolean.parseBoolean(Tool.getValue(elem));

      elem = Tool.getChildNodeByTagName(tag, "roles");
      NodeList list = Tool.getChildNodesByTagName(elem, "role");
      roles = new IdentityID[list.getLength()];

      for(int i = 0; i < list.getLength(); i++) {
         roles[i] = IdentityID.getIdentityIDFromKey(Tool.getValue((Element) list.item(i)));
      }
   }

   protected boolean sysAdmin = false;
   protected boolean orgAdmin = false;

   static final class Serializer extends StdSerializer<FSRole> {
      public Serializer() {
         super(FSRole.class);
      }

      @Override
      public void serialize(FSRole value, JsonGenerator gen, SerializerProvider provider)
         throws IOException
      {
         gen.writeStartObject();
         writeContent(value, gen);
         gen.writeEndObject();
      }

      @Override
      public void serializeWithType(FSRole value, JsonGenerator gen, SerializerProvider serializers,
                                    TypeSerializer typeSer) throws IOException
      {
         WritableTypeId typeId = typeSer.typeId(value, JsonToken.START_OBJECT);
         typeSer.writeTypePrefix(gen, typeId);
         writeContent(value, gen);
         typeSer.writeTypeSuffix(gen, typeId);
      }

      private void writeContent(FSRole value, JsonGenerator gen) throws IOException {
         gen.writeStringField("className", value.getClass().getName());
         gen.writeStringField("name", value.name);
         gen.writeStringField("description", value.desc);
         gen.writeBooleanField("defaultRole", value.defaultRole);
         gen.writeBooleanField("sysAdmin", value.isSysAdmin());
         gen.writeBooleanField("orgAdmin", value.isOrgAdmin());
         gen.writeStringField("organization", value.organization);
         gen.writeArrayFieldStart("roles");

         for(IdentityID role : value.roles) {
            gen.writeString(role.convertToKey());
         }

         gen.writeEndArray();
      }
   }

   static final class Deserializer extends StdDeserializer<FSRole> {
      public Deserializer() {
         super(FSRole.class);
      }

      @Override
      public FSRole deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
         ObjectNode root = p.getCodec().readTree(p);

         String className = root.get("className").asText(null);
         FSRole value;

         if(className == null) {
            value = new FSRole();
         }
         else {
            try {
               value = (FSRole) Class.forName(className).getConstructor().newInstance();
            }
            catch(Exception e) {
               throw new JsonMappingException(
                  p, "Failed to create instance of role class " + className, e);
            }
         }

         value.name = root.get("name").asText(null);

         value.desc = root.get("description").asText(null);
         value.defaultRole = root.get("defaultRole").asBoolean(false);
         value.sysAdmin = root.get("sysAdmin").asBoolean(false);
         JsonNode orgAdminNode = root.get("orgAdmin");
         value.orgAdmin = orgAdminNode == null ?
            value.sysAdmin : orgAdminNode.asBoolean(false);
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

         return value;
      }
   }
}
