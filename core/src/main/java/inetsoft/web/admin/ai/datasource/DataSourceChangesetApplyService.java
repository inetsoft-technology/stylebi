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
package inetsoft.web.admin.ai.datasource;

import inetsoft.web.admin.datasource.*;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.admin.ai.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Applies a whole data-source changeset, all-or-nothing, and audits every attempt -- the
 * data-sources analog of {@code inetsoft.web.admin.ai.AdminChangesetApplyService} and (within this
 * run) {@code PermissionChangesetApplyService}/{@code ProviderChangesetApplyService}, replicated
 * rather than shared (01-spec.md section 6, carry-forward item 5).
 *
 * <p>Every verb in this area is {@code snapshotScope: storage} unconditionally (01-spec.md section
 * 4/7), so the Tier-2 backup is taken synchronously here, before any change is attempted -- and
 * every plan is {@code requiresAgentSignoff: true} unconditionally, so {@code reviewOutcome} is
 * always required, matching every storage-scoped prior area.
 *
 * <p>Both id resolution (by name, never a captured id, section 2) and the JDBC password merge
 * (section 0.1) and the delete dependency preflight (section 0.2) are re-run HERE, fresh, at apply
 * time -- never reusing anything computed at preview time except the plan hash comparison itself,
 * matching the "authoritative state captured during apply, not the preview-time snapshot" discipline
 * every prior area's own apply service already follows.
 */
@Component
public class DataSourceChangesetApplyService {
   @Autowired
   public DataSourceChangesetApplyService(DataSourceChangePlanService planService,
                                          DataSourceService dataSourceService,
                                          AdminBackupService backupService)
   {
      this.planService = planService;
      this.dataSourceService = dataSourceService;
      this.backupService = backupService;
   }

   /**
    * Resolves, gates on the plan hash, backs up, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409) -- reused verbatim, per 01-spec.md section 6.
    * @throws IllegalArgumentException if {@code reviewOutcome} is blank, or {@code
    *         acknowledgeIrreversibleDelete} is not exactly {@code true} while the plan contains a
    *         delete entry (section 4/11 -- delete is declared non-compensable in this cut).
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied.
    */
   public DataSourceApplyResult apply(DataSourceApplyRequest req, Principal user) throws Exception {
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

         boolean hasDelete = req.getChanges().stream()
            .anyMatch(c -> DataSourceChangeRequest.VERB_DELETE.equals(
               DataSourceChangePlanService.requireVerb("change", c.getVerb())));

         if(hasDelete && !Boolean.TRUE.equals(req.getAcknowledgeIrreversibleDelete())) {
            throw new IllegalArgumentException(
               "acknowledgeIrreversibleDelete: must be true because this changeset contains a " +
               "delete -- deleting a data source has NO live inverse in this cut (section 4); the " +
               "Tier-2 snapshot taken for this apply is the only recovery path, not merely the " +
               "path of last resort");
         }

         String txId = "datasource-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         String reviewOutcome = req.getReviewOutcome();
         List<DataSourceApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;
         // Whether the item that threw (if any) had already entered its own mutating call
         // (createDataSourceFolder/updateDataSource/deleteDataSource) before the throw -- only that
         // case is a genuine partial-mutation risk that must force STATUS_ROLLBACK_FAILED on its
         // own; a throw that fires strictly before the mutating call means the item was never
         // touched, so it must not by itself override an otherwise fully-verified rollback (bug
         // 76808, mirroring bug 76567's LicenseChangesetApplyService fix).
         boolean unknownStateMutationEntered = false;

         List<DataSourceChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            DataSourceChangeRequest original = originals.get(i);
            String key = change.property();
            AtomicBoolean mutationEntered = new AtomicBoolean(false);

            try {
               applyOne(txId, reviewedTask, key, original, user, backupRef, reviewOutcome, results,
                       undoable, mutationEntered);
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- must never
               // be treated as rolled back. Same rule every prior area's apply service follows.
               results.add(new DataSourceApplyOutcome(key, null, null,
                  AdminChangeRecord.STATUS_FAILED, messageOf(e), null));
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
            return new DataSourceApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED,
                                             backupRef, Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> rollbackOwnFailures =
            rollback(txId, reviewedTask, undoable, backupRef, reviewOutcome, user);

         // An unknownStateFailures entry only forces rollback-failed when that item's own mutating
         // call had actually been entered (a real partial-mutation risk); if it never touched the
         // data source, it must not by itself override an otherwise fully-verified rollback.
         if(rollbackOwnFailures.isEmpty() && !unknownStateMutationEntered) {
            return new DataSourceApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK,
                                             backupRef, Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollbackOwnFailures);
         LOG.error("Data source changeset {} rollback failed; data sources still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new DataSourceApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                                          backupRef, Collections.unmodifiableList(results),
                                          Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, String key, DataSourceChangeRequest original,
                         Principal user, String backupRef, String reviewOutcome,
                         List<DataSourceApplyOutcome> results, List<Undo> undoable,
                         AtomicBoolean mutationEntered)
      throws Exception
   {
      String verb = DataSourceChangePlanService.requireVerb("change", original.getVerb());

      if(DataSourceChangeRequest.VERB_UPDATE.equals(verb)) {
         applyUpdate(txId, task, key, original, user, backupRef, reviewOutcome, results, undoable,
                    mutationEntered);
      }
      else if(DataSourceChangeRequest.VERB_CREATE.equals(verb)) {
         applyFolderCreate(txId, task, key, original, user, backupRef, reviewOutcome, results,
                           undoable, mutationEntered);
      }
      else {
         applyDelete(txId, task, key, original, user, backupRef, reviewOutcome, results, undoable,
                    mutationEntered);
      }
   }

   // ---------------------------------------------------------------- folder create

   /** Bug 76599, Gap 2a: creates the requested folder with no other mutation. Metadata-only
    * ({@code RISK_LOW}, matching {@code DataSourceChangePlanService#resolveFolderCreate}'s own
    * classification). Rollback removes only the originally-requested leaf path -- any ancestor
    * folder {@link DataSourceService#createDataSourceFolder} auto-created as a side effect is
    * NOT individually rolled back, a known, disclosed incompleteness matching viewsheets' own
    * folder-create rollback contract (plugin/admin/CLAUDE.md's Viewsheets section), not a bug. */
   private void applyFolderCreate(String txId, String task, String key,
                                  DataSourceChangeRequest original, Principal user,
                                  String backupRef, String reviewOutcome,
                                  List<DataSourceApplyOutcome> results, List<Undo> undoable,
                                  AtomicBoolean mutationEntered)
      throws Exception
   {
      String folderPath = original.getFolderPath();
      String beforeProjection = "(does not exist)";

      mutationEntered.set(true);
      dataSourceService.createDataSourceFolder(folderPath, user);

      boolean verified = dataSourceService.dataSourceFolderExists(folderPath);
      String afterProjection = "(created)";
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new DataSourceApplyOutcome(key, beforeProjection, afterProjection, status,
         verified ? null : "folder not found after create", null));
      writeAudit(txId, task, key, ActionRecord.OBJECT_TYPE_FOLDER, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, afterProjection, status,
                backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.folderCreated(key, folderPath));
      }
   }

   // ---------------------------------------------------------------- update

   private void applyUpdate(String txId, String task, String name, DataSourceChangeRequest original,
                            Principal user, String backupRef, String reviewOutcome,
                            List<DataSourceApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
      throws Exception
   {
      // Section 2/6: re-resolve id fresh, from the name, against the live state AT APPLY TIME --
      // never a preview-captured id, which may have gone cold in DataSourceApiService's own
      // 1-hour idle-eviction cache by the time a human finishes reviewing the plan.
      String id = planService.resolveCurrentId("apply." + name, name, user);
      DataSourceProperties current = dataSourceService.getDataSource(id, user);
      DataSourceProperties proposed;
      DataSourceProperties before;

      if(current instanceof JdbcDataSourceProperties) {
         JdbcDataSourceProperties currentJdbc = (JdbcDataSourceProperties) current;
         proposed = planService.buildProposedJdbc("apply." + name, id, name, currentJdbc,
                                                   original.getSpec(), user);
         before = planService.buildBeforeJdbc(id, currentJdbc, user);
      }
      else {
         proposed = planService.buildProposedTabular("apply." + name, name,
            (TabularDataSourceProperties) current, original.getSpec());
         before = current;
      }

      String beforeProjection = DataSourceProjection.project(before);
      mutationEntered.set(true);
      dataSourceService.updateDataSource(id, proposed, user);

      // Re-resolve by the NEW name (a rename may have just happened) to confirm the change landed
      // and build afterValue -- password merged the same way for this read too (section 6 step 4),
      // so afterValue never carries the masked literal either.
      String afterName = proposed.getName() == null ? name : proposed.getName();
      String newId = planService.resolveCurrentId("apply." + name, afterName, user);
      DataSourceProperties after = dataSourceService.getDataSource(newId, user);
      DataSourceProperties afterMerged = after instanceof JdbcDataSourceProperties
         ? planService.buildBeforeJdbc(newId, (JdbcDataSourceProperties) after, user) : after;
      String afterProjection = DataSourceProjection.project(afterMerged);
      boolean verified = afterProjection.equals(DataSourceProjection.project(proposed));
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new DataSourceApplyOutcome(name, beforeProjection, afterProjection, status,
         verified ? null : "data source did not match the proposed value after update", null));
      writeAudit(txId, task, name, ActionRecord.OBJECT_TYPE_DATASOURCE, AdminChangeRecord.RISK_HIGH,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, afterProjection, status,
                backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.updated(name, before));
      }
   }

   // ---------------------------------------------------------------- delete

   private void applyDelete(String txId, String task, String name, DataSourceChangeRequest original,
                            Principal user, String backupRef, String reviewOutcome,
                            List<DataSourceApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
      throws Exception
   {
      boolean force = Boolean.TRUE.equals(original.getForce());
      String id = planService.resolveCurrentId("apply." + name, name, user);
      DataSourceProperties current = dataSourceService.getDataSource(id, user);
      DataSourceProperties before = current instanceof JdbcDataSourceProperties
         ? planService.buildBeforeJdbc(id, (JdbcDataSourceProperties) current, user) : current;
      String beforeProjection = DataSourceProjection.project(before);

      // Section 0.2 step 2a: re-run the dependency preflight against live state AT APPLY TIME too
      // -- a concurrent change since preview could have added a new dependency.
      List<AssetObject> dependencies = planService.findDependencies(name);
      DataSourceChangePlanService.requireForceIfDependent("apply." + name, name, dependencies, force);
      String advisory = dependencies.isEmpty() ? null :
         "deleted with force: true despite " + dependencies.size() + " dependent asset(s) still " +
         "referencing this data source: " + DataSourceProjection.projectDependencies(dependencies);

      mutationEntered.set(true);
      dataSourceService.deleteDataSource(id, force, user);

      boolean verified;

      try {
         DataSourceList after = dataSourceService.getDataSources(name, user);
         verified = after.getDataSources() == null || after.getDataSources().isEmpty();
      }
      catch(Exception e) {
         verified = false;
      }

      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new DataSourceApplyOutcome(name, beforeProjection, null, status,
         verified ? null : "data source still present after delete", advisory));
      writeAudit(txId, task, name, ActionRecord.OBJECT_TYPE_DATASOURCE, AdminChangeRecord.RISK_HIGH,
                AdminChangeRecord.ACTION_APPLY, beforeProjection, null, status, backupRef,
                reviewOutcome, user);

      // Delete has NO live inverse in this cut (section 4) -- never added to `undoable`, regardless
      // of verified/failed. Its own outcome is always applied or failed, never individually rolled
      // back; other entries in the same plan still roll back normally around it.
   }

   // ---------------------------------------------------------------- rollback

   /** Undoes verified UPDATE/folder-CREATE changes newest-first, attempting all of them and
    * collecting any failures. Delete entries are never present here (section 4/6) -- they were
    * never added to {@code undoable}. */
   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String backupRef, String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            if(undo.kind == Undo.Kind.FOLDER_CREATED) {
               rollbackFolderCreate(undo, txId, task, backupRef, reviewOutcome, user, failures);
            }
            else {
               rollbackUpdate(undo, txId, task, backupRef, reviewOutcome, user, failures);
            }
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.name, messageOf(e)));
         }
      }

      return failures;
   }

   private void rollbackUpdate(Undo undo, String txId, String task, String backupRef,
                               String reviewOutcome, Principal user,
                               List<RollbackFailure> failures)
      throws Exception
   {
      String id = planService.resolveCurrentId("rollback." + undo.name, undo.name, user);
      dataSourceService.updateDataSource(id, undo.before, user);
      DataSourceProperties reread = dataSourceService.getDataSource(id, user);
      DataSourceProperties rereadMerged = reread instanceof JdbcDataSourceProperties
         ? planService.buildBeforeJdbc(id, (JdbcDataSourceProperties) reread, user) : reread;
      boolean verified = DataSourceProjection.project(rereadMerged)
         .equals(DataSourceProjection.project(undo.before));
      writeAudit(txId, task, undo.name, ActionRecord.OBJECT_TYPE_DATASOURCE,
                AdminChangeRecord.RISK_HIGH, AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.name,
            "rollback of update did not restore the prior value"));
      }
   }

   /** Removes only the originally-requested leaf folder path -- any ancestor folder {@link
    * DataSourceApiService#createDataSourceFolder} auto-created as a side effect is NOT
    * individually rolled back, matching viewsheets' own disclosed-incomplete folder-create
    * rollback contract (bug 76599, Gap 2a). */
   private void rollbackFolderCreate(Undo undo, String txId, String task, String backupRef,
                                     String reviewOutcome, Principal user,
                                     List<RollbackFailure> failures)
      throws Exception
   {
      dataSourceService.removeDataSourceFolder(undo.folderPath);
      boolean verified = !dataSourceService.dataSourceFolderExists(undo.folderPath);
      writeAudit(txId, task, undo.name, ActionRecord.OBJECT_TYPE_FOLDER, AdminChangeRecord.RISK_LOW,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.name,
            "rollback of folder create did not remove the folder"));
      }
   }

   private void writeAudit(String txId, String task, String key, String objectType,
                           String riskLevel, String adminAction, String before, String after,
                           String status, String backupRef, String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(key);
         record.setObjectType(objectType);
         record.setBeforeValue(before);
         record.setAfterValue(after);
         record.setAction(adminAction);
         record.setStatus(status);
         record.setRiskLevel(riskLevel);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_STORAGE);
         record.setBackupRef(backupRef);
         record.setReviewOutcome(reviewOutcome);
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         // An audit write must never replace the real outcome -- same rule every prior area's apply
         // service follows. Also: section 8's own finding -- DataSourceApiService's OWN audit
         // stream (ActionRecord, fired inline inside updateDataSource/deleteDataSource) always
         // reports SUCCESS regardless of whether the call threw, so THIS record -- built
         // independently from this service's own knowledge of whether the call threw -- is the
         // only reliable audit record of failure for this area. Never treat the wrapped API's own
         // ActionRecord as a cross-check for this one.
         LOG.error("Failed to write data source admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static String lastStatus(List<DataSourceApplyOutcome> results) {
      return results.isEmpty() ? null : results.get(results.size() - 1).status();
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   /** One undo descriptor built during apply, replayed in reverse by {@link #rollback}. Only ever
    * built for a verified UPDATE (section 4/6) or a verified folder CREATE (bug 76599, Gap 2a). */
   private static final class Undo {
      enum Kind { UPDATED, FOLDER_CREATED }

      static Undo updated(String name, DataSourceProperties before) {
         Undo u = new Undo(name, Kind.UPDATED);
         u.before = before;
         return u;
      }

      static Undo folderCreated(String key, String folderPath) {
         Undo u = new Undo(key, Kind.FOLDER_CREATED);
         u.folderPath = folderPath;
         return u;
      }

      private Undo(String name, Kind kind) {
         this.name = name;
         this.kind = kind;
      }

      final String name;
      final Kind kind;
      DataSourceProperties before;
      String folderPath;
   }

   private static final Logger LOG = LoggerFactory.getLogger(DataSourceChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale and same JVM-local-only
    * limitation as every prior area's own lock. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final DataSourceChangePlanService planService;
   private final DataSourceService dataSourceService;
   private final AdminBackupService backupService;
}
