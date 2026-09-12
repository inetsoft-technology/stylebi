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
package inetsoft.report.script.formula;

import inetsoft.report.internal.table.CalcCellContext;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.util.script.graal.ScriptScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

/**
 * This array represents a cell in a calc. It is used to execute a formula
 * in the calc scope (for condition evaluation).
 */
public class CalcCellScope implements ScriptScope {
   /**
    */
   public CalcCellScope(RuntimeCalcTableLens table, int row, int col) {
      this.table = table;

      setCell(row, col);
   }

   /**
    * Set the row and column index of this table cell.
    */
   public void setCell(int row, int col) {
      this.row = row;
      this.col = col;
   }

   public String getClassName() {
      return "CalcCell";
   }

   @Override
   public boolean hasMember(String id) {
      CalcCellContext context = table.getCellContext(row, col);
      return context != null && context.getGroup(id) != null;
   }

   @Override
   public Object getMember(String id) {
      CalcCellContext context = table.getCellContext(row, col);
      CalcCellContext.Group group = context.getGroup(id);

      if(group != null) {
         return group.getValue(context);
      }

      return members.get(id);
   }

   @Override
   public void putMember(String id, Object value) {
      // values can't be set in a crosstab cell formula scope
      LOG.error("Property can not be modified: " + id);
   }

   @Override
   public Object[] getMemberKeys() {
      CalcCellContext context = table.getCellContext(row, col);
      Vector ids = new Vector();

      for(CalcCellContext.Group group : context.getGroups()) {
         if(group.getName() != null) {
            ids.add(group.getName());
         }
      }

      return ids.toArray(new String[ids.size()]);
   }

   private RuntimeCalcTableLens table;
   private int row, col;
   private final Map<String, Object> members = new LinkedHashMap<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(CalcCellScope.class);
}
