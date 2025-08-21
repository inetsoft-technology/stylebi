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
package inetsoft.web.portal.model.database;

import java.io.Serializable;

public class QueryFieldModel implements Serializable {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getAlias() {
      return alias;
   }

   public void setAlias(String alias) {
      this.alias = alias;
   }

   public String getDataType() {
      return dataType;
   }

   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   public AutoDrillInfo getDrillInfo() {
      return drillInfo;
   }

   public void setDrillInfo(AutoDrillInfo drillInfo) {
      this.drillInfo = drillInfo;
   }

   public XFormatInfoModel getFormat() {
      return format;
   }

   public void setFormat(XFormatInfoModel format) {
      this.format = format;
   }

   private String name;
   private String alias;
   private String dataType;
   private AutoDrillInfo drillInfo;
   private XFormatInfoModel format;
}
