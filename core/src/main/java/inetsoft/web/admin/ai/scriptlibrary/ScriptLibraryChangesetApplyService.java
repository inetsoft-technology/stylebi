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
package inetsoft.web.admin.ai.scriptlibrary;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.Permission;
import inetsoft.sree.security.ResourceType;
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
 * Applies a whole Script Library changeset ({@code create}/{@code rename}/{@code update}/
 * {@code delete}), all-or-nothing, and audits every attempt -- the Script Library analog of
 * {@code RecycleBinChangesetApplyService}.
 *
 * <p>Every verb here has a real, working rollback -- including {@code delete}, unlike Viewsheets'
 * own non-compensable delete -- because a script is just a name/description/body-text triple with
 * no other product state riding on the physical deletion; capturing that triple before mutating
 * and replaying it via the same {@code create}/{@code update}/{@code rename} primitives is exact,
 * the same "capture the bytes, restore them on rollback" shape Custom Shapes' own {@code delete}
 * already establishes for this plugin. {@code delete} is still always classified {@code RISK_HIGH}
 * (see {@code ScriptLibraryChangePlanService}'s own javadoc for why) and always requires
 * {@code acknowledgeIrreversibleDelete: true}, even though its own content is fully recoverable.
 * A permission GRANT on a deleted script (if any) is left as a dangling, harmless entry rather
 * than removed and reconstructed -- consistent with this area not touching permissions at all on
 * {@code delete}, only on {@code rename} (which must, since a grant is keyed by the resource's own
 * name and would otherwise silently apply to nothing).
 */
@Component
public class ScriptLibraryChangesetApplyService {
   @Autowired
   public ScriptLibraryChangesetApplyService(ScriptLibraryChangePlanService planService,
                                             ScriptLibraryService scriptLibraryService,
                                             LibManagerProvider libManagerProvider,
                                             AdminBackupService backupService)
   {
      this.planService = planService;
      this.scriptLibraryService = scriptLibraryService;
      this.libManagerProvider = libManagerProvider;
      this.backupService = backupService;
   }

   /**
    * Resolves, gates on the plan hash, backs up, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409).
    * @throws IllegalArgumentException if {@code reviewOutcome} is blank while the plan requires
    *         agent signoff, or {@code acknowledgeIrreversibleDelete} is not exactly {@code true}
    *         while the plan contains a high-risk {@code delete} entry.
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied.
    */
   public ScriptLibraryApplyResult apply(ScriptLibraryApplyRequest req, Principal user)
      throws Exception
   {
      APPLY_LOCK.lock();

      try {
         ResolvedPlan plan = planService.resolve(req, user);

         if(req.getPlanHash() == null || !plan.planHash().equals(req.getPlanHash())) {
            throw new AdminChangesetApplyService.PlanHashMismatchException(plan);
         }

         if(plan.requiresAgentSignoff() &&
            (req.getReviewOutcome() == null || req.getReviewOutcome().trim().isEmpty()))
         {
            throw new IllegalArgumentException(
               "reviewOutcome: required because this changeset contains a high-risk change");
         }

         boolean hasHighRisk = plan.changes().stream()
            .anyMatch(c -> AdminChangeRecord.RISK_HIGH.equals(c.risk()));

         if(hasHighRisk && !Boolean.TRUE.equals(req.getAcknowledgeIrreversibleDelete())) {
            throw new IllegalArgumentException(
               "acknowledgeIrreversibleDelete: must be true because this changeset contains a " +
               "delete entry -- the script's own content is captured and would be restored if " +
               "this change is later rolled back, but anything calling it by name will break for " +
               "as long as the deletion stands");
         }

         String txId = "scriptlibrary-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         String reviewOutcome = req.getReviewOutcome();
         LibManager lib = libManagerProvider.getManager(user);
         List<ScriptLibraryApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         List<ScriptLibraryChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            ScriptLibraryChangeRequest original = originals.get(i);
            String key = change.property();

            AtomicBoolean mutationEntered = new AtomicBoolean(false);

            try {
               applyOne(txId, plan.task(), key, original, user, lib, backupRef, reviewOutcome,
                       results, undoable, mutationEntered);
            }
            catch(Exception e) {
               results.add(new ScriptLibraryApplyOutcome(key, null, null,
                  AdminChangeRecord.STATUS_FAILED, messageOf(e), null));

               if(mutationEntered.get()) {
                  unknownStateFailures.add(new RollbackFailure(key,
                     "state unknown: apply did not return a verifiable outcome (" + messageOf(e) + ")"));
               }

               failed = true;
               break;
            }

            if(AdminChangeRecord.STATUS_FAILED.equals(lastStatus(results))) {
               failed = true;
               break;
            }
         }

         if(!failed) {
            return new ScriptLibraryApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED,
                                                backupRef, Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollback(txId, plan.task(), undoable, lib, backupRef, reviewOutcome, user));

         if(failures.isEmpty()) {
            return new ScriptLibraryApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK,
                                                backupRef, Collections.unmodifiableList(results), null);
         }

         LOG.error("Script library changeset {} rollback failed; entries still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new ScriptLibraryApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                                             backupRef, Collections.unmodifiableList(results),
                                             Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, String key, ScriptLibraryChangeRequest original,
                         Principal user, LibManager lib, String backupRef, String reviewOutcome,
                         List<ScriptLibraryApplyOutcome> results, List<Undo> undoable,
                         AtomicBoolean mutationEntered)
      throws Exception
   {
      String verb = ScriptLibraryChangePlanService.requireVerb("change", original.getVerb());

      switch(verb) {
         case ScriptLibraryChangeRequest.VERB_CREATE ->
            applyCreate(txId, task, key, original, user, lib, backupRef, reviewOutcome, results,
                       undoable, mutationEntered);
         case ScriptLibraryChangeRequest.VERB_RENAME ->
            applyRename(txId, task, key, original, user, lib, backupRef, reviewOutcome, results,
                       undoable, mutationEntered);
         case ScriptLibraryChangeRequest.VERB_UPDATE ->
            applyUpdate(txId, task, key, original, user, lib, backupRef, reviewOutcome, results,
                       undoable, mutationEntered);
         default ->
            applyDelete(txId, task, key, original, user, lib, backupRef, reviewOutcome, results,
                       undoable, mutationEntered);
      }
   }

   // ---------------------------------------------------------------- create

   private void applyCreate(String txId, String task, String key, ScriptLibraryChangeRequest original,
                            Principal user, LibManager lib, String backupRef, String reviewOutcome,
                            List<ScriptLibraryApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
      throws Exception
   {
      String name = original.getName();

      if(scriptLibraryService.exists(name, user)) {
         throw new IllegalArgumentException(
            "name: a script library entry named \"" + name + "\" was created since preview");
      }

      String description = original.getDescription() == null ? "" : original.getDescription();
      String text = original.getText() == null ? "" : original.getText();
      mutationEntered.set(true);
      lib.setScript(name, text);

      if(!description.isEmpty()) {
         lib.setScriptComment(name, description);
      }

      lib.save();

      boolean verified = scriptLibraryService.exists(name, user);
      String after = verified ? ScriptLibraryChangePlanService.project(name, description, text) : null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ScriptLibraryApplyOutcome(key, null, after, status,
         verified ? null : "script library entry not found after create", null));
      writeAudit(txId, task, key, AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_APPLY,
                null, after, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.create(key, name));
      }
   }

   // ---------------------------------------------------------------- rename

   private void applyRename(String txId, String task, String key, ScriptLibraryChangeRequest original,
                            Principal user, LibManager lib, String backupRef, String reviewOutcome,
                            List<ScriptLibraryApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
      throws Exception
   {
      String name = original.getName();
      String newName = original.getNewName();
      ScriptLibraryEntryDetail entry = scriptLibraryService.requireEntry(name, user);

      if(scriptLibraryService.exists(newName, user)) {
         throw new IllegalArgumentException(
            "newName: a script library entry named \"" + newName + "\" was created since preview");
      }

      String before = ScriptLibraryChangePlanService.project(entry);
      Permission permission = scriptLibraryService.securityEngine.getPermission(ResourceType.SCRIPT, name);
      mutationEntered.set(true);
      lib.renameScript(name, newName);
      lib.save();

      if(permission != null) {
         scriptLibraryService.securityEngine.removePermission(ResourceType.SCRIPT, name);
         scriptLibraryService.securityEngine.setPermission(ResourceType.SCRIPT, newName, permission);
      }

      boolean verified = !scriptLibraryService.exists(name, user) &&
         scriptLibraryService.exists(newName, user);
      String after = verified ?
         ScriptLibraryChangePlanService.project(newName, entry.description(), entry.text()) : null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ScriptLibraryApplyOutcome(key, before, after, status,
         verified ? null : "script library entry not found under the new name after rename", null));
      writeAudit(txId, task, key, AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_APPLY,
                before, after, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.rename(key, newName, name, permission));
      }
   }

   // ---------------------------------------------------------------- update

   private void applyUpdate(String txId, String task, String key, ScriptLibraryChangeRequest original,
                            Principal user, LibManager lib, String backupRef, String reviewOutcome,
                            List<ScriptLibraryApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
      throws Exception
   {
      String name = original.getName();
      ScriptLibraryEntryDetail entry = scriptLibraryService.requireEntry(name, user);
      String before = ScriptLibraryChangePlanService.project(entry);
      mutationEntered.set(true);
      lib.setScriptComment(name, original.getDescription());
      lib.save();

      boolean verified = Tool.equals(lib.getScriptComment(name), original.getDescription());
      String after = verified ?
         ScriptLibraryChangePlanService.project(name, original.getDescription(), entry.text()) : null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ScriptLibraryApplyOutcome(key, before, after, status,
         verified ? null : "description did not change after update", null));
      writeAudit(txId, task, key, AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_APPLY,
                before, after, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.update(key, name, entry.description()));
      }
   }

   // ---------------------------------------------------------------- delete

   private void applyDelete(String txId, String task, String key, ScriptLibraryChangeRequest original,
                            Principal user, LibManager lib, String backupRef, String reviewOutcome,
                            List<ScriptLibraryApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
      throws Exception
   {
      String name = original.getName();
      ScriptLibraryEntryDetail entry = scriptLibraryService.requireEntry(name, user);
      List<String> dependents = scriptLibraryService.dependents(name);
      boolean force = Boolean.TRUE.equals(original.getForce());

      if(!dependents.isEmpty() && !force) {
         throw new IllegalArgumentException("force: script library entry \"" + name +
            "\" is used by: " + String.join(", ", dependents) + " as of apply time -- it may " +
            "have gained a new dependent since preview; set force: true to delete anyway");
      }

      String before = ScriptLibraryChangePlanService.project(entry);
      mutationEntered.set(true);
      lib.removeScript(name);
      lib.save();

      boolean verified = !scriptLibraryService.exists(name, user);
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String advisory = dependents.isEmpty() ? null :
         "still used by: " + String.join(", ", dependents) + " -- those will break while this " +
         "deletion stands";
      results.add(new ScriptLibraryApplyOutcome(key, before, null, status,
         verified ? null : "script library entry still present after delete", advisory));
      writeAudit(txId, task, key, AdminChangeRecord.RISK_HIGH, AdminChangeRecord.ACTION_APPLY,
                before, null, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.delete(key, entry));
      }
   }

   // ---------------------------------------------------------------- rollback

   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          LibManager lib, String backupRef, String reviewOutcome,
                                          Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            rollbackOne(undo, txId, task, lib, backupRef, reviewOutcome, user, failures);
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.key, messageOf(e)));
         }
      }

      return failures;
   }

   private void rollbackOne(Undo undo, String txId, String task, LibManager lib, String backupRef,
                            String reviewOutcome, Principal user, List<RollbackFailure> failures)
      throws Exception
   {
      boolean verified;

      switch(undo.kind) {
         case CREATE -> {
            lib.removeScript(undo.name);
            lib.save();
            verified = lib.getScript(undo.name) == null;
         }
         case RENAME -> {
            lib.renameScript(undo.newName, undo.name);
            lib.save();

            if(undo.permission != null) {
               scriptLibraryService.securityEngine.removePermission(ResourceType.SCRIPT, undo.newName);
               scriptLibraryService.securityEngine.setPermission(ResourceType.SCRIPT, undo.name,
                  undo.permission);
            }

            verified = lib.getScript(undo.name) != null && lib.getScript(undo.newName) == null;
         }
         case UPDATE -> {
            lib.setScriptComment(undo.name, undo.description);
            lib.save();
            verified = Tool.equals(lib.getScriptComment(undo.name), undo.description);
         }
         default -> {
            lib.setScript(undo.name, undo.text);

            if(undo.description != null && !undo.description.isEmpty()) {
               lib.setScriptComment(undo.name, undo.description);
            }

            lib.save();
            verified = lib.getScript(undo.name) != null;
         }
      }

      writeAudit(txId, task, undo.key, AdminChangeRecord.RISK_LOW, AdminChangeRecord.ACTION_ROLLBACK,
                null, null, verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of " + undo.kind.name().toLowerCase() + " did not restore the prior state"));
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
         record.setObjectType(ActionRecord.OBJECT_TYPE_SCRIPT);
         record.setBeforeValue(before);
         record.setAfterValue(after);
         record.setAction(adminAction);
         record.setStatus(status);
         record.setRiskLevel(risk);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_STORAGE);
         record.setBackupRef(backupRef);
         record.setReviewOutcome(reviewOutcome);
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         LOG.error("Failed to write script library admin change audit record for transaction {}",
                   txId, auditFailure);
      }
   }

   private static String lastStatus(List<ScriptLibraryApplyOutcome> results) {
      return results.isEmpty() ? null : results.get(results.size() - 1).status();
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   private enum UndoKind { CREATE, RENAME, UPDATE, DELETE }

   /** One undo descriptor built during apply, replayed in reverse by {@link #rollback}. */
   private static final class Undo {
      static Undo create(String key, String name) {
         return new Undo(UndoKind.CREATE, key, name, null, null, null, null);
      }

      static Undo rename(String key, String newName, String name, Permission permission) {
         return new Undo(UndoKind.RENAME, key, name, newName, null, null, permission);
      }

      static Undo update(String key, String name, String priorDescription) {
         return new Undo(UndoKind.UPDATE, key, name, null, priorDescription, null, null);
      }

      static Undo delete(String key, ScriptLibraryEntryDetail entry) {
         return new Undo(UndoKind.DELETE, key, entry.name(), null, entry.description(),
                         entry.text(), null);
      }

      private Undo(UndoKind kind, String key, String name, String newName, String description,
                  String text, Permission permission)
      {
         this.kind = kind;
         this.key = key;
         this.name = name;
         this.newName = newName;
         this.description = description;
         this.text = text;
         this.permission = permission;
      }

      final UndoKind kind;
      final String key;
      final String name;
      final String newName;
      final String description;
      final String text;
      final Permission permission;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ScriptLibraryChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final ScriptLibraryChangePlanService planService;
   private final ScriptLibraryService scriptLibraryService;
   private final LibManagerProvider libManagerProvider;
   private final AdminBackupService backupService;
}
