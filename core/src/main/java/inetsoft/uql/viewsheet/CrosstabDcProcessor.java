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
package inetsoft.uql.viewsheet;

import inetsoft.util.data.CommonKVModel;
import inetsoft.report.TableLens;
import inetsoft.report.filter.HiddenRowColFilter;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.graph.ChartDcProcessor;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.apache.commons.lang.ArrayUtils;

import java.util.*;
import java.util.stream.Stream;

public class CrosstabDcProcessor {
   public CrosstabDcProcessor(VSCrosstabInfo info,
                              DateComparisonInfo dcInfo,
                              DataRef[] rtRowHeaders,
                              DataRef[] rtColHeaders,
                              DataRef[] rtAggrs) {
      super();

      this.info = info;
      this.dcInfo = dcInfo;
      this.rtRowHeaders = rtRowHeaders;
      this.rtColHeaders = rtColHeaders;
      this.rtAggrs = rtAggrs;
   }

   public void process(String source, Viewsheet vs) {
      CommonKVModel<String, XDimensionRef> dateFields =
         getComparisonDateRef(rtRowHeaders, rtColHeaders);

      if(dateFields == null || dateFields.getKey() == null || dateFields.getValue() == null) {
         info.setRuntimeRowHeaders(rtRowHeaders);
         info.setRuntimeColHeaders(rtColHeaders);
         info.setRuntimeAggregates(rtAggrs);

         return;
      }

      XDimensionRef dateDim = dateFields.getValue();

      if(!dcInfo.isCompareAll() &&
         !dcInfo.periodLevelSameAsGranularityLevel() ||
         !dcInfo.isStdPeriod())
      {
         dateDim.setTimeSeries(false);
      }

      removeOtherSameFields(dateDim);
      //clearSortRankingForDate((VSDimensionRef) dateDim);
      List<XDimensionRef> dcDateDims = new ArrayList<>();
      XDimensionRef periodDim = updateRuntimeHeaders(dateDim, "ROW".equals(dateFields.getKey()));
      info.setDateComparisonRef((XDimensionRef) dateDim.clone());
      dcInfo.updateDateDimensionLevel(dateDim, source, vs, true);

      if(periodDim != null) {
         dcDateDims.add(periodDim);
         ChartDcProcessor.setMonthOfFullWeek(periodDim, dcInfo);
      }

      dcDateDims.add((XDimensionRef) dateDim.clone());
      dcDateDims.add(dateDim);
      int weekOfMonth = dcInfo.getToDateWeekOfMonth();;
      DateComparisonUtil.updateWeekGroupingLevels(dcInfo, rtRowHeaders, weekOfMonth);
      DateComparisonUtil.updateWeekGroupingLevels(dcInfo, rtColHeaders, weekOfMonth);
      Calculator calculator = dcInfo.createComparisonCalc(
         periodDim != null ? periodDim : dateDim, periodDim != null, dcDateDims);
      List<XAggregateRef> dateComparisonAggs = updateRuntimeAggregates(calculator);

      syncComparisonRuntimeRefs(dcDateDims, dateComparisonAggs);
      info.setRuntimeRowHeaders(rtRowHeaders);
      info.setRuntimeColHeaders(rtColHeaders);
      info.setRuntimeAggregates(rtAggrs);
      info.setDcTempGroups(DateComparisonUtil.getAllTempDateGroupRef(dcInfo, source,
         (VSDimensionRef) info.getDateComparisonRef(), vs));
   }

   /**
    * Update runtime headers by date comparison info.
    *
    * @param dateDim the date ref to do date comparison
    * @param fromRow true if the date dim is from row headers, else from col headers.
    * @return the date dim if for standard periods of date comparison.
    */
   private XDimensionRef updateRuntimeHeaders(XDimensionRef dateDim, boolean fromRow) {
      info.setDateComparisonOnRow(Boolean.TRUE.equals(fromRow));

      if(dateDim == null || !dcInfo.isDateSeries()) {
         return null;
      }

      XDimensionRef periodDim = null;

      if(dateDim instanceof VSDimensionRef &&
         (!(dcInfo.getPeriods() instanceof StandardPeriods) ||
         !dcInfo.periodLevelSameAsGranularityLevel()))
      {
         if(dcInfo.getPeriods() instanceof StandardPeriods &&
            !dcInfo.periodLevelSameAsGranularityLevel())
         {
            periodDim = (VSDimensionRef) dateDim.clone();
            periodDim.setDateLevel(dcInfo.getPeriodDateLevel());
            ((VSDimensionRef) periodDim).setDcRange(!dcInfo.isCompareAll());
         }
         else if(dcInfo.getPeriods() instanceof CustomPeriods) {
            periodDim = (VSDimensionRef) dateDim.clone();
            ((VSDimensionRef) periodDim).setDcRange(true);
            ((VSDimensionRef) periodDim).setGroupType(NamedRangeRef.DATA_GROUP + "");
            periodDim.setNamedGroupInfo(
               DateComparisonUtil.createCustomRangeNameGroup((CustomPeriods) dcInfo.getPeriods()));
         }
      }

      if(periodDim != null) {
         if(Boolean.TRUE.equals(fromRow)) {
            rtRowHeaders = addPeriodDim(rtRowHeaders, dateDim, periodDim);
         }
         else if(Boolean.FALSE.equals(fromRow)) {
            rtColHeaders = addPeriodDim(rtColHeaders, dateDim, periodDim);
         }
      }

      return updatePeriodDim(periodDim, dateDim);
   }

   private DataRef[] addPeriodDim(DataRef[] refs, XDimensionRef dateDim, XDimensionRef periodDim) {
      int index = ArrayUtils.indexOf(refs, dateDim);

      if(dcInfo.getInterval().getGranularity() == DateComparisonInfo.ALL) {
         refs[index] = periodDim;
      }
      else {
         refs = (DataRef[]) ArrayUtils.add(refs, index, periodDim);
      }

      return refs;
   }

   /**
    * Update period dimension if dc use standard periods.
    *
    * @param dateDim the date ref to do date comparison
    * @return the period dimension field.
    */
   private XDimensionRef updatePeriodDim(XDimensionRef periodDim, DataRef dateDim) {
      if(periodDim == null && dcInfo.getPeriods() instanceof StandardPeriods &&
         (!dcInfo.isCompareAll() || !dcInfo.periodLevelSameAsGranularityLevel()))
      {
         XDimensionRef peroidDim = (XDimensionRef) dateDim.clone();
         peroidDim.setDateLevel(dcInfo.getPeriodDateLevel());
      }

      return periodDim;
   }

   /**
    * Update runtime aggregates by date comparison.
    *
    * @param calculator the calculator should be applied to runtime aggregates.
    * @return the aggregates list which applied dc calculator.
    */
   private List<XAggregateRef> updateRuntimeAggregates(Calculator calculator) {
      List<XAggregateRef> dcAggrs = new ArrayList<>();
      List<DataRef> newRtFields = new ArrayList<>();

      if(rtAggrs == null || rtAggrs.length == 0) {
         return dcAggrs;
      }

      Arrays.stream(rtAggrs)
         .filter(ref -> ref instanceof VSAggregateRef)
         .forEach(ref -> {
            if(dcInfo.isValuePlus() || dcInfo.isValueOnly()) {
               ((VSAggregateRef) ref).setCalculator(null);
               newRtFields.add(ref);
            }

            if(calculator != null) {
               ref = (DataRef) ref.clone();
               ((VSAggregateRef) ref).setCalculator(calculator);
               dcAggrs.add((VSAggregateRef) ref);
               newRtFields.add(ref);
            }
         });

      if(dcAggrs.size() > 0) {
         rtAggrs = newRtFields.toArray(new DataRef[newRtFields.size()]);
      }

      return dcAggrs;
   }

   /**
    * Find the date type ref to do date comparison, search order is row, col
    * @return CommonKVModel key is axis(ROW or COL), value is date dimension.
    */
   public CommonKVModel<String, XDimensionRef> getComparisonDateRef(DataRef[] rowHeaders,
                                                                    DataRef[] colHeaders)
   {
      return DateComparisonUtil.getComparisonDateRef(rowHeaders, colHeaders);
   }

   /**
    * sync date comparison runtime fields.
    * Since runtime fields will be cleared after setting format or doing other actions,
    * so we need to save the runtime refs
    * and setting back to the runtime fields when appling date comparison to make sure
    * setting can be keep.
    *
    * @param dimensionRefs dimension fields.
    * @param aggregateRefs aggregate fields.
    */
   private void syncComparisonRuntimeRefs(List<XDimensionRef> dimensionRefs,
                                          List<XAggregateRef> aggregateRefs)
   {
      DataRef[] comparisonRefs =
         Stream.concat(dimensionRefs.stream(), aggregateRefs.stream())
            .filter(DataRef.class::isInstance)
            .map(DataRef.class::cast)
            .toArray(DataRef[]::new);

      DataRef[] oldRefs = info.getRuntimeDateComparisonRefs();

      if(oldRefs != null && comparisonRefs != null) {
         for(int i = 0; i < comparisonRefs.length; i++) {
            DataRef ref = comparisonRefs[i];

            if(!(ref instanceof VSDataRef)) {
               continue;
            }

            Optional<VSDataRef> oldRefOp = Arrays.stream(oldRefs)
               .filter(chartRef -> chartRef != null)
               .filter(VSDataRef.class::isInstance)
               .map(VSDataRef.class::cast)
               .filter(dataRef -> Tool.equals(dataRef.getFullName(), ((VSDataRef) ref).getFullName()))
               .findFirst();

            if(!oldRefOp.isPresent()) {
               continue;
            }

            DataRef oldRef = oldRefOp.get();

            if(oldRef instanceof VSDimensionRef && ref instanceof VSDimensionRef) {
               ((VSDimensionRef) ref).setOrder(((VSDimensionRef) oldRef).getOrder());
               ((VSDimensionRef) ref).setSortByColValue(
                  ((VSDimensionRef) oldRef).getSortByColValue());
            }
            else if(oldRef instanceof VSAggregateRef && ref instanceof VSAggregateRef) {
               // sync aggregate.
            }
         }
      }

      if(comparisonRefs != null) {
         comparisonRefs = Arrays.stream(comparisonRefs).map(ref -> {
            ref = (DataRef) ref.clone();

            if(ref instanceof VSDimensionRef) {
               ((VSDimensionRef) ref).setDateLevelValue(((VSDimensionRef) ref).getDateLevel() + "");
            }

            return ref;
         })
            .toArray(DataRef[]::new);
      }

      info.setRuntimeDateComparisonRefs(comparisonRefs);
   }

   /**
    * Remove other dimensions that has same date group with compare dimension.
    * @param dateDim compare date dimension.
    */
   private void removeOtherSameFields(DataRef dateDim) {
      if(dateDim == null) {
         return;
      }

      if(rtRowHeaders != null) {
         rtRowHeaders = removeOtherSameFields(rtRowHeaders, dateDim);
      }

      if(rtColHeaders != null) {
         rtColHeaders = removeOtherSameFields(rtColHeaders, dateDim);
      }
   }

   private DataRef[] removeOtherSameFields(DataRef[] fields, DataRef dateDim) {
      if(fields == null || dateDim == null) {
         return fields;
      }

      return Arrays.stream(fields)
         .filter(field -> field != null)
         .filter(field -> field == dateDim || !Tool.equals(field.getName(), dateDim.getName()))
         .toArray(DataRef[]::new);
   }

   private void clearSortRankingForDate(VSDimensionRef ref) {
      ref.setOrder(XConstants.SORT_ASC);
      ref.setSortByColValue(null);
      ref.setRankingNValue(null);
      ref.setRankingColValue(null);
      ref.setRankingCondition(null);
      ref.setGroupOthersValue("false");
   }

   // for date comparison showing change, an extra period is added to the beginning so
   // the change value is not empty. we hide the extra period in the result similar to chart.
   public static TableLens hideFirstPeriod(TableLens table, CrosstabVSAssemblyInfo info) {
      DateComparisonInfo dcInfo = DateComparisonUtil.getDateComparison(info, info.getViewsheet());

      if(dcInfo == null || dcInfo.getComparisonOption() == DateComparisonInfo.VALUE ||
         !(dcInfo.getPeriods() instanceof StandardPeriods))
      {
         return table;
      }

      Date startDate = dcInfo.getStartDate();
      VSCrosstabInfo crosstab = info.getVSCrosstabInfo();
      boolean onRows = crosstab.isDateComparisonOnRow();
      VSDataRef compRef = info.getDateComparisonRef();
      DataRef[] headers = onRows ? crosstab.getRowHeaders() : crosstab.getColHeaders();
      int idx = -1;
      int cnt = onRows ? table.getRowCount() : table.getColCount();

      for(int i = 0; i < headers.length; i++) {
         if(compRef.getFullName().equals(((VSDataRef) headers[i]).getFullName())) {
            idx = i;
            break;
         }
      }

      if(idx < 0) {
         return table;
      }

      for(int row : new HashSet<>(info.getRowHeights().keySet())) {
         if(info.getRowHeight(row) == 0) {
            info.setRowHeight(row, -1);
         }
      }

      HiddenRowColFilter hiddenRowColFilter = new HiddenRowColFilter(table);

      for(int i = 0; i < cnt; i++) {
         Object val = onRows ? table.getObject(i, idx) : table.getObject(idx, i);

         if(val instanceof Date && ((Date) val).compareTo(startDate) < 0) {
            if(onRows) {
               hiddenRowColFilter.hiddenRow(i);
            }
            else {
               hiddenRowColFilter.hiddenCol(i);
            }
         }
      }

      return hiddenRowColFilter;
   }

   private VSCrosstabInfo info;
   private DateComparisonInfo dcInfo;
   private DataRef[] rtRowHeaders;
   private DataRef[] rtColHeaders;
   private DataRef[] rtAggrs;
}
