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
package inetsoft.web.binding.dnd;

import inetsoft.web.binding.model.graph.ChartRefModel;

import java.util.ArrayList;
import java.util.List;

public class ChartRemoveColumnInfo extends ChangeColumnInfo {
   public void setRefs(List<ChartRefModel> refs) {
      this.refs = refs;
   }

   public List<ChartRefModel> getRefs() {
      return refs;
   }

   public void setAggr(ChartRefModel agg) {
      this.agg = agg;
   }

   public ChartRefModel getAggr() {
      return agg;
   }

   public void setType(String rtype) {
      this.type = rtype;
   }

   public String getType() {
      return type;
   }
   
   private String type;
   private int index;
   private ChartRefModel agg;
   private List<ChartRefModel> refs = new ArrayList<>();
}