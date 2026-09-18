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
package inetsoft.web.admin.ai.general;

import com.fasterxml.jackson.databind.JsonNode;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.general.CacheSettingsService;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for the admin-chat general-settings area -- the Enterprise Manager
 * "Settings &gt; General" page, minus license keys (owned by the licensing area).
 *
 * <p>Placed in {@code community/core}, not {@code enterprise/}: every service behind it lives in
 * {@code community/core} with no enterprise layer at all, the same community-tier shape
 * {@code AdminClusterController} and {@code AdminPresentationController} already establish. None of
 * these tools is enterprise-gated.
 *
 * <p>Never calls {@code GeneralSettingsPageController} itself -- wraps the sub-services directly
 * through {@link GeneralSettingsAccess}, for the usual reason: that controller's own
 * {@code @Secured} gate is checkPermission-mediated and therefore inert for a wiz-tagged caller.
 * (Its POST does skip a null sub-model rather than rewriting all seven, so a partial post would
 * not itself clobber the sub-models a caller never read -- the reason to go around it is the gate,
 * not the payload shape.) The real gate for this area is {@link #requireSiteAdmin},
 * matching {@code AdminLicensingController#requireSiteAdmin}; the {@code @Secured} annotations
 * reuse the EM page's own resource string as belt-and-suspenders visibility to tooling that
 * enumerates secured endpoints, not as a load-bearing check.
 *
 * <p><b>Site administrators only, including for MV settings.</b> The EM page has a second branch
 * that lets a non-site-admin edit {@code mvSettingsModel} alone on a multi-tenant deployment. That
 * branch is deliberately not reproduced: {@link #requireSiteAdmin} admits only site
 * administrators, so an organization administrator gets 403 for this whole area even where
 * Enterprise Manager would let them through. This is a deliberate narrowing -- the safe direction,
 * and stated here rather than left to be discovered.
 */
@RestController
public class AdminGeneralSettingsController {
   @Autowired
   public AdminGeneralSettingsController(GeneralSettingsAccess access,
                                         GeneralChangePlanService planService,
                                         GeneralChangesetApplyService applyService,
                                         CacheSettingsService cacheSettingsService,
                                         LocalizationSettingsService localizationSettingsService)
   {
      this.access = access;
      this.planService = planService;
      this.applyService = applyService;
      this.cacheSettingsService = cacheSettingsService;
      this.localizationSettingsService = localizationSettingsService;
   }

   /**
    * Reads one sub-model (when {@code subModel} is given) or all six (when omitted).
    *
    * <p>There is no {@code scope} parameter, unlike the presentation area's equivalent: these
    * settings are deployment-global and no service behind them takes an org argument.
    *
    * <p>The four {@code email} credential fields are masked regardless of which sub-model is
    * requested -- including the no-{@code subModel} form, which is the shortest path a caller has
    * to all of them at once.
    *
    * <p>A sub-model whose read throws is reported in {@code readErrors} instead of failing the
    * whole response. {@code PerformanceSettingsService.getModel} parses four properties with no
    * default, so on a deployment where {@code query.runtime.timeout} is unset or non-numeric it
    * throws -- and an operator asking about mail settings should not get nothing back because an
    * unrelated property is unparseable. A single named sub-model that fails still throws, since
    * there is no partial answer to give.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/general",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/general/settings")
   public GeneralGetResult getSettings(
      @RequestParam(value = "subModel", required = false) String subModelKey, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      Map<String, JsonNode> subModels = new LinkedHashMap<>();
      Map<String, String> readErrors = new LinkedHashMap<>();

      if(subModelKey == null) {
         for(GeneralSubModel subModel : GeneralSubModel.values()) {
            try {
               subModels.put(subModel.key(), projectedRead(subModel, user));
            }
            catch(Exception e) {
               LOG.warn("Failed to read the \"{}\" general settings sub-model", subModel.key(), e);
               readErrors.put(subModel.key(), messageOf(e));
            }
         }
      }
      else {
         GeneralSubModel subModel = GeneralSubModel.require(subModelKey);
         subModels.put(subModel.key(), projectedRead(subModel, user));
      }

      return new GeneralGetResult(subModels, readErrors.isEmpty() ? null : readErrors);
   }

   /** Resolves a general-settings change plan without mutating anything. */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/general",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/general/preview")
   public ResolvedPlan preview(@RequestBody GeneralChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Applies a reviewed general-settings change plan. Every plan takes a backup, synchronously,
    * inside {@link GeneralChangesetApplyService#apply} before any mutation.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/general",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/general/apply")
   public GeneralApplyResult apply(@RequestBody GeneralApplyRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

   /**
    * Clears cached data across the deployment.
    *
    * <p>POST, although Enterprise Manager exposes the same operation as a GET: a mutating GET
    * would be wrong on its own terms and, under the CSRF exemption that covers
    * {@code /api/wiz/**}, gratuitously risky.
    *
    * <p>An action rather than a plan -- there is no before/after value to diff, so there is no
    * plan hash or task token to carry. What it does carry is
    * {@code acknowledgeIrreversibleAction}, and that flag means something here: cleanup deletes
    * cached data on every live node and nothing restores it. This is the one genuinely
    * irreversible operation in the area, which is exactly why the ordinary change path has no
    * blanket acknowledgement to dilute it.
    *
    * <p>On a cluster the underlying fan-out skips nodes that are down, silently, so a node that is
    * offline now keeps its stale cache. Nothing in the product reports which nodes were reached,
    * so the response says so rather than implying deployment-wide success.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/general",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/general/cache/cleanup")
   public Map<String, String> cleanUpCache(@RequestBody GeneralActionRequest req, Principal user) {
      requireSiteAdmin(user);
      String task = requireTask(req);

      if(!Boolean.TRUE.equals(req.getAcknowledgeIrreversibleAction())) {
         throw new IllegalArgumentException(
            "acknowledgeIrreversibleAction: must be true -- clearing the cache deletes cached " +
            "data across every live node and nothing restores it. Confirm with the operator " +
            "before retrying.");
      }

      String txId = "gen-" + newIdSuffix();

      // Audited whether or not it succeeds. cleanUpCache fans out across cluster nodes and can
      // throw partway through, having already cleared the nodes it reached -- auditing only the
      // success path would leave the most destructive operation in this area with no trail in
      // exactly the case an operator most needs one. The apply service audits its failures for
      // the same reason.
      try {
         cacheSettingsService.cleanUpCache();
      }
      catch(RuntimeException e) {
         writeActionAudit(txId, "cache", task, "cache cleanup failed: " + messageOf(e), user,
                          AdminChangeRecord.STATUS_FAILED);
         throw e;
      }

      writeActionAudit(txId, "cache", task, "cleared the cache", user,
                       AdminChangeRecord.STATUS_VERIFIED);

      return Map.of(
         "status", "applied",
         "transactionId", txId,
         "advisory",
         "Cache cleanup was requested on every cluster node that is currently up. Nodes that are " +
         "down are skipped without notice and will keep their stale cache until they are cleaned " +
         "separately; the product does not report which nodes were reached.");
   }

   /**
    * Reloads locale resources so that locale label changes take effect without a restart.
    *
    * <p>POST for the same reason cleanup is. Idempotent and low risk, so it takes no
    * acknowledgement -- only a {@code task} for the audit record.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/general",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/general/localization/reload")
   public Map<String, String> reloadLocales(@RequestBody GeneralActionRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      String task = requireTask(req);
      String txId = "gen-" + newIdSuffix();

      try {
         localizationSettingsService.reloadLocales();
      }
      catch(Exception e) {
         writeActionAudit(txId, "localization", task, "locale reload failed: " + messageOf(e), user,
                          AdminChangeRecord.STATUS_FAILED);
         throw e;
      }

      writeActionAudit(txId, "localization", task, "reloaded locale resources", user,
                       AdminChangeRecord.STATUS_VERIFIED);

      return Map.of("status", "applied", "transactionId", txId);
   }

   private JsonNode projectedRead(GeneralSubModel subModel, Principal user) throws Exception {
      JsonNode node = GeneralJson.toNode(access.read(subModel, user));
      return GeneralJson.maskSecrets(subModel, node);
   }

   private static String requireTask(GeneralActionRequest req) {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      return req.getTask().trim();
   }

   /**
    * Audits an action that has no before/after value of its own.
    *
    * <p>Recorded at {@code RISK_HIGH}/{@code SCOPE_VALUE} with no backup reference: an action is
    * not preceded by a snapshot, because there is no value a snapshot could restore.
    */
   private void writeActionAudit(String txId, String property, String task, String description,
                                 Principal user, String status)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         // Required: AdminChangeRecord.validate() rejects a record with no transactionId, and the
         // changeset tools filter on it -- without one, the audit row for the single genuinely
         // irreversible operation in this area would be silently discarded.
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(property);
         record.setObjectType(ActionRecord.OBJECT_TYPE_EMPROPERTY);
         record.setAfterValue(description);
         record.setAction(AdminChangeRecord.ACTION_APPLY);
         record.setStatus(status);
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_VALUE);
         // Deliberately null: these actions are deployment-global.
         record.setOrganizationId(null);
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         // An audit write must never replace the real outcome.
         LOG.error("Failed to write general settings action audit record for {}", property,
                   auditFailure);
      }
   }

   /** Same rationale and shape as {@code AdminLicensingController#requireSiteAdmin} -- see there. */
   private void requireSiteAdmin(Principal user) {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site Administrator role required");
      }
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   @ExceptionHandler(IllegalArgumentException.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
   }

   @ExceptionHandler(AdminChangesetApplyService.PlanHashMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handlePlanHashMismatch(
      AdminChangesetApplyService.PlanHashMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   @ExceptionHandler(AdminChangesetApplyService.TaskTokenMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handleTaskTokenMismatch(
      AdminChangesetApplyService.TaskTokenMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   private static final Logger LOG = LoggerFactory.getLogger(AdminGeneralSettingsController.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   private final GeneralSettingsAccess access;
   private final GeneralChangePlanService planService;
   private final GeneralChangesetApplyService applyService;
   private final CacheSettingsService cacheSettingsService;
   private final LocalizationSettingsService localizationSettingsService;
}
