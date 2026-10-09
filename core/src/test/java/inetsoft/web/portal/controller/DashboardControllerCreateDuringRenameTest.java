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
package inetsoft.web.portal.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import inetsoft.web.portal.model.DashboardModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Bug #78101 regression coverage.
 *
 * A dashboard create still running under a user's old name while an administrator renamed the
 * user added the dashboard to the old name's registry and selection after the rename had moved
 * them, so the dashboard was missing from the renamed user's Dashboard tab. The create now adds
 * the dashboard holding the dashboard manager's lock, which the rename holds while it moves them,
 * and fails if the user, who was in the security provider, is gone.
 *
 * [new][user exists]          registry and selection are changed inside runLocked()
 * [new][user renamed]         nothing is stored, the new viewsheet is removed, the create fails
 * [new][renamed before start]  old name not in the provider when the create starts (another
 *                              node), but recorded as renamed: the create fails
 * [new][refused, renamed]      the viewsheet is removed under the old and the new name, without
 *                              the user's permissions, unless a dashboard of the new name uses it
 * [new][name recreated]        a renamed name that a new user has now: the dashboard is created
 * [new][user not in provider] an SSO user is not checked, the dashboard is created
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardControllerCreateDuringRenameTest {
   private static final IdentityID USER = new IdentityID("u0", "orgA");

   private DashboardController controller;
   private AssetRepository engine;
   private SecurityProvider provider;
   private DashboardRegistry registry;
   private DashboardManager dashboardManager;
   private SRPrincipal principal;
   private MockedStatic<AssetUtil> assetUtil;
   private final boolean[] locked = new boolean[1];
   private final List<String> unlocked = new ArrayList<>();
   private final List<Boolean> ignorePermOnRemove = new ArrayList<>();

   @BeforeEach
   void before() throws Exception {
      engine = mock(AssetRepository.class);
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getAssetRepository()).thenReturn(engine);

      provider = mock(SecurityProvider.class);
      when(provider.getUsers()).thenReturn(new IdentityID[] { USER });
      when(provider.getUser(USER)).thenReturn(new User(USER));
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);

      registry = mock(DashboardRegistry.class);
      DashboardRegistryManager registryManager = mock(DashboardRegistryManager.class);
      when(registryManager.getRegistry(any(IdentityID.class))).thenReturn(registry);
      doAnswer(inv -> recordUnlocked("putDashboard")).when(registry).putDashboard(any(), any());

      dashboardManager = mock(DashboardManager.class);
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
      doAnswer(inv -> recordUnlocked("addDashboard"))
         .when(dashboardManager).addDashboard(any(), any());

      controller = new DashboardController(
         mock(AnalyticRepository.class), viewsheetService, mock(DashboardServiceProxy.class),
         securityEngine, registryManager, dashboardManager,
         mock(DependencyHandler.class), mock(RenameTransformHandler.class));

      principal = new SRPrincipal(USER, new IdentityID[0], new String[0], "orgA", 1L);

      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setComposedDashboard(true);
      when(engine.getSheet(any(), any(), anyBoolean(), any())).thenReturn(vs);
      // the new viewsheet is unique when it is created, and exists when it is removed
      when(engine.containsEntry(any()))
         .thenAnswer(inv -> Boolean.TRUE.equals(AssetRepository.IGNORE_PERM.get()));
      // the user is logged out and gone, so the refused create's viewsheet is removed without
      // the user's permissions
      doAnswer(inv -> {
         ignorePermOnRemove.add(Boolean.TRUE.equals(AssetRepository.IGNORE_PERM.get()));
         return null;
      }).when(engine).removeSheet(any(), any(), anyBoolean());
      when(registry.getDashboardNames()).thenReturn(new String[0]);

      assetUtil = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(anyBoolean())).thenReturn(engine);
   }

   @AfterEach
   void after() {
      assetUtil.close();
   }

   @Test
   void newDashboardStoresTheDashboardHoldingTheLock() throws Exception {
      controller.newDashboard(newModel("d1"), principal);

      verify(registry).putDashboard(eq("d1"), any());
      verify(dashboardManager).addDashboard(new DefaultIdentity(USER, Identity.USER), "d1");
      assertEquals(List.of(), unlocked, "stored without holding the dashboard manager's lock");
   }

   @Test
   void newDashboardFailsWhenTheUserIsRenamedMeanwhile() throws Exception {
      // the user is listed when the create starts and is gone once it holds the lock
      when(provider.getUser(USER)).thenReturn(null);

      assertThrows(MessageException.class,
                   () -> controller.newDashboard(newModel("d1"), principal));

      verify(registry, never()).putDashboard(any(), any());
      verify(dashboardManager, never()).addDashboard(any(), any());
      verify(engine).removeSheet(argThat(e -> USER.equals(e.getUser())), same(principal),
                                 eq(true));
      assertEquals(List.of(true), ignorePermOnRemove);
   }

   @Test
   void newDashboardFailsWhenTheUserWasRenamedBeforeItStarted() throws Exception {
      // a session of the old name on another node starts the create after the rename, so the
      // old name is not in the provider from the start, like an SSO user, but is recorded as
      // renamed
      when(provider.getUsers()).thenReturn(new IdentityID[0]);
      when(provider.getUser(USER)).thenReturn(null);
      when(dashboardManager.isRenamedUser(USER)).thenReturn(true);

      assertThrows(MessageException.class,
                   () -> controller.newDashboard(newModel("d1"), principal));

      verify(registry, never()).putDashboard(any(), any());
      verify(dashboardManager, never()).addDashboard(any(), any());
      verify(engine).removeSheet(argThat(e -> USER.equals(e.getUser())), same(principal),
                                 eq(true));
      assertEquals(List.of(true), ignorePermOnRemove);
   }

   @Test
   void refusedCreateRemovesItsViewsheetAlsoUnderTheNewName() throws Exception {
      // the user's assets may already have been moved to the new name
      IdentityID renamed = new IdentityID("u0b", "orgA");
      when(provider.getUser(USER)).thenReturn(null);
      when(dashboardManager.getRenamedUser(USER)).thenReturn(renamed);

      assertThrows(MessageException.class,
                   () -> controller.newDashboard(newModel("d1"), principal));

      verify(engine).removeSheet(argThat(e -> USER.equals(e.getUser())), same(principal),
                                 eq(true));
      verify(engine).removeSheet(argThat(e -> renamed.equals(e.getUser()) &&
                                    "d1".equals(e.getPath())), same(principal), eq(true));
      assertEquals(List.of(true, true), ignorePermOnRemove);
   }

   @Test
   void refusedCreateKeepsAViewsheetOfTheNewNameThatADashboardUses() throws Exception {
      IdentityID renamed = new IdentityID("u0b", "orgA");
      when(provider.getUser(USER)).thenReturn(null);
      when(dashboardManager.getRenamedUser(USER)).thenReturn(renamed);
      VSDashboard used = new VSDashboard();
      used.setViewsheet(new ViewsheetEntry("d1", renamed));
      when(registry.getDashboardNames()).thenReturn(new String[] { "d1" });
      when(registry.getDashboard("d1")).thenReturn(used);

      assertThrows(MessageException.class,
                   () -> controller.newDashboard(newModel("d1"), principal));

      verify(engine).removeSheet(argThat(e -> USER.equals(e.getUser())), same(principal),
                                 eq(true));
      verify(engine, never()).removeSheet(argThat(e -> renamed.equals(e.getUser())), any(),
                                          anyBoolean());
   }

   @Test
   void newDashboardIsCreatedForRecreatedUserOfARenamedName() throws Exception {
      // the old name was given to a new user after the rename
      when(dashboardManager.isRenamedUser(USER)).thenReturn(true);

      controller.newDashboard(newModel("d1"), principal);

      verify(registry).putDashboard(eq("d1"), any());
      verify(dashboardManager).addDashboard(any(), eq("d1"));
   }

   @Test
   void newDashboardIsCreatedForUserNotInProvider() throws Exception {
      // an SSO user is not in the provider at all, so it is not checked
      when(provider.getUsers()).thenReturn(new IdentityID[0]);
      when(provider.getUser(USER)).thenReturn(null);

      controller.newDashboard(newModel("d1"), principal);

      verify(registry).putDashboard(eq("d1"), any());
      verify(dashboardManager).addDashboard(any(), eq("d1"));
      verify(engine, never()).removeSheet(any(), any(), anyBoolean());
   }

   private Object recordUnlocked(String call) {
      if(!locked[0]) {
         unlocked.add(call);
      }

      return null;
   }

   private static DashboardModel newModel(String name) {
      return DashboardModel.builder().name(name).description("").build();
   }
}
