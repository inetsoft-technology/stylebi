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
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.AuthenticationProvider;
import inetsoft.sree.security.Group;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.AdminIdentityRefs;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.PlanHashInputs;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.SecurityProviderGuard;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.security.auth.MissingResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested list of identity changes into a {@link ResolvedPlan} and hashes it -- the
 * identities analog of {@code inetsoft.web.admin.ai.AdminChangePlanService} and (within this run)
 * {@code ScheduleChangePlanService}/{@code PermissionChangePlanService}, replicated rather than
 * shared (spec section 6, carry-forward item 5 -- the <em>third</em> structural-area data point).
 *
 * <p>Calls no new {@code SecurityService} method -- every operation this service needs is
 * already public ({@code get*}/{@code create*}/{@code delete*} per unit). The one piece of net-new
 * authorization logic this track adds is the default-org/self-org delete refusal (spec section
 * 4a/6), since {@code SecurityService.deleteOrganization} itself lacks it.
 */
@Component
public class IdentityChangePlanService {
   @Autowired
   public IdentityChangePlanService(SecurityService securityService,
                                    SecurityEngine securityEngine)
   {
      this.securityService = securityService;
      this.securityEngine = securityEngine;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but does perform live reads (one
    * {@code get*} per change, to capture {@code currentValue} for a delete or to confirm a create's
    * id is free).
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized verb/unitType, a field illegal for the
    *                                 resolved unitType, a create whose id already exists, a delete
    *                                 whose id does not exist, or (organization delete only) the
    *                                 default-org/self-org refusal.
    */
   public ResolvedPlan resolve(IdentityChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      // Matches PermissionChangePlanService: a plan resolved against a provider that cannot be
      // written to is a plan (and a signed task token) the apply guard is bound to reject, so
      // refuse here rather than hand back a green preview followed by a failing apply.
      SecurityProviderGuard.requireEditableAuthentication(securityEngine);

      String currentOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      // Bug #79016: hidden per-change hash input for a supplied user password (see hash()).
      Map<String, String> hiddenInputs = new LinkedHashMap<>();
      MemberState members = new MemberState(plannedCreates(req, currentOrgId));
      int index = 0;

      for(IdentityChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         PlanChange resolved =
            resolveOne(label, change, user, currentOrgId, seenKeys, hiddenInputs);
         requireMembersResolvable(label, change, resolved, user, members);
         changes.add(resolved);
      }

      // Every verb is risk:high, snapshotScope:storage, unconditionally (spec section 4/7).
      String planHash = hash(changes, hiddenInputs);
      return new ResolvedPlan(req.getTask().trim(), Collections.unmodifiableList(changes),
                              true, true, planHash, TaskAuditToken.issue(planHash, req.getTask().trim()));
   }

   private PlanChange resolveOne(String label, IdentityChangeRequest change, Principal user,
                                 String currentOrgId, Set<String> seenKeys,
                                 Map<String, String> hiddenInputs)
      throws Exception
   {
      if(change == null) {
         throw new IllegalArgumentException(label + ": must not be null");
      }

      String verb = requireVerb(label, change.getVerb());
      IdentityUnitType unitType = requireUnitType(label, change.getUnitType());

      if(IdentityChangeRequest.VERB_CREATE.equals(verb)) {
         if(change.getId() != null) {
            throw new IllegalArgumentException(
               label + ".id: not used for verb=create; the id is derived from spec");
         }

         if(change.getSpec() == null) {
            throw new IllegalArgumentException(label + ".spec: required for verb=create");
         }

         return resolveCreate(label, unitType, change.getSpec(), user, currentOrgId, seenKeys,
                              hiddenInputs);
      }

      if(IdentityChangeRequest.VERB_UPDATE.equals(verb)) {
         if(change.getSpec() == null) {
            throw new IllegalArgumentException(label + ".spec: required for verb=update");
         }

         String id = requireNonBlank(label + ".id", change.getId());
         return resolveUpdate(label, unitType, id, change.getSpec(), user, currentOrgId, seenKeys);
      }

      if(change.getSpec() != null) {
         throw new IllegalArgumentException(
            label + ".spec: not used for verb=delete; remove it or use verb=create");
      }

      String id = requireNonBlank(label + ".id", change.getId());
      return resolveDelete(label, unitType, id, user, currentOrgId, seenKeys);
   }

   // ---------------------------------------------------------------- create

   private PlanChange resolveCreate(String label, IdentityUnitType unitType, IdentitySpec spec,
                                    Principal user, String currentOrgId, Set<String> seenKeys,
                                    Map<String, String> hiddenInputs)
      throws Exception
   {
      requireLegalFields(label, unitType, spec);

      if(unitType != IdentityUnitType.ORGANIZATION) {
         normalizeMemberRefs(label, unitType, spec, blankToDefault(spec.getOrgId(), currentOrgId));
      }

      switch(unitType) {
      case USER:
         return resolveCreateUser(label, spec, user, currentOrgId, seenKeys, hiddenInputs);
      case GROUP:
         return resolveCreateGroup(label, spec, user, currentOrgId, seenKeys);
      case ROLE:
         return resolveCreateRole(label, spec, user, currentOrgId, seenKeys);
      default:
         return resolveCreateOrganization(label, spec, user, seenKeys);
      }
   }

   private PlanChange resolveCreateUser(String label, IdentitySpec spec, Principal user,
                                        String currentOrgId, Set<String> seenKeys,
                                        Map<String, String> hiddenInputs)
      throws Exception
   {
      String name = requireNonBlank(label + ".spec.name", spec.getName());
      requireNoReferenceSeparators(label + ".spec.name", name);
      IdentityNameRules.requireUserName(label + ".spec.name", name);
      String orgId = blankToDefault(spec.getOrgId(), currentOrgId);
      IdentityID id = new IdentityID(name, orgId);
      String key = key(IdentityUnitType.USER, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      requireNoBlankPassword(label, spec.getPassword());
      IdentityNameRules.requireAliasOrOrgName(label + ".spec.alias", trimmed(spec.getAlias()));
      requireCreateIdFree(label, () -> securityService.getUser(id, user), key);
      String proposed = IdentityProjection.projectUserSpec(id, spec, newUserRoles(id, spec, user));
      // Bug #79016: the projection carries only pw:set, so two different passwords hashed alike.
      // The digest goes into the hash only (the apply-time read-back is masked and is compared
      // through the projection, never the hash). requireNoBlankPassword has already refused a
      // blank value, so a non-null password is the effective one.
      if(spec.getPassword() != null) {
         PlanHashInputs.addHidden(hiddenInputs, key, "pw",
                                  PlanHashInputs.digest(PASSWORD_DIGEST_DOMAIN, spec.getPassword()));
      }

      return new PlanChange(key, orgId, null, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "create user " + id.getLabel());
   }

   /** The roles the user will really hold once created (the defaults SecurityService seeds,
    * or the explicit roles), so the plan matches the apply-time read-back. Roles and default
    * flags of identities created earlier in the same plan are not visible here (not yet
    * applied); that forward-reference gap is deliberately not solved here. */
   private List<IdentityID> newUserRoles(IdentityID id, IdentitySpec spec, Principal user) {
      List<IdentityID> requested = IdentityMerge.toIdentityIds(spec.getRoles(), id.getOrgID());

      try {
         return securityService.projectNewUserRoles(id, requested, user);
      }
      catch(Exception e) {
         return requested;
      }
   }

   private PlanChange resolveCreateGroup(String label, IdentitySpec spec, Principal user,
                                         String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      String name = requireNonBlank(label + ".spec.name", spec.getName());
      requireNoReferenceSeparators(label + ".spec.name", name);
      IdentityNameRules.requireGroupOrRoleName(label + ".spec.name", name);
      String orgId = blankToDefault(spec.getOrgId(), currentOrgId);
      IdentityID id = new IdentityID(name, orgId);
      String key = key(IdentityUnitType.GROUP, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      requireCreateIdFree(label, () -> securityService.getGroup(id, user), key);
      String proposed = IdentityProjection.projectGroupSpec(id, spec);
      return new PlanChange(key, orgId, null, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "create group " + id.getLabel());
   }

   private PlanChange resolveCreateRole(String label, IdentitySpec spec, Principal user,
                                        String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      String name = requireNonBlank(label + ".spec.name", spec.getName());
      requireNoReferenceSeparators(label + ".spec.name", name);
      IdentityNameRules.requireGroupOrRoleName(label + ".spec.name", name);
      String orgId = blankToDefault(spec.getOrgId(), currentOrgId);
      IdentityID id = new IdentityID(name, orgId);
      String key = key(IdentityUnitType.ROLE, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      requireCreateIdFree(label, () -> securityService.getRole(id, user), key);
      requireRoleAdminFlagsExclusive(label, Boolean.TRUE.equals(spec.getSysAdmin()),
                                     Boolean.TRUE.equals(spec.getOrgAdmin()), false);
      String proposed = IdentityProjection.projectRoleSpec(id, spec);
      return new PlanChange(key, orgId, null, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "create role " + id.getLabel());
   }

   private PlanChange resolveCreateOrganization(String label, IdentitySpec spec, Principal user,
                                                Set<String> seenKeys)
      throws Exception
   {
      String id = requireNonBlank(label + ".spec.id", spec.getId());
      String orgName = requireNonBlank(label + ".spec.orgName", spec.getOrgName());
      IdentityNameRules.requireOrgId(label + ".spec.id", id);
      IdentityNameRules.requireAliasOrOrgName(label + ".spec.orgName", orgName);
      String key = key(IdentityUnitType.ORGANIZATION, id);
      requireUnseen(label, key, seenKeys);
      requireCreateIdFree(label, () -> securityService.getOrganization(id, user), key);
      String proposed = IdentityProjection.projectOrganizationSpec(id, spec);
      return new PlanChange(key, id, null, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "create organization " + id);
   }

   // ---------------------------------------------------------------- delete

   private PlanChange resolveDelete(String label, IdentityUnitType unitType, String rawId,
                                    Principal user, String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      switch(unitType) {
      case USER: {
         IdentityID id = parseIdentityId(label, rawId, currentOrgId, unitType, directory());
         String key = key(unitType, id.convertToKey());
         requireUnseen(label, key, seenKeys);
         SecurityUser existing = requireGet(label, () -> securityService.getUser(id, user), key);
         String before = IdentityProjection.projectUser(existing);
         return new PlanChange(key, id.getOrgID(), before, null, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true, "delete user " + id.getLabel());
      }
      case GROUP: {
         IdentityID id = parseIdentityId(label, rawId, currentOrgId, unitType, directory());
         String key = key(unitType, id.convertToKey());
         requireUnseen(label, key, seenKeys);
         SecurityGroup existing = requireGet(label, () -> securityService.getGroup(id, user), key);
         String before = IdentityProjection.projectGroup(existing, id);
         return new PlanChange(key, id.getOrgID(), before, null, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true, "delete group " + id.getLabel());
      }
      case ROLE: {
         IdentityID id = parseIdentityId(label, rawId, currentOrgId, unitType, directory());
         String key = key(unitType, id.convertToKey());
         requireUnseen(label, key, seenKeys);
         SecurityRole existing = requireGet(label, () -> securityService.getRole(id, user), key);
         String before = IdentityProjection.projectRole(existing, id);
         return new PlanChange(key, id.getOrgID(), before, null, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true, "delete role " + id.getLabel());
      }
      default: {
         String organizationId = requireNonBlank(label + ".id", rawId);
         requireNotProtectedOrganization(label, organizationId);
         String key = key(unitType, organizationId);
         requireUnseen(label, key, seenKeys);
         SecurityOrganization existing =
            requireGet(label, () -> securityService.getOrganization(organizationId, user), key);
         String before = IdentityProjection.projectOrganization(existing, organizationId);
         return new PlanChange(key, organizationId, before, null, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true, "delete organization " + organizationId);
      }
      }
   }

   // ---------------------------------------------------------------- update

   /**
    * Note deliberately NOT here: no default-org/self-org refusal ({@link
    * #requireNotProtectedOrganization}) for organization update -- charter counter-assertion 6:
    * updating the default/self organization's {@code orgName}/{@code locale} is normal admin
    * activity and must be allowed, unlike delete's non-reversible removal.
    */
   private PlanChange resolveUpdate(String label, IdentityUnitType unitType, String rawId,
                                    IdentitySpec spec, Principal user, String currentOrgId,
                                    Set<String> seenKeys)
      throws Exception
   {
      clearSingleBlankLists(spec);
      requireLegalFieldsForUpdate(label, unitType, spec);

      switch(unitType) {
      case USER:
         return resolveUpdateUser(label, rawId, spec, user, currentOrgId, seenKeys);
      case GROUP:
         return resolveUpdateGroup(label, rawId, spec, user, currentOrgId, seenKeys);
      case ROLE:
         return resolveUpdateRole(label, rawId, spec, user, currentOrgId, seenKeys);
      default:
         return resolveUpdateOrganization(label, rawId, spec, user, seenKeys);
      }
   }

   private PlanChange resolveUpdateUser(String label, String rawId, IdentitySpec spec,
                                        Principal user, String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      IdentityID id =
         parseIdentityId(label, rawId, currentOrgId, IdentityUnitType.USER, directory());
      requireNotDeactivatingCaller(label, spec, id, user);
      requireRenameFreeOfSeparators(label, spec, id);
      requireRenamedNameLegal(label, spec, id, IdentityNameRules::requireUserName);
      String key = key(IdentityUnitType.USER, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      normalizeMemberRefs(label, IdentityUnitType.USER, spec, id.getOrgID());
      SecurityUser existing = requireGet(label, () -> securityService.getUser(id, user), key);
      requireChangedValueLegal(label + ".spec.alias", spec.getAlias(), existing.getAlias(),
                               IdentityNameRules::requireAliasOrOrgName);
      String before = IdentityProjection.projectUser(existing);
      SecurityUser merged = IdentityMerge.mergeUser(existing, spec, id);
      String proposed = IdentityProjection.projectUser(merged);
      return new PlanChange(key, id.getOrgID(), before, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "update user " + id.getLabel());
   }

   private PlanChange resolveUpdateGroup(String label, String rawId, IdentitySpec spec,
                                         Principal user, String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      IdentityID id =
         parseIdentityId(label, rawId, currentOrgId, IdentityUnitType.GROUP, directory());
      requireRenameFreeOfSeparators(label, spec, id);
      requireRenamedNameLegal(label, spec, id, IdentityNameRules::requireGroupOrRoleName);
      String key = key(IdentityUnitType.GROUP, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      normalizeMemberRefs(label, IdentityUnitType.GROUP, spec, id.getOrgID());
      SecurityGroup existing = requireGet(label, () -> securityService.getGroup(id, user), key);
      String before = IdentityProjection.projectGroup(existing, id);
      SecurityGroup merged = IdentityMerge.mergeGroup(existing, spec, id);
      String proposed = IdentityProjection.projectGroup(merged, merged.getIdentityID());
      return new PlanChange(key, id.getOrgID(), before, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "update group " + id.getLabel());
   }

   /**
    * Refuses {@code active=false} on the calling user. {@code SecurityService.updateUser} forces
    * the status of the account whose identity key equals the principal name back to active, and
    * the Enterprise Manager disables the checkbox for it, so the change would plan as applicable
    * and then be silently undone. The comparison is that same exact key equality on the parsed
    * (pre-rename) id: another user with the same bare name in another organization is not the
    * caller.
    */
   private static void requireNotDeactivatingCaller(String label, IdentitySpec spec, IdentityID id,
                                                    Principal user)
   {
      if(spec.getActive() != null && !spec.getActive() && user != null &&
         id.convertToKey().equals(user.getName()))
      {
         throw new IllegalArgumentException(
            label + ".spec.active: the calling user (" + id.getLabel() + ") cannot be " +
            "deactivated; the Enterprise Manager forbids it and apply would force the account " +
            "back to active");
      }
   }

   /**
    * Refuses a role whose effective flags become both system administrator and organization
    * administrator. A role that already had both is not a new conflict, so unrelated edits and
    * clearing one flag stay allowed. Flags inherited through role inheritance are not checked.
    */
   private static void requireRoleAdminFlagsExclusive(String label, boolean sysAdmin,
                                                      boolean orgAdmin, boolean alreadyBoth)
   {
      if(sysAdmin && orgAdmin && !alreadyBoth) {
         throw new IllegalArgumentException(
            label + ".spec.sysAdmin and " + label + ".spec.orgAdmin are mutually exclusive: " +
            "a role cannot be both a system administrator and an organization administrator " +
            "(the resulting role, after merging this spec onto the existing role, would be both)");
      }
   }

   private PlanChange resolveUpdateRole(String label, String rawId, IdentitySpec spec,
                                        Principal user, String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      IdentityID id =
         parseIdentityId(label, rawId, currentOrgId, IdentityUnitType.ROLE, directory());
      requireRenameFreeOfSeparators(label, spec, id);
      requireRenamedNameLegal(label, spec, id, IdentityNameRules::requireGroupOrRoleName);
      String key = key(IdentityUnitType.ROLE, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      normalizeMemberRefs(label, IdentityUnitType.ROLE, spec, id.getOrgID());
      SecurityRole existing = requireGet(label, () -> securityService.getRole(id, user), key);
      String before = IdentityProjection.projectRole(existing, id);
      SecurityRole merged = IdentityMerge.mergeRole(existing, spec, id);
      requireRoleAdminFlagsExclusive(
         label, Boolean.TRUE.equals(merged.getSysAdmin()),
         Boolean.TRUE.equals(merged.getOrgAdmin()),
         Boolean.TRUE.equals(existing.getSysAdmin()) && Boolean.TRUE.equals(existing.getOrgAdmin()));
      String proposed = IdentityProjection.projectRole(merged, merged.getIdentityID());
      return new PlanChange(key, id.getOrgID(), before, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "update role " + id.getLabel());
   }

   private PlanChange resolveUpdateOrganization(String label, String rawId, IdentitySpec spec,
                                                Principal user, Set<String> seenKeys)
      throws Exception
   {
      String organizationId = requireNonBlank(label + ".id", rawId);
      String key = key(IdentityUnitType.ORGANIZATION, organizationId);
      requireUnseen(label, key, seenKeys);
      SecurityOrganization existing =
         requireGet(label, () -> securityService.getOrganization(organizationId, user), key);

      if(!isAbsent(spec.getId())) {
         String proposedId = spec.getId().trim();

         // case-SENSITIVE on purpose: IdentityService.setOrganizationInfo gates its migration
         // cascade on !Tool.equals(), so a case-only rename (e.g. "host-org" -> "HOST-ORG") is a
         // real rename there and must be gated here too. The guards themselves stay
         // case-insensitive -- see their own doc comments.
         if(!proposedId.equals(organizationId)) {
            IdentityNameRules.requireOrgId(label + ".spec.id", proposedId);
            requireNotReservedOrganizationIdRename(label, organizationId, proposedId);
            requireNoDuplicateOrganizationId(label, organizationId, proposedId, user);
         }
      }

      requireChangedValueLegal(label + ".spec.orgName", spec.getOrgName(), existing.getName(),
                               IdentityNameRules::requireAliasOrOrgName);
      String before = IdentityProjection.projectOrganization(existing, organizationId);
      SecurityOrganization merged = IdentityMerge.mergeOrganization(existing, spec, organizationId);
      String proposed = IdentityProjection.projectOrganization(merged, merged.getId());
      return new PlanChange(key, organizationId, before, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true,
                            "update organization " + organizationId);
   }

   /**
    * The narrower, update-only counterpart to {@link #requireNotProtectedOrganization} (delete-
    * only) -- refuses an id-changing update only when the id is ACTUALLY changing and either side
    * of that change is the default/self organization's id, mirroring {@code
    * UserTreeService.editOrganization}'s own conditional guard. Both call sites decide "is the id
    * ACTUALLY changing" case-SENSITIVELY, matching the {@code !Tool.equals()} gate on {@code
    * IdentityService.setOrganizationInfo}'s migration cascade; the two must move together, or a
    * case-only rename slips past this refusal while still triggering the full cascade. The
    * comparisons below stay case-INSENSITIVE on purpose. Deliberately not
    * a reuse of {@link #requireNotProtectedOrganization}: that helper fires unconditionally on the
    * default/self org's CURRENT id, which would wrongly refuse a plain orgName/locale-only update
    * of the default/self organization -- behavior this area's own charter (counter-assertion 6,
    * see {@link #resolveUpdate}'s doc comment) requires to keep working.
    */
   private static void requireNotReservedOrganizationIdRename(String label, String currentId,
                                                               String proposedId)
   {
      if(Organization.getDefaultOrganizationID().equalsIgnoreCase(currentId) ||
         Organization.getDefaultOrganizationID().equalsIgnoreCase(proposedId))
      {
         throw new IllegalArgumentException(
            label + ".spec.id: \"" + Organization.getDefaultOrganizationID() + "\" is the " +
            "default organization's id; renaming it away, or renaming another organization to " +
            "it, is refused");
      }

      if(Organization.getSelfOrganizationID().equalsIgnoreCase(currentId) ||
         Organization.getSelfOrganizationID().equalsIgnoreCase(proposedId))
      {
         throw new IllegalArgumentException(
            label + ".spec.id: \"" + Organization.getSelfOrganizationID() + "\" is the self " +
            "organization's id; renaming it away, or renaming another organization to it, is " +
            "refused");
      }
   }

   /**
    * Mirrors {@code UserTreeService.checkDuplicateOrgIDs}'s id branch (lines 1311-1328) -- refuses
    * an id-changing update whose proposed id case-insensitively collides with any OTHER existing
    * organization's id. {@code SecurityService.updateOrganization}'s own call chain has no such
    * check (confirmed by reading it in full); without this, a colliding rename would interleave
    * {@code copyOrganizationInternal}'s writes against whatever organization already lives at the
    * target id.
    */
   private void requireNoDuplicateOrganizationId(String label, String currentId, String proposedId,
                                                 Principal user)
      throws Exception
   {
      for(SecurityOrganization other : securityService.getOrganizations(user).getOrganizations()) {
         if(other.getId() != null && !other.getId().equalsIgnoreCase(currentId) &&
            other.getId().equalsIgnoreCase(proposedId))
         {
            throw new IllegalArgumentException(
               label + ".spec.id: \"" + proposedId + "\" is already the id of another " +
               "organization; organization ids must be unique");
         }
      }
   }

   /**
    * The default-org/self-org preview-time refusal this spec adds (spec section 4a/6), closing the
    * confirmed gap in {@code SecurityService.deleteOrganization} -- that method has no
    * equivalent check, unlike the EM path ({@code RoleController.deleteIdentities}'
    * {@code includesDefaultOrg}/{@code includesSelfOrg}), and there is no way to get it "for free"
    * by tracing deeper, since the EM check lives entirely at the controller level, not inside the
    * shared {@code IdentityService} method both paths otherwise share.
    *
    * <p>Compared case-insensitively, not via exact match: {@code DatabaseAuthenticationProvider}
    * resolves organization ids per the {@code security.user.caseSensitive} property (default
    * {@code true}, but a supported {@code false} configuration for case-insensitive DB collations --
    * see its {@code getOrganization}, which falls back to {@code equalsIgnoreCase}). Under that
    * configuration, {@code SecurityService.deleteOrganization} itself resolves a differently-cased
    * id to the *same* protected organization it would resolve for the canonically-cased id -- so an
    * exact-match guard here would fail to refuse a plan naming e.g. {@code "HOST-ORG"} while
    * {@code deleteOrganization} still deletes the real, canonically-cased default/self organization.
    * Comparing case-insensitively closes that gap regardless of which provider/config is deployed;
    * the only cost is over-refusing the vanishingly rare case of a genuinely distinct organization
    * whose id differs from a protected id only by case under a case-sensitive provider, an acceptable
    * trade given this guard is this area's only backstop against deleting the default/self
    * organization (checkPermission is bypassed for every admin-chat caller, spec section 4a).
    */
   static void requireNotProtectedOrganization(String label, String organizationId) {
      if(organizationId.equalsIgnoreCase(Organization.getDefaultOrganizationID())) {
         throw new IllegalArgumentException(
            label + ".id: \"" + organizationId + "\" is the default organization; deleting it is " +
            "refused (this area closes a gap in SecurityService.deleteOrganization itself, " +
            "which -- unlike the EM console -- has no default-organization protection; see " +
            "01-spec.md section 4a)");
      }

      if(organizationId.equalsIgnoreCase(Organization.getSelfOrganizationID())) {
         throw new IllegalArgumentException(
            label + ".id: \"" + organizationId + "\" is the self organization; deleting it is " +
            "refused (same gap as the default-organization case above)");
      }
   }

   // ---------------------------------------------------------------- field-set validation

   /**
    * The discriminator-confusion defense (spec section 11): asserts every populated field on
    * {@code spec} is legal for {@code unitType}, naming the offending field and, where it belongs
    * to a different unit's shape, suggesting that unit -- the concrete realization of "accept the
    * natural alias, fail loud otherwise" applied to a discriminator mismatch rather than a value
    * alias, closing the exact gap this repo's CLAUDE.md names ({@code fieldConfigs}' {@code
    * fieldType} vs. {@code role} mixup).
    */
   static void requireLegalFields(String label, IdentityUnitType unitType, IdentitySpec spec) {
      checkField(label, "id", spec.getId(), unitType == IdentityUnitType.ORGANIZATION,
                IdentityUnitType.ORGANIZATION);
      checkField(label, "orgName", spec.getOrgName(), unitType == IdentityUnitType.ORGANIZATION,
                IdentityUnitType.ORGANIZATION);
      checkField(label, "name", spec.getName(), unitType != IdentityUnitType.ORGANIZATION, null);
      checkField(label, "orgId", spec.getOrgId(), unitType != IdentityUnitType.ORGANIZATION, null);
      checkField(label, "password", spec.getPassword(), unitType == IdentityUnitType.USER,
                IdentityUnitType.USER);
      checkField(label, "alias", spec.getAlias(), unitType == IdentityUnitType.USER,
                IdentityUnitType.USER);
      checkField(label, "active", spec.getActive(), unitType == IdentityUnitType.USER,
                IdentityUnitType.USER);
      checkField(label, "emails", spec.getEmails(), unitType == IdentityUnitType.USER,
                IdentityUnitType.USER);
      checkField(label, "groups", spec.getGroups(), unitType == IdentityUnitType.USER,
                IdentityUnitType.USER);
      checkField(label, "parentGroups", spec.getParentGroups(), unitType == IdentityUnitType.GROUP,
                IdentityUnitType.GROUP);
      checkField(label, "memberUsers", spec.getMemberUsers(), unitType == IdentityUnitType.GROUP,
                IdentityUnitType.GROUP);
      checkField(label, "memberGroups", spec.getMemberGroups(), unitType == IdentityUnitType.GROUP,
                IdentityUnitType.GROUP);
      checkField(label, "description", spec.getDescription(), unitType == IdentityUnitType.ROLE,
                IdentityUnitType.ROLE);
      checkField(label, "inheritedRoles", spec.getInheritedRoles(), unitType == IdentityUnitType.ROLE,
                IdentityUnitType.ROLE);
      checkField(label, "assignedUsers", spec.getAssignedUsers(), unitType == IdentityUnitType.ROLE,
                IdentityUnitType.ROLE);
      checkField(label, "assignedGroups", spec.getAssignedGroups(), unitType == IdentityUnitType.ROLE,
                IdentityUnitType.ROLE);
      checkField(label, "defaultRole", spec.getDefaultRole(), unitType == IdentityUnitType.ROLE,
                IdentityUnitType.ROLE);
      checkField(label, "sysAdmin", spec.getSysAdmin(), unitType == IdentityUnitType.ROLE,
                IdentityUnitType.ROLE);
      checkField(label, "orgAdmin", spec.getOrgAdmin(), unitType == IdentityUnitType.ROLE,
                IdentityUnitType.ROLE);
      checkField(label, "properties", spec.getProperties(), unitType == IdentityUnitType.ORGANIZATION,
                IdentityUnitType.ORGANIZATION);
      // "roles" is legal on both USER (assign existing roles) and GROUP (assign existing roles to
      // the group) -- spec section 11 -- so it gets its own check rather than the single-unit
      // pattern above.
      checkField(label, "roles", spec.getRoles(),
                unitType == IdentityUnitType.USER || unitType == IdentityUnitType.GROUP, null);
      checkField(label, "locale", spec.getLocale(),
                unitType == IdentityUnitType.USER || unitType == IdentityUnitType.ORGANIZATION, null);
      // "theme" is legal on every unit type -- no check needed.
      requireNoBlankElements(label, spec);
   }

   /** Refuses a null or blank element in any identity-name/email list (Bug #78854): such an
    * element names nothing, and {@code [""]} would project (and hash) the same as {@code []}. */
   private static void requireNoBlankElements(String label, IdentitySpec spec) {
      requireNoBlankElements(label, "emails", spec.getEmails());
      requireNoBlankElements(label, "groups", spec.getGroups());
      requireNoBlankElements(label, "roles", spec.getRoles());
      requireNoBlankElements(label, "parentGroups", spec.getParentGroups());
      requireNoBlankElements(label, "memberUsers", spec.getMemberUsers());
      requireNoBlankElements(label, "memberGroups", spec.getMemberGroups());
      requireNoBlankElements(label, "inheritedRoles", spec.getInheritedRoles());
      requireNoBlankElements(label, "assignedUsers", spec.getAssignedUsers());
      requireNoBlankElements(label, "assignedGroups", spec.getAssignedGroups());
   }

   private static void requireNoBlankElements(String label, String fieldName, List<String> values) {
      if(values == null) {
         return;
      }

      for(int i = 0; i < values.size(); i++) {
         String value = values.get(i);

         if(value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + ".spec." + fieldName + "[" + i +
               "]: must not be blank; remove the empty entry (send [] for an empty list)");
         }
      }
   }

   /** On update, a list that is exactly one blank string is the documented {@code ""} "clear
    * this field" signal (the plugin wraps a bare {@code ""} into {@code [""]}), so it is replaced
    * with {@code []} before validation, projection, hashing and apply (Bug #78854). A blank
    * element mixed with other values is still refused by {@link #requireNoBlankElements}. */
   private static void clearSingleBlankLists(IdentitySpec spec) {
      spec.setEmails(clearIfSingleBlank(spec.getEmails()));
      spec.setGroups(clearIfSingleBlank(spec.getGroups()));
      spec.setRoles(clearIfSingleBlank(spec.getRoles()));
      spec.setParentGroups(clearIfSingleBlank(spec.getParentGroups()));
      spec.setMemberUsers(clearIfSingleBlank(spec.getMemberUsers()));
      spec.setMemberGroups(clearIfSingleBlank(spec.getMemberGroups()));
      spec.setInheritedRoles(clearIfSingleBlank(spec.getInheritedRoles()));
      spec.setAssignedUsers(clearIfSingleBlank(spec.getAssignedUsers()));
      spec.setAssignedGroups(clearIfSingleBlank(spec.getAssignedGroups()));
   }

   private static List<String> clearIfSingleBlank(List<String> values) {
      if(values != null && values.size() == 1 && values.get(0) != null &&
         values.get(0).trim().isEmpty())
      {
         return new ArrayList<>();
      }

      return values;
   }

   private static void checkField(String label, String fieldName, Object value, boolean legal,
                                  IdentityUnitType suggest)
   {
      if(legal || isAbsent(value)) {
         return;
      }

      String suggestion = suggest == null ? "" :
         " (did you mean unitType=\"" + suggest.label() + "\"?)";
      throw new IllegalArgumentException(
         label + ".spec." + fieldName + ": not used for this unitType" + suggestion);
   }

   private static boolean isAbsent(Object value) {
      if(value == null) {
         return true;
      }

      if(value instanceof String s) {
         return s.trim().isEmpty();
      }

      if(value instanceof Collection<?> c) {
         return c.isEmpty();
      }

      return false;
   }

   /**
    * The two verb-specific field refusals {@code update} adds on top of {@link
    * #requireLegalFields}'s unchanged unitType-ownership check (design section 2.3/9), plus the
    * at-least-one-field requirement (item 4). {@code spec.id} on organization update is no longer
    * refused here (bug-76834) -- it is a real, supported rename, validated instead in {@link
    * #resolveUpdateOrganization} (reserved-id / duplicate-id checks, which need a live read of
    * every organization and so cannot run in this static, spec-only method). Additive -- does not
    * modify {@link #requireLegalFields}'s own signature or behavior; {@code create}'s call site is
    * untouched.
    */
   static void requireLegalFieldsForUpdate(String label, IdentityUnitType unitType, IdentitySpec spec) {
      requireLegalFields(label, unitType, spec);

      if(unitType == IdentityUnitType.USER && !isAbsent(spec.getPassword())) {
         throw new IllegalArgumentException(label + ".spec.password: not used for verb=\"update\" " +
            "— password rotation is out of scope for update in this cut (spec.password is " +
            "silently unreachable by SecurityService.updateUser anyway; refusing here prevents " +
            "you from believing it took effect). Remove it; the account's password is left " +
            "completely untouched by every other field you do change.");
      }

      if((unitType == IdentityUnitType.USER || unitType == IdentityUnitType.GROUP ||
         unitType == IdentityUnitType.ROLE) && !isAbsent(spec.getOrgId()))
      {
         throw new IllegalArgumentException(label + ".spec.orgId: not used for verb=\"update\" " +
            "— an identity's organization is fixed by the id you're targeting, not settable " +
            "via spec (this closes an org-smuggling path: the validated org comes from the " +
            "resolved id, never from the request body). Omit it; use \"name:orgId\" in the " +
            "top-level id if you need to disambiguate which organization's identity you're " +
            "targeting.");
      }

      requireAtLeastOneField(label, unitType, spec);
   }

   /** Throws if every field legal for {@code unitType} is absent (key not present / null) from
    * {@code spec} (design section 2.3 item 4) -- an update with nothing to change is a caller
    * mistake, not a valid no-op. Tests presence via {@code != null}, NOT {@link #isAbsent}: an
    * explicit empty string/list is a real, intentional "clear this field" signal for {@code update}
    * (design section 2.4), the same distinction {@link IdentityMerge}'s own per-field
    * present-or-current logic already draws -- {@code isAbsent} exists for a different, unrelated
    * job ({@link #requireLegalFields}'s cross-unitType field-ownership check on {@code create},
    * where an empty value is a harmless no-op) and must not be reused here. */
   private static void requireAtLeastOneField(String label, IdentityUnitType unitType, IdentitySpec spec) {
      boolean anyPresent;

      switch(unitType) {
      case USER:
         anyPresent = spec.getName() != null || spec.getAlias() != null ||
            spec.getActive() != null || spec.getEmails() != null ||
            spec.getGroups() != null || spec.getRoles() != null ||
            spec.getLocale() != null || spec.getTheme() != null;
         break;
      case GROUP:
         anyPresent = spec.getName() != null || spec.getParentGroups() != null ||
            spec.getMemberUsers() != null || spec.getMemberGroups() != null ||
            spec.getRoles() != null || spec.getTheme() != null;
         break;
      case ROLE:
         anyPresent = spec.getName() != null || spec.getDescription() != null ||
            spec.getInheritedRoles() != null || spec.getAssignedUsers() != null ||
            spec.getAssignedGroups() != null || spec.getTheme() != null ||
            spec.getDefaultRole() != null || spec.getSysAdmin() != null ||
            spec.getOrgAdmin() != null;
         break;
      default:
         anyPresent = spec.getId() != null || spec.getOrgName() != null || spec.getLocale() != null ||
            spec.getTheme() != null || spec.getProperties() != null;
         break;
      }

      if(!anyPresent) {
         throw new IllegalArgumentException(label + ".spec: at least one field is required for " +
            "verb=\"update\" -- an update with nothing to change is refused, not treated as a no-op");
      }
   }


   // ---------------------------------------------------------------- member resolvability

   /** Identities the plan creates/deletes so far, and every create the plan lists (Bug #79562). */
   private static final class MemberState {
      MemberState(Set<String> allCreates) {
         this.allCreates = allCreates;
      }

      final Set<String> allCreates;
      final Set<String> createdSoFar = new HashSet<>();
      final Set<String> deletedSoFar = new HashSet<>();
   }

   private Set<String> plannedCreates(IdentityChangePlanRequest req, String currentOrgId) {
      Set<String> result = new HashSet<>();

      for(IdentityChangeRequest change : req.getChanges()) {
         if(change == null || change.getSpec() == null ||
            !IdentityChangeRequest.VERB_CREATE.equals(change.getVerb()))
         {
            continue;
         }

         try {
            IdentityUnitType type = requireUnitType("changes", change.getUnitType());
            String name = change.getSpec().getName();

            if(type != IdentityUnitType.ORGANIZATION && name != null && !name.trim().isEmpty()) {
               String org = blankToDefault(change.getSpec().getOrgId(), currentOrgId);
               result.add(key(type, new IdentityID(name.trim(), org).convertToKey()));
            }
         }
         catch(RuntimeException e) {
            // reported by resolveOne with the right label
         }
      }

      return result;
   }

   /**
    * Bug #79562: apply hands membership lists to SecurityService, which silently drops every
    * reference it cannot honour (nonexistent, not administrable by the caller, a system
    * administrator role for a non-site-admin, a circular group). Refuse those at preview, naming
    * the field, so the plan never shows a membership the apply cannot grant. Apply runs the
    * changes in request order, so a reference to an identity created earlier in the plan is
    * accepted and one created later is refused. On update only NEW references are checked for
    * administrability, as apply keeps stored members the caller cannot administer. Roles are not
    * ADMIN-checked by apply and are not here.
    */
   private void requireMembersResolvable(String label, IdentityChangeRequest request,
                                         PlanChange resolved, Principal user, MemberState state)
   {
      IdentityUnitType unitType = requireUnitType(label, request.getUnitType());

      if(unitType == IdentityUnitType.ORGANIZATION) {
         return;
      }

      String property = resolved.property();
      String verb = request.getVerb();
      IdentityID self = IdentityID.getIdentityIDFromKey(property.substring(property.indexOf('|') + 1));

      if(IdentityChangeRequest.VERB_DELETE.equals(verb)) {
         state.deletedSoFar.add(property);
         return;
      }

      boolean create = IdentityChangeRequest.VERB_CREATE.equals(verb);
      IdentitySpec spec = request.getSpec();
      Object existing = create ? null : loadExisting(unitType, self, user);

      if(create && unitType != IdentityUnitType.ROLE && spec.getRoles() != null &&
         !spec.getRoles().isEmpty() &&
         !securityEngine.getSecurityProvider().checkPermission(
            user, ResourceType.SECURITY_ROLE, SecurityService.getIdentityRootResorucePath(),
            ResourceAction.ADMIN))
      {
         throw new IllegalArgumentException(
            label + ".spec.roles: the calling user lacks the root role-administration permission, " +
            "so apply ignores the whole roles list when creating a " + unitType.name().toLowerCase() +
            " (only the default roles are assigned); drop spec.roles or have a role administrator " +
            "make this change");
      }

      switch(unitType) {
      case USER:
         checkRefs(label, "groups", spec.getGroups(), AdminIdentityRefs.Kind.GROUP, self,
                   existing == null ? null : ((SecurityUser) existing).getGroups(), user, state);
         checkRefs(label, "roles", spec.getRoles(), AdminIdentityRefs.Kind.ROLE, self, null,
                   user, state);
         break;
      case GROUP:
         SecurityGroup g = (SecurityGroup) existing;
         checkRefs(label, "parentGroups", spec.getParentGroups(), AdminIdentityRefs.Kind.GROUP,
                   self, g == null ? null : g.getParentGroups(), user, state);
         checkRefs(label, "memberUsers", spec.getMemberUsers(), AdminIdentityRefs.Kind.USER,
                   self, g == null ? null : g.getMemberUsers(), user, state);
         // Apply resolves create-group memberGroups in the CALLER's organization, not the
         // group's own (SecurityService.filterMemberGroups); mirror exactly that lookup.
         checkRefs(label, "memberGroups", spec.getMemberGroups(), AdminIdentityRefs.Kind.GROUP,
                   create ? callerOrgOr(user, self) : self,
                   g == null ? null : g.getMemberGroups(), user, state);
         checkRefs(label, "roles", spec.getRoles(), AdminIdentityRefs.Kind.ROLE, self, null,
                   user, state);
         checkCircular(label, spec, self, g, create ? callerOrgOr(user, self) : self);
         break;
      default:
         SecurityRole r = (SecurityRole) existing;
         checkRefs(label, "inheritedRoles", spec.getInheritedRoles(), AdminIdentityRefs.Kind.ROLE,
                   self, null, user, state);
         checkRefs(label, "assignedUsers", spec.getAssignedUsers(), AdminIdentityRefs.Kind.USER,
                   self, r == null ? null : r.getAssignedUsers(), user, state);
         checkRefs(label, "assignedGroups", spec.getAssignedGroups(), AdminIdentityRefs.Kind.GROUP,
                   self, r == null ? null : r.getAssignedGroups(), user, state);
         break;
      }

      if(create) {
         state.createdSoFar.add(property);
      }
   }

   /** {@code self} re-homed to the calling principal's organization, or {@code self}. */
   private static IdentityID callerOrgOr(Principal user, IdentityID self) {
      try {
         String name = user == null ? null : user.getName();
         IdentityID caller = name == null ? null : IdentityID.getIdentityIDFromKey(name);
         return caller == null || caller.getOrgID() == null ? self :
            new IdentityID(self.getName(), caller.getOrgID());
      }
      catch(RuntimeException e) {
         return self;
      }
   }

   private Object loadExisting(IdentityUnitType unitType, IdentityID id, Principal user) {
      try {
         switch(unitType) {
         case USER:
            return securityService.getUser(id, user);
         case GROUP:
            return securityService.getGroup(id, user);
         default:
            return securityService.getRole(id, user);
         }
      }
      catch(Exception e) {
         return null;
      }
   }

   private void checkRefs(String label, String fieldName, List<String> values,
                          AdminIdentityRefs.Kind kind, IdentityID self, List<String> stored,
                          Principal user, MemberState state)
   {
      if(values == null) {
         return;
      }

      IdentityUnitType type = IdentityUnitType.valueOf(kind.name());
      SecurityProvider provider = securityEngine.getSecurityProvider();

      for(int i = 0; i < values.size(); i++) {
         String value = values.get(i);

         if(value == null || value.trim().isEmpty()) {
            continue;
         }

         String field = label + ".spec." + fieldName + "[" + i + "]";
         IdentityID ref = new IdentityID(value.trim(), self.getOrgID());
         String refKey = key(type, ref.convertToKey());
         String what = "\"" + value.trim() + "\"";

         if(state.deletedSoFar.contains(refKey)) {
            throw new IllegalArgumentException(
               field + ": " + what + " is deleted earlier in this plan, so apply cannot add it to " +
               fieldName + "; reference an identity that will exist");
         }

         if(state.createdSoFar.contains(refKey)) {
            continue;
         }

         // inheritedRoles is applied with an organization-scoped lookup (no global-role fallback).
         boolean exists = "inheritedRoles".equals(fieldName) ?
            provider.getAuthenticationProvider().getRole(ref) != null :
            directory().exists(kind, ref);

         if(!exists) {
            if(state.allCreates.contains(refKey)) {
               throw new IllegalArgumentException(
                  field + ": " + what + " is created later in this plan, so it does not exist yet " +
                  "when this change is applied (changes apply in the order listed) -- list the " +
                  "create of " + what + " before this change");
            }

            throw new IllegalArgumentException(
               field + ": " + what + " does not exist in organization \"" + self.getOrgID() +
               "\"; apply would silently drop it from " + fieldName + " -- reference an existing " +
               kind.name().toLowerCase() + " or create it earlier in this plan");
         }

         if(kind == AdminIdentityRefs.Kind.ROLE) {
            if(!OrganizationManager.getInstance().isSiteAdmin(user) &&
               SecurityService.isSystemAdminRoleAssignment(provider.getAuthenticationProvider(), ref))
            {
               throw new IllegalArgumentException(
                  field + ": " + what + " is (or inherits) a system administrator role, which only a site " +
                  "administrator may assign; apply would silently drop it from " + fieldName);
            }

            continue;
         }

         if(stored != null && stored.contains(ref.getName())) {
            continue;
         }

         ResourceType resourceType = kind == AdminIdentityRefs.Kind.USER ?
            ResourceType.SECURITY_USER : ResourceType.SECURITY_GROUP;

         if(!provider.checkPermission(user, resourceType, ref.convertToKey(), ResourceAction.ADMIN)) {
            throw new IllegalArgumentException(
               field + ": " + what + " is not administrable by the calling user; apply would " +
               "silently drop it from " + fieldName);
         }
      }
   }

   /** Mirrors the circular-membership drops of SecurityService.filterMemberGroups and
    * updateParentGroups. */
   private void checkCircular(String label, IdentitySpec spec, IdentityID self, SecurityGroup g,
                              IdentityID memberLookup)
   {
      AuthenticationProvider auth = securityEngine.getSecurityProvider().getAuthenticationProvider();
      List<String> parents = spec.getParentGroups() != null ? spec.getParentGroups() :
         g == null ? null : g.getParentGroups();

      if(spec.getParentGroups() != null) {
         for(int i = 0; i < spec.getParentGroups().size(); i++) {
            String parent = spec.getParentGroups().get(i);

            if(parent != null && reaches(auth, self, parent.trim(), new HashSet<>())) {
               throw new IllegalArgumentException(
                  label + ".spec.parentGroups[" + i + "]: \"" + parent.trim() + "\" would make " +
                  "the group a member of itself (circular); apply would silently drop it");
            }
         }
      }

      if(spec.getMemberGroups() != null && parents != null) {
         for(int i = 0; i < spec.getMemberGroups().size(); i++) {
            String member = spec.getMemberGroups().get(i);

            if(member == null) {
               continue;
            }

            IdentityID memberId = new IdentityID(member.trim(), memberLookup.getOrgID());

            for(String parent : parents) {
               if(parent != null && reaches(auth, memberId, parent, new HashSet<>())) {
                  throw new IllegalArgumentException(
                     label + ".spec.memberGroups[" + i + "]: \"" + member.trim() + "\" is " +
                     "already an ancestor of \"" + parent + "\" (circular); apply would " +
                     "silently drop it");
               }
            }
         }
      }
   }

   /** True when {@code target} is {@code start} itself or one of its ancestors. */
   private static boolean reaches(AuthenticationProvider auth, IdentityID target, String start,
                                  Set<String> visited)
   {
      if(start.equals(target.getName())) {
         return true;
      }

      if(!visited.add(start)) {
         return false;
      }

      Group group = auth.getGroup(new IdentityID(start, target.getOrgID()));

      if(group == null || group.getGroups() == null) {
         return false;
      }

      for(String parent : group.getGroups()) {
         if(reaches(auth, target, parent, visited)) {
            return true;
         }
      }

      return false;
   }

   // ---------------------------------------------------------------- member/list references

   /**
    * Bug #79016: resolves the org-qualified forms a membership/assignment list entry can take
    * ({@code name:org}, the storage key {@code name~;~org}, the caret label {@code name^org}) with
    * the same {@link AdminIdentityRefs#parse} that {@code id}/{@code owner} use, and replaces the
    * entry with the bare name. These lists are plain name strings, so the identity they name can
    * only live in the unit's own organization: a reference that resolves to another org is
    * refused, naming the field, instead of being taken literally (a literal
    * {@code admin^host-org} member is never added). Entries without a separator are left exactly
    * as given, with no directory lookup. Mutates {@code spec} in place, like {@link
    * #clearSingleBlankLists}, so the projection, the hash and apply (which resolves first) all see
    * the resolved names.
    *
    * <p>The eight fields it covers, each parsed as its own kind: user {@code groups}/{@code roles};
    * group {@code parentGroups}/{@code memberUsers}/{@code memberGroups}/{@code roles}; role
    * {@code inheritedRoles}/{@code assignedUsers}/{@code assignedGroups}.
    */
   void normalizeMemberRefs(String label, IdentityUnitType unitType, IdentitySpec spec,
                            String unitOrgId)
   {
      switch(unitType) {
      case USER:
         spec.setGroups(normalizeRefs(label, "groups", spec.getGroups(),
                                      AdminIdentityRefs.Kind.GROUP, unitOrgId));
         spec.setRoles(normalizeRefs(label, "roles", spec.getRoles(),
                                     AdminIdentityRefs.Kind.ROLE, unitOrgId));
         break;
      case GROUP:
         spec.setParentGroups(normalizeRefs(label, "parentGroups", spec.getParentGroups(),
                                            AdminIdentityRefs.Kind.GROUP, unitOrgId));
         spec.setMemberUsers(normalizeRefs(label, "memberUsers", spec.getMemberUsers(),
                                           AdminIdentityRefs.Kind.USER, unitOrgId));
         spec.setMemberGroups(normalizeRefs(label, "memberGroups", spec.getMemberGroups(),
                                            AdminIdentityRefs.Kind.GROUP, unitOrgId));
         spec.setRoles(normalizeRefs(label, "roles", spec.getRoles(),
                                     AdminIdentityRefs.Kind.ROLE, unitOrgId));
         break;
      case ROLE:
         spec.setInheritedRoles(normalizeRefs(label, "inheritedRoles", spec.getInheritedRoles(),
                                              AdminIdentityRefs.Kind.ROLE, unitOrgId));
         spec.setAssignedUsers(normalizeRefs(label, "assignedUsers", spec.getAssignedUsers(),
                                             AdminIdentityRefs.Kind.USER, unitOrgId));
         spec.setAssignedGroups(normalizeRefs(label, "assignedGroups", spec.getAssignedGroups(),
                                              AdminIdentityRefs.Kind.GROUP, unitOrgId));
         break;
      default:
         break;
      }
   }

   private List<String> normalizeRefs(String label, String fieldName, List<String> values,
                                      AdminIdentityRefs.Kind kind, String unitOrgId)
   {
      if(values == null) {
         return null;
      }

      List<String> result = new ArrayList<>(values.size());

      for(int i = 0; i < values.size(); i++) {
         String value = values.get(i);

         if(value == null || !hasReferenceSeparator(value)) {
            result.add(value);
            continue;
         }

         String field = label + ".spec." + fieldName + "[" + i + "]";
         IdentityID resolved =
            AdminIdentityRefs.parse(field, value, unitOrgId, kind, true, false, directory());

         if(!Objects.equals(resolved.getOrgID(), unitOrgId)) {
            throw new IllegalArgumentException(
               field + ": \"" + value.trim() + "\" names organization \"" + resolved.getOrgID() +
               "\", but this identity lives in organization \"" + unitOrgId + "\" and its " +
               fieldName + " list can only reference identities of that organization -- drop the " +
               "organization qualifier (set spec.orgId on create to pick the organization the " +
               "identity is created in)");
         }

         result.add(resolved.getName());
      }

      return result;
   }

   private static boolean hasReferenceSeparator(String value) {
      return value.indexOf(':') >= 0 || value.indexOf('^') >= 0 ||
         value.contains(IdentityID.KEY_DELIMITER);
   }
   // ---------------------------------------------------------------- shared helpers

   @FunctionalInterface
   private interface Getter<T> {
      T get() throws Exception;
   }

   /** Confirms a create's derived id is free -- {@code get*} throwing {@link
    * MissingResourceException} means it is; returning successfully means it already exists. */
   private static <T> void requireCreateIdFree(String label, Getter<T> getter, String key)
      throws Exception
   {
      try {
         getter.get();
      }
      catch(MissingResourceException e) {
         return;
      }

      throw new IllegalArgumentException(label + ": " + key + " already exists; use a different " +
         "name/id, delete it first if you intend to replace it, or use verb=update if you intend " +
         "to change its existing fields instead of replacing it");
   }

   private static <T> T requireGet(String label, Getter<T> getter, String key) throws Exception {
      try {
         return getter.get();
      }
      catch(MissingResourceException e) {
         throw new IllegalArgumentException(label + ": " + key + " not found or not permitted");
      }
   }

   static String requireVerb(String label, String verb) {
      if(IdentityChangeRequest.VERB_CREATE.equals(verb) ||
         IdentityChangeRequest.VERB_DELETE.equals(verb) ||
         IdentityChangeRequest.VERB_UPDATE.equals(verb))
      {
         return verb;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"" + IdentityChangeRequest.VERB_CREATE + "\", \"" +
         IdentityChangeRequest.VERB_UPDATE + "\", or \"" + IdentityChangeRequest.VERB_DELETE +
         "\", got " + String.valueOf(verb));
   }

   static IdentityUnitType requireUnitType(String label, String unitType) {
      if(unitType != null) {
         for(IdentityUnitType t : IdentityUnitType.values()) {
            if(t.label().equalsIgnoreCase(unitType.trim())) {
               return t;
            }
         }
      }

      throw new IllegalArgumentException(
         label + ".unitType: must be \"user\", \"group\", \"role\", or \"organization\", got " +
         String.valueOf(unitType));
   }

   /**
    * Parses an identity reference through {@link AdminIdentityRefs#parse}: a bare name, {@code
    * name:orgId}, the storage key {@code name~;~orgId}, or the caret label {@code name^orgId}
    * (Bug #78857). Not {@code IdentityID.getIdentityIDFromKey} for a bare name, because that
    * defaults the org from {@code ThreadContext.getPrincipal()}. Another org is allowed when it
    * exists. A global ({@code __GLOBAL__}) identity is refused: a global role's delete cannot be
    * rolled back ({@code SecurityService.createRole} refuses a null org).
    */
   static IdentityID parseIdentityId(String label, String rawId, String currentOrgId,
                                     IdentityUnitType unitType, AdminIdentityRefs.Directory dir)
   {
      requireNonBlank(label + ".id", rawId);
      return AdminIdentityRefs.parse(label + ".id", rawId, currentOrgId,
                                     AdminIdentityRefs.Kind.valueOf(unitType.name()), true, false,
                                     dir);
   }

   /** The provider lookups {@link #parseIdentityId} uses. */
   AdminIdentityRefs.Directory directory() {
      return AdminIdentityRefs.of(securityEngine::getSecurityProvider);
   }

   /**
    * Bug #78857: refuses a user/group/role name holding an identity-reference separator. Such a
    * name could not be addressed again through {@link #parseIdentityId} (the EM validators forbid
    * them too, but only on the client).
    */
   static void requireNoReferenceSeparators(String field, String name) {
      if(name.contains(":") || name.contains("^") || name.contains(IdentityID.KEY_DELIMITER)) {
         throw new IllegalArgumentException(
            field + ": must not contain \":\", \"^\" or \"" + IdentityID.KEY_DELIMITER +
            "\" -- they are identity-reference separators");
      }
   }

   /** A rename (a {@code spec.name} other than the current name) gets the create-time check. */
   private static void requireRenameFreeOfSeparators(String label, IdentitySpec spec,
                                                     IdentityID id)
   {
      String name = spec == null || spec.getName() == null ? null : spec.getName().trim();

      if(name != null && !name.equals(id.getName())) {
         requireNoReferenceSeparators(label + ".spec.name", name);
      }
   }

   /** A rename gets the EM character check; an unchanged name (possibly external) does not. */
   private static void requireRenamedNameLegal(String label, IdentitySpec spec, IdentityID id,
                                               java.util.function.BiConsumer<String, String> check)
   {
      requireChangedValueLegal(label + ".spec.name", spec == null ? null : spec.getName(),
                               id.getName(), check);
   }

   /** Applies {@code check} only when the trimmed new value differs from the existing one. */
   private static void requireChangedValueLegal(String field, String proposed, String existing,
                                                java.util.function.BiConsumer<String, String> check)
   {
      String value = trimmed(proposed);

      if(value != null && !value.equals(existing)) {
         check.accept(field, value);
      }
   }

   private static String trimmed(String value) {
      return value == null ? null : value.trim();
   }

   static String requireNonBlank(String field, String value) {
      if(value == null || value.trim().isEmpty()) {
         throw new IllegalArgumentException(field + ": required");
      }

      return value.trim();
   }

   private static void requireNoBlankPassword(String label, String password) {
      if(password == null || password.trim().isEmpty()) {
         throw new IllegalArgumentException(label + ".spec.password: required for a new user");
      }
   }

   private static void requireUnseen(String label, String key, Set<String> seenKeys) {
      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for " + key + "; list each identity at most once");
      }
   }

   private static String blankToDefault(String value, String fallback) {
      return value == null || value.trim().isEmpty() ? fallback : value.trim();
   }

   static String key(IdentityUnitType unitType, String id) {
      return unitType.name() + "|" + id;
   }

   /** SHA-256 over the canonical plan. Same field-order/control-character contract as
    * {@code AdminChangePlanService#hash}/{@code ScheduleChangePlanService#hash}/
    * {@code PermissionChangePlanService#hash}.
    *
    * <p>Deliberately excludes {@code task}: it is a free-text, audit-only label (see {@link
    * IdentityChangesetApplyService}'s {@code writeAudit} calls, its only use post-resolve) with
    * no bearing on what is actually mutated or verified, and the caller is never required to
    * replay it byte-for-byte between preview and apply. */
   private static String hash(List<PlanChange> changes, Map<String, String> hiddenInputs) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonical(change.currentValue())).append(SEP)
            .append(canonical(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
         // Bug #79016: appended only for a change that supplied a password.
         PlanHashInputs.appendHidden(canonical, SEP, hiddenInputs, change.property());
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
         throw new IllegalStateException("SHA-256 is required to hash an identity change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String PASSWORD_DIGEST_DOMAIN =
      "inetsoft.admin.ai.identity.password\u0000";
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final SecurityService securityService;
   private final SecurityEngine securityEngine;
}
