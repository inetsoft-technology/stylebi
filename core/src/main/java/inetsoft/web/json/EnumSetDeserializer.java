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
package inetsoft.web.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.type.CollectionLikeType;

import java.io.IOException;
import java.util.EnumSet;

public class EnumSetDeserializer extends JsonDeserializer<EnumSet> implements ContextualDeserializer {
   @SuppressWarnings("unchecked")
   @Override
   public EnumSet deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      EnumSet result = EnumSet.noneOf(enumType);

      if(p.getCurrentToken() == JsonToken.START_ARRAY) {
         while(p.nextToken() != JsonToken.END_ARRAY) {
            String name = p.getValueAsString();
            Enum<?> value = Enum.valueOf(enumType, name);
            result.add(value);
         }
      }

      return result;
   }

   @Override
   public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
      CollectionLikeType type = (CollectionLikeType) property.getType();
      EnumSetDeserializer deserializer = new EnumSetDeserializer();
      deserializer.enumType = type.getContentType().getRawClass();
      return deserializer;
   }

   private Class enumType;
}
