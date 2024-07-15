/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.execution;

import inetsoft.report.StyleConstants;
import inetsoft.report.TableLens;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.MaxColsTableLens;
import inetsoft.report.lens.MaxRowsTableLens2;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.SortInfo;
import inetsoft.uql.asset.SortRef;
import inetsoft.uql.erm.CalculateAggregate;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.util.*;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * CrosstabVSAQuery, the crosstab viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CrosstabVSAQuery extends AbstractCrosstabVSAQuery {
   /**
    * Create a crosstab viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param crosstab the specified crosstab to be processed.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    */
   public CrosstabVSAQuery(ViewsheetSandbox box, String crosstab,
                           boolean detail)
   {
      super(box, crosstab, detail);
   }

   public CrosstabVSAQuery(ViewsheetSandbox box, String crosstab,
                           boolean detail, boolean columnLimit)
   {
      this(box, crosstab, detail);
      this.columnLimit = columnLimit;
   }

   /**
    * Get the table.
    * @return the table of the query.
    */
   @Override
   public TableLens getTableLens() throws Exception {
      return getTableLens(true);
   }

   /**
    * Get the table.
    * @param isFinal <tt>true</tt> the tableLens is final for display,
    * <tt>false</tt> otherwise.
    * @return the table of the query.
    */
   public TableLens getTableLens(boolean isFinal) throws Exception {
      final ViewsheetSandbox box = this.box;
      box.lockRead();

      try {
         if(isCancelled()) {
            throw new CancelledException("Query cancelled");
         }

         CrosstabDataVSAssembly cassembly = getVSAssembly();
         VSCrosstabInfo cinfo = cassembly.getVSCrosstabInfo();

         if(cinfo == null) {
            return null;
         }

         DataRef[] cheaders = cinfo.getRuntimeColHeaders();
         DataRef[] rheaders = cinfo.getRuntimeRowHeaders();
         DataRef[] aggs = cinfo.getRuntimeAggregates();

         if(cheaders.length == 0 && rheaders.length == 0 && aggs.length == 0) {
            return null;
         }

         TableLens data = super.getTableLens();

         if(!(data instanceof CrossTabFilter)) {
            return data;
         }

         boolean forCalc = containsCalculator(cinfo.getRuntimeAggregates());
         CrossTabFilter crosstab = (CrossTabFilter) data;
         crosstab.setFillBlankWithZero(cinfo.isFillBlankWithZero());
         crosstab.setSummarySideBySide(cinfo.isSummarySideBySide());
         crosstab.setSuppressRowGrandTotal(!cinfo.isRowTotalVisible());
         crosstab.setSuppressColumnGrandTotal(!cinfo.isColTotalVisible());
         crosstab.setRowTimeSeries(isTimeSeries(rheaders));
         crosstab.setColTimeSeries(isTimeSeries(cheaders));
         crosstab.setSortOthersLast(cinfo.isSortOthersLast());
         crosstab.setForCalc(forCalc);

         for(int i = 0; i < rheaders.length - 1; i++) {
            VSDimensionRef dref = (VSDimensionRef) rheaders[i];
            crosstab.setSuppressRowGroupTotal(!dref.isSubTotalVisible(), i);
         }

         for(int i = 0; i < cheaders.length - 1; i++) {
            VSDimensionRef dref = (VSDimensionRef) cheaders[i];
            crosstab.setSuppressColumnGroupTotal(!dref.isSubTotalVisible(), i);
         }

         if(cinfo.isSummarySideBySide()) {
            crosstab.createVSSpan();
         }

         TableLens base = crosstab;

//         if(forCalc) {
//            CrossCalcFilter calcFilter = new CrossCalcFilter(crosstab, cinfo.getRuntimeAggregates(),
//               false, cinfo.isCalculateTotal());
//            List<CalcColumn> allcalcs = calcFilter.getCalcColumns();
//            List<XDimensionRef> dims = new ArrayList<>();
//
//            boolean hasDateRef = false;
//
//            if(rheaders != null) {
//               for(int i = 0; i < rheaders.length; i++) {
//                  if(!(rheaders[i] instanceof XDimensionRef)) {
//                     continue;
//                  }
//
//                  dims.add((XDimensionRef) rheaders[i]);
//
//                  if(((XDimensionRef) rheaders[i]).isDate()) {
//                     hasDateRef = true;
//                  }
//               }
//            }
//
//            if(!hasDateRef && cheaders != null) {
//               dims.clear();
//
//               for(int i = 0; i < cheaders.length; i++) {
//                  if(!(cheaders[i] instanceof XDimensionRef)) {
//                     continue;
//                  }
//
//                  dims.add((XDimensionRef) cheaders[i]);
//               }
//            }
//
//            VSDataRef innerFld = (dims.size() > 0) ? dims.get(dims.size() - 1) : null;
//            String innerDim = (innerFld != null) ? innerFld.getFullName() : null;
//
//            if(allcalcs != null) {
//               for(CalcColumn calcCol : allcalcs) {
//                  if(calcCol instanceof ValueOfColumn) {
//                     ((AbstractColumn) calcCol).setDimensions(dims);
//                     ((AbstractColumn) calcCol).setInnerDim(innerDim);
//                  }
//               }
//            }
//
//            calcFilter.setFillBlankWithZero(cinfo.isFillBlankWithZero());
//            calcFilter.prepareCalc();
//            base = calcFilter;
//         }

         TableDataVSAssemblyInfo tinfo = (TableDataVSAssemblyInfo) cassembly.getInfo();
         SortInfo sinfo = tinfo.getSortInfo();

         if(sinfo != null && sinfo.getSortCount() > 0) {
            List<Integer> cols = new ArrayList<>();
            List<Boolean> ascs = new ArrayList<>();
            SortRef[] sorts = sinfo.getSorts();

            for(SortRef sort : sorts) {
               if(sort.getOrder() != StyleConstants.SORT_ASC &&
                  sort.getOrder() != StyleConstants.SORT_DESC)
               {
                  continue;
               }

               boolean asc = sort.getOrder() == StyleConstants.SORT_ASC;
               List<Integer> temp = findColumns(crosstab, sort.getName());

               for(Integer col : temp) {
                  int idx = cols.indexOf(col);

                  if(idx >= 0) {
                     cols.remove(idx);
                     ascs.remove(idx);
                  }

                  cols.add(col);
                  ascs.add(asc);
               }
            }

            if(!cols.isEmpty()) {
               int[] cols2 = new int[cols.size()];
               boolean[] ascs2 = new boolean[ascs.size()];

               for(int i = 0; i < cols.size(); i++) {
                  cols2[i] = cols.get(i);
                  ascs2[i] = ascs.get(i);
               }

               base = new CrossTabSortFilter(crosstab, cols2, ascs2);
            }
         }

         CrosstabVSAssemblyInfo info = (CrosstabVSAssemblyInfo) cassembly.getInfo();
         base = CrosstabDcProcessor.hideFirstPeriod(base, info);

         int maxRow = Util.getTableOutputMaxrow();

         if(maxRow > 0 && isFinal) {
            TableLens maxRowTable = new MaxRowsTableLens2(base, maxRow);

            if(columnLimit && maxRowTable.getColCount() > Util.getOrganizationMaxColumn()) {
               Tool.addUserMessage(DataVSAQuery.getColumnLimitNotification());

               return new MaxColsTableLens(maxRowTable, Util.getOrganizationMaxColumn());
            }

            return maxRowTable;
         }

         if(columnLimit && base.getColCount() > Util.getOrganizationMaxColumn()) {
            Tool.addUserMessage(DataVSAQuery.getColumnLimitNotification());

            return new MaxColsTableLens(base, Util.getOrganizationMaxColumn());
         }

         return base;
      }
      finally {
         box.unlockRead();
      }
   }

   private CrosstabDataVSAssembly getVSAssembly() {
      CrosstabDataVSAssembly cassembly = (CrosstabDataVSAssembly) getAssembly();
      VSCrosstabInfo info = cassembly.getVSCrosstabInfo();
      DataRef[][] refs = {info.getRowHeaders(), info.getColHeaders()};
      DataRef[][] rRefs = {info.getRuntimeRowHeaders(), info.getRuntimeColHeaders()};
      List<DataRef> refList = new ArrayList<>();

      for(DataRef[] ref : refs) {
         refList.addAll(Arrays.asList(ref));
      }

      for(DataRef[] rRef : rRefs) {
         refList.addAll(Arrays.asList(rRef));
      }

      refList.stream().filter(Objects::nonNull)
         .filter(r -> r instanceof XDimensionRef)
         .forEach(r -> ((XDimensionRef) r).setSortOthersLast(info.isSortOthersLast()));

      return cassembly;
   }

   private boolean containsCalculator(DataRef[] aggrs) {
      if(aggrs == null || aggrs.length == 0) {
         return false;
      }

      return Arrays.stream(aggrs).anyMatch(ref -> {
         if(!(ref instanceof CalculateAggregate)) {
            return false;
         }

         CalculateAggregate aggr = (CalculateAggregate) ref;
         Calculator calculator = aggr.getCalculator();
         return calculator != null;
      });
   }

   /**
    * Return if need to support timeseries for crosstab header.
    * @param  headers row/col headers.
    */
   private boolean isTimeSeries(DataRef[] headers) {
      DataRef lastHeader =
         headers != null && headers.length > 0 ? headers[headers.length - 1] : null;

      if(lastHeader instanceof VSDimensionRef) {
         VSDimensionRef dim = (VSDimensionRef) lastHeader;
         return dim.isDateTime() && dim.isTimeSeries();
      }

      return false;
   }

   private List<Integer> findColumns(CrossTabFilter crosstab, String name) {
      List<Integer> cols = new ArrayList<>();

      for(int r = 0; r < crosstab.getHeaderRowCount(); r++) {
         for(int c = 0; c < crosstab.getColCount(); c++) {
            Object val = crosstab.getObject(r, c);
            // special handle for empty corner cell
            String header = val == null ? "" : val + "";
            // @by davyc, we'd better use data value to build sort column,
            // so it can be working in different locale env, but if doing so
            // need store some external data in web
            String lheader = Tool.localize(header);

            if(header.equals(name) || lheader.equals(name)) {
               cols.add(c);
            }
         }

         if(!cols.isEmpty()) {
            break;
         }
      }

      return cols;
   }

   private boolean columnLimit = true;

   public static class CrossTabSortFilter extends SortFilter {
      public CrossTabSortFilter(CrossTabFilter table, int[] cols, boolean[] asc) {
         super(table, cols, asc);
         hrow = table.getHeaderRowCount();
         hcol = table.getHeaderColCount();
         keepSpan = !"false".equals(SreeEnv.getProperty("vs.crosstab.keepspan"));

         if(keepSpan) {
            fixCrosstabSpan(table);
         }
      }

      public Rectangle getVSSpan(int r, int c) {
         r = getBaseRowIndex(r);
         Rectangle span = ((CrossTabFilter) getTable()).getVSSpan(r, c);

         if(span != null && span.height > 1 && r >= hrow) {
            span = new Rectangle(span.x, 0, span.width, 1);
         }

         return span;
      }

      @Override
      public Dimension getSpan(int r, int c) {
         if(r < hrow) {
            return super.getSpan(r, c);
         }

         if(keepSpan && spans == null) {
            initSpan();
         }

         if(keepSpan) {
            Object span = spans.get(r, c);
            return span == SparseMatrix.NULL ? null : (Dimension) span;
         }

         return null;
      }

      private void fixCrosstabSpan(CrossTabFilter crosstab) {
         for(int c = 0; c < crosstab.getColCount(); c++) {
            for(int r = 0; crosstab.moreRows(r); r++) {
               Dimension span = crosstab.getSpan(r, c);

               if(span != null) {
                  if(span.width == 1 && span.height == 1) {
                     crosstab.setSpan(r, c, null);
                  }

                  for(int r2 = r; r2 < r + span.height; r2++) {
                     for(int c2 = c; c2 < c + span.width; c2++) {
                        if(r2 == r && c2 == c) {
                           continue;
                        }

                        crosstab.setSpan(r2, c2, null);
                     }
                  }
               }
            }
         }
      }

      private synchronized void initSpan() {
         if(spans != null) {
            return;
         }

         spans = new SparseMatrix();
         BitSet bits = new BitSet();

         for(int c = 0; c < hcol; c++) {
            bits.clear();

            for(int r = 0; moreRows(r); r++) {
               initSpan0(r, c, bits);
            }
         }
      }

      private void initSpan0(int r, int c, BitSet bits) {
         // processed
         if(bits.get(r)) {
            return;
         }

         bits.set(r);
         int br = getBaseRowIndex(r);
         int sbr = findSpanStartRow(br, c);

         // no span
         if(sbr == -1) {
            return;
         }

         Dimension span = getTable().getSpan(sbr, c);

         int spanR = r;
         int spanBaseR = br;
         int spanH = 1;
         r++;

         for(; moreRows(r); r++) {
            br = getBaseRowIndex(r);

            if(br >= sbr && br < sbr + span.height) {
               spanH++;
               continue;
            }
            else {
               if(spanBaseR >= sbr && spanBaseR < sbr + span.height) {
                  spans.set(spanR, c, new Dimension(span.width, spanH));
                  bits.set(spanR);
               }

               spanR = r;
               spanBaseR = br;
               spanH = 1;
            }
         }

         if(spanBaseR >= sbr && spanBaseR < sbr + span.height) {
            spans.set(spanR, c, new Dimension(span.width, spanH));
            bits.set(spanR);
         }
      }

      private int findSpanStartRow(int r, int c) {
         Dimension span;

         for(int i = r; i >= 0; i--) {
            span = getTable().getSpan(i, c);

            if(span != null) {
               return i;
            }
         }

         // means no span
         return -1;
      }

      private int hrow;
      private int hcol;
      private boolean keepSpan;
      private SparseMatrix spans;
   }
}
