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
package inetsoft.web.admin.ai.identities;

import inetsoft.web.admin.security.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.admin.ai.*;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.DeleteIdentitiesTaskImpactResponse;
import inetsoft.web.security.auth.MissingResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applies a whole identity changeset, all-or-nothing except for the one deliberately
 * non-compensable verb (organization delete, spec section 6), and audits every attempt -- the
 * identities analog of {@code inetsoft.web.admin.ai.AdminChangesetApplyService} and (within this
 * run) {@code ScheduleChangesetApplyService}/{@code PermissionChangesetApplyService}, replicated
 * rather than shared (spec section 6, carry-forward item 5 -- the <em>third</em> structural-area
 * data point).
 *
 * <p>Every verb in this area is {@code snapshotScope: storage} (spec section 4/7), so the Tier-2
 * backup is taken synchronously here, under the transaction id this apply generates, before any
 * change is attempted -- the same {@code plan.requiresStorageBackup() ? backupService.backup(txId)
 * : null} shape {@code AdminChangesetApplyService} already uses, which for this area always takes
 * the backup branch.
 */
@Component
public class IdentityChangesetApplyService {
   @Autowired
   public IdentityChangesetApplyService(IdentityChangePlanService planService,
                                        SecurityService securityService,
                                        IdentityService identityService,
                                        SecurityEngine securityEngine,
                                        AdminBackupService backupService)
   {
      this.planService = planService;
      this.securityService = securityService;
      this.identityService = identityService;
      this.securityEngine = securityEngine;
      this.backupService = backupService;
   }

   /**
    * Resolves, gates on the plan hash, backs up, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409) -- reused verbatim, per spec section 6.
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied --
    *         same contract as {@code AdminChangesetApplyService}.
    */
   public IdentityApplyResult apply(IdentityApplyRequest req, Principal user) throws Exception {
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

         String txId = "identity-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         String reviewOutcome = req.getReviewOutcome();
         String currentOrgId = OrganizationManager.getInstance().getCurrentOrgID();
         String providerName = defaultEditableProviderName();
         List<IdentityApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         List<IdentityChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            IdentityChangeRequest original = originals.get(i);
            String key = change.property();

            try {
               applyOne(txId, reviewedTask, key, original, currentOrgId, providerName, backupRef,
                       reviewOutcome, user, results, undoable);
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- must
               // never be treated as rolled back. Same rule every prior area's apply service
               // follows. Organization-delete never reaches this branch on its own account (it
               // catches internally, spec section 6 item 5) -- if it does, something unexpected
               // happened and the conservative "unknown state" handling below is still correct.
               results.add(new IdentityApplyOutcome(key, null, null, AdminChangeRecord.STATUS_FAILED,
                                                     messageOf(e), null));
               unknownStateFailures.add(new RollbackFailure(key,
                  "state unknown: apply did not return a verifiable outcome (" + messageOf(e) + ")"));
               failed = true;
               break;
            }

            if(AdminChangeRecord.STATUS_FAILED.equals(lastStatus(results))) {
               failed = true;
               break;
            }
         }

         if(!failed) {
            return new IdentityApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED, backupRef,
                                           Collections.unmodifiableList(results), null);
         }

         Map<String, String> rollbackAdvisories = new LinkedHashMap<>();
         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollback(txId, reviewedTask, undoable, backupRef, reviewOutcome, user,
                                  rollbackAdvisories));
         // Merge each rollback's own disclosure (spec section 6/11: e.g. the generated-password
         // notice) into that entry's ORIGINAL outcome record -- rollback runs after `results` is
         // already built, and this area's contract is that the advisory is a first-class field on
         // the outcome the caller sees, not a log line only.
         List<IdentityApplyOutcome> finalResults = results.stream()
            .map(o -> mergeAdvisory(o, rollbackAdvisories.get(o.property())))
            .collect(Collectors.toList());

         if(failures.isEmpty()) {
            return new IdentityApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK,
                                           backupRef, Collections.unmodifiableList(finalResults), null);
         }

         LOG.error("Identity changeset {} rollback failed; identities still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new IdentityApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                                        backupRef, Collections.unmodifiableList(finalResults),
                                        Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, String key, IdentityChangeRequest original,
                         String currentOrgId, String providerName, String backupRef,
                         String reviewOutcome, Principal user, List<IdentityApplyOutcome> results,
                         List<Undo> undoable)
      throws Exception
   {
      IdentityUnitType unitType = IdentityChangePlanService.requireUnitType("change", original.getUnitType());

      if(IdentityChangeRequest.VERB_CREATE.equals(original.getVerb())) {
         switch(unitType) {
         case USER:
            applyCreateUser(txId, task, key, original.getSpec(), currentOrgId, backupRef,
                           reviewOutcome, user, results, undoable);
            return;
         case GROUP:
            applyCreateGroup(txId, task, key, original.getSpec(), currentOrgId, backupRef,
                            reviewOutcome, user, results, undoable);
            return;
         case ROLE:
            applyCreateRole(txId, task, key, original.getSpec(), currentOrgId, backupRef,
                           reviewOutcome, user, results, undoable);
            return;
         default:
            applyCreateOrganization(txId, task, key, original.getSpec(), backupRef, reviewOutcome,
                                   user, results, undoable);
            return;
         }
      }

      if(IdentityChangeRequest.VERB_UPDATE.equals(original.getVerb())) {
         switch(unitType) {
         case USER:
            applyUpdateUser(txId, task, key, original.getId(), original.getSpec(), currentOrgId,
                           backupRef, reviewOutcome, user, results, undoable);
            return;
         case GROUP:
            applyUpdateGroup(txId, task, key, original.getId(), original.getSpec(), currentOrgId,
                            backupRef, reviewOutcome, user, results, undoable);
            return;
         case ROLE:
            applyUpdateRole(txId, task, key, original.getId(), original.getSpec(), currentOrgId,
                           backupRef, reviewOutcome, user, results, undoable);
            return;
         default:
            applyUpdateOrganization(txId, task, key, original.getId(), original.getSpec(),
                                   backupRef, reviewOutcome, user, results, undoable);
            return;
         }
      }

      switch(unitType) {
      case USER:
         applyDeleteUser(txId, task, key, original.getId(), currentOrgId, providerName, backupRef,
                        reviewOutcome, user, results, undoable);
         return;
      case GROUP:
         applyDeleteGroup(txId, task, key, original.getId(), currentOrgId, providerName, backupRef,
                         reviewOutcome, user, results, undoable);
         return;
      case ROLE:
         applyDeleteRole(txId, task, key, original.getId(), currentOrgId, backupRef, reviewOutcome,
                        user, results, undoable);
         return;
      default:
         // Non-compensable by design (spec section 6 item 5) -- never throws, never adds to
         // `undoable`; a failure here is reported as STATUS_FAILED without contributing an
         // "unknown state" RollbackFailure, because nothing needs undoing.
         applyDeleteOrganizationSafely(txId, task, key, original.getId(), backupRef, reviewOutcome,
                                      user, results);
      }
   }

   // ---------------------------------------------------------------- create

   private void applyCreateUser(String txId, String task, String key, IdentitySpec spec,
                                String currentOrgId, String backupRef, String reviewOutcome,
                                Principal user, List<IdentityApplyOutcome> results,
                                List<Undo> undoable)
      throws Exception
   {
      String orgId = blankToDefault(spec.getOrgId(), currentOrgId);
      IdentityID id = new IdentityID(spec.getName().trim(), orgId);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(id);
      request.setPassword(spec.getPassword());
      request.setAlias(spec.getAlias());
      request.setLocale(spec.getLocale());
      request.setActive(spec.getActive() == null || spec.getActive());
      request.setEmails(spec.getEmails());
      request.setGroups(spec.getGroups());
      request.setRoles(IdentityMerge.toIdentityIds(spec.getRoles(), orgId));
      request.setTheme(spec.getTheme());

      try {
         securityService.createUser(request, orgId, user);
      }
      catch(Exception e) {
         if(tryGet(() -> securityService.getUser(id, user)) == null) {
            // Nothing was ever created -- createUser's precondition checks (permission,
            // existence, validateLocale) all run before its first write (provider.addUser), with
            // no side effect on any other entity, so "still doesn't exist" is sound proof this
            // was a clean no-op, not a partial mutation (unlike update/delete, which delegate to
            // IdentityService.syncIdentity and can have side effects before their own final
            // write -- see isUnchangedSince's javadoc).
            results.add(new IdentityApplyOutcome(key, null, null, AdminChangeRecord.STATUS_FAILED,
               messageOf(e), null));
            writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                      null, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome, user);
            return;
         }

         throw e;
      }

      SecurityUser after = tryGet(() -> securityService.getUser(id, user));
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection = verified ? IdentityProjection.projectUser(after) : null;
      results.add(new IdentityApplyOutcome(key, null, afterProjection, status,
                                           verified ? null : "user not found after create", null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                null, afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.createdUser(key, id));
      }
   }

   private void applyCreateGroup(String txId, String task, String key, IdentitySpec spec,
                                 String currentOrgId, String backupRef, String reviewOutcome,
                                 Principal user, List<IdentityApplyOutcome> results,
                                 List<Undo> undoable)
      throws Exception
   {
      String orgId = blankToDefault(spec.getOrgId(), currentOrgId);
      IdentityID id = new IdentityID(spec.getName().trim(), orgId);

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(id);
      request.setParentGroups(spec.getParentGroups());
      request.setMemberUsers(spec.getMemberUsers());
      request.setMemberGroups(spec.getMemberGroups());
      request.setRoles(IdentityMerge.toIdentityIds(spec.getRoles(), orgId));
      request.setTheme(spec.getTheme());

      try {
         securityService.createGroup(request, orgId, user);
      }
      catch(Exception e) {
         if(tryGet(() -> securityService.getGroup(id, user)) == null) {
            // See applyCreateUser's identical catch above -- createGroup has the same shape
            // (no write before its own precondition checks fail, no other-entity side effect).
            results.add(new IdentityApplyOutcome(key, null, null, AdminChangeRecord.STATUS_FAILED,
               messageOf(e), null));
            writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                      null, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome, user);
            return;
         }

         throw e;
      }

      SecurityGroup after = tryGet(() -> securityService.getGroup(id, user));
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection = verified ? IdentityProjection.projectGroup(after, id) : null;
      results.add(new IdentityApplyOutcome(key, null, afterProjection, status,
                                           verified ? null : "group not found after create", null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                null, afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.createdGroup(key, id));
      }
   }

   private void applyCreateRole(String txId, String task, String key, IdentitySpec spec,
                                String currentOrgId, String backupRef, String reviewOutcome,
                                Principal user, List<IdentityApplyOutcome> results,
                                List<Undo> undoable)
      throws Exception
   {
      String orgId = blankToDefault(spec.getOrgId(), currentOrgId);
      IdentityID id = new IdentityID(spec.getName().trim(), orgId);

      SecurityRole request = new SecurityRole();
      request.setIdentityID(id);
      request.setDescription(spec.getDescription());
      request.setAssignedUsers(spec.getAssignedUsers());
      request.setAssignedGroups(spec.getAssignedGroups());
      request.setInheritedRoles(IdentityMerge.toIdentityIds(spec.getInheritedRoles(), orgId));
      request.setTheme(spec.getTheme());
      request.setDefaultRole(spec.getDefaultRole());
      request.setSysAdmin(spec.getSysAdmin());

      try {
         securityService.createRole(request, orgId, user);
      }
      catch(Exception e) {
         if(tryGet(() -> securityService.getRole(id, user)) == null) {
            // See applyCreateUser's identical catch above -- createRole has the same shape
            // (no write before its own precondition checks fail, no other-entity side effect).
            results.add(new IdentityApplyOutcome(key, null, null, AdminChangeRecord.STATUS_FAILED,
               messageOf(e), null));
            writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                      null, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome, user);
            return;
         }

         throw e;
      }

      SecurityRole after = tryGet(() -> securityService.getRole(id, user));
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection = verified ? IdentityProjection.projectRole(after, id) : null;
      results.add(new IdentityApplyOutcome(key, null, afterProjection, status,
                                           verified ? null : "role not found after create", null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                null, afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.createdRole(key, id));
      }
   }

   private void applyCreateOrganization(String txId, String task, String key, IdentitySpec spec,
                                        String backupRef, String reviewOutcome, Principal user,
                                        List<IdentityApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      String organizationId = spec.getId().trim();

      SecurityOrganization request = new SecurityOrganization();
      request.setId(organizationId);
      request.setName(spec.getOrgName());
      request.setLocale(spec.getLocale());
      request.setTheme(spec.getTheme());
      request.setProperties(spec.getProperties());

      // No member pre-population and no copyFromOrgID -- spec section 1/2: an org create in this
      // cut is always empty, so `createOrganization`'s member-creation branch is never reached.
      try {
         securityService.createOrganization(request, null, user);
      }
      catch(Exception e) {
         if(tryGet(() -> securityService.getOrganization(organizationId, user)) == null) {
            // See applyCreateUser's identical catch above -- createOrganization has the same
            // shape (e.g. validateLocale throws strictly before its first write, per its own
            // comment at the call site in SecurityService), no other-entity side effect. This is
            // the exact live repro for this bug (an invalid locale on org create).
            results.add(new IdentityApplyOutcome(key, null, null, AdminChangeRecord.STATUS_FAILED,
               messageOf(e), null));
            writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                      null, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome, user);
            return;
         }

         throw e;
      }

      SecurityOrganization after = tryGet(() -> securityService.getOrganization(organizationId, user));
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection =
         verified ? IdentityProjection.projectOrganization(after, organizationId) : null;
      results.add(new IdentityApplyOutcome(key, null, afterProjection, status,
                                           verified ? null : "organization not found after create",
                                           null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                null, afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.createdOrganization(key, organizationId));
      }
   }

   // ---------------------------------------------------------------- delete

   private void applyDeleteUser(String txId, String task, String key, String rawId,
                                String currentOrgId, String providerName, String backupRef,
                                String reviewOutcome, Principal user,
                                List<IdentityApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      IdentityID id = IdentityChangePlanService.parseIdentityId("change", rawId, currentOrgId);
      // Authoritative before-state, captured DURING apply, per spec section 2.5 of the guide.
      SecurityUser before = securityService.getUser(id, user);
      String beforeProjection = IdentityProjection.projectUser(before);
      String advisory = describeDeleteTaskImpacts(providerName, id, Identity.USER, user);

      try {
         securityService.deleteUser(id, user);
      }
      catch(Exception e) {
         if(e instanceof SecurityService.PreMutationRefusalException &&
            isUnchangedSince(() -> securityService.getUser(id, user), beforeProjection,
                             IdentityProjection::projectUser))
         {
            results.add(new IdentityApplyOutcome(key, beforeProjection, null,
               AdminChangeRecord.STATUS_FAILED, messageOf(e), advisory));
            writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                      beforeProjection, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome,
                      user);
            return;
         }

         throw e;
      }

      boolean verified = tryGet(() -> securityService.getUser(id, user)) == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new IdentityApplyOutcome(key, beforeProjection, null, status,
                                           verified ? null : "user still present after delete",
                                           advisory));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                beforeProjection, null, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.deletedUser(key, id, before));
      }
   }

   private void applyDeleteGroup(String txId, String task, String key, String rawId,
                                 String currentOrgId, String providerName, String backupRef,
                                 String reviewOutcome, Principal user,
                                 List<IdentityApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      IdentityID id = IdentityChangePlanService.parseIdentityId("change", rawId, currentOrgId);
      SecurityGroup before = securityService.getGroup(id, user);
      String beforeProjection = IdentityProjection.projectGroup(before, id);
      String advisory = describeDeleteTaskImpacts(providerName, id, Identity.GROUP, user);

      try {
         securityService.deleteGroup(id, user);
      }
      catch(Exception e) {
         if(e instanceof SecurityService.PreMutationRefusalException &&
            isUnchangedSince(() -> securityService.getGroup(id, user), beforeProjection,
                             g -> IdentityProjection.projectGroup(g, id)))
         {
            results.add(new IdentityApplyOutcome(key, beforeProjection, null,
               AdminChangeRecord.STATUS_FAILED, messageOf(e), advisory));
            writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                      beforeProjection, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome,
                      user);
            return;
         }

         throw e;
      }

      boolean verified = tryGet(() -> securityService.getGroup(id, user)) == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new IdentityApplyOutcome(key, beforeProjection, null, status,
                                           verified ? null : "group still present after delete",
                                           advisory));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                beforeProjection, null, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.deletedGroup(key, id, before));
      }
   }

   private void applyDeleteRole(String txId, String task, String key, String rawId,
                                String currentOrgId, String backupRef, String reviewOutcome,
                                Principal user, List<IdentityApplyOutcome> results,
                                List<Undo> undoable)
      throws Exception
   {
      IdentityID id = IdentityChangePlanService.parseIdentityId("change", rawId, currentOrgId);
      SecurityRole before = securityService.getRole(id, user);
      String beforeProjection = IdentityProjection.projectRole(before, id);
      // getDeleteTaskImpacts only covers USER/GROUP (community IdentityService's own type filter,
      // spec section 6) -- no advisory for role.

      try {
         securityService.deleteRole(id, user);
      }
      catch(Exception e) {
         if(e instanceof SecurityService.PreMutationRefusalException &&
            isUnchangedSince(() -> securityService.getRole(id, user), beforeProjection,
                             r -> IdentityProjection.projectRole(r, id)))
         {
            results.add(new IdentityApplyOutcome(key, beforeProjection, null,
               AdminChangeRecord.STATUS_FAILED, messageOf(e), null));
            writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                      beforeProjection, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome,
                      user);
            return;
         }

         throw e;
      }

      boolean verified = tryGet(() -> securityService.getRole(id, user)) == null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new IdentityApplyOutcome(key, beforeProjection, null, status,
                                           verified ? null : "role still present after delete", null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                beforeProjection, null, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.deletedRole(key, id, before));
      }
   }

   /**
    * Never throws (spec section 6 item 5) -- a failure here reports {@code STATUS_FAILED} without
    * contributing an "unknown state" {@link RollbackFailure}, because organization delete is
    * declared non-compensable by design, not merely unimplemented: there is no live inverse, so a
    * failure before or after the real mutation is equally "report and stop," never "roll back."
    */
   private void applyDeleteOrganizationSafely(String txId, String task, String key, String rawId,
                                              String backupRef, String reviewOutcome, Principal user,
                                              List<IdentityApplyOutcome> results)
   {
      String organizationId = rawId == null ? null : rawId.trim();
      String beforeProjection = null;

      try {
         SecurityOrganization before = securityService.getOrganization(organizationId, user);
         beforeProjection = IdentityProjection.projectOrganization(before, organizationId);

         securityService.deleteOrganization(organizationId, user);

         boolean verified = tryGet(() -> securityService.getOrganization(organizationId, user)) == null;
         String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
         results.add(new IdentityApplyOutcome(key, beforeProjection, null, status,
                                              verified ? null : "organization still present after delete",
                                              null));
         writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                   beforeProjection, null, status, backupRef, reviewOutcome, user);
      }
      catch(Exception e) {
         results.add(new IdentityApplyOutcome(key, beforeProjection, null,
                                              AdminChangeRecord.STATUS_FAILED, messageOf(e), null));
         writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                   beforeProjection, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome,
                   user);
      }
   }

   private String describeDeleteTaskImpacts(String providerName, IdentityID id, int type,
                                            Principal user)
   {
      try {
         IdentityModel model = IdentityModel.builder().identityID(id).type(type).build();
         DeleteIdentitiesTaskImpactResponse impact =
            identityService.getDeleteTaskImpacts(new IdentityModel[] {model}, providerName, user);

         if(impact.ownedTasks().isEmpty() && impact.executeAsTasks().isEmpty()) {
            return null;
         }

         StringBuilder sb = new StringBuilder("schedule task impact: ");

         if(!impact.ownedTasks().isEmpty()) {
            sb.append(impact.ownedTasks().size()).append(" task(s) owned by this identity will be ")
               .append("deleted (").append(String.join(", ", impact.ownedTasks())).append("); ");
         }

         if(!impact.executeAsTasks().isEmpty()) {
            sb.append(impact.executeAsTasks().size()).append(" task(s) executing as this identity ")
               .append("will have their \"execute as\" reset to the task owner (")
               .append(String.join(", ", impact.executeAsTasks())).append(")");
         }

         return sb.toString().trim();
      }
      catch(Exception e) {
         // Disclosure, not a gate (spec section 6) -- a failure to compute it must never block or
         // fail the delete itself.
         LOG.warn("Failed to compute schedule task impact for {}", id, e);
         return null;
      }
   }

   // ---------------------------------------------------------------- update

   private void applyUpdateUser(String txId, String task, String key, String rawId,
                                IdentitySpec spec, String currentOrgId, String backupRef,
                                String reviewOutcome, Principal user,
                                List<IdentityApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      IdentityID id = IdentityChangePlanService.parseIdentityId("change", rawId, currentOrgId);
      // Authoritative before-state, captured DURING apply, per spec section 2.5 of the guide --
      // matches applyDeleteUser's discipline, not reused from preview's resolve.
      SecurityUser before = securityService.getUser(id, user);
      String beforeProjection = IdentityProjection.projectUser(before);
      SecurityUser merged = IdentityMerge.mergeUser(before, spec, id);

      securityService.updateUser(id, merged, user);

      // Re-verify via the merged identity's OWN id -- if spec.name renamed the user, that is the
      // NEW id, not the original `id` variable used to call updateUser above (design section 4/11's
      // flagged subtle bug: reusing `id` here would always report "not found" after a rename).
      IdentityID afterId = merged.getIdentityID();
      SecurityUser after = tryGet(() -> securityService.getUser(afterId, user));
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection = verified ? IdentityProjection.projectUser(after) : null;
      results.add(new IdentityApplyOutcome(key, beforeProjection, afterProjection, status,
                                           verified ? null : "user not found after update", null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_EDIT, AdminChangeRecord.ACTION_APPLY,
                beforeProjection, afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.updatedUser(key, id, afterId, before));
      }
   }

   private void applyUpdateGroup(String txId, String task, String key, String rawId,
                                 IdentitySpec spec, String currentOrgId, String backupRef,
                                 String reviewOutcome, Principal user,
                                 List<IdentityApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      IdentityID id = IdentityChangePlanService.parseIdentityId("change", rawId, currentOrgId);
      SecurityGroup before = securityService.getGroup(id, user);
      String beforeProjection = IdentityProjection.projectGroup(before, id);
      SecurityGroup merged = IdentityMerge.mergeGroup(before, spec, id);

      securityService.updateGroup(id, merged, user);

      IdentityID afterId = merged.getIdentityID();
      SecurityGroup after = tryGet(() -> securityService.getGroup(afterId, user));
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection = verified ? IdentityProjection.projectGroup(after, afterId) : null;
      results.add(new IdentityApplyOutcome(key, beforeProjection, afterProjection, status,
                                           verified ? null : "group not found after update", null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_EDIT, AdminChangeRecord.ACTION_APPLY,
                beforeProjection, afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.updatedGroup(key, id, afterId, before));
      }
   }

   private void applyUpdateRole(String txId, String task, String key, String rawId,
                                IdentitySpec spec, String currentOrgId, String backupRef,
                                String reviewOutcome, Principal user,
                                List<IdentityApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      IdentityID id = IdentityChangePlanService.parseIdentityId("change", rawId, currentOrgId);
      SecurityRole before = securityService.getRole(id, user);
      String beforeProjection = IdentityProjection.projectRole(before, id);
      SecurityRole merged = IdentityMerge.mergeRole(before, spec, id);

      securityService.updateRole(id, merged, user);

      IdentityID afterId = merged.getIdentityID();
      SecurityRole after = tryGet(() -> securityService.getRole(afterId, user));
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection = verified ? IdentityProjection.projectRole(after, afterId) : null;
      results.add(new IdentityApplyOutcome(key, beforeProjection, afterProjection, status,
                                           verified ? null : "role not found after update", null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_EDIT, AdminChangeRecord.ACTION_APPLY,
                beforeProjection, afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.updatedRole(key, id, afterId, before));
      }
   }

   /**
    * Organization's own id never changes on update (refused at validation, spec.id is always
    * absent by the time this runs) -- there is no separate "after id" to track, unlike the
    * user/group/role update paths above.
    */
   private void applyUpdateOrganization(String txId, String task, String key, String rawId,
                                        IdentitySpec spec, String backupRef, String reviewOutcome,
                                        Principal user, List<IdentityApplyOutcome> results,
                                        List<Undo> undoable)
      throws Exception
   {
      String organizationId = rawId == null ? null : rawId.trim();
      SecurityOrganization before = securityService.getOrganization(organizationId, user);
      String beforeProjection = IdentityProjection.projectOrganization(before, organizationId);
      SecurityOrganization merged = IdentityMerge.mergeOrganization(before, spec, organizationId);

      securityService.updateOrganization(organizationId, merged, user);

      SecurityOrganization after = tryGet(() -> securityService.getOrganization(organizationId, user));
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection =
         verified ? IdentityProjection.projectOrganization(after, organizationId) : null;
      results.add(new IdentityApplyOutcome(key, beforeProjection, afterProjection, status,
                                           verified ? null : "organization not found after update",
                                           null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_EDIT, AdminChangeRecord.ACTION_APPLY,
                beforeProjection, afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.updatedOrganization(key, organizationId, before));
      }
   }

   // ---------------------------------------------------------------- rollback

   /** Undoes verified changes newest-first, attempting all of them and collecting any failures.
    * Organization-delete entries never appear here (spec section 6 item 5 -- never added to
    * {@code undoable}). {@code advisories} is an output parameter: {@code key -> disclosure}, for
    * the two rollback paths that have something to disclose (spec section 6/11). */
   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String backupRef, String reviewOutcome, Principal user,
                                          Map<String, String> advisories)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            switch(undo.kind) {
            case CREATED_USER:
               rollbackCreatedUser(txId, task, undo, backupRef, reviewOutcome, user, failures);
               break;
            case CREATED_GROUP:
               rollbackCreatedGroup(txId, task, undo, backupRef, reviewOutcome, user, failures);
               break;
            case CREATED_ROLE:
               rollbackCreatedRole(txId, task, undo, backupRef, reviewOutcome, user, failures);
               break;
            case CREATED_ORGANIZATION:
               rollbackCreatedOrganization(txId, task, undo, backupRef, reviewOutcome, user, failures);
               break;
            case DELETED_USER:
               rollbackDeletedUser(txId, task, undo, backupRef, reviewOutcome, user, failures, advisories);
               break;
            case DELETED_GROUP:
               rollbackDeletedGroup(txId, task, undo, backupRef, reviewOutcome, user, failures);
               break;
            case DELETED_ROLE:
               rollbackDeletedRole(txId, task, undo, backupRef, reviewOutcome, user, failures, advisories);
               break;
            case UPDATED_USER:
               rollbackUpdatedUser(txId, task, undo, backupRef, reviewOutcome, user, failures);
               break;
            case UPDATED_GROUP:
               rollbackUpdatedGroup(txId, task, undo, backupRef, reviewOutcome, user, failures);
               break;
            case UPDATED_ROLE:
               rollbackUpdatedRole(txId, task, undo, backupRef, reviewOutcome, user, failures);
               break;
            case UPDATED_ORGANIZATION:
               rollbackUpdatedOrganization(txId, task, undo, backupRef, reviewOutcome, user, failures);
               break;
            }
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.key, messageOf(e)));
         }
      }

      return failures;
   }

   /** Combines an outcome's own advisory (if any, e.g. a delete's {@code getDeleteTaskImpacts}
    * disclosure recorded at the original attempt) with a later rollback's own advisory (if any) --
    * both surfaced, neither silently dropped. */
   private static IdentityApplyOutcome mergeAdvisory(IdentityApplyOutcome outcome,
                                                      String rollbackAdvisory)
   {
      if(rollbackAdvisory == null) {
         return outcome;
      }

      String combined = outcome.advisory() == null ? rollbackAdvisory :
         outcome.advisory() + " | " + rollbackAdvisory;
      return new IdentityApplyOutcome(outcome.property(), outcome.before(), outcome.after(),
                                      outcome.status(), outcome.error(), combined);
   }

   private void rollbackCreatedUser(String txId, String task, Undo undo, String backupRef,
                                    String reviewOutcome, Principal user,
                                    List<RollbackFailure> failures)
      throws Exception
   {
      securityService.deleteUser(undo.userId, user);
      boolean verified = tryGet(() -> securityService.getUser(undo.userId, user)) == null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_DELETE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of create reported the user as still present after delete"));
      }
   }

   private void rollbackCreatedGroup(String txId, String task, Undo undo, String backupRef,
                                     String reviewOutcome, Principal user,
                                     List<RollbackFailure> failures)
      throws Exception
   {
      securityService.deleteGroup(undo.userId, user);
      boolean verified = tryGet(() -> securityService.getGroup(undo.userId, user)) == null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_DELETE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of create reported the group as still present after delete"));
      }
   }

   private void rollbackCreatedRole(String txId, String task, Undo undo, String backupRef,
                                    String reviewOutcome, Principal user,
                                    List<RollbackFailure> failures)
      throws Exception
   {
      securityService.deleteRole(undo.userId, user);
      boolean verified = tryGet(() -> securityService.getRole(undo.userId, user)) == null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_DELETE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of create reported the role as still present after delete"));
      }
   }

   private void rollbackCreatedOrganization(String txId, String task, Undo undo, String backupRef,
                                            String reviewOutcome, Principal user,
                                            List<RollbackFailure> failures)
      throws Exception
   {
      securityService.deleteOrganization(undo.organizationId, user);
      boolean verified =
         tryGet(() -> securityService.getOrganization(undo.organizationId, user)) == null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_DELETE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of create reported the organization as still present after delete"));
      }
   }

   /**
    * Restores every field of a user update, including a rename back to the original name -- {@code
    * before} is passed AS the request, unmodified: {@code before}'s own {@code identityID} already
    * IS the original (pre-update) identity, which is exactly what triggers {@code updateUser}'s
    * existing rename-back behavior when a rename happened, and every other field on {@code before}
    * is already the exact pre-update value to restore. No new merge call needed -- this is the
    * forward merge's mirror image, achieved by reusing {@code updateUser} itself with {@code before}
    * as-is (design section 8).
    */
   private void rollbackUpdatedUser(String txId, String task, Undo undo, String backupRef,
                                    String reviewOutcome, Principal user,
                                    List<RollbackFailure> failures)
      throws Exception
   {
      securityService.updateUser(undo.afterId, undo.beforeUser, user);
      boolean verified = tryGet(() -> securityService.getUser(undo.userId, user)) != null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_EDIT,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of update reported the user as still missing under its original name after restore"));
      }
   }

   private void rollbackUpdatedGroup(String txId, String task, Undo undo, String backupRef,
                                     String reviewOutcome, Principal user,
                                     List<RollbackFailure> failures)
      throws Exception
   {
      securityService.updateGroup(undo.afterId, undo.beforeGroup, user);
      boolean verified = tryGet(() -> securityService.getGroup(undo.userId, user)) != null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_EDIT,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of update reported the group as still missing under its original name after restore"));
      }
   }

   private void rollbackUpdatedRole(String txId, String task, Undo undo, String backupRef,
                                    String reviewOutcome, Principal user,
                                    List<RollbackFailure> failures)
      throws Exception
   {
      securityService.updateRole(undo.afterId, undo.beforeRole, user);
      boolean verified = tryGet(() -> securityService.getRole(undo.userId, user)) != null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_EDIT,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of update reported the role as still missing under its original name after restore"));
      }
   }

   /** Organization's own id never changes on update, so there is no separate after-id to restore
    * from -- {@link Undo#organizationId} already names the (unchanging) identity to call {@code
    * updateOrganization} on. Compensable (unlike organization delete, declared permanently
    * non-compensable by design) -- a real, useful improvement, not a gap being closed (design
    * section 8). */
   private void rollbackUpdatedOrganization(String txId, String task, Undo undo, String backupRef,
                                            String reviewOutcome, Principal user,
                                            List<RollbackFailure> failures)
      throws Exception
   {
      securityService.updateOrganization(undo.organizationId, undo.beforeOrganization, user);
      boolean verified =
         tryGet(() -> securityService.getOrganization(undo.organizationId, user)) != null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_EDIT,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of update reported the organization as missing after restore"));
      }
   }

   /**
    * Recreates a deleted user from its captured before-DTO -- a partial inverse (spec section 4/6):
    * groups, roles, alias, locale, active flag, emails, and theme come back, but the original
    * password cannot (it was never captured, since the server never exposes one on read). A fresh
    * password meeting {@link IdentityService#validatePasswordStrength} is generated and appears
    * exactly once, in this outcome's {@code advisory} field (spec section 9's one narrow exception
    * to "never echoed back") -- never persisted to the audit record.
    */
   private void rollbackDeletedUser(String txId, String task, Undo undo, String backupRef,
                                    String reviewOutcome, Principal user,
                                    List<RollbackFailure> failures, Map<String, String> advisories)
      throws Exception
   {
      SecurityUser before = undo.beforeUser;
      String generatedPassword = generatePassword();

      SecurityUser restore = new SecurityUser();
      restore.setIdentityID(undo.userId);
      restore.setPassword(generatedPassword);
      restore.setAlias(before.getAlias());
      restore.setLocale(before.getLocale());
      restore.setActive(before.isActive());
      restore.setEmails(before.getEmails());
      restore.setGroups(before.getGroups());
      restore.setRoles(before.getRoles());
      restore.setTheme(before.getTheme());

      securityService.createUser(restore, undo.userId.getOrgID(), user);

      boolean verified = tryGet(() -> securityService.getUser(undo.userId, user)) != null;
      String advisory = verified
         ? "account recreated with a freshly generated password (one-time disclosure, not " +
           "recoverable afterward): " + generatedPassword +
           " -- communicate this to the user out of band and require an immediate password change; " +
           "group/role/attribute state was restored from the captured before-state, but any role " +
           "that only referenced this user via another role's inheritance edge (spec section 4/14 " +
           "item 1) may not have been restored"
         : null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_CREATE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of delete reported the user as still missing after re-create"));
      }
      else if(advisory != null) {
         advisories.put(undo.key, advisory);
      }
   }

   private void rollbackDeletedGroup(String txId, String task, Undo undo, String backupRef,
                                     String reviewOutcome, Principal user,
                                     List<RollbackFailure> failures)
      throws Exception
   {
      SecurityGroup before = undo.beforeGroup;

      SecurityGroup restore = new SecurityGroup();
      restore.setIdentityID(undo.userId);
      restore.setParentGroups(before.getParentGroups());
      // No memberUsers/memberGroups to restore -- a deletable group is always already empty of
      // member users (identityService.deleteIdentities' own check, spec section 4); member groups
      // are a flagged-unknown (spec section 14 item 2), not assumed either way here.
      restore.setRoles(before.getRoles());
      restore.setTheme(before.getTheme());

      securityService.createGroup(restore, undo.userId.getOrgID(), user);

      boolean verified = tryGet(() -> securityService.getGroup(undo.userId, user)) != null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_CREATE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of delete reported the group as still missing after re-create"));
      }
   }

   /**
    * Recreates a deleted role from its captured before-DTO, including reassigning it to every
    * captured {@code assignedUsers}/{@code assignedGroups} -- {@code createRole} itself performs
    * the reassignment as part of create (spec section 2's correction), so no separate
    * {@code update}-adjacent call is needed.
    */
   private void rollbackDeletedRole(String txId, String task, Undo undo, String backupRef,
                                    String reviewOutcome, Principal user,
                                    List<RollbackFailure> failures, Map<String, String> advisories)
      throws Exception
   {
      SecurityRole before = undo.beforeRole;

      SecurityRole restore = new SecurityRole();
      restore.setIdentityID(undo.userId);
      restore.setDescription(before.getDescription());
      restore.setAssignedUsers(before.getAssignedUsers());
      restore.setAssignedGroups(before.getAssignedGroups());
      restore.setInheritedRoles(before.getInheritedRoles());
      restore.setTheme(before.getTheme());

      securityService.createRole(restore, undo.userId.getOrgID(), user);

      boolean verified = tryGet(() -> securityService.getRole(undo.userId, user)) != null;
      String advisory = verified
         ? "role recreated and reassigned to its captured members; any OTHER role that inherited " +
           "this one is not restored (spec section 4/14 item 1 -- not captured by this DTO surface)"
         : null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_CREATE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of delete reported the role as still missing after re-create"));
      }
      else if(advisory != null) {
         advisories.put(undo.key, advisory);
      }
   }

   // ---------------------------------------------------------------- shared helpers

   @FunctionalInterface
   private interface Getter<T> {
      T get() throws Exception;
   }

   /** {@code null} on {@link MissingResourceException} (does not exist / not permitted), the value
    * otherwise. */
   private static <T> T tryGet(Getter<T> getter) throws Exception {
      try {
         return getter.get();
      }
      catch(MissingResourceException e) {
         return null;
      }
   }

   /**
    * True only when a delete's own re-check, run right after {@code securityService.delete*}
    * threw a {@link SecurityService.PreMutationRefusalException}, confirms the identity's
    * current state is byte-for-byte identical to the {@code beforeProjection} captured at the
    * top of {@code applyDelete*} -- i.e. the refusal fired before any mutation took effect, so
    * there is nothing to roll back and this can be reported as a plain {@code STATUS_FAILED}
    * instead of an "unknown state" {@link RollbackFailure}.
    *
    * <p>Callers must only invoke this for a caught
    * {@link SecurityService.PreMutationRefusalException} -- comparing the entity's own
    * projection is NOT sufficient proof of "nothing was mutated" for an arbitrary exception:
    * {@code IdentityService.syncIdentity} runs dashboard/schedule-task cleanup unconditionally
    * BEFORE its entity-specific {@code eprovider.remove*} call, so a plain {@code Exception} from
    * that call (or from {@code syncIdentity}'s own catch-and-report-as-warning fallback) can leave
    * those side effects applied while the entity's own fields (which is all
    * {@code IdentityProjection} looks at) are still unchanged -- a false "nothing happened"
    * reading. {@code PreMutationRefusalException} is only thrown from the specific call sites
    * that are structurally guaranteed to run before {@code syncIdentity} is ever invoked, so this
    * check has no such gap for those.
    *
    * <p>False -- including when the re-check itself throws, or the identity is now missing
    * entirely -- means the caller must keep the conservative "unknown state" classification.
    */
   private static <T> boolean isUnchangedSince(Getter<T> currentGetter, String beforeProjection,
                                               Function<T, String> projector)
   {
      T current;

      try {
         current = currentGetter.get();
      }
      catch(Exception e) {
         return false;
      }

      return current != null && beforeProjection != null &&
         beforeProjection.equals(projector.apply(current));
   }

   private static String blankToDefault(String value, String fallback) {
      return value == null || value.trim().isEmpty() ? fallback : value.trim();
   }

   /** {@code IdentityService.getDeleteTaskImpacts}/{@code deleteIdentities} take a provider name,
    * used only to re-resolve the same editable provider {@code SecurityService} itself resolves
    * internally (its own {@code getEditableAuthenticationProvider}, {@code private}, not callable
    * from here) -- {@code SUtil.getEditableAuthenticationProvider} is the same public utility that
    * private method delegates to, so calling it directly here resolves to the identical provider. */
   private String defaultEditableProviderName() {
      EditableAuthenticationProvider provider =
         SUtil.getEditableAuthenticationProvider(securityEngine.getSecurityProvider());
      return provider == null ? null : provider.getProviderName();
   }

   /** Meets {@code IdentityService.validatePasswordStrength}: at least one upper, one lower, one
    * digit, one symbol, 8-72 characters. Built from a fixed pool per class rather than raw random
    * bytes so the result is printable and copy-pasteable. */
   private static String generatePassword() {
      String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
      String lower = "abcdefghijkmnpqrstuvwxyz";
      String digits = "23456789";
      String symbols = "!@#%^&*-_=+";
      String all = upper + lower + digits + symbols;
      StringBuilder sb = new StringBuilder();
      sb.append(upper.charAt(RANDOM.nextInt(upper.length())));
      sb.append(lower.charAt(RANDOM.nextInt(lower.length())));
      sb.append(digits.charAt(RANDOM.nextInt(digits.length())));
      sb.append(symbols.charAt(RANDOM.nextInt(symbols.length())));

      for(int i = 0; i < 12; i++) {
         sb.append(all.charAt(RANDOM.nextInt(all.length())));
      }

      List<Character> chars = new ArrayList<>();

      for(char c : sb.toString().toCharArray()) {
         chars.add(c);
      }

      Collections.shuffle(chars, RANDOM);
      StringBuilder shuffled = new StringBuilder(chars.size());
      chars.forEach(shuffled::append);
      return shuffled.toString();
   }

   private void writeAudit(String txId, String task, String key, String actionRecordName,
                           String adminAction, String before, String after, String status,
                           String backupRef, String reviewOutcome, Principal user)
   {
      try {
         AdminChangeRecord record = new AdminChangeRecord();
         record.setTransactionId(txId);
         record.setTaskDescription(task);
         record.setProperty(key);
         record.setObjectType(ActionRecord.OBJECT_TYPE_USERPERMISSION);
         record.setBeforeValue(before);
         record.setAfterValue(after);
         record.setAction(adminAction);
         record.setStatus(status);
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_STORAGE);
         record.setBackupRef(backupRef);
         record.setReviewOutcome(reviewOutcome);
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         // An audit write must never replace the real outcome -- same rule every prior area's
         // apply service follows.
         LOG.error("Failed to write identity admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static String lastStatus(List<IdentityApplyOutcome> results) {
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
      enum Kind { CREATED_USER, CREATED_GROUP, CREATED_ROLE, CREATED_ORGANIZATION,
                  DELETED_USER, DELETED_GROUP, DELETED_ROLE,
                  UPDATED_USER, UPDATED_GROUP, UPDATED_ROLE, UPDATED_ORGANIZATION }

      static Undo createdUser(String key, IdentityID id) {
         return new Undo(Kind.CREATED_USER, key, id, null, null, null, null, null, null);
      }

      static Undo createdGroup(String key, IdentityID id) {
         return new Undo(Kind.CREATED_GROUP, key, id, null, null, null, null, null, null);
      }

      static Undo createdRole(String key, IdentityID id) {
         return new Undo(Kind.CREATED_ROLE, key, id, null, null, null, null, null, null);
      }

      static Undo createdOrganization(String key, String organizationId) {
         return new Undo(Kind.CREATED_ORGANIZATION, key, null, null, organizationId, null, null,
                         null, null);
      }

      static Undo deletedUser(String key, IdentityID id, SecurityUser before) {
         return new Undo(Kind.DELETED_USER, key, id, null, null, before, null, null, null);
      }

      static Undo deletedGroup(String key, IdentityID id, SecurityGroup before) {
         return new Undo(Kind.DELETED_GROUP, key, id, null, null, null, before, null, null);
      }

      static Undo deletedRole(String key, IdentityID id, SecurityRole before) {
         return new Undo(Kind.DELETED_ROLE, key, id, null, null, null, null, before, null);
      }

      /** {@code beforeId}/{@code afterId} differ exactly when {@code spec.name} renamed the
       * identity -- {@code userId} (below) reuses the plan key's own "old id" convention;
       * {@code afterId} is the post-update id, needed to look the identity up for rollback since
       * it may live under a new name now. */
      static Undo updatedUser(String key, IdentityID beforeId, IdentityID afterId, SecurityUser before) {
         return new Undo(Kind.UPDATED_USER, key, beforeId, afterId, null, before, null, null, null);
      }

      static Undo updatedGroup(String key, IdentityID beforeId, IdentityID afterId, SecurityGroup before) {
         return new Undo(Kind.UPDATED_GROUP, key, beforeId, afterId, null, null, before, null, null);
      }

      static Undo updatedRole(String key, IdentityID beforeId, IdentityID afterId, SecurityRole before) {
         return new Undo(Kind.UPDATED_ROLE, key, beforeId, afterId, null, null, null, before, null);
      }

      /** No separate {@code afterId} -- organization's own id never changes on update (refused at
       * validation). Unlike organization delete (declared permanently non-compensable), this
       * rollback IS compensable, hence the new {@link #beforeOrganization} field (organization had
       * no existing "before" field on {@code Undo} until now, since delete never added one to
       * {@code undoable}). */
      static Undo updatedOrganization(String key, String organizationId, SecurityOrganization before) {
         return new Undo(Kind.UPDATED_ORGANIZATION, key, null, null, organizationId, null, null,
                         null, before);
      }

      private Undo(Kind kind, String key, IdentityID userId, IdentityID afterId,
                  String organizationId, SecurityUser beforeUser, SecurityGroup beforeGroup,
                  SecurityRole beforeRole, SecurityOrganization beforeOrganization)
      {
         this.kind = kind;
         this.key = key;
         this.userId = userId;
         this.afterId = afterId;
         this.organizationId = organizationId;
         this.beforeUser = beforeUser;
         this.beforeGroup = beforeGroup;
         this.beforeRole = beforeRole;
         this.beforeOrganization = beforeOrganization;
      }

      final Kind kind;
      final String key;
      /** Set for every unit except organization (which uses {@link #organizationId} instead). For
       * {@code UPDATED_*} kinds, this is the ORIGINAL (pre-update) id -- matches the plan key's own
       * convention (spec section 4). */
      final IdentityID userId;
      /** Only set for {@code UPDATED_USER}/{@code UPDATED_GROUP}/{@code UPDATED_ROLE} -- the
       * post-update id, needed to look the identity up for rollback since a rename means it may no
       * longer live under {@link #userId}'s name. */
      final IdentityID afterId;
      final String organizationId;
      final SecurityUser beforeUser;
      final SecurityGroup beforeGroup;
      final SecurityRole beforeRole;
      /** Only set for {@code UPDATED_ORGANIZATION} -- organization delete never captured this
       * (non-compensable by design), but organization update's rollback needs it. */
      final SecurityOrganization beforeOrganization;
   }

   private static final Logger LOG = LoggerFactory.getLogger(IdentityChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale as every prior area's own
    * lock: JVM-local only, does not protect a clustered deployment or a concurrent edit made
    * through Enterprise Manager directly. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final IdentityChangePlanService planService;
   private final SecurityService securityService;
   private final IdentityService identityService;
   private final SecurityEngine securityEngine;
   private final AdminBackupService backupService;
}
