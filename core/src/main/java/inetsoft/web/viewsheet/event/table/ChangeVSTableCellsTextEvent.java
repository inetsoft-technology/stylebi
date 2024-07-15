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
package inetsoft.web.viewsheet.event.table;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableChangeVSTableCellsTextEvent.class)
public interface ChangeVSTableCellsTextEvent extends BaseTableEvent {
   TableCellTextChange[] changes();

   class Builder extends ImmutableChangeVSTableCellsTextEvent.Builder {
   }

   final class TableCellTextChange {
      public int getCol() {
         return col;
      }

      public void setCol(int col) {
         this.col = col;
      }

      public int getRow() {
         return row;
      }

      public void setRow(int row) {
         this.row = row;
      }

      public String getText() {
         return text;
      }

      public void setText(String text) {
         this.text = text;
      }

      @Override
      public String toString() {
         return "TableCellTextChange{" +
            "col='" + col + '\'' +
            ", row=" + row +
            ", text=" + text +
            '}';
      }

      private int col;
      private int row;
      private String text;
   }
}
