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
package inetsoft.web.admin.ai.permissions;

import inetsoft.web.admin.security.PermissionGrant;
import inetsoft.web.admin.security.SecurityService;
import inetsoft.sree.security.*;
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
 * Applies a whole permission-grant changeset, all-or-nothing, and audits every attempt -- the
 * permissions analog of {@code inetsoft.web.admin.ai.AdminChangesetApplyService} and (within this
 * run) {@code inetsoft.enterprise.web.admin.ai.schedule.ScheduleChangesetApplyService}, replicated
 * rather than shared (spec section 6, carry-forward item 5).
 *
 * <p>Unlike schedule tasks, every verb here is {@code snapshotScope: value} (spec section 4/7) --
 * no Tier-2 backup is requested by this area's own plans, so {@code backupRef} is always
 * {@code null} on every {@link ApplyResult} this service returns.
 *
 * <p>Verification is single-grant, not whole-resource (spec section 6): after each change, this
 * class re-reads the raw {@link Permission} and checks only the touched identity's action set
 * ({@link PermissionProjection#actionsFor}), not the resource's total projection -- a concurrent,
 * unrelated grant change on the same resource must not make this apply look like it failed.
 */
@Component
public class PermissionChangesetApplyService {
   @Autowired
   public PermissionChangesetApplyService(PermissionChangePlanService planService,
                                          SecurityService securityService,
                                          SecurityEngine securityEngine)
   {
      this.planService = planService;
      this.securityService = securityService;
      this.securityEngine = securityEngine;
   }

   /**
    * Resolves, gates on the plan hash, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409) -- reused verbatim from the properties area, per spec section 6.
    */
   public ApplyResult apply(PermissionApplyRequest req, Principal user) throws Exception {
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

         String txId = "permgrant-" + newIdSuffix();
         String reviewOutcome = req.getReviewOutcome();
         String currentOrgId = OrganizationManager.getInstance().getCurrentOrgID();
         List<ApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;
         // Whether the item that threw (if any) had already entered its own mutating call
         // (createPermissionGrant/updatePermissionGrant/deletePermissionGrant) before the throw --
         // only that case is a genuine partial-mutation risk that must force
         // STATUS_ROLLBACK_FAILED on its own; a throw that fires strictly before the mutating call
         // means the item was never touched, so it must not by itself override an otherwise
         // fully-verified rollback (bug 76856, mirroring bug 76808's DataSourceChangesetApplyService
         // fix).
         boolean unknownStateMutationEntered = false;

         List<PermissionChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            PermissionChangeRequest original = originals.get(i);
            String key = change.property();
            AtomicBoolean mutationEntered = new AtomicBoolean(false);

            try {
               applyOne(txId, reviewedTask, key, original, currentOrgId, reviewOutcome, user, results,
                       undoable, mutationEntered);
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- must
               // never be treated as rolled back. Same rule AdminChangesetApplyService and
               // ScheduleChangesetApplyService follow.
               results.add(new ApplyOutcome(key, null, null, AdminChangeRecord.STATUS_FAILED,
                                            messageOf(e)));
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
            return new ApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED, null,
                                   Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> rollbackOwnFailures =
            rollback(txId, reviewedTask, undoable, reviewOutcome, user);

         // An unknownStateFailures entry only forces rollback-failed when that item's own mutating
         // call had actually been entered (a real partial-mutation risk); if it never touched
         // storage, it must not by itself override an otherwise fully-verified rollback.
         if(rollbackOwnFailures.isEmpty() && !unknownStateMutationEntered) {
            return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK, null,
                                   Collections.unmodifiableList(results), null);
         }

         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollbackOwnFailures);
         LOG.error("Permission changeset {} rollback failed; grants still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new ApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, null,
                                Collections.unmodifiableList(results),
                                Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, String key, PermissionChangeRequest original,
                         String currentOrgId, String reviewOutcome, Principal user,
                         List<ApplyOutcome> results, List<Undo> undoable,
                         AtomicBoolean mutationEntered)
      throws Exception
   {
      ResourceType resourceType =
         PermissionChangePlanService.requireAllowedResourceType("change", original.getResourceType());
      String resourcePath = original.getResourcePath();
      String identityType =
         PermissionChangePlanService.requireIdentityType("change", original.getIdentityType());
      IdentityID identityId =
         PermissionChangePlanService.requireIdentityId("change", original.getIdentityId(), currentOrgId);

      if(PermissionChangeRequest.VERB_CREATE.equals(original.getVerb())) {
         applyCreate(txId, task, key, resourceType, resourcePath, identityType, identityId,
                    original.getActions(), currentOrgId, reviewOutcome, user, results, undoable,
                    mutationEntered);
      }
      else if(PermissionChangeRequest.VERB_UPDATE.equals(original.getVerb())) {
         applyUpdate(txId, task, key, resourceType, resourcePath, identityType, identityId,
                    original.getActions(), currentOrgId, reviewOutcome, user, results, undoable,
                    mutationEntered);
      }
      else {
         applyDelete(txId, task, key, resourceType, resourcePath, identityType, identityId,
                    currentOrgId, reviewOutcome, user, results, undoable, mutationEntered);
      }
   }

   private void applyCreate(String txId, String task, String key, ResourceType resourceType,
                            String resourcePath, String identityType, IdentityID identityId,
                            List<String> actions, String orgId, String reviewOutcome,
                            Principal user, List<ApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
      throws Exception
   {
      // Authoritative before-state, captured DURING apply, per spec section 2.5 of the guide.
      // A raw-null Permission means no entry existed for this resource at all -- the null-vs-empty
      // distinction that decides this create's rollback path (spec section 4/6).
      Permission rawBefore = securityEngine.getSecurityProvider()
         .getPermission(resourceType, resourcePath, null);
      boolean resourceHadNoPriorEntry = rawBefore == null;

      PermissionGrant grant = new PermissionGrant();
      grant.setIdentityID(identityId);
      grant.setType(identityType);
      grant.setActions(actions);
      mutationEntered.set(true);
      securityService.createPermissionGrant(resourcePath, resourceType.name(), grant, user);

      Permission rawAfter = securityEngine.getSecurityProvider()
         .getPermission(resourceType, resourcePath, null);
      Set<String> actualActions =
         PermissionProjection.actionsFor(rawAfter, identityType, identityId, orgId);
      boolean verified = actualActions.equals(new TreeSet<>(actions));
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String before = PermissionProjection.projectGrant(identityType, identityId, null);
      String after = PermissionProjection.projectGrant(identityType, identityId, actions);
      results.add(new ApplyOutcome(key, before, after, status,
                                   verified ? null : "grant actions did not match after create"));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                before, after, status, reviewOutcome, user);

      if(verified) {
         undoable.add(new Undo(PermissionChangeRequest.VERB_CREATE, key, resourceType, resourcePath,
                               identityType, identityId, null, resourceHadNoPriorEntry));
      }
   }

   private void applyUpdate(String txId, String task, String key, ResourceType resourceType,
                            String resourcePath, String identityType, IdentityID identityId,
                            List<String> newActions, String orgId, String reviewOutcome,
                            Principal user, List<ApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
      throws Exception
   {
      Permission rawBefore = securityEngine.getSecurityProvider()
         .getPermission(resourceType, resourcePath, null);
      List<String> priorActions = new ArrayList<>(
         PermissionProjection.actionsFor(rawBefore, identityType, identityId, orgId));

      PermissionGrant newGrant = new PermissionGrant();
      newGrant.setIdentityID(identityId);
      newGrant.setType(identityType);
      newGrant.setActions(newActions);
      mutationEntered.set(true);
      securityService.updatePermissionGrant(resourcePath, resourceType.name(),
         identityId.convertToKey(), identityType, newGrant, user);

      Permission rawAfter = securityEngine.getSecurityProvider()
         .getPermission(resourceType, resourcePath, null);
      Set<String> actualActions =
         PermissionProjection.actionsFor(rawAfter, identityType, identityId, orgId);
      boolean verified = actualActions.equals(new TreeSet<>(newActions));
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String before = PermissionProjection.projectGrant(identityType, identityId, priorActions);
      String after = PermissionProjection.projectGrant(identityType, identityId, newActions);
      results.add(new ApplyOutcome(key, before, after, status,
                                   verified ? null : "grant actions did not match after update"));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_EDIT, AdminChangeRecord.ACTION_APPLY,
                before, after, status, reviewOutcome, user);

      if(verified) {
         undoable.add(new Undo(PermissionChangeRequest.VERB_UPDATE, key, resourceType, resourcePath,
                               identityType, identityId, priorActions, false));
      }
   }

   private void applyDelete(String txId, String task, String key, ResourceType resourceType,
                            String resourcePath, String identityType, IdentityID identityId,
                            String orgId, String reviewOutcome, Principal user,
                            List<ApplyOutcome> results, List<Undo> undoable,
                            AtomicBoolean mutationEntered)
      throws Exception
   {
      // Authoritative before-state, captured DURING apply, not the preview-time snapshot -- per
      // spec section 2.5 of the guide.
      Permission rawBefore = securityEngine.getSecurityProvider()
         .getPermission(resourceType, resourcePath, null);
      List<String> priorActions = new ArrayList<>(
         PermissionProjection.actionsFor(rawBefore, identityType, identityId, orgId));

      mutationEntered.set(true);
      securityService.deletePermissionGrant(resourcePath, resourceType.name(),
         identityId.convertToKey(), identityType, user);

      Permission rawAfter = securityEngine.getSecurityProvider()
         .getPermission(resourceType, resourcePath, null);
      Set<String> actualActions =
         PermissionProjection.actionsFor(rawAfter, identityType, identityId, orgId);
      boolean verified = actualActions.isEmpty();
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String before = PermissionProjection.projectGrant(identityType, identityId, priorActions);
      String after = PermissionProjection.projectGrant(identityType, identityId, null);
      results.add(new ApplyOutcome(key, before, after, status,
                                   verified ? null : "grant still present after delete"));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                before, after, status, reviewOutcome, user);

      if(verified) {
         undoable.add(new Undo(PermissionChangeRequest.VERB_DELETE, key, resourceType, resourcePath,
                               identityType, identityId, priorActions, false));
      }
   }

   /** Undoes verified changes newest-first, attempting all of them and collecting any failures. */
   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String reviewOutcome, Principal user)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            if(PermissionChangeRequest.VERB_CREATE.equals(undo.originalVerb)) {
               // Undo a create: always remove just our own grant first -- safe regardless of what
               // else is on the resource, and closes a narrow read-then-write race the null-vs-empty
               // branch alone would have (06-review.md flagged item 2): if a concurrent, unrelated
               // grant was added on the same resource between this create's before-capture and now,
               // it must survive this rollback. Only when the resource had no permission entry at
               // all before this apply (spec section 4/6's null-vs-empty finding) AND a FRESH
               // post-delete read confirms it is genuinely empty now -- not the stale
               // resourceHadNoPriorEntry flag alone -- delete the storage entry outright, restoring
               // "no entry" exactly rather than leaving an explicit-but-empty Permission.
               securityService.deletePermissionGrant(undo.resourcePath, undo.resourceType.name(),
                  undo.identityId.convertToKey(), undo.identityType, user);

               if(undo.resourceHadNoPriorEntry) {
                  Permission afterDelete = securityEngine.getSecurityProvider()
                     .getPermission(undo.resourceType, undo.resourcePath, null);

                  if(PermissionProjection.projectTotal(afterDelete, undo.identityId.getOrgID())
                     .isEmpty())
                  {
                     securityEngine.getSecurityProvider()
                        .removePermission(undo.resourceType, undo.resourcePath, null);
                  }
               }

               Permission raw = securityEngine.getSecurityProvider()
                  .getPermission(undo.resourceType, undo.resourcePath, null);
               boolean verified = PermissionProjection
                  .actionsFor(raw, undo.identityType, undo.identityId, undo.identityId.getOrgID())
                  .isEmpty();
               writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_DELETE,
                         AdminChangeRecord.ACTION_ROLLBACK, null, null,
                         verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                         reviewOutcome, user);

               if(!verified) {
                  failures.add(new RollbackFailure(undo.key,
                     "rollback of create reported the grant as still present after delete"));
               }
            }
            else if(PermissionChangeRequest.VERB_UPDATE.equals(undo.originalVerb)) {
               PermissionGrant restore = new PermissionGrant();
               restore.setIdentityID(undo.identityId);
               restore.setType(undo.identityType);
               restore.setActions(undo.priorActions);
               securityService.updatePermissionGrant(undo.resourcePath, undo.resourceType.name(),
                  undo.identityId.convertToKey(), undo.identityType, restore, user);

               Permission raw = securityEngine.getSecurityProvider()
                  .getPermission(undo.resourceType, undo.resourcePath, null);
               boolean verified = PermissionProjection
                  .actionsFor(raw, undo.identityType, undo.identityId, undo.identityId.getOrgID())
                  .equals(new TreeSet<>(undo.priorActions));
               writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_EDIT,
                         AdminChangeRecord.ACTION_ROLLBACK, null, null,
                         verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                         reviewOutcome, user);

               if(!verified) {
                  failures.add(new RollbackFailure(undo.key,
                     "rollback of update did not restore the prior actions"));
               }
            }
            else {
               // Undo a delete: re-create the grant from the captured before-actions.
               PermissionGrant restore = new PermissionGrant();
               restore.setIdentityID(undo.identityId);
               restore.setType(undo.identityType);
               restore.setActions(undo.priorActions);
               securityService.createPermissionGrant(undo.resourcePath, undo.resourceType.name(),
                  restore, user);

               Permission raw = securityEngine.getSecurityProvider()
                  .getPermission(undo.resourceType, undo.resourcePath, null);
               boolean verified = !PermissionProjection
                  .actionsFor(raw, undo.identityType, undo.identityId, undo.identityId.getOrgID())
                  .isEmpty();
               writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_CREATE,
                         AdminChangeRecord.ACTION_ROLLBACK, null, null,
                         verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                         reviewOutcome, user);

               if(!verified) {
                  failures.add(new RollbackFailure(undo.key,
                     "rollback of delete reported the grant as still missing after re-create"));
               }
            }
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.key, messageOf(e)));
         }
      }

      return failures;
   }

   private void writeAudit(String txId, String task, String key, String actionRecordName,
                           String adminAction, String before, String after, String status,
                           String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(key);
         record.setObjectType(ActionRecord.OBJECT_TYPE_OBJECTPERMISSION);
         record.setBeforeValue(before);
         record.setAfterValue(after);
         record.setAction(adminAction);
         record.setStatus(status);
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_VALUE);
         record.setBackupRef(null);
         record.setReviewOutcome(reviewOutcome);
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         // An audit write must never replace the real outcome -- same rule
         // AdminChangeService.applyChange and ScheduleChangesetApplyService follow.
         LOG.error("Failed to write permission-grant admin change audit record for transaction {}",
                   txId, auditFailure);
      }
   }

   private static String lastStatus(List<ApplyOutcome> results) {
      return results.isEmpty() ? null : results.get(results.size() - 1).status();
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String newIdSuffix() {
      return String.format("%016x", RANDOM.nextLong());
   }

   /** One undo descriptor built during apply, replayed in reverse by {@link #rollback}. */
   private static final class Undo {
      Undo(String originalVerb, String key, ResourceType resourceType, String resourcePath,
          String identityType, IdentityID identityId, List<String> priorActions,
          boolean resourceHadNoPriorEntry)
      {
         this.originalVerb = originalVerb;
         this.key = key;
         this.resourceType = resourceType;
         this.resourcePath = resourcePath;
         this.identityType = identityType;
         this.identityId = identityId;
         this.priorActions = priorActions;
         this.resourceHadNoPriorEntry = resourceHadNoPriorEntry;
      }

      final String originalVerb;
      final String key;
      final ResourceType resourceType;
      final String resourcePath;
      final String identityType;
      final IdentityID identityId;
      /** Set for {@code update} (the actions to restore) and {@code delete} (the actions to
       * re-create); unused for {@code create}. */
      final List<String> priorActions;
      /** Only meaningful for {@code create} -- true when the resource had no permission entry at
       * all before this apply (spec section 4/6's null-vs-empty finding). */
      final boolean resourceHadNoPriorEntry;
   }

   private static final Logger LOG = LoggerFactory.getLogger(PermissionChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale as
    * {@code AdminChangesetApplyService#APPLY_LOCK}/{@code ScheduleChangesetApplyService}'s own
    * lock: JVM-local only, does not protect a clustered deployment or a concurrent edit made
    * through Enterprise Manager directly. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final PermissionChangePlanService planService;
   private final SecurityService securityService;
   private final SecurityEngine securityEngine;
}
