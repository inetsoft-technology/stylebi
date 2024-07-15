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
package inetsoft.web.adhoc.model.property;

public class ColorInfo {
   public ColorInfo() {

   }

   public ColorInfo(String color, String type) {
      this.type = type;
      this.color = color;
   }

   /**
    * Gets color type
    */
   public String getType() {
      return type;
   }

   /**
    * Sets color type
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Gets color value
    */
   public String getColor() {
      return color;
   }

   /**
    * Sets color value
    */
   public void setColor(String color) {
      this.color = color;
   }

   private String type;
   private String color;
}
