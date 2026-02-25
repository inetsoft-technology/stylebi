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

package inetsoft.web.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Deserializer for the RuntimeSheet prop map that reads embedded type information for each value.
 * This allows polymorphic values (e.g., Dimension, Point2D.Double) to be correctly restored
 * without enabling global default typing on the ObjectMapper.
 *
 * <p>Expects each entry in format: {@code "key": {"@class": "fully.qualified.ClassName", "value": ...}}</p>
 *
 * <p><b>SECURITY NOTE:</b> This deserializer uses a class allowlist to restrict which types can be
 * instantiated, providing defense-in-depth against deserialization attacks (CVE-2017-7525 and
 * descendants). Only classes in {@link #ALLOWED_CLASSES} can be deserialized; others are logged
 * and skipped. If new types are added to the prop map, they must be added to the allowlist.</p>
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
      // Buffer fields first since JSON field ordering is not guaranteed (RFC 8259)
      String className = null;
      JsonNode valueNode = null;

      while(parser.nextToken() != JsonToken.END_OBJECT) {
         String fieldName = parser.currentName();
         parser.nextToken();

         if("@class".equals(fieldName)) {
            className = parser.getValueAsString();
         }
         else if("value".equals(fieldName)) {
            valueNode = parser.readValueAsTree();
         }
      }

      if(className == null || valueNode == null) {
         return null;
      }

      if(!ALLOWED_CLASSES.contains(className)) {
         LOG.warn("Ignoring prop map value with disallowed class: {}", className);
         return null;
      }

      try {
         Class<?> clazz = Class.forName(className);
         ObjectMapper mapper = (ObjectMapper) parser.getCodec();
         return mapper.convertValue(valueNode, clazz);
      }
      catch(ClassNotFoundException e) {
         LOG.warn("Ignoring prop map value with unknown class: {}", className);
         return null;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(TypedPropertyMapDeserializer.class);

   /**
    * Allowlist of classes that can be deserialized from the prop map.
    * Add new types here if they are legitimately stored in RuntimeSheet properties.
    */
   private static final Set<String> ALLOWED_CLASSES = Set.of(
      "java.awt.Dimension",
      "java.awt.Point",
      "java.awt.geom.Point2D$Double",
      "java.awt.Insets",
      "java.awt.Rectangle",
      "java.lang.Boolean",
      "java.lang.Double",
      "java.lang.Float",
      "java.lang.Integer",
      "java.lang.Long",
      "java.lang.String"
   );
}
