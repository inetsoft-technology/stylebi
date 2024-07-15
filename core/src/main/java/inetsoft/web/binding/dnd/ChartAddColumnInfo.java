/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.binding.dnd;


import inetsoft.web.binding.model.graph.ChartRefModel;

public class ChartAddColumnInfo extends ChangeColumnInfo {
   public void setAggr(ChartRefModel agg) {
      this.agg = agg;
   }

   public ChartRefModel getAggr() {
      return agg;
   }
   
   public void setType(String type) {
      this.type = type;
   }

   public String getType() {
      return type;
   }

   public void setIndex(int index) {
      this.index = index;
   }

   public int getIndex() {
      return index;
   }

   public void setReplace(boolean replace) {
      this.replace = replace;
   }

   public boolean getReplace() {
      return replace;
   }

   public String toAttribute() {
      String attribute = "";

      if(index != 0) {
         attribute += "_index:" + index;
      }

      return "_type:" + type + attribute;
   }

   private String type;
   private int index;
   private boolean replace;
   private ChartRefModel agg;
}
