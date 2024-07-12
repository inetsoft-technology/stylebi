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

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.filter.CalcFilter;
import inetsoft.report.filter.CrossFilter;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Select crosstab cells based on group spec.
 *
 * @version 8.0, 7/27/2005
 * @author InetSoft Technology Corp
 */
public class CrosstabGroupSelector extends RangeSelector {
   /**
    * Get a selector for the table and groups.
    */
   public static synchronized CrosstabGroupSelector getSelector(
      String colname, CrossFilter table, Map specs)
   {
      specs = new Object2ObjectOpenHashMap(specs);
      Iterator iter = specs.keySet().iterator();

      // remove wildcard spec
      while(iter.hasNext()) {
         Object key = iter.next();
         NamedCellRange.GroupSpec spec = (NamedCellRange.GroupSpec) specs.get(key);

         if("*".equals(spec.getValue()) && spec.isByValue()) {
            iter.remove();
         }
      }

      Vector key = new Vector();
      key.add(table);
      key.add(specs.keySet());

      CrosstabGroupSelector selector = (CrosstabGroupSelector)
      selectorcache.get(key);

      if(selector == null) {
         selector = new CrosstabGroupSelector(table, specs);
         selectorcache.put(key, selector);
      }

      selector.init(colname, specs);
      return selector;
   }

   private CrosstabGroupSelector(CrossFilter table, Map groupspecs) {
      this.crosstab = table;
      this.groupspecs = groupspecs;
      this.groups = new ArrayList<String>(groupspecs.keySet());

      // if group spec is empty, all cells are eligible
      if(groups.size() > 0) {
         // create a mapping for group tuples to cell list
         for(int r = 0; table.moreRows(r); r++) {
            for(int c = 0; c < table.getColCount(); c++) {
               Map values = table.getKeyValuePairs(r, c, null);
               TupleValues tupleValues = new TupleValues();

               for(String group : groups) {
                  Object val = values.get(group);

                  // optimization, only need to check for date formatted group value if the
                  // group doesn't existing in the key-value pair.
                  if(val == null) {
                     val = values.get(getGroupValue(values, group));
                  }

                  tupleValues.add(val);
               }

               Tuple tuple = new Tuple(tupleValues.toArray());
               Collection<Point> cells = groupmap.get(tuple);

               if(cells == null) {
                  // (r, c) are permutations and will not repeat, so we use a list
                  // instead of set for efficiency.
                  groupmap.put(tuple, cells = new ArrayList<>(table.getDataColCount()));
               }

               cells.add(new Point(c, r));
            }
         }
      }
   }

   /**
    * Get the correct group value to fix the header in date format
    */
   private String getGroupValue(Map map, String group) {
      Set<String> keys = map.keySet();

      for(String key : keys) {
         if(isEqualsColumn(key, group)) {
            return key;
         }
      }

      return group;
   }

   /**
    * Initialize the selector from the group values.
    */
   private void init(String colname, Map specs) {
      this.colname = colname;
      this.groupspecs = specs;

      TupleValues tupleValues = new TupleValues();

      for(String gname : groups) {
         NamedCellRange.GroupSpec spec = (NamedCellRange.GroupSpec) groupspecs.get(gname);

         if(!spec.isByValue()) {
            LOG.warn(
               "Hint: if you are trying to specify a range based on value, " +
               "quote the number with single quotes so it's not treated as a " +
               "group position.");
            throw new RuntimeException(
               "Group positional reference not allowed in table reference: " + this);
         }

         tupleValues.add(spec.getValue());
      }

      Tuple tuple = new Tuple(tupleValues.toArray());
      header = false;

      for(int i = 0; i < crosstab.getRowHeaderCount(); i++) {
         if(Tool.equals(colname, crosstab.getRowHeader(i))) {
            header = true;
            break;
         }
      }

      if(!header) {
         for(int i = 0; i < crosstab.getColHeaderCount(); i++) {
            if(Tool.equals(colname, crosstab.getColHeader(i))) {
               header = true;
            }
         }
      }

      Collection<Point> cells = groupmap.get(tuple);
      // currcells is used for contains(), so we change it to a set for efficiency.
      currcells = cells != null ? new LinkedHashSet<>(cells) : null;
   }

   /**
    * Get the cells in the current group.
    */
   public Collection<Point> getGroupCells() {
      return currcells;
   }

   /**
    * Check if a cell matches selection criterias.
    * @return one of the flag defined in RowSelector.
    */
   @Override
   public int match(XTable lens, int row, int col) {
      if(groups.size() > 0 && (currcells == null || !currcells.contains(new Point(col, row)))) {
         return RangeProcessor.NO;
      }

      CrossFilter table = (CrossFilter) lens;
      TableDataDescriptor desc = table.getDescriptor();
      TableDataPath tpath = desc.getCellDataPath(row, col);

      // unknown cell
      if(tpath == null) {
         return RangeProcessor.NO;
      }

      // ignore 'Total' header cell
      if(header && tpath.getType() != TableDataPath.GROUP_HEADER) {
         return RangeProcessor.NO;
      }

      String[] path = tpath.getPath();

      // not at the deep enough level
      if(path.length < groupspecs.size() + 1) {
         return RangeProcessor.NO;
      }

      String colHeader = path[path.length - 1];

      // wrong aggregate column
      if(!isEqualsColumn(colHeader, colname) &&
         !isEqualsColumn(getColumName2(table, colname), colHeader))
      {
         return RangeProcessor.NO;
      }

      for(String gname : groups) {
         boolean inpath = false;

         // check if the specified group is on the path
         for(int i = 0; i < path.length; i++) {
            if(isEqualsColumn(path[i], gname)) {
               inpath = true;
               break;
            }
         }

         if(!inpath) {
            return RangeProcessor.NO;
         }
      }

      return RangeProcessor.YES;
   }

   /**
    * When crosstab query not pushdown aggregates, then the data header in tablelens not
    * containd the formula name, but the colname contains the formula name, so if colname
    * not matched the header name, then look for the corresponding header for the colname.
    *
    * @param table the crossfilter.
    * @param colname the column name of the binding.
    */
   private String getColumName2(CrossFilter table, String colname) {
      if(!(table instanceof CalcFilter)) {
         return colname;
      }

      List<String>  measureHeaders = ((CalcFilter) table).getMeasureHeaders();

      if(measureHeaders == null || measureHeaders.indexOf(colname) == -1) {
         return colname;
      }

      int idx = measureHeaders.indexOf(colname);

      return idx != -1 ? table.getDataHeader(idx) : colname;
   }

   /**
    * check the column name and path are equal.
    */
   private static boolean isEqualsColumn(String path, String colname) {
      if(Objects.equals(path, colname)) {
         return true;
      }
      //For the header in the date time format
      else if(path != null && path.startsWith(colname)) {
         try {
            Date date = CoreTool.parseDateTime(path);

            if(Tool.equals(colname, AssetUtil.format(date))) {
               return true;
            }
         }
         catch(Exception ex) {
            // ignore
         }
      }

      return false;
   }

   private Map groupspecs;
   private String colname;
   private boolean header;
   private List<String> groups;
   private Collection<Point> currcells;
   private CrossFilter crosstab;
   private Map<Tuple, Collection<Point>> groupmap = new HashMap<>();

   // Vector of [XTable, Set] -> CrosstabGroupSelector
   private static DataCache selectorcache = new DataCache(20, 60000);
   private static final Logger LOG =
      LoggerFactory.getLogger(CrosstabGroupSelector.class);
}
