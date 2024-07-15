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
import inetsoft.web.composer.model.SortInfoModel;
import inetsoft.web.composer.model.vs.TableStylePaneModel;
import inetsoft.web.viewsheet.model.PreviewTableCellModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Command used to pass preview table information to a vsobject
 */
@Value.Immutable
@JsonSerialize(as = ImmutableLoadPreviewTableCommand.class)
public abstract class LoadPreviewTableCommand implements ViewsheetCommand {
   @Nullable
   public abstract PreviewTableCellModel[][] tableData();

   @Nullable
   public abstract String worksheetId();

   @Nullable
   public abstract TableStylePaneModel styleModel();

   @Nullable
   public abstract SortInfoModel sortInfo();

   @Nullable
   public abstract int[] colWidths();

   // prototype cache for table cells
   @Value.Derived
   public Map<Integer, PreviewTableCellModel.PrototypedPreviewTableCellModel> prototypeCache() {
      final Map<PreviewTableCellModel.PrototypedPreviewTableCellModel, Integer> dataPathToIndex =
         new HashMap<>();
      final Map<Integer, PreviewTableCellModel.PrototypedPreviewTableCellModel> prototypeIndexes =
         new HashMap<>();
      final PreviewTableCellModel[][] tableData = tableData();

      if(tableData != null) {
         for(PreviewTableCellModel[] cells : tableData) {
            for(PreviewTableCellModel cell : cells) {
               if(cell == null) {
                  continue;
               }

               PreviewTableCellModel.PrototypedPreviewTableCellModel prototype =
                  cell.createModelPrototype();
               dataPathToIndex.putIfAbsent(prototype, dataPathToIndex.size());
               final Integer index = dataPathToIndex.get(prototype);
               prototypeIndexes.put(index, prototype);
               cell.setModelPrototypeIndex(index);
            }
         }
      }

      return prototypeIndexes;
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableLoadPreviewTableCommand.Builder {
   }
}
