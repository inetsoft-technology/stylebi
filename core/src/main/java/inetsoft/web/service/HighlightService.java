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
package inetsoft.web.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.data.BoxDataSet;
import inetsoft.report.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.filter.*;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.internal.binding.Field;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XCube;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
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

   /**
    * Get data refs available for the assembly. Called from highlight and hyperlink dialogs
    * to get available fields.
    *
    * @param rvs        the Runtime Viewsheet instance
    * @param assembly   the VS Assembly
    * @param row        the selected row
    * @param col        the selected col
    * @param dpath      the table data path for table assemblies
    * @param colName    the selected column name
    * @return  List of available data refs
    * @throws Exception if failed to get data refs
    */
   public List<DataRef> getRefsForVSAssembly(RuntimeViewsheet rvs, VSAssembly assembly, int row,
                                             int col, TableDataPath dpath, String colName,
                                             boolean isHighlight)
      throws Exception
   {
      return getRefsForVSAssembly(rvs, assembly, row, col, dpath, colName, isHighlight, false);
   }

   /**
    * Get data refs available for the assembly. Called from highlight and hyperlink dialogs
    * to get available fields.
    *
    * @param rvs        the Runtime Viewsheet instance
    * @param assembly   the VS Assembly
    * @param row        the selected row
    * @param col        the selected col
    * @param dpath      the table data path for table assemblies
    * @param colName    the selected column name
    * @param applyDiscrete whether to apply discrete for aggregate.
    * @return  List of available data refs
    * @throws Exception if failed to get data refs
    */
   public List<DataRef> getRefsForVSAssembly(RuntimeViewsheet rvs, VSAssembly assembly, int row,
                                             int col, TableDataPath dpath, String colName,
                                             boolean isHighlight, boolean applyDiscrete)
      throws Exception
   {
      List<DataRef> fieldList = new ArrayList<>();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return fieldList;
      }

      if(assembly instanceof CrosstabVSAssembly) {
         VSCrosstabInfo info = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
         DataRef[] cheaders = info.getRuntimeColHeaders();
         DataRef[] rheaders = isHighlight ?
            info.getRuntimeRowHeaders() : info.getPeriodRuntimeRowHeaders();
         XDimensionRef[] dcTempGroups = info.getDcTempGroups();
         DataRef[] aggregates = info.getRuntimeAggregates();
         Object data = box.getData(assembly.getAbsoluteName());

         if(data instanceof TableLens) {
            TableLens table = (TableLens) data;
            Field[] fields = TableHighlightAttr.getAvailableFields(table, dpath);

            if(fields != null && fields.length > 0) {
               for(int i = 0; !isHighlight && i < fields.length; i++) {
                  String name = fields[i].getName();

                  if(name.equals(CrossTabFilter.ROW_GRAND_TOTAL_HEADER) ||
                     name.equals(CrossTabFilter.COL_GRAND_TOTAL_HEADER))
                  {
                     AttributeRef attr = new AttributeRef(null, name);
                     attr.setDataType(fields[i].getDataType());
                     fieldList.add(attr);
                  }
               }

               for(DataRef rheader : rheaders) {
                  DataRef ref = getDimensionColumnRef(rheader);

                  for(Field f : fields) {
                     if(isSameField(ref, f)) {
                        fieldList.add(ref);
                        break;
                     }
                  }
               }

               for(DataRef cheader : cheaders) {
                  DataRef ref = getDimensionColumnRef(cheader);

                  for(Field f : fields) {
                     if(isSameField(ref, f)) {
                        fieldList.add(ref);
                        break;
                     }
                  }
               }

               for(DataRef tempGroup : dcTempGroups) {
                  DataRef ref = getDimensionColumnRef(tempGroup);

                  for(Field f : fields) {
                     if(isSameField(ref, f)) {
                        fieldList.add(ref);
                        break;
                     }
                  }
               }

               for(DataRef aggregate : aggregates) {
                  DataRef ref = getAggregateColumn(aggregate);

                  for(Field f : fields) {
                     if(isSameField(ref, f, true)) {
                        if(fieldList.contains(f)) {
                           continue;
                        }

                        fieldList.add(f);
                        break;
                     }
                  }
               }
            }
         }
      }
      else if(assembly instanceof TableVSAssembly) {
         Object data = box.getData(assembly.getAbsoluteName());
         boolean iscrosstab = false;

         if(data instanceof TableLens) {
            TableLens table = (TableLens) data;
            iscrosstab = table.getDescriptor().getType() == TableDataDescriptor.CROSSTAB_TABLE;
            boolean vsAssemblyBinding = VSUtil.isVSAssemblyBinding(assembly);

            while(!iscrosstab && table instanceof TableFilter && !vsAssemblyBinding) {
               table = ((TableFilter) table).getTable();

               if(table.getDescriptor().getType() == TableDataDescriptor.CROSSTAB_TABLE) {
                  iscrosstab = true;
               }
            }

            if(iscrosstab || vsAssemblyBinding) {
               Field[] fields = vsAssemblyBinding ?
                  TableHighlightAttr.getAvailableFields(table, row, col) :
                  TableHighlightAttr.getAvailableFields((TableLens) data, dpath);

               if(fields != null && fields.length > 0) {
                  fieldList.addAll(Arrays.asList(fields));
               }
            }
         }

         if(fieldList.isEmpty()) {
            if(!iscrosstab) {
               ColumnSelection cols = VSUtil.getBaseColumns(assembly, true);
               VSUtil.mergeColumnAlias(cols, ((TableVSAssembly)assembly).getColumnSelection());

               for(int i = 0; i < cols.getAttributeCount(); i++) {
                  fieldList.add(cols.getAttribute(i));
               }
            }
            else {
               Worksheet ws = box.getWorksheet();
               AbstractTableAssembly tassembly = (AbstractTableAssembly)
                  ws.getAssembly(assembly.getTableName());

               if(tassembly.isAggregate()) {
                  ColumnSelection columns = tassembly.getColumnSelection(true);
                  columns = VSUtil.getVSColumnSelection(columns);

                  for(int i = 0; i < columns.getAttributeCount(); i++) {
                     fieldList.add(columns.getAttribute(i));
                  }
               }
               else {
                  DataVSAQuery query = (DataVSAQuery) VSAQuery.
                     createVSAQuery(box, assembly, DataMap.NORMAL);
                  ColumnSelection columns;
                  columns = query.getDefaultColumnSelection();
                  columns = VSUtil.getVSColumnSelection(columns);

                  for(int i = 0; i < columns.getAttributeCount(); i++) {
                     fieldList.add(columns.getAttribute(i));
                  }
               }
            }
         }
      }
      else if(assembly instanceof CalcTableVSAssembly) {
         TableLens table = (TableLens) box.getData(assembly.getAbsoluteName());
         Field[] fields = TableHighlightAttr.getAvailableFields(table, row, col);

         if(fields != null && fields.length > 0) {
            for(Field field : fields) {
               if(!fieldList.contains(field)) {
                  fieldList.add(field);
               }
            }
         }
      }
      else if(assembly instanceof ChartVSAssembly) {
         ChartVSAssemblyInfo assemblyInfo = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
         SourceInfo sourceInfo = assemblyInfo.getSourceInfo();
         String ctype = sourceInfo == null ? null :
            VSUtil.getCubeType(sourceInfo.getPrefix(), sourceInfo.getSource());
         VSChartInfo chartInfo = assemblyInfo.getVSChartInfo();
         ColumnSelection columns = GraphUtil.createViewColumnSelection(chartInfo, colName,
                                                                       applyDiscrete, assemblyInfo.getChartDescriptor());
         columns = sortColumns(columns, ctype);

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            fieldList.add(columns.getAttribute(i));
         }
      }

      return fieldList;
   }

   public List<String> getNamedGroupFields(RuntimeCalcTableLens table, List<DataRef> fields) {
      List<String> namedGroupFields = new ArrayList<>();
      TableLayout layout = table.getElement().getTableLayout();

      for(int i = 0; i < fields.size(); i++) {
         if(fields.get(i) == null) {
            continue;
         }

         String fieldName = fields.get(i).getName();
         Point pos = table.getCellLocation(fieldName);
         int row = pos.y;
         int col = pos.x;
         CellBinding binding = layout.getCellBinding(row, col);

         if(binding.getBType() != TableCellBinding.GROUP) {
            continue;
         }

         if(binding.getType() == TableCellBinding.BIND_FORMULA) {
            String formula = binding.getValue();

            if(formula == null || !formula.startsWith("mapList(")) {
               continue;
            }

            namedGroupFields.add(fieldName);
            continue;
         }

         TableCellBinding cellBinding = (TableCellBinding) binding;

         if(cellBinding.getOrderInfo(false) != null &&
            cellBinding.getOrderInfo(false).getRealNamedGroupInfo() != null)
         {
            namedGroupFields.add(fieldName);
         }
      }

      return namedGroupFields;
   }

   /**
    * Gets the correct group column reference for date range columns, Called from highlight.
    * @param ref the base ref.
    * @return the correct header ref.
    */
   public DataRef getDimensionColumnRef(DataRef ref) {
      if(ref instanceof VSDimensionRef) {
         VSDimensionRef dref = (VSDimensionRef) ref;
         GroupRef gref = dref.createGroupRef(null);
         ColumnRef cref = (ColumnRef) gref.getDataRef();

         if(cref.getDataRef() instanceof DateRangeRef) {
            cref.setView(Tool.localize(cref.toView()));
            // @by davyc, DateRangeRef data type is number or timeinstant,
            //  so here use original type, otherwise for part option, condition
            //  editor will be error for time editor, in fact it is default
            // cref.setDataType(XSchema.DATE);
            cref.setDataType(cref.getDataRef().getDataType());
            ref = cref;
         }
         // fix bug1288617773137, and crosstab highlight
         else if(cref.getDataRef() instanceof NamedRangeRef) {
            ref = cref;
         }
         else {
            DataRef cdref = cref.getDataRef();

            // for cube ref, use correct data ref
            if((cdref.getRefType() & DataRef.CUBE) != 0) {
               String attr = dref.getFullName();
               attr = VSCubeTableLens.getDisplayFullValue(attr);
               AttributeRef attrref = new AttributeRef(null, attr);
               attrref.setDataType(cdref.getDataType());
               attrref.setRefType(cdref.getRefType());
               cref.setDataRef(attrref);
               ref = cref;
            }
         }
      }

      return ref;
   }

   private boolean isSameField(DataRef ref1, DataRef ref2) {
      return isSameField(ref1, ref2, false);
   }

   private boolean isSameField(DataRef ref1, DataRef ref2, boolean isAggregate) {
      if(ref1 == null || ref2 == null) {
         return false;
      }

      if(ref1.equals(ref2)) {
         return true;
      }

      String refName1 = getDataRefName(ref1);
      String refName2 = getDataRefName(ref2);

      if(isAggregate) {
         if(Tool.isEmptyString(refName1) || Tool.isEmptyString(refName2)) {
            return false;
         }

         if(refName2.startsWith(refName1 + ".") ) {
            return refName2.substring(refName1.length() + 1).matches("\\d+");
         }
      }

      return Tool.equals(refName1, refName2);
   }

   private String getDataRefName(DataRef ref) {
      String name = ref.getName();

      if(ref instanceof ColumnRef && ((ColumnRef) ref).getDataRef() instanceof DateRangeRef) {
         DateRangeRef dataRef = (DateRangeRef) ((ColumnRef) ref).getDataRef();
         name = dataRef.getDateOption() == DateRangeRef.NONE_INTERVAL ? dataRef.getDataRef().getName() : name;
      }

      return name;
   }

   /**
    * Add aggregate column.
    */
   private DataRef getAggregateColumn(DataRef dref) {
      VSAggregateRef aggr = (VSAggregateRef) dref;
      DataRef ref = aggr.getDataRef();

      if(ref instanceof ColumnRef) {
         ColumnRef col2 = (ColumnRef) ref;
         DataRef bref = col2.getDataRef();

         if(bref instanceof AliasDataRef || bref instanceof AttributeRef) {
            int refType = bref.getRefType();
            String field = bref.getAttribute();

            if(aggr.getCalculator() != null) {
               field = aggr.getFullName(field, null, aggr.getCalculator());
            }

            bref = new AttributeRef(field);
            ((AttributeRef) bref).setRefType(refType);
            col2 = (ColumnRef) col2.clone();
            col2.setDataRef(bref);
            col2.setView(dref.toView());
            ref = col2;
         }
         else if(bref instanceof ExpressionRef) {
            String field = bref.getAttribute();

            if(aggr.getCalculator() != null) {
               field = aggr.getFullName(field, null, aggr.getCalculator());
            }

            bref = (ExpressionRef) bref.clone();
            ((ExpressionRef) bref).setName(field);
            col2 = (ColumnRef) col2.clone();
            col2.setDataRef(bref);
            col2.setView(dref.toView());
            ref = col2;
         }

         AggregateFormula formula = aggr.getFormula();

         if(formula != null && formula.getDataType() != null) {
            col2.setDataType(formula.getDataType());
         }
      }

      return ref;
   }

   /**
    * Sort the columns by name. For Chart GetChartColumnsEvent
    */
   private ColumnSelection sortColumns(ColumnSelection cols, String ctype) {
      List<DataRef> list = new ArrayList<>();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);

         if(ref instanceof ColumnRef) {
            String view = ((ColumnRef) ref).getView();
            ColumnRef column = ((ColumnRef) ref).getDataRef() instanceof DateRangeRef ||
               ((ColumnRef) ref).getDataRef() instanceof NamedRangeRef
               ? ((ColumnRef) ref) : VSUtil.getVSColumnRef((ColumnRef) ref);

            if(view != null && (XCube.SQLSERVER.equals(ctype) &&
               (ref.getRefType() & DataRef.CUBE_MEASURE) ==
                  DataRef.CUBE_MEASURE ||
               cols.getProperty("View_" + view) != null))
            {
               column.setView(view);
            }

            list.add(column);
         }
      }

      list.sort(new VSUtil.DataRefComparator());
      cols.clear();

      for(DataRef ref : list) {
         cols.addAttribute(ref);
      }

      return cols;
   }

   private final DataRefModelFactoryService dataRefModelFactoryService;
   private final ViewsheetService viewsheetService;
}
