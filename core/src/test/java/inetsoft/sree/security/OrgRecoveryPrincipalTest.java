/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.sree.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.OrgOperationJournal.Entry;
import inetsoft.sree.security.OrgOperationJournal.Type;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.Identity;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81308, the id change that a recovery resumes decides which members, groups and roles the
 * original request dropped or kept with the principal of the caller of the original request, not
 * with the principal of whoever runs the recovery.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgRecoveryPrincipalTest {
   private static final String X = "b81308_x";
   private static final String Y = "b81308_y";
   private static final IdentityID U1 = new IdentityID("u1", X);
   private static final IdentityID U2 = new IdentityID("u2", X);
   private static final IdentityID SITE = new IdentityID("siteAdmin", X);
   private static final IdentityID Y_ADMIN = new IdentityID("yAdmin", Y);

   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider provider;
   private IdentityService identityService;
   private MockedStatic<SUtil> sutil;
   private Principal threadPrincipal;
   private DashboardRegistryManager dashboards;

   @BeforeEach
   void setUp() throws Exception {
      threadPrincipal = ThreadContext.getContextPrincipal();
      builder = SecurityTestDataBuilder.create()
         .addOrg("B81308X", X)
         .addOrg("B81308Y", Y)
         .addSysAdminRole("siteAdmins", X)
         .addUser(U1.name, X, "password")
         .addUser(U2.name, X, "password")
         .addUser(SITE.name, X, "password")
         .addUserToRole(SITE.name, "siteAdmins", X)
         .addUser(Y_ADMIN.name, Y, "password")
         .grantPermission(ResourceType.SECURITY_USER, new IdentityID("Users", Y).convertToKey(),
                          ResourceAction.ADMIN, Y_ADMIN.name, Identity.USER, Y)
         .grantPermission(ResourceType.SECURITY_ORGANIZATION, Y, ResourceAction.ADMIN,
                          Y_ADMIN.name, Identity.USER, Y)
         .setup();
      provider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);
      dashboards = mock(DashboardRegistryManager.class);
      identityService = new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         null, null, null, mock(FavoritesService.class), null, null, null, null,
         null, null, null, mock(IndexedStorage.class), Optional.empty(), null, null, null,
         dashboards, null, null, null, null, null, null, null, null,
         mock(RepletRegistryManager.class), Optional.empty());
      // a user of another organization is hidden only in a multi tenant installation
      sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
   }

   @AfterEach
   void tearDown() {
      if(sutil != null) {
         sutil.close();
      }

      setResuming(null);
      ThreadContext.setContextPrincipal(threadPrincipal);
      OrganizationContextHolder.setCurrentOrgId(null);

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void u2OfXIsHiddenFromTheOrgAdminOfY() {
      assertTrue(identityService.isOrgMemberHiddenFrom(U2, builder.principalOf(Y_ADMIN.name, Y)));
      assertFalse(identityService.isOrgMemberHiddenFrom(U2, builder.principalOf(SITE.name, X)));
   }

   // the original request, by a site admin, dropped u2: the frozen members are u1 and siteAdmin

   @Test
   void resumeUnderSiteAdminRemovesTheDroppedMember() throws Exception {
      resume(builder.principalOf(SITE.name, X), frozen(List.of("u1", "siteAdmin"), null));

      assertNull(provider.getUser(U2));
   }

   @Test
   void resumeUnderOrgAdminOfOtherOrgRemovesTheDroppedMember() throws Exception {
      resume(builder.principalOf(Y_ADMIN.name, Y), frozen(List.of("u1", "siteAdmin"), null));

      assertNull(provider.getUser(U2), "the dropped member of the original request is kept");
      assertNotNull(provider.getUser(U1));
      assertNotNull(provider.getUser(SITE));
   }

   // the original request, by an org admin of X that could not see siteAdmin, kept siteAdmin

   @Test
   void resumeUnderSiteAdminKeepsTheMemberTheOriginalRequestKept() throws Exception {
      // the model of that request did not contain siteAdmin, the frozen decision does
      resume(builder.principalOf(SITE.name, X), frozen(List.of("u1", "siteAdmin"), null),
             List.of(U1));

      assertNotNull(provider.getUser(SITE), "a member the original request kept is deleted");
      assertNull(provider.getUser(U2));
   }

   @Test
   void anExplicitEmptyDecisionIsNotAMissingDecision() throws Exception {
      // everything of X was dropped, the principal of the resume would keep all of it (hidden)
      resume(builder.principalOf(Y_ADMIN.name, Y), frozen(List.of(), null));

      assertNull(provider.getUser(U1));
      assertNull(provider.getUser(U2));
   }

   @Test
   void theRequesterOfTheOriginalRequestIsNotDeleted() throws Exception {
      // u2 was the requester, it dropped itself: kept, whoever resumes
      resume(builder.principalOf(Y_ADMIN.name, Y),
             frozen(List.of("u1", "siteAdmin"), U2.convertToKey()));

      assertNotNull(provider.getUser(U2));
   }

   @Test
   void theRecovererIsNotProtectedAsTheRequester() throws Exception {
      // u1 of X resumes, it was dropped by the original request of siteAdmin
      resume(builder.principalOf(U1.name, X),
             frozen(List.of("siteAdmin"), SITE.convertToKey()));

      assertNull(provider.getUser(U1));
      assertNull(provider.getUser(U2));
      assertNotNull(provider.getUser(SITE));
   }

   // a record from before the fix has no decision: the principal of the resume decides as before

   @Test
   void aRecordWithoutADecisionFallsBackToThePrincipalOfTheResume() throws Exception {
      resume(builder.principalOf(SITE.name, X), null);
      assertNull(provider.getUser(U2));
   }

   @Test
   void aRecordWithAnUnreadableDecisionFallsBackToo() throws Exception {
      resume(builder.principalOf(SITE.name, X), "not json");
      assertNull(provider.getUser(U2));
   }

   // the write side: the original request journals what its own principal decided
   @Test
   void theOriginalRequestJournalsTheDecisionOfItsOwnPrincipal() throws Exception {
      String x2 = "b81308_x2";
      // the node "dies" after the journal write and the member step
      doThrow(new IllegalStateException("node died")).when(dashboards)
         .migrateRegistry(isNull(), any(), any());
      Principal yAdmin = builder.principalOf(Y_ADMIN.name, Y);
      ThreadContext.setContextPrincipal(yAdmin);
      FSOrganization oldOrg = (FSOrganization) provider.getOrganization(X);
      oldOrg.setMembers(provider.getOrganizationMembers(X));
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .id(x2).name("B81308X").oldName("B81308X").status(true)
         .members(List.of(member(U1), member(SITE))).build();
      Method m = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, Principal.class);
      m.setAccessible(true);
      OrgOperationJournal journal = OrgOperationJournal.getInstance();

      try {
         assertThrows(Exception.class, () -> m.invoke(identityService, oldOrg, model, provider,
                                                      yAdmin));
         Entry entry = journal.list().stream().filter(e -> e.involves(X)).findFirst()
            .orElseThrow();
         assertNotNull(entry.getDecision());
         Map<?, ?> decision = new ObjectMapper().readValue(entry.getDecision(), Map.class);
         // u2 was dropped, kept with the undeletable role because X is hidden from yAdmin
         assertEquals(Set.of("u1", "siteAdmin", "siteAdmins", "u2"),
                      new HashSet<>((List<?>) decision.get("members")));
         assertEquals(Y_ADMIN.convertToKey(), decision.get("requester"));
      }
      finally {
         journal.list().stream().filter(e -> e.involves(X) || e.involves(x2))
            .forEach(e -> journal.complete(e.getOpId()));
      }
   }

   @Test
   void theDecisionSurvivesTheRecordRoundTrip() throws Exception {
      String decision = frozen(List.of("u1"), U1.convertToKey());
      Entry entry = new Entry("op", Type.RENAME, X, "b81308_x2", "node", 1L,
                              OrgOperationJournal.State.FAILED, "{}", List.of(), null, decision);
      ObjectMapper mapper = new ObjectMapper();
      Entry read = mapper.readValue(mapper.writeValueAsString(entry), Entry.class);

      assertEquals(decision, read.getDecision());
      assertEquals(decision, entry.withState(OrgOperationJournal.State.RUNNING).getDecision());
   }

   @Test
   void aRecordWrittenBeforeTheFixHasNoDecision() throws Exception {
      String old = "{\"opId\":\"op\",\"type\":\"RENAME\",\"fromId\":\"a\",\"toId\":\"b\"," +
         "\"node\":\"n\",\"started\":1,\"state\":\"FAILED\",\"payload\":\"{}\",\"targets\":[]," +
         "\"providerName\":null}";
      assertNull(new ObjectMapper().readValue(old, Entry.class).getDecision());
   }

   @Test
   void aRecordWithFieldsOfALaterVersionIsStillRead() throws Exception {
      // what a node that does not know the decision field does with a record that has it
      String future = "{\"opId\":\"op\",\"type\":\"RENAME\",\"fromId\":\"a\",\"toId\":\"b\"," +
         "\"node\":\"n\",\"started\":1,\"state\":\"FAILED\",\"payload\":\"{}\",\"targets\":[]," +
         "\"providerName\":null,\"decision\":\"x\",\"another\":1}";
      assertEquals("op", new ObjectMapper().readValue(future, Entry.class).getOpId());
   }

   private static String frozen(List<String> members, String requester) throws Exception {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("members", members);
      map.put("requester", requester);
      return new ObjectMapper().writeValueAsString(map);
   }

   private void resume(Principal principal, String decision) throws Exception {
      resume(principal, decision, List.of(U1, SITE));
   }

   // the model of the original request lists the given users, u2 is dropped
   private void resume(Principal principal, String decision, List<IdentityID> modelMembers)
      throws Exception
   {
      ThreadContext.setContextPrincipal(principal);
      setResuming(new Entry("op", Type.RENAME, X, X, "node", 1L,
                            OrgOperationJournal.State.FAILED, "{}", List.of(), null, decision));
      FSOrganization oldOrg = (FSOrganization) provider.getOrganization(X);
      oldOrg.setMembers(provider.getOrganizationMembers(X));
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .id(X).name("B81308X").oldName("B81308X").status(true)
         .members(modelMembers.stream().map(OrgRecoveryPrincipalTest::member).toList()).build();
      Method m = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, Principal.class);
      m.setAccessible(true);
      m.invoke(identityService, oldOrg, model, provider, principal);
   }

   private static IdentityModel member(IdentityID id) {
      return IdentityModel.builder().identityID(id).type(Identity.USER).build();
   }

   private static void setResuming(Entry entry) {
      try {
         Field f = IdentityService.class.getDeclaredField("RESUMING");
         f.setAccessible(true);
         @SuppressWarnings("unchecked")
         ThreadLocal<Entry> local = (ThreadLocal<Entry>) f.get(null);

         if(entry == null) {
            local.remove();
         }
         else {
            local.set(entry);
         }
      }
      catch(ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }
   }
}
