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
package inetsoft.web.admin.security.user;

/*
 * Bug #79875: a pane that was loaded before the id of its organization was changed (by another
 * request, or by the recovery of an interrupted change) names the organization by its display
 * name, which resolves to the renamed organization, so its save was taken for a save of that
 * organization with the members of the pane. The members that the pane lacks are deleted.
 */

import inetsoft.mv.MVManager;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgStalePaneEditTest {
   private static final String OLD = "stale_old";
   private static final String NEW = "stale_new";
   private static final String NAME = "StaleOrg";

   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider provider;
   private IdentityService identityService;

   @BeforeEach
   void setUp() throws Exception {
      // the organization after its id was changed from OLD to NEW, the name is kept
      builder = SecurityTestDataBuilder.create()
         .addOrg(NAME, NEW)
         .addUser("u1", NEW, "pw1")
         .addUser("u2", NEW, "pw2")
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
      ThreadContext.setContextPrincipal(null);

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // the cause, without a recovery: a save that is built on an organization with fewer members
   // than it has drops the members that it lacks
   @Test
   void paneWithoutTheMembersDropsThem() {
      FSOrganization org = new FSOrganization(NEW);
      org.setName(NAME);
      org.setMembers(new String[0]);

      ReflectionTestUtils.invokeMethod(identityService, "updateOrganizationMembers", org,
                                       new ArrayList<IdentityModel>(), NEW, provider, null);

      assertNull(provider.getUser(new IdentityID("u1", NEW)));
      assertNull(provider.getUser(new IdentityID("u2", NEW)));
   }

   @Test
   void paneLoadedForTheOldIdIsRefusedAndTheMembersSurvive() throws Exception {
      EditOrganizationPaneModel stale = EditOrganizationPaneModel.builder()
         .name(NAME).oldName(NAME).id(NEW).oldId(OLD).status(true)
         .members(new ArrayList<IdentityModel>()).build();

      MessageException e = assertThrows(
         MessageException.class, () -> userTreeService().editOrganization(stale, "p", builder.principalOf("u1", NEW)));

      assertTrue(e.getMessage().contains("was changed from " + OLD + " to " + NEW),
                 e.getMessage());
      assertNotNull(provider.getUser(new IdentityID("u1", NEW)));
      assertNotNull(provider.getUser(new IdentityID("u2", NEW)));
   }

   @Test
   void paneOfTheCurrentIdOrWithoutTheIdIsNotRefused() {
      Organization org = provider.getOrganization(NEW);

      for(String oldId : new String[] { NEW, null, "" }) {
         EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
            .name(NAME).oldName(NAME).id(NEW).oldId(oldId).status(true).build();
         assertDoesNotThrow(() -> UserTreeService.checkPaneOrganization(model, org));
      }
   }

   @Test
   void paneModelCarriesTheLoadedId() throws Exception {
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .name(NAME).oldName(NAME).id(NEW).oldId(OLD).status(true).build();
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      EditOrganizationPaneModel back =
         mapper.readValue(mapper.writeValueAsString(model), EditOrganizationPaneModel.class);

      // the EM posts the model it got, so the id comes back with the save
      assertEquals(OLD, back.oldId());
      assertEquals(NEW, back.id());
   }

   private UserTreeService userTreeService() {
      AuthenticationProviderService authenticationProviderService =
         mock(AuthenticationProviderService.class);
      when(authenticationProviderService.getProviderByName(anyString())).thenReturn(provider);

      return new UserTreeService(
         authenticationProviderService, mock(SystemAdminService.class), identityService,
         mock(LocalizationSettingsService.class), SecurityEngine.getSecurity(),
         mock(IdentityThemeService.class), mock(SimpMessagingTemplate.class),
         mock(FavoritesService.class), mock(DataCycleManager.class), mock(LicenseManager.class),
         mock(MVManager.class), mock(IndexedStorage.class), mock(CustomThemesManager.class),
         mock(DashboardRegistryManager.class), mock(XRepository.class),
         mock(DependencyStorageService.class), mock(RecycleBin.class));
   }
}
