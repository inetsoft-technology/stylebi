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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.CheckMissingMVEvent;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.GroupedThread;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.ExecutionRecord;
import inetsoft.util.log.*;
import inetsoft.web.binding.command.SetGrayedOutFieldsCommand;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.security.Principal;
import java.sql.Date;
import java.util.*;

@Component
public class VSLifecycleService {
   @Autowired
   public VSLifecycleService(ViewsheetService viewsheetService, AssetRepository assetRepository,
                             CoreLifecycleService coreLifecycleService,
                             VSBookmarkService vsBookmarkService,
                             DataRefModelFactoryService dataRefModelFactoryService,
                             VSCompositionService vsCompositionService,
                             ParameterService parameterService)
   {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
      this.coreLifecycleService = coreLifecycleService;
      this.vsBookmarkService = vsBookmarkService;
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.vsCompositionService = vsCompositionService;
      this.parameterService = parameterService;
   }

   public String openViewsheet(OpenViewsheetEvent event, Principal principal, String linkUri)
      throws Exception
   {
      return CommandDispatcher.withDummyDispatcher(principal, dispatcher -> openViewsheet(
         event, principal, dispatcher, new RuntimeViewsheetRef(this.viewsheetService), null,
         linkUri));
   }

   public String openViewsheet(OpenViewsheetEvent event, Principal principal,
                               CommandDispatcher commandDispatcher,
                               RuntimeViewsheetRef runtimeViewsheetRef,
                               @Nullable RuntimeViewsheetManager runtimeViewsheetManager,
                               String linkUri)
      throws Exception
   {
      try {
         String id = event.getRuntimeViewsheetId();

         if(id != null && !id.isEmpty()) {
            openReturnedViewsheet(
               id, principal, linkUri, event, commandDispatcher, runtimeViewsheetRef,
               runtimeViewsheetManager);
            return id;
         }

         VSUtil.OPEN_VIEWSHEET.set(true);
         AssetDataCache.monitor(true);
         String orgID = ((XPrincipal) principal).getOrgId();
         AssetEntry entry = AssetEntry.createAssetEntry(event.getEntryId(), orgID);

         if(entry == null || !entry.isViewsheet()) {
            return null;
         }

         if(Thread.currentThread() instanceof GroupedThread) {
            String entryPath;

            try {
               entryPath = VSEventUtil.getViewhseetAssetPath(entry, assetRepository);
            }
            catch(Exception ex) {
               entryPath = entry.getPath();
            }

            if(entry.getScope() == AssetRepository.USER_SCOPE) {
               entryPath = Tool.MY_DASHBOARD + "/" + entryPath;
            }

            ((GroupedThread) Thread.currentThread())
               .addRecord(LogContext.DASHBOARD, entryPath);
         }

         entry.setProperty("openAutoSaved", Boolean.toString(event.isOpenAutoSaved()));

         if(!event.isOpenAutoSaved()) {
            assetRepository.clearCache(entry);
         }

         String desc = entry.getDescription();
         desc = desc.substring(0, desc.indexOf("/") + 1);
         desc += viewsheetService.localizeAssetEntry(
            entry.getPath(), principal, true, entry,
            entry.getScope() == AssetRepository.USER_SCOPE);
         entry.setProperty("_description_", desc);

         if(!event.isViewer() && event.getDrillFrom() != null) {
            entry.setProperty("preview", "true");
         }

         if(event.isViewer()) {
            applyViewsheetQuota(entry, principal, runtimeViewsheetManager);
         }

         if(event.isMeta()) {
            entry.setProperty("meta", "true");
         }

         return openViewsheet(
            event, entry, principal, linkUri, commandDispatcher, runtimeViewsheetRef,
            runtimeViewsheetManager);
      }
      finally {
         VSUtil.OPEN_VIEWSHEET.remove();
         AssetDataCache.monitor(false);
      }
   }

   public void closeViewsheet(String runtimeId, Principal principal) {
      closeViewsheet(runtimeId, principal, null);
   }

   /**
    * Closes a viewsheet.
    *
    * @param runtimeId the runtime identifier of the viewsheet to close.
    * @param principal a principal identifying the current user.
    */
   public void closeViewsheet(String runtimeId, Principal principal,
                              @Nullable RuntimeViewsheetManager runtimeViewsheetManager)
   {
      try {
         viewsheetService.closeViewsheet(runtimeId, principal);

         if(runtimeViewsheetManager != null) {
            runtimeViewsheetManager.sheetClosed(runtimeId);
         }
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to close viewsheet", e);
      }
   }

   private String openViewsheet(OpenViewsheetEvent event, AssetEntry entry, Principal principal,
                                String linkUri, CommandDispatcher dispatcher,
                                RuntimeViewsheetRef runtimeViewsheetRef,
                                RuntimeViewsheetManager runtimeViewsheetManager) throws Exception
   {
      // for Feature #26586, clear the old execution breakdown records.
      //AuditRecordUtils.deleteExecutionBreakDownRecord(entry.getName());
      String runtimeId = null;
      Map<String, String[]> parameters = event.getParameters();
      boolean auditFinish = true;
      boolean viewer = event.isViewer() || event.getDrillFrom() != null ||
         parameters != null && !parameters.isEmpty();
      entry.setProperty("sync", Boolean.toString(event.isSync()));

      String userSessionId = principal == null ?
         XSessionService.createSessionID(XSessionService.USER, null) :
         ((XPrincipal) principal).getSessionID();
      String objectName = entry.getDescription();
      LogUtil.PerformanceLogEntry logEntry = new LogUtil.PerformanceLogEntry(objectName);
      String execSessionId =
         XSessionService.createSessionID(XSessionService.EXPORE_VIEW, entry.getName());
      String objectType = ExecutionRecord.OBJECT_TYPE_VIEW;
      String execType = ExecutionRecord.EXEC_TYPE_START;
      Date execTimestamp = new Date(System.currentTimeMillis());
      logEntry.setStartTime(execTimestamp.getTime());

      ExecutionRecord executionRecord = new ExecutionRecord(
         execSessionId, userSessionId, objectName, objectType, execType, execTimestamp,
         ExecutionRecord.EXEC_STATUS_SUCCESS, null);
      Audit.getInstance().auditExecution(executionRecord, principal);

      executionRecord = new ExecutionRecord(
         execSessionId, userSessionId, objectName, objectType, ExecutionRecord.EXEC_TYPE_FINISH,
         execTimestamp, ExecutionRecord.EXEC_STATUS_SUCCESS, null);

      // Add selection parameters if hyperlinkSourceID is set
      if(event.getHyperlinkSourceId() != null) {
         RuntimeViewsheet rvs =
            viewsheetService.getViewsheet(event.getHyperlinkSourceId(), principal);
         parameters = parameters == null ? new HashMap<>() : parameters;

         if(rvs != null && rvs.getViewsheetSandbox() != null) {
            Hyperlink.Ref hyperlinkRef = new Hyperlink.Ref();
            // get selection parameters of source rvs
            VSUtil.addSelectionParameter(hyperlinkRef,
                                         rvs.getViewsheetSandbox().getSelections());
            Enumeration<?> keys = hyperlinkRef.getParameterNames();

            while(keys.hasMoreElements()) {
               String pname = (String) keys.nextElement();

               // dont add parameters that already have values set
               if(parameters.containsKey(pname)) {
                  continue;
               }

               Object paramValue = hyperlinkRef.getParameter(pname);
               String[] values = new String[0];

               if(Tool.getDataType(paramValue).equals(Tool.ARRAY)) {
                  if(((Object[]) paramValue).length > 0) {
                     paramValue = ((Object[]) paramValue)[0].toString().split("\\^");
                     Object[] params =
                        (Object[]) Tool.getData(Tool.ARRAY, Tool.getDataString(paramValue));

                     if(params != null && params.length > 0) {
                        values = Arrays.stream(params)
                           .map((param) -> Tool.getData(Tool.getDataType(param),
                                                        Tool.getDataString(param)).toString())
                           .toArray(String[]::new);
                     }
                  }
               }
               else {
                  values = new String[] { Tool.getDataString(paramValue) };
               }

               parameters.put(pname, values);
            }
         }
      }

      VariableTable variables = parameterService.readParameters(parameters);

      try {
         // only set this property for viewer, will apply relevant layout
         if(event.isViewer()) {
            entry.setProperty("_device_display_width", Integer.toString(event.getWidth()));
         }

         entry.setProperty("_device_mobile", Boolean.toString(event.isMobile()));
         entry.setProperty("_device_user_agent", event.getUserAgent());

         if(LogManager.getInstance().isDebugEnabled(LOG.getName())) {
            LOG.debug(
               "Browser: userAgent=" + event.getUserAgent() + ", displayWidth=" +
                  event.getWidth() + ", mobile=" + event.isMobile());
         }

         runtimeId = coreLifecycleService.openViewsheet(
            viewsheetService, event, principal, linkUri, event.getEmbeddedViewsheetId(),
            entry, dispatcher, runtimeViewsheetRef, runtimeViewsheetManager, viewer,
            event.getDrillFrom(), variables, event.getFullScreenId(), execSessionId);
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
         coreLifecycleService.setExportType(rvs, dispatcher);
         coreLifecycleService.setPermission(rvs, principal, dispatcher);

         if(event.getBookmarkName() != null && event.getBookmarkUser() != null) {
            IdentityID bookmarkUser = IdentityID.getIdentityIDFromKey(event.getBookmarkUser());
            VSBookmarkInfo openedBookmark = rvs.getOpenedBookmark();

            // Bug #66887, only open bookmark if it's different from the currently opened bookmark
            // prevents the default bookmark from loading twice
            if(openedBookmark == null ||
               !(Tool.equals(openedBookmark.getName(), event.getBookmarkName()) &&
                  Tool.equals(openedBookmark.getOwner(), bookmarkUser)))
            {
               vsBookmarkService.processBookmark(
                  runtimeId, rvs, linkUri, principal, event.getBookmarkName(),
                  bookmarkUser, event, dispatcher);
            }
         }

         if(rvs != null) {
            auditFinish = shouldAuditFinish(rvs.getViewsheetSandbox());

            if(event.getPreviousUrl() != null) {
               rvs.setPreviousURL(event.getPreviousUrl());
            }
            // drill from exist? it is the previous viewsheet
            else if(event.getDrillFrom() != null) {
               RuntimeViewsheet drvs =
                  viewsheetService.getViewsheet(event.getDrillFrom(), principal);
               AssetEntry dentry = drvs.getEntry();
               String didentifier = dentry.toIdentifier();
               String purl = linkUri + "app/viewer/view/" + didentifier +
                  "?rvid=" + event.getDrillFrom();
               rvs.setPreviousURL(purl);
            }

            String url = rvs.getPreviousURL();

            if(url != null) {
               SetPreviousUrlCommand command = new SetPreviousUrlCommand();
               command.setPreviousUrl(url);
               dispatcher.sendCommand(command);
            }

            VSModelTrapContext context = new VSModelTrapContext(rvs, true);

            if(context.isCheckTrap()) {
               context.checkTrap(null, null);
               DataRef[] refs = context.getGrayedFields();

               if(refs.length > 0) {
                  DataRefModel[] refsModel = new DataRefModel[refs.length];

                  for(int i = 0; i < refs.length; i++) {
                     refsModel[i] = dataRefModelFactoryService.createDataRefModel(refs[i]);
                  }

                  SetGrayedOutFieldsCommand command = new SetGrayedOutFieldsCommand(refsModel);
                  dispatcher.sendCommand(command);
               }
            }

            Viewsheet vs = rvs.getViewsheet();
            Assembly[] assemblies = vs.getAssemblies();

            // fix bug1309250160380, fix AggregateInfo for CrosstabVSAssembly
            for(Assembly assembly : assemblies) {
               if(assembly instanceof CrosstabVSAssembly) {
                  VSEventUtil.fixAggregateInfo(
                     (CrosstabVSAssembly) assembly, rvs, assetRepository, principal);
               }
            }

            // fix z-index. flash may use a different z-index structure so we should eliminate
            // duplicate values (which may happen for group containers).
            vsCompositionService.shrinkZIndex(vs, dispatcher);
         }

         execTimestamp = new Date(System.currentTimeMillis());
         executionRecord.setExecTimestamp(execTimestamp);
         executionRecord.setExecStatus(ExecutionRecord.EXEC_STATUS_SUCCESS);
      }
      catch(ConfirmException ex) {
         // have already handled this exception when refreshViewsheet.
         if(!(ex.getEvent() instanceof CheckMissingMVEvent)) {
            throw ex;
         }
      }
      catch(Exception e) {
         execTimestamp = new Date(System.currentTimeMillis());
         executionRecord.setExecTimestamp(execTimestamp);
         executionRecord.setExecStatus(ExecutionRecord.EXEC_STATUS_FAILURE);
         executionRecord.setExecError(e.getMessage());
         throw e;
      }
      finally {
         // @by ankitmathur fix bug1415397231289, Only log the "finish" record
         // in this method if the Viewsheet does not contain Variables. If it
         // does CollectParametersOverEvent will be called and we should log the
         // record in that event instead.
         if(auditFinish) {
            Audit.getInstance().auditExecution(executionRecord, principal);
         }

         if(executionRecord != null && executionRecord.getExecTimestamp() != null) {
            logEntry.setFinishTime(executionRecord.getExecTimestamp().getTime());
            LogUtil.logPerformance(logEntry);
         }
      }

      return runtimeId;
   }

   private void openReturnedViewsheet(String rid, Principal principal, String linkUri,
                                      OpenViewsheetEvent event, CommandDispatcher dispatcher,
                                      RuntimeViewsheetRef runtimeViewsheetRef,
                                      RuntimeViewsheetManager runtimeViewsheetManager)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(rid, principal);

      if(rvs == null) {
         coreLifecycleService.sendMessage(
            "Viewsheet " + rid + " was expired", MessageCommand.Type.INFO, dispatcher);
         return;
      }

      rvs.setSocketSessionId(dispatcher.getSessionId());
      rvs.setSocketUserName(dispatcher.getUserName());
      runtimeViewsheetRef.setRuntimeId(rid);

      if(runtimeViewsheetManager != null) {
         runtimeViewsheetManager.sheetOpened(rid);
      }

      dispatcher.sendCommand(null, new SetRuntimeIdCommand(rid));
      coreLifecycleService.setExportType(rvs, dispatcher);
      coreLifecycleService.setPermission(rvs, principal, dispatcher);
      coreLifecycleService.setComposedDashboard(rvs, dispatcher);
      vsBookmarkService.processBookmark(rid, rvs, linkUri, principal, event.getBookmarkName(),
                                        IdentityID.getIdentityIDFromKey(event.getBookmarkUser()),
                                        event, dispatcher);
      ChangedAssemblyList clist = coreLifecycleService.createList(
         true, event, dispatcher, rvs, linkUri);

      // optimization, call resetRuntime() explicitly instead of passing true to
      // refreshViewsheet's resetRuntime (last parameter). otherwise the touch timestamp
      // would be updated causing cached tablelens to be invalidated after binding change
      rvs.resetRuntime();

      coreLifecycleService.refreshViewsheet(
         rvs, rid, linkUri, event.getWidth(), event.getHeight(), event.isMobile(),
         event.getUserAgent(), dispatcher, true, false, false, clist,
         event.isManualRefresh(), false);
      String url = rvs.getPreviousURL();

      if(url != null) {
         SetPreviousUrlCommand command = new SetPreviousUrlCommand();
         command.setPreviousUrl(url);
         dispatcher.sendCommand(command);
      }
   }

   private void applyViewsheetQuota(AssetEntry entry, Principal user, RuntimeViewsheetManager runtimeViewsheetManager) {
      int limit = -1;
      String prop = SreeEnv.getProperty("viewsheet.user.instance.limit");

      if(prop != null) {
         try {
            limit = Integer.parseInt(prop);
         }
         catch(Exception ignore) {
         }
      }

      if(limit <= 0) {
         return;
      }

      Set<RuntimeViewsheet> list =
         new TreeSet<>(Comparator.comparingLong(RuntimeSheet::getLastAccessed));

      for(RuntimeViewsheet sheet : viewsheetService.getRuntimeViewsheets(user)) {
         if(sheet.getEntry().equals(entry) && sheet.isViewer()) {
            list.add(sheet);
         }
      }

      // a viewsheet is being opened, so we want limit - 1 instances left
      while(list.size() >= limit) {
         Iterator<RuntimeViewsheet> iterator = list.iterator();
         RuntimeViewsheet sheet = iterator.next();
         iterator.remove();

         LOG.debug(
            "Closing viewsheet " + sheet.getID() + " due to quota for " + user);

         closeViewsheet(sheet.getID(), user, null);

         if(runtimeViewsheetManager != null) {
            runtimeViewsheetManager.sheetClosed(sheet.getID());
         }
      }
   }

   private boolean shouldAuditFinish(ViewsheetSandbox viewsheetSandbox) {
      try {
         Viewsheet vs = viewsheetSandbox.getViewsheet();
         ViewsheetInfo vsInfo = vs == null ? null : vs.getViewsheetInfo();

         if(vsInfo != null && vsInfo.isDisableParameterSheet()) {
            return true;
         }

         return shouldAuditFinish0(viewsheetSandbox);
      }
      catch(Exception e) {
         // In case there are any issues/errors in checking the Variables for
         // this Viewsheet, just swallow the exception and continue on with the
         // previous logic. There is no reason to display this error to the end-user.
      }

      return true;
   }

   private boolean shouldAuditFinish0(ViewsheetSandbox viewsheetSandbox) {
      VariableTable vars = new VariableTable();
      AssetQuerySandbox abox = viewsheetSandbox.getAssetQuerySandbox();
      UserVariable[] params = abox.getAllVariables(vars);

      if(params != null && params.length > 0) {
         return false;
      }

      ViewsheetSandbox[] sandboxes = viewsheetSandbox.getSandboxes();

      if(sandboxes != null) {
         for(ViewsheetSandbox sandbox : sandboxes) {
            if(viewsheetSandbox == sandbox) {
               continue;
            }

            if(!shouldAuditFinish0(sandbox)) {
               return false;
            }
         }
      }

      return true;
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
   private final CoreLifecycleService coreLifecycleService;
   private final VSBookmarkService vsBookmarkService;
   private final DataRefModelFactoryService dataRefModelFactoryService;
   private final VSCompositionService vsCompositionService;
   private final ParameterService parameterService;
   private static final Logger LOG = LoggerFactory.getLogger(VSLifecycleService.class);
}
