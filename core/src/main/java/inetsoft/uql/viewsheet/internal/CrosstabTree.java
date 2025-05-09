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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.*;
import inetsoft.report.composition.execution.VSCubeTableLens;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.filter.CrossTabCubeFilter;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.internal.table.TableTool;
import inetsoft.uql.*;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.xmla.*;
import inetsoft.util.*;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class captures the hierarchy expand/collapse status.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public class CrosstabTree implements XMLSerializable, Cloneable, Serializable {
   public CrosstabTree() {
   }

   /**
    * Update and sync the tree states with the current binding.
    * This method must be called before this CrosstabTree is used.
    */
   public void updateHierarchy(VSCrosstabInfo cinfo, XCube cube, String cubeType) {
      this.cinfo = cinfo;
      this.cubeType = cubeType;
      DataRef[] rows = cinfo.getPeriodRuntimeRowHeaders();
      DataRef[] cols = cinfo.getRuntimeColHeaders();
      DataRef[][] arrs = {rows, cols};
      Map<String,String> oparents = parents;

      // only clear child map if hierarchy changed. otherwise the map needs to keep track
      // of dimensions while drilling up/down.
      if(!Objects.equals(ocube, cube)) {
         childRefs = new HashMap<>();
      }

      parents = new HashMap<>();
      ocube = cube;

      // build to child->parent mapping
      for(DataRef[] arr : arrs) {
         for(int i = 0; i < arr.length; i++) {
            VSDimensionRef dim = (VSDimensionRef) arr[i];
            VSDimensionRef child0 = getChildRef(dim, arr, cube, false);
            VSDimensionRef lastRef = VSUtil.getLastDrillLevelRef((VSDimensionRef) arr[i], cube);

            if(child0 != null) {
               String cname = getFieldName(child0);
               String pname = getFieldName(dim);

               if(cname != null && !cname.equals(pname)) {
                  updateChildRef(pname, child0);
               }
            }
            else if(lastRef != null) {
               updateChildRef(lastRef.getFullName(), dim);
            }
            //root node
            else {
               updateChildRef(getHierarchyRootKey(dim), dim);
            }

            VSDimensionRef child = getChildRef(dim, arr, cube, true);

            if(child == null) {
               Optional<VSDimensionRef> childOptional =
                  Arrays.stream(arr)
                     .filter(VSDimensionRef.class::isInstance)
                     .map(VSDimensionRef.class::cast)
                     .filter(ref ->
                        VSUtil.getDimRanking(ref, cube, dim) > VSUtil.getDimRanking(dim, cube, null))
                     .sorted(Comparator.comparingInt(ref -> VSUtil.getDimRanking(ref, cube, dim)))
                     .findFirst();

               if(childOptional.isPresent()) {
                  child = childOptional.get();
               }
            }

            if(child != null) {
               String cname = getFieldName(child);
               String pname = getFieldName(dim);
               String opname = oparents.get(cname);

               if(cname != null && !cname.equals(pname)) {
                  parents.put(cname, pname);
               }

               // hierarchy changed, clear the parent expanded status
               if(opname != null && !opname.equals(pname)) {
                  expanded.remove(pname);
                  expanded.remove(opname);
               }
            }
         }
      }

      // remove non-existant data
      Iterator iter = expanded.keySet().iterator();

      while(iter.hasNext()) {
         Object key = iter.next();
         String path0 = getParents(key, parents, "");
         String path1 = getParents(key, oparents, "");

         if(!parents.containsValue(key) ||
            // if the hierarchy structure changed, the expanded paths are no longer valid
            !Tool.equals(path0, path1) && !path0.contains(path1))
         {
            iter.remove();
         }
      }

      colops = getDrillOps(cols, cube);
      rowops = getDrillOps(rows, cube);
   }

   public static VSDimensionRef getChildRef(VSDimensionRef pref, DataRef[] refs, XCube cube,
                                            boolean recursive)
   {
      VSDimensionRef child = findChild(pref, refs, cube, recursive);

      if(child != null) {
         // @by stephenwebster, For bug1428520708167
         // The current cinfo runtime header data refs have information about
         // named grouping. When seeking the child ref, we must copy the
         // current runtime info to the child ref which was created so that
         // the parent map is populated with the same value as retrieved from
         // the table in isParentsExpanded. Otherwise, that method returns
         // true always and the state of the crosstabtree is incorrect.
         if(pref != null && pref.getName().equals(child.getName())) {
            child.setGroupType(pref.getGroupType());
            child.setNamedGroupInfo(pref.getNamedGroupInfo());
         }
      }

      return child;
   }

   public void updateChildRef(String parentRef, VSDimensionRef childRef) {
      childRefs.put(NamedRangeRef.getBaseName(parentRef), childRef);
   }

   public VSDimensionRef getChildRef(String parent) {
      return childRefs.get(NamedRangeRef.getBaseName(parent));
   }

   public void removeChildRef(String parent) {
      childRefs.remove(NamedRangeRef.getBaseName(parent));
   }

   public Map<String, VSDimensionRef> getChildRefs() {
      return childRefs;
   }

   /**
    * Find the child level of a dim, directly or indirectly.
    */
   public static VSDimensionRef findChild(VSDimensionRef ref, DataRef[] refs, XCube cube) {
      return findChild(ref, refs, cube, true);
   }

   /**
    * Find the child level of a dim, directly or indirectly.
    */
   private static VSDimensionRef findChild(VSDimensionRef ref, DataRef[] refs, XCube cube,
                                           boolean recursive)
   {
      VSDimensionRef next = VSUtil.getNextLevelRef(ref, cube, true);
      VSDimensionRef child = findInRefs(refs, cube, next);

      // in pre-13.3 versions, getNextLevelRef calls VSUtil.getNextDateLevel instead of
      // getDrillDownDateLevel. with the change in the drill path, existing structure
      // (e.g. Month, DayOfMonth, HourOfDay) may no longer be considered in a hierarch,
      // since the drill path is now (Month, Day, Hour). the following checkes for the
      // previous hierarchy and so if the levels match that, they are still treated as a
      // hierarchy so the levels won't be all expended on open. (60839)
      if(child == null) {
         VSDimensionRef next2 = VSUtil.getNextLevelRef(ref, cube, false);
         child = findInRefs(refs, cube, next2);
      }

      if(child != null) {
         return child;
      }

      return recursive && next != null ? findChild(next, refs, cube, recursive) : null;
   }

   private static VSDimensionRef findInRefs(DataRef[] refs, XCube cube, VSDimensionRef child) {
      if(child != null) {
         String cubeType = (cube != null) ? cube.getType() : null;
         int index = indexOfField(refs, getFieldName(child, cubeType), cubeType);

         if(index >= 0) {
            return (VSDimensionRef) refs[index];
         }
      }

      return null;
   }

   /**
    * Get the path containing all parents.
    */
   private String getParents(Object key, Map parents, String path) {
      if(parents.containsKey(key)) {
         Object parent = parents.get(key);

         path = parent + "." + path;
         return getParents(parent, parents, path);
      }

      return path;
   }

   /**
    * Get the drill op for the dimensions.
    */
   private String[] getDrillOps(DataRef[] refs, XCube cube) {
      String[] ops = new String[refs.length];

      for(int i = 0; i < ops.length; i++) {
         ops[i] = VSUtil.getCrosstabDrillOp(cube, refs[i], refs);
      }

      return ops;
   }

   /**
    * Set the expanded status of a value (in a hierarchy).
    * @return true to add the child dimension when setting expanded to true,
    * and true to remove the child dimensions when setting expanded to false.
    */
   public boolean setExpanded(XTable table, int row, int col, boolean flag) {
      String field = getField(row, col);
      String path = getPath(table, row, col);
      Set set = expanded.get(field);

      if(flag) {
         if(set == null) {
            expanded.put(field, set = new HashSet());
         }

         if(cubeType != null && (path.equals("Total") || path.contains("[Total]"))) {
            return true;
         }

         set.add(path);
         return !parents.containsValue(field);
      }
      else {
         if(set != null) {
            set.remove(path);

            if(set.size() == 0) {
               expanded.remove(field);
            }

            removeChild(table, row, col);

            // if is meta table, return true directly, because of current time
            // is different in meta table, see bug1329379850489
            if(isDateParent(field, table)) {
               return true;
            }
         }

         return !expanded.containsKey(field);
      }
   }

   /**
    * Remove the nref's ancestor expanded path.
    */
   public void updateExpanded(DataRef nref, DataRef[] refs, XCube cube) {
      if(!(nref instanceof VSDimensionRef) || cube == null || expanded.isEmpty()) {
         return;
      }

      int level = getRefLevel((VSDimensionRef) nref, cube);

      for(DataRef ref : refs) {
         if(!(ref instanceof VSDimensionRef)) {
            continue;
         }

         String name = ref.getAttribute();

         if(name != null && !"".equals(name) &&
            getRefLevel((VSDimensionRef) ref, cube) < level &&
            expanded.containsKey(getFieldName(ref)))
         {
            expanded.remove(getFieldName(ref));
         }
      }
   }

   /**
    * Get the level of the ref.
    */
   private int getRefLevel(VSDimensionRef ref, XCube cube) {
      String name = ref.getGroupColumnValue();
      int dot = name.lastIndexOf('.');
      String dimname = (dot > 0) ? name.substring(0, dot) : "";
      String mbrname= name.substring(dot + 1);
      XDimension xdim = cube.getDimension(dimname);

      if(xdim == null) {
         xdim = VSUtil.findDimension(cube, mbrname);
      }

      if(xdim != null) {
         return VSUtil.getScope(VSUtil.getDimMemberName(name, xdim), xdim,
                                ref.getDateLevel());
      }

      return -1;
   }

   /**
    * Remove the child expanded path.
    */
   private void removeChild(XTable table, int row, int col) {
      String field = getField(row, col);
      String path = getPath(table, row, col);
      String childField = getNextField(field);

      if(childField == null) {
         return;
      }

      Set<String> set = expanded.get(childField);

      if(set == null) {
         return;
      }

      DataRef[] rows = cinfo.getPeriodRuntimeRowHeaders();
      DataRef[] cols = cinfo.getRuntimeColHeaders();

      if(col < rows.length && col >= 0) {
         int c = indexOfField(rows, childField);

         if(c < 0 || c >= rows.length) {
            return;
         }

         for(int r = row - 1; r > 0; r--) {
            if(!Tool.equals(getPath(table, r, col), path)) {
               break;
            }

            removeChildPath(table, r, c, set);
         }

         while(table.moreRows(row)) {
            if(!Tool.equals(getPath(table, row, col), path)) {
               break;
            }

            removeChildPath(table, row, c, set);
            row++;
         }
      }
      else if(row < cols.length && row >= 0) {
         int r = indexOfField(cols, childField);

         if(r < 0 || r >= cols.length) {
            return;
         }

         for(int c = col - 1; c > 0; c--) {
            if(!Tool.equals(getPath(table, row, c), path)) {
               break;
            }

            removeChildPath(table, r, c, set);
         }

         for(int c = col; c < table.getColCount(); c++) {
            if(!Tool.equals(getPath(table, row, c), path)) {
               break;
            }

            removeChildPath(table, r, c, set);
         }
      }

      if(set.size() == 0) {
         expanded.remove(childField);
      }
   }

   private void removeChildPath(XTable table, int row, int col, Set<String> set) {
      String childPath = getPath(table, row, col);

      if(set.contains(childPath)) {
         set.remove(childPath);
         removeChild(table, row, col);
      }
   }

   /**
    * Get field of next level.
    */
   public String getNextField(String field) {
      for(String child : parents.keySet()) {
         if(parents.get(child).equals(field)) {
            return child;
         }
      }

      return null;
   }

   /**
    * Get parent field of child.
    */
   public String getParentField(String child) {
      return parents.get(child);
   }

   /**
    * Add all row/col to the expanded path.
    */
   private void addAllPath(XTable table, int row, int col, int hrows, int hcols) {
      String field = getField(row, col);
      Set set = expanded.get(field);
      String total = Catalog.getCatalog().getString("Total");

      if(set == null) {
         expanded.put(field, set = new HashSet());
         DataRef[] rows = cinfo.getPeriodRuntimeRowHeaders();
         DataRef[] cols = cinfo.getRuntimeColHeaders();

         if(col < rows.length) {
            for(int r = hrows; r < table.getRowCount(); r++) {
               String path = getPath(table, r, col, true);

               if(isValidPath(path) && !"".equals(path) && !set.contains(path)) {
                  set.add(path);
               }
            }
         }
         else if(row < cols.length) {
            for(int c = hcols; c < table.getColCount(); c++) {
               String path = getPath(table, row, c, true);

               if(isValidPath(path) && !"".equals(path) && !set.contains(path)) {
                  set.add(path);
               }
            }
         }
      }
      else {
         String path = getPath(table, row, col);

         if(!set.contains(path) && !total.equals(path) && !path.contains("[" + total + "]")) {
            set.add(path);
         }
      }
   }

   private boolean isValidPath(String path) {
      String total = Catalog.getCatalog().getString("Total");
      String grandTotal = Catalog.getCatalog().getString("Grand Total");

      return path != null && !total.equals(path) && !path.contains("[" + total + "]") &&
         !grandTotal.equals(path) && !path.contains("[" + grandTotal + "]");
   }
   /**
    * Check if the cell is expanded.
    */
   private boolean isExpanded(XTable table, int row, int col) {
      String field = getField(row, col);
      Set set = expanded.get(field);
      DataRef[] rows = cinfo.getPeriodRuntimeRowHeaders();
      DataRef[] cols = cinfo.getRuntimeColHeaders();
      String cfield = getNextField(field);
      Object val = null;

      if(cfield != null) {
         // use crosstab to avoid using the value from TextFormat
	      TableLens table2 = Util.getNestedTable(table, CrossTabFilter.class);

         if(col < rows.length && indexOfField(rows, cfield) >= 0) {
            int idx = indexOfField(rows, cfield);
            TableDataPath path = table2.getDescriptor().getCellDataPath(row, idx);

            // for mergeSpan=false and row-total displayed, the value for 'Total' label
            // should not be treated as a real value for this purpose. (43649)
            if(path != null && path.getType() != TableDataPath.GROUP_HEADER) {
               val = null;
            }
            else {
               val = table2.getObject(row, idx);
            }
         }
         else if(row < cols.length && indexOfField(cols, cfield) >= 0) {
            int idx = indexOfField(cols, cfield);
            TableDataPath path = table2.getDescriptor().getCellDataPath(idx, col);

            if(path != null && path.getType() != TableDataPath.GROUP_HEADER) {
               val = null;
            }
            else {
               val = table2.getObject(indexOfField(cols, cfield), col);
            }
         }
      }

      return  set == null || set.contains(getPath(table, row, col)) ||
         set.contains(getPath(table, row, col, false, false)) ||
         (val != null && !"".equals(val) && val != COLLAPSED);
   }

   /**
    * Check if all parents of the cell are expanded.
    */
   public boolean isParentsExpanded(XTable table, int r, int c) {
      String field = table.getObject(0, c) + "";

      for(String p = parents.get(field); p != null; p = parents.get(p)) {
         int col = field2Col(table, p);

         if(col < 0) {
            break;
         }

         if(!containsParent(p, table, r, col)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if all parents of the cell are expanded.
    */
   private boolean containsParent(String p, XTable table, int r, int c) {
      Set set = expanded.get(p);

      if(set != null) {
         // if is meta table, don't check parent, because of current time is
         // different in meta table, see bug1329379850489
         if(!set.isEmpty() && isDateParent(p, table)) {
            return true;
         }
         else if(!set.contains(getPath2(table, r, c)) &&
            !set.contains(getPath2(table, r, c, false)))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Check the parent whether is date type for meta table.
    */
   private boolean isDateParent(String p, XTable table) {
      TableLens meta = Util.getNestedTable(table, XNodeMetaTable.class);

      if(meta != null) {
         String top = getTopParent(p);
         int topcol = field2Col(table, top);
         Object parent = topcol >= 0 ? meta.getObject(1, topcol) : null;

         if(parent != null && Tool.isDateClass(parent.getClass())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get top parent.
    */
   private String getTopParent(String field) {
      String p = parents.get(field);

      while(p != null) {
         String parent = parents.get(p);

         if(parent != null) {
            p = parent;
         }
         else {
            return p;
         }
      }

      return p == null ? field : p;
   }

   /**
    * Get the dimension with expanded children.
    * @return field name to the set of paths, e.g. [State].[Piscataway]
    * !!!!!! this is an internal data structure and should not be used in normal circumstances. !!!
    */
   public Map<String,Set<String>> getExpandedPaths() {
      return expanded;
   }

   /**
    * This is added for cube query, to avoid add "Total" as descendant.
    */
   public Map<String,Set<String>> getCubeExpandedPaths() {
      if(expanded == null || expanded.isEmpty() ||
         !cinfo.isColTotalVisible() && !cinfo.isRowTotalVisible())
      {
         return expanded;
      }

      Map<String, Set<String>> cubeExpanded = new HashMap<>();
      Set<Map.Entry<String, Set<String>>> entrySet = expanded.entrySet();

      for(Map.Entry<String, Set<String>> entry : entrySet) {
         String field = entry.getKey();
         Set<String> set = entry.getValue();

         if(!entry.getValue().isEmpty()) {
            set = set.stream()
               .filter(v -> !Catalog.getCatalog().getString("Total").equals(v))
               .collect(Collectors.toSet());
         }

         cubeExpanded.put(entry.getKey(), set);
      }

      return cubeExpanded;
   }

   /**
    * Check if the parent cell is expanded.
    * @param column child column name.
    * @param pval parent cell value.
    */
   public boolean isParentExpanded(String column, Object pval) {
      Set set = expanded.get(parents.get(column));
      return set != null && set.contains(pval);
   }

   /**
    * Remove drill (expanded) for the column.
    */
   public void removeDrill(String field) {
      expanded.remove(field);
   }

   /**
    * Check if any cell is expanded in the column.
    */
   public boolean isDrilled(String field) {
      return expanded.containsKey(field);
   }

   /**
    * Check if any cell is extended in the crosstab.
    */
   public boolean isDrilled() {
      return !expanded.isEmpty();
   }

   /**
    * Clear all expanded states.
    */
   public void clearDrills() {
      expanded.clear();
   }

   /**
    * Check if the column contains both expanded and collapsed members.
    */
   private boolean isPartial(XTable table, int row, int col) {
      String field = getField(row, col);
      Set set = expanded.get(field);

      return set != null;
   }

   /**
    * Get the drill operation for the cell.
    */
   public String getDrillOp(final XTable table, final int row, final int col) {
      if(cinfo == null) {
         return "";
      }

      DataRef[] rows = cinfo.getPeriodRuntimeRowHeaders();
      DataRef[] cols = cinfo.getRuntimeColHeaders();
      DataRef[] aggrs = cinfo.getRuntimeAggregates();
      TableDataPath path = table.getDescriptor().getCellDataPath(row, col);
      int hrows = cols.length;
      int hcols = rows.length;
      String drillop = "";

      if(path == null || path.getType() != TableDataPath.GROUP_HEADER) {
         return "";
      }

      if(aggrs.length > 1) {
         if(cinfo.isSummarySideBySide()) {
            hrows++;
         }
         else {
            hcols++;
         }
      }

      hrows = Math.max(1, hrows);
      hcols = Math.max(1, hcols);
      DataRef dref = null;

      CrossTabFilter filter = Util.getCrosstab(table);
      boolean isFilledDate = filter != null && filter.isFilledDate(row, col);

      if(col < hcols && row < hrows) {
         // header cells, no drill
      }
      else if(col < rows.length) {
         if(row >= cols.length && col < rowops.length && !isFilledDate) {
            drillop = rowops[col];
            dref = rows[col];
         }
      }
      else if(row < cols.length) {
         if(col >= rows.length && row < colops.length && !isFilledDate) {
            drillop = colops[row];
            dref = cols[row];
         }
      }

      if(drillop.length() > 0) {
         if(isPartial(table, row, col)) {
            drillop = isExpanded(table, row, col) ? "-" : "+";
         }

         if("-".equals(drillop)) {
            addAllPath(table, row, col, hrows, hcols);
         }

         Object val = table.getObject(row, col);
         String others = Catalog.getCatalog().getString("Others");

         // check if parent is expanded
         if(val == CrosstabTree.COLLAPSED) {
            if(cubeType != null && !XCube.MODEL.equals(cubeType)) {
               drillop = "";
            }
            else {
               String op2 = "";
               String field = parents.get(getField(row, col));

               if(field != null) {
                  if(col < rows.length && col > 0 && indexOfField(rows, field) >= 0) {
                     int col2 = indexOfField(rows, field);

                     if(col2 != col) {
                        op2 = getDrillOp(table, row, col2);
                     }
                  }
                  else if(row < cols.length && row > 0 && indexOfField(cols, field) >= 0) {
                     int row2 = indexOfField(cols, field);

                     if(row2 != row) {
                        op2 = getDrillOp(table, row2, col);
                     }
                  }
               }

               // parent not expanded, this cell is not a real value
               if(!op2.equals("-")) {
                  drillop = "";
               }
            }
         }
         // drill on Others not supported
         else if(dref instanceof VSDimensionRef &&
                 ((VSDimensionRef) dref).isGroupOthers() &&
                 others.equals(val))
         {
            drillop = "";
         }
      }

      return drillop;
   }

   /**
    * Get the corresponding field name for a cell in the header region.
    */
   private String getField(int row, int col) {
      DataRef[] rows = cinfo.getPeriodRuntimeRowHeaders();
      DataRef[] cols = cinfo.getRuntimeColHeaders();

      if(col < rows.length) {
         return getFieldName(rows[col]);
      }
      else if(row < cols.length) {
         return getFieldName(cols[row]);
      }

      return null;
   }

   private String getUniqueName(XTable table, int row, int col) {
      Object val = table.getObject(row, col);

      if(val == null) {
         return null;
      }

      if(val instanceof MemberObject) {
         return ((MemberObject) val).getUName();
      }

      XTable base = table;
      table = Util.getCrosstab(table);

      if(table == null) {
         Object cubeData = getCubeData(base, row, col);

         if(cubeData != null) {
            return cubeData + "";
         }

         return null;
      }

      row = TableTool.getBaseRowIndex((TableLens) base, (TableLens) table, row);
      col = TableTool.getBaseColIndex((TableLens) base, (TableLens) table, col);

      if(row < 0 || col < 0) {
         return null;
      }

      val = table.getObject(row, col);

      if(val instanceof MemberObject) {
         return ((MemberObject) val).getUName();
      }

      String cubeData = getCubeData(table, row, col);

      if(cubeData != null) {
         return cubeData;
      }

      return null;
   }

   private String getCubeData(XTable base, int row, int col) {
      // using getData() to get unique value, e.g. 2001.Qtr1 instead of Qtr1
      DataTableLens cube = (CrossTabCubeFilter)
         Util.getNestedTable(base, CrossTabCubeFilter.class);

      if(cube == null) {
         cube = (VSCubeTableLens) Util.getNestedTable(base, VSCubeTableLens.class);
      }

      if(cube != null && cubeType != null && !XCube.MODEL.equals(cubeType)) {
         if(cube instanceof VSCubeTableLens && row < base.getHeaderRowCount()) {
            int temp = row;
            row = col;
            col = temp + base.getHeaderColCount();
            col = Math.min(col, cube.getColCount() - 1);
         }

         Object obj = cube.getData(row, col);

         if(obj instanceof CubeDate) {
            MemberObject mobj = ((CubeDate) obj).getMemberObject();
            return mobj.getUName();
         }

         return obj + "";
      }

      return null;
   }

   /**
    * Get the cell value as a string.
    */
   private String getString(XTable table, int row, int col, int refType) {
      Object val = (table instanceof DataTableLens)
            ? ((DataTableLens) table).getData(row, col)
            : table.getObject(row, col);

      // null is treated as collapsed cell in path and in isExpanded().
      if(val == COLLAPSED) {
         return null;
      }

      // avoid differences between java date and sql timestamp
      if(val instanceof java.util.Date && !(val instanceof java.sql.Timestamp)) {
         val = new java.sql.Timestamp(((Date) val).getTime());
      }

      if(XCube.MODEL.equals(cubeType)) {
         val = VSCubeTableLens.getDisplayValue(val, refType);
      }

      String str = val != null ? val.toString() : null;
      // handle null as a valid value (different from a collapsed cell).
      return StringUtils.isEmpty(str) ? "_^empty^_" : str;
   }

   /**
    * Get the tuple to uniquely identify a value in the hierarchy.
    * @param table this must be a crosstab table.
    */
   private String getPath(XTable table, int row, int col) {
      return getPath(table, row, col, false);
   }

   /**
    * Get the tuple to uniquely identify a value in the hierarchy.
    * @param table this must be a crosstab table.
    */
   public String getPath(XTable table, int row, int col, boolean ignoreGrandTotal) {
      return getPath(table, row, col, ignoreGrandTotal, true);
   }

   /**
    * Get the tuple to uniquely identify a value in the hierarchy.
    * @param table this must be a crosstab table.
    */
   public String getPath(XTable table, int row, int col, boolean ignoreGrandTotal,
                         boolean forceContainsParents)
   {
      String uName = getUniqueName(table, row, col);

      if(uName != null) {
         return uName;
      }

      XTable base = table;
      CrossTabFilter crossTabFilter = Util.getCrosstab(table);
      table = crossTabFilter;
      row = TableTool.getBaseRowIndex((TableLens) base, (TableLens) table, row);
      col = TableTool.getBaseColIndex((TableLens) base, (TableLens) table, col);
      DataRef[] rows = cinfo.getPeriodRuntimeRowHeaders();
      DataRef[] cols = cinfo.getRuntimeColHeaders();
      DataRef[] refs = (col < rows.length) ? rows : cols;
      int idx = (refs == rows) ? col : row;

      if(idx < 0) {
         return null;
      }

      DataRef ref = refs[idx];
      String field = getFieldName(refs[idx]);
      String val = getString(table, row, col, refs[idx].getRefType());
      String path = StringUtils.isEmpty(val) ? val : "[" + val + "]";
      String grandTotalLabel = null;

      if(crossTabFilter != null) {
         grandTotalLabel = crossTabFilter.getGrandTotalLabel();
      }

      if(ignoreGrandTotal && val != null && val.equals(grandTotalLabel)) {
         path = null;
      }

      // fix bc issue
      if(!forceContainsParents && isNoneDatePartDateGroup(ref)) {
         return path;
      }

      for(String parent = parents.get(field); parent != null;
          parent = parents.get(parent))
      {
         int pidx = indexOfField(refs, parent);

         if(pidx < 0) {
            break;
         }

         int r, c;

         if(refs == rows) {
            r = row;
            c = pidx;
         }
         else {
            r = pidx;
            c = col;
         }

         val = getString(table, r, c, refs[pidx].getRefType());
         path = StringUtils.isEmpty(val) ? path : "[" + val + "]." + path;

         if(ignoreGrandTotal && val != null && val.equals(grandTotalLabel)) {
            path = null;
         }
      }

      return path;
   }

   private boolean isNoneDatePartDateGroup(DataRef ref) {
      return ref instanceof VSDimensionRef && XSchema.isDateType(ref.getDataType()) &&
         (((VSDimensionRef) ref).getDateLevel() & DateRangeRef.PART_DATE_GROUP) == 0;
   }

   /**
    * Get the path from a flat table.
    * @param table this is a non-crosstab table.
    */
   private String getPath2(XTable table, int row, int col) {
      return getPath2(table, row, col, true);
   }

   /**
    * Get the path from a flat table.
    * @param table this is a non-crosstab table.
    */
   private String getPath2(XTable table, int row, int col, boolean forceContainsParents) {
      String uName = getUniqueName(table, row, col);

      if(uName != null) {
         return uName;
      }

      String field = table.getObject(0, col) + "";
      DataRef ref = findRef(field);
      Object member = getString(table, row, col, ref == null ? -1 : ref.getRefType());
      String path = "[" + member + "]";

      // fix bc issue
      if(!forceContainsParents && isNoneDatePartDateGroup(ref)) {
         return path;
      }

      for(String p = parents.get(field); p != null; p = parents.get(p)) {
         int pidx = field2Col(table, p);
         String pfield = table.getObject(0, pidx) + "";
         DataRef pref = findRef(pfield);

         if(pidx < 0) {
            break;
         }

         Object val = getString(table, row, pidx, pref == null ? -1 : pref.getRefType());
         path = "[" + val + "]." + path;
      }

      return path;
   }

   private DataRef findRef(String fullName) {
      DataRef[] rows = cinfo.getPeriodRuntimeRowHeaders();
      DataRef[] cols = cinfo.getRuntimeColHeaders();
      DataRef[][] arrs = {rows, cols};

      for(int i = 0; i < arrs.length; i++) {
         DataRef[] arr = arrs[i];

         for(int j = 0; arr != null && j < arr.length; j++) {
            if(arr[j] instanceof VSDimensionRef &&
               Tool.equals(((VSDimensionRef) arr[j]).getFullName(), fullName))
            {
               return arr[j];
            }
         }
      }

      return null;
   }

   /**
    * Get the field name from the data ref.
    */
   public String getFieldName(DataRef ref) {
      return getFieldName(ref, cubeType);
   }

   /**
    * Get the field name from the data ref.
    */
   public static String getFieldName(DataRef ref, String cubeType) {
      if(cubeType != null && !cubeType.isEmpty() && !XCube.MODEL.equals(cubeType) &&
         ref instanceof VSDimensionRef)
      {
         VSDimensionRef dim = (VSDimensionRef) ref;
         DataRef ref0 = dim.getDataRef();
         return ref0 == null ? null : MDXHelper.getFieldName(ref0);
      }

      return ((VSDataRef) ref).getFullName();
   }

   /**
    * Find the index of the field.
    */
   private int indexOfField(DataRef[] refs, String field) {
      return indexOfField(refs, field, cubeType);
   }

   /**
    * Find the index of the field.
    */
   private static int indexOfField(DataRef[] refs, String field, String cubeType) {
      for(int i = 0; i < refs.length; i++) {
         String fieldName = getFieldName(refs[i], cubeType);

         if(fieldName.equals(field) || NamedRangeRef.getBaseName(fieldName).equals(field)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<CrosstabTree>");

      for(String field : expanded.keySet()) {
         writer.println("<expanded field=\"" + field + "\">");

         for(Object path : expanded.get(field)) {
            writer.println("<path><![CDATA[" + path + "]]></path>");
         }

         writer.println("</expanded>");
      }

      writer.println("</CrosstabTree>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      NodeList enodes = Tool.getChildNodesByTagName(tag, "expanded");

      for(int i = 0; i < enodes.getLength(); i++) {
         Element enode = (Element) enodes.item(i);
         String field = Tool.getAttribute(enode, "field");
         NodeList pnodes = Tool.getChildNodesByTagName(enode, "path");
         Set set = new HashSet();

         for(int j = 0; j < pnodes.getLength(); j++) {
            set.add(Tool.getValue(pnodes.item(j)));
         }

         expanded.put(field, set);
      }
   }

   /**
    * Get the column index of a field.
    */
   private final int field2Col(XTable table, String field) {
      if(idxtbl != table) {
         idxtbl = table;

         for(int i = 0; i < table.getColCount(); i++) {
            fieldidx.put(table.getObject(0, i), i);
         }
      }

      Integer obj = fieldidx.get(field);
      return (obj == null) ? -1 : obj;
   }

   /**
    * Making a copy of this tree.
    */
   @Override
   public Object clone() {
      try {
         CrosstabTree ctree = (CrosstabTree) super.clone();

         ctree.parents = Tool.deepCloneMap(parents);
         ctree.expanded = Tool.deepCloneMap(expanded);
         ctree.fieldidx = Tool.deepCloneMap(fieldidx);

         return ctree;
      }
      catch(CloneNotSupportedException ex) {
         // impossible
      }

      return null;
   }

   public static String getHierarchyRootKey(VSDimensionRef ref) {
      return "root_" + NamedRangeRef.getBaseName(ref.getFullName());
   }

   // get the name used to set the drill filter condition in DrillFilterAssembly
   public static String getDrillFilterName(VSDimensionRef ref, XCube cube, boolean nextLevel) {
      VSDimensionRef nextRef = VSUtil.getNextLevelRef(ref, cube, true);
      String name = NamedRangeRef.getBaseName(nextRef != null && nextLevel ? nextRef.getFullName()
                                   : ref.getFullName());

      return nextRef == null ? name + ".self" : name;
   }

   public boolean hasDrillUpOperation() {
      if(rowops != null) {
         for(String op : rowops) {
            if(Tool.equals(ChartConstants.DRILL_UP_OP, op)) {
               return true;
            }
         }
      }

      if(colops != null) {
         for(String op : colops) {
            if(Tool.equals(ChartConstants.DRILL_UP_OP, op)) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean hasDrillDownOperation() {
      if(rowops != null) {
         for(String op : rowops) {
            if(Tool.equals(ChartConstants.DRILL_DOWN_OP, op)) {
               return true;
            }
         }
      }

      if(colops != null) {
         for(String op : colops) {
            if(Tool.equals(ChartConstants.DRILL_DOWN_OP, op)) {
               return true;
            }
         }
      }

      return false;
   }

   public static final String COLLAPSED = new String("");

   private VSCrosstabInfo cinfo;
   private String cubeType;
   // child level -> parent level
   private Map<String,String> parents = new HashMap<>();
   // parent ref -> child ref
   private Map<String, VSDimensionRef> childRefs = new HashMap<>();
   // field -> expanded values (path)
   private Map<String, Set<String>> expanded = new HashMap<>();
   // field -> column index
   private Map<Object,Integer> fieldidx = new HashMap<>();
   // XTable for the fieldidx
   private Object idxtbl;
   private String[] colops;
   private String[] rowops;
   private XCube ocube; // cube used to update hierarchy
}
