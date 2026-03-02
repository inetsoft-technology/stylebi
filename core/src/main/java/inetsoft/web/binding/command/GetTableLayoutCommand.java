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
package inetsoft.web.binding.command;

import inetsoft.web.binding.model.table.CalcTableLayout;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.viewsheet.command.ViewsheetCommand;

import java.util.HashMap;
import java.util.Map;

/**
 * Command that instructs the client to refresh an assembly object.
 *
 * @since 12.3
 */
public class GetTableLayoutCommand implements ViewsheetCommand {
   /**
    * Construstor.
    */
   public GetTableLayoutCommand(CalcTableLayout layout) {
      this.layout = layout;
   }

   /**
    * Get the table layout.
    * @return the table layout.
    */
   public CalcTableLayout getLayout() {
      return layout;
   }

   /**
    * Set the table layout.
    * @param layout the table layout.
    */
   public void setLayout(CalcTableLayout layout) {
      this.layout = layout;
   }

   public Map<String, CellBindingInfo> getCellBindings() {
      return cellBindings;
   }

   public void setCellBindings(Map<String, CellBindingInfo> cellBindings) {
      this.cellBindings = cellBindings;
   }

   private CalcTableLayout layout;
   private Map<String, CellBindingInfo> cellBindings = new HashMap<>();
}
