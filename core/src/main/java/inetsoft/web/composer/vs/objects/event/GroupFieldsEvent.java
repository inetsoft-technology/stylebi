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
package inetsoft.web.composer.vs.objects.event;

/**
 * Class that encapsulates the parameters grouping column fields for charts and tables.
 *
 * @since 12.3
 */
public class GroupFieldsEvent extends TableCellEvent {
   public String getGroupName() {
      return groupName;
   }

   public void setGroupName(String groupName) {
      this.groupName = groupName;
   }

   public String getPrevGroupName() {
      return prevGroupName;
   }

   public void setPrevGroupName(String prevGroupName) {
      this.prevGroupName = prevGroupName;
   }

   public String[] getLabels() {
      if(labels == null) {
         return new String[]{};
      }

      return labels;
   }

   public void setLabels(String[] labels) {
      this.labels = labels;
   }

   public String getColumnName() {
      return columnName;
   }

   public void setColumnName(String columnName) {
      this.columnName = columnName;
   }

   public boolean isLegend() {
      return legend;
   }

   public void setLegend(boolean legend) {
      this.legend = legend;
   }

   public boolean isAxis() {
      return axis;
   }

   public void setAxis(boolean axis) {
      this.axis = axis;
   }

   @Override
   public String toString() {
      return "TableColumnEvent{" +
         "name='" + this.getName() + '\'' +
         ", col=" + getCol() +
         ", row=" + getRow() +
         ", groupName=" + groupName +
         ", prevGroupName=" + prevGroupName +
         ", labels=" + labels +
         '}';
   }

   private String groupName;
   private String prevGroupName;
   private String[] labels;
   private String columnName;
   private boolean legend;
   private boolean axis;
}
