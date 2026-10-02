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
package inetsoft.sree.internal;

/*
 * Bug #77161: DataCycleManager.identityRemoved/identityRenamed did nothing, so a removed or
 * renamed user or group stayed in the start/end/failure/exceed notification recipients of the
 * data cycles, and a later identity with the old name received the cycle notifications.
 */

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class,
                                  DataCycleManagerOrgLifecycleTest.DataCycleManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataCycleManagerIdentityHookTest {
   @Autowired
   private DataCycleManager dataCycleManager;

   @Autowired
   private ScheduleManager scheduleManager;

   private SecurityTestDataBuilder builder;

   @AfterEach
   void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void userRemoved_removesBareAndUserTokensFromAllLists() throws Exception {
      String org = "dc_hook_user_rm";
      setupOrgs(org);
      seed(org, "Cycle", ALL, true);

      removeViaScheduleManager(new User(new IdentityID("bob", org)), org);

      assertAllLists(org, "Cycle", "bob(Group),carol@x.com");
   }

   @Test
   void groupRemoved_removesOnlyGroupToken() throws Exception {
      String org = "dc_hook_group_rm";
      setupOrgs(org);
      seed(org, "Cycle", ALL, true);

      removeViaScheduleManager(new Group(new IdentityID("bob", org)), org);

      assertAllLists(org, "Cycle", "bob, bob(User),carol@x.com");
   }

   @Test
   void userRenamed_renamesKeepingFormDelimitersAndSpacing() throws Exception {
      String org = "dc_hook_user_mv";
      setupOrgs(org);
      seed(org, "Cycle", ALL, true);

      OrganizationManager.runInOrgScope(org, () -> {
         scheduleManager.identityRenamed(new IdentityID("bob", org),
                                         new User(new IdentityID("bob2", org)));
         return null;
      });

      assertAllLists(org, "Cycle", "bob2, bob2(User);bob(Group),carol@x.com");
   }

   @Test
   void groupRenamed_renamesOnlyGroupToken() throws Exception {
      String org = "dc_hook_group_mv";
      setupOrgs(org);
      seed(org, "Cycle", ALL, true);

      OrganizationManager.runInOrgScope(org, () -> {
         scheduleManager.identityRenamed(new IdentityID("bob", org),
                                         new Group(new IdentityID("bob2", org)));
         return null;
      });

      assertAllLists(org, "Cycle", "bob, bob(User);bob2(Group),carol@x.com");
   }

   @Test
   void notifyOffLists_areCleanedToo() throws Exception {
      String org = "dc_hook_notify_off";
      setupOrgs(org);
      seed(org, "Cycle", ALL, false);

      OrganizationManager.runInOrgScope(org, () -> {
         dataCycleManager.identityRemoved(
            new DefaultIdentity(new IdentityID("bob", org), Identity.USER));
         return null;
      });

      DataCycleManager.CycleInfo info = assertAllLists(org, "Cycle", "bob(Group),carol@x.com");
      assertFalse(info.isStartNotify());
      assertFalse(info.isEndNotify());
      assertFalse(info.isFailureNotify());
      assertFalse(info.isExceedNotify());
   }

   @Test
   void roleAndOrganization_areNoOps() throws Exception {
      String org = "dc_hook_role";
      setupOrgs(org);
      seed(org, "Cycle", ALL, true);
      DataCycleManager spy = spy(dataCycleManager);

      OrganizationManager.runInOrgScope(org, () -> {
         spy.identityRemoved(new Role(new IdentityID("bob", org)));
         spy.identityRenamed("bob", new Role(new IdentityID("bob2", org)));
         spy.identityRemoved(
            new DefaultIdentity(new IdentityID("bob", org), Identity.ORGANIZATION));
         return null;
      });

      assertAllLists(org, "Cycle", ALL);
      verify(spy, never()).setCycleInfo(any(), any(), any());
      verify(spy, never()).save();
   }

   @Test
   void onlyChangedCyclesWritten_andSavedOnce() throws Exception {
      String org = "dc_hook_save";
      setupOrgs(org);
      seed(org, "First", "bob", true);
      seed(org, "Second", "bob(User)", true);
      seed(org, "Other", "alice,bob(Group)", true);
      DataCycleManager spy = spy(dataCycleManager);

      OrganizationManager.runInOrgScope(org, () -> {
         spy.identityRemoved(new DefaultIdentity(new IdentityID("bob", org), Identity.USER));
         return null;
      });

      verify(spy).setCycleInfo(eq("First"), eq(org), any());
      verify(spy).setCycleInfo(eq("Second"), eq(org), any());
      verify(spy, never()).setCycleInfo(eq("Other"), any(), any());
      verify(spy, times(1)).save();
      assertEquals("alice,bob(Group)", dataCycleManager.getCycleInfo("Other", org).getStartEmail());
   }

   @Test
   void noMatch_writesNothing() throws Exception {
      String org = "dc_hook_nomatch";
      setupOrgs(org);
      seed(org, "Cycle", "alice,carol@x.com", true);
      DataCycleManager spy = spy(dataCycleManager);

      OrganizationManager.runInOrgScope(org, () -> {
         spy.identityRenamed("bob", new User(new IdentityID("bob2", org)));
         return null;
      });

      verify(spy, never()).setCycleInfo(any(), any(), any());
      verify(spy, never()).save();
   }

   // a site admin in org A removes a user of org B; both orgs have a cycle with the same name
   @Test
   void crossOrgRemove_updatesIdentityOrgOnly() throws Exception {
      String orgA = "dc_hook_admin_org";
      String orgB = "dc_hook_user_org";
      setupOrgs(orgA, orgB);
      seed(orgA, "Shared", ALL, true);
      seed(orgA, "OnlyA", ALL, true);
      seed(orgB, "Shared", ALL, true);
      seed(orgB, "OnlyB", ALL, true);

      removeViaScheduleManager(new User(new IdentityID("bob", orgB)), orgA);

      assertAllLists(orgB, "Shared", "bob(Group),carol@x.com");
      assertAllLists(orgB, "OnlyB", "bob(Group),carol@x.com");
      assertAllLists(orgA, "Shared", ALL);
      assertAllLists(orgA, "OnlyA", ALL);
   }

   @Test
   void crossOrgRename_updatesIdentityOrgOnly() throws Exception {
      String orgA = "dc_hook_admin_org2";
      String orgB = "dc_hook_user_org2";
      setupOrgs(orgA, orgB);
      seed(orgA, "OnlyA", ALL, true);
      seed(orgB, "OnlyB", ALL, true);

      OrganizationManager.runInOrgScope(orgA, () -> {
         dataCycleManager.identityRenamed("bob", new Group(new IdentityID("bob2", orgB)));
         return null;
      });

      assertAllLists(orgB, "OnlyB", "bob, bob(User);bob2(Group),carol@x.com");
      assertAllLists(orgA, "OnlyA", ALL);
   }

   // UserTreeService runs updateCycleInfoNotify after the hook, which must not rename again
   @Test
   void userRename_hookThenUpdateCycleInfoNotify_isIdempotent() throws Exception {
      String org = "dc_hook_legacy_user";
      setupOrgs(org);
      seed(org, "Cycle", ALL, true);

      OrganizationManager.runInOrgScope(org, () -> {
         scheduleManager.identityRenamed(new IdentityID("bob", org),
                                         new User(new IdentityID("bobby", org)));
         dataCycleManager.updateCycleInfoNotify("bob", "bobby", true);
         return null;
      });

      assertAllLists(org, "Cycle", "bobby, bobby(User);bob(Group),carol@x.com");
   }

   @Test
   void groupRename_hookThenUpdateCycleInfoNotify_isIdempotent() throws Exception {
      String org = "dc_hook_legacy_group";
      setupOrgs(org);
      seed(org, "Cycle", ALL, true);

      OrganizationManager.runInOrgScope(org, () -> {
         scheduleManager.identityRenamed(new IdentityID("bob", org),
                                         new Group(new IdentityID("bobby", org)));
         dataCycleManager.updateCycleInfoNotify("bob", "bobby", false);
         return null;
      });

      assertAllLists(org, "Cycle", "bob, bob(User);bobby(Group),carol@x.com");
   }

   // bob -> bob2, then alice -> bob, each followed by the legacy pass: no token renamed twice
   @Test
   void chainedRenames_withLegacyPass_renameEachTokenOnce() throws Exception {
      String org = "dc_hook_chain";
      setupOrgs(org);
      seed(org, "Cycle", "bob(User),alice(User)", true);

      OrganizationManager.runInOrgScope(org, () -> {
         scheduleManager.identityRenamed(new IdentityID("bob", org),
                                         new User(new IdentityID("bob2", org)));
         dataCycleManager.updateCycleInfoNotify("bob", "bob2", true);
         scheduleManager.identityRenamed(new IdentityID("alice", org),
                                         new User(new IdentityID("bob", org)));
         dataCycleManager.updateCycleInfoNotify("alice", "bob", true);
         return null;
      });

      assertAllLists(org, "Cycle", "bob2(User),bob(User)");
   }

   private void setupOrgs(String... orgs) throws Exception {
      SecurityTestDataBuilder b = SecurityTestDataBuilder.create();

      for(String org : orgs) {
         b = b.addOrg(org.toUpperCase(), org);
      }

      builder = b.setup();
   }

   private void seed(String org, String cycle, String list, boolean notify) {
      DataCycleManager.CycleInfo info = new DataCycleManager.CycleInfo(cycle, org);
      info.setStartNotify(notify);
      info.setStartEmail(list);
      info.setEndNotify(notify);
      info.setEndEmail(list);
      info.setFailureNotify(notify);
      info.setFailureEmail(list);
      info.setExceedNotify(false);
      info.setExceedEmail(list);
      dataCycleManager.addCondition(cycle, org, TimeCondition.at(1, 0, 0));
      dataCycleManager.setCycleInfo(cycle, org, info);
   }

   // dispatches like IdentityService.syncIdentity: a DefaultIdentity built from the removed ID
   private void removeViaScheduleManager(Identity identity, String threadOrg) throws Exception {
      IdentityID id = identity.getIdentityID();
      EditableAuthenticationProvider provider = mock(EditableAuthenticationProvider.class);
      when(provider.getUser(id)).thenReturn(new User(id));
      when(provider.getGroup(id)).thenReturn(new Group(id));
      Identity removed = new DefaultIdentity(id, identity.getType());

      OrganizationManager.runInOrgScope(threadOrg, () -> {
         scheduleManager.identityRemoved(removed, provider);
         return null;
      });
   }

   private DataCycleManager.CycleInfo assertAllLists(String org, String cycle, String expected) {
      DataCycleManager.CycleInfo info = dataCycleManager.getCycleInfo(cycle, org);
      assertNotNull(info);
      assertEquals(expected, info.getStartEmail(), org + "/" + cycle + " start");
      assertEquals(expected, info.getEndEmail(), org + "/" + cycle + " end");
      assertEquals(expected, info.getFailureEmail(), org + "/" + cycle + " failure");
      assertEquals(expected, info.getExceedEmail(), org + "/" + cycle + " exceed");
      return info;
   }

   private static final String ALL = "bob, bob(User);bob(Group),carol@x.com";
}
