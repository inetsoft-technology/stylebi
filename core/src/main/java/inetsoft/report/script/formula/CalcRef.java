/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.script.formula;

import inetsoft.report.internal.table.*;
import inetsoft.util.script.FormulaContext;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * This class provides the named cell reference support for calc table.
 */
public class CalcRef extends ScriptableObject implements Wrapper {
   /**
    * Create a calc name reference.
    */
   public CalcRef(RuntimeCalcTableLens table, String name) {
      this.table = table;
      this.cellname = name;
   }

   @Override
   public String getClassName() {
      return "CalcRef";
   }

   /**
    * No numeric indexing.
    */
   @Override
   public boolean has(int index, Scriptable start) {
      return false;
   }

   /**
    * Get cells according to the CellRange specification.
    */
   @Override
   public Object get(String id, Scriptable start) {
      try {
         // returns the sequence number in the group
         if("#".equals(id)) {
            Point loc = FormulaContext.getCellLocation();
            CalcCellContext context = table.getCellContext(loc.y, loc.x);

            if(context != null) {
               CalcCellContext.Group group = context.getGroup(cellname);

               if(group != null) {
                  // position is 0 based to sync with get(int, Scriptable)
                  return group.getPosition();
               }
            }

            return 0;
         }
         // return all cells with the specified name
         else if("**".equals(id)) {
            return getAllValues(cellname);
         }
         // return all group values
         else if("*".equals(id)) {
            return getGroupValues(cellname);
         }
         // return child (of the current context) cells with the specified name
         else if("+".equals(id)) {
            return getChildGroupValues(cellname);
         }
         // get the value of this reference, this ($name['.']) is same as $name
         // exception in some cases, the value may need to be explicitly
         // retrieved. For example, $name == null is always false since the
         // reference is not null, but $name['.'] == null would be true if the
         // value of the reference is null
         else if(".".equals(id)) {
            return unwrap();
         }
         // check positional reference
         else if(id.length() > 0) {
            try {
               boolean pos = id.charAt(0) == '+';
               boolean neg = id.charAt(0) == '-';
               int idx = Integer.parseInt(pos ? id.substring(1) : id);

               return getByPosition(idx, pos || neg);
            }
            catch(NumberFormatException ex) {
               // not a positional reference
            }
            catch(Exception ex) {
               LOG.debug("Failed to get positional reference: " + id, ex);
            }
         }

         return getBySpec(id);
      }
      catch(Exception ex) {
         LOG.warn("Failed to get reference property: " + id, ex);
      }

      return super.get(id, start);
   }

   /**
    * Get all values of the group across the entire table.
    */
   private Object[] getAllValues(String cellname) {
      CalcCellIterator iter = new CalcCellIterator(table, cellname);
      List<Object> values = new ArrayList<>();

      while(iter.hasNext()) {
         iter.next();
         values.add(iter.getValue(null));
      }

      // if * is used, it should never be used as a scalar value
      return values.toArray();
   }

   /**
    * Get the current group values.
    */
   private Object[] getGroupValues(String cellname) {
      Point loc = FormulaContext.getCellLocation();
      CalcCellContext context = table.getCellContext(loc.y, loc.x);

      if(context != null) {
         CalcCellContext.Group group = context.getGroup(cellname);

         if(group != null) {
            return group.getGroupValues(context.getValueIndex(group.getName()));
         }
      }

      return new Object[0];
   }

   /**
    * Get all values in the group (cellname) that is a child of the current
    * cell context.
    */
   private Object[] getChildGroupValues(String cellname) {
      Point loc = FormulaContext.getCellLocation();
      CalcCellContext context = table.getCellContext(loc.y, loc.x);
      CalcCellIterator iter = new CalcCellIterator(table, cellname);
      ArrayList<Object> values = new ArrayList<>();

      while(iter.hasNext()) {
         if(isChildGroup(context, iter.next())) {
            values.add(iter.getValue(null));
         }
      }

      // if * is used, it should never be used as a scalar value
      return values.toArray();
   }

   /**
    * Check if the cell (loc) is a child of the context.
    */
   private boolean isChildGroup(CalcCellContext context, Point loc) {
      CalcCellContext context2 = table.getCellContext(loc.y, loc.x);

      for(String gname : context2.getGroupNames()) {
         int pos = context2.getGroup(gname).getPosition();
         CalcCellContext.Group pgroup = context.getGroup(gname);

         if(pgroup != null && pgroup.getPosition() != pos) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get the value by group/condition specification.
    */
   private Object getBySpec(String id) throws Exception {
      CellRange range = CellRange.parse(cellname + "@" + id);
      Collection cells = range.getCells(table);

      return range.getCollectionValue(cells);
   }

   /**
    * No numeric indexing.
    */
   @Override
   public Object get(int index, Scriptable start) {
      try {
         if(index < 0) {
            return getByPosition(index, true);
         }

         return getByPosition(index, false);
      }
      catch(Exception ex) {
         LOG.warn("Failed to get indexed property: " + index, ex);
      }

      return null;
   }

   /**
    * The named references are readonly.
    */
   @Override
   public void put(String id, Scriptable start, Object value) {
      // values can't be set in a crosstab cell formula scope
      LOG.error("Property can not be modified: {}", id);
   }

   /**
    * The named references are readonly.
    */
   @Override
   public void put(int index, Scriptable start, Object value) {
      LOG.error("Property can not be modified: {}", index);
   }

   /**
    * This function is called if the referenced is used without any indexing,
    * e.g. $name + 2
    */
   @Override
   public Object getDefaultValue(Class hint) {
      return unwrap();
   }

   /**
    * Get the group value of this cell by position.
    */
   private Object getByPosition(int idx, boolean relative) {
      Point loc = FormulaContext.getCellLocation();
      CalcCellContext context = table.getCellContext(loc.y, loc.x);

      if(context != null) {
         CalcCellContext.Group group = context.getGroup(cellname);

         if(group != null) {
            if(relative) {
               idx += group.getPosition();
            }

            return group.getValue(context, idx);
         }
      }

      // if not relative indexing, get the array and index as an array
      if(!relative) {
         Object val = unwrap();

         if(val != null) {
            Object[] arr = (Object[]) FormulaFunctions.toArray(val);

            if(idx < arr.length) {
               return arr[idx];
            }
         }
      }

      return null;
   }

   /**
    * Use the wrapper to allow $name to be used both as a scalar value, and
    * supports the $name[reference] syntax.
    */
   @Override
   public Object unwrap() {
      // if $name is referenced from a cell, it's most likely a parent cell
      // or a cell in the same group, we check the cell context here without
      // checking all possible choices here for efficiency
      Point loc = FormulaContext.getCellLocation();
      CalcCellContext context = table.getCellContext(loc.y, loc.x);

      if(context != null) {
         CalcCellContext.Group group = context.getGroup(cellname);

         // check for group value
         if(group != null) {
            return group.getValue(context);
         }

         // check for cell in the same group
         CalcCellMap cmap = table.getCalcCellMap();
         Point[] locs = cmap.getLocations(cellname, context);

         if(locs.length > 0) {
            if(locs.length == 1) {
               return table.getObject(locs[0].y, locs[0].x);
            }
            else {
               Object[] arr = new Object[locs.length];

               for(int i = 0; i < arr.length; i++) {
                  arr[i] = table.getObject(locs[i].y, locs[i].x);
               }

               return arr;
            }
         }
      }

      return get("", this);
   }

   @Override
   public Object[] getIds() {
      return new Object[0];
   }

   @Override
   public boolean hasInstance(Scriptable value) {
      return false;
   }

   private RuntimeCalcTableLens table;
   private String cellname; // the name in $name, not the current cell

   private static final Logger LOG =
      LoggerFactory.getLogger(CalcRef.class);
}
