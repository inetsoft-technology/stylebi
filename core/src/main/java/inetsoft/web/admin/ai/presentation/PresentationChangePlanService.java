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
package inetsoft.web.admin.ai.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested list of presentation sub-model changes into a {@link ResolvedPlan} and hashes
 * it -- the presentation analog of {@code inetsoft.web.admin.ai.AdminChangePlanService} and (within
 * this run) {@code LicenseChangePlanService}/{@code ClusterChangePlanService}, replicated rather than
 * shared (01-spec.md section 6, matching every prior area's own "replicate, don't generalize"
 * precedent).
 *
 * <p><b>Deviation from 01-spec.md's Flagged Decision 5, recorded here per this build's own reporting
 * duty.</b> The spec recommends area-local, object-typed {@code PresentationPlanChange}/
 * {@code PresentationResolvedPlan} records instead of the shared String-typed
 * {@code PlanChange}/{@code ResolvedPlan}, reasoning that forcing a compound sub-model value through
 * a {@code String} is "needless double-serialization". That reasoning was sound against the one
 * precedent the spec had read (C.4's {@code ProviderApplyOutcome}), but this pin also contains two
 * more recent, actually-shipped community-tier areas the spec did not have: {@code
 * ClusterChangePlanService} and {@code LicenseChangePlanService}, both of which reuse the shared
 * {@code PlanChange}/{@code ResolvedPlan} unmodified and JSON-serialize into the existing {@code
 * String} fields, reserving an area-local record only for the {@code ApplyOutcome} shape (adding one
 * field, {@code advisory}, in licensing's case). Reusing the shared plan types here matches that
 * (more current) established pattern, avoids a parallel {@code PlanHashMismatchException}/{@code
 * ResolvedPlan} type family, and lets this area reuse {@code AdminChangesetApplyService.
 * PlanHashMismatchException} and its controller-level 409 handling verbatim, the same way cluster and
 * licensing both do. See {@link PresentationApplyOutcome} for where an area-local record IS still
 * needed (the apply side, to keep {@code before}/{@code after} as nested JSON rather than an escaped
 * string).
 */
@Component
public class PresentationChangePlanService {
   @Autowired
   public PresentationChangePlanService(PresentationSettingsAccess access) {
      this.access = access;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but does perform a live read of every named
    * sub-model's current value.
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *         list, an unrecognized verb/subModel/scope, a duplicate sub-model+scope pair, an
    *         unrecognized {@code spec} field, {@code scope: "organization"} for a global-only
    *         sub-model, an unsafe {@code lookAndFeel} file name, a secret {@code webMap} field, or a
    *         {@code portalIntegration.tabs} array that omits a current tab.
    */
   public ResolvedPlan resolve(PresentationChangePlanRequest req, Principal principal)
      throws Exception
   {
      List<ResolvedChange> resolved = resolveEntries(req, principal);
      List<PlanChange> changes = new ArrayList<>();

      for(ResolvedChange entry : resolved) {
         changes.add(entry.planChange());
      }

      String task = req.getTask().trim();
      List<PlanChange> immutableChanges = Collections.unmodifiableList(changes);
      return new ResolvedPlan(task, immutableChanges, true, true, hash(immutableChanges));
   }

   /**
    * Same resolution {@link #resolve} performs, but also returns the actual proposed model object
    * (not just its JSON projection) for each change, so {@link PresentationChangesetApplyService} can
    * pass it straight to {@link PresentationSettingsAccess#write} without re-deriving it a second way.
    * Package-visible, mirroring {@code ProviderChangePlanService}'s own preflight-methods-reused-at-
    * apply-time shape.
    */
   List<ResolvedChange> resolveEntries(PresentationChangePlanRequest req, Principal principal)
      throws Exception
   {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<ResolvedChange> result = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      int index = 0;

      for(PresentationChangeRequest raw : req.getChanges()) {
         String label = "changes[" + index++ + "]";

         if(raw == null) {
            throw new IllegalArgumentException(label + ": must not be null");
         }

         requireVerb(label, raw.getVerb());
         PresentationSubModel subModel = requireSubModel(label, raw.getSubModel());
         boolean global = requireScope(label, subModel, raw.getScope());
         requireUnseen(label, subModel, global, seen);

         JsonNode spec = requireSpecObject(label, raw.getSpec());
         requireKnownFields(label, subModel, spec);

         Object currentModel = access.read(subModel, principal, global);
         JsonNode currentNode = PresentationJson.toNode(currentModel);

         if(subModel == PresentationSubModel.PORTAL_INTEGRATION) {
            requireWholeTabList(label, spec, currentNode);
         }

         if(subModel == PresentationSubModel.LOOK_AND_FEEL) {
            spec = sanitizeLookAndFeelFileNames(label, spec);
         }

         requireNoSecretFields(label, subModel, spec);

         JsonNode mergedNode = PresentationJson.merge(currentNode, spec);
         Object proposedModel;

         try {
            proposedModel = PresentationJson.toModel(mergedNode, subModel.modelClass());
         }
         catch(Exception e) {
            throw new IllegalArgumentException(
               label + ".spec: does not produce a valid \"" + subModel.key() + "\" value (" +
               e.getMessage() + ")", e);
         }

         String orgId = global ? null :
            OrganizationManager.getInstance().getCurrentOrgID();
         String scopeLabel = global ? PresentationChangeRequest.SCOPE_GLOBAL :
            PresentationChangeRequest.SCOPE_ORGANIZATION;
         String property = subModel.key() + ":" + scopeLabel;

         PlanChange planChange = new PlanChange(
            property, orgId, projectedValue(subModel, currentNode),
            projectedValue(subModel, mergedNode), subModel.risk(), subModel.scope(), true,
            "update " + subModel.key() + " (" + scopeLabel + " scope)");

         result.add(new ResolvedChange(subModel, global, currentModel, proposedModel, planChange));
      }

      return result;
   }

   // ---------------------------------------------------------------- validation helpers

   static void requireVerb(String label, String verb) {
      if(verb == null || !PresentationChangeRequest.VERB_UPDATE.equalsIgnoreCase(verb.trim())) {
         throw new IllegalArgumentException(
            label + ".verb: must be \"update\" -- presentation sub-models cannot be created or " +
            "destroyed, got " + verb);
      }
   }

   static PresentationSubModel requireSubModel(String label, String subModel) {
      if(subModel == null) {
         throw new IllegalArgumentException(label + ".subModel: required");
      }

      try {
         return PresentationSubModel.require(subModel);
      }
      catch(IllegalArgumentException e) {
         throw new IllegalArgumentException(label + "." + e.getMessage());
      }
   }

   static boolean requireScope(String label, PresentationSubModel subModel, String scope) {
      if(scope == null) {
         throw new IllegalArgumentException(
            label + ".scope: required, must be \"global\" or \"organization\"");
      }

      String trimmed = scope.trim();
      boolean global;

      if(PresentationChangeRequest.SCOPE_GLOBAL.equalsIgnoreCase(trimmed)) {
         global = true;
      }
      else if(PresentationChangeRequest.SCOPE_ORGANIZATION.equalsIgnoreCase(trimmed)) {
         global = false;
      }
      else {
         throw new IllegalArgumentException(
            label + ".scope: must be \"global\" or \"organization\", got \"" + scope + "\"");
      }

      if(!global && subModel.globalOnly()) {
         throw new IllegalArgumentException(
            label + ".scope: \"" + subModel.key() + "\" has no organization-scoped layer -- it is " +
            "global-only. \"organization\" always means the calling principal's own organization, " +
            "never a different org, and this sub-model does not have a separate per-organization " +
            "value at all.");
      }

      return global;
   }

   private static void requireUnseen(String label, PresentationSubModel subModel, boolean global,
                                      Set<String> seen)
   {
      String key = subModel.key() + ":" + global;

      if(!seen.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for \"" + subModel.key() + "\" (" +
            (global ? "global" : "organization") + " scope); list each sub-model/scope pair once");
      }
   }

   private static JsonNode requireSpecObject(String label, JsonNode spec) {
      if(spec == null || spec.isNull() || !spec.isObject() || spec.isEmpty()) {
         throw new IllegalArgumentException(
            label + ".spec: required, must be a non-empty object naming only the fields being " +
            "changed");
      }

      return spec;
   }

   private static void requireKnownFields(String label, PresentationSubModel subModel, JsonNode spec) {
      Set<String> valid = subModel.fieldNames();
      Iterator<String> names = spec.fieldNames();

      while(names.hasNext()) {
         String field = names.next();

         if(!valid.contains(field)) {
            throw new IllegalArgumentException(
               label + ".spec." + field + ": not a field of \"" + subModel.key() + "\" -- valid " +
               "fields are " + valid);
         }
      }
   }

   /** 03-reconcile.md Addition 2: {@code portalIntegration.tabs} must always be every current tab, in
    * full -- {@code PortalIntegrationViewSettingsService.setModel} both indexes into the live tab list
    * by a caller-echoed {@code originalIndex} with no bounds/identity check AND does a whole-list
    * replace underneath, so a shorter {@code tabs} array would silently drop the missing tabs. */
   private static void requireWholeTabList(String label, JsonNode spec, JsonNode current) {
      JsonNode tabs = spec.get("tabs");

      if(tabs == null) {
         return;
      }

      if(!tabs.isArray()) {
         throw new IllegalArgumentException(label + ".spec.tabs: must be an array");
      }

      JsonNode currentTabs = current.get("tabs");
      int currentCount = currentTabs == null ? 0 : currentTabs.size();

      if(tabs.size() != currentCount) {
         throw new IllegalArgumentException(
            label + ".spec.tabs: must include every current tab (" + currentCount + "), not a " +
            "partial list -- got " + tabs.size() + " entries. portalIntegration.tabs is a " +
            "whole-list replace underneath (PortalIntegrationViewSettingsService.setModel); a " +
            "shorter list would silently drop the tabs left out, not leave them unchanged.");
      }
   }

   /** 03-reconcile.md Addition 1: {@code lookAndFeel}'s org-scoped CSS-upload path
    * ({@code LookAndFeelService.setViewsheet}) writes a caller-supplied file name straight to
    * {@code DataSpace} with no validation of its own; the extension suffix on {@code setLogo}/
    * {@code setFavicon} has the same gap in miniature. Normalizes to a safe basename (the natural
    * "pasted a full path" LLM mistake) and refuses loud only when nothing safe remains. */
   private static JsonNode sanitizeLookAndFeelFileNames(String label, JsonNode spec) {
      ObjectNode copy = spec.deepCopy();

      for(String fileField : List.of("logoFile", "faviconFile", "viewsheetFile")) {
         JsonNode file = copy.get(fileField);

         if(file != null && file.isObject() && file.hasNonNull("name")) {
            String sanitized =
               sanitizeBaseName(label + ".spec." + fileField + ".name", file.get("name").asText());
            ((ObjectNode) file).put("name", sanitized);
         }
      }

      return copy;
   }

   private static String sanitizeBaseName(String label, String raw) {
      String normalized = raw.replace('\\', '/');
      int idx = normalized.lastIndexOf('/');
      String base = (idx >= 0 ? normalized.substring(idx + 1) : normalized).trim();

      if(base.isEmpty() || base.equals(".") || base.equals("..") || base.contains("..")) {
         throw new IllegalArgumentException(
            label + ": \"" + raw + "\" is not a safe file name -- once any path is stripped it " +
            "must not be blank or still contain \"..\" (lookAndFeel's org-scoped upload path " +
            "writes this name to storage verbatim; see 03-reconcile.md Addition 1)");
      }

      return base;
   }

   /** 01-spec.md section 9: a sub-model's {@link PresentationSubModel#secretFields()} are treated as
    * secret-classified for this area even though the underlying service returns them in the clear --
    * refused outright in a write {@code spec} (matching {@code AdminPropertyCatalog.isSecret}'s own
    * "refuse to change a secret through admin-chat" treatment), masked on every read (see
    * {@link #projectedValue}).
    *
    * <p>Called for every sub-model, not just the ones that have secrets -- the set is empty for the
    * other fourteen, so the loop is a no-op. Guarding the call with a sub-model test is what let
    * {@code share}'s two webhook URLs stay writable here after the properties path stopped writing
    * them (Bug #76170), and it would let the next addition do the same.
    *
    * <p>Refusing the write is what makes {@code AdminPropertyCatalog}'s guidance for these names
    * ("admin-chat will not write it ... configure it through Enterprise Manager") true of the whole
    * admin-chat surface rather than of one endpoint. That string is instruction text read by an
    * agent, so a sub-model that accepts a value the properties path refuses does not merely leave a
    * second way in -- it makes the guidance a false statement. */
   private static void requireNoSecretFields(String label, PresentationSubModel subModel,
                                             JsonNode spec)
   {
      for(String secret : subModel.secretFields()) {
         if(spec.has(secret)) {
            throw new IllegalArgumentException(
               label + ".spec." + secret + ": secret-classified credentials cannot be set through " +
               "admin-chat (01-spec.md section 9) -- change it through Enterprise Manager instead");
         }
      }
   }

   /** Package-visible so {@link PresentationChangesetApplyService} can compute the identical
    * before/after projection when verifying a write's read-back, instead of re-deriving its own. */
   static String projectedValue(PresentationSubModel subModel, JsonNode node) {
      return PresentationJson.writeString(PresentationJson.maskSecrets(subModel, node));
   }

   // ---------------------------------------------------------------- hash

   /** SHA-256 over the canonical plan, same field-order/control-character-free contract as every
    * other area's own {@code hash} method. Package-visible so {@link PresentationChangesetApplyService}
    * can recompute the identical hash from the same {@link ResolvedChange#planChange()} list
    * {@link #resolveEntries} already produced, instead of resolving the whole plan a second time.
    *
    * <p>Deliberately does NOT take {@code task}: the free-text task description is never
    * canonicalized or otherwise constrained (only checked for non-blank in {@link #resolveEntries}),
    * so two equally valid paraphrases of the same intent over the identical {@code changes} would
    * otherwise hash differently and trip a false {@code PlanHashMismatchException} between preview
    * and apply. {@code task} is write-only downstream -- it flows into
    * {@link PresentationChangesetApplyService#writeAudit} as
    * {@code AdminChangeRecord.setTaskDescription}, a plain audit label that is never read back or
    * compared -- so excluding it from the hash does not weaken the gate against a changed plan. */
   static String hash(List<PlanChange> changes) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonicalValue(change.orgId())).append(SEP)
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
         throw new IllegalStateException("SHA-256 is required to hash a presentation change plan", e);
      }
   }

   private static String canonicalValue(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final PresentationSettingsAccess access;

   /** One resolved change: the sub-model/scope it targets, its current and ready-to-write proposed
    * model objects, and the {@link PlanChange} record describing it -- returned by
    * {@link #resolveEntries} so {@link PresentationChangesetApplyService} can write
    * {@link #proposedModel()} (and, on rollback, write {@link #currentModel()} back) directly instead
    * of re-deriving either from JSON a second time. */
   record ResolvedChange(PresentationSubModel subModel, boolean global, Object currentModel,
                         Object proposedModel, PlanChange planChange)
   {
   }
}
