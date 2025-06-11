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
package inetsoft.analytic.composition.event;

import inetsoft.graph.data.*;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Chart VSSelection util.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class ChartVSSelectionUtil {
   /**
    * Create the VSSelection according to the selection string.
    * @param selected selected values in the form of, e.g.
    *  name1^VALUE:value1^INDEX:rowIndex^AND^name2^VALUE:value2^INDEX:rowIndex``name3^INDEX:n1
    * @param expothers true to expand the 'Others' item to individual items.
    */
   public static VSSelection getVSSelection(String selected, VSDataSet lens,
                                            VSDataSet alens,
                                            DataSet viewdata,
                                            boolean rangeSelection,
                                            VSChartInfo cinfo,
                                            boolean drillThrough,
                                            String cubetype,
                                            boolean expothers,
                                            boolean onlyDim,
                                            boolean drillFilter)
   {
      return getVSSelection(selected, lens, alens, viewdata, rangeSelection, cinfo, drillThrough,
         cubetype, expothers, onlyDim, drillFilter, false);
   }
   /**
    * Create the VSSelection according to the selection string.
    * @param selected selected values in the form of, e.g.
    *  name1^VALUE:value1^INDEX:rowIndex^AND^name2^VALUE:value2^INDEX:rowIndex``name3^INDEX:n1
    * @param expothers true to expand the 'Others' item to individual items.
    */
   public static VSSelection getVSSelection(String selected, VSDataSet lens,
                                            VSDataSet alens,
                                            DataSet viewdata,
                                            boolean rangeSelection,
                                            VSChartInfo cinfo,
                                            boolean drillThrough,
                                            String cubetype,
                                            boolean expothers,
                                            boolean onlyDim,
                                            boolean drillFilter,
                                            boolean flyover)
   {
      boolean cube = drillThrough || XCube.SQLSERVER.equals(cubetype) ||
         XCube.MONDRIAN.equals(cubetype) || XCube.ESSBASE.equals(cubetype) ||
         XCube.SAP.equals(cubetype);
      VSSelection selection = cube ? new VSCubeSelection() : new VSSelection();

      if(selected == null || lens == null) {
         return selection;
      }

      String[] headers = new String[lens.getColCount()];
      String[] headers2 = new String[lens.getColCount()];

      for(int i = 0; i < headers.length; i++) {
         headers[i] = lens.getHeader(i);
         headers2[i] = getHeader(headers[i], lens, cubetype);
      }

      boolean boxplot = viewdata instanceof BoxDataSet;
      String[] selects = selected.split("``");

      for(int i = 0; i < selects.length; i++) {
         int idx = findHeader(selects[i]);

         if(idx < 0) {
            continue;
         }

         String header = selects[i].substring(0, idx);
         String text = selects[i].substring(idx + 1);
         Collection<VSPoint> points = new HashSet<>();

         if(text.startsWith("INDEX:")) {
            String value = text.substring("INDEX:".length());
            int row = Integer.parseInt(value);

            if(row < 0) {
               continue;
            }

            if(viewdata instanceof PairsDataSet &&
               PairsDataSet.YMEASURE_VALUE.equals(header))
            {
               if(!drillFilter) {
                  header = (String) viewdata.getData(PairsDataSet.YMEASURE_NAME, row);
                  String header2 = (String) viewdata.getData(PairsDataSet.XMEASURE_NAME, row);
                  headers = headers2 = new String[] { header, header2 };
               }

               row = ((PairsDataSet) viewdata).getBaseRow(row);
            }

            Set<String> treeFields = null;

            if(cinfo.getChartType() == GraphTypes.CHART_SUNBURST ||
               cinfo.getChartType() == GraphTypes.CHART_CIRCLE_PACKING ||
               cinfo.getChartType() == GraphTypes.CHART_ICICLE ||
               cinfo.getChartType() == GraphTypes.CHART_TREEMAP)
            {
               treeFields = new HashSet<>();
               ChartRef[] treeDims = cinfo.getRTGroupFields();
               boolean leaf = treeDims.length > 0 &&
                  treeDims[treeDims.length - 1].getFullName().equals(header);

               for(ChartRef dim : treeDims) {
                  treeFields.add(dim.getFullName());

                  if(dim.getFullName().equals(header)) {
                     break;
                  }
               }

               if(leaf) {
                  if(cinfo.getColorField() != null &&
                     cinfo.getColorField().getDataRef() instanceof ChartRef) {
                     treeFields.add(((ChartRef) cinfo.getColorField().getDataRef()).getFullName());
                  }

                  if(cinfo.getShapeField() != null &&
                     cinfo.getShapeField().getDataRef() instanceof ChartRef) {
                     treeFields.add(((ChartRef) cinfo.getShapeField().getDataRef()).getFullName());
                  }

                  if(cinfo.getSizeField() != null &&
                     cinfo.getSizeField().getDataRef() instanceof ChartRef) {
                     treeFields.add(((ChartRef) cinfo.getSizeField().getDataRef()).getFullName());
                  }

                  if(cinfo.getTextField() != null &&
                     cinfo.getTextField().getDataRef() instanceof ChartRef) {
                     treeFields.add(((ChartRef) cinfo.getTextField().getDataRef()).getFullName());
                  }
               }
            }
            else if(cinfo instanceof RelationChartInfo) {
               treeFields = new HashSet<>();
               treeFields.add(header);

               treeFields.addAll(Arrays.stream(cinfo.getXFields())
                                    .map(f -> f.getFullName()).collect(Collectors.toList()));
               treeFields.addAll(Arrays.stream(cinfo.getYFields())
                                    .map(f -> f.getFullName()).collect(Collectors.toList()));
            }
            else if(cinfo instanceof CandleChartInfo) {
               treeFields = new HashSet<>();
               CandleChartInfo candleInfo = (CandleChartInfo) cinfo;

               if(candleInfo.getHighField() != null) {
                  treeFields.add(candleInfo.getHighField().getFullName());
               }
               else if(candleInfo.getLowField() != null) {
                  treeFields.add(candleInfo.getLowField().getFullName());
               }
               else if(candleInfo.getCloseField() != null) {
                  treeFields.add(candleInfo.getCloseField().getFullName());
               }
               else if(candleInfo.getOpenField() != null) {
                  treeFields.add(candleInfo.getOpenField().getFullName());
               }

               if(cinfo.getColorField() != null &&
                  cinfo.getColorField().getDataRef() instanceof ChartRef)
               {
                  treeFields.add(cinfo.getColorField().getFullName());
               }

               if(cinfo.getShapeField() != null &&
                  cinfo.getShapeField().getDataRef() instanceof ChartRef)
               {
                  treeFields.add(cinfo.getShapeField().getFullName());
               }

               if(cinfo.getSizeField() != null &&
                  cinfo.getSizeField().getDataRef() instanceof ChartRef)
               {
                  treeFields.add(cinfo.getSizeField().getFullName());
               }

               if(cinfo.getTextField() != null &&
                  cinfo.getTextField().getDataRef() instanceof ChartRef)
               {
                  treeFields.add(cinfo.getTextField().getFullName());
               }
            }

            if(treeFields != null) {
               ChartRef[][] xyfields = { cinfo.getRTXFields(), cinfo.getRTYFields() };

               for(ChartRef[] refs : xyfields) {
                  for(ChartRef ref : refs) {
                     treeFields.add(ref.getFullName());
                  }
               }
            }

            // boxplot treat as range
            if(boxplot) {
               BoxDataSet boxDataSet = (BoxDataSet) viewdata;

               if(header.startsWith(BoxDataSet.MAX_PREFIX)) {
                  String baseCol = BoxDataSet.getBaseName(header);
                  VSPoint rangePt = new VSPoint();

                  for(String dim : boxDataSet.getDims()) {
                     DataRef ref = cinfo.getFieldByName(dim, false);

                     if(cubetype != null && ref != null &&
                        (ref.getRefType() & DataRef.CUBE_MEASURE) == DataRef.CUBE_MEASURE)
                     {
                        continue;
                     }

                     rangePt.addValue(getFieldValue(dim, viewdata.getData(dim, row)));
                  }

                  Object max = viewdata.getData(BoxDataSet.MAX_PREFIX + baseCol, row);
                  Object min = viewdata.getData(BoxDataSet.MIN_PREFIX + baseCol, row);

                  DataRef bref = cinfo.getFieldByName(baseCol, false);

                  if(cubetype == null || bref == null ||
                     (bref.getRefType() & DataRef.CUBE_MEASURE) != DataRef.CUBE_MEASURE)
                  {
                     rangePt.addValue(new VSFieldValue(baseCol, Tool.getDataString(min),
                        Tool.getDataString(max)));
                  }

                  points.add(rangePt);
               }
               else {
                  VSPoint valPt = new VSPoint();

                  for(String dim : boxDataSet.getDims()) {
                     valPt.addValue(getFieldValue(dim, viewdata.getData(dim, row)));
                  }

                  if(!onlyDim) {
                     valPt.addValue(getFieldValue(header, viewdata.getData(header, row)));
                  }

                  points.add(valPt);
               }
            }
            else {
               VSFieldValue[][] pts = lens.getFieldValues(
                  row, headers, headers2, expothers, GraphTypes.isTreemap(cinfo.getChartType()));

               for(VSFieldValue[] pt : pts) {
                  VSPoint pj = new VSPoint();

                  for(VSFieldValue pair : pt) {
                     if(treeFields == null || treeFields.contains(pair.getFieldName())) {
                        pj.addValue(pair);
                     }
                  }

                  points.add(pj);
               }
            }
         }
         else if(text.startsWith("VALUE:")) {
            String[] valstrs = text.split("\\^AND\\^");
            // add back the header to the first item
            valstrs[0] = header + "^" + valstrs[0];

            for(String valstr : valstrs) {
               int hidx = valstr.indexOf('^');

               if(hidx <= 0) {
                  continue;
               }

               header = valstr.substring(0, hidx);
               int cidx = lens.indexOfHeader(header);

               // discard aggregate brushing
               if(cidx < 0 || (lens.getPeriodCol() == cidx && !flyover) ||
                  lens.isRealAggregate(header))
               {
                  continue;
               }

               header = getHeader(header, lens, cubetype);
               String value = valstr.substring(hidx + "^VALUE:".length());
               int ridx = value.indexOf("^INDEX:");
               int row = -1;

               if(ridx != -1) {
                  try {
                     row = Integer.parseInt(value.substring(ridx + "^INDEX:".length()));
                  }
                  catch(Exception ignore) {
                  }

                  value = value.substring(0, ridx);
               }

               if("".equals(value) || Tool.FAKE_NULL.equals(value)) {
                  if(lens.getRowCount() > 1) {
                     Object obj = lens.getData(header, 1);

                     if(obj instanceof DCMergeDatesCell &&
                        ((DCMergeDatesCell) obj).ingoreInChartSelection())
                     {
                        continue;
                     }
                  }

                  value = null;
               }

               VSFieldValue[] pairs = lens.getFieldValues(
                  header, value, expothers, GraphTypes.isTreemap(cinfo.getChartType()), row);

               // try all lens to support brush on all the axes
               if(pairs.length == 0 && alens != null) {
                  pairs = alens.getFieldValues(header, value, expothers,
                                               GraphTypes.isTreemap(cinfo.getChartType()), row);
               }

               if(points.size() == 0) {
                  for(int j = 0; j < pairs.length; j++) {
                     VSPoint point = new VSPoint();
                     point.addValue(pairs[j]);

                     if(!points.contains(point)) {
                        points.add(point);
                     }
                  }
               }
               else {
                  List<VSPoint> points2 = new ArrayList<>();

                  for(VSPoint point : points) {
                     for(int j = 0; j < pairs.length; j++) {
                        VSPoint point2 = (VSPoint) point.clone();
                        point2.addValue(pairs[j]);

                        if(!points2.contains(point2)) {
                           points2.add(point2);
                        }
                     }
                  }

                  points = points2;
               }
            }

            for(VSPoint point : points) {
               selection.addPoint(point);
            }

            points.clear();
         }

         // add in all measures for radar, candle, stock
         if(drillThrough) {
            if(points.size() == 0) {
               points.add(new VSPoint());
            }

            if(cinfo instanceof MergedVSChartInfo) {
               addMeasuresToSelection(selection, points, lens, headers, header, true);
            }

            if(cinfo.getChartType() == GraphTypes.CHART_SUNBURST ||
               cinfo.getChartType() == GraphTypes.CHART_CIRCLE_PACKING ||
               cinfo.getChartType() == GraphTypes.CHART_ICICLE ||
               cinfo.getChartType() == GraphTypes.CHART_TREEMAP)
            {
               addMeasuresToSelection(selection, points, lens, headers, header, false);
            }

            if(lens.isRealAggregate(header)) {
               for(VSPoint pj : points) {
                  pj.addValue(new VSFieldValue(header, null));
               }
            }

            for(VSPoint pj : points) {
               if(pj.isEmpty()) {
                  VSFieldValue pair = new VSFieldValue(header, null);
                  pj.addValue(pair);
               }
            }
         }

         for(VSPoint pj : points) {
            if(!pj.isEmpty()) {
               selection.addPoint(pj);
            }
         }
      }

      int range = rangeSelection ?
         VSSelection.PHYSICAL_RANGE : VSSelection.NONE_RANGE;
      int ctype = GraphTypeUtil.getRTChartType(cinfo);
      // for aggregated chart, range selection is meaningless
      rangeSelection = !cinfo.isAggregated() && rangeSelection;

      // for non-point chart, range selection is meaningless
      if(rangeSelection) {
         if(GraphTypes.isMap(ctype)) {
            // range selection supported for latitude/longitude binding
            ColumnSelection cols = cinfo.getGeoColumns();
            ChartRef[][] xyrefs = {cinfo.getRTYFields(), cinfo.getRTXFields()};
            boolean existLatitudeOrlongitude = false;

            if(cols != null && cols.getAttributeCount() > 0) {
               for(ChartRef[] refs : xyrefs) {
                  for(int i = 0; i < refs.length; i++) {
                     if(refs[i] instanceof VSChartAggregateRef &&
                        cols.getAttribute(refs[i].getFullName()) != null)
                     {
                        existLatitudeOrlongitude = true;
                        break;
                     }
                  }
               }
            }

            if(!existLatitudeOrlongitude) {
               rangeSelection = false;
            }
         }
         else if((!GraphTypes.isPoint(ctype) || GraphTypeUtil.isWordCloud(cinfo)) &&
            !GraphTypes.isContour(ctype))
         {
            rangeSelection = false;
         }
      }

      // for range selection, we should remove aesthetic fields
      if(rangeSelection && !selection.isEmpty()) {
         Set set = new HashSet();
         ChartRef[][] xyrefs = {
            cinfo.getRTYFields(), cinfo.getRTXFields(),
            cinfo instanceof MapInfo ? ((MapInfo) cinfo).getRTGeoFields() : new ChartRef[0]};

         for(ChartRef[] refs : xyrefs) {
            for(int i = 0; i < refs.length; i++) {
               if(!isRangeSelectionSupported(refs[i])) {
                  rangeSelection = false;
               }

               set.add(refs[i].getFullName());
            }
         }

         if(rangeSelection) {
            range = VSSelection.LOGICAL_RANGE;
            // discard aesthetic fields if lasso selection in plot
            removeAesthetic(selection, set);
         }
      }

      selection.setRange(range);
      return selection.isEmpty() ? null : selection;
   }

   private static int findHeader(String str) {
      int v = str.indexOf("^VALUE:");
      return v >= 0 ? v : str.indexOf("^INDEX:");
   }

   /**
    * Add in all measures for merged chart radar, candle, stock and sunburst, circle_packing, icicle.
    */
   private static void addMeasuresToSelection(VSSelection selection, Collection<VSPoint> points,
                                              VSDataSet lens, String[] headers, String header,
                                              boolean merged)
   {
      if(selection == null || points == null || lens == null) {
         return;
      }

      for(int j = 0; j < headers.length; j++) {
         if(headers[j].equals(header)) {
            continue;
         }

         if(!lens.isMeasure(headers[j])) {
            continue;
         }

         for(VSPoint pj : points) {
            if(merged) {
               if(pj.isEmpty()) {
                  VSPoint point0 = (VSPoint) pj.clone();
                  VSFieldValue pair = new VSFieldValue(headers[j], null);
                  point0.addValue(pair);
                  selection.addPoint(point0);
               }
            }
            else if(pj.isEmpty() || pj.getValue(headers[j]) == null){
               pj.addValue(new VSFieldValue(headers[j], null));
            }
         }
      }
   }

   /**
    * Get the field value used for analysis.
    */
   private static VSFieldValue getFieldValue(String name, Object value) {
      return new VSFieldValue(name, value, true);
   }

   /**
    * Get proper header considering named group.
    */
   private static String getHeader(String header, VSDataSet lens, String cubeType) {
      if(cubeType == null || cubeType.trim().length() == 0) {
         return header;
      }

      int idx = header.indexOf("Group(");

      if(XCube.SQLSERVER.equals(cubeType)) {
         if(idx < 0) {
            String header0 = "Group(" + header;

            for(int i = 0; i < lens.getColCount(); i++) {
               String hdr = lens.getHeader(i);

               if(hdr.indexOf(header0) >= 0) {
                  return hdr;
               }
            }
         }
      }
      else if(idx >= 0) {
         String hdr = header.substring(idx + 6, header.length() - 1);

         if(lens.indexOfHeader(hdr) >= 0) {
            return hdr;
         }
      }

      return header;
   }

   /**
    * Check if range selection is supported for this chart ref.
    * @param ref the specified chart ref.
    * @return true if range selection is supported, false otherwise.
    */
   private static boolean isRangeSelectionSupported(ChartRef ref) {
      if((ref.getRefType() & DataRef.CUBE) != 0) {
         return false;
      }

      // a measure always supports range selection
      if(!(ref instanceof VSDimensionRef)) {
         return true;
      }

      VSDimensionRef dim = (VSDimensionRef) ref;
      int order = dim.getOrder();

      return (order == XConstants.SORT_ASC || order == XConstants.SORT_DESC) &&
         (dim.getNamedGroupInfo() == null || dim.getNamedGroupInfo().isEmpty());
   }

   /**
    * Remove aesthetic fields.
    */
   private static void removeAesthetic(VSSelection selection, Set set) {
      if(selection == null || selection.isEmpty()) {
         return;
      }

      for(int i = selection.getPointCount() - 1; i >= 0; i--) {
         VSPoint point = selection.getPoint(i);

         for(int j = point.getValueCount() - 1; j >= 0; j--) {
            VSFieldValue val = point.getValue(j);
            String fld = val.getFieldName();

            if(!set.contains(fld)) {
               point.removeValue(j);
            }
         }

         if(point.isEmpty()) {
            selection.removePoint(i);
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(ChartVSSelectionUtil.class);
}
