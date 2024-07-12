/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.json.lookup;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.type.*;

import java.util.List;

/**
 * Modifier for specifying the deserializer of List<{@link JsonLookupEndpoint}>.
 */
public class JsonLookupEndpointsModifier extends BeanDeserializerModifier {
   @Override
   public JsonDeserializer<?> modifyCollectionDeserializer(DeserializationConfig config,
                                                           CollectionType type,
                                                           BeanDescription beanDesc,
                                                           JsonDeserializer<?> deserializer)
   {
      if(type.getContentType().getRawClass().equals(JsonLookupEndpoint.class) && type.isTypeOrSubTypeOf(List.class)) {
         return new JsonLookupEndpointsDeserializer();
      }
      else {
         return super.modifyCollectionDeserializer(config, type, beanDesc, deserializer);
      }
   }
}
