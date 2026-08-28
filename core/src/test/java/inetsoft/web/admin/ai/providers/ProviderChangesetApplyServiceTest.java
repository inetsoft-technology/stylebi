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

import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 01-spec.md section 6 (apply/rollback per verb, both chains) and section 7 (the Tier-2 backup,
 * unconditional for this whole area, taken synchronously before any mutation). Backs
 * {@link AuthenticationProviderService}/{@link AuthorizationProviderService} with a small in-memory
 * fake chain (mutated by the mocked add/remove calls) so verification/rollback assertions reflect
 * real state transitions rather than a fixed stub.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ProviderChangesetApplyServiceTest {
   private static final IdentityID CALLER_ROLE = new IdentityID("Administrator", "host-org");

   @Mock private AuthenticationProviderService authenticationProviderService;
   @Mock private AuthorizationProviderService authorizationProviderService;
   @Mock private SecurityEngine securityEngine;
   @Mock private AdminBackupService backupService;
   @Mock private XPrincipal user;

   private final List<String> authChainNames = new ArrayList<>();
   private final Map<String, AuthenticationProviderModel> authModels = new HashMap<>();
   private final List<String> authzChainNames = new ArrayList<>();
   private final Map<String, AuthorizationProviderModel> authzModels = new HashMap<>();

   private ProviderChangePlanService planService;
   private ProviderChangesetApplyService service;

   @BeforeEach void setUp() throws Exception {
      planService = new ProviderChangePlanService(authenticationProviderService,
                                                  authorizationProviderService, securityEngine);
      service = new ProviderChangesetApplyService(planService, authenticationProviderService,
                                                  authorizationProviderService, backupService);

      lenient().when(user.getRoles()).thenReturn(new IdentityID[] { CALLER_ROLE });
      lenient().when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");
      wireAuthenticationFake();
      wireAuthorizationFake();
   }

   // -------------------------------------------------------------------------
   // success
   // -------------------------------------------------------------------------

   @Test void appliesACreateFileAuthenticationProviderAndReportsApplied() throws Exception {
      var result = service.apply(applyRequest("create p1", createFile(ProviderChain.AUTHENTICATION, "p1")),
                                 user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("admin-snapshot/ref", result.backupRef());
      assertNull(result.rollbackFailures());
      assertEquals(1, result.results().size());
      assertEquals(AdminChangeRecord.STATUS_VERIFIED, result.results().get(0).status());
      assertTrue(authChainNames.contains("p1"));
      verify(backupService).backup(anyString());
   }

   @Test void appliesACreateFileAuthorizationProviderAndReportsApplied() throws Exception {
      var result = service.apply(
         applyRequest("create p1", createFile(ProviderChain.AUTHORIZATION, "p1")), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertTrue(authzChainNames.contains("p1"));
   }

   @Test void appliesADeleteAuthenticationProviderAndReportsAppliedWithNoAdvisory() throws Exception {
      seedHealthyAuthentication("keep", "victim");

      var result = service.apply(applyRequest("delete victim", deleteAuth("victim")), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(authChainNames.contains("victim"));
      assertNull(result.results().get(0).advisory());
   }

   @Test void appliesAnLdapAuthenticationCreateBuildingTheModelFromSpec() throws Exception {
      ProviderChangeRequest change = new ProviderChangeRequest();
      change.setVerb("create");
      change.setChain("authentication");
      change.setName("ldap1");
      change.setProviderType("LDAP");
      change.setSpec(ldapSpec());

      var result = service.apply(applyRequest("create ldap1", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals(SecurityProviderType.LDAP, authModels.get("ldap1").providerType());
   }

   @Test void nChangePlanSpanningBothChainsAppliesAllAndReportsTwoOutcomes() throws Exception {
      var result = service.apply(applyRequest("create both",
         createFile(ProviderChain.AUTHENTICATION, "a1"), createFile(ProviderChain.AUTHORIZATION, "z1")),
         user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals(2, result.results().size());
      assertTrue(authChainNames.contains("a1"));
      assertTrue(authzChainNames.contains("z1"));
   }

   // -------------------------------------------------------------------------
   // throw mid-apply -> rollback / rollback-failed (section 6, "fails by throwing")
   // -------------------------------------------------------------------------

   @Test void throwMidApplyRollsBackEarlierChangeButReportsRollbackFailedForTheUnknownState()
      throws Exception
   {
      // change 2's own add throws -- its state is unknown and must never be reported as rolled
      // back, even though change 1's own rollback succeeds (section 6/2.5 of the guide).
      doThrow(new RuntimeException("simulated race: duplicate name"))
         .when(authenticationProviderService)
         .addAuthenticationProvider(argThat(m -> m != null && "p2".equals(m.providerName())),
                                    eq("p2"), eq(user));

      var result = service.apply(applyRequest("create p1 then p2",
         createFile(ProviderChain.AUTHENTICATION, "p1"), createFile(ProviderChain.AUTHENTICATION, "p2")),
         user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertTrue(result.rollbackFailures().stream()
         .anyMatch(f -> f.property().contains("p2") && f.error().contains("state unknown")));
      // change 1 was still rolled back correctly, disclosed via its own outcome/state, even though
      // the OVERALL status is rollback-failed because of change 2's unknown state.
      assertFalse(authChainNames.contains("p1"));
   }

   @Test void undoRunsNewestFirst() throws Exception {
      doThrow(new RuntimeException("boom"))
         .when(authenticationProviderService)
         .addAuthenticationProvider(argThat(m -> m != null && "p3".equals(m.providerName())),
                                    eq("p3"), eq(user));

      service.apply(applyRequest("create p1, p2, p3",
         createFile(ProviderChain.AUTHENTICATION, "p1"), createFile(ProviderChain.AUTHENTICATION, "p2"),
         createFile(ProviderChain.AUTHENTICATION, "p3")), user);

      InOrder inOrder = inOrder(authenticationProviderService);
      inOrder.verify(authenticationProviderService).removeAuthenticationProvider(anyInt(), eq("p2"),
                                                                                 eq(user));
      inOrder.verify(authenticationProviderService).removeAuthenticationProvider(anyInt(), eq("p1"),
                                                                                 eq(user));
   }

   @Test void anUndoThatItselfFailsIsReportedAlongsideTheUnknownStateFailure() throws Exception {
      doThrow(new RuntimeException("boom"))
         .when(authenticationProviderService)
         .addAuthenticationProvider(argThat(m -> m != null && "p2".equals(m.providerName())),
                                    eq("p2"), eq(user));
      doThrow(new RuntimeException("rollback also fails"))
         .when(authenticationProviderService)
         .removeAuthenticationProvider(anyInt(), eq("p1"), eq(user));

      var result = service.apply(applyRequest("create p1 then p2",
         createFile(ProviderChain.AUTHENTICATION, "p1"), createFile(ProviderChain.AUTHENTICATION, "p2")),
         user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      Set<String> failedKeys = result.rollbackFailures().stream()
         .map(f -> f.property()).collect(Collectors.toSet());
      assertTrue(failedKeys.stream().anyMatch(k -> k.contains("p1")));
      assertTrue(failedKeys.stream().anyMatch(k -> k.contains("p2")));
   }

   // -------------------------------------------------------------------------
   // delete-rollback's mandatory "restored at end, not original position" advisory
   // -------------------------------------------------------------------------

   @Test void deleteRollbackDisclosesTheRestoredAtEndAdvisoryAsAFirstClassField() throws Exception {
      seedHealthyAuthentication("keep", "victim");
      doThrow(new RuntimeException("boom"))
         .when(authenticationProviderService)
         .addAuthenticationProvider(argThat(m -> m != null && "boom".equals(m.providerName())),
                                    eq("boom"), eq(user));

      var result = service.apply(applyRequest("delete victim then create boom",
         deleteAuth("victim"), createFile(ProviderChain.AUTHENTICATION, "boom")), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      var victimOutcome = result.results().stream()
         .filter(o -> o.property().contains("victim")).findFirst().orElseThrow();
      assertNotNull(victimOutcome.advisory());
      assertTrue(victimOutcome.advisory().contains("END of the chain"));
      assertTrue(authChainNames.contains("victim")); // recreated by rollback
   }

   // -------------------------------------------------------------------------
   // index resolved fresh at apply time, never a preview-captured index (section 1/6)
   // -------------------------------------------------------------------------

   @Test void deleteResolvesIndexFreshByNameNotAPreviewCapturedIndex() throws Exception {
      seedHealthyAuthentication("x", "y", "victim"); // victim is at index 2, not 0

      service.apply(applyRequest("delete victim", deleteAuth("victim")), user);

      verify(authenticationProviderService).removeAuthenticationProvider(eq(2), eq("victim"), eq(user));
   }

   // -------------------------------------------------------------------------
   // hash / reviewOutcome gates (shared machinery, still worth one assertion per area)
   // -------------------------------------------------------------------------

   @Test void applyRefusesAStalePlanHash() throws Exception {
      ProviderApplyRequest req = applyRequest("create p1", createFile(ProviderChain.AUTHENTICATION, "p1"));
      req.setPlanHash("not-the-real-hash");

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, user));
      assertTrue(authChainNames.isEmpty());
   }

   @Test void applyRefusesAMissingReviewOutcome() throws Exception {
      ProviderChangePlanRequest planReq = request("create p1",
         List.of(createFile(ProviderChain.AUTHENTICATION, "p1")));
      ResolvedPlan plan = planService.resolve(planReq, user);
      ProviderApplyRequest req = applyRequest("create p1", createFile(ProviderChain.AUTHENTICATION, "p1"));
      req.setPlanHash(plan.planHash());
      req.setReviewOutcome("  ");

      assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(authChainNames.isEmpty());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private void seedHealthyAuthentication(String... names) {
      for(String name : names) {
         authChainNames.add(name);
         authModels.put(name, AuthenticationProviderModel.builder()
            .providerName(name).providerType(SecurityProviderType.FILE).build());
      }
   }

   private AuthenticationProvider sysAdminProvider(String name) {
      AuthenticationProvider p = mock(AuthenticationProvider.class);
      lenient().when(p.getProviderName()).thenReturn(name);
      lenient().when(p.getRoles()).thenReturn(new IdentityID[] { CALLER_ROLE });
      lenient().when(p.getRole(CALLER_ROLE)).thenReturn(mock(Role.class));
      lenient().when(p.isSystemAdministratorRole(CALLER_ROLE)).thenReturn(true);
      lenient().when(p.getRoleMembers(CALLER_ROLE)).thenReturn(new Identity[] { mock(Identity.class) });
      return p;
   }

   private void wireAuthenticationFake() throws Exception {
      lenient().when(authenticationProviderService.getProviderListModel()).thenAnswer(inv -> {
         SecurityProviderStatusList.Builder b = SecurityProviderStatusList.builder();

         for(String n : authChainNames) {
            b.addProviders(SecurityProviderStatus.builder()
               .name(n).label(n).cacheEnabled(false).cacheAge(0).loading(false).build());
         }

         return b.build();
      });
      lenient().when(authenticationProviderService.getAuthenticationProvider(anyString()))
         .thenAnswer(inv -> authModels.get((String) inv.getArgument(0)));
      lenient().doAnswer(inv -> {
         AuthenticationProviderModel model = inv.getArgument(0);
         authChainNames.add(model.providerName());
         authModels.put(model.providerName(), model);
         return null;
      }).when(authenticationProviderService).addAuthenticationProvider(any(), anyString(), any());
      lenient().doAnswer(inv -> {
         int idx = inv.getArgument(0);
         String name = authChainNames.remove(idx);
         authModels.remove(name);
         return null;
      }).when(authenticationProviderService).removeAuthenticationProvider(anyInt(), anyString(), any());

      AuthenticationChain chain = mock(AuthenticationChain.class);
      lenient().when(chain.getProviders()).thenAnswer(inv ->
         authChainNames.stream().map(this::sysAdminProvider).collect(Collectors.toList()));
      lenient().when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(chain));
   }

   private void wireAuthorizationFake() throws Exception {
      lenient().when(authorizationProviderService.getProviderListModel()).thenAnswer(inv -> {
         SecurityProviderStatusList.Builder b = SecurityProviderStatusList.builder();

         for(String n : authzChainNames) {
            b.addProviders(SecurityProviderStatus.builder()
               .name(n).label(n).cacheEnabled(false).cacheAge(0).loading(false).build());
         }

         return b.build();
      });
      lenient().when(authorizationProviderService.getAuthorizationProvider(anyString()))
         .thenAnswer(inv -> authzModels.get((String) inv.getArgument(0)));
      lenient().doAnswer(inv -> {
         AuthorizationProviderModel model = inv.getArgument(0);
         authzChainNames.add(model.providerName());
         authzModels.put(model.providerName(), model);
         return null;
      }).when(authorizationProviderService).addAuthorizationProvider(any(), anyString(), any());
      lenient().doAnswer(inv -> {
         int idx = inv.getArgument(0);
         String name = authzChainNames.remove(idx);
         authzModels.remove(name);
         return null;
      }).when(authorizationProviderService).removeAuthorizationProvider(anyInt(), anyString(), any());

      AuthorizationChain chain = mock(AuthorizationChain.class);
      lenient().when(chain.getProviders()).thenAnswer(inv ->
         authzChainNames.stream().map(n -> {
            AuthorizationProvider p = mock(AuthorizationProvider.class);
            lenient().when(p.getProviderName()).thenReturn(n);
            return p;
         }).collect(Collectors.toList()));
      lenient().when(securityEngine.getAuthorizationChain()).thenReturn(Optional.of(chain));
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

   private static ProviderChangePlanRequest request(String task, List<ProviderChangeRequest> changes) {
      ProviderChangePlanRequest req = new ProviderChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private ProviderApplyRequest applyRequest(String task, ProviderChangeRequest... changes)
      throws Exception
   {
      List<ProviderChangeRequest> list = List.of(changes);
      ResolvedPlan plan = planService.resolve(request(task, list), user);
      ProviderApplyRequest req = new ProviderApplyRequest();
      req.setTask(task);
      req.setChanges(list);
      req.setPlanHash(plan.planHash());
      req.setReviewOutcome("looks safe");
      return req;
   }
}
