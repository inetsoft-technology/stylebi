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
package inetsoft.uql.asset.sync;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.storage.JsonXmlTranscoder;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import org.w3c.dom.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@JsonSerialize(using = DependenciesInfo.Serializer.class)
@JsonDeserialize(using = DependenciesInfo.Deserializer.class)
public class DependenciesInfo implements RenameTransformObject, Cloneable {
   public List<AssetObject> getDependencies() {
      return dependencies;
   }

   public void setDependencies(List<AssetObject> dependencies) {
      this.dependencies = dependencies;
   }

   public List<AssetObject> getEmbedDependencies() {
      return embedDependencies;
   }

   public void setEmbedDependencies(List<AssetObject> dependencies) {
      this.embedDependencies = dependencies;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<dependenciesInfo>");
      writeContent(writer);
      writer.println("</dependenciesInfo>");
   }

   private void writeContent(PrintWriter writer) {
      writer.println("<assetObjects>");

      if(dependencies != null && !dependencies.isEmpty()) {
         for(AssetObject assetObject : dependencies) {
            writer.println("<assetObject class=\"" + assetObject.getClass().getName() + "\">");
            assetObject.writeXML(writer);
            writer.println("</assetObject>");
         }
      }

      writer.println("</assetObjects>");
      writer.println("<embedAssetObjects>");

      if(embedDependencies != null && !embedDependencies.isEmpty()) {
         for(AssetObject assetObject : embedDependencies) {
            writer.println("<assetObject class=\"" + assetObject.getClass().getName() + "\">");
            assetObject.writeXML(writer);
            writer.println("</assetObject>");
         }
      }

      writer.println("</embedAssetObjects>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      parseContent(tag);
   }

   private void parseContent(Element tag) throws Exception {
      Element assetObjectsElem = Tool.getChildNodeByTagName(tag, "assetObjects");

      if(assetObjectsElem != null) {
         NodeList assetObjects = Tool.getChildNodesByTagName(assetObjectsElem, "assetObject");

         if(assetObjects != null && assetObjects.getLength() > 0) {
            dependencies = new ArrayList<>();

            for(int i = 0; i < assetObjects.getLength(); i++) {
               Element assetObj = (Element) assetObjects.item(i);
               String cls = Tool.getAttribute(assetObj, "class");

               try {
                  AssetObject assetObject = (AssetObject) Class.forName(cls).newInstance();
                  assetObject.parseXML(Tool.getFirstChildNode(assetObj));
                  dependencies.add(assetObject);
               }
               catch(ClassNotFoundException ex) {
                  // ignore
               }
            }
         }
      }

      Element embedAssetObjects = Tool.getChildNodeByTagName(tag, "embedAssetObjects");

      if(embedAssetObjects != null) {
         NodeList assetObjects = Tool.getChildNodesByTagName(embedAssetObjects, "assetObject");

         if(assetObjects != null && assetObjects.getLength() > 0) {
            embedDependencies = new ArrayList<>();

            for(int i = 0; i < assetObjects.getLength(); i++) {
               Element assetObj = (Element) assetObjects.item(i);
               String cls = Tool.getAttribute(assetObj, "class");
               AssetObject assetObject = (AssetObject) Class.forName(cls).newInstance();
               assetObject.parseXML(Tool.getFirstChildNode(assetObj));
               embedDependencies.add(assetObject);
            }
         }
      }
   }

   @Override
   public Object clone() throws CloneNotSupportedException {
      DependenciesInfo info = ((DependenciesInfo) super.clone());
      info.setDependencies((List<AssetObject>) Tool.clone(this.dependencies));
      info.setEmbedDependencies((List<AssetObject>) Tool.clone(this.embedDependencies));

      return info;
   }

   private List<AssetObject> dependencies;
   private List<AssetObject> embedDependencies;

   static final class Serializer extends StdSerializer<DependenciesInfo> {
      public Serializer() {
         super(DependenciesInfo.class);
      }

      @Override
      public void serialize(DependenciesInfo value, JsonGenerator gen, SerializerProvider provider)
         throws IOException
      {
         gen.writeStartObject();
         transcode(value, gen);
         gen.writeEndObject();
      }

      @Override
      public void serializeWithType(DependenciesInfo value, JsonGenerator gen,
                                    SerializerProvider serializers, TypeSerializer typeSer)
         throws IOException
      {
         WritableTypeId typeId = typeSer.typeId(value, JsonToken.START_OBJECT);
         typeSer.writeTypePrefix(gen, typeId);
         transcode(value, gen);
         typeSer.writeTypeSuffix(gen, typeId);
      }

      private void transcode(DependenciesInfo value, JsonGenerator gen) throws IOException {
         StringWriter xml = new StringWriter();
         PrintWriter writer = new PrintWriter(xml);
         value.writeXML(writer);
         writer.flush();

         try {
            Document document = Tool.parseXML(new StringReader(xml.toString()));
            gen.writeObjectField(
               "dependenciesInfo", new JsonXmlTranscoder().transcodeToJson(document));
         }
         catch(Exception e) {
            throw new IOException("Failed to transcode XML", e);
         }
      }
   }

   static final class Deserializer extends StdDeserializer<DependenciesInfo> {
      public Deserializer() {
         super(DependenciesInfo.class);
      }

      @Override
      public DependenciesInfo deserialize(JsonParser p, DeserializationContext ctxt)
         throws IOException
      {
         DependenciesInfo value = new DependenciesInfo();
         ObjectNode root = p.getCodec().readTree(p);
         ObjectNode info = (ObjectNode) root.get("dependenciesInfo");

         try {
            Document document =
               new JsonXmlTranscoder().transcodeToXml(info, "dependenciesInfo");
            value.parseXML(document.getDocumentElement());
         }
         catch(Exception e) {
            throw new JsonMappingException(p, "Failed to transcode XML", e);
         }

         return value;
      }
   }
}
