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
package inetsoft.web.viewsheet.service;

import inetsoft.report.composition.graph.*;
import inetsoft.report.filter.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.model.property.*;
import inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ChartPropertyService {
   @Autowired
   public ChartPropertyService(VisualFrameModelFactoryService visualFrameModelFactoryService) {
      this.visualService = visualFrameModelFactoryService;
   }

   /**
    * Check if the chart type is supported.
    */
   public boolean isSupported(ChartInfo info, String methodName, boolean all) {
      if(!info.isMultiStyles()) {
         int chartType = info.getChartType();
         int rtype = info.getRTChartType();

         if("effectEnabled".equals(methodName)) {
            return effectEnabled(chartType, rtype, info);
         }
         else if("isSparklineSupported".equals(methodName)) {
            return isSparklineSupported(chartType, rtype, info);
         }

      }
      else {
         DataRef[] arefs = new DataRef[0];

         arefs = addChartRefs(arefs, info.getRTXFields());
         arefs = addChartRefs(arefs, info.getRTYFields());

         for(DataRef aref : arefs) {
            if(!(aref instanceof ChartAggregateRef)) {
               continue;
            }

            int chartType = ((ChartAggregateRef) aref).getChartType();
            int rtype = ((ChartAggregateRef) aref).getRTChartType();
            boolean rc = true;

            if("effectEnabled".equals(methodName)) {
               rc = effectEnabled(chartType, rtype, null);
            }
            else if("isSparklineSupported".equals(methodName)) {
               rc = isSparklineSupported(chartType, rtype, info);
            }

            if(!all && rc) {
               return true;
            }
            else if(all && !rc) {
               return false;
            }
         }
      }

      return all;
   }

   /**
    * Check if the apply effect check box is enabled.
    */
   public boolean effectEnabled(int chartType, int rtype, ChartInfo info) {
      if(chartType == GraphTypes.CHART_BAR ||
         chartType == GraphTypes.CHART_BAR_STACK ||
         chartType == GraphTypes.CHART_FUNNEL ||
         chartType == GraphTypes.CHART_PIE ||
         chartType == GraphTypes.CHART_DONUT ||
         chartType == GraphTypes.CHART_WATERFALL ||
         chartType == GraphTypes.CHART_PARETO ||
         chartType == GraphTypes.CHART_POINT ||
         chartType == GraphTypes.CHART_POINT_STACK ||
         chartType == GraphTypes.CHART_INTERVAL ||
         GraphUtil.containsMapPoint(info) && !GraphTypes.isContour(chartType))
      {
         return true;
      }
      else if(chartType == GraphTypes.CHART_AUTO &&
         (rtype == GraphTypes.CHART_BAR ||
            rtype == GraphTypes.CHART_BAR_STACK ||
            rtype == GraphTypes.CHART_POINT ||
            rtype == GraphTypes.CHART_POINT_STACK))
      {
         return true;
      }

      return false;
   }

   /**
    * Check if the chart is a bar or line.
    */
   public static boolean isSparklineSupported(int chartType, int rtype, ChartInfo info) {
      if(GraphTypeUtil.isScatterMatrix(info)) {
         return false;
      }

      return (chartType == GraphTypes.CHART_BAR
              || chartType == GraphTypes.CHART_BAR_STACK
              || chartType == GraphTypes.CHART_LINE
              || chartType == GraphTypes.CHART_LINE_STACK
              || chartType == GraphTypes.CHART_AREA
              || chartType == GraphTypes.CHART_AREA_STACK
              || chartType == GraphTypes.CHART_POINT
              || chartType == GraphTypes.CHART_POINT_STACK
              || chartType == GraphTypes.CHART_INTERVAL
              || chartType == GraphTypes.CHART_AUTO
              || chartType == GraphTypes.CHART_STEP
              || chartType == GraphTypes.CHART_JUMP
              || chartType == GraphTypes.CHART_STEP_STACK
              || chartType == GraphTypes.CHART_STEP_AREA
              || chartType == GraphTypes.CHART_STEP_AREA_STACK) &&
         (rtype == GraphTypes.CHART_BAR
            || rtype == GraphTypes.CHART_BAR_STACK
            || rtype == GraphTypes.CHART_LINE
            || rtype == GraphTypes.CHART_LINE_STACK
            || rtype == GraphTypes.CHART_AREA
            || rtype == GraphTypes.CHART_AREA_STACK
            || rtype == GraphTypes.CHART_POINT
            || rtype == GraphTypes.CHART_POINT_STACK
            || chartType == GraphTypes.CHART_JUMP
            || chartType == GraphTypes.CHART_STEP
            || chartType == GraphTypes.CHART_STEP_STACK
            || chartType == GraphTypes.CHART_STEP_AREA
            || chartType == GraphTypes.CHART_STEP_AREA_STACK);
   }

   /**
    * Get the measures in the chart.
    * @param info chart info.
    * @param rt whether get runtime measures.
    * @return chart measures.
    */
   public MeasureInfo[] getMeasures(ChartInfo info, boolean rt) throws Exception {
      List<MeasureInfo> measures= new ArrayList<>();
      measures.add(new MeasureInfo("", "", false));

      List<String> names = GraphUtil.getMeasuresName(info, rt);

      for(String name : names) {
         ChartRef ref = info.getFieldByName(name, rt);

         if(ref == null) {
            continue;
         }

         if(ref instanceof ChartDimensionRef) {
            XDimensionRef dim = (ChartDimensionRef) ref;

            if(dim.isDateTime()) {
               measures.add(new MeasureInfo(dim.getFullName(), dim.toView(), true,
                  XSchema.TIME.equals(dim.getDataType()), dim.isGroupOthers()));
            }
         }
         else if(ref instanceof ChartAggregateRef) {
            measures.add(new MeasureInfo(ref.getFullName(), ref.toView(),
                                         XSchema.isDateType(ref.getDataType())));
         }
      }

      return measures.toArray(new MeasureInfo[measures.size()]);
   }

   /**
    * Gets the current measure in the measure list.
    * @param field, the field of the target.
    * @param measures, measures of the chat.
    * @return corresponding meausre object of the field.
    */
   public MeasureInfo getMeasure(String field, MeasureInfo[] measures) {
      MeasureInfo measure = null;

      int i = 0;
      for( ; i < measures.length; i++) {
         if(field.equals(measures[i].getName())) {
            measure = new MeasureInfo(field, measures[i].getLabel(),
                                      measures[i].isDateField(),
                                      measures[i].isTimeField(),
                                      measures[i].isGroupOthers());
            break;
         }
      }

      // has no matched measure
      if(measures.length == i) {
         measure = new MeasureInfo("", "", false);
      }

      return measure;
   }

   /**
    * Adds chart refs
    */
   public DataRef[] addChartRefs(DataRef[] totalRefs, DataRef[] refs) {
      if(refs == null || refs.length == 0) {
         return totalRefs;
      }

      DataRef[] narr = new DataRef[totalRefs.length + refs.length];

      if(totalRefs.length > 0) {
         System.arraycopy(totalRefs, 0, narr, 0, totalRefs.length);
      }

      System.arraycopy(refs, 0, narr, totalRefs.length, refs.length);

      return narr;
   }

   /**
    * Check if there is a pie chart.
    */
   public boolean supportsTarget(ChartInfo cinfo, int[] types) {
      if(cinfo == null) {
         return true;
      }

      int ctype;

      if(!cinfo.isMultiStyles()) {
         ctype = cinfo.getChartType();

         for(int type : types) {
            if(ctype == type) {
               return false;
            }
         }
      }
      else {
         for(int i = 0; i < cinfo.getXFields().length; i++) {
            if(cinfo.getXField(i) instanceof ChartAggregateRef) {
               ctype = ((ChartAggregateRef) cinfo.getXField(i)).getChartType();

               for(int type : types) {
                  if(ctype == type) {
                     return false;
                  }
               }
            }
         }

         for(int i = 0; i < cinfo.getYFields().length; i++) {
            if(cinfo.getYField(i) instanceof ChartAggregateRef) {
               ctype = ((ChartAggregateRef) cinfo.getYField(i)).getChartType();

               for(int type : types) {
                  if(ctype == type) {
                     return false;
                  }
               }
            }
         }
      }

      return true;
   }

   /**
    * Get targetinfo list.
    */
   public TargetInfo[] getTargetInfoList(ChartDescriptor cDescp, ChartInfo info, boolean rt)
      throws Exception
   {
      List<TargetInfo> targetList = new ArrayList<>();

      for(int i = 0; i < cDescp.getTargetCount(); i++) {
         GraphTarget target = cDescp.getTarget(i);
         targetList.add(getTargetInfo(info, target, rt));
      }

      return targetList.toArray(new TargetInfo[targetList.size()]);
   }

   /**
    * Map a GraphTarget to TargetInfo.
    */
   public TargetInfo getTargetInfo(ChartInfo info, GraphTarget target, boolean rt)
      throws Exception
   {
      TargetInfo targetInfo = new TargetInfo();

      targetInfo.setIndex(target.getIndex());
      targetInfo.setFieldLabel(target.getFieldLabel());
      String genericLabel = getGenericLabel(target);

      if(genericLabel != null) {
         targetInfo.setGenericLabel(genericLabel);
         targetInfo.setTabFlag(getTabFlag(target));
      }

      String field = target.getField() == null ? "" : target.getField();
      targetInfo.setMeasure(getMeasure(field, getMeasures(info, rt)));
      targetInfo.setChartScope(target.isChartScope());
      targetInfo.setLineStyle(target.getLineStyle());

      String color;
      color = target.getLineColor() != null ?
         "#" + Tool.colorToHTMLString(target.getLineColor()) : "";
      targetInfo.setLineColor(new ColorInfo(color, COLOR_PALETTE));
      color = target.getFillAbove() != null ?
         "#" + Tool.colorToHTMLString(target.getFillAbove()) : "";
      targetInfo.setFillAboveColor(
         new ColorInfo(color,TRANSENABLED_COLOR_PALETTE));
      color = target.getFillBelow() != null ?
         "#" + Tool.colorToHTMLString(target.getFillBelow()) : "";
      targetInfo.setFillBelowColor(
         new ColorInfo(color, TRANSENABLED_COLOR_PALETTE));
      color = target.getBandFill().getColor(0) != null ?
         "#" + Tool.colorToHTMLString(target.getBandFill().getColor(0)) : "";
      targetInfo.setFillBandColor(
         new ColorInfo(color, TRANSENABLED_COLOR_PALETTE));
      targetInfo.setAlpha(Integer.toString(Math.abs(target.getAlphaValue())));
      targetInfo.setStrategyInfo(getStrategyInfo(target));
      targetInfo.setBandFill(visualService.createVisualFrameModel(target.getBandFill()));
      retrieveTargetValueFields(targetInfo, target);
      targetInfo.setLabelFormats(getLabelFormats(target).stream()
                                 .collect(Collectors.joining(",")));

      List<String> labelFormats = getLabelFormats(target);

      if(labelFormats.size() > 0) {
         targetInfo.setLabel(labelFormats.get(0));
         targetInfo.setToLabel(labelFormats.get(0));

         if(labelFormats.size() > 1) {
            targetInfo.setToLabel(labelFormats.get(1));
         }
      }

      targetInfo.setSupportFill(supportTargetFill(info));

      return targetInfo;
   }

   /**
    * Whether support fill below color or above color.
    * @param  info, current chart info.
    */
   private boolean supportTargetFill(ChartInfo info) {
      return info == null || !GraphTypes.is3DBar(info.getChartType());
   }

   /**
    * Sets the label value in the statistic pane of the target dialog.
    */
   private List<String> getLabelFormats(GraphTarget target) {
      List<String> labelFormats = new ArrayList<>();
      DynamicValue[] raw = target.getLabelFormats();
      boolean same = true;
      boolean isStatistics = getTabFlag(target) == TargetInfo.STATISTICS_TARGET;

      for(int i = 0; i < raw.length; i++) {
         String str = raw[i].getDValue();

         if(str == null) {
            continue;
         }

         if(i > 0 && same) {
            same = raw[i].getDValue().equals(raw[i - 1].getDValue());
         }

         boolean emptyFirstLabel = nextComma(str, 0) == 0;
         str = isStatistics && !emptyFirstLabel ? escapeLabel(str) : str;
         labelFormats.add(str);
      }

      if(same && raw.length > 0) {
         labelFormats.clear();
         labelFormats.add(isStatistics ? escapeLabel(raw[0].getDValue()) : raw[0].getDValue());
      }

      return labelFormats;
   }

   /**
    * Gets the generic label according to the different strategy
    */
   public String getGenericLabel(GraphTarget target) {
      TargetStrategyWrapper strategy = target.getStrategy();

      if(strategy instanceof PercentageWrapper) {
         return ((PercentageWrapper) strategy).getDescriptLabel();
      }
      else if(strategy instanceof PercentileWrapper) {
         return ((PercentileWrapper) strategy).getDescriptLabel();
      }
      else if(strategy instanceof ConfidenceIntervalWrapper) {
         return ((ConfidenceIntervalWrapper) strategy).getDescriptLabel();
      }
      else if(strategy instanceof StandardDeviationWrapper) {
         return ((StandardDeviationWrapper) strategy).getDescriptLabel();
      }
      else if(strategy instanceof QuantileWrapper) {
         return ((QuantileWrapper) strategy).getDescriptLabel();
      }
      else {
         return strategy.getGenericLabel();
      }
   }

   /**
    * Gets the tab flag of the dialog by the strategy label of the target.
    * @param  target, target strategy generic lable.
    * @return 'Line'/'Band'/'Stat'
    */
   public int getTabFlag(GraphTarget target) {
      //Fixed bug #23902 that should load target band tab for ChartPropertyDialog.
      TargetStrategyWrapper strategyWrapper = target.getStrategy();

      if(strategyWrapper instanceof DynamicLineWrapper) {
         int size = ((DynamicLineWrapper) strategyWrapper).getParameters().length;

         if(size < 2) {
            return TargetInfo.LINE_TARGET;
         }
         else {
            return TargetInfo.BAND_TARGET;
         }
      }

      return TargetInfo.STATISTICS_TARGET;
   }

   /**
    * Map the value fields from target(GraphTarget) to targetInfo(TargetInfo)
    */
   public void retrieveTargetValueFields(TargetInfo targetInfo,
                                          GraphTarget target)
   {
      TargetStrategyWrapper templateStrategy = target.getStrategy();
      DynamicLineWrapper dls;

      // Set up the value input field based on the line target parameter
      if(templateStrategy instanceof DynamicLineWrapper) {
         dls = (DynamicLineWrapper)target.getStrategy();
      }
      else {
         dls = new DynamicLineWrapper();
      }

      TargetParameterWrapper[] parameters = dls.getParameters();

      if(parameters.length > 0) {
         String value = getTargetParameterName(parameters[0]);
         targetInfo.setValue(value);

         if(parameters.length > 1) {
            String toValue = getTargetParameterName(parameters[1]);
            targetInfo.setToValue(toValue);
         }
      }
   }

   /**
    * gets the info of target strategy config.
    * @param  target, graph target of the chart
    * @return target strategy config type.
    */
   public StrategyInfo getStrategyInfo(GraphTarget target) {
      TargetStrategyWrapper strategy = target.getStrategy();
      //Default strategy info.
      StrategyInfo strategyInfo = new StrategyInfo("Confidence Interval", "95");

      if(strategy instanceof PercentageWrapper) {
         String percentages = ((PercentageWrapper) strategy).getPercentages();
         TargetParameterWrapper parameter = ((PercentageWrapper) strategy).getAggregate();
         String agg = getTargetParameterName(parameter);
         strategyInfo = new StrategyInfo("Percentage", percentages);
         strategyInfo.setPercentageAggregateVal(agg);
      }
      else if(strategy instanceof PercentileWrapper) {
         String percentiles = ((PercentileWrapper) strategy).getPercentiles();
         strategyInfo = new StrategyInfo("Percentiles", percentiles);
      }
      else if(strategy instanceof QuantileWrapper) {
         String number = ((QuantileWrapper) strategy).getNumberOfQuantiles();
         strategyInfo = new StrategyInfo("Quantiles", number);
      }
      else if(strategy instanceof StandardDeviationWrapper) {
         String factors = ((StandardDeviationWrapper) strategy).getFactors();
         String isSample = ((StandardDeviationWrapper) strategy).isSample();
         strategyInfo = new StrategyInfo("Standard Deviation", factors);
         strategyInfo.setStandardIsSample(isSample);
      }
      else if(strategy instanceof ConfidenceIntervalWrapper) {
         String confidenceLevel =
            ((ConfidenceIntervalWrapper) strategy).getConfidenceLevel();
         strategyInfo = new StrategyInfo("Confidence Interval", confidenceLevel);
      }

      return strategyInfo;
   }

   public String getTargetParameterName(TargetParameterWrapper parameter) {
      String value;

      // Use formula if exists, otherwise Dvalue
      if(parameter.getFormula() != null) {
         value = parameter.getFormula().getName();
      }
      else {
         value = parameter.getConstantValue().getDValue();
      }

      return value == null ? "" : value;
   }

   /**
    * Used to escape labels for statistics
    */
   public String escapeLabel(String label) {
      // Escape commas which have not been escaped already
      if(label == null || label.isEmpty()) {
         return "";
      }

      String result = "";
      int end = nextComma(label, 0);

      for(int start = 0; start < label.length(); ) {
         result += label.substring(start, end);

         if(end < label.length()) {
            result += "\\,";
         }

         start = end + 1;
         end = nextComma(label, start);
      }

      return result;
   }

   /**
    * Find the position of the next comma.
    * @param start the starting position to look.
    * @return comma index or the end of the string.
    */
   public int nextComma(String str, int start) {
      boolean inBrace = false;

      for(; start < str.length(); start++) {
         if(inBrace) {
            if(str.charAt(start) == '}') {
               inBrace = false;
            }
         }
         else if(str.charAt(start) == ',') {
            break;
         }
         else if(str.charAt(start) == '\\') {
            start++;
         }
         else if(str.charAt(start) == '{') {
            inBrace = true;
         }
      }

      return start;
   }

   /**
    * Updates all the targets in chart(included new added).
    */
   public void updateAllTargets(ChartDescriptor cDescp, TargetInfo[] targetList) {
      for(TargetInfo targetInfo : targetList) {
         GraphTarget target;
         //target is not changed, so no need to update it.
         if(!targetInfo.isChanged()) {
            continue;
         }

         //check the button clicked if 'Add' or 'Edit', if Add, the index of the
         // info is -1, or is other value if it is 'Edit'
         if(targetInfo.getIndex() == -1 || cDescp.getTargetCount() == 0) {
            target = new GraphTarget();
            target.setIndex(cDescp.getTargetCount());
         }
         else if(targetInfo.getIndex() >= cDescp.getTargetCount()) {
            continue;
         }
         else {
            target = cDescp.getTarget(targetInfo.getIndex());
         }

         updateTarget(targetInfo, target);

         //If the index is -1, indicates that the target is new added.
         if(targetInfo.getIndex() == -1) {
            cDescp.addTarget(target);
         }
      }
   }

   /**
    * Updates the info mation of the target
    * @param targetInfo, object which stored the new infomation of the target.
    * @param target, the old graph target on the chart.
    */
   public void updateTarget(TargetInfo targetInfo, GraphTarget target) {
      if(targetInfo.getTabFlag() == TargetInfo.LINE_TARGET) {
         updateLineTarget(targetInfo, target);
      }
      else if(targetInfo.getTabFlag() == TargetInfo.BAND_TARGET) {
         updateBandTarget(targetInfo, target);
      }
      else if(targetInfo.getTabFlag() == TargetInfo.STATISTICS_TARGET) {
         updateStatTarget(targetInfo, target);
      }
   }

   /**
    * Sets the common info of the line target, band target, stat target.
    * @param targetInfo, object which stored the target updated infomatiom.
    * @param target, the old GraphTarget on the chart.
    */
   public void updateTargetCommonInfo(TargetInfo targetInfo, GraphTarget target) {
      if(targetInfo.getLineColor().getColor() != null) {
         Color lineColor = Tool.getColorFromHexString(
            targetInfo.getLineColor().getColor());
         target.setLineColor(lineColor);
      }

      if(targetInfo.getFillAboveColor().getColor() != null) {
         Color aboveColor = Tool.getColorFromHexString(
            targetInfo.getFillAboveColor().getColor());
         target.setFillAbove(aboveColor);
      }

      if(targetInfo.getFillBelowColor().getColor() != null) {
         Color belowColor = Tool.getColorFromHexString(
            targetInfo.getFillBelowColor().getColor());
         target.setFillBelow(belowColor);
      }

      target.setLineStyle(targetInfo.getLineStyle());
      target.setChartScope(targetInfo.isChartScope());
      target.setAlphaValue(Integer.parseInt(targetInfo.getAlpha()));
      String field = Objects.equals(targetInfo.getMeasure().getName(), "") ? null :
         targetInfo.getMeasure().getName();
      target.setField(field);
      target.setDateField(targetInfo.getMeasure().isDateField());
      target.setTimeField(targetInfo.getMeasure().isTimeField());
   }

   /**
    * Updates the line target infomation.
    * @param targetInfo, object which stored the new infomation of the target.
    * @param target, old graph target.
    */
   public void updateLineTarget(TargetInfo targetInfo, GraphTarget target) {
      updateTargetCommonInfo(targetInfo, target);

      TargetParameterWrapper tpw = new TargetParameterWrapper();
      String valueField = targetInfo.getValue();

      if(isFormulaSupported(valueField)) {
         Formula formula = getFomulaInstanceByName(valueField);
         tpw.setFormula(formula);
         tpw.setConstantValue("");
      }
      else {
         tpw.setFormula(null);
         tpw.setDateField(targetInfo.getMeasure().isDateField());
         tpw.setTimeField(targetInfo.getMeasure().isTimeField());
         tpw.setConstantValue(valueField.trim());
      }

      target.setStrategy(new DynamicLineWrapper(tpw));
      target.setLabelFormats(targetInfo.getLabel());
   }

   /**
    * Gets a formula object if support.
    * @param formulaName, formula name.
    * @return the object of the Formula based on the formulaName.
    */
   public Formula getFomulaInstanceByName(String formulaName) {
      if(!isFormulaSupported(formulaName)) {
         return null;
      }

      Formula formula = null;

      if("Average".equals(formulaName)) {
         formula =  new AverageFormula();
      }
      else if("Min".equals(formulaName)) {
         formula = new MinFormula();
      }
      else if("Max".equals(formulaName)) {
         formula = new MaxFormula();
      }
      else if("Median".equals(formulaName)) {
         formula = new MedianFormula();
      }
      else if("Sum".equals(formulaName)) {
         formula = new SumFormula();
      }

      return formula;
   }

   /**
    * Check the target value whther is formula supported
    * @param value, the value input in the ui of the value input box.
    * @return if is formula supported, return true, if not, it indicates support
    * constant value.
    */
   private boolean isFormulaSupported(String value) {
      for(String FORMULA_TYPE : FORMULA_TYPES) {
         if(FORMULA_TYPE.equals(value)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Updates the band target infomation.
    * @param targetInfo, mapping model of the graph target.
    * @param target, graph target.
    */
   public void updateBandTarget(TargetInfo targetInfo, GraphTarget target) {
      updateTargetCommonInfo(targetInfo, target);

      TargetParameterWrapper tpw1 = new TargetParameterWrapper();
      TargetParameterWrapper tpw2 = new TargetParameterWrapper();
      String valueField = targetInfo.getValue();
      String toValueField = targetInfo.getToValue();
      if(isFormulaSupported(valueField)) {
         Formula formula1 = getFomulaInstanceByName(valueField);
         tpw1.setFormula(formula1);
         tpw1.setConstantValue("");
      }
      else {
         tpw1.setFormula(null);
         tpw1.setDateField(targetInfo.getMeasure().isDateField());
         tpw1.setTimeField(targetInfo.getMeasure().isTimeField());
         tpw1.setConstantValue(valueField.trim());
      }

      if(isFormulaSupported(toValueField)) {
         Formula formula2 = getFomulaInstanceByName(toValueField);
         tpw2.setFormula(formula2);
         tpw2.setConstantValue("");
      }
      else {
         tpw2.setDateField(targetInfo.getMeasure().isDateField());
         tpw2.setTimeField(targetInfo.getMeasure().isTimeField());
         tpw2.setFormula(null);
         tpw2.setConstantValue(toValueField.trim());
      }

      target.setStrategy(new DynamicLineWrapper(tpw1, tpw2));
      target.setLabelFormats(new DynamicValue[]{
         new DynamicValue(targetInfo.getLabel()),
         new DynamicValue(targetInfo.getToLabel())
      });

      if(targetInfo.getFillBandColor().getColor() != null) {
         Color bandColor = Tool.getColorFromHexString(
            targetInfo.getFillBandColor().getColor());
         target.getBandFill().setUserColor(0, bandColor);
      }
   }

   /**
    * Updates the statistic target infomation.
    * @param targetInfo, object which stored the new infomation of the target.
    * @param target, old graph target.
    */
   public void updateStatTarget(TargetInfo targetInfo, GraphTarget target) {
      updateTargetCommonInfo(targetInfo, target);
      target.setStrategy(getComputation(targetInfo));
      updateStatLabel(targetInfo,target);
      updateCategoricalColor(targetInfo.getBandFill(), target.getBandFill());
   }

   /**
    * Updates the labels of the statistic target.
    * @param targetInfo target info
    * @param target     graph target.
    */
   public void updateStatLabel(TargetInfo targetInfo, GraphTarget target) {
      String labelFormats = targetInfo.getLabelFormats();

      // now the labelFormat allow "", please see Bug #62025.
      if("".equals(labelFormats)) {
         target.setLabelFormats(new DynamicValue[] { new DynamicValue("") });
      }
      else {
         String raw = escapeLabel(labelFormats);
         List<DynamicValue> labels = new ArrayList<>();

         if(labelFormats.charAt(0) == ',') {
            labels.add(new DynamicValue(""));
            raw = raw.substring(1);
         }

         int end = nextComma(raw, 0);

         for(int start = 0; start < raw.length(); ) {
            String label = raw.substring(start, end);
            labels.add(new DynamicValue(unescapeLabel(label)));
            start = end + 1;
            end = nextComma(raw, start);
         }

         target.setLabelFormats(labels.toArray(new DynamicValue[0]));
      }
   }

   /**
    * Updates the categorical color pane.
    * @param cateColorModel, categorical color model.
    * @param cCFWrapper, CategoricalColorFrameWrapper
    */
   public void updateCategoricalColor(CategoricalColorModel cateColorModel,
                                       CategoricalColorFrameWrapper cCFWrapper)
   {
      int colorCount = cCFWrapper.getColorCount();

      Map<Integer, Color> cssmap = cCFWrapper.getCSSColors();
      String[] colors = cateColorModel.getColors();
      String[] cssColors = cateColorModel.getCssColors();
      String[] defaultColors = cateColorModel.getDefaultColors();

      for(int i = 0; i < colorCount; i++) {
         cCFWrapper.setColor(i, Tool.getColorFromHexString(colors[i]));
         cCFWrapper.setDefaultColor(i, Tool.getColorFromHexString(defaultColors[i]));
         cssmap.put(i, Tool.getColorFromHexString(cssColors[i]));
      }
   }

   /**
    * Gets the computation info if is statistic target
    * @param targetInfo, the object stored the info of the graph target.
    */
   public TargetStrategyWrapper getComputation(TargetInfo targetInfo) {
      StrategyInfo strategyInfo = targetInfo.getStrategyInfo();
      TargetStrategyWrapper strategyConfig = null;

      if("Confidence Interval".equals(strategyInfo.getName())) {
         strategyConfig = new ConfidenceIntervalWrapper(strategyInfo.getValue());
      }
      else if("Percentage".equals(strategyInfo.getName())) {
         TargetParameterWrapper tpw = new TargetParameterWrapper();
         String percentageAgg = strategyInfo.getPercentageAggregateVal();
         if(isFormulaSupported(percentageAgg)) {
            Formula formula = getFomulaInstanceByName(percentageAgg);
            tpw.setFormula(formula);
            tpw.setConstantValue("");
         }
         else {
            tpw.setFormula(null);
            tpw.setConstantValue(percentageAgg.trim());
         }

         String[] percentageParms =
            getStrategyConfigParms(strategyInfo.getValue());
         strategyConfig = new PercentageWrapper(percentageParms, tpw);
      }
      else if("Percentiles".equals(strategyInfo.getName())) {
         String[] percentileParms =
            getStrategyConfigParms(strategyInfo.getValue());
         strategyConfig = new PercentileWrapper(percentileParms);
      }
      else if("Quantiles".equals(strategyInfo.getName())) {
         strategyConfig = new QuantileWrapper(strategyInfo.getValue());
      }
      else if("Standard Deviation".equals(strategyInfo.getName())) {
         String[] factorParms = getStrategyConfigParms(strategyInfo.getValue());
         strategyConfig = new StandardDeviationWrapper(
            "true".equals(strategyInfo.getStandardIsSample()), factorParms);
      }

      return strategyConfig;
   }

   /**
    * Gets the parms of the strategy config by the string input.
    * @param  inputValue, the string format like 90,80   /  90, which different
    *    parm saperated by a ','
    * @return string array of the parms
    */
   public String[] getStrategyConfigParms(String inputValue) {
      String parmStr = inputValue.trim();
      //calculate the count of the parms.
      for(int i = 0; i < parmStr.length(); i++) {
         if(parmStr.charAt(i) == ',') {
         }
      }

      return parmStr.split(",");
   }

   public void removeDeletedTargets(ChartDescriptor cDescp, Integer[] deletedList) {
      Arrays.sort(deletedList);

      for(int i = deletedList.length - 1; i >= 0; i--) {
         GraphTarget target = cDescp.getTarget(deletedList[i]);
         cDescp.removeTarget(target);
      }

      //after removing target, update target index
      for(int i = 0; i < cDescp.getTargetCount(); i++) {
         GraphTarget target = cDescp.getTarget(i);
         target.setIndex(i);
      }
   }

   /**
    * Used to unescape labels for updating.
    */
   static String unescapeLabel(String label) {
      // Unescape commas which have been escaped
      if(label == null || label.isEmpty()) {
         return "";
      }

      // Replace "\," with ","
      return escapedCommaPattern.matcher(label).replaceAll(",");
   }

   private VisualFrameModelFactoryService visualService;
   public static final String COLOR_PALETTE = "color palette";
   public static final String TRANSENABLED_COLOR_PALETTE = "transparent enabled color";
   public static final int[] NO_TARGET_STYLES = {
      GraphTypes.CHART_PIE, GraphTypes.CHART_DONUT, GraphTypes.CHART_3D_PIE,
      GraphTypes.CHART_RADAR, GraphTypes.CHART_FILL_RADAR, GraphTypes.CHART_TREEMAP,
      GraphTypes.CHART_ICICLE, GraphTypes.CHART_SUNBURST, GraphTypes.CHART_CIRCLE_PACKING,
      GraphTypes.CHART_MEKKO, GraphTypes.CHART_TREE, GraphTypes.CHART_NETWORK,
      GraphTypes.CHART_CIRCULAR, GraphTypes.CHART_FUNNEL, GraphTypes.CHART_GANTT,
      GraphTypes.CHART_SCATTER_CONTOUR, GraphTypes.CHART_MAP_CONTOUR
   };
   private static final String[] FORMULA_TYPES = {"Average","Min","Max",
                                                  "Median","Sum"};
   private static Pattern escapedCommaPattern = Pattern.compile("\\\\,");
}
