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
package inetsoft.web.portal.model.database.cube;

import inetsoft.web.portal.model.database.AutoDrillInfo;
import inetsoft.web.portal.model.database.XFormatInfoModel;

public class XMetaInfoModel {
   public XMetaInfoModel() {
   }

   public AutoDrillInfo getDrillInfo() {
      return drillInfo;
   }

   public void setDrillInfo(AutoDrillInfo drillInfo) {
      this.drillInfo = drillInfo;
   }

   public XFormatInfoModel getFormatInfo() {
      return formatInfo;
   }

   public void setFormatInfo(XFormatInfoModel formatInfo) {
      this.formatInfo = formatInfo;
   }

   public boolean isAsDate() {
      return asDate;
   }

   public void setAsDate(boolean asDate) {
      this.asDate = asDate;
   }

   public String getDatePattern() {
      return datePattern;
   }

   public void setDatePattern(String datePattern) {
      this.datePattern = datePattern;
   }

   public String getLocale() {
      return locale;
   }

   public void setLocale(String locale) {
      this.locale = locale;
   }

   private AutoDrillInfo drillInfo;
   private XFormatInfoModel formatInfo;
   private boolean asDate;
   private String datePattern;
   private String locale;
}
