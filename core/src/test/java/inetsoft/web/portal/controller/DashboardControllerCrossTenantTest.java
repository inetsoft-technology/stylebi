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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import inetsoft.web.portal.model.DashboardModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77057 regression coverage.
 *
 * DashboardController stored any client-supplied viewsheet identifier in the caller's dashboard
 * registry, and on delete removed the referenced user-scope viewsheet with a principal forged
 * from the identifier's owner (new XPrincipal(entry.getUser())). The forged principal carried the
 * victim's org and name, so the asset engine's org and owner checks always passed and an org A
 * user could delete an org B user's composed dashboard viewsheet.
 *
 * [delete][foreign identifier]  removeSheet/getSheet must run as the caller, never as the stored owner
 * [delete][own identifier]      owner still deletes own composed viewsheet (regression)
 * [new][foreign identifier]     identifier is permission-checked as the caller; rejected -> not saved
 * [edit][foreign identifier]    same check on edit
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DashboardControllerCrossTenantTest {
   private static final IdentityID ATTACKER = new IdentityID("attacker", "orgA");
   private static final IdentityID VICTIM = new IdentityID("victim", "orgB");

   private DashboardController controller;
   private AssetRepository engine;
   private ViewsheetService viewsheetService;
   private DashboardRegistry registry;
   private SRPrincipal attacker;
   private MockedStatic<AssetUtil> assetUtil;

   @BeforeEach
   void before() throws Exception {
      engine = mock(AssetRepository.class);
      viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getAssetRepository()).thenReturn(engine);

      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.getUsers()).thenReturn(new IdentityID[] { ATTACKER, VICTIM });
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);

      registry = mock(DashboardRegistry.class);
      DashboardRegistryManager registryManager = mock(DashboardRegistryManager.class);
      when(registryManager.getRegistry(any(IdentityID.class))).thenReturn(registry);

      controller = new DashboardController(
         mock(AnalyticRepository.class), viewsheetService, mock(DashboardServiceProxy.class),
         securityEngine, registryManager, mock(DashboardManager.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class));

      attacker = new SRPrincipal(ATTACKER, new IdentityID[0], new String[0], "orgA", 1L);

      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setComposedDashboard(true);
      when(engine.getSheet(any(), any(), anyBoolean(), any())).thenReturn(vs);

      assetUtil = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(anyBoolean())).thenReturn(engine);
   }

   @AfterEach
   void after() {
      assetUtil.close();
   }

   @Test
   void deleteRemovesForeignViewsheetOnlyAsCaller() throws Exception {
      registerDashboard("x", identifierOf(VICTIM, "VictimDash"));

      controller.deleteDashboard("x", attacker);

      verify(engine).removeSheet(argThat(e -> VICTIM.equals(e.getUser())), same(attacker), eq(false));
      verify(engine, never()).removeSheet(any(), argThat(p -> !isAttacker(p)), anyBoolean());
      verify(engine, never()).getSheet(any(), argThat(p -> !isAttacker(p)), anyBoolean(), any());
   }

   @Test
   void deleteRemovesOwnComposedViewsheet() throws Exception {
      registerDashboard("mine", identifierOf(ATTACKER, "mine"));

      controller.deleteDashboard("mine", attacker);

      verify(engine).removeSheet(any(AssetEntry.class), same(attacker), eq(false));
   }

   @Test
   void newDashboardRejectsUnreadableIdentifier() throws Exception {
      String identifier = identifierOf(VICTIM, "VictimDash");
      denyRead(identifier);

      DashboardModel model = DashboardModel.builder().name("x").identifier(identifier).build();

      assertThrows(MessageException.class, () -> controller.newDashboard(model, attacker));
      verify(registry, never()).addDashboard(anyString(), any());
   }

   @Test
   void editDashboardRejectsUnreadableIdentifier() throws Exception {
      registerDashboard("x", identifierOf(ATTACKER, "x"));
      String identifier = identifierOf(VICTIM, "VictimDash");
      denyRead(identifier);

      DashboardModel model = DashboardModel.builder().name("x").identifier(identifier).build();

      assertThrows(MessageException.class, () -> controller.editDashboard("x", model, attacker));
      verify(registry, never()).addDashboard(anyString(), any());
      verify(engine, never()).removeSheet(any(), any(), anyBoolean());
   }

   private void registerDashboard(String name, String identifier) {
      VSDashboard dashboard = new VSDashboard();
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);
      ViewsheetEntry viewsheet = new ViewsheetEntry(entry.getPath(), entry.getUser());
      viewsheet.setIdentifier(identifier);
      dashboard.setViewsheet(viewsheet);
      when(registry.getDashboard(name)).thenReturn(dashboard);
   }

   private void denyRead(String identifier) throws Exception {
      doThrow(new MessageException("denied")).when(engine).checkAssetPermission(
         same(attacker),
         argThat(e -> e != null && identifier.equals(e.toIdentifier())),
         eq(ResourceAction.READ), anyBoolean());
   }

   private static String identifierOf(IdentityID owner, String name) {
      return new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, name, owner)
         .toIdentifier();
   }

   private boolean isAttacker(Principal principal) {
      return principal == attacker;
   }
}
