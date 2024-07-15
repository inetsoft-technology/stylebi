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
package inetsoft.uql.viewsheet.graph;

import inetsoft.util.data.CommonKVModel;
import inetsoft.graph.aesthetic.*;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.internal.graph.ChangeChartDataProcessor;
import inetsoft.report.internal.graph.ChangeChartTypeProcessor;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.aesthetic.SizeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.apache.commons.lang.ArrayUtils;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

public class ChartDcProcessor {
   public ChartDcProcessor(VSChartInfo info, DateComparisonInfo dcInfo) {
      super();

      this.info = info;
      this.dcInfo = dcInfo;
   }

   public void process(String source, Viewsheet vs) {
      CommonKVModel<String, XDimensionRef> comparisonDate = getComparisonDateRef();

      if(comparisonDate == null || comparisonDate.getKey() == null ||
         comparisonDate.getValue() == null)
      {
         return;
      }

      changeMultiStyle();
      XDimensionRef dateDim = comparisonDate.getValue();
      boolean dimensionFromX = "X".equals(comparisonDate.getKey());
      info.setDcBaseDateOnX(dimensionFromX);

      if(dateDim != null && !dcInfo.isCompareAll() &&
         !dcInfo.periodLevelSameAsGranularityLevel() ||
         !dcInfo.isStdPeriod())
      {
         dateDim.setTimeSeries(false);
      }

      /* @experimental, set title to something more descriptive.
      if(dimensionFromX && info.getChartDescriptor() != null) {
         String title = dcInfo.getInterval().getDescription();
         info.getChartDescriptor()
            .getTitlesDescriptor()
            .getXTitleDescriptor()
            .setTitle(title);
      }
       */

      removeOtherSameFields(dateDim);
      clearSortRankingForDate((VSDimensionRef) dateDim);
      VSDimensionRef periodRef = null;
      boolean seriesDate = dcInfo.isDateSeries();
      List<XDimensionRef> comparisonDateDims = new ArrayList<>();
      DateComparisonPeriods periods = dcInfo.getPeriods();
      boolean useFacet = dcInfo.isUseFacet();
      boolean inner = true;

      if(dateDim != null && seriesDate) {
         if(dateDim instanceof ChartDimensionRef) {
            if(periods instanceof StandardPeriods && !dcInfo.periodLevelSameAsGranularityLevel()) {
               periodRef = (VSDimensionRef) dateDim.clone();
               periodRef.setDateLevel(dcInfo.getPeriodDateLevel());
               periodRef.setDcRange(!dcInfo.isCompareAll());
               comparisonDateDims.add(periodRef);

               setMonthOfFullWeek(periodRef, dcInfo);
            }
            else if(periods instanceof CustomPeriods) {
               CustomPeriods customPeriods = (CustomPeriods) periods;
               periodRef = (VSDimensionRef) dateDim.clone();
               comparisonDateDims.add(periodRef);
               periodRef.setDcRange(true);
               periodRef.setNamedGroupInfo(
                  DateComparisonUtil.createCustomRangeNameGroup(customPeriods));
            }
         }

         if(periodRef != null) {
            ChartRef[] xFields = info.getRTXFields();
            ChartRef[] yFields = info.getRTYFields();

            if(!useFacet) {
               ChartRef[] rtFields = dimensionFromX ? yFields : xFields;
               long aggCount = rtFields == null ? 0 : Arrays.stream(rtFields)
                  .filter(VSAggregateRef.class::isInstance)
                  .count();
               DateComparisonInterval interval = dcInfo.getInterval();
               boolean addToAxis = dcInfo.isStdPeriod() || !dcInfo.isCompareAll() ||
                  dimensionFromX || interval.getGranularity() == DateComparisonInfo.YEAR ||
                  interval.getGranularity() == DateComparisonInfo.QUARTER ||
                  interval.getGranularity() == DateComparisonInfo.MONTH ||
                  // for ALL granunarity, replace the default granularity (year) dim. (64088)
                  dcInfo.getInterval().getGranularity() == DateComparisonInfo.ALL;

               if(addToAxis) {
                  inner = false;

                  if(dimensionFromX) {
                     xFields = addPeriodRef(xFields, periodRef);
                     info.setRTXFields(xFields);
                  }
                  else {
                     yFields = addPeriodRef(yFields, periodRef);
                     info.setRTYFields(yFields);
                  }
               }

               // still apply color if color is not used so it's easier to distinguish
               // different periods.
               if(!hasAestheticField(ChartConstants.AESTHETIC_COLOR) && aggCount == 1) {
                  updateAestheticField(periodRef, ChartConstants.AESTHETIC_COLOR);
               }
            }
            else if(dimensionFromX) {
               if(dcInfo.getInterval().getGranularity() == DateComparisonInfo.ALL) {
                  xFields = addPeriodRef(xFields, periodRef);
                  info.setRTXFields(xFields);
               }
               else {
                  yFields = (ChartRef[]) ArrayUtils.add(yFields, periodRef);
                  info.setRTYFields(yFields);
               }
            }
            else { // dimensionFromY
               if(dcInfo.getInterval().getGranularity() == DateComparisonInfo.ALL) {
                  yFields = addPeriodRef(yFields, periodRef);
                  info.setRTYFields(yFields);
               }
               else {
                  xFields = (ChartRef[]) ArrayUtils.add(xFields, periodRef);
                  info.setRTXFields(xFields);
               }
            }
         }
      }

      XDimensionRef[] dcTempGroups = DateComparisonUtil.getAllTempDateGroupRef(
         dcInfo, source, (VSDimensionRef) dateDim, vs);

      info.setDcTempGroups(dcTempGroups);

      List<XDimensionRef> comparisonBindingDateDims = new ArrayList<>(comparisonDateDims);

      if(periodRef == null && periods instanceof StandardPeriods &&
         (!dcInfo.isCompareAll() || !dcInfo.periodLevelSameAsGranularityLevel()))
      {
         XDimensionRef toDatePeriod = (XDimensionRef) dateDim.clone();
         toDatePeriod.setDateLevel(dcInfo.getPeriodDateLevel());
         comparisonDateDims.add(toDatePeriod);
      }

      comparisonDateDims.add(dateDim);
      comparisonBindingDateDims.add(dateDim);
      dcInfo.updateDateDimensionLevel(dateDim, source, vs, inner);
      DateComparisonUtil.updateWeekGroupingLevels(dcInfo, info, dcInfo.getToDateWeekOfMonth());
      removeAesthetic(comparisonBindingDateDims, dateDim);
      Calculator calculator = dcInfo.createComparisonCalc(periodRef != null ? periodRef : dateDim,
            periodRef != null, comparisonDateDims);

      if(calculator != null && calculator instanceof ValueOfCalc) {
         ((ValueOfCalc) calculator).setDcTempGroups(Arrays.asList(info.getDcTempGroups()));
      }

      List<XAggregateRef> dateComparisonAggs = new ArrayList<>();
      boolean valuePlus = dcInfo.isValuePlus();
      dateComparisonAggs.addAll(updateAggregatesCalc(calculator, info.getRTXFields(), true, valuePlus));
      dateComparisonAggs.addAll(updateAggregatesCalc(calculator, info.getRTYFields(), false, valuePlus));

      fixComparisonTimeSeries(comparisonDateDims, dcInfo);
      syncComparisonRuntimeRefs(comparisonDateDims, dateComparisonAggs);
      info.setDateComparisonRef((VSDataRef) (periodRef != null ? periodRef.clone() :
         dateDim.clone()));
      info.setAppliedCustomPeriodsDc(dcInfo.getPeriods() instanceof CustomPeriods);
      new ChangeChartDataProcessor(info).sortRefs(info, true);
      updateDateComparisonChartType(info);
      updateStaticColorFrame(info, dcInfo);
      setDefaultFormatForAggs(info);
   }

   // for month->week-of-month, avoid breaking week at month boundary by returning
   // the month of the full week (and datePart("wy") returns the week of month of
   // the month containing the full week).
   public static void setMonthOfFullWeek(XDimensionRef periodRef, DateComparisonInfo dcInfo) {
      if(periodRef.getDateLevel() == XConstants.MONTH_DATE_GROUP &&
         dcInfo.getInterval().getContextLevel() == XConstants.WEEK_DATE_GROUP)
      {
         periodRef.setDateLevel(DateRangeRef.MONTH_OF_FULL_WEEK);
      }
   }

   // add custom period to the fields. for ALL granularity, date is not further grouped so
   // we just replace the last group (e.g. Year) with the custom period named group.
   private ChartRef[] addPeriodRef(ChartRef[] xFields, VSDimensionRef periodRef) {
      if(dcInfo.getInterval().getGranularity() == DateComparisonInfo.ALL) {
         xFields[xFields.length - 1] = (ChartRef) periodRef;
      }
      else {
         xFields = (ChartRef[]) ArrayUtils.add(xFields, periodRef);
      }

      return xFields;
   }

   private void setDefaultFormatForAggs(VSChartInfo info) {
      if(info == null) {
         return;
      }

      setDefaultFormatForAxisAggs(info, info.getRTXFields(), true);
      setDefaultFormatForAxisAggs(info, info.getRTYFields(), false);

      if(dcInfo.isValuePlus() && !info.isSeparatedGraph())
      {
         if(info.getRTAxisDescriptor() != null
            && info.getRTAxisDescriptor().getAxisLabelTextFormat() != null)
         {
            GraphFormatUtil.setDefaultNumberFormat(
               info.getRTAxisDescriptor().getAxisLabelTextFormat().getFormat());
         }

         if(info.getRTAxisDescriptor2() != null
            && info.getRTAxisDescriptor2().getAxisLabelTextFormat() != null)
         {
            GraphFormatUtil.setDefaultNumberFormat(
               info.getRTAxisDescriptor2().getAxisLabelTextFormat().getFormat());
         }
      }
   }

   private void setDefaultFormatForAxisAggs(VSChartInfo info, ChartRef[] rtxFields, boolean x) {
      if(rtxFields != null) {
         Arrays.stream(rtxFields)
            .filter(VSChartAggregateRef.class::isInstance)
            .map(VSChartAggregateRef.class::cast)
            .forEach(agg -> {
               int dType;

               if(agg.isSecondaryY()) {
                  dType = x ? ChartConstants.DROP_REGION_X2 : ChartConstants.DROP_REGION_Y2;
               }
               else {
                  dType = x ? ChartConstants.DROP_REGION_X : ChartConstants.DROP_REGION_Y;
               }

               Calculator calculator = agg.getCalculator();
               GraphFormatUtil.setDefaultNumberFormat(info.getChartDescriptor(), info,
                  agg.getDataType(), agg, dType, true,
                  calculator == null ? false : calculator.isPercent());
            });
      }
   }

   private void changeMultiStyle() {
      if(!dcInfo.isValuePlus() && !info.isMultiStyles()) {
         info.setRuntimeMulti(false);
      }
      else {
         boolean omulti = info.isMultiStyles();
         info.setRuntimeMulti(true);
         int chartType = info.getRTChartType();
         new ChangeChartTypeProcessor(chartType, chartType,
            omulti, true, null, info, false, info.getChartDescriptor())
            .processMultiChanged(true);
         LegendsDescriptor legendsDescriptor = info.getChartDescriptor().getLegendsDescriptor();
         List<ChartAggregateRef> aestheticAggregateRefs = info.getAestheticAggregateRefs(true);

         //update runtime chart type to aggregate ref.
         if(aestheticAggregateRefs.size() > 0) {
            aestheticAggregateRefs.forEach(aggregateRef -> {
               aggregateRef.setRTChartType(chartType);

               if(!omulti) {
                  if(aggregateRef.getColorField() != null) {
                     syncLegendVisible(legendsDescriptor,
                        aggregateRef.getColorField().getLegendDescriptor(),
                        ChartConstants.AESTHETIC_COLOR);
                  }

                  if(aggregateRef.getShapeField() != null) {
                     syncLegendVisible(legendsDescriptor,
                        aggregateRef.getShapeField().getLegendDescriptor(),
                        ChartConstants.AESTHETIC_SHAPE);
                  }

                  if(aggregateRef.getSizeField() != null) {
                     syncLegendVisible(legendsDescriptor,
                        aggregateRef.getSizeField().getLegendDescriptor(),
                        ChartConstants.AESTHETIC_SIZE);
                  }
               }
            });
         }
      }
   }

   private void syncLegendVisible(LegendsDescriptor from, LegendDescriptor target, int type) {
      if(from == null || target == null) {
         return;
      }

      if(type == ChartConstants.AESTHETIC_COLOR && from.getColorLegendDescriptor() != null) {
         target.setVisible(from.getColorLegendDescriptor().isVisible());
      }
      else if(type == ChartConstants.AESTHETIC_SHAPE && from.getShapeLegendDescriptor() != null) {
         target.setVisible(from.getShapeLegendDescriptor().isVisible());
      }
      else if(type == ChartConstants.AESTHETIC_SIZE && from.getSizeLegendDescriptor() != null) {
         target.setVisible(from.getSizeLegendDescriptor().isVisible());
      }
   }

   /**
    * Remove other dimensions that has same date group with compare dimension.
    * @param dateDim compare date dimension.
    */
   private void removeOtherSameFields(DataRef dateDim) {
      if(dateDim == null) {
         return;
      }

      ChartRef[] fields = info.getRTXFields();

      if(fields != null) {
         info.setRTXFields(removeAxisOtherSameFields(fields, dateDim));
      }

      fields = info.getRTYFields();

      if(fields != null) {
         info.setRTYFields(removeAxisOtherSameFields(fields, dateDim));
      }
   }

   private ChartRef[] removeAxisOtherSameFields(ChartRef[] axisFields, DataRef dateDim) {
      if(axisFields == null || dateDim == null) {
         return axisFields;
      }

      return Arrays.stream(axisFields)
         .filter(field -> field != null)
         .filter(field -> field == dateDim || !Tool.equals(field.getName(), dateDim.getName()))
         .toArray(ChartRef[]::new);
   }

   /**
    * Remove the Aesthetic group when it is same with comparison ref
    * but binding refs do not contains it.
    * @param comparisonBindingDateDims comparison ref to binding,
    * @param comparisonDim comparison ref.
    */
   private void removeAesthetic(List<XDimensionRef> comparisonBindingDateDims,
                                XDimensionRef comparisonDim)
   {
      if(info.isMultiStyles()) {
         List<ChartAggregateRef> aggs = info.getAestheticAggregateRefs(true);

         if(aggs != null) {
            aggs.forEach(agg ->
               removeAestheticRTDateRef(agg, comparisonBindingDateDims, comparisonDim));
         }
      }
      else {
         removeAestheticRTDateRef(info, comparisonBindingDateDims, comparisonDim);
      }
   }

   private void removeAestheticRTDateRef(ChartBindable bindable,
                                         List<XDimensionRef> comparisonBindingDateDims,
                                         XDimensionRef comparisonDim)
   {
      if(bindable == null) {
         return;
      }

      removeAestheticRTDateRef(bindable.getColorField(), comparisonBindingDateDims, comparisonDim);
      removeAestheticRTDateRef(bindable.getShapeField(), comparisonBindingDateDims, comparisonDim);
      removeAestheticRTDateRef(bindable.getSizeField(), comparisonBindingDateDims, comparisonDim);
      removeAestheticRTDateRef(bindable.getTextField(), comparisonBindingDateDims, comparisonDim);
   }

   private void removeAestheticRTDateRef(AestheticRef aestheticRef,
                                         List<XDimensionRef> comparisonBindingDateDims,
                                         XDimensionRef comparisonDim)
   {
      if(aestheticRef instanceof VSAestheticRef &&
         aestheticRef.getRTDataRef() instanceof ChartRef)
      {
         ChartRef ref = (ChartRef) aestheticRef.getRTDataRef();

         if(!Tool.equals(ref.getName(), comparisonDim.getName())) {
            return;
         }

         boolean find = comparisonBindingDateDims.stream()
            .anyMatch(dateDim -> Tool.equals(dateDim.getFullName(), ref.getFullName()));

         if(!find) {
            ((VSAestheticRef) aestheticRef).setRTDataRef(null);
         }
      }
   }

   /**
    * Update the calculator for x or y axis aggregates.
    *
    * @param calculator new calculator.
    * @param refs       axis refs.
    * @param x          whether is x axis else is y ref.
    * @param valuePlus  true if showing value in addition to change/percent.
    *
    * @return updated aggregates.
    */
   private List<XAggregateRef> updateAggregatesCalc(Calculator calculator, ChartRef[] refs,
                                                    boolean x, boolean valuePlus)
   {
      List<XAggregateRef> aggs = new ArrayList<>();
      ChartRef[] axisFields = x ? info.getRTXFields() : info.getRTYFields();

      if(axisFields != null) {
         Arrays.stream(axisFields)
            .filter(VSChartAggregateRef.class::isInstance)
            .map(VSChartAggregateRef.class::cast)
            .forEach(ref -> {
               if(ref.getCalculator() != null && calculator == null) {
                  aggs.add(ref);
               }

               ref.setCalculator(null);
            });
      }

      if(refs != null) {
         Arrays.stream(refs)
            .forEach(ref -> {
                  if(ref instanceof VSChartAggregateRef) {
                     VSChartAggregateRef aref = (VSChartAggregateRef) ref;

                     if(calculator != null ||
                        (aref.getColorField() != null && aref.getColorField().isRuntime()))
                     {
                        if(valuePlus) {
                           aggs.add(aref);
                           aref.setSecondaryY(false);
                           aref = (VSChartAggregateRef) aref.clone();
                           aref.setSecondaryY(true);
                        }

                        aref.setCalculator(calculator);
                        aggs.add(aref);
                     }
                  }
            });
      }

      /* @dcColorRemove
      if(info.isMultiStyles() && aggs.size() > 0 && dcInfo.getDcColorFrameWrapper() != null) {
         aggs.stream()
            .filter(aggr -> ((ChartAggregateRef) aggr).getColorField() != null &&
               aggr.getCalculator() != null)
            .forEach(aggr -> ((ChartAggregateRef) aggr).getColorField()
               .setVisualFrameWrapper(dcInfo.getDcColorFrameWrapper()));

      }
       */

      if(aggs.size() > 0 && valuePlus) {
         ChartRef[] addAggs = aggs.stream()
            .filter(agg -> agg.getCalculator() != null)
            .toArray(ChartRef[]::new);
         axisFields = (ChartRef[]) ArrayUtils.addAll(axisFields, addAggs);

         if(x) {
            info.setRTXFields(axisFields);
         }
         else {
            info.setRTYFields(axisFields);
         }
      }

      return aggs;
   }


   /**
    * Find the date type ref to do date comparison, search order is x, y
    *
    * @return CommonKVModel key is axis(X or Y), value is date dimension.
    */
   public CommonKVModel<String, XDimensionRef> getComparisonDateRef() {
      if(info == null) {
         return null;
      }

      CommonKVModel<String, XDimensionRef> result = new CommonKVModel<>();
      ChartRef[] xFields = info.getRTXFields();
      ChartRef[] yFields = info.getRTYFields();
      DataRef dateDim = null;
      Boolean dimensionFromX = null;

      if(xFields != null) {
         DataRef findDim = DateComparisonUtil.findDateDimension(xFields);

         if(findDim != null && containsAggregate(yFields)) {
            dimensionFromX = true;
            dateDim = findDim;
         }
      }

      if(dateDim == null && yFields != null) {
         DataRef findDim = DateComparisonUtil.findDateDimension(yFields);

         if(findDim != null && containsAggregate(xFields)) {
            dimensionFromX = false;
            dateDim = findDim;
         }
      }

      if(!(dateDim instanceof XDimensionRef)) {
         return null;
      }

      result.setKey(dimensionFromX ? "X" : "Y");
      result.setValue((XDimensionRef) dateDim);

      return result;
   }

   /**
    * Whether contains the aggregate.
    * @param fields fields to be checked.
    *@return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   private boolean containsAggregate(DataRef[] fields) {
      return Arrays.stream(fields).anyMatch(VSAggregateRef.class::isInstance);
   }

   /**
    * Update the binding aesthetic field by dimension.
    *
    * @param dim dimension to been binding.
    * @param  aestheticType aestheticType in ChartConstants.
    */
   private void updateAestheticField(VSDimensionRef dim, int aestheticType) {
      if(info.isMultiStyles()) {
         List<ChartAggregateRef> aggs = info.getAestheticAggregateRefs(true);

         if(aggs != null) {
            aggs.forEach(agg -> updateAestheticField(agg, dim, aestheticType));
         }
      }
      else {
         updateAestheticField(info, dim, aestheticType);
      }
   }

   /**
    * Update the binding color field by dimension.
    *
    * @param bindable binding to update
    * @param dim      dimension to be set to binding aesthetic.
    * @param aestheticType aestheticType in ChartConstants.
    */
   private void updateAestheticField(ChartBindable bindable, VSDimensionRef dim, int aestheticType)
   {
      if(bindable == null) {
         return;
      }

      AestheticRef aestheticRef = null;

      if(ChartConstants.AESTHETIC_COLOR == aestheticType) {
         aestheticRef = bindable.getColorField();
      }
      else if(ChartConstants.AESTHETIC_SHAPE == aestheticType) {
         aestheticRef = bindable.getShapeField();
      }
      else if(ChartConstants.AESTHETIC_SIZE == aestheticType) {
         aestheticRef = bindable.getSizeField();
      }
      else if(ChartConstants.AESTHETIC_TEXT == aestheticType) {
         aestheticRef = bindable.getTextField();
      }

      Integer updateAestheticType = aestheticType;
      AestheticRef nAestheticRef = null;
      dim = (VSDimensionRef) dim.clone();

      if(aestheticRef instanceof VSAestheticRef && !aestheticRef.isRuntime()) {
         // move current color field to shape if shape is empty.
         if(ChartConstants.AESTHETIC_COLOR == aestheticType && bindable.getRTShapeField() == null) {
            VSAestheticRef aref = createAestheticRef(((VSAestheticRef) aestheticRef).getRTDataRef());
            bindable.setShapeField(aref);
            nAestheticRef = aref;
            updateAestheticType = ChartConstants.AESTHETIC_SHAPE;
         }

         ((VSAestheticRef) aestheticRef).setRTDataRef(dim);
      }
      else {
         VSAestheticRef aref = createAestheticRef(dim);

         if(ChartConstants.AESTHETIC_COLOR == aestheticType) {
            bindable.setColorField(aref);
         }
         else if(ChartConstants.AESTHETIC_SHAPE == aestheticType) {
            bindable.setShapeField(aref);
         }
         else if(ChartConstants.AESTHETIC_SIZE == aestheticType) {
            bindable.setSizeField(aref);
         }
         else if(ChartConstants.AESTHETIC_TEXT == aestheticType) {
            bindable.setTextField(aref);
         }

         updateAestheticType = aestheticType;
         nAestheticRef = aref;
      }

      if(nAestheticRef != null && updateAestheticType != null) {
         GraphUtil.fixVisualFrame(nAestheticRef, updateAestheticType, bindable.getRTChartType(), info);
      }
   }

   private VSAestheticRef createAestheticRef(VSDataRef dim) {
      VSAestheticRef aref = new VSAestheticRef();
      aref.setDataRef(dim);
      aref.setRTDataRef(dim);
      aref.setRuntime(true);
      // @dcColorRemove
      //aref.setVisualFrameWrapper(dcInfo.getDcColorFrameWrapper());
      return aref;
   }

   private void clearSortRankingForDate(VSDimensionRef ref) {
      ref.setOrder(XConstants.SORT_ASC);
      ref.setSortByColValue(null);
      ref.setRankingNValue(null);
      ref.setRankingColValue(null);
      ref.setRankingCondition(null);
      ref.setGroupOthersValue("false");
   }

   /**
    * Fix the dimension time series, party date level do not support time series.
    * @param comparisonDateDims date comparison dimensions.
    */
   private void fixComparisonTimeSeries(List<XDimensionRef> comparisonDateDims,
                                        DateComparisonInfo dcInfo)
   {
      if(comparisonDateDims == null) {
         return;
      }

      // if only single point in a serie, use bar instead of line.
      // (2 years same-month for each year group by month)
      DateComparisonInterval dcInterval = dcInfo.getInterval();
      boolean singlePointPerSerie = dcInfo.isStdPeriod() &&
         !dcInfo.isCompareAll() &&
         (dcInterval.getGranularity() & dcInterval.getLevel()) == dcInterval.getGranularity() &&
         dcInterval.getContextLevel() == ((StandardPeriods) dcInfo.getPeriods()).getDateLevel() &&
         DateComparisonUtil.dcIntervalLevelToDateGroupLevel(dcInterval.getLevel())
            != dcInterval.getContextLevel();

      for(XDimensionRef dref : comparisonDateDims) {
         boolean dateType = info.isDateDimension(dref, dref.getDateLevel());

         if(!dateType || singlePointPerSerie) {
            dref.setTimeSeries(false);
         }
      }
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
      ChartRef[] comparisonRefs =
         Stream.concat(dimensionRefs.stream(), aggregateRefs.stream())
            .filter(ChartRef.class::isInstance)
            .map(ChartRef.class::cast)
            .toArray(ChartRef[]::new);
      ChartRef[] oldRefs = info.getRuntimeDateComparisonRefs();

      for(int i = 0; i < comparisonRefs.length; i++) {
         ChartRef ref = comparisonRefs[i];

         if(ref == null) {
            continue;
         }

         Optional<ChartRef> oldRefOp = Arrays.stream(oldRefs)
            .filter(chartRef -> chartRef != null)
            .filter(chartRef -> Tool.equals(chartRef.getFullName(), ref.getFullName()))
            .findFirst();

         if(!oldRefOp.isPresent()) {
            continue;
         }

         ChartRef oldRef = oldRefOp.get();

         if(oldRef instanceof HighlightRef && ref instanceof HighlightRef) {
            ((HighlightRef) ref).setHighlightGroup(((HighlightRef) oldRef).getHighlightGroup());
            ((HighlightRef) ref).setTextHighlightGroup(((HighlightRef) oldRef).getTextHighlightGroup());
         }

         if(oldRef instanceof HyperlinkRef && ref instanceof HyperlinkRef) {
            ((HyperlinkRef) ref).setHyperlink(((HyperlinkRef) oldRef).getHyperlink());
         }

         if(oldRef instanceof VSChartDimensionRef && ref instanceof VSChartDimensionRef) {
            ref.setAxisDescriptor(oldRef.getAxisDescriptor());
            ((VSChartDimensionRef) ref).setRTAxisDescriptor(null);
            ref.setTextFormat(oldRef.getTextFormat());
         }
         else if(oldRef instanceof VSChartAggregateRef && ref instanceof VSChartAggregateRef) {
            VSChartAggregateRef agg = (VSChartAggregateRef) ref;
            VSChartAggregateRef oldAgg = (VSChartAggregateRef) oldRef;

            if(agg.getCalculator() != null) {
               ref.setTextFormat(oldRef.getTextFormat());
            }

            ref.setAxisDescriptor(oldRef.getAxisDescriptor());
            syncAestheticRefs(agg.getColorField(), oldAgg.getColorField());
            syncAestheticRefs(agg.getShapeField(), oldAgg.getShapeField());
            syncAestheticRefs(agg.getSizeField(), oldAgg.getSizeField());
            syncAestheticRefs(agg.getTextField(), oldAgg.getTextField());
         }
      }

      comparisonRefs = Arrays.stream(comparisonRefs)
         .map(ref -> {
            ref = (ChartRef) ref.clone();

            if(ref instanceof VSDimensionRef) {
               ((VSDimensionRef) ref)
                  .setDateLevelValue(((VSDimensionRef) ref).getDateLevel() + "");
            }

            return ref;
         })
         .toArray(ChartRef[]::new);

      info.setRuntimeDateComparisonRefs(comparisonRefs);
   }

   private void syncAestheticRefs(AestheticRef newRef, AestheticRef oldRef) {
      if(oldRef != null && newRef != null) {
         newRef.setLegendDescriptor(oldRef.getLegendDescriptor());
      }
   }

   public void updateDateComparisonChartType(VSChartInfo vsChartInfo) {
      VSDataRef comparisonRef = vsChartInfo.getDateComparisonRef();
      // if chart is stacked, keep it stacked instead of changing it to side-by-side. (55095)
      boolean stacked = isStackedChart(vsChartInfo, comparisonRef);
      int tempChartType = stacked ? GraphTypes.CHART_BAR_STACK : GraphTypes.CHART_BAR;
      int barStyle = GraphTypeUtil.getAvailableAutoChartType(tempChartType, true, stacked, false);

      if(dcInfo != null && !dcInfo.invalid()) {
         if(dcInfo.isValuePlus()) {
            vsChartInfo.setRuntimeSeparated(false);
            List<ChartAggregateRef> aggs = vsChartInfo.getAestheticAggregateRefs(true);
            boolean preferLine = dcInfo.isUseFacet();

            for(ChartAggregateRef agg : aggs) {
               if(agg == null) {
                  continue;
               }

               // for value & change, show change as line in sub-graphs, and as point if
               // bars are side-by-side
               int temp = preferLine ? GraphTypes.CHART_LINE : GraphTypes.CHART_POINT;
               int newType = agg.getCalculator() == null ? barStyle :
                  GraphTypeUtil.getAvailableAutoChartType(temp, true, stacked, false);

               changeRuntimeType(agg, newType);
            }
         }
         else {
            if(info.isMultiStyles()) {
               List<ChartAggregateRef> aggs = vsChartInfo.getAestheticAggregateRefs(true);

               for(ChartAggregateRef agg : aggs) {
                  if(agg == null) {
                     continue;
                  }

                  changeRuntimeType(agg, barStyle);
               }
            }
            else {
               changeRuntimeType(info, barStyle);

               // VSSizeFrameStrategy.supportsFieldFrame() is true, causing
               // VSFrameVisitor.createFrame() to get the size frame from each aggregate.
               // we need to set the size frame on each aggr for it to be used.
               for(VSDataRef aggr : vsChartInfo.getAggregateRefs()) {
                  fixDCSizeFrame((ChartBindable) aggr, barStyle);
               }
            }
         }

         GraphUtil.fixVisualFrames(vsChartInfo, true);
      }
      else {
         if(GraphTypes.CHART_AUTO == vsChartInfo.getChartType() && !vsChartInfo.isMultiStyles()) {
            new ChangeChartTypeProcessor(vsChartInfo.getRTChartType(), vsChartInfo.getRTChartType(),
               null, vsChartInfo).fixShapeField(vsChartInfo, vsChartInfo, vsChartInfo.getRTChartType());
         }
      }
   }

   private boolean isStackedChart(VSChartInfo cinfo, VSDataRef comparisonRef) {
      int ctype = cinfo.getChartType();
      return (GraphTypes.isStack(ctype) ||
         // this should be same as AbstractChartInfo.getRTChartType
         ctype == GraphTypes.CHART_AUTO ||
         // for line and point, it's generally not set to stack since multiple lines/points
         // can be shown well without stacking. we should change to stack since the dc uses
         // bar as the main chart type, and side by side can easily lead to overcrowding.
         // (wizard may set to line/point without stacking if there is no aesthetic binding).
         ctype == GraphTypes.CHART_LINE || ctype == GraphTypes. CHART_POINT) &&
         Arrays.stream(cinfo.getAestheticRefs(true))
            .anyMatch(a -> a != null && a.getDataRef() instanceof ChartDimensionRef &&
               !Tool.equals(a.getDataRef().getName(), comparisonRef.getName()));
   }

   private boolean hasAestheticField(int aestheticType) {
      if(info.isMultiStyles()) {
         List<ChartAggregateRef> aggrs = info.getAestheticAggregateRefs(true);

         return aggrs != null && aggrs.stream()
            .filter(aggr -> hasAestheticField(aggr, aestheticType))
            .findAny().isPresent();
      }
      else {
         return hasAestheticField(info, aestheticType);
      }
   }

   private boolean hasAestheticField(ChartBindable bindable, int aestheticType) {
      if(bindable == null) {
         return false;
      }

      if(ChartConstants.AESTHETIC_COLOR == aestheticType) {
         return bindable.getColorField() != null;
      }
      else if(ChartConstants.AESTHETIC_SHAPE == aestheticType) {
         return bindable.getShapeField() != null;
      }
      else if(ChartConstants.AESTHETIC_SIZE == aestheticType) {
         return bindable.getSizeField() != null;
      }
      else if(ChartConstants.AESTHETIC_TEXT == aestheticType) {
         return bindable.getTextField() != null;
      }

      return false;
   }

   /**
    * Update the static color frame when comparison option is value, because will add a new agg.
    * @param dinfo date comparison info.
    */
   private void updateStaticColorFrame(VSChartInfo vsChartInfo, DateComparisonInfo dinfo) {
      if(dinfo != null && vsChartInfo.getRTColorField() == null) {
         VSDataRef[] fields = vsChartInfo.getRTFields(true, false, false, false);
         VSChartAggregateRef[] aggrs = fields == null ? null : Arrays.stream(fields)
            .filter(a -> a instanceof VSChartAggregateRef)
            .toArray(VSChartAggregateRef[]::new);

         if(aggrs == null) {
            return;
         }

         /* @dcColorRemove
         //refreshDcColorFrame(aggrs, dinfo);
         VSChartAggregateRef[] calcs = Arrays.stream(aggrs)
            .filter(a -> a.getCalculator() != null)
            .toArray(VSChartAggregateRef[]::new);

         // only apply dc palette if changes showing as point/line on top of bars
         if(calcs.length == aggrs.length) {
            return;
         }

         for(int i = 0; i < calcs.length; i++) {
            VSChartAggregateRef aggr = calcs[i];

            if(aggr.getColorFrameWrapper() instanceof StaticColorFrameWrapper &&
               dcInfo.getDcColorFrameWrapper() != null)
            {
               VisualFrame visualFrame = dcInfo.getDcColorFrameWrapper().getVisualFrame();
               CategoricalColorFrame categoricalColorFrame = (CategoricalColorFrame) visualFrame;
               ((StaticColorFrameWrapper) aggr.getColorFrameWrapper())
                  .setDcRuntimeColor(categoricalColorFrame.getColor(i));
            }

            GraphUtil.fixStaticColorFrame(aggr, vsChartInfo, aggr, true);
         }
          */

         List<Color> barColors = new ArrayList<>();

         // set point/line to the same color as bar.
         for(VSChartAggregateRef aggr : aggrs) {
            if(aggr.getCalculator() != null) {
               if(aggr.getColorFrameWrapper() instanceof StaticColorFrameWrapper &&
                  !barColors.isEmpty())
               {
                  ((StaticColorFrameWrapper) aggr.getColorFrameWrapper())
                     .setDcRuntimeColor(barColors.remove(0));
                  // don't show change on legend since value is already shown with same color.
                  aggr.getColorFrame().getLegendSpec().setVisible(false);
               }
            }
            else {
               if(aggr.getColorFrameWrapper() instanceof StaticColorFrameWrapper) {
                  barColors.add(aggr.getColorFrame().getColor(null));
               }
            }
         }
      }
   }

   /**
    * Refresh colors for trend points/lines, avoid duplicated with original xy aggregates.
    */
   /* @dcColorRemove
   private void refreshDcColorFrame(VSChartAggregateRef[] xyaggrs, DateComparisonInfo dinfo) {
      List<VSChartAggregateRef> aggrs = Arrays.stream(xyaggrs)
         .filter(aggr -> aggr.getCalculator() == null && aggr.getColorField() == null)
         .collect(Collectors.toList());

      // don't need to refresh if have color field.
      if(aggrs.size() == 0) {
         return;
      }

      List<Color> usedColors = Arrays.stream(xyaggrs)
         .filter(aggr -> aggr.getCalculator() == null && aggr.getColorField() == null)
         .map(aggr -> aggr.getColorFrame())
         .filter(frame -> frame instanceof StaticColorFrame)
         .map(frame -> ((StaticColorFrame) frame).getColor())
         .collect(Collectors.toList());

      CategoricalColorFrame frame = (CategoricalColorFrame)
         dinfo.getDcColorFrameWrapper().getVisualFrame();
      List<Color> list = new ArrayList<>();

      for(int i = 0; i < xyaggrs.length + 5 && i < frame.getColorCount(); i++) {
         Color color = frame.getColor(i);

         if(!usedColors.contains(color)) {
            list.add(color);
         }
      }

      for(int i = 0; i < list.size(); i++) {
         frame.setDefaultColor(i, list.get(i));
      }
   }
    */

   private void changeRuntimeType(ChartBindable bindable, int newType) {
      int oldType = bindable.getRTChartType();
      bindable.setRTChartType(newType);

      if(oldType != newType) {
         fixDCSizeFrame(bindable, newType);

         if(GraphTypes.isPoint(newType)) {
            GShape shape = GShape.CIRCLE.create(true, true);
            shape.setLineColor(Color.WHITE);
            bindable.setShapeFrame(new StaticShapeFrame(shape));
         }
      }
   }

   private void fixDCSizeFrame(ChartBindable bindable, int chartType) {
      SizeFrameWrapper sizeFrameWrapper = bindable.getSizeFrameWrapper();

      if(sizeFrameWrapper == null) {
         return;
      }

      sizeFrameWrapper = (SizeFrameWrapper) sizeFrameWrapper.clone();
      VisualFrame sizeFrame = sizeFrameWrapper.getVisualFrame();

      if(sizeFrame instanceof StaticSizeFrame) {
         switch(chartType) {
         // bars are more numerous in date comparison and tend to be small, use more space
         // so they are easier to read.
         case GraphTypes.CHART_BAR:
         case GraphTypes.CHART_BAR_STACK:
            ((StaticSizeFrame) sizeFrame).setSize(25);
            break;
         // make line/point larger so it's easier to read
         case GraphTypes.CHART_LINE:
         case GraphTypes.CHART_LINE_STACK:
         case GraphTypes.CHART_POINT:
         case GraphTypes.CHART_POINT_STACK:
            ((StaticSizeFrame) sizeFrame).setSize(3);
            break;
         }

         if(bindable instanceof VSChartAggregateRef) {
            ((VSChartAggregateRef) bindable).setRuntimeSizeframe(sizeFrameWrapper);
         }
         else if(bindable instanceof VSChartInfo) {
            ((VSChartInfo) bindable).setRuntimeSizeFrame(sizeFrameWrapper);
         }
         else {
            bindable.setSizeFrame((StaticSizeFrame) sizeFrame);
         }
      }

      if(bindable.getSizeField() == null) {
         return;
      }

      VisualFrame visualFrame = bindable.getSizeField().getVisualFrame();

      if(!(visualFrame instanceof LinearSizeFrame)) {
         return;
      }

      LinearSizeFrame linearSizeFrame = (LinearSizeFrame) visualFrame;

      if(!GraphTypes.isBar(chartType) &&
         linearSizeFrame.getLargest() == linearSizeFrame.getSmallest())
      {
//         linearSizeFrame.setSmallest(1);
//         linearSizeFrame.setLargest(linearSizeFrame.getMax());
      }
   }

   private VSChartInfo info;
   private DateComparisonInfo dcInfo;
}
