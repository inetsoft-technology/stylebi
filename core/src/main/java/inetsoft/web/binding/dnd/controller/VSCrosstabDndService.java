/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.binding.dnd.controller;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.DataMap;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.graph.calc.*;
import inetsoft.report.internal.Util;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractModelTrapContext;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.dnd.BindingDropTarget;
import inetsoft.web.binding.dnd.TableTransfer;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.*;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import inetsoft.web.binding.model.SourceInfo;

import java.security.Principal;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Service
@ClusterProxy
public class VSCrosstabDndService {

   public VSCrosstabDndService(ViewsheetService viewsheetService,
                               VSAssemblyInfoHandler assemblyInfoHandler,
                               ConvertTableRefService convertTableRefService,
                               VSBindingTreeService bindingTreeService,
                               VSCrosstabBindingHandler crosstabHandler,
                               VSBindingService bfactory,
                               CoreLifecycleService coreLifecycleService) {
      this.viewsheetService = viewsheetService;
      this.assemblyInfoHandler = assemblyInfoHandler;
      this.convertTableRefService = convertTableRefService;
      this.bindingTreeService = bindingTreeService;
      this.crosstabHandler = crosstabHandler;
      this.bfactory = bfactory;
      this.coreLifecycleService = coreLifecycleService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void dnd(@ClusterProxyKey String runtimeId, VSDndEvent event, Principal principal,
                   String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal) ;

      if(rvs == null) {
         return null;
      }

      CrosstabVSAssembly assembly = (CrosstabVSAssembly) rvs.getViewsheet().getAssembly(event.name());
      CrosstabVSAssembly clone = assembly.clone();
      TableTransfer transfer = (TableTransfer)event.getTransfer();
      BindingDropTarget dropTarget = (BindingDropTarget)event.getDropTarget();
      inetsoft.uql.asset.SourceInfo sinfo = assembly.getSourceInfo();
      SourceInfo sourceInfo = new SourceInfo();
      sourceInfo.setType(sinfo.getType());
      sourceInfo.setSource(sinfo.getSource());
      String dragType = transfer.getDragType();
      String dropType = dropTarget.getDropType();
      DataRef ref = crosstabHandler.getDataRef(assembly, dragType, transfer.getDragIndex());
      int convertType = CrosstabConstants.AGGREGATE.equals(dropTarget.getDropType()) ?
         VSEventUtil.CONVERT_TO_MEASURE : VSEventUtil.CONVERT_TO_DIMENSION;

      crosstabHandler.addRemoveColumns(rvs, clone, transfer.getDragType(), transfer.getDragIndex(),
                                       dropTarget.getDropType(), dropTarget.getDropIndex(), dropTarget.getReplace(), dispatcher,
                                       linkUri);
      applyAssemblyInfo(rvs, assembly, (VSAssemblyInfo) clone.getInfo(), dispatcher, event, null,
                        linkUri, (VSCrosstabInfo crosstabInfo) -> {
            VSUtil.updateCalculate(crosstabInfo, dropTarget);
         }, ClearTableHeaderAliasHandler::processClearAliasFormat);

      assembly.getVSCrosstabInfo().updateRuntimeId();

      if(("rows".equals(dragType) || "cols".equals(dragType)) &&
         "aggregates".equals(dropType) ||
         ("rows".equals(dropType) || "cols".equals(dropType)) &&
            "aggregates".equals(dragType))
      {
         if(ref != null) {
            String[] refNames = new String[]{ ref.getName() };
            convertTableRefService.convertTableRef0(refNames, convertType, sourceInfo, false,
                                                    assembly.getName(), rvs, principal,
                                                    dispatcher, false);
         }
         else {
            LOG.error("Crosstab dnd failed: " + transfer.getDragIndex() + " of " + dragType +
                         ": " + assembly.getVSCrosstabInfo());
         }
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean checktrap(@ClusterProxyKey String vsId, VSDndEvent event,
                            Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = ViewsheetEngine.getViewsheetEngine().getViewsheet(vsId, principal);

      if(rvs == null) {
         return false;
      }

      CrosstabVSAssembly assembly = (CrosstabVSAssembly)getVSAssembly(rvs, event.name());
      CrosstabVSAssembly clone = assembly.clone();
      CrosstabVSAssembly old = assembly.clone();
      TableTransfer transfer = (TableTransfer) event.getTransfer();
      BindingDropTarget dropTarget = (BindingDropTarget) event.getDropTarget();
      inetsoft.uql.asset.SourceInfo sinfo = assembly.getSourceInfo();
      SourceInfo sourceInfo = new SourceInfo();
      sourceInfo.setType(sinfo.getType());
      sourceInfo.setSource(sinfo.getSource());
      String dragType = transfer.getDragType();
      String dropType = dropTarget.getDropType();

      // Only should check trap when rows/cols to aggregates. other cases will not cause trap.
      if(CrosstabConstants.AGGREGATE.equals(dragType) ||
         !CrosstabConstants.AGGREGATE.equals(dropType))
      {
         return false;
      }

      DataRef ref = crosstabHandler.getDataRef(assembly, dragType, transfer.getDragIndex());
      int convertType = CrosstabConstants.AGGREGATE.equals(dropType) ?
         VSEventUtil.CONVERT_TO_MEASURE : VSEventUtil.CONVERT_TO_DIMENSION;

      crosstabHandler.addRemoveColumns(rvs, clone, transfer.getDragType(), transfer.getDragIndex(),
                                       dropTarget.getDropType(), dropTarget.getDropIndex(), dropTarget.getReplace(), null,
                                       null);
      clearRuntimeInfo(old);
      clearRuntimeInfo(clone);

      rvs.getViewsheet().getAssembly(event.name()).setVSAssemblyInfo(clone.getVSAssemblyInfo());
      rvs.getViewsheetSandbox().updateAssembly(event.name());

      if(("rows".equals(dragType) || "cols".equals(dragType)) &&
         "aggregates".equals(dropType) ||
         ("rows".equals(dropType) || "cols".equals(dropType)) &&
            "aggregates".equals(dragType))
      {
         if(ref != null) {
            String[] refNames = new String[]{ ref.getName() };
            convertTableRefService.convertTableRef0(refNames, convertType, sourceInfo, false,
                                                    assembly.getName(), rvs, null,
                                                    null, false);
         }
         else {
            LOG.error("Crosstab dnd failed: " + transfer.getDragIndex() + " of " + dragType +
                         ": " + assembly.getVSCrosstabInfo());
         }
      }

      AbstractModelTrapContext.TrapInfo trap = new VSModelTrapContext(rvs).checkTrap(old.getVSAssemblyInfo(), clone.getVSAssemblyInfo());
      rvs.getViewsheet().getAssembly(event.name()).setVSAssemblyInfo(old.getVSAssemblyInfo());

      return trap.showWarning();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void dndFromTree(@ClusterProxyKey String runtimeId, VSDndEvent event, Principal principal,
                           String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         return null;
      }

      CrosstabVSAssembly assembly = (CrosstabVSAssembly) getVSAssembly(rvs, event.name());
      CrosstabVSAssembly nassembly = assembly.clone();

      if(getColumnCount(assembly) + event.getEntries().length > Util.getOrganizationMaxColumn()) {
         MessageCommand command = new MessageCommand();
         command.setMessage(Util.getColumnLimitMessage());
         command.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(command);

         return null;
      }

      BindingDropTarget dropTarget = (BindingDropTarget) event.getDropTarget();
      String dropType = dropTarget.getDropType();
      List<String> needConvertRefNames = crosstabHandler.getNeedConvertRefNames(assembly,
                                                                                event.getEntries(), dropType, rvs);
      AssetEntry[] entries = event.getEntries();
      inetsoft.uql.asset.SourceInfo sinfo = ((DataVSAssemblyInfo) assembly.getInfo()).getSourceInfo();
      boolean sourceChange = sinfo == null || assemblyInfoHandler.sourceChanged(event.getTable(), assembly);

      if(needConvertRefNames.size() > 0) {
         SourceInfo sourceInfo = new SourceInfo();
         sourceInfo.setType(event.getSourceType());
         sourceInfo.setSource(event.getTable());
         int convertType = "aggregates".equals(dropType) ?
            VSEventUtil.CONVERT_TO_MEASURE : VSEventUtil.CONVERT_TO_DIMENSION;
         convertTableRefService.convertTableRef(needConvertRefNames.toArray(new String[0]),
                                                convertType, sourceInfo, sourceChange, assembly.getName(),
                                                rvs, principal, dispatcher);
         TreeNodeModel model =
            bindingTreeService.getBinding(rvs.getID(), assembly.getName(), false, principal);
         List<AssetEntry> convertedEntries = new ArrayList<>();
         List<String> convertedPaths = crosstabHandler.getConvertedEntriesPath(event.getEntries(),
                                                                               dropType);
         getConvertedEntries(model, convertedPaths, convertedEntries);
         entries = convertedEntries.toArray(new AssetEntry[0]);
         nassembly = assembly.clone();
      }

      // Handle source changed.
      if(sourceChange) {
         assemblyInfoHandler.changeSource(nassembly, event.getTable(), event.getSourceType());
         VSCrosstabInfo vsCrosstabInfo = nassembly.getVSCrosstabInfo();

         if(vsCrosstabInfo != null) {
            AggregateInfo ainfo =  vsCrosstabInfo.getAggregateInfo();

            if(ainfo != null) {
               List<DataRef> calcFields = ainfo.getFormulaFields();
               Set<String> calcFieldsRefs = ainfo.removeFormulaFields(calcFields);
               vsCrosstabInfo.removeFormulaFields(calcFieldsRefs);
            }
         }
      }

      crosstabHandler.addColumns(nassembly, entries, dropType,
                                 dropTarget.getDropIndex(), dropTarget.getReplace(), rvs, dispatcher, linkUri);

      if("rows".equals(dropType)) {
         ClearTableHeaderAliasHandler.clearHeaderAliasByIndex(
            assembly.getFormatInfo().getFormatMap(), dropTarget.getDropIndex());
         ClearTableHeaderAliasHandler.clearHeaderAliasByIndex(
            nassembly.getFormatInfo().getFormatMap(), dropTarget.getDropIndex());
      }

      applyAssemblyInfo(rvs, assembly, nassembly, dispatcher, event,
                        "/events/vscrosstab/dnd/addColumns", linkUri, (VSCrosstabInfo crosstabInfo) -> {
            VSUtil.updateCalculate(crosstabInfo, dropTarget);
         }, ClearTableHeaderAliasHandler::processClearAliasFormat);

      assembly.getVSCrosstabInfo().updateRuntimeId();

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void dndTotree(@ClusterProxyKey String runtimeId, VSDndEvent event, Principal principal,
                         String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         return null;
      }

      CrosstabVSAssembly assembly = (CrosstabVSAssembly)getVSAssembly(rvs, event.name());
      CrosstabVSAssembly clone = assembly.clone();
      TableTransfer transfer = (TableTransfer)event.getTransfer();
      DataRef removeRef = crosstabHandler.removeColumns(rvs, clone, transfer.getDragType(), transfer.getDragIndex(),
                                                        dispatcher, linkUri);
      applyAssemblyInfo(rvs, assembly, (VSAssemblyInfo) clone.getInfo(), dispatcher,
                        event, null, linkUri, (VSCrosstabInfo crosstabInfo) -> {
            DataRef[] cols = crosstabInfo.getDesignColHeaders();
            DataRef[] rows = crosstabInfo.getDesignRowHeaders();
            DataRef[] aggRefs = crosstabInfo.getDesignAggregates();

            Arrays.stream(aggRefs).forEach((DataRef ref) -> {
               VSAggregateRef aggregateRef = (VSAggregateRef) ref;
               Calculator calculator = aggregateRef.getCalculator();
               String removeName = ((VSDataRef) removeRef).getFullName();
               updateCalculateInfo(rows.length != 0,
                                   cols.length != 0, calculator, removeName);

               if(calculator instanceof PercentCalc) {
                  crosstabInfo.setPercentageByValue(((PercentCalc) calculator).getPercentageByValue());
               }
            });
         }, ClearTableHeaderAliasHandler::processClearAliasFormat);

      assembly.getVSCrosstabInfo().updateRuntimeId();

      return null;
   }

   /**
    * update calculator when remove field from crosstab headers.
    * @param hasRow         if crosstab has row header.
    * @param hasCol         if crosstab has col header.
    * @param calc           target calcuator need to refresh.
    * @param removedColumnName the removed column fullname of the crosstab.
    */
   private static void updateCalculateInfo(boolean hasRow, boolean hasCol, Calculator calc,
                                           String removedColumnName)
   {
      if(calc instanceof ValueOfCalc) {
         ValueOfCalc valueOfCalc = (ValueOfCalc) calc;
         valueOfCalc.setColumnName(getNewCalcColumnName(valueOfCalc.getColumnName(),
                                                        removedColumnName, hasRow, hasCol));
      }
      else if(calc instanceof RunningTotalCalc) {
         RunningTotalCalc rcalc = (RunningTotalCalc) calc;
         rcalc.setBreakBy(getNewCalcColumnName(rcalc.getColumnName(),
                                               removedColumnName, hasRow, hasCol));
      }
      else if(calc instanceof MovingCalc) {
         MovingCalc rcalc = (MovingCalc) calc;
         rcalc.setInnerDim(getNewCalcColumnName(rcalc.getColumnName(),
                                                removedColumnName, hasRow, hasCol));
      }
      else if(calc instanceof PercentCalc) {
         PercentCalc percentCalc = (PercentCalc) calc;
         String percentageBy = percentCalc.getPercentageByValue();

         if(Tool.isEmptyString(percentageBy)) {
            return;
         }

         if(XConstants.PERCENTAGE_BY_ROW == Integer.parseInt(percentageBy) && !hasRow && hasCol) {
            percentageBy = XConstants.PERCENTAGE_BY_COL + "";
         }
         else if(XConstants.PERCENTAGE_BY_COL == Integer.parseInt(percentageBy) && !hasCol && hasRow) {
            percentageBy = XConstants.PERCENTAGE_BY_ROW + "";
         }

         percentCalc.setPercentageByValue(percentageBy);
      }
   }

   /**
    * Get new column name for valueofcalc, movingcalc, runningtotalcal when remove crosstab headers.
    * @param columnName             the columname of the calc.
    * @param removedColumnName      the removed column fullname of the crosstab.
    * @param hasRow                 if crosstab has row header.
    * @param hasCol                 if crosstab has col header.
    */
   private static String getNewCalcColumnName(String columnName, String removedColumnName,
                                              boolean hasRow, boolean hasCol)
   {
      String newColumnName = columnName;

      if(StringUtils.equals(removedColumnName, columnName)) {
         newColumnName = hasRow ? AbstractCalc.ROW_INNER
            : hasCol ? AbstractCalc.COLUMN_INNER : columnName;
      }
      else if(Objects.equals(columnName, AbstractCalc.ROW_INNER) && !hasRow && hasCol) {
         newColumnName = AbstractCalc.COLUMN_INNER;
      }
      else if(Objects.equals(columnName, AbstractCalc.COLUMN_INNER) && !hasCol && hasRow) {
         newColumnName = AbstractCalc.ROW_INNER;
      }

      return newColumnName;
   }


   protected VSAssembly getVSAssembly(RuntimeViewsheet rvs, String name) {
      Viewsheet viewsheet = rvs.getViewsheet();
      return viewsheet.getAssembly(name);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly oassembly,
                                    VSAssembly nassembly, CommandDispatcher dispatcher,
                                    VSDndEvent event, String url, String linkUri,
                                    Consumer<VSCrosstabInfo> updateCalculate,
                                    BiConsumer<Map<TableDataPath, VSCompositeFormat>, TableDataPath> clearAliasFormatProcessor)
      throws Exception
   {
      // validate current binding when source changed.
      if(oassembly instanceof DataVSAssembly) {
         inetsoft.uql.asset.SourceInfo osource = ((DataVSAssemblyInfo) oassembly.getInfo()).getSourceInfo();
         inetsoft.uql.asset.SourceInfo nsource = ((DataVSAssemblyInfo) nassembly.getInfo()).getSourceInfo();

         if(osource != null && osource.getSource() != null && nsource != null) {
            if(!osource.getSource().equals(nsource.getSource()) &&
               nsource.getType() == inetsoft.uql.asset.SourceInfo.VS_ASSEMBLY)
            {
               VSAQuery query = VSAQuery.createVSAQuery(rvs.getViewsheetSandbox(),
                                                        nassembly, DataMap.DETAIL);
               query.createAssemblyTable(nassembly.getTableName());
            }

            if(!osource.getSource().equals(nsource.getSource()) ||
               VSUtil.isVSAssemblyBinding(event.getTable()))
            {
               assemblyInfoHandler.validateBinding(nassembly);
            }
         }
      }

      applyAssemblyInfo(rvs, oassembly, (VSAssemblyInfo) nassembly.getInfo(), dispatcher,
         event, url, linkUri, updateCalculate, clearAliasFormatProcessor);
   }

   protected void applyAssemblyInfo(RuntimeViewsheet rvs, VSAssembly assembly,
      VSAssemblyInfo clone, CommandDispatcher dispatcher, VSDndEvent event,
      String url, String linkUri, Consumer<VSCrosstabInfo> updateCalculate,
      BiConsumer<Map<TableDataPath, VSCompositeFormat>, TableDataPath> clearAliasFormatProcessor)
      throws Exception
   {
      assemblyInfoHandler.apply(rvs, clone, viewsheetService, event.confirmed(),
         event.checkTrap(), false, false, dispatcher, url,
         event, linkUri, updateCalculate, clearAliasFormatProcessor);

      try {
         final BindingModel binding = bfactory.createModel(assembly);
         final SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);
         coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);
      }
      catch(ConfirmException ex) {
         if(!coreLifecycleService.waitForMV(ex, rvs, dispatcher)) {
            throw ex;
         }
      }
   }

   private void clearRuntimeInfo(CrosstabVSAssembly crosstab) {
      crosstab.getVSCrosstabInfo().setRuntimeRowHeaders(null);
      crosstab.getVSCrosstabInfo().setRuntimeColHeaders(null);
      crosstab.getVSCrosstabInfo().setRuntimeAggregates(null);
   }

   private int getColumnCount(CrosstabVSAssembly cross) {
      VSCrosstabInfo cinfo = cross.getVSCrosstabInfo();

      if(cinfo == null) {
         return 0;
      }

      int count = 0;
      count += cinfo.getRowHeaders() == null ? 0 : cinfo.getRowHeaders().length;
      count += cinfo.getColHeaders() == null ? 0 : cinfo.getColHeaders().length;

      if(cinfo.getAggregates() != null) {
         for(int i = 0; i < cinfo.getAggregates().length; i++) {
            if(!VSUtil.isFake(cinfo.getAggregates()[i])) {
               count++;
            }
         }
      }

      return count;
   }

   private void getConvertedEntries(TreeNodeModel root, List<String> convertedPaths,
                                    List<AssetEntry> convertedEntries) {
      List<TreeNodeModel> children = root.children();

      if(children == null) {
         return;
      }

      children.stream().forEach(child -> {
         Object data = child.data();

         if(data instanceof AssetEntry && convertedPaths.contains(((AssetEntry) data).getPath())) {
            convertedEntries.add((AssetEntry) data);
         }

         getConvertedEntries(child, convertedPaths, convertedEntries);
      });
   }

   private final ViewsheetService viewsheetService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
   private final ConvertTableRefService convertTableRefService;
   private final VSBindingTreeService bindingTreeService;
   private VSCrosstabBindingHandler crosstabHandler;
   private final VSBindingService bfactory;
   private final CoreLifecycleService coreLifecycleService;

   private static final Logger LOG = LoggerFactory.getLogger(VSCrosstabDndService.class);

}
