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
import inetsoft.util.Tool;
import org.w3c.dom.*;

import java.io.*;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This is a queue to keep the rename transformation tasks. For multi rename transformation tasks
 * must keep FIFO, else will cause transformation failed.
 * <p>
 * for example: rename a logical model and save, then rename its attributes and save.
 * although rename logical will start transform before the attribute transform, but transform
 * attribute is more granular and time-consuming task, so we must keep the rename FIFO,
 * and use concurrent processing inside each rename transformation.
 * <p>
 * In order to share the queue between studio and web server,
 * so persistant the queue in DependencyDBIndexedStorage
 */
@JsonSerialize(using = RenameTransformQueue.Serializer.class)
@JsonDeserialize(using = RenameTransformQueue.Deserializer.class)
public class RenameTransformQueue extends LinkedBlockingQueue<RenameDependencyInfo>
   implements RenameTransformObject
{
   /**
    * Constructs a queue.
    */
   public RenameTransformQueue() {
      super();
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   public void writeXML(PrintWriter writer) {
      writer.print("<renameTransformQueue class=\"" + getClass().getName() + "\">");
      stream().filter(Objects::nonNull).forEach(info -> info.writeXML(writer));
      writer.print("</renameTransformQueue>");
   }

   /**
    * Method to parse an xml segment.
    */
   public void parseXML(Element elem) throws Exception {
      NodeList list = Tool.getChildNodesByTagName(elem, "renameDependencyInfo");

      for(int i = 0; i < list.getLength(); i++) {
         Element ielem = (Element) list.item(i);
         RenameDependencyInfo rinfo = new RenameDependencyInfo();
         rinfo.parseXML(ielem);
         add(rinfo);
      }
   }

   static final class Serializer extends StdSerializer<RenameTransformQueue> {
      public Serializer() {
         super(RenameTransformQueue.class);
      }

      @Override
      public void serialize(RenameTransformQueue value, JsonGenerator gen, SerializerProvider provider)
         throws IOException
      {
         gen.writeStartObject();
         transcode(value, gen);
         gen.writeEndObject();
      }

      @Override
      public void serializeWithType(RenameTransformQueue value, JsonGenerator gen,
                                    SerializerProvider serializers, TypeSerializer typeSer)
         throws IOException
      {
         WritableTypeId typeId = typeSer.typeId(value, JsonToken.START_OBJECT);
         typeSer.writeTypePrefix(gen, typeId);
         transcode(value, gen);
         typeSer.writeTypeSuffix(gen, typeId);
      }

      private void transcode(RenameTransformQueue value, JsonGenerator gen) throws IOException {
         StringWriter xml = new StringWriter();
         PrintWriter writer = new PrintWriter(xml);
         value.writeXML(writer);
         writer.flush();

         try {
            Document document = Tool.parseXML(new StringReader(xml.toString()));
            gen.writeObjectField(
               "renameTransformQueue", new JsonXmlTranscoder().transcodeToJson(document));
         }
         catch(Exception e) {
            throw new IOException("Failed to transcode XML", e);
         }
      }
   }

   static final class Deserializer extends StdDeserializer<RenameTransformQueue> {
      public Deserializer() {
         super(RenameTransformQueue.class);
      }

      @Override
      public RenameTransformQueue deserialize(JsonParser p, DeserializationContext ctxt)
         throws IOException
      {
         RenameTransformQueue value = new RenameTransformQueue();
         ObjectNode root = p.getCodec().readTree(p);
         ObjectNode info = (ObjectNode) root.get("renameTransformQueue");

         try {
            Document document =
               new JsonXmlTranscoder().transcodeToXml(info, "renameTransformQueue");
            value.parseXML(document.getDocumentElement());
         }
         catch(Exception e) {
            throw new JsonMappingException(p, "Failed to transcode XML", e);
         }

         return value;
      }
   }
}
