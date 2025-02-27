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

import inetsoft.graph.AxisSpec;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.scale.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DefaultGraphGenerator generates a default element graph. It is not separated.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class DefaultGraphGenerator extends GraphGenerator {
   /**
    * Constructor.
    * @param chart the specified chart.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    */
   public DefaultGraphGenerator(ChartVSAssemblyInfo chart, DataSet adata, DataSet data,
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
   public DefaultGraphGenerator(ChartInfo info, ChartDescriptor desc, DataSet adata, DataSet data,
                                VariableTable vars, String vsrc, int sourceType, Dimension size)
   {
      super(info, desc, adata, data, vars, vsrc, sourceType, size);
   }

   /**
    * Get the axis descriptor of a column.
    */
   @Override
   protected AxisDescriptor getAxisDescriptor(String col) {
      // for non-separate chart, the axis descriptor for measures is stored
      // in ChartInfo; the axis descriptors for dimensions (may be a measure)
      // are stored in XDimensionRef/ChartAggregateRef
      ChartRef ref = getChartRef(col, true, null);

      if(ref == null) {
         return new AxisDescriptor();
      }
      else if(GraphUtil.isDimension(ref)) {
         return getAxisDescriptor0(ref);
      }

      List measures = ymeasures.size() > 0 ? ymeasures : xmeasures;
      boolean measure = measures.contains(col);
      return measure ? getAxisDescriptor0() : getAxisDescriptor0(ref);
   }

   /**
    * Create element graph internally.
    */
   @Override
   protected void createEGraph0() {
      synchronized(info) {
         // 1. add fields
         addYDFields(); // add y dimension fields
         addXDFields(); // add x dimension fields
         addYMFields(true); // add y measure fields
         addXMFields(true); // add x measure fields
         checkValidity();

         // 2. add elements
         boolean xmcontained = xmeasures.size() > 0;
         boolean xdcontained = xdims.size() > 0;
         boolean ymcontained = ymeasures.size() > 0;
         boolean ydcontained = ydims.size() > 0;

         // 3. add fake data
         if(!xmcontained && !ymcontained && this.data != null) {
            Integer fakeVal = getFakeVal(xdcontained, ydcontained);

            if(data instanceof BrushDataSet) {
               BrushDataSet bdata = (BrushDataSet) this.data;
               DataSet odata = bdata.getDataSet(false);
               DataSet adata = bdata.getDataSet(true);

               ExpandableDataSet eset = new ExpandableDataSet(odata);
               eset.addMeasure("value", fakeVal);
               odata = eset;

               eset = new ExpandableDataSet(adata);
               eset.addMeasure("value", fakeVal);
               adata = eset;

               this.adata = adata;
               this.odata = odata;
               prepareForBrushDataSet(odata, adata);
               this.data = new BrushDataSet(adata, odata);
            }
            else {
               ExpandableDataSet eset = new ExpandableDataSet(this.data);
               eset.addMeasure("value", fakeVal);
               this.odata = eset;
               this.data = eset;
            }

            if(isInvertedFake(data)) {
               xmeasures.add("value");
               xmcontained = true;
            }
            else {
               ymeasures.add("value");
               ymcontained = true;
            }

            scales.put("value", new LinearScale("value"));
            fake = true;
         }

         if(ymeasures.size() > 0) {
            String measure = ymeasures.get(0);
            int ctype = GraphTypeUtil.getChartType(measure, info);

            if(GraphTypes.is3DBar(ctype) && xmeasures.size() == 0 &&
               xdims.size() == 0)
            {
               this.data = createFakeDimension(this.data);
               xdims.add("Dimension");
               xdcontained = true;
            }
         }
         else if(xmeasures.size() > 0) {
            String measure = xmeasures.get(0);
            int ctype = GraphTypeUtil.getChartType(measure, info);

            if(GraphTypes.is3DBar(ctype) && ymeasures.size() == 0 &&
               ydims.size() == 0)
            {
               this.data = createFakeDimension(this.data);
               ydims.add("Dimension");
               ydcontained = true;
            }
         }

         Coordinate coord = null;

         // y measure exists?
         if(ymcontained) {
            String name0 = ymeasures.get(0);
            int type0 = GraphTypeUtil.getChartType(name0, info);
            boolean polar = GraphTypes.isPolar(type0);
            boolean bar3D = GraphTypes.is3DBar(type0);
            int[] typeall = new int[ymeasures.size()];

            if(polar && xmcontained) {
               throw new MessageException(Catalog.getCatalog().getString(
                  "em.common.graph.XmeaNotAllowed"), LogLevel.WARN, true,
                                          ConfirmException.INFO);
            }

            boolean stackMeasure = GraphTypeUtil.isStackMeasures(info, desc);

            if(stackMeasure && ymeasures.size() > 1) {
               int type = GraphTypeUtil.getChartType(ymeasures.get(0), info);
               typeall = new int[] { type };

               for(int k = 0; k < ymeasures.size(); k++) {
                  fixMScale(ymeasures.get(k), type);
               }

               createElement(type, ymeasures.toArray(new String[0]), null);
            }
            // if multiple measures are put together in a pie, should stack all variables
            // instead of starting at the same base
            else if(!info.isMultiStyles() && GraphTypes.isPie(getChartType())) {
               int type = GraphTypeUtil.getChartType(ymeasures.get(0), info);

               for(int k = 0; k < ymeasures.size(); k++) {
                  typeall[k] = type;
                  fixMScale(ymeasures.get(k), type);
               }

               createElement(type, ymeasures.toArray(new String[0]), null);
            }
            else {
               for(int i = 0; i < ymeasures.size(); i++) {
                  String name = ymeasures.get(i);
                  int type = GraphTypeUtil.getChartType(name, info);

                  typeall[i] = type;
                  fixMScale(name, type);
                  createElement(type, name);
               }
            }

            Scale xscale = null;
            Scale yscale = scales.get(name0);

            // polar? x dimension is not required in coord
            if(polar) {
               // do nothing
            }
            // x measure contained? point chart
            else if(xmcontained) {
               if(!GraphTypes.isPoint(type0) && !GraphTypes.isLine(type0)) {
                  throw new MessageException(Catalog.getCatalog().getString(
                     "em.common.graph.onePointSupport", "" + type0),
                     LogLevel.WARN, true, ConfirmException.INFO);
               }

               String measure = xmeasures.get(0);
               fixMScale(measure, type0);
               xscale = scales.get(xmeasures.get(0));
            }
            // xdimension contained?
            else if(xdcontained) {
               String field = xdims.get(xdims.size() - 1);
               ChartRef cref = getChartRef(field, true, info.getRTXFields());

               if(cref instanceof XDimensionRef) {
                  fixDScale(field, isDate((XDimensionRef) cref), typeall);
               }

               xscale = scales.get(field);
            }
            // place labels in center for word cloud
            else if(!polar && !bar3D && info.getTextField() != null &&
               ymeasures.get(0).equals("value"))
            {
               graph.getElement(0).addDim("value");
               xscale = yscale;
            }

            if(!polar) {
               if(bar3D) {
                  coord = new Rect25Coord(xscale, yscale);
               }
               else {
                  coord = new RectCoord(xscale, yscale);
               }
            }
            else {
               yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
               coord = createPolarCoord(type0, yscale);
            }
         }
         // x measure contained?
         else if(xmcontained) {
            String name0 = xmeasures.get(0);
            int type0 = GraphTypeUtil.getChartType(name0, info);
            boolean polar = GraphTypes.isPolar(type0);
            boolean bar3D = GraphTypes.is3DBar(type0);
            int[] typeall = new int[xmeasures.size()];
            boolean stackMeasure = GraphTypeUtil.isStackMeasures(info, desc);

            if(stackMeasure && xmeasures.size() > 1) {
               int type = GraphTypeUtil.getChartType(xmeasures.get(0), info);
               typeall = new int[] { type };
               fixMScale(xmeasures.get(0), type);
               createElement(type, xmeasures.toArray(new String[0]), null);
            }
            else {
               for(int i = 0; i < xmeasures.size(); i++) {
                  String name = xmeasures.get(i);
                  int type = GraphTypeUtil.getChartType(name, info);

                  typeall[i] = type;
                  fixMScale(name, type);
                  createElement(type, name);
               }
            }

            Scale xscale = scales.get(name0);
            Scale yscale = null;

            if(polar) {
               yscale = scales.get(name0);
            }
            // y dimension exists?
            else if(ydcontained) {
               String field = ydims.get(ydims.size() - 1);
               ChartRef cref = getChartRef(field, true, info.getRTYFields());

               if(cref instanceof XDimensionRef) {
                  fixDScale(field, isDate((XDimensionRef) cref), typeall);
               }

               yscale = scales.get(field);
            }

            if(!polar) {
               if(bar3D) {
                  coord = new Rect25Coord(yscale, xscale);
               }
               else {
                  coord = new RectCoord(yscale, xscale);
               }

               coord.transpose();
               rotatedCoords.add(coord);
            }
            else {
               yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
               coord = createPolarCoord(type0, yscale);
            }
         }
         // neither x measure nor y measure
         else {
            // neither coord nor element is required
         }

         if(coord != null) {
            int chartType = getFirstChartType();
            fixCoordProperties(coord, chartType);
            Coordinate graphCoord = createCoord(coord, chartType);
            graph.setCoordinate(graphCoord);
         }
      }
   }

   /**
    * Fix the scales properties of the specified coord,
    * set the attribute from desc and info.
    */
   @Override
   protected void fixCoordProperties(Coordinate coord, int chartType) {
      super.fixCoordProperties(coord, chartType);

      if(!(coord instanceof RectCoord) || coord instanceof Rect25Coord) {
         return;
      }

      Scale yscale = ((RectCoord) coord).getYScale();
      // primary y axis fields
      List<String> yfields = new ArrayList<>();
      // secondary y axis fields
      List<String> y2fields = new ArrayList<>();
      List<ChartAggregateRef> aggs = new ArrayList<>();

      if(yscale != null) {
         String[] fields = yscale.getFields();

         for(String field : fields) {
            String field0 = GraphUtil.getOriginalCol(field);
            VSDataRef ref = info.getRTFieldByFullName(field0);

            if(ref == null && field.startsWith(SparklinePoint.PREFIX)) {
               ref = info.getRTFieldByFullName(
                  field.substring(SparklinePoint.PREFIX.length()));
            }

            if(!(ref instanceof ChartAggregateRef)) {
               yfields.add(field);
               break;
            }

            ChartAggregateRef aref = (ChartAggregateRef) ref;
            int type = info.isMultiStyles() ? aref.getRTChartType() :
                                              info.getRTChartType();

            if(GraphTypes.supportsSecondaryAxis(type) && aref.isSecondaryY()) {
               y2fields.add(field);
               aggs.add(aref);
            }
            else {
               yfields.add(field);
            }
         }
      }

      if(y2fields.size() == 0) {
         return;
      }

      List<String> brush2fields = new ArrayList<>();

      // add brush fields
      for(String y2field : y2fields) {
         String allfield = BrushDataSet.ALL_HEADER_PREFIX + y2field;

         if(data.indexOfHeader(allfield) >= 0) {
            brush2fields.add(allfield);
         }
      }

      List<String> all2fields = new ArrayList<>(y2fields);
      all2fields.addAll(brush2fields);

      AxisDescriptor desc = getAxisDescriptor0();
      AxisDescriptor desc2 = getAxisDescriptor2();
      Scale scale2 = createScale(desc2, data, y2fields.toArray(new String[0]),
                         aggs.toArray(new ChartAggregateRef[0]));
      ScaleRange range = (yscale instanceof LinearScale)
         ? ((LinearScale) yscale).getScaleRange() : null;

      yscale.setFields(yfields.toArray(new String[0]));
      yscale.setDataFields(yfields.toArray(new String[0]));
      scale2.setFields(all2fields.toArray(new String[0]));
      scale2.setDataFields(all2fields.toArray(new String[0]));

      // might be a brush range
      if(range instanceof BrushRange) {
         range = ((BrushRange) range).getScaleRange();
      }

      if(range instanceof StackRange && yscale instanceof LinearScale) {
         StackRange range1 = new StackRange();

         for(String yfield : yfields) {
            range1.addStackFields(yfield);
         }

         range1.setGroupField(((StackRange) range).getGroupField());
         ((LinearScale) yscale).setScaleRange(range1);
      }

      if(range instanceof StackRange && scale2 instanceof LinearScale) {
         StackRange range2 = new StackRange();

         for(String y2field : all2fields) {
            range2.addStackFields(y2field);
         }

         range2.setGroupField(((StackRange) range).getGroupField());
         ((LinearScale) scale2).setScaleRange(range2);
      }

      setupYScale(scale2, desc2, (RectCoord) coord, true);
      ((RectCoord) coord).setYScale2(scale2);

      // no binding on the primary axis, hide labels (which is meaningless)
      // and share the scale with 2nd y (bug1335514813120)
      if(yfields.size() == 0) {
         yscale = scale2.clone();
         yscale.setAxisSpec((AxisSpec) yscale.getAxisSpec().clone());
         yscale.getAxisSpec().setLabelVisible(false);
         ((RectCoord) coord).setYScale(yscale);
      }

      // setup default color encoding to show axis labels in the
      // same color as the element
      if(graph.getElementCount() >= 2 && yfields.size() == 1 && y2fields.size() == 1) {
         GraphElement elem = getElement(yfields.get(0));
         TextFormat fmt = desc.getAxisLabelTextFormat().getUserDefinedFormat();

         if(elem != null && yfields.size() == 1 && fmt.getColor() == null) {
            Color clr = elem.getColorFrame().getColor(yfields.get(0));

            if(clr != null) {
               // text is thin so making it darker to make it more readable
               yscale.getAxisSpec().getTextSpec().setColor(clr.darker());
            }
         }

         elem = getElement(y2fields.get(0));
         fmt = desc2.getAxisLabelTextFormat().getUserDefinedFormat();

         if(elem != null && y2fields.size() == 1 && fmt.getColor() == null) {
            Color clr = elem.getColorFrame().getColor(y2fields.get(0));

            if(clr != null) {
               scale2.getAxisSpec().getTextSpec().setColor(clr.darker());
            }
         }
      }
   }

   /**
    * Get the axis descriptor of a chart info.
    */
   protected AxisDescriptor getAxisDescriptor2() {
      AxisDescriptor rdesc = null;

      if(info instanceof VSChartInfo) {
         rdesc = ((VSChartInfo) info).getRTAxisDescriptor2();
      }

      return rdesc == null ? info.getAxisDescriptor2() : rdesc;
   }

   /**
    * Find element by var name.
    */
   private GraphElement getElement(String var) {
      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if(elem.getVarCount() == 1 && elem.getVar(0).equals(var)) {
            return elem;
         }
      }

      return null;
   }

   private static final Logger LOG = LoggerFactory.getLogger(DefaultGraphGenerator.class);
}
