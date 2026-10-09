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
package inetsoft.web.admin.security;

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.admin.security.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77833: a user, group or role rename moves the old name's data (schedule tasks,
 * dashboards, registries, autosave files, permission grants, inheriting roles, the organization's
 * member list) to the new name only once the renamed identity is saved. A failed save keeps the
 * data on the identity that still exists; a save that fails after the new record was written
 * (the old one could not be removed) still moves it.
 * <p>
 * Uses a real FileAuthenticationProvider with the real FileAuthorizationProvider registered as
 * its change listener (so a group or role removal's delete event really strips the old name),
 * the real ScheduleManager bean and real autosave storage. The dashboard manager and the
 * replet/dashboard registries are mocks: their part is checked as calls, not stored state.
 * Failures are injected into the provider's identity storage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityServiceRenameFailureTest {
   private static final String ORG = "idrnorg";
   private static final IdentityID DAVE = new IdentityID("dave", ORG);

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   private SecurityTestDataBuilder builder;
   private MockedStatic<SUtil> sutilStatic;
   private SRPrincipal dave;
   private IdentityService service;
   private DashboardManager dashboardManager;
   private RepletRegistryManager repletRegistryManager;
   private DashboardRegistryManager dashboardRegistryManager;
   private final List<String> taskNames = new ArrayList<>();
   private final List<String> permPaths = new ArrayList<>();

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("idrnOrg", ORG)
         .addOrgAdminRole("idrnOrgAdmin", ORG)
         .addUser("dave", ORG, "password")
         .addUserToRole("dave", "idrnOrgAdmin", ORG);
      builder.setup();
      // registers the authorization provider as the authentication provider's change listener
      SecurityProvider provider = CompositeSecurityProvider.create(provider(), authz());
      securityEngineOverrides.setSecurityEnabled(true);
      securityEngineOverrides.setSecurityProvider(provider);
   }

   @AfterAll
   void teardownAll() {
      securityEngineOverrides.clear();

      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      dave = builder.principalOf("dave", ORG);
      dave.setProperty("__internal__", "true");
      ThreadContext.setContextPrincipal(dave);
      ThreadContext.setPrincipal(dave);
      ScheduleClient scheduleClient =
         (ScheduleClient) ReflectionTestUtils.getField(scheduleManager, "scheduleClient");
      reset(scheduleClient);
      when(scheduleClient.isReady()).thenReturn(true);

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));
      when(securityProvider.getOrganization(anyString()))
         .thenAnswer(inv -> provider().getOrganization(inv.getArgument(0)));
      AuthorizationChain chain = mock(AuthorizationChain.class);
      when(chain.getProviders()).thenReturn(List.of(authz()));
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.getSecurityProvider()).thenReturn(securityProvider);
      when(engine.getAuthorizationChain()).thenReturn(Optional.of(chain));

      dashboardManager = mock(DashboardManager.class);
      repletRegistryManager = mock(RepletRegistryManager.class);
      dashboardRegistryManager = mock(DashboardRegistryManager.class);
      // the real manager runs the user's dashboard moves holding its lock
      doAnswer(inv -> {
         ((Runnable) inv.getArgument(0)).run();
         return null;
      }).when(dashboardManager).runLocked(any());
      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", engine);
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(service, "dashboardManager", dashboardManager);
      ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);
      ReflectionTestUtils.setField(service, "repletRegistryManager", repletRegistryManager);
      ReflectionTestUtils.setField(service, "dashboardRegistryManager", dashboardRegistryManager);
      ReflectionTestUtils.setField(service, "indexedStorage", mock(IndexedStorage.class));
      ReflectionTestUtils.setField(service, "themeService", mock(IdentityThemeService.class));
      ReflectionTestUtils.setField(service, "sessionLicenseServiceProvider",
                                   mock(SessionLicenseServiceProvider.class));
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);
      sutilStatic.close();
      orgTasks().values().removeIf(t -> t != null && taskNames.contains(t.getName()));
      taskNames.clear();
      permPaths.forEach(path -> authz().removePermission(ResourceType.VIEWSHEET, path, ORG));
      permPaths.clear();
   }

   // ---- user

   @Test
   void renameUserKeepsDataWhenTheNewUserIsNotSaved() throws Exception {
      FSUser user = addUser("alice", null, null);
      IdentityID oldId = user.getIdentityID();
      IdentityID newId = new IdentityID("alice2", ORG);
      saveTask("AliceOwned", oldId, null);
      saveTask("AliceRunAs", DAVE, user);
      grant("vs-alice", p -> p.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), ORG));
      String autoSave = writeAutoSave(oldId);

      Throwable thrown = syncIdentity(renamedUser(newId), oldId,
                                      fail("userStorage", s -> failPut(s, newId)));

      assertInstanceOf(MessageException.class, thrown);
      assertNotNull(provider().getUser(oldId));
      assertNull(provider().getUser(newId));
      assertEquals(oldId, task("AliceOwned").getOwner(), "task owner stays on the existing user");
      assertEquals(oldId, task("AliceRunAs").getIdentity().getIdentityID(),
                   "execute-as stays on the existing user");
      assertEquals(Set.of(oldId), userGrants("vs-alice"));
      assertTrue(autoSaveFiles().contains(autoSave), "autosave file keeps the old name");
      verify(dashboardManager, never()).setDashboards(eq(new DefaultIdentity(newId, Identity.USER)), any());
      verify(dashboardManager, never()).removeDashboards(any());
      verifyNoInteractions(repletRegistryManager);
      verify(dashboardRegistryManager, never()).renameUser(any(), any());
   }

   @Test
   void renameUserMovesDataWhenOnlyTheOldUserIsNotRemoved() throws Exception {
      FSUser user = addUser("amy", null, null);
      IdentityID oldId = user.getIdentityID();
      IdentityID newId = new IdentityID("amy2", ORG);
      saveTask("AmyOwned", oldId, null);
      saveTask("AmyRunAs", DAVE, user);
      grant("vs-amy", p -> p.setUserGrantsForOrg(ResourceAction.READ, Set.of("amy"), ORG));
      writeAutoSave(oldId);

      // the new user is saved, the old one is not removed, so the provider's change listener
      // never renames the grantee
      Throwable thrown = syncIdentity(renamedUser(newId), oldId,
                                      fail("userStorage", s -> failRemove(s, oldId)));

      assertInstanceOf(MessageException.class, thrown);
      assertNotNull(provider().getUser(oldId));
      assertNotNull(provider().getUser(newId));
      assertEquals(newId, task("AmyOwned").getOwner());
      assertEquals(newId, task("AmyRunAs").getIdentity().getIdentityID());
      assertEquals(Set.of(newId), userGrants("vs-amy"));
      assertTrue(autoSaveFiles().stream().anyMatch(f -> f.contains(newId.convertToKey())),
                 "autosave file moved to the new name");
      verify(repletRegistryManager).renameUser(oldId, newId);
      verify(dashboardRegistryManager).renameUser(oldId, newId);
      verify(dashboardManager).removeDashboards(new DefaultIdentity(oldId, Identity.USER));
   }

   @Test
   void renameUserKeepsDataWhenTheSaveCannotBeChecked() throws Exception {
      FSUser user = addUser("ann", null, null);
      IdentityID oldId = user.getIdentityID();
      IdentityID newId = new IdentityID("ann2", ORG);
      saveTask("AnnOwned", oldId, null);
      grant("vs-ann", p -> p.setUserGrantsForOrg(ResourceAction.READ, Set.of("ann"), ORG));

      // the save fails and the storage cannot be read either: the new user is not confirmed
      Throwable thrown = syncIdentity(renamedUser(newId), oldId, fail("userStorage", s -> {
         failPut(s, newId);
         doThrow(new IllegalStateException("simulated read failure"))
            .when(s).get(newId.convertToKey());
      }));

      assertInstanceOf(MessageException.class, thrown);
      assertEquals(oldId, task("AnnOwned").getOwner());
      assertEquals(Set.of(oldId), userGrants("vs-ann"));
      verifyNoInteractions(repletRegistryManager);
   }

   @Test
   void renameUserMovesData() throws Exception {
      FSUser user = addUser("abe", null, null);
      IdentityID oldId = user.getIdentityID();
      IdentityID newId = new IdentityID("abe2", ORG);
      saveTask("AbeOwned", oldId, null);
      grant("vs-abe", p -> p.setUserGrantsForOrg(ResourceAction.READ, Set.of("abe"), ORG));
      writeAutoSave(oldId);
      List<Boolean> oldUserExistsOnRead = new ArrayList<>();
      when(dashboardManager.getDashboards(new DefaultIdentity(oldId, Identity.USER))).thenAnswer(inv -> {
         oldUserExistsOnRead.add(provider().getUser(oldId) != null);
         return new String[] { "d1" };
      });

      assertNull(syncIdentity(renamedUser(newId), oldId, null));

      assertNull(provider().getUser(oldId));
      assertNotNull(provider().getUser(newId));
      assertEquals(newId, task("AbeOwned").getOwner());
      // renamed once, by the change listener and updateIdentityPermissions together
      assertEquals(Set.of(newId), userGrants("vs-abe"));
      assertTrue(autoSaveFiles().stream().anyMatch(f -> f.contains(newId.convertToKey())));
      assertEquals(List.of(true), oldUserExistsOnRead,
                   "dashboards are read while the old user still exists");
      verify(dashboardManager).setDashboards(new DefaultIdentity(newId, Identity.USER),
                                             new String[] { "d1" });
      verify(repletRegistryManager).renameUser(oldId, newId);
      verify(dashboardRegistryManager).renameUser(oldId, newId);
   }

   // Bug #78101: a dashboard that a request still running under the old name creates after the
   // dashboards were read is moved too, and the selection and the registries are moved holding the
   // dashboard manager's lock, which the create holds while it adds the dashboard
   @Test
   void renameUserMovesDashboardsCreatedAfterTheReadHoldingTheLock() throws Exception {
      FSUser user = addUser("ava", null, null);
      IdentityID oldId = user.getIdentityID();
      IdentityID newId = new IdentityID("ava2", ORG);
      Identity oldIdentity = new DefaultIdentity(oldId, Identity.USER);
      when(dashboardManager.getDashboards(oldIdentity)).thenReturn(new String[] { "d1" });
      when(dashboardManager.getDashboards(oldIdentity, false))
         .thenReturn(new String[] { "d1", "d2" });
      boolean[] locked = new boolean[1];
      List<String> unlocked = new ArrayList<>();
      doAnswer(inv -> {
         locked[0] = true;

         try {
            ((Runnable) inv.getArgument(0)).run();
         }
         finally {
            locked[0] = false;
         }

         return null;
      }).when(dashboardManager).runLocked(any());
      doAnswer(inv -> recordUnlocked(locked, unlocked, "setDashboards"))
         .when(dashboardManager).setDashboards(any(), any());
      doAnswer(inv -> recordUnlocked(locked, unlocked, "removeDashboards"))
         .when(dashboardManager).removeDashboards(any());
      doAnswer(inv -> recordUnlocked(locked, unlocked, "replet.renameUser"))
         .when(repletRegistryManager).renameUser(any(), any());
      doAnswer(inv -> recordUnlocked(locked, unlocked, "dashboardRegistry.renameUser"))
         .when(dashboardRegistryManager).renameUser(any(), any());
      doAnswer(inv -> recordUnlocked(locked, unlocked, "addRenamedUser"))
         .when(dashboardManager).addRenamedUser(any());

      assertNull(syncIdentity(renamedUser(newId), oldId, null));

      verify(dashboardManager).setDashboards(new DefaultIdentity(newId, Identity.USER),
                                             new String[] { "d1", "d2" });
      verify(dashboardManager).removeDashboards(oldIdentity);
      verify(repletRegistryManager).renameUser(oldId, newId);
      verify(dashboardRegistryManager).renameUser(oldId, newId);
      verify(dashboardManager).addRenamedUser(oldId);
      assertEquals(List.of(), unlocked, "moved without holding the dashboard manager's lock");
   }

   private static Object recordUnlocked(boolean[] locked, List<String> unlocked, String call) {
      if(!locked[0]) {
         unlocked.add(call);
      }

      return null;
   }

   @Test
   void setUserInfoKeepsOrganizationMemberWhenTheNewUserIsNotSaved() throws Exception {
      FSUser user = addUser("bob", null, null);
      IdentityID newId = new IdentityID("bob2", ORG);
      addOrganizationMember("bob");

      Throwable thrown = setUserInfo(user, "bob2", fail("userStorage", s -> failPut(s, newId)));

      assertInstanceOf(MessageException.class, thrown);
      assertTrue(organizationMembers().contains("bob"));
      assertFalse(organizationMembers().contains("bob2"));
   }

   @Test
   void setUserInfoRenamesOrganizationMemberWhenOnlyTheOldUserIsNotRemoved() throws Exception {
      FSUser user = addUser("bea", null, null);
      addOrganizationMember("bea");

      Throwable thrown = setUserInfo(user, "bea2",
                                     fail("userStorage", s -> failRemove(s, user.getIdentityID())));

      assertInstanceOf(MessageException.class, thrown);
      assertTrue(organizationMembers().contains("bea2"));
      assertFalse(organizationMembers().contains("bea"));
   }

   @Test
   void setUserInfoRenamesOrganizationMember() throws Exception {
      FSUser user = addUser("ben", null, null);
      addOrganizationMember("ben");

      assertNull(setUserInfo(user, "ben2", null));

      assertNotNull(provider().getUser(new IdentityID("ben2", ORG)));
      assertTrue(organizationMembers().contains("ben2"));
      assertFalse(organizationMembers().contains("ben"));
   }

   // ---- group

   @Test
   void renameGroupKeepsDataWhenTheNewGroupIsNotSaved() throws Exception {
      FSGroup group = addGroup("grp");
      IdentityID oldId = group.getIdentityID();
      IdentityID newId = new IdentityID("grp2", ORG);
      FSUser member = addUser("gina", new String[] { "grp" }, null);
      saveTask("GrpRunAs", DAVE, group);
      grant("vs-grp", p -> p.setGroupGrantsForOrg(ResourceAction.READ, Set.of("grp"), ORG));
      addOrganizationMember("grp");
      dave.setGroups(new String[] { "grp" });

      Throwable thrown = setGroupInfo(group, "grp2", List.of(member.getIdentityID()),
                                      fail("groupStorage", s -> failPut(s, newId)));

      assertInstanceOf(MessageException.class, thrown);
      assertNotNull(provider().getGroup(oldId));
      assertNull(provider().getGroup(newId));
      assertEquals(oldId, task("GrpRunAs").getIdentity().getIdentityID());
      assertEquals(Set.of(oldId), groupGrants("vs-grp"));
      assertArrayEquals(new String[] { "grp" }, provider().getUser(member.getIdentityID()).getGroups());
      assertArrayEquals(new String[] { "grp" }, dave.getGroups());
      assertTrue(organizationMembers().contains("grp"));
      assertFalse(organizationMembers().contains("grp2"));
      verify(dashboardManager, never()).removeDashboards(any());
   }

   @Test
   void renameGroupMovesDataWhenTheOldGroupIsNotRemoved() throws Exception {
      FSGroup group = addGroup("gold");
      IdentityID oldId = group.getIdentityID();
      IdentityID newId = new IdentityID("gold2", ORG);
      saveTask("GoldRunAs", DAVE, group);
      grant("vs-gold", p -> p.setGroupGrantsForOrg(ResourceAction.READ, Set.of("gold"), ORG));

      Throwable thrown = syncIdentity(renamedGroup(newId), oldId,
                                      fail("groupStorage", s -> failRemove(s, oldId)));

      assertInstanceOf(MessageException.class, thrown);
      assertNotNull(provider().getGroup(oldId));
      assertNotNull(provider().getGroup(newId));
      assertEquals(newId, task("GoldRunAs").getIdentity().getIdentityID());
      assertEquals(Set.of(newId), groupGrants("vs-gold"));
   }

   @Test
   void renameGroupMovesData() throws Exception {
      FSGroup group = addGroup("gem");
      IdentityID oldId = group.getIdentityID();
      IdentityID newId = new IdentityID("gem2", ORG);
      FSUser member = addUser("gus", new String[] { "gem" }, null);
      saveTask("GemRunAs", DAVE, group);
      grant("vs-gem", p -> p.setGroupGrantsForOrg(ResourceAction.READ, Set.of("gem"), ORG));
      addOrganizationMember("gem");
      dave.setGroups(new String[] { "gem" });

      assertNull(setGroupInfo(group, "gem2", List.of(member.getIdentityID()), null));

      assertNull(provider().getGroup(oldId));
      assertNotNull(provider().getGroup(newId));
      assertEquals(newId, task("GemRunAs").getIdentity().getIdentityID());
      // the old group's removal is a delete event, so the grant must have moved before it
      assertEquals(Set.of(newId), groupGrants("vs-gem"));
      assertArrayEquals(new String[] { "gem2" }, provider().getUser(member.getIdentityID()).getGroups());
      assertArrayEquals(new String[] { "gem2" }, dave.getGroups());
      assertTrue(organizationMembers().contains("gem2"));
      assertFalse(organizationMembers().contains("gem"));
      verify(dashboardManager).removeDashboards(new DefaultIdentity(oldId, Identity.GROUP));
   }

   // ---- role

   @Test
   void renameRoleKeepsDataWhenTheNewRoleIsNotSaved() throws Exception {
      FSRole parent = addRole("rparent", null);
      FSRole child = addRole("rchild", new IdentityID[] { parent.getIdentityID() });
      IdentityID oldId = parent.getIdentityID();
      IdentityID newId = new IdentityID("rparent2", ORG);
      FSUser member = addUser("rita", null, new IdentityID[] { oldId });
      grant("vs-rparent", p -> p.setRoleGrantsForOrg(ResourceAction.READ, Set.of("rparent"), ORG));

      Throwable thrown = setRoleInfo("rparent", "rparent2", List.of(member.getIdentityID()),
                                     fail("roleStorage", s -> failPut(s, newId)));

      assertInstanceOf(MessageException.class, thrown);
      assertNotNull(provider().getRole(oldId));
      assertNull(provider().getRole(newId));
      assertArrayEquals(new IdentityID[] { oldId }, provider().getRole(child.getIdentityID()).getRoles());
      assertEquals(Set.of(oldId), roleGrants("vs-rparent"));
      assertArrayEquals(new IdentityID[] { oldId }, provider().getUser(member.getIdentityID()).getRoles());
   }

   @Test
   void renameRoleMovesDataWhenTheOldRoleIsNotRemoved() throws Exception {
      FSRole parent = addRole("rpold", null);
      FSRole child = addRole("rcold", new IdentityID[] { parent.getIdentityID() });
      IdentityID oldId = parent.getIdentityID();
      IdentityID newId = new IdentityID("rpold2", ORG);
      grant("vs-rpold", p -> p.setRoleGrantsForOrg(ResourceAction.READ, Set.of("rpold"), ORG));

      Throwable thrown = syncIdentity(renamedRole(newId), oldId,
                                      fail("roleStorage", s -> failRemove(s, oldId)));

      assertInstanceOf(MessageException.class, thrown);
      assertNotNull(provider().getRole(oldId));
      assertNotNull(provider().getRole(newId));
      assertArrayEquals(new IdentityID[] { newId }, provider().getRole(child.getIdentityID()).getRoles());
      assertEquals(Set.of(newId), roleGrants("vs-rpold"));
   }

   @Test
   void renameRoleMovesData() throws Exception {
      FSRole parent = addRole("rpok", null);
      FSRole child = addRole("rcok", new IdentityID[] { parent.getIdentityID() });
      IdentityID oldId = parent.getIdentityID();
      IdentityID newId = new IdentityID("rpok2", ORG);
      FSUser member = addUser("rex", null, new IdentityID[] { oldId });
      grant("vs-rpok", p -> p.setRoleGrantsForOrg(ResourceAction.READ, Set.of("rpok"), ORG));

      assertNull(setRoleInfo("rpok", "rpok2", List.of(member.getIdentityID()), null));

      assertNull(provider().getRole(oldId));
      assertNotNull(provider().getRole(newId));
      // the old role's removal is a delete event, so inheritance and the grant must have moved
      // before it
      assertArrayEquals(new IdentityID[] { newId }, provider().getRole(child.getIdentityID()).getRoles());
      assertEquals(Set.of(newId), roleGrants("vs-rpok"));
      assertArrayEquals(new IdentityID[] { newId }, provider().getUser(member.getIdentityID()).getRoles());
   }

   // ---- helpers

   private interface Body {
      void run() throws Exception;
   }

   private Throwable syncIdentity(Identity identity, IdentityID oID,
                                  StorageFailure failure)
      throws Exception
   {
      Method method = IdentityService.class.getDeclaredMethod(
         "syncIdentity", EditableAuthenticationProvider.class, Identity.class, IdentityID.class);
      method.setAccessible(true);
      return run(failure, () -> method.invoke(service, provider(), identity, oID));
   }

   private Throwable setUserInfo(FSUser user, String newName, StorageFailure failure)
      throws Exception
   {
      EditUserPaneModel model = EditUserPaneModel.builder()
         .name(newName).oldName(user.getName()).organization(ORG).build();
      Method method = IdentityService.class.getDeclaredMethod(
         "setUserInfo", FSUser.class, EditUserPaneModel.class, EditableAuthenticationProvider.class,
         List.class);
      method.setAccessible(true);
      return run(failure, () -> method.invoke(service, user, model, provider(),
                                               new ArrayList<IdentityID>()));
   }

   private Throwable setGroupInfo(FSGroup group, String newName, List<IdentityID> users,
                                  StorageFailure failure)
      throws Exception
   {
      EditGroupPaneModel model = EditGroupPaneModel.builder()
         .name(newName).oldName(group.getName()).organization(ORG).build();
      Method method = IdentityService.class.getDeclaredMethod(
         "setGroupInfo", FSGroup.class, EditGroupPaneModel.class,
         EditableAuthenticationProvider.class, IdentityID[].class, IdentityID[].class, List.class,
         List.class, Map.class);
      method.setAccessible(true);
      return run(failure, () -> method.invoke(
         service, group, model, provider(), provider().getUsers(), provider().getGroups(),
         new ArrayList<>(users), new ArrayList<IdentityID>(), new HashMap<IdentityID, String>()));
   }

   private Throwable setRoleInfo(String oldName, String newName, List<IdentityID> users,
                                 StorageFailure failure)
      throws Exception
   {
      EditRolePaneModel model = EditRolePaneModel.builder()
         .name(newName).oldName(oldName).organization(ORG).isSysAdmin(false).isOrgAdmin(false)
         .build();
      Method method = IdentityService.class.getDeclaredMethod(
         "setRoleInfo", EditRolePaneModel.class, EditableAuthenticationProvider.class,
         IdentityID[].class, IdentityID[].class, String[].class, List.class, List.class,
         List.class, java.security.Principal.class);
      method.setAccessible(true);
      return run(failure, () -> method.invoke(
         service, model, provider(), provider().getUsers(), provider().getGroups(), new String[0],
         new ArrayList<>(users), new ArrayList<IdentityID>(), new ArrayList<IdentityID>(), dave));
   }

   /**
    * Runs the body with one of the provider's identity storages swapped for a mock that
    * delegates to it, with the failure's operations stubbed to fail. No swap when failure is null.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private Throwable run(StorageFailure failure, Body body) throws Exception {
      Field field = null;
      KeyValueStorage real = null;

      if(failure != null) {
         field = FileAuthenticationProvider.class.getDeclaredField(failure.field());
         field.setAccessible(true);
         real = (KeyValueStorage) field.get(provider());
         KeyValueStorage failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
         failure.stubs().accept(failing);
         field.set(provider(), failing);
      }

      try {
         body.run();
         return null;
      }
      catch(InvocationTargetException e) {
         return e.getCause();
      }
      finally {
         if(field != null) {
            field.set(provider(), real);
         }
      }
   }

   private static StorageFailure fail(String field, Consumer<KeyValueStorage<?>> stubs) {
      return new StorageFailure(field, stubs);
   }

   private record StorageFailure(String field, Consumer<KeyValueStorage<?>> stubs) {
   }

   private static void failPut(KeyValueStorage<?> storage, IdentityID id) {
      doReturn(CompletableFuture.failedFuture(new IOException("simulated put failure")))
         .when(storage).put(eq(id.convertToKey()), any());
   }

   private static void failRemove(KeyValueStorage<?> storage, IdentityID id) {
      doReturn(CompletableFuture.failedFuture(new IOException("simulated remove failure")))
         .when(storage).remove(id.convertToKey());
   }

   private FSUser renamedUser(IdentityID id) {
      FSUser user = new FSUser(id);
      user.setOrganization(ORG);
      return user;
   }

   private FSGroup renamedGroup(IdentityID id) {
      FSGroup group = new FSGroup(id);
      group.setOrganization(ORG);
      return group;
   }

   private FSRole renamedRole(IdentityID id) {
      FSRole role = new FSRole(id, (String) null);
      role.setOrganization(ORG);
      return role;
   }

   private FSUser addUser(String name, String[] groups, IdentityID[] roles) {
      FSUser user = new FSUser(new IdentityID(name, ORG));
      user.setOrganization(ORG);

      if(groups != null) {
         user.setGroups(groups);
      }

      if(roles != null) {
         user.setRoles(roles);
      }

      provider().addUser(user);
      return (FSUser) provider().getUser(user.getIdentityID());
   }

   private FSGroup addGroup(String name) {
      FSGroup group = renamedGroup(new IdentityID(name, ORG));
      provider().addGroup(group);
      return (FSGroup) provider().getGroup(group.getIdentityID());
   }

   private FSRole addRole(String name, IdentityID[] roles) {
      FSRole role = renamedRole(new IdentityID(name, ORG));

      if(roles != null) {
         role.setRoles(roles);
      }

      provider().addRole(role);
      return (FSRole) provider().getRole(role.getIdentityID());
   }

   private void addOrganizationMember(String name) {
      FSOrganization org = (FSOrganization) provider().getOrganization(ORG);
      List<String> members = new ArrayList<>(organizationMembers());
      members.add(name);
      org.setMembers(members.toArray(new String[0]));
      provider().setOrganization(ORG, org);
   }

   private List<String> organizationMembers() {
      String[] members = provider().getOrganization(ORG).getMembers();
      return members == null ? List.of() : Arrays.asList(members);
   }

   private void grant(String path, Consumer<Permission> grants) {
      Permission permission = new Permission();
      grants.accept(permission);
      authz().setPermission(ResourceType.VIEWSHEET, path, permission, ORG);
      permPaths.add(path);
   }

   private Set<IdentityID> userGrants(String path) {
      return new HashSet<>(permission(path).getOrgScopedUserGrants(ResourceAction.READ, organization()));
   }

   private Set<IdentityID> groupGrants(String path) {
      return new HashSet<>(permission(path).getOrgScopedGroupGrants(ResourceAction.READ, organization()));
   }

   private Set<IdentityID> roleGrants(String path) {
      return new HashSet<>(permission(path).getOrgScopedRoleGrants(ResourceAction.READ, organization()));
   }

   private Permission permission(String path) {
      Permission permission = authz().getPermission(ResourceType.VIEWSHEET, path, ORG);
      assertNotNull(permission, "permission of " + path);
      return permission;
   }

   private Organization organization() {
      return provider().getOrganization(ORG);
   }

   private String writeAutoSave(IdentityID user) throws Exception {
      String file = AutoSaveUtils.RECYCLE_PREFIX + "1^128^" + user.convertToKey() + "^vs1^rename";
      AutoSaveUtils.writeAutoSaveFile("x".getBytes(), file, dave);
      assertTrue(autoSaveFiles().contains(file), "autosave file written");
      return file;
   }

   private List<String> autoSaveFiles() {
      return AutoSaveUtils.getAutoSavedFiles(dave, true);
   }

   private void saveTask(String name, IdentityID owner, Identity executeAs) throws Exception {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(owner);
      task.setIdentity(executeAs);
      taskNames.add(name);
      scheduleManager.save(List.of(task), ORG);
      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "task saved");
   }

   private ScheduleTask task(String name) {
      return orgTasks().values().stream()
         .filter(t -> t != null && name.equals(t.getName()))
         .findFirst()
         .orElseThrow(() -> new AssertionError("task " + name + " not found"));
   }

   @SuppressWarnings("unchecked")
   private Map<String, ScheduleTask> orgTasks() {
      return (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(ORG);
   }

   private FileAuthenticationProvider provider() {
      return (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider");
   }

   private FileAuthorizationProvider authz() {
      return (FileAuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider");
   }
}
