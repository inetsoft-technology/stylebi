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
package inetsoft.web.admin.ai;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.admin.properties.PropertyChangeSideEffects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdminChangeService {
   @Autowired
   public AdminChangeService(PropertyChangeSideEffects sideEffects) {
      this.sideEffects = sideEffects;
   }

   public AdminChangeResult applyChange(AdminChangeRequest req, Principal principal) {
      requireNonBlank("transactionId", req.getTransactionId());
      requireNonBlank("property", req.getProperty());
      requireValidAction(req.getAction());

      FaultInjectionProbe probe = FaultInjectionProbe.match(req);

      // Placed BEFORE the try block below, deliberately: a throw here propagates all the way out
      // of applyChange, the same as the (currently unreachable through any real call) unanticipated
      // exception AdminChangesetApplyService.apply's own catch(Exception e) exists to handle. See
      // docs/teams/2026-08-26-a1-fault-injection/01-design.md - this is a test-only hook, inert
      // unless both FaultInjectionProbe gates hold, neither of which any real request can satisfy.
      if(probe != null && probe.throwsFault()) {
         throw new AdminChangeFaultInjectedException(req.getProperty(), req.getAction());
      }

      AdminChangeResult result = new AdminChangeResult();
      result.setProperty(req.getProperty());

      if(probe != null) {
         // A "soft" fault: applyChange returns normally with status FAILED, exactly like a real
         // failure that never touched SreeEnv would - never a thrown exception. Unlike the throw
         // above, this never touches SreeEnv and is audited through the same writeAudit call the
         // real failure path below uses, so it is provably inert against server state.
         result.setStatus(AdminChangeRecord.STATUS_FAILED);
         result.setError("fault injection: forced failure for " + req.getProperty());

         try {
            writeAudit(req, principal, null, null, result.getStatus(), result.getError());
         }
         catch(Exception auditFailure) {
            LOG.error("Failed to write admin change audit record for transaction {}",
                      req.getTransactionId(), auditFailure);
         }

         return result;
      }

      // Unlike PropertiesController.editProperty (which treats a "" value as "keep the
      // current value"), admin-chat treats "" as an explicit set-to-empty; reset-to-default is
      // expressed via a null value (the broker's stage_property_reset).
      //
      // This value is NOT trimmed here. AdminPropertyCatalog.canonicalizeValue trims on the way
      // into the plan, so the hash the operator approves already covers the exact value that will
      // be written. Trimming again here would be harmless for that path, but this method is also
      // reachable from rollback with a STORED value that never went through canonicalizeValue -
      // trimming a stored value with significant surrounding whitespace would write back a
      // different value than was there before, so status (computed against this same desired
      // value) would report "verified" for a property that was not actually restored.
      String desired = req.getValue();
      String before = null;
      String status = AdminChangeRecord.STATUS_FAILED;
      String error = null;
      AdminPropertyName name = null;

      try {
         // orgScope=false: SreeEnv.getProperty(name) is ORG-SCOPED and would resolve to
         // inetsoft.org.{currentOrg}.{name} when that key exists, while setProperty/remove write
         // the literal key. Mixing them reads one key and writes another, so the read-back verify
         // fails and the caller's rollback writes the override's value into the global key. The
         // three-argument form applies only fixPropertyNameCase, matching setProperty and remove.
         name = AdminPropertyName.parse(req.getProperty());
         before = SreeEnv.getProperty(name.key(), false, false);
         result.setBeforeValue(before);
         // Marks the snapshot read as having actually completed, so a caller can distinguish
         // "property was unset" (beforeRead=true, beforeValue=null) from "the read itself threw"
         // (beforeRead stays false) - see AdminChangeResult.isBeforeRead().
         result.setBeforeRead(true);

         // An allow-listed credential is stored encrypted, so an apply must write it through
         // setPassword - the same encryption the Enterprise Manager field applies - or the store
         // would hold plaintext where every reader calls Tool.decryptPassword.
         //
         // A ROLLBACK must not. Its value is the stored form captured by the getProperty read
         // above, i.e. already ciphertext, and re-encrypting it would double-encrypt and restore
         // something that never decrypts back to the original secret. The action is what separates
         // the two: only an apply carries a plaintext value.
         //
         // Written as "is APPLY" rather than "is not ROLLBACK" deliberately. requireValidAction
         // also accepts RESTORE, which nothing issues today but which would, by its name, replay a
         // stored value exactly as rollback does - so excluding only ROLLBACK would encrypt it and
         // reintroduce the double-encryption this branch exists to prevent, in a path with no test
         // to catch it. Naming the one action that carries plaintext fails closed for RESTORE and
         // for any action added later; the deny-list spelling failed open for both. That is the
         // same argument as isEncryptedCredential being an allow-list, applied to actions.
         boolean encryptOnWrite = AdminPropertyCatalog.isEncryptedCredential(name.baseName())
            && AdminChangeRecord.ACTION_APPLY.equals(req.getAction());

         // AdminChangePlanService refuses a credential under cloud secrets, but a plan is approved
         // at preview and executed later, so that check is not the one that protects the write.
         // Cloud secrets turning on in between would leave setPassword skipping encryption and
         // writing the literal secret into a property everything downstream reads as a
         // secret-manager reference - succeeding silently, which is the exact failure this class
         // otherwise refuses to leave open. Re-check at the point of the write.
         if(encryptOnWrite && desired != null && Tool.isCloudSecrets()) {
            throw new IllegalStateException(
               AdminPropertyCatalog.cloudSecretsRefusal(name.key()));
         }

         if(desired == null) {
            // Side-effect hooks match exact literals (e.g. "security.exposedefaultorgtoall"), so
            // they must receive the base name; an org-qualified name would silently never fire.
            sideEffects.applyPreRemoveSideEffects(name.baseName());
            SreeEnv.remove(name.key());
         }
         else if(encryptOnWrite) {
            SreeEnv.setPassword(name.key(), desired);
         }
         else {
            SreeEnv.setProperty(name.key(), desired);
         }

         SreeEnv.save();

         if(desired == null) {
            sideEffects.applyPostRemoveSideEffects(name.baseName());
         }
         else {
            sideEffects.applyEditSideEffects(name.baseName());
         }

         // beforeValue and afterValue stay the STORED form on every path - ciphertext for a
         // credential. That is deliberate and load-bearing, not an oversight: the changeset apply
         // service replays getBeforeValue() as the rollback value, so masking or decrypting either
         // one would break rollback. Ciphertext is not the secret, so nothing sensitive is
         // recorded by keeping it.
         String after = SreeEnv.getProperty(name.key(), false, false);
         result.setAfterValue(after);

         // The comparison has to speak the same language as the write. An encrypting write stores
         // ciphertext while `desired` is plaintext, so comparing the raw read would report FAILED
         // for every successful credential write and roll it straight back; getPassword reverses
         // the encryption so like is compared with like.
         String verified = encryptOnWrite ? SreeEnv.getPassword(name.key()) : after;
         status = Objects.equals(verified, desired)
            ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      }
      catch(Exception ex) {
         error = ex.getMessage();
         status = AdminChangeRecord.STATUS_FAILED;

         try {
            // name is null only when AdminPropertyName.parse itself threw (e.g. a blank
            // property), in which case there is no resolved key and the raw, unparsed string
            // is the only fallback available.
            String key = name != null ? name.key() : req.getProperty();
            result.setAfterValue(SreeEnv.getProperty(key, false, false));
         }
         catch(Exception ignore) {
            // leave afterValue as-is; still audit below
         }
      }
      finally {
         result.setStatus(status);
         result.setError(error);

         try {
            writeAudit(req, principal, before, result.getAfterValue(), status, error);
         }
         catch(Exception auditFailure) {
            // An audit write must never replace the real outcome: propagating from finally would
            // discard the before/after evidence the caller needs to decide whether the server
            // state moved, turning a recoverable failure into an unrecoverable one.
            LOG.error("Failed to write admin change audit record for transaction {}",
                      req.getTransactionId(), auditFailure);
         }
      }

      return result;
   }

   /**
    * Rejects null/blank required fields up front, before any {@code SreeEnv}
    * mutation or audit write. A record with a blank transactionId or property
    * is unqueryable (getChangeset filters by transactionId), so persisting it
    * would silently orphan the audit entry -- fail loud instead.
    */
   private void requireNonBlank(String fieldName, String value) {
      if(value == null || value.trim().isEmpty()) {
         throw new IllegalArgumentException(fieldName + ": must not be blank");
      }
   }

   /**
    * {@code AdminChangeRecord.validate()} also requires a non-blank {@code action}
    * drawn from a known set of values. Fail loud on the same grounds as
    * {@link #requireNonBlank} -- before any {@code SreeEnv} mutation or audit write --
    * rather than letting an unqueryable/unrecognized action slip through.
    */
   private void requireValidAction(String action) {
      requireNonBlank("action", action);

      if(!AdminChangeRecord.ACTION_APPLY.equals(action) &&
         !AdminChangeRecord.ACTION_ROLLBACK.equals(action) &&
         !AdminChangeRecord.ACTION_RESTORE.equals(action))
      {
         throw new IllegalArgumentException("action: must be one of " +
            AdminChangeRecord.ACTION_APPLY + ", " + AdminChangeRecord.ACTION_ROLLBACK +
            ", " + AdminChangeRecord.ACTION_RESTORE);
      }
   }

   private void writeAudit(AdminChangeRequest req, Principal principal,
                           String before, String after, String status, String error)
   {
      AdminChangeRecord record = new AdminChangeRecord();
      record.setTransactionId(req.getTransactionId());
      record.setTaskDescription(req.getTaskDescription());
      record.setProperty(req.getProperty());
      record.setObjectType(ActionRecord.OBJECT_TYPE_EMPROPERTY);
      record.setBeforeValue(before);
      record.setAfterValue(after);
      record.setAction(req.getAction());
      record.setStatus(status);
      record.setRiskLevel(req.getRiskLevel());
      record.setSnapshotScope(req.getSnapshotScope());
      record.setBackupRef(req.getBackupRef());
      record.setReviewOutcome(req.getReviewOutcome());
      // userSessionID is intentionally left unpopulated; organizationId IS populated downstream by
      // DefaultAudit.
      record.setUserName(principal == null ? null : principal.getName());
      record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
      record.setServerHostName(Tool.getHost());
      Audit.getInstance().auditAdminChange(record, principal);
   }

   /**
    * Test-only fault injection, matched against every {@code applyChange} call. Both gates below
    * must hold, and neither is settable by any HTTP request, so this is inert in any real
    * deployment:
    *
    * <ul>
    *   <li>the JVM system property {@link #FAULT_INJECTION_ENABLED_PROPERTY}, read fresh on every
    *       call and settable only by a {@code -D} flag at server start; and</li>
    *   <li>a property name matching {@link #FAULT_INJECTION_PATTERN} -
    *       {@code test.faultinjection.<apply|rollback>.<throw|fail>.<label>} - a namespace that
    *       does not collide with any real StyleBI property.</li>
    * </ul>
    *
    * <p>The pattern is matched against the property name AFTER {@link AdminPropertyName#parse}
    * lowercases it (every real caller reaches this method with an already-resolved,
    * already-lowercased {@code PlanChange.property()} - see {@code AdminPropertyCatalog.resolve}
    * and {@code AdminChangePlanService.resolve}), so the reserved namespace is spelled all
    * lower-case; {@link #FAULT_INJECTION_PATTERN} is compiled case-insensitively as well so a test
    * calling {@link #applyChange} directly, before that normalization, still matches.</p>
    *
    * <p>Exists to exercise {@code AdminChangesetApplyService}'s compensating-transaction paths
    * ({@code rolled-back}, {@code rollback-failed}) against this REAL class, not a mock - see
    * {@code docs/teams/2026-08-26-a1-fault-injection/01-design.md} in the stylebi-wiz repo for the
    * full design and the exact request sequences that exercise each outcome.
    */
   private static final class FaultInjectionProbe {
      private FaultInjectionProbe(boolean throwsFault) {
         this.throwsFault = throwsFault;
      }

      /** {@code true} for a {@code throw}-mode probe, {@code false} for a {@code fail}-mode one. */
      boolean throwsFault() {
         return throwsFault;
      }

      /** @return a matching probe for this request, or {@code null} if either gate does not hold. */
      static FaultInjectionProbe match(AdminChangeRequest req) {
         if(!Boolean.getBoolean(FAULT_INJECTION_ENABLED_PROPERTY)) {
            return null;
         }

         String property = req.getProperty();
         Matcher m = property == null ? null : FAULT_INJECTION_PATTERN.matcher(property);

         if(m == null || !m.matches()) {
            return null;
         }

         // Firing only on the matching action lets one probe apply completely normally and fail
         // only on its rollback (or vice versa) - required to exercise a rollback-failed scenario
         // where the item that fails to roll back is not the same item whose apply triggered the
         // rollback in the first place. See 01-design.md §2.3, scenario 3.
         String targetAction = "apply".equals(m.group(1))
            ? AdminChangeRecord.ACTION_APPLY : AdminChangeRecord.ACTION_ROLLBACK;

         if(!targetAction.equals(req.getAction())) {
            return null;
         }

         return new FaultInjectionProbe("throw".equals(m.group(2)));
      }

      private final boolean throwsFault;
   }

   /**
    * Thrown only by {@link FaultInjectionProbe} in {@code throw} mode. Never thrown in a real
    * deployment - see that class's javadoc for the two gates that must both hold first.
    */
   public static final class AdminChangeFaultInjectedException extends RuntimeException {
      public AdminChangeFaultInjectedException(String property, String action) {
         super("fault injection: forced throw for " + property + " (" + action + ")");
      }
   }

   private static final String FAULT_INJECTION_ENABLED_PROPERTY =
      "inetsoft.admin.ai.faultInjection.enabled";
   private static final Pattern FAULT_INJECTION_PATTERN = Pattern.compile(
      "^test\\.faultinjection\\.(apply|rollback)\\.(throw|fail)\\.[a-zA-Z0-9-]+$",
      Pattern.CASE_INSENSITIVE);
   private static final Logger LOG = LoggerFactory.getLogger(AdminChangeService.class);
   private final PropertyChangeSideEffects sideEffects;
}
