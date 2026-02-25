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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Deserializer for the RuntimeSheet prop map that reads embedded type information for each value.
 * This allows polymorphic values (e.g., Dimension, Point2D.Double) to be correctly restored
 * without enabling global default typing on the ObjectMapper.
 *
 * <p>Expects each entry in format: {@code "key": {"@class": "fully.qualified.ClassName", "value": ...}}</p>
 *
 * <p><b>SECURITY NOTE:</b> This deserializer instantiates arbitrary classes based on the embedded
 * type information, which could be exploited for deserialization attacks (CVE-2017-7525 and
 * descendants) if used with untrusted data. This is acceptable here because:</p>
 * <ol>
 *    <li>The prop map stores internal application state only - it never contains user-supplied input.</li>
 *    <li>The serialized data is only exchanged between trusted Ignite cluster nodes
 *        over authenticated/encrypted channels.</li>
 * </ol>
 * <p><b>DO NOT</b> use this deserializer for untrusted external data.</p>
 */
public class TypedPropertyMapDeserializer extends JsonDeserializer<TypedPropertyMapWrapper> {
   @Override
   public TypedPropertyMapWrapper deserialize(JsonParser parser, DeserializationContext context)
      throws IOException
   {
      Map<String, Object> result = new HashMap<>();

      if(parser.currentToken() != JsonToken.START_OBJECT) {
         throw context.wrongTokenException(parser, TypedPropertyMapWrapper.class, JsonToken.START_OBJECT,
            "Expected START_OBJECT for typed property map");
      }

      while(parser.nextToken() != JsonToken.END_OBJECT) {
         String key = parser.currentName();
         parser.nextToken();

         if(parser.currentToken() == JsonToken.VALUE_NULL) {
            result.put(key, null);
         }
         else if(parser.currentToken() == JsonToken.START_OBJECT) {
            result.put(key, deserializeTypedValue(parser, context));
         }
         else {
            throw context.wrongTokenException(parser, Object.class, JsonToken.START_OBJECT,
               "Expected START_OBJECT or VALUE_NULL for typed property value");
         }
      }

      return new TypedPropertyMapWrapper(result);
   }

   private Object deserializeTypedValue(JsonParser parser, DeserializationContext context)
      throws IOException
   {
      String className = null;
      Object value = null;

      while(parser.nextToken() != JsonToken.END_OBJECT) {
         String fieldName = parser.currentName();
         parser.nextToken();

         if("@class".equals(fieldName)) {
            className = parser.getValueAsString();
         }
         else if("value".equals(fieldName)) {
            if(className == null) {
               throw context.instantiationException(Object.class,
                  "@class field must appear before value field");
            }

            try {
               Class<?> clazz = Class.forName(className);
               JavaType javaType = context.constructType(clazz);
               JsonDeserializer<Object> deserializer = context.findRootValueDeserializer(javaType);
               value = deserializer.deserialize(parser, context);
            }
            catch(ClassNotFoundException e) {
               throw context.instantiationException(Object.class,
                  "Unknown class: " + className);
            }
         }
      }

      return value;
   }
}
