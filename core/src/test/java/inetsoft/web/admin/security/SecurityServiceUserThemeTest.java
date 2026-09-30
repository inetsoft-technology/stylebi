/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2026, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.web.admin.security;

/*
 * Bug #77265: REST create/update user accepted "theme" but never assigned it, because
 * updateUser only called the rename-only IdentityThemeService.updateTheme and createUser never
 * touched the theme service. Both now call updateUserTheme like the EM user editor
 * (null = keep, "" = default theme, an id that cannot be assigned to the user's organization is
 * ignored with a warning).
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.user.*;
import inetsoft.web.admin.general.LocalizationSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SecurityServiceUserThemeTest {
   @BeforeEach
   void setUp() {
      authenticationProvider = mock(AuthenticationProvider.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getAuthenticationProvider()).thenReturn(authenticationProvider);

      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      securityEngineStatic = mockStatic(SecurityEngine.class,
                                        withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);

      editableProvider = mock(EditableAuthenticationProvider.class,
                              withSettings().defaultAnswer(CALLS_REAL_METHODS).lenient());

      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getEditableAuthenticationProvider(securityProvider))
         .thenReturn(editableProvider);
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(new Properties());

      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      organizationManagerStatic = mockStatic(OrganizationManager.class,
                                             withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      when(orgManager.getCurrentOrgID()).thenReturn("org1");
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);

      Audit audit = mock(Audit.class, withSettings().lenient());
      auditStatic = mockStatic(Audit.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));

      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("caller", "org1").convertToKey());

      when(securityProvider.getOrganizationIDs())
         .thenReturn(new String[]{ "org1", "org2", Organization.getDefaultOrganizationID() });
      when(securityProvider.checkPermission(eq(principal), any(ResourceType.class), anyString(),
                                            eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      when(editableProvider.getRoles()).thenReturn(new IdentityID[0]);

      themes = new HashSet<>();
      customThemesManager = mock(CustomThemesManager.class, withSettings().lenient());
      when(customThemesManager.getCustomThemes()).thenAnswer(inv -> themes);
      doAnswer(invocation -> {
         CustomThemesManager.ThemesUpdate<?> update = invocation.getArgument(0);
         Set<CustomTheme> result = update.apply(new HashSet<>(themes));

         if(result != null) {
            themes = result;
         }

         return null;
      }).when(customThemesManager).updateCustomThemes(any());

      service = new SecurityService(
         securityEngine, mock(IdentityService.class, withSettings().lenient()),
         mock(ActionPermissionService.class), mock(LocalizationSettingsService.class),
         new IdentityThemeService(customThemesManager), mock(SystemAdminService.class),
         mock(UserTreeService.class, withSettings().lenient()), customThemesManager);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      organizationManagerStatic.close();
      auditStatic.close();
      sreeEnvStatic.close();
      securityEngineStatic.close();
   }

   @Test
   void update_validThemeId_assignsUser() throws Exception {
      themes.add(theme("t1", "org1"));
      update("u1", "u1", "org1", "t1");
      assertEquals(List.of("u1"), users("t1"));
   }

   @Test
   void update_themeOmitted_keepsAssignment() throws Exception {
      themes.add(theme("t1", "org1", "u1"));
      update("u1", "u1", "org1", null);
      assertEquals(List.of("u1"), users("t1"));
   }

   @Test
   void update_emptyTheme_clearsAssignment() throws Exception {
      themes.add(theme("t1", "org1", "u1"));
      update("u1", "u1", "org1", "");
      assertTrue(users("t1").isEmpty());
   }

   @Test
   void update_switchTheme_movesUser() throws Exception {
      themes.add(theme("t1", "org1", "u1"));
      themes.add(theme("t2", "org1"));
      update("u1", "u1", "org1", "t2");
      assertTrue(users("t1").isEmpty());
      assertEquals(List.of("u1"), users("t2"));
   }

   @Test
   void update_renameWithTheme_assignmentFollowsNewName() throws Exception {
      themes.add(theme("t1", "org1", "u1"));
      themes.add(theme("t2", "org1"));
      update("u1", "u2", "org1", "t2");
      assertTrue(users("t1").isEmpty());
      assertEquals(List.of("u2"), users("t2"));
   }

   @Test
   void update_renameThemeOmitted_stillRenames() throws Exception {
      themes.add(theme("t1", "org1", "u1"));
      update("u1", "u2", "org1", null);
      assertEquals(List.of("u2"), users("t1"));
   }

   @Test
   void update_unknownThemeId_ignoredAndKeepsAssignment() throws Exception {
      themes.add(theme("t1", "org1", "u1"));
      update("u1", "u1", "org1", "nope");
      assertEquals(List.of("u1"), users("t1"));
   }

   @Test
   void update_defaultLiteralId_clearsAssignment() throws Exception {
      // Bug #77304: the reserved default theme id selects the default theme like ""
      themes.add(theme("t1", "org1", "u1"));
      update("u1", "u1", "org1", "default");
      assertTrue(users("t1").isEmpty());
   }

   @Test
   void update_otherOrgTheme_ignored() throws Exception {
      themes.add(theme("t2", "org2"));
      update("u1", "u1", "org1", "t2");
      assertTrue(users("t2").isEmpty());
   }

   @Test
   void update_globalTheme_assignedOnlyForDefaultOrgUser() throws Exception {
      String defaultOrg = Organization.getDefaultOrganizationID();
      themes.add(theme("g1", null));
      update("d1", "d1", defaultOrg, "g1");
      assertEquals(List.of("d1"), users("g1"));

      update("u1", "u1", "org1", "g1");
      assertEquals(List.of("d1"), users("g1"));
   }

   @Test
   void create_validThemeId_assignsUser() throws Exception {
      themes.add(theme("t1", "org1"));
      create("n1", "org1", "t1");
      assertEquals(List.of("n1"), users("t1"));
   }

   @Test
   void create_themeOmittedOrEmpty_assignsNothing() throws Exception {
      themes.add(theme("t1", "org1"));
      create("n1", "org1", null);
      create("n2", "org1", "");
      assertTrue(users("t1").isEmpty());
   }

   @Test
   void create_unknownOrOtherOrgTheme_userCreatedNotAssigned() throws Exception {
      themes.add(theme("t2", "org2"));
      create("n1", "org1", "nope");
      create("n2", "org1", "t2");
      assertTrue(users("t2").isEmpty());
      verify(editableProvider, times(2)).addUser(any(FSUser.class));
   }

   private void update(String oldName, String newName, String orgID, String themeId)
      throws Exception
   {
      IdentityID oldId = new IdentityID(oldName, orgID);
      FSUser oldUser = new FSUser(oldId);
      when(securityProvider.getUser(oldId)).thenReturn(oldUser);
      when(editableProvider.getUser(oldId)).thenReturn(oldUser);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID(newName, orgID));
      request.setTheme(themeId);
      service.updateUser(oldId, request, principal);
   }

   private void create(String name, String orgID, String themeId) throws Exception {
      when(editableProvider.getOrganization(orgID)).thenReturn(new FSOrganization(orgID));

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID(name, orgID));
      request.setPassword("Str0ng!Passw0rd");
      request.setTheme(themeId);
      service.createUser(request, null, principal);
   }

   private List<String> users(String themeId) {
      return themes.stream().filter(t -> themeId.equals(t.getId())).findFirst().orElseThrow()
         .getUsers();
   }

   private static CustomTheme theme(String id, String orgID, String... users) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      theme.setOrgID(orgID);
      theme.setJarPath(orgID == null ? "portal/theme/" + id + ".jar" :
                          "portal/" + orgID + "/theme/" + id + ".jar");
      theme.setUsers(new ArrayList<>(Arrays.asList(users)));
      return theme;
   }

   private Set<CustomTheme> themes;
   private SecurityProvider securityProvider;
   private AuthenticationProvider authenticationProvider;
   private EditableAuthenticationProvider editableProvider;
   private CustomThemesManager customThemesManager;
   private Principal principal;
   private SecurityService service;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
}
