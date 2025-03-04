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
package inetsoft.web.binding.handler;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.InputScriptEvent;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.Common;
import inetsoft.report.internal.binding.BindingTool;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractModelTrapContext.TrapInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.*;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.event.ConvertChartRefEvent;
import inetsoft.web.binding.event.ConvertTableRefEvent;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableDrillController;
import inetsoft.web.viewsheet.event.ViewsheetEvent;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class VSAssemblyInfoHandler {
   @Autowired
   public VSAssemblyInfoHandler(CoreLifecycleService coreLifecycleService,
                                DataRefModelFactoryService dataRefService,
                                ParameterService parameterService)
   {
      this.coreLifecycleService = coreLifecycleService;
      this.dataRefService = dataRefService;
      this.parameterService = parameterService;
   }

   public void apply(RuntimeViewsheet rvs, VSAssemblyInfo info, ViewsheetService engine)
      throws Exception
   {
      apply(rvs, info, engine, false, false, false, false, null);
   }

   public void apply(RuntimeViewsheet rvs, VSAssemblyInfo info, ViewsheetService engine,
                     boolean confirmed, boolean checkTrap, boolean refreshPara,
                     boolean isEvent, CommandDispatcher commandDispatcher)
                     throws Exception
   {
      apply(rvs, info, engine, confirmed, checkTrap, refreshPara, isEvent,
         commandDispatcher, null, null, null, null);
   }

   /** ApplyVSAssemblyInfoEvent */
   public void apply(RuntimeViewsheet rvs, VSAssemblyInfo info, ViewsheetService engine,
                     boolean confirmed, boolean checkTrap, boolean refreshPara,
                     boolean isEvent, CommandDispatcher commandDispatcher,
                     String url, ViewsheetEvent evt, String linkUri,
                     Consumer<VSCrosstabInfo> updateCalculate)
      throws Exception
   {
      apply(rvs, info, engine, confirmed, checkTrap, refreshPara, isEvent, commandDispatcher,
         url, evt, linkUri, updateCalculate, null);
   }

   /** ApplyVSAssemblyInfoEvent */
   public void apply(RuntimeViewsheet rvs, VSAssemblyInfo info, ViewsheetService engine,
                     boolean confirmed, boolean checkTrap, boolean refreshPara,
                     boolean isEvent, CommandDispatcher commandDispatcher,
                     String url, ViewsheetEvent evt, String linkUri,
                     Consumer<VSCrosstabInfo> updateCalculate,
                     BiConsumer<Map<TableDataPath, VSCompositeFormat>, TableDataPath> removedObjFormatProcessor)
      throws Exception
   {
      apply(rvs, info, engine, confirmed, checkTrap, refreshPara, true, isEvent,
         commandDispatcher, url, evt, linkUri, updateCalculate, removedObjFormatProcessor);
   }

   public void apply(RuntimeViewsheet rvs, VSAssemblyInfo info, ViewsheetService engine,
                     boolean confirmed, boolean checkTrap, boolean refreshPara, boolean refreshData,
                     boolean isEvent, CommandDispatcher commandDispatcher,
                     String url, ViewsheetEvent evt, String linkUri,
                     Consumer<VSCrosstabInfo> updateCalculate)
      throws Exception
   {
      apply(rvs, info, engine, confirmed, checkTrap,
         refreshPara, refreshData, isEvent, commandDispatcher, url,
         evt, linkUri, updateCalculate, null);
   }

   /** ApplyVSAssemblyInfoEvent */
   public void apply(RuntimeViewsheet rvs, VSAssemblyInfo info, ViewsheetService engine,
                     boolean confirmed, boolean checkTrap, boolean refreshPara, boolean refreshData,
                     boolean isEvent, CommandDispatcher commandDispatcher,
                     String url, ViewsheetEvent evt, String linkUri,
                     Consumer<VSCrosstabInfo> updateCalculate,
                     BiConsumer<Map<TableDataPath, VSCompositeFormat>, TableDataPath> removedObjFormatProcessor)
                     throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      if(info instanceof TableVSAssemblyInfo) {
         processHideColumns((TableVSAssemblyInfo) info);
      }

      String name = info.getAbsoluteName2();
      VSAssembly assembly = vs.getAssembly(name);
      fixColumnWidths(assembly, box);

      if(assembly != null) {
         VSAssemblyInfo oinfo = assembly.getVSAssemblyInfo().clone();
         ViewsheetScope scope = box.getScope();
         int hint = assembly.setVSAssemblyInfo(info);

         if(isEvent) {
            scope.addVariable("event", new InputScriptEvent(name, assembly));
         }

         if(info instanceof DataVSAssemblyInfo && oinfo instanceof DataVSAssemblyInfo) {
            removeVariable(box, (DataVSAssemblyInfo)info, (DataVSAssemblyInfo) oinfo);
         }

         try {
            if(refreshPara) {
               List vars = new ArrayList();
               VSEventUtil.refreshParameters(engine, box, vs, false, null, vars);

               if(!vars.isEmpty() && !vs.getViewsheetInfo().isDisableParameterSheet()) {
                  UserVariable[] vtable = new UserVariable[vars.size()];
                  vars.toArray(vtable);
                  parameterService.collectParameters(vs, vtable,
                                                       false, commandDispatcher);
                  return;
               }
            }

            // apply selection for calendar?
            if(assembly instanceof CalendarVSAssembly) {
               return;
            }

            // for crosstab, fix aggregate info by its header,
            // column, aggregates
            if(assembly instanceof CrosstabVSAssembly) {
               // execute runtime fields for crosstab
               try {
                  box.updateAssembly(assembly.getAbsoluteName());
                  CrosstabVSAssembly cross = (CrosstabVSAssembly) assembly;
                  CrosstabVSAssemblyInfo ocinfo = (CrosstabVSAssemblyInfo) oinfo;
                  FormatInfo finfo = cross.getFormatInfo();
                  CrosstabVSAssemblyInfo ncinfo = (CrosstabVSAssemblyInfo)
                     cross.getVSAssemblyInfo();
                  TableHyperlinkAttr hyperlink = ncinfo.getHyperlinkAttr();
                  TableHighlightAttr highlight = ncinfo.getHighlightAttr();
                  TableLens lens = box.getVSTableLens(name, false);

                  // save the column header info to be restored after change is applied. (60379)
                  BaseTableDrillController.saveColumnInfo(cross.getCrosstabInfo(), lens);

                  if(finfo != null) {
                     VSUtil.syncCrosstabPath(cross, ocinfo, false, finfo.getFormatMap(),
                        removedObjFormatProcessor);
                  }

                  if(hyperlink != null) {
                     VSUtil.syncCrosstabPath(cross, ocinfo, false, hyperlink.getHyperlinkMap());
                  }

                  if(highlight != null) {
                     VSUtil.syncCrosstabPath(cross, ocinfo, false, highlight.getHighlightMap());
                  }

                  validateTopN(cross.getVSCrosstabInfo());
                  addFakeAggregate(cross.getVSCrosstabInfo());

                  if(updateCalculate != null) {
                     updateCalculate.accept(cross.getVSCrosstabInfo());
                  }

                  cleanHighlight(highlight, ncinfo);
               }
               catch(Exception e) {
                  // ignore it
               }
            }

            if(checkTrap && assembly instanceof DataVSAssembly) {
               VSModelTrapContext context = new VSModelTrapContext(rvs, true);
               TrapInfo trapInfo = context.isCheckTrap()
                  ? context.checkTrap(oinfo, assembly.getVSAssemblyInfo())
                  : null ;

               if(!confirmed && trapInfo != null && trapInfo.showWarning()) {
                  assembly.setVSAssemblyInfo(oinfo);
                  VSTrapCommand tcommand = new VSTrapCommand();
                  tcommand.setMessage(
                     Catalog.getCatalog().getString("designer.binding.continueTrap"));
                  tcommand.setType(MessageCommand.Type.INFO);

                  if(url != null) {
                     tcommand.addEvent(url, evt);
                  }

                  commandDispatcher.sendCommand(tcommand);
                  return;
               }

               if(context.isCheckTrap()) {
                  getCurrentGrayedOutFields(context, commandDispatcher);
               }
            }

            if(assembly instanceof EmbeddedTableVSAssembly &&
               AnnotationVSUtil.needRefreshAnnotation(info, commandDispatcher))
            {
               AnnotationVSUtil.fixAnnotationCellValue(rvs, (EmbeddedTableVSAssembly) assembly, evt);
            }

            try {
               this.coreLifecycleService.execute(rvs, assembly.getAbsoluteName(),
                                                 linkUri, hint, refreshData, commandDispatcher);

               if(assembly instanceof CrosstabVSAssembly) {
                  TableLens lens2 = box.getVSTableLens(name, false);

                  // restore the column header info saved before the change is applied. (60379)
                  boolean changed = BaseTableDrillController.restoreColumnInfo(
                     (CrosstabVSAssemblyInfo) assembly.getVSAssemblyInfo(), lens2);

                  if(changed) {
                     this.coreLifecycleService.execute(rvs, assembly.getAbsoluteName(), linkUri, hint,
                                                       refreshData, commandDispatcher);
                  }
               }
            }
            catch(ConfirmException e) {
               if(!this.coreLifecycleService.waitForMV(e, rvs, commandDispatcher)) {
                  throw e;
               }
            }

            // fix the column width to make the column fit the data
            if(assembly instanceof TableVSAssembly && fixColumnWidths(assembly, box) &&
               commandDispatcher != null)
            {
               this.coreLifecycleService.refreshVSAssembly(rvs, assembly.getAbsoluteName(),
                                                           commandDispatcher, true);
            }

            if(assembly instanceof TableDataVSAssembly) {
               updateLocalMap(vs, (TableDataVSAssembly) assembly);
            }

            if(commandDispatcher != null) {
               try {
                  this.coreLifecycleService.layoutViewsheet(rvs, rvs.getID(), url, commandDispatcher);
               }
               catch(ConfirmException e) {
                  if(!this.coreLifecycleService.waitForMV(e, rvs, commandDispatcher)) {
                     throw e;
                  }
               }
            }
         }
         finally {
            if(isEvent) {
               scope.removeVariable("event");
            }
         }
      }
   }

   public void dateLevelChanged(DrillFilterInfo drillInfo, DataRef[] orefs, DataRef[] nrefs) {
      if(orefs == null) {
         return;
      }

      for(int i = 0; i < orefs.length; i++) {
         if(!(orefs[i] instanceof VSDimensionRef)) {
            continue;
         }

         VSDimensionRef odim = (VSDimensionRef) orefs[i];

         if(i >= nrefs.length) {
            continue;
         }

         if(nrefs[i] instanceof VSDimensionRef) {
            VSDimensionRef ndim = (VSDimensionRef) nrefs[i];

            if(XSchema.isDateType(odim.getDataType()) &&
               Tool.equals(odim.getName(), ndim.getName()) &&
               !Tool.equals(odim.getFullName(), ndim.getFullName()))
            {
               removeDimDrill(drillInfo, ndim);
            }
         }
      }
   }

   private void removeDimDrill(DrillFilterInfo drillInfo, VSDimensionRef dim) {
      if(drillInfo == null || dim == null) {
         return;
      }

      Set<String> fields = drillInfo.getFields();

      if(fields.size() == 0) {
         return;
      }

      for(String field : fields) {
         ConditionList list = drillInfo.getDrillFilterConditionList(field);

         if(findConditionDim(list, dim)) {
            drillInfo.setDrillFilterConditionList(field, null);
         }
      }
   }

   private boolean findConditionDim(ConditionList list, VSDimensionRef dim) {
      for(int i = 0; i < list.getSize(); i++) {
         if(list.getConditionItem(i) == null) {
            continue;
         }

         DataRef ref = list.getConditionItem(i).getAttribute();

         if(XSchema.isDateType(ref.getDataType()) ||
            ref instanceof ColumnRef && ((ColumnRef) ref).getDataRef() instanceof DateRangeRef)
         {
            DateRangeRef date = (DateRangeRef) ((ColumnRef) ref).getDataRef();

            if(Tool.equals(date.getDataRef().getName(), dim.getName())) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check trap.
    */
   public void checkTrap(VSAssemblyInfo oinfo, VSAssemblyInfo ninfo,
      BindingModel oldModel, CommandDispatcher command, RuntimeViewsheet rvs)
   {
      VSModelTrapContext context = new VSModelTrapContext(rvs, true);
      boolean required = context.isCheckTrap();
      boolean contains = required &&
         context.checkTrap(oinfo, ninfo).showWarning();
      BindingModel model = contains ? oldModel : null;
      DataRef[] fields = required ? context.getGrayedFields() : new DataRef[0];
      DataRefModel[] fieldsModel = new DataRefModel[fields.length];

      for(int i = 0; i < fields.length; i++) {
         fieldsModel[i] = dataRefService.createDataRefModel(fields[i]);
      }

      VSBindingTrapCommand tcmd = new VSBindingTrapCommand(model, fieldsModel);
      command.sendCommand(tcmd);
   }

   public DataRefModel[] getGrayedOutFields(RuntimeViewsheet rvs) {
      return getGrayedOutFields(rvs, true);
   }

   public DataRefModel[] getGrayedOutFields(RuntimeViewsheet rvs, boolean initAgg) {
      VSModelTrapContext context = new VSModelTrapContext(rvs, initAgg);
      boolean required = context.isCheckTrap();

      if(!required || rvs.getViewsheet() == null) {
         return null;
      }

      VSAssemblyInfo info = rvs.getViewsheet().getVSAssemblyInfo();
      context.checkTrap(info, info);
      DataRef[] refs = context.getGrayedFields();
      DataRefModel[] refsModel = new DataRefModel[refs.length];

      for(int i = 0; i < refs.length; i++) {
         refsModel[i] = dataRefService.createDataRefModel(refs[i]);
      }

      return refsModel;
   }

   /**
    * Get grayed out fields for vs.
    */
   public void getGrayedOutFields(RuntimeViewsheet rvs, CommandDispatcher command) {
      SetGrayedOutFieldsCommand gcommand = new SetGrayedOutFieldsCommand(getGrayedOutFields(rvs));
      command.sendCommand(gcommand);
   }

   public void getCurrentGrayedOutFields(VSModelTrapContext context,
      CommandDispatcher command)
   {
      DataRef[] refs = context.getGrayedFields();
      DataRefModel[] refsModel = new DataRefModel[refs.length];

      for(int i = 0; i < refs.length; i++) {
         refsModel[i] = dataRefService.createDataRefModel(refs[i]);
      }

      SetGrayedOutFieldsCommand gcommand = new SetGrayedOutFieldsCommand(refsModel);
      command.sendCommand(gcommand);
   }

   public boolean handleSourceChanged(VSAssembly assembly, String newSource, String url,
                                      ViewsheetEvent event, CommandDispatcher dispatcher,
                                      ViewsheetSandbox box) throws Exception
   {
      if(!event.confirmed() && sourceChanged(newSource, assembly)) {
         MessageCommand command = new MessageCommand();
         command.setMessage(
            Catalog.getCatalog().getString("viewer.viewsheet.chart.sourceChanged"));
         command.setType(MessageCommand.Type.CONFIRM);
         command.addEvent(url, event);
         dispatcher.sendCommand(command);
         return true;
      }

      //If confirmed is true, source is changed, so clear old source.
      if(event.confirmed() || isSourceEmpty(assembly)) {
         int sourceType = SourceInfo.ASSET;

         if(event instanceof ConvertTableRefEvent) {
            sourceType = ((ConvertTableRefEvent) event).source().getType();
         }
         else if(event instanceof ConvertChartRefEvent) {
            sourceType = ((ConvertChartRefEvent) event).binding().getSource().getType();
         }

         changeSource(assembly, newSource, sourceType);
         validateBinding(assembly);

         // need to generate the base table assembly
         if(sourceType == XSourceInfo.VS_ASSEMBLY) {
            box.updateAssembly(assembly.getName());
         }
      }

      return false;
   }

   public boolean sourceChanged(String table, VSAssembly assembly) {
      if(assembly == null) {
         return false;
      }

      DataVSAssemblyInfo info = (DataVSAssemblyInfo) assembly.getInfo();

      if(info == null) {
         return false;
      }

      final SourceInfo sourceInfo = info.getSourceInfo();

      if(sourceInfo == null) {
         return false;
      }

      final int type = sourceInfo.getType();
      final String tableName = VSUtil.getTableName(table);

      if(type == SourceInfo.VS_ASSEMBLY) {
         final Viewsheet viewsheet = assembly.getViewsheet();
         final boolean embedded = viewsheet.isEmbedded();

         if(embedded) {
            final String vsName = viewsheet.getAbsoluteName();
            final String name = sourceInfo.getSource();

            return !tableName.replace(vsName + '.', "").equals(name);
         }
      }

      return !tableName.equals(sourceInfo.getSource());
   }

   private boolean isSourceEmpty(VSAssembly assembly) {
      DataVSAssemblyInfo info = (DataVSAssemblyInfo) assembly.getInfo();
      return info == null || info.getSourceInfo() == null;
   }

   /**
    * Add the new sourceinfo and check the old binding columns when source changed,
    * if cannot found the columns in the source, just remove them to avoid colum
    * not found exception.
    * @param assembly the assembly which need to handle source changed.
    * @param table    the new source table name.
    */
   public void changeSource(VSAssembly assembly, String table, int sourceType) {
      SourceInfo sinfo = new SourceInfo(sourceType, null, VSUtil.getTableName(table));
      VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();
      ((DataVSAssemblyInfo) info).setSourceInfo(sinfo);
   }

   public void validateBinding(VSAssembly assembly) {
      ColumnSelection availableCols = VSUtil.getBaseColumns(assembly, false);

      if(assembly instanceof TableVSAssembly) {
         validateTableColumns(availableCols, (TableVSAssembly) assembly);
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         validateCrosstabColumns(availableCols, (CrosstabVSAssembly) assembly);
      }
      else if(assembly instanceof CalcTableVSAssembly) {
         validateCalcTableColumns(availableCols, (CalcTableVSAssembly) assembly);
      }
      else if(assembly instanceof ChartVSAssembly) {
         validateChartColumns(availableCols, (ChartVSAssembly) assembly);
      }
   }

   /**
    * validate the cell bindings for calc table, following columsn will be removed.
    * 1. binding column or the dependent columns(expression base column, second column)
    *    which are not exist in the new source.
    * 2. sort columns which are not exist in the new source.
    */
   private void validateCalcTableColumns(ColumnSelection ncols, CalcTableVSAssembly table) {
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) table.getInfo();
      TableLayout layout = info.getTableLayout();

      if(layout == null) {
         return;
      }

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            CellBinding binding = layout.getCellBinding(r, c);

            if(binding == null) {
               continue;
            }

            String value = binding.getValue();

            if(value == null) {
               continue;
            }

            if(binding.getType() == TableCellBinding.BIND_COLUMN) {
               TableCellBinding cell = (TableCellBinding) binding;
               String formula = BindingTool.getFormulaString(cell.getFormula());

               if(formula != null && !"none".equalsIgnoreCase(formula) &&
                  value.startsWith(formula + "(") && value.endsWith(")"))
               {
                  value = value.substring(formula.length() + 1, value.length() - 1);
               }
               else {
                  value = VSLayoutTool.getOriginalColumn(value);
               }

               if(!isAvailableQueryField(value, ncols) &&
                  !isAvailableQueryField(binding.getValue(), ncols))
               {
                  layout.setCellBinding(r, c, null);
                  continue;
               }

               String second = BindingTool.getSecondFormula(cell.getFormula());

               if(!isAvailableQueryField(second, ncols)) {
                  layout.setCellBinding(r, c, null);
               }
            }
            else if(binding.getType() == TableCellBinding.BIND_FORMULA) {
               // this is called when source changed, for calc table, the old formula
               // may not work anymore. however, it's probably best to let the user
               // making the change instead of clearing them out, which could something
               // user created with a lot of effort, and could work or made to work with
               // some changes
               //layout.setCellBinding(r, c, null);
            }
         }
      }

      if(info.getSortInfo() == null) {
         return;
      }

      SortInfo sinfo = info.getSortInfo();
      int count = sinfo.getSortCount();

      for(int i = count - 1; i > -1; i--) {
         if(!isAvailableQueryField(sinfo.getSort(i).getDataRef(), ncols)) {
            sinfo.removeSort(i);
         }
      }
   }

   private void validateTableColumns(ColumnSelection ncols, TableVSAssembly table) {
      ColumnSelection cols = ((TableVSAssemblyInfo) table.getInfo()).getColumnSelection();

      for(int i = cols.getAttributeCount() - 1; i > -1; i--) {
         if(!isAvailableQueryField(cols.getAttribute(i), ncols)) {
            cols.removeAttribute(i);
         }
      }
   }

   private void validateCrosstabColumns(ColumnSelection ncols,
                                        CrosstabVSAssembly assembly)
   {
      VSCrosstabInfo info = assembly.getVSCrosstabInfo();
      info.setDesignRowHeaders(getValidCrosstabColumns(ncols, info.getRowHeaders()));
      info.setDesignColHeaders(getValidCrosstabColumns(ncols, info.getColHeaders()));
      info.setDesignAggregates(getValidCrosstabColumns(ncols, info.getAggregates()));
   }

   private DataRef[] getValidCrosstabColumns(ColumnSelection ncols, DataRef[] refs) {
      ArrayList<DataRef> list = new ArrayList<>();

      if(refs == null || refs.length == 0) {
         return new DataRef[0];
      }

      for(int i = refs.length - 1; i > -1; i--) {
         if(isAvailableQueryField(refs[i], ncols)) {
            list.add(0, refs[i]);
         }
      }

      return list.toArray(new DataRef[list.size()]);
   }

   private void validateChartColumns(ColumnSelection ncols, ChartVSAssembly assembly) {
      VSChartInfo info = assembly.getVSChartInfo();
      ChartRef[] xfields = info.getXFields();

      for(int i = xfields.length - 1; i > -1; i--) {
         if(!isAvailableQueryField(xfields[i], ncols)) {
            info.removeXField(i);
         }
      }

      ChartRef[] yfields = info.getYFields();

      for(int i = yfields.length - 1; i > -1; i--) {
         if(!isAvailableQueryField(yfields[i], ncols)) {
            info.removeYField(i);
         }
      }

      ChartRef[] gfields = info.getGroupFields();

      for(int i = gfields.length - 1; i > -1; i--) {
         if(!isAvailableQueryField(gfields[i], ncols)) {
            info.removeGroupField(i);
         }
      }

      if(!isAvailableQueryField(info.getPathField(), ncols)) {
         info.setPathField(null);
      }

      if(info instanceof VSMapInfo) {
         VSMapInfo minfo = (VSMapInfo) info;
         ChartRef[] geofields = minfo.getGeoFields();

         for(int i = geofields.length - 1; i > -1; i--) {
            if(!isAvailableQueryField(geofields[i], ncols)) {
               minfo.removeGeoField(i);
            }
         }
      }
      else if(info instanceof CandleVSChartInfo) {
         CandleVSChartInfo cinfo = (CandleVSChartInfo) info;

         if(!isAvailableQueryField(cinfo.getCloseField(), ncols)) {
            cinfo.setCloseField(null);
         }

         if(!isAvailableQueryField(cinfo.getOpenField(), ncols)) {
            cinfo.setOpenField(null);
         }

         if(!isAvailableQueryField(cinfo.getHighField(), ncols)) {
            cinfo.setHighField(null);
         }

         if(!isAvailableQueryField(cinfo.getLowField(), ncols)) {
            cinfo.setLowField(null);
         }
      }
      else if(info instanceof RelationChartInfo) {
         RelationVSChartInfo cinfo = (RelationVSChartInfo) info;

         if(!isAvailableQueryField(cinfo.getSourceField(), ncols)) {
            cinfo.setSourceField(null);
         }

         if(!isAvailableQueryField(cinfo.getTargetField(), ncols)) {
            cinfo.setTargetField(null);
         }
      }
      else if(info instanceof GanttChartInfo) {
         GanttChartInfo ginfo = (GanttChartInfo) info;

         if(!isAvailableQueryField(ginfo.getStartField(), ncols)) {
            ginfo.setStartField(null);
         }

         if(!isAvailableQueryField(ginfo.getEndField(), ncols)) {
            ginfo.setEndField(null);
         }

         if(!isAvailableQueryField(ginfo.getMilestoneField(), ncols)) {
            ginfo.setMilestoneField(null);
         }
      }

      validateAestheticFields(info, ncols);

      for(ChartRef[] xyrefs : new ChartRef[][] {info.getXFields(), info.getYFields()}) {
         for(ChartRef ref : xyrefs) {
            if(ref instanceof ChartAggregateRef) {
               validateAestheticFields((ChartAggregateRef) ref, ncols);
            }
         }
      }
   }

   private void validateAestheticFields(ChartBindable bindable, ColumnSelection ncols) {
      if(!isAvailableQueryField(bindable.getColorField(), ncols)) {
         bindable.setColorField(null);
      }

      if(!isAvailableQueryField(bindable.getShapeField(), ncols)) {
         bindable.setShapeField(null);
      }

      if(!isAvailableQueryField(bindable.getSizeField(), ncols)) {
         bindable.setSizeField(null);
      }

      if(!isAvailableQueryField(bindable.getTextField(), ncols)) {
         bindable.setTextField(null);
      }

      if(bindable instanceof RelationChartInfo) {
         RelationChartInfo info2 = (RelationChartInfo) bindable;

         if(!isAvailableQueryField(info2.getNodeColorField(), ncols)) {
            info2.setNodeColorField(null);
         }

         if(!isAvailableQueryField(info2.getNodeSizeField(), ncols)) {
            info2.setNodeSizeField(null);
         }
      }
   }

   private boolean isAvailableQueryField(DataRef ref, ColumnSelection ncols) {
      if(ref == null) {
         return true;
      }

      String name = ref.getAttribute();

      // the new created dimensionref and aggregateref have no inner dataref.
      // aestheticref's runtime dataRef has been clear, so should get dataref name.
      if(name == null || name.isEmpty()) {
         if(ref instanceof VSDimensionRef) {
            name = ((VSDimensionRef) ref).getGroupColumnValue();
         }
         else if(ref instanceof VSAggregateRef) {
            name = ((VSAggregateRef) ref).getColumnValue();
         }
         else if(ref instanceof VSAestheticRef) {
            name = ((VSAestheticRef) ref).getDataRef().getName();
         }
      }

      if(!isAvailableQueryField(name, ref.getEntity(), ncols)) {
         return false;
      }

      // validate second column
      if(ref instanceof VSAggregateRef) {
         VSAggregateRef aggr = (VSAggregateRef) ref;
         AggregateFormula formula = aggr.getFormula();
         DataRef secondaryColumn = aggr.getSecondaryColumn();
         String secondaryColumnValue = aggr.getSecondaryColumnValue();

         if(formula != null && formula.isTwoColumns() &&
            (!isAvailableQueryField(secondaryColumnValue,
               secondaryColumn == null ? null : secondaryColumn.getEntity(), ncols) ||
            !isAvailableQueryField(secondaryColumn, ncols)))
         {
            return false;
         }
      }

      // validate calculator field.
      if(ref instanceof ChartAggregateRef) {
         Calculator calc = ((ChartAggregateRef) ref).getCalculator();
         String colname = null;

         if(calc instanceof PercentCalc) {
            colname = ((PercentCalc) calc).getColumnName();
         }
         else if(calc instanceof ChangeCalc) {
            colname = ((ChangeCalc) calc).getColumnName();
         }

         if(!isAvailableQueryField(colname, ref.getEntity(), ncols)) {
            return false;
         }
      }

      return true;
   }

   private boolean isAvailableQueryField(String attribute, String entity, ColumnSelection ncols) {
      return attribute == null || ncols.getAttribute(attribute, entity) != null;
   }

   private boolean isAvailableQueryField(String name, ColumnSelection ncols) {
      return name == null || ncols.getAttribute(name) != null;
   }

   private void removeVariable(ViewsheetSandbox box, DataVSAssemblyInfo info,
      DataVSAssemblyInfo oinfo)
   {
      AssetQuerySandbox abox = box.getAssetQuerySandbox();
      VariableTable vart = abox.getVariableTable();

      if(oinfo.getPreConditionList() == null) {
         return;
      }

      VSUtil.removeVariable(vart, oinfo.getPreConditionList(), info.getPreConditionList());
   }

   /**
    * Fix the column width if it needs to be calculated from data. This
    * is only done if the binding of a table is changed on the viewer by
    * user. It ensures the new (numeric) data will be fully displayed
    * so user won't be misled if the number string is truncated on the
    * display. (Macquarie requirement).
    */
   private boolean fixColumnWidths(VSAssembly vsobj, ViewsheetSandbox box) {
      boolean changed = false;

      try {
         TableVSAssemblyInfo tinfo =
            (TableVSAssemblyInfo) vsobj.getVSAssemblyInfo();
         ColumnSelection cols = tinfo.getColumnSelection();
         VSTableLens tbl = box.getVSTableLens(tinfo.getAbsoluteName(), false);

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            double cw = tinfo.getColumnWidth2(i, tbl);

            // for auto-size (set in VSTableObj)
            if(cw < 0) {
               cw = -cw;

               ColumnRef col = (ColumnRef) cols.getAttribute(i);
               Font fn = tbl.getFont(0, i);
               FontMetrics fm = Common.getFontMetrics(fn);
               double pw = Common.stringWidth(Tool.toString(tbl.getObject(0, i)),
                                              fn, fm) + CELL_MARGIN;

               // only calculate column width for numeric values as per Macquarie
               if(XSchema.isNumericType(col.getDataType())) {
                  pw = Math.max(pw, getPreferredWidth(tbl, i));
               }

               if(pw > cw) {
                  cw = pw;
               }

               changed = true;
               tinfo.setColumnWidthValue2(i, cw, tbl);
            }
         }
      }
      catch(Exception e) {
         // ignored
      }

      return changed;
   }

   /**
    * Update the localize map when table column change.
    */
   private void updateLocalMap(Viewsheet vs, TableDataVSAssembly assembly) {
      ViewsheetInfo vinfo = vs.getViewsheetInfo();
      String[] children = getLocalizationComponents(assembly);
      String[] components = vinfo.getLocalComponents();

      for(String component : components) {
         String[] names = Tool.split(component, "^_^", false);

         if(!Tool.equals(names[0], assembly.getName())) {
            continue;
         }

         boolean find = false;

         for(String child : children) {
            if(Tool.equals(names[1], child)) {
               find = true;
               break;
            }
         }

         if(!find) {
            vinfo.setLocalID(component, null);
         }
      }
   }

   /**
    * Get the preferred column width for a column.
    */
   private double getPreferredWidth(VSTableLens tbl, int col) {
      Font fn = null;
      FontMetrics fm = null;
      double pw = 0;

      for(int r = 1; tbl.moreRows(r) && r < 1000; r++) {
         if(fn == null) {
            fn = tbl.getFont(r, col);
            fm = Common.getFontMetrics(fn);
         }

         double cw = Common.stringWidth(Tool.toString(tbl.getObject(r, col)),
                                        fn, fm);
         pw = Math.max(pw, cw);
      }

      // add margin on left and right
      return pw + CELL_MARGIN;
   }

   /**
    * Add fake aggregate to crosstab.
    */
   private void addFakeAggregate(VSCrosstabInfo info) {
      DataRef[] cols = info.getDesignColHeaders();
      DataRef[] rows = info.getDesignRowHeaders();

      if(isEmptyArray(cols) && isEmptyArray(rows)) {
         removeFakeAggregate(info);

         return;
      }

      DataRef[] aggs = info.getDesignAggregates();

      if(isEmptyArray(aggs)) {
         aggs = new DataRef[1];
         ExpressionRef exp = new ExpressionRef();
         exp.setOnAggregate(true);
         exp.setExpression("0");
         CalculateRef ref = new CalculateRef(false);
         ref.setDataRef(exp);
         ref.setFake(true);
         ref.setDataType(XSchema.DOUBLE);
         ref.setName("Aggregate");
         VSAggregateRef agg = new VSAggregateRef();
         agg.setDataRef(ref);
         aggs[0] = agg;
         info.setDesignAggregates(aggs);

         return;
      }

      if(aggs.length == 1 && VSUtil.isFake(aggs[0])) {
         return;
      }

      removeFakeAggregate(info);
   }

   private void validateTopN(VSCrosstabInfo info) {
      DataRef[] cols = info.getDesignColHeaders();
      DataRef[] rows = info.getDesignRowHeaders();
      DataRef[] aggregates = info.getDesignAggregates();
      validateTopN(cols, aggregates);
      validateTopN(rows, aggregates);
   }

   private void validateTopN(DataRef[] groups, DataRef[] aggregates) {
      for(DataRef group : groups) {
         VSDimensionRef dim = (VSDimensionRef) group;

         if(dim.getRankingOption() == XCondition.NONE) {
            continue;
         }

         String nval = dim.getRankingNValue();

         if(nval != null && !nval.startsWith("=") && !nval.startsWith("$")) {
            int n = -1;

            try {
               n = Integer.parseInt(nval);
            }
            catch(Exception ex) {
               // ignore
            }

            if(n <= 0) {
               dim.setRankingOptionValue(XCondition.NONE + "");
               dim.setRankingNValue("3");
            }

            if(aggregates != null) {
               String rankingColValue = dim.getRankingColValue();

               boolean exist = Arrays.stream(aggregates)
                  .anyMatch(agg -> {
                     if(!(agg instanceof VSAggregateRef)) {
                        return false;
                     }

                     VSAggregateRef aggregateRef = (VSAggregateRef) agg;
                     boolean calcSupportSort = aggregateRef.getCalculator() != null &&
                        aggregateRef.getCalculator().supportSortByValue();

                     return  aggregateRef.getFullName(calcSupportSort) != null
                        && aggregateRef.getFullName(calcSupportSort).equals(rankingColValue) ||
                        (aggregateRef.isVariable() &&
                        StringUtils.equals(aggregateRef.getFullNameByDVariable(), rankingColValue));
                  });

               if(!exist && aggregates.length > 0 && aggregates[0] instanceof VSAggregateRef) {
                  VSAggregateRef aggregateRef = (VSAggregateRef) aggregates[0];
                  boolean calcSupportSort = aggregateRef.getCalculator() != null &&
                     aggregateRef.getCalculator().supportSortByValue();
                  dim.setRankingCol(aggregateRef.getFullName(calcSupportSort));
               }
            }
         }
      }
   }

   /**
    * Remove fake aggregate from crosstab.
    */
   private void removeFakeAggregate(VSCrosstabInfo info) {
      DataRef[] aggs = info.getDesignAggregates();

      if(isEmptyArray(aggs)) {
         return;
      }

      Vector<DataRef> refs = new Vector<>();

      for(DataRef ref : aggs) {
         if(!VSUtil.isFake(ref)) {
            refs.add(ref);
         }
      }

      aggs = refs.toArray(new DataRef[refs.size()]);
      info.setDesignAggregates(aggs);
   }

   /**
    * Check whether the specified array is an null or empty array.
    */
   private boolean isEmptyArray(Object[] objs) {
      return objs == null || objs.length == 0;
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   public String getName() {
      return Catalog.getCatalog().getString("Apply Selection");
   }

   /**
    * Clean crosstable's highlight.
    */
   private void cleanHighlight(TableHighlightAttr highlight,
                               CrosstabVSAssemblyInfo cinfo)
   {
      VSCrosstabInfo vscrosstabinfo = cinfo.getVSCrosstabInfo();
      Enumeration highlightGroups = highlight.getAllHighlights();

      while(highlightGroups.hasMoreElements()) {
         HighlightGroup highlightGroup =
            (HighlightGroup) highlightGroups.nextElement();
         String[] levels = highlightGroup.getLevels();

         for(String level : levels) {
            String[] names = highlightGroup.getNames(level);

            for(String name : names) {
               Highlight texthighlight =
                  highlightGroup.getHighlight(level, name);
               ConditionList conditionlist = texthighlight.getConditionGroup();

               for(int d = 0; d < conditionlist.getSize(); d += 2) {
                  ConditionItem conditionItem =
                     conditionlist.getConditionItem(d);
                  DataRef dataRef = conditionItem.getAttribute();

                  if(!containsColumn(vscrosstabinfo, dataRef)) {
                    highlightGroup.removeHighlight(level, name);
                  }
               }
            }
         }
      }
   }

   /**
    * Return if the crossstab info contains the target field.
    */
   private boolean containsColumn(VSCrosstabInfo info, DataRef dataRef) {
      if(dataRef == null) {
         return false;
      }

      if(!XSchema.isDateType(dataRef.getDataType())) {
         return containsAggregate(info, dataRef) || containsRuntimeHeader(info, dataRef);
      }

      if(containsAggregate(info, dataRef)) {
         return true;
      }

      dataRef = DataRefWrapper.getBaseDataRef(dataRef);

      if(containsField(info.getRuntimeRowHeaders(), dataRef)) {
         return true;
      }

      if(containsField(info.getRuntimeColHeaders(), dataRef)) {
         return true;
      }

      return false;
   }

   /**
    * Check if contains the header data ref.
    * @param ref the specified data ref.
    * @return <tt>true</tt> if contains the data ref, <tt>false</tt> otherwise.
    */
   private static boolean containsRuntimeHeader(VSCrosstabInfo crosstab, DataRef ref) {
      DataRef[] cols2 = crosstab.getRuntimeColHeaders();
      DataRef[] rows2 = crosstab.getRuntimeRowHeaders();

      for(int i = 0; i < cols2.length; i++) {
         if(cols2[i].equals(ref) || VSUtil.isSameCol(ref.getName(), cols2[i])) {
            return true;
         }
      }

      for(int i = 0; i < rows2.length; i++) {
         if(rows2[i].equals(ref) || VSUtil.isSameCol(ref.getName(), rows2[i])) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if contains the aggregate data ref.
    * @param ref the specified data ref.
    * @return <tt>true</tt> if contains the data ref, <tt>false</tt> otherwise.
    */
   private static boolean containsAggregate(VSCrosstabInfo crosstab, DataRef ref) {
      DataRef[][] aggrs = {crosstab.getRuntimeAggregates(), crosstab.getAggregates()};

      if(ref == null) {
         return false;
      }

      for(DataRef[] aggrs2 : aggrs) {
         for(int i = 0; i < aggrs2.length; i++) {
            if(!(ref instanceof VSAggregateRef) && Tool.equals(aggrs2[i].getName(), ref.getName())) {
               return true;
            }

            if(aggrs2[i] instanceof VSDataRef &&
               ((VSDataRef) aggrs2[i]).getFullName().equals(ref.getName())) {
               return true;
            }

            if(aggrs2[i].equals(ref)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Return if the target dataref array contains the target field.
    */
   private boolean containsField(DataRef[] cols, DataRef ref) {
       for(DataRef col : cols) {
          col = DataRefWrapper.getBaseDataRef(col);

          if(col != null && col.equals(ref)) {
             return true;
          }
      }

      return false;
   }

   /**
    * Remove column from hide columns if it added to column selection.
    */
   private void processHideColumns(TableVSAssemblyInfo tinfo) {
      if(!tinfo.isForm()) {
         return;
      }

      ColumnSelection columns = tinfo.getColumnSelection();
      ColumnSelection hidden = tinfo.getHiddenColumns();
      changeColumns(hidden, columns);
   }

   /**
    * Modify column according to the user action. If user set a column to hidden
    * remove from column selection. If user add a hidden column to table, remove
    * it from hidden columns.
    */
   private void changeColumns(ColumnSelection rcols, ColumnSelection dcols) {
      List<DataRef> list = new ArrayList();

      for(int i = 0; i < dcols.getAttributeCount(); i++) {
         DataRef ref = dcols.getAttribute(i);

         if(!rcols.containsAttribute(ref)) {
            list.add(ref);
         }
      }

      dcols.removeAllAttributes();

      for(int i = 0; i < list.size(); i++) {
         dcols.addAttribute(list.get(i));
      }
   }

   /**
    * Get localization components for the given assembly
    */
   public String[] getLocalizationComponents(Assembly assembly) {
      String[] childNodes = null;
      int type = assembly.getAssemblyType();

      if(type == Viewsheet.CHART_ASSET) {
         childNodes = new String[]{"Title", "X Axis Title" , "Y Axis Title",
                                   "Secondary X Axis Title", "Secondary Y Axis Title",
                                   "Color Legend Axis Title", "Shape Legend Axis Title",
                                   "Size Legend Axis Title"};
      }
      else if(type == Viewsheet.CROSSTAB_ASSET) {
         CrosstabVSAssembly crossTab = (CrosstabVSAssembly) assembly;
         DataRef[] rows = null;
         DataRef[] cols = null;
         DataRef[] aggs = null;
         List<String> list = new ArrayList<>();
         list.add("Title");
         list.add("Grand Total");
         list.add("Group Total");

         if(crossTab.getVSCrosstabInfo() != null) {
            rows = crossTab.getVSCrosstabInfo().getRuntimeRowHeaders();
            cols = crossTab.getVSCrosstabInfo().getRuntimeColHeaders();
            aggs = crossTab.getVSCrosstabInfo().getRuntimeAggregates();
         }

         for(int j = 0; rows != null && j < rows.length; j++) {
            list.add(rows[j].getName());
         }

         if(aggs != null && (aggs.length > 1 ||
            cols == null || cols.length == 0 ||
            rows == null || rows.length == 0))
         {
            for(DataRef agg : aggs) {
               VSAggregateRef vref = (VSAggregateRef) agg;
               String field = agg.getName();

               if(vref.getCalculator() != null) {
                  field = vref.getFullName(field, null, vref.getCalculator());
               }

               list.add(field);
            }
         }

         childNodes = new String[list.size()];
         list.toArray(childNodes);
      }
      else if(type == Viewsheet.FORMULA_TABLE_ASSET) {
         TableLens lens = ((CalcTableVSAssembly) assembly).getBaseTable();
         TableDataDescriptor desc =
            lens != null ? lens.getDescriptor() : null;
         List<String> list = new ArrayList<>();

         for(int row = 0; desc != null && row < lens.getHeaderRowCount() && lens.moreRows(row);
             row++)
         {
            for(int col = 0; col < lens.getColCount(); col++) {
               TableDataPath path = desc.getCellDataPath(row, col);

               if(path.getType() == TableDataPath.HEADER || row == 0) {
                  String object = (String) lens.getObject(row, col);

                  if(object != null && !object.startsWith("=")) {
                     list.add(object);
                  }
               }
            }
         }

         list.add("Title");
         childNodes = new String[list.size()];
         list.toArray(childNodes);
      }
      else if(type == Viewsheet.CHECKBOX_ASSET ||
         type == Viewsheet.RADIOBUTTON_ASSET ||
         type == Viewsheet.TABLE_VIEW_ASSET)
      {
         String[] title = new String[]{"Title"};
         String[] columns = null;

         if(type == Viewsheet.TABLE_VIEW_ASSET) {
            ColumnSelection sel = ((TableVSAssembly) assembly).getColumnSelection();
            columns = sel != null ? sel.stream().map(DataRef::getName).toArray(String[]::new) : null;
         }

         childNodes = (String[]) Tool.mergeArray(title, columns);
      }
      else if(type == Viewsheet.CALENDAR_ASSET ||
         type == Viewsheet.SELECTION_TREE_ASSET ||
         type == Viewsheet.SELECTION_LIST_ASSET ||
         type == Viewsheet.CURRENTSELECTION_ASSET)
      {
         childNodes = new String[]{"Title"};
      }

      return childNodes;
   }

   /**
    * When an assembly has been moved, check if any lines point to it and make sure
    * the line endpoints are updated.
    */
   public void updateAnchoredLines(RuntimeViewsheet rvs, List<String> assemblies,
                                   CommandDispatcher dispatcher)
      throws Exception
   {
      Viewsheet viewsheet = rvs.getViewsheet();
      //get all the line assemblies in the viewsheet
      List<LineVSAssembly> lines = Arrays.stream(viewsheet.getAssemblies())
         .filter(LineVSAssembly.class::isInstance)
         .map(LineVSAssembly.class::cast)
         .collect(Collectors.toList());

      for(LineVSAssembly line: lines) {
         updateAnchoredLinePosition(line, viewsheet, rvs, assemblies, dispatcher);
      }
   }

   private void updateAnchoredLinePosition(LineVSAssembly line, Viewsheet viewsheet,
                                           RuntimeViewsheet rvs,
                                           List<String> assemblies,
                                           CommandDispatcher dispatcher)
      throws Exception
   {
      LineVSAssemblyInfo lineAssemblyInfo = (LineVSAssemblyInfo) line.getInfo();
      line.updateAnchor(viewsheet);

      //change endPos of the line
      if(assemblies.contains(lineAssemblyInfo.getEndAnchorID())) {
         Point currEndPos = line.getAnchorPos(viewsheet, lineAssemblyInfo,
                                              lineAssemblyInfo.getEndAnchorID(),
                                              lineAssemblyInfo.getEndAnchorPos());
         line.setEndPos(currEndPos);
      }

      // change startPos of the line
      if(assemblies.contains(lineAssemblyInfo.getStartAnchorID())) {
         Point currStartPos = line.getAnchorPos(viewsheet, lineAssemblyInfo,
                                                lineAssemblyInfo.getStartAnchorID(),
                                                lineAssemblyInfo.getStartAnchorPos());
         line.setStartPos(currStartPos);
      }

      coreLifecycleService.refreshVSAssembly(rvs, line, dispatcher);
   }

   private static final int CELL_MARGIN = 6;
   private final CoreLifecycleService coreLifecycleService;
   private final DataRefModelFactoryService dataRefService;
   private final ParameterService parameterService;
}
