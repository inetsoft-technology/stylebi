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

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;

import java.awt.*;
import java.awt.geom.RectangularShape;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;

/**
 * Module that defines serializers and deserializers for classes in third-party libraries.
 *
 * @since 12.3
 */
public class ThirdPartySupportModule extends SimpleModule {
   /**
    * Creates a new instance of <tt>ThirdPartySupportModule</tt>.
    */
   public ThirdPartySupportModule() {
      addSerializer(Dimension.class, new DimensionSerializer());
      addDeserializer(Dimension.class, new DimensionDeserializer());
      addSerializer(Point.class, new PointSerializer());
      addDeserializer(Point.class, new PointDeserializer());
      addSerializer(Insets.class, new InsetsSerializer());
      addDeserializer(Insets.class, new InsetsDeserializer());
      addSerializer(RectangularShape.class, new RectangularShapeSerializer());
      addDeserializer(Rectangle.class, new RectangleDeserializer());
      addSerializer(Time.class, new DateSerializer(
         false, new SimpleDateFormat("HH:mm:ss")));
      addSerializer(Timestamp.class, new DateSerializer(
         false, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")));
      addSerializer(Date.class, new DateSerializer(
         false, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")));
      addSerializer(EnumSet.class, new EnumSetSerializer());
      addDeserializer(EnumSet.class, new EnumSetDeserializer());

      addSerializer(Class.class, new ClassSerializer());
      addSerializer(Method.class, new MethodSerializer());
      addSerializer(Parameter.class, new ParameterSerializer());
   }
}
