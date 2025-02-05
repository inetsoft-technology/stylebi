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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.Organization;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.uql.viewsheet.vslayout.AbstractLayout;
import inetsoft.uql.viewsheet.vslayout.VSAssemblyLayout;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.util.log.LogUtil;
import inetsoft.util.profile.Profile;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.*;
import inetsoft.web.composer.vs.event.CloseSheetEvent;
import inetsoft.web.composer.vs.event.NewViewsheetEvent;
import inetsoft.web.composer.ws.event.SaveSheetEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.controller.VSRefreshController;
import inetsoft.web.viewsheet.event.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Controller that provides a REST endpoint for composer viewsheet actions.
 */
@Controller
public class ComposerViewsheetController {
   /**
    * Creates a new instance of <tt>ComposerViewsheetController</tt>.
    */
   @Autowired
   public ComposerViewsheetController(RuntimeViewsheetRef runtimeViewsheetRef,
                                      RuntimeViewsheetManager runtimeViewsheetManager,
                                      PlaceholderService placeholderService,
                                      ViewsheetService viewsheetService,
                                      VSObjectTreeService vsObjectTreeService,
                                      VSRefreshController refreshController,
                                      VSLayoutService vsLayoutService,
                                      VSObjectModelFactoryService objectModelService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.runtimeViewsheetManager = runtimeViewsheetManager;
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
      this.vsObjectTreeService = vsObjectTreeService;
      this.refreshController = refreshController;
      this.vsLayoutService = vsLayoutService;
      this.objectModelService = objectModelService;
   }

   /**
    * Open a new viewsheet in composer.
    *
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to open runtime viewsheet
    */
   @LoadingMask
   @MessageMapping("composer/viewsheet/new")
   public void newViewsheet(@Payload NewViewsheetEvent event, Principal principal,
                            CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      String runtimeId = viewsheetService.openTemporaryViewsheet(event.getDataSource(), principal, null);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      String execSessionId =
         XSessionService.createSessionID(XSessionService.EXPORE_VIEW, rvs.getEntry().getName());
      rvs.setExecSessionID(execSessionId);
      rvs.setSocketSessionId(commandDispatcher.getSessionId());
      rvs.setSocketUserName(commandDispatcher.getUserName());
      AssetEntry entry = rvs.getEntry();
      SetRuntimeIdCommand runtimeIdCommand = new SetRuntimeIdCommand();
      runtimeIdCommand.setRuntimeId(runtimeId);
      runtimeViewsheetRef.setRuntimeId(runtimeId);
      runtimeViewsheetManager.sheetOpened(runtimeId);
      commandDispatcher.sendCommand(runtimeIdCommand);
      placeholderService.setViewsheetInfo(rvs, linkUri, commandDispatcher);

      ChangedAssemblyList clist =
         placeholderService.createList(true, event, commandDispatcher, rvs, linkUri);

      placeholderService.refreshViewsheet(rvs, entry.toIdentifier(), linkUri,
                                          event.getWidth(), event.getHeight(),
                                          event.isMobile(), event.getUserAgent(),
                                          commandDispatcher, true, false, false, clist);
      VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
      PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      commandDispatcher.sendCommand(treeCommand);
   }

   /**
    * Save viewsheet.
    *
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/viewsheet/save")
   public boolean saveViewsheet(@Payload SaveSheetEvent event, Principal principal,
                                CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      String vsOrgID = null;
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_EDIT,
         null, ActionRecord.OBJECT_TYPE_DASHBOARD);

      try {
         ViewsheetService vsService = viewsheetService;
         RuntimeViewsheet rvs = vsService.getViewsheet(
            runtimeViewsheetRef.getRuntimeId(), principal);
         AssetEntry entry = rvs.getEntry();
         vsOrgID = entry != null ? entry.getOrgID() : null;

         if(event.confirmed()) {
            rvs.setProperty("mvconfirmed", "true");
         }

         vsService.setViewsheet(rvs.getViewsheet(), entry, principal, true,
            !event.isUpdateDepend());

         if(event.isUpdateDepend()) {
            vsService.renameDep(rvs.getID());
            // After update depenency in asset data, should reload current vs to get latest data.
            // Send a command to reopen the vs.
            dispatcher.sendCommand(new ReopenSheetCommand(entry.toIdentifier()));
         }
         else {
            vsService.clearRenameDep(rvs.getID());
         }

         rvs.setSavePoint(rvs.getCurrent());

         String mvmsg = checkMVMessage(rvs, entry);

         if(mvmsg != null && mvmsg.length() > 0) {
            MessageCommand msgCmd = new MessageCommand();
            msgCmd.setMessage(mvmsg);
            msgCmd.setType(MessageCommand.Type.CONFIRM);
            String url = event.isClose() ?
               "/events/composer/viewsheet/save-and-close" : "/events/composer/viewsheet/save";
            msgCmd.addEvent(url, event);
            dispatcher.sendCommand(msgCmd);
            return false;
         }

         AssetEntry parent = entry.getParent();
         String objectName = parent != null ?
            entry.getParent().getDescription() + "/" + entry.getName() : entry.getName();
         actionRecord.setObjectName(objectName);
         SaveSheetCommand command = SaveSheetCommand.builder()
            .savePoint(rvs.getSavePoint())
            .id(entry.toIdentifier())
            .build();
         dispatcher.sendCommand(command);
         placeholderService.setViewsheetInfo(rvs, linkUri, dispatcher);
         return true;
      }
      catch(Exception ex) {
         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(ex.getMessage());
         }

         if(ex instanceof MessageException && SUtil.isDefaultVSGloballyVisible()
               && Tool.equals(vsOrgID, Organization.getDefaultOrganizationID())
               && !Tool.equals(vsOrgID, ((XPrincipal)principal).getOrgId())) {
            throw new MessageException(Catalog.getCatalog().getString("deny.access.write.globally.visible"));
         }

         throw ex;
      }
      finally {
         if(actionRecord != null && actionRecord.getObjectName() != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }
   }

   /**
    * Save viewsheet and send a close event to the client.
    *
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if save viewsheet fails
    */
   @LoadingMask
   @MessageMapping("composer/viewsheet/save-and-close")
   public void saveAndCloseViewsheet(@Payload SaveSheetEvent event, Principal principal,
                                     CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      if(saveViewsheet(event, principal, commandDispatcher, linkUri)) {
         commandDispatcher.sendCommand(CloseSheetCommand.builder().build());
      }
   }

   /**
    * Close viewsheet.
    *
    * @param event      the event indicating if the autosave file should be deleted
    * @param principal  a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/viewsheet/close")
   public void closeViewsheet(@Payload CloseSheetEvent event, Principal principal) throws Exception {
      String runtimeId = runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      final String bindingId = rvs.getBindingID();
      String rid = rvs.getViewsheetSandbox().getID();

      if(bindingId != null) {
         viewsheetService.closeViewsheet(bindingId, principal);
         runtimeViewsheetManager.sheetClosed(bindingId);
      }

      viewsheetService.closeViewsheet(runtimeId, principal);
      runtimeViewsheetManager.sheetClosed(runtimeId);
      Profile.getInstance().removeProfileData(rid);
      VSEventUtil.deleteAutoSavedFile(rvs.getEntry(), principal);
      AssetEntry entry = rvs.getEntry();
      Viewsheet vs = rvs.getViewsheet();

      AssetRepository rep = AssetUtil.getAssetRepository(false);
      ((AbstractAssetEngine)rep).fireAutoSaveEvent(entry);
   }

   /**
    * Preview viewsheet.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @LoadingMask(true)
   @MessageMapping("composer/viewsheet/preview")
   public void previewViewsheet(@Payload OpenPreviewViewsheetEvent event,
                                Principal principal,
                                CommandDispatcher dispatcher,
                                @LinkUri String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(event.getRuntimeViewsheetId(),
                                                           principal);
      Viewsheet viewsheet = rvs.getViewsheet();

      // For auditing
      String userSessionID = principal == null ?
         XSessionService.createSessionID(XSessionService.USER, null) :
         ((XPrincipal) principal).getSessionID();
      AssetEntry entry = rvs.getEntry();
      String objectName = entry.getDescription();
      LogUtil.PerformanceLogEntry logEntry = new LogUtil.PerformanceLogEntry(objectName);
      String execSessionID = XSessionService.createSessionID(
         XSessionService.EXPORE_VIEW, entry.getName());
      String objectType = ExecutionRecord.OBJECT_TYPE_VIEW;
      String execType = ExecutionRecord.EXEC_TYPE_START;
      java.sql.Date execTimestamp = new java.sql.Date(System.currentTimeMillis());
      logEntry.setStartTime(execTimestamp.getTime());
      ExecutionRecord executionRecord = new ExecutionRecord(execSessionID, userSessionID,
                                                            objectName, objectType, execType,
                                                            execTimestamp,
                                                            ExecutionRecord.EXEC_STATUS_SUCCESS,
                                                            null);
      Audit.getInstance().auditExecution(executionRecord, principal);
      executionRecord = new ExecutionRecord(execSessionID, userSessionID, objectName,
                                            objectType, ExecutionRecord.EXEC_TYPE_FINISH,
                                            execTimestamp, ExecutionRecord.EXEC_STATUS_SUCCESS,
                                            null);

      viewsheet.clearLayoutState();
      AbstractLayout vsLayout = viewsheet.getLayoutInfo().getViewsheetLayouts()
         .stream()
         .filter(layout -> layout.getName().equals(event.getLayoutName()))
         .findFirst()
         .orElse(null);

      try {
         VSUtil.OPEN_VIEWSHEET.set(true);
         final String designId = event.getRuntimeViewsheetId();
         String id = viewsheetService.openPreviewViewsheet(designId, principal, vsLayout);
         RuntimeViewsheet rvs2 = viewsheetService.getViewsheet(id, principal);
         rvs2.setSocketSessionId(dispatcher.getSessionId());
         rvs2.setSocketUserName(dispatcher.getUserName());
         runtimeViewsheetManager.sheetOpened(id);
         SetRuntimeIdCommand command = new SetRuntimeIdCommand();
         command.setRuntimeId(id);
         dispatcher.sendCommand(command);

         ChangedAssemblyList clist = placeholderService.createList(
            true, event, dispatcher, rvs2, linkUri);
         ViewsheetSandbox box = rvs2.getViewsheetSandbox();
         AssetQuerySandbox qbox = box.getAssetQuerySandbox();

         if(qbox != null) {
            qbox.setActive(true);
         }

         if(!box.isCancelled(execTimestamp.getTime())) {
            placeholderService.refreshViewsheet(rvs2, rvs2.getEntry().toIdentifier(),
               linkUri, event.getWidth(), event.getHeight(), event.isMobile(),
               event.getUserAgent(), dispatcher, true, false, true, clist);
            TextVSAssembly textVSAssembly = rvs2.getViewsheet() == null ?
               null : rvs2.getViewsheet().getWarningTextAssembly(false);

            if(textVSAssembly != null) {
               rvs.getViewsheet().adjustWarningTextPosition();
               placeholderService.addDeleteVSObject(rvs2, textVSAssembly, dispatcher);
               placeholderService.refreshVSAssembly(rvs2, textVSAssembly, dispatcher);
            }
         }

         placeholderService.shrinkZIndex(rvs2.getViewsheet(), dispatcher);
         placeholderService.setPermission(rvs2, principal, dispatcher);
         placeholderService.setExportType(rvs2, dispatcher);
         execTimestamp = new java.sql.Date(System.currentTimeMillis());
         executionRecord.setExecTimestamp(execTimestamp);
         executionRecord.setExecStatus(ExecutionRecord.EXEC_STATUS_SUCCESS);
      }
      catch(Exception e) {
         execTimestamp = new java.sql.Date(System.currentTimeMillis());
         executionRecord.setExecTimestamp(execTimestamp);
         executionRecord.setExecStatus(ExecutionRecord.EXEC_STATUS_FAILURE);
         executionRecord.setExecError(e.getMessage());
         throw e;
      }
      finally {
         VSUtil.OPEN_VIEWSHEET.remove();
         Audit.getInstance().auditExecution(executionRecord, principal);

         if(executionRecord != null && executionRecord.getExecTimestamp() != null) {
            logEntry.setFinishTime(executionRecord.getExecTimestamp().getTime());
            LogUtil.logPerformance(logEntry);
         }
      }
   }

   /**
    * Refresh the preview viewsheet.
    *
    * @param event               the event parameters.
    * @param principal           a principal identifying the current user.
    * @param commandDispatcher   the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @LoadingMask
   @MessageMapping("composer/viewsheet/preview/refresh")
   public void refreshPreviewViewsheet(@Payload OpenPreviewViewsheetEvent event,
                                       Principal principal,
                                       CommandDispatcher commandDispatcher,
                                       @LinkUri String linkUri)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      RuntimeViewsheet parentRvs = rvs.getOriginalID() == null ? rvs :
         viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      AbstractLayout vsLayout;

      vsLayout = parentRvs.getViewsheet().getLayoutInfo().getViewsheetLayouts()
         .stream()
         .filter(layout -> layout.getName().equals(event.getLayoutName()))
         .findFirst()
         .orElse(null);

      if(viewsheetService.refreshPreviewViewsheet(id, principal, vsLayout)) {
         rvs = viewsheetService.getViewsheet(id, principal);
         rvs.setSocketSessionId(commandDispatcher.getSessionId());
         rvs.setSocketUserName(commandDispatcher.getUserName());

         final Viewsheet newPreviewVS = rvs.getViewsheet();

         if(newPreviewVS != null) {
            // remove old annotations
            AnnotationVSUtil.removeUselessAssemblies(viewsheet.getAssemblies(),
                                                     newPreviewVS.getAssemblies(),
                                                     commandDispatcher);
            ChangedAssemblyList clist = placeholderService.createList(
               true, event, commandDispatcher, rvs, linkUri);
            placeholderService.refreshViewsheet(rvs, id, linkUri, event.getWidth(),
                                                event.getHeight(), event.isMobile(),
                                                event.getUserAgent(), commandDispatcher, false,
                                                false, true, clist);
         }

         runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      }
   }

   /**
    * Refresh viewsheet.
    *
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/viewsheet/refresh")
   public void refreshViewsheet(@Payload OpenViewsheetEvent event,
                                Principal principal, CommandDispatcher dispatcher,
                                @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      ChangedAssemblyList clist = placeholderService.createList(
         true, event, dispatcher, rvs, linkUri);

      placeholderService.refreshViewsheet(
         rvs, rvs.getEntry().toIdentifier(), linkUri, event.getWidth(),
         event.getHeight(), event.isMobile(), event.getUserAgent(), dispatcher,
         false, false, true, clist);

      // all chart graph pair will be reset and cancel during refreshing.
      // asynchronous execution of refreshing and getting chart area, will cause the chart get empty area(canceled).
      // so refresh all layout charts after refreshing.
      if(!Tool.isEmptyString(event.getLayoutName())) {
         refreshLayoutChartObjects(rvs, event.getLayoutName(), dispatcher);
      }
   }

   private void refreshLayoutChartObjects(RuntimeViewsheet rvs, String layoutName, CommandDispatcher dispatcher) {
      vsLayoutService.findViewsheetLayout(rvs.getViewsheet(), layoutName).ifPresent(layout -> {
         refreshLayoutRegionChart(vsLayoutService.getVSAssemblyLayouts(layout, VSLayoutService.HEADER),
            VSLayoutService.HEADER, rvs, dispatcher);
         refreshLayoutRegionChart(vsLayoutService.getVSAssemblyLayouts(layout, VSLayoutService.FOOTER),
            VSLayoutService.FOOTER, rvs, dispatcher);
         refreshLayoutRegionChart(vsLayoutService.getVSAssemblyLayouts(layout, VSLayoutService.CONTENT),
            VSLayoutService.CONTENT, rvs, dispatcher);
      });
   }

   private void refreshLayoutRegionChart(List<VSAssemblyLayout> refreshLayouts, int region,
                                         RuntimeViewsheet rvs, CommandDispatcher dispatcher)
   {
      refreshLayouts.stream()
         .filter(obj -> rvs.getViewsheet().getAssembly(obj.getName()) instanceof ChartVSAssembly)
         .forEach(layoutAssembly -> {
            AddLayoutObjectCommand command = new AddLayoutObjectCommand();

            if(layoutAssembly.getTableLayout() == region) {
               command.setObject(vsLayoutService.createObjectModel(rvs, layoutAssembly, objectModelService));
               command.setRegion(region);
               dispatcher.sendCommand(command);
            }
         });
   }

    private String checkMVMessage(RuntimeViewsheet rvs, AssetEntry entry) {
      if("true".equals(rvs.getProperty("mvconfirmed"))) {
         return "";
      }

      try {
         if(MVManager.getManager().containsMV(entry)) {
            // mv is not enabled? don't warn user the message, but set the
            // status to warn
            return Catalog.getCatalog().getString("vs.mv.exist");
         }
      }
      catch(Exception ex) {
         // ignore it
      }

      return "";
   }

   @MessageMapping("composer/viewsheet/checkmv")
   public void checkMV(@Payload CheckMVEvent event, Principal principal,
      CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
      runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
        return;
      }

      MVManager mgr = MVManager.getManager();
      boolean required = "true".equals(SreeEnv.getProperty("mv.required"));
      boolean metadata = "true".equals(SreeEnv.getProperty("mv.metadata"));
      AssetEntry entry = AssetEntry.createAssetEntry(event.getEntryId());
      VSRefreshEvent refresh = VSRefreshEvent.builder().confirmed(false).initing(false).build();

      if(event.isRefreshDirectly()) {
         this.refreshController.refreshViewsheet(refresh, principal, dispatcher, linkUri);
         return;
      }

      // wait for is not background
      if(event.isBackground()) {
         vs.getViewsheetInfo().setMetadata(true);
         vs.getBaseEntry().setProperty("mv_background", "true");
         // refresh the viewsheet so that any assemblies that have not been added are added with
         // metadata
         this.refreshController.refreshViewsheet(refresh, principal, dispatcher, linkUri);
      }
      // wait for is not confirmed
      else if(event.isConfirmed()) {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();

         if(box == null) {
            return;
         }

         if(mgr.isPending(box.getAssetEntry(), (XPrincipal) principal)) {
            mgr.cancelMV(box.getAssetEntry(), (XPrincipal) principal);

            if(rvs.isRuntime()) {
               if(required) {
                  rvs.setProperty("cancelled", "true");
                  MessageCommand cmd = new MessageCommand();
                  cmd.setType(MessageCommand.Type.ERROR);
                  cmd.setMessage(Catalog.getCatalog().getString("vs.mv.missing"));
                  dispatcher.sendCommand(cmd);
               }
               else {
                  box.setMVDisabled(true);
               }
            }
            else if(metadata) {
               vs.getViewsheetInfo().setMetadata(true);
            }
            else {
               box.setMVDisabled(true);
            }
         }
      }
      else {
         // wait until MV is done
         while(mgr.isPending(entry, (XPrincipal) principal)) {
            // mv creation in background, the original wait should be canceled
            if(!event.isWaitFor() && !rvs.isRuntime() &&
               vs.getViewsheetInfo().isMetadata())
            {
               return;
            }

            Thread.sleep(200);
         }

         // prompt user to refresh when background mv is created
         if(event.isWaitFor()) {
            vs.getViewsheetInfo().setMetadata(false);
            vs.getBaseEntry().setProperty("mv_background", null);

            MessageCommand cmd = new MessageCommand();
            cmd.setMessage(Catalog.getCatalog().getString("vs.mv.complete"));
            cmd.setType(MessageCommand.Type.CONFIRM);
            cmd.addEvent("/events/vs/refresh", refresh);
            dispatcher.sendCommand(cmd);
         }
         else {
            try {
               // refresh viewsheet after mv data is created.
               this.refreshController.refreshViewsheet(refresh, principal,
                  dispatcher, linkUri);
            }
            // when click cancel button in mv message dialog(have Run In Backgound
            // and Cancel button), refresh viewsheet when mv data is not created may
            // throw confirm exception, here just ingore it.
            catch(ConfirmException ex) {
            }

            // cancel progress, close the progress dailog.
            MessageCommand cmd = new MessageCommand();
            cmd.setType(MessageCommand.Type.PROGRESS);
            dispatcher.sendCommand(cmd);
         }
      }
   }

   @GetMapping("/api/composer/viewsheet/help-url")
   @ResponseBody
   public String getHelpUrL() {
      return Encode.forUri(Tool.getHelpBaseURL());
   }

   @GetMapping("/api/composer/viewsheet/script-help-url")
   @ResponseBody
   public String getScriptHelpUrL() {
      String base = Tool.getHelpBaseURL();
      return Encode.forUri(base + "#cshid=GeneralScriptFunctions");
   }

   @GetMapping("/api/composer/viewsheet/checkDependChanged")
   @ResponseBody
   public boolean checkDependChanged(@RequestParam("rid") String rid) {
      return this.viewsheetService.needRenameDep(rid);
   }

   @MessageMapping("composer/viewsheet/check-base-ws-expired")
   public void checkWorksheetChanged(CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Viewsheet vs = rvs.getViewsheet();
      AssetEntry wentry = vs.getBaseEntry();

      if(wentry == null || vs.getBaseWorksheet() == null) {
         return;
      }

      Worksheet ws = vs.getOriginalWorksheet();
      AssetRepository engine = viewsheetService.getAssetRepository();
      Worksheet latestWs;

      try {
         latestWs = (Worksheet) engine.getSheet(
            wentry, null, false, AssetContent.ALL, false);
      }
      catch(Exception e) {
         latestWs = null;
      }

      if(latestWs != null && latestWs.getLastModified() > ws.getLastModified()) {
         MessageCommand cmd = new MessageCommand();
         cmd.setType(MessageCommand.Type.INFO);
         String wsName = wentry.getAlias();
         wsName = wsName != null && !"".equals(wsName.trim())? wsName : wentry.getName();
         cmd.setMessage(Catalog.getCatalog().getString("asset.viewsheet.ws.changed", wsName));
         dispatcher.sendCommand(cmd);
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final RuntimeViewsheetManager runtimeViewsheetManager;
   private final PlaceholderService placeholderService;
   private final ViewsheetService viewsheetService;
   private final VSObjectTreeService vsObjectTreeService;
   private final VSRefreshController refreshController;
   private final VSLayoutService vsLayoutService;
   private final VSObjectModelFactoryService objectModelService;
}
