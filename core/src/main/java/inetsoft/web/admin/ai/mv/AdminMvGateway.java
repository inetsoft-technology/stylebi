/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.ai.mv;

import inetsoft.mv.trans.UserInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.admin.content.repository.MVService;
import inetsoft.web.admin.content.repository.MVSupportService;
import inetsoft.web.admin.content.repository.model.MaterializedModel;
import inetsoft.web.admin.model.NameLabelTuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin delegate to the existing community {@code MVService}/{@code MVSupportService} beans -- no
 * business logic duplicated from either; every actual mutation/analysis call is a straight
 * passthrough. Does identifier resolution and DTO reshaping only, the same split {@code
 * AdminScheduleGateway} uses for its own community-only wrapped service.
 */
@Service
public class AdminMvGateway {
   @Autowired
   public AdminMvGateway(MVService mvService, MVSupportService mvSupportService,
                         SecurityEngine securityEngine)
   {
      this.mvService = mvService;
      this.mvSupportService = mvSupportService;
      this.securityEngine = securityEngine;
   }

   /** Wraps {@code MVService.getMVInfo} -- omit {@code assetIds} for every MV in the deployment. */
   public MvStatusView getStatus(List<String> assetIds, Principal user) throws Exception {
      List<MaterializedModel> mvs = mvService.getMVInfo(assetIds, user).mvs();
      return new MvStatusView(isWsMvEnabled(), canMaterialize(user), mvs);
   }

   /** {@code true} if worksheet-level MV analysis is enabled server-wide. Surfaced explicitly
    * (rather than left as {@code MVService.analyze}'s own silent candidate-dropping filter) so a
    * caller passing a worksheet asset id while this is off gets a clear signal instead of a
    * "successful" analysis silently missing the asset it asked about. */
   public boolean isWsMvEnabled() {
      return SreeEnv.getBooleanProperty("ws.mv.enabled");
   }

   /** {@code true} if {@code user} holds the {@code MATERIALIZATION}/{@code ACCESS} permission
    * every mutating MV endpoint enforces server-side -- lets a caller get a clean, up-front
    * refusal instead of discovering it only after {@code preview_mv_changes} resolves a plan. */
   public boolean canMaterialize(Principal user) throws inetsoft.sree.security.SecurityException {
      return securityEngine.checkPermission(user, ResourceType.MATERIALIZATION, "*", ResourceAction.ACCESS);
   }

   /**
    * Kicks off an analysis for a single viewsheet/worksheet identifier. Delegates directly to
    * {@code MVSupportService.analyze(List, ...)}, bypassing {@code MVService.analyze}'s
    * {@code ContentRepositoryTreeNode}/tree-node wrapper entirely -- the caller already has the
    * identifier (the same shape {@code list_viewsheets} returns), not a tree node.
    */
   public String analyze(String viewsheetAssetId, boolean expandGroups, boolean bypassVpm,
                         boolean fullData, boolean applyParentVsParameters, Principal user)
      throws Exception
   {
      if(AssetEntry.createAssetEntry(viewsheetAssetId) == null) {
         throw new IllegalArgumentException(
            "viewsheetAssetId: not a valid asset identifier: " + viewsheetAssetId);
      }

      MVSupportService.AnalysisResult result = mvSupportService.analyze(
         List.of(viewsheetAssetId), expandGroups, bypassVpm, fullData, user, false,
         applyParentVsParameters);
      return result.getId();
   }

   /** Wraps {@code MVSupportService.getAnalysisResult} -- the returned handle throws {@code
    * IllegalStateException} from its own accessors once the analysis is unknown/expired; callers
    * in this package translate that into {@link AnalysisExpiredException}. */
   public MVSupportService.AnalysisResult getAnalysisResult(String analysisId) {
      return mvSupportService.getAnalysisResult(analysisId);
   }

   public List<NameLabelTuple> getDataCycles(Principal user) throws Exception {
      return mvService.getDataCycles(user);
   }

   /** Read the same way {@code MVService.checkAnalyzeStatus} does, through the static accessor --
    * {@code MVManager} is a process-wide singleton, not worth injecting a second handle to. */
   public String getDefaultCycle() {
      String cycle = inetsoft.mv.MVManager.getManager().getDefaultCycle();
      return cycle == null ? "" : cycle;
   }

   /** Mirrors {@code MVSupportService.setDataCycle(List, AnalysisResult, String, String)} --
    * mutates the pending (not-yet-created) candidate definitions cached under the analysis. */
   public void setDataCycle(List<String> mvNames, MVSupportService.AnalysisResult result,
                            String cycle, String orgId)
   {
      mvSupportService.setDataCycle(mvNames, result, cycle, orgId);
   }

   /** Mirrors {@code MVSupportService.createMV} -- returns a non-null warning/error message on
    * failure, {@code null} on success. */
   public String createMV(List<String> mvNames, List<MVSupportService.MVStatus> mvStatusList,
                          boolean background, boolean noData, Principal principal)
      throws Throwable
   {
      return mvSupportService.createMV(mvNames, mvStatusList, background, noData, principal);
   }

   /** Mirrors {@code MVSupportService.dispose} -- deletes the MV definition and its underlying
    * cluster files. No live inverse. */
   public void dispose(List<String> mvNames) {
      mvSupportService.dispose(mvNames);
   }

   /** Resolves an MV name within the caller's own organization, guarding against a caller reaching
    * another organization's MV by naming it directly. */
   public boolean existsInOrg(String name, String orgId) {
      return mvSupportService.existsInOrg(name, orgId);
   }

   /**
    * Folds check-analysis + get-model + show-plan + exceptions into one poll response (all four
    * read from the same analysisId-scoped cluster-map entry). {@code completed:false} until the
    * background analysis job finishes -- the caller re-polls.
    *
    * @throws AnalysisExpiredException if {@code analysisId} is unknown or has expired.
    */
   public MvAnalysisView getAnalysis(String analysisId, Principal user) throws Exception {
      MVSupportService.AnalysisResult analysisResult = mvSupportService.getAnalysisResult(analysisId);
      boolean completed;

      try {
         completed = analysisResult.isCompleted();
      }
      catch(IllegalStateException e) {
         throw new AnalysisExpiredException(analysisId);
      }

      List<NameLabelTuple> cycles = mvService.getDataCycles(user);
      boolean onDemand = "true".equals(SreeEnv.getProperty("mv.ondemand"));
      boolean runInBackground = "true".equals(SreeEnv.getProperty("mv.run.background"));

      if(!completed) {
         return new MvAnalysisView(analysisId, false, false, List.of(), List.of(), cycles,
                                   getDefaultCycle(), onDemand, runInBackground);
      }

      boolean exception = !analysisResult.getExceptions().isEmpty();
      List<MVSupportService.MVStatus> mvStatusList = analysisResult.getStatus();

      for(MVSupportService.MVStatus status : mvStatusList) {
         // Re-checks exists/hasData against the live MVManager, not the cached analysis snapshot
         // -- matches MVController#getModel's own behavior.
         status.updateStatus();
      }

      List<MaterializedModel> models = mvService.getMaterializedModel(mvStatusList);
      List<MvCandidateView> candidates = new ArrayList<>();

      for(MaterializedModel model : models) {
         String plan = mvService.processPlan(List.of(model.name()), analysisResult, mvStatusList)
            .toString();
         candidates.add(new MvCandidateView(model.name(), model.sheets(), model.table(),
                                            model.cycle(), model.exists(), model.hasData(), plan));
      }

      List<String> exceptionReasons = new ArrayList<>();

      for(UserInfo info : analysisResult.getExceptions()) {
         exceptionReasons.add(info.getSheetName() + ": " + info.getMessage());
      }

      return new MvAnalysisView(analysisId, true, exception, candidates, exceptionReasons, cycles,
                                getDefaultCycle(), onDemand, runInBackground);
   }

   private final MVService mvService;
   private final MVSupportService mvSupportService;
   private final SecurityEngine securityEngine;
}
