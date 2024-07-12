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
package inetsoft.web.viewsheet.model;

import java.util.List;

public class CrosstabDrillFilterAction extends DrillFilterAction {
   public List<DrillCellInfo> getCellInfos() {
      return cellInfos;
   }

   public CrosstabDrillFilterAction setCellInfos(List<DrillCellInfo> cellInfos) {
      this.cellInfos = cellInfos;

      return this;
   }

   private List<DrillCellInfo> cellInfos;

   public static class DrillCellInfo {
      public int getRow() {
         return row;
      }

      public DrillCellInfo setRow(int row) {
         this.row = row;
         return this;
      }

      public int getCol() {
         return col;
      }

      public DrillCellInfo setCol(int col) {
         this.col = col;
         return this;
      }

      public String getField() {
         return field;
      }

      public DrillCellInfo setField(String field) {
         this.field = field;
         return this;
      }

      public String getDirection() {
         return direction;
      }

      public DrillCellInfo setDirection(String direction) {
         this.direction = direction;
         return this;
      }

      public String getSelectionString() {
         return row + "X" + col;
      }

      private int row;
      private int col;
      private String field;
      private String direction;
   }
}
