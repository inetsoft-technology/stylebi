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

import inetsoft.sree.ClientInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SecurityEngine permission scenario table:
 *  [Basic allow]      security provider is unavailable                            -> allowed
 *  [Basic deny]       principal is null while a provider exists                   -> denied
 *  [Login guard]      non-exempt resource with unauthenticated principal          -> SecurityException
 *  [Exempt path]      MY_DASHBOARDS skips login guard                             -> provider result returned
 *  [Fallback admin]   requested action denied but ADMIN granted                   -> allowed
 *  [VPM path]         principal marked as VPM user                                -> VPM provider selected
 *  [Everyone mode]    blank datasource root permission under everyone mode        -> READ allowed
 *
 * Test design:
 *  - use the Spring test context required by SecurityEngine/DataSpace initialization
 *  - replace internal providers through reflection to keep scenarios deterministic
 *  - keep comments in English per java-unit-test-generation-prompt.md
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class SecurityEnginePermissionTest {
   private static final IdentityID USER_ID = new IdentityID("alice", "org1");

   @BeforeEach
   void setUp() {
      ReflectionTestUtils.setField(engine, "provider", null);
      ReflectionTestUtils.setField(engine, "vprovider", null);
      ReflectionTestUtils.setField(engine, "vpm_provider", null);
      ReflectionTestUtils.setField(engine, "users", new ConcurrentHashMap<ClientInfo, SRPrincipal>());

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("security.datasource.everyone", "false");
      SreeEnv.setProperty("security.script.everyone", "false");
      SreeEnv.setProperty("security.tablestyle.everyone", "false");
      SreeEnv.setProperty("security.scheduletask.everyone", "false");

      SecurityEngine.updateSecurityDatasourceEveryoneValue();
      SecurityEngine.updateSecurityScriptEveryoneValue();
      SecurityEngine.updateSecurityTablestyleEveryoneValue();
      SecurityEngine.updateSecuritySchduletaskEveryoneValue();
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("security.enabled", "false");
      SreeEnv.setProperty("security.datasource.everyone", "false");
      SreeEnv.setProperty("security.script.everyone", "false");
      SreeEnv.setProperty("security.tablestyle.everyone", "false");
      SreeEnv.setProperty("security.scheduletask.everyone", "false");

      SecurityEngine.updateSecurityDatasourceEveryoneValue();
      SecurityEngine.updateSecurityScriptEveryoneValue();
      SecurityEngine.updateSecurityTablestyleEveryoneValue();
      SecurityEngine.updateSecuritySchduletaskEveryoneValue();
   }

   // [Scenario: basic allow] no provider is available -> checkPermission defaults to allow
   // Setup: security disabled and both provider/vprovider are absent
   @Test
   void checkPermission_noProviderAvailable_returnsTrue() throws Exception {
      SreeEnv.setProperty("security.enabled", "false");

      boolean allowed = engine.checkPermission(new SRPrincipal(USER_ID), ResourceType.VIEWSHEET,
                                               "vs/test", ResourceAction.READ);

      assertTrue(allowed);
   }

   // [Scenario: basic deny] principal is null while a provider exists -> denied
   // Setup: security enabled and provider present
   @Test
   void checkPermission_nullPrincipalWithProvider_returnsFalse() throws Exception {
      SecurityProvider provider = mock(SecurityProvider.class);
      setProvider(provider);

      boolean allowed = engine.checkPermission(null, ResourceType.VIEWSHEET,
                                               "vs/test", ResourceAction.READ);

      assertFalse(allowed);
      verifyNoInteractions(provider);
   }

   // [Scenario: login guard / exempt path] unauthenticated principal handling depends on resource type
   // Setup: provider exists, principal is not cached as a logged-in user, and only exempt resource types bypass login validation
   @ParameterizedTest
   @MethodSource("unauthenticatedPermissionCases")
   void checkPermission_unauthenticatedPrincipal_behavesByResourceType(ResourceType resourceType,
                                                                       String resource,
                                                                       boolean providerDecision,
                                                                       boolean expectedAllowed,
                                                                       Class<? extends Throwable> expectedException)
      throws Exception
   {
      SecurityProvider provider = mock(SecurityProvider.class);
      setProvider(provider);
      SRPrincipal principal = new SRPrincipal(USER_ID, new IdentityID[0], new String[0], "org1", 1L);

      when(provider.checkPermission(principal, resourceType, resource, ResourceAction.READ))
         .thenReturn(providerDecision);

      if(expectedException != null) {
         assertThrows(expectedException,
            () -> engine.checkPermission(principal, resourceType, resource, ResourceAction.READ));
      }
      else {
         boolean allowed = engine.checkPermission(principal, resourceType, resource, ResourceAction.READ);
         assertEquals(expectedAllowed, allowed);
         verify(provider).checkPermission(principal, resourceType, resource, ResourceAction.READ);
      }
   }

   private static Stream<Arguments> unauthenticatedPermissionCases() {
      return Stream.of(
         // normal protected resource: login guard should reject the unauthenticated principal
         Arguments.of(ResourceType.VIEWSHEET, "vs/test", false, false, SecurityException.class),
         // exempt dashboard resource: login guard is skipped and provider decision is returned
         Arguments.of(ResourceType.MY_DASHBOARDS, "dashboard/home", true, true, null),
         // exempt viewsheet action resource: login guard is skipped and provider decision is returned
         Arguments.of(ResourceType.VIEWSHEET_ACTION, "action/share", true, true, null)
      );
   }

   // [Scenario: fallback admin] requested action denied but ADMIN allowed -> allowed
   // Setup: provider denies READ and grants ADMIN for the same resource
   @Test
   void checkPermission_actionDeniedButAdminAllowed_returnsTrue() throws Exception {
      SecurityProvider provider = mock(SecurityProvider.class);
      setProvider(provider);
      SRPrincipal principal = loggedInPrincipal(USER_ID, 3L);

      when(provider.checkPermission(principal, ResourceType.VIEWSHEET,
         "vs/test", ResourceAction.READ)).thenReturn(false);
      when(provider.checkPermission(principal, ResourceType.VIEWSHEET,
         "vs/test", ResourceAction.ADMIN)).thenReturn(true);

      boolean allowed = engine.checkPermission(principal, ResourceType.VIEWSHEET,
                                               "vs/test", ResourceAction.READ);

      assertTrue(allowed);
      verify(provider).checkPermission(principal, ResourceType.VIEWSHEET, "vs/test",
         ResourceAction.READ);
      verify(provider).checkPermission(principal, ResourceType.VIEWSHEET, "vs/test",
         ResourceAction.ADMIN);
   }

   // [Scenario: VPM path] principal marked as VPM user -> VPM provider is used instead of default provider
   // Setup: default provider denies, VPM provider allows, principal carries the VPM flag
   @Test
   void checkPermission_vpmPrincipal_usesVpmProvider() throws Exception {
      SecurityProvider provider = mock(SecurityProvider.class);
      SecurityProvider vpmProvider = mock(SecurityProvider.class);
      setProvider(provider);
      setVpmProvider(vpmProvider);

      SRPrincipal principal = loggedInPrincipal(USER_ID, 4L);
      principal.setProperty(SUtil.VPM_USER, "true");

      when(vpmProvider.checkPermission(principal, ResourceType.VIEWSHEET,
         "vs/test", ResourceAction.READ)).thenReturn(true);

      boolean allowed = engine.checkPermission(principal, ResourceType.VIEWSHEET,
                                               "vs/test", ResourceAction.READ);

      assertTrue(allowed);
      verify(vpmProvider).checkPermission(principal, ResourceType.VIEWSHEET,
         "vs/test", ResourceAction.READ);
      verify(provider, never()).checkPermission(any(), any(), anyString(), any());
   }

   // [Scenario: everyone mode] blank datasource root permission under everyone mode -> READ allowed
   // Setup: datasource everyone is enabled, provider denies direct checks, no explicit root permission exists
   @Test
   void checkPermission_datasourceRootBlankPermissionInEveryoneMode_allowsRead() throws Exception {
      SecurityProvider provider = mock(SecurityProvider.class);
      setProvider(provider);
      SRPrincipal principal = loggedInPrincipal(USER_ID, 5L);

      SreeEnv.setProperty("security.datasource.everyone", "true");
      SecurityEngine.updateSecurityDatasourceEveryoneValue();

      when(provider.checkPermission(principal, ResourceType.DATA_SOURCE_FOLDER,
         "/", ResourceAction.READ)).thenReturn(false);
      when(provider.checkPermission(principal, ResourceType.DATA_SOURCE_FOLDER,
         "/", ResourceAction.ADMIN)).thenReturn(false);
      when(provider.getPermission(ResourceType.DATA_SOURCE_FOLDER, "/")).thenReturn(null);

      boolean allowed = engine.checkPermission(principal, ResourceType.DATA_SOURCE_FOLDER,
                                               "/", ResourceAction.READ);

      assertTrue(allowed);
   }

   // [Scenario: special library everyone mode] blank library permission under everyone mode -> READ allowed
   // Setup: the resource-specific permission is absent, the parent library permission is blank, and READ should be granted
   @ParameterizedTest
   @MethodSource("blankLibraryEveryoneCases")
   void checkPermission_blankLibraryPermissionInEveryoneMode_allowsRead(ResourceType resourceType,
                                                                        String resource)
      throws Exception
   {
      SecurityProvider provider = mock(SecurityProvider.class);
      setProvider(provider);
      SRPrincipal principal = loggedInPrincipal(USER_ID, 6L);
      Resource parent = resourceType.getParent(resource);

      if(resourceType == ResourceType.SCRIPT_LIBRARY || resourceType == ResourceType.SCRIPT) {
         SreeEnv.setProperty("security.script.everyone", "true");
         SecurityEngine.updateSecurityScriptEveryoneValue();
      }
      else {
         SreeEnv.setProperty("security.tablestyle.everyone", "true");
         SecurityEngine.updateSecurityTablestyleEveryoneValue();
      }

      when(provider.checkPermission(principal, resourceType, resource, ResourceAction.READ)).thenReturn(false);
      when(provider.checkPermission(principal, resourceType, resource, ResourceAction.ADMIN)).thenReturn(false);
      when(provider.getPermission(resourceType, resource)).thenReturn(null);

      if(parent != null) {
         when(provider.getPermission(parent.getType(), parent.getPath())).thenReturn(null);
      }

      boolean allowed = engine.checkPermission(principal, resourceType, resource, ResourceAction.READ);

      assertTrue(allowed);
   }

   private static Stream<Arguments> blankLibraryEveryoneCases() {
      return Stream.of(
         // script library: blank library permission allows READ when script everyone mode is enabled
         Arguments.of(ResourceType.SCRIPT_LIBRARY, "*"),
         // table style library: blank library permission allows READ when table-style everyone mode is enabled
         Arguments.of(ResourceType.TABLE_STYLE_LIBRARY, "*")
      );
   }

   // [Scenario: special inherited content] child resource with blank permission inherits parent decision
   // Setup: direct checks fail, child permission is blank, and a parent resource grants READ
   @ParameterizedTest
   @MethodSource("inheritedContentCases")
   void checkPermission_blankSpecialPermission_inheritsParentDecision(ResourceType resourceType,
                                                                      String resource,
                                                                      ResourceType parentType,
                                                                      String parentPath,
                                                                      Runnable enableEveryone)
      throws Exception
   {
      SecurityProvider provider = mock(SecurityProvider.class);
      setProvider(provider);
      SRPrincipal principal = loggedInPrincipal(USER_ID, 7L);

      enableEveryone.run();

      when(provider.checkPermission(principal, resourceType, resource, ResourceAction.READ)).thenReturn(false);
      when(provider.checkPermission(principal, resourceType, resource, ResourceAction.ADMIN)).thenReturn(false);
      when(provider.getPermission(resourceType, resource)).thenReturn(null);
      when(provider.checkPermission(principal, parentType, parentPath, ResourceAction.READ)).thenReturn(true);

      boolean allowed = engine.checkPermission(principal, resourceType, resource, ResourceAction.READ);

      assertTrue(allowed);
   }

   private static Stream<Arguments> inheritedContentCases() {
      return Stream.of(
         // script resource: blank permission inherits the script library decision
         Arguments.of(ResourceType.SCRIPT, "createBulletGraph", ResourceType.SCRIPT_LIBRARY, "*",
            (Runnable) () -> {
               SreeEnv.setProperty("security.script.everyone", "true");
               SecurityEngine.updateSecurityScriptEveryoneValue();
            }),
         // table style resource: blank permission inherits the containing table-style folder decision
         Arguments.of(ResourceType.TABLE_STYLE, "folder::style", ResourceType.TABLE_STYLE, "folder",
            (Runnable) () -> {
               SreeEnv.setProperty("security.tablestyle.everyone", "true");
               SecurityEngine.updateSecurityTablestyleEveryoneValue();
            })
      );
   }

   // [Scenario: datasource everyone families] datasource-related special types honor their dedicated blank-permission rules
   // Setup: each datasource-related resource type uses its own branch under security.datasource.everyone
   @ParameterizedTest
   @MethodSource("datasourceEveryoneCases")
   void checkPermission_datasourceEveryoneSpecialCases_allowReadWhenRulesMatch(ResourceType resourceType,
                                                                               String resource,
                                                                               ResourceType parentType,
                                                                               String parentPath)
      throws Exception
   {
      SecurityProvider provider = mock(SecurityProvider.class);
      setProvider(provider);
      SRPrincipal principal = loggedInPrincipal(USER_ID, 8L);

      SreeEnv.setProperty("security.datasource.everyone", "true");
      SecurityEngine.updateSecurityDatasourceEveryoneValue();

      when(provider.checkPermission(principal, resourceType, resource, ResourceAction.READ)).thenReturn(false);
      when(provider.checkPermission(principal, resourceType, resource, ResourceAction.ADMIN)).thenReturn(false);
      when(provider.getPermission(resourceType, resource)).thenReturn(null);

      if(parentType != null) {
         when(provider.checkPermission(principal, parentType, parentPath, ResourceAction.READ)).thenReturn(true);
      }

      boolean allowed = engine.checkPermission(principal, resourceType, resource, ResourceAction.READ);

      assertTrue(allowed);
   }

   private static Stream<Arguments> datasourceEveryoneCases() {
      return Stream.of(
         // query folder: blank permission alone is enough to allow READ in datasource everyone mode
         Arguments.of(ResourceType.QUERY_FOLDER, "folder::datasource", null, null),
         // data source: blank permission inherits the data-source folder parent decision
         Arguments.of(ResourceType.DATA_SOURCE, "folder/source", ResourceType.DATA_SOURCE_FOLDER, "folder"),
         // data source folder: blank permission inherits the parent folder decision
         Arguments.of(ResourceType.DATA_SOURCE_FOLDER, "folder/sub", ResourceType.DATA_SOURCE_FOLDER, "folder"),
         // data model folder: blank permission inherits the backing data-source decision
         Arguments.of(ResourceType.DATA_MODEL_FOLDER, "datasource/modelFolder", ResourceType.DATA_SOURCE, "datasource"),
         // query: blank permission inherits the backing data-source decision
         Arguments.of(ResourceType.QUERY, "queryName::datasource", ResourceType.DATA_SOURCE, "datasource"),
         // cube: blank permission inherits the backing data-source decision
         Arguments.of(ResourceType.CUBE, "cubeName::datasource", ResourceType.DATA_SOURCE, "datasource")
      );
   }

   // [Scenario: chart type fallback] CHART_TYPE with no direct permission falls back to its folder parent
   // Setup: chart type permission is absent and the chart-type folder parent grants READ
   @Test
   void checkPermission_chartTypeWithoutDirectPermission_inheritsFolderPermission() throws Exception {
      SecurityProvider provider = mock(SecurityProvider.class);
      setProvider(provider);
      SRPrincipal principal = loggedInPrincipal(USER_ID, 9L);
      String resource = "bar/stacked";

      when(provider.checkPermission(principal, ResourceType.CHART_TYPE, resource, ResourceAction.READ))
         .thenReturn(false);
      when(provider.checkPermission(principal, ResourceType.CHART_TYPE, resource, ResourceAction.ADMIN))
         .thenReturn(false);
      when(provider.getPermission(ResourceType.CHART_TYPE, resource)).thenReturn(null);
      when(provider.checkPermission(principal, ResourceType.CHART_TYPE_FOLDER, "bar", ResourceAction.READ))
         .thenReturn(true);

      boolean allowed = engine.checkPermission(principal, ResourceType.CHART_TYPE, resource,
         ResourceAction.READ);

      assertTrue(allowed);
   }

   // [Scenario: schedule task fallback] blank schedule task folder permission falls back to root under everyone mode
   // Setup: direct checks fail, the folder permission is blank, and the root schedule folder is blank for READ
   @Test
   void checkPermission_scheduleTaskFolderWithoutPermission_fallsBackToRootRead() throws Exception {
      SecurityProvider provider = mock(SecurityProvider.class);
      setProvider(provider);
      SRPrincipal principal = loggedInPrincipal(USER_ID, 10L);
      String resource = "/";

      SreeEnv.setProperty("security.scheduletask.everyone", "true");
      SecurityEngine.updateSecuritySchduletaskEveryoneValue();

      when(provider.checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER, resource, ResourceAction.READ))
         .thenReturn(false);
      when(provider.checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER, resource, ResourceAction.ADMIN))
         .thenReturn(false);
      when(provider.getPermission(ResourceType.SCHEDULE_TASK_FOLDER, resource)).thenReturn(null);

      boolean allowed = engine.checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER, resource,
         ResourceAction.READ);

      assertTrue(allowed);
   }

   private SRPrincipal loggedInPrincipal(IdentityID userId, long secureId) {
      SRPrincipal principal = new SRPrincipal(userId, new IdentityID[0], new String[0],
         userId.getOrgID(), secureId);
      principal.setProperty("login.user", "true");
      principal.setIgnoreLogin(false);

      @SuppressWarnings("unchecked")
      Map<ClientInfo, SRPrincipal> users =
         (Map<ClientInfo, SRPrincipal>) ReflectionTestUtils.getField(engine, "users");
      users.put(principal.getUser().getCacheKey(), principal);
      return principal;
   }

   private void setProvider(SecurityProvider provider) {
      ReflectionTestUtils.setField(engine, "provider", provider);
   }

   private void setVpmProvider(SecurityProvider provider) {
      ReflectionTestUtils.setField(engine, "vpm_provider", provider);
   }

   @Autowired
   private SecurityEngine engine;
}
