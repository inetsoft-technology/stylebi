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
import inetsoft.graph.scale.Scale;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.uql.VariableTable;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;

import java.awt.*;
import java.text.Format;

/**
 * RadarGraphGenerator generates radar element graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class RadarGraphGenerator extends MergedGraphGenerator {
   /**
    * Constructor.
    * @param chart the specified chart.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    */
   public RadarGraphGenerator(ChartVSAssemblyInfo chart, DataSet adata, DataSet data,
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
   public RadarGraphGenerator(ChartInfo info, ChartDescriptor desc, DataSet adata, DataSet data,
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
      Catalog catalog = Catalog.getCatalog();
      ChartRef[] groups = info.getRTGroupFields();

      if(!(ymeasures.size() >= 2 ||
         ymeasures.size() == 1 && groups.length == 1 && groups[0] instanceof XDimensionRef))
      {
         throw new MessageException(catalog.getString("viewer.viewsheet.alert.radar"),
            LogLevel.INFO, false, MessageCommand.INFO);
      }

      // never enter this condition
      /*
      ChartRef refs[] = info.getRTYFields();

      if(refs.length == 0 ||
         !(refs[refs.length - 1] instanceof XAggregateRef))
      {
         throw new MessageException(Catalog.getCatalog().getString(
            "em.common.graph.measureRequired"), Level.SEVERE, true,
            MessageCommand.INFO);
      }
      */

      // do not support invert
      if(xmeasures.size() > 0) {
         throw new MessageException(catalog.getString(
            "em.common.graph.radarNotInverted"), LogLevel.WARN, false,
            MessageCommand.INFO);
      }

      return true;
   }

   /**
    * Create element graph internally.
    */
   @Override
   protected void createEGraph0() {
      // 1. add fields
      addXDFields(); // add x dimension fields
      addYDFields(); // add y dimension fields
      addYMFields(false); // add y measure fields not as a combined one
      checkValidity();

      int ctype = getChartType();
      AbstractParallelCoord coord;

      if(ymeasures.size() == 1 && info.getRTGroupFields().length == 1) {
         String var = ymeasures.get(0);
         String dim = info.getRTGroupFields()[0].getFullName();

         createElement(ctype, new String[] { var }, null);
         coord = new OneVarParallelCoord(var, dim, scales.get(var));
      }
      else {
         String[] flds = new String[ymeasures.size()];
         Scale[] innerscales = new Scale[flds.length];

         for(int i = 0; i < flds.length; i++) {
            flds[i] = ymeasures.get(i);
            fixMScale(flds[i], GraphTypes.CHART_RADAR);
            innerscales[i] = scales.get(flds[i]);
         }

         // 2. add elements
         createElement(ctype, flds, null);

         // 3. add coords
         coord = new ParallelCoord(innerscales);
      }

      fixParallelCoord(coord);

      // init after ScaleOption is set
      if(coord instanceof OneVarParallelCoord) {
         coord.init(data);
      }

      fixCoordProperties(coord, GraphTypes.CHART_RADAR);

      PolarCoord polarCoord = new PolarCoord(coord);
      Coordinate graphCoord = createCoord(polarCoord, ctype);

      graph.setCoordinate(graphCoord);
   }

   /**
    * Set coordinate properties.
    */
   private void fixParallelCoord(AbstractParallelCoord coord) {
      AxisDescriptor xdesc = null;
      AxisSpec spec = new AxisSpec();
      final Scale scale = coord.getAxisLabelScale();
      String[] flds = scale == null ? null : scale.getFields();
      Format fmt = getDefaultFormat(flds);
      CompositeTextFormat format = null;

      if(GraphTypes.isRadarN(info)) {
         xdesc = ((RadarChartInfo) info).getLabelAxisDescriptor();
         format = xdesc.getColumnLabelTextFormat("_Parallel_Label_");
      }
      else {
         ChartRef[] grefs = info.getRTGroupFields();

         if(grefs.length > 0) {
            xdesc = grefs[0].getAxisDescriptor();
         }

         if(flds != null) {
            ChartRef dim = info.getFieldByName(flds[0], true);

            if(dim != null) {
               format = dim.getAxisDescriptor().getColumnLabelTextFormat(flds[0]);
            }

            addHighlightToAxis(spec, flds);
         }
      }

      if(format == null) {
         format = xdesc.getAxisLabelTextFormat();
      }

      spec.setLineColor(xdesc.getLineColor());
      spec.setLineVisible(!maxMode && xdesc.isLineVisible() || maxMode && xdesc.isMaxModeLineVisible());
      spec.setTickVisible(xdesc.isTicksVisible());
      spec.setTextSpec(GraphUtil.getTextSpec(format, fmt, null));
      spec.setLabelVisible(!maxMode && xdesc.isLabelVisible() || maxMode && xdesc.isMaxModeLabelVisible());
      spec.setTextFrame(xdesc.getTextFrame());
      spec.setTruncate(xdesc.isTruncate());

      if(xdesc.isNoNull()) {
         scale.setScaleOption(scale.getScaleOption() | Scale.NO_NULL);
      }

      scale.setAxisSpec(spec);
   }

   /**
    * Get the axis descriptor of a column.
    */
   @Override
   protected AxisDescriptor getAxisDescriptor(String col) {
      // for radar, the axis descriptors for measures are stored in
      // ChartAggregateRef; the axis descriptors for dimensions are stored in
      // XDimensionRef
      ChartRef ref = getChartRef(col, true, null);
      return ref == null ? new AxisDescriptor() : getAxisDescriptor0(ref);
   }

   /**
    * Check if x dimension was consumed.
    * @param chartType the chart style.
    */
   @Override
   protected boolean isXConsumed(int chartType) {
      return false;
   }

   /**
    * Check if y dimension was consumed.
    * @param chartType the chart style.
    */
   @Override
   protected boolean isYConsumed(int chartType) {
      return false;
   }
}
