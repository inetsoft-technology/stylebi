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
package inetsoft.report.script.viewsheet;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.GraphFormatUtil;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.internal.graph.MapData;
import inetsoft.report.script.AbstractChartBindingScriptable;
import inetsoft.uql.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DrillFilterInfo;
import inetsoft.util.Tool;
import inetsoft.util.script.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class represents an ChartInfo in viewsheet the Javascript environment.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class VSChartBindingScriptable extends AbstractChartBindingScriptable {
   /**
    * Create a javascript object represent chart binding infos.
    * @param script the parent scriptable
    */
   public VSChartBindingScriptable(ChartVSAScriptable script) {
      this.script = script;

      addProperty("xFields", "getXFields", "setXFields", Object[].class,
                  getClass(), this);
      addProperty("yFields", "getYFields", "setYFields", Object[].class,
                  getClass(), this);
      addProperty("geoFields", "getGeoFields", "setGeoFields",
                  Object[].class, getClass(), this);
      addProperty("sizes", new VSChartArray("SizeFrame", SizeFrame.class) {
         @Override
         public AbstractChartInfo getInfo() {
            return VSChartBindingScriptable.this.getInfo();
         }
      });
      addProperty("colors", new VSChartArray("ColorFrame", ColorFrame.class) {
         @Override
         public AbstractChartInfo getInfo() {
            return VSChartBindingScriptable.this.getInfo();
         }
      });
      addProperty("shapes", new VSChartArray("ShapeFrame", ShapeFrame.class) {
         @Override
         public AbstractChartInfo getInfo() {
            return VSChartBindingScriptable.this.getInfo();
         }
      });
      addProperty("lines", new VSChartArray("LineFrame", LineFrame.class) {
         @Override
         public AbstractChartInfo getInfo() {
            return VSChartBindingScriptable.this.getInfo();
         }
      });
      addProperty("textures", new VSChartArray("TextureFrame", TextureFrame.class) {
         @Override
         public AbstractChartInfo getInfo() {
            return VSChartBindingScriptable.this.getInfo();
         }
      });
   }

   /**
    * Get the chart info of the chart element.
    */
   @Override
   protected AbstractChartInfo getInfo() {
      return script.getChartInfo();
   }

   protected ChartVSAssembly getChartAssembly() {
      return script.getChartAssembly();
   }

   protected ViewsheetSandbox getViewsheetSandbox() {
      return script.box;
   }

   /**
    * Set a list of x fields in chart info.
    * @return null if value is not set.
    */
   public String[] getXFields() {
      VSChartInfo info = (VSChartInfo) getInfo();
      ChartRef[] refs = info.getXFields();
      String[] names = new String[refs.length];

      for(int i = 0; i < refs.length; i++) {
         names[i] = refs[i].getName();

         // @by larryl, in case the run time ref is not set
      if("".equals(names[i]) && refs[i] instanceof VSAggregateRef) {
            names[i] = ((VSAggregateRef) refs[i]).getVSName();
         }
      }

      return names;
   }

   /**
    * Set a list of x fields in chart info.
    */
   public void setXFields(Object[] fields) {
      VSChartInfo info = (VSChartInfo) getInfo();
      Map<VSDataRef, Hyperlink> links = new HashMap<>();
      collectLinks(info.getRTXFields(), links);
      clearDrillFilter(info.getRTXFields());
      info.removeXFields();

      for(int i = 0; i < fields.length; i++) {
         if(JSObject.isArray(fields[i])) {
            Object[] arr = JSObject.split(fields[i]);
            // array contains column name and column type
            if(arr.length >= 2) {
               String cname = arr[0].toString();
               String ctype = arr[1].toString();
               DataRef ref = createDataRef(cname);
               String formula = arr.length >= 3 ? arr[2].toString() : null;
               int poption = arr.length >= 4 ?
                  Integer.parseInt(arr[3].toString()) :
                  XConstants.PERCENTAGE_NONE;
               String field2 = arr.length == 5 ? arr[4].toString() : null;
               DataRef secCol = field2 == null || field2.equals("") ? null :
                  createDataRef(field2);
               if(ChartConstants.NUMBER.equals(ctype)) {
                  VSChartAggregateRef aref = new VSChartAggregateRef();
                  aref.setDataRef(ref);
                  aref.setColumnValue(cname);
                  AggregateFormula aformula = getFormula(formula);
                  aref.setFormula(aformula);
                  aref.setPercentageOption(poption);

                  if(secCol != null) {
                     aref.setSecondaryColumn(secCol);
                  }

                  if(checkFieldByName(info.getXFields(), aref.getName())) {
                     return;
                  }

                  Hyperlink link = findHyperlink(links, aref);

                  if(link != null) {
                     aref.setHyperlink(link);
                  }

                  info.addXField(aref);
                  info.updateChartType(!info.isMultiStyles());
               }
               else {
                  VSChartDimensionRef dim = createDimensionRef(ref, ctype);

                  if(checkFieldByName(info.getXFields(), dim.getName())) {
                     return;
                  }

                  Hyperlink link = findHyperlink(links, dim);

                  if(link != null) {
                     dim.setHyperlink(link);
                  }

                  dim.setCaption(dim.getFullName());
                  info.addXField(dim);
                  info.updateChartType(!info.isMultiStyles());
               }
            }
         }
      }
   }

   /**
    * Set a list of y fields in chart info.
    */
   public void setYFields(Object[] fields) {
      VSChartInfo info = (VSChartInfo) getInfo();
      Map<VSDataRef, Hyperlink> links = new HashMap<>();
      collectLinks(info.getRTYFields(), links);
      clearDrillFilter(info.getRTYFields());
      info.removeYFields();

      for(int i = 0; i < fields.length; i++) {
         if(JSObject.isArray(fields[i])) {
            Object[] arr = JSObject.split(fields[i]);

            // array contains column name, column type and formula
            if(arr.length >= 2) {
               String cname = arr[0].toString();
               String ctype = arr[1].toString();
               DataRef ref = createDataRef(cname);
               String formula = arr.length >= 3 ? arr[2].toString() : null;
               int poption = arr.length >= 4 ?
                  Integer.parseInt(arr[3].toString()) :
                  XConstants.PERCENTAGE_NONE;
               String field2 = arr.length == 5 ? arr[4].toString() : null;
               DataRef secCol = field2 == null || field2.equals("") ? null :
                  createDataRef(field2);

               if(ChartConstants.NUMBER.equals(ctype)) {
                  VSChartAggregateRef aref = new VSChartAggregateRef();
                  aref.setDataRef(ref);
                  aref.setColumnValue(cname);
                  AggregateFormula aformula = getFormula(formula);
                  aref.setFormula(aformula);
                  aref.setPercentageOption(poption);

                  if(secCol != null) {
                     aref.setSecondaryColumn(secCol);
                  }

                  if(checkFieldByName(info.getYFields(), aref.getName())) {
                     return;
                  }

                  Hyperlink link = findHyperlink(links, aref);

                  if(link != null) {
                     aref.setHyperlink(link);
                  }

                  ChartVSAssembly chartAssembly = getChartAssembly();
                  ChartVSAssemblyInfo cinfo = chartAssembly.getChartInfo();

                  GraphFormatUtil.setDefaultNumberFormat(
                     chartAssembly.getChartDescriptor(), chartAssembly.getVSChartInfo(), XSchema.DOUBLE, aref,
                     ChartConstants.DROP_REGION_Y);

                  info.addYField(aref);
                  info.updateChartType(!info.isMultiStyles());

                  if(cinfo.getDateComparisonInfo() != null) {
                     ChartDcProcessor processor = new ChartDcProcessor(
                        cinfo.getVSChartInfo(), cinfo.getDateComparisonInfo());
                     processor.updateDateComparisonChartType(cinfo.getVSChartInfo());
                  }
               }
               else {
                  VSChartDimensionRef dim = createDimensionRef(ref, ctype);

                  if(checkFieldByName(info.getYFields(), dim.getName())) {
                     return;
                  }

                  Hyperlink link = findHyperlink(links, dim);

                  if(link != null) {
                     dim.setHyperlink(link);
                  }

                  info.addYField(dim);
                  info.updateChartType(!info.isMultiStyles());
               }
            }
         }
      }
   }

   @Override
   public void setColorField(Object arg1, Object arg2, String arg3) {
      super.setColorField(arg1, arg2, arg3);

      GraphFormatUtil.fixDefaultNumberFormat(getChartAssembly().getChartDescriptor(), getInfo());
   }

   @Override
   public void setShapeField(Object arg1, Object arg2, String arg3) {
      super.setShapeField(arg1, arg2, arg3);

      GraphFormatUtil.fixDefaultNumberFormat(getChartAssembly().getChartDescriptor(), getInfo());
   }

   @Override
   public void setSizeField(Object arg1, Object arg2, String arg3) {
      super.setSizeField(arg1, arg2, arg3);

      GraphFormatUtil.fixDefaultNumberFormat(getChartAssembly().getChartDescriptor(), getInfo());
   }

   @Override
   public void setTextField(Object arg1, Object arg2, String arg3) {
      super.setTextField(arg1, arg2, arg3);

      GraphFormatUtil.fixDefaultNumberFormat(getChartAssembly().getChartDescriptor(), getInfo());
   }

   private void clearDrillFilter(ChartRef[] refs) {
      DrillFilterInfo drill = getChartAssembly().getDrillFilterInfo();

      Arrays.stream(refs)
         .map(f -> f.getFullName())
         .forEach(a -> drill.setDrillFilterConditionList(NamedRangeRef.getBaseName(a), null));
   }

   private void collectLinks(VSDataRef[] refs,
      Map<VSDataRef, Hyperlink> links)
   {
      for(VSDataRef ref : refs) {
         if((ref instanceof HyperlinkRef) &&
            ((HyperlinkRef) ref).getHyperlink() != null)
         {
            links.put(ref, ((HyperlinkRef) ref).getHyperlink());
         }
      }
   }

   private Hyperlink findHyperlink(Map<VSDataRef, Hyperlink> links, VSDataRef ref) {
      for(VSDataRef r : links.keySet()) {
         if(r.getName().equals(ref.getName())) {
            return links.get(r);
         }
      }

      return null;
   }

   /**
    * Set a list of x fields in chart info.
    * @return null if value is not set.
    */
   public String[] getYFields() {
      VSChartInfo info = (VSChartInfo) getInfo();
      ChartRef[] refs = info.getYFields();
      String[] names = new String[refs.length];

      for(int i = 0; i < refs.length; i++) {
         names[i] = refs[i].getName();

         // @by larryl, in case the run time ref is not set
         if("".equals(names[i]) && refs[i] instanceof VSAggregateRef) {
            names[i] = ((VSAggregateRef) refs[i]).getVSName();
         }
      }

      return names;
   }

   /**
    * Get a list of geo fields in map info.
    * @return null if value is not set.
    */
   public String[] getGeoFields() {
      VSMapInfo info = getMapInfo();

      if(info == null) {
         return null;
      }

      ChartRef[] refs = info.getGeoFields();
      String[] names = new String[refs.length];

      for(int i = 0; i < refs.length; i++) {
         names[i] = refs[i].getName();
      }

      return names;
   }

   /**
    * Set a list of geo fields in map info.
    */
   public void setGeoFields(Object[] fields) {
      VSMapInfo info = getMapInfo();

      if(info == null) {
         LOG.warn("The current element is not a map");
         return;
      }

      info.removeGeoFields();

      for(int i = 0; i < fields.length; i++) {
         if(JSObject.isArray(fields[i])) {
            Object[] arr = JSObject.split(fields[i]);

            // array contains column name and column type
            if(arr.length >= 2) {
               String cname = arr[0].toString();
               String ctype = arr[1].toString();
               DataRef ref = createDataRef(cname);

               if(ChartConstants.STRING.equals(ctype)) {
                  VSChartGeoRef geo = new VSChartGeoRef();

                  if(checkFieldByName(info.getGeoFields(), ref.getName())) {
                     return;
                  }

                  geo.setGroupColumnValue(cname);
                  geo.setDataRef(ref);
                  info.addGeoField(geo);
               }
            }
         }
      }
   }

   /**
    * Set the map layer for a geographic column.
    * @param field the specified column name.
    * @param layer the specified map layer.
    */
   @Override
   public void setMapLayer(String field, String layer) {
      VSMapInfo info = getMapInfo();

      if(info == null) {
         LOG.warn("The current element is not a map");
         return;
      }

      int l = MapData.getLayer(layer);
      GeoRef gref = null;

      for(ChartRef ref : info.getGeoFields()) {
         if(field.equals(ref.getName())) {
            gref = (GeoRef) ref;
         }
      }

      if(gref != null) {
         gref.getGeographicOption().setLayerValue(l + "");

         ColumnSelection columns = info.getGeoColumns();
         DataRef dataref = columns.getAttribute(field);

         if(dataref instanceof GeoRef) {
            ((GeoRef) dataref).getGeographicOption().setLayerValue(l + "");
         }
      }
      else {
         LOG.warn("Geographic column not found: " + field);
      }

      super.setMapLayer(field, layer);
   }

   /**
    * Set manual mappings.
    * @param field the specified column name.
    * @param value the value that feature mapping mapped to.
    * @param geoCode geographic code.
    */
   @Override
   public void addMapping(String field, String value, String geoCode) {
      VSMapInfo info = getMapInfo();

      if(info == null) {
         LOG.warn("The current element is not a map");
         return;
      }

      updateGeoColumns(script.box, script.getVSAssembly(), info);
      super.addMapping(field, value, geoCode);
      ColumnSelection selection = info.getRTGeoColumns();
      GeoRef geo = (GeoRef) selection.getAttribute(field);

      if(geo == null) {
         return;
      }

      geo.getGeographicOption().getMapping().addMapping(value, geoCode);
   }

   /**
    * Set manual mappings.
    * @param field the specified column name.
    * @param value the value that feature mapping mapped to.
    */
   @Override
   public void removeMapping(String field, String value) {
      VSMapInfo info = getMapInfo();

      if(info == null) {
         LOG.warn("The current element is not a map");
         return;
      }

      updateGeoColumns(script.box, script.getVSAssembly(), info);
      super.removeMapping(field, value);
      ColumnSelection selection = info.getRTGeoColumns();
      GeoRef geo = (GeoRef) selection.getAttribute(field);

      if(geo == null) {
         return;
      }

      geo.getGeographicOption().getMapping().removeMapping(value);
   }

   /**
    * Get the map info of the chart element.
    */
   private VSMapInfo getMapInfo() {
      VSChartInfo info = (VSChartInfo) getInfo();
      return info instanceof VSMapInfo ? (VSMapInfo) info : null;
   }

   /**
    * Create an aesthetic ref for the named field.
    */
   @Override
   protected AestheticRef createAestheticRef(String field, String type) {
      VSAestheticRef aestheticRef = new VSAestheticRef();
      VSDataRef ref0 = findField(field);

      if(ref0 != null) {
         aestheticRef.setALLDataRef(ref0);
         return aestheticRef;
      }

      DataRef ref = createDataRef(field);
      boolean createDimension = !ChartConstants.NUMBER.equals(type);

      if(createDimension) {
         VSChartDimensionRef dref = createDimensionRef(ref, type);
         aestheticRef.setALLDataRef(dref);
      }
      else {
         VSChartAggregateRef aref = new VSChartAggregateRef();
         aref.setDataRef(ref);
         aref.setFormula(AggregateFormula.SUM);
         aestheticRef.setALLDataRef(aref);
      }

      return aestheticRef;
   }

   /**
    * Set the size frame.
    * @param frame the size frame.
    */
   @Override
   public void setSizeFrame(SizeFrame frame) {
      if(frame != null) {
         VSChartInfo info = (VSChartInfo) getInfo();

         // set autoSize to false when setSize of StaticSizeFrame
         if((frame instanceof StaticSizeFrame) &&
            info.getSizeFrameWrapper() != null)
         {
            info.getSizeFrameWrapper().setChanged(true);
         }

         info.setSizeFrame(frame);
      }
   }

   /**
    * Get the size frame.
    * @return the size frame.
    */
   @Override
   public SizeFrame getSizeFrame() {
      VSChartInfo info = (VSChartInfo) getInfo();
      return info != null ? info.getSizeFrame() : null;
   }

   /**
    * Set top n for a dimension column.
    * @param dim the dimension ref
    * @param n the top n
    */
   @Override
   protected void setTopN(XDimensionRef dim, int n) {
      if(dim instanceof VSDimensionRef) {
         VSDimensionRef dim2 = (VSDimensionRef) dim;
         dim2.setRankingNValue(n + "");

         if(n > 0) {
            if(dim2.getRankingOption() == 0) {
               dim2.setRankingOptionValue(XCondition.TOP_N + "");
            }

            if(dim2.getRankingCol() == null) {
               VSDataRef[] aggregates = getInfo().getAggregateRefs();

               if(aggregates.length > 0) {
                  dim2.setRankingColValue(aggregates[0].getFullName());
               }
            }
         }
      }
      else {
         LOG.warn("Dimension column not found: " + dim.getName());
      }
   }

   /**
    * Get top n for a dimension column.
    * @param dim the dimension ref
    * @return the top n
    */
   @Override
   protected int getTopN(XDimensionRef dim) {
      if(dim instanceof VSDimensionRef) {
         int n = 0;

         try{
            n = Integer.parseInt(((VSDimensionRef) dim).getRankingNValue());
         }
         catch (NumberFormatException e){
            LOG.warn(
               "The dimension column: " + dim.getName() +
               " topN is not a valid integer");
         }

         return n;
      }

      LOG.warn("Dimension column not found:" + dim.getName());
      return 0;
   }

   /**
    * Set topn summary column for a dimension column.
    * @param dim the dimension ref
    * @param sumfield the summary column
    */
   @Override
   protected void setTopNSummaryCol(XDimensionRef dim, String sumfield) {
      if(dim instanceof VSDimensionRef) {
         ((VSDimensionRef) dim).setRankingColValue(sumfield);
      }
      else {
         LOG.warn("Dimension column not found: " + dim.getName());
      }
   }

   /**
    * Get topn summary column for a dimension column.
    * @param dim the dimension column ref
    * @return the summary column
    */
   @Override
   protected String getTopNSummaryCol(XDimensionRef dim) {
      if(dim instanceof VSDimensionRef) {
         return ((VSDimensionRef) dim).getRankingColValue();
      }

      LOG.warn("Dimension column not found: " + dim.getName());
      return null;
   }

   /**
    * Set topn reverse for a dimension column.
    * @param dim the dimension column ref
    * @param reserve <code>true</code> if reverse
    */
   @Override
   protected void setTopNReverse(XDimensionRef dim, boolean reserve) {
      if(dim instanceof VSDimensionRef) {
         int op = reserve ? XCondition.BOTTOM_N : XCondition.TOP_N;
         ((VSDimensionRef) dim).setRankingOptionValue(op + "");
      }
      else {
         LOG.warn("Dimension column not found: " +  dim.getName());
      }
   }

   /**
    * Get topn reverse for a dimension column.
    * @param dim the dimension column ref
    * @return <code>true</code> if reverse, <code>false</code> means not found
    * or not reverse
    */
   @Override
   protected boolean isTopNReverse(XDimensionRef dim) {
      if(dim instanceof VSDimensionRef) {
         return ((VSDimensionRef) dim).getRankingOptionValue().equals(
            "" + XCondition.BOTTOM_N);
      }

      LOG.warn("Dimension column not found: " + dim.getName());
      return false;
   }

   /**
    * Create a chart aggregate ref.
    */
   @Override
   protected ChartAggregateRef createChartAggregateRef() {
      return new VSChartAggregateRef();
   }

   /**
    * Create a chart dimension ref.
    */
   @Override
   protected ChartDimensionRef createChartDimensionRef() {
      return new VSChartDimensionRef();
   }

   /**
    * Create a chart dimension ref.
    */
   private VSChartDimensionRef createDimensionRef(DataRef ref, String ctype) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setDataRef(ref);
      dim.setGroupColumnValue(ref.getName());

      if(ChartConstants.DATE.equals(ctype)) {
         ((BaseField) ref).setDataType(ctype);
      }

      return dim;
   }

   /**
    * Update map geographic column selection.
    * @param assembly the chart assembly to update.
    * @param info the map info.
    */
   private void updateGeoColumns(ViewsheetSandbox box, VSAssembly assembly,
                                 VSChartInfo info)
   {
      try {
         Viewsheet vs = box.getViewsheet();
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
    * Get all the dimensionrefs which contains the specifed dataref.
    * @param aesthetic if contains aesthetic refs.
    */
   private VSDimensionRef[] getDimensions(DataRef fld, boolean aesthetic) {
      if(fld == null) {
         return null;
      }

      VSChartInfo info = (VSChartInfo) getInfo();
      XDimensionRef[] xrefs = info.getAllDimensions(fld, aesthetic);
      ArrayList<VSDimensionRef> dims = new ArrayList<>();

      for(XDimensionRef ref : xrefs) {
         if(ref instanceof VSDimensionRef) {
            dims.add((VSDimensionRef) ref);
         }
      }

      if(info instanceof VSMapInfo) {
         VSMapInfo minfo = (VSMapInfo) info;
         ArrayList<VSDimensionRef> refs = new ArrayList<>();
         refs.addAll(dims);
         ChartRef[] grefs = minfo.getGeoFields();

         for(int i = 0; i < grefs.length; i++) {
            if(grefs[i] instanceof VSDimensionRef) {
               VSDimensionRef dim = (VSDimensionRef) grefs[i];

               if(Tool.equals(dim.getDataRef(), fld)) {
                  refs.add(dim);
               }
            }
         }

         VSDimensionRef[] arefs = new VSDimensionRef[refs.size()];
         refs.toArray(arefs);
         return arefs;
      }
      else {
         return dims.toArray(new VSDimensionRef[0]);
      }
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "VSChartInfo";
   }

   private ChartVSAScriptable script;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSChartBindingScriptable.class);
}
