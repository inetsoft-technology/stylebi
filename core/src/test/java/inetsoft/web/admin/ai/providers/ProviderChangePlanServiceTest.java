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
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

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

   @BeforeEach void setUp() {
      service = new ProviderChangePlanService(authenticationProviderService,
                                              authorizationProviderService, securityEngine);
      lenient().when(user.getRoles()).thenReturn(new IdentityID[] { CALLER_ROLE });
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

   @Test void resolveThrowsOnDatabaseProviderType() {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("create");
      change.setChain("authentication");
      change.setName("p1");
      change.setProviderType("DATABASE");
      stubEmptyAuthenticationChain(List.of());
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("excluded"));
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

   @Test void resolveRefusesDeleteOfDatabaseTypedAuthenticationProvider() throws Exception {
      stubHealthyAuthenticationChainOf("victim");
      AuthenticationProviderModel dbModel = AuthenticationProviderModel.builder()
         .providerName("victim").providerType(SecurityProviderType.DATABASE).build();
      when(authenticationProviderService.getAuthenticationProvider("victim")).thenReturn(dbModel);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(deleteAuth("victim"))), user));
      assertTrue(ex.getMessage().contains("DATABASE"));
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
