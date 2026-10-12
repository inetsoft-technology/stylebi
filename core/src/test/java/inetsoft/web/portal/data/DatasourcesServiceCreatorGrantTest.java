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
package inetsoft.web.portal.data;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.XUtil;
import inetsoft.web.portal.data.DatasourcesServiceSavedSecretTest.LocalTestDs;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77798: the private grant of the creator of a new data source is written after the rest of
 * the creation, so that a failed permission write doesn't skip it. It must still be written when
 * a later step fails once the data source is saved, and a failed grant must not hide the failure
 * of that step.
 */
@Tag("core")
class DatasourcesServiceCreatorGrantTest {
   // the type of DatasourcesServiceSavedSecretTest.LocalTestDs
   private static final String TYPE = "LocalSavedSecretTest";

   @BeforeEach
   void setUp() throws Exception {
      sreeEnv = mockStatic(SreeEnv.class);
      licenseManager = mockStatic(LicenseManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orga");
      when(orgManager.getCurrentOrgID(any())).thenReturn("orga");
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      xutil = mockStatic(XUtil.class, CALLS_REAL_METHODS);
      xutil.when(() -> XUtil.isDataSourceNameValid(any(), anyString(), any())).thenReturn("Valid");

      Config config = mock(Config.class);
      when(config.getDataSourceClass(TYPE)).thenReturn(LocalTestDs.class.getName());
      doReturn(LocalTestDs.class).when(config).getClass(TYPE, LocalTestDs.class.getName());
      configStatic = mockStatic(Config.class);
      configStatic.when(Config::getConfig).thenReturn(config);

      repository = mock(XRepository.class);
      when(repository.getDataSourceNames()).thenReturn(new String[0]);
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      // the creator may only create data sources, so the new one gets a private grant
      when(securityEngine.checkPermission(any(), eq(ResourceType.CREATE_DATA_SOURCE),
                                          anyString(), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      registry = mock(DataSourceRegistry.class);
      DatasourcesServiceSavedSecretTest.REGISTRY.set(registry);
      when(registry.getDataSourceFullNames()).thenReturn(new String[0]);
      service = new DatasourcesService(repository, securityEngine,
                                       mock(DataSourceStatusService.class), registry, config);
      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("alice", "orga").convertToKey());
   }

   @AfterEach
   void tearDown() {
      DatasourcesServiceSavedSecretTest.REGISTRY.remove();
      xutil.close();
      configStatic.close();
      orgManagerStatic.close();
      sreeEnv.close();
      licenseManager.close();
   }

   // the asset entry of the saved data source can't be read
   @Test
   void grantIsWrittenWhenALaterStepFails() throws Exception {
      IllegalStateException failure = new IllegalStateException("simulated");
      when(registry.getEntries(any(), any(AssetEntry.Type.class))).thenThrow(failure);

      assertSame(failure, assertThrows(IllegalStateException.class, () ->
         service.createNewDataSource(definition("mine"), false, principal)));

      verify(repository).updateDataSource(any(XDataSource.class), isNull(), eq(false));
      verify(securityEngine).setPermission(eq(ResourceType.DATA_SOURCE), eq("mine"),
                                           argThat(this::grantsAlice));
   }

   @Test
   void failedGrantDoesNotHideTheFailureOfALaterStep() throws Exception {
      IllegalStateException failure = new IllegalStateException("simulated");
      IllegalStateException grantFailure = new IllegalStateException("simulated grant");
      when(registry.getEntries(any(), any(AssetEntry.Type.class))).thenThrow(failure);
      doThrow(grantFailure).when(securityEngine)
         .setPermission(eq(ResourceType.DATA_SOURCE), eq("mine"), any());

      IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
         service.createNewDataSource(definition("mine"), false, principal));

      assertSame(failure, thrown);
      assertArrayEquals(new Throwable[] { grantFailure }, thrown.getSuppressed());
   }

   // with no other failure, a failed grant is thrown
   @Test
   void failedGrantIsThrown() throws Exception {
      IllegalStateException grantFailure = new IllegalStateException("simulated grant");
      when(registry.getEntries(any(), any(AssetEntry.Type.class))).thenReturn(new AssetEntry[0]);
      doThrow(grantFailure).when(securityEngine)
         .setPermission(eq(ResourceType.DATA_SOURCE), eq("mine"), any());

      assertSame(grantFailure, assertThrows(IllegalStateException.class, () ->
         service.createNewDataSource(definition("mine"), false, principal)));
      verify(repository).updateDataSource(any(XDataSource.class), isNull(), eq(false));
   }

   private boolean grantsAlice(Permission permission) {
      return permission != null && permission.getUserGrants(ResourceAction.READ).stream()
         .anyMatch(identity -> "alice".equals(identity.getName()));
   }

   private static DataSourceDefinition definition(String name) {
      TabularView root = new TabularView();
      root.addTabularView(view("clientId", null, false));
      root.addTabularView(view("clientSecret", null, false));
      DataSourceDefinition def = new DataSourceDefinition();
      def.setType(TYPE);
      def.setName(name);
      def.setParentPath("");
      def.setTabularView(root);
      return def;
   }

   private static TabularView view(String prop, Object value, boolean visible) {
      TabularView v = new TabularView();
      v.setValue(prop);
      v.setVisible(visible);
      TabularEditor e = new TabularEditor();
      e.setValue(value);
      v.setEditor(e);
      return v;
   }

   private DatasourcesService service;
   private XRepository repository;
   private SecurityEngine securityEngine;
   private DataSourceRegistry registry;
   private XPrincipal principal;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<LicenseManager> licenseManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<XUtil> xutil;
   private MockedStatic<Config> configStatic;
}
