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
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.awt.geom.Point2D;
import java.io.IOException;

/**
 * Class that handles deserializing {@link Point2D.Double} objects from JSON.
 */
public class Point2DDeserializer extends StdDeserializer<Point2D.Double> {
   /**
    * Creates a new instance of <tt>Point2DDeserializer</tt>.
    */
   public Point2DDeserializer() {
      super(Point2D.Double.class);
   }

   @Override
   public Point2D.Double deserialize(JsonParser jsonParser,
                                     DeserializationContext deserializationContext)
      throws IOException
   {
      JsonNode node = jsonParser.getCodec().readTree(jsonParser);
      double x = node.path("x").doubleValue();
      double y = node.path("y").doubleValue();
      return new Point2D.Double(x, y);
   }
}
