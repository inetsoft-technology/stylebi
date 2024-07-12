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
package inetsoft.web.portal.model.database;

public class AttributeFormatInfoModel {
   public String getFormat() {
      return format;
   }

   public void setFormat(String format) {
      this.format = format;
   }

   public String getFormatSpec() {
      return formatSpec;
   }

   public void setFormatSpec(String formatSpec) {
      this.formatSpec = formatSpec;
   }

   public boolean isDurationPadZeros() {
      return durationPadZeros;
   }

   public void setDurationPadZeros(boolean durationPadZeros) {
      this.durationPadZeros = durationPadZeros;
   }

   public String[] getDecimalFmts() {
      return decimalFmts;
   }

   public void setDecimalFmts(String[] decimalFmts) {
      this.decimalFmts = decimalFmts;
   }

   private String format = null;
   private String formatSpec = null;
   private boolean durationPadZeros = true;
   private String[] decimalFmts;
}
