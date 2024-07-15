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
package inetsoft.web.binding.handler;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.internal.DimensionD;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.composition.region.GraphBounds;
import inetsoft.report.filter.CrossTabFilterUtil;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.graph.*;
import inetsoft.report.lens.SubTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AbstractModelTrapContext.TrapInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.VSTrapCommand;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.binding.model.ValueLabelListModel;
import inetsoft.web.binding.model.ValueLabelModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.math.BigDecimal;
import java.text.Format;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Component
public class VSChartDataHandler {
   /**
    * Convert to measrue.
    */
   public static final int CONVERT_TO_MEASURE = VSEventUtil.CONVERT_TO_MEASURE;
   /**
    * Convert to dimension.
    */
   public static final int CONVERT_TO_DIMENSION = VSEventUtil.CONVERT_TO_DIMENSION;
   /**
    * Set geographic.
    */
   public static final String SET_GEOGRAPHIC = "set";
   /**
    * Clear geographic.
    */
   public static final String CLEAR_GEOGRAPHIC = "clear";

   @Autowired
   public VSChartDataHandler(VSChartHandler chartHandler,
      VSAssemblyInfoHandler assemblyInfoHandler,
      PlaceholderService placeholderService)
   {
      this.chartHandler = chartHandler;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.placeholderService = placeholderService;
   }

   /**
    * Get the distinct value for the specified dimension ref.
    * @param box the specified root viewsheet sandbox.
    * @param chart the specified chart info.
    * @param dim the specified dimension ref.
    *
    * @return data set contains distinct values, null otherwise.
    */
   private static DataSet getDistinctValues(ViewsheetSandbox box, DataVSAssembly chart,
                                           VSDimensionRef dim)
      throws Exception
   {
      String name = chart.getAbsoluteName();
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         box = box.getSandbox(name.substring(0, index));
      }

      name = chart.getName();
      String tname = chart.getTableName();
      Viewsheet vs = chart.getViewsheet();
      WorksheetWrapper ws = new WorksheetWrapper(chart.getWorksheet());
      VSUtil.shrinkTable(vs, ws);

      TableAssembly table = tname == null || ws == null ? null :
         VSAQuery.getVSTableAssembly(tname, false, vs, ws);

      if(table == null) {
         return null;
      }

      table = box.getBoundTable(table, "distinct_random", false);
      VSAQuery.normalizeTable(table);
      ws.addAssembly(table);
      ColumnSelection cols = table.getColumnSelection();

      List<DynamicValue> list = dim.getDynamicValues();
      box.executeDynamicValues(name, list);
      List<DataRef> refs = dim.update(vs, cols);

      if(list.size() == 0 || refs.size() == 0) {
         return null;
      }

      // add column
      dim = (VSDimensionRef) refs.get(0);
      GroupRef group = dim.createGroupRef(cols);
      ColumnRef column = (ColumnRef) group.getDataRef();

      // calc field may use other fields. we could only make the used field
      // visible, but just leaving all visible is simpler and safer
      if(!(dim.getDataRef() instanceof CalculateRef)) {
         for(int i = 0; i < cols.getAttributeCount(); i++) {
            ColumnRef tcol = (ColumnRef) cols.getAttribute(i);
            tcol.setVisible(false);
         }
      }

      boolean aggregated = true;

      if(chart instanceof ChartVSAssembly) {
         aggregated = ((ChartVSAssembly) chart).getVSChartInfo().isAggregated();
      }

      cols.removeAttribute(column);
      column.setVisible(true);
      cols.addAttribute(column);
      table.setColumnSelection(cols);

      // distinct value
      // if chart is not aggregated, we don't use SummaryFilter to produce the distinct
      // values since it would collapse strings differ only in case. this matches the
      // behavior of the chart rendering. (48962)
      // still group if date so it matches chart. (54400)
      if(aggregated || !XSchema.STRING.equals(group.getDataType())) {
         AggregateInfo ainfo = table.getAggregateInfo();
         ainfo.addGroup(group);
      }

      // ascending order
      SortInfo sinfo = table.getSortInfo();
      sinfo.clear();
      SortRef sort = new SortRef(column);
      sort.setOrder(XConstants.SORT_ASC);
      sinfo.addSort(sort);
      TableAssembly assembly = null;
      String noEmpty = "";

      if(table instanceof MirrorTableAssembly) {
         assembly = ((MirrorTableAssembly) table).getTableAssembly();

         if(assembly instanceof CubeTableAssembly) {
            noEmpty = assembly.getProperty("noEmpty");
            assembly.setProperty("noEmpty", "false");
         }

         assembly.setPreRuntimeConditionList(null);
         assembly.setPostRuntimeConditionList(null);
      }

      AssetQuerySandbox abox = box.getAssetQuerySandbox();
      AssetQuery query = AssetQuery.createAssetQuery(
         table, AssetQuerySandbox.RUNTIME_MODE, abox, false, -1L, true, false);
      VariableTable vars = abox.getVariableTable();
      TableLens data = query.getTableLens(vars);
      data = AssetQuery.shuckOffFormat(data);

      if(assembly != null && !"".equals(noEmpty)) {
         assembly.setProperty("noEmpty", noEmpty);
      }

      if(data == null) {
         return null;
      }

      if(data.getColCount() > 1) {
         int col = Util.findColumn(data, dim.getDataRef());

         if(col >= 0) {
            data = new SubTableLens(data, -1, col, -1, 1);
         }
         else {
            return null;
         }
      }

      if(AssetUtil.isCubeTable(table)) {
         data = new VSCubeTableLens(data, table.getColumnSelection(true));
      }

      // use data set to transform int to date for date dimension (if any)
      return new VSDataSet(data, new VSDataRef[] {dim});
   }

   private static void setRankingConditionList(VSDimensionRef vdim, TableAssembly table,
                                               DataVSAssembly dataAssembly)
   {
      AggregateInfo ainfo = table.getAggregateInfo();
      ColumnSelection cols = table.getColumnSelection();
      RankingCondition cond = vdim.getRankingCondition();
      boolean crosstab = dataAssembly instanceof CrosstabVSAssembly;

      if(cond != null) {
         DataRef aref = cond.getDataRef();

         if(aref == null) {
            vdim.updateRanking(VSUtil.getVSColumnSelection(cols));
            cond = vdim.getRankingCondition();
         }
      }

      if(cond != null && dataAssembly != null) {
         String rankingCol = vdim.getRankingCol();
         DataRef[] bindingRefs = dataAssembly.getBindingRefs();

         if(bindingRefs == null) {
            return;
         }

         Optional<VSAggregateRef> first = Arrays.stream(bindingRefs)
            .filter(VSAggregateRef.class::isInstance)
            .map(VSAggregateRef.class::cast)
            .filter(ref -> Tool.equals(rankingCol, crosstab ?
               CrossTabFilterUtil.getCrosstabRTAggregateName(ref, false) : ref.getFullName(false))).findFirst();

         if(!first.isPresent()) {
            return;
         }

         VSAggregateRef aggregateRef = first.get();
         AggregateRef aggRef = aggregateRef.createAggregateRef(cols);

         if(!ainfo.containsAggregate(aggRef)) {
            ainfo.addAggregate(aggRef);
            ColumnRef column = (ColumnRef) aggRef.getDataRef();
            cols.addAttribute(column);
         }

         cond = cond.clone();
         GroupRef group = vdim.createGroupRef(cols);

         if(group == null) {
            return;
         }

         DataRef aref = cond.getDataRef();
         aref = AssetUtil.getColumnRefFromAttribute(cols, aref);
         DataRef aref2 = (aref != null) ? AssetUtil.findRef(ainfo, aref) : null;

         if(aref2 == null) {
            return;
         }

         cond.setDataRef(aref2);
         DataRef gref = group.getDataRef();
         ConditionItem ranking = new ConditionItem(gref, cond, 0);
         ConditionList rconds = new ConditionList();
         rconds.append(ranking);
         table.setRankingConditionList(rconds);
      }
   }



   public void convertChartRef(RuntimeViewsheet rvs, ChartVSAssembly chart,
                               String refName, int changeType) throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = chart.getViewsheet();
      String name = chart.getAbsoluteName();
      String table = chart.getTableName();
      ChartVSAssemblyInfo oinfo =
         (ChartVSAssemblyInfo) chart.getVSAssemblyInfo().clone();
      VSSelection bselection = oinfo.getBrushSelection();
      ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo vsChartInfo = ninfo.getVSChartInfo();
      fixAggregateInfo(vsChartInfo, refName, changeType);

      // removed the bound data ref
      boolean changed = fixChartInfo(vsChartInfo, refName, changeType);
      boolean detectRequired = false;

      // clear runtime fields so the aesthetic fields won't be restored
      // in update() of VSChartInfo
      vsChartInfo.clearRuntime();

      // keep geographic
      chartHandler.updateGeoColumns(box, vs, chart, vsChartInfo);
      boolean isGeo = vsChartInfo.isGeoColumn(refName);

      if(isGeo) {
         boolean todim = changeType == CONVERT_TO_DIMENSION;
         boolean isdim = !todim;
         boolean refChanged =
            chartHandler.fixMapInfo(vsChartInfo, refName, CLEAR_GEOGRAPHIC);
         boolean isDate = isDate(oinfo.getVSChartInfo(), refName);

         detectRequired = todim && !isDate;
         chartHandler.changeGeographic(vsChartInfo, refName, CLEAR_GEOGRAPHIC, isdim);

         if(detectRequired) {
            chartHandler.changeGeographic(vsChartInfo, refName, SET_GEOGRAPHIC, todim);
         }

         chartHandler.updateGeoColumns(box, vs, chart, vsChartInfo);
         changed = changed || refChanged || detectRequired;
      }

      int hint = 0;
      box.updateAssembly(chart.getAbsoluteName());

      if(changed) {
         vsChartInfo = (VSChartInfo)
            new ChangeChartDataProcessor(vsChartInfo).process();
         ninfo.setVSChartInfo(vsChartInfo);
         hint |= chartHandler.createCommands(oinfo, ninfo);
         boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) ==
            VSAssembly.INPUT_DATA_CHANGED;

         // clear brush for data changed
         if(dchanged && table != null && bselection != null && !bselection.isEmpty()) {
            hint = hint | VSAssembly.OUTPUT_DATA_CHANGED;
            vs.setBrush(table, chart);
         }

         ChangedAssemblyList clist = new ChangedAssemblyList(false);
         box.processChange(name, hint, clist);

         if(detectRequired) {
            DataSet source = chartHandler.getChartData(box, chart);
            SourceInfo sourceInfo = chart.getSourceInfo();
            chartHandler.autoDetect(vs, sourceInfo, vsChartInfo, refName, source);
         }
      }
   }

   /**
    * ChangeChartDataEvent
    **/
   public void changeChartData(RuntimeViewsheet rvs,
      ChartVSAssemblyInfo oldInfo, ChartVSAssemblyInfo ninfo,
      String url, VSDndEvent evt, CommandDispatcher dispatcher)
      throws Exception
   {
      boolean checkTrap = evt == null ? false : evt.checkTrap();
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String name = ninfo.getAbsoluteName2();
      ChartVSAssembly assembly = (ChartVSAssembly) vs.getAssembly(name);

      synchronized(assembly) {
         vs = assembly.getViewsheet();
         String table = assembly.getTableName();
         ChartVSAssemblyInfo oinfo =
            (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();

         // fix info
         chartHandler.fixAggregateInfo(ninfo, vs, null);

         ChartVSAssemblyInfo ninfoCopy = (ChartVSAssemblyInfo) ninfo.clone();
         int hint = assembly.setVSAssemblyInfo(ninfo);
         box.updateAssembly(assembly.getAbsoluteName());
         ninfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
         // ChangeChartDataProcessor logic would clear runtime value,
         // get chart type would use runtime value, so get chart type before clear operator.
         int ctype = getChartType(ninfo.getVSChartInfo());
         VSChartInfo cinfo = ninfo.getVSChartInfo();
         cinfo = (VSChartInfo) new ChangeChartDataProcessor(cinfo).process();

         int aggCount = Arrays.stream(cinfo.getBindingRefs(false))
            .filter(ref -> ref instanceof ChartAggregateRef && !((ChartAggregateRef) ref).isDiscrete())
            .collect(Collectors.toList()).size();


         if(evt != null && !cinfo.isMultiStyles() &&
            GraphTypes.isWaterfall(cinfo.getChartType()) && aggCount > 1)
         {
            assembly.setVSAssemblyInfo(oinfo);
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(Catalog.getCatalog().getString(
               "em.common.graph.measuresNotSupported"));
            dispatcher.sendCommand(messageCommand);
            return;
         }

         ChangeChartProcessor.fixGroupOption(cinfo);
         ninfo.setVSChartInfo(cinfo);
         chartHandler.fixShapeField(ninfo.getVSChartInfo(), ctype);
         ChangeChartProcessor pro = new ChangeChartProcessor();
         VSChartInfo ocinfo = oinfo.getVSChartInfo();
         VSChartInfo ncinfo = ninfo.getVSChartInfo();

         // @by ChrisSpagnoli bug1412008160666 #1 2014-10-28
         // Clear custom tip, if it contains numeric references
         if(tipFormatContainsNumericReferences(ocinfo.getToolTipValue())) {
            ncinfo.setToolTipValue(null);
         }

         pro.fixMapFrame(ocinfo, ncinfo);
         pro.fixNamedGroup(ocinfo, ncinfo);
         pro.updateColorFrameCSSParentParams(ninfo, ncinfo);
         // fix Bug #32912, update again to refresh the runtime values.
         box.updateAssembly(assembly.getAbsoluteName());
         hint = hint | chartHandler.createCommands(oinfo, ninfoCopy);

         boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) ==
            VSAssembly.INPUT_DATA_CHANGED;
         VSSelection bselection = oinfo.getBrushSelection();

         // clear brush for data changed
         if(dchanged && table != null && bselection != null && !bselection.isEmpty()) {
            hint = hint | assembly.setBrushSelection(null);
            vs.setBrush(table, assembly);
         }

         ChangedAssemblyList clist = new ChangedAssemblyList(false);
         box.processChange(name, hint, clist);

         if(checkTrap) {
            VSModelTrapContext context = new VSModelTrapContext(rvs, true);
            TrapInfo trapInfo = context.isCheckTrap()
               ? context.checkTrap(oldInfo, ninfo) : null ;

            if(!evt.confirmed() && trapInfo != null && trapInfo.showWarning()) {
               assembly.setVSAssemblyInfo(oldInfo);
               String msg = Catalog.getCatalog().getString(
                     "designer.binding.continueTrap");
               VSTrapCommand tcommand = new VSTrapCommand();
               tcommand.setMessage(
                  Catalog.getCatalog().getString("designer.binding.continueTrap"));
               tcommand.setType(MessageCommand.Type.INFO);

               if(url != null) {
                  tcommand.addEvent(url, evt);
               }

               dispatcher.sendCommand(tcommand);
               return;
            }

            if(context.isCheckTrap()) {
               assemblyInfoHandler.getCurrentGrayedOutFields(context, dispatcher);
            }
         }
      }
   }

   /**
    * ChangeChartRefEvent
    **/
   public void changeChartRef(RuntimeViewsheet rvs, ChartVSAssemblyInfo ninfo)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String name = ninfo.getAbsoluteName2();
      ChartVSAssembly assembly = (ChartVSAssembly) vs.getAssembly(name);

      if(assembly != null) {
         ChartVSAssemblyInfo oinfo =
            (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();
         vs = assembly.getViewsheet();
         VSSelection bselection = assembly.getBrushSelection();
         String table = assembly.getTableName();
         int hint = assembly.setVSAssemblyInfo(ninfo);

         if(hint != 0) {
            VSChartInfo ocinfo = oinfo.getVSChartInfo();
            VSChartInfo ncinfo = ninfo.getVSChartInfo();
            boolean layerChanged = false;
            String otype = ocinfo.getMapType();
            String ntype = ncinfo.getMapType();

            if(!Tool.equals(otype, ntype)) {
               ninfo.setVSChartInfo(ncinfo);
               box.updateAssembly(name);
               ncinfo = ninfo.getVSChartInfo();
               chartHandler.updateGeoColumns(box, vs, assembly, ncinfo);
               DataSet source = chartHandler.getChartData(box, assembly);
               SourceInfo sourceInfo = ninfo.getSourceInfo();
               layerChanged = fixMapLayer(vs, sourceInfo, source, ncinfo);
            }

            boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) ==
               VSAssembly.INPUT_DATA_CHANGED || layerChanged;

            if(dchanged && table != null && bselection != null && !bselection.isEmpty()) {
               hint = hint | assembly.setBrushSelection(null);
               vs.setBrush(table, assembly);
            }

            box.updateAssembly(name);
            new ChangeChartDataProcessor().sortRefs(assembly.getVSChartInfo());
            ChangeChartProcessor process = new ChangeChartProcessor();
            process.fixMapFrame(ocinfo, ncinfo);
            process.fixNamedGroup(ocinfo, ncinfo);
            ChangeChartProcessor.fixTarget(ocinfo, ncinfo, assembly.getChartDescriptor());
            ChangedAssemblyList clist = new ChangedAssemblyList(false);
            box.processChange(name, hint, clist);
         }
      }
   }

   /**
    * BrowseDimensionDataEvent
    */
   public ValueLabelListModel browseDimensionData(RuntimeViewsheet rvs, String assemblyName,
                                                  VSDimensionRef dim)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      DataVSAssembly assembly = (DataVSAssembly) vs.getAssembly(assemblyName);
      DataSet set = null;

      try {
         set = getDistinctValues(box, assembly, dim);
      }
      catch(Exception ex) {
         if(ex.getMessage() != null && ex.getMessage().startsWith("Query timeout")) {
            throw new TimeoutException(Catalog.getCatalog()
               .getString("common.timeout", assemblyName));
         }

         throw ex;
      }

      if(set instanceof VSDataSet && Util.isTimeoutTable(((VSDataSet) set).getTable())) {
         throw new TimeoutException(Catalog.getCatalog()
            .getString("common.timeout", assemblyName));
      }

      int dateLevel = dim.isDateRange() ?
         dim.getDateLevel() : XConstants.NONE_DATE_GROUP;
      List<ValueLabelModel> list = new ArrayList<>();
      Set<String> added = new HashSet<>();
      boolean isCube = VSUtil.isCubeSource(assembly.getTableName());

      if(set != null) {
         for(int i = 0; i < set.getRowCount(); i++) {
            Object obj = set.getData(0, i);
            String str = null;

            if(obj != null) {
               str = Tool.getDataString(obj);
            }

            if(isCube && (obj == null || Tool.isEmptyString(str))) {
               continue;
            }

            if(obj instanceof Double) {
               str = new BigDecimal(str).toPlainString();
            }

            if(!added.contains(str)) {
               added.add(str);

               String value = str == null ? "" : str;
               String label = value;

               if(dateLevel != XConstants.NONE_DATE_GROUP) {
                  Format fmt = XUtil.getDefaultDateFormat(
                     dim.getDateLevel(), dim.getDataType());

                  if(obj instanceof Number) {
                     obj = new Date(((Number) obj).longValue());
                  }

                  if(obj instanceof Date && fmt != null) {
                     label = fmt.format(obj);
                  }
               }

               list.add(ValueLabelModel.builder()
                           .value(value)
                           .label(label)
                           .build());
            }
         }
      }

      return ValueLabelListModel.builder()
         .list(list)
         .build();
   }

   /**
    * ChangeChartAestheticEvent
    **/
   public void changeChartAesthetic(RuntimeViewsheet rvs, ChartVSAssemblyInfo ninfo)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String name = ninfo.getAbsoluteName2();
      ChartVSAssembly assembly = (ChartVSAssembly) vs.getAssembly(name);

      synchronized(assembly) {
         vs = assembly.getViewsheet();
         String table = assembly.getTableName();
         ChartVSAssemblyInfo oinfo =
            (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();
         chartHandler.fixAggregateInfo(ninfo, vs, null);
         fixAggregateRefSizeField(ninfo);

         int hint = assembly.setVSAssemblyInfo(ninfo);
         box.updateAssembly(assembly.getAbsoluteName());

         // fix bug1352448598261, chart type is not valid when in flex side,
         // so GraphUtil.as.fixVisualFrame may cause invalid result, here
         // fix it again
         new ChangeChartDataProcessor(ninfo.getVSChartInfo(), false).process();
         ChangeChartProcessor pro = new ChangeChartProcessor();
         VSChartInfo ocinfo = oinfo.getVSChartInfo();
         VSChartInfo ncinfo = ninfo.getVSChartInfo();
         pro.fixMapFrame(ocinfo, ncinfo);
         pro.fixAestheticNamedGroup(ncinfo);
         hint = hint | chartHandler.createCommands(oinfo, ninfo);
         boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) == VSAssembly.INPUT_DATA_CHANGED;
         VSSelection bselection = oinfo.getBrushSelection();

         // clear brush for data changed
         if(dchanged && table != null && bselection != null && !bselection.isEmpty()) {
            hint = hint | assembly.setBrushSelection(null);
            vs.setBrush(table, assembly);
         }

         ChangedAssemblyList clist = new ChangedAssemblyList(false);
         box.processChange(name, hint, clist);
      }
   }

   /**
    * GetChartAreaEvent
    **/
   public void refreshChart(RuntimeViewsheet rvs, ChartVSAssemblyInfo info,
      Dimension reqsize) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      String cname = info.getAbsoluteName();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(cname);

      if(assembly == null) {
         return;
      }

      VGraphPair pair = null;

      AssetQuerySandbox wbox = box.getAssetQuerySandbox();
      VSChartInfo cinfo = ((ChartVSAssembly) assembly).getVSChartInfo();
      VSDataSet chart = (VSDataSet) box.getData(cname);
      String[] names = Tool.split(cname, '.');
      Viewsheet viewsheet = vs;
      String name = cname;
      boolean isViewsheet = assembly != null && assembly.isEmbedded();

      for(int i = 0; isViewsheet && i < names.length - 1; i++) {
         viewsheet = (Viewsheet) viewsheet.getAssembly(names[i]);
         name = name.substring(name.indexOf(".") + 1);
         VSAssembly vsAssembly = (VSAssembly) viewsheet.getAssembly(name);
         isViewsheet = vsAssembly != null && vsAssembly.isEmbedded();
      }

      cinfo.setLocalMap(
         VSUtil.getLocalMap(viewsheet, names[names.length -1]));

      pair = box.getVGraphPair(cname, true, reqsize);

      if(pair != null && pair.isCancelled()) {
         box.clearGraph(cname);
         pair = box.getVGraphPair(cname, true, reqsize);
      }

      box.updateAssembly(assembly.getAbsoluteName());

      if(pair != null && pair.isCompleted()) {
         // update size in descriptor
         updatePreferredSize(pair, (ChartVSAssembly) assembly);
      }
   }

   /**
    * ChangeGeographicEvent
    **/
   public void setGeographic(RuntimeViewsheet rvs, String name,
      ChartVSAssemblyInfo ninfo, String refName, boolean isDimension, String type)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);
      vs = chart.getViewsheet();
      ChartVSAssemblyInfo oinfo =
         (ChartVSAssemblyInfo) chart.getVSAssemblyInfo().clone();

      chartHandler.fixAggregateInfo(ninfo, vs, null);

      SourceInfo osinfo = oinfo.getSourceInfo();
      SourceInfo nsinfo = ninfo.getSourceInfo();
      VSChartInfo cinfo = ninfo.getVSChartInfo();
      boolean changed = false;

      if(!Tool.equals(osinfo, nsinfo)) {
         List<AggregateRef> refs = copyExpressionRefs(cinfo);
         cinfo.getGeoColumns().clear();
         cinfo.removeFields();
         changed = true;
         pasteExproessionRefs(refs, cinfo);
      }

      // change geographic
      chartHandler.updateGeoColumns(box, vs, chart, cinfo);
      chartHandler.changeGeographic(cinfo, refName, type, isDimension);

      // fix map info
      VSChartInfo ocinfo = (VSChartInfo) cinfo.clone();
      changed = chartHandler.fixMapInfo(cinfo, refName, type) || changed;
      new ChangeChartProcessor().fixMapFrame(ocinfo, cinfo);

      // refresh map
      chart.setVSAssemblyInfo(ninfo);
      box.updateAssembly(chart.getAbsoluteName());

      // auto detect
      if(type.equals(SET_GEOGRAPHIC)) {
         if(isDimension) {
            chartHandler.updateAllGeoColumns(box, vs, chart);
            DataSet source = chartHandler.getChartData(box, chart);
            chartHandler.autoDetect(vs, nsinfo, cinfo, refName, source);
         }
         else if(!MapHelper.isValidType(cinfo.getMapType())) {
            cinfo.setMeasureMapType("World");
         }
      }

      String otype = oinfo.getVSChartInfo().getMapType();
      String ntype = cinfo.getMapType();
      changed = !Tool.equals(otype, ntype) || changed;

      if(changed) {
         VSSelection bselection = oinfo.getBrushSelection();
         String table = chart.getTableName();
         int hint = chartHandler.createCommands(oinfo, ninfo);
         boolean dchanged = (hint & VSAssembly.INPUT_DATA_CHANGED) ==
            VSAssembly.INPUT_DATA_CHANGED;

         // clear brush for data changed
         if(dchanged && table != null && bselection != null && !bselection.isEmpty()) {
            hint = hint | VSAssembly.OUTPUT_DATA_CHANGED;
            vs.setBrush(table, chart);
         }

         ChangedAssemblyList clist = new ChangedAssemblyList(false);
         box.processChange(name, hint, clist);
      }
   }

   /**
    * Get the available chart type to show a corresponding shape frame.
    */
   public int getChartType(VSChartInfo info) {
      boolean sep = !info.isMultiStyles();

      info.updateChartType(sep);
      ChartRef[] xrefs = info.getRTXFields();
      ChartRef[] yrefs = info.getRTYFields();

      // separated?
      if(sep) {
         return info.getRTChartType();
      }

      ChartRef[] refs = yrefs;

      for(ChartRef ref : refs) {
         if(ref instanceof VSChartAggregateRef) {
            VSChartAggregateRef aggr = (VSChartAggregateRef) ref;
            return aggr.getRTChartType();
         }
      }

      refs = xrefs;

      for(ChartRef ref : refs) {
         if(ref instanceof VSChartAggregateRef) {
            VSChartAggregateRef aggr = (VSChartAggregateRef) ref;
            return aggr.getRTChartType();
         }
      }

      // handle no measure case
      ChartRef xref = xrefs.length == 0 ? null : xrefs[xrefs.length - 1];
      ChartRef yref = yrefs.length == 0 ? null : yrefs[yrefs.length - 1];
      boolean xdim = xref instanceof VSDimensionRef;
      boolean ydim = yref instanceof VSDimensionRef;

      if(xref == null) {
         return ydim ? GraphTypes.CHART_POINT : GraphTypes.CHART_BAR;
      }
      else if(yref == null) {
         return xdim ? GraphTypes.CHART_POINT : GraphTypes.CHART_BAR;
      }
      else if(xdim && ydim) {
         return GraphTypes.CHART_POINT;
      }

      return GraphTypes.CHART_AUTO;
   }

   // @by ChrisSpagnoli bug1412008160666 #1 2014-10-28
   // Determine if tip format contains numeric references
   public boolean tipFormatContainsNumericReferences(String tipfmt) {
      return chartHandler.tipFormatContainsNumericReferences(tipfmt);
   }

   /**
    * Fix the SizeField of the chart aggregateRef.
    */
   private void fixAggregateRefSizeField(ChartVSAssemblyInfo ninfo) {
      VSChartInfo vinfo = ninfo.getVSChartInfo();

      if(vinfo.isMultiAesthetic()) {
         return;
      }

      VSDataRef[] arr = vinfo.getFields();

      for(VSDataRef ref : arr) {
         if(ref instanceof ChartAggregateRef) {
            ((ChartAggregateRef) ref).setSizeField(null);
         }
      }
   }

   /**
    * Fix map layer.
    */
   public boolean fixMapLayer(Viewsheet vs, SourceInfo sourceInfo, DataSet source,
      VSChartInfo ncinfo)
   {
      boolean changed = false;

      // fix geo column selection
      ColumnSelection rcols = ncinfo.getRTGeoColumns();
      ColumnSelection cols = ncinfo.getGeoColumns();

      for(int i = 0; i < rcols.getAttributeCount(); i++) {
         DataRef ref = rcols.getAttribute(i);

         if(ref instanceof VSChartGeoRef) {
            String refName = ref.getName();
            GeoRef gcol = (GeoRef) cols.getAttribute(i);
            GeographicOption opt = gcol.getGeographicOption();
            changed = MapHelper.autoDetect(vs, sourceInfo, ncinfo, opt, refName,
               source) || changed;
         }
      }

      chartHandler.copyGeoColumns(ncinfo);

      /*
      // fix geo fields
      ChartRef[] rgflds = ncinfo.getRTGeoFields();
      ChartRef[] gflds = ncinfo.getGeoFields();

      for(int i = 0; i < rgflds.length; i++) {
         String refName = rgflds[i].getName();
         GeoRef gfld = (GeoRef) gflds[i];
         GeographicOption opt = gfld.getGeographicOption();
         changed = MapHelper.autoDetect(vs, sourceInfo, ncinfo, opt, refName,
            source) || changed;
      }
      */

      return changed;
   }

   /**
    * Update content & legends size.
    */
   private void updatePreferredSize(VGraphPair pair, ChartVSAssembly chart) {
      if(chart == null) {
         return;
      }

      ChartDescriptor desc = chart.getChartDescriptor();

      if(desc == null) {
         return;
      }

      desc.setPreferredSize(getContentSize(pair));

      LegendsDescriptor legendsDesc = desc.getLegendsDescriptor();

      // only update the legends after use modified it, is minimized, not update
      // the preferred size
      if(legendsDesc == null || legendsDesc.getPreferredSize() == null) {
         return;
      }

      Dimension2D d = getLegendsSize(pair);

      // don't clear user specified size if legend is temporarily not visible
      // (e.g. due to some selection condition).
      if(d != null) {
         Dimension csize = pair.getContentSize();
         legendsDesc.setPreferredSize(new DimensionD(
            d.getWidth() / csize.getWidth(),
            d.getHeight() / csize.getHeight()));
      }
   }

   /**
    * Get chart content size.
    */
   private Dimension getContentSize(VGraphPair pair) {
      if(pair == null) {
         return null;
      }

      GraphBounds cbounds = new GraphBounds(pair.getRealSizeVGraph(),
                                            pair.getRealSizeVGraph(), null);
      Rectangle2D contentB = cbounds.getContentBounds();

      return new Dimension((int) contentB.getWidth(), (int) contentB.getHeight());
   }

   /**
    * Get legends size.
    */
   private Dimension2D getLegendsSize(VGraphPair pair) {
      if(pair == null) {
         return null;
      }

      VGraph graph = pair.getRealSizeVGraph();
      LegendGroup legends = graph.getLegendGroup();

      if(legends == null) {
         return null;
      }

      Rectangle2D legendsB = legends.getBounds();
      // use Dimension2D to make sure the legend size ratio correct
      return new DimensionD(legendsB.getWidth(), legendsB.getHeight());
   }

   /**
    * Fix the chart info. If the ref which want to be converted is used in the
    * binding, just remove it from the binding.
    */
   public boolean fixChartInfo(VSChartInfo cinfo, String refName, int changeType) {
      boolean changed = false;
      DataRef temp;

      // clear field in all aesthetic bindings
      for(ChartAggregateRef aggr : cinfo.getAestheticAggregateRefs(false)) {
         changed = clearAestheticFields(aggr, refName) || changed;
      }

      // check the x binding
      for(int i = cinfo.getXFieldCount() - 1; i > -1; i--) {
         temp = cinfo.getXField(i);

         if(changeType == CONVERT_TO_MEASURE && temp instanceof VSChartAggregateRef) {
            continue;
         }
         else if(changeType == CONVERT_TO_DIMENSION && temp instanceof VSChartDimensionRef) {
            continue;
         }

         if(chartHandler.equalsFieldName(refName, temp)) {
            cinfo.removeXField(i);
            changed = true;
         }
      }

      // check the y binding
      for(int i = cinfo.getYFieldCount() - 1; i > -1; i--) {
         temp = cinfo.getYField(i);

         if(changeType == CONVERT_TO_MEASURE && temp instanceof VSChartAggregateRef) {
            continue;
         }
         else if(changeType == CONVERT_TO_DIMENSION && temp instanceof VSChartDimensionRef) {
            continue;
         }

         if(chartHandler.equalsFieldName(refName, temp)) {
            cinfo.removeYField(i);
            changed = true;
         }
      }

      // check the high, low, close, open fields
      if(changeType == CONVERT_TO_DIMENSION) {
         if(cinfo instanceof CandleVSChartInfo) {
            CandleVSChartInfo cdlinfo = (CandleVSChartInfo) cinfo;

            if(cdlinfo.getHighField() != null &&
               chartHandler.equalsFieldName(refName, cdlinfo.getHighField()))
            {
               cdlinfo.setHighField(null);
               changed = true;
            }

            if(cdlinfo.getLowField() != null &&
               chartHandler.equalsFieldName(refName, cdlinfo.getLowField()))
            {
               cdlinfo.setLowField(null);
               changed = true;
            }

            if(cdlinfo.getOpenField() != null &&
               chartHandler.equalsFieldName(refName, cdlinfo.getOpenField()))
            {
               cdlinfo.setOpenField(null);
               changed = true;
            }

            if(cdlinfo.getCloseField() != null &&
               chartHandler.equalsFieldName(refName, cdlinfo.getCloseField()))
            {
               cdlinfo.setCloseField(null);
               changed = true;
            }
         }
         else if(cinfo instanceof GanttVSChartInfo) {
            GanttVSChartInfo ganttVSChartInfo = (GanttVSChartInfo) cinfo;

            if(ganttVSChartInfo.getStartField() != null &&
               chartHandler.equalsFieldName(refName, ganttVSChartInfo.getStartField()))
            {
               ganttVSChartInfo.setStartField(null);
               changed = true;
            }

            if(ganttVSChartInfo.getEndField() != null &&
               chartHandler.equalsFieldName(refName, ganttVSChartInfo.getEndField()))
            {
               ganttVSChartInfo.setEndField(null);
               changed = true;
            }

            if(ganttVSChartInfo.getMilestoneField() != null &&
               chartHandler.equalsFieldName(refName, ganttVSChartInfo.getMilestoneField()))
            {
               ganttVSChartInfo.setMilestoneField(null);
               changed = true;
            }
         }
      }
      else if(changeType == CONVERT_TO_MEASURE) {
         if(cinfo instanceof RelationChartInfo) {
            RelationChartInfo cinfo2 = (RelationChartInfo) cinfo;

            if(cinfo2.getSourceField() != null &&
               chartHandler.equalsFieldName(refName, cinfo2.getSourceField()))
            {
               cinfo2.setSourceField(null);
               changed = true;
            }

            if(cinfo2.getTargetField() != null &&
               chartHandler.equalsFieldName(refName, cinfo2.getTargetField()))
            {
               cinfo2.setTargetField(null);
               changed = true;
            }
         }
      }

      // check aesthetic fields
      changed = clearAestheticFields(cinfo, refName) || changed;

      // check group fields
      for(int i = cinfo.getGroupFieldCount() - 1; i >= 0; i--) {
         VSChartRef group = (VSChartRef) cinfo.getGroupField(i);

         if(chartHandler.equalsFieldName(refName, group)) {
            cinfo.removeGroupField(i);
            changed = true;
         }
      }

      if(cinfo.supportsPathField() && cinfo.getPathField() != null) {
         if(chartHandler.equalsFieldName(refName, cinfo.getPathField())) {
            cinfo.setPathField(null);
            changed = true;
         }
      }

      return changed;
   }

   /**
    * Fix the aggregate info in the chart info.
    */
   public boolean fixAggregateInfo(VSChartInfo cinfo, String refName, int changeType) {
      AggregateInfo ainfo = cinfo.getAggregateInfo();

      if(ainfo == null) {
         return true;
      }

      boolean changed = VSEventUtil.fixAggInfoByConvertRef(ainfo, changeType, refName);

      if(changed) {
         cinfo.setAggregateInfo(sortColumns(ainfo, changeType));
      }

      return changed;
   }

   /**
    * Check if the specified ref is date.
    */
   public boolean isDate(VSChartInfo cinfo, String refName) {
      AggregateInfo ainfo = cinfo.getAggregateInfo();

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aref = ainfo.getAggregate(i);

         if(refName.equals(aref.getName())) {
            String dataType = aref.getDataRef().getDataType();
            return XSchema.isDateType(dataType);
         }
      }

      return false;
   }

   /**
    * Remove any aesthetic binding to the converted ref.
    */
   public boolean clearAestheticFields(ChartBindable cinfo, String refName) {
      boolean changed = false;

      if(cinfo.getColorField() != null &&
         chartHandler.equalsFieldName(refName, cinfo.getColorField().getDataRef()))
      {
         cinfo.setColorField(null);
         changed = true;
      }

      if(cinfo.getShapeField() != null &&
         chartHandler.equalsFieldName(refName, cinfo.getShapeField().getDataRef()))
      {
         cinfo.setShapeField(null);
         changed = true;
      }

      if(cinfo.getSizeField() != null &&
         chartHandler.equalsFieldName(refName, cinfo.getSizeField().getDataRef()))
      {
         cinfo.setSizeField(null);
         changed = true;
      }

      if(cinfo.getTextField() != null &&
         chartHandler.equalsFieldName(refName, cinfo.getTextField().getDataRef()))
      {
         cinfo.setTextField(null);
         changed = true;
      }

      if(cinfo instanceof RelationChartInfo) {
         RelationChartInfo info2 = (RelationChartInfo) cinfo;

         if(info2.getNodeColorField() != null &&
            chartHandler.equalsFieldName(refName, info2.getNodeColorField().getDataRef()))
         {
            info2.setNodeColorField(null);
            changed = true;
         }

         if(info2.getNodeSizeField() != null &&
            chartHandler.equalsFieldName(refName, info2.getNodeSizeField().getDataRef()))
         {
            info2.setNodeSizeField(null);
            changed = true;
         }
      }

      return changed;
   }

   /**
    * Sort the groups and aggregates in AggregateInfo after convertion.
    */
   public AggregateInfo sortColumns(AggregateInfo ainfo, int changeType) {
      AggregateRef[] arefs = ainfo.getAggregates();
      GroupRef[] grefs = ainfo.getGroups();
      AggregateRef[] newAggrRefs = new AggregateRef[arefs.length];
      GroupRef[] newGroupRefs = new GroupRef[grefs.length];
      List list;

      if(changeType == CONVERT_TO_MEASURE) {
         list = Arrays.asList(arefs);
      }
      else {
         list = Arrays.asList(grefs);
      }

      Collections.sort(list, new VSUtil.DataRefComparator());

      if(changeType == CONVERT_TO_MEASURE) {
         list.toArray(newAggrRefs);
         ainfo.setAggregates(newAggrRefs);
      }
      else {
         list.toArray(newGroupRefs);
         ainfo.setGroups(newGroupRefs);
      }

      return ainfo;
   }

   /**
    * Copy all expression refs.
    */
   private List<AggregateRef> copyExpressionRefs(VSChartInfo cinfo) {
      AggregateInfo ainfo = cinfo.getAggregateInfo();
      List<AggregateRef> refs = new ArrayList<>();

      if(ainfo == null || ainfo.isEmpty()) {
         return refs;
      }

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aref = ainfo.getAggregate(i);

         if(aref.getDataRef() instanceof ExpressionRef) {
            refs.add(aref);
         }
      }

      return refs;
   }

   /**
    * Paste the expression to aggregate info.
    */
   private void pasteExproessionRefs(List<AggregateRef> refs, VSChartInfo cinfo) {
      AggregateInfo ainfo = cinfo.getAggregateInfo();

      for(int i = 0; i < refs.size(); i++) {
         AggregateRef aref = refs.get(i);

         if(!ainfo.containsAggregate(aref)) {
            ainfo.addAggregate(aref);
         }
      }
   }

   private final VSChartHandler chartHandler;
   private VSAssemblyInfoHandler assemblyInfoHandler;
   private final PlaceholderService placeholderService;
}
