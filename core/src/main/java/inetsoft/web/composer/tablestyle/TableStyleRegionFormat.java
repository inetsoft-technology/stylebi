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
package inetsoft.web.composer.tablestyle;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.report.style.XTableStyle;
import inetsoft.util.Tool;
import inetsoft.web.composer.tablestyle.css.CSSUtil;

import java.awt.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.PROPERTY,
   property = "type")
@JsonSubTypes({
   @JsonSubTypes.Type(value = BodyRegionFormat.class, name = "body"),
   @JsonSubTypes.Type(value = RowRegionFormat.class, name = "rowRegion"),
   @JsonSubTypes.Type(value = ColRegionFormat.class, name = "colRegion"),
   @JsonSubTypes.Type(value = BorderFormat.class, name = "borderRegion")
})
public interface TableStyleRegionFormat {
   abstract String getRegion();

   abstract void updateTableStyle(XTableStyle tableStyle);

   default String getAttributeKey(String attrName) {
      StringBuilder builder = new StringBuilder();
      builder.append(getRegion());
      builder.append(".");
      builder.append(attrName);
      return builder.toString();
   }

   default String getColorString(Object color) {
      if(color instanceof Color) {
         return "#" + Tool.colorToHTMLString((Color) color);
      }

      return null;
   }

   default Object getColor(String color) {
      if(!Tool.isEmptyString(color)) {
         String[] parts = color.split("#");
         String result = parts[1];
         return Tool.getColorFromHexString(result);
      }

      return null;
   }

   default String getBorderStyle(Object border, Object color) {
      if(border instanceof Integer && color instanceof Color) {
         return CSSUtil.getBorderStyle2(((Integer) border).intValue(), (Color) color);
      }

      return null;
   }

}
