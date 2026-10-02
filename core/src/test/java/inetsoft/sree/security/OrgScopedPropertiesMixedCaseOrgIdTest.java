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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.CustomThemesManagerMocks;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77534: org-scoped properties are stored as <code>inetsoft.org.&lt;org&gt;.&lt;name&gt;</code>
 * with the org ID lower case, also when the current org ID has upper case letters (the enterprise
 * OrganizationManager keeps its case). The org delete and rename clean-ups must find them for an
 * org ID with upper case letters, and a rename that only changes the case of the org ID must keep
 * them, because the old and the new org ID have the same property names.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  OrgScopedPropertiesMixedCaseOrgIdTest.PortalThemesManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgScopedPropertiesMixedCaseOrgIdTest {
   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      List<String> keys = SreeEnv.getProperties().keySet().stream()
         .map(String.class::cast)
         .filter(key -> key.contains("77534"))
         .toList();

      for(String key : keys) {
         SreeEnv.remove(key);
      }
   }

   @Test
   void delete_mixedCaseOrgId_removesOrgScopedProperties() {
      String orgId = "B77534Del";
      writeAs(orgId, "format.date", "MM/dd/yyyy");
      SreeEnv.setProperty(LOG_KEY + "^" + orgId, "debug");
      assertEquals(List.of("inetsoft.org.b77534del.format.date"), scopedKeys("b77534del"),
                   "precondition: the org-scoped property is stored with the org ID lower case");

      // the org delete path of IdentityService.syncIdentity()
      createIdentityService().removeOrgProperties(orgId);

      assertEquals(List.of(), scopedKeys("b77534del"),
                   "the deleted org's scoped properties must be removed");
      assertNull(SreeEnv.getProperty(LOG_KEY + "^" + orgId),
                 "the deleted org's log level property must be removed");
   }

   @Test
   void updateOrgProperties_mixedCaseOrgIds_movesOrgScopedProperties() {
      String fromOrgId = "B77534Ren";
      String toOrgId = "B77534RenNew";
      writeAs(fromOrgId, "format.date", "MM/dd/yyyy");

      createIdentityService().updateOrgProperties(fromOrgId, toOrgId);

      assertEquals(List.of(), scopedKeys("b77534ren"), "the old org's properties must be moved");
      assertEquals("MM/dd/yyyy", SreeEnv.getProperty("inetsoft.org.b77534rennew.format.date"),
                   "the new org must hold the moved property");
   }

   @Test
   void delete_mixedCaseOrgIdInTurkishLocale_removesOrgScopedProperties() {
      // the engine lower-cases the org ID in the default locale, where the upper case I of a
      // Turkish locale becomes a dotless i, so the clean-up must lower-case it the same way
      Locale locale = Locale.getDefault();
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      try {
         String orgId = "B77534DelI";
         writeAs(orgId, "format.date", "MM/dd/yyyy");
         assertEquals(1, scopedKeys("b77534deli").size() + scopedKeys("b77534delı").size(),
                      "precondition: the org-scoped property is stored");

         createIdentityService().removeOrgProperties(orgId);

         assertEquals(List.of(), scopedKeys("b77534deli"));
         assertEquals(List.of(), scopedKeys("b77534delı"));
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   @Test
   void rename_mixedCaseOrgIds_movesOrgScopedProperties() {
      String fromOrgId = "B77534From";
      String toOrgId = "B77534To";
      writeAs(fromOrgId, "format.date", "MM/dd/yyyy");
      SreeEnv.setProperty(LOG_KEY + "^" + fromOrgId, "debug");

      IdentityService identityService = renameOrganization(fromOrgId, toOrgId);

      assertEquals(List.of(), scopedKeys("b77534from"), "the old org's properties must be moved");
      assertEquals("MM/dd/yyyy", SreeEnv.getProperty("inetsoft.org.b77534to.format.date"),
                   "the renamed org must hold the moved property");
      assertEquals("debug", SreeEnv.getProperty(LOG_KEY + "^" + toOrgId));
      assertNull(SreeEnv.getProperty(LOG_KEY + "^" + fromOrgId));
      verify(identityService).removeOrgProperties(fromOrgId);
   }

   @Test
   void rename_onlyCaseOfOrgIdChanges_keepsOrgScopedProperties() {
      String fromOrgId = "B77534Case";
      String toOrgId = "b77534case";
      writeAs(fromOrgId, "format.date", "MM/dd/yyyy");
      SreeEnv.setProperty(LOG_KEY + "^" + fromOrgId, "debug");

      renameOrganization(fromOrgId, toOrgId);

      assertEquals("MM/dd/yyyy", SreeEnv.getProperty("inetsoft.org.b77534case.format.date"),
                   "a rename that only changes the case of the org ID must keep its properties");
      assertEquals("debug", SreeEnv.getProperty(LOG_KEY + "^" + toOrgId),
                   "the log level property must be moved to the new org ID");
      assertNull(SreeEnv.getProperty(LOG_KEY + "^" + fromOrgId));
   }

   /**
    * Writes an org-scoped property through the real org-scoped write path, as a principal of an
    * org whose current org ID keeps its case like the enterprise OrganizationManager's.
    */
   private static void writeAs(String orgId, String name, String value) {
      OrganizationManager manager = mock(OrganizationManager.class);
      when(manager.getCurrentOrgID()).thenReturn(orgId);
      ThreadContext.setContextPrincipal(new SRPrincipal(
         new IdentityID("tester", orgId), new IdentityID[0], new String[0], orgId, 1L));

      try(MockedStatic<OrganizationManager> om = mockStatic(OrganizationManager.class)) {
         om.when(OrganizationManager::getInstance).thenReturn(manager);
         SreeEnv.setProperty(name, value, true);
      }
      finally {
         ThreadContext.setContextPrincipal(null);
      }
   }

   /**
    * Renames the organization through AbstractEditableAuthenticationProvider.copyOrganization()
    * with the property methods of IdentityService running for real.
    */
   private static IdentityService renameOrganization(String fromOrgId, String toOrgId) {
      FSOrganization fromOrg = new FSOrganization(fromOrgId);
      fromOrg.setName(fromOrgId);
      IdentityService identityService = mock(IdentityService.class);
      doCallRealMethod().when(identityService).updateOrgProperties(any(), any());
      doCallRealMethod().when(identityService).removeOrgProperties(any());
      CustomThemesManager themesManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themesManager);
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>());

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(themesManager);
         new StubProvider().copyOrganization(
            fromOrg, null, toOrgId, toOrgId, identityService, mock(IdentityThemeService.class),
            mock(DashboardRegistryManager.class), mock(DataCycleManager.class),
            mock(Principal.class), true);
      }
      catch(Exception e) {
         // Tolerated: the end of the rename needs registries that this context does not
         // provide. The property steps run before it, which is verified below.
      }

      verify(identityService).updateOrgProperties(fromOrgId, toOrgId);
      // the step right after the removal of the old org's properties ran
      verify(identityService).removeOrgScopedDataSpaceElements(any());
      return identityService;
   }

   private static List<String> scopedKeys(String lowerCaseOrgId) {
      String prefix = "inetsoft.org." + lowerCaseOrgId + ".";
      return SreeEnv.getProperties().keySet().stream()
         .map(String.class::cast)
         .filter(key -> key.startsWith(prefix))
         .sorted()
         .toList();
   }

   private static IdentityService createIdentityService() {
      return new IdentityService(
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         Optional.empty(), null, null, null, null, null, null, null, null, null, null, null,
         null, null, Optional.empty());
   }

   private static final String LOG_KEY = "log.level.test77534";

   @Configuration
   public static class PortalThemesManagerConfig {
      @Bean
      public PortalThemesManager portalThemesManager() {
         PortalThemesManager mockPortalThemesManager = mock(PortalThemesManager.class);
         when(mockPortalThemesManager.getCssEntries()).thenReturn(new HashMap<>());
         return mockPortalThemesManager;
      }
   }

   private static class StubProvider extends AbstractEditableAuthenticationProvider {
      @Override public IdentityID[] getUsers() { return new IdentityID[0]; }
      @Override public IdentityID[] getRoles() { return new IdentityID[0]; }
      @Override public IdentityID[] getGroups() { return new IdentityID[0]; }
      @Override public User  getUser(IdentityID id)  { return null; }
      @Override public Group getGroup(IdentityID id) { return null; }
      @Override public Role  getRole(IdentityID id)  { return null; }
      @Override public void addOrganization(Organization organization) { }
      @Override public void removeOrganization(String id) { }
      @Override public boolean authenticate(IdentityID userIdentity, Object credential) { return false; }
      @Override public Organization getOrganization(String id)  { return null; }
      @Override public String getOrgIdFromName(String name)     { return null; }
      @Override public String getOrgNameFromID(String id)       { return null; }
      @Override public String[] getOrganizationIDs()            { return new String[0]; }
      @Override public String[] getOrganizationNames()          { return new String[0]; }
      @Override public void tearDown() {}
   }
}
