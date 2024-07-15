/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.graph;

import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.scale.TimeScale;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCube;
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
         String label = XCube.SQLSERVER.equals(cubeType) ? name : GraphUtil.getCaption(ref);
         label = Tool.localize(label);

         int index2 = data.indexOfHeader(name);

         if(index2 < 0) {
            throw new MessageException(
               Catalog.getCatalog().getString("common.viewsheet.aggrInvalid", name));
         }

         ganttFields.add(name);
      }

      String[] ganttFields = getGanttFields();

      if(ganttFields.length > 0) {
         Scale scale = new TimeScale(ganttFields);
         scale.setScaleOption(scale.getScaleOption() | Scale.NO_NULL);
         scale.getAxisSpec().setAbbreviate(true);
         scales.put(ganttFields[0], scale);
         fixMScale(ganttFields[0], info.getChartType());
      }
   }

   /**
    * Set coordinate properties.
    */
   private void fixGanttCoord(RectCoord coord) {
      /*
      AxisDescriptor xdesc = null;
      AxisSpec spec = new AxisSpec();
      Scale scale = coord.getAxisLabelScale();
      String[] flds = scale == null ? null : scale.getFields();
      Format fmt = getDefaultFormat(flds);
      CompositeTextFormat format = null;

      if(GraphTypes.isGanttN(info)) {
         xdesc = ((GanttChartInfo) info).getLabelAxisDescriptor();
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
      spec.setLineVisible(!maxMode && xdesc.isLineVisible() || maxMode && xdesc.isMaxModeVisible());
      spec.setTickVisible(xdesc.isTicksVisible());
      spec.setTextSpec(GraphUtil.getTextSpec(format, fmt, null));
      spec.setLabelVisible(!maxMode && xdesc.isLabelVisible() || maxMode && xdesc.isMaxModeVisible());
      spec.setTextFrame(xdesc.getTextFrame());
      spec.setTruncate(xdesc.isTruncate());
      coord.getAxisLabelScale().setAxisSpec(spec);
       */
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

         for(int i = 0; i < grefs.length; i++) {
            if(grefs[i] != null && (fullname && GraphUtil.equalsName(grefs[i], fld) ||
               !fullname && fld.equals(grefs[i].getName())))
            {
               return grefs[i];
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
      return ganttFields.stream().filter(a -> a != null).toArray(String[]::new);
   }

   private List<String> ganttFields = new ArrayList<>();
}
