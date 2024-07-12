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
package inetsoft.web.portal.model.database.events;

import javax.annotation.Nullable;

public class GetModelEvent {
   public GetModelEvent() {
   }

   public String getDatasource() {
      return datasource;
   }

   public void setDatasource(String datasource) {
      this.datasource = datasource;
   }

   public String getPhysicalName() {
      return physicalName;
   }

   public void setPhysicalName(String physicalName) {
      this.physicalName = physicalName;
   }

   public String getLogicalName() {
      return logicalName;
   }

   public void setLogicalName(String logicalName) {
      this.logicalName = logicalName;
   }

   public String getParent() {
      return parent;
   }

   @Nullable
   public void setParent(String parent) {
      this.parent = parent;
   }

   public String getAdditional() {
      return additional;
   }

   public void setAdditional(String additional) {
      this.additional = additional;
   }

   @Override
   public String toString() {
      return "GetModelEvent{" +
         "datasource='" + datasource + '\'' +
         ", physicalName='" + physicalName + '\'' +
         ", logicalName='" + logicalName + '\'' +
         ", parent='" + parent + '\'' +
         ", additional='" + additional + '\'' +
         '}';
   }

   private String datasource;
   private String physicalName;
   private String logicalName;
   private String parent; // for extended. physical model or logical model parent.
   @Nullable
   private String additional; // additional source
}