/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.wiz.model;

import java.util.ArrayList;
import java.util.List;

public class JoinTableMeta extends WorksheetTableMeta {
   public List<JoinInfo> getJoins() {
      return joins;
   }

   public void setJoins(List<JoinInfo> joins) {
      this.joins = joins;
   }

   private List<JoinInfo> joins = new ArrayList<>();

   @Override
   public String getTableType() {
      return "joinTable";
   }

   public static class JoinInfo {
      private String leftTable;
      private String rightTable;
      private String leftColumn;
      private String rightColumn;
      private String joinType;

      public String getLeftTable() {
         return leftTable;
      }

      public void setLeftTable(String leftTable) {
         this.leftTable = leftTable;
      }

      public String getRightTable() {
         return rightTable;
      }

      public void setRightTable(String rightTable) {
         this.rightTable = rightTable;
      }

      public String getLeftColumn() {
         return leftColumn;
      }

      public void setLeftColumn(String leftColumn) {
         this.leftColumn = leftColumn;
      }

      public String getRightColumn() {
         return rightColumn;
      }

      public void setRightColumn(String rightColumn) {
         this.rightColumn = rightColumn;
      }

      public String getJoinType() {
         return joinType;
      }

      public void setJoinType(String joinType) {
         this.joinType = joinType;
      }
   }
}
