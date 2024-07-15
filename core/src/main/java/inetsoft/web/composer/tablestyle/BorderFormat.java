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

import inetsoft.report.style.XTableStyle;

/**
 * for table style top-border bottom-border  left-border right-border.
 */
public class BorderFormat implements TableStyleRegionFormat {
   public BorderFormat() {
   }

   public BorderFormat(XTableStyle tableStyle, String region) {
      super();

      this.region = region;
      Object border = tableStyle.get(getAttributeKey("border"));
      Object color = tableStyle.get(getAttributeKey("color"));
      setBorder(border instanceof Integer ? (Integer) border : -1);
      setBorderColor(getColorString(color));
   }

   @Override
   public void updateTableStyle(XTableStyle tableStyle) {
      if(tableStyle != null) {
         tableStyle.put(getAttributeKey("border"), this.border >= 0 ? this.border : null);
         tableStyle.put(getAttributeKey("color"), getColor(this.borderColor));
      }
   }

   @Override
   public String getRegion() {
      return region;
   }

   public void setRegion(String region) {
      this.region = region;
   }

   public int getBorder() {
      return border;
   }

   public void setBorder(int border) {
      this.border = border;
   }

   public String getBorderColor() {
      return borderColor;
   }

   public void setBorderColor(String borderColor) {
      this.borderColor = borderColor;
   }

   private String region;
   private int border;
   private String borderColor;
}
