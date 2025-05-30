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

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.CheckMissingMVEvent;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.AffinityCallable;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.GroupedThread;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.ExecutionRecord;
import inetsoft.util.log.*;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.io.Serializable;
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
                             ParameterService parameterService,
                             VSLifecycleControllerServiceProxy serviceProxy)
   {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
      this.coreLifecycleService = coreLifecycleService;
      this.vsBookmarkService = vsBookmarkService;
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.vsCompositionService = vsCompositionService;
      this.parameterService = parameterService;
      this.serviceProxy = serviceProxy;
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
         serviceProxy.setRuntimeParameters(event.getHyperlinkSourceId(), parameters, principal);
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

         auditFinish = serviceProxy.processSheet(runtimeId, event, linkUri, auditFinish, dispatcher, principal);

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
      runtimeViewsheetRef.setRuntimeId(rid);

      if(runtimeViewsheetManager != null) {
         runtimeViewsheetManager.sheetOpened(rid);
      }

      serviceProxy.openReturnedViewsheet(rid, principal, linkUri, event, dispatcher);
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

      List<OpenViewsheet> list = viewsheetService.invokeOnAll(new GetOpenViewsheetsTask(user))
         .stream()
         .flatMap(Collection::stream)
         .sorted()
         .toList();

      // a viewsheet is being opened, so we want limit - 1 instances left
      while(list.size() >= limit) {
         Iterator<OpenViewsheet> iterator = list.iterator();
         OpenViewsheet sheet = iterator.next();
         iterator.remove();

         LOG.debug(
            "Closing viewsheet " + sheet.getId() + " due to quota for " + user);

         final String rid = sheet.getId();
         Cluster.getInstance().affinityCall(ViewsheetEngine.CACHE_NAME, rid, new AffinityCallable<Void>() {
            @Override
            public Void call() throws Exception {
               ViewsheetEngine.getViewsheetEngine().closeViewsheet(rid, user);
               return null;
            }
         });

         if(runtimeViewsheetManager != null) {
            runtimeViewsheetManager.sheetClosed(sheet.getId());
         }
      }
   }

   private static final class OpenViewsheet implements Serializable, Comparable<OpenViewsheet> {
      public OpenViewsheet(String id, long lastModified) {
         this.id = id;
         this.lastModified = lastModified;
      }

      public String getId() {
         return id;
      }

      public long getLastModified() {
         return lastModified;
      }

      @Override
      public int compareTo(@NotNull VSLifecycleService.OpenViewsheet o) {
         return Comparator.comparing(OpenViewsheet::getLastModified)
            .thenComparing(OpenViewsheet::getId)
            .compare(this, o);
      }

      private final String id;
      private final long lastModified;
   }

   private static final class GetOpenViewsheetsTask implements ViewsheetService.Task<ArrayList<OpenViewsheet>> {
      public GetOpenViewsheetsTask(Principal user) {
         this.user = user;
      }

      @Override
      public ArrayList<OpenViewsheet> apply(ViewsheetService service) throws Exception {
         ArrayList<OpenViewsheet> result = new ArrayList<>();

         for(RuntimeViewsheet rvs : service.getRuntimeViewsheets(user)) {
            result.add(new OpenViewsheet(rvs.getID(), rvs.getLastAccessed()));
         }

         return result;
      }

      private final Principal user;
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
   private final CoreLifecycleService coreLifecycleService;
   private final VSBookmarkService vsBookmarkService;
   private final DataRefModelFactoryService dataRefModelFactoryService;
   private final VSCompositionService vsCompositionService;
   private final ParameterService parameterService;
   private final VSLifecycleControllerServiceProxy serviceProxy;
   private static final Logger LOG = LoggerFactory.getLogger(VSLifecycleService.class);
}
