/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import inetsoft.util.Tool;
import org.w3c.dom.*;

import java.io.*;
import java.util.*;

/**
 * The number of times each queued rename transform task has been started, keyed by task id. It is
 * stored next to the rename queue so that a task which keeps killing the server in the middle of
 * its transform is dropped after {@link #MAX_ATTEMPTS} attempts instead of being replayed at every
 * cluster start.
 */
@JsonSerialize(using = RenameTransformAttempts.Serializer.class)
@JsonDeserialize(using = RenameTransformAttempts.Deserializer.class)
public class RenameTransformAttempts implements RenameTransformObject {
   /**
    * Gets the number of times a task has been started.
    *
    * @param taskId the task id.
    *
    * @return the number of attempts, 0 if none is recorded.
    */
   public int get(String taskId) {
      Integer count = attempts.get(taskId);
      return count == null ? 0 : count;
   }

   /**
    * Sets the number of times a task has been started.
    */
   public void set(String taskId, int count) {
      attempts.put(taskId, count);
   }

   /**
    * Removes the count of a task.
    */
   public void remove(String taskId) {
      attempts.remove(taskId);
   }

   /**
    * Removes the counts of the tasks that are no longer in the queue.
    */
   public void retainAll(Collection<String> taskIds) {
      attempts.keySet().retainAll(taskIds);
   }

   public boolean isEmpty() {
      return attempts.isEmpty();
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<renameTransformAttempts class=\"" + getClass().getName() + "\">");

      for(Map.Entry<String, Integer> e : attempts.entrySet()) {
         writer.print("<task id=\"" + Tool.escape(e.getKey()) + "\" attempts=\"" +
                         e.getValue() + "\"/>");
      }

      writer.print("</renameTransformAttempts>");
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      NodeList list = Tool.getChildNodesByTagName(elem, "task");

      for(int i = 0; i < list.getLength(); i++) {
         Element task = (Element) list.item(i);
         String id = Tool.getAttribute(task, "id");
         String count = Tool.getAttribute(task, "attempts");

         if(id != null && count != null) {
            attempts.put(id, Integer.parseInt(count));
         }
      }
   }

   private final Map<String, Integer> attempts = new HashMap<>();

   /**
    * The number of times a queued rename transform task is started before it is dropped.
    */
   public static final int MAX_ATTEMPTS = 3;

   static final class Serializer extends StdSerializer<RenameTransformAttempts> {
      public Serializer() {
         super(RenameTransformAttempts.class);
      }

      @Override
      public void serialize(RenameTransformAttempts value, JsonGenerator gen,
                            SerializerProvider provider) throws IOException
      {
         gen.writeStartObject();
         transcode(value, gen);
         gen.writeEndObject();
      }

      @Override
      public void serializeWithType(RenameTransformAttempts value, JsonGenerator gen,
                                    SerializerProvider serializers, TypeSerializer typeSer)
         throws IOException
      {
         WritableTypeId typeId = typeSer.typeId(value, JsonToken.START_OBJECT);
         typeSer.writeTypePrefix(gen, typeId);
         transcode(value, gen);
         typeSer.writeTypeSuffix(gen, typeId);
      }

      private void transcode(RenameTransformAttempts value, JsonGenerator gen) throws IOException {
         StringWriter xml = new StringWriter();
         PrintWriter writer = new PrintWriter(xml);
         value.writeXML(writer);
         writer.flush();

         try {
            Document document = Tool.parseXML(new StringReader(xml.toString()));
            gen.writeObjectField(
               "renameTransformAttempts", new JsonXmlTranscoder().transcodeToJson(document));
         }
         catch(Exception e) {
            throw new IOException("Failed to transcode XML", e);
         }
      }
   }

   static final class Deserializer extends StdDeserializer<RenameTransformAttempts> {
      public Deserializer() {
         super(RenameTransformAttempts.class);
      }

      @Override
      public RenameTransformAttempts deserialize(JsonParser p, DeserializationContext ctxt)
         throws IOException
      {
         RenameTransformAttempts value = new RenameTransformAttempts();
         ObjectNode root = p.getCodec().readTree(p);
         ObjectNode info = (ObjectNode) root.get("renameTransformAttempts");

         try {
            Document document =
               new JsonXmlTranscoder().transcodeToXml(info, "renameTransformAttempts");
            value.parseXML(document.getDocumentElement());
         }
         catch(Exception e) {
            throw new JsonMappingException(p, "Failed to transcode XML", e);
         }

         return value;
      }
   }
}
