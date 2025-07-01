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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.ExecutionRecord;
import inetsoft.util.log.LogUtil;
import inetsoft.web.binding.handler.VSChartDataHandler;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.embed.EmbedAssemblyInfo;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.controller.table.BaseTableController;
import inetsoft.web.viewsheet.event.RefreshVSAssemblyEvent;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Controller
public class VSRefreshController {
   /**
    * Creates a new instance of <tt>VSRefreshController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime vs associated with the websocket
    * @param coreLifecycleService  service containing vs util functions
    * @param viewsheetService    viewsheet service instance
    * @param vsObjectTreeService service for creating the vs object tree
    */
   @Autowired
   public VSRefreshController(RuntimeViewsheetRef runtimeViewsheetRef,
                              CoreLifecycleService coreLifecycleService,
                              ViewsheetService viewsheetService,
                              VSObjectTreeService vsObjectTreeService,
                              VSBookmarkService vsBookmarkService,
                              VSChartDataHandler chartDataHandler,
                              ParameterService parameterService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
      this.vsObjectTreeService = vsObjectTreeService;
      this.vsBookmarkService = vsBookmarkService;
      this.chartDataHandler = chartDataHandler;
      this.parameterService = parameterService;
   }

   /**
    * Refresh a viewsheet
    */
   @LoadingMask
   @MessageMapping("/vs/refresh")
   public void refreshViewsheet(@Payload VSRefreshEvent event, Principal principal,
                                CommandDispatcher commandDispatcher,
                                @LinkUri String linkUri) throws Exception
   {
      String id = this.runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      String bookmarkName = event.bookmarkName();
      IdentityID bookmarkUser = event.bookmarkUser();
      boolean initing = event.initing();
      boolean checkShareFilter = event.checkShareFilter();
      boolean tableMetaData = event.tableMetaData();
      boolean confirmed = event.confirmed();
      boolean userRefresh = event.userRefresh();
      boolean resizing = event.resizing();
      int width = event.width();
      int height = event.height();
      VariableTable variables = null;
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(event.parameters() != null) {
         variables = parameterService.readParameters(event.parameters());
      }

      coreLifecycleService.setExportType(rvs, commandDispatcher);

      // don't pile up refresh
      if(pending.containsKey(id)) {
         return;
      }

      box.lockWrite();
      pending.put(id, true);

      // reset embed assembly size on refresh
      if(event.embedAssemblySize() != null) {
         EmbedAssemblyInfo embedAssemblyInfo = rvs.getEmbedAssemblyInfo();

         if(embedAssemblyInfo != null) {
            embedAssemblyInfo.setAssemblySize(event.embedAssemblySize());
         }
      }

      AssetEntry entry = rvs.getEntry();
      String userSessionId = principal == null ?
         XSessionService.createSessionID(XSessionService.USER, null) :
         ((XPrincipal) principal).getSessionID();
      String objectName = entry != null ? entry.getDescription() : null;
      LogUtil.PerformanceLogEntry logEntry = new LogUtil.PerformanceLogEntry(objectName);
      String execSessionId =
         XSessionService.createSessionID(XSessionService.EXPORE_VIEW,
            entry != null ? entry.getName() : null);
      String objectType = ExecutionRecord.OBJECT_TYPE_VIEW;
      String execType = ExecutionRecord.EXEC_TYPE_START;
      Date execTimestamp = new Date(System.currentTimeMillis());
      logEntry.setStartTime(execTimestamp.getTime());

      ExecutionRecord executionRecord = new ExecutionRecord(
         execSessionId, userSessionId, objectName, objectType, execType, execTimestamp,
         ExecutionRecord.EXEC_STATUS_SUCCESS, null);

      if(entry != null && executionRecord != null) {
         Audit.getInstance().auditExecution(executionRecord, principal);
         executionRecord = new ExecutionRecord(
            execSessionId, userSessionId, objectName, objectType, ExecutionRecord.EXEC_TYPE_FINISH,
            execTimestamp, ExecutionRecord.EXEC_STATUS_SUCCESS, null);
      }

      try {
         refreshViewsheet(rvs, bookmarkName, bookmarkUser, initing, checkShareFilter,
                          tableMetaData, confirmed, userRefresh, resizing, width, height,
                          variables, principal, commandDispatcher, linkUri);
         execTimestamp = new Date(System.currentTimeMillis());
         executionRecord.setExecTimestamp(execTimestamp);
         executionRecord.setExecStatus(ExecutionRecord.EXEC_STATUS_SUCCESS);
      }
      catch(Exception e) {
         if(entry != null) {
            execTimestamp = new Date(System.currentTimeMillis());
            executionRecord.setExecTimestamp(execTimestamp);
            executionRecord.setExecStatus(ExecutionRecord.EXEC_STATUS_FAILURE);
            executionRecord.setExecError(e.getMessage());
         }

         throw e;
      }
      finally {
         pending.remove(id);
         box.unlockWrite();

         if(entry != null && executionRecord != null) {
            Audit.getInstance().auditExecution(executionRecord, principal);
         }

         if(entry != null && executionRecord != null && executionRecord.getExecTimestamp() != null)
         {
            logEntry.setFinishTime(executionRecord.getExecTimestamp().getTime());
            LogUtil.logPerformance(logEntry);
         }
      }
   }

   private void refreshViewsheet(RuntimeViewsheet rvs, String bookmarkName, IdentityID bookmarkUser,
                                 boolean initing, boolean checkShareFilter, boolean tableMetaData,
                                 boolean confirmed, boolean userRefresh, boolean resizing,
                                 int width, int height, VariableTable variables,
                                 Principal principal, CommandDispatcher commandDispatcher,
                                 String linkUri)
      throws Exception
   {
      vsBookmarkService.processBookmark(rvs.getID(), rvs, linkUri, principal, bookmarkName,
                                         bookmarkUser, null, commandDispatcher);
      ChangedAssemblyList clist = coreLifecycleService.createList(true, commandDispatcher,
                                                                  rvs, linkUri);
      ChangedAssemblyList.ReadyListener rlistener = clist.getReadyListener();

      if(principal instanceof SRPrincipal) {
         ThreadContext.setLocale(((SRPrincipal) principal).getLocale());
      }

      if(rlistener != null && initing) {
         rlistener.setInitingGrid(true);
         rlistener.setID(rvs.getID());
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      if(!rvs.isNeedRefresh() && checkShareFilter &&
         !isShareFilterNeedRefresh(rvs) && !isEmbeddedVSNeedRefresh(rvs))
      {
         return;
      }

      if(rvs.isNeedRefresh()) {
         rvs.setNeedRefresh(false);
      }

      try {
         AssetQuerySandbox wbox = box.getAssetQuerySandbox();

         if(variables != null && wbox != null) {
            wbox.refreshVariableTable(variables);
         }

         // @by stephenwebster, For Bug #1432
         // When a refresh occurs, viewsheets based on models get their
         // worksheets created dynamically.  There is a unique case where if
         // the viewsheet is refreshing at the same time it is being opened
         // (Due to scale to screen/window resize), this refresh event can wipe
         // out the worksheet causing other events to fail
         // (like GetChartAreaEvent), and displays an error.
         // The error that occurs due to the refresh action is unnecessary.
         // Here we will hint to the ViewsheetSandbox that the viewsheet is
         // being refreshed so we can prevent these errors from being
         // propogated to the end users.
         box.setRefreshing(true);

         String maxModeChart = null;
         Dimension maxModeSize = null;

         if(resizing) {
            for(Assembly assembly : box.getViewsheet().getAssemblies(true)) {
               if(assembly instanceof ChartVSAssembly) {
                  maxModeSize = ((ChartVSAssemblyInfo) assembly.getInfo()).getMaxSize();

                  if(maxModeSize != null) {
                     maxModeChart = assembly.getAbsoluteName();
                     break;
                  }
               }
            }
         }
         // for table metadata, do not reset runtime.
         // if just reizing, don't force the data cache to be cleared. (55009)
         else if(!tableMetaData) {
            rvs.resetRuntime();
            rvs.setTouchTimestamp(System.currentTimeMillis());
            box.setTouchTimestamp(rvs.getTouchTimestamp());
         }

         if(maxModeChart != null) {
            ((ChartVSAssemblyInfo) box.getViewsheet().getAssembly(maxModeChart).getInfo())
               .setMaxSize(maxModeSize);
         }

         // refresh the current bookmark info when other user changed the current
         // opened bookmark
         rvs.refreshCurrentBookmark(commandDispatcher, confirmed);
         final boolean mobile = "true".equals(rvs.getEntry().getProperty("_device_mobile"));
         // refreshViewsheet invokes onLoad, and should be called before reset(),
         // which execute the element scripts
         coreLifecycleService.refreshViewsheet(
            rvs, rvs.getID(), linkUri, width, height, mobile, null, commandDispatcher,
            false, false, true, clist);

         Viewsheet vs = rvs.getViewsheet();

         // replace viewsheet to keep the viewsheet for redo being uptodate
         // if this refresh event is call from other events, don't modify the
         // undo queue since it's managed by the top event
         if(vs != null && userRefresh) {
            rvs.replaceCheckpoint(vs.prepareCheckpoint());
         }

         if(!rvs.isViewer() && !rvs.isPreview()) {
            VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
            PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
            commandDispatcher.sendCommand(treeCommand);
         }
      }
      finally {
         box.setRefreshing(false);
      }
   }

   /**
    * Check if need refresh.
    * @return <tt>true</tt> if Refresh/unRefresh.
    */
   private Boolean isShareFilterNeedRefresh(RuntimeViewsheet rvs) {
      Assembly[] assemblies = rvs.getViewsheet().getAssemblies();
      RuntimeSheet[] rarr = viewsheetService.getRuntimeSheets(rvs.getUser());

      if(assemblies == null || rarr == null) {
         return false;
      }

      for(Assembly assembly : assemblies) {
         for(RuntimeSheet runtimeSheet : rarr) {
            if(!(runtimeSheet instanceof RuntimeViewsheet)) {
               return false;
            }

            RuntimeViewsheet rv = (RuntimeViewsheet) runtimeSheet;
            ViewsheetSandbox rbox = rv.getViewsheetSandbox();

            if(rbox == null) {
               continue;
            }

            ViewsheetSandbox[] boxes = rbox.getSandboxes();

            for(ViewsheetSandbox box : boxes) {
               Viewsheet vs = box.getViewsheet();

               List<VSAssembly> list = VSUtil.getSharedVSAssemblies(vs, (VSAssembly) assembly);

               if(list.size() > 0) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Check if Embedded VS need refresh.
    * @param rvs runtime viewsheet
    * @return <tt>true</tt> if Refresh/unRefresh.
    */
   private boolean isEmbeddedVSNeedRefresh(RuntimeViewsheet rvs) throws Exception {
      Assembly[] assemblies = rvs.getViewsheet().getAssemblies();

      //check whether embedded viewsheet needs to update
      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof Viewsheet)) {
            continue;
         }

         Viewsheet embeddedVS = (Viewsheet) assembly;
         AssetEntry enrty = embeddedVS.getEntry();
         Viewsheet parentVS = enrty == null ? null :
            (Viewsheet) rvs.getAssetRepository().getSheet(
               enrty, null, false, AssetContent.ALL);

         if(parentVS != null && embeddedVS.getLastModified() < parentVS.getLastModified()) {
            return true;
         }
      }

      return false;
   }

   @MessageMapping("/vs/refresh/assembly")
   public void refreshVsAssembly(RefreshVSAssemblyEvent event, CommandDispatcher dispatcher,
                                 @LinkUri String linkUri, Principal principal)
      throws Exception
   {
      String runtimeId = event.getVsRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs.getAssembly(event.getAssemblyName());

      // reset the assembly and dependencies.
      if(assembly != null) {
         refreshAssemblyAndDependencies(rvs, assembly, linkUri, dispatcher);
      }

      updateUndoState(rvs, dispatcher);
   }

   @MessageMapping("/vs/refresh/assembly/view")
   public void refreshVsAssemblyView(RefreshVSAssemblyEvent event,
                                     CommandDispatcher dispatcher,
                                     @LinkUri String linkUri,
                                     Principal principal)
      throws Exception
   {
      String runtimeId = event.getVsRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs.getAssembly(event.getAssemblyName());

      if(assembly != null) {
         try {
            coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);

            if(assembly instanceof TableDataVSAssembly) {
               int rows = Math.max(assembly.getPixelSize().height / 16, 100);
               BaseTableController.loadTableData(
                  rvs, assembly.getAbsoluteName(), 0, 0, rows, linkUri, dispatcher);
            }
         }
         catch(ExpiredSheetException e) {
            LOG.warn("Viewsheet [{}] is expired.", runtimeId);
         }
      }
   }

   /**
    * Refresh the assembly and reset dependencies.
    * @param rvs runtime viewsheet.
    * @param assembly vs assembly.
    * @param linkUri
    * @param dispatcher
    * @throws Exception
    */
   private void refreshAssemblyAndDependencies(RuntimeViewsheet rvs, VSAssembly assembly,
                                               String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      AssemblyEntry assemblyEntry = assembly.getAssemblyEntry();
      final Worksheet ws = vs.getBaseWorksheet();
      final AssemblyRef[] refs = vs.getDependings(assemblyEntry);
      final List<Assembly> rlist = new ArrayList<>();
      ChangedAssemblyList clist = new ChangedAssemblyList();

      for(int i = 0; i < refs.length; i++) {
         AssemblyEntry entry = refs[i].getEntry();
         Assembly tassembly = null;

         if(entry.isWSAssembly()) {
            tassembly = ws != null ? ws.getAssembly(entry) : null;

            if(tassembly instanceof TableAssembly &&
               !(assembly instanceof EmbeddedTableVSAssembly))
            {
               ((TableAssembly) tassembly).setPreRuntimeConditionList(null);
               ((TableAssembly) tassembly).setPostRuntimeConditionList(null);
               AssemblyRef[] refs2 = vs.getDependeds(entry);

               for(int j = 0; refs2 != null && j < refs2.length; j++) {
                  Assembly assembly2 = vs.getAssembly(refs2[j].getEntry());

                  if(assembly2 instanceof SelectionVSAssembly) {
                     rlist.add(assembly2);
                  }
               }
            }
         }
         else if(!assembly.getAbsoluteName().equals(entry.getName())) {
            tassembly = vs.getAssembly(entry);
         }

         if(tassembly != null) {
            rlist.add(tassembly);
         }
      }

      if(!rlist.contains(assembly)) {
         rlist.add(assembly);
      }

      Assembly[] rarr = new Assembly[rlist.size()];
      rlist.toArray(rarr);
      rvs.getViewsheetSandbox().reset(null, rarr, clist, false, false,
         null);
      coreLifecycleService.execute(rvs, assembly.getAbsoluteName(), linkUri, clist, dispatcher,
                                   false);

      // refresh new table data.
      if(assembly instanceof TableDataVSAssembly) {
         try {
            dispatcher.sendCommand(assembly.getAbsoluteName(), new AssemblyLoadingCommand());

            BaseTableController.loadTableData(
               rvs, assembly.getAbsoluteName(), 0, 0, 100, linkUri, dispatcher);
         }
         finally {
            dispatcher.sendCommand(assembly.getAbsoluteName(), new ClearAssemblyLoadingCommand());
         }
      }
   }

   /**
    * Update the undo state.
    * @param rvs
    * @param dispatcher
    */
   private void updateUndoState(RuntimeViewsheet rvs, CommandDispatcher dispatcher) {
      UpdateUndoStateCommand pointCommand = new UpdateUndoStateCommand();
      pointCommand.setPoints(rvs.size());
      pointCommand.setCurrent(rvs.getCurrent());
      pointCommand.setSavePoint(rvs.getSavePoint());
      dispatcher.sendCommand(pointCommand);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CoreLifecycleService coreLifecycleService;
   private final ViewsheetService viewsheetService;
   private final VSObjectTreeService vsObjectTreeService;
   private final VSBookmarkService vsBookmarkService;
   private final VSChartDataHandler chartDataHandler;
   private final ParameterService parameterService;
   private final ConcurrentMap<String, Boolean> pending = new ConcurrentHashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(VSRefreshController.class);
}
