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
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.web.portal.model.DashboardModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77357: with security off, DashboardController.getIdentity() (since Bug #74247) returned
 * anonymous with a null org, and that identity became the owner of every composed dashboard it
 * created (anonymous~;~__GLOBAL__), which no security-off principal (anonymous@host-org) matches.
 * The owner is now anonymous in the default org. Dashboards are still selected under anonymous
 * (DashboardManager keys are name-only), so the Bug #74247 listing is unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DashboardControllerSecurityOffOwnerTest {
   private DashboardController controller;
   private ViewsheetService viewsheetService;
   private DashboardRegistryManager registryManager;
   private DashboardManager dashboardManager;
   private IdentityID hostAnonymous;
   private MockedStatic<AssetUtil> assetUtil;

   @BeforeEach
   void before() {
      AssetRepository engine = mock(AssetRepository.class);
      viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getAssetRepository()).thenReturn(engine);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      registryManager = mock(DashboardRegistryManager.class);
      when(registryManager.getRegistry(any(IdentityID.class)))
         .thenReturn(mock(DashboardRegistry.class));
      dashboardManager = mock(DashboardManager.class);
      // the real manager runs the create's registry and selection changes holding its lock
      doAnswer(inv -> {
         ((Runnable) inv.getArgument(0)).run();
         return null;
      }).when(dashboardManager).runLocked(any());

      controller = new DashboardController(
         mock(AnalyticRepository.class), viewsheetService, mock(DashboardServiceProxy.class),
         securityEngine, registryManager, dashboardManager,
         mock(DependencyHandler.class), mock(RenameTransformHandler.class));

      hostAnonymous = new IdentityID(XPrincipal.ANONYMOUS, Organization.getDefaultOrganizationID());

      assetUtil = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(anyBoolean())).thenReturn(engine);
   }

   @AfterEach
   void after() {
      assetUtil.close();
   }

   @Test
   void newComposedDashboardIsOwnedByDefaultOrgAnonymous() throws Exception {
      SRPrincipal anonymous = new SRPrincipal(hostAnonymous, new IdentityID[0], new String[0],
                                              hostAnonymous.getOrgID(), 1L);
      assertOwnedByDefaultOrgAnonymous(anonymous);
   }

   @Test
   void virtualAdminNewComposedDashboardIsOwnedByDefaultOrgAnonymous() throws Exception {
      IdentityID adminId = new IdentityID("admin", Organization.getDefaultOrganizationID());
      SRPrincipal admin = new SRPrincipal(adminId,
                                          new IdentityID[] { new IdentityID("Administrator", null) },
                                          new String[0], adminId.getOrgID(), 1L);
      assertOwnedByDefaultOrgAnonymous(admin);
   }

   private void assertOwnedByDefaultOrgAnonymous(SRPrincipal principal) throws Exception {
      DashboardModel model = DashboardModel.builder().name("Bug77357").build();

      controller.newDashboard(model, principal);

      ArgumentCaptor<AssetEntry> entry = ArgumentCaptor.forClass(AssetEntry.class);
      verify(viewsheetService).setViewsheet(any(), entry.capture(), same(principal),
                                            anyBoolean(), anyBoolean());
      assertEquals(AssetRepository.USER_SCOPE, entry.getValue().getScope());
      assertEquals(hostAnonymous, entry.getValue().getUser());
      verify(registryManager).getRegistry(hostAnonymous);
      // selected under anonymous, as the Bug #74247 listing looks it up
      verify(dashboardManager).addDashboard(
         argThat(id -> XPrincipal.ANONYMOUS.equals(id.getName())), eq("Bug77357"));
   }
}
