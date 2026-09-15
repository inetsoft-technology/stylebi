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

import inetsoft.report.internal.Util;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;

/**
 * Resolves a requested list of provider changes into a {@link ResolvedPlan} and hashes it -- the
 * providers analog of {@code inetsoft.web.admin.ai.AdminChangePlanService} and (within this run)
 * {@code ScheduleChangePlanService}/{@code PermissionChangePlanService}/
 * {@code IdentityChangePlanService}, replicated rather than shared (01-spec.md section 6,
 * carry-forward item 5 -- now a fourth structural-area data point).
 *
 * <p>Wraps {@link AuthenticationProviderService}/{@link AuthorizationProviderService} directly --
 * there is no Public API (*ApiService) layer for providers (01-spec.md section 0). Never calls
 * either service's own {@code getProviderByName} (01-spec.md section 2/03-reconcile.md Addition 1:
 * the two services resolve a name differently -- the authentication side also matches a
 * catalog-translated display string, the authorization side does not); this service's own name
 * resolution is raw-name-only, against {@link AuthenticationProviderService#getProviderListModel()}/
 * {@link AuthorizationProviderService#getProviderListModel()}, for both chains uniformly.
 */
@Component
public class ProviderChangePlanService {
   @Autowired
   public ProviderChangePlanService(AuthenticationProviderService authenticationProviderService,
                                    AuthorizationProviderService authorizationProviderService,
                                    SecurityEngine securityEngine)
   {
      this.authenticationProviderService = authenticationProviderService;
      this.authorizationProviderService = authorizationProviderService;
      this.securityEngine = securityEngine;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but does perform live reads (the current
    * provider list for every chain touched, plus one {@code get*} per delete, to capture
    * {@code currentValue}).
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *                                 list, an unrecognized verb/chain/providerType, a field illegal
    *                                 for the resolved verb, a create whose name already exists, a
    *                                 delete whose name does not exist or targets a DATABASE/CUSTOM
    *                                 provider, an LDAP create refused by the multi-tenant gate
    *                                 (03-reconcile.md Addition 2), or a delete refused by either
    *                                 self-lockout preflight (01-spec.md section 4).
    */
   public ResolvedPlan resolve(ProviderChangePlanRequest req, Principal user) throws Exception {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<PlanChange> changes = new ArrayList<>();
      Set<String> seenKeys = new HashSet<>();
      Map<ProviderChain, String> chainProjections = new EnumMap<>(ProviderChain.class);
      // Bug 76655 (F7): per-chain set of duplicate target names already claimed earlier in THIS
      // request -- resolveDuplicateName's own collision check only sees the live, unmutated chain,
      // which can't catch two duplicate entries proposing the same newName within one preview.
      Map<ProviderChain, Set<String>> reservedDuplicateNames = new EnumMap<>(ProviderChain.class);
      int index = 0;

      for(ProviderChangeRequest change : req.getChanges()) {
         String label = "changes[" + index++ + "]";

         if(change == null) {
            throw new IllegalArgumentException(label + ": must not be null");
         }

         String verb = requireVerb(label, change.getVerb());
         ProviderChain chain = requireChain(label, change.getChain());
         String name = requireNonBlank(label + ".name", change.getName());
         chainProjections.computeIfAbsent(chain, this::currentChainProjection);
         List<SecurityProviderStatus> currentList = currentProviderList(chain);

         if(ProviderChangeRequest.VERB_CREATE.equals(verb)) {
            String providerType = requireNonBlank(label + ".providerType", change.getProviderType());
            changes.add(resolveCreate(label, chain, name, providerType, change.getSpec(),
                                      currentList, seenKeys));
            continue;
         }

         if(ProviderChangeRequest.VERB_DUPLICATE.equals(verb)) {
            if(change.getProviderType() != null) {
               throw new IllegalArgumentException(
                  label + ".providerType: not used for verb=duplicate; a duplicate keeps the " +
                  "source provider's own type");
            }
            if(change.getSpec() != null) {
               throw new IllegalArgumentException(
                  label + ".spec: not used for verb=duplicate; a duplicate keeps the source " +
                  "provider's own configuration -- use newName to control only its name");
            }

            changes.add(resolveDuplicate(label, chain, name, change.getNewName(), currentList,
                                         seenKeys,
                                         reservedDuplicateNames.computeIfAbsent(
                                            chain, c -> new HashSet<>())));
            continue;
         }

         if(ProviderChangeRequest.VERB_UPDATE.equals(verb)) {
            if(change.getProviderType() != null) {
               throw new IllegalArgumentException(
                  label + ".providerType: not used for verb=update; an update cannot change a " +
                  "provider's type -- delete and create a new one instead (bug 76686)");
            }

            if(change.getNewName() != null) {
               throw new IllegalArgumentException(
                  label + ".newName: not used for verb=update; renaming a provider is not " +
                  "supported in this cut, only duplicate accepts newName (bug 76686)");
            }

            changes.add(resolveUpdate(label, chain, name, change.getSpec(), currentList, seenKeys,
                                      user));
            continue;
         }

         if(change.getProviderType() != null) {
            throw new IllegalArgumentException(
               label + ".providerType: not used for verb=delete; remove it or use verb=create");
         }

         if(change.getSpec() != null) {
            throw new IllegalArgumentException(
               label + ".spec: not used for verb=delete; remove it or use verb=create");
         }

         changes.add(resolveDelete(label, chain, name, user, currentList, seenKeys));
      }

      String task = req.getTask().trim();
      String planHash = hash(changes, chainProjections);
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), true, true,
                              planHash, TaskAuditToken.issue(planHash, task));
   }

   // ---------------------------------------------------------------- create

   private PlanChange resolveCreate(String label, ProviderChain chain, String name,
                                    String providerType, ProviderLdapSpec spec,
                                    List<SecurityProviderStatus> currentList, Set<String> seenKeys)
   {
      String key = key(chain, name);
      requireUnseen(label, key, seenKeys);
      requireNameFree(label, currentList, name);

      if("FILE".equalsIgnoreCase(providerType)) {
         if(spec != null) {
            throw new IllegalArgumentException(label + ".spec: not used for providerType=FILE");
         }

         String proposed = ProviderProjection.projectFileProvider(name);
         return new PlanChange(key, NOT_ORG_SCOPED, null, proposed, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true,
                               "create " + chain.label() + " provider " + name + " (FILE)");
      }

      if("LDAP".equalsIgnoreCase(providerType)) {
         if(chain != ProviderChain.AUTHENTICATION) {
            throw new IllegalArgumentException(
               label + ".providerType: \"LDAP\" is only valid for chain=\"authentication\" " +
               "(the authorization chain accepts only \"FILE\", 01-spec.md section 11)");
         }

         requireLdapSpec(label, spec);
         requireLdapMultiTenantAllowed(label);
         String proposed = ProviderProjection.projectLdapSpec(name, spec);
         return new PlanChange(key, NOT_ORG_SCOPED, null, proposed, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true,
                               "create authentication provider " + name + " (LDAP)");
      }

      if("DATABASE".equalsIgnoreCase(providerType) || "CUSTOM".equalsIgnoreCase(providerType)) {
         throw new IllegalArgumentException(
            label + ".providerType: \"" + providerType + "\" is excluded from this cut -- " +
            "license-gating (DATABASE) and arbitrary-classloading (CUSTOM) risk this area does not " +
            "yet solve for an admin-chat caller (01-spec.md section 1)");
      }

      throw new IllegalArgumentException(
         label + ".providerType: must be \"FILE\" or \"LDAP\", got " + providerType);
   }

   private static void requireLdapSpec(String label, ProviderLdapSpec spec) {
      if(spec == null) {
         throw new IllegalArgumentException(label + ".spec: required for providerType=LDAP");
      }

      requireNonBlank(label + ".spec.ldapServer", spec.getLdapServer());

      if(!"ACTIVE_DIRECTORY".equalsIgnoreCase(spec.getLdapServer()) &&
         !"GENERIC".equalsIgnoreCase(spec.getLdapServer()))
      {
         throw new IllegalArgumentException(label + ".spec.ldapServer: must be \"ACTIVE_DIRECTORY\" " +
            "or \"GENERIC\", got " + spec.getLdapServer());
      }

      requireNonBlank(label + ".spec.protocol", spec.getProtocol());
      requireNonBlank(label + ".spec.hostName", spec.getHostName());

      if(spec.getHostPort() == null) {
         throw new IllegalArgumentException(label + ".spec.hostPort: required");
      }

      requireNonBlank(label + ".spec.rootDN", spec.getRootDN());
      boolean useCredential = Boolean.TRUE.equals(spec.getUseCredential());

      if(useCredential) {
         requireNonBlank(label + ".spec.secretId", spec.getSecretId());

         if(spec.getAdminID() != null || spec.getPassword() != null) {
            throw new IllegalArgumentException(
               label + ".spec: useCredential=true requires secretId, not adminID/password " +
               "(01-spec.md section 11 -- a field belonging to the other mode is refused loud, not " +
               "silently dropped)");
         }
      }
      else {
         requireNonBlank(label + ".spec.adminID", spec.getAdminID());
         requireNonBlank(label + ".spec.password", spec.getPassword());

         if(spec.getSecretId() != null) {
            throw new IllegalArgumentException(
               label + ".spec: useCredential=false (or unset) requires adminID/password, not " +
               "secretId");
         }
      }
   }

   /** 03-reconcile.md Addition 2: {@code AuthenticationProviderService.getProviderFromModel}'s LDAP
    * branch and {@code LdapAuthenticationProvider.checkParameters()} were both read directly in this
    * pass (04-build-java.md) and neither enforces the multi-tenant restriction
    * {@code getAuthenticationProvider} otherwise reports as {@code ldapProviderEnabled} for the EM
    * frontend's benefit -- this area self-imposes it, mirroring {@code AuthenticationProviderService
    * .java:116}'s own flag expression negated. */
   private static void requireLdapMultiTenantAllowed(String label) {
      if(LicenseManager.isEnterprise() && SUtil.isMultiTenant()) {
         throw new IllegalArgumentException(
            label + ".providerType: \"LDAP\" authentication providers are not available in a " +
            "multi-tenant enterprise deployment (mirrors the EM UI's ldapProviderEnabled flag, " +
            "which AuthenticationProviderService.getProviderFromModel itself does not enforce -- " +
            "this area's own service does, 03-reconcile.md Addition 2)");
      }
   }

   // ---------------------------------------------------------------- update (bug 76686)

   /**
    * Resolves an {@code update} entry: a partial-field edit of an existing provider, preserving its
    * chain index (unlike delete+create, which always lands the recreated provider at the END of the
    * chain -- bug 76686's own symptom). Authentication-chain LDAP providers only in this cut:
    * <ul>
    *   <li>{@code chain="authorization"} is refused outright -- the only authorization provider type
    *       this area creates (FILE) has no editable configuration fields, and {@link ProviderLdapSpec}
    *       is authentication-chain LDAP configuration only, so there is nothing an update through
    *       this DTO could meaningfully change on that chain.</li>
    *   <li>A FILE-typed authentication provider is refused for the same reason (no editable fields);
    *       DATABASE/CUSTOM are excluded from this area entirely, symmetric with create/delete.</li>
    * </ul>
    * Merges the caller's partial {@code spec} onto the current provider's LDAP configuration
    * ({@link #mergePartialLdapSpec}), cross-validates the merged {@code useCredential} mode against
    * the raw fields the caller actually sent ({@link #requireLdapCredentialCrossValidation}), then
    * runs the new self-lockout preflight ({@link #requireAuthenticationEditPreflight}) before
    * building the {@code before}/{@code proposed} projection pair.
    */
   private PlanChange resolveUpdate(String label, ProviderChain chain, String name, ProviderLdapSpec spec,
                                    List<SecurityProviderStatus> currentList, Set<String> seenKeys,
                                    Principal user)
      throws Exception
   {
      String key = key(chain, name);
      requireUnseen(label, key, seenKeys);
      requireNameExists(label, currentList, name);

      if(chain == ProviderChain.AUTHORIZATION) {
         throw new IllegalArgumentException(
            label + ".chain: verb=update is not supported for chain=\"authorization\" in this cut " +
            "-- the only authorization provider type this area creates (FILE) has no editable " +
            "configuration fields, and spec's fields are authentication-chain LDAP configuration " +
            "only (01-spec.md section 11's create-side type restriction applied symmetrically); " +
            "delete and create a new one if a different provider is needed");
      }

      if(spec == null) {
         throw new IllegalArgumentException(
            label + ".spec: required for verb=update (at least one field to change)");
      }

      AuthenticationProviderModel current = authenticationProviderService.getAuthenticationProvider(name);
      requireUpdatableAuthenticationType(label, current.providerType());

      LdapAuthenticationProviderModel mergedLdap =
         mergePartialLdapSpec(label, current.ldapProviderModel(), spec);
      requireLdapCredentialCrossValidation(label, spec, mergedLdap);

      AuthenticationProviderModel proposed = AuthenticationProviderModel.builder()
         .providerName(name)
         .oldName(name)
         .providerType(SecurityProviderType.LDAP)
         .ldapProviderModel(mergedLdap)
         .build();

      requireAuthenticationEditPreflight(label, name, proposed, user);

      String before = ProviderProjection.projectAuthenticationProvider(current);
      String proposedProjection = ProviderProjection.projectAuthenticationProvider(proposed);
      return new PlanChange(key, NOT_ORG_SCOPED, before, proposedProjection, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true,
                            "update authentication provider " + name);
   }

   /** Package-visible so {@link ProviderChangesetApplyService} can re-run this exact type gate at
    * apply time. */
   static void requireUpdatableAuthenticationType(String label, SecurityProviderType type) {
      if(type != SecurityProviderType.LDAP) {
         throw new IllegalArgumentException(
            label + ": provider is type " + type + ", not LDAP -- this area's update verb only " +
            "edits LDAP configuration (a FILE provider has no editable fields, and DATABASE/CUSTOM " +
            "providers are excluded from this area, 01-spec.md section 1's create-side exclusion " +
            "applied symmetrically to update, bug 76686)");
      }
   }

   /**
    * Overlays whatever fields the caller's partial {@code spec} actually sent (non-null) onto
    * {@code current}'s own LDAP configuration -- every field {@code spec} leaves {@code null} is
    * carried over from {@code current} unchanged. {@code ldapServer}, when sent, is validated the
    * same way {@link #requireLdapSpec} validates it for create. Package-visible so
    * {@link ProviderChangesetApplyService} can build the SAME merged value fresh at apply time
    * (never re-deriving the merge independently, mirroring {@code DataSourceChangePlanService
    * .buildProposedJdbc}'s own precedent).
    */
   static LdapAuthenticationProviderModel mergePartialLdapSpec(String label,
                                                                LdapAuthenticationProviderModel current,
                                                                ProviderLdapSpec spec)
   {
      LdapAuthenticationProviderModel.Builder builder = LdapAuthenticationProviderModel.builder();

      if(spec.getLdapServer() != null) {
         if(!"ACTIVE_DIRECTORY".equalsIgnoreCase(spec.getLdapServer()) &&
            !"GENERIC".equalsIgnoreCase(spec.getLdapServer()))
         {
            throw new IllegalArgumentException(
               label + ".spec.ldapServer: must be \"ACTIVE_DIRECTORY\" or \"GENERIC\", got " +
               spec.getLdapServer());
         }

         builder.ldapServer(SecurityProviderType.valueOf(spec.getLdapServer().toUpperCase()));
      }
      else {
         builder.ldapServer(current.ldapServer());
      }

      builder.protocol(spec.getProtocol() != null ? spec.getProtocol() : current.protocol());
      builder.hostName(spec.getHostName() != null ? spec.getHostName() : current.hostName());
      builder.hostPort(spec.getHostPort() != null ? spec.getHostPort() : current.hostPort());
      builder.rootDN(spec.getRootDN() != null ? spec.getRootDN() : current.rootDN());
      builder.useCredential(spec.getUseCredential() != null ? spec.getUseCredential() :
                            current.useCredential());
      builder.adminID(spec.getAdminID() != null ? spec.getAdminID() : current.adminID());
      builder.secretId(spec.getSecretId() != null ? spec.getSecretId() : current.secretId());
      builder.password(spec.getPassword() != null ? spec.getPassword() : current.password());
      builder.userFilter(spec.getUserFilter() != null ? spec.getUserFilter() : current.userFilter());
      builder.userBase(spec.getUserBase() != null ? spec.getUserBase() : current.userBase());
      builder.userAttr(spec.getUserAttr() != null ? spec.getUserAttr() : current.userAttr());
      builder.mailAttr(spec.getMailAttr() != null ? spec.getMailAttr() : current.mailAttr());
      builder.groupFilter(spec.getGroupFilter() != null ? spec.getGroupFilter() : current.groupFilter());
      builder.groupBase(spec.getGroupBase() != null ? spec.getGroupBase() : current.groupBase());
      builder.groupAttr(spec.getGroupAttr() != null ? spec.getGroupAttr() : current.groupAttr());
      builder.roleFilter(spec.getRoleFilter() != null ? spec.getRoleFilter() : current.roleFilter());
      builder.roleBase(spec.getRoleBase() != null ? spec.getRoleBase() : current.roleBase());
      builder.roleAttr(spec.getRoleAttr() != null ? spec.getRoleAttr() : current.roleAttr());
      builder.userRoleFilter(spec.getUserRoleFilter() != null ? spec.getUserRoleFilter() :
                             current.userRoleFilter());
      builder.roleRoleFilter(spec.getRoleRoleFilter() != null ? spec.getRoleRoleFilter() :
                             current.roleRoleFilter());
      builder.groupRoleFilter(spec.getGroupRoleFilter() != null ? spec.getGroupRoleFilter() :
                              current.groupRoleFilter());
      builder.startTls(spec.getStartTls() != null ? spec.getStartTls() : current.startTls());
      builder.searchTree(spec.getSearchTree() != null ? spec.getSearchTree() : current.searchTree());
      builder.sysAdminRoles(spec.getSysAdminRoles() != null ?
                            spec.getSysAdminRoles().toArray(new String[0]) : current.sysAdminRoles());
      return builder.build();
   }

   /**
    * Cross-field {@code useCredential} validation, run against the MERGED/proposed model (not the
    * raw partial spec) -- a caller rotating only {@code password} on a provider currently in
    * {@code useCredential=true}/{@code secretId} mode would pass a naive per-field-if-present check
    * cleanly (neither the omitted {@code secretId} nor the newly-sent {@code password} violates
    * anything in isolation) while producing a merged model that is internally contradictory.
    * Mirrors {@code DataSourceChangePlanService.requireCredentialCrossValidation}'s exact shape:
    * check which raw {@code spec} fields the caller actually sent against the RESOLVED state of the
    * proposed object. Package-visible for the same apply-time reuse reason as
    * {@link #mergePartialLdapSpec}.
    */
   static void requireLdapCredentialCrossValidation(String label, ProviderLdapSpec spec,
                                                     LdapAuthenticationProviderModel proposed)
   {
      if(proposed.useCredential()) {
         if(spec.getAdminID() != null || spec.getPassword() != null) {
            throw new IllegalArgumentException(
               label + ".spec: useCredential=true requires secretId, not adminID/password (a field " +
               "belonging to the other mode is refused loud, not silently dropped)");
         }

         if(proposed.secretId() == null || proposed.secretId().isBlank()) {
            throw new IllegalArgumentException(
               label + ".spec.secretId: required when the resolved useCredential is true");
         }
      }
      else {
         if(spec.getSecretId() != null) {
            throw new IllegalArgumentException(
               label + ".spec: useCredential=false (or unset) does not accept secretId; set " +
               "useCredential=true to use a stored credential instead");
         }

         if(proposed.adminID() == null || proposed.adminID().isBlank()) {
            throw new IllegalArgumentException(
               label + ".spec.adminID: required when the resolved useCredential is false");
         }

         if(proposed.password() == null || proposed.password().isBlank()) {
            throw new IllegalArgumentException(
               label + ".spec.password: required when the resolved useCredential is false");
         }
      }
   }

   /**
    * bug 76686's one genuinely new preflight: generalizes {@link #requireAuthenticationDeletePreflight}'s
    * simulate-and-check pattern from "remove the named provider from a copy of the chain" to "replace
    * the named provider, in the copy, with the provider built from the merged/proposed model" --
    * {@code providerHasSysAdmins}/{@code callerRetainsSysAdmin} both already operate on a plain
    * {@code List<AuthenticationProvider>} and do not care whether the list changed via removal or
    * replacement. No new authorization-chain preflight is needed (confirmed by tracing {@code
    * AuthorizationChain.getPermission}'s resolution, which has no length-dependent branch, and edit
    * never changes chain length -- 01-diagnosis.md Revision round 1 item 2).
    * <p><b>This preflight performs a real, live LDAP connection test at PREVIEW time.</b> Building
    * the "proposed" provider instance (via {@link
    * AuthenticationProviderService#buildProviderForPreflightSimulation}, the same {@code
    * getProviderFromModel} path {@code create}/{@code duplicate} use) calls, for LDAP, {@code
    * LdapAuthenticationProvider.checkParameters()} -- which does {@code createContext()}/
    * {@code testContext()}, an actual bind against the real directory server using the proposed
    * (possibly just-rotated) credentials, not a local field-shape check. This is genuinely new
    * behavior for {@code preview_provider_changes}, not an equivalent, already-accepted cost: {@code
    * resolveCreate} never calls {@code getProviderFromModel} at all -- {@code create}'s own live bind
    * happens only at {@code apply} time, inside {@code addAuthenticationProvider}. (An earlier draft
    * of the design behind this preflight claimed the opposite -- "checkParameters() only, not a live
    * bind... already-accepted risk, not new risk" -- corrected here per review; see
    * 01-diagnosis.md's own "Important finding" callout for the full trace.) The instance is torn down
    * in a {@code finally} block immediately after the checks and never added to the live chain, but a
    * transient LDAP outage or slow network can still cause an {@code update} entry's own {@code
    * preview_provider_changes} call to fail or hang -- a materially different failure mode than every
    * other verb in this area, which only validates field shape at preview and defers any live
    * connection test to {@code apply}. This is treated as an intentional, accepted trade-off (it
    * catches a bad rotated password/host before commit, arguably better than {@code create}'s
    * fail-only-at-apply behavior), not a defect -- but it is not free, and callers/operators should
    * know a hung LDAP server can make an {@code update} preview hang with it.
    * <p>Package-visible for the same apply-time re-run reason as
    * {@link #requireAuthenticationDeletePreflight}.
    */
   void requireAuthenticationEditPreflight(String label, String name,
                                           AuthenticationProviderModel mergedModel, Principal user)
      throws Exception
   {
      AuthenticationProvider proposedProvider = authenticationProviderService
         .buildProviderForPreflightSimulation(mergedModel)
         .orElseThrow(() -> new IllegalArgumentException(
            label + ".spec: failed to construct the proposed provider configuration for preflight " +
            "simulation"));

      try {
         List<AuthenticationProvider> simulated = simulatedAuthenticationProvidersAfterEdit(name,
            proposedProvider);

         if(simulated.stream().noneMatch(ProviderChangePlanService::providerHasSysAdmins)) {
            throw new IllegalArgumentException(
               label + ": updating authentication provider \"" + name + "\" would leave the " +
               "authentication chain with no remaining provider defining a system administrator " +
               "role with a real member -- refused (bug 76686, reproducing " +
               "AuthenticationProviderService.providerHasSysAdmins against the simulated post-edit " +
               "chain)");
         }

         IdentityID[] callerRoles = callerRoles(user);

         if(!callerRetainsSysAdmin(simulated, callerRoles)) {
            throw new IllegalArgumentException(
               label + ": updating authentication provider \"" + name + "\" would resolve none of " +
               "the calling session's own roles to system-administrator against the edited chain -- " +
               "applying this plan would lock the calling session out of every admin-chat area, not " +
               "just this one (bug 76686). The deployment as a whole may retain system-administrator " +
               "capability through a different provider/role/account even though this specific " +
               "caller would not.");
         }
      }
      finally {
         proposedProvider.tearDown();
      }
   }

   private List<AuthenticationProvider> simulatedAuthenticationProvidersAfterEdit(String name,
      AuthenticationProvider proposedProvider)
   {
      List<AuthenticationProvider> current = securityEngine.getAuthenticationChain()
         .map(AuthenticationChain::getProviders).orElseGet(List::of);
      List<AuthenticationProvider> simulated = new ArrayList<>(current);

      for(int i = 0; i < simulated.size(); i++) {
         if(simulated.get(i).getProviderName().equals(name)) {
            simulated.set(i, proposedProvider);
            break;
         }
      }

      return simulated;
   }

   // ---------------------------------------------------------------- duplicate

   /**
    * Resolves a {@code duplicate} entry: the source must exist, {@code newName} (if given) must not
    * collide -- with the live chain OR with another duplicate entry's target name already resolved
    * earlier in this same request (bug 76655, {@code reservedNames}; the live chain alone cannot
    * see a sibling entry's not-yet-applied proposed name, since preview never mutates anything) --
    * and the proposed value is the source's own projection with its name swapped to the copy's name
    * -- reusing {@link ProviderProjection#projectAuthenticationProvider}/
    * {@link ProviderProjection#projectAuthorizationProvider} rather than a bespoke projection, since
    * a duplicate is otherwise byte-for-byte the source (bug 76602). Unlike {@code create}, no
    * provider-type restriction applies here (03-fix.md): the real EM "Duplicate" button is
    * unconditional, not gated by type.
    *
    * <p><b>Correction (bug 76655):</b> this javadoc used to claim duplicate needs no type
    * restriction because it "never spins up a fresh, unvetted instance" -- true of THIS method
    * (preview only reads and projects, no mutation), but false of {@code apply}:
    * {@code ProviderChangesetApplyService.applyDuplicateAuthentication} calls {@code
    * AuthenticationProviderService.addAuthenticationProvider}, which reaches the exact same
    * {@code getProviderFromModel} chain a fresh {@code create} would -- a real {@code
    * Class.forName}/{@code newInstance} for CUSTOM, a real, live {@code testConnection()} for
    * DATABASE. That risk is real, but narrower than {@code create}'s: a duplicate's connection
    * parameters/class name are not caller-supplied, they come from an already-configured, existing
    * provider, and {@code AuthenticationProviderService.checkProviderTypeLicensed} (bug 76359)
    * already refuses any DATABASE/CUSTOM introduction -- duplicate included, since it calls the
    * same {@code addAuthenticationProvider} entry point -- on a non-Enterprise-licensed deployment.
    * So on Community, this path is already blocked; on Enterprise, duplicating a DATABASE/CUSTOM
    * provider does re-run a live connection test / re-instantiate a class, the same as {@code
    * create} would, just against parameters an EM admin (not this caller) already configured.
    */
   private PlanChange resolveDuplicate(String label, ProviderChain chain, String name, String newName,
                                       List<SecurityProviderStatus> currentList, Set<String> seenKeys,
                                       Set<String> reservedNames)
      throws Exception
   {
      String key = key(chain, name);
      requireUnseen(label, key, seenKeys);
      requireNameExists(label, currentList, name);

      String actualNewName = resolveDuplicateName(label, newName, name, currentList);

      if(!reservedNames.add(actualNewName)) {
         throw new IllegalArgumentException(
            label + ".newName: \"" + actualNewName + "\" is already claimed by another duplicate " +
            "entry earlier in this same request; choose a different name for one of them");
      }

      if(chain == ProviderChain.AUTHENTICATION) {
         AuthenticationProviderModel source = authenticationProviderService.getAuthenticationProvider(name);
         AuthenticationProviderModel duplicated =
            ((ImmutableAuthenticationProviderModel) source).withProviderName(actualNewName);
         String proposed = ProviderProjection.projectAuthenticationProvider(duplicated);
         return new PlanChange(key, NOT_ORG_SCOPED, null, proposed, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true,
                               "duplicate authentication provider " + name + " as " + actualNewName);
      }

      AuthorizationProviderModel source = authorizationProviderService.getAuthorizationProvider(name);
      AuthorizationProviderModel duplicated =
         ((ImmutableAuthorizationProviderModel) source).withProviderName(actualNewName);
      String proposed = ProviderProjection.projectAuthorizationProvider(duplicated);
      return new PlanChange(key, NOT_ORG_SCOPED, null, proposed, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true,
                            "duplicate authorization provider " + name + " as " + actualNewName);
   }

   /**
    * Computes the copy's actual name: an explicit, non-colliding {@code newName} if given, otherwise
    * the same {@code Util.getCopyName}/{@code getNextCopyName} collision loop
    * {@code AuthenticationProviderController#copyAuthenticationProvider}/
    * {@code AuthorizationProviderController#copyAuthorizationProviderCache} already use (bug 76602 --
    * called through, not reimplemented independently). Package-visible and {@code static} so
    * {@link ProviderChangesetApplyService} can recompute this SAME deterministic result fresh at
    * apply time, against the live list read at that moment, rather than trusting a value captured at
    * preview time (matching every other verb's "resolved fresh at apply" discipline in this area).
    */
   static String resolveDuplicateName(String label, String requestedNewName, String sourceName,
                                      List<SecurityProviderStatus> currentList)
   {
      if(requestedNewName != null && !requestedNewName.trim().isEmpty()) {
         String trimmed = requestedNewName.trim();

         if(existsInList(currentList, trimmed)) {
            throw new IllegalArgumentException(
               label + ".newName: \"" + trimmed + "\" already exists in this chain; choose a " +
               "different name or omit newName to auto-generate one");
         }

         return trimmed;
      }

      String copyName = Util.getCopyName(sourceName);

      while(existsInList(currentList, copyName)) {
         copyName = Util.getNextCopyName(sourceName, copyName);
      }

      return copyName;
   }

   // ---------------------------------------------------------------- delete

   private PlanChange resolveDelete(String label, ProviderChain chain, String name, Principal user,
                                    List<SecurityProviderStatus> currentList, Set<String> seenKeys)
      throws Exception
   {
      String key = key(chain, name);
      requireUnseen(label, key, seenKeys);
      requireNameExists(label, currentList, name);

      if(chain == ProviderChain.AUTHENTICATION) {
         AuthenticationProviderModel model = authenticationProviderService.getAuthenticationProvider(name);
         requireDeletableAuthenticationType(label, model.providerType());
         requireAuthenticationDeletePreflight(label, name, user);
         String before = ProviderProjection.projectAuthenticationProvider(model);
         return new PlanChange(key, NOT_ORG_SCOPED, before, null, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true,
                               "delete authentication provider " + name);
      }

      AuthorizationProviderModel model = authorizationProviderService.getAuthorizationProvider(name);
      requireDeletableAuthorizationType(label, model.providerType());
      requireAuthorizationDeletePreflight(label, name);
      String before = ProviderProjection.projectAuthorizationProvider(model);
      return new PlanChange(key, NOT_ORG_SCOPED, before, null, AdminChangeRecord.RISK_HIGH,
                            AdminChangeRecord.SCOPE_STORAGE, true,
                            "delete authorization provider " + name);
   }

   /** This area's own delete-target restriction (04-build-java.md): only FILE/LDAP, the types this
    * area can itself create, may be deleted -- a DATABASE/CUSTOM provider pre-existing from EM is
    * refused, because delete's rollback path would need to recreate it via the exact
    * {@code getProviderFromModel}/{@code createCustomProvider} call chain 01-spec.md section 1
    * excludes from creation for license-gating/classloading reasons. */
   private static void requireDeletableAuthenticationType(String label, SecurityProviderType type) {
      if(type != SecurityProviderType.FILE && type != SecurityProviderType.LDAP) {
         throw new IllegalArgumentException(
            label + ": provider is type " + type + ", not FILE or LDAP -- this area cannot delete " +
            "a DATABASE/CUSTOM authentication provider (01-spec.md section 1's create-side " +
            "exclusion applied symmetrically to delete's own rollback path, 04-build-java.md)");
      }
   }

   private static void requireDeletableAuthorizationType(String label, SecurityProviderType type) {
      if(type != SecurityProviderType.FILE) {
         throw new IllegalArgumentException(
            label + ": provider is type " + type + ", not FILE -- this area cannot delete a CUSTOM " +
            "authorization provider (same reasoning as the authentication-chain DATABASE/CUSTOM " +
            "exclusion, 04-build-java.md)");
      }
   }

   /**
    * 01-spec.md section 4, two independent checks, neither redundant with the other:
    * <ol>
    *   <li>Deployment-wide invariant, reproducing {@code AuthenticationProviderService
    *       .providerHasSysAdmins}'s own predicate against a simulated post-removal chain (never
    *       mutating the live chain).</li>
    *   <li>Caller-specific: using the same simulated list, reproduce {@code AuthenticationChain
    *       .isSystemAdministratorRole}'s "first provider that defines this role" resolution against
    *       the calling principal's own JWT-baked roles ({@code ((XPrincipal) principal).getRoles()},
    *       the same source {@code OrganizationManager.isSiteAdmin}'s own first, decisive branch
    *       reads). This can fire even when check 1 passes -- the deployment as a whole may retain
    *       system-administrator capability through a different provider/role while this specific
    *       caller loses theirs.</li>
    * </ol>
    * {@code OrganizationManager.isSiteAdmin(Principal)} also has a second, fallback branch that
    * re-reads the live user's stored roles via {@code SecurityProvider.getUser(...)} when the
    * JWT-baked roles alone don't resolve to sys-admin (community/core/.../OrganizationManager.java:
    * 125-140, read directly in this pass) -- this preflight does not reproduce that fallback branch,
    * matching 01-spec.md section 4's own stated trace (JWT-baked roles only). Recorded as an
    * open item in 04-build-java.md rather than silently expanding this preflight's scope beyond what
    * was specified.
    */
   /** Package-visible so {@link ProviderChangesetApplyService} can re-run this exact check at apply
    * time against the freshly-read live chain (01-spec.md section 6 step 2a: a concurrent change
    * between preview and apply could alter which providers/roles remain even if the hash still
    * happens to match for an unrelated reason -- belt-and-suspenders given the stakes). */
   void requireAuthenticationDeletePreflight(String label, String name, Principal user) {
      List<AuthenticationProvider> simulated = simulatedAuthenticationProviders(name);

      if(simulated.stream().noneMatch(ProviderChangePlanService::providerHasSysAdmins)) {
         throw new IllegalArgumentException(
            label + ": deleting authentication provider \"" + name + "\" would leave the " +
            "authentication chain with no remaining provider defining a system administrator role " +
            "with a real member -- refused (01-spec.md section 4, reproducing " +
            "AuthenticationProviderService.providerHasSysAdmins against the simulated post-removal " +
            "chain)");
      }

      IdentityID[] callerRoles = callerRoles(user);

      if(!callerRetainsSysAdmin(simulated, callerRoles)) {
         throw new IllegalArgumentException(
            label + ": deleting authentication provider \"" + name + "\" would resolve none of the " +
            "calling session's own roles to system-administrator against the remaining chain -- " +
            "applying this plan would lock the calling session out of every admin-chat area, not " +
            "just this one (01-spec.md section 4). The deployment as a whole may retain " +
            "system-administrator capability through a different provider/role/account even though " +
            "this specific caller would not.");
      }
   }

   private List<AuthenticationProvider> simulatedAuthenticationProviders(String name) {
      List<AuthenticationProvider> current = securityEngine.getAuthenticationChain()
         .map(AuthenticationChain::getProviders).orElseGet(List::of);
      List<AuthenticationProvider> simulated = new ArrayList<>(current);
      simulated.removeIf(p -> p.getProviderName().equals(name));
      return simulated;
   }

   private static IdentityID[] callerRoles(Principal user) {
      if(user instanceof XPrincipal xp) {
         IdentityID[] roles = xp.getRoles();
         return roles == null ? new IdentityID[0] : roles;
      }

      return new IdentityID[0];
   }

   private static boolean providerHasSysAdmins(AuthenticationProvider provider) {
      return Arrays.stream(provider.getRoles())
         .anyMatch(role -> provider.isSystemAdministratorRole(role) &&
                          provider.getRoleMembers(role).length > 0);
   }

   private static boolean callerRetainsSysAdmin(List<AuthenticationProvider> simulated,
                                                IdentityID[] callerRoles)
   {
      for(IdentityID roleId : callerRoles) {
         Optional<AuthenticationProvider> resolving = simulated.stream()
            .filter(p -> p.getRole(roleId) != null)
            .findFirst();

         if(resolving.isPresent() && resolving.get().isSystemAdministratorRole(roleId)) {
            return true;
         }
      }

      return false;
   }

   /** 01-spec.md section 4: a new, minimal floor this spec proposes -- no
    * {@code providerHasSysAdmins}-equivalent guard exists for the authorization chain, and no
    * caller-specific self-lockout applies to it (a wiz principal's own gate never consults the
    * authorization chain, section 4's own risk framing) -- so this is a floor, not a two-part
    * preflight. */
   /** Package-visible for the same apply-time re-check reason as
    * {@link #requireAuthenticationDeletePreflight}. */
   void requireAuthorizationDeletePreflight(String label, String name) {
      List<AuthorizationProvider> current = securityEngine.getAuthorizationChain()
         .map(AuthorizationChain::getProviders).orElseGet(List::of);
      List<AuthorizationProvider> simulated = new ArrayList<>(current);
      simulated.removeIf(p -> p.getProviderName().equals(name));

      if(simulated.isEmpty()) {
         throw new IllegalArgumentException(
            label + ": deleting authorization provider \"" + name + "\" would leave the " +
            "authorization chain with zero remaining providers -- refused as a precautionary floor " +
            "(01-spec.md section 4; AuthorizationChain's own checkPermission behavior on an empty " +
            "chain was not traced to a specific line in this pass, section 14 item 3)");
      }
   }

   // ---------------------------------------------------------------- name resolution (Addition 1)

   /** 03-reconcile.md Addition 1: raw-name match only, for both chains, never
    * {@code AuthenticationProviderService.getProviderByName}/
    * {@code AuthorizationProviderService.getProviderByName} directly (confirmed in 04-build-java.md
    * to resolve a name two different ways between the two services). */
   private static boolean existsInList(List<SecurityProviderStatus> list, String name) {
      for(SecurityProviderStatus s : list) {
         if(s.name().equals(name)) {
            return true;
         }
      }

      return false;
   }

   private static void requireNameFree(String label, List<SecurityProviderStatus> list, String name) {
      if(existsInList(list, name)) {
         throw new IllegalArgumentException(
            label + ".name: \"" + name + "\" already exists in this chain; use a different name, " +
            "verb=update to edit it in place, or delete it first if you intend to replace it with a " +
            "different provider type");
      }
   }

   private static void requireNameExists(String label, List<SecurityProviderStatus> list, String name) {
      if(!existsInList(list, name)) {
         throw new IllegalArgumentException(label + ".name: \"" + name + "\" not found in this chain");
      }
   }

   // ---------------------------------------------------------------- shared helpers

   private List<SecurityProviderStatus> currentProviderList(ProviderChain chain) {
      return chain == ProviderChain.AUTHENTICATION
         ? authenticationProviderService.getProviderListModel().providers()
         : authorizationProviderService.getProviderListModel().providers();
   }

   private String currentChainProjection(ProviderChain chain) {
      if(chain == ProviderChain.AUTHENTICATION) {
         return ProviderProjection.projectChain(
            authenticationProviderService.getProviderListModel().providers(), true);
      }

      return ProviderProjection.projectChain(
         authorizationProviderService.getProviderListModel().providers(), false);
   }

   static String requireVerb(String label, String verb) {
      if(ProviderChangeRequest.VERB_CREATE.equals(verb) || ProviderChangeRequest.VERB_DELETE.equals(verb) ||
         ProviderChangeRequest.VERB_DUPLICATE.equals(verb) || ProviderChangeRequest.VERB_UPDATE.equals(verb))
      {
         return verb;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"" + ProviderChangeRequest.VERB_CREATE + "\", \"" +
         ProviderChangeRequest.VERB_DELETE + "\", \"" + ProviderChangeRequest.VERB_DUPLICATE +
         "\", or \"" + ProviderChangeRequest.VERB_UPDATE + "\", got " + String.valueOf(verb));
   }

   /** Deliberately case-insensitive/trimmed exact-label match, no abbreviation aliasing -- "auth" is
    * genuinely ambiguous between the two full words (01-spec.md section 11). */
   static ProviderChain requireChain(String label, String chain) {
      if(chain != null) {
         for(ProviderChain c : ProviderChain.values()) {
            if(c.label().equalsIgnoreCase(chain.trim())) {
               return c;
            }
         }
      }

      throw new IllegalArgumentException(
         label + ".chain: must be \"authentication\" or \"authorization\", got " +
         String.valueOf(chain));
   }

   static String requireNonBlank(String field, String value) {
      if(value == null || value.trim().isEmpty()) {
         throw new IllegalArgumentException(field + ": required");
      }

      return value.trim();
   }

   private static void requireUnseen(String label, String key, Set<String> seenKeys) {
      if(!seenKeys.add(key)) {
         throw new IllegalArgumentException(
            label + ": duplicate entry for " + key + "; list each provider at most once");
      }
   }

   static String key(ProviderChain chain, String name) {
      return chain.label() + "|" + name;
   }

   /**
    * SHA-256 over the canonical plan. Same field-order/control-character contract as every prior
    * area's own {@code hash} method, extended with one input none of them needed: the whole-chain,
    * order-sensitive projection for every chain touched by this plan (01-spec.md section 5) --
    * captured once per chain, not stored in any {@link PlanChange} (whose two value slots are
    * reserved for the narrower per-provider projection, section 5's own "hash's unit and
    * verification's unit can differ" design, see 04-build-java.md). A concurrent create/delete/
    * reorder on a touched chain changes that chain's projection, so {@code apply}'s fresh re-resolve
    * produces a different hash and is refused via the existing 409 path -- no new conflict-handling
    * code needed.
    * <p>
    * {@code task} is deliberately excluded from this hash: it is a free-text audit narrative, never
    * compared, parsed, or gated on -- it flows only into {@code ProviderChangesetApplyService
    * .writeAudit}'s {@code record.setTaskDescription(task)}. Including it here would fail the
    * apply-time plan-hash check whenever a caller paraphrases the task description between preview
    * and apply while the actual {@code changes}/chain state stay identical.
    */
   private static String hash(List<PlanChange> changes, Map<ProviderChain, String> chainProjections)
   {
      StringBuilder canonical = new StringBuilder();

      for(ProviderChain chain : ProviderChain.values()) {
         String projection = chainProjections.get(chain);

         if(projection != null) {
            canonical.append("chain:").append(chain.label()).append(SEP).append(projection).append(SEP);
         }
      }

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
         throw new IllegalStateException("SHA-256 is required to hash a provider change plan", e);
      }
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   /** Provider configuration is deployment-wide, not org-scoped (01-spec.md section 1/5) -- every
    * {@link PlanChange} this service builds passes this in place of a bare {@code null} literal so
    * the omission reads as deliberate, not an oversight. See 03-reconcile.md's {@code orgId}
    * resolution for why a sentinel string was considered and rejected in favor of {@code null}
    * (nothing downstream requires non-null, confirmed by 04-build-java.md). */
   private static final String NOT_ORG_SCOPED = null;
   private final AuthenticationProviderService authenticationProviderService;
   private final AuthorizationProviderService authorizationProviderService;
   private final SecurityEngine securityEngine;
}
