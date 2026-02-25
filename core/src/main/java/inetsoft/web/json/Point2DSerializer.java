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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.awt.geom.Point2D;
import java.io.IOException;

/**
 * Class that handles serializing {@link Point2D} objects as JSON.
 */
public class Point2DSerializer extends StdSerializer<Point2D> {
   /**
    * Creates a new instance of <tt>Point2DSerializer</tt>.
    */
   public Point2DSerializer() {
      super(Point2D.class);
   }

   @Override
   public void serialize(Point2D value, JsonGenerator generator,
                         SerializerProvider provider) throws IOException
   {
      generator.writeStartObject();
      generator.writeNumberField("x", value.getX());
      generator.writeNumberField("y", value.getY());
      generator.writeEndObject();
   }
}
