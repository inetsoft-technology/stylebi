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
import inetsoft.uql.util.Identity;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.SecurityProviderGuard;
import inetsoft.web.admin.ai.TaskAuditToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves a requested list of permission-grant changes into a {@link ResolvedPlan} and hashes it
 * -- the permissions analog of {@code inetsoft.web.admin.ai.AdminChangePlanService} and (within
 * this run) {@code inetsoft.enterprise.web.admin.ai.schedule.ScheduleChangePlanService},
 * replicated rather than shared (spec section 6, carry-forward item 5 -- this area is the
 * <em>second</em> structural-area data point Track C.0's closing note asked for).
 *
 * <p>Calls no new {@code SecurityService} method -- every check this service needs is either
 * data it already has (the caller's {@link Principal}, the raw {@link Permission} it reads
 * directly) or an existing public method ({@code getPermissionGrant}). This is a deliberately
 * simpler integration than schedule tasks needed (see {@code 02-plan.md}).
 */
@Component
public class PermissionChangePlanService {
   @Autowired
   public PermissionChangePlanService(SecurityService securityService,
                                      SecurityEngine securityEngine)
   {
      this.securityService = securityService;
      this.securityEngine = securityEngine;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but does perform live reads (the raw
    * {@code Permission} for each targeted resource) and one live permission-grant lookup per
    * change (via {@code SecurityService.getPermissionGrant}, which itself enforces
    * {@code checkPermissionAccess} -- the real authorization boundary this preflight sits in
    * front of, not behind).
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized verb, an excluded/unrecognized resource
    *                                 type, an unrecognized identity type, a create whose grant
    *                                 already exists, an update/delete whose grant does not exist,
    *                                 missing/present {@code actions} for the wrong verb, or the
    *                                 self-lockout preflight failing.
    */
   public ResolvedPlan resolve(PermissionChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      // A virtual-backed provider reads back a canned Permission, so every before-state below
      // would be fabricated and the apply this previews could only fail. Refuse here rather than
      // hand back a plan that cannot be applied.
      SecurityProviderGuard.requireWritableAuthorization(securityEngine);

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      String currentOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      int index = 0;

      for(PermissionChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         changes.add(resolveOne(label, change, user, currentOrgId, seenKeys));
      }

      // Every verb is risk:high, snapshotScope:value, unconditionally (spec section 4/7) -- no
      // "recognized" axis the way an uncatalogued property has one.
      String planHash = hash(changes);
      return new ResolvedPlan(req.getTask().trim(), Collections.unmodifiableList(changes),
                              false, true, planHash, TaskAuditToken.issue(planHash, req.getTask().trim()));
   }

   private PlanChange resolveOne(String label, PermissionChangeRequest change, Principal user,
                                 String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String verb = requireVerb(label, change.getVerb());
      ResourceType resourceType = requireAllowedResourceType(label, change.getResourceType());
      String resourcePath = requireNonBlank(label + ".resourcePath", change.getResourcePath());
      String identityType = requireIdentityType(label, change.getIdentityType());
      IdentityID identityId = requireIdentityId(label, change.getIdentityId(), currentOrgId);

      String seenKey = grantKey(resourceType.name(), resourcePath, identityType, identityId);

      if(!seenKeys.add(seenKey)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for " + seenKey + "; list each grant at most once");
      }

      Permission rawBefore = securityEngine.getSecurityProvider()
         .getPermission(resourceType, resourcePath, null);
      String totalProjection = PermissionProjection.projectTotal(rawBefore, currentOrgId);

      PermissionGrant existing = securityService.getPermissionGrant(
         resourcePath, resourceType.name(), identityId.getName(), identityType, user);

      if(PermissionChangeRequest.VERB_CREATE.equals(verb)) {
         if(change.getActions() == null || change.getActions().isEmpty()) {
            throw new IllegalArgumentException(label + ".actions: required for verb=create");
         }

         requireActions(label, change.getActions());

         if(existing != null) {
            throw new IllegalArgumentException(
               label + ": a grant already exists for " + seenKey + "; use verb=update instead");
         }

         String proposed = PermissionProjection.projectTotalWithOverride(
            rawBefore, currentOrgId, identityType, identityId, change.getActions());
         return new PlanChange(seenKey, identityId.getOrgID(), totalProjection, proposed,
                               AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true,
                               "create permission grant");
      }

      if(PermissionChangeRequest.VERB_UPDATE.equals(verb)) {
         if(change.getActions() == null || change.getActions().isEmpty()) {
            throw new IllegalArgumentException(label + ".actions: required for verb=update");
         }

         requireActions(label, change.getActions());

         if(existing == null) {
            throw new IllegalArgumentException(
               label + ": no grant exists for " + seenKey + "; use verb=create instead");
         }

         requireNoSelfLockout(label, user, identityType, identityId, existing, change.getActions());
         String proposed = PermissionProjection.projectTotalWithOverride(
            rawBefore, currentOrgId, identityType, identityId, change.getActions());
         return new PlanChange(seenKey, identityId.getOrgID(), totalProjection, proposed,
                               AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true,
                               "update permission grant");
      }

      // delete
      if(change.getActions() != null && !change.getActions().isEmpty()) {
         throw new IllegalArgumentException(
            label + ".actions: not used for verb=delete; remove it or use verb=create/update");
      }

      if(existing == null) {
         throw new IllegalArgumentException(label + ": no grant exists for " + seenKey);
      }

      requireNoSelfLockout(label, user, identityType, identityId, existing, null);
      String proposed = PermissionProjection.projectTotalWithOverride(
         rawBefore, currentOrgId, identityType, identityId, null);
      return new PlanChange(seenKey, identityId.getOrgID(), totalProjection, proposed,
                            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_VALUE, true,
                            "delete permission grant");
   }

   /**
    * The self-lockout preflight (spec section 4): refuses a plan that would remove the caller's
    * own {@code ADMIN} grant. Direct-identity match only (spec section 14 item 6 -- group/role
    * membership is not resolved). Applied unconditionally, not gated on whether
    * {@code checkPermissionAccess}'s action-tree branch would have covered the caller anyway --
    * determining that precisely would mean re-deriving its private tree-walk (spec section 4's
    * correction); over-refusing here is the accepted cost.
    */
   private static void requireNoSelfLockout(String label, Principal user, String identityType,
                                             IdentityID identityId, PermissionGrant existing,
                                             List<String> newActions)
   {
      if(!Identity.Type.USER.name().equals(identityType)) {
         return;
      }

      IdentityID caller = IdentityID.getIdentityIDFromKey(user.getName());

      if(!caller.equals(identityId)) {
         return;
      }

      boolean hadAdmin = existing.getActions() != null &&
         existing.getActions().contains(ResourceAction.ADMIN.name());

      if(!hadAdmin) {
         return;
      }

      boolean keepsAdmin = newActions != null && newActions.contains(ResourceAction.ADMIN.name());

      if(!keepsAdmin) {
         throw new IllegalArgumentException(label + ": this change would remove your own ADMIN "
            + "grant on this resource, which would prevent this plan's own rollback from being "
            + "performed by you -- refusing to plan a change whose own undo you could not perform");
      }
   }

   static String requireVerb(String label, String verb) {
      if(PermissionChangeRequest.VERB_CREATE.equals(verb) ||
         PermissionChangeRequest.VERB_UPDATE.equals(verb) ||
         PermissionChangeRequest.VERB_DELETE.equals(verb))
      {
         return verb;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"" + PermissionChangeRequest.VERB_CREATE + "\", \"" +
         PermissionChangeRequest.VERB_UPDATE + "\", or \"" + PermissionChangeRequest.VERB_DELETE +
         "\", got " + String.valueOf(verb));
   }

   static ResourceType requireAllowedResourceType(String label, String resourceType) {
      if(resourceType == null || !ALLOWED_RESOURCE_TYPES.contains(resourceType)) {
         throw new IllegalArgumentException(
            label + ".resourceType: must be one of " + ALLOWED_RESOURCE_TYPES +
            " in this cut, got " + String.valueOf(resourceType) +
            " (SCHEDULE_TASK/SCHEDULE_TASK_FOLDER excluded due to a found parity gap; " +
            "SECURITY_*/LOGIN_AS excluded as identity-adjacent; EM/EM_COMPONENT excluded " +
            "because checkPermissionAccess routes every Security-Actions capability type " +
            "through a single EM_COMPONENT/\"settings/security/actions\" ACCESS check, so making " +
            "it settable here would let a caller grant themselves that master gate -- see " +
            "00-charter.md)");
      }

      return ResourceType.valueOf(resourceType);
   }

   static String requireIdentityType(String label, String identityType) {
      if(identityType == null) {
         throw new IllegalArgumentException(label + ".identityType: required");
      }

      try {
         return Identity.Type.valueOf(identityType).name();
      }
      catch(IllegalArgumentException e) {
         throw new IllegalArgumentException(
            label + ".identityType: must be USER, GROUP, ROLE, or ORGANIZATION, got " +
            identityType);
      }
   }

   /**
    * Parses a bare identity name or a {@code name:orgId}-shaped key (spec section 11 -- a plain
    * colon, the human/tool-facing wire format, not {@code IdentityID.convertToKey()}'s internal
    * {@code "~;~"} delimiter, which never appears in a caller-supplied argument). **Deliberately
    * does not use {@code IdentityID.getIdentityIDFromKey} directly** -- that method defaults a
    * bare name's org from {@code ThreadContext.getPrincipal()}, which is not set outside a live
    * request thread (untestable without a thread-local fixture) and, more importantly, an explicit
    * org suffix would silently diverge from what {@code SecurityService.getPermissionGrant} can
    * actually look up: that method reconstructs the target identity using the *caller's own* org
    * regardless of what org the lookup key names (read directly -- {@code new
    * IdentityID(idName, IdentityID.getIdentityIDFromKey(principal.getName()).orgID)}), while
    * {@code createPermissionGrant}/{@code updatePermissionGrant}/{@code deletePermissionGrant}
    * take a full {@code PermissionGrant} with an arbitrary org and do not enforce the same
    * constraint -- an asymmetry that would make an explicit cross-org {@code identityId} silently
    * check one org and mutate another. Refusing any org suffix that does not match the caller's
    * current org sidesteps that asymmetry entirely, consistent with this area's "no org-targeting
    * parameter" decision (spec section 4a/10).
    */
   static IdentityID requireIdentityId(String label, String identityId,
                                               String currentOrgId)
   {
      String trimmed = requireNonBlank(label + ".identityId", identityId);
      int delim = trimmed.indexOf(':');

      if(delim < 0) {
         return new IdentityID(trimmed, currentOrgId);
      }

      String name = trimmed.substring(0, delim);
      String orgId = trimmed.substring(delim + 1);

      if(!Objects.equals(orgId, currentOrgId)) {
         throw new IllegalArgumentException(
            label + ".identityId: targets org \"" + orgId + "\", but this area only supports " +
            "the caller's current org (\"" + currentOrgId + "\") in this cut -- no org-targeting " +
            "parameter exists yet");
      }

      return new IdentityID(name, currentOrgId);
   }

   /**
    * Validates every action name against {@link ResourceAction}.
    *
    * <p>Nothing downstream does: {@code SecurityService.setPermission} selects grants with an
    * exact-case {@code grant.getActions().contains(action.name())}, so an unrecognized or
    * lowercase name matches no action, is silently dropped, and persists nothing without throwing.
    * The apply service's own post-write verification then compares the empty result against the
    * requested list and reports a mismatch -- blaming the write for what is really a bad argument.
    * Rejecting it at plan time keeps that failure a 400 naming the offending action.
    */
   static List<String> requireActions(String label, List<String> actions) {
      if(actions == null || actions.isEmpty()) {
         throw new IllegalArgumentException(label + ".actions: required");
      }

      for(String action : actions) {
         if(action == null || !ALLOWED_ACTIONS.contains(action)) {
            throw new IllegalArgumentException(
               label + ".actions: must each be one of " + ALLOWED_ACTIONS + ", got " +
               String.valueOf(action) + " (names are case-sensitive)");
         }
      }

      return actions;
   }

   static String requireNonBlank(String field, String value) {
      if(value == null || value.trim().isEmpty()) {
         throw new IllegalArgumentException(field + ": required");
      }

      return value.trim();
   }

   static String grantKey(String resourceType, String resourcePath, String identityType,
                          IdentityID identityId)
   {
      return resourceType + "|" + resourcePath + "|" + identityType + "|" +
         identityId.convertToKey();
   }

   /** SHA-256 over the canonical plan. Same field-order/control-character contract as
    * {@code AdminChangePlanService#hash} and {@code ScheduleChangePlanService#hash}.
    *
    * <p>Deliberately excludes {@code task}: it is a free-text, audit-only label (see {@link
    * PermissionChangesetApplyService}'s {@code writeAudit} calls, its only use post-resolve) with
    * no bearing on what is actually mutated or verified, and the caller is never required to
    * replay it byte-for-byte between preview and apply. */
   private static String hash(List<PlanChange> changes) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonical(change.currentValue())).append(SEP)
            .append(canonical(change.proposedValue())).append(SEP)
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
         throw new IllegalStateException("SHA-256 is required to hash a permission change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   /** Resource types this cut administers permissions on -- everything else is refused loud (spec
    * section 1). Content/asset-shaped, hierarchical types EM's own repository-permission page
    * already administers, PLUS the capability-level "Security Actions" leaf types (bug 76600
    * Gap 1) -- every leaf {@code ActionPermissionService.getActionTree} builds, each keyed by
    * exactly this same (ResourceType, resourcePath) shape, minus SCHEDULE_TASK/
    * SCHEDULE_TASK_FOLDER (found parity gap, spec section 4a) and EM/EM_COMPONENT/LOGIN_AS
    * (excluded on purpose: {@code SecurityService.checkPermissionAccess} routes every one of
    * these Security-Actions leaf types through a single EM_COMPONENT/"settings/security/actions"
    * ACCESS check on the caller -- making EM_COMPONENT itself settable here would let a caller
    * grant themselves that master gate and, through it, editing rights over every capability
    * type in this list at once). */
   static final Set<String> ALLOWED_RESOURCE_TYPES = Set.of(
      "ASSET", "REPORT", "DASHBOARD", "DATA_SOURCE", "DATA_SOURCE_FOLDER", "QUERY", "QUERY_FOLDER",
      "DATA_MODEL_FOLDER", "SCRIPT", "SCRIPT_LIBRARY", "TABLE_STYLE", "TABLE_STYLE_LIBRARY",
      "CHART_TYPE", "CHART_TYPE_FOLDER", "CUBE", "PROTOTYPE", "LIBRARY",
      "VIEWSHEET_ACTION", "SCHEDULE_OPTION", "AI_ASSISTANT", "CROSS_JOIN", "CREATE_DATA_SOURCE",
      "VIEWSHEET_CALCULATED_FIELD", "WORKSHEET_EXPRESSION_COLUMN", "FREE_FORM_SQL",
      "MATERIALIZATION", "MY_DASHBOARDS", "PHYSICAL_TABLE",
      "PORTAL_REPOSITORY_TREE_DRAG_AND_DROP", "PROFILE", "UPLOAD_DRIVERS", "DEVICE", "VIEWSHEET",
      "WORKSHEET", "VIEWSHEET_TOOLBAR_ACTION", "SHARE", "SCHEDULER", "PORTAL_TAB");

   /** Every {@link ResourceAction} constant, by exact name -- what {@link #requireActions} checks
    * against and what the rejection message lists. */
   static final Set<String> ALLOWED_ACTIONS =
      Collections.unmodifiableSortedSet(Arrays.stream(ResourceAction.values())
         .map(ResourceAction::name)
         .collect(Collectors.toCollection(TreeSet::new)));

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final SecurityService securityService;
   private final SecurityEngine securityEngine;
}
