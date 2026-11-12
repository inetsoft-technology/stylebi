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

/*
 * Bug #79875: an id change that was interrupted after it moved the members is resumed with the
 * model of the original request, whose members are named by the old organization. A member that
 * the first part moved already must not be created again as a blank member of the old
 * organization, which the rest of the change would copy over the moved one (the user then could
 * not log in under the new id).
 */

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.web.admin.security.user.IdentityThemeService;
import inetsoft.sree.security.OrgOperationJournal.Entry;
import inetsoft.sree.security.OrgOperationJournal.Type;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.Identity;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgIdChangeResumeTest {
   private static final String OLD = "resume_old";
   private static final String NEW = "resume_new";

   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider provider;
   private IdentityService identityService;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("ResumeOrg", OLD)
         .addUser("u1", OLD, "pw1")
         .addUser("u2", OLD, "pw2")
         .setup();
      provider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);
      identityService = new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         mock(IdentityThemeService.class), null, null, mock(FavoritesService.class),
         mock(Cluster.class), null, null, null, null, null, mock(ScheduleManager.class),
         mock(IndexedStorage.class), Optional.empty(), null, null,
         mock(SessionLicenseServiceProvider.class), mock(DashboardRegistryManager.class), null,
         mock(DashboardManager.class), null, null, null, null, null, null,
         mock(RepletRegistryManager.class), Optional.empty());
   }

   @AfterEach
   void tearDown() {
      setResuming(null);
      ThreadContext.setContextPrincipal(null);

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void resumedMemberMoveKeepsTheMovedUsersAndTheirPasswords() throws Exception {
      List<IdentityModel> members = List.of(member("u1"), member("u2"));
      FSOrganization target = new FSOrganization(NEW);
      target.setName("ResumeOrg");
      target.setMembers(new String[] { "u1", "u2" });

      // the part of the change that ran before the node died: u1 and u2 are moved
      updateMembers(target, members);
      IdentityID u1 = new IdentityID("u1", NEW);
      IdentityID u2 = new IdentityID("u2", NEW);
      assertNotNull(provider.getUser(u1));
      assertNull(provider.getUser(new IdentityID("u1", OLD)));
      assertTrue(login(u1, "pw1"), "precondition: the moved user logs in");
      assertNotNull(provider.getUser(u2), "precondition: u2 moved");
      assertTrue(login(u2, "pw2"), "precondition: the second moved user logs in");

      // the resume, with the model of the original request
      setResuming(new Entry("op", Type.RENAME, OLD, NEW, "node", 1L,
                            OrgOperationJournal.State.FAILED, "{}", List.of(), null));
      updateMembers(target, members);

      assertTrue(login(u1, "pw1"), "u1 must still log in under the new id");
      assertTrue(login(u2, "pw2"), "u2 must still log in under the new id");
      assertNull(provider.getUser(new IdentityID("u1", OLD)), "no blank member in the old id");
      assertNull(provider.getUser(new IdentityID("u2", OLD)), "no blank member in the old id");
      assertEquals(0, Arrays.stream(provider.getUsers())
                      .filter(i -> OLD.equals(i.getOrgID())).count());
   }

   @Test
   void editAfterARecoveryOfTheSameOrganizationIsStopped() {
      Entry rename = new Entry("op", Type.RENAME, OLD, NEW, "node", 1L,
                               OrgOperationJournal.State.RUNNING, "{}", List.of(), null);

      assertThrows(MessageException.class,
                   () -> IdentityService.checkNotRecovered(List.of(rename), OLD, "other"));
      assertThrows(MessageException.class,
                   () -> IdentityService.checkNotRecovered(List.of(rename), "x", "RESUME_NEW"));
      assertDoesNotThrow(
         () -> IdentityService.checkNotRecovered(List.of(rename), "unrelated", null));
      assertDoesNotThrow(() -> IdentityService.checkNotRecovered(List.of(), OLD, NEW));
   }

   private boolean login(IdentityID user, String password) {
      return provider.authenticate(user, new DefaultTicket(user, password));
   }

   private void updateMembers(FSOrganization target, List<IdentityModel> members) {
      ReflectionTestUtils.invokeMethod(identityService, "updateOrganizationMembers", target,
                                       members, OLD, provider, null);
   }

   private static IdentityModel member(String name) {
      return IdentityModel.builder().identityID(new IdentityID(name, OLD)).type(Identity.USER)
         .build();
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
