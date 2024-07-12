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
package inetsoft.web.composer.model.ws;

import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.model.AggregateInfoModel;

import java.util.HashMap;
import java.util.Map;

public class AggregateDialogModel {
   public int getMaxCol() {
      return maxCol;
   }

   public void setMaxCol(int maxCol) {
      this.maxCol = maxCol;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public ColumnRefModel[] getColumns() {
      if(columns == null) {
         columns = new ColumnRefModel[0];
      }

      return columns;
   }

   public void setColumns(ColumnRefModel[] columns) {
      this.columns = columns;
   }

   public AggregateInfoModel getInfo() {
      if(info == null) {
         info = new AggregateInfoModel();
      }

      return info;
   }

   public void setInfo(AggregateInfoModel info) {
      this.info = info;
   }

   public Map<String, String[]> getGroupMap() {
      if(groupMap == null) {
         groupMap = new HashMap<>();
      }

      return groupMap;
   }

   public void setGroupMap(Map<String, String[]> groupMap) {
      this.groupMap = groupMap;
   }

   public Map<String, String> getAliasMap() {
      if(aliasMap == null) {
         aliasMap = new HashMap<>();
      }

      return aliasMap;
   }

   public void setAliasMap(Map<String, String> aliasMap) {
      this.aliasMap = aliasMap;
   }

   private String name;
   private ColumnRefModel[] columns;
   private AggregateInfoModel info;
   private Map<String, String[]> groupMap;
   private Map<String, String> aliasMap;

   private int maxCol = 0;
}
