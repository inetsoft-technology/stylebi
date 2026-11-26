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
package inetsoft.web.wiz.service;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.XSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.mockito.MockedStatic;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redmine #82413: the permission re-checks of the plugin email, share and schedule endpoints. The
 * strategy behind {@code SecurityEngine} approves every check for a wiz principal, so each check
 * has to run on the real user's copy of the principal. The engine mock below models exactly that:
 * it approves a wiz principal and answers {@code realUserAllowed} for anyone else, so a check made
 * on the wiz principal passes where the real user is denied.
 */
@Tag("core")
class WizDashboardAccessTest {
   private MockedStatic<XSessionService> sessionStatic;

   /** A principal registers itself with the session service when it is built. */
   @BeforeEach
   void mockSessionService() {
      sessionStatic = mockStatic(XSessionService.class);
      sessionStatic.when(XSessionService::getService).thenReturn(mock(XSessionService.class));
   }

   @AfterEach
   void closeSessionService() {
      sessionStatic.close();
   }

   private static final String ORG = "org1";

   @Test
   void toolbarActionIsEvaluatedForTheRealUserNotTheWizPrincipal() {
      Fixture f = new Fixture(false);

      SecurityException e = assertThrows(SecurityException.class,
         () -> f.access.requireToolbarAction(f.wiz, "Email"));
      assertTrue(e.getMessage().contains("Email"));
      assertFalse(f.access.hasToolbarAction(f.wiz, "ExportExpandComponents"));
      assertFalse(SRPrincipal.isWizPrincipal(f.lastChecked[0]));
   }

   @Test
   void toolbarActionPassesForAUserWhoHoldsIt() {
      Fixture f = new Fixture(true);

      assertDoesNotThrow(() -> f.access.requireToolbarAction(f.wiz, "Email"));
      assertTrue(f.access.hasToolbarAction(f.wiz, "ExportExpandComponents"));
   }

   @Test
   void scheduleNeedsTheScheduleTabAndSchedulerAccessForTheRealUser() throws Exception {
      Fixture denied = new Fixture(false);
      assertThrows(SecurityException.class, () -> denied.access.requireScheduleAccess(denied.wiz));
      Fixture f = new Fixture(true);
      assertDoesNotThrow(() -> f.access.requireScheduleAccess(f.wiz));

      // one of the two rights is enough to refuse
      Fixture tabOnly = new Fixture(true);
      doAnswer(inv -> SRPrincipal.isWizPrincipal(inv.getArgument(0)))
         .when(tabOnly.engine).checkPermission(any(), eq(ResourceType.SCHEDULER), anyString(),
                                               any(ResourceAction.class));
      assertThrows(SecurityException.class, () -> tabOnly.access.requireScheduleAccess(tabOnly.wiz));
   }

   @Test
   void shareTargetIsEvaluatedForTheRealUser() {
      Fixture denied = new Fixture(false);
      Fixture granted = new Fixture(true);

      assertThrows(SecurityException.class,
                   () -> denied.access.requireShareTarget(denied.wiz, "slack"));
      assertDoesNotThrow(() -> granted.access.requireShareTarget(granted.wiz, "slack"));
   }

   @Test
   void aCheckThatCannotBeEvaluatedIsARefusal() throws Exception {
      Fixture f = new Fixture(true);
      when(f.engine.checkPermission(any(), any(ResourceType.class), anyString(),
                                    any(ResourceAction.class)))
         .thenThrow(new inetsoft.sree.security.SecurityException("login"));

      assertThrows(SecurityException.class, () -> f.access.requireToolbarAction(f.wiz, "Email"));
   }

   @Test
   void aDashboardIsReadOnTheRealUsersCopyOfThePrincipal() throws Exception {
      Fixture f = new Fixture(false);
      when(f.repository.containsEntry(any(AssetEntry.class))).thenReturn(true);
      // the repository approves a wiz principal and refuses the real user, as the strategy does
      doAnswer(inv -> {
         if(!SRPrincipal.isWizPrincipal(inv.getArgument(0))) {
            throw new inetsoft.sree.security.SecurityException("no read");
         }

         return null;
      }).when(f.repository).checkAssetPermission(any(Principal.class), any(AssetEntry.class),
                                                  any(ResourceAction.class));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> f.access.requireReadableDashboard("Sales/Overview", null, f.wiz));
      assertTrue(e.getMessage().contains("No dashboard"));
   }

   @Test
   void anUnreadableDashboardIsReportedLikeAMissingOne() throws Exception {
      Fixture missing = new Fixture(true);
      when(missing.repository.containsEntry(any(AssetEntry.class))).thenReturn(false);
      Fixture unreadable = new Fixture(true);
      when(unreadable.repository.containsEntry(any(AssetEntry.class))).thenReturn(true);
      doThrow(new inetsoft.sree.security.SecurityException("no read"))
         .when(unreadable.repository).checkAssetPermission(any(Principal.class),
                                                           any(AssetEntry.class),
                                                           any(ResourceAction.class));

      String a = assertThrows(IllegalArgumentException.class,
         () -> missing.access.requireReadableDashboard("Sales/Overview", "global", missing.wiz))
         .getMessage();
      String b = assertThrows(IllegalArgumentException.class,
         () -> unreadable.access.requireReadableDashboard("Sales/Overview", "global",
                                                           unreadable.wiz)).getMessage();
      assertEquals(a, b);
   }

   @Test
   void aReadableDashboardResolvesWithTheCallersOwnOrgAndUser() throws Exception {
      Fixture f = new Fixture(true);
      when(f.repository.containsEntry(any(AssetEntry.class))).thenReturn(true);

      AssetEntry global = f.access.requireReadableDashboard("/Sales/Overview", null, f.wiz);
      assertEquals("Sales/Overview", global.getPath());
      assertEquals(AssetRepository.GLOBAL_SCOPE, global.getScope());
      assertEquals(ORG, global.getOrgID());

      AssetEntry user = f.access.requireReadableDashboard("Mine/VS", "user", f.wiz);
      assertEquals(AssetRepository.USER_SCOPE, user.getScope());
      assertEquals("bob", user.getUser().name);
   }

   @Test
   void malformedPathsAndScopesAreRefusedWithTheFieldNamed() {
      Fixture f = new Fixture(true);

      for(String path : new String[] { null, "", "  ", "../secret", "a/../b", "a^b", "/" }) {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> f.access.requireReadableDashboard(path, null, f.wiz), "path=" + path);
         assertTrue(e.getMessage().contains("path"), e.getMessage());
      }

      assertThrows(IllegalArgumentException.class,
                   () -> f.access.requireReadableDashboard("A/B", "private", f.wiz));
   }

   @Test
   void theCallerIsTakenFromThePrincipalOnly() {
      Fixture f = new Fixture(true);

      assertEquals(new IdentityID("bob", ORG), WizDashboardAccess.caller(f.wiz));
      assertThrows(IllegalArgumentException.class, () -> WizDashboardAccess.caller(null));
   }

   /** An engine that approves a wiz principal and answers {@code realUserAllowed} otherwise. */
   private static final class Fixture {
      final SecurityEngine engine = mock(SecurityEngine.class);
      final AssetRepository repository = mock(AssetRepository.class);
      final WizDashboardAccess access = new WizDashboardAccess(repository, engine);
      final SRPrincipal wiz;
      final Principal[] lastChecked = new Principal[1];

      Fixture(boolean realUserAllowed) {
         wiz = new SRPrincipal(new IdentityID("bob", ORG), new IdentityID[0], new String[0], ORG,
                               42L);
         wiz.setProperty("wiz", "true");

         try {
            when(engine.checkPermission(any(), any(ResourceType.class), anyString(),
                                        any(ResourceAction.class)))
               .thenAnswer(inv -> {
                  lastChecked[0] = inv.getArgument(0);
                  return SRPrincipal.isWizPrincipal(inv.getArgument(0)) || realUserAllowed;
               });
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }
   }
}
