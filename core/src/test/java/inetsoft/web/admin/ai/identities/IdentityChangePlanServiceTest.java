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
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.security.auth.MissingResourceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Spec section 1 (scope/field-set validation, the discriminator-confusion defense), section 2
 * (identity/existence), section 4a (default-org/self-org refusal, this track's own net-new
 * authorization logic), and section 5 (hash).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class IdentityChangePlanServiceTest {
   @Mock private SecurityService securityService;
   @Mock private SecurityEngine securityEngine;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   @Mock private EditableAuthenticationProvider editableAuthentication;
   @Mock private AuthorizationProvider authorizationProvider;
   private IdentityChangePlanService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Tool> tool;

   @BeforeEach void setUp() {
      service = new IdentityChangePlanService(securityService, securityEngine);

      // resolve() refuses outright without a writable provider. Built before the stub because the
      // constructor registers a listener on the mock, which Mockito rejects mid-stubbing.
      SecurityProvider writable =
         new TestSecurityProvider(editableAuthentication, authorizationProvider);
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(writable);
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");

      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      tool.close();
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      IdentityChangePlanRequest req = request("  ", List.of(deleteUser("bob")));
      assertTrue(assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user))
                    .getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      IdentityChangePlanRequest req = request("task", List.of());
      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
   }

   @Test void resolveThrowsOnUnrecognizedVerb() {
      IdentityChangeRequest change = deleteUser("bob");
      change.setVerb("rename");
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user)).getMessage().contains("verb"));
   }

   @Test void resolveThrowsOnUnrecognizedUnitType() {
      IdentityChangeRequest change = deleteUser("bob");
      change.setUnitType("robot");
      assertTrue(assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user))
            .getMessage().contains("unitType"));
   }

   @Test void resolveThrowsOnDuplicateEntries() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      IdentityChangeRequest c1 = createUser("bob");
      IdentityChangeRequest c2 = createUser("bob");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(c1, c2)), user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   // -------------------------------------------------------------------------
   // discriminator-confusion defense (spec section 11)
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnGroupFieldOnUserSpec() {
      IdentityChangeRequest change = createUser("bob");
      change.getSpec().setParentGroups(List.of("Admins"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("parentGroups"));
      assertTrue(ex.getMessage().contains("group"));
      verifyNoInteractions(securityService);
   }

   @Test void resolveThrowsOnUserFieldOnRoleSpec() {
      IdentityChangeRequest change = createRole("Analyst");
      change.getSpec().setPassword("Password1!");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("password"));
   }

   @Test void resolveThrowsOnNameFieldOnOrganizationSpec() {
      IdentityChangeRequest change = createOrganization("org1", "Org One");
      change.getSpec().setName("bob");

      assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
   }

   // bug-76715 assertion 5: defaultRole/sysAdmin/properties must be refused loud, naming the field
   // and suggesting the correct unitType, if sent on a unitType they don't belong to.

   @Test void resolveThrowsOnDefaultRoleFieldOnUserSpec() {
      IdentityChangeRequest change = createUser("bob");
      change.getSpec().setDefaultRole(true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("defaultRole"));
      assertTrue(ex.getMessage().contains("role"));
   }

   @Test void resolveThrowsOnSysAdminFieldOnUserSpec() {
      IdentityChangeRequest change = createUser("bob");
      change.getSpec().setSysAdmin(true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("sysAdmin"));
      assertTrue(ex.getMessage().contains("role"));
   }

   @Test void resolveThrowsOnPropertiesFieldOnRoleSpec() {
      IdentityChangeRequest change = createRole("Analyst");
      change.getSpec().setProperties(List.of(PropertyModel.builder().name("k").value("v").build()));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("properties"));
      assertTrue(ex.getMessage().contains("organization"));
   }

   @Test void resolveAllowsDefaultRoleAndSysAdminOnRoleCreate() throws Exception {
      when(securityService.getRole(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such role"));
      IdentityChangeRequest change = createRole("Analyst");
      change.getSpec().setDefaultRole(true);
      change.getSpec().setSysAdmin(false);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      assertEquals(1, plan.changes().size());
   }

   // bug-76824: orgAdmin gets the same field-ownership treatment as defaultRole/sysAdmin above.

   @Test void resolveThrowsOnOrgAdminFieldOnUserSpec() {
      IdentityChangeRequest change = createUser("bob");
      change.getSpec().setOrgAdmin(true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("orgAdmin"));
      assertTrue(ex.getMessage().contains("role"));
   }

   @Test void resolveAllowsOrgAdminOnRoleCreate() throws Exception {
      when(securityService.getRole(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such role"));
      IdentityChangeRequest change = createRole("Org Admin Role");
      change.getSpec().setOrgAdmin(true);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveRefusesRoleCreateThatIsBothSysAdminAndOrgAdmin() throws Exception {
      when(securityService.getRole(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such role"));
      IdentityChangeRequest change = createRole("Both Role");
      change.getSpec().setSysAdmin(true);
      change.getSpec().setOrgAdmin(true);

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(e.getMessage().contains("spec.sysAdmin"));
      assertTrue(e.getMessage().contains("spec.orgAdmin"));
   }

   @Test void resolveAllowsRoleCreateWithSysAdminAlone() throws Exception {
      when(securityService.getRole(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such role"));
      IdentityChangeRequest change = createRole("Sys Role");
      change.getSpec().setSysAdmin(true);

      assertEquals(1, service.resolve(request("task", List.of(change)), user).changes().size());
   }

   private SecurityRole roleWithFlags(boolean sysAdmin, boolean orgAdmin) throws Exception {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole existing = existingRole(id);
      existing.setSysAdmin(sysAdmin);
      existing.setOrgAdmin(orgAdmin);
      when(securityService.getRole(eq(id), eq(user))).thenReturn(existing);
      return existing;
   }

   private ResolvedPlan resolveRoleUpdate(Boolean sysAdmin, Boolean orgAdmin, String description)
      throws Exception
   {
      IdentitySpec spec = new IdentitySpec();
      spec.setSysAdmin(sysAdmin);
      spec.setOrgAdmin(orgAdmin);
      spec.setDescription(description);
      return service.resolve(request("task", List.of(updateRole("Viewer", spec))), user);
   }

   @Test void resolveRefusesRoleUpdateAddingOrgAdminToSysAdminRole() throws Exception {
      roleWithFlags(true, false);
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> resolveRoleUpdate(null, true, null));
      assertTrue(e.getMessage().contains("spec.sysAdmin"));
      assertTrue(e.getMessage().contains("spec.orgAdmin"));
   }

   @Test void resolveRefusesRoleUpdateAddingSysAdminToOrgAdminRole() throws Exception {
      roleWithFlags(false, true);
      assertThrows(IllegalArgumentException.class, () -> resolveRoleUpdate(true, null, null));
   }

   @Test void resolveAllowsUnrelatedEditOnRoleThatAlreadyHasBothFlags() throws Exception {
      roleWithFlags(true, true);
      assertEquals(1, resolveRoleUpdate(null, null, "New description").changes().size());
      assertEquals(1, resolveRoleUpdate(true, null, "Restated").changes().size());
   }

   @Test void resolveAllowsClearingOneFlagOnRoleThatAlreadyHasBothFlags() throws Exception {
      roleWithFlags(true, true);
      assertEquals(1, resolveRoleUpdate(null, false, null).changes().size());
   }

   @Test void resolveAllowsSwappingTheFlagOnARole() throws Exception {
      roleWithFlags(true, false);
      assertEquals(1, resolveRoleUpdate(false, true, null).changes().size());
   }

   @Test void resolveAllowsPropertiesOnOrganizationCreate() throws Exception {
      when(securityService.getOrganization(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such organization"));
      IdentityChangeRequest change = createOrganization("org1", "Org One");
      change.getSpec().setProperties(List.of(PropertyModel.builder().name("k").value("v").build()));

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveAllowsMemberFieldsOnGroupCreate() throws Exception {
      // spec section 2's correction: memberUsers/memberGroups ARE legal on group create.
      when(securityService.getGroup(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such group"));
      IdentityChangeRequest change = createGroup("Analysts");
      change.getSpec().setMemberUsers(List.of("bob"));

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveAllowsAssignedFieldsOnRoleCreate() throws Exception {
      // spec section 2's correction: assignedUsers/assignedGroups ARE legal on role create -- and
      // this is the mechanism delete-role's rollback depends on.
      when(securityService.getRole(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such role"));
      IdentityChangeRequest change = createRole("Analyst");
      change.getSpec().setAssignedUsers(List.of("bob"));

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      assertEquals(1, plan.changes().size());
   }

   // -------------------------------------------------------------------------
   // identity/existence (spec section 2)
   // -------------------------------------------------------------------------

   @Test void resolveCreateThrowsWhenIdAlreadyExists() throws Exception {
      when(securityService.getUser(any(), eq(user))).thenReturn(new SecurityUser());
      IdentityChangeRequest change = createUser("bob");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveDeleteThrowsWhenIdDoesNotExist() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      IdentityChangeRequest change = deleteUser("bob");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("not found"));
   }

   // -------------------------------------------------------------------------
   // default-org / self-org refusal (spec section 4a/6 -- this track's own added guard)
   // -------------------------------------------------------------------------

   @Test void resolveRefusesDeletingDefaultOrganization() {
      IdentityChangeRequest change = deleteOrganization(Organization.getDefaultOrganizationID());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("default organization"));
      verifyNoInteractions(securityService);
   }

   @Test void resolveRefusesDeletingSelfOrganization() {
      IdentityChangeRequest change = deleteOrganization(Organization.getSelfOrganizationID());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("self organization"));
   }

   // Regression for a confirmed case-sensitivity bypass found during review: DatabaseAuthenticationProvider
   // resolves organization ids case-insensitively when security.user.caseSensitive=false, so an exact-match
   // guard would let a differently-cased id past this refusal while SecurityService.deleteOrganization
   // still resolves and deletes the real protected organization. Must refuse regardless of caller casing.
   @Test void resolveRefusesDeletingDefaultOrganizationRegardlessOfCase() {
      IdentityChangeRequest change =
         deleteOrganization(Organization.getDefaultOrganizationID().toUpperCase());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("default organization"));
      verifyNoInteractions(securityService);
   }

   @Test void resolveRefusesDeletingSelfOrganizationRegardlessOfCase() {
      IdentityChangeRequest change =
         deleteOrganization(Organization.getSelfOrganizationID().toLowerCase());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("self organization"));
   }

   @Test void resolveAllowsDeletingAnOrdinaryOrganization() throws Exception {
      when(securityService.getOrganization(eq("org1"), eq(user)))
         .thenReturn(new SecurityOrganization());
      IdentityChangeRequest change = deleteOrganization("org1");

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      assertEquals(1, plan.changes().size());
   }

   // -------------------------------------------------------------------------
   // hash / plan shape (spec section 5/7)
   // -------------------------------------------------------------------------

   @Test void everyChangeIsHighRiskAndStorageScoped() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      ResolvedPlan plan = service.resolve(request("task", List.of(createUser("bob"))), user);

      PlanChange change = plan.changes().get(0);
      assertEquals("high", change.risk());
      assertEquals("storage", change.snapshotScope());
      assertTrue(plan.requiresStorageBackup());
      assertTrue(plan.requiresAgentSignoff());
      assertTrue(change.recognized());
   }

   @Test void hashIsStableAcrossRepeatedResolution() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      IdentityChangePlanRequest req = request("task", List.of(createUser("bob")));

      String hash1 = service.resolve(req, user).planHash();
      String hash2 = service.resolve(req, user).planHash();
      assertEquals(hash1, hash2);
   }

   @Test void issuesATaskTokenBoundToThePlanHash() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      var plan = service.resolve(request("task", List.of(createUser("bob"))), user);

      assertEquals("TKN:" + plan.planHash() + "\u001ftask", plan.taskToken());
   }

   @Test void hashChangesWhenSpecDiffers() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));

      IdentityChangeRequest change1 = createUser("bob");
      IdentityChangeRequest change2 = createUser("bob");
      change2.getSpec().setAlias("Bob Smith");

      String hash1 = service.resolve(request("task", List.of(change1)), user).planHash();
      String hash2 = service.resolve(request("task", List.of(change2)), user).planHash();
      assertNotEquals(hash1, hash2);
   }

   // task is a free-text, audit-only label (bug 76454) -- a caller that does not replay it
   // byte-for-byte between preview and apply must not see a false planHash conflict.
   @Test void hashIsUnaffectedByDifferentTaskStrings() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      IdentityChangeRequest change = createUser("bob");

      String hash1 = service.resolve(request("create user bob", List.of(change)), user).planHash();
      String hash2 = service.resolve(request("add a new user named bob", List.of(change)), user).planHash();
      assertEquals(hash1, hash2);
   }

   // -------------------------------------------------------------------------
   // update verb (design section 2/3/9)
   // -------------------------------------------------------------------------

   @Test void resolveUpdateThrowsWhenIdMissing() {
      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("New Alias");
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("update");
      change.setUnitType("user");
      change.setSpec(spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("id"));
   }

   @Test void resolveUpdateThrowsWhenSpecMissing() {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("update");
      change.setUnitType("user");
      change.setId("bob");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec"));
   }

   @Test void resolveUpdateThrowsWhenSpecIsEmpty() {
      IdentityChangeRequest change = updateUser("bob", new IdentitySpec());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("at least one field"));
      verifyNoInteractions(securityService);
   }

   @Test void resolveUpdateRefusesPasswordOnUser() {
      IdentitySpec spec = new IdentitySpec();
      spec.setPassword("Password1!");
      IdentityChangeRequest change = updateUser("bob", spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec.password"));
      assertTrue(ex.getMessage().contains("out of scope for update"));
      verifyNoInteractions(securityService);
   }

   @Test void resolveUpdateRefusesOrgIdOnUser() {
      IdentitySpec spec = new IdentitySpec();
      spec.setOrgId("other-org");
      IdentityChangeRequest change = updateUser("bob", spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec.orgId"));
      assertTrue(ex.getMessage().contains("not settable via spec"));
   }

   @Test void resolveUpdateRefusesOrgIdOnGroup() {
      IdentitySpec spec = new IdentitySpec();
      spec.setOrgId("other-org");
      IdentityChangeRequest change = updateGroup("Analysts", spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec.orgId"));
   }

   @Test void resolveUpdateRefusesOrgIdOnRole() {
      IdentitySpec spec = new IdentitySpec();
      spec.setOrgId("other-org");
      IdentityChangeRequest change = updateRole("Viewer", spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec.orgId"));
   }

   // bug-76834: organization id rename is a real, supported capability -- the field is no longer
   // refused outright. Renamed from resolveUpdateRefusesIdOnOrganization, which asserted the old
   // unconditional refusal this fix removes.

   @Test void resolveUpdateAllowsRenamingAnOrganizationId() throws Exception {
      when(securityService.getOrganization(eq("org1"), eq(user)))
         .thenReturn(existingOrganization("org1"));
      when(securityService.getOrganizations(eq(user))).thenReturn(organizationList("org1"));
      IdentitySpec spec = new IdentitySpec();
      spec.setId("org2");
      IdentityChangeRequest change = updateOrganization("org1", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.proposedValue().contains("id=org2"));
   }

   @Test void resolveUpdateRefusesRenamingAnOrganizationToTheDefaultOrganizationId() throws Exception {
      when(securityService.getOrganization(eq("org1"), eq(user)))
         .thenReturn(existingOrganization("org1"));
      IdentitySpec spec = new IdentitySpec();
      spec.setId(Organization.getDefaultOrganizationID());
      IdentityChangeRequest change = updateOrganization("org1", spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec.id"));
      assertTrue(ex.getMessage().contains("default organization"));
      // The reserved-id check is cheap (no live read) and short-circuits before the duplicate-id
      // check's full organization-list scan.
      verify(securityService, never()).getOrganizations(any());
   }

   @Test void resolveUpdateRefusesRenamingTheDefaultOrganizationAwayFromItsId() throws Exception {
      String defaultId = Organization.getDefaultOrganizationID();
      when(securityService.getOrganization(eq(defaultId), eq(user)))
         .thenReturn(existingOrganization(defaultId));
      IdentitySpec spec = new IdentitySpec();
      spec.setId("some-other-id");
      IdentityChangeRequest change = updateOrganization(defaultId, spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec.id"));
      assertTrue(ex.getMessage().contains("default organization"));
   }

   @Test void resolveUpdateRefusesRenamingAnOrganizationToAnotherOrganizationsId() throws Exception {
      when(securityService.getOrganization(eq("org1"), eq(user)))
         .thenReturn(existingOrganization("org1"));
      when(securityService.getOrganizations(eq(user))).thenReturn(organizationList("org1", "org2"));
      IdentitySpec spec = new IdentitySpec();
      spec.setId("org2");
      IdentityChangeRequest change = updateOrganization("org1", spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec.id"));
      assertTrue(ex.getMessage().contains("already the id of another organization"));
   }

   // Regression for the guardrail/cascade case-sensitivity mismatch: the "is the id changing"
   // trigger here used equalsIgnoreCase, so a case-only rename skipped BOTH guards entirely --
   // yet IdentityService.setOrganizationInfo gates its migration cascade on case-sensitive
   // !Tool.equals(), so it ran the full cascade (registry migration, dataspace relocation,
   // copyOrganization permission migration) on the default organization anyway.
   @Test void resolveUpdateRefusesCaseOnlyRenameOfTheDefaultOrganizationId() throws Exception {
      String defaultId = Organization.getDefaultOrganizationID();
      when(securityService.getOrganization(eq(defaultId), eq(user)))
         .thenReturn(existingOrganization(defaultId));
      IdentitySpec spec = new IdentitySpec();
      spec.setId(defaultId.toUpperCase());
      IdentityChangeRequest change = updateOrganization(defaultId, spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec.id"));
      assertTrue(ex.getMessage().contains("default organization"));
   }

   @Test void resolveUpdateRefusesCaseOnlyRenameOfTheSelfOrganizationId() throws Exception {
      String selfId = Organization.getSelfOrganizationID();
      when(securityService.getOrganization(eq(selfId), eq(user)))
         .thenReturn(existingOrganization(selfId));
      IdentitySpec spec = new IdentitySpec();
      spec.setId(selfId.toLowerCase());
      IdentityChangeRequest change = updateOrganization(selfId, spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec.id"));
      assertTrue(ex.getMessage().contains("self organization"));
   }

   // The counter-test to the two above: tightening the trigger must not start refusing a
   // case-only rename of an ORDINARY organization. That stays supported (see bug #75776 and
   // AbstractEditableAuthenticationProvider's sameStorageBucket handling); it is now simply
   // subjected to the same guards as any other id change, both of which it passes.
   @Test void resolveUpdateAllowsCaseOnlyRenameOfAnOrdinaryOrganizationId() throws Exception {
      when(securityService.getOrganization(eq("org1"), eq(user)))
         .thenReturn(existingOrganization("org1"));
      // now reached, where the case-insensitive trigger used to short-circuit past it
      when(securityService.getOrganizations(eq(user))).thenReturn(organizationList("org1", "org2"));
      IdentitySpec spec = new IdentitySpec();
      spec.setId("ORG1");
      IdentityChangeRequest change = updateOrganization("org1", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);

      assertEquals(1, plan.changes().size());
      assertTrue(plan.changes().get(0).proposedValue().contains("id=ORG1"));
   }

   @Test void resolveUpdateStillEnforcesTheDiscriminatorConfusionDefense() {
      IdentitySpec spec = new IdentitySpec();
      spec.setParentGroups(List.of("Admins"));
      IdentityChangeRequest change = updateUser("bob", spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("parentGroups"));
   }

   @Test void resolveUpdateThrowsWhenIdDoesNotExist() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      when(securityService.getUser(eq(id), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("New Alias");
      IdentityChangeRequest change = updateUser("bob", spec);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("not found"));
   }

   @Test void resolveUpdateAcceptsUpdateAsAVerb() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      when(securityService.getUser(eq(id), eq(user))).thenReturn(existingUser(id));
      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("New Alias");
      IdentityChangeRequest change = updateUser("bob", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      assertEquals(1, plan.changes().size());
      assertTrue(plan.changes().get(0).recognized());
   }

   @Test void resolveUpdateProducesADiffLimitedToTheTouchedField() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      SecurityUser existing = existingUser(id);
      existing.setAlias("Old Alias");
      when(securityService.getUser(eq(id), eq(user))).thenReturn(existing);
      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("New Alias");
      IdentityChangeRequest change = updateUser("bob", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);

      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.currentValue().contains("alias=Old Alias"));
      assertTrue(planChange.proposedValue().contains("alias=New Alias"));
      // Every other field-segment is identical between currentValue and proposedValue -- the only
      // diff is the alias segment, confirming the merge didn't touch anything else (IdentityProjection's
      // field-by-field format makes this mechanically string-diffable, design section 11).
      assertEquals(planChange.currentValue().replace("alias=Old Alias", "alias=New Alias"),
                  planChange.proposedValue());
   }

   @Test void resolveUpdateGroupProducesAMergedProposal() throws Exception {
      IdentityID id = new IdentityID("Analysts", "host-org");
      SecurityGroup existing = existingGroup(id);
      existing.setParentGroups(List.of("Employees"));
      when(securityService.getGroup(eq(id), eq(user))).thenReturn(existing);
      IdentitySpec spec = new IdentitySpec();
      spec.setParentGroups(List.of("Contractors"));
      IdentityChangeRequest change = updateGroup("Analysts", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.proposedValue().contains("parentGroups=Contractors"));
   }

   @Test void resolveUpdateRoleProducesAMergedProposal() throws Exception {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole existing = existingRole(id);
      existing.setDescription("Old description");
      when(securityService.getRole(eq(id), eq(user))).thenReturn(existing);
      IdentitySpec spec = new IdentitySpec();
      spec.setDescription("New description");
      IdentityChangeRequest change = updateRole("Viewer", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.proposedValue().contains("description=New description"));
   }

   @Test void resolveUpdateOrganizationProducesAMergedProposal() throws Exception {
      when(securityService.getOrganization(eq("org1"), eq(user)))
         .thenReturn(existingOrganization("org1"));
      IdentitySpec spec = new IdentitySpec();
      spec.setOrgName("New Org Name");
      IdentityChangeRequest change = updateOrganization("org1", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.proposedValue().contains("name=New Org Name"));
   }

   @Test void resolveUpdateRoleDefaultRoleAndSysAdminProducesAMergedProposal() throws Exception {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole existing = existingRole(id);
      existing.setDefaultRole(false);
      existing.setSysAdmin(false);
      when(securityService.getRole(eq(id), eq(user))).thenReturn(existing);
      IdentitySpec spec = new IdentitySpec();
      spec.setDefaultRole(true);
      IdentityChangeRequest change = updateRole("Viewer", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.proposedValue().contains("defaultRole=true"));
      // sysAdmin was never mentioned -- must be preserved (false), not tripped to true as a
      // side-effect of the defaultRole change.
      assertTrue(planChange.proposedValue().contains("sysAdmin=false"));
   }

   @Test void resolveUpdateRoleOrgAdminProducesAMergedProposal() throws Exception {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole existing = existingRole(id);
      existing.setOrgAdmin(true);
      when(securityService.getRole(eq(id), eq(user))).thenReturn(existing);
      IdentitySpec spec = new IdentitySpec();
      spec.setDescription("New description"); // unrelated to orgAdmin
      IdentityChangeRequest change = updateRole("Viewer", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.proposedValue().contains("description=New description"));
      // orgAdmin was never mentioned -- must be preserved (true), not reset to false as a
      // side-effect of the description change.
      assertTrue(planChange.proposedValue().contains("orgAdmin=true"));
   }

   @Test void resolveUpdateOrganizationPropertiesProducesAMergedProposal() throws Exception {
      SecurityOrganization existing = existingOrganization("org1");
      existing.setProperties(List.of(PropertyModel.builder().name("custom.key").value("old").build()));
      when(securityService.getOrganization(eq("org1"), eq(user))).thenReturn(existing);
      IdentitySpec spec = new IdentitySpec();
      spec.setProperties(List.of(PropertyModel.builder().name("custom.key").value("new").build()));
      IdentityChangeRequest change = updateOrganization("org1", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.proposedValue().contains("custom.key=new"));
   }

   @Test void resolveUpdateAcceptsDefaultRoleAsTheOnlyPopulatedFieldOnRole() throws Exception {
      IdentityID id = new IdentityID("Viewer", "host-org");
      when(securityService.getRole(eq(id), eq(user))).thenReturn(existingRole(id));
      IdentitySpec spec = new IdentitySpec();
      spec.setDefaultRole(true);
      IdentityChangeRequest change = updateRole("Viewer", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      assertEquals(1, plan.changes().size());
   }

   @Test void resolveUpdateAcceptsPropertiesAsTheOnlyPopulatedFieldOnOrganization() throws Exception {
      when(securityService.getOrganization(eq("org1"), eq(user)))
         .thenReturn(existingOrganization("org1"));
      IdentitySpec spec = new IdentitySpec();
      spec.setProperties(List.of());
      IdentityChangeRequest change = updateOrganization("org1", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      assertEquals(1, plan.changes().size());
   }

   // Counter-assertion 3 (charter): update must NOT reuse delete's default/self-org protection --
   // updating the default/self organization's orgName/locale is normal admin activity.
   @Test void resolveUpdateAllowsUpdatingTheDefaultOrganization() throws Exception {
      when(securityService.getOrganization(eq(Organization.getDefaultOrganizationID()), eq(user)))
         .thenReturn(existingOrganization(Organization.getDefaultOrganizationID()));
      IdentitySpec spec = new IdentitySpec();
      spec.setOrgName("Renamed Default Org");
      IdentityChangeRequest change = updateOrganization(Organization.getDefaultOrganizationID(), spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);
      assertEquals(1, plan.changes().size());
   }

   // Regression tests for review round 1 Blocker 2 (06-review-r1.md): requireAtLeastOneField used
   // to test presence via isAbsent, which treats an explicit empty string/list the SAME as
   // omitted/null -- wrongly refusing a legitimate clear-only update as "nothing to change." Fixed
   // to test presence via != null (matching IdentityMerge's own per-field semantics). Each test below
   // supplies exactly ONE explicit-empty field as the spec's only populated key: the update must be
   // ACCEPTED (resolve() does not throw) and the resulting proposal must show that field cleared.

   @Test void resolveUpdateAcceptsExplicitEmptyListAsTheOnlyPopulatedFieldOnUser() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      SecurityUser existing = existingUser(id);
      existing.setEmails(List.of("old@x.com"));
      when(securityService.getUser(eq(id), eq(user))).thenReturn(existing);
      IdentitySpec spec = new IdentitySpec();
      spec.setEmails(List.of());
      IdentityChangeRequest change = updateUser("bob", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);

      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.currentValue().contains("emails=old@x.com"));
      assertTrue(planChange.proposedValue().contains("emails=;"));
   }

   @Test void resolveUpdateAcceptsExplicitEmptyStringAsTheOnlyPopulatedFieldOnUser() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      SecurityUser existing = existingUser(id);
      existing.setAlias("Old Alias");
      when(securityService.getUser(eq(id), eq(user))).thenReturn(existing);
      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("");
      IdentityChangeRequest change = updateUser("bob", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);

      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.currentValue().contains("alias=Old Alias"));
      assertTrue(planChange.proposedValue().contains("alias=;"));
   }

   @Test void resolveUpdateAcceptsExplicitEmptyListAsTheOnlyPopulatedFieldOnGroup() throws Exception {
      IdentityID id = new IdentityID("Analysts", "host-org");
      SecurityGroup existing = existingGroup(id);
      existing.setParentGroups(List.of("Employees"));
      when(securityService.getGroup(eq(id), eq(user))).thenReturn(existing);
      IdentitySpec spec = new IdentitySpec();
      spec.setParentGroups(List.of());
      IdentityChangeRequest change = updateGroup("Analysts", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);

      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.currentValue().contains("parentGroups=Employees"));
      assertTrue(planChange.proposedValue().contains("parentGroups=;"));
   }

   @Test void resolveUpdateAcceptsExplicitEmptyStringAsTheOnlyPopulatedFieldOnRole() throws Exception {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole existing = existingRole(id);
      existing.setDescription("Old description");
      when(securityService.getRole(eq(id), eq(user))).thenReturn(existing);
      IdentitySpec spec = new IdentitySpec();
      spec.setDescription("");
      IdentityChangeRequest change = updateRole("Viewer", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);

      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.currentValue().contains("description=Old description"));
      assertTrue(planChange.proposedValue().contains("description=;"));
   }

   @Test void resolveUpdateAcceptsExplicitEmptyStringAsTheOnlyPopulatedFieldOnOrganization() throws Exception {
      when(securityService.getOrganization(eq("org1"), eq(user)))
         .thenReturn(existingOrganization("org1"));
      IdentitySpec spec = new IdentitySpec();
      spec.setOrgName("");
      IdentityChangeRequest change = updateOrganization("org1", spec);

      ResolvedPlan plan = service.resolve(request("task", List.of(change)), user);

      PlanChange planChange = plan.changes().get(0);
      assertTrue(planChange.currentValue().contains("name=Org One"));
      assertTrue(planChange.proposedValue().contains("name=;"));
   }

   // -------------------------------------------------------------------------
   // fixtures
   // -------------------------------------------------------------------------

   // -------------------------------------------------------------------------
   // Bug #78857: identity reference forms and reference-separator names
   // -------------------------------------------------------------------------

   @ParameterizedTest
   @ValueSource(strings = { "bob", "bob:host-org", "bob~;~host-org", "bob^host-org" })
   void resolveDeleteAndUpdateAcceptEveryReferenceForm(String ref) throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      lenient().when(securityService.getUser(eq(id), eq(user))).thenReturn(existingUser(id));
      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("New Alias");

      ResolvedPlan delete = service.resolve(request("task", List.of(deleteUser(ref))), user);
      ResolvedPlan update = service.resolve(request("task", List.of(updateUser(ref, spec))), user);

      assertEquals("USER|bob~;~host-org", delete.changes().get(0).property());
      assertEquals("USER|bob~;~host-org", update.changes().get(0).property());
   }

   @Test void resolveAcceptsAnotherExistingOrgAndRefusesAnUnknownOne() throws Exception {
      IdentityID id = new IdentityID("bob", "other-org");
      when(editableAuthentication.getOrganization("other-org")).thenReturn(mock(Organization.class));
      when(securityService.getUser(eq(id), eq(user))).thenReturn(existingUser(id));

      ResolvedPlan plan =
         service.resolve(request("task", List.of(deleteUser("bob^other-org"))), user);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(deleteUser("bob~;~nope-org"))), user));

      assertEquals("USER|bob~;~other-org", plan.changes().get(0).property());
      assertTrue(ex.getMessage().contains("organization \"nope-org\" does not exist"),
                 ex.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = { "Everyone^__GLOBAL__", "Everyone~;~__GLOBAL__", "Everyone:__GLOBAL__" })
   void resolveRefusesAGlobalIdentity(String ref) {
      IdentityChangeRequest change = deleteUser(ref);
      change.setUnitType("role");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("global"), ex.getMessage());
   }

   @Test void resolveRefusesACaretReferenceWithAnUnknownOrg() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(deleteUser("bob^SELF"))), user));
      assertTrue(ex.getMessage().contains("no organization \"SELF\""), ex.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = { "a:b", "a^b", "a~;~b" })
   void resolveCreateRefusesANameHoldingAReferenceSeparator(String name) {
      for(IdentityChangeRequest change :
         List.of(createUser(name), createGroup(name), createRole(name)))
      {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resolve(request("task", List.of(change)), user));
         assertTrue(ex.getMessage().contains("spec.name: must not contain"), ex.getMessage());
      }
   }

   @Test void resolveUpdateRefusesARenameToANameHoldingAReferenceSeparator() {
      IdentitySpec spec = new IdentitySpec();
      spec.setName("bob^host-org2");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(updateUser("bob", spec))), user));
      assertTrue(ex.getMessage().contains("spec.name: must not contain"), ex.getMessage());
   }

   /** An existing external name holding a separator stays updatable, and can be renamed away. */
   @Test void resolveUpdateOfAnExistingCaretNameIsAllowed() throws Exception {
      IdentityID id = new IdentityID("j^smith", "host-org");
      when(editableAuthentication.getUser(eq(id))).thenReturn(mock(User.class));
      when(securityService.getUser(eq(id), eq(user))).thenReturn(existingUser(id));
      IdentitySpec alias = new IdentitySpec();
      alias.setAlias("J");
      IdentitySpec rename = new IdentitySpec();
      rename.setName("jsmith");
      IdentitySpec sameName = new IdentitySpec();
      sameName.setName("j^smith");
      sameName.setAlias("J");

      for(IdentitySpec spec : List.of(alias, rename, sameName)) {
         ResolvedPlan plan =
            service.resolve(request("task", List.of(updateUser("j^smith", spec))), user);
         assertEquals("USER|j^smith~;~host-org", plan.changes().get(0).property());
      }
   }

   private static IdentityChangePlanRequest request(String task, List<IdentityChangeRequest> changes) {
      IdentityChangePlanRequest req = new IdentityChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static IdentityChangeRequest createUser(String name) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("create");
      change.setUnitType("user");
      IdentitySpec spec = new IdentitySpec();
      spec.setName(name);
      spec.setPassword("Password1!AAA");
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest createGroup(String name) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("create");
      change.setUnitType("group");
      IdentitySpec spec = new IdentitySpec();
      spec.setName(name);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest createRole(String name) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("create");
      change.setUnitType("role");
      IdentitySpec spec = new IdentitySpec();
      spec.setName(name);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest createOrganization(String id, String name) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("create");
      change.setUnitType("organization");
      IdentitySpec spec = new IdentitySpec();
      spec.setId(id);
      spec.setOrgName(name);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest deleteUser(String id) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("delete");
      change.setUnitType("user");
      change.setId(id);
      return change;
   }

   private static IdentityChangeRequest deleteOrganization(String id) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("delete");
      change.setUnitType("organization");
      change.setId(id);
      return change;
   }

   private static IdentityChangeRequest updateUser(String id, IdentitySpec spec) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("update");
      change.setUnitType("user");
      change.setId(id);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest updateGroup(String id, IdentitySpec spec) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("update");
      change.setUnitType("group");
      change.setId(id);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest updateRole(String id, IdentitySpec spec) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("update");
      change.setUnitType("role");
      change.setId(id);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest updateOrganization(String id, IdentitySpec spec) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("update");
      change.setUnitType("organization");
      change.setId(id);
      change.setSpec(spec);
      return change;
   }

   private static SecurityUser existingUser(IdentityID id) {
      SecurityUser u = new SecurityUser();
      u.setIdentityID(id);
      u.setActive(true);
      u.setEmails(List.of());
      u.setGroups(List.of());
      u.setRoles(List.of());
      return u;
   }

   private static SecurityGroup existingGroup(IdentityID id) {
      SecurityGroup g = new SecurityGroup();
      g.setIdentityID(id);
      g.setParentGroups(List.of());
      g.setMemberUsers(List.of());
      g.setMemberGroups(List.of());
      g.setRoles(List.of());
      return g;
   }

   private static SecurityRole existingRole(IdentityID id) {
      SecurityRole r = new SecurityRole();
      r.setIdentityID(id);
      r.setAssignedUsers(List.of());
      r.setAssignedGroups(List.of());
      r.setInheritedRoles(List.of());
      return r;
   }

   private static SecurityOrganization existingOrganization(String id) {
      SecurityOrganization o = new SecurityOrganization();
      o.setId(id);
      o.setName("Org One");
      return o;
   }

   private static SecurityOrganizationList organizationList(String... ids) {
      SecurityOrganizationList list = new SecurityOrganizationList();
      list.setOrganizations(Arrays.stream(ids).map(IdentityChangePlanServiceTest::existingOrganization)
                                .collect(Collectors.toList()));
      return list;
   }

   /** See {@code SecurityProviderGuardTest}: stands in for {@code CompositeSecurityProvider},
    * whose factory needs the Spring context. */
   private static final class TestSecurityProvider extends AbstractSecurityProvider {
      TestSecurityProvider(AuthenticationProvider authentication,
                           AuthorizationProvider authorization)
      {
         super(authentication, authorization);
      }
   }

   // -------------------------------------------------------------------------
   // Bug #79016: user password reaches the plan hash; org-qualified list references resolve
   // -------------------------------------------------------------------------

   private ResolvedPlan previewUserWithPassword(String password) throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      IdentityChangeRequest change = createUser("bob");
      change.getSpec().setPassword(password);
      return service.resolve(request("task", List.of(change)), user);
   }

   @Test void createUserDifferentPasswordsHashDifferently() throws Exception {
      ResolvedPlan a = previewUserWithPassword("Password1!AAA");
      ResolvedPlan b = previewUserWithPassword("Password2!BBB");

      assertNotEquals(a.planHash(), b.planHash());
      // the displayed projection still carries only the presence token
      assertEquals(a.changes().get(0).proposedValue(), b.changes().get(0).proposedValue());
      assertTrue(a.changes().get(0).proposedValue().contains("password=pw:set;"));
      assertFalse(a.changes().get(0).proposedValue().contains("Password1"));
   }

   @Test void createUserSamePasswordHashesTheSame() throws Exception {
      assertEquals(previewUserWithPassword("Password1!AAA").planHash(),
                   previewUserWithPassword("Password1!AAA").planHash());
   }

   private ResolvedPlan previewGroupCreate(IdentitySpec patch) throws Exception {
      lenient().when(securityService.getGroup(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such group"));
      IdentityChangeRequest change = createGroup("team");
      change.getSpec().setMemberUsers(patch.getMemberUsers());
      change.getSpec().setMemberGroups(patch.getMemberGroups());
      change.getSpec().setParentGroups(patch.getParentGroups());
      change.getSpec().setRoles(patch.getRoles());
      return service.resolve(request("task", List.of(change)), user);
   }

   private static IdentitySpec memberUsers(String... names) {
      IdentitySpec spec = new IdentitySpec();
      spec.setMemberUsers(new java.util.ArrayList<>(Arrays.asList(names)));
      return spec;
   }

   @ParameterizedTest
   @ValueSource(strings = { "admin", "admin:host-org", "admin~;~host-org", "admin^host-org" })
   void groupCreateResolvesEveryOrgQualifiedMemberFormToTheBareName(String ref) throws Exception {
      ResolvedPlan plan = previewGroupCreate(memberUsers(ref));

      String proposed = plan.changes().get(0).proposedValue();
      assertTrue(proposed.contains("memberUsers=admin;"), proposed);
      // the unit's own id key keeps its "~;~"; the member list must hold no org qualifier
      assertFalse(proposed.contains("^"), proposed);
      assertFalse(proposed.contains("admin~"), proposed);
   }

   @Test void qualifiedAndBareMemberHashTheSame() throws Exception {
      assertEquals(previewGroupCreate(memberUsers("admin")).planHash(),
                   previewGroupCreate(memberUsers("admin^host-org")).planHash());
   }

   @ParameterizedTest
   @ValueSource(strings = { "admin:other-org", "admin~;~other-org", "admin^other-org" })
   void groupCreateRefusesAMemberFromAnotherOrgNamingTheField(String ref) {
      when(editableAuthentication.getOrganization("other-org")).thenReturn(mock(Organization.class));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> previewGroupCreate(memberUsers(ref)));

      assertTrue(ex.getMessage().matches("(?s).*memberUsers.*spec\\.orgId.*"), ex.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = { "admin^SELF", "admin~;~SELF", "admin:SELF" })
   void groupCreateRefusesAMemberWithAnUnknownOrgNamingTheField(String ref) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> previewGroupCreate(memberUsers(ref)));

      assertTrue(ex.getMessage().contains("memberUsers"), ex.getMessage());
   }

   @Test void groupCreateKeepsALiteralCaretMemberWhenOnlyThatUserExists() throws Exception {
      IdentityID literal = new IdentityID("j^smith", "host-org");
      when(editableAuthentication.getUser(eq(literal))).thenReturn(mock(User.class));

      ResolvedPlan plan = previewGroupCreate(memberUsers("j^smith"));

      assertTrue(plan.changes().get(0).proposedValue().contains("memberUsers=j^smith;"));
   }

   @Test void groupCreateResolvesEveryGroupListField() throws Exception {
      IdentitySpec spec = new IdentitySpec();
      spec.setParentGroups(new java.util.ArrayList<>(List.of("pg^host-org")));
      spec.setMemberGroups(new java.util.ArrayList<>(List.of("mg:host-org")));
      spec.setRoles(new java.util.ArrayList<>(List.of("r~;~host-org")));

      String proposed = previewGroupCreate(spec).changes().get(0).proposedValue();

      assertTrue(proposed.contains("parentGroups=pg;"), proposed);
      assertTrue(proposed.contains("memberGroups=mg;"), proposed);
      assertTrue(proposed.contains("roles=r;"), proposed);
   }

   @Test void userCreateResolvesGroupsAndRoles() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      when(securityService.projectNewUserRoles(any(), any(), eq(user)))
         .thenAnswer(inv -> inv.getArgument(1));
      IdentityChangeRequest change = createUser("bob");
      change.getSpec().setGroups(new java.util.ArrayList<>(List.of("g^host-org")));
      change.getSpec().setRoles(new java.util.ArrayList<>(List.of("r:host-org")));

      String proposed = service.resolve(request("task", List.of(change)), user)
         .changes().get(0).proposedValue();

      assertTrue(proposed.contains("groups=g;"), proposed);
      assertTrue(proposed.contains("roles=r~;~host-org;"), proposed);
   }

   // Bug #79563: the plan of a user create shows the roles the create will really give
   @Test void userCreateWithoutRolesShowsTheDefaultRolesTheCreateWillSeed() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      when(securityService.projectNewUserRoles(any(), isNull(), eq(user)))
         .thenReturn(List.of(new IdentityID("Everyone", "host-org")));

      String proposed = service.resolve(request("task", List.of(createUser("bob"))), user)
         .changes().get(0).proposedValue();

      assertTrue(proposed.contains("roles=Everyone~;~host-org;"), proposed);
   }

   @Test void userCreateRolesAreWhatTheSharedComputationReturnsNotTheRawSpec() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      // a caller without root role ADMIN: the explicit role is ignored by the create
      when(securityService.projectNewUserRoles(any(), any(), eq(user)))
         .thenReturn(List.of(new IdentityID("Everyone", "host-org")));
      IdentityChangeRequest change = createUser("bob");
      change.getSpec().setRoles(new java.util.ArrayList<>(List.of("r:host-org")));

      String proposed = service.resolve(request("task", List.of(change)), user)
         .changes().get(0).proposedValue();

      assertTrue(proposed.contains("roles=Everyone~;~host-org;"), proposed);
      assertFalse(proposed.contains("r~;~"), proposed);
   }

   @Test void userCreatePlanDoesNotFailWhenTheRoleComputationThrows() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      when(securityService.projectNewUserRoles(any(), any(), eq(user)))
         .thenThrow(new IllegalStateException("not editable"));
      IdentityChangeRequest change = createUser("bob");
      change.getSpec().setRoles(new java.util.ArrayList<>(List.of("r:host-org")));

      String proposed = service.resolve(request("task", List.of(change)), user)
         .changes().get(0).proposedValue();

      assertTrue(proposed.contains("roles=r~;~host-org;"), proposed);
   }

   @Test void roleCreateResolvesInheritedRolesAssignedUsersAndAssignedGroups() throws Exception {
      when(securityService.getRole(any(), eq(user)))
         .thenThrow(new MissingResourceException("no such role"));
      IdentityChangeRequest change = createRole("Analyst");
      change.getSpec().setInheritedRoles(new java.util.ArrayList<>(List.of("ir^host-org")));
      change.getSpec().setAssignedUsers(new java.util.ArrayList<>(List.of("au~;~host-org")));
      change.getSpec().setAssignedGroups(new java.util.ArrayList<>(List.of("ag:host-org")));

      String proposed = service.resolve(request("task", List.of(change)), user)
         .changes().get(0).proposedValue();

      assertTrue(proposed.contains("inheritedRoles=ir;"), proposed);
      assertTrue(proposed.contains("assignedUsers=au;"), proposed);
      assertTrue(proposed.contains("assignedGroups=ag;"), proposed);
   }

   @Test void updateResolvesQualifiedMembersAgainstTheTargetUnitsOwnOrg() throws Exception {
      IdentityID id = new IdentityID("team", "host-org");
      when(securityService.getGroup(eq(id), eq(user))).thenReturn(existingGroup(id));
      IdentitySpec spec = memberUsers("admin^host-org");

      String proposed = service.resolve(request("task", List.of(updateGroup("team", spec))), user)
         .changes().get(0).proposedValue();

      assertTrue(proposed.contains("memberUsers=admin;"), proposed);
      // the spec the apply step reads was normalized in place, so apply sees the bare name too
      assertEquals(List.of("admin"), spec.getMemberUsers());
   }

   @Test void updateRefusesAQualifiedMemberFromAnotherOrg() {
      IdentityID id = new IdentityID("team", "host-org");
      when(editableAuthentication.getOrganization("other-org")).thenReturn(mock(Organization.class));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(
            updateGroup("team", memberUsers("admin:other-org")))), user));

      assertTrue(ex.getMessage().contains("memberUsers"), ex.getMessage());
   }
}
