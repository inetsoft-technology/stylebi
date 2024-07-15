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
import inetsoft.graph.GraphConstants;
import inetsoft.graph.aesthetic.TextFrame;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.PairsDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.guide.form.LabelForm;
import inetsoft.graph.scale.*;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * SeparateGraphGenerator generates separate element graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class SeparateGraphGenerator extends GraphGenerator {
   /**
    * Constructor.
    * @param chart the specified chart.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    */
   public SeparateGraphGenerator(ChartVSAssemblyInfo chart, DataSet adata, DataSet data,
                                 VariableTable vars,DataSet vdata, int sourceType, Dimension size)
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
   public SeparateGraphGenerator(ChartInfo info, ChartDescriptor desc, DataSet adata, DataSet data,
                                 VariableTable vars, String vsrc, int sourceType, Dimension size)
   {
      super(info, desc, adata, data, vars, vsrc, sourceType, size);
   }

   /**
    * Get the axis descriptor of a column.
    */
   @Override
   protected AxisDescriptor getAxisDescriptor(String col) {
      // for separate chart, the axis descriptors for measures are stored in
      // ChartAggregateRef; the axis descriptors for dimensions are stored in
      // DimensionRef
      ChartRef ref = getChartRef(col, true, null);
      // this logic should match ChartRegionHandler.getAxisDescriptor
      return ref == null ? info.getAxisDescriptor() : getAxisDescriptor0(ref);
   }

   @Override
   protected AxisDescriptor getAxisDescriptor(Scale scale, String axisType) {
      String[] fields = scale.getFields();

      // don't hide measure names
      if(fields.length == 1) {
         switch(fields[0]) {
         case PairsDataSet.XMEASURE_NAME:
         case PairsDataSet.YMEASURE_NAME:
            // currently the measure name axes in scatter plot matrix are
            // mapped to the chart level axis descriptor. we may consider
            // separating them into two axis descriptors for individual control.
            // since they are generated from the set same of measure names,
            // it seems reasonable to share the axis options
            return info.getAxisDescriptor();
         case PairsDataSet.XMEASURE_VALUE:
         case PairsDataSet.YMEASURE_VALUE:
            return new AxisDescriptor();
         }
      }

      return super.getAxisDescriptor(scale, axisType);
   }

   /**
    * Create element graph internally.
    */
   @Override
   protected void createEGraph0() {
      // 1. add fields
      addYDFields(); // add y dimension fields
      addXDFields(); // add x dimension fields
      addYMFields(false); // add y measure fields
      addXMFields(false); // add x measure fields

      checkValidity();

      scatter_matrix = xmeasures.size() > 1 && ymeasures.size() > 1;

      if(scatter_matrix) {
         setupScatterMatrix();
      }

      // 2. add elements
      boolean xmcontained = xmeasures.size() > 0;
      boolean xdcontained = xdims.size() > 0;
      boolean ymcontained = ymeasures.size() > 0;
      boolean ydcontained = ydims.size() > 0;
      boolean treemap = GraphTypes.isTreemap(info.getRTChartType());
      boolean relation = GraphTypes.isRelation(info.getRTChartType());
      boolean gantt = GraphTypes.isGantt(info.getRTChartType());

      if(!xmcontained && !ymcontained && this.data != null && !treemap && !relation && !gantt) {
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

      List<Coordinate> coords = new ArrayList<>();
      boolean vertical = xmeasures.size() <= 1;
      boolean polar = false;

      if(GraphTypes.isMekko(info.getRTChartType())) {
         MekkoCoord coord = new MekkoCoord();
         coords.add(coord);
         String xdim = null;
         String ymeasure = null;

         if(xdims.size() > 0) {
            coord.setDim(xdim = xdims.get(0));
         }

         if(ymeasures.size() > 0) {
            if(adata != null) {
               ymeasure = ymeasures.get(0);
               coord.setVar(BrushDataSet.ALL_HEADER_PREFIX + ymeasure);
            }
            else {
               coord.setVar(ymeasure = ymeasures.get(0));
            }

            fixMScale(ymeasure, info.getRTChartType());
         }

         createElement(info.getRTChartType(), ymeasure, xdim);
         fixCoordProperties(coord, info.getRTChartType());
      }
      // y measure exists?
      else if(ymcontained) {
         polar = GraphTypes.isPolar(GraphTypeUtil.getChartType(ymeasures.get(0), info));

         for(int i = 0; i < ymeasures.size(); i++) {
            String yname = ymeasures.get(i);
            int type = GraphTypeUtil.getChartType(yname, info);
            boolean bar3D = GraphTypes.is3DBar(type);

            fixMScale(yname, type);

            if(polar && xmcontained) {
               throw new MessageException(Catalog.getCatalog().getString(
                  "em.common.graph.XmeaNotAllowed"), LogLevel.WARN, true,
                                          MessageCommand.INFO);
            }

            // polar? x dimension is not required in coord
            if(polar) {
               PolarCoord polarCoord;
               Scale yscale = scales.get(yname);

               createElement(type, yname);
               yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);

               polarCoord = createPolarCoord(type, yscale);
               fixCoordProperties(polarCoord, type);
               coords.add(polarCoord);
            }
            // x measure contained? point chart
            else if(xmcontained) {
               if(ymeasures.size() > 1 && xmeasures.size() > 1) {
                  throw new MessageException(Catalog.getCatalog().getString(
                     "em.common.graph.oneAxisSupported"), LogLevel.WARN, true,
                     MessageCommand.INFO);
               }

               for(int j = 0; j < xmeasures.size(); j++) {
                  String xname = xmeasures.get(j);
                  int ctype = GraphTypeUtil.getChartType(xname, info);
                  Scale xscale = scales.get(xname);
                  Scale yscale = scales.get(yname);
                  Coordinate coord;
                  fixMScale(xname, ctype);
                  xscale = scales.get(xname);
                  createElement(type, yname, xname);

                  if(bar3D) {
                     coord = new Rect25Coord(xscale, yscale);
                  }
                  else {
                     coord = new RectCoord(xscale, yscale);
                  }

                  fixCoordProperties(coord, ctype);
                  coords.add(coord);
               }
            }
            // x dimension not contained?
            else {
               Coordinate coord;
               Scale yscale = scales.get(yname);
               Scale xscale = null;
               createElement(type, yname);

               if(xdcontained) {
                  String field = xdims.get(xdims.size() - 1);
                  ChartRef cref = getChartRef(field, true, info.getRTXFields());

                  if(cref instanceof XDimensionRef) {
                     fixDScale(field, isDate((XDimensionRef) cref), type);
                  }

                  xscale = scales.get(field);
               }
               // place labels in center for word cloud
               else if(info.getTextField() != null && "value".equals(ymeasures.get(0))) {
                  graph.getElement(0).addDim("value");

                  // brushing
                  if(adata != null && graph.getElementCount() > 1) {
                     graph.getElement(1).addDim("value");
                  }

                  xscale = yscale;
               }

               if(bar3D) {
                  coord = new Rect25Coord(xscale, yscale);
               }
               else {
                  coord = new RectCoord(xscale, yscale);
               }

               fixCoordProperties(coord, type);
               coords.add(coord);
            }
         }
      }
      // x measure contained?
      else if(xmcontained) {
         polar = GraphTypes.isPolar(GraphTypeUtil.getChartType(xmeasures.get(0), info));
         boolean bar3D = GraphTypes.is3DBar(GraphTypeUtil.getChartType(xmeasures.get(0), info));

         for(int i = 0; i < xmeasures.size(); i++) {
            String xname = xmeasures.get(i);
            int type = GraphTypeUtil.getChartType(xname, info);

            fixMScale(xname, type);
            createElement(type, xname);

            Coordinate coord;
            Scale xscale = scales.get(xname);
            Scale yscale = null;

            if(polar) {
               xscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
               coord = createPolarCoord(type, xscale);
            }
            else {
               if(ydcontained) {
                  String field = ydims.get(ydims.size() - 1);
                  ChartRef cref = getChartRef(field, true, info.getRTYFields());

                  if(cref instanceof XDimensionRef) {
                     fixDScale(field, isDate((XDimensionRef) cref), type);
                  }

                  yscale = scales.get(field);
               }

               if(bar3D) {
                  coord = new Rect25Coord(yscale, xscale);
               }
               else {
                  coord = new RectCoord(yscale, xscale);
               }

               coord.transpose();
               rotatedCoords.add(coord);
            }

            fixCoordProperties(coord, type);
            coords.add(coord);
         }
      }
      else if(treemap) {
         TreemapCoord coord = new TreemapCoord();

         if(GraphTypes.isPolar(info.getRTChartType())) {
            coord.setLayout(TreemapCoord.Layout.SQUARE);
         }
         else if(info.getRTChartType() == GraphTypes.CHART_CIRCLE_PACKING) {
            switch(SreeEnv.getProperty("graph.circle.packing", "circle").toLowerCase()) {
            case "circle":
               coord.setLayout(TreemapCoord.Layout.SQUARE);
               break;
            case "fill_x":
               coord.setLayout(TreemapCoord.Layout.FILL_X);
               break;
            case "fill":
               coord.setLayout(TreemapCoord.Layout.FILL);
               break;
            }
         }

         coords.add(coord);

         // add a swap gap to avoid circles touching facet grid line.
         switch(info.getRTChartType()) {
         case GraphTypes.CHART_CIRCLE_PACKING:
         case GraphTypes.CHART_SUNBURST:
            coord.setPadding(4);
            break;
         default:
            // 1 pixel padding to allow facet grid line. (57993)
            coord.setPadding(info.isFacet() ? 1 : 0);
         }

         String[] dims = Arrays.stream(info.getRTGroupFields())
               .map(f -> f.getFullName())
               .filter(f -> data.indexOfHeader(f) >= 0)
               .toArray(String[]::new);

         createElement(info.getRTChartType(), dims, null);
      }
      else if(relation) {
         RelationCoord coord = new RelationCoord();
         RelationChartInfo info2 = (RelationChartInfo) info;
         coords.add(coord);

         if(info2.getRTSourceField() != null && info2.getRTTargetField() != null) {
            String[] dims = { info2.getRTSourceField().getFullName(),
                              info2.getRTTargetField().getFullName() };
            createElement(info.getRTChartType(), dims, null);
         }
      }
      // neither x measure nor y measure
      else {
         // neither coord nor element is required
      }

      if(coords.size() > 0) {
         Coordinate[] coordinates = new Coordinate[coords.size()];
         coords.toArray(coordinates);
         int chartType = polar ? GraphTypes.CHART_PIE : -1;
         Coordinate graphCoord = createCoord(coordinates, chartType, vertical);
         graph.setCoordinate(graphCoord);
      }
   }

   // transform dataset and setup dim/measure for scatter plot matrix
   private void setupScatterMatrix() {
      String[] xmeasures = this.xmeasures.toArray(new String[0]);
      String[] ymeasures = this.ymeasures.toArray(new String[0]);

      if(this.xmeasures.equals(this.ymeasures)) {
         TextFrame texts = info.getAxisDescriptor().getTextFrame();

         for(int i = 0; i < xmeasures.length; i++) {
            PlotDescriptor plotdesc = desc.getPlotDescriptor();
            String str = (String) texts.getText(xmeasures[i]);

            if(str == null) {
               str = xmeasures[i];
            }

            LabelForm label = new LabelForm(str, new Object[] {
                  xmeasures[i], xmeasures[i], Scale.MID_VALUE, Scale.MID_VALUE});
            label.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
            label.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
            label.setMeasure(xmeasures[i]);
            label.setHint(GraphElement.HINT_CLIP, "true");
            label.setLine(GraphConstants.NONE);

            if(plotdesc.getTextFormat().getFont() != null) {
               label.getTextSpec().setFont(plotdesc.getTextFormat().getFont());
            }

            if(plotdesc.getTextFormat().getColor() != null) {
               label.getTextSpec().setColor(plotdesc.getTextFormat().getColor());
            }

            graph.addForm(label);
         }
      }

      graph.setScatterMatrix(true);
      fixScatterMatrixConditions();

      this.xmeasures.clear();
      this.ymeasures.clear();
      this.xmeasures.add(PairsDataSet.XMEASURE_VALUE);
      this.ymeasures.add(PairsDataSet.YMEASURE_VALUE);

      xdims.add(PairsDataSet.XMEASURE_NAME);
      ydims.add(PairsDataSet.YMEASURE_NAME);

      scales.put(PairsDataSet.XMEASURE_NAME,
                 new CategoricalScale(PairsDataSet.XMEASURE_NAME));
      scales.put(PairsDataSet.YMEASURE_NAME,
                 new CategoricalScale(PairsDataSet.YMEASURE_NAME));
      scales.put(PairsDataSet.XMEASURE_VALUE,
                 new LinearScale(PairsDataSet.XMEASURE_VALUE));
      scales.put(PairsDataSet.YMEASURE_VALUE,
                 new LinearScale(PairsDataSet.YMEASURE_VALUE));

      // create scale for measures
      for(String measure : xmeasures) {
         graph.setScale(measure + ".x", createScatterMatrixScale(measure, true));
      }

      for(String measure : ymeasures) {
         graph.setScale(measure + ".y", createScatterMatrixScale(measure, false));
      }

      if(this.data instanceof BrushDataSet) {
         BrushDataSet bdata = (BrushDataSet) this.data;
         DataSet odata = bdata.getDataSet(false);
         DataSet adata = bdata.getDataSet(true);

         odata = new PairsDataSet(odata, xmeasures, ymeasures);
         adata = new PairsDataSet(adata, xmeasures, ymeasures);

         this.adata = adata;
         this.odata = odata;
         prepareForBrushDataSet(odata, adata);
         this.data = new BrushDataSet(adata, odata);
      }
      else {
         PairsDataSet ndata = new PairsDataSet(this.data, xmeasures, ymeasures);
         this.odata = ndata;
         this.data = ndata;
      }

      if(bconds != null) {
         applyBrushing();
      }
   }

   private Scale createScatterMatrixScale(String measure, boolean x) {
      LinearScale scale = x ? new LinearScale(PairsDataSet.XMEASURE_VALUE)
         : new LinearScale(PairsDataSet.YMEASURE_VALUE);
      AxisDescriptor xdesc = getAxisDescriptor(measure);
      PlotDescriptor plotdesc = desc.getPlotDescriptor();
      AxisSpec axis = createAxisSpec(xdesc, plotdesc, new String[] {measure}, scale, false, true,x);
      scale.setAxisSpec(axis);

      // per Steve Few recommendation, only start at 0 for bar and bar-like graphs
      if(isGraphType(GraphTypes.CHART_LINE, GraphTypes.CHART_STEP, GraphTypes.CHART_JUMP)) {
         scale.setScaleOption(Scale.TICKS);
      }
      else if(isGraphType(GraphTypes.CHART_POINT, GraphTypes.CHART_STOCK, GraphTypes.CHART_CANDLE))
      {
         scale.setScaleOption(Scale.TICKS | Scale.GAPS);
      }

      applyAxisDescriptor(scale, xdesc);

      return scale;
   }

   // map brushing condition to base table
   private void fixScatterMatrixConditions() {
      if(bconds == null) {
         return;
      }

      Set<String> measures = new HashSet<>(xmeasures);
      measures.addAll(ymeasures);

      for(int i = 0; i < bconds.getSize(); i++) {
         ConditionItem cond = bconds.getConditionItem(i);

         if(cond != null) {
            DataRef ref = cond.getAttribute();

            if(measures.contains(ref.getName())) {
               DataRef xname = new AttributeRef(PairsDataSet.XMEASURE_NAME);
               DataRef yname = new AttributeRef(PairsDataSet.YMEASURE_NAME);
               DataRef xvalue = new AttributeRef(PairsDataSet.XMEASURE_VALUE);
               DataRef yvalue = new AttributeRef(PairsDataSet.YMEASURE_VALUE);
               bconds.remove(i);

               Condition namecond = new Condition();
               namecond.setOperation(Condition.EQUAL_TO);
               namecond.setType(XSchema.STRING);
               namecond.addValue(ref.getName());

               bconds.insert(i++, new ConditionItem(xname, namecond,
                                                    cond.getLevel() + 1));
               bconds.insert(i++, new JunctionOperator(JunctionOperator.AND,
                                                       cond.getLevel() + 1));
               bconds.insert(i++, new ConditionItem(xvalue, cond.getCondition(),
                                                    cond.getLevel() + 1));
               bconds.insert(i++, new JunctionOperator(JunctionOperator.OR,
                                                       cond.getLevel() + 1));
               bconds.insert(i++, new ConditionItem(yname, namecond,
                                                    cond.getLevel() + 1));
               bconds.insert(i++, new JunctionOperator(JunctionOperator.AND,
                                                       cond.getLevel() + 1));
               bconds.insert(i++, new ConditionItem(yvalue, cond.getCondition(),
                                                    cond.getLevel() + 1));
            }
         }
      }
   }

   // hide legend if not necessary
   @Override
   protected void fixGraphProperties() {
      super.fixGraphProperties();

      if(scatter_matrix && info.getRTColorField() == null) {
         for(int i = 0; i < graph.getElementCount(); i++) {
            GraphElement elem = graph.getElement(i);

            // hide default legend showing different measures as color
            if(elem.getColorFrame() != null) {
               elem.getColorFrame().getLegendSpec().setVisible(false);
            }
         }
      }
   }

   private boolean scatter_matrix = false;
}
