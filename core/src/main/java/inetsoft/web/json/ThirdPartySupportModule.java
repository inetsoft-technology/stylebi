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

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.geom.Point2D;
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
      addSerializer(Point2D.class, new Point2DSerializer());
      addDeserializer(Point2D.Double.class, new Point2DDeserializer());
      addSerializer(Insets.class, new InsetsSerializer());
      addDeserializer(Insets.class, new InsetsDeserializer());
      addSerializer(RectangularShape.class, new RectangularShapeSerializer());
      addDeserializer(Rectangle.class, new RectangleDeserializer());
      addSerializer(Time.class, new DateSerializer(
         false, gregorianDateFormat("HH:mm:ss")));
      addSerializer(Timestamp.class, new DateSerializer(
         false, gregorianDateFormat("yyyy-MM-dd HH:mm:ss")));
      addSerializer(Date.class, new DateSerializer(
         false, gregorianDateFormat("yyyy-MM-dd HH:mm:ss")));
      addSerializer(EnumSet.class, new EnumSetSerializer());
      addDeserializer(EnumSet.class, new EnumSetDeserializer());

      addSerializer(Class.class, new ClassSerializer());
      addSerializer(Method.class, new MethodSerializer());
      addSerializer(Parameter.class, new ParameterSerializer());
   }

   /**
    * Bug #77566: a locale-less {@code SimpleDateFormat} uses the JVM default locale's calendar
    * (e.g. Buddhist for th_TH, Japanese imperial for ja_JP_JP), and none of these patterns carry
    * an era field, so the serialized digits would otherwise silently become the wrong calendar
    * system's year on a non-Gregorian-default deployment. These serializers have no matching
    * custom deserializer registered anywhere, so nothing in this codebase ever reads this exact
    * string back through the same format -- forcing Gregorian here only fixes the serialized
    * value, it does not change any persisted/round-tripped format.
    */
   private static SimpleDateFormat gregorianDateFormat(String pattern) {
      return Tool.createGregorianDateFormat(pattern);
   }
}
