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
package inetsoft.web.viewsheet.command;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.composer.model.vs.HyperlinkModel;
import inetsoft.web.viewsheet.model.ModelPrototype;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import inetsoft.web.viewsheet.model.table.BaseTableCellModelPrototype;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Class used to instruct the client to add an assembly object.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableLoadTableDataCommand.class)
public abstract class LoadTableDataCommand implements ViewsheetCommand {
   public abstract double[] colWidths();
   public abstract int rowCount();
   public abstract int colCount();
   public abstract int dataRowCount();
   public abstract int dataColCount();
   public abstract int headerRowCount();
   public abstract int headerColCount();
   public abstract int[] headerRowHeights();
   public abstract int dataRowHeight();
   public abstract int[] headerRowPositions();
   public abstract int[] dataRowPositions();
   public abstract int scrollHeight();
   public abstract boolean wrapped();
   public abstract boolean formChanged();
   @Nullable
   public abstract String limitMessage();
   public abstract BaseTableCellModel[][] tableCells();

   //if assembly is Table, and start != 0, then should send table header to web when load
   //table data. Because the order of the title may change.
   @Nullable
   public abstract BaseTableCellModel[][] tableHeaderCells();
   public abstract int start();
   public abstract int end();
   // number of post-expansion row-headers
   public abstract int runtimeRowHeaderCount();

   // number of post-expansion col-headers
   public abstract int runtimeColHeaderCount();

   // runtime data row count (for add/remove in form tables)
   public abstract int runtimeDataRowCount();
   public abstract HyperlinkModel[] rowHyperlinks();

   // prototype cache for table cells
   @Value.Derived
   public Map<Integer, ModelPrototype> prototypeCache() {
      final Map<BaseTableCellModelPrototype, Integer> dataPathToIndex = new HashMap<>();
      final Map<Integer, ModelPrototype> prototypeIndexes = new HashMap<>();

      for(BaseTableCellModel[] cells : tableCells()) {
         for(BaseTableCellModel cell : cells) {
            BaseTableCellModelPrototype prototype = cell.createModelPrototype();
            dataPathToIndex.putIfAbsent(prototype, dataPathToIndex.size());
            final Integer index = dataPathToIndex.get(prototype);
            prototypeIndexes.put(index, prototype);
            cell.setModelPrototypeIndex(index);
         }
      }

      return prototypeIndexes;
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableLoadTableDataCommand.Builder {
   }
}
