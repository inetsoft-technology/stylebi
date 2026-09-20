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
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
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
   public IdentityChangePlanService(SecurityService securityService) {
      this.securityService = securityService;
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

      String currentOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      int index = 0;

      for(IdentityChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";
         changes.add(resolveOne(label, change, user, currentOrgId, seenKeys));
      }

      // Every verb is risk:high, snapshotScope:storage, unconditionally (spec section 4/7).
      String planHash = hash(changes);
      return new ResolvedPlan(req.getTask().trim(), Collections.unmodifiableList(changes),
                              true, true, planHash, TaskAuditToken.issue(planHash, req.getTask().trim()));
   }

   private PlanChange resolveOne(String label, IdentityChangeRequest change, Principal user,
                                 String currentOrgId, Set<String> seenKeys)
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

         return resolveCreate(label, unitType, change.getSpec(), user, currentOrgId, seenKeys);
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
                                    Principal user, String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      requireLegalFields(label, unitType, spec);

      switch(unitType) {
      case USER:
         return resolveCreateUser(label, spec, user, currentOrgId, seenKeys);
      case GROUP:
         return resolveCreateGroup(label, spec, user, currentOrgId, seenKeys);
      case ROLE:
         return resolveCreateRole(label, spec, user, currentOrgId, seenKeys);
      default:
         return resolveCreateOrganization(label, spec, user, seenKeys);
      }
   }

   private PlanChange resolveCreateUser(String label, IdentitySpec spec, Principal user,
                                        String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      String name = requireNonBlank(label + ".spec.name", spec.getName());
      String orgId = blankToDefault(spec.getOrgId(), currentOrgId);
      IdentityID id = new IdentityID(name, orgId);
      String key = key(IdentityUnitType.USER, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      requireNoBlankPassword(label, spec.getPassword());
      requireCreateIdFree(label, () -> securityService.getUser(id, user), key);
      String proposed = IdentityProjection.projectUserSpec(id, spec);
      return new PlanChange(key, orgId, null, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "create user " + id.convertToKey());
   }

   private PlanChange resolveCreateGroup(String label, IdentitySpec spec, Principal user,
                                         String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      String name = requireNonBlank(label + ".spec.name", spec.getName());
      String orgId = blankToDefault(spec.getOrgId(), currentOrgId);
      IdentityID id = new IdentityID(name, orgId);
      String key = key(IdentityUnitType.GROUP, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      requireCreateIdFree(label, () -> securityService.getGroup(id, user), key);
      String proposed = IdentityProjection.projectGroupSpec(id, spec);
      return new PlanChange(key, orgId, null, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "create group " + id.convertToKey());
   }

   private PlanChange resolveCreateRole(String label, IdentitySpec spec, Principal user,
                                        String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      String name = requireNonBlank(label + ".spec.name", spec.getName());
      String orgId = blankToDefault(spec.getOrgId(), currentOrgId);
      IdentityID id = new IdentityID(name, orgId);
      String key = key(IdentityUnitType.ROLE, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      requireCreateIdFree(label, () -> securityService.getRole(id, user), key);
      String proposed = IdentityProjection.projectRoleSpec(id, spec);
      return new PlanChange(key, orgId, null, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "create role " + id.convertToKey());
   }

   private PlanChange resolveCreateOrganization(String label, IdentitySpec spec, Principal user,
                                                Set<String> seenKeys)
      throws Exception
   {
      String id = requireNonBlank(label + ".spec.id", spec.getId());
      requireNonBlank(label + ".spec.orgName", spec.getOrgName());
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
         IdentityID id = parseIdentityId(label, rawId, currentOrgId);
         String key = key(unitType, id.convertToKey());
         requireUnseen(label, key, seenKeys);
         SecurityUser existing = requireGet(label, () -> securityService.getUser(id, user), key);
         String before = IdentityProjection.projectUser(existing);
         return new PlanChange(key, id.getOrgID(), before, null, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true, "delete user " + id.convertToKey());
      }
      case GROUP: {
         IdentityID id = parseIdentityId(label, rawId, currentOrgId);
         String key = key(unitType, id.convertToKey());
         requireUnseen(label, key, seenKeys);
         SecurityGroup existing = requireGet(label, () -> securityService.getGroup(id, user), key);
         String before = IdentityProjection.projectGroup(existing, id);
         return new PlanChange(key, id.getOrgID(), before, null, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true, "delete group " + id.convertToKey());
      }
      case ROLE: {
         IdentityID id = parseIdentityId(label, rawId, currentOrgId);
         String key = key(unitType, id.convertToKey());
         requireUnseen(label, key, seenKeys);
         SecurityRole existing = requireGet(label, () -> securityService.getRole(id, user), key);
         String before = IdentityProjection.projectRole(existing, id);
         return new PlanChange(key, id.getOrgID(), before, null, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true, "delete role " + id.convertToKey());
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
      IdentityID id = parseIdentityId(label, rawId, currentOrgId);
      String key = key(IdentityUnitType.USER, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      SecurityUser existing = requireGet(label, () -> securityService.getUser(id, user), key);
      String before = IdentityProjection.projectUser(existing);
      SecurityUser merged = IdentityMerge.mergeUser(existing, spec, id);
      String proposed = IdentityProjection.projectUser(merged);
      return new PlanChange(key, id.getOrgID(), before, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "update user " + id.convertToKey());
   }

   private PlanChange resolveUpdateGroup(String label, String rawId, IdentitySpec spec,
                                         Principal user, String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      IdentityID id = parseIdentityId(label, rawId, currentOrgId);
      String key = key(IdentityUnitType.GROUP, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      SecurityGroup existing = requireGet(label, () -> securityService.getGroup(id, user), key);
      String before = IdentityProjection.projectGroup(existing, id);
      SecurityGroup merged = IdentityMerge.mergeGroup(existing, spec, id);
      String proposed = IdentityProjection.projectGroup(merged, merged.getIdentityID());
      return new PlanChange(key, id.getOrgID(), before, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "update group " + id.convertToKey());
   }

   private PlanChange resolveUpdateRole(String label, String rawId, IdentitySpec spec,
                                        Principal user, String currentOrgId, Set<String> seenKeys)
      throws Exception
   {
      IdentityID id = parseIdentityId(label, rawId, currentOrgId);
      String key = key(IdentityUnitType.ROLE, id.convertToKey());
      requireUnseen(label, key, seenKeys);
      SecurityRole existing = requireGet(label, () -> securityService.getRole(id, user), key);
      String before = IdentityProjection.projectRole(existing, id);
      SecurityRole merged = IdentityMerge.mergeRole(existing, spec, id);
      String proposed = IdentityProjection.projectRole(merged, merged.getIdentityID());
      return new PlanChange(key, id.getOrgID(), before, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true, "update role " + id.convertToKey());
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

         if(!proposedId.equalsIgnoreCase(organizationId)) {
            requireNotReservedOrganizationIdRename(label, organizationId, proposedId);
            requireNoDuplicateOrganizationId(label, organizationId, proposedId, user);
         }
      }

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
    * UserTreeService.editOrganization}'s own conditional guard (lines 1257-1261). Deliberately not
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
    * Parses a bare identity name or a {@code name:orgId}-shaped key (the human/tool-facing wire
    * format -- a plain colon, not {@code IdentityID.convertToKey()}'s internal delimiter, matching
    * C.2's {@code requireIdentityId} precedent and its reasoning for not using {@code
    * IdentityID.getIdentityIDFromKey} directly).
    */
   static IdentityID parseIdentityId(String label, String rawId, String currentOrgId) {
      String trimmed = requireNonBlank(label + ".id", rawId);
      int delim = trimmed.indexOf(':');

      if(delim < 0) {
         return new IdentityID(trimmed, currentOrgId);
      }

      return new IdentityID(trimmed.substring(0, delim), trimmed.substring(delim + 1));
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
         throw new IllegalStateException("SHA-256 is required to hash an identity change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final SecurityService securityService;
}
