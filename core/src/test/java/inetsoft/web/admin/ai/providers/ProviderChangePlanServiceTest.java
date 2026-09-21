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
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 01-spec.md section 1 (scope/providerType-chain cross-validation), section 2 (name resolution,
 * raw-name-only per 03-reconcile.md Addition 1), section 4 (both self-lockout preflight checks),
 * and section 5 (the whole-chain, order-sensitive hash input).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ProviderChangePlanServiceTest {
   private static final IdentityID CALLER_ROLE = new IdentityID("Administrator", "host-org");

   @Mock private AuthenticationProviderService authenticationProviderService;
   @Mock private AuthorizationProviderService authorizationProviderService;
   @Mock private SecurityEngine securityEngine;
   @Mock private XPrincipal user;
   private ProviderChangePlanService service;
   private MockedStatic<Tool> tool;

   @BeforeEach void setUp() {
      service = new ProviderChangePlanService(authenticationProviderService,
                                              authorizationProviderService, securityEngine);
      lenient().when(user.getRoles()).thenReturn(new IdentityID[] { CALLER_ROLE });
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach void tearDown() {
      tool.close();
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      ProviderChangePlanRequest req = request("  ", List.of(deleteAuth("p1")));
      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user))
                    .getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      ProviderChangePlanRequest req = request("task", List.of());
      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
   }

   @Test void resolveThrowsOnUnrecognizedVerb() {
      ProviderChangeRequest change = deleteAuth("p1");
      change.setVerb("rename");
      stubEmptyAuthenticationChain(List.of("p1"));
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user)).getMessage().contains("verb"));
   }

   @Test void resolveThrowsOnAmbiguousChainAbbreviation() {
      ProviderChangeRequest change = deleteAuth("p1");
      change.setChain("auth");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("chain"));
      assertTrue(ex.getMessage().contains("authentication"));
      assertTrue(ex.getMessage().contains("authorization"));
   }

   @Test void resolveThrowsOnDuplicateEntries() {
      stubEmptyAuthenticationChain(List.of());
      ProviderChangeRequest c1 = createFile(ProviderChain.AUTHENTICATION, "p1");
      ProviderChangeRequest c2 = createFile(ProviderChain.AUTHENTICATION, "p1");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(c1, c2)), user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   // -------------------------------------------------------------------------
   // duplicate verb (bug 76602)
   // -------------------------------------------------------------------------

   @Test void resolveDuplicateAuthenticationAutoGeneratesCopyName() throws Exception {
      stubProviderList(authenticationProviderService, List.of("p1"));
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(fileModel("p1"));

      ResolvedPlan plan = service.resolve(request("dup", List.of(duplicateAuth("p1", null))), user);

      PlanChange change = plan.changes().get(0);
      assertNull(change.currentValue());
      assertTrue(change.proposedValue().contains("name=Copy of p1;"));
   }

   @Test void resolveDuplicateAuthenticationWithExplicitNewNameUsesIt() throws Exception {
      stubProviderList(authenticationProviderService, List.of("p1"));
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(fileModel("p1"));

      ResolvedPlan plan = service.resolve(request("dup", List.of(duplicateAuth("p1", "p1-clone"))), user);

      assertTrue(plan.changes().get(0).proposedValue().contains("name=p1-clone;"));
   }

   @Test void resolveDuplicateAuthenticationExplicitNewNameCollidesThrows() {
      stubProviderList(authenticationProviderService, List.of("p1", "p2"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("dup", List.of(duplicateAuth("p1", "p2"))), user));
      assertTrue(ex.getMessage().contains("newName"));
   }

   // Bug 76655 (F7): two duplicate entries in the same request, different sources, both proposing
   // the same explicit newName -- the live chain (read once, unmutated during preview) contains
   // neither proposed copy yet, so the OLD collision check (against the live chain only) let both
   // through, deferring the real collision to apply-time, where it fails the second entry and rolls
   // back the whole changeset. Must now be refused at preview/resolve() time instead.
   @Test void resolveDuplicateTwoEntriesSameExplicitNewNameThrowsAtPreview() throws Exception {
      stubProviderList(authenticationProviderService, List.of("p1", "p2"));
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(fileModel("p1"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("dup", List.of(
            duplicateAuth("p1", "clone"), duplicateAuth("p2", "clone"))), user));

      assertTrue(ex.getMessage().contains("newName"));
      assertTrue(ex.getMessage().contains("clone"));
   }

   // Two duplicates targeting DIFFERENT explicit newNames must not collide with each other.
   @Test void resolveDuplicateTwoEntriesDifferentExplicitNewNamesBothSucceed() throws Exception {
      stubProviderList(authenticationProviderService, List.of("p1", "p2"));
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(fileModel("p1"));
      when(authenticationProviderService.getAuthenticationProvider("p2")).thenReturn(fileModel("p2"));

      ResolvedPlan plan = service.resolve(request("dup", List.of(
         duplicateAuth("p1", "clone1"), duplicateAuth("p2", "clone2"))), user);

      assertEquals(2, plan.changes().size());
   }

   @Test void resolveDuplicateAuthenticationSourceNotFoundThrows() {
      stubProviderList(authenticationProviderService, List.of());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("dup", List.of(duplicateAuth("missing", null))), user));
      assertTrue(ex.getMessage().contains("not found"));
   }

   @Test void resolveDuplicateRejectsProviderType() {
      stubProviderList(authenticationProviderService, List.of("p1"));
      ProviderChangeRequest change = duplicateAuth("p1", null);
      change.setProviderType("FILE");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("dup", List.of(change)), user));
      assertTrue(ex.getMessage().contains("providerType"));
   }

   @Test void resolveDuplicateRejectsSpec() {
      stubProviderList(authenticationProviderService, List.of("p1"));
      ProviderChangeRequest change = duplicateAuth("p1", null);
      change.setSpec(ldapSpec());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("dup", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec"));
   }

   @Test void resolveDuplicateAuthorizationAlsoSupported() throws Exception {
      stubProviderList(authorizationProviderService, List.of("p1"));
      when(authorizationProviderService.getAuthorizationProvider("p1")).thenReturn(authzFileModel("p1"));
      ProviderChangeRequest change = duplicateAuth("p1", null);
      change.setChain("authorization");

      ResolvedPlan plan = service.resolve(request("dup", List.of(change)), user);

      assertTrue(plan.changes().get(0).proposedValue().contains("name=Copy of p1;"));
   }

   // -------------------------------------------------------------------------
   // providerType/chain cross-validation (section 11)
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnLdapForAuthorizationChain() {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("create");
      change.setChain("authorization");
      change.setName("p1");
      change.setProviderType("LDAP");
      stubProviderList(authorizationProviderService, List.of());
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("providerType"));
   }

   // -------------------------------------------------------------------------
   // DATABASE create (bug 76710/76716) -- the blanket client-side exclusion is gone; the real
   // license gate (AuthenticationProviderService.checkProviderTypeLicensed, bug 76359) is reused
   // via requireProviderTypeLicensed rather than re-derived, so these tests verify THIS service
   // calls that reused gate and propagates its refusal, not the gate's own internal logic (that
   // belongs to AuthenticationProviderServiceTest).
   // -------------------------------------------------------------------------

   @Test void resolveAllowsDatabaseProviderTypeCreateOnceLicensed() throws Exception {
      ProviderChangeRequest change = createDatabase(ProviderChain.AUTHENTICATION, "db1", databaseSpec());
      stubEmptyAuthenticationChain(List.of());

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);

      assertEquals(1, plan.changes().size());
      assertTrue(plan.changes().get(0).proposedValue().contains("type=DATABASE;"));
      verify(authenticationProviderService).requireProviderTypeLicensed(SecurityProviderType.DATABASE);
   }

   @Test void resolveDatabaseCreatePropagatesTheReusedLicenseRefusal() {
      ProviderChangeRequest change = createDatabase(ProviderChain.AUTHENTICATION, "db1", databaseSpec());
      stubEmptyAuthenticationChain(List.of());
      doThrow(new RuntimeException("em.securityProvider.databaseNotLicensed"))
         .when(authenticationProviderService)
         .requireProviderTypeLicensed(SecurityProviderType.DATABASE);

      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("databaseNotLicensed"));
   }

   @Test void resolveThrowsOnMissingDatabaseSpecForDatabaseCreate() {
      ProviderChangeRequest change = createDatabase(ProviderChain.AUTHENTICATION, "db1", null);
      stubEmptyAuthenticationChain(List.of());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("databaseSpec"));
   }

   @Test void resolveThrowsOnSpecInsteadOfDatabaseSpecForDatabaseCreate() {
      ProviderChangeRequest change = createDatabase(ProviderChain.AUTHENTICATION, "db1", databaseSpec());
      change.setSpec(ldapSpec());
      stubEmptyAuthenticationChain(List.of());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec"));
      assertTrue(ex.getMessage().contains("databaseSpec"));
   }

   @Test void resolveThrowsOnDatabaseForAuthorizationChain() {
      ProviderChangeRequest change = createDatabase(ProviderChain.AUTHORIZATION, "db1", databaseSpec());
      stubProviderList(authorizationProviderService, List.of());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("DATABASE"));
      verifyNoInteractions(authenticationProviderService);
   }

   @Test void resolveThrowsOnDatabaseUseCredentialTrueWithUserPresent() {
      ProviderDatabaseSpec spec = databaseSpec();
      spec.setUseCredential(true);
      spec.setSecretId("vault:1");
      // user still set alongside useCredential=true -- illegal combination
      ProviderChangeRequest change = createDatabase(ProviderChain.AUTHENTICATION, "db1", spec);
      stubEmptyAuthenticationChain(List.of());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("useCredential"));
   }

   // -------------------------------------------------------------------------
   // requiresLogin=false exempts the whole credential-mode requirement (bug 76716 review finding,
   // confirmed against the real EM dialog: database-provider-view.component.html's @if wraps the
   // ENTIRE secretId/useCredential/user/password block on requiresLogin).
   // -------------------------------------------------------------------------

   @Test void resolveAllowsDatabaseCreateWithRequiresLoginFalseAndNoCredentialFields() throws Exception {
      ProviderDatabaseSpec spec = new ProviderDatabaseSpec();
      spec.setDriver("com.mysql.cj.jdbc.Driver");
      spec.setUrl("jdbc:mysql://db1.example.com:3306/security");
      spec.setHashAlgorithm("SHA-256");
      spec.setRequiresLogin(false);
      // Deliberately no useCredential/secretId/user/password at all.
      ProviderChangeRequest change = createDatabase(ProviderChain.AUTHENTICATION, "db1", spec);
      stubEmptyAuthenticationChain(List.of());

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveDatabaseCreateStillRequiresCredentialsWhenRequiresLoginOmitted() {
      ProviderDatabaseSpec spec = new ProviderDatabaseSpec();
      spec.setDriver("com.mysql.cj.jdbc.Driver");
      spec.setUrl("jdbc:mysql://db1.example.com:3306/security");
      spec.setHashAlgorithm("SHA-256");
      // requiresLogin left null (omitted) -- must default to true, same as before this fix.
      ProviderChangeRequest change = createDatabase(ProviderChain.AUTHENTICATION, "db1", spec);
      stubEmptyAuthenticationChain(List.of());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("user"));
   }

   @Test void resolveThrowsOnCustomProviderType() {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("create");
      change.setChain("authorization");
      change.setName("p1");
      change.setProviderType("CUSTOM");
      stubProviderList(authorizationProviderService, List.of());
      assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveThrowsOnUnrecognizedProviderType() {
      ProviderChangeRequest change = createFile(ProviderChain.AUTHENTICATION, "p1");
      change.setProviderType("SAML");
      stubEmptyAuthenticationChain(List.of());
      assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveThrowsOnSpecOnFileCreate() {
      ProviderChangeRequest change = createFile(ProviderChain.AUTHENTICATION, "p1");
      change.setSpec(new ProviderLdapSpec());
      stubEmptyAuthenticationChain(List.of());
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec"));
   }

   @Test void resolveThrowsOnProviderTypeOnDelete() {
      ProviderChangeRequest change = deleteAuth("p1");
      change.setProviderType("FILE");
      stubEmptyAuthenticationChain(List.of("p1"));
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("providerType"));
   }

   @Test void resolveThrowsOnSpecOnDelete() {
      ProviderChangeRequest change = deleteAuth("p1");
      change.setSpec(ldapSpec());
      stubEmptyAuthenticationChain(List.of("p1"));
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec"));
   }

   @Test void resolveThrowsOnLdapUseCredentialWithPassword() {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("create");
      change.setChain("authentication");
      change.setName("p1");
      change.setProviderType("LDAP");
      ProviderLdapSpec spec = ldapSpec();
      spec.setUseCredential(true);
      spec.setSecretId("vault:1");
      // password still set alongside useCredential=true -- illegal combination
      change.setSpec(spec);
      stubEmptyAuthenticationChain(List.of());
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("useCredential"));
   }

   @Test void resolveThrowsOnLdapMissingAdminIdWhenNotUsingCredential() {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("create");
      change.setChain("authentication");
      change.setName("p1");
      change.setProviderType("LDAP");
      ProviderLdapSpec spec = ldapSpec();
      spec.setAdminID(null);
      spec.setPassword(null);
      change.setSpec(spec);
      stubEmptyAuthenticationChain(List.of());
      assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
   }

   // -------------------------------------------------------------------------
   // 03-reconcile.md Addition 2 -- multi-tenant LDAP gating this area self-imposes, confirmed by
   // this review (07-review-r1.md) to have had zero test coverage despite requireLdapMultiTenant
   // Allowed existing in production code: LicenseManager.isEnterprise()/SUtil.isMultiTenant() are
   // both static, so a mocked test run never previously touched this refusal branch (both statics
   // resolve to their real, effectively-false values in a bare unit-test JVM by default -- every
   // pre-existing successful LDAP-create test above was passing this gate incidentally, not because
   // it was verified).
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnLdapCreateInMultiTenantEnterpriseDeployment() {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("create");
      change.setChain("authentication");
      change.setName("p1");
      change.setProviderType("LDAP");
      change.setSpec(ldapSpec());
      stubEmptyAuthenticationChain(List.of());

      try(MockedStatic<LicenseManager> license = mockStatic(LicenseManager.class);
          MockedStatic<SUtil> sUtil = mockStatic(SUtil.class))
      {
         license.when(LicenseManager::isEnterprise).thenReturn(true);
         sUtil.when(SUtil::isMultiTenant).thenReturn(true);

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resolve(request("task", List.of(change)), user));
         assertTrue(ex.getMessage().contains("multi-tenant"));
      }
   }

   @Test void resolveAllowsLdapCreateWhenEnterpriseButNotMultiTenant() throws Exception {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("create");
      change.setChain("authentication");
      change.setName("p1");
      change.setProviderType("LDAP");
      change.setSpec(ldapSpec());
      stubEmptyAuthenticationChain(List.of());

      try(MockedStatic<LicenseManager> license = mockStatic(LicenseManager.class);
          MockedStatic<SUtil> sUtil = mockStatic(SUtil.class))
      {
         license.when(LicenseManager::isEnterprise).thenReturn(true);
         sUtil.when(SUtil::isMultiTenant).thenReturn(false);

         ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
         assertEquals(1, plan.changes().size());
      }
   }

   // -------------------------------------------------------------------------
   // identity/existence (section 2)
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnCreateNameAlreadyExists() {
      stubEmptyAuthenticationChain(List.of("p1"));
      ProviderChangeRequest change = createFile(ProviderChain.AUTHENTICATION, "p1");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveThrowsOnDeleteNameNotFound() {
      stubEmptyAuthenticationChain(List.of("other"));
      ProviderChangeRequest change = deleteAuth("p1");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("not found"));
   }

   @Test void resolveDeleteNeverCallsGetProviderByNameOnEitherService() throws Exception {
      // Two healthy (sys-admin-holding) providers so deleting one still passes both preflight
      // checks via the other.
      stubHealthyAuthenticationChainOf("p1", "p2");
      when(authenticationProviderService.getAuthenticationProvider("p1"))
         .thenReturn(fileModel("p1"));
      service.resolve(request("task", List.of(deleteAuth("p1"))), user);
      verify(authenticationProviderService, never()).getProviderByName(anyString());
      verify(authorizationProviderService, never()).getProviderByName(anyString());
   }

   // -------------------------------------------------------------------------
   // section 4 preflight -- authentication chain delete
   // -------------------------------------------------------------------------

   @Test void resolveAllowsAuthenticationDeleteWhenAnotherProviderKeepsSysAdmin() throws Exception {
      // Two providers both recognize CALLER_ROLE as sys-admin -- deleting one leaves the other.
      AuthenticationProvider keep = sysAdminProvider("keep");
      AuthenticationProvider victim = sysAdminProvider("victim");
      stubAuthenticationChain(List.of(keep, victim), List.of("keep", "victim"));
      when(authenticationProviderService.getAuthenticationProvider("victim"))
         .thenReturn(fileModel("victim"));

      ResolvedPlan plan = service.resolve(request("task", List.of(deleteAuth("victim"))), user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveRefusesAuthenticationDeleteWhenNoProviderWouldRetainSysAdminMember() throws Exception {
      // The only provider defining a sys-admin-with-a-member role is the one being deleted.
      AuthenticationProvider victim = sysAdminProvider("victim");
      AuthenticationProvider plain = plainProvider("plain");
      stubAuthenticationChain(List.of(victim, plain), List.of("victim", "plain"));
      when(authenticationProviderService.getAuthenticationProvider("victim"))
         .thenReturn(fileModel("victim"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(deleteAuth("victim"))), user));
      assertTrue(ex.getMessage().contains("no remaining provider"));
   }

   @Test void resolveRefusesAuthenticationDeleteWhenCallersOwnRoleWouldNotResolve() throws Exception {
      // The remaining provider retains SOME sys-admin (deployment-wide check 1 passes), but not one
      // that resolves the CALLING principal's own role -- check 2 must still refuse.
      IdentityID otherRole = new IdentityID("OtherAdmin", "host-org");
      AuthenticationProvider victim = sysAdminProvider("victim"); // recognizes CALLER_ROLE
      AuthenticationProvider keep = mock(AuthenticationProvider.class);
      lenient().when(keep.getProviderName()).thenReturn("keep");
      lenient().when(keep.getRoles()).thenReturn(new IdentityID[] { otherRole });
      lenient().when(keep.getRole(otherRole)).thenReturn(mock(Role.class));
      lenient().when(keep.getRole(CALLER_ROLE)).thenReturn(null);
      lenient().when(keep.isSystemAdministratorRole(otherRole)).thenReturn(true);
      lenient().when(keep.getRoleMembers(otherRole)).thenReturn(new Identity[] { mock(Identity.class) });

      stubAuthenticationChain(List.of(victim, keep), List.of("victim", "keep"));
      when(authenticationProviderService.getAuthenticationProvider("victim"))
         .thenReturn(fileModel("victim"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(deleteAuth("victim"))), user));
      assertTrue(ex.getMessage().contains("lock the calling session out"));
   }

   @Test void resolveShouldAllowAuthenticationDeleteWhenCallerResolvesViaLiveStoreRoles() throws Exception {
      // bug 76860 (docs/teams/2026-09-21-bug-76860-provider-delete-preflight-deep/02-root-cause.md):
      // callerRetainsSysAdmin (ProviderChangePlanService.java:1040-1054) only reproduces
      // OrganizationManager.isSiteAdmin's branch 1 -- a direct JWT-baked role match via getRole().
      // It does not reproduce branch 3 (provider.getRoles(userIdentity) + getAllRoles() +
      // isSystemAdministratorRole, OrganizationManager.java:95-100). Here the single surviving
      // provider ("Primary", matching the reporter's own single-provider post-delete chain)
      // deliberately does NOT recognize the caller's JWT-baked CALLER_ROLE via getRole() (branch 1
      // fails, by construction), but DOES resolve the caller to system-administrator via the
      // per-user getRoles(IdentityID) overload (branch 3 would succeed). This currently fails --
      // the current code wrongly refuses this delete. It must pass once the fix adds an additive
      // branch-3 reproduction alongside the existing branch-1 check.
      IdentityID callerUser = new IdentityID("caller", "host-org");
      IdentityID sysAdminRole = new IdentityID("SysAdminRole", "host-org");
      lenient().when(user.getName()).thenReturn(callerUser.convertToKey());

      AuthenticationProvider primary = mock(AuthenticationProvider.class);
      lenient().when(primary.getProviderName()).thenReturn("Primary");
      // check 1 (providerHasSysAdmins, deployment-wide floor): satisfied via sysAdminRole.
      lenient().when(primary.getRoles()).thenReturn(new IdentityID[] { sysAdminRole });
      lenient().when(primary.isSystemAdministratorRole(sysAdminRole)).thenReturn(true);
      lenient().when(primary.getRoleMembers(sysAdminRole))
         .thenReturn(new Identity[] { mock(Identity.class) });
      // branch 1 (the only branch the current code reproduces): the caller's JWT-baked role is not
      // recognized by this provider at all.
      lenient().when(primary.getRole(CALLER_ROLE)).thenReturn(null);
      lenient().when(primary.isSystemAdministratorRole(CALLER_ROLE)).thenReturn(false);
      // branch 3 (not yet implemented by callerRetainsSysAdmin): the caller's own USER identity
      // resolves directly to the system-administrator role via the per-user getRoles(IdentityID)
      // overload -- this is the live-store path branches 2/3 of isSiteAdmin(Principal) consult and
      // the current preflight never does.
      lenient().when(primary.getRoles(callerUser)).thenReturn(new IdentityID[] { sysAdminRole });
      lenient().when(primary.getRole(sysAdminRole)).thenReturn(new Role(sysAdminRole));

      // A freshly-created, role-less provider being deleted (bug 76860's own repro shape) -- never
      // queried beyond getProviderName() once simulatedAuthenticationProviders() filters it out.
      AuthenticationProvider probe = mock(AuthenticationProvider.class);
      lenient().when(probe.getProviderName()).thenReturn("A-BS-08-KeyVault-DB-Probe");

      stubAuthenticationChain(List.of(primary, probe),
                              List.of("Primary", "A-BS-08-KeyVault-DB-Probe"));
      when(authenticationProviderService.getAuthenticationProvider("A-BS-08-KeyVault-DB-Probe"))
         .thenReturn(dbModel("A-BS-08-KeyVault-DB-Probe"));

      ResolvedPlan plan = service.resolve(
         request("task", List.of(deleteAuth("A-BS-08-KeyVault-DB-Probe"))), user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveRefusesAuthenticationDeleteWhenNaiveUnionWouldWronglyAllow() throws Exception {
      // bug 76860 fix-correctness regression (02-root-cause.md rows 4-5): the fix's branch-2/3
      // reproduction must dispatch getRoles(IdentityID) chain-order-aware, first-non-empty-provider-
      // wins -- exactly like the real AuthenticationChain.getRoles(IdentityID) -- not as a naive
      // per-provider-independent union. Provider "first" is queried first (chain order) and returns
      // a non-empty but non-admin result for the caller's user identity; provider "second" would ALSO
      // resolve the caller to system-administrator via getRoles(IdentityID), but a chain-faithful
      // dispatch never reaches it, because "first" already returned non-empty. A naive per-provider
      // union (try each independently, OR the results) would wrongly ALLOW this delete -- the
      // dangerous direction, since the caller would actually be locked out post-delete. Branch 1 is
      // deliberately made to fail on both providers so this test isolates branch 2/3's own dispatch.
      IdentityID callerUser = new IdentityID("caller", "host-org");
      IdentityID nonAdminRole = new IdentityID("NonAdminRole", "host-org");
      IdentityID otherSysAdminRole = new IdentityID("OtherSysAdminRole", "host-org");
      lenient().when(user.getName()).thenReturn(callerUser.convertToKey());

      AuthenticationProvider first = mock(AuthenticationProvider.class);
      lenient().when(first.getProviderName()).thenReturn("first");
      lenient().when(first.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(first.getRole(CALLER_ROLE)).thenReturn(null); // branch 1 fails
      lenient().when(first.isSystemAdministratorRole(CALLER_ROLE)).thenReturn(false);
      lenient().when(first.getRoles(callerUser)).thenReturn(new IdentityID[] { nonAdminRole });
      lenient().when(first.getRole(nonAdminRole)).thenReturn(new Role(nonAdminRole));
      lenient().when(first.isSystemAdministratorRole(nonAdminRole)).thenReturn(false);

      AuthenticationProvider second = mock(AuthenticationProvider.class);
      lenient().when(second.getProviderName()).thenReturn("second");
      lenient().when(second.getRole(CALLER_ROLE)).thenReturn(null); // branch 1 fails
      lenient().when(second.isSystemAdministratorRole(CALLER_ROLE)).thenReturn(false);
      // check 1 (providerHasSysAdmins, deployment-wide floor): satisfied via otherSysAdminRole,
      // independent of the per-user getRoles(IdentityID) dispatch this test is really about.
      lenient().when(second.getRoles()).thenReturn(new IdentityID[] { otherSysAdminRole });
      lenient().when(second.isSystemAdministratorRole(otherSysAdminRole)).thenReturn(true);
      lenient().when(second.getRoleMembers(otherSysAdminRole))
         .thenReturn(new Identity[] { mock(Identity.class) });
      // "second" WOULD resolve the caller to system-administrator via getRoles(IdentityID) -- but a
      // chain-faithful dispatch must never reach this, since "first" already answered non-empty.
      lenient().when(second.getRoles(callerUser)).thenReturn(new IdentityID[] { otherSysAdminRole });
      lenient().when(second.getRole(otherSysAdminRole)).thenReturn(new Role(otherSysAdminRole));

      AuthenticationProvider victim = mock(AuthenticationProvider.class);
      lenient().when(victim.getProviderName()).thenReturn("victim");

      stubAuthenticationChain(List.of(first, second, victim), List.of("first", "second", "victim"));
      when(authenticationProviderService.getAuthenticationProvider("victim")).thenReturn(fileModel("victim"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(deleteAuth("victim"))), user));
      assertTrue(ex.getMessage().contains("lock the calling session out"));
   }

   @Test void resolveAllowsAuthenticationDeleteWhenSysAdminParentRoleLivesOnAnotherProvider() throws Exception {
      // bug 76860 fix-correctness regression (02-root-cause.md row 6): the fix's branch-2/3 parent-
      // role expansion (AuthenticationProvider.getAllRoles's default BFS, which resolves getRole()
      // against "this") must resolve getRole chain-wide across simulated, not per-provider-isolated.
      // The caller's direct role is known only to provider "a"; that role's PARENT role -- the one
      // actually flagged system-administrator -- is known only to a different provider, "b". A
      // per-provider-isolated walk (each provider expanding only against its own getRole()) would
      // never discover the parent (provider "a" doesn't know about it), and would wrongly REFUSE.
      IdentityID callerUser = new IdentityID("caller", "host-org");
      IdentityID directRole = new IdentityID("DirectRole", "host-org");
      IdentityID parentSysAdminRole = new IdentityID("ParentSysAdminRole", "host-org");
      lenient().when(user.getName()).thenReturn(callerUser.convertToKey());

      AuthenticationProvider a = mock(AuthenticationProvider.class);
      lenient().when(a.getProviderName()).thenReturn("a");
      lenient().when(a.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(a.getRole(CALLER_ROLE)).thenReturn(null); // branch 1 fails
      lenient().when(a.isSystemAdministratorRole(CALLER_ROLE)).thenReturn(false);
      lenient().when(a.getRoles(callerUser)).thenReturn(new IdentityID[] { directRole });
      lenient().when(a.getRole(directRole)).thenReturn(new Role(directRole, new IdentityID[] { parentSysAdminRole }));
      lenient().when(a.isSystemAdministratorRole(directRole)).thenReturn(false);
      lenient().when(a.getRole(parentSysAdminRole)).thenReturn(null); // "a" does not know this role

      AuthenticationProvider b = mock(AuthenticationProvider.class);
      lenient().when(b.getProviderName()).thenReturn("b");
      lenient().when(b.getRole(CALLER_ROLE)).thenReturn(null); // branch 1 fails
      lenient().when(b.isSystemAdministratorRole(CALLER_ROLE)).thenReturn(false);
      // check 1 (providerHasSysAdmins, deployment-wide floor): satisfied via parentSysAdminRole.
      lenient().when(b.getRoles()).thenReturn(new IdentityID[] { parentSysAdminRole });
      lenient().when(b.getRole(parentSysAdminRole)).thenReturn(new Role(parentSysAdminRole));
      lenient().when(b.isSystemAdministratorRole(parentSysAdminRole)).thenReturn(true);
      lenient().when(b.getRoleMembers(parentSysAdminRole))
         .thenReturn(new Identity[] { mock(Identity.class) });

      AuthenticationProvider victim = mock(AuthenticationProvider.class);
      lenient().when(victim.getProviderName()).thenReturn("victim");

      stubAuthenticationChain(List.of(a, b, victim), List.of("a", "b", "victim"));
      when(authenticationProviderService.getAuthenticationProvider("victim")).thenReturn(fileModel("victim"));

      ResolvedPlan plan = service.resolve(request("task", List.of(deleteAuth("victim"))), user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveAllowsAuthenticationDeleteWhenCallerResolvesOnlyViaBranchOne() throws Exception {
      // bug 76860 fix-correctness regression: confirms the additive OR still works when branch 2/3
      // is silent -- the VirtualAuthenticationProvider shape (00-map.md): a provider that resolves
      // the caller correctly via getRole()/isSystemAdministratorRole() (branch 1) while relying on
      // getRoles(IdentityID)'s no-op-default-shaped silence (unstubbed here, so Mockito's own default
      // answer for an array-returning method -- an empty array -- stands in for it). Must still ALLOW.
      IdentityID callerUser = new IdentityID("caller", "host-org");
      lenient().when(user.getName()).thenReturn(callerUser.convertToKey());

      AuthenticationProvider keep = sysAdminProvider("keep"); // resolves CALLER_ROLE via branch 1
      // getRoles(callerUser) intentionally left unstubbed -- Mockito's default answer for it is []).
      AuthenticationProvider victim = mock(AuthenticationProvider.class);
      lenient().when(victim.getProviderName()).thenReturn("victim");

      stubAuthenticationChain(List.of(keep, victim), List.of("keep", "victim"));
      when(authenticationProviderService.getAuthenticationProvider("victim")).thenReturn(fileModel("victim"));

      ResolvedPlan plan = service.resolve(request("task", List.of(deleteAuth("victim"))), user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveAllowsDeleteOfDatabaseTypedAuthenticationProvider() throws Exception {
      // bug 76716: DATABASE delete's own rollback-recreate risk is no longer excluded once create
      // supports it -- the same checkProviderTypeLicensed gate that guards a genuine new DATABASE
      // creation already guards a rollback-recreate too (addAuthenticationProvider is the single
      // entry point both go through), so this area's own delete-target restriction no longer needs
      // to exclude DATABASE independently.
      stubHealthyAuthenticationChainOf("keep", "victim");
      when(authenticationProviderService.getAuthenticationProvider("victim")).thenReturn(dbModel("victim"));

      ResolvedPlan plan = service.resolve(request("task", List.of(deleteAuth("victim"))), user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveRefusesDeleteOfCustomTypedAuthenticationProvider() throws Exception {
      stubHealthyAuthenticationChainOf("victim");
      AuthenticationProviderModel customModel = AuthenticationProviderModel.builder()
         .providerName("victim").providerType(SecurityProviderType.CUSTOM).build();
      when(authenticationProviderService.getAuthenticationProvider("victim")).thenReturn(customModel);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(deleteAuth("victim"))), user));
      assertTrue(ex.getMessage().contains("CUSTOM"));
   }

   // -------------------------------------------------------------------------
   // section 4 preflight -- authorization chain delete (floor only)
   // -------------------------------------------------------------------------

   @Test void resolveAllowsAuthorizationDeleteWhenAnotherProviderRemains() throws Exception {
      AuthorizationProvider keep = mock(AuthorizationProvider.class);
      lenient().when(keep.getProviderName()).thenReturn("keep");
      AuthorizationProvider victim = mock(AuthorizationProvider.class);
      lenient().when(victim.getProviderName()).thenReturn("victim");
      AuthorizationChain chain = mock(AuthorizationChain.class);
      when(chain.getProviders()).thenReturn(List.of(keep, victim));
      when(securityEngine.getAuthorizationChain()).thenReturn(Optional.of(chain));
      stubProviderList(authorizationProviderService, List.of("keep", "victim"));
      when(authorizationProviderService.getAuthorizationProvider("victim"))
         .thenReturn(authzFileModel("victim"));

      ResolvedPlan plan = service.resolve(request("task", List.of(deleteAuthz("victim"))), user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveRefusesAuthorizationDeleteThatWouldEmptyTheChain() throws Exception {
      AuthorizationProvider victim = mock(AuthorizationProvider.class);
      lenient().when(victim.getProviderName()).thenReturn("victim");
      AuthorizationChain chain = mock(AuthorizationChain.class);
      when(chain.getProviders()).thenReturn(List.of(victim));
      when(securityEngine.getAuthorizationChain()).thenReturn(Optional.of(chain));
      stubProviderList(authorizationProviderService, List.of("victim"));
      when(authorizationProviderService.getAuthorizationProvider("victim"))
         .thenReturn(authzFileModel("victim"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(deleteAuthz("victim"))), user));
      assertTrue(ex.getMessage().contains("zero remaining"));
   }

   // -------------------------------------------------------------------------
   // update verb (bug 76686)
   // -------------------------------------------------------------------------

   @Test void resolveUpdateMergesPartialSpecPreservingOtherFieldsAndSucceeds() throws Exception {
      stubHealthyAuthenticationChainOf("p1");
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(ldapModel("p1"));
      AuthenticationProvider proposedProvider = sysAdminProvider("p1");
      when(authenticationProviderService.buildProviderForPreflightSimulation(any()))
         .thenReturn(Optional.of(proposedProvider));

      ProviderLdapSpec patch = new ProviderLdapSpec();
      patch.setHostName("rotated-host.example.com");

      ResolvedPlan plan = service.resolve(request("update", List.of(updateAuth("p1", patch))), user);

      String proposed = plan.changes().get(0).proposedValue();
      assertTrue(proposed.contains("hostName=rotated-host.example.com;"));
      assertTrue(proposed.contains("rootDN=dc=example,dc=com;")); // untouched field carried over
      assertTrue(proposed.contains("adminID=cn=admin;")); // untouched field carried over
      verify(proposedProvider).tearDown();
   }

   @Test void resolveUpdateRejectsChainAuthorization() {
      stubProviderList(authorizationProviderService, List.of("z1"));
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("update");
      change.setChain("authorization");
      change.setName("z1");
      change.setSpec(ldapSpec());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("authorization"));
   }

   @Test void resolveUpdateRejectsProviderType() {
      stubHealthyAuthenticationChainOf("p1");
      ProviderChangeRequest change = updateAuth("p1", ldapSpec());
      change.setProviderType("LDAP");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("providerType"));
   }

   @Test void resolveUpdateRejectsNewName() {
      stubHealthyAuthenticationChainOf("p1");
      ProviderChangeRequest change = updateAuth("p1", ldapSpec());
      change.setNewName("p1-renamed");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("newName"));
   }

   @Test void resolveUpdateRejectsEmptySpec() {
      stubHealthyAuthenticationChainOf("p1");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateAuth("p1", null))), user));
      assertTrue(ex.getMessage().contains("spec"));
   }

   @Test void resolveUpdateRejectsNonLdapCurrentType() throws Exception {
      stubHealthyAuthenticationChainOf("p1");
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(fileModel("p1"));
      ProviderLdapSpec patch = new ProviderLdapSpec();
      patch.setHostName("new-host");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateAuth("p1", patch))), user));
      assertTrue(ex.getMessage().contains("LDAP"));
   }

   @Test void resolveUpdateRejectsPasswordOnlyChangeWhenCurrentUsesCredential() throws Exception {
      stubHealthyAuthenticationChainOf("p1");
      AuthenticationProviderModel current = AuthenticationProviderModel.builder()
         .providerName("p1").providerType(SecurityProviderType.LDAP)
         .ldapProviderModel(LdapAuthenticationProviderModel.builder()
            .ldapServer(SecurityProviderType.GENERIC).protocol("ldap").hostName("ldap.example.com")
            .hostPort(389).rootDN("dc=example,dc=com").useCredential(true).secretId("vault:1")
            .build())
         .build();
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(current);

      ProviderLdapSpec patch = new ProviderLdapSpec();
      patch.setPassword("rotated-password"); // belongs to the other (non-credential) mode

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateAuth("p1", patch))), user));
      assertTrue(ex.getMessage().contains("useCredential"));
   }

   // -------------------------------------------------------------------------
   // update verb, DATABASE target (bug 76716) -- no license check needed here at all:
   // checkProviderTypeLicensed's introducingUnlicensedType is always false for update (it never
   // changes a provider's type), so editing an already-existing DATABASE provider's other fields
   // is the grandfathered case bug 76359 deliberately leaves unlicensed-safe.
   // -------------------------------------------------------------------------

   @Test void resolveUpdateMergesPartialDatabaseSpecPreservingOtherFieldsAndSucceeds() throws Exception {
      stubHealthyAuthenticationChainOf("db1");
      when(authenticationProviderService.getAuthenticationProvider("db1")).thenReturn(dbModel("db1"));
      AuthenticationProvider proposedProvider = sysAdminProvider("db1");
      when(authenticationProviderService.buildProviderForPreflightSimulation(any()))
         .thenReturn(Optional.of(proposedProvider));

      ProviderDatabaseSpec patch = new ProviderDatabaseSpec();
      patch.setUrl("jdbc:mysql://rotated-host.example.com:3306/security");

      ResolvedPlan plan = service.resolve(request("update", List.of(updateAuthDatabase("db1", patch))), user);

      String proposed = plan.changes().get(0).proposedValue();
      assertTrue(proposed.contains("type=DATABASE;"));
      assertTrue(proposed.contains("url=jdbc:mysql://rotated-host.example.com:3306/security;"));
      assertTrue(proposed.contains("driver=com.mysql.cj.jdbc.Driver;")); // untouched field carried over
      verify(proposedProvider).tearDown();
      verify(authenticationProviderService, never()).requireProviderTypeLicensed(any());
   }

   @Test void resolveUpdateAllowsRequiresLoginFalseEvenWithOtherwiseContradictoryCredentialFields()
      throws Exception
   {
      // bug 76716 review finding: requiresLogin=false in the update patch resolves the MERGED
      // model's own requiresLogin() to false, which must skip requireDatabaseCredentialCrossValidation
      // entirely -- proven here by deliberately sending secretId together with user/password (a
      // combination that would otherwise be refused loud) and confirming it still succeeds.
      stubHealthyAuthenticationChainOf("db1");
      when(authenticationProviderService.getAuthenticationProvider("db1")).thenReturn(dbModel("db1"));
      AuthenticationProvider proposedProvider = sysAdminProvider("db1");
      when(authenticationProviderService.buildProviderForPreflightSimulation(any()))
         .thenReturn(Optional.of(proposedProvider));

      ProviderDatabaseSpec patch = new ProviderDatabaseSpec();
      patch.setRequiresLogin(false);
      patch.setSecretId("vault:1");
      patch.setUser("would-be-contradictory");
      patch.setPassword("would-be-contradictory");

      ResolvedPlan plan = service.resolve(request("task", List.of(updateAuthDatabase("db1", patch))), user);
      assertEquals(1, plan.changes().size());
      assertTrue(plan.changes().get(0).proposedValue().contains("requiresLogin=false;"));
   }

   @Test void resolveUpdateRejectsSpecWhenCurrentProviderIsDatabase() throws Exception {
      stubHealthyAuthenticationChainOf("db1");
      when(authenticationProviderService.getAuthenticationProvider("db1")).thenReturn(dbModel("db1"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateAuth("db1", ldapSpec()))), user));
      assertTrue(ex.getMessage().contains("databaseSpec"));
   }

   @Test void resolveUpdateRejectsDatabaseSpecWhenCurrentProviderIsLdap() throws Exception {
      stubHealthyAuthenticationChainOf("p1");
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(ldapModel("p1"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateAuthDatabase("p1", databaseSpec()))), user));
      assertTrue(ex.getMessage().contains("spec"));
   }

   @Test void resolveUpdateRejectsCustomCurrentType() throws Exception {
      stubHealthyAuthenticationChainOf("p1");
      AuthenticationProviderModel custom = AuthenticationProviderModel.builder()
         .providerName("p1").providerType(SecurityProviderType.CUSTOM).build();
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(custom);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateAuthDatabase("p1", databaseSpec()))), user));
      assertTrue(ex.getMessage().contains("LDAP or DATABASE"));
   }

   @Test void resolveUpdateRejectsDatabaseSecretIdWhenMergedUseCredentialIsFalse() throws Exception {
      // Mirrors resolveUpdateRejectsPasswordOnlyChangeWhenCurrentUsesCredential's LDAP precedent,
      // inverse direction: dbModel("db1")'s stored config has useCredential=false, the patch omits
      // useCredential (so the MERGED model still resolves to false) but adds secretId -- a field
      // belonging to the other mode. This is requireDatabaseCredentialCrossValidation running
      // against the MERGED/proposed model, not a stateless pre-merge check.
      stubHealthyAuthenticationChainOf("db1");
      when(authenticationProviderService.getAuthenticationProvider("db1")).thenReturn(dbModel("db1"));

      ProviderDatabaseSpec patch = new ProviderDatabaseSpec();
      patch.setSecretId("vault:1");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateAuthDatabase("db1", patch))), user));
      assertTrue(ex.getMessage().contains("secretId"));
   }

   @Test void resolveUpdateRefusesWhenEditWouldStripCallersOwnSysAdminRole() throws Exception {
      // Single-provider chain -- editing "victim" into a config that resolves no sys-admin at all
      // must be refused by the new preflight (deployment-wide check fires first here).
      stubHealthyAuthenticationChainOf("victim");
      when(authenticationProviderService.getAuthenticationProvider("victim")).thenReturn(ldapModel("victim"));
      AuthenticationProvider proposedProvider = plainProvider("victim");
      when(authenticationProviderService.buildProviderForPreflightSimulation(any()))
         .thenReturn(Optional.of(proposedProvider));

      ProviderLdapSpec patch = new ProviderLdapSpec();
      patch.setHostName("rotated-host.example.com");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateAuth("victim", patch))), user));
      assertTrue(ex.getMessage().contains("no remaining provider"));
      verify(proposedProvider).tearDown();
   }

   // -------------------------------------------------------------------------
   // section 5 -- the whole-chain, order-sensitive hash
   // -------------------------------------------------------------------------

   @Test void hashIsStableAcrossTwoIdenticalResolutions() throws Exception {
      stubHealthyAuthenticationChainOf("p1", "p2");
      when(authenticationProviderService.getAuthenticationProvider("p2")).thenReturn(fileModel("p2"));

      ResolvedPlan first = service.resolve(request("task", List.of(deleteAuth("p2"))), user);
      ResolvedPlan second = service.resolve(request("task", List.of(deleteAuth("p2"))), user);
      assertEquals(first.planHash(), second.planHash());
   }

   @Test void hashIsUnaffectedByDifferentTaskStrings() throws Exception {
      stubHealthyAuthenticationChainOf("p1", "p2");
      when(authenticationProviderService.getAuthenticationProvider("p2")).thenReturn(fileModel("p2"));

      ResolvedPlan first = service.resolve(request("delete p2", List.of(deleteAuth("p2"))), user);
      ResolvedPlan second = service.resolve(
         request("please remove provider p2", List.of(deleteAuth("p2"))), user);
      assertEquals(first.planHash(), second.planHash());
   }

   @Test void hashChangesWhenProviderOrderChangesButMembershipDoesNot() throws Exception {
      // A create -- no preflight involved -- isolates the whole-chain projection's own order
      // sensitivity (section 5) from the delete preflight's own membership requirements.
      stubProviderList(authenticationProviderService, List.of("p1", "p2"));
      ResolvedPlan first = service.resolve(
         request("task", List.of(createFile(ProviderChain.AUTHENTICATION, "p3"))), user);

      reset(authenticationProviderService);
      stubProviderList(authenticationProviderService, List.of("p2", "p1"));
      ResolvedPlan second = service.resolve(
         request("task", List.of(createFile(ProviderChain.AUTHENTICATION, "p3"))), user);

      assertNotEquals(first.planHash(), second.planHash());
   }

   @Test void hashChangesWhenAConcurrentProviderIsAddedToTheSameChain() throws Exception {
      stubHealthyAuthenticationChainOf("p1", "p2");
      when(authenticationProviderService.getAuthenticationProvider("p2")).thenReturn(fileModel("p2"));
      ResolvedPlan first = service.resolve(request("task", List.of(deleteAuth("p2"))), user);

      reset(securityEngine, authenticationProviderService);
      stubHealthyAuthenticationChainOf("p1", "p2", "p3");
      when(authenticationProviderService.getAuthenticationProvider("p2")).thenReturn(fileModel("p2"));
      ResolvedPlan second = service.resolve(request("task", List.of(deleteAuth("p2"))), user);

      assertNotEquals(first.planHash(), second.planHash());
   }

   @Test void passwordNeverAppearsInAHashProjectionString() {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("create");
      change.setChain("authentication");
      change.setName("p1");
      change.setProviderType("LDAP");
      ProviderLdapSpec spec = ldapSpec();
      spec.setPassword("s3cr3t-literal-value");
      change.setSpec(spec);
      stubEmptyAuthenticationChain(List.of());

      ResolvedPlan plan;
      try {
         plan = service.resolve(request("task", List.of(change)), user);
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      for(PlanChange pc : plan.changes()) {
         assertFalse(String.valueOf(pc.proposedValue()).contains("s3cr3t-literal-value"));
      }

      assertFalse(plan.planHash().contains("s3cr3t-literal-value"));
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private AuthenticationProvider sysAdminProvider(String name) {
      AuthenticationProvider p = mock(AuthenticationProvider.class);
      lenient().when(p.getProviderName()).thenReturn(name);
      lenient().when(p.getRoles()).thenReturn(new IdentityID[] { CALLER_ROLE });
      lenient().when(p.getRole(CALLER_ROLE)).thenReturn(mock(Role.class));
      lenient().when(p.isSystemAdministratorRole(CALLER_ROLE)).thenReturn(true);
      lenient().when(p.getRoleMembers(CALLER_ROLE)).thenReturn(new Identity[] { mock(Identity.class) });
      return p;
   }

   private AuthenticationProvider plainProvider(String name) {
      AuthenticationProvider p = mock(AuthenticationProvider.class);
      lenient().when(p.getProviderName()).thenReturn(name);
      lenient().when(p.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(p.getRole(any())).thenReturn(null);
      return p;
   }

   private void stubAuthenticationChain(List<AuthenticationProvider> providers, List<String> names) {
      AuthenticationChain chain = mock(AuthenticationChain.class);
      lenient().when(chain.getProviders()).thenReturn(providers);
      lenient().when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(chain));
      stubProviderList(authenticationProviderService, names);
   }

   /** A two-provider chain, both recognizing {@link #CALLER_ROLE} as sys-admin, so any single
    * delete's preflight passes by default. */
   private void stubHealthyAuthenticationChainOf(String... names) {
      List<AuthenticationProvider> providers = new java.util.ArrayList<>();

      for(String name : names) {
         providers.add(sysAdminProvider(name));
      }

      stubAuthenticationChain(providers, List.of(names));
   }

   private void stubEmptyAuthenticationChain(List<String> names) {
      lenient().when(securityEngine.getAuthenticationChain()).thenReturn(Optional.empty());
      stubProviderList(authenticationProviderService, names);
   }

   private static void stubProviderList(AuthenticationProviderService svc, List<String> names) {
      SecurityProviderStatusList.Builder builder = SecurityProviderStatusList.builder();

      for(String name : names) {
         builder.addProviders(SecurityProviderStatus.builder()
            .name(name).label(name).cacheEnabled(false).cacheAge(0).loading(false).build());
      }

      lenient().when(svc.getProviderListModel()).thenReturn(builder.build());
   }

   private static void stubProviderList(AuthorizationProviderService svc, List<String> names) {
      SecurityProviderStatusList.Builder builder = SecurityProviderStatusList.builder();

      for(String name : names) {
         builder.addProviders(SecurityProviderStatus.builder()
            .name(name).label(name).cacheEnabled(false).cacheAge(0).loading(false).build());
      }

      lenient().when(svc.getProviderListModel()).thenReturn(builder.build());
   }

   private static AuthenticationProviderModel fileModel(String name) {
      return AuthenticationProviderModel.builder()
         .providerName(name).providerType(SecurityProviderType.FILE).build();
   }

   private static AuthenticationProviderModel ldapModel(String name) {
      return AuthenticationProviderModel.builder()
         .providerName(name).providerType(SecurityProviderType.LDAP)
         .ldapProviderModel(LdapAuthenticationProviderModel.builder()
            .ldapServer(SecurityProviderType.GENERIC).protocol("ldap").hostName("ldap.example.com")
            .hostPort(389).rootDN("dc=example,dc=com").adminID("cn=admin")
            .password("initial-password").build())
         .build();
   }

   private static AuthenticationProviderModel dbModel(String name) {
      return AuthenticationProviderModel.builder()
         .providerName(name).providerType(SecurityProviderType.DATABASE)
         .dbProviderModel(DatabaseAuthenticationProviderModel.builder()
            .driver("com.mysql.cj.jdbc.Driver").url("jdbc:mysql://db1.example.com:3306/security")
            .hashAlgorithm("SHA-256").requiresLogin(true).useCredential(false)
            .user("svc_auth").password("initial-password").build())
         .build();
   }

   private static ProviderDatabaseSpec databaseSpec() {
      ProviderDatabaseSpec spec = new ProviderDatabaseSpec();
      spec.setDriver("com.mysql.cj.jdbc.Driver");
      spec.setUrl("jdbc:mysql://db1.example.com:3306/security");
      spec.setHashAlgorithm("SHA-256");
      spec.setUser("svc_auth");
      spec.setPassword("initial-password");
      return spec;
   }

   private static ProviderChangeRequest createDatabase(ProviderChain chain, String name,
                                                        ProviderDatabaseSpec spec)
   {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("create");
      change.setChain(chain.label());
      change.setName(name);
      change.setProviderType("DATABASE");
      change.setDatabaseSpec(spec);
      return change;
   }

   private static ProviderChangeRequest updateAuthDatabase(String name, ProviderDatabaseSpec spec) {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("update");
      change.setChain("authentication");
      change.setName(name);
      change.setDatabaseSpec(spec);
      return change;
   }

   private static AuthorizationProviderModel authzFileModel(String name) {
      return AuthorizationProviderModel.builder()
         .providerName(name).providerType(SecurityProviderType.FILE).build();
   }

   private static ProviderLdapSpec ldapSpec() {
      ProviderLdapSpec spec = new ProviderLdapSpec();
      spec.setLdapServer("GENERIC");
      spec.setProtocol("ldap");
      spec.setHostName("ldap.example.com");
      spec.setHostPort(389);
      spec.setRootDN("dc=example,dc=com");
      spec.setAdminID("cn=admin");
      spec.setPassword("initial-password");
      return spec;
   }

   private static ProviderChangeRequest createFile(ProviderChain chain, String name) {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("create");
      change.setChain(chain.label());
      change.setName(name);
      change.setProviderType("FILE");
      return change;
   }

   private static ProviderChangeRequest duplicateAuth(String name, String newName) {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("duplicate");
      change.setChain("authentication");
      change.setName(name);
      change.setNewName(newName);
      return change;
   }

   private static ProviderChangeRequest updateAuth(String name, ProviderLdapSpec spec) {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("update");
      change.setChain("authentication");
      change.setName(name);
      change.setSpec(spec);
      return change;
   }

   private static ProviderChangeRequest deleteAuth(String name) {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("delete");
      change.setChain("authentication");
      change.setName(name);
      return change;
   }

   private static ProviderChangeRequest deleteAuthz(String name) {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("delete");
      change.setChain("authorization");
      change.setName(name);
      return change;
   }

   private static ProviderChangePlanRequest request(String task, List<ProviderChangeRequest> changes) {
      ProviderChangePlanRequest req = new ProviderChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }
}
