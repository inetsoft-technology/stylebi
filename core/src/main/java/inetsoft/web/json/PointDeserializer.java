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
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.awt.*;
import java.io.IOException;

/**
 * Class that handles deserializing {@link Point} objects from JSON.
 *
 * @since 12.3
 */
public class PointDeserializer extends StdDeserializer<Point> {
   /**
    * Creates a new instance of <tt>PointDeserializer</tt>.
    */
   public PointDeserializer() {
      super(Point.class);
   }

   @Override
   public Point deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
      JsonNode node = parser.getCodec().readTree(parser);
      int x = node.get("x").intValue();
      int y = node.get("y").intValue();
      return new Point(x, y);
   }
}
