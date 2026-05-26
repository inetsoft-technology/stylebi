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
package inetsoft.web.binding.event;

import inetsoft.web.binding.model.table.TableCell;

import java.io.Serializable;

/**
 * Class that encapsulates the parameters for applying a selection.
 *
 * @since 12.3
 */
public class GetCellBindingEvent implements Serializable {
   /**
    * Get the assembly name.
    * @return assembly name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the assembly name.
    * @param name assembly name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the select cells.
    * @return the select cells.
    */
   public TableCell[] getSelectCells() {
      return selectCells;
   }

   /**
    * Set the select cells.
    * @param selectCells the select cells.
    */
   public void setSelectCells(TableCell[] selectCells) {
      this.selectCells = selectCells;
   }

   private String name;
   private TableCell[] selectCells;
}
