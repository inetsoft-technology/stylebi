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
 * Class that encapsulates the parameters for hide and show table columns.
 *
 * @since 13.3
 */
public class ShowHideCrosstabColumnsEvent extends VSObjectEvent {
   public int[] getColumns() {
      if(columns == null) {
         return new int[0];
      }

      return columns;
   }

   public void setColumns(int[] columns) {
      this.columns = columns;
   }

   public boolean isShowColumns() {
      return showColumns;
   }

   public void setShowColumns(boolean showColumns) {
      this.showColumns = showColumns;
   }

   @Override
   public String toString() {
      return "ShowHideTableColumnsEvent{" +
         "name='" + this.getName() + '\'' + '}';
   }

   private int[] columns;
   private boolean showColumns;
}
