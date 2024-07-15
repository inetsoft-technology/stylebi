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
package inetsoft.report.composition.execution;

import inetsoft.report.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.report.lens.DistinctTableLens;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.SelectionVSAssembly;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.xmla.MemberObject;
import inetsoft.uql.xmla.XMLAUtil;
import inetsoft.util.*;
import inetsoft.util.swap.XSwappableObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class tracks table information such as column items and association.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class RealtimeTableMetaData extends TableMetaData {
   /**
    * Constructor.
    */
   public RealtimeTableMetaData(String name) {
      super(name);
   }

   /**
    * Get a distinct list of column values.
    * @param column column name.
    * @return a list of values or null if the column is not in this table.
    */
   private List<Object> getColumnValues(String column) {
      ColumnMetaData col = colmap.get(column);
      return (col == null) ? null : col.getValues();
   }

   /**
    * Get a distinct column(s) table from the meta data.
    */
   @Override
   public XTable getColumnTable(String vname, DataRef[] refs) {
      lock.readLock().lock();

      try {
         String[] columns = getColumns(refs);
         String[][] names = getColNames(refs);

         if(columns.length == 1) {
            ColumnTable lens = (ColumnTable) getColumnTable0(columns, names);
            Comparator comp = Util.getColumnComparator(table, refs[0]);
            lens.setComparator(comp);
            int refType = refs[0].getRefType();

            if((refType & DataRef.CUBE_TIME_DIMENSION) != DataRef.CUBE_TIME_DIMENSION) {
               return lens;
            }

            SortFilter sfilter = new SortFilter(lens, new int[]{0});
            sfilter.setComparer(0, new DimensionComparer(refType, comp));

            return sfilter;
         }

         ColumnsTable table = new ColumnsTable(columns);
         int[] dimTypes = new int[refs.length];
         Comparator[] comps = new Comparator[refs.length];

         for(int i = 0; i < refs.length; i++) {
            if((refs[i].getRefType() & DataRef.CUBE) == 0) {
               dimTypes = null;
               comps = null;
               break;
            }

            dimTypes[i] = refs[i].getRefType();
            comps[i] = Util.getColumnComparator(this.table, refs[i]);
            table.setComparator(i, comps[i]);
         }

         return PostProcessor.distinct(table, null, dimTypes, comps);
      }
      finally {
         lock.readLock().unlock();
      }
   }

   /**
    * Get a distinct column(s) table from the meta data.
    */
   @Override
   public XTable getColumnTable(String vname, String[] columns) {
      lock.readLock().lock();

      try {
         return getColumnTable0(columns, null);
      }
      finally {
         lock.readLock().unlock();
      }
   }

   /**
    * Get a distinct column(s) table from the meta data.
    */
   private XTable getColumnTable0(String[] columns, String[][] cols) {
      if(columns.length == 1) {
         List<Object> values = getColumnValues(columns[0]);

         if(values == null && cols != null) {
            values = getColumnValues(cols[0][0]);
         }

         if(values == null && cols != null) {
            values = getColumnValues(cols[0][1]);
         }

         if(values != null) {
            ColumnTable columnTable = new ColumnTable(columns[0], values);
            int idx = columns[0].lastIndexOf(".");

            if(idx >= 0) {
               AttributeRef ref = new AttributeRef(
                  columns[0].substring(0, idx), columns[0].substring(idx + 1));
               columnTable.setComparator(Util.getColumnComparator(table, ref));
            }

            return columnTable;
         }
      }

      TableLens table = new ColumnsTable(columns);
      // the output must be sorted. We rely on DistinctTableLens sorting the
      // rows if there are more than one column. Explicit sorting must be added
      // if the DistinctTableLens implementation is changed.
      return new DistinctTableLens(table);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public SelectionSet getAssociatedValues(String vname, Map<String, Collection<Object>> selections,
                                          DataRef[] refs, String measure,
                                          SelectionMeasureAggregation measureAggregation)
   {
      lock.readLock().lock();

      try {
         BitSet[] bitmaps = new BitSet[colmap.size()];
         ColumnMetaData[] colmetas = new ColumnMetaData[refs.length];
         int[] tcols = new int[refs.length];
         boolean cube = refs != null && refs.length > 0 &&
            (refs[0].getRefType() & DataRef.CUBE) == DataRef.CUBE;
         SelectionSet set = cube ? new CubeSelectionSet() : new SelectionSet();

         for(int i = 0; i < refs.length; i++) {
            colmetas[i] = colmap.get(refs[i].getName());

            if(colmetas[i] == null) {
               return set;
            }

            tcols[i] = colmetas[i].getIndex();
         }

         Map<ColumnMetaData[], Set<Object>> pathselection = new HashMap<>();
         // ColumnMetaData -> List of RangeCondition
         Map<ColumnMetaData[], List<Object>> ranges = new HashMap<>();

         // create a bitmap array, with one bitmap per column selection. The
         // selected value positions are marked as 1.
         for(String key : selections.keySet()) {
            String col = key;

            // selection path is used to make sure the association is properly
            // restricted by tree selections. See logic in find()
            if(col.startsWith(SelectionVSAssembly.SELECTION_PATH)) {
               Set<Object> paths = (Set<Object>) selections.get(key);

               if(paths.size() > 0) {
                  col = col.substring(SelectionVSAssembly.SELECTION_PATH.length());
                  String[] cols = VSUtil.parseSelectionKey(col);
                  ColumnMetaData[] idxs = new ColumnMetaData[cols.length];

                  for(int i = 0; i < cols.length; i++) {
                     idxs[i] = colmap.get(cols[i]);

                     if(idxs[i] == null) {
                        idxs = null;
                        break;
                     }
                  }

                  if(idxs != null) {
                     pathselection.put(idxs, paths);
                  }
               }

               continue;
            }
            else if(col.startsWith(SelectionVSAssembly.RANGE)) {
               List<Object> conds = (List<Object>) selections.get(key);

               if(conds.size() > 0) {
                  col = col.substring(SelectionVSAssembly.RANGE.length());
                  String[] cols = VSUtil.parseSelectionKey(col);
                  ColumnMetaData[] idxs = new ColumnMetaData[cols.length];

                  for(int i = 0; i < cols.length; i++) {
                     final ColumnMetaData meta = colmap.get(cols[i]);

                     if(meta == null) {
                        idxs = null;
                        break;
                     }

                     idxs[i] = meta;
                  }

                  if(idxs != null) {
                     ranges.put(idxs, conds);
                  }
               }

               continue;
            }

            ColumnMetaData cmeta = colmap.get(col);

            if(cmeta != null) {
               int idx = cmeta.getIndex();

               if(bitmaps[idx] == null) {
                  bitmaps[idx] = new BitSet();
               }

               for(Object val : selections.get(key)) {
                  if(cube && val instanceof String) {
                     val = new MemberObject((String) val);
                  }

                  int vidx = cmeta.getValueIndex(val);

                  if(cube && vidx < 0) {
                     for(Object obj : cmeta.getValues()) {
                        if(obj instanceof MemberObject) {
                           MemberObject mobj = (MemberObject) obj;

                           if(XMLAUtil.isIdentity(val.toString(), mobj)) {
                              vidx = cmeta.getValueIndex(mobj);
                           }
                        }
                     }
                  }

                  if(vidx >= 0) {
                     bitmaps[idx].set(vidx);
                  }
               }
            }
         }

         Set<RowKey> added = new ObjectOpenHashSet<>();
         int tcnt = tuples.size();
         final Map<SelectionSet.Tuple, Formula> formulaMap = measureAggregation.getFormulas();
         Integer midx = measureIdxMap.get(measure);
         Formula mformula = getAggregateFormula(measure);

         // iterate through the tuples to find matches with the selection bitmap
         for(int r = 0, fidx = 0; r < tcnt; fidx++) {
            Object[] fragment = tuples.getFragment(fidx);

            for(int k = 0; k < fragment.length && r < tcnt; r++, k++) {
               RowTuple tuple = (RowTuple) fragment[k];
               int[] vidx = tuple.find(bitmaps, pathselection, ranges, tcols);

               if(vidx != null) {
                  RowKey key = new RowKey(vidx);
                  boolean dup = added.contains(key);

                  if(!dup || mformula != null) {
                     if(!dup) {
                        added.add(key);
                     }

                     Object[] values = new Object[vidx.length];

                     for(int i = 0; i < vidx.length; i++) {
                        values[i] = colmetas[i].getValue(vidx[i]);

                        if(StringUtils.isEmpty(values[i])) {
                           values[i] = null;
                        }

                        values[i] = SelectionSet.normalize(values[i]);
                     }

                     for(int i = 1; i <= vidx.length; i++) {
                        SelectionSet.Tuple setval = new SelectionSet.Tuple(values, i);

                        if(mformula != null) {
                           Formula form = formulaMap.get(setval);

                           if(form == null) {
                              formulaMap.put(setval, form = (Formula) mformula.clone());
                           }

                           form.addValue(tuple.getMeasure(midx));
                        }

                        if(!dup) {
                           set.add(setval);
                        }
                     }
                  }
               }
            }

            if(set.size() > 100000) {
               LOG.warn(
                  "Association data exceed 100000, truncated: " +
                     vname + " " + Tool.arrayToString(refs));
               break;
            }
         }

         return set;
      }
      finally {
         lock.readLock().unlock();
      }
   }

   /**
    * Get the formula for aggregating measure values.
    */
   private Formula getAggregateFormula(String measure) {
      if(measure == null) {
         return null;
      }

      Integer midx = measureIdxMap.get(measure);

      if(midx == null) {
         return null;
      }

      Formula form = mformulas[midx];

      if(!(form instanceof MaxFormula) && !(form instanceof MinFormula)) {
         // aggregate on aggregate for count and sum
         form = new SumFormula();
      }

      return form;
   }

   /**
    * Get the column type.
    */
   @Override
   public synchronized String getType(String column) {
      lock.readLock().lock();

      try {
         ColumnMetaData col = colmap.get(column);
         return (col == null) ? null : col.getType();
      }
      finally {
         lock.readLock().unlock();
      }
   }

   /**
    * Get the column meta data.
    */
   ColumnMetaData getColumnMetaData(String column) {
      lock.readLock().lock();

      try {
         return colmap.get(column);
      }
      finally {
         lock.readLock().unlock();
      }
   }

   /**
    * Load data to extract column items and association information.
    */
   @Override
   public void process(XTable table, String[] columns, List<AggregateRef> aggrs) {
      if(table == null || !table.moreRows(0)) {
         return;
      }

      lock.writeLock().lock();

      try {
         colmap.clear();
         colIdxMap.clear();
         tuples.dispose();
         tuples = new XSwappableObjectList<>(RowTuple.class);
         this.table = table;

         // create header and column meta data for each column
         ColumnMetaData[] cols = new ColumnMetaData[columns.length];
         int mcnt = aggrs.size();
         int[] colidx = new int[cols.length];
         int[] midx = new int[mcnt];
         // call table.moreRows to fix the data type of a column, which might
         // change during the data loading process (a BigDecimal column might
         // store both int and float values)
         table.moreRows(XTable.EOT);
         ColumnIndexMap columnIndexMap = new ColumnIndexMap(table, true);
         appliedMaxRow = Util.getAppliedMaxRows(table);

         for(int i = 0; i < cols.length; i++) {
            int idx = Util.findColumn(columnIndexMap, columns[i]);

            if(idx < 0) {
               throw new RuntimeException("Column not found in table: " + columns[i]);
            }

            cols[i] = new ColumnMetaData();
            colidx[i] = idx;
            Class cls = table.getColType(idx);
            cols[i].setType(Tool.getDataType(cls));
            cols[i].setIndex(i);
            colmap.put(columns[i], cols[i]);
            colIdxMap.put(columns[i], idx);
         }

         mformulas = new Formula[aggrs.size()];

         for(int i = 0; i < aggrs.size(); i++) {
            String mname = aggrs.get(i).toString();
            int idx = Util.findColumn(columnIndexMap, mname);

            if(idx < 0) {
               throw new RuntimeException("Measure not found in table: " +
                                          mname);
            }

            colIdxMap.put(mname, idx);
            midx[i] = idx;
            mformulas[i] = Util.createFormula(
               table, aggrs.get(i).getFormula().getFormulaName());
            measureIdxMap.put(mname, i);
         }

         boolean[] overflow = new boolean[cols.length];

         // load association
         for(int r = 1; table.moreRows(r); r++) {
            RowTuple tuple = new RowTuple(cols.length, mcnt);

            for(int i = 0; i < cols.length; i++) {
               Object val = table.getObject(r, colidx[i]);

               if(overflow[i]) {
                  tuple.set(i, -1);
               }
               else {
                  tuple.set(i, cols[i].addValue(val));
               }
            }

            for(int i = 0; i < mcnt; i++) {
               Object val = table.getObject(r, midx[i]);
               tuple.setMeasure(i, val);
            }

            tuples.add(tuple);

            if((r & 0x1fff) == 0) {
               for(int i = 0; i < cols.length; i++) {
                  if(!overflow[i] && cols[i].getValueCount() > 1000000) {
                     overflow[i] = true;
                  }
               }
            }
         }
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Dispose the table meta data.
    */
   @Override
   public void dispose() {
      lock.writeLock().lock();

      try {
         for(ColumnMetaData cmetadata : colmap.values()) {
            cmetadata.dispose();
         }

         colmap.clear();
         super.dispose();
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Get columns name.
    */
   private String[][] getColNames(DataRef[] refs) {
      String[][] columns = new String[refs.length][2];

      for(int i = 0; i < columns.length; i++) {
         if(!(refs[i] instanceof ColumnRef)) {
            continue;
         }

         ColumnRef colRef = (ColumnRef) refs[i];
         DataRef ref = colRef.getDataRef();
         columns[i][0] = ref.getName();
         columns[i][1] = colRef.getRealName();
      }

      return columns;
   }

   public int getAppliedMaxRow() {
      return appliedMaxRow;
   }

   /**
    * This class captures a distinct row.
    */
   private static final class RowTuple implements Serializable {
      // for serialization
      public RowTuple() {
      }

      /**
       * Create a tuple of specified length.
       */
      public RowTuple(int length, int mcnt) {
         tuple = new int[length];
         measures = (mcnt > 0) ? new Object[mcnt] : null;
      }

      /**
       * Set the value of a tuple element.
       */
      public final void set(int idx, int val) {
         tuple[idx] = val;
      }

      /**
       * Set the measure value.
       */
      public final void setMeasure(int idx, Object val) {
         measures[idx] = val;
      }

      /**
       * Get the measure value.
       */
      public final Object getMeasure(int idx) {
         return measures[idx];
      }

      /**
       * Populate the row from the tuple values.
       */
      public void populateRow(int[] row, int[] idxmap) {
         for(int i = 0; i < idxmap.length; i++) {
            row[i] = tuple[idxmap[i]];
         }
      }

      /**
       * Check if the selection bitmap matches the row, and return the target
       * column value index.
       * @param bitmaps a bitmap marks the selected items for each column.
       * @param pathselection tree path selection.
       * @param ranges the range conditions.
       * @param tcols the target columns.
       * @return value indexes of target column, or null if the row doesn't
       * match.
       */
      public int[] find(BitSet[] bitmaps,
                        Map<ColumnMetaData[], Set<Object>> pathselection,
                        Map<ColumnMetaData[], List<Object>> ranges,
                        int[] tcols)
      {
         for(int i = 0; i < tuple.length; i++) {
            if(bitmaps[i] != null && !bitmaps[i].get(tuple[i])) {
               return null;
            }
         }

         // if tree selections are applied, make sure the full path is selected.
         for(ColumnMetaData[] cols : pathselection.keySet()) {
            Set<Object> paths = pathselection.get(cols);

            // Can start at level 2. Level 1 selection is taken care of by regular selection set
            // elsewhere.
            for(int i = 2; i <= cols.length; i++) {
               final ColumnMetaData[] subPathCols = Arrays.copyOfRange(cols, 0, i);
               // Use encode to get the correct parent path and
               // avoid getting the wrong parent path due to "/" in the value.
               String epstr = getPath(subPathCols, true);
               int lastdash = epstr.lastIndexOf('/');
               String dstr = epstr.substring(0, lastdash + 1) + "CHILD_SELECTION_EXISTS";

               if(paths.contains(dstr) && !paths.contains(getPath(subPathCols, false))) {
                  return null;
               }
            }
         }

         // if range conditions are defined, make sure this row meets condition
         for(ColumnMetaData[] metas : ranges.keySet()) {
            List<Object> conds = ranges.get(metas);
            Object[] vals = new Object[0];

            try {
               vals = Arrays.stream(metas)
                  .map((meta) -> meta.getValue(tuple[meta.getIndex()]))
                  .toArray(Object[]::new);
            }
            catch(Exception ex) {
               // ignore it for cancelling query
            }

            for(int i = 0; i < conds.size(); ) {
               RangeCondition cond = (RangeCondition) conds.get(i);
               String condId = cond.getId();
               int end = i + 1;

               // find index of next group
               for(; end < conds.size(); end++) {
                  if(!Objects.equals(condId, ((RangeCondition) conds.get(end)).getId())) {
                     break;
                  }
               }

               boolean ok = false;

               // ranges in same group are OR conditions (calendar)
               for(; i < end; i++) {
                  cond = (RangeCondition) conds.get(i);

                  if(cond.evaluate(vals)) {
                     ok = true;
                     break;
                  }
               }

               // relations between groups are AND
               if(!ok) {
                  return null;
               }

               i = end;
            }
         }

         int[] vidx = new int[tcols.length];

         for(int i = 0; i < vidx.length; i++) {
            vidx[i] = tuple[tcols[i]];
         }

         return vidx;
      }

      /**
       * Get the path of the specified columns.
       */
      private String getPath(ColumnMetaData[] cols, boolean encode) {
         StringBuilder buf = new StringBuilder();

         for(int i = 0; i < cols.length; i++) {
            if(i > 0) {
               buf.append("/");
            }

            Object val = cols[i].getValue(tuple[cols[i].getIndex()]);
            String dataString = Tool.getDataString(val);
            buf.append(encode ? Tool.byteEncode(dataString) : dataString);
         }

         return buf.toString();
      }

      @Override
      public boolean equals(Object obj) {
         try {
            RowTuple row = (RowTuple) obj;

            if(row.tuple.length == tuple.length) {
               for(int i = 0; i < tuple.length; i++) {
                  if(tuple[i] != row.tuple[i]) {
                     return false;
                  }
               }

               return true;
            }
         }
         catch(ClassCastException ex) {
         }

         return false;
      }

      public int hashCode() {
         int hash = 0;

         for(int elem : tuple) {
            hash = 31 * hash + elem + (elem >> 32);
         }

         return hash;
      }

      public String toString() {
         StringBuilder sb = new StringBuilder("Tuple");
         sb.append('[');

         for(int i = 0; i < tuple.length; i++) {
            if(i > 0) {
               sb.append(',');
            }

            sb.append(tuple[i]);
         }

         sb.append(']');

         return sb.toString();
      }

      private int[] tuple;
      private Object[] measures;
   }

   // table lens for a single column
   class ColumnTable extends AbstractTableLens implements RowLimitableTable {
      public ColumnTable(String name, List<Object> values) {
         this.name = name;
         this.values = new ArrayList<>(values);
      }

      @Override
      public int getRowCount() {
         return values.size() + 1;
      }

      @Override
      public int getColCount() {
         return 1;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == 0) {
            return name;
         }

         return values.get(r - 1);
      }

      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      @Override
      public TableDataDescriptor getDescriptor() {
         // override the function to make sure the descriptor is correct
         // for the mapped column table
         if(descriptor == null) {
            descriptor = new ColumnDescriptor(table.getDescriptor(),
                                              new String[] {name});
         }

         return descriptor;
      }

      public Comparator getComparator() {
         return comp;
      }

      public void setComparator(Comparator comp) {
         this.comp = comp;
      }

      public int getAppliedMaxRows() {
         return getAppliedMaxRow();
      }

      /**
       *  get original processed table
       */
      public XTable getTable() {
         return table;
      }

      private List<Object> values;
      private String name;
      private TableDataDescriptor descriptor;
      private Comparator comp;
   }

   // table lens for multiple columns
   public class ColumnsTable extends AbstractTableLens implements RowLimitableTable {
      public ColumnsTable(String[] columns) {
         this.columns = columns;
         idxmap = new int[columns.length];
         cmetas = new ColumnMetaData[columns.length];
         comps = new Comparator[columns.length];

         for(int i = 0; i < columns.length; i++) {
            ColumnMetaData cmeta = colmap.get(columns[i]);

            if(cmeta == null) {
               String msg = Catalog.getCatalog().getString(
                  "common.report.composition.execution.missColumn", columns[i],
                  colmap.keySet().toString());
               CoreTool.addUserMessage(msg);
               throw new RuntimeException(msg);
            }

            idxmap[i] = cmeta.getIndex();
            cmetas[i] = (ColumnMetaData) cmeta.clone();
         }

         lastRow = new int[columns.length];
      }

      @Override
      public int getRowCount() {
         return tuples.size() + 1;
      }

      @Override
      public int getColCount() {
         return columns.length;
      }

      @Override
      public boolean isNull(int r, int c) {
         return getObject(r, c) == null;
      }

      @Override
      public synchronized Object getObject(int r, int c) {
         if(r == 0) {
            return columns[c];
         }

         if(r != lastR) {
            RowTuple tuple = tuples.get(r - 1);

            lastR = r;
            tuple.populateRow(lastRow, idxmap);
         }

         return cmetas[c].getValue(lastRow[c]);
      }

      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      @Override
      public TableDataDescriptor getDescriptor() {
         // override the function to make sure the descriptor is correct
         // for the mapped columns table
         if(descriptor == null) {
            descriptor = new ColumnDescriptor(table.getDescriptor(), columns);
         }

         return descriptor;
      }

      public Comparator getComparator(String column) {
         if(comps == null) {
            return null;
         }

         for(int i = 0; i < comps.length; i++) {
            if(Tool.equals(columns[i], column)) {
               return comps[i];
            }
         }

         return null;
      }

      public void setComparator(int i, Comparator comp) {
         if(i < 0 || i >= comps.length) {
            return;
         }

         comps[i] = comp;
      }

      public int getAppliedMaxRows() {
         return getAppliedMaxRow();
      }

      /**
       *  get original processed table
       */
      public XTable getTable() {
         return table;
      }

      private String[] columns;
      private Comparator[] comps;
      private ColumnMetaData[] cmetas;
      private int[] idxmap;
      private int[] lastRow;
      private int lastR = -1;
      private TableDataDescriptor descriptor;
   }

   /**
    * A hash key for an array of integers.
    */
   private static class RowKey {
      public RowKey(int[] arr) {
         this.arr = arr;
      }

      public boolean equals(Object obj) {
         try {
            int[] arr2 = ((RowKey) obj).arr;

            if(arr.length != arr2.length) {
               return false;
            }

            for(int i = 0; i < arr.length; i++) {
               if(arr[i] != arr2[i]) {
                  return false;
               }
            }

            return true;
         }
         catch(Exception ex) {
            return false;
         }
      }

      public int hashCode() {
         int code = 0;

         for(int i = 0; i < arr.length; i++) {
            code += arr[i];
         }

         return code;
      }

      private int[] arr;
   }

   /**
    * Table data descriptor for ColumnTable and ColumnsTable.
    */
   private class ColumnDescriptor implements TableDataDescriptor {
      /**
       * Constructor, to create a data descriptor.
       * @param desc the table data descriptor for wrapper.
       * @param columns the column(s) table contained column names.
       */
      public ColumnDescriptor(TableDataDescriptor desc, String[] columns) {
         this.descriptor = desc;
         this.columns = columns;
      }

      @Override
      public TableDataPath getColDataPath(int col) {
         return descriptor.getColDataPath(getCol(col));
      }

      @Override
      public TableDataPath getRowDataPath(int row) {
         return descriptor.getRowDataPath(row);
      }

      @Override
      public TableDataPath getCellDataPath(int row, int col) {
         return descriptor.getCellDataPath(row, getCol(col));
      }

      @Override
      public boolean isColDataPath(int col, TableDataPath path) {
         return descriptor.isColDataPath(getCol(col), path);
      }

      @Override
      public boolean isRowDataPath(int row, TableDataPath path) {
         return descriptor.isRowDataPath(row, path);
      }

      @Override
      public boolean isCellDataPathType(int row, int col, TableDataPath path) {
         return descriptor.isCellDataPathType(row, getCol(col), path);
      }

      @Override
      public boolean isCellDataPath(int row, int col, TableDataPath path) {
         return descriptor.isCellDataPath(row, getCol(col), path);
      }

      @Override
      public int getRowLevel(int row) {
         return descriptor.getRowLevel(row);
      }

      @Override
      public int getType() {
         return descriptor.getType();
      }

      @Override
      public XMetaInfo getXMetaInfo(TableDataPath path) {
         return descriptor.getXMetaInfo(path);
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         return descriptor.getXMetaInfoPaths();
      }

      @Override
      public boolean containsFormat() {
         return descriptor.containsFormat();
      }

      @Override
      public boolean containsDrill() {
         return descriptor.containsDrill();
      }

      private int getCol(int col) {
         // here should not be null value
         return colIdxMap.get(columns[col]);
      }

      private TableDataDescriptor descriptor;
      private String[] columns;
   }

   private ReadWriteLock lock = new ReentrantReadWriteLock();
   private Hashtable<String, ColumnMetaData> colmap = new Hashtable<>();
   private XSwappableObjectList<RowTuple> tuples = new XSwappableObjectList<>(RowTuple.class);
   private XTable table; // original processed table, to get descriptor
   // column name -> table column index
   private Map<String,Integer> colIdxMap = new HashMap<>();
   // measure name -> measure index
   private Map<String,Integer> measureIdxMap = new HashMap<>();
   private Formula[] mformulas;
   private int appliedMaxRow = 0;

   private static final Logger LOG = LoggerFactory.getLogger(RealtimeTableMetaData.class);
}
