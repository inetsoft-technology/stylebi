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
 * Bug #77071: an org admin must be able to save its own organization when the organization has a
 * site admin member that is hidden from it, and the save must keep that hidden member.
 *
 * The org admin holds ADMIN on the org's "Users" root, a fallback grant that makes checkPermission()
 * return true for every user of the org, site admins included. The member list shown to the org
 * admin hides the site admin, so the saved model never carries it. The save guard used to treat it
 * as removed and reject the save, and the member merge would have dropped (deleted) it.
 */

import inetsoft.mv.MVManager;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.util.Identity;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import inetsoft.web.admin.security.user.IdentityThemeService;
import inetsoft.web.admin.security.user.SystemAdminService;
import inetsoft.web.admin.security.user.UserTreeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgHiddenMemberSaveTest {
   private static final String ORG_ID = "hidmem_org";
   private static final String ORG_NAME = "HidMemOrg";
   private static final IdentityID ORG_ADMIN = new IdentityID("orgAdmin", ORG_ID);
   private static final IdentityID SITE_ADMIN = new IdentityID("siteAdmin", ORG_ID);
   private static final IdentityID SITE_ADMIN2 = new IdentityID("siteAdmin2", ORG_ID);
   private static final IdentityID PLAIN = new IdentityID("plain", ORG_ID);

   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider fileProvider;
   private IdentityService identityService;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)
         .addSysAdminRole("siteAdmins", ORG_ID)
         .addUser(ORG_ADMIN.name, ORG_ID, "password")
         .addUser(SITE_ADMIN.name, ORG_ID, "password")
         .addUser(SITE_ADMIN2.name, ORG_ID, "password")
         .addUser(PLAIN.name, ORG_ID, "password")
         .addUserToRole(SITE_ADMIN.name, "siteAdmins", ORG_ID)
         .addUserToRole(SITE_ADMIN2.name, "siteAdmins", ORG_ID)
         .grantPermission(ResourceType.SECURITY_USER, new IdentityID("Users", ORG_ID).convertToKey(),
                          ResourceAction.ADMIN, ORG_ADMIN.name, Identity.USER, ORG_ID)
         .setup();

      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);
      identityService = createIdentityService();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void orgAdminSave_hiddenSiteAdminMember_saveAllowedAndMemberKept() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN);
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(SITE_ADMIN),
                 "precondition: siteAdmin must be a site admin");
      assertTrue(SecurityEngine.getSecurity().getSecurityProvider().checkPermission(
                    orgAdmin, ResourceType.SECURITY_USER, SITE_ADMIN.convertToKey(),
                    ResourceAction.ADMIN),
                 "precondition: the Users root grant gives the org admin ADMIN on the site admin");
      assertTrue(identityService.isOrgMemberHiddenFrom(SITE_ADMIN, orgAdmin),
                 "a site admin member must be hidden from an org admin");
      assertFalse(identityService.isOrgMemberHiddenFrom(PLAIN, orgAdmin),
                  "an ordinary member must be visible to an org admin");

      // the org admin's model carries only the members it can see
      EditOrganizationPaneModel model = model(List.of(member(ORG_ADMIN), member(PLAIN)));

      assertDoesNotThrow(() -> checkOrgEditedHasSysAdmin(model, orgAdmin),
                         "saving without a hidden site admin must not count as removing it");
      setOrganizationInfo(model, orgAdmin);

      assertNotNull(fileProvider.getUser(SITE_ADMIN), "the hidden site admin must be kept");
      assertNotNull(fileProvider.getUser(SITE_ADMIN2), "the hidden site admin must be kept");
      assertNotNull(fileProvider.getUser(PLAIN), "the visible member must be kept");
      assertNotNull(fileProvider.getUser(ORG_ADMIN), "the org admin must be kept");
   }

   @Test
   void orgAdminSave_visibleMemberOmitted_memberRemoved() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN);
      EditOrganizationPaneModel model = model(List.of(member(ORG_ADMIN)));

      assertDoesNotThrow(() -> checkOrgEditedHasSysAdmin(model, orgAdmin));
      setOrganizationInfo(model, orgAdmin);

      assertNull(fileProvider.getUser(PLAIN), "an explicitly removed visible member must be removed");
      assertNotNull(fileProvider.getUser(SITE_ADMIN), "the hidden site admin must be kept");
   }

   @Test
   void siteAdminSave_siteAdminMemberOmitted_memberRemoved() throws Exception {
      SRPrincipal siteAdmin = loginAs(SITE_ADMIN);
      assertFalse(identityService.isOrgMemberHiddenFrom(SITE_ADMIN2, siteAdmin),
                  "a site admin member must be visible to a site admin");

      setOrganizationInfo(model(List.of(member(ORG_ADMIN), member(SITE_ADMIN), member(PLAIN))),
                          siteAdmin);

      assertNull(fileProvider.getUser(SITE_ADMIN2),
                 "a site admin member omitted by a site admin must be removed");
      assertNotNull(fileProvider.getUser(PLAIN), "the kept member must remain");
   }

   private SRPrincipal loginAs(IdentityID user) {
      SRPrincipal principal = builder.principalOf(user.name, ORG_ID);
      ThreadContext.setContextPrincipal(principal);
      return principal;
   }

   private void setOrganizationInfo(EditOrganizationPaneModel model, Principal principal)
      throws Exception
   {
      FSOrganization oldOrg = (FSOrganization) fileProvider.getOrganization(ORG_ID);
      oldOrg.setMembers(fileProvider.getOrganizationMembers(ORG_ID));
      Method setOrganizationInfo = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, Principal.class);
      setOrganizationInfo.setAccessible(true);
      setOrganizationInfo.invoke(identityService, oldOrg, model, fileProvider, principal);
   }

   private void checkOrgEditedHasSysAdmin(EditOrganizationPaneModel model, Principal principal) {
      ReflectionTestUtils.invokeMethod(createUserTreeService(), "checkOrgEditedHasSysAdmin",
         fileProvider.getOrganization(ORG_ID), model, principal);
   }

   private static EditOrganizationPaneModel model(List<IdentityModel> members) {
      return EditOrganizationPaneModel.builder()
         .id(ORG_ID)
         .name(ORG_NAME)
         .oldName(ORG_NAME)
         .members(members)
         .status(true)
         .build();
   }

   private static IdentityModel member(IdentityID id) {
      return IdentityModel.builder().identityID(id).type(Identity.USER).build();
   }

   private static IdentityService createIdentityService() {
      return new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         null, null, null, mock(FavoritesService.class), null, null, null, null,
         null, null, null, null, Optional.empty(), null, null, null,
         mock(DashboardRegistryManager.class), null, null, null, null, null, null, null, null,
         mock(RepletRegistryManager.class), Optional.empty());
   }

   private UserTreeService createUserTreeService() {
      AuthenticationProviderService authenticationProviderService =
         mock(AuthenticationProviderService.class);
      when(authenticationProviderService.getProviderByName(anyString())).thenReturn(fileProvider);

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
