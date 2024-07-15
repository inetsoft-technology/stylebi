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
package inetsoft.web.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.awt.*;
import java.io.IOException;

/**
 * Class that handles deserializing {@link Dimension} objects from JSON.
 *
 * @since 12.3
 */
public class DimensionDeserializer extends StdDeserializer<Dimension> {
   /**
    * Creates a new instance of <tt>DimensionDeserializer</tt>.
    */
   public DimensionDeserializer() {
      super(Dimension.class);
   }

   @Override
   public Dimension deserialize(JsonParser jsonParser,
                                DeserializationContext deserializationContext)
      throws IOException
   {
      JsonNode node = jsonParser.getCodec().readTree(jsonParser);
      int width = node.get("width").intValue();
      int height  = node.get("height").intValue();
      return new Dimension(width, height);
   }
}
