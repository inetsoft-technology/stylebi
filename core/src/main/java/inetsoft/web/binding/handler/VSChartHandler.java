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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.mv.MVExecutionException;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.internal.graph.MapHelper;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class VSChartHandler extends ChartHandler {
   /**
    * Creates a new instance of <tt>VSChartHandler</tt>.
    *
    * @param assetRepository the asset repository.
    * @param viewsheetService
    */
   @Autowired
   public VSChartHandler(
      AssetRepository assetRepository,
      ViewsheetService viewsheetService,
      CalculatorHandler calculatorHandler)
   {
      this.assetRepository = assetRepository;
      this.viewsheetService = viewsheetService;
      this.calculatorHandler = calculatorHandler;
   }

   public DataSet getChartData(ViewsheetSandbox box0, ChartVSAssembly assembly) throws Exception {
      return getChartData(box0, assembly, true);
   }

   public DataSet getChartData(ViewsheetSandbox box0, ChartVSAssembly assembly, boolean shrink)
      throws Exception
   {
      ChartVSAssembly nassembly = assembly.clone();
      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) nassembly.getVSAssemblyInfo();
      String name = ainfo.getName() + "__map__" + idCounter.incrementAndGet();

      if(name.contains(".")) {
         int idx = name.lastIndexOf(".");
         name = name.substring(idx);
      }

      ainfo.setName(name);
      VSChartInfo cinfo = ainfo.getVSChartInfo();
      nassembly.setBrushSelection(null);
      ColumnSelection rcols = cinfo.getRTGeoColumns();

      for(int i = 0; i < rcols.getAttributeCount(); i++) {
         DataRef ref = rcols.getAttribute(i);

         if(!(ref instanceof VSChartGeoRef)) {
            continue;
         }

         DataRef dref = getRTFieldByName(cinfo, ref.getName());

         if(dref == null) {
            cinfo.addXField((VSChartGeoRef) ref);
         }
      }

      final ViewsheetSandbox box = getSandbox(box0, assembly.getAbsoluteName());
      Viewsheet vs = box.getViewsheet();

      try {
         vs.addAssembly(nassembly, false, false, false);

         ChartVSAQuery query = (ChartVSAQuery) VSAQuery.createVSAQuery(
            box, nassembly, DataMap.NORMAL);
         query.setIgnoreRuntimeCondition(true);
         query.setNoEmpty(false);
         query.setShrinkTable(shrink);
         ColumnSelection columns = query.getDefaultColumnSelection();
         cinfo.update(vs, columns, true, null, null, null);
         DataRef[] flds = cinfo.getRTFields();

         // ignore ranking
         for(int i = 0; i < flds.length; i++) {
            if(flds[i] instanceof VSDimensionRef) {
               VSDimensionRef dim = (VSDimensionRef) flds[i];
               dim.setRankingCondition(null);
            }
         }

         clearNamedGroups(cinfo);
         return (VSDataSet) query.getData();
      }
      catch(MVExecutionException ex) {
         LOG.debug("Failed to get chart data from MV.");
         return null;
      }
      finally {
         vs.removeAssembly(name, false);
      }
   }

   /**
    * Ref auto detect.
    * @param vs the viewsheet.
    * @param sourceInfo map assembly source info.
    * @param info viewsheet map info.
    * @param refName the ref name that changes geographic.
    * @param source map assembly dataset.
    */
   public void autoDetect(Viewsheet vs, SourceInfo sourceInfo, VSChartInfo info,
      String refName, DataSet source)
   {
      GeoRef geoCol = (GeoRef) getGeoCol(info, refName);

      if(geoCol != null) {
         GeographicOption opt = geoCol.getGeographicOption();
         MapHelper.autoDetect(vs, sourceInfo, info, opt, refName, source);
      }

      copyGeoColumns(info);
   }

   public void copyGeoColumns(VSChartInfo info) {
      ColumnSelection rcols = info.getRTGeoColumns();

      for(int i = 0; i < rcols.getAttributeCount(); i++) {
         DataRef dataRef = rcols.getAttribute(i);

         if(!(dataRef instanceof VSChartGeoRef)) {
            continue;
         }

         VSChartGeoRef geoRef = (VSChartGeoRef) dataRef;
         GeoRef geoCol = (GeoRef) getGeoCol(info, geoRef.getName());

         if(geoCol != null) {
            GeographicOption opt = (GeographicOption)
               geoCol.getGeographicOption().clone();
            geoRef.setGeographicOption(opt);
         }
      }
   }

   /**
    * Update map geographic column selection.
    * @param chart the chart assembly to update.
    */
   public void updateAllGeoColumns(ViewsheetSandbox box, Viewsheet vs, ChartVSAssembly chart) {
      Assembly[] assemblies = vs.getAssemblies(true);
      new ArrayList<VSChartInfo>();
      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo)
         chart.getVSAssemblyInfo();
      SourceInfo sinfo = ainfo.getSourceInfo();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof ChartVSAssembly) {
            ChartVSAssembly chart0 = (ChartVSAssembly) assembly;
            ChartVSAssemblyInfo ainfo0 = (ChartVSAssemblyInfo)
               chart0.getVSAssemblyInfo();
            VSChartInfo cinfo0 = ainfo0.getVSChartInfo();
            SourceInfo sinfo0 = chart0.getSourceInfo();

            if(Tool.equals(sinfo0, sinfo)) {
               updateGeoColumns(box, vs, chart0, cinfo0);
            }
         }
      }
   }

   /**
    * Get geo col or geo field from map info.
    */
   protected VSChartGeoRef getGeoRef(VSChartInfo info, String refName) {
      DataRef ref = info.getRTGeoColumns().getAttribute(refName);
      return ref instanceof VSChartGeoRef ? (VSChartGeoRef) ref : null;
   }

   /**
    * Update map geographic column selection.
    * @param assembly the chart assembly to update.
    * @param info the map info.
    */
   public void updateGeoColumns(ViewsheetSandbox box, Viewsheet vs,
      ChartVSAssembly assembly, VSChartInfo info)
   {
      box = getSandbox(box, assembly.getAbsoluteName());

      try {
         ChartVSAQuery query = (ChartVSAQuery) VSAQuery.createVSAQuery(box,
            assembly, DataMap.NORMAL);
         query.setShrinkTable(false);
         ColumnSelection columns = query.getDefaultColumnSelection();
         info.updateGeoColumns(vs, columns);
      }
      catch(Exception ex) {
         // ignore it
      }
   }

   /**
    * Get viewsheet sand box.
    */
   private ViewsheetSandbox getSandbox(ViewsheetSandbox box, String name) {
      int index = name.lastIndexOf(".");

      if(index >= 0) {
         ViewsheetSandbox box0 = box.getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         return getSandbox(box0, name);
      }

      return box;
   }

   /**
    * Change geographic.
    * @param info viewsheet map info.
    * @param refName the ref name that changes geographic.
    * @param type change type, set or clear
    * @param dim true if the ref is dimension.
    */
   public DataRef changeGeographic(VSChartInfo info, String refName,
                                   String type, boolean dim)
   {
      ColumnSelection cols = info.getGeoColumns();
      ColumnSelection rcols = info.getRTGeoColumns();

      if(type.equals(SET_GEOGRAPHIC)) {
         if(dim) {
            VSChartGeoRef ref = new VSChartGeoRef();
            ref.setGroupColumnValue(refName);
            cols.addAttribute(ref);
            return ref;
         }
         else {
            BaseField ref = new BaseField(refName);
            cols.addAttribute(ref);
            return ref;
         }
      }
      else {
         for(int i = 0; i < rcols.getAttributeCount(); i++) {
            DataRef ref = rcols.getAttribute(i);

            if(refName.equals(ref.getName())) {
               cols.removeAttribute(i);

               if(cols.getAttributeCount() == 0) {
                  info.setMeasureMapType("");
               }

               return ref;
            }
         }
      }

      return null;
   }

   /**
    * Fix the shape frame for the shape field depending on the chart type.
    * @param info the chart info needed to be fixed.
    * @param type0 the chart type.
    */
   public void fixShapeField(VSChartInfo info, int type0) {
      List<VSAestheticRef> arefs = new ArrayList<>();
      List<Integer> types = new ArrayList<>();

      arefs.add((VSAestheticRef) info.getShapeField());
      types.add(type0);

      for(Object ref : info.getAggregateRefs()) {
         if(ref instanceof ChartAggregateRef) {
            ChartAggregateRef aggr = (ChartAggregateRef) ref;
            arefs.add((VSAestheticRef) aggr.getShapeField());

            if(info.isMultiStyles()) {
               types.add(aggr.getRTChartType());
            }
            else {
               types.add(type0);
            }
         }
      }

      for(int i = 0; i < arefs.size(); i++) {
         VSAestheticRef sfield = arefs.get(i);

         if(sfield == null || sfield.getVisualFrame() == null) {
            continue;
         }

         VisualFrame sframe = sfield.getVisualFrame();
         VisualFrame newFrame;
         int type = types.get(i);

         if(GraphUtil.isCategorical(sfield.getDataRef())) {
            if(GraphTypes.supportsPoint(type, info)) {
               if(!(sframe instanceof CategoricalShapeFrame)) {
                  newFrame = new CategoricalShapeFrame();
                  sfield.setVisualFrame(newFrame);
               }
            }
            else if(GraphTypes.supportsLine(type, info)) {
               if(!(sframe instanceof CategoricalLineFrame)) {
                  newFrame = new CategoricalLineFrame();
                  sfield.setVisualFrame(newFrame);
               }
            }
            else if(GraphTypes.supportsTexture(type)) {
               if(!(sframe instanceof CategoricalTextureFrame)) {
                  newFrame = new CategoricalTextureFrame();
                  sfield.setVisualFrame(newFrame);
               }
            }
         }
         else {
            if(GraphTypes.supportsPoint(type, info)) {
               if(!(sframe instanceof LinearShapeFrame)) {
                  newFrame = new FillShapeFrame();
                  sfield.setVisualFrame(newFrame);
               }
            }
            else if(GraphTypes.supportsLine(type, info)) {
               if(!(sframe instanceof LinearLineFrame)) {
                  newFrame = new LinearLineFrame();
                  sfield.setVisualFrame(newFrame);
               }
            }
            else if(GraphTypes.supportsTexture(type)) {
               if(!(sframe instanceof LinearTextureFrame)) {
                  newFrame = new LeftTiltTextureFrame();
                  sfield.setVisualFrame(newFrame);
               }
            }
         }
      }
   }

   /**
    * Source info changed, fix the aggregate info of the chart info.
    */
   public void fixAggregateInfo(ChartVSAssemblyInfo info, Viewsheet vs,
                                AggregateInfo oainfo)
   {
      VSChartInfo cinfo = info.getVSChartInfo();
      SourceInfo sinfo = info.getSourceInfo();
      AggregateInfo ainfo = cinfo.getAggregateInfo();

      if(sinfo == null || (ainfo != null && !ainfo.isEmpty())) {
         return;
      }

      if(ainfo == null) {
         ainfo = new AggregateInfo();
         cinfo.setAggregateInfo(ainfo);
      }

      TableAssembly table = getTableAssembly(sinfo, vs);

      if(table != null) {
         VSEventUtil.createAggregateInfo(table, ainfo, oainfo, vs, true);
      }
   }

   /**
    * Get table assembly by source info.
    */
   protected TableAssembly getTableAssembly(SourceInfo sinfo, Viewsheet vs) {
      return VSEventUtil.getTableAssembly(vs, sinfo, assetRepository, null);
   }

   private void clearNamedGroups(VSChartInfo info) {
      VSDataRef[] refs = info.getRTFields();

      for(VSDataRef ref : refs) {
         if(ref instanceof VSDimensionRef) {
            ((VSDimensionRef) ref).setNamedGroupInfo(null);
         }
      }
   }

   private DataRef getRTFieldByName(VSChartInfo info, String name) {
      VSDataRef[] refs = info.getRTFields();

      for(int i = 0; i < refs.length; i++) {
         if(name.equals(refs[i].getName())) {
            return refs[i];
         }
      }

      return null;
   }

   private DataRef getGeoCol(VSChartInfo info, String refName) {
      ColumnSelection rcols = info.getRTGeoColumns();
      GeoRef rGeoCol = (GeoRef) rcols.getAttribute(refName);
      int idx = rcols.indexOfAttribute(rGeoCol);
      return idx >= 0 ? info.getGeoColumns().getAttribute(idx) : null;
   }

   /**
    * Get all dimensions on binding.
    *
    * @param aesthetic if contains aesthetic dimensions.
    */
   public List<XDimensionRef> getAllDimensions(VSChartInfo cinfo, boolean aesthetic) {
      List<XDimensionRef> list = new ArrayList<>();
      getAllDimensions(cinfo.getBindingRefs(true), list);

      if(aesthetic) {
         getAllDimensions(cinfo.getAestheticRefs(true), list);
      }

      return list;
   }

   public VSChartInfo getChartInfo(String vsId, String assemblyName,
      Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ChartVSAssembly assembly = (ChartVSAssembly) viewsheet.getAssembly(assemblyName);
      VSChartInfo cinfo = assembly.getVSChartInfo();

      return cinfo;
   }

   /**
    * Create commands, check if tree data and aesthetic of chart changed or not.
    */
   public int createCommands(ChartVSAssemblyInfo oinfo, ChartVSAssemblyInfo ninfo)
   {
      VSChartInfo ocinfo = oinfo.getVSChartInfo();
      VSChartInfo ncinfo = ninfo.getVSChartInfo();
      VSMapInfo ominfo = ocinfo instanceof VSMapInfo ? (VSMapInfo) ocinfo : null;
      VSMapInfo nminfo = ncinfo instanceof VSMapInfo ? (VSMapInfo) ncinfo : null;

      // tree changed?
      // source info, agginfo, pagginfo, etc.
      boolean treeChanged = false;

      if(!Tool.equals(oinfo.getSourceInfo(), ninfo.getSourceInfo()) ||
         !equalsContent(ocinfo.getAggregateInfo(), ncinfo.getAggregateInfo()) ||
         (ocinfo != null && ncinfo != null &&
         !ocinfo.getGeoColumns().equals(ncinfo.getGeoColumns())) ||
         ocinfo != null && ncinfo == null)
      {
         treeChanged = true;
      }

      // data changed?
      // chart type, class type, data binding, etc.
      boolean dataChanged = false;

      if(!Tool.equals(ocinfo.getClass().getName(), ncinfo.getClass().getName()))
      {
         dataChanged = true;
      }
      else if(ocinfo.isSeparatedGraph() != ncinfo.isSeparatedGraph()) {
         dataChanged = true;
      }
      else if(ocinfo.isMultiStyles() != ncinfo.isMultiStyles()) {
         dataChanged = true;
      }
      else if(ocinfo.getChartType() != ncinfo.getChartType()) {
         dataChanged = true;
      }
      else {
         if(ominfo != null && nminfo != null) {
            if(!equalsContent(ominfo.getXFields(), nminfo.getXFields()) ||
               !equalsContent(ominfo.getYFields(), nminfo.getYFields()) ||
               !equalsContent(ominfo.getGeoFields(), nminfo.getGeoFields()))
            {
               dataChanged = true;
            }
         }
         else if(ocinfo instanceof CandleVSChartInfo) {
            CandleVSChartInfo occ = (CandleVSChartInfo) ocinfo;
            CandleVSChartInfo ncc = (CandleVSChartInfo) ncinfo;

            if(!equalsContent(occ.getHighField(), ncc.getHighField()) ||
               !equalsContent(occ.getLowField(), ncc.getLowField()) ||
               !equalsContent(occ.getCloseField(), ncc.getCloseField()) ||
               !equalsContent(occ.getOpenField(), ncc.getOpenField()))
            {
               dataChanged = true;
            }
         }
         else if(!equalsContent(ocinfo.getGroupFields(),
                                ncinfo.getGroupFields())) {
            dataChanged = true;
         }

         if(!dataChanged &&
            !equalsContent(ocinfo.getXFields(), ncinfo.getXFields()) ||
            !equalsContent(ocinfo.getYFields(), ncinfo.getYFields()))
         {
            dataChanged = true;
         }
      }

      // aesthetic changed?
      boolean aestheticChanged = false;

      if(!Tool.equals(ocinfo.getClass().getName(), ncinfo.getClass().getName()))
      {
         aestheticChanged = true;
      }
      else {
         AestheticRef colorField = ocinfo.getColorField();
         AestheticRef shapeField = ocinfo.getShapeField();
         AestheticRef sizeField = ocinfo.getSizeField();
         AestheticRef textField = ocinfo.getTextField();

         if(!equalsContent(colorField, ncinfo.getColorField()) ||
            !equalsContent(shapeField, ncinfo.getShapeField()) ||
            !equalsContent(sizeField, ncinfo.getSizeField()) ||
            !equalsContent(textField, ncinfo.getTextField()))
         {
            aestheticChanged = true;
         }
         else if(colorField == null || shapeField == null || sizeField == null ||
                 textField == null)
         {
            if(!Tool.equals(ocinfo.getSizeFrame(), ncinfo.getSizeFrame())) {
               aestheticChanged = true;
            }
            else if(ocinfo instanceof MergedVSChartInfo) {
               ColorFrame cFrame = ocinfo.getColorFrame();
               ShapeFrame spFrame = ocinfo.getShapeFrame();
               TextureFrame tFrame = ocinfo.getTextureFrame();
               LineFrame lFrame = ocinfo.getLineFrame();

               if(!Tool.equals(cFrame, ncinfo.getColorFrame()) ||
                  !Tool.equals(spFrame, ncinfo.getShapeFrame()) ||
                  !Tool.equals(tFrame, ncinfo.getTextureFrame()) ||
                  !Tool.equals(lFrame, ncinfo.getLineFrame()))
               {
                  aestheticChanged = true;
               }
            }
            // xy charts
            else {
               List olist = getModelRefs(ocinfo);
               List nlist = getModelRefs(ncinfo);

               if(olist.size() != nlist.size()) {
                  aestheticChanged = true;
               }
               else {
                  if(ocinfo.getRTFields().length != ncinfo.getRTFields().length)
                  {
                     aestheticChanged = true;
                  }

                  for(int i = 0; i < olist.size(); i++) {
                     VSChartAggregateRef oaggr = (VSChartAggregateRef) olist.get(i);
                     VSChartAggregateRef naggr = (VSChartAggregateRef) nlist.get(i);

                     if(!equalsFrame(oaggr, naggr)) {
                        aestheticChanged = true;
                        break;
                     }

                     if(!Tool.equals(oaggr.getTextFormat(), naggr.getTextFormat())) {
                        aestheticChanged = true;
                        break;
                     }

                     colorField = oaggr.getColorField();
                     shapeField = oaggr.getShapeField();
                     sizeField = oaggr.getSizeField();
                     textField = oaggr.getTextField();

                     if(!equalsContent(colorField, naggr.getColorField()) ||
                        !equalsContent(shapeField, naggr.getShapeField()) ||
                        !equalsContent(sizeField, naggr.getSizeField()) ||
                        !equalsContent(textField, naggr.getTextField()))
                     {
                        aestheticChanged = true;
                        dataChanged = true;
                        break;
                     }
                  }
               }
            }
         }

         if(!aestheticChanged) {
            PlotDescriptor ode = oinfo.getChartDescriptor().getPlotDescriptor();
            PlotDescriptor nde = ninfo.getChartDescriptor().getPlotDescriptor();
            aestheticChanged = ode.isValuesVisible() != nde.isValuesVisible();
         }
      }

      int hint = treeChanged || dataChanged || aestheticChanged
         ? VSAssembly.INPUT_DATA_CHANGED | VSAssembly.VIEW_CHANGED
         : VSAssembly.NONE_CHANGED;

      return hint;
   }

   /**
    * Fix map info, remove non-geo ref from x/y/geo.
    * @param info viewsheet map info.
    * @param refName  the ref name that changes geographic.
    * @param type change type, set or clear
    */
   public boolean fixMapInfo(VSChartInfo info, String refName, String type) {
      boolean changed = false;

      if(type.equals(SET_GEOGRAPHIC)) {
         return false;
      }

      // x fields
      for(int i = info.getXFieldCount() - 1; i >= 0; i--) {
         ChartRef ref = info.getXField(i);

         if(ref instanceof VSChartAggregateRef && equalsFieldName(refName, ref)) {
            info.removeXField(i);
            changed = true;
         }
      }

      // y fields
      for(int i = info.getYFieldCount() - 1; i >= 0; i--) {
         ChartRef ref = info.getYField(i);

         if(ref instanceof VSChartAggregateRef && equalsFieldName(refName, ref)) {
            info.removeYField(i);
            changed = true;
         }
      }

      // geo fields
      if(info instanceof VSMapInfo) {
         VSMapInfo minfo = (VSMapInfo) info;

         for(int i = minfo.getGeoFieldCount() - 1; i >= 0; i--) {
            ChartRef ref = minfo.getGeoFieldByName(i);

            if(equalsFieldName(refName, ref)) {
               minfo.removeGeoField(i);
               changed = true;
            }
         }
      }

      return changed;
   }

   /**
    * Check if the data ref's dynamic name equals to the specified ref name.
    * @param refName the specifed ref name
    * @param ref the data ref
    */
   public boolean equalsFieldName(String refName, DataRef ref) {
      String name = null;

      if(ref instanceof VSAggregateRef) {
         name = ((VSAggregateRef) ref).getColumnValue();
      }
      else if(ref instanceof VSDimensionRef) {
         name = ((VSDimensionRef) ref).getGroupColumnValue();
      }

      return Tool.equals(name, refName);
   }

   /**
    * Clear the color frame when use defined color change.
    * @param cinfo vs chart info to clear color assign.
    */
   public static void clearColorFrame(VSChartInfo cinfo, boolean isOthers, DataRef aggr) {
      if(cinfo == null) {
         return;
      }

      if(aggr == null) {
         clearColorAssign(cinfo.getColorField(), isOthers);
      }

      if(cinfo instanceof RelationChartInfo) {
         clearColorAssign(((RelationChartInfo) cinfo).getNodeColorField(), isOthers);
      }

      for(ChartAggregateRef aggr2 : cinfo.getAestheticAggregateRefs(false)) {
         // if only changing color field for an aggregate in a multi-style chart, only clear
         // the corresponding aggregate's color frame. (60178)
         if(!(aggr instanceof VSDataRef) ||
            ((VSDataRef) aggr).getFullName().equals(aggr2.getFullName()))
         {
            clearColorAssign(aggr2.getColorField(), isOthers);
         }
      }
   }

   private static void clearColorAssign(AestheticRef colorAestheticRef, boolean isOthers) {
      if(colorAestheticRef == null) {
         return;
      }

      if(colorAestheticRef.getVisualFrame() instanceof CategoricalColorFrame) {
         CategoricalColorFrame frame = (CategoricalColorFrame) colorAestheticRef.getVisualFrame();

         if(!frame.isUseGlobal()) {
            return;
         }

         if(isOthers) {
            frame.setColor("Others", null);
         }
         else {
            frame.clearStatic();
         }
      }
   }

   /**
    * Get all model data refs.
    */
   private static List getModelRefs(VSChartInfo cinfo) {
      ArrayList yarr = new ArrayList();
       // there is no runtime infomation now
      ChartRef[] yrefs = cinfo.getXFields();

      for(int i = 0; i < yrefs.length; i++) {
         ChartRef ref = yrefs[i];

         if(ref instanceof VSChartAggregateRef) {
            yarr.add(ref);
         }
      }

      ArrayList xarr = new ArrayList();
      ChartRef[] xrefs = cinfo.getYFields();

      for(int i = 0; i < xrefs.length; i++) {
         ChartRef ref = xrefs[i];

         if(ref instanceof VSChartAggregateRef) {
            xarr.add(ref);
         }
      }

      if(yarr.size() >= xarr.size()) {
         return yarr;
      }

      return xarr;
   }

   /**
    * Check if two object equals in content.
    */
   private static boolean equalsContent(Object obj, Object obj2) {
      if(obj instanceof Object[]) {
         if(!(obj2 instanceof Object[])) {
            return false;
         }

         Object[] arr1 = (Object[]) obj;
         Object[] arr2 = (Object[]) obj2;

         if(arr1.length != arr2.length) {
            return false;
         }

         for(int i = 0; i < arr1.length; i++) {
            if(!Tool.equalsContent(arr1[i], arr2[i])) {
               return false;
            }
         }

         return true;
      }

      return Tool.equalsContent(obj, obj2);
   }

   /**
    * Check if two object equals in frame.
    */
   private static boolean equalsFrame(VSChartAggregateRef ref1,
      VSChartAggregateRef ref2)
   {
      if(!Tool.equals(ref1.getColorFrame(), ref2.getColorFrame()) ||
         !Tool.equals(ref1.getShapeFrame(), ref2.getShapeFrame()) ||
         !Tool.equals(ref1.getTextureFrame(), ref2.getTextureFrame()) ||
         !Tool.equals(ref1.getLineFrame(), ref2.getLineFrame()) ||
         !Tool.equals(ref1.getSummaryColorFrame(),
            ref2.getSummaryColorFrame()) ||
         !Tool.equals(ref1.getSummaryTextureFrame(),
            ref2.getSummaryTextureFrame()))
      {
         return false;
      }

      if(GraphTypes.supportsPoint(ref1.getChartType(), null) !=
         GraphTypes.supportsPoint(ref2.getChartType(), null) ||
         GraphTypes.supportsLine(ref1.getChartType(), null) !=
         GraphTypes.supportsLine(ref2.getChartType(), null) ||
         GraphTypes.supportsTexture(ref1.getChartType()) !=
         GraphTypes.supportsTexture(ref2.getChartType()) ||
         GraphTypes.isWaterfall(ref1.getChartType()) !=
         GraphTypes.isWaterfall(ref2.getChartType()))
      {
         return false;
      }

      return true;
   }

   private final AssetRepository assetRepository;
   private final ViewsheetService viewsheetService;

   public static final String SET_GEOGRAPHIC = "set";
   public static final String CLEAR_GEOGRAPHIC = "clear";
   private static final AtomicLong idCounter = new AtomicLong(0);
   private static final Logger LOG =
      LoggerFactory.getLogger(VSChartHandler.class);
}
