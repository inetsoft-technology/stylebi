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
package inetsoft.report.script.viewsheet;

import inetsoft.graph.EGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.execution.BoundTableNotFoundException;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.*;
import inetsoft.report.lens.DataSetTable;
import inetsoft.report.script.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.log.LogManager;
import inetsoft.util.script.*;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The chart assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ChartVSAScriptable extends VSAScriptable implements CommonChartScriptable {
   /**
    * Create a chart viewsheet assembly scriptable.
    */
   public ChartVSAScriptable(ViewsheetSandbox box) {
      super(box);
      addFunctions();
      data = new TableArray2();

      // default creator to avoid np exception if a chart is not visible
      // but script is executed
      creator = new GraphCreator() {
         public EGraph createGraph() {
            return new EGraph();
         }

         public DataSet getGraphDataSet() {
            return null;
         }
      };
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "ChartVSA";
   }

   /**
    * Set the creator for EGraph.
    */
   public void setGraphCreator(GraphCreator creator) {
      creatorReady = true;
      this.creator = creator;
   }

   /**
    * Add user defined functions to the scope.
    */
   private void addFunctions() {
      try {
         addFunctionProperty(getClass(), "setHyperlink", int.class, Object.class);
      }
      catch(Exception ex) {
         LOG.warn("Failed to register chart assembly functions", ex);
      }
   }

   private EGraph getGraph() {
      try {
         return creator.getGraph();
      }
      catch(ColumnNotFoundException | ScriptException messageException) {
         LOG.warn("Failed to create graph: {}", messageException.getMessage());
         Tool.addUserMessage(messageException.getMessage());
         return null;
      }
      catch(Exception ex) {
         // fix Bug #22878, make sure the chart properties dialog can be opened
         // even the graph cannot be generate rightly becauseof the wrong script,
         // to let the user the modify the wrong script.
         LOG.warn("Failed to create graph.", ex);
         Tool.addUserWarning(ex.getMessage());
         return null;
      }
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      if(!(getVSAssemblyInfo() instanceof ChartVSAssemblyInfo)) {
         return Undefined.instance;
      }

      if(creator != null) {
         if(name.equals("graph")) {
            return getGraph();
         }
         else if(name.equals("dataset")) {
            if(creator.getDataSet() == null) {
               creator.setDataSet(getDataSet());
            }

            return creator.getDataSet();
         }
      }

      VSChartInfo vinfo = getChartInfo();

      if(vinfo != null) {
         return getPropertyValue(name, vinfo, start);
      }

      return super.get(name, start);
   }

   /**
    * Sets a named property in this object.
    */
   @Override
   public void put(String name, Scriptable start, Object value) {
      if(name.equals("graph") && creator != null) {
         value = JavaScriptEngine.unwrap(value);

         if(value instanceof EGraph) {
            creator.setGraph((EGraph) value);
         }
      }
      else if(name.equals("chartStyle")) {
         value = JavaScriptEngine.unwrap(value);
         getInfo().setChartStyle((Integer) value);
         return;
      }
      // support: data = runQuery(...) in the same way as in reports
      else if(name.equals("data")) {
         value = JavaScriptEngine.unwrap(value);

         if(value instanceof TableArray2) {
            data = (TableArray2) value;
         }

         DataSet nset = PropertyDescriptor.createDataSet(value);

         if(nset != null) {
            if(creator != null) {
               creator.setDataSet(nset);
            }
            else {
               super.put("dataset", start, nset);
            }

            data.table = new DataSetTable(nset);
         }
      }
      else if(name.equals("dataset") && !(value instanceof DataSet)) {
         value = JavaScriptEngine.unwrap(value);
         DataSet nset = PropertyDescriptor.createDataSet(value);

         if(creator != null) {
            creator.setDataSet(nset);
         }
         else {
            super.put("dataset", start, nset);
         }

         return;
      }
      else if(name.equals("query")) {
         if(value instanceof String) {
            getChartInfo().removeFields();
            Viewsheet vs = box.getViewsheet();
            String source = (String) value;

            if(vs != null) {
               SourceInfo sinfo = new SourceInfo();
               sinfo.setType(SourceInfo.ASSET);
               // use cube:: to separate the viewsheet source and cube source
               sinfo.setSource(source.startsWith("cube::") ?
                  Assembly.CUBE_VS + source.substring(6) : source);
               getInfo().setSourceInfo(sinfo);
            }
         }
      }

      super.put(name, start, value);
   }

   /**
    * Get the value of the specific property.
    * @param name the name of the property.
    * @param vinfo binding info of the chart.
    * @return the value of the sepecific property.
    */
   private Object getPropertyValue(String name, VSChartInfo vinfo, Scriptable start) {
      ChartBindable bindable = this.bindable;

      if(bindable == null) {
         bindable = this.bindable = ChartProcessor.getChartBindable(vinfo, null);
      }

      if("xFields".equals(name)) {
         return getFieldNames(vinfo.getRTXFields());
      }
      else if("yFields".equals(name)) {
         return getFieldNames(vinfo.getRTYFields());
      }
      else if("geoFields".equals(name)) {
         return vinfo instanceof VSMapInfo
            ? getFieldNames(((VSMapInfo) vinfo).getRTGeoFields())
            : new String[0];
      }
      else if("colorField".equals(name)) {
         return getFieldName(bindable.getRTColorField());
      }
      else if("shapeField".equals(name)) {
         return getFieldName(bindable.getRTShapeField());
      }
      else if("sizeField".equals(name)) {
         return getFieldName(bindable.getRTSizeField());
      }
      else if("textField".equals(name)) {
         return getFieldName(bindable.getRTTextField());
      }
      else if("data".equals(name) || "table".equals(name)) {
         return data;
      }
      else if("query".equals(name)) {
         if(getInfo().getSourceInfo() != null) {
            return getInfo().getSourceInfo().getSource();
         }

         return null;
      }
      else if("dataConditions".equals(name)) {
         return getTipConditions();
      }

      return super.get(name, start);
   }

   /**
    * Get the dataSet object.
    * @return the dataSet object.
    */
   @Override
   public DataSet getDataSet() {
      if(!(getVSAssemblyInfo() instanceof ChartVSAssemblyInfo)) {
         return null;
      }

      try {
         return (DataSet) box.getData(assembly);
      }
      catch(BoundTableNotFoundException tableNotFoundException) {
         LOG.debug("Failed to get chart data", tableNotFoundException);
      }
      catch(ScriptException scriptException) {
         // ScriptException should be logged at appropriate level when created
      }
      catch(Exception ex) {
         LOG.error("Failed to get chart data", ex);
      }

      return null;
   }

   /**
    * Add the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      final VSChartInfo vinfo = getChartInfo();
      createRTAxisDescriptor(vinfo);
      ChartDescriptor desc = getRTChartDescriptor();
      ChartVSAssemblyInfo cinfo = (ChartVSAssemblyInfo) getVSAssemblyInfo();

      processor = new ChartProcessor(this);
      processor.addProperties(vinfo, desc);
      processor.addFunctions(vinfo, desc);

      addProperty("combinedTooltip", "isCombinedToolTip", "setCombinedToolTip",
                  boolean.class, VSChartInfo.class, vinfo);
      addProperty("tipView", "getTipView", "setTipView", String.class,
                  getClass(), this);
      addProperty("tipAlpha", "getAlpha", "setAlpha", String.class,
                  ChartVSAssemblyInfo.class, cinfo);
      addProperty("flyoverViews", "getFlyoverViews", "setFlyoverViews",
                  String[].class, ChartVSAssemblyInfo.class, cinfo);
      addProperty("flyOnClick", "isFlyOnClick", "setFlyOnClick",
                  boolean.class, ChartVSAssemblyInfo.class, cinfo);
      addProperty("separatedStyle", "getChartStyle", "setChartStyle",
                  int.class, ChartVSAssemblyInfo.class, cinfo);
      addProperty("drillEnabled", "isDrillEnabled", "setDrillEnabled",
                  boolean.class, ChartVSAssemblyInfo.class, cinfo);
      addProperty("padding", "getPadding", "setPadding",
                  Insets.class, ChartVSAssemblyInfo.class, cinfo);
      addProperty("mapType", "getMapType", "setMapType", String.class,
         ChartVSAssemblyInfo.class, cinfo);
      addProperty("xAxis", new AxisScriptable(vinfo, vinfo.getRTAxisDescriptor(), true));
      addProperty("yAxis", new AxisScriptable(vinfo, vinfo.getRTAxisDescriptor()));
      addProperty("y2Axis", new AxisScriptable(vinfo, vinfo.getRTAxisDescriptor2()));
      addProperty("axis", new ChartArray("Axis", Object.class) {
         @Override
         public AbstractChartInfo getInfo() {
            return vinfo;
         }
      });
      addProperty("titleVisible", "isTitleVisible", "setTitleVisible", boolean.class,
         ChartVSAssemblyInfo.class, cinfo);

      addProperty("bindingInfo", bindingInfo = new VSChartBindingScriptable(this));
      addProperty("data", data);
      addProperty("dataset", null);
      addProperty("graph", null);
      addProperty("query", null);
      addProperty("shapeField", null);
      addProperty("colorField", null);
      addProperty("sizeField", null);
      addProperty("textField", null);
      addProperty("xFields", null);
      addProperty("yFields", null);
      addProperty("geoFields", null);
      addProperty("dataConditions", null);

      addProperty("singleStyle", new VSChartArray("ChartType", int.class) {
         @Override
         public AbstractChartInfo getInfo() {
            return getChartInfo();
         }
      });

      addProperty("chartStyle", new ChartArrayImpl("ChartType", int.class));
      addProperty("title", "getTitle", "setTitle", String.class, cinfo.getClass(), cinfo);
      addProperty("dateComparisonEnabled", "isDateComparisonEnabled", "setDateComparisonEnabled",
         boolean.class, ChartVSAssemblyInfo.class, cinfo);
   }

   /**
    * Set the foreground.
    * @param foreground the specified foreground.
    */
   @Override
   public void setForeground(Color foreground) {
      super.setForeground(foreground);

      getChartFormat().forEach(fmt -> {
            if(fmt != null) {
               fmt.setColor(foreground);
            }
         });
   }

   /**
    * Set the font to the chart.
    * @param font the specific font
    */
   @Override
   public void setFont(Font font) {
      super.setFont(font);

      getChartFormat().forEach(fmt -> {
         if(fmt != null) {
            fmt.setFont(font);
         }
      });
   }

   /**
    * Get chart format from the descriptors.
    */
   private List<CompositeTextFormat> getChartFormat() {
      List<CompositeTextFormat> list = new ArrayList<>();
      GraphFormatUtil.getChartFormat(getChartInfo(), getRTChartDescriptor(),
                               rAxisDesc, list);

      return list;
   }

   /**
    * Get the binding info of the chart.
    * @return the chart binding scriptable.
    */
   public VSChartBindingScriptable getBindingInfo() {
      return bindingInfo;
   }

   public void setTipView(String tipView) {
      getInfo().setTipOption(ChartVSAssemblyInfo.VIEWTIP_OPTION);
      getInfo().setTipView(tipView);
   }

   public void setTipViewValue(String tipView) {
      getInfo().setTipOptionValue(ChartVSAssemblyInfo.VIEWTIP_OPTION);
      getInfo().setTipViewValue(tipView);
   }

   public String getTipView() {
      return getInfo().getTipView();
   }

   /**
    * Get the field names.
    */
   private String[] getFieldNames(VSDataRef[] refs) {
      if(refs == null) {
         return new String[0];
      }

      String[] arr = new String[refs.length];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = getFieldName(refs[i]);
      }

      return arr;
   }

   /**
    * Get the field name.
    */
   private String getFieldName(DataRef ref) {
      return (ref == null) ? null : ref.getName();
   }

   /**
    * Set a hyperlink on a cell.
    */
   public void setHyperlink(int col, Object link) {
      ChartProcessor.setHyperlink(col, link, getDataSet());
      hasHyperlink = true;
   }

   private LegendsDescriptor getLegendsDescriptor() {
      if(!(getVSAssemblyInfo() instanceof ChartVSAssemblyInfo)) {
         return null;
      }

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) getVSAssemblyInfo();
      ChartDescriptor desc = getRTChartDescriptor();
      LegendsDescriptor legends = desc.getLegendsDescriptor();

      return legends;
   }

   /**
    * Set the visibility of the specific action.
    * @param name the name of the specific action.
    * @param visible the visibility of the action.
    */
   @Override
   public void setActionVisible(String name, boolean visible) {
      super.setActionVisible(name, visible);

      LegendDescriptor legend = null;
      boolean runtime = box.getMode() != Viewsheet.SHEET_DESIGN_MODE;

      if(runtime) {
         if("Color Legend".equals(name)) {
            legend = getLegendsDescriptor().getColorLegendDescriptor();
         }
         else if("Shape Legend".equals(name)) {
            legend = getLegendsDescriptor().getShapeLegendDescriptor();
         }
         else if("Size Legend".equals(name)) {
            legend = getLegendsDescriptor().getSizeLegendDescriptor();
         }

         if(legend != null) {
            legend.setVisible(visible);
         }
      }
   }

   /**
    * Get chart info.
    */
   protected VSChartInfo getChartInfo() {
      if(!(getVSAssemblyInfo() instanceof ChartVSAssemblyInfo)) {
         return null;
      }

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) getVSAssemblyInfo();

      return info == null ? null : info.getVSChartInfo();
   }

   protected ChartVSAssembly getChartAssembly() {
      VSAssembly vsAssembly = getVSAssembly();

      if(!(vsAssembly instanceof ChartVSAssembly)) {
         return null;
      }

      return ((ChartVSAssembly) vsAssembly);
   }

   /**
    * Get the suffix of a property, may be "", [] or ().
    * @param prop the property.
    */
   @Override
   public String getSuffix(Object prop) {
      // optimization, no need to retrieve value if get is expensive.
      if("highlighted".equals(prop) || "webMapStyle".equals(prop)) {
         return "";
      }
      else if("xFields".equals(prop) || "yFields".equals(prop) ||
         "geoFields".equals(prop) || "axis".equals(prop) ||
         "singleStyle".equals(prop) ||
         get(prop + "", this) instanceof ArrayObject)
      {
         return "[]";
      }
      else if(get(prop + "", this) instanceof FunctionObject) {
         return "()";
      }

      return super.getSuffix(prop);
   }

   /**
    * Get the ids of Axis.
    */
   public Object[] getAxisIds() {
      if(axisScript == null) {
         axisScript = new AxisScriptable(getChartInfo(), new AxisDescriptor());
      }

      return axisScript.getIds();
   }

   /**
    * Get the ids of field Axis.
    */
   public Object[] getFieldAxisIds() {
      if(fieldAxisScript == null) {
         fieldAxisScript =
            new AxisScriptable(getChartInfo(), new AxisDescriptor());
         fieldAxisScript.setFieldAxis(true);
      }

      return fieldAxisScript.getIds();
   }

   /**
    * Get the ids of legend.
    */
   public Object[] getLegendIds() {
      if(legendScript == null) {
         legendScript = new LegendScriptable(new LegendDescriptor());
      }

      return legendScript.getIds();
   }

   /**
    * Get the ids of title.
    */
   public Object[] getTitleIds() {
      if(titleScript == null) {
         titleScript = new TitleScriptable(new TitleDescriptor());
      }

      return titleScript.getIds();
   }

   /**
    * Get the ids of value formats.
    */
   public Object[] getValueFormatIds() {
      if(valueScript == null) {
         valueScript = new TextFormatScriptable(new CompositeTextFormat());
      }

      return valueScript.getIds();
   }

   /**
    * Get the ids of graph.
    */
   public Object[] getEGraphIds() {
      if(graphScript == null) {
         graphScript = new VSEGraphScriptable();
      }

      return graphScript.getIds();
   }

   /**
    * Check whether already executed hyperlink.
    */
   public boolean hasHyperlink() {
      return hasHyperlink;
   }

   /**
    * Get the runtime chart descriptor.
    * @return the chart desciptor.
    */
   private void createRTAxisDescriptor(VSChartInfo vinfo) {
      if(vinfo == null) {
         return;
      }

      if(vinfo instanceof MergedVSChartInfo || vinfo instanceof DefaultVSChartInfo) {
         if(rAxisDesc == null) {
            // @by stephenwebster, For Bug #747
            // Check the existence of the runtime descriptors prior to
            // design time to ensure any changes made through script are not
            // overwritten.
            rAxisDesc = vinfo.getRTAxisDescriptor();

            if(rAxisDesc == null) {
               rAxisDesc = vinfo.getAxisDescriptor() == null ?
                  new AxisDescriptor() :
                  (AxisDescriptor) vinfo.getAxisDescriptor().clone();

               vinfo.setRTAxisDescriptor(rAxisDesc);
            }


            AxisDescriptor rAxisDesc2 = vinfo.getRTAxisDescriptor2();

            if(rAxisDesc2 == null) {
               rAxisDesc2 = vinfo.getAxisDescriptor2() == null ?
                  new AxisDescriptor() :
                  (AxisDescriptor) vinfo.getAxisDescriptor2().clone();

               vinfo.setRTAxisDescriptor2(rAxisDesc2);
            }
         }
      }

      ChartRef[][] narrs = {vinfo.getRTXFields(), vinfo.getRTYFields(),
         vinfo.getRTGroupFields()};

      for(ChartRef[] arr : narrs) {
         for(ChartRef ref : arr) {
            if(ref instanceof VSChartRef) {
                VSChartRef cref = (VSChartRef) ref;

               if(cref.getRTAxisDescriptor() == null) {
                  AxisDescriptor rAxisDesc = cref.getAxisDescriptor() == null ?
                     new AxisDescriptor() :
                     (AxisDescriptor) cref.getAxisDescriptor().clone();
                  cref.setRTAxisDescriptor(rAxisDesc);
               }
            }
         }
      }
   }

   /**
    * Get the runtime chart descriptor.
    * @return the chart desciptor.
    */
   @Override
   public ChartDescriptor getRTChartDescriptor() {
      if(!(getVSAssemblyInfo() instanceof ChartVSAssemblyInfo)) {
         return null;
      }

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) getVSAssemblyInfo();

      if(info.getRTChartDescriptor() == null) {
         ChartDescriptor rdesc = info.getChartDescriptor() == null ?
            new ChartDescriptor() : (ChartDescriptor) info.getChartDescriptor().clone();
         info.setRTChartDescriptor(rdesc);
      }

      return info.getRTChartDescriptor();
   }

   /**
    * Get the assembly info of current chart.
    */
   private ChartVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof ChartVSAssemblyInfo) {
         return (ChartVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new ChartVSAssemblyInfo();
   }

   /**
    * Hide properties included for backward compatibility.
    */
   @Override
   protected boolean isPublicProperty(Object name) {
      return ChartProcessor.isPublicProperty(name);
   }


   /**
    * Add a line target
    */
   public void addTargetLine(Object field, Object clrs, Object values, Object options) {
      addGraphTarget(field, ChartProcessor.TargetType.LINE, clrs, values, options);
   }

   /**
    * Add a Band target (identical to line target)
    */
   public void addTargetBand(Object field, Object clrs, Object values, Object options) {
      addTargetLine(field, clrs, values, options);
   }

   /**
    * Add a Percentage target
    */
   public void addPercentageTarget(Object field, Object clrs, Object values, Object options) {
      addGraphTarget(field, ChartProcessor.TargetType.PERCENTAGE, clrs, values, options);
   }

   /**
    * Add a Percentile target
    */
   public void addPercentileTarget(Object field, Object clrs, Object values, Object options) {
      addGraphTarget(field, ChartProcessor.TargetType.PERCENTILE, clrs, values, options);
   }

   /**
    * Add a Confidence Interval target
    */
   public void addConfidenceIntervalTarget(Object field, Object clrs,
                                           Object values, Object options)
   {
      addGraphTarget(field, ChartProcessor.TargetType.CONFIDENCE_INTERVAL, clrs,
         values, options);
   }

   /**
    * Add a Quantile target
    */
   public void addQuantileTarget(Object field, Object clrs, Object values, Object options) {
      addGraphTarget(field, ChartProcessor.TargetType.QUANTILE, clrs, values, options);
   }

   /**
    * Add a Standard Deviation target
    */
   public void addStandardDeviationTarget(Object field, Object clrs,
                                          Object values, Object options)
   {
      addGraphTarget(field, ChartProcessor.TargetType.STANDARD_DEVIATION, clrs, values, options);
   }

   /**
    * Add a target line to the chart.  This was named "addGraphTarget" to
    * differentiate it from the old "addTarget" function
    */
   private void addGraphTarget(Object field, ChartProcessor.TargetType type,
                               Object clr, Object values, Object optional)
   {
      processor.addTarget(field, type, clr, values, optional);
   }

   public void clearTargets() {
      processor.clearTargets();
   }

   public void setTrendLineExcludedMeasures(Object measures) {
      processor.setTrendLineExcludedMeasures(measures);
   }

   /**
    * Add a target line to the chart.  This is the original(old) function,
    * from <= 11.4, maintained here for backwards compatibility.
    */
   public void addTarget(String value, String label, Object style, Object clr, Object measure) {
      processor.addBCTarget(value, label, style, clr, measure);
   }

   /**
    * Set the label alias for the color legend descriptor.
    */
   public void setLabelAliasOfColorLegend(String label, String alias) {
      processor.setLabelAliasOfColorLegend(label, alias);
   }

   /**
    * Set the label alias for the shape legend descriptor.
    */
   public void setLabelAliasOfShapeLegend(String label, String alias) {
      processor.setLabelAliasOfShapeLegend(label, alias);
   }

   /**
    * Set the label alias for the size legend descriptor.
    */
   public void setLabelAliasOfSizeLegend(String label, String alias) {
      processor.setLabelAliasOfSizeLegend(label, alias);
   }

   /**
    * Get the graph object in the chart.
    */
   @Override
   public EGraph getEGraph() {
      if(creator != null && creatorReady) {
         return getGraph();
      }

      if(getDataSet() == null) {
         return null;
      }

      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) getVSAssemblyInfo();
      Assembly boundTable = box.getViewsheet()
              .getBaseWorksheet().getAssembly(ainfo.getTableName() + "_O");
      int sourceType = XSourceInfo.NONE;

      if(boundTable instanceof BoundTableAssembly) {
         sourceType = ((BoundTableAssembly) boundTable).getSourceInfo().getType();
      }

      GraphGenerator gen = GraphGenerator.getGenerator(
         ainfo, null, getDataSet(), box.getAllVariables(), null, sourceType, null);

      try {
         return gen.createEGraph();
      }
      catch(ColumnNotFoundException colNotFoundException) {
         ColumnNotFoundException thrown = LOG.isDebugEnabled() ? colNotFoundException : null;
         LogManager.getInstance().logException(
            LOG, colNotFoundException.getLogLevel(), colNotFoundException.getMessage(), thrown);
         Tool.addUserWarning(colNotFoundException.getMessage());
         return null;
      }
      catch(MessageException messageException) {
         MessageException thrown = messageException.isDumpStack() ? messageException : null;
         LogManager.getInstance().logException(
            LOG, messageException.getLogLevel(),
            "Failed to generate graph: " + messageException.getMessage(), thrown);
         Tool.addUserWarning(messageException.getMessage());
         return null;
      }
      catch(Exception ex) {
         LOG.warn("Failed to generate graph.", ex);
         Tool.addUserWarning(ex.getMessage());
         return null;
      }
   }

   /**
    * Lets end users query a dataset object.
    */
   private class TableArray2 extends TableArray {
      public TableArray2() {
         super(null);
      }

      @Override
      public XTable getTable() {
         try {
            DataSet dataset = getDataSet();

            if(table == null || dataset != odataset) {
               if(dataset instanceof VSDataSet) {
                  table = ((VSDataSet) dataset).getTable();
               }
               else {
                  table = new DataSetTable(dataset);
               }

               // if dataset changed, clear the cached data so the
               // new dataset will be used
               clearCache();
               odataset = dataset;
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get chart data table", ex);
            return null;
         }

         return table;
      }

      private XTable table;
      private DataSet odataset = null;
   }

   /**
    * Clear cached data, assuming the data in component has changed.
    */
   @Override
   public void clearCache() {
      // re-calculated highlight
      creatorReady = false;
      addProperty("highlighted", new ChartHighlightedArray(this));
   }

   @Override
   public void clearCache(int type) {
      clearCache();
   }

   // Signature to avoid error from chart processor
   public void setTextID(String title, String textID) {
      throw new RuntimeException("setTextID() is not supported in viewsheet");
   }

   public String getTextID(String title) {
      return null;
   }

   public ViewsheetSandbox getViewsheetSandbox() {
      return box;
   }

   private class ChartArrayImpl extends VSChartArray implements Wrapper {
      public ChartArrayImpl(String property, Class pType) {
         super(property, pType);
      }

      @Override
      public String getClassName() {
         return "ChartArrayImpl";
      }

      @Override
      public AbstractChartInfo getInfo() {
         return ChartVSAScriptable.this.getChartInfo();
      }

      @Override
      public Object unwrap() {
         return ChartVSAScriptable.this.getInfo().getChartStyle();
      }

      @Override
      public Object getDefaultValue(Class hint) {
         if(hint == ScriptRuntime.ByteClass ||
            hint == ScriptRuntime.DoubleClass ||
            hint == ScriptRuntime.FloatClass ||
            hint == ScriptRuntime.IntegerClass ||
            hint == ScriptRuntime.LongClass ||
            hint == ScriptRuntime.NumberClass ||
            hint == ScriptRuntime.ShortClass ||
            hint == ScriptRuntime.StringClass)
         {
            return unwrap();
         }

         return super.getDefaultValue(hint);
      }
   }

   private TableArray2 data;
   private AxisDescriptor rAxisDesc;
   private AxisScriptable axisScript;
   private AxisScriptable fieldAxisScript;
   private LegendScriptable legendScript;
   private TitleScriptable titleScript;
   private TextFormatScriptable valueScript;
   private VSEGraphScriptable graphScript;
   private VSChartBindingScriptable bindingInfo;
   private ChartProcessor processor;
   private boolean hasHyperlink;
   private GraphCreator creator;
   private boolean creatorReady = false;
   private transient ChartBindable bindable;
   private static final Logger LOG = LoggerFactory.getLogger(ChartVSAScriptable.class);
}
