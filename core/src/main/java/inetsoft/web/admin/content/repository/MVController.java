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

import inetsoft.mv.trans.UserInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
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
   }

   @PostMapping("/api/em/content/repository/mv/analyze")
   public AnalyzeMVResponse analyze(@RequestBody AnalyzeMVRequest analyzeMVRequest,
                                    Principal principal)
      throws Exception
   {
      MVSupportService.AnalysisResult analysisResult =
         mvService.analyze(analyzeMVRequest, principal);
      return AnalyzeMVResponse.builder()
         .completed(false)
         .analysisId(analysisResult.getId())
         .build();
   }

   @GetMapping("/api/em/content/materialized-view/check-analysis/{analysisId}")
   public AnalyzeMVResponse checkStatus(Principal principal,
                                        @PathVariable("analysisId") String analysisId)
      throws Exception
   {
      return mvService.checkAnalyzeStatus(analysisId, principal);
   }

   @GetMapping("/api/em/content/repository/mv/get-model/{analysisId}")
   @SuppressWarnings("unchecked")
   public AnalyzeMVResponse getModel(@RequestParam("hideData") boolean hideData,
                                     @RequestParam("hideExist") boolean hideExist,
                                     @PathVariable("analysisId") String analysisId)
   {
      List<MVSupportService.MVStatus> mvStatusList = support.getMVStatusList(analysisId);

      for(MVSupportService.MVStatus status : mvStatusList) {
         status.updateStatus();
      }

      List<MaterializedModel> models = mvService.getMaterializedModel(mvStatusList, hideData, hideExist);
      return AnalyzeMVResponse.builder()
         .completed(true)
         .exception(false)
         .status(models)
         .dateFormat(Tool.getDateFormatPattern())
         .analysisId(analysisId)
         .build();
   }

   @PostMapping("/api/em/content/repository/mv/show-plan/{analysisId}")
   public String showPlan(@PathVariable("analysisId") String analysisId,
                          @RequestBody CreateUpdateMVRequest createUpdateMVRequest)
   {
      MVSupportService.AnalysisResult analysisResult = support.getAnalysisResult(analysisId);
      List<MVSupportService.MVStatus> mvStatusList = analysisResult.getStatus();
      StringBuffer info = mvService.processPlan(createUpdateMVRequest.mvNames(), analysisResult,
                                                mvStatusList);
      return info.toString();
   }

   @SuppressWarnings("unchecked")
   @PostMapping("/api/em/content/repository/mv/create")
   public CreateMVResponse create(@RequestParam(name = "createId") String createId,
                                  @RequestParam(name = "analysisId") String analysisId,
                                  @RequestBody CreateUpdateMVRequest createUpdateMVRequest,
                                  Principal principal)
      throws Throwable
   {
      return mvService.create(createId, analysisId, createUpdateMVRequest, principal);
   }

   @SuppressWarnings("unchecked")
   @PostMapping("/api/em/content/repository/mv/set-cycle/{analysisId}")
   public void setCycle(@PathVariable("analysisId") String analysisId,
                        @RequestBody CreateUpdateMVRequest createUpdateMVRequest)
   {
      support.setDataCycle(createUpdateMVRequest.mvNames(), support.getAnalysisResult(analysisId),
                           createUpdateMVRequest.cycle());
   }

   @GetMapping("/api/em/content/repository/mv/exceptions/{analysisId}")
   public MVExceptionResponse setCycle(@PathVariable("analysisId") String analysisId) {
      MVSupportService.AnalysisResult analysisResult = support.getAnalysisResult(analysisId);
      List<UserInfo> exceptions = analysisResult.getExceptions();
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
                                    Principal principal) throws Exception
   {
      String[] mvNames = model.mvs().stream().map(MaterializedModel::name).toArray(String[]::new);
      MVSupportService.AnalysisResult analysisResult = support.analyze(mvNames, principal);
      return mvService.checkAnalyzeStatus(analysisResult, principal);
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
   public CreateMVResponse updateMaterializedViews(
      @RequestParam(name = "updateId", required = false) String updateId,
      @RequestBody MVManagementModel model,
      Principal principal) throws Throwable
   {
      return mvService.update(
         updateId,
         model.mvs().stream().map(MaterializedModel::name).toArray(String[]::new),
         model.runInBackground(),
         principal);
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

   @GetMapping("/api/em/content/repository/asset-exists")
   public boolean mvAssetExists(@RequestParam("path") String path, Principal principal) {
      try {
         AssetEntry entry = AssetEntry.createAssetEntry(path);
         AssetRepository repository = AssetUtil.getAssetRepository(false);

         if(entry == null) {
            return false;
         }

         return repository.getSheet(entry, principal, false, AssetContent.ALL) != null;
      }
      catch(Exception e) {
         return false;
      }
   }

   private final MVService mvService;
   private final MVSupportService support;
   private final SecurityProvider securityProvider;
}
