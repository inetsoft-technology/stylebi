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

import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.security.*;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.security.auth.MissingResourceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78854: list and scalar values in an identity projection must be separator-escaped, so
 * two different requests can never share a projection (and so a planHash). The pinned strings
 * and hashes for ordinary names were captured on the unfixed base; they must not change.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class IdentityProjectionSeparatorTest {
   @Mock private SecurityService securityService;
   @Mock private IdentityService identityService;
   @Mock private SecurityEngine securityEngine;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   @Mock private EditableAuthenticationProvider editableAuthentication;
   @Mock private AuthorizationProvider authorizationProvider;
   private IdentityChangePlanService planService;
   private IdentityChangesetApplyService applyService;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Tool> tool;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach void setUp() {
      planService = new IdentityChangePlanService(securityService, securityEngine);
      applyService = new IdentityChangesetApplyService(planService, securityService,
                                                       identityService, securityEngine,
                                                       backupService);
      SecurityProvider writable =
         new TestSecurityProvider(editableAuthentication, authorizationProvider);
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(writable);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      lenient().when(user.getName()).thenReturn(new IdentityID("caller", "host-org").convertToKey());

      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> ((String) inv.getArgument(0)).substring(4));
      tool.when(Tool::getHost).thenReturn("test-host");

      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      tool.close();
      auditStatic.close();
   }

   // -------------------------------------------------------------------------
   // ordinary names: projection and planHash pinned on the unfixed base
   // -------------------------------------------------------------------------

   @Test void ordinaryUserProjectionsAreUnchanged() {
      IdentityID id = new IdentityID("bob", "host-org");
      assertEquals(PIN_USER_LIVE, IdentityProjection.projectUser(liveUser(id)));
      assertEquals(PIN_USER_SPEC, IdentityProjection.projectUserSpec(id, userSpec()));
   }

   @Test void ordinaryGroupProjectionsAreUnchanged() {
      IdentityID id = new IdentityID("Analysts", "host-org");
      assertEquals(PIN_GROUP_LIVE, IdentityProjection.projectGroup(liveGroup(id), id));
      assertEquals(PIN_GROUP_SPEC, IdentityProjection.projectGroupSpec(id, groupSpec()));
   }

   @Test void ordinaryRoleProjectionsAreUnchanged() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      assertEquals(PIN_ROLE_LIVE, IdentityProjection.projectRole(liveRole(id), id));
      assertEquals(PIN_ROLE_SPEC, IdentityProjection.projectRoleSpec(id, roleSpec()));
   }

   @Test void ordinaryOrganizationProjectionsAreUnchanged() {
      SecurityOrganization o = new SecurityOrganization();
      o.setId("org1");
      o.setName("Org One");
      o.setLocale("en_US");
      o.setProperties(orgProperties());
      assertEquals(PIN_ORG_LIVE, IdentityProjection.projectOrganization(o, "org1"));
      assertEquals(PIN_ORG_SPEC, IdentityProjection.projectOrganizationSpec("org1", orgSpec()));
   }

   @Test void ordinaryPlanHashesAreUnchanged() throws Exception {
      IdentityID bob = new IdentityID("bob", "host-org");
      IdentityID analysts = new IdentityID("Analysts", "host-org");
      IdentityID viewer = new IdentityID("Viewer", "host-org");
      lenient().when(securityService.getUser(eq(bob), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      lenient().when(securityService.getGroup(eq(analysts), eq(user)))
         .thenReturn(liveGroup(analysts));
      lenient().when(securityService.getRole(eq(viewer), eq(user)))
         .thenThrow(new MissingResourceException("no such role"));
      lenient().when(securityService.getOrganization(eq("org1"), eq(user)))
         .thenThrow(new MissingResourceException("no such organization"));

      IdentitySpec groupUpdate = new IdentitySpec();
      groupUpdate.setParentGroups(List.of("Contractors", "Employees"));
      groupUpdate.setRoles(List.of("Viewer", "Designer"));

      assertEquals(PIN_HASH_CREATE_USER,
                   hashOf(change("create", "user", null, userSpec())));
      ResolvedPlan update =
         planService.resolve(planRequest(change("update", "group", "Analysts", groupUpdate)), user);
      assertEquals(PIN_UPDATE_GROUP_CURRENT, update.changes().get(0).currentValue());
      assertEquals(PIN_UPDATE_GROUP_PROPOSED, update.changes().get(0).proposedValue());
      assertEquals(PIN_HASH_UPDATE_GROUP, update.planHash());
      assertEquals(PIN_HASH_CREATE_ROLE,
                   hashOf(change("create", "role", null, roleSpec())));
      assertEquals(PIN_HASH_CREATE_ORG,
                   hashOf(change("create", "organization", null, orgSpec())));
   }

   // -------------------------------------------------------------------------
   // separator collisions now differ
   // -------------------------------------------------------------------------

   @Test void commaInsideAListElementDiffersFromTwoElements() {
      IdentityID id = new IdentityID("QA-ID-BS-34-c", "host-org");
      IdentitySpec joined = new IdentitySpec();
      joined.setParentGroups(List.of("GA,GB"));
      IdentitySpec split = new IdentitySpec();
      split.setParentGroups(List.of("GA", "GB"));

      String a = IdentityProjection.projectGroupSpec(id, joined);
      String b = IdentityProjection.projectGroupSpec(id, split);
      assertNotEquals(a, b);
      assertTrue(a.contains("parentGroups=GA\\,GB;"), a);
      assertTrue(b.contains("parentGroups=GA,GB;"), b);
   }

   @Test void forgedFieldInAMemberNameIsEscaped() {
      IdentityID id = new IdentityID("g", "host-org");
      IdentitySpec spec = new IdentitySpec();
      spec.setMemberUsers(List.of("u;memberGroups=X"));

      String p = IdentityProjection.projectGroupSpec(id, spec);
      assertTrue(p.contains("memberUsers=u\\;memberGroups\\=X;memberGroups=;"), p);
   }

   @Test void backslashAndSemicolonAndEqualsInValuesDiffer() {
      IdentityID id = new IdentityID("g", "host-org");
      assertNotEquals(groupSpecProjection(id, "a\\", "b"), groupSpecProjection(id, "a\\,b"));
      assertNotEquals(groupSpecProjection(id, "a;b"), groupSpecProjection(id, "a", "b"));
      assertNotEquals(groupSpecProjection(id, "a=b"), groupSpecProjection(id, "a", "b"));
   }

   @Test void scalarFieldShiftDiffers() {
      IdentityID id = new IdentityID("bob", "host-org");
      IdentitySpec a = new IdentitySpec();
      a.setAlias("A;locale=en");
      a.setLocale("");
      IdentitySpec b = new IdentitySpec();
      b.setAlias("A");
      b.setLocale("en;locale=");

      assertNotEquals(IdentityProjection.projectUserSpec(id, a),
                      IdentityProjection.projectUserSpec(id, b));
   }

   @Test void organizationPropertySeparatorsDiffer() {
      IdentitySpec joined = new IdentitySpec();
      joined.setProperties(List.of(prop("a", "1,b=2")));
      IdentitySpec split = new IdentitySpec();
      split.setProperties(List.of(prop("a", "1"), prop("b", "2")));

      assertNotEquals(IdentityProjection.projectOrganizationSpec("org1", joined),
                      IdentityProjection.projectOrganizationSpec("org1", split));
   }

   @Test void identityKeySeparatorsInNameOrOrgDiffer() {
      // The literal ~;~ between name and org is kept; a ~;~ (or , ; =) inside either part is
      // escaped, so a name/org boundary can't be moved.
      SecurityGroup g = new SecurityGroup();
      g.setParentGroups(List.of());
      g.setMemberUsers(List.of());
      g.setMemberGroups(List.of());
      g.setRoles(List.of(new IdentityID("a~;~b", "c")));
      SecurityGroup h = new SecurityGroup();
      h.setParentGroups(List.of());
      h.setMemberUsers(List.of());
      h.setMemberGroups(List.of());
      h.setRoles(List.of(new IdentityID("a", "b~;~c")));
      IdentityID id = new IdentityID("g", "host-org");

      String pg = IdentityProjection.projectGroup(g, id);
      String ph = IdentityProjection.projectGroup(h, id);
      assertNotEquals(pg, ph);
      assertTrue(pg.contains("roles=a~\\;~b~;~c;"), pg);
      assertTrue(ph.contains("roles=a~;~b~\\;~c;"), ph);

      SecurityGroup r1 = new SecurityGroup();
      r1.setRoles(List.of(new IdentityID("R1,R2", "host-org")));
      SecurityGroup r2 = new SecurityGroup();
      r2.setRoles(List.of(new IdentityID("R1", "host-org"), new IdentityID("R2", "host-org")));
      assertNotEquals(IdentityProjection.projectGroup(r1, id), IdentityProjection.projectGroup(r2, id));
   }

   // -------------------------------------------------------------------------
   // blank list elements are refused
   // -------------------------------------------------------------------------

   @Test void blankListElementIsRefusedWithTheFieldName() {
      IdentitySpec spec = new IdentitySpec();
      spec.setName("g");
      spec.setParentGroups(List.of(""));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> planService.resolve(planRequest(change("create", "group", null, spec)), user));
      assertTrue(e.getMessage().contains("changes[0].spec.parentGroups[0]"), e.getMessage());
   }

   @Test void blankListElementIsRefusedOnUpdateToo() {
      IdentitySpec spec = new IdentitySpec();
      spec.setEmails(Arrays.asList("a@example.com", "  "));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> planService.resolve(planRequest(change("update", "user", "bob", spec)), user));
      assertTrue(e.getMessage().contains("changes[0].spec.emails[1]"), e.getMessage());
   }

   // -------------------------------------------------------------------------
   // end to end: preview one form, apply the other -> plan-hash mismatch (409)
   // -------------------------------------------------------------------------

   @Test void previewJoinedApplySplitIsAPlanHashMismatch() throws Exception {
      assertSwapIsRefused(List.of("GA,GB"), List.of("GA", "GB"));
   }

   @Test void previewSplitApplyJoinedIsAPlanHashMismatch() throws Exception {
      assertSwapIsRefused(List.of("GA", "GB"), List.of("GA,GB"));
   }

   private void assertSwapIsRefused(List<String> previewed, List<String> applied) throws Exception {
      IdentityID id = new IdentityID("QA-ID-BS-34-c", "host-org");
      lenient().when(securityService.getGroup(eq(id), eq(user)))
         .thenThrow(new MissingResourceException("no such group"));

      IdentitySpec previewSpec = new IdentitySpec();
      previewSpec.setName(id.getName());
      previewSpec.setParentGroups(previewed);
      ResolvedPlan preview =
         planService.resolve(planRequest(change("create", "group", null, previewSpec)), user);

      IdentitySpec applySpec = new IdentitySpec();
      applySpec.setName(id.getName());
      applySpec.setParentGroups(applied);
      IdentityApplyRequest req = new IdentityApplyRequest();
      req.setTask("task");
      req.setChanges(List.of(change("create", "group", null, applySpec)));
      req.setPlanHash(preview.planHash());
      req.setTaskToken(preview.taskToken());
      req.setReviewOutcome("approved by test");

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                   () -> applyService.apply(req, user));
      verify(securityService, never()).createGroup(any(), any(), any());
   }

   // -------------------------------------------------------------------------
   // fixtures
   // -------------------------------------------------------------------------

   private String hashOf(IdentityChangeRequest change) throws Exception {
      return planService.resolve(planRequest(change), user).planHash();
   }

   private static String groupSpecProjection(IdentityID id, String... parents) {
      IdentitySpec spec = new IdentitySpec();
      spec.setParentGroups(List.of(parents));
      return IdentityProjection.projectGroupSpec(id, spec);
   }

   private static IdentityChangePlanRequest planRequest(IdentityChangeRequest... changes) {
      IdentityChangePlanRequest req = new IdentityChangePlanRequest();
      req.setTask("task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static IdentityChangeRequest change(String verb, String unitType, String id,
                                               IdentitySpec spec)
   {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb(verb);
      change.setUnitType(unitType);
      change.setId(id);
      change.setSpec(spec);
      return change;
   }

   private static IdentitySpec userSpec() {
      IdentitySpec spec = new IdentitySpec();
      spec.setName("bob");
      spec.setPassword("Password1!AAA");
      spec.setAlias("Bob Smith");
      spec.setLocale("en_US");
      spec.setEmails(List.of("bob@example.com", "b.smith@example.com"));
      spec.setGroups(List.of("Sales", "Analysts"));
      spec.setRoles(List.of("Viewer", "Designer"));
      return spec;
   }

   private static SecurityUser liveUser(IdentityID id) {
      SecurityUser u = new SecurityUser();
      u.setIdentityID(id);
      u.setAlias("Bob Smith");
      u.setLocale("en_US");
      u.setActive(true);
      u.setEmails(List.of("bob@example.com", "b.smith@example.com"));
      u.setGroups(List.of("Sales", "Analysts"));
      u.setRoles(List.of(new IdentityID("Viewer", "host-org"), new IdentityID("Designer", null)));
      return u;
   }

   private static IdentitySpec groupSpec() {
      IdentitySpec spec = new IdentitySpec();
      spec.setName("Analysts");
      spec.setParentGroups(List.of("Employees", "Contractors"));
      spec.setMemberUsers(List.of("bob", "alice"));
      spec.setMemberGroups(List.of("SubGroup"));
      spec.setRoles(List.of("Viewer"));
      return spec;
   }

   private static SecurityGroup liveGroup(IdentityID id) {
      SecurityGroup g = new SecurityGroup();
      g.setIdentityID(id);
      g.setParentGroups(List.of("Employees"));
      g.setMemberUsers(List.of("bob", "alice"));
      g.setMemberGroups(List.of("SubGroup"));
      g.setRoles(List.of(new IdentityID("Viewer", "host-org")));
      return g;
   }

   private static IdentitySpec roleSpec() {
      IdentitySpec spec = new IdentitySpec();
      spec.setName("Viewer");
      spec.setDescription("Read-only access");
      spec.setAssignedUsers(List.of("bob", "alice"));
      spec.setAssignedGroups(List.of("Analysts"));
      spec.setInheritedRoles(List.of("Everyone"));
      spec.setDefaultRole(true);
      return spec;
   }

   private static SecurityRole liveRole(IdentityID id) {
      SecurityRole r = new SecurityRole();
      r.setIdentityID(id);
      r.setDescription("Read-only access");
      r.setAssignedUsers(List.of("bob", "alice"));
      r.setAssignedGroups(List.of("Analysts"));
      r.setInheritedRoles(List.of(new IdentityID("Everyone", "host-org")));
      r.setDefaultRole(true);
      return r;
   }

   private static IdentitySpec orgSpec() {
      IdentitySpec spec = new IdentitySpec();
      spec.setId("org1");
      spec.setOrgName("Org One");
      spec.setLocale("en_US");
      spec.setProperties(orgProperties());
      return spec;
   }

   private static List<PropertyModel> orgProperties() {
      return List.of(prop("quota", "10"), prop("color", "blue"));
   }

   private static PropertyModel prop(String name, String value) {
      return PropertyModel.builder().name(name).value(value).build();
   }

   // Captured on origin/stylebi-wiz-main-rebase 15532cf901 (before the fix).
   private static final String PIN_USER_LIVE =
      "id=bob~;~host-org;alias=Bob Smith;locale=en_US;theme=;active=true;password=pw:set;emails=b.smith@example.com,bob@example.com;groups=Analysts,Sales;roles=Designer~;~__GLOBAL__,Viewer~;~host-org;";
   private static final String PIN_USER_SPEC =
      "id=bob~;~host-org;alias=Bob Smith;locale=en_US;theme=;active=true;password=pw:set;emails=b.smith@example.com,bob@example.com;groups=Analysts,Sales;roles=Designer,Viewer;";
   private static final String PIN_GROUP_LIVE =
      "id=Analysts~;~host-org;theme=;parentGroups=Employees;memberUsers=alice,bob;memberGroups=SubGroup;roles=Viewer~;~host-org;";
   private static final String PIN_GROUP_SPEC =
      "id=Analysts~;~host-org;theme=;parentGroups=Contractors,Employees;memberUsers=alice,bob;memberGroups=SubGroup;roles=Viewer;";
   private static final String PIN_ROLE_LIVE =
      "id=Viewer~;~host-org;description=Read-only access;theme=;assignedUsers=alice,bob;assignedGroups=Analysts;inheritedRoles=Everyone~;~host-org;defaultRole=true;sysAdmin=false;orgAdmin=false;";
   private static final String PIN_ROLE_SPEC =
      "id=Viewer~;~host-org;description=Read-only access;theme=;assignedUsers=alice,bob;assignedGroups=Analysts;inheritedRoles=Everyone;defaultRole=true;sysAdmin=false;orgAdmin=false;";
   private static final String PIN_ORG_LIVE =
      "id=org1;name=Org One;locale=en_US;theme=;properties=color=blue,quota=10;";
   private static final String PIN_ORG_SPEC =
      "id=org1;name=Org One;locale=en_US;theme=;properties=color=blue,quota=10;";
   private static final String PIN_UPDATE_GROUP_CURRENT =
      "id=Analysts~;~host-org;theme=;parentGroups=Employees;memberUsers=alice,bob;memberGroups=SubGroup;roles=Viewer~;~host-org;";
   private static final String PIN_UPDATE_GROUP_PROPOSED =
      "id=Analysts~;~host-org;theme=;parentGroups=Contractors,Employees;memberUsers=alice,bob;memberGroups=SubGroup;roles=Designer~;~host-org,Viewer~;~host-org;";
   private static final String PIN_HASH_CREATE_USER =
      "69f70438a6dcdbac02532b9ca5202ddd1d5b78c16c8d40de9fbc7b646d95dc4a";
   private static final String PIN_HASH_UPDATE_GROUP =
      "b4054e4d653adc055c1cb5838687a27f8b8d630dc7609abdc79982d9e25a6076";
   private static final String PIN_HASH_CREATE_ROLE =
      "e273d9abd7a7717b35e39331b13ef8e76f75448bfdfff09c6b8631d8787f148b";
   private static final String PIN_HASH_CREATE_ORG =
      "74c12ce13e2c4c54aef5c5435b3b22a1fbeefdc0bd824c212b602677692c743d";

   private static final class TestSecurityProvider extends AbstractSecurityProvider {
      TestSecurityProvider(AuthenticationProvider authentication,
                           AuthorizationProvider authorization)
      {
         super(authentication, authorization);
      }
   }
}
