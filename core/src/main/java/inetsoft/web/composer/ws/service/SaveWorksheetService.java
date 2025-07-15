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
package inetsoft.web.composer.ws.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.mv.MVManager;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.CompositeTableAssemblyInfo;
import inetsoft.uql.asset.internal.TableAssemblyInfo;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.composer.model.ws.SaveWSConfirmationModel;
import inetsoft.web.composer.vs.command.ReopenSheetCommand;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.event.SaveSheetEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.SaveSheetCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.Enumeration;
import java.util.List;

@Service
@ClusterProxy
public class SaveWorksheetService extends WorksheetControllerService {
   @Autowired
   public SaveWorksheetService(AssetRepository assetRepository,
                               ViewsheetService viewsheetService) {
      super(viewsheetService);
      this.assetRepository = assetRepository;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean saveWorksheet(@ClusterProxyKey String runtimeId, SaveSheetEvent event,
                                Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(runtimeId, principal);

      if(!process(rws, event, true, principal, commandDispatcher)) {
         return false;
      }

      rws.setSavePoint(rws.getCurrent());

      if(!event.isClose()) {
         SaveSheetCommand command = SaveSheetCommand.builder()
            .savePoint(rws.getSavePoint())
            .id(rws.getEntry().toIdentifier())
            .build();
         commandDispatcher.sendCommand(command);
      }

      RenameDependencyInfo dinfo = DependencyTransformer.createRenameInfo(rws);

      if(!dinfo.getDependencyMap().isEmpty()) {
         RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
      }

      initWorksheetOldName(rws);
      syncNameGroupInfo(rws.getWorksheet(), principal);

      return true;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public SaveWSConfirmationModel checkPrimaryAssembly(@ClusterProxyKey String runtimeId,
                                                       SaveSheetEvent event, Principal principal) throws Exception
   {
      RuntimeWorksheet rws = getWorksheetEngine().getWorksheet(runtimeId, principal);

      try {
         if(!process(rws, event, false, principal, null)) {
            return SaveWSConfirmationModel.builder()
               .required(false)
               .confirmationMsg("")
               .build();
         }
      }
      catch(DependencyException dex) {
         return SaveWSConfirmationModel.builder()
            .required(true)
            .confirmationMsg(dex.getMessage())
            .build();
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean checkDependChanged(@ClusterProxyKey String rid, Principal principal) {
      WorksheetService worksheetService = getWorksheetEngine();

      return worksheetService != null && worksheetService.needRenameDep(rid);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean checkCycle(@ClusterProxyKey String rid, Principal principal)
      throws Exception
   {
      rid = Tool.byteDecode(rid);
      WorksheetService engine = getWorksheetEngine();
      RuntimeWorksheet rws = engine.getWorksheet(rid, principal);

      return checkJoinCycle(rws);
   }


   private void syncNameGroupInfo(Worksheet ws, Principal principal) throws Exception {
      AssetEntry[] outerDependencies = ws.getOuterDependencies(true);

      if(outerDependencies == null || outerDependencies.length == 0) {
         return;
      }

      for(AssetEntry outerDependency : outerDependencies) {
         AbstractSheet sheet =
            assetRepository.getSheet(outerDependency, principal, false, AssetContent.ALL);

         if(sheet instanceof Viewsheet) {
            Viewsheet viewsheet = (Viewsheet) sheet;
            Assembly[] assemblies = viewsheet.getAssemblies();
            boolean change = false;

            for(Assembly assembly : assemblies) {
               if(!(assembly instanceof CalcTableVSAssembly)) {
                  continue;
               }

               change = syncCalcTableNamedGroup((CalcTableVSAssembly) assembly, change);
            }

            if(change) {
               assetRepository.setSheet(outerDependency, sheet, principal, false);
            }
         }
      }
   }

   private boolean syncCalcTableNamedGroup(CalcTableVSAssembly assembly, boolean change) {
      CalcTableVSAssemblyInfo calcAssemblyInfo = (CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo();
      TableLayout tableLayout = calcAssemblyInfo.getTableLayout();
      List<CellBindingInfo> cellInfos = tableLayout.getCellInfos(true);

      if(cellInfos == null || cellInfos.size() == 0) {
         return change;
      }

      for(CellBindingInfo cellInfo : cellInfos) {
         if(cellInfo == null) {
            continue;
         }

         TableCellBinding cellBinding = ((TableLayout.TableCellBindingInfo) cellInfo).getCellBinding();

         if(cellBinding == null || cellBinding.getOrderInfo(false) == null) {
            continue;
         }

         OrderInfo orderInfo = cellBinding.getOrderInfo(false);
         XNamedGroupInfo namedGroupInfo = orderInfo.getNamedGroupInfo();

         if(namedGroupInfo != null && namedGroupInfo instanceof AssetNamedGroupInfo) {
            AssetNamedGroupInfo oldInfo = (AssetNamedGroupInfo) namedGroupInfo;
            AssetEntry entry = oldInfo.getEntry();
            NamedGroupAssembly namedGroupAssembly = LayoutTool.getNamedGroupAssembly(entry);

            if(namedGroupAssembly != null) {
               AssetNamedGroupInfo newInfo = new AssetNamedGroupInfo(entry, namedGroupAssembly);

               if(oldInfo != null && !oldInfo.equalsContent(newInfo)) {
                  orderInfo.setNamedGroupInfo(newInfo);
                  change = true;
               }
            }
         }
      }

      return change;
   }

   /**
    * Process save worksheet event.
    * From {@link SaveSheetEvent}
    */
   private boolean process(
      RuntimeWorksheet rws, SaveSheetEvent event, boolean isSave,
      Principal user, CommandDispatcher commandDispatcher)
      throws Exception
   {
      AssetEntry entry = rws.getEntry();
      String name = entry.getPath();
      boolean success;

      WorksheetService worksheetService = getWorksheetEngine();

      if(isSave && worksheetService.needRenameDep(rws.getID()) && !event.isUpdateDepend()){
         worksheetService.rollbackRenameDep(rws.getID());
      }

      success = process0(rws, event, isSave, user, commandDispatcher);

      if(success && worksheetService.needRenameDep(rws.getID()) &&
         event.isUpdateDepend())
      {
         worksheetService.renameDep(rws.getID());
         // After update depenency in asset data, should reload current vs to get latest data.
         // Send a command to reopen the vs.
         commandDispatcher.sendCommand(new ReopenSheetCommand(entry.toIdentifier()));
      }
      else if(isSave) {
         worksheetService.clearRenameDep(rws.getID());
      }

      return success;
   }

   private boolean process0(RuntimeWorksheet rws, SaveSheetEvent event, boolean isSave,
                            Principal user, CommandDispatcher dispatcher)
      throws Exception
   {
      if(rws.isDisposed()) {
         return false;
      }

      if(event.confirmed()) {
         rws.setProperty("mvconfirmed", "true");
      }

      catalog = Catalog.getCatalog(user);
      AssetEntry entry = rws.getEntry();

      // log save worksheet action
      String objectType = AssetEventUtil.getObjectType(entry);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(user), null, null, objectType,
                                                   actionTimestamp,
                                                   ActionRecord.ACTION_STATUS_FAILURE, null);

      try {
         if(actionRecord != null) {
            actionRecord.setActionName(ActionRecord.ACTION_NAME_EDIT);
            actionRecord.setObjectName(entry.getDescription());
         }

         String mvmsg = checkMVMessage(rws, entry);

         if(mvmsg != null && mvmsg.length() > 0) {
            if(dispatcher != null) {
               MessageCommand msgCmd = new MessageCommand();
               msgCmd.setMessage(mvmsg);
               msgCmd.setType(MessageCommand.Type.CONFIRM);
               String url = event.isClose() ? "/events/composer/worksheet/save-and-close" :
                  "/events/composer/worksheet/save";
               msgCmd.addEvent(url, event);
               dispatcher.sendCommand(msgCmd);
            }

            return false;
         }

         String alias = entry.getAlias();
         entry.setAlias(alias);
         rws.getWorksheet().setLastModified(System.currentTimeMillis());
         getWorksheetEngine().setWorksheet(rws.getWorksheet(), entry, user, event.isForceSave(),
                                           !event.isUpdateDepend());
         rws.getWorksheet().fireEvent(AbstractSheet.SHEET_SAVED, null);

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         }

         // @by ankitmathur Fix bug1415292205565 If saving the Worksheet was
         // successful, check and remove any auto-saved versions of the
         // Worksheet. If this action was a "save as", we should also delete
         // the auto-saved version of the original Worksheet (if one exists).
         VSEventUtil.deleteAutoSavedFile(entry, user);
      }
      catch(Exception ex) {
         if(ex instanceof ConfirmException) {
            actionRecord = null;
         }

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(ex.getMessage());
         }

         throw ex;
      }
      finally {
         if(actionRecord != null && isSave) {
            Audit.getInstance().auditAction(actionRecord, user);
         }
      }

      return true;
   }

   private String checkMVMessage(RuntimeWorksheet rws, AssetEntry entry) {
      if("true".equals(rws.getProperty("mvconfirmed"))) {
         return "";
      }

      String mvmsg = "";
      String[] wpaths = MVManager.getManager().findSheets(entry, true);

      if(wpaths.length > 0) {
         mvmsg += catalog.getString("Worksheets") + ":\n";
      }

      for(int i = 0; i < wpaths.length; i++) {
         mvmsg += (i > 0 ? ",\n" : "") + "   " + wpaths[i];
      }

      String[] vpaths = MVManager.getManager().findSheets(entry, false);

      if(vpaths.length > 0) {
         if(wpaths.length > 0) {
            mvmsg += "\n\n";
         }

         mvmsg += catalog.getString("Viewsheets") + ":\n";
      }

      for(int i = 0; i < vpaths.length; i++) {
         mvmsg += (i > 0 ? ",\n" : "") + "   " + vpaths[i];
      }

      if(mvmsg.isEmpty()) {
         return "";
      }

      return catalog.getString("mv.dependmessage", mvmsg);
   }


   public static void initWorksheetOldName(RuntimeWorksheet rws) {
      Worksheet worksheet = rws.getWorksheet();

      if(worksheet != null) {
         for(Assembly obj : worksheet.getAssemblies()) {
            if(obj instanceof AbstractWSAssembly) {
               ((AbstractWSAssembly) obj).setOldName(obj.getName());

               if(obj instanceof AbstractTableAssembly) {
                  DependencyTransformer.initTableColumnOldNames((AbstractTableAssembly) obj, false);
               }
            }
         }
      }
   }

   private boolean checkJoinCycle(RuntimeWorksheet rws) {
      Graph graph = new Graph(true);

      Assembly[] assemblies = rws.getWorksheet().getAssemblies();

      if(assemblies == null) {
         return false;
      }

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof TableAssembly)) {
            continue;
         }

         TableAssemblyInfo tableInfo = ((TableAssembly) assembly).getTableInfo();

         if(tableInfo instanceof CompositeTableAssemblyInfo) {
            CompositeTableAssemblyInfo compositeInfo = (CompositeTableAssemblyInfo) tableInfo;
            Enumeration<TableAssemblyOperator> tableOperators = compositeInfo.getOperators();

            while(tableOperators.hasMoreElements()) {
               TableAssemblyOperator assemblyOperator = tableOperators.nextElement();
               TableAssemblyOperator.Operator[] operators = assemblyOperator.getOperators();

               if(operators == null) {
                  continue;
               }

               for(TableAssemblyOperator.Operator operator : operators) {
                  if(operator == null || !operator.isJoin() ||
                     Tool.isEmptyString(operator.getLeftTable()) ||
                     Tool.isEmptyString(operator.getRightTable()))
                  {
                     continue;
                  }

                  graph.addEdge(operator.getLeftTable(), operator.getRightTable(), 1, false);
               }
            }
         }
      }

      Object[] cycle = graph.findCycle(Graph.WEAK_WEIGHT);

      if(cycle != null && cycle.length > 2) {
         return true;
      }

      return false;
   }



   private AssetRepository assetRepository;
   private Catalog catalog;
}
