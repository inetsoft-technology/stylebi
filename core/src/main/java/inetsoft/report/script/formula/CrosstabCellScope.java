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

import inetsoft.report.filter.CrossTabFilter;
import inetsoft.util.script.graal.ScriptScope;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * This array represents a cell in a crosstab. It is used to execute a formula
 * in the crosstab scope (for condition evaluation).
 */
public class CrosstabCellScope implements ScriptScope {
   /**
    */
   public CrosstabCellScope(CrossTabFilter table, int row, int col) {
      this.table = table;

      setCell(row, col);
   }

   /**
    * Set the row and column index of this table cell.
    */
   public void setCell(int row, int col) {
      this.row = row;
      this.col = col;

      propmap = table.getKeyValuePairs(row, col, new Object2ObjectOpenHashMap<>());
   }

   public String getClassName() {
      return "CrosstabCell";
   }

   @Override
   public boolean hasMember(String id) {
      return propmap.get(id) != null;
   }

   @Override
   public Object getMember(String id) {
      Object val = propmap.get(id);

      if(val != null) {
         return val;
      }

      return null;
   }

   @Override
   public void putMember(String id, Object value) {
      // values can't be set in a crosstab cell formula scope
      LOG.error("Property can not be modified: " + id);
   }

   @Override
   public Object[] getMemberKeys() {
      return propmap.keySet().toArray();
   }

   public String toString() {
      return getClassName() + propmap.toString();
   }

   private CrossTabFilter table;
   private int row, col;
   private Map propmap;

   private static final Logger LOG =
      LoggerFactory.getLogger(CrosstabCellScope.class);
}
