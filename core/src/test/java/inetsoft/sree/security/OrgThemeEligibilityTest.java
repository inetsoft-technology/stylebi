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
 * Bug #77095: an organization update (REST PUT and EM org pane save, both of which end in
 * IdentityService.setOrganizationInfo()) must only accept a global theme or a theme owned by the
 * edited organization as the organization default. Any other requested id (another org's private
 * theme, or an id with no theme) is ignored and the organization keeps its current theme. An empty
 * theme (the EM "Default" option) clears the organization default like null.
 */

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.CustomThemesManagerMocks;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.DataSpace;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
class OrgThemeEligibilityTest {
   private static final String EDITED_ORG_ID = "orgtheme_edited";
   private static final String EDITED_ORG_NAME = "OrgThemeEdited";
   private static final String OTHER_ORG_ID = "orgtheme_other";
   private static final String OTHER_ORG_NAME = "OrgThemeOther";
   private static final String RENAMED_ORG_ID = "orgtheme_renamed";
   private static final String RENAMED_ORG_NAME = "OrgThemeRenamed";

   private static final String GLOBAL_THEME = "orgtheme_global";
   private static final String OWN_THEME = "orgtheme_own";
   private static final String CURRENT_THEME = "orgtheme_current";
   private static final String OTHER_THEME = "orgtheme_other_private";

   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider fileProvider;
   private CustomThemesManager themesManager;
   private DashboardRegistryManager dashboardRegistryManager;
   private Set<CustomTheme> themes;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(EDITED_ORG_NAME, EDITED_ORG_ID)
         .addOrg(OTHER_ORG_NAME, OTHER_ORG_ID)
         .setup();

      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);

      themes = new HashSet<>();
      themes.add(theme(GLOBAL_THEME, null));
      themes.add(theme(OWN_THEME, EDITED_ORG_ID));
      themes.add(theme(CURRENT_THEME, EDITED_ORG_ID, EDITED_ORG_ID));
      themes.add(theme(OTHER_THEME, OTHER_ORG_ID, OTHER_ORG_ID));

      themesManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themesManager);
      when(themesManager.getCustomThemes()).thenReturn(themes);

      dashboardRegistryManager = mock(DashboardRegistryManager.class);

      FSOrganization edited = (FSOrganization) fileProvider.getOrganization(EDITED_ORG_ID);
      edited.setTheme(CURRENT_THEME);
      fileProvider.setOrganization(EDITED_ORG_ID, edited);

      ThreadContext.setContextPrincipal(new SRPrincipal(new IdentityID("tester", EDITED_ORG_ID),
         new IdentityID[0], new String[0], EDITED_ORG_ID, 1L));
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
   void otherOrgPrivateTheme_ignoredAndCurrentKept() throws Exception {
      updateTheme(OTHER_THEME);

      assertEquals(CURRENT_THEME, storedTheme(),
                   "another org's private theme must not become the org default");
      assertEquals(List.of(OTHER_ORG_ID), themeById(OTHER_THEME).getOrganizations(),
                   "the other org's private theme must not gain the edited org");
      assertTrue(themeById(CURRENT_THEME).getOrganizations().contains(EDITED_ORG_ID),
                 "the current theme must keep the edited org");
      verify(themesManager, never()).setOrgSelectedTheme(eq(OTHER_THEME), anyString());
      verify(themesManager, never()).setOrgSelectedTheme(anyString(), eq(EDITED_ORG_ID));
   }

   @Test
   void unknownTheme_ignoredAndCurrentKept() throws Exception {
      updateTheme("orgtheme_no_such_theme");

      assertEquals(CURRENT_THEME, storedTheme(), "an unknown theme id must not be stored");
      verify(themesManager, never()).setOrgSelectedTheme(anyString(), eq(EDITED_ORG_ID));
   }

   @Test
   void globalTheme_accepted() throws Exception {
      updateTheme(GLOBAL_THEME);

      assertEquals(GLOBAL_THEME, storedTheme());
      assertTrue(themeById(GLOBAL_THEME).getOrganizations().contains(EDITED_ORG_ID));
      assertFalse(themeById(CURRENT_THEME).getOrganizations().contains(EDITED_ORG_ID));
      verify(themesManager).setOrgSelectedTheme(GLOBAL_THEME, EDITED_ORG_ID);
   }

   @Test
   void ownOrgTheme_accepted() throws Exception {
      updateTheme(OWN_THEME);

      assertEquals(OWN_THEME, storedTheme());
      assertTrue(themeById(OWN_THEME).getOrganizations().contains(EDITED_ORG_ID));
      verify(themesManager).setOrgSelectedTheme(OWN_THEME, EDITED_ORG_ID);
   }

   @Test
   void emptyTheme_clearsToDefault() throws Exception {
      // the EM "Default" option sends an empty theme
      updateTheme("");

      assertNull(storedTheme(), "an empty theme must clear the org default");
      assertFalse(themeById(CURRENT_THEME).getOrganizations().contains(EDITED_ORG_ID));
      verify(themesManager).setOrgSelectedTheme("default", EDITED_ORG_ID);
      verify(themesManager, never()).setOrgSelectedTheme("", EDITED_ORG_ID);
   }

   @Test
   void nullTheme_clearsToDefault() throws Exception {
      updateTheme(null);

      assertNull(storedTheme(), "a null theme must clear the org default");
      assertFalse(themeById(CURRENT_THEME).getOrganizations().contains(EDITED_ORG_ID));
      verify(themesManager).setOrgSelectedTheme("default", EDITED_ORG_ID);
   }

   @Test
   void unchangedTheme_roundTripIsNoOp() throws Exception {
      updateTheme(CURRENT_THEME);

      assertEquals(CURRENT_THEME, storedTheme());
      verify(themesManager, never()).setCustomThemes(any());
      verify(themesManager, never()).setOrgSelectedTheme(anyString(), anyString());
   }

   @Test
   void unchangedLegacyOtherOrgTheme_roundTripIsNoOp() throws Exception {
      // a value stored before the fix is re-sent unchanged by a GET -> PUT round trip
      FSOrganization edited = (FSOrganization) fileProvider.getOrganization(EDITED_ORG_ID);
      edited.setTheme(OTHER_THEME);
      fileProvider.setOrganization(EDITED_ORG_ID, edited);

      updateTheme(OTHER_THEME);

      assertEquals(OTHER_THEME, storedTheme());
      verify(themesManager, never()).setCustomThemes(any());
      verify(themesManager, never()).setOrgSelectedTheme(anyString(), anyString());
   }

   @Test
   void idChange_otherOrgPrivateTheme_newOrgKeepsCurrentTheme() throws Exception {
      FSOrganization newOrg = renameWithTheme(OTHER_THEME);

      assertEquals(CURRENT_THEME, newOrg.getTheme(),
                   "the renamed org must keep its current theme, not the rejected one");
      assertEquals(List.of(OTHER_ORG_ID), themeById(OTHER_THEME).getOrganizations(),
                   "the other org's private theme must not gain the renamed org");
      verify(themesManager, never()).setOrgSelectedTheme(eq(OTHER_THEME), anyString());
   }

   @Test
   void idChange_ownOrgTheme_accepted() throws Exception {
      // the edited org's own themes still carry the old org id when the theme is chosen,
      // copyThemes() migrates them to the new id afterwards
      FSOrganization newOrg = renameWithTheme(OWN_THEME);

      assertEquals(OWN_THEME, newOrg.getTheme());
      assertTrue(themeById(OWN_THEME).getOrganizations().contains(RENAMED_ORG_ID));
      verify(themesManager).setOrgSelectedTheme(OWN_THEME, RENAMED_ORG_ID);
   }

   private void updateTheme(String theme) throws Exception {
      FSOrganization oldOrg = (FSOrganization) fileProvider.getOrganization(EDITED_ORG_ID);
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .id(EDITED_ORG_ID)
         .name(EDITED_ORG_NAME)
         .oldName(EDITED_ORG_NAME)
         .members(List.of())
         .status(true)
         .theme(theme)
         .build();

      invokeSetOrganizationInfo(oldOrg, model);
   }

   /**
    * Changes the edited organization's id and returns the new organization built by
    * setOrganizationInfo(), whose theme is what copyOrganizationInternal() stores for the renamed
    * organization.
    */
   private FSOrganization renameWithTheme(String theme) throws Exception {
      FSOrganization oldOrg = (FSOrganization) fileProvider.getOrganization(EDITED_ORG_ID);
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .id(RENAMED_ORG_ID)
         .name(RENAMED_ORG_NAME)
         .oldName(EDITED_ORG_NAME)
         .members(List.of())
         .status(true)
         .theme(theme)
         .build();

      try {
         invokeSetOrganizationInfo(oldOrg, model);
      }
      catch(InvocationTargetException e) {
         // Tolerated: syncIdentity() needs storage infrastructure this minimal context does not
         // provide. The new organization's theme is set before syncIdentity() is called.
      }

      ArgumentCaptor<Organization> newOrg = ArgumentCaptor.forClass(Organization.class);
      verify(dashboardRegistryManager).migrateRegistry(isNull(), any(), newOrg.capture());
      return (FSOrganization) newOrg.getValue();
   }

   private void invokeSetOrganizationInfo(FSOrganization oldOrg, EditOrganizationPaneModel model)
      throws Exception
   {
      Method setOrganizationInfo = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, Principal.class);
      setOrganizationInfo.setAccessible(true);
      setOrganizationInfo.invoke(createIdentityService(), oldOrg, model, fileProvider,
                                 mock(Principal.class));
   }

   private String storedTheme() {
      return fileProvider.getOrganization(EDITED_ORG_ID).getTheme();
   }

   private CustomTheme themeById(String id) {
      return themes.stream().filter(t -> id.equals(t.getId())).findFirst().orElseThrow();
   }

   private static CustomTheme theme(String id, String orgID, String... organizations) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      theme.setOrgID(orgID);
      theme.setOrganizations(new ArrayList<>(Arrays.asList(organizations)));
      return theme;
   }

   private IdentityService createIdentityService() throws Exception {
      RepletRegistryManager repletRegistryManager = mock(RepletRegistryManager.class);
      when(repletRegistryManager.getRegistry(anyString())).thenReturn(mock(RepletRegistry.class));
      DataSpace dataSpace = mock(DataSpace.class);
      when(dataSpace.getOrgScopedPaths(any())).thenReturn(new String[0]);

      return new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         null, null, null, mock(FavoritesService.class), null, null, null, null,
         null, null, null, null, Optional.empty(), null, themesManager, null,
         dashboardRegistryManager, null, null, null, null, dataSpace, null, null, null,
         repletRegistryManager, Optional.empty());
   }
}
