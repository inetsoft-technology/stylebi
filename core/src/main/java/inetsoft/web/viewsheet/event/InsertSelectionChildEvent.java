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
package inetsoft.web.viewsheet.event;

import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.vs.objects.event.ChangeVSObjectBindingEvent;

public class InsertSelectionChildEvent extends ChangeVSObjectBindingEvent {
   public int getToIndex() {
      return toIndex;
   }

   public void setToIndex(int toIndex) {
      this.toIndex = toIndex;
   }

   public OutputColumnRefModel[] getColumns() {
      return columns;
   }

   public void setColumn(OutputColumnRefModel[] columns) {
      this.columns = columns;
   }

   @Override
   public String toString() {
      return "InsertSelectionChildEvent{" +
         "name='" + this.getName() + '\'' +
         "binding='" + this.getBinding() + '\'' +
         "componentBinding='" + this.getComponentBinding() + '\'' +
         "x='" + this.getX() + '\'' +
         "y='" + this.getY() + '\'' +
         "tab='" + this.isTab() + '\'' +
         "toIndex='" + toIndex + '\'' +
         '}';
   }

   private int toIndex;
   private OutputColumnRefModel[] columns;
}
