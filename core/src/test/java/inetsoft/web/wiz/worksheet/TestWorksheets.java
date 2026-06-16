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
package inetsoft.web.wiz.worksheet;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.BoundTableAssembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;

/**
 * Test fixtures for building concrete worksheets with table assemblies.
 */
final class TestWorksheets {
   private TestWorksheets() {
   }

   /**
    * Build a concrete table assembly named {@code name} whose column selection
    * contains one {@link ColumnRef} per supplied column name. The caller is
    * responsible for adding it to the worksheet via {@code ws.addAssembly(...)}.
    */
   static TableAssembly tableWithColumns(Worksheet ws, String name, String... cols) {
      BoundTableAssembly table = new BoundTableAssembly(ws, name);
      ColumnSelection columns = new ColumnSelection();

      for(String col : cols) {
         columns.addAttribute(new ColumnRef(new AttributeRef(null, col)));
      }

      table.setColumnSelection(columns, false);
      return table;
   }
}
