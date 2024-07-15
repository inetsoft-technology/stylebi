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
package inetsoft.web.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.awt.*;
import java.io.IOException;

/**
 * Class that handles deserializing {@link Rectangle} objects from JSON.
 *
 * @since 12.3
 */
public class RectangleDeserializer extends StdDeserializer<Rectangle> {
   /**
    * Creates a new instance of <tt>RectangleDeserializer</tt>.
    */
   public RectangleDeserializer() {
      super(Rectangle.class);
   }

   @Override
   public Rectangle deserialize(JsonParser parser, DeserializationContext context)
      throws IOException
   {
      JsonNode node = parser.getCodec().readTree(parser);
      int x = node.get("x").intValue();
      int y = node.get("y").intValue();
      int width = node.get("width").intValue();
      int height = node.get("height").intValue();
      return new Rectangle(x, y, width, height);
   }
}
