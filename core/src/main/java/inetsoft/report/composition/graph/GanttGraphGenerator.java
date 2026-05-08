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

import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.scale.TimeScale;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.*;

import java.awt.*;
import java.text.Format;
import java.util.List;
import java.util.*;

/**
 * GanttGraphGenerator generates gantt element graph.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class GanttGraphGenerator extends MergedGraphGenerator {
   /**
    * Constructor.
    * @param chart the specified chart.
    * @param adata the data generated without brushing, null if no brushing.
    * @param data the data generated with brushing.
    * @param vars the specified variable table.
    */
   public GanttGraphGenerator(ChartVSAssemblyInfo chart, DataSet adata, DataSet data,
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
   public GanttGraphGenerator(ChartInfo info, ChartDescriptor desc, DataSet adata, DataSet data,
                              VariableTable vars, String vsrc, int sourceType, Dimension size)
   {
      super(info, desc, adata, data, vars, vsrc, sourceType, size);
   }

   /**
    * Create element graph internally.
    */
   @Override
   protected void createEGraph0() {
      // 1. add fields
      addXDFields(); // add x dimension fields
      addYDFields(); // add y dimension fields
      addGanttFields();
      checkValidity();

      // 2. add elements
      createElement(GraphTypes.CHART_GANTT, ganttFields.toArray(new String[0]), null);

      // 3. add coords
      Scale xscale = ydims.size() > 0 ? scales.get(ydims.get(ydims.size() - 1)) : null;
      String[] ganttFields = getGanttFields();
      Scale yscale = ganttFields.length > 0 ? scales.get(ganttFields[0]) : null;
      RectCoord coord = new RectCoord(xscale, yscale);
      coord.transpose();

      fixCoordProperties(coord, GraphTypes.CHART_GANTT);
      fixGanttCoord(coord);

      Coordinate graphCoord = createCoord(coord, GraphTypes.CHART_GANTT);
      graph.setCoordinate(graphCoord);
   }

   /**
    * Add all the y measures.
    */
   private void addGanttFields() {
      GanttChartInfo ginfo = (GanttChartInfo) info;
      ChartRef[] chartRefs = { ginfo.getRTStartField(), ginfo.getRTEndField(),
                             ginfo.getRTMilestoneField() };

      for(ChartRef ref : chartRefs) {
         if(ref == null) {
            ganttFields.add(null);
            continue;
         }

         String name = GraphUtil.getName(ref);
         int index2 = data.indexOfHeader(name);

         if(index2 < 0) {
            throw new MessageException(
               Catalog.getCatalog().getString("common.viewsheet.aggrInvalid", name));
         }

         ganttFields.add(name);
      }

      String[] ganttFields = getGanttFields();

      if(ganttFields.length > 0) {
         TimeScale scale = new TimeScale(ganttFields);
         scale.setScaleOption(scale.getScaleOption() | Scale.NO_NULL);
         scale.getAxisSpec().setAbbreviate(true);
         scale.setFill(true);
         scales.put(ganttFields[0], scale);
         fixMScale(ganttFields[0], info.getChartType());
      }
   }

   /**
    * Set coordinate properties.
    */
   private void fixGanttCoord(RectCoord coord) {
      // fixCoordProperties() replaced the AxisSpec on the timescale, so reapply
      // labelBetween here so month labels appear centred between tick boundaries.
      Scale yScale = coord.getYScale();

      if(yScale != null) {
         yScale.getAxisSpec().setLabelBetween(true);
      }

      Scale xScale = coord.getXScale();
      if(xScale != null) {
         xScale.getAxisSpec().setGridBetween(true);
      }
   }

   @Override
   protected boolean isXConsumed(int chartType) {
      return false;
   }

   @Override
   protected boolean isYConsumed(int chartType) {
      return true;
   }

   @Override
   protected AxisDescriptor getAxisDescriptor(String col) {
      ChartRef ref = getChartRef(col, true, null);
      return ref == null ? new AxisDescriptor() : getAxisDescriptor0(ref);
   }

   @Override
   protected ChartRef getChartRef(String fld, boolean fullname, ChartRef[] refs) {
      ChartRef ref = super.getChartRef(fld, fullname, refs);

      if(ref == null) {
         GanttChartInfo ginfo = (GanttChartInfo) info;
         ChartRef[] grefs = { ginfo.getRTStartField(), ginfo.getRTEndField(),
                             ginfo.getRTMilestoneField() };

         for(ChartRef gref : grefs) {
            if(gref != null && (fullname && GraphUtil.equalsName(gref, fld) ||
               !fullname && fld.equals(gref.getName())))
            {
               return gref;
            }
         }
      }

      return ref;
   }

   @Override
   protected Format getDefaultFormat(String[] cols) {
      GanttChartInfo ginfo = (GanttChartInfo) info;

      if(cols.length > 0 && ginfo.getRTStartField() != null &&
         Objects.equals(cols[0], ginfo.getRTStartField().getFullName()))
      {
         return XUtil.getDefaultDateFormat(DateRangeRef.DAY_DATE_GROUP);
      }

      return super.getDefaultFormat(cols);
   }

   // get non-null gantt fields.
   private String[] getGanttFields() {
      return ganttFields.stream().filter(Objects::nonNull).toArray(String[]::new);
   }

   private final List<String> ganttFields = new ArrayList<>();
}
