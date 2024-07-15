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
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * This array represents a cell in a crosstab. It is used to execute a formula
 * in the crosstab scope (for condition evaluation).
 */
public class CrosstabCellScope extends ScriptableObject {
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

   @Override
   public String getClassName() {
      return "CrosstabCell";
   }

   @Override
   public boolean has(String id, Scriptable start) {
      return propmap.get(id) != null || super.has(id, start);
   }

   @Override
   public boolean has(int index, Scriptable start) {
      return false;
   }

   @Override
   public Object get(String id, Scriptable start) {
      Object val = propmap.get(id);

      if(val != null) {
         return val;
      }

      return super.get(id, start);
   }

   @Override
   public Object get(int index, Scriptable start) {
      return Undefined.instance;
   }

   @Override
   public void put(String id, Scriptable start, Object value) {
      // values can't be set in a crosstab cell formula scope
      LOG.error("Property can not be modified: " + id);
   }

   @Override
   public void put(int index, Scriptable start, Object value) {
      LOG.error("Property can not be modified: " + index);
   }

   @Override
   public Object getDefaultValue(Class hint) {
      if(hint == ScriptRuntime.BooleanClass) {
         return Boolean.TRUE;
      }
      else if(hint == ScriptRuntime.NumberClass) {
         return ScriptRuntime.NaNobj;
      }

      return this;
   }

   @Override
   public Object[] getIds() {
      return propmap.keySet().toArray();
   }

   @Override
   public boolean hasInstance(Scriptable value) {
      return false;
   }

   public String toString() {
      return super.toString() + propmap.toString();
   }

   private CrossTabFilter table;
   private int row, col;
   private Map propmap;

   private static final Logger LOG =
      LoggerFactory.getLogger(CrosstabCellScope.class);
}
