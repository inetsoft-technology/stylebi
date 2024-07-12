/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.data.BoxDataSet;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.filter.*;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.XAggregateRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.model.FontInfo;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.composer.model.vs.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

@Component
public class HighlightService {
   @Autowired
   public HighlightService(
      ViewsheetService viewsheetService,
      DataRefModelFactoryService dataRefModelFactoryService)
   {
      this.viewsheetService = viewsheetService;
      this.dataRefModelFactoryService = dataRefModelFactoryService;
   }

   /**
    * For stack measure, the text value is the total of all measures so instead of defining
    * condition on individual measure, we define the conditions for the top measure and
    * name it 'Total'.
    */
   public static void processStackMeasures(ChartDescriptor desc, ChartInfo info,
                                           ColumnSelection colsel, String aggr)
   {
      if(GraphTypeUtil.isStackMeasures(info, desc) && desc.getPlotDescriptor().isStackValue()) {
         ChartRef[] yrefs = info.getYFields();
         ChartRef[] xrefs = info.getXFields();
         String topMeasure = getTopMeasure(yrefs, info);

         if(topMeasure == null) {
            topMeasure = getTopMeasure(xrefs, info);
         }

         // if defining highlight on middle measure (the top measure is missing at this point),
         // should not use 'Total', just the measure itself. (61316)
         if(!Objects.equals(topMeasure, aggr)) {
            return;
         }

         for(int i = colsel.getAttributeCount() - 1; i >= 0; i--) {
            DataRef ref = colsel.getAttribute(i);

            if(GraphUtil.isMeasure(info.getFieldByName(ref.getName(), false))) {
               if(ref.getName().equals(topMeasure)) {
                  if(ref instanceof BaseField) {
                     ((BaseField) ref).setView("Total");
                  }
                  else if(ref instanceof ColumnRef) {
                     ((ColumnRef) ref).setView("Total");
                  }
               }
               else {
                  colsel.removeAttribute(i);
               }
            }
         }
      }
   }

   public static void processBoxplot(ChartInfo info, ColumnSelection colsel) {
      if(GraphTypes.isBoxplot(info.getRTChartType())) {
         List<XAggregateRef> yrefs = GraphUtil.getMeasures(info.getYFields());

         if(yrefs.isEmpty()) {
            yrefs = GraphUtil.getMeasures(info.getXFields());
         }

         for(XAggregateRef yref : yrefs) {
            String name = yref.getName();
            colsel.addAttribute(new ColumnRef(new AttributeRef(BoxDataSet.MAX_PREFIX + name)));
            colsel.addAttribute(new ColumnRef(new AttributeRef(BoxDataSet.Q75_PREFIX + name)));
            colsel.addAttribute(new ColumnRef(new AttributeRef(BoxDataSet.MEDIUM_PREFIX + name)));
            colsel.addAttribute(new ColumnRef(new AttributeRef(BoxDataSet.Q25_PREFIX + name)));
            colsel.addAttribute(new ColumnRef(new AttributeRef(BoxDataSet.MIN_PREFIX + name)));
         }
      }
   }

   private static String getTopMeasure(ChartRef[] yrefs, ChartInfo info) {
      String topMeasure = null;
      // in a multi-style chart, if a measure has a aesthetic dim while the other doesn't,
      // the one with aesthetic is always on top due to the sorting in IntervalElement
      // (null is sorted first). (61327)
      boolean requireTextField = info.isMultiStyles() &&
         Arrays.stream(yrefs).anyMatch(y -> hasGroup(y));

      for(int i = yrefs.length - 1; i >= 0; i--) {
         if(GraphUtil.isMeasure(yrefs[i]) && (!requireTextField || hasGroup(yrefs[i]))) {
            topMeasure = yrefs[i].getFullName();
            break;
         }
      }

      return topMeasure;
   }

   private static boolean hasGroup(ChartRef y) {
      if(y instanceof ChartAggregateRef) {
         ChartAggregateRef aggr = (ChartAggregateRef) y;
         return Arrays.stream(new AestheticRef[]{ aggr.getTextField() , aggr.getColorField(), aggr.getShapeField(), aggr.getSizeField() })
            .filter(a -> a != null && a.getDataRef() instanceof ChartDimensionRef)
            .filter(a -> !a.getFullName().equals(y.getFullName()))
            .count() > 0;
      }

      return false;
   }

   public HighlightModel convertHighlightToModel(Highlight highlight) {
      HighlightModel model = new HighlightModel();
      model.setName(highlight.getName());
      model.setForeground(highlight.getForeground() != null ?
         "#" + Tool.colorToHTMLString(highlight.getForeground()) : "");
      model.setBackground(highlight.getBackground() != null ?
         "#" + Tool.colorToHTMLString(highlight.getBackground()) : "");
      FontInfo fontInfo = new FontInfo();
      Font font = highlight.getFont();

      if(font != null) {
         fontInfo = new FontInfo(font);
      }

      model.setFontInfo(fontInfo);
      VSConditionDialogModel vsConditionDialogModel = new VSConditionDialogModel();
      vsConditionDialogModel.setConditionList(
         ConditionUtil.fromConditionListToModel(highlight.getConditionGroup(),
            dataRefModelFactoryService));
      model.setVsConditionDialogModel(vsConditionDialogModel);

      return model;
   }

   public Highlight convertModelToHighlight(HighlightModel highlightModel,
                                            Principal principal) throws Exception
   {
      return convertModelToHighlight(highlightModel, principal, false);
   }

   public Highlight convertModelToHighlight(HighlightModel highlightModel,
      Principal principal, boolean aggUseAggField) throws Exception
   {
      Highlight highlight = new TextHighlight();
      highlight.setName(highlightModel.getName());
      highlight.setForeground(Tool.getColorFromHexString(
         getColorHexString(highlightModel.getForeground())));
      highlight.setBackground(Tool.getColorFromHexString(
         getColorHexString(highlightModel.getBackground())));
      highlight.setConditionGroup(ConditionUtil.fromModelToConditionList(
         highlightModel.getVsConditionDialogModel().getConditionList(), null,
         viewsheetService, principal, null, null, aggUseAggField));

      FontInfo fontInfo = highlightModel.getFontInfo();
      Font font = null;

      if(fontInfo != null) {
         font = fontInfo.toFont();
      }

      highlight.setFont(font);

      return highlight;
   }

   public static String getColorHexString(String color) {
      if(color != null && !color.isEmpty()) {
         color = String.format("#%06x", Integer.decode(color));
      }

      return color;
   }

   public void getRowHighlight(
      TableHighlightAttr hattr, List<HighlightModel> highlightModelList, HighlightDialogModel model,
      TableDataPath dataPath, String tableName, DataRefModel[] fields)
   {
      TableDataPath rowPath = new TableDataPath(dataPath.getLevel(), dataPath.getType());
      HighlightGroup rows = hattr.getHighlight(rowPath);

      if(rows != null && !rows.isEmpty()) {
         for(String name : rows.getNames()) {
            Highlight cellHighlight = rows.getHighlight(name);
            HighlightModel highlightModel = convertHighlightToModel(cellHighlight);
            highlightModel.setApplyRow(true);
            VSConditionDialogModel vsConditionDialogModel =
               highlightModel.getVsConditionDialogModel();
            vsConditionDialogModel.setTableName(tableName);
            vsConditionDialogModel.setFields(fields);
            highlightModelList.add(highlightModel);
         }
      }

      // @by yiyangliang, for bug #19386
      // get the list of highlight names that are used in other cells.
      // when user enables "apply to row" on a highlight, its name will be checked against
      // this list to make sure that there are no duplicate names in other cells on the
      // same row.
      Enumeration dataPaths = hattr.getAllDataPaths();
      Set<String> set = new HashSet<>();

      while(dataPaths.hasMoreElements()) {
         TableDataPath dPath = (TableDataPath) dataPaths.nextElement();

         if(!dPath.equals(dataPath) && dPath.getType() == dataPath.getType()
            && !dPath.isRow())
         {
            String[] names = hattr.getHighlight(dPath).getNames();
            Arrays.stream(names).forEach((highlightName)-> set.add(highlightName));
         }
      }

      if(set.size() > 0) {
         model.setUsedHighlightNames(set.toArray(new String[0]));
      }
   }


   public void applyToRowHighlight(TableHighlightAttr hAttr, TableDataPath path,
                                    HighlightModel[] highlights, Principal principal)
      throws Exception
   {
      TableDataPath rowPath = new TableDataPath(path.getLevel(), path.getType());
      HighlightGroup rows = hAttr.getHighlight(rowPath);
      rows = rows == null ? new HighlightGroup() : rows;
      rows.removeHighlights(HighlightGroup.DEFAULT_LEVEL);

      for(HighlightModel highlightModel : highlights) {
         if(highlightModel.isApplyRow()) {
            rows.addHighlight(highlightModel.getName(),
                              convertModelToHighlight(highlightModel, principal));
         }
      }

      hAttr.setHighlight(rowPath, rows);
   }

   /**
    * Fix the datatype of the target column ref.
    *
    * For dategroup DateRangeRef, should use the original datatype in condition.
    */
   public void fixColumnDataType(DataRef ref) {
      if(!(ref instanceof ColumnRef) || !(((ColumnRef) ref).getDataRef() instanceof DateRangeRef)) {
         return;
      }

      DateRangeRef rangeRef = (DateRangeRef) ((ColumnRef) ref).getDataRef();
      String originalType = rangeRef.getOriginalType();

      if(rangeRef.getDateOption() == DateRangeRef.NONE_INTERVAL &&
         !StringUtils.isEmpty(originalType))
      {
         ((ColumnRef) ref).setDataType(originalType);
      }
      else {
         ((ColumnRef) ref).setDataType(
            DateRangeRef.getDataType(rangeRef.getDateOption(), originalType));
      }
   }

   private final DataRefModelFactoryService dataRefModelFactoryService;
   private final ViewsheetService viewsheetService;
}
