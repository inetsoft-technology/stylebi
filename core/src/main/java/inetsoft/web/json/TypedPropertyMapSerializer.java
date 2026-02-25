/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Map;

/**
 * Serializer for the RuntimeSheet prop map that embeds type information for each value.
 * This allows polymorphic values (e.g., Dimension, Point2D.Double) to be correctly
 * deserialized without enabling global default typing on the ObjectMapper.
 *
 * <p>Each entry is serialized as: {@code "key": {"@class": "fully.qualified.ClassName", "value": ...}}</p>
 *
 * <p><b>SECURITY NOTE:</b> The corresponding deserializer ({@link TypedPropertyMapDeserializer})
 * allows instantiation of arbitrary classes based on the embedded type information. This is
 * acceptable because:</p>
 * <ol>
 *    <li>The prop map stores internal application state only - it never contains user-supplied input.</li>
 *    <li>The serialized data is only exchanged between trusted Ignite cluster nodes.</li>
 * </ol>
 */
public class TypedPropertyMapSerializer extends JsonSerializer<TypedPropertyMapWrapper> {
   @Override
   public void serialize(TypedPropertyMapWrapper wrapper, JsonGenerator gen, SerializerProvider provider)
      throws IOException
   {
      Map<String, Object> map = wrapper.getValues();
      gen.writeStartObject();

      for(Map.Entry<String, Object> entry : map.entrySet()) {
         gen.writeFieldName(entry.getKey());
         Object value = entry.getValue();

         if(value == null) {
            gen.writeNull();
         }
         else {
            gen.writeStartObject();
            gen.writeStringField("@class", value.getClass().getName());
            gen.writeFieldName("value");
            provider.defaultSerializeValue(value, gen);
            gen.writeEndObject();
         }
      }

      gen.writeEndObject();
   }
}
