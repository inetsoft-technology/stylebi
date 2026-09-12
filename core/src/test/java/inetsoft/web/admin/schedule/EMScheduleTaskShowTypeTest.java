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
package inetsoft.web.admin.schedule;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.UserEnv;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that the Enterprise Manager task list/folder view is stored as a persisted
 * per-user preference, under a key of its own so that it does not track the portal.
 */
@Tag("core")
class EMScheduleTaskShowTypeTest {
   private static final String PROPERTY = "schedule.show.tasks.as.list";
   private static final String USER_PROPERTY = "em.schedule.showTasksAsList";
   private static final String PORTAL_USER_PROPERTY = "portal.schedule.showTasksAsList";

   private EMScheduleTaskShowType controller;
   private Principal alice;
   private Principal bob;

   @BeforeEach
   void before() {
      controller = new EMScheduleTaskShowType();
      alice = mock(Principal.class);
      bob = mock(Principal.class);
   }

   @Test
   void get_noStoredPreference_usesPropertyDefault() {
      try(MockedStatic<UserEnv> ue = mockStatic(UserEnv.class);
          MockedStatic<SreeEnv> se = mockStatic(SreeEnv.class))
      {
         ue.when(() -> UserEnv.getProperty(alice, USER_PROPERTY, null)).thenReturn(null);
         se.when(() -> SreeEnv.getBooleanProperty(PROPERTY)).thenReturn(false);

         assertFalse(controller.getScheduleTaskShowType(alice));
      }
   }

   @Test
   void get_storedFalse_isNotTreatedAsUnset() {
      try(MockedStatic<UserEnv> ue = mockStatic(UserEnv.class);
          MockedStatic<SreeEnv> se = mockStatic(SreeEnv.class))
      {
         ue.when(() -> UserEnv.getProperty(alice, USER_PROPERTY, null)).thenReturn("false");
         se.when(() -> SreeEnv.getBooleanProperty(PROPERTY)).thenReturn(true);

         assertFalse(controller.getScheduleTaskShowType(alice));
         se.verifyNoInteractions();
      }
   }

   @Test
   void get_preferenceIsNotSharedBetweenUsers() {
      try(MockedStatic<UserEnv> ue = mockStatic(UserEnv.class);
          MockedStatic<SreeEnv> se = mockStatic(SreeEnv.class))
      {
         ue.when(() -> UserEnv.getProperty(alice, USER_PROPERTY, null)).thenReturn("false");
         ue.when(() -> UserEnv.getProperty(bob, USER_PROPERTY, null)).thenReturn(null);
         se.when(() -> SreeEnv.getBooleanProperty(PROPERTY)).thenReturn(true);

         assertFalse(controller.getScheduleTaskShowType(alice));
         assertTrue(controller.getScheduleTaskShowType(bob));
      }
   }

   /**
    * Enterprise Manager and the portal are separate surfaces, so toggling one must not
    * rearrange the other.
    */
   @Test
   void get_doesNotReadThePortalPreference() {
      try(MockedStatic<UserEnv> ue = mockStatic(UserEnv.class);
          MockedStatic<SreeEnv> se = mockStatic(SreeEnv.class))
      {
         ue.when(() -> UserEnv.getProperty(alice, PORTAL_USER_PROPERTY, null)).thenReturn("false");
         ue.when(() -> UserEnv.getProperty(alice, USER_PROPERTY, null)).thenReturn(null);
         se.when(() -> SreeEnv.getBooleanProperty(PROPERTY)).thenReturn(true);

         assertTrue(controller.getScheduleTaskShowType(alice));
      }
   }

   @Test
   void put_storesUnderTheEmKeyOnly() throws Exception {
      try(MockedStatic<UserEnv> ue = mockStatic(UserEnv.class);
          MockedStatic<SreeEnv> se = mockStatic(SreeEnv.class))
      {

         controller.setConfiguration("false", alice);

         ue.verify(() -> UserEnv.setProperty(alice, USER_PROPERTY, "false"));
         ue.verify(() -> UserEnv.setProperty(any(), eq(PORTAL_USER_PROPERTY), any()), never());
         se.verifyNoInteractions();
      }
   }

   /**
    * The installation-wide default must be read with the varargs-free overload.
    * getBooleanProperty(name, "true") reads as "default to true" but actually means
    * "true only when the value equals 'true'", which is a different thing.
    */
   @Test
   void get_defaultIsReadWithoutTrueValues() {
      try(MockedStatic<UserEnv> ue = mockStatic(UserEnv.class);
          MockedStatic<SreeEnv> se = mockStatic(SreeEnv.class))
      {
         ue.when(() -> UserEnv.getProperty(alice, USER_PROPERTY, null)).thenReturn(null);

         controller.getScheduleTaskShowType(alice);

         se.verify(() -> SreeEnv.getBooleanProperty(PROPERTY));
      }
   }

   /**
    * Anonymous preferences are UserEnv's business: it drops the write unless
    * anonymous.userdata.save is enabled. The controller must not write installation-wide
    * configuration on its behalf.
    */
   @Test
   void put_neverWritesTheGlobalProperty() {
      try(MockedStatic<UserEnv> ue = mockStatic(UserEnv.class);
          MockedStatic<SreeEnv> se = mockStatic(SreeEnv.class))
      {
         controller.setConfiguration("false", null);

         ue.verify(() -> UserEnv.setProperty(null, USER_PROPERTY, "false"));
         se.verifyNoInteractions();
      }
   }
}
