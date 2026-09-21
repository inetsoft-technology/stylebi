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
package inetsoft.web.admin.ai.dashboard;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.admin.ai.*;
import inetsoft.web.admin.content.repository.RepositoryDashboardService;
import inetsoft.web.admin.content.repository.model.NewRepositoryFolderRequest;
import inetsoft.web.admin.content.repository.model.RepositoryDashboardSettingsModel;
import inetsoft.web.admin.content.repository.model.RepositoryFolderDashboardSettingsModel;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Applies a whole Portal Dashboard changeset, all-or-nothing, and audits every attempt -- the
 * dashboard analog of {@code ViewsheetChangesetApplyService} (Redmine #76695).
 *
 * <p>Unlike the viewsheet area, EVERY verb here is compensable: a dashboard delete only removes a
 * registry binding, never the bound viewsheet's own content (see {@code
 * DashboardChangePlanService#resolveDashboardDelete}'s own description), so it is always queued for
 * rollback via re-creating the same binding. There is therefore no {@code
 * acknowledgeIrreversibleDelete} gate in this area at all.
 */
@Component
public class DashboardChangesetApplyService {
   @Autowired
   public DashboardChangesetApplyService(DashboardChangePlanService planService,
                                         RepositoryDashboardService repositoryDashboardService,
                                         DashboardManager dashboardManager,
                                         AdminBackupService backupService)
   {
      this.planService = planService;
      this.repositoryDashboardService = repositoryDashboardService;
      this.dashboardManager = dashboardManager;
      this.backupService = backupService;
   }

   /**
    * Resolves, gates on the plan hash, backs up, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409).
    * @throws IllegalArgumentException if {@code reviewOutcome} is blank while the plan contains any
    *         high-risk change (a dashboard delete).
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied.
    */
   public DashboardApplyResult apply(DashboardApplyRequest req, Principal user) throws Exception {
      APPLY_LOCK.lock();

      try {
         ResolvedPlan plan = planService.resolve(req, user);

         if(req.getPlanHash() == null || !plan.planHash().equals(req.getPlanHash())) {
            throw new AdminChangesetApplyService.PlanHashMismatchException(plan);
         }

         String reviewedTask;

         try {
            reviewedTask = TaskAuditToken.verify(req.getTaskToken(), plan.planHash());
         }
         catch(TaskAuditToken.TaskTokenException e) {
            throw new AdminChangesetApplyService.TaskTokenMismatchException(plan, e.getMessage());
         }

         if(plan.requiresAgentSignoff() &&
            (req.getReviewOutcome() == null || req.getReviewOutcome().trim().isEmpty()))
         {
            throw new IllegalArgumentException(
               "reviewOutcome: required because this changeset contains a high-risk change");
         }

         String txId = "dashboard-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         String reviewOutcome = req.getReviewOutcome();
         List<DashboardApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;
         // Whether the item that threw (if any) had already entered its own mutating call before
         // the throw -- only that case is a genuine partial-mutation risk that must force
         // STATUS_ROLLBACK_FAILED on its own; a throw that fires strictly before the mutating call
         // means the item was never touched, so it must not by itself override an otherwise
         // fully-verified rollback (bug 76856, mirroring bug 76808's DataSourceChangesetApplyService
         // fix).
         boolean unknownStateMutationEntered = false;
         List<DashboardChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            DashboardChangeRequest original = originals.get(i);
            String key = change.property();
            AtomicBoolean mutationEntered = new AtomicBoolean(false);

            try {
               applyOne(txId, reviewedTask, key, original, user, backupRef, reviewOutcome, results,
                       undoable, mutationEntered);
            }
            catch(Exception e) {
               results.add(new DashboardApplyOutcome(key, null, null,
                  AdminChangeRecord.STATUS_FAILED, messageOf(e)));
               unknownStateFailures.add(new RollbackFailure(key,
                  "state unknown: apply did not return a verifiable outcome (" + messageOf(e) + ")"));
               unknownStateMutationEntered = mutationEntered.get();
               failed = true;
               break;
            }

            if(AdminChangeRecord.STATUS_FAILED.equals(lastStatus(results))) {
               failed = true;
               break;
            }
         }

         if(!failed) {
            return new DashboardApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED,
                                            backupRef, Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> rollbackOwnFailures =
            rollback(txId, reviewedTask, undoable, backupRef, reviewOutcome, user);

         // An unknownStateFailures entry only forces rollback-failed when that item's own mutating
         // call had actually been entered (a real partial-mutation risk); if it never touched the
         // dashboard, it must not by itself override an otherwise fully-verified rollback.
         if(rollbackOwnFailures.isEmpty() && !unknownStateMutationEntered) {
            return new DashboardApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK,
                                            backupRef, Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollbackOwnFailures);
         LOG.error("Dashboard changeset {} rollback failed; units still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new DashboardApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                                         backupRef, Collections.unmodifiableList(results),
                                         Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, String key, DashboardChangeRequest original,
                         Principal user, String backupRef, String reviewOutcome,
                         List<DashboardApplyOutcome> results, List<Undo> undoable,
                         AtomicBoolean mutationEntered)
      throws Exception
   {
      String unitType = DashboardChangePlanService.requireUnitType("change", original.getUnitType());

      if(DashboardChangeRequest.UNIT_DASHBOARD.equals(unitType)) {
         String verb = DashboardChangePlanService.requireDashboardVerb("change", original.getVerb());

         if(DashboardChangeRequest.VERB_CREATE.equals(verb)) {
            applyDashboardCreate(txId, task, key, original, user, backupRef, reviewOutcome, results,
                                 undoable, mutationEntered);
         }
         else if(DashboardChangeRequest.VERB_UPDATE.equals(verb)) {
            applyDashboardUpdate(txId, task, key, original, user, backupRef, reviewOutcome, results,
                                 undoable, mutationEntered);
         }
         else {
            applyDashboardDelete(txId, task, key, original, user, backupRef, reviewOutcome, results,
                                 undoable, mutationEntered);
         }

         return;
      }

      applyDashboardFolderReorder(txId, task, key, original, user, backupRef, reviewOutcome, results,
                                  undoable, mutationEntered);
   }

   // ---------------------------------------------------------------- dashboard create

   private void applyDashboardCreate(String txId, String task, String key,
                                     DashboardChangeRequest original, Principal user,
                                     String backupRef, String reviewOutcome,
                                     List<DashboardApplyOutcome> results, List<Undo> undoable,
                                     AtomicBoolean mutationEntered)
      throws Exception
   {
      IdentityID owner = DashboardChangePlanService.parseOwner(original.getOwner());
      String name = original.getName();
      String registryName = DashboardChangePlanService.fixDashboardName(name, owner);
      DashboardRegistry registry = planService.registryFor(owner);

      if(registry.getDashboard(registryName) != null) {
         throw new IllegalArgumentException(
            "name: dashboard \"" + name + "\" already exists at apply time -- it may have been " +
            "created since preview");
      }

      NewRepositoryFolderRequest addReq = new NewRepositoryFolderRequest();
      addReq.setOwner(owner);
      mutationEntered.set(true);
      repositoryDashboardService.addDashboard(addReq, user);
      String placeholderName = addReq.getPath();

      RepositoryDashboardSettingsModel model = RepositoryDashboardSettingsModel.builder()
         .name(name)
         .oname(placeholderName)
         .description(original.getDescription())
         .viewsheet(original.getViewsheet())
         .enable(!Boolean.FALSE.equals(original.getEnable()))
         .visible(true)
         .permissions(null)
         .build();

      RepositoryDashboardSettingsModel after =
         repositoryDashboardService.setSettings(placeholderName, model, owner, user);
      boolean verified = after != null && registry.getDashboard(registryName) != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection = verified
         ? DashboardChangePlanService.projectDashboard(name, after.description(), after.viewsheet(),
                                                       after.enable())
         : null;
      results.add(new DashboardApplyOutcome(key, null, afterProjection, status,
         verified ? null : "dashboard not found under the requested name after create"));
      writeAudit(txId, task, key, AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_APPLY, null,
                afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.dashboardCreate(key, name, owner));
      }
   }

   // ---------------------------------------------------------------- dashboard update

   private void applyDashboardUpdate(String txId, String task, String key,
                                     DashboardChangeRequest original, Principal user,
                                     String backupRef, String reviewOutcome,
                                     List<DashboardApplyOutcome> results, List<Undo> undoable,
                                     AtomicBoolean mutationEntered)
      throws Exception
   {
      IdentityID owner = DashboardChangePlanService.parseOwner(original.getOwner());
      String oname = original.getOname();
      String registryOldName = DashboardChangePlanService.fixDashboardName(oname, owner);
      DashboardRegistry registry = planService.registryFor(owner);

      if(registry.getDashboard(registryOldName) == null) {
         throw new IllegalArgumentException(
            "oname: no dashboard found named \"" + oname + "\" at apply time -- it may have been " +
            "renamed or deleted since preview");
      }

      RepositoryDashboardSettingsModel current = repositoryDashboardService.getSettings(oname, owner, user);
      String name = original.getName();
      String newDisplayName = name != null ? name : oname;
      String mergedDescription = original.getDescription() != null
         ? normalizeClearedField(original.getDescription()) : current.description();
      String mergedViewsheet = original.getViewsheet() != null ? original.getViewsheet() : current.viewsheet();
      boolean mergedEnable = original.getEnable() != null ? original.getEnable() : current.enable();

      RepositoryDashboardSettingsModel model = RepositoryDashboardSettingsModel.builder()
         .name(newDisplayName)
         .oname(oname)
         .description(mergedDescription)
         .viewsheet(mergedViewsheet)
         .enable(mergedEnable)
         .visible(current.visible())
         .permissions(null)
         .build();

      String beforeProjection = DashboardChangePlanService.projectDashboard(
         oname, current.description(), current.viewsheet(), current.enable());
      mutationEntered.set(true);
      RepositoryDashboardSettingsModel after = repositoryDashboardService.setSettings(oname, model, owner, user);
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection = verified
         ? DashboardChangePlanService.projectDashboard(newDisplayName, after.description(),
                                                       after.viewsheet(), after.enable())
         : null;
      results.add(new DashboardApplyOutcome(key, beforeProjection, afterProjection, status,
         verified ? null : "dashboard update did not return a verified result"));
      String risk = newDisplayName.equals(oname) ? AdminChangeRecord.RISK_LOW : AdminChangeRecord.RISK_HIGH;
      writeAudit(txId, task, key, risk, AdminChangeRecord.ACTION_APPLY, beforeProjection,
                afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.dashboardUpdate(key, newDisplayName, owner, oname, current.description(),
                                           current.viewsheet(), current.enable()));
      }
   }

   // ---------------------------------------------------------------- dashboard delete

   private void applyDashboardDelete(String txId, String task, String key,
                                     DashboardChangeRequest original, Principal user,
                                     String backupRef, String reviewOutcome,
                                     List<DashboardApplyOutcome> results, List<Undo> undoable,
                                     AtomicBoolean mutationEntered)
      throws Exception
   {
      IdentityID owner = DashboardChangePlanService.parseOwner(original.getOwner());
      String oname = original.getOname();
      String registryName = DashboardChangePlanService.fixDashboardName(oname, owner);
      DashboardRegistry registry = planService.registryFor(owner);

      if(registry.getDashboard(registryName) == null) {
         throw new IllegalArgumentException(
            "oname: no dashboard found named \"" + oname + "\" at apply time -- it may have " +
            "already been deleted since preview");
      }

      RepositoryDashboardSettingsModel current = repositoryDashboardService.getSettings(oname, owner, user);
      String beforeProjection = DashboardChangePlanService.projectDashboard(
         oname, current.description(), current.viewsheet(), current.enable());

      mutationEntered.set(true);
      repositoryDashboardService.delete(registryName, owner);

      boolean verified = registry.getDashboard(registryName) == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new DashboardApplyOutcome(key, beforeProjection, null, status,
         verified ? null : "dashboard still present after delete"));
      writeAudit(txId, task, key, AdminChangeRecord.RISK_HIGH, AdminChangeRecord.ACTION_APPLY,
                beforeProjection, null, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.dashboardDelete(key, oname, owner, current.description(),
                                           current.viewsheet(), current.enable(),
                                           current.permissions()));
      }
   }

   // ---------------------------------------------------------------- dashboard folder reorder

   private void applyDashboardFolderReorder(String txId, String task, String key,
                                            DashboardChangeRequest original, Principal user,
                                            String backupRef, String reviewOutcome,
                                            List<DashboardApplyOutcome> results, List<Undo> undoable,
                                            AtomicBoolean mutationEntered)
      throws Exception
   {
      IdentityID owner = DashboardChangePlanService.parseOwner(original.getOwner());
      List<String> requested = original.getDashboards();
      List<String> before = readFolderOrderAtApply(owner, user);
      String beforeProjection = String.join("|", before);

      mutationEntered.set(true);
      writeFolderOrder(owner, requested, user);

      List<String> after = readFolderOrderAtApply(owner, user);
      String afterProjection = String.join("|", after);
      boolean verified = after.equals(requested);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new DashboardApplyOutcome(key, beforeProjection, afterProjection, status,
         verified ? null : "dashboard folder order did not verify after reorder"));
      writeAudit(txId, task, key, AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_APPLY,
                beforeProjection, afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.folderReorder(key, owner, before));
      }
   }

   private List<String> readFolderOrderAtApply(IdentityID owner, Principal user) throws Exception {
      if(owner == null) {
         return repositoryDashboardService.getDashboardFolderSettings(user).dashboards();
      }

      return planService.readFolderOrder(owner);
   }

   private void writeFolderOrder(IdentityID owner, List<String> requested, Principal user)
      throws Exception
   {
      if(owner == null) {
         RepositoryFolderDashboardSettingsModel model = RepositoryFolderDashboardSettingsModel.builder()
            .dashboards(requested)
            .permissions(null)
            .build();
         repositoryDashboardService.setDashboardFolderSettings(model, user);
         return;
      }

      Identity identity = new DefaultIdentity(owner, Identity.USER);
      List<String> all = new ArrayList<>(Arrays.asList(dashboardManager.getDashboards(identity)));
      all.sort(Comparator.comparingInt(requested::indexOf));
      dashboardManager.setDashboards(identity, all.toArray(new String[0]));
   }

   // ---------------------------------------------------------------- rollback

   /** Undoes verified changes newest-first -- every verb in this area is compensable, so nothing is
    * ever excluded from {@code undoable}. */
   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String backupRef, String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            undo.rollback(this, txId, task, backupRef, reviewOutcome, user, failures);
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.key, messageOf(e)));
         }
      }

      return failures;
   }

   /** Undo of a create: delete the just-created binding. */
   private void rollbackDashboardCreate(Undo undo, String txId, String task, String backupRef,
                                        String reviewOutcome, Principal user,
                                        List<RollbackFailure> failures) throws Exception
   {
      String registryName = DashboardChangePlanService.fixDashboardName(undo.name, undo.owner);
      repositoryDashboardService.delete(registryName, undo.owner);
      DashboardRegistry registry = planService.registryFor(undo.owner);
      boolean verified = registry.getDashboard(registryName) == null;
      writeAudit(txId, task, undo.key, AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_ROLLBACK,
                null, null, verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key, "rollback of create did not remove the dashboard"));
      }
   }

   /** Undo of an update/rename: write the captured PRE-apply fields straight back. */
   private void rollbackDashboardUpdate(Undo undo, String txId, String task, String backupRef,
                                        String reviewOutcome, Principal user,
                                        List<RollbackFailure> failures) throws Exception
   {
      RepositoryDashboardSettingsModel model = RepositoryDashboardSettingsModel.builder()
         .name(undo.beforeOname)
         .oname(undo.name)
         .description(undo.beforeDescription)
         .viewsheet(undo.beforeViewsheet)
         .enable(undo.beforeEnable)
         .visible(true)
         .permissions(null)
         .build();
      RepositoryDashboardSettingsModel reread =
         repositoryDashboardService.setSettings(undo.name, model, undo.owner, user);
      boolean verified = reread != null;
      writeAudit(txId, task, undo.key, AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_ROLLBACK,
                null, null, verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of update did not restore the prior name/description/viewsheet/enable state"));
      }
   }

   /** Undo of a delete: re-create the same binding via the same two-step create chain {@link
    * #applyDashboardCreate} uses. Compensable because delete never touches the bound viewsheet's own
    * content (see {@code DashboardChangePlanService#resolveDashboardDelete}). */
   private void rollbackDashboardDelete(Undo undo, String txId, String task, String backupRef,
                                        String reviewOutcome, Principal user,
                                        List<RollbackFailure> failures) throws Exception
   {
      NewRepositoryFolderRequest addReq = new NewRepositoryFolderRequest();
      addReq.setOwner(undo.owner);
      repositoryDashboardService.addDashboard(addReq, user);
      String placeholderName = addReq.getPath();

      // Bug fix (review finding): addDashboard() above unconditionally grants a fresh "grant-all"
      // permission (Permission.updateGrantAllByOrg) to the placeholder, since it has no way to know
      // this is a re-creation rather than a genuinely new dashboard. Passing undo.beforePermissions
      // here (rather than null) makes setSettings's own permission-restore branch run --
      // RepositoryDashboardService.setSettings applies a non-null permissions model whenever
      // `permissions.changed() || renamed` (renamed is always true here: placeholder -> undo.name)
      // -- so the re-created dashboard's ACL matches what existed before the delete, not the
      // addDashboard default. Without this, a delete+rollback (e.g. because a LATER, unrelated
      // change in the same all-or-nothing apply batch failed) would silently reopen a
      // previously-restricted dashboard to the entire org.
      RepositoryDashboardSettingsModel model = RepositoryDashboardSettingsModel.builder()
         .name(undo.name)
         .oname(placeholderName)
         .description(undo.beforeDescription)
         .viewsheet(undo.beforeViewsheet)
         .enable(undo.beforeEnable)
         .visible(true)
         .permissions(undo.beforePermissions)
         .build();
      RepositoryDashboardSettingsModel reread =
         repositoryDashboardService.setSettings(placeholderName, model, undo.owner, user);
      boolean verified = reread != null;
      writeAudit(txId, task, undo.key, AdminChangeRecord.RISK_HIGH, AdminChangeRecord.ACTION_ROLLBACK,
                null, null, verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key, "rollback of delete did not re-create the dashboard"));
      }
   }

   private void rollbackFolderReorder(Undo undo, String txId, String task, String backupRef,
                                      String reviewOutcome, Principal user,
                                      List<RollbackFailure> failures) throws Exception
   {
      writeFolderOrder(undo.owner, undo.beforeOrder, user);
      List<String> reread = readFolderOrderAtApply(undo.owner, user);
      boolean verified = reread.equals(undo.beforeOrder);
      writeAudit(txId, task, undo.key, AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_ROLLBACK,
                null, null, verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key, "rollback of reorder did not restore the prior order"));
      }
   }

   private void writeAudit(String txId, String task, String key, String risk, String adminAction,
                           String before, String after, String status, String backupRef,
                           String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(key);
         record.setObjectType(ActionRecord.OBJECT_TYPE_DASHBOARD);
         record.setBeforeValue(before);
         record.setAfterValue(after);
         record.setAction(adminAction);
         record.setStatus(status);
         record.setRiskLevel(risk);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_STORAGE);
         record.setBackupRef(backupRef);
         record.setReviewOutcome(reviewOutcome);
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new java.sql.Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         LOG.error("Failed to write dashboard admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static String lastStatus(List<DashboardApplyOutcome> results) {
      return results.isEmpty() ? null : results.get(results.size() - 1).status();
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   /** An explicit "" from the caller means "clear this field" -- same convention as the viewsheet
    * area's own {@code normalizeClearedField}. */
   private static String normalizeClearedField(String raw) {
      return (raw != null && raw.isEmpty()) ? null : raw;
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   /** One undo descriptor built during apply, replayed in reverse by {@link #rollback}. */
   private static final class Undo {
      static Undo dashboardCreate(String key, String name, IdentityID owner) {
         Undo u = new Undo(key, Kind.DASHBOARD_CREATE);
         u.name = name;
         u.owner = owner;
         return u;
      }

      static Undo dashboardUpdate(String key, String currentName, IdentityID owner,
                                  String beforeOname, String beforeDescription,
                                  String beforeViewsheet, boolean beforeEnable)
      {
         Undo u = new Undo(key, Kind.DASHBOARD_UPDATE);
         u.name = currentName;
         u.owner = owner;
         u.beforeOname = beforeOname;
         u.beforeDescription = beforeDescription;
         u.beforeViewsheet = beforeViewsheet;
         u.beforeEnable = beforeEnable;
         return u;
      }

      static Undo dashboardDelete(String key, String name, IdentityID owner,
                                  String beforeDescription, String beforeViewsheet,
                                  boolean beforeEnable, ResourcePermissionModel beforePermissions)
      {
         Undo u = new Undo(key, Kind.DASHBOARD_DELETE);
         u.name = name;
         u.owner = owner;
         u.beforeDescription = beforeDescription;
         u.beforeViewsheet = beforeViewsheet;
         u.beforeEnable = beforeEnable;
         u.beforePermissions = beforePermissions;
         return u;
      }

      static Undo folderReorder(String key, IdentityID owner, List<String> beforeOrder) {
         Undo u = new Undo(key, Kind.FOLDER_REORDER);
         u.owner = owner;
         u.beforeOrder = beforeOrder;
         return u;
      }

      private Undo(String key, Kind kind) {
         this.key = key;
         this.kind = kind;
      }

      void rollback(DashboardChangesetApplyService svc, String txId, String task, String backupRef,
                    String reviewOutcome, Principal user, List<RollbackFailure> failures)
         throws Exception
      {
         switch(kind) {
         case DASHBOARD_CREATE:
            svc.rollbackDashboardCreate(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         case DASHBOARD_UPDATE:
            svc.rollbackDashboardUpdate(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         case DASHBOARD_DELETE:
            svc.rollbackDashboardDelete(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         case FOLDER_REORDER:
            svc.rollbackFolderReorder(this, txId, task, backupRef, reviewOutcome, user, failures);
            break;
         }
      }

      enum Kind { DASHBOARD_CREATE, DASHBOARD_UPDATE, DASHBOARD_DELETE, FOLDER_REORDER }

      final String key;
      final Kind kind;
      String name;
      IdentityID owner;
      String beforeOname;
      String beforeDescription;
      String beforeViewsheet;
      boolean beforeEnable;
      ResourcePermissionModel beforePermissions;
      List<String> beforeOrder;
   }

   private static final Logger LOG = LoggerFactory.getLogger(DashboardChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final DashboardChangePlanService planService;
   private final RepositoryDashboardService repositoryDashboardService;
   private final DashboardManager dashboardManager;
   private final AdminBackupService backupService;
}
