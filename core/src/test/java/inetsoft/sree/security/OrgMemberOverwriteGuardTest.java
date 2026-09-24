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
 * Editing an organization must never overwrite an existing identity of another organization.
 *
 * A GET of the organization edit model with a name-only key used to resolve the org id from the
 * caller's current org, so the returned model carried the current org's users and roles as
 * members. Saving that model with a changed org id made updateOrganizationMembers() treat those
 * identities as "new" members (they are not users of the edited org) and store a blank
 * FSUser/FSRole over each of them, wiping every password and role of the current org.
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
import inetsoft.util.MessageException;
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

import java.lang.reflect.InvocationTargetException;
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
class OrgMemberOverwriteGuardTest {
   private static final String HOST_ORG_ID = "ovguard_host";
   private static final String HOST_ORG_NAME = "OvGuardHost";
   private static final String EDITED_ORG_ID = "ovguard_edited";
   private static final String EDITED_ORG_NAME = "OvGuardEdited";
   private static final String RENAMED_ORG_ID = "ovguard_renamed";
   private static final String RENAMED_ORG_NAME = "OvGuardRenamed";

   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider fileProvider;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(HOST_ORG_NAME, HOST_ORG_ID)
         .addOrg(EDITED_ORG_NAME, EDITED_ORG_ID)
         .addRole("hostRole", HOST_ORG_ID)
         .addUser("hostAdmin", HOST_ORG_ID, "password")
         .addUserToRole("hostAdmin", "hostRole", HOST_ORG_ID)
         .setup();

      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);
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
   void setOrganizationInfo_userMemberOfOtherOrg_rejectedAndUserUntouched() throws Exception {
      IdentityID hostAdmin = new IdentityID("hostAdmin", HOST_ORG_ID);
      User before = fileProvider.getUser(hostAdmin);
      assertNotNull(before, "precondition: host org user must exist");
      String passwordBefore = before.getPassword();
      IdentityID[] rolesBefore = before.getRoles();

      assertRenameRejected(member(hostAdmin, Identity.USER));

      User after = fileProvider.getUser(hostAdmin);
      assertNotNull(after, "host org user must still exist");
      assertEquals(passwordBefore, after.getPassword(), "host org user's password must be kept");
      assertArrayEquals(rolesBefore, after.getRoles(), "host org user's roles must be kept");
   }

   @Test
   void setOrganizationInfo_roleMemberOfOtherOrg_rejectedAndRoleUntouched() throws Exception {
      IdentityID hostRole = new IdentityID("hostRole", HOST_ORG_ID);
      assertNotNull(fileProvider.getRole(hostRole), "precondition: host org role must exist");

      assertRenameRejected(member(hostRole, Identity.ROLE));

      assertNotNull(fileProvider.getRole(hostRole), "host org role must still exist");
      assertTrue(Arrays.asList(fileProvider.getUser(new IdentityID("hostAdmin", HOST_ORG_ID))
                                  .getRoles()).contains(hostRole),
                 "host org user must still hold the host org role");
   }

   @Test
   void updateOrganizationMembers_existingIdentityNotOverwritten_newMemberCreated() {
      IdentityID hostAdmin = new IdentityID("hostAdmin", HOST_ORG_ID);
      IdentityID newbie = new IdentityID("newbie", EDITED_ORG_ID);
      String passwordBefore = fileProvider.getUser(hostAdmin).getPassword();
      IdentityID[] rolesBefore = fileProvider.getUser(hostAdmin).getRoles();

      FSOrganization renamedOrg = new FSOrganization(RENAMED_ORG_ID);
      renamedOrg.setName(RENAMED_ORG_NAME);
      renamedOrg.setMembers(new String[]{ "hostAdmin", "newbie" });

      ThreadContext.setContextPrincipal(new SRPrincipal(new IdentityID("tester", EDITED_ORG_ID),
         new IdentityID[0], new String[0], EDITED_ORG_ID, 1L));

      ReflectionTestUtils.invokeMethod(createIdentityService(), "updateOrganizationMembers",
         renamedOrg,
         new ArrayList<>(List.of(member(hostAdmin, Identity.USER), member(newbie, Identity.USER))),
         EDITED_ORG_ID, fileProvider);

      User after = fileProvider.getUser(hostAdmin);
      assertNotNull(after, "existing user of another org must not be removed");
      assertEquals(passwordBefore, after.getPassword(),
                   "existing user of another org must not be replaced with a blank user");
      assertArrayEquals(rolesBefore, after.getRoles(),
                        "existing user of another org must keep its roles");
      assertNotNull(fileProvider.getUser(newbie), "a genuinely new member must still be created");
   }

   @Test
   void getOrganizationModel_nameDoesNotMatchOrgId_rejected() {
      OrganizationContextHolder.setCurrentOrgId(HOST_ORG_ID);
      Principal principal = new SRPrincipal(new IdentityID("tester", HOST_ORG_ID),
         new IdentityID[0], new String[0], HOST_ORG_ID, 1L);
      ThreadContext.setContextPrincipal((SRPrincipal) principal);
      UserTreeService userTreeService = createUserTreeService();

      // what a name-only key resolves to: the edited org's name with the caller's current org id
      assertThrows(MessageException.class, () -> userTreeService.getOrganizationModel(
         "", new IdentityID(EDITED_ORG_NAME, HOST_ORG_ID), principal, false, null));
      // "name~;~null"
      assertThrows(MessageException.class, () -> userTreeService.getOrganizationModel(
         "", new IdentityID(EDITED_ORG_NAME, null), principal, false, null));
   }

   private void assertRenameRejected(IdentityModel otherOrgMember) throws Exception {
      FSOrganization oldOrg = (FSOrganization) fileProvider.getOrganization(EDITED_ORG_ID);
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .id(RENAMED_ORG_ID)
         .name(RENAMED_ORG_NAME)
         .oldName(EDITED_ORG_NAME)
         .members(List.of(otherOrgMember))
         .status(true)
         .build();

      Method setOrganizationInfo = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, Principal.class);
      setOrganizationInfo.setAccessible(true);

      InvocationTargetException ex = assertThrows(InvocationTargetException.class,
         () -> setOrganizationInfo.invoke(createIdentityService(), oldOrg, model, fileProvider,
                                          mock(Principal.class)));
      assertInstanceOf(MessageException.class, ex.getCause(),
                       "a member that is an existing identity of another org must be rejected");
      assertNotNull(fileProvider.getOrganization(EDITED_ORG_ID),
                    "the rejected edit must not have renamed the organization");
   }

   private static IdentityModel member(IdentityID id, int type) {
      return IdentityModel.builder().identityID(id).type(type).build();
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
         authenticationProviderService, mock(SystemAdminService.class), mock(IdentityService.class),
         mock(LocalizationSettingsService.class), SecurityEngine.getSecurity(),
         mock(IdentityThemeService.class), mock(SimpMessagingTemplate.class),
         mock(FavoritesService.class), mock(DataCycleManager.class), mock(LicenseManager.class),
         mock(MVManager.class), mock(IndexedStorage.class), mock(CustomThemesManager.class),
         mock(DashboardRegistryManager.class), mock(XRepository.class),
         mock(DependencyStorageService.class), mock(RecycleBin.class));
   }
}
