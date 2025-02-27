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
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.*;
import inetsoft.graph.element.SchemaElement;
import inetsoft.graph.scale.*;
import inetsoft.graph.schema.CandlePainter;
import inetsoft.graph.schema.SchemaPainter;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.XAggregateRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * CandleGraphGenerator generates candle element graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class CandleGraphGenerator extends MergedGraphGenerator {
   /**
    * Constructor.
    * @param chart the specified chart.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    */
   public CandleGraphGenerator(ChartVSAssemblyInfo chart, DataSet adata, DataSet data,
                               VariableTable vars, DataSet vdata, int sourceType, Dimension size)
   {
      super(chart, adata, data, vars, vdata, sourceType, size);
   }

   /**
    * Constructor.
    * @param info the specified chart info.
    * @param desc the specified chart descriptor.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    * @param vsrc the source (worksheet/tbl, query) of the chart.
    */
   public CandleGraphGenerator(ChartInfo info, ChartDescriptor desc, DataSet adata, DataSet data,
                               VariableTable vars, String vsrc, int sourceType, Dimension size)
   {
      super(info, desc, adata, data, vars, vsrc, sourceType, size);
   }

   /**
    * Check the validity.
    * @return true if valid, false invalid (no warn for better user experience).
    * It will throw exception to warn user when serious problem found.
    */
   @Override
   protected boolean checkValidity() {
      // do not support invert
      if(xmeasures.size() > 0) {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.graph.candleNotInverted"), LogLevel.WARN, true,
                                    ConfirmException.INFO);
      }

      ChartRef[] refs = info.getRTYFields();

      if(refs.length > 0 && GraphUtil.isMeasure(refs[refs.length - 1])) {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.graph.aggNotAllowed"), LogLevel.WARN, true,
                                    ConfirmException.INFO);
      }

      return true;
   }

   /**
    * Check the data is valid.
    */
   private boolean isValid() {
      ChartRef[] xrefs = info.getRTXFields();
      ChartRef[] yrefs = info.getRTYFields();

      if((xrefs.length == 0 || !GraphUtil.isDimension(xrefs[xrefs.length - 1])) &&
         (yrefs.length == 0 || !GraphUtil.isDimension(yrefs[yrefs.length - 1])))
      {
         dimContained = false;
      }

      CandleChartInfo sinfo = (CandleChartInfo) info;

      if(sinfo.getLowField() == null) {
         lowContained = false;
      }

      if(sinfo.getHighField() == null) {
         highContained = false;
      }

      if(sinfo.getCloseField() == null) {
         closeContained = false;
      }

      if(sinfo.getOpenField() == null) {
         openContained = false;
      }

      if(dimContained && lowContained && highContained &&
         closeContained && (openContained || !isOpenRequired()))
      {
         return true;
      }

      return false;
   }

   /**
    * Create invalidate element graph.
    */
   private void createFakeEGraph() {
      createFakeData();

      // 1. add fields
      addXDFields();
      addYDFields();

      CandleChartInfo sinfo = (CandleChartInfo) info;
      String name = null;
      String label = null;

      if(dimContained) {
         ChartRef[] refs = info.getRTXFields();
         ChartRef ref = null;

         for(int i = 0; i < refs.length; i++) {
            if(GraphUtil.isDimension(refs[i])) {
               ref = refs[i];
            }
         }

         name = GraphUtil.getName(ref);
         label = GraphUtil.getCaption(ref);
         label = Tool.localize(label);

         // inner-most x dim added in addXDFields(), need to removed and replaced by name later.
         if(!xdims.isEmpty()) {
            xdims.remove(xdims.size() - 1);
         }
      }
      else if(ydims.size() > 0) {
         name = "Fake";
         label = name;
      }

      if(name != null) {
         xdims.add(name);
         xdimlabels.put(name, label);
         scales.put(name, new CategoricalScale(name));
         ChartRef dref = getChartRef(name, true, info.getRTXFields());

         if(dref instanceof XDimensionRef) {
            fixDScale(name, isDate((XDimensionRef) dref), getSchemaType());
         }
      }

      XAggregateRef lref = (XAggregateRef) sinfo.getRTLowField();
      XAggregateRef href = (XAggregateRef) sinfo.getRTHighField();
      XAggregateRef oref = (XAggregateRef) sinfo.getRTOpenField();
      XAggregateRef cref = (XAggregateRef) sinfo.getRTCloseField();
      String hname = href == null ? "High" : GraphUtil.getName(href);
      String oname = oref == null ? "Open" : GraphUtil.getName(oref);
      String cname = cref == null ? "Close" : GraphUtil.getName(cref);
      String lname = lref == null ? "Low" : GraphUtil.getName(lref);
      String hlabel = href == null ? "High" : GraphUtil.getCaption(href);
      String olabel = oref == null ? "Open" : GraphUtil.getCaption(oref);
      String clabel = cref == null ? "Close" : GraphUtil.getCaption(cref);
      String llabel = lref == null ? "Low" : GraphUtil.getCaption(lref);
      String[] flds = new String[] {hname, cname, oname, lname};

      // 2. add elements
      SchemaElement element = new SchemaElement(createSchemaPainter());

      for(String xdim : xdims) {
         element.addDim(xdim);
      }

      element.addSchema(flds);
      element.setHint("editable", "false");
      element.setHint("_fake_", "true");

      StaticColorFrame colorFrame = new StaticColorFrame();
      StaticSizeFrame sizeFrame = new StaticSizeFrame();
      StaticTextureFrame textFrame = new StaticTextureFrame();

      colorFrame.setDefaultColor(Color.gray);
      sizeFrame.setSize(10, CompositeValue.Type.DEFAULT);
      element.setColorFrame(colorFrame);
      element.setSizeFrame(sizeFrame);
      element.setTextureFrame(textFrame);
      graph.addElement(element);

      // 3. add coords
      ymeasures.add(hname);
      ymeasures.add(cname);
      ymeasures.add(oname);
      ymeasures.add(lname);
      ymeasurelabels.put(hname, hlabel);
      ymeasurelabels.put(cname, clabel);
      ymeasurelabels.put(oname, olabel);
      ymeasurelabels.put(lname, llabel);
      AxisDescriptor axis = getAxisDescriptor(lname);
      Scale yscale = createScale(axis, null, flds, null);
      scales.put(hname, yscale);
      scales.put(oname, yscale);
      scales.put(cname, yscale);
      scales.put(lname, yscale);
      Scale xscale = null;

      if(xdims.size() > 0) {
         String xname = xdims.get(xdims.size() - 1);
         xscale = scales.get(xname);
      }

      // leave gap, then candle bar will not overlap axis
      if(xscale instanceof TimeScale) {
         ((TimeScale) xscale).setFill(false);
      }

      Coordinate coord = new RectCoord(xscale, yscale);
      fixCoordProperties(coord, GraphTypes.CHART_CANDLE);

      Coordinate graphCoord = createCoord(coord, getSchemaType());
      graph.setCoordinate(graphCoord);
   }

   /**
    * Get the axis descriptor of a column.
    */
   @Override
   protected AxisDescriptor getAxisDescriptor(String col) {
      // for stock and candle, the axis descriptor for measures is stored in
      // ChartInfo; the axis descriptors for dimensions are stored in
      // XDimensionRef
      ChartRef ref = getChartRef(col, true, null);

      if(ref == null) {
         return new AxisDescriptor();
      }
      else if(GraphUtil.isDimension(ref)) {
         return getAxisDescriptor0(ref);
      }
      else {
         return getAxisDescriptor0();
      }
   }

   /**
    * Create element graph internally.
    */
   @Override
   protected void createEGraph0() {
      if(isValid()) {
         CandleChartInfo sinfo = (CandleChartInfo) info;

         // 1. add fields
         addXDFields(); // add x dimension fields
         addYDFields(); // add y dimension fields
         checkValidity();

         XAggregateRef lref = (XAggregateRef) sinfo.getRTLowField();
         String lname = GraphUtil.getName(lref);
         String llabel = GraphUtil.getCaption(lref);
         XAggregateRef href = (XAggregateRef) sinfo.getRTHighField();
         String hname = GraphUtil.getName(href);
         String hlabel = GraphUtil.getCaption(href);
         XAggregateRef oref = (XAggregateRef) sinfo.getRTOpenField();
         String oname = GraphUtil.getName(oref);
         String olabel = GraphUtil.getCaption(oref);
         XAggregateRef cref = (XAggregateRef) sinfo.getRTCloseField();
         String cname = GraphUtil.getName(cref);
         String clabel = GraphUtil.getCaption(cref);
         String[] flds = Arrays.asList(hname, cname, oname, lname).stream()
            .filter(a -> a != null).toArray(String[]::new);
         // 2. add elements
         createElement(getSchemaType(), flds, null);

         // 3. add coords
         ymeasures.add(hname);
         ymeasures.add(cname);

         if(oname != null) {
            ymeasures.add(oname);
         }

         ymeasures.add(lname);

         ymeasurelabels.put(hname, hlabel);
         ymeasurelabels.put(cname, clabel);
         ymeasurelabels.put(lname, llabel);

         if(oname != null) {
            ymeasurelabels.put(oname, olabel);
         }

         AxisDescriptor axis = getAxisDescriptor(lname);
         Scale yscale = createScale(axis, null, flds, null);

         scales.put(lname, yscale);
         scales.put(hname, yscale);
         scales.put(cname, yscale);

         if(oname != null) {
            scales.put(oname, yscale);
         }

         fixMScale(lname, getSchemaType());
         fixMScale(hname, getSchemaType());
         fixMScale(cname, getSchemaType());

         if(oname != null) {
            fixMScale(oname, getSchemaType());
         }

         Scale xscale = null;

         if(xdims.size() > 0) {
            String xname = xdims.get(xdims.size() - 1);
            ChartRef dref = getChartRef(xname, true, info.getRTXFields());

            if(dref instanceof XDimensionRef) {
               fixDScale(xname, isDate((XDimensionRef) dref), getSchemaType());
            }

            xscale = scales.get(xname);
         }

         // leave gap, then stock bar will not overlap axis
         if(xscale instanceof TimeScale) {
            ((TimeScale) xscale).setFill(false);
         }

         yscale = scales.get(lname);
         Coordinate coord = new RectCoord(xscale, yscale);
         fixCoordProperties(coord, GraphTypes.CHART_CANDLE);
         Coordinate graphCoord = createCoord(coord, getSchemaType());
         graph.setCoordinate(graphCoord);
      }
      else {
         createFakeEGraph();
      }
   }

   /**
    * Get the ChartRef for specified field name.
    */
   @Override
   protected ChartRef getChartRef(String fld, boolean fullname, ChartRef[] refs) {
      ChartRef ref = super.getChartRef(fld, fullname, refs);

      if(ref != null) {
         return ref;
      }

      CandleChartInfo sinfo = (CandleChartInfo) info;
      DataRef lref = sinfo.getRTLowField();
      DataRef href = sinfo.getRTHighField();
      DataRef cref = sinfo.getRTCloseField();
      DataRef oref = sinfo.getRTOpenField();

      if(fullname && GraphUtil.equalsName(lref, fld) ||
         !fullname && lref != null && fld.equals(lref.getName()))
      {
         return (ChartRef) lref;
      }
      else if(fullname && GraphUtil.equalsName(href, fld) ||
              !fullname && href != null && fld.equals(href.getName()))
      {
         return (ChartRef) href;
      }
      else if(fullname && GraphUtil.equalsName(cref, fld) ||
              !fullname && cref != null && fld.equals(cref.getName()))
      {
         return (ChartRef) cref;
      }
      else if(fullname && GraphUtil.equalsName(oref, fld) ||
              !fullname && oref != null && fld.equals(oref.getName()))
      {
         return (ChartRef) oref;
      }

      return null;
   }

   /**
    * Get the data average.
    */
   private double getAverage(String col) {
      double value = 0;

      for(int i = 0; i < data.getRowCount(); i++) {
         Object obj = data.getData(col, i);

         if(!(obj instanceof Number)) {
            continue;
         }

         value += ((Number) obj).doubleValue();
      }

      return value / data.getRowCount();
   }

   /**
    * Create invalidate data.
    */
   private void createFakeData() {
      this.adata = null;

      if(!dimContained && !lowContained && !highContained &&
         !openContained && !closeContained)
      {
         Object[][] data = {{"Fake", "High", "Open", "Close", "Low"},
                           {"Fake", 400, 200, 300, 100}};
         this.data = new DefaultDataSet(data);
      }
      else {
         // remove all created calc values, they are invalid now
         this.data.removeCalcValues();

         List<CalcColumn> calcs = this.data.getCalcColumns();
         ExpandableDataSet edset = new ExpandableDataSet(this.data);

         for(CalcColumn calcColumn : calcs) {
            edset.addCalcColumn(calcColumn);
         }

         if(!dimContained) {
            edset.addDimension("Fake", "Fake");
         }

         CandleChartInfo sinfo = (CandleChartInfo) info;
         XAggregateRef href = (XAggregateRef) sinfo.getRTHighField();
         XAggregateRef oref = (XAggregateRef) sinfo.getRTOpenField();
         XAggregateRef cref = (XAggregateRef) sinfo.getRTCloseField();
         XAggregateRef lref = (XAggregateRef) sinfo.getRTLowField();

         double hvalue = 0;
         double ovalue = 0;
         double cvalue = 0;
         double lvalue = 0;

         if(href != null) {
            hvalue = getAverage(GraphUtil.getName(href));
         }

         if(oref != null) {
            ovalue = getAverage(GraphUtil.getName(oref));
         }

         if(cref != null) {
            cvalue = getAverage(GraphUtil.getName(cref));
         }

         if(lref != null) {
            lvalue = getAverage(GraphUtil.getName(lref));
         }

         if(!dimContained) {
            edset.addDimension("Fake", "Fake");
         }

         if(!highContained && !closeContained &&
            !lowContained && !openContained)
         {
            edset.addMeasure("High", 400d);
            edset.addMeasure("Open", 200d);
            edset.addMeasure("Close", 300d);
            edset.addMeasure("Low", 100d);
         }
         // the value should have the same magnitude with the data
         else {
            if(!highContained) {
               if(openContained) {
                  edset.addMeasure("High", ovalue / OPEN);
               }
               else if(closeContained) {
                  edset.addMeasure("High", cvalue / CLOSE);
               }
               else if(lowContained) {
                  edset.addMeasure("High", lvalue / LOW);
               }
            }

            if(!openContained) {
               if(highContained) {
                  edset.addMeasure("Open", hvalue * OPEN);
               }
               else if(closeContained) {
                  edset.addMeasure("Open", cvalue / CLOSE * OPEN);
               }
               else if(lowContained) {
                  edset.addMeasure("Open", lvalue / LOW * OPEN);
               }
            }

            if(!closeContained) {
               if(highContained) {
                  edset.addMeasure("Close", hvalue * CLOSE);
               }
               else if(openContained) {
                  edset.addMeasure("Close", ovalue / OPEN * CLOSE);
               }
               else if(lowContained) {
                  edset.addMeasure("Close", lvalue / LOW * CLOSE);
               }
            }

            if(!lowContained) {
               if(highContained) {
                  edset.addMeasure("Low", hvalue * LOW);
               }
               else if(openContained) {
                  edset.addMeasure("Low", ovalue / OPEN * LOW);
               }
               else if(closeContained) {
                  edset.addMeasure("Low", cvalue / CLOSE * LOW);
               }
            }
         }

         this.data = edset;
      }
   }

   /**
    * Check if x dimension was consumed.
    * @param chartType the chart style.
    */
   @Override
   protected boolean isXConsumed(int chartType) {
      return xdims.size() > 0;
   }

   /**
    * Check if y dimension was consumed.
    * @param chartType the chart style.
    */
   @Override
   protected boolean isYConsumed(int chartType) {
      return false;
   }

   /**
    * Check if the open field is required.
    */
   protected boolean isOpenRequired() {
      return true;
   }

   /**
    * Get the actual schema chart type.
    */
   protected int getSchemaType() {
      return GraphTypes.CHART_CANDLE;
   }

   /**
    * Create the painter for the schema.
    */
   protected SchemaPainter createSchemaPainter() {
      return new CandlePainter();
   }

   private static final double LOW = 0.4;
   private static final double OPEN = 0.8;
   private static final double CLOSE = 0.6;

   private boolean dimContained = true;
   private boolean lowContained = true;
   private boolean highContained = true;
   private boolean openContained = true;
   private boolean closeContained = true;
}
