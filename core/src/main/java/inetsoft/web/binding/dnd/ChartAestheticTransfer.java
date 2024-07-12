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
package inetsoft.web.binding.dnd;

import inetsoft.web.binding.model.graph.ChartRefModel;

public class ChartAestheticTransfer extends ChartTransfer {
   public void setAggr(ChartRefModel agg) {
      this.agg = agg;
   }

   public ChartRefModel getAggr() {
      return agg;
   }

   public String getTargetField() {
      return targetField;
   }

   public void setTargetField(String targetField) {
      this.targetField = targetField;
   }

   public String toAttribute() {
      return "_type:" + this.getDragType();
   }

   private ChartRefModel agg;
   private String targetField;
}