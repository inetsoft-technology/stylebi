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
package inetsoft.mv.data;

import inetsoft.mv.*;
import inetsoft.mv.fs.*;
import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.util.ThreadPool;
import inetsoft.util.Tool;
import inetsoft.util.swap.XIntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * MVBuilder, it has the capability to build MV and Sub MVs.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class MVBuilder {
   /**
    * Create an instance of MVBuilder.
    */
   public MVBuilder(final XTable lens, final int[] dims, final int[] measures,
                    final boolean aggregated)
   {
      super();

      init(lens, dims, measures, null, null, aggregated);
   }

   /**
    * Create an instance of MVBuilder.
    */
   public MVBuilder(final XTable lens, final MVDef def,
                    final boolean aggregated)
   {
      this(lens, def, aggregated, null);
   }

   /**
    * Create an instance of MVBuilder.
    */
   public MVBuilder(final XTable lens, final MVDef def,
                    final boolean aggregated, final MV omv)
   {
      super();

      XTable data = lens;
      // expand data by appending dynamic mv column
      data = MVCreatorUtil.expand(def, data);

      if(def != null && def.getBreakColumn() != null) {
         breakcol = checkBlockBounds(data, def.getBreakColumn());
      }

      XIntList dlist = new XIntList(); // dimension
      XIntList mlist = new XIntList(); // measure
      List<MVColumn> cols = def.getColumns();
      List<MVColumn> dcolList = new ArrayList<>();
      List<MVColumn> mcolList = new ArrayList<>();

      for(int i = 0; i < cols.size(); i++) {
         MVColumn col = cols.get(i);
         ColumnRef vcol = col.getColumn();
         int index = getColIndex(data, vcol);

         if(index < 0) {
            // calculated column will be processed at runtime and is not needed in mv,
            // so don't warn it.
            if(!(vcol instanceof CalculateRef)) {
               LOG.warn("Materialized view column not found: " + col);
            }

            def.removeColumn(i);
            i--;
            continue;
         }

         if(col.isDimension()) {
            dlist.add(index);
            dcolList.add(col);
         }
         else {
            mlist.add(index);
            mcolList.add(col);
         }
      }

      int[] dims = dlist.toArray();
      int[] measures = mlist.toArray();
      dcolList.addAll(mcolList);
      MVColumn[] mvcols = new MVColumn[dcolList.size()];
      dcolList.toArray(mvcols);
      init(data, dims, measures, mvcols, def, aggregated, omv);
   }

   /**
    * Check which mv columns need to reset range.
    */
   private void checkDateColumn(MVColumn[] mvcols, XTable data,
                                boolean[] didxs, boolean[] fakeDates)
   {
      Arrays.fill(didxs, false);
      Arrays.fill(fakeDates, false);

      for(int i = 0; i < mvcols.length; i++) {
         MVColumn col = mvcols[i];
         MVColumn base = col;
         String range = "";

         if(col instanceof DateMVColumn && !((DateMVColumn) col).isReal()) {
            fakeDates[i] = true;
         }

         if(col instanceof DateMVColumn) {
            continue;
         }
         else if(col instanceof RangeMVColumn) {
            base = ((RangeMVColumn) col).getBase();
            range = "for Range";
         }

         if(base != null) {
            ColumnRef vcol = base.getColumn();
            int index = getColIndex(data, vcol);

            if(base.isDateTime()) {
               didxs[i] = true;
            }
         }
      }

      colIndex.clear();
   }

   /**
    * If breakby column is set, sort table and set the break-up boundaries.
    */
   private int checkBlockBounds(XTable data, String breakcol) {
      int col = Util.findColumn(data, breakcol);

      if(col < 0) {
         LOG.warn("Distinct count column not found: " + breakcol);
         return -1;
      }

      return col;
   }

   /*
    * init MV.
    */
   private void initMV(final XTable lens, final int[] dimensions,
                       final int[] measures, final MVColumn[] mvcols,
                       final boolean[] mflags, final XDimDictionary[] dicts,
                       final boolean aggregated)
   {
      final int dcnt = dimensions.length;
      final int mcnt = measures.length;
      final Class[] types = mv.types;
      final String[] columnNames = mv.headers;
      final String[] identifiers = mv.identifiers;
      final XMetaInfo[] infos = mv.infos;
      final Class[] coltypes = new Class[lens.getColCount()];
      TableDataDescriptor desc = lens.getDescriptor();

      // dimension first
      for(int i = 0; i < dcnt; i++) {
         int col = dimensions[i];
         int metacol = col;

         columnNames[i] = AssetUtil.format(XUtil.getHeader(lens, col));
         identifiers[i] = lens.getColumnIdentifier(col);
         types[i] = getColType(lens, mvcols[i], col, coltypes);

         if(columnNames[i].startsWith(RangeMVColumn.PREFIX)) {
            String metaheader = columnNames[i].substring(RangeMVColumn.PREFIX.length());
            int col2 = Util.findColumn(lens, metaheader);

            if(col2 >= 0) {
               metacol = col2;
            }
         }

         int[] tmp = getMetaCol(columnNames[i], col, lens);

         if(tmp[0] >= 0) {
            metacol = tmp[0];
         }

         if(metacol >= 0) {
            String header = Util.getHeader(lens, metacol).toString();
            Class type = getColType(lens, mvcols[i], metacol, coltypes);
            TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL,
               Util.getDataType(type), new String[] {header});
            infos[i] = desc.getXMetaInfo(path);

            // part level? neither format nor auto drill is meaningful
            // only reset XMateInfo for DateMVColumn
            if(tmp[1] == 1 && infos[i] != null && mvcols[i] instanceof DateMVColumn) {
               infos[i] = null;
            }
         }

         dicts[i] = new XDimDictionary();
         dicts[i].setDataType(getColType(lens, mvcols[i], col, coltypes));
      }

      // measure following dimensions
      for(int i = 0; i < mcnt; i++) {
         int col = measures[i];
         String header = Util.getHeader(lens, col).toString();
         columnNames[i + dcnt] = AssetUtil.format(XUtil.getHeader(lens, col));
         identifiers[i + dcnt] = lens.getColumnIdentifier(col);
         types[i + dcnt] = getColType(lens, mvcols[i + dcnt], col, coltypes);

         int tmp = getMetaCol(columnNames[i + dcnt], col, lens)[0];
         String header0 = header;

         if(tmp >= 0) {
            header0 = Util.getHeader(lens, tmp).toString();
         }

         // try summary in case the column is aggregated so format is correct
         // since aggregate formula may disable the default format. (45013)
         TableDataPath path = new TableDataPath(
            -1, TableDataPath.SUMMARY,
            Util.getDataType(getColType(lens, mvcols[i + dcnt], col, coltypes)),
            new String[] {header0});
         infos[i + dcnt] = desc.getXMetaInfo(path);

         // Bug #54920, path from the descriptor seems more correct
         // so replace the auto generated meta info if possible
         if(infos[i + dcnt] != null &&
            "true".equals(infos[i + dcnt].getProperty("autoCreatedFormat")))
         {
            path = desc.getCellDataPath(lens.getHeaderRowCount(), col);

            if(path != null) {
               XMetaInfo xMetaInfo = desc.getXMetaInfo(path);

               if(xMetaInfo != null) {
                  infos[i + dcnt] = xMetaInfo;
               }
            }
         }

         if(infos[i + dcnt] == null) {
            path = new TableDataPath(
               -1, TableDataPath.DETAIL,
               Util.getDataType(getColType(lens, mvcols[i + dcnt], col, coltypes)),
               new String[]{ header0 });
            infos[i + dcnt] = desc.getXMetaInfo(path);
         }

         if(infos[i + dcnt] == null) {
            path = new TableDataPath(
               0, TableDataPath.SUMMARY, path.getDataType(), path.getPath());
            infos[i + dcnt] = desc.getXMetaInfo(path);

            // if info is still null then get the path from the descriptor
            if(infos[i + dcnt] == null) {
               path = desc.getCellDataPath(lens.getHeaderRowCount(), col);

               if(path != null) {
                  infos[i + dcnt] = desc.getXMetaInfo(path);
               }
            }
         }

         Class<?> cls = getColType(lens, mvcols[i + dcnt], col, coltypes);

         // aggregated? do not maintain drill unless it's a date column
         if(aggregated && infos[i + dcnt] != null && !Date.class.isAssignableFrom(cls)) {
            infos[i + dcnt] = infos[i + dcnt].clone();
            infos[i + dcnt].setXDrillInfo(null);
         }

         mflags[i] = MVCreatorUtil.isNumberColumn(mvcols[i + dcnt], cls);

         if(!mflags[i]) {
            // for date, don't log this information, because from 11.3, date
            // will be stored as measure for min mv file size
            if(!Date.class.isAssignableFrom(cls)) {
               LOG.info("Non-numeric measure found: " + columnNames[i + dcnt] + " (" + cls + ")");
               // necessary for min/max of string values
               dicts[i + dcnt] = new XDimDictionary();
               dicts[i + dcnt].setDataType(cls);
            }
         }
      }
   }

   /**
    * This seems to be a hacky fix for finding the base column of a name
    * like 'Sum(col1)'. Introduced for bug1373278955714. Should consider
    * refix with better solution in the future.
    */
   private int[] getMetaCol(String cname, int col, XTable lens) {
      int metacol = -1;
      int len = cname.length();
      int index = cname.indexOf("(");
      boolean part = false;
      // dynamic column?
      boolean dcol = lens instanceof XDynamicTable ?
         ((XDynamicTable) lens).getBaseColCount() <= col : false;

      // only when the column is a dynamic column need to find base column
      if(index >= 0 && cname.charAt(len - 1) == ')' && dcol) {
         String metaheader = cname.substring(index + 1, len - 1);
         int col2 = Util.findColumn(lens, metaheader);

         if(col2 < 0) {
            String lmheader = MVDef.getLMHeader(metaheader);

            if(lmheader != null) {
               col2 = Util.findColumn(lens, lmheader);
            }
         }

         String prefix = cname.substring(0, index);
         part = prefix.indexOf("Of") > 0;

         if(col2 >= 0) {
            metacol = col2;
         }
      }

      //bug1400566534669, check if in base columns
      if(dcol && part) {
         lens.moreRows(0);

         for(int c = 0; c < ((XDynamicTable) lens).getBaseColCount(); c++) {
            if(cname != null && cname.equals(lens.getObject(0, c))) {
               metacol = c;
               break;
            }
         }
      }

      return new int[] {metacol, part ? -1 : 1};
   }

   /*
    * Add value from lens to dicts.
    */
   private int addValueToDicts(final XTable lens, final MVDef def,
                               final int[] dimensions, final int[] measures,
                               final MVColumn[] mvcols, int start, int end,
                               final XDimDictionary[] dicts, int smvBlockIndex,
                               final boolean[] numbers,
                               final boolean[] convertDates)
   {
      final int dcnt = dimensions.length;
      final int mcnt = measures.length;
      MVBlockInfo binfo = initBlockInfo(dicts, smvBlockIndex);

      for(int i = 0; i < numbers.length; i++) {
         binfo.getColumnInfo(i + dcnt).setNumber(numbers[i]);
      }

      // in case of integer overflow
      if(end < 0) {
         end = Integer.MAX_VALUE;
      }

      // optimize, iterator the base table here, add the value to dicts,
      // and calculate the range for number MVColumn, so we can avoid scan
      // the base table multi times, reduce swap the base table
      int size = MVCreatorUtil.addRowToDictionaries(
         lens, start, end, dimensions, measures,
         dicts, binfo.getColumnInfos(), numbers,
         convertDates);

      for(XDimDictionary xdd : dicts) {
         if(xdd != null) {
            xdd.complete();
         }
      }

      binfo.setRowCount(binfo.getRowCount() + size);
      MVCreatorUtil.resetNumRange(mvcols, binfo.getColumnInfos(), numbers);
      MVCreatorUtil.initColumnInfos(mv, binfo.getColumnInfos(), dicts,
                                    numbers, smvBlockIndex);
      return size;
   }

   /**
    * Init MVBlockInfo.
    */
   private MVBlockInfo initBlockInfo(XDimDictionary[] dicts, int smvBlockIndex) {
      MVBlockInfo binfo = null;

      if(smvBlockIndex != -1) {
         binfo = mv.getBlockInfo(smvBlockIndex);
         XDimDictionary[] odicts = mv.getDictionaries(smvBlockIndex);

         for(int i = 0; i < dicts.length; i++) {
            if(dicts[i] != null) {
               dicts[i].mergeFrom(odicts[i]);
            }
         }
      }
      else {
         MVColumnInfo[] cinfos = new MVColumnInfo[dicts.length];

         for(int i = 0; i < cinfos.length; i++) {
            cinfos[i] = new MVColumnInfo();
         }

         binfo = new MVBlockInfo(cinfos, 0);
         mv.addBlockInfo(binfo);
      }

      return binfo;
   }

   /**
    * Initialize this MVBuilder.
    */
   private void init(final XTable lens, final int[] dimensions,
                     final int[] measures, final MVColumn[] mvcols,
                     final MVDef def,
                     final boolean aggregated)
   {
      init(lens, dimensions, measures, mvcols, def, aggregated, null);
   }

   /**
    * Initialize this MVBuilder.
    */
   private void init(final XTable lens, final int[] dimensions,
                     final int[] measures, final MVColumn[] mvcols,
                     final MVDef def,
                     final boolean aggregated, final MV omv)
   {
      final int dcnt = dimensions.length;
      final int mcnt = measures.length;
      final int hcnt = lens.getHeaderRowCount();
      final boolean[] mflags = new boolean[mcnt];
      final XDimDictionary[] dicts;
      final String[] columnNames = omv != null ? omv.headers : new String[dcnt + mcnt];
      final String[] identifiers = new String[dcnt + mcnt];
      final Class[] types = omv != null ? omv.getTypes() : new Class[dcnt + mcnt];
      final XMetaInfo[] infos = omv != null ? omv.infos : new XMetaInfo[dcnt + mcnt];
      initBreakOption(dimensions, measures);

      // Clear the range of mv columns since the values from
      // a previous run may no longer apply here.
      for(MVColumn mvColumn : mvcols) {
         mvColumn.setRange(null, null);
      }

      // init MV
      if(omv == null || omv.getBlockSize() == 0) {
         dicts = new XDimDictionary[dcnt + mcnt];
         mv = new MV(dcnt, mcnt, columnNames, identifiers, types, infos, def);
         initMV(lens, dimensions, measures, mvcols, mflags, dicts, aggregated);
      }
      else {
         dicts = omv.getDictionaries(omv.getBlockSize() - 1);
         mv = (MV) omv.clone();
         final Class[] coltypes = new Class[lens.getColCount()];

         for(int i = 0; i < mcnt; i++) {
            Class<?> cls = getColType(lens, mvcols[i + dcnt], measures[i], coltypes);
            mflags[i] = Number.class.isAssignableFrom(cls);
         }
      }

      final int preferred = getPreferredBlock();
      // date range column index
      final boolean[] didxs = new boolean[dcnt + mcnt];
      final boolean[] fakeDates = new boolean[dcnt + mcnt];
      checkDateColumn(mvcols, lens, didxs, fakeDates);
      // which column is number
      final boolean[] numbers = MVCreatorUtil.getNumberColumns(types, mvcols, dcnt, mcnt);
      final boolean[] convertDates = MVCreatorUtil.getConvertDateColumns(mvcols, dcnt, mcnt);

      iterator = new Iterator<SubMV>() {
         int r = hcnt;
         int start = 0;
         SubMV smv = null;
         // incremental mv with break column?
         boolean breakIncremental = omv != null && breakcol >= 0 && !isDesktop();
         int subMVBlockIndex = -1;
         XDimDictionary[] dicts2;
         boolean append = true;
         Set<Integer> updatedBlocks = new HashSet<>();

         /**
          * Get the size for each block when incremental with break column.
          */
         private int getIncrementalBlockSize() {
            if(dbreak < 0 && mbreak < 0) {
               return getBlockSize();
            }

            int row = r;
            int size = 0;
            int preBlockIndex = -1;

            while(lens.moreRows(row)) {
               Object obj = null;

               if(dbreak >= 0) {
                  obj = lens.getObject(row, dimensions[dbreak]);
               }
               else if(mbreak >= 0) {
                  obj = lens.getObject(row, measures[mbreak]);
               }

               // for incremental, the omv's index may be invalid, and mv has
               // updated the break value in MVIncremental.updateBreakValueIndex,
               // so here is safe and correct to use mv instead of omv
               int blockIndex = mv.getBreakValueIndex(convertDate(obj));

               // the obj is greater than the max value of the omv,
               // append rows to last block, other rows maybe
               // create a new block
               if(blockIndex < 0) {
                  int size0 = getBlockSize();
                  size = Math.max(size0, size);
                  breakIncremental = false;
                  preBlockIndex = preBlockIndex == -1 ? subMVBlockIndex
                     : preBlockIndex;
                  break;
               }

               if(preBlockIndex < 0) {
                  preBlockIndex = blockIndex;
               }

               // time to break if the two value obviously not in same block
               if(preBlockIndex != blockIndex) {
                  break;
               }

               row++;
               size++;
            }

            subMVBlockIndex = preBlockIndex;
            return size;
         }

         /**
          * Get the size for each block.
          */
         private int getBlockSize() {
            int size = preferred;

            // append specified row to last block
            if(append && omv != null && omv.getBlockSize() > 0) {
               MVBlockInfo binfo = mv.getBlockInfo(mv.getBlockSize() - 1);
               int rowCount = binfo.getRowCount();
               int remain = (int) (preferred * 1.25 - rowCount);
               append = false;

               if(remain > 0 && preferred > rowCount) {
                  size = lens.moreRows(r + remain) ? (preferred - rowCount)
                                               : lens.getRowCount();
                  subMVBlockIndex = mv.getBlockSize() - 1;
               }
            }

            int row0 = isDesktop() ? preferred : r + size;

            // find the break boundary if set
            if(breakcol >= 0 && lens.moreRows(row0)) {
               Object prev = lens.getObject(row0 - 1, breakcol);
               int nextb = row0;
               int lookahead = nextb;
               String maxStr = SreeEnv.getProperty("mv.max.block");
               int maxBlock = maxStr != null ? Integer.parseInt(maxStr) : preferred * 10;
               boolean found = false;

               for(; lens.moreRows(nextb) && nextb <= maxBlock; nextb++) {
                  // optimization, look at 100 rows and if eq, skip to the next row.
                  // this is based on the assumption the breakcol is sorted.
                  if(lookahead == nextb) {
                     lookahead += 100;

                     if(lens.moreRows(lookahead)) {
                        if(Tool.equals(prev, lens.getObject(lookahead, breakcol))) {
                           // skip to the next row
                           nextb = lookahead;
                           // lookahead again on next iteration
                           lookahead = nextb + 1;
                           continue;
                        }
                     }
                  }

                  if(!Tool.equals(prev, lens.getObject(nextb, breakcol))) {
                     found = true;
                     break;
                  }
               }

               size = nextb - r;

               if(!found) {
                  LOG.warn("Distinct count defined on " + lens.getObject(0, breakcol) +
                     " caused MV block to exceed maximum size (" + maxBlock +
                              "). As a result, the disinct count may not be accurate. " +
                     "Modify the dashboard to remove the distinct count, or increase " +
                     "maximum block size by defining mv.max.block property.");
               }
            }

            return size;
         }

         @Override
         public boolean hasNext() {
            // preferred * 1.25 to avoid too thin sub mv
            int size = breakIncremental ? getIncrementalBlockSize() :
                                          getBlockSize();

            if(size <= 0 || !lens.moreRows(r)) {
               return false;
            }

            // clone mv dicts.
            dicts2 = new XDimDictionary[dicts.length];
            int[] lastRows = new int[dicts.length];

            for(int i = 0; i < dicts.length; i++) {
               if(dicts[i] != null) {
                  dicts2[i] = (XDimDictionary) dicts[i].clone();
                  dicts2[i].setBaseRow(r);
               }
            }

            // for incremental MV, the index should add the last row counts
            // if the dimDictionary is overflow
            if(subMVBlockIndex != -1 && !updatedBlocks.contains(subMVBlockIndex)) {
               XDimDictionary[] odicts = mv.getDictionaries(subMVBlockIndex);

               updatedBlocks.add(subMVBlockIndex);

               for(int i = 0; i < odicts.length; i++) {
                  if(odicts[i] != null && odicts[i].isOverflow()) {
                     lastRows[i] = odicts[i].size();
                     dicts2[i].setOverflow(true);
                  }
               }
            }

            // prepare dicts for every block.
            size = addValueToDicts(lens, def, dimensions, measures, mvcols,
                                   r, r + size, dicts2, subMVBlockIndex,
                                   numbers, convertDates);
            XDimIndex[] dims = new XDimIndex[dcnt];
            int bindex = subMVBlockIndex == -1 ? mv.getBlockSize() - 1
                                               : subMVBlockIndex;
            bindex = bindex == -1 ? 0 : bindex;
            MVColumnInfo[] currentColInfos = mv.getBlockInfo(bindex).getColumnInfos();

            DefaultTableBlock block = new DefaultTableBlock(
               size, dcnt, mcnt, currentColInfos, columnNames,
               identifiers, types, mvcols);
            smv = new SubMV(dims, block);
            smv.setBlockIndex(subMVBlockIndex);
            start = r;

            for(int j = 0; j < dcnt; j++) {
               if(dicts2[j].size() < 500) {
                  dims[j] = new BitDimIndex();
               }
               else {
                  // use the dim column as index (from DefaultTableBlock)
               }
            }

            final int endRow = start + size;
            final List<CompletableFuture<Void>> futures = new ArrayList<>();

            for(int col = 0; col < dcnt; col++) {
               final CompletableFuture<Void> future = new CompletableFuture<>();
               futures.add(future);
               final int finalCol = col;

               ThreadPool.addOnDemand(() -> {
                  try {
                     for(int row = start; row < endRow; row++) {
                        Object obj = lens.getObject(row, dimensions[finalCol]);
                        int idx = convertDimValue(finalCol, row + lastRows[finalCol], obj);

                        if(dims[finalCol] != null) {
                           dims[finalCol].addKey(idx, row - start);
                        }

                        int blockIdx = row - start;
                        block.addDimension(blockIdx, finalCol, idx);
                     }

                     future.complete(null);
                  }
                  catch(Exception ex) {
                     future.completeExceptionally(ex);
                  }
               });
            }

            for(int col = 0; col < mcnt; col++) {
               // for not real date column, it doesn't create the index,
               // because it getValue from the base column,
               // so doesn't need to calculate index for this column
               if(fakeDates[dcnt + col] && mbreak != col) {
                  continue;
               }

               final CompletableFuture<Void> future = new CompletableFuture<>();
               futures.add(future);
               final int finalCol = col;

               ThreadPool.addOnDemand( () -> {
                  try {
                     for(int row = start; row < endRow; row++) {
                        Object obj = lens.getObject(row, measures[finalCol]);
                        double measure = MVCreatorUtil.convertMeasureValue(obj, row + lastRows[dcnt + finalCol],
                                                                           dicts2[finalCol + dcnt], numbers[finalCol]);
                        int blockIdx = row - start;
                        block.addMeasure(blockIdx, finalCol, measure);
                     }

                     future.complete(null);
                  }
                  catch(Exception ex) {
                     future.completeExceptionally(ex);
                  }
               });
            }

            try {
               CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            }
            catch(Exception ex) {
               throw new RuntimeException(ex);
            }

            r += size;
            MVCreatorUtil.resetDateRange(mcnt, dcnt, currentColInfos, mvcols);
            MVCreatorUtil.setDateMVRange(mcnt, dcnt, currentColInfos, mvcols,
               columnNames);

            // for each block, the break value is the max value of the block
            if(dictBreakIndex >= 0) {
               Object max = currentColInfos[dictBreakIndex].getMax();
               mv.addBreakValues(convertDate(max), bindex);
            }

            return smv != null;
         }

         @Override
         public SubMV next() {
            smv.complete();
            return smv;
         }

         @Override
         public void remove() {
         }

         /**
          * If the Object is Date, return the time of the Date.
          */
         private Object convertDate(Object obj) {
            if(obj instanceof Date) {
               return ((Date) obj).getTime();
            }

            return obj;
         }

         /**
          * Convert physical dimension value to logic value.
          */
         private int convertDimValue(int j, int row, Object obj) {
            int idx = dicts2[j].indexOf(obj, row);

            if(idx < 0) {
               throw new RuntimeException("Can't find value[" + obj +
                                          "] in dimension[" + j + "]");
            }

            return idx;
         }
      };
   }

   /**
    * Get break column in the dictionary index.
    */
   public int getBreakColDictIndex() {
      return dictBreakIndex;
   }

   /**
    * Init break column options.
    */
   private void initBreakOption(int[] dimensions, int[] measures) {
      // desktop just ignore it
      if(breakcol > 0 && !isDesktop()) {
         for(int i = 0; i < measures.length; i++) {
            if(breakcol == measures[i]) {
               mbreak = i;
               dictBreakIndex = dimensions.length + i;
               return;
            }
         }

         for(int i = 0; i < dimensions.length; i++) {
            if(breakcol == dimensions[i]) {
               dbreak = i;
               dictBreakIndex = i;
               return;
            }
         }
      }
   }

   /**
    * Get the created MV.
    */
   public MV getMV() {
      return mv;
   }

   /**
    * Get the created sub mvs.
    */
   public Iterator<SubMV> getSubMVs() {
      return iterator;
   }

   private boolean isDesktop() {
      boolean desktop = FSService.getConfig().isDesktop();
      return desktop;
   }

   /**
    * Get the preferred block row count.
    */
   private int getPreferredBlock() {
      boolean desktop = isDesktop();

      // desktop system? do not split data
      if(desktop) {
         return Integer.MAX_VALUE;
      }

      String val = SreeEnv.getProperty("mv.preferred.block");
      int preferred = Integer.parseInt(val);
      return preferred;
   }

   /**
    * Get the vcol index.
    */
   private int getColIndex(XTable data, ColumnRef vcol) {
      if(colIndex.containsKey(vcol)) {
         return colIndex.get(vcol);
      }

      int index = AssetUtil.findColumn(data, vcol);
      colIndex.put(vcol, index);
      return index;
   }

   /**
    * Gets the data type of the specified table column.
    *
    * @param table  the table.
    * @param column the column meta-data.
    * @param index  the index of the column.
    *
    * @return the column data type.
    */
   private Class getColType(XTable table, MVColumn column, int index, Class[] coltypes) {
      Class result = coltypes[index];

      if(result == null) {
         result = MVCreatorUtil.getColType(table, column, index);
         coltypes[index] = result;
      }

      return result;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(MVBuilder.class);
   private MV mv;
   // if set, the valid boundary to break blocks
   private int breakcol = -1;
   private int dbreak = -1; // break column in dimension index
   private int mbreak = -1; // break column in measure index
   private int dictBreakIndex = -1; // break column in the index of dict
   // cache the col index
   private Map<ColumnRef, Integer> colIndex = new HashMap<>();
   private Iterator<SubMV> iterator;
}
