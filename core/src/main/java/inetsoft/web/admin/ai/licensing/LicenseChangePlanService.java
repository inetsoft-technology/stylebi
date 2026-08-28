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
package inetsoft.web.admin.ai.licensing;

import inetsoft.report.internal.license.License;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.internal.license.LicenseType;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Resolves a requested list of license key changes into a {@link ResolvedPlan} and hashes it --
 * the licensing analog of {@code inetsoft.web.admin.ai.AdminChangePlanService} and (within this
 * run) {@code ProviderChangePlanService}/{@code ClusterChangePlanService}, replicated rather than
 * shared (01-spec.md section 6, carry-forward item 5).
 *
 * <p>Bypasses {@code LicenseKeySettingsService.setModel}/{@code updateKeys} entirely (01-spec.md
 * section 0) -- that method does a nondeterministic replace-plus-remove that cannot recover a
 * caller's "add"/"remove" intent from a partial list. Resolves each change against
 * {@link LicenseManager#getInstalledLicenses()}/{@link LicenseManager#parseLicense(String)}
 * directly, never against {@code LicenseKeyModel} (whose {@code valid()} always returns
 * {@code true}, section 14 D2/D6, {@code stylebi#76344}).
 *
 * <p><b>03-reconcile.md addition 2, confirmed by reading {@code EnterpriseLicenseStrategy
 * .addLicense} in full (04-build-java.md):</b> the method's only already-claimed check is
 * {@code claimedLicense == null} (this node's own claim state) -- it never compares the requested
 * key against {@code installedLicenses} before running the cluster-node claiming exchange
 * ({@code AddLicenseMessage}). So a duplicate {@code add} of an already-installed, non-pooled key,
 * in a multi-node cluster with an idle node, can cause a second node to also claim it. This
 * service therefore refuses an {@code add} of an already-installed key outright ({@link
 * #resolveAdd}), the same "refuse rather than accept a plan that could vacuously or riskily
 * proceed" treatment already given a {@code remove} of a not-installed key.
 *
 * <p><b>This area is not gated here.</b> Per 03-reconcile.md addition 1, the
 * {@code LicenseManager.isEnterprise()} gate lives at {@link AdminLicensingController}'s entry
 * point, not in this service -- keeping this class callable (and testable) the same way regardless
 * of which strategy backs {@link LicenseManager} in the calling context.
 */
@Component
public class LicenseChangePlanService {
   @Autowired
   public LicenseChangePlanService(LicenseManager licenseManager) {
      this.licenseManager = licenseManager;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but does perform a live read (the current
    * installed-license set) plus one {@code parseLicense} per {@code add}.
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *         list, an unrecognized verb, a blank/duplicate key, an {@code add} whose key resolves
    *         to {@code LicenseType.INVALID}/fails {@code valid()}, an {@code add} of an
    *         already-installed key (03-reconcile.md addition 2), or a {@code remove} of a key that
    *         is not currently installed (01-spec.md section 2).
    */
   public ResolvedPlan resolve(LicenseChangePlanRequest req) {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      Set<License> installed = licenseManager.getInstalledLicenses();
      Map<String, License> byKey = new HashMap<>();

      for(License license : installed) {
         if(license.key() != null) {
            byKey.put(license.key(), license);
         }
      }

      List<ResolvedEntry> resolvedEntries = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      int index = 0;
      int addCount = 0;
      int removeCount = 0;

      for(LicenseChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";

         if(change == null) {
            throw new IllegalArgumentException(label + ": must not be null");
         }

         String verb = requireVerb(label, change.getVerb());
         String key = requireKey(label, change.getKey());
         requireUnseen(label, key, seenKeys);

         if(VERB_ADD.equals(verb)) {
            resolvedEntries.add(resolveAdd(label, key, byKey));
            addCount++;
         }
         else {
            resolvedEntries.add(resolveRemove(label, key, byKey));
            removeCount++;
         }
      }

      boolean deLicensingWarning =
         computeDeLicensingWarning(installed.size(), addCount, removeCount);

      List<PlanChange> changes = new ArrayList<>();

      for(ResolvedEntry entry : resolvedEntries) {
         changes.add(withWarningIfApplicable(entry, deLicensingWarning));
      }

      String task = req.getTask().trim();
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), true, true,
                              hash(task, changes, installed.size()));
   }

   // ---------------------------------------------------------------- add

   private ResolvedEntry resolveAdd(String label, String key, Map<String, License> byKey) {
      if(byKey.containsKey(key)) {
         throw new IllegalArgumentException(
            label + ".key: \"" + key + "\" is already installed -- adding it again is refused " +
            "rather than passed through, because EnterpriseLicenseStrategy.addLicense's cluster-" +
            "node claiming exchange has no already-installed guard of its own and can cause a " +
            "second cluster node to also claim a non-pooled key on a duplicate add " +
            "(03-reconcile.md addition 2); remove it first if you intend to reinstall it");
      }

      License resolved = licenseManager.parseLicense(key);

      if(resolved.type() == LicenseType.INVALID || !resolved.valid()) {
         throw new IllegalArgumentException(
            label + ".key: \"" + key + "\" does not resolve to a valid license (type=" +
            resolved.type() + ", valid=" + resolved.valid() + ") -- refused rather than installed " +
            "as a permanently-broken license entry (01-spec.md section 2/14 D2)");
      }

      LicenseKeyProjection projection = LicenseKeyProjection.of(resolved);
      PlanChange change = new PlanChange(key, NOT_ORG_SCOPED, null, projection.canonical(),
                                         AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE,
                                         true, "add license key " + key + " (" + resolved.type() + ")");
      return new ResolvedEntry(VERB_ADD, change);
   }

   // ---------------------------------------------------------------- remove

   private ResolvedEntry resolveRemove(String label, String key, Map<String, License> byKey) {
      License current = byKey.get(key);

      if(current == null) {
         throw new IllegalArgumentException(
            label + ".key: \"" + key + "\" is not currently installed -- removing it would be a " +
            "silent no-op at the LicenseManager.removeLicense level (01-spec.md section 2), refused " +
            "here instead");
      }

      LicenseKeyProjection projection = LicenseKeyProjection.of(current);
      PlanChange change = new PlanChange(key, NOT_ORG_SCOPED, projection.canonical(), null,
                                         AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE,
                                         true, "remove license key " + key + " (" + current.type() + ")");
      return new ResolvedEntry(VERB_REMOVE, change);
   }

   // ---------------------------------------------------------------- de-licensing warning

   /**
    * {@code resultingInstalledCount = currentCount + addCount - removeCount} -- every accepted
    * {@code add} is guaranteed to target a currently-not-installed key (a duplicate add is refused
    * above) and every accepted {@code remove} is guaranteed to target a currently-installed key, so
    * this net computation (01-spec.md section 5) needs no per-key set arithmetic beyond the two
    * counts. Package-visible so {@link LicenseChangesetApplyService} can re-derive this fact from
    * fresh state at apply time, exactly as it re-derives every other plan-time check (mirroring
    * {@code DataSourceApplyRequest.acknowledgeIrreversibleDelete}'s own apply-time re-derivation
    * pattern).
    */
   static boolean computeDeLicensingWarning(int currentInstalledCount, int addCount, int removeCount) {
      return removeCount > 0 && currentInstalledCount + addCount - removeCount <= 0;
   }

   /** Prepends the de-licensing advisory to every {@code remove}-verb entry's description when the
    * plan-wide count would reach zero (01-spec.md section 5) -- a first-class, structured-field
    * disclosure (the {@link PlanChange#description()} column), not buried in free text elsewhere,
    * matching how this program's own precedent (identities' partial-membership warning) surfaces a
    * plan-level fact without a bespoke top-level field on the shared {@link ResolvedPlan}. */
   private static PlanChange withWarningIfApplicable(ResolvedEntry entry, boolean deLicensingWarning) {
      PlanChange change = entry.change();

      if(!deLicensingWarning || !VERB_REMOVE.equals(entry.verb())) {
         return change;
      }

      String description = "WARNING: after this plan applies, 0 license keys will remain " +
         "installed -- acknowledgeDelicensing:true is required on apply (01-spec.md section 5/6). " +
         change.description();
      return new PlanChange(change.property(), change.orgId(), change.currentValue(),
                            change.proposedValue(), change.risk(), change.snapshotScope(),
                            change.recognized(), description);
   }

   // ---------------------------------------------------------------- validation helpers

   static String requireVerb(String label, String verb) {
      if(VERB_ADD.equals(verb) || VERB_REMOVE.equals(verb)) {
         return verb;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"" + VERB_ADD + "\" or \"" + VERB_REMOVE + "\", got " + verb);
   }

   static String requireKey(String label, String key) {
      if(key == null || key.trim().isEmpty()) {
         throw new IllegalArgumentException(label + ".key: required");
      }

      return key.trim();
   }

   private static void requireUnseen(String label, String key, Set<String> seenKeys) {
      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for key \"" + key + "\"; list each key at most once");
      }
   }

   // ---------------------------------------------------------------- hash

   /**
    * SHA-256 over the canonical plan. Same field-order/control-character contract as every prior
    * area's own {@code hash} method, extended with the raw installed-license count (01-spec.md
    * section 5) -- the de-licensing warning depends on the WHOLE deployment's installed count, not
    * only the keys this plan touches, so a concurrent add/remove of a key this plan never mentions
    * must still perturb the hash.
    */
   private static String hash(String task, List<PlanChange> changes, int installedCount) {
      StringBuilder canonical = new StringBuilder(task).append('\n')
         .append("installedCount:").append(installedCount).append(SEP);

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonicalValue(change.currentValue())).append(SEP)
            .append(canonicalValue(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
      }

      try {
         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash a license change plan", e);
      }
   }

   private static String canonicalValue(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   /** Licensing is deployment-wide, not org-scoped (01-spec.md section 1) -- every {@link
    * PlanChange} this service builds passes this in place of a bare {@code null} literal so the
    * omission reads as deliberate, matching {@code ProviderChangePlanService.NOT_ORG_SCOPED}. */
   private static final String NOT_ORG_SCOPED = null;
   static final String VERB_ADD = LicenseChangeRequest.VERB_ADD;
   static final String VERB_REMOVE = LicenseChangeRequest.VERB_REMOVE;
   private final LicenseManager licenseManager;

   /** One resolved change plus the verb that produced it, so the de-licensing warning can be
    * applied to only the {@code remove}-verb entries after the plan-wide count is known. */
   private record ResolvedEntry(String verb, PlanChange change) {
   }
}
