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
package inetsoft.mv.data;

import inetsoft.mv.*;
import inetsoft.report.*;
import inetsoft.report.composition.execution.MVInfo;
import inetsoft.report.filter.BinaryTableFilter;
import inetsoft.report.lens.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.jdbc.XFilterNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MVQuery, the server node query to merge data from data nodes.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class MVQuery {
   /**
    * Create an instance of MVQuery.
    */
   public MVQuery(MV mv, GroupRef[] groups, boolean[] order,
                  AggregateRef[] aggregates, AggregateRef[] subaggrs,
                  XFilterNode cond)
   {
      this.mv = mv;
      this.processed = new HashSet();
      this.groups = groups;
      this.aggregates = aggregates;
      this.subaggrs = subaggrs;
      this.order = order;
      this.cond = cond;

      // mv may be null when check mv can hit or not
      if(mv != null) {
         init();
      }
   }

   /**
    * Set the information of the MV this table based on.
    */
   public static void setMVInfos(TableLens lens, MVDef mvDef) {
      lens.setProperty(MV_EXECUTION_INFO, mvDef);
   }

   /**
    * Get the information of the MV this table based on.
    */
   public static List<MVInfo> getMVInfos(TableLens lens) {
      if(lens == null) {
         return null;
      }

      TableLens currentLens = lens;
      List<MVInfo> mvInfos = new ArrayList<>();

      if(lens instanceof SetTableLens) {
         for(TableLens sub : ((SetTableLens) lens).getTables()) {
            mvInfos.addAll(getMVInfos(sub));
         }

         return mvInfos;
      }
      else if(lens instanceof CalcTableLens) {
         return getMVInfos(((CalcTableLens) lens).getDataTable());
      }
      else if(lens instanceof TableFilter) {
         return getMVInfos(((TableFilter) lens).getTable());
      }
      else if(lens instanceof BinaryTableFilter) {
         List<MVInfo> mvInfos1 = getMVInfos(((BinaryTableFilter) lens).getLeftTable());
         List<MVInfo> mvInfos2 = getMVInfos(((BinaryTableFilter) lens).getRightTable());

         mvInfos.addAll(mvInfos1);
         mvInfos.addAll(mvInfos2);
         return mvInfos;
      }

      MVInfo mvInfo = (MVInfo) currentLens.getProperty(MV_EXECUTION_INFO);

      if(mvInfo != null) {
         mvInfos.add(mvInfo);
      }

      return mvInfos;
   }

   /**
    * Add one table block into this MVQuery.
    * @param id the id of the specified sub mv.
    */
   public void add(String id, SubTableBlock block) {
      lock.lock();

      try {
         if(processed.contains(id)) {
            return;
         }

         processed.add(id);
         convertRow(block);

         if(table == null) {
            if(isDetail()) {
               table = new DetailMergedTableBlock(this, block, ccnt, dcnt, mcnt,
                                                  dictIdxes, types, intcols, tscols);
            }
            else if(donly) {
               table = new GroupOnlyMergedTableBlock(this, block);
            }
            else {
               table = new GroupedMergedTableBlock(this, block);
            }
         }

         table.add(block);
      }
      catch(Exception ex) {
         LOG.error("Failed to merge table blocks.", ex);
      }
      finally {
         lock.unlock();
      }
   }

   private void init() {
      dcnt = groups.length;
      mcnt = aggregates.length;
      ccnt = dcnt + mcnt;
      donly = mcnt == 0;

      mdates = new String[ccnt];
      types = new Class[ccnt];
      intcols = new boolean[ccnt];
      tscols = new boolean[ccnt];
      dictIdxes = new int[ccnt];
      Arrays.fill(dictIdxes, -1);

      smcnt = subaggrs.length;
      sccnt = dcnt + smcnt;
      sdonly = smcnt == 0;
      sdictIdxes = new int[sccnt];
      Arrays.fill(sdictIdxes, -1);

      for(int i = 0; i < dcnt; i++) {
         DataRef ref = groups[i].getDataRef();
         String attrName = MVDef.getMVHeader(ref);
         int idx = mv.indexOfHeader(attrName, 0);

         if(idx < 0) {
            continue;
         }

         if(mv.getDictionary(idx, 0) != null) {
            dictIdxes[i] = idx;
            sdictIdxes[i] = idx;
         }

         types[i] = mv.types[idx];
         intcols[i] = isIntColumn(types[i], attrName);
         tscols[i] = isTimestampColumn(groups[i]);
      }

      initMeasures(aggregates, dictIdxes, false);
      initMeasures(subaggrs, sdictIdxes, true);
   }

   private void initMeasures(AggregateRef[] aggregates, int[] dictIdxes, boolean sub) {
      for(int i = 0; i < aggregates.length; i++) {
         final int j = i + dcnt;
         AggregateRef aref = aggregates[i];
         DataRef ref = GroupedTableBlock.getDataRef(aref.getDataRef());
         AggregateFormula form = aref.getFormula();
         String attrName = MVDef.getMVHeader(ref);
         int idx = mv.indexOfHeader(attrName, mv.getDimCount());
         boolean isdim = idx < 0;

         if(idx < 0) {
            idx = mv.indexOfHeader(attrName, 0);
         }

         if(ref != null && !sub) {
            String dtype = form.getDataType();

            if(dtype == null) {
               dtype = aref.getDataType();
            }

            types[j] = Tool.getDataClass(dtype);
         }

         if(idx < 0 || (!AggregateFormula.MIN.equals(form) &&
            !AggregateFormula.MAX.equals(form) &&
            !AggregateFormula.LAST.equals(form) &&
            !AggregateFormula.FIRST.equals(form)))
         {
            continue;
         }

         XDimDictionary dim = mv.getDictionary(idx, 0);

         if(dim != null) {
            dictIdxes[j] = idx;
         }

         // range is already applied in getMeasureValue of MVDimColumn, so
         // it shouldn't be applied again throgh the mdicts
         if(isdim && dim != null && dim.getRangeMin() != 0) {
            dictIdxes[j] = -1;
         }

         if(dictIdxes[j] != -1) {
            dimAggregate = true;
         }

         if(sub || ref == null || !XSchema.isDateType(ref.getDataType())) {
            continue;
         }

         mdates[j] = ref.getDataType();
         types[j] = mv.types[idx];
         intcols[j] = isIntColumn(types[j], attrName);
      }
   }

   /**
    * Convert the value to real value.
    */
   private void convertRow(SubTableBlock block) throws IOException {
      int blockIdx = block.getBlockIndex();
      int rcnt = block.getRowCount();
      XDimDictionary[] dicts = new XDimDictionary[dcnt];
      XDimDictionary[] mdicts = new XDimDictionary[smcnt];

      for(int i = 0; i < dcnt; i++) {
         if(sdictIdxes[i] != -1) {
            dicts[i] = mv.getDictionary(sdictIdxes[i], blockIdx);
         }
      }

      for(int i = 0; i < smcnt; i++) {
         if(sdictIdxes[dcnt + i] != -1) {
            mdicts[i] = mv.getDictionary(sdictIdxes[dcnt + i], blockIdx);
         }
      }

      Object[] arr = new Object[smcnt];
      XSwappableObjectList<MVRow> rows = new XSwappableObjectList<>(MVRow2.class);

      for(int i = 0; i < rcnt; i++) {
         MVRow row = block.getRow(i);
         Object[] groups = new Object[dcnt];
         Object[] aggregates2 = null;

         for(int j = 0; j < dcnt; j++) {
            if(dicts[j] != null) {
               groups[j] = dicts[j].getValue((int) row.getGroups()[j]);
            }
            else {
               groups[j] = row.getGroups()[j];
            }
         }

          // aggregate on dimension
         if(!sdonly && dimAggregate) {
            row.getObject(arr, smcnt);
            aggregates2 = new Object[smcnt];

            for(int j = 0; j < smcnt; j++) {
               if(arr[j] instanceof Number && mdicts[j] != null) {
                  aggregates2[j] = mdicts[j].getValue(((Number) arr[j]).intValue());
               }
               else {
                  aggregates2[j] = arr[j];
               }
            }
         }

         MVRow2 nrow;

         if(dimAggregate) {
            nrow = new MVRow2(groups, aggregates2);
         }
         else {
            nrow = new MVRow2(groups, row.getAggregates());
         }

         nrow.setFormulas(row.getFormulas());
         rows.add(nrow);
      }

      rows.complete();
      block.setRows(rows);
   }

   /**
    * Check if this MVQuery is completed.
    */
   public boolean isCompleted() {
      return completed;
   }

   /**
    * Complete this MVQuery, then it will generate XTableObject.
    * @param all true if all blocks are added, false if still streaming.
    */
   public void complete(boolean all) {
      TableLens tbl = table.getTableLens();

      // tbl is null if not streaming
      if(tbl == null || all || isCancelled()) {
         table.complete();
      }

      lock.lock();

      try {
         if(completed) {
            return;
         }

         Object[][] vals = null;

         if(tbl == null) {
            int rcnt = table.getRowCount();
            PagedTableLens list = null;
            boolean swap = false;

            // @by jasons, use a XSwappableTable for *any* table with less than
            // 10,000 rows. This allows the table to be kept in the data cache and
            // prevents extreme memory consumption by large tables.
            if(rcnt < 10000) {
               vals = new Object[rcnt][];
            }
            else {
               swap = true;
               list = new PagedTableLens0();
               list.setColCount(ccnt);
               list.addRow(table.headers);
               tbl = list;
            }

            MVRow2 row = null;

            for(int i = 0; i < rcnt; i++) {
               try {
                  row = (MVRow2) table.getRow(i);
                  Object[] objs = row.getObjects(ccnt, dcnt, mcnt, dictIdxes,
                                                 types, intcols, tscols);

                  if(swap) {
                     list.addRow(objs);
                  }
                  else {
                     vals[i] = objs;
                  }
               }
               catch(IOException ex) {
                  // shouldn't happen
                  LOG.error("Failed to get row.", ex);
               }
            }

            if(list != null) {
               list.complete();
            }
         }

         if(tbl != null) {
            lens = new SwappableAggregateTable(dcnt, tbl, table.headers, mdates,
                                               tscols);
         }
         else {
            lens = new AggregateTable(dcnt, vals, table.headers, mdates, tscols);
         }

         setMVInfos(lens, mv.getDef());

         for(int i = 0; i < ccnt; i++) {
            lens.setXMetaInfo(i, table.getXMetaInfo(i), getColumnType(lens.getHeader(i)));
            lens.setColumnIdentifier(i, table.getColumnIdentifier(i));
         }

         completed = true;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Get the meta info if any.
    */
   public XMetaInfo getXMetaInfo(String header) {
      return mv.getXMetaInfo(header);
   }

   /**
    * Get the identifier.
    */
   public String getColumnIdentifier(String header) {
      return mv.getColumnIdentifier(header);
   }

   /**
    * Get the data as a result.
    */
   public XTable getData() {
      return lens;
   }

   /**
    * Set the sql helper.
    */
   public void setSQLHelper(SQLHelper helper) {
      this.helper = helper;
   }

   /**
    * Get the sql helper.
    */
   public SQLHelper getSQLHelper() {
      return helper;
   }

   /**
    * Set whether is detail query.
    */
   public void setDetail(boolean detail) {
      this.detail = detail;
   }

   /**
    * Check if is detail query.
    */
   public boolean isDetail() {
      return detail;
   }

   /**
    * Set the associated job.
    */
   public void setJob(MVJob job) {
      this.job = job;
   }

   /**
    * Cancel and cleanup MVQuery table.
    */
   public void cancel() {
      if(table != null) {
         table.complete();
      }
   }

   /**
    * Check if this job has been cancelled.
    */
   public boolean isCancelled() {
      return job != null && job.isCancelled();
   }

   /**
    * Check if the column is stored as MVIntColumn.
    */
   private boolean isIntColumn(Class type, String attrName) {
      MVColumn mvcol = mv.getDef().getColumn(attrName, true);

      if(mvcol == null) {
         mvcol = mv.getDef().getColumn(attrName, false);
      }

      if(mvcol == null) {
         return false;
      }

      return isIntColumn(type, mvcol);
   }

   /**
    * Get the column type of the MVColumn by column name.
    * @param attrName The name of the attribute to lookup
    */
   private String getColumnType(String attrName) {
      MVColumn mvcol = mv.getDef().getColumn(attrName, true);

      if(mvcol == null) {
         mvcol = mv.getDef().getColumn(attrName, false);
      }

      if(mvcol == null || mvcol.getColumn() == null) {
         return XSchema.STRING;
      }

      return mvcol.getColumn().getDataType();
   }

   /**
    * Check if the column should return Timestamp values.
    */
   private boolean isTimestampColumn(GroupRef gcol) {
      // database queries return timestamp for date grouping expression, so
      // we must match it in MV to avoid subtle differences
      switch(gcol.getDateGroup()) {
      case DateRangeRef.YEAR_DATE_GROUP:
      case DateRangeRef.QUARTER_DATE_GROUP:
      case DateRangeRef.MONTH_DATE_GROUP:
      case DateRangeRef.WEEK_DATE_GROUP:
      case DateRangeRef.DAY_DATE_GROUP:
         return true;
      }

      return false;
   }

   /**
    * Get the maximum number of rows in the output.
    */
   public int getMaxRows() {
      return job.getTable().getMaxRows();
   }

   /**
    * Check if the query has collected enough data for max rows.
    */
   public boolean isFulfilled() {
      if(table == null) {
         return false;
      }

      TableLens lens = table.getTableLens();

      if(lens != null && isDetail()) {
         TableAssembly tbl = job.getTable();
         int maxrows = tbl.getMaxRows();

         if(maxrows > 0) {
            int rcnt = lens.getRowCount();
            return -(rcnt + 1) >= maxrows + 1;
         }
      }

      return false;
   }

   /**
    * Check if the column is stored as MVIntColumn.
    */
   static boolean isIntColumn(Class type, MVColumn mvcol) {
      if(mvcol.isDateTime() ||
         mvcol instanceof DateMVColumn && !((DateMVColumn) mvcol).isReal())
      {
         return false;
      }

      // for non-number type, always use MVIntColumn, otherwise in MVBuilder
      // the generated index is not same as MVDoubleColumn.getDimValue, because
      // it has XDimDictionary
      // fix bug1302852318985
      // date type use double column directly, because date has no XDimDictionary
      // fix bug1303296686849
      if(type == Integer.class || type == Short.class || type == Byte.class ||
         !Number.class.isAssignableFrom(type) &&
         !Date.class.isAssignableFrom(type))
      {
         Number min = (mvcol == null) ? null : mvcol.getOriginalMin();
         Number max = (mvcol == null) ? null : mvcol.getOriginalMax();

         if(min == null || max == null ||
            // avoid integer overflow
            min.doubleValue() > Integer.MIN_VALUE &&
            max.doubleValue() - min.doubleValue() < Integer.MAX_VALUE / 2)
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Gets mv query groups
    */
   public GroupRef[] getGroups() {
      return groups;
   }

   /**
    * Gets mv query aggregates
    */
   public AggregateRef[] getAggregates() {
      return aggregates;
   }

   /**
    * Get the query condition.
    */
   public XFilterNode getCondition() {
      return cond;
   }

   /**
    * Gets mv query types
    */
   public Class[] getTypes() {
      return types;
   }

   /**
    * Gets the array that determines whether a column is stored as MVIntColumn
    */
   public boolean[] getIntegerColumns() {
      return intcols;
   }

   /**
    * Gets the array that determines if a column should return Timestamp values
    */
   public boolean[] getTimestampColumns() {
      return tscols;
   }

   /**
    * Gets the sort order
    */
   public boolean[] getOrder() {
      return order;
   }

   public long dataId() {
      MVStorage storage = MVStorage.getInstance();
      long id = (long) mv.getFile().hashCode() + storage.getLastModified(mv.getFile());

      for(int i = 0; i < groups.length; i++) {
         id += groups[i].toString().hashCode();
      }

      for(int i = 0; i < aggregates.length; i++) {
         id += aggregates[i].toString().hashCode();
      }

      for(int i = 0; i < subaggrs.length; i++) {
         id += subaggrs[i].toString().hashCode();
      }

      for(int i = 0; i < order.length; i++) {
         id += order[i] ? 1 : 0;
      }

      if(cond != null) {
         id += dataId(cond);
      }

      return id;
   }

   // calculate condition tree data id
   private static long dataId(XNode node) {
      long id = node.toString().hashCode();

      for(int i = 0; i < node.getChildCount(); i++) {
         id += dataId(node.getChild(i));
      }

      return id;
   }

   private static class SwappableAggregateTable extends AggregateTable {
      public SwappableAggregateTable(int cnt, TableLens list,
                                     String[] headers, String[] mdates,
                                     boolean[] tscols)
      {
         super(cnt, headers, mdates, tscols);
         this.list = list;
      }

      @Override
      public int getRowCount() {
         return list.getRowCount();
      }

      @Override
      public boolean moreRows(int r) {
         return list.moreRows(r);
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == 0) {
            return super.getObject(r, c);
         }

         return getVal(list.getObject(r, c), c);
      }

      /**
       * Dispose the table to clear up temporary resources.
       */
      @Override
      public void dispose() {
         if(list != null) {
            list.dispose();
         }

         super.dispose();
      }

      /**
       * Finalize the object.
       */
      @Override
      protected void finalize() throws Throwable {
         dispose();
         super.finalize();
      }

      private TableLens list;
   }

   /**
    * The aggregate table as the execution result of this MVQuery.
    */
   private static class AggregateTable extends AbstractTableLens {
      public AggregateTable(int cnt, String[] headers, String[] mdates, boolean[] tscols) {
         this.cnt = cnt;
         this.headers = headers;
         this.mdates = mdates;
         this.tscols = tscols;
      }

      public AggregateTable(int cnt, Object[][] vals, String[] headers,
                            String[] mdates, boolean[] tscols)
      {
         this(cnt, headers, mdates, tscols);
         this.vals = vals;
      }

      /**
       * Get the header name from this table by column index.
       * @param col index of column to lookup
       * @return The header name at col index.
       */
      public String getHeader(int col) {
         return headers[col];
      }

      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      // why we treat all column as header col? this will cause
      // table style wrong, see bug1376602474163
      /*
      public int getHeaderColCount() {
         return cnt;
      }
      */

      @Override
      public int getRowCount() {
         return vals.length + 1;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == 0) {
            return headers[c];
         }

         return getVal(vals[r - 1][c], c);
      }

      protected Object getVal(Object val, int c) {
         if(mdates[c] == null || !(val instanceof Number)) {
            if(tscols[c] && val instanceof Date) {
               val = new Timestamp(((Date) val).getTime());
            }

            return val;
         }

         if(tscols[c]) {
            val = new Timestamp(((Number) val).longValue());
         }
         else if(XSchema.DATE.equals(mdates[c])) {
            val = new java.sql.Date(((Number) val).longValue());
         }
         else if(XSchema.TIME.equals(mdates[c])) {
            val = new Time(((Number) val).longValue());
         }
         else if(val instanceof Number) {
            val = new java.sql.Date(((Number) val).longValue());
         }

         return val;
      }

      @Override
      public void setObject(int r, int c, Object v) {
         if(r == 0) {
            headers[c] = v == null ? null : v.toString();
         }
         else {
            throw new RuntimeException("Not implemented method: setObject");
         }
      }

      @Override
      public int getColCount() {
         return headers.length;
      }

      @Override
      public String getColumnIdentifier(int col) {
         String identifier = super.getColumnIdentifier(col);
         return identifier == null ? headers[col] : identifier;
      }

      @Override
      public TableDataDescriptor getDescriptor() {
         if(descriptor == null) {
            descriptor = new TableDataDescriptor2(this);
         }

         return descriptor;
      }

      public void setXMetaInfo(int col, XMetaInfo minfo, String dataType) {
         TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL,
            dataType, new String[] {headers[col]});

         if(minfo == null) {
            if(mmap != null) {
               mmap.remove(path);
            }
         }
         else {
            if(mmap == null) {
               mmap = new HashMap();
            }

            mmap.put(path, minfo);
         }
      }

      /**
       * Dispose the table to clear up temporary resources.
       */
      @Override
      public void dispose() {
         if(mmap != null) {
            mmap.clear();
         }

         if(vals == null) {
            return;
         }

         for(int i = 0; i < vals.length; i++) {
            vals[i] = null;
         }
      }

      /**
       * Finalize the object.
       */
      @Override
      protected void finalize() throws Throwable {
         dispose();
         super.finalize();
      }

      /**
       * Table data descriptor.
       */
      private final class TableDataDescriptor2 extends
         DefaultTableDataDescriptor
      {
         public TableDataDescriptor2(XTable table) {
            super(table);
         }

         @Override
         public final XMetaInfo getXMetaInfo(TableDataPath path) {
            if(mmap == null || !path.isCell()) {
               return null;
            }

            return (XMetaInfo) mmap.get(path);
         }

         @Override
         public final boolean containsFormat() {
            if(cformat == 0) {
               cformat = XUtil.containsFormat(mmap) ? CONTAINED : NOT_CONTAINED;
            }

            return cformat == CONTAINED;
         }

         @Override
         public final boolean containsDrill() {
            if(cdrill == 0) {
               cdrill = XUtil.containsDrill(mmap) ? CONTAINED : NOT_CONTAINED;
            }

            return cdrill == CONTAINED;
         }

         private static final int NOT_CONTAINED = 1;
         private static final int CONTAINED = 2;
         private int cformat = 0;
         private int cdrill = 0;
      }

      private TableDataDescriptor descriptor;
      private String[] headers;
      private String[] mdates;
      private boolean[] tscols;
      private Object[][] vals;
      private int cnt;
      private Map mmap = null;
   }

   // avoid anonymous inner class which holds a reference to this
   private static class PagedTableLens0 extends PagedTableLens {
   }

   GroupRef[] groups;
   AggregateRef[] aggregates;
   boolean[] order;
   boolean dimAggregate;
   private AggregateRef[] subaggrs;
   private XFilterNode cond;
   private MV mv;
   private Lock lock = new ReentrantLock();
   private MergedTableBlock table;
   private SQLHelper helper;
   private AggregateTable lens;
   private boolean completed;
   private boolean detail;
   private Set processed;
   private MVJob job;
   private int dcnt;
   private int mcnt;
   private int ccnt;
   private String[] mdates;
   private Class[] types;
   private boolean[] tscols;
   private boolean[] intcols;
   private int[] dictIdxes;
   private boolean donly;

   // sub aggregates, used for convertRow
   private int smcnt;
   private int sccnt;
   private boolean sdonly;
   private int[] sdictIdxes;

   private static final String MV_EXECUTION_INFO = "mv-execution-info";
   private static final Logger LOG = LoggerFactory.getLogger(MVQuery.class);
}
