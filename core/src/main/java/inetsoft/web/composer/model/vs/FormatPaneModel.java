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
package inetsoft.web.composer.model.vs;

import inetsoft.uql.XFormatInfo;

public class FormatPaneModel {
   public FormatPaneModel() {
   }

   public FormatPaneModel(XFormatInfo formatInfo) {
      if(formatInfo == null) {
         return;
      }

      setFormat(formatInfo.getFormat());

      if(!DATE_OPTIONS[DATE_OPTIONS.length - 1].equals(formatInfo.getFormatSpec())) {
         setValue("");
         setDateValue(formatInfo.getFormatSpec());
      }
      else {
         setValue(formatInfo.getFormatSpec());
         setDateValue("Custom");
      }
   }

   public void updateXFormatInfo(XFormatInfo formatInfo) {
       formatInfo.setFormat(getFormat());
       formatInfo.setFormatSpec("Custom".equals(getDateValue()) ?
         getValue() : getDateValue());
   }

   public String getFormat() {
      return this.format;
   }

   public void setFormat(String format) {
      this.format = format;
   }

   public String getValue() {
      return this.value;
   }

   public void setValue(String value) {
      this.value = value;
   }

   public String getDateValue() {
      return this.dateValue;
   }

   public void setDateValue(String dateValue) {
      this.dateValue = dateValue;
   }

   private String format;
   private String value;
   private String dateValue;
   private static final String[] DATE_OPTIONS =
      {"Full", "Long", "Medium", "Short", "Custom"};
}
