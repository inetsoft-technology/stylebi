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
package inetsoft.web.composer.model.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.web.binding.drm.ColumnRefModel;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TableAssemblyOperatorModel {
   public int getOperation() {
      return operation;
   }

   public void setOperation(int operation) {
      this.operation = operation;
   }

   public boolean getDistinct() {
      return distinct;
   }

   public void setDistinct(boolean distinct) {
      this.distinct = distinct;
   }

   public ColumnRefModel getLref() {
      return lref;
   }

   public void setLref(ColumnRefModel lref) {
      this.lref = lref;
   }

   public ColumnRefModel getRref() {
      return rref;
   }

   public void setRref(ColumnRefModel rref) {
      this.rref = rref;
   }

   public String getLtable() {
      return ltable;
   }

   public void setLtable(String ltable) {
      this.ltable = ltable;
   }

   public String getRtable() {
      return rtable;
   }

   public void setRtable(String rtable) {
      this.rtable = rtable;
   }

   private int operation;
   private boolean distinct;
   private ColumnRefModel lref;
   private ColumnRefModel rref;
   private String ltable;
   private String rtable;
}