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
package inetsoft.web.admin.content.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import inetsoft.mv.trans.UserInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.security.ConnectionStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
public class MVController {
   @Autowired
   public MVController(MVService mvService,
                       MVSupportService support,
                       SecurityProvider securityProvider)
   {
      this.mvService = mvService;
      this.support = support;
      this.securityProvider = securityProvider;
      this.createMVCache = Caffeine.newBuilder()
         .expireAfterAccess(10L, TimeUnit.MINUTES)
         .maximumSize(1000L)
         .build();
   }

   @PostMapping("/api/em/content/repository/mv/analyze")
   public void analyze(@RequestBody AnalyzeMVRequest analyzeMVRequest, HttpServletRequest req,
                       Principal principal)
      throws Exception
   {
      HttpSession session = req.getSession(true);
      MVSupportService.AnalysisResult jobs = mvService.analyze(analyzeMVRequest, principal);
      session.setAttribute("mv_jobs", jobs);
   }

   @GetMapping("/api/em/content/materialized-view/check-analysis")
   public AnalyzeMVResponse checkStatus(HttpServletRequest req, Principal principal)
      throws Exception
   {
      HttpSession session = req.getSession(true);
      MVSupportService.AnalysisResult jobs = (MVSupportService.AnalysisResult)
         session.getAttribute("mv_jobs");
      AnalyzeMVResponse response = mvService.checkAnalyzeStatus(jobs, principal);

      if(jobs.isCompleted()) {
         session.setAttribute("mvstatus", jobs.getStatus());
      }

      return response;
   }

   @GetMapping("/api/em/content/repository/mv/get-model")
   @SuppressWarnings("unchecked")
   public AnalyzeMVResponse getModel(HttpServletRequest req,
                                     @RequestParam("hideData") boolean hideData,
                                     @RequestParam("hideExist") boolean hideExist)
   {
      HttpSession session = req.getSession(true);
      List<MVSupportService.MVStatus> mvstatus0 = (List) session.getAttribute("mvstatus");

      if(mvstatus0 == null) {
         mvstatus0 = support.getMVStatus(null);
         session.setAttribute("mvstatus", mvstatus0);
      }
      else {
         for(MVSupportService.MVStatus status : mvstatus0) {
            status.updateStatus();
         }
      }

      List<MaterializedModel> models = mvService.getMaterializedModel(mvstatus0, hideData, hideExist);
      return AnalyzeMVResponse.builder()
         .completed(true)
         .exception(false)
         .status(models)
         .dateFormat(Tool.getDateFormatPattern())
         .build();
   }

   @PostMapping("/api/em/content/repository/mv/show-plan")
   public String showPlan(HttpServletRequest req,
                          @RequestBody CreateUpdateMVRequest createUpdateMVRequest)
   {
      HttpSession session = req.getSession(true);
      List<MVSupportService.MVStatus> mvstatus = (List<MVSupportService.MVStatus>)
         session.getAttribute("mvstatus");
      MVSupportService.AnalysisResult jobs = (MVSupportService.AnalysisResult)
         session.getAttribute("mv_jobs");
      StringBuffer info = mvService.processPlan(createUpdateMVRequest.mvNames(), jobs, mvstatus);
      session.setAttribute("info", info);
      return info.toString();
   }

   @SuppressWarnings("unchecked")
   @PostMapping("/api/em/content/repository/mv/create")
   public CreateMVResponse create(HttpServletRequest req,
                      @RequestParam(name = "createId", required = false) String createId,
                      @RequestBody CreateUpdateMVRequest createUpdateMVRequest,
                      Principal principal) throws Throwable
   {
      HttpSession session = req.getSession(true);

      if(createId != null) {
         CompletableFuture<CreateMVResponse> future = createMVCache.getIfPresent(createId);

         if(future != null) {
            if(future.isDone()) {
               createMVCache.invalidate(createId);
               return future.get();
            }
            else {
               return CreateMVResponse.builder().complete(false).build();
            }
         }
      }

      if(createId == null) {
         create0(session, createUpdateMVRequest, principal);
         return CreateMVResponse.builder().complete(true).build();
      }
      else if(createMVCache.getIfPresent(createId) == null) {
         CompletableFuture<CreateMVResponse> future = new CompletableFuture<>();
         createMVCache.put(createId, future);
         ThreadPool.addOnDemand(() -> {
            Principal oPrincipal = ThreadContext.getPrincipal();
            ThreadContext.setPrincipal(principal);

            try {
               create0(session, createUpdateMVRequest, principal);
               future.complete(CreateMVResponse.builder().complete(true).build());
            }
            catch(Throwable e) {
               future.completeExceptionally(e);
            }
            finally {
               ThreadContext.setPrincipal(oPrincipal);
            }
         });
      }

      return CreateMVResponse.builder().complete(false).build();
   }

   public void create0(HttpSession session, CreateUpdateMVRequest createUpdateMVRequest,
                       Principal principal)
      throws Throwable
   {
      ActionRecord actionRecord = SUtil.getActionRecord(
         principal, ActionRecord.ACTION_NAME_CREATE,
         (DataCycleManager.TASK_PREFIX + createUpdateMVRequest.cycle()).trim(),
         ActionRecord.OBJECT_TYPE_TASK);

      try {
         IdentityID user = IdentityID.getIdentityIDFromKey(principal.getName());

         if(createUpdateMVRequest.runInBackground() && Cluster.getInstance().isSchedulerRunning() &&
            !SUtil.getRepletRepository().checkPermission(
               principal, ResourceType.SCHEDULER, "*", ResourceAction.ACCESS))
         {
            throw new RuntimeException("User '" + user.getName() + "' doesn't have schedule permission.");
         }

         String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);
         List<MVSupportService.MVStatus> mvstatus = (List<MVSupportService.MVStatus>)
            session.getAttribute("mvstatus");
         DataCycleManager dcmanager = DataCycleManager.getDataCycleManager();
         dcmanager.setEnable(createUpdateMVRequest.cycle(), orgId, true);

         if(principal instanceof XPrincipal) {
            principal = (XPrincipal) ((XPrincipal) principal).clone();
         }

         String exception = support.createMV(createUpdateMVRequest.mvNames(), mvstatus,
                                             createUpdateMVRequest.runInBackground(),
                                             createUpdateMVRequest.noData(),
                                             principal);

         if(exception != null) {
            throw new RuntimeException(exception);
         }
      }
      catch(Exception e) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(e.getMessage());
         throw e;
      }
      finally {
         if(actionRecord != null) {
            actionRecord.setObjectUser(XPrincipal.SYSTEM);
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }
   }

   @SuppressWarnings("unchecked")
   @PostMapping("/api/em/content/repository/mv/set-cycle")
   public void setCycle(HttpServletRequest req,
                        @RequestBody CreateUpdateMVRequest createUpdateMVRequest)
   {
      HttpSession session = req.getSession(true);
      List<MVSupportService.MVStatus> mvstatus = (List<MVSupportService.MVStatus>)
         session.getAttribute("mvstatus");
      support.setDataCycle(createUpdateMVRequest.mvNames(), mvstatus,
                           createUpdateMVRequest.cycle());
   }

   @GetMapping("/api/em/content/repository/mv/exceptions")
   public MVExceptionResponse setCycle(HttpServletRequest req) {
      HttpSession session = req.getSession(true);
      MVSupportService.AnalysisResult jobs = (MVSupportService.AnalysisResult) session.getAttribute("mv_jobs");
      List<UserInfo> exceptions = jobs == null ? null : jobs.getExceptions();
      assert exceptions != null;
      List<MVExceptionModel> exceptionModels = exceptions.stream()
         .map(exception -> MVExceptionModel.builder()
            .viewsheet(exception.getSheetName())
            .reasons(exception.getMessage())
            .build()).collect(Collectors.toList());
      return MVExceptionResponse.builder().exceptions(exceptionModels).build();
   }

   @PostMapping("/api/em/content/materialized-view/info")
   public MVManagementModel getMVInfo(@RequestBody(required = false) AnalyzeMVRequest nodes,
                                      Principal principal)
      throws Exception
   {
      List<String> ids = null;
      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(SecurityEngine.getSecurity().getSecurityProvider().getOrganization(currOrgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

      if(nodes != null) {
         ids = nodes.nodes().stream()
            .map(mvService::toAssetEntry).map(AssetEntry::toIdentifier)
            .collect(Collectors.toList());
      }

      return mvService.getMVInfo(ids, principal);
   }

   @PostMapping("/api/em/content/materialized-view/analysis")
   public AnalyzeMVResponse analyze(@RequestBody MVManagementModel model,
                                    HttpServletRequest request,
                                    Principal principal) throws Exception
   {
      String[] mvNames = model.mvs().stream().map(MaterializedModel::name).toArray(String[]::new);
      MVSupportService.AnalysisResult jobs = support.analyze(mvNames, principal);
      HttpSession session = request.getSession(true);
      session.setAttribute("mv_jobs", jobs);

      return checkStatus(request, principal);
   }

   @PostMapping("/api/em/content/materialized-view/remove")
   public void deleteMVs(@RequestBody MVManagementModel model) {
      support.dispose(
         model.mvs()
            .stream()
            .map(MaterializedModel::name)
            .collect(Collectors.toList())
      );
   }

   @PostMapping("/api/em/content/materialized-view/date-as-ages")
   public void setShowAges(@RequestBody MVManagementModel model) throws Exception {
      String showDateAsAges = model.showDateAsAges() ? "true" : "false";
      SreeEnv.setProperty("mvmanager.dates.ages", showDateAsAges);
      SreeEnv.save();
   }

   @PostMapping("/api/em/content/materialized-view/data-cycle")
   public void setDataCycle(@RequestBody MVManagementModel model) {
      String[] mvNames = model.mvs().stream().map(MaterializedModel::name).toArray(String[]::new);
      support.setDataCycle(mvNames, model.cycle());
   }

   @PostMapping("/api/em/content/materialized-view/update")
   public ConnectionStatus updateMaterializedViews(@RequestBody MVManagementModel model,
                                                   Principal principal) throws Throwable
   {
      Catalog catalog = Catalog.getCatalog();
      String msg = support.recreateMV(
         model.mvs()
            .stream()
            .map(MaterializedModel::name)
            .toArray(String[]::new),
         model.runInBackground(),
         principal
      );

      if(msg == null) {
         msg = model.runInBackground() ? catalog.getString("em.alert.createMV0") :
            catalog.getString("em.alert.createMV");
      }

      return new ConnectionStatus(msg);
   }

   @GetMapping("/api/em/content/repository/mv/ws-mv-enabled")
   public WSMVEnabledModel isWSMVEnabled() {
      return WSMVEnabledModel.builder().enabled(
         SreeEnv.getBooleanProperty("ws.mv.enabled")).build();
   }

   @GetMapping("/api/em/content/repository/mv/permission")
   public MVHasPermissionModel hasMVPermission(Principal principal) {
      boolean canMaterialize = securityProvider.checkPermission(
         principal, ResourceType.MATERIALIZATION, "*", ResourceAction.ACCESS);

      return MVHasPermissionModel.builder().allow(canMaterialize).build();
   }

   private final MVService mvService;
   private final MVSupportService support;
   private final SecurityProvider securityProvider;
   private final Cache<String, CompletableFuture<CreateMVResponse>> createMVCache;
}
