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
package inetsoft.web.admin.ai.providers;

import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.admin.ai.*;
import inetsoft.web.admin.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Applies a whole provider changeset, all-or-nothing, and audits every attempt -- the providers
 * analog of {@code inetsoft.web.admin.ai.AdminChangesetApplyService} and (within this run)
 * {@code ScheduleChangesetApplyService}/{@code PermissionChangesetApplyService}/
 * {@code IdentityChangesetApplyService}, replicated rather than shared (01-spec.md section 6,
 * carry-forward item 5).
 *
 * <p>Every verb in this area is {@code snapshotScope: storage} unconditionally (01-spec.md section
 * 4/7), so the Tier-2 backup is taken synchronously here, before any change is attempted.
 */
@Component
public class ProviderChangesetApplyService {
   @Autowired
   public ProviderChangesetApplyService(ProviderChangePlanService planService,
                                        AuthenticationProviderService authenticationProviderService,
                                        AuthorizationProviderService authorizationProviderService,
                                        AdminBackupService backupService)
   {
      this.planService = planService;
      this.authenticationProviderService = authenticationProviderService;
      this.authorizationProviderService = authorizationProviderService;
      this.backupService = backupService;
   }

   /**
    * Resolves, gates on the plan hash, backs up, then executes.
    *
    * @throws AdminChangesetApplyService.PlanHashMismatchException if the hash is missing or stale
    *         (maps to HTTP 409) -- reused verbatim, per 01-spec.md section 6.
    * @throws Exception if the Tier-2 backup itself fails, in which case nothing was applied.
    */
   public ProviderApplyResult apply(ProviderApplyRequest req, Principal user) throws Exception {
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

         String txId = "provider-" + newIdSuffix();
         String backupRef = plan.requiresStorageBackup() ? backupService.backup(txId) : null;
         String reviewOutcome = req.getReviewOutcome();
         List<ProviderApplyOutcome> results = new ArrayList<>();
         List<Undo> undoable = new ArrayList<>();
         List<RollbackFailure> unknownStateFailures = new ArrayList<>();
         boolean failed = false;

         List<ProviderChangeRequest> originals = req.getChanges();

         for(int i = 0; i < plan.changes().size(); i++) {
            PlanChange change = plan.changes().get(i);
            ProviderChangeRequest original = originals.get(i);
            String key = change.property();

            try {
               applyOne(txId, plan.task(), key, original, user, backupRef, reviewOutcome, results,
                       undoable);
            }
            catch(Exception e) {
               // A throw carries no verifiable before/after evidence for THIS change -- must never
               // be treated as rolled back. Same rule every prior area's apply service follows.
               results.add(new ProviderApplyOutcome(key, null, null, AdminChangeRecord.STATUS_FAILED,
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
            return new ProviderApplyResult(txId, AdminChangesetApplyService.STATUS_APPLIED, backupRef,
                                           Collections.unmodifiableList(results), null);
         }

         Map<String, String> rollbackAdvisories = new LinkedHashMap<>();
         List<RollbackFailure> failures = new ArrayList<>(unknownStateFailures);
         failures.addAll(rollback(txId, plan.task(), undoable, backupRef, reviewOutcome, user,
                                  rollbackAdvisories));
         // Merge each rollback's own disclosure (the "restored at the end of the chain, not its
         // original position" notice, 01-spec.md section 6/11) into that entry's ORIGINAL outcome
         // record -- the advisory is a first-class field on the outcome the caller sees, not a log
         // line only.
         List<ProviderApplyOutcome> finalResults = results.stream()
            .map(o -> mergeAdvisory(o, rollbackAdvisories.get(o.property())))
            .collect(Collectors.toList());

         if(failures.isEmpty()) {
            return new ProviderApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLED_BACK,
                                           backupRef, Collections.unmodifiableList(finalResults), null);
         }

         LOG.error("Provider changeset {} rollback failed; providers still changed: {}", txId,
                  failures.stream().map(RollbackFailure::property).collect(Collectors.joining(", ")));
         return new ProviderApplyResult(txId, AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                                        backupRef, Collections.unmodifiableList(finalResults),
                                        Collections.unmodifiableList(failures));
      }
      finally {
         APPLY_LOCK.unlock();
      }
   }

   private void applyOne(String txId, String task, String key, ProviderChangeRequest original,
                         Principal user, String backupRef, String reviewOutcome,
                         List<ProviderApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      ProviderChain chain = ProviderChangePlanService.requireChain("change", original.getChain());
      String name = original.getName().trim();

      if(ProviderChangeRequest.VERB_CREATE.equals(original.getVerb())) {
         if(chain == ProviderChain.AUTHENTICATION) {
            applyCreateAuthentication(txId, task, key, name, original, backupRef, reviewOutcome,
                                     user, results, undoable);
         }
         else {
            applyCreateAuthorization(txId, task, key, name, backupRef, reviewOutcome, user, results,
                                    undoable);
         }

         return;
      }

      if(chain == ProviderChain.AUTHENTICATION) {
         applyDeleteAuthentication(txId, task, key, name, backupRef, reviewOutcome, user, results,
                                  undoable);
      }
      else {
         applyDeleteAuthorization(txId, task, key, name, backupRef, reviewOutcome, user, results,
                                 undoable);
      }
   }

   // ---------------------------------------------------------------- create

   private void applyCreateAuthentication(String txId, String task, String key, String name,
                                          ProviderChangeRequest original, String backupRef,
                                          String reviewOutcome, Principal user,
                                          List<ProviderApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      AuthenticationProviderModel.Builder builder = AuthenticationProviderModel.builder()
         .providerName(name);

      if("LDAP".equalsIgnoreCase(original.getProviderType())) {
         builder.providerType(SecurityProviderType.LDAP)
            .ldapProviderModel(buildLdapModel(original.getSpec()));
      }
      else {
         builder.providerType(SecurityProviderType.FILE);
      }

      authenticationProviderService.addAuthenticationProvider(builder.build(), name, user);

      AuthenticationProviderModel after =
         tryGet(() -> authenticationProviderService.getAuthenticationProvider(name),
               list -> indexOfName(list, name) >= 0,
               authenticationProviderService.getProviderListModel().providers());
      boolean verified = after != null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection = verified ? ProviderProjection.projectAuthenticationProvider(after) : null;
      results.add(new ProviderApplyOutcome(key, null, afterProjection, status,
                                           verified ? null : "provider not found after create", null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                null, afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.createdAuthentication(key, name));
      }
   }

   private void applyCreateAuthorization(String txId, String task, String key, String name,
                                         String backupRef, String reviewOutcome, Principal user,
                                         List<ProviderApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      AuthorizationProviderModel model = AuthorizationProviderModel.builder()
         .providerName(name)
         .providerType(SecurityProviderType.FILE)
         .build();

      authorizationProviderService.addAuthorizationProvider(model, name, user);

      List<SecurityProviderStatus> afterList =
         authorizationProviderService.getProviderListModel().providers();
      boolean verified = indexOfName(afterList, name) >= 0;
      AuthorizationProviderModel after =
         verified ? authorizationProviderService.getAuthorizationProvider(name) : null;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      String afterProjection = verified ? ProviderProjection.projectAuthorizationProvider(after) : null;
      results.add(new ProviderApplyOutcome(key, null, afterProjection, status,
                                           verified ? null : "provider not found after create", null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_CREATE, AdminChangeRecord.ACTION_APPLY,
                null, afterProjection, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.createdAuthorization(key, name));
      }
   }

   private static LdapAuthenticationProviderModel buildLdapModel(ProviderLdapSpec spec) {
      boolean useCredential = Boolean.TRUE.equals(spec.getUseCredential());
      return LdapAuthenticationProviderModel.builder()
         .ldapServer(SecurityProviderType.valueOf(spec.getLdapServer().toUpperCase()))
         .protocol(spec.getProtocol())
         .hostName(spec.getHostName())
         .hostPort(spec.getHostPort())
         .rootDN(spec.getRootDN())
         .useCredential(useCredential)
         .adminID(spec.getAdminID())
         .secretId(spec.getSecretId())
         .password(spec.getPassword())
         .userFilter(spec.getUserFilter())
         .userBase(spec.getUserBase())
         .userAttr(spec.getUserAttr())
         .mailAttr(spec.getMailAttr())
         .groupFilter(spec.getGroupFilter())
         .groupBase(spec.getGroupBase())
         .groupAttr(spec.getGroupAttr())
         .roleFilter(spec.getRoleFilter())
         .roleBase(spec.getRoleBase())
         .roleAttr(spec.getRoleAttr())
         .userRoleFilter(spec.getUserRoleFilter())
         .roleRoleFilter(spec.getRoleRoleFilter())
         .groupRoleFilter(spec.getGroupRoleFilter())
         .startTls(spec.getStartTls())
         .searchTree(Boolean.TRUE.equals(spec.getSearchTree()))
         .sysAdminRoles(spec.getSysAdminRoles() == null ? null :
                       spec.getSysAdminRoles().toArray(new String[0]))
         .build();
   }

   // ---------------------------------------------------------------- delete

   /**
    * 01-spec.md section 1/6's single most safety-critical mechanical detail, confirmed directly in
    * this pass against {@code removeAuthenticationProvider(int index, ...)}'s own signature
    * (04-build-java.md): index is resolved fresh, from the name, against the chain as read AT APPLY
    * TIME -- never a preview-captured index, since the chain is a live list any concurrent EM session
    * can also mutate. The section 4 preflight is re-run here too (step 2a), against this same
    * freshly-read state, not merely trusted from preview.
    */
   private void applyDeleteAuthentication(String txId, String task, String key, String name,
                                          String backupRef, String reviewOutcome, Principal user,
                                          List<ProviderApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      AuthenticationProviderModel before = authenticationProviderService.getAuthenticationProvider(name);
      String beforeProjection = ProviderProjection.projectAuthenticationProvider(before);
      planService.requireAuthenticationDeletePreflight("apply." + key, name, user);
      int index = indexOfName(authenticationProviderService.getProviderListModel().providers(), name);

      if(index < 0) {
         results.add(new ProviderApplyOutcome(key, beforeProjection, null,
                                              AdminChangeRecord.STATUS_FAILED,
                                              "provider not found at apply time (concurrent change)",
                                              null));
         writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                   beforeProjection, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome,
                   user);
         return;
      }

      authenticationProviderService.removeAuthenticationProvider(index, name, user);

      boolean verified = indexOfName(authenticationProviderService.getProviderListModel().providers(),
                                     name) < 0;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ProviderApplyOutcome(key, beforeProjection, null, status,
                                           verified ? null : "provider still present after delete",
                                           null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                beforeProjection, null, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.deletedAuthentication(key, name, before));
      }
   }

   private void applyDeleteAuthorization(String txId, String task, String key, String name,
                                         String backupRef, String reviewOutcome, Principal user,
                                         List<ProviderApplyOutcome> results, List<Undo> undoable)
      throws Exception
   {
      AuthorizationProviderModel before = authorizationProviderService.getAuthorizationProvider(name);
      String beforeProjection = ProviderProjection.projectAuthorizationProvider(before);
      planService.requireAuthorizationDeletePreflight("apply." + key, name);
      int index = indexOfName(authorizationProviderService.getProviderListModel().providers(), name);

      if(index < 0) {
         results.add(new ProviderApplyOutcome(key, beforeProjection, null,
                                              AdminChangeRecord.STATUS_FAILED,
                                              "provider not found at apply time (concurrent change)",
                                              null));
         writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                   beforeProjection, null, AdminChangeRecord.STATUS_FAILED, backupRef, reviewOutcome,
                   user);
         return;
      }

      authorizationProviderService.removeAuthorizationProvider(index, name, user);

      boolean verified = indexOfName(authorizationProviderService.getProviderListModel().providers(),
                                     name) < 0;
      String status = verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      results.add(new ProviderApplyOutcome(key, beforeProjection, null, status,
                                           verified ? null : "provider still present after delete",
                                           null));
      writeAudit(txId, task, key, ActionRecord.ACTION_NAME_DELETE, AdminChangeRecord.ACTION_APPLY,
                beforeProjection, null, status, backupRef, reviewOutcome, user);

      if(verified) {
         undoable.add(Undo.deletedAuthorization(key, name, before));
      }
   }

   // ---------------------------------------------------------------- rollback

   private List<RollbackFailure> rollback(String txId, String task, List<Undo> undoable,
                                          String backupRef, String reviewOutcome, Principal user,
                                          Map<String, String> advisories)
   {
      List<RollbackFailure> failures = new ArrayList<>();

      for(int i = undoable.size() - 1; i >= 0; i--) {
         Undo undo = undoable.get(i);

         try {
            switch(undo.kind) {
            case CREATED_AUTHENTICATION:
               rollbackCreatedAuthentication(txId, task, undo, backupRef, reviewOutcome, user,
                                            failures);
               break;
            case CREATED_AUTHORIZATION:
               rollbackCreatedAuthorization(txId, task, undo, backupRef, reviewOutcome, user,
                                           failures);
               break;
            case DELETED_AUTHENTICATION:
               rollbackDeletedAuthentication(txId, task, undo, backupRef, reviewOutcome, user,
                                            failures, advisories);
               break;
            case DELETED_AUTHORIZATION:
               rollbackDeletedAuthorization(txId, task, undo, backupRef, reviewOutcome, user,
                                           failures, advisories);
               break;
            }
         }
         catch(Exception e) {
            failures.add(new RollbackFailure(undo.key, messageOf(e)));
         }
      }

      return failures;
   }

   private static ProviderApplyOutcome mergeAdvisory(ProviderApplyOutcome outcome,
                                                      String rollbackAdvisory)
   {
      if(rollbackAdvisory == null) {
         return outcome;
      }

      String combined = outcome.advisory() == null ? rollbackAdvisory :
         outcome.advisory() + " | " + rollbackAdvisory;
      return new ProviderApplyOutcome(outcome.property(), outcome.before(), outcome.after(),
                                      outcome.status(), outcome.error(), combined);
   }

   private void rollbackCreatedAuthentication(String txId, String task, Undo undo, String backupRef,
                                              String reviewOutcome, Principal user,
                                              List<RollbackFailure> failures)
      throws Exception
   {
      int index = indexOfName(authenticationProviderService.getProviderListModel().providers(),
                              undo.name);

      if(index < 0) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of create could not find the provider to remove (already gone)"));
         return;
      }

      authenticationProviderService.removeAuthenticationProvider(index, undo.name, user);
      boolean verified = indexOfName(authenticationProviderService.getProviderListModel().providers(),
                                     undo.name) < 0;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_DELETE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of create reported the provider as still present after delete"));
      }
   }

   private void rollbackCreatedAuthorization(String txId, String task, Undo undo, String backupRef,
                                             String reviewOutcome, Principal user,
                                             List<RollbackFailure> failures)
      throws Exception
   {
      int index = indexOfName(authorizationProviderService.getProviderListModel().providers(),
                              undo.name);

      if(index < 0) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of create could not find the provider to remove (already gone)"));
         return;
      }

      authorizationProviderService.removeAuthorizationProvider(index, undo.name, user);
      boolean verified = indexOfName(authorizationProviderService.getProviderListModel().providers(),
                                     undo.name) < 0;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_DELETE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of create reported the provider as still present after delete"));
      }
   }

   /**
    * Recreates a deleted authentication provider from its captured before-DTO -- a partial inverse
    * (01-spec.md section 4/6): {@code addAuthenticationProvider} has no insert-at-index form, so the
    * provider comes back at the END of the chain, not its original position, disclosed here as a
    * first-class advisory, never buried in free text.
    *
    * <p>For an LDAP provider created with a literal (non-{@code useCredential}) bind password, this
    * recreate is expected to fail: the real password is never captured on read (masked to
    * {@code Util.PLACEHOLDER_PASSWORD}, section 9), and {@code replacePlaceholderWithPassword}'s
    * substitution only fires for an edit ({@code model.oldName() != null}), never for this recreate.
    * The literal placeholder string is passed through as-is; the server's own
    * {@code checkParameters()} live-bind safety net (already relied on per section 13) throws on the
    * bad credential, which surfaces here as an ordinary {@link RollbackFailure} -- the existing
    * "never routine, needs manual intervention" contract, not a silent partial success. A
    * {@code useCredential: true} (secretId-referenced) LDAP provider is unaffected: {@code secretId}
    * is not secret-classified and is captured/echoed verbatim (section 9), so that flavor recreates a
    * fully working provider. See 04-build-java.md for the full trace.
    */
   private void rollbackDeletedAuthentication(String txId, String task, Undo undo, String backupRef,
                                              String reviewOutcome, Principal user,
                                              List<RollbackFailure> failures,
                                              Map<String, String> advisories)
      throws Exception
   {
      authenticationProviderService.addAuthenticationProvider(undo.beforeAuthentication, undo.name,
                                                              user);
      boolean verified = indexOfName(authenticationProviderService.getProviderListModel().providers(),
                                     undo.name) >= 0;
      String advisory = verified
         ? "provider restored at the END of the chain, not its original position -- addProvider has " +
           "no insert-at-index form; if resolution order matters for this provider (order determines " +
           "which provider's role definitions win, 01-spec.md section 4), an operator may need a " +
           "follow-up reorder through EM, this area has no reorder tool in this cut"
         : null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_CREATE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of delete reported the provider as still missing after re-create"));
      }
      else {
         advisories.put(undo.key, advisory);
      }
   }

   private void rollbackDeletedAuthorization(String txId, String task, Undo undo, String backupRef,
                                             String reviewOutcome, Principal user,
                                             List<RollbackFailure> failures,
                                             Map<String, String> advisories)
      throws Exception
   {
      authorizationProviderService.addAuthorizationProvider(undo.beforeAuthorization, undo.name, user);
      boolean verified = indexOfName(authorizationProviderService.getProviderListModel().providers(),
                                     undo.name) >= 0;
      String advisory = verified
         ? "provider restored at the END of the chain, not its original position -- addProvider has " +
           "no insert-at-index form; this area has no reorder tool in this cut"
         : null;
      writeAudit(txId, task, undo.key, ActionRecord.ACTION_NAME_CREATE,
                AdminChangeRecord.ACTION_ROLLBACK, null, null,
                verified ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED,
                backupRef, reviewOutcome, user);

      if(!verified) {
         failures.add(new RollbackFailure(undo.key,
            "rollback of delete reported the provider as still missing after re-create"));
      }
      else {
         advisories.put(undo.key, advisory);
      }
   }

   // ---------------------------------------------------------------- shared helpers

   @FunctionalInterface
   private interface Getter<T> {
      T get() throws Exception;
   }

   /** Returns {@code null} if the create is not verified in the fresh list, otherwise the value from
    * {@code getter}. Avoids calling {@code getAuthenticationProvider} on a name that a concurrent
    * failure left absent (section 2's NPE-avoidance discipline, applied here even though a
    * just-created name should always resolve). */
   private static <T> T tryGet(Getter<T> getter, java.util.function.Predicate<List<SecurityProviderStatus>> present,
                               List<SecurityProviderStatus> list) throws Exception
   {
      return present.test(list) ? getter.get() : null;
   }

   private static int indexOfName(List<SecurityProviderStatus> list, String name) {
      for(int i = 0; i < list.size(); i++) {
         if(list.get(i).name().equals(name)) {
            return i;
         }
      }

      return -1;
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
         record.setObjectType(ActionRecord.OBJECT_TYPE_SECURITY_PROVIDER);
         record.setBeforeValue(before);
         record.setAfterValue(after);
         record.setAction(adminAction);
         record.setStatus(status);
         record.setRiskLevel(AdminChangeRecord.RISK_HIGH);
         record.setSnapshotScope(AdminChangeRecord.SCOPE_STORAGE);
         record.setBackupRef(backupRef);
         record.setReviewOutcome(reviewOutcome);
         // organizationId is deliberately left unset: provider configuration is deployment-wide,
         // not org-scoped (01-spec.md section 1/5), so there is no organization to attribute this
         // change to -- not an oversight, mirrors ProviderChangePlanService.NOT_ORG_SCOPED.
         record.setUserName(user == null ? null : user.getName());
         record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
         record.setServerHostName(Tool.getHost());
         Audit.getInstance().auditAdminChange(record, user);
      }
      catch(Exception auditFailure) {
         // An audit write must never replace the real outcome -- same rule every prior area's apply
         // service follows.
         LOG.error("Failed to write provider admin change audit record for transaction {}", txId,
                   auditFailure);
      }
   }

   private static String lastStatus(List<ProviderApplyOutcome> results) {
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
      enum Kind { CREATED_AUTHENTICATION, CREATED_AUTHORIZATION, DELETED_AUTHENTICATION,
                  DELETED_AUTHORIZATION }

      static Undo createdAuthentication(String key, String name) {
         return new Undo(Kind.CREATED_AUTHENTICATION, key, name, null, null);
      }

      static Undo createdAuthorization(String key, String name) {
         return new Undo(Kind.CREATED_AUTHORIZATION, key, name, null, null);
      }

      static Undo deletedAuthentication(String key, String name, AuthenticationProviderModel before) {
         return new Undo(Kind.DELETED_AUTHENTICATION, key, name, before, null);
      }

      static Undo deletedAuthorization(String key, String name, AuthorizationProviderModel before) {
         return new Undo(Kind.DELETED_AUTHORIZATION, key, name, null, before);
      }

      private Undo(Kind kind, String key, String name, AuthenticationProviderModel beforeAuthentication,
                  AuthorizationProviderModel beforeAuthorization)
      {
         this.kind = kind;
         this.key = key;
         this.name = name;
         this.beforeAuthentication = beforeAuthentication;
         this.beforeAuthorization = beforeAuthorization;
      }

      final Kind kind;
      final String key;
      final String name;
      final AuthenticationProviderModel beforeAuthentication;
      final AuthorizationProviderModel beforeAuthorization;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ProviderChangesetApplyService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   /** Serializes the entire body of {@link #apply} -- same rationale and same JVM-local-only
    * limitation as every prior area's own lock. */
   private static final ReentrantLock APPLY_LOCK = new ReentrantLock();
   private final ProviderChangePlanService planService;
   private final AuthenticationProviderService authenticationProviderService;
   private final AuthorizationProviderService authorizationProviderService;
   private final AdminBackupService backupService;
}
