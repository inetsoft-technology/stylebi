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

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
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
      return new ResolvedPlan(task, Collections.unmodifiableList(changes), true, true,
                              hash(task, changes, chainProjections));
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
         return new PlanChange(key, null, null, proposed, AdminChangeRecord.RISK_HIGH,
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
         return new PlanChange(key, null, null, proposed, AdminChangeRecord.RISK_HIGH,
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
         return new PlanChange(key, null, before, null, AdminChangeRecord.RISK_HIGH,
                               AdminChangeRecord.SCOPE_STORAGE, true,
                               "delete authentication provider " + name);
      }

      AuthorizationProviderModel model = authorizationProviderService.getAuthorizationProvider(name);
      requireDeletableAuthorizationType(label, model.providerType());
      requireAuthorizationDeletePreflight(label, name);
      String before = ProviderProjection.projectAuthorizationProvider(model);
      return new PlanChange(key, null, before, null, AdminChangeRecord.RISK_HIGH,
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
            "or delete it first if you intend to replace it (update is not supported yet)");
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
      if(ProviderChangeRequest.VERB_CREATE.equals(verb) || ProviderChangeRequest.VERB_DELETE.equals(verb)) {
         return verb;
      }

      throw new IllegalArgumentException(
         label + ".verb: must be \"" + ProviderChangeRequest.VERB_CREATE + "\" or \"" +
         ProviderChangeRequest.VERB_DELETE + "\", got " + String.valueOf(verb));
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
    */
   private static String hash(String task, List<PlanChange> changes,
                              Map<ProviderChain, String> chainProjections)
   {
      StringBuilder canonical = new StringBuilder(task).append('\n');

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
   private final AuthenticationProviderService authenticationProviderService;
   private final AuthorizationProviderService authorizationProviderService;
   private final SecurityEngine securityEngine;
}
