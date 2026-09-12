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
package inetsoft.web.embed;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.awt.*;
import java.io.IOException;

/**
 * Contains info about the assembly that is embedded as a standalone component
 */
@JsonSerialize(using = EmbedAssemblyInfo.Serializer.class)
@JsonDeserialize(using = EmbedAssemblyInfo.Deserializer.class)
public class EmbedAssemblyInfo {
   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   public Dimension getAssemblySize() {
      return assemblySize;
   }

   public void setAssemblySize(Dimension assemblySize) {
      this.assemblySize = assemblySize;
   }

   private String assemblyName;
   private Dimension assemblySize;

   public static final class Serializer extends StdSerializer<EmbedAssemblyInfo> {
      public Serializer() {
         super(EmbedAssemblyInfo.class);
      }

      @Override
      public void serialize(EmbedAssemblyInfo value, JsonGenerator gen, SerializerProvider provider)
         throws IOException
      {
         gen.writeStartObject();
         gen.writeStringField("assemblyName", value.assemblyName);

         if(value.assemblySize == null) {
            gen.writeNullField("assemblySize");
         }
         else {
            gen.writeObjectFieldStart("assemblySize");
            gen.writeNumberField("width", value.assemblySize.width);
            gen.writeNumberField("height", value.assemblySize.height);
            gen.writeEndObject();
         }

         gen.writeEndObject();
      }
   }

   public static final class Deserializer extends StdDeserializer<EmbedAssemblyInfo> {
      public Deserializer() {
         super(EmbedAssemblyInfo.class);
      }

      @Override
      public EmbedAssemblyInfo deserialize(JsonParser p, DeserializationContext ctxt)
         throws IOException, JacksonException
      {
         JsonNode node = p.getCodec().readTree(p);
         EmbedAssemblyInfo assemblyInfo = new EmbedAssemblyInfo();
         assemblyInfo.assemblyName = node.get("assemblyName").asText();
         JsonNode child = node.get("assemblySize");

         if(!child.isNull()) {
            int width = child.get("width").asInt();
            int height = child.get("height").asInt();
            assemblyInfo.assemblySize = new Dimension(width, height);
         }

         return assemblyInfo;
      }
   }
}
