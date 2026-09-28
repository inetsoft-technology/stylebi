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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XDataSourceWrapper;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.XUtil;
import inetsoft.util.AbstractSecretsManager;
import inetsoft.util.MessageException;
import inetsoft.util.credential.*;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77157: saving a data source must not accept a cloud secret id that the caller does not
 * already manage. An id is accepted only if it is already stored on the data source being saved,
 * a writable data source in the current organization already stores it, or the caller is an
 * administrator who may introduce new secret ids.
 */
@Tag("core")
class DatasourcesServiceSavedSecretTest {
   @BeforeEach
   void setUp() throws Exception {
      sreeEnv = mockStatic(SreeEnv.class);
      licenseManager = mockStatic(LicenseManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orga");
      when(orgManager.getCurrentOrgID(any())).thenReturn("orga");
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      xutil = mockStatic(XUtil.class, CALLS_REAL_METHODS);
      xutil.when(() -> XUtil.isDataSourceNameValid(any(), anyString(), any())).thenReturn("Valid");

      secretsManager = mock(AbstractSecretsManager.class);
      when(secretsManager.getCredential(any())).thenAnswer(inv -> {
         Credential req = inv.getArgument(0);
         CloudClientCredentials stored = new CloudClientCredentials();
         stored.setId(req.getId());
         stored.setClientId("client-of-" + req.getId());
         stored.setClientSecret("secret-of-" + req.getId());
         return stored;
      });
      STORE.set(secretsManager);

      Config config = mock(Config.class);
      when(config.getDataSourceClass(CLOUD)).thenReturn(CloudTestDs.class.getName());
      doReturn(CloudTestDs.class).when(config).getClass(CLOUD, CloudTestDs.class.getName());
      when(config.getDataSourceClass(LOCAL)).thenReturn(LocalTestDs.class.getName());
      doReturn(LocalTestDs.class).when(config).getClass(LOCAL, LocalTestDs.class.getName());
      configStatic = mockStatic(Config.class);
      configStatic.when(Config::getConfig).thenReturn(config);

      repository = mock(XRepository.class);
      when(repository.getDataSourceNames()).thenReturn(new String[0]);
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      registry = mock(DataSourceRegistry.class);
      REGISTRY.set(registry);
      when(registry.getEntries(any(), any(AssetEntry.Type.class))).thenReturn(new AssetEntry[0]);
      when(registry.getDataSourceFullNames()).thenReturn(new String[0]);
      service = new DatasourcesService(repository, securityEngine,
                                       mock(DataSourceStatusService.class), registry, config);
      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("alice", "orga").convertToKey());
   }

   @AfterEach
   void tearDown() {
      STORE.remove();
      REGISTRY.remove();
      xutil.close();
      configStatic.close();
      orgManagerStatic.close();
      sreeEnv.close();
      licenseManager.close();
   }

   @Test
   void createRejectsForeignSecretIdForCreateOnlyUser() {
      grantCreateOnly();

      assertThrows(MessageException.class, () -> service.createNewDataSource(
         definition(CLOUD, "mine", FOREIGN_ID), false, principal));

      verifyNothingSaved();
      verifyNeverResolved(FOREIGN_ID);
   }

   @Test
   void updateRejectsWriteHolderSwappingToForeignSecretId() throws Exception {
      storeOwnSource();

      assertThrows(MessageException.class, () -> service.updateDataSource(
         "mine", definition(CLOUD, "mine", FOREIGN_ID), principal));

      verifyNothingSaved();
      verifyNeverResolved(FOREIGN_ID);
   }

   @Test
   void updateAcceptsUnchangedSecretId() throws Exception {
      storeOwnSource();

      service.updateDataSource("mine", definition(CLOUD, "mine", OWN_ID), principal);

      assertEquals(OWN_ID, savedCredentialId("mine"));
      verify(registry, never()).getDataSourceFullNames();
   }

   @Test
   void createAcceptsSecretIdStoredOnWritableSourceInCurrentOrg() throws Exception {
      grantCreateOnly();
      storeSharedSource(true);

      service.createNewDataSource(definition(CLOUD, "mine", FOREIGN_ID), false, principal);

      assertEquals(FOREIGN_ID, savedCredentialId(null));
   }

   @Test
   void createRejectsSecretIdStoredOnlyOnSourceWithoutWrite() throws Exception {
      grantCreateOnly();
      storeSharedSource(false);

      assertThrows(MessageException.class, () -> service.createNewDataSource(
         definition(CLOUD, "mine", FOREIGN_ID), false, principal));

      verifyNothingSaved();
      verifyNeverResolved(FOREIGN_ID);
   }

   @Test
   void siteAdminMayIntroduceNewSecretId() throws Exception {
      grantCreateOnly();
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      service.createNewDataSource(definition(CLOUD, "mine", FOREIGN_ID), false, principal);

      assertEquals(FOREIGN_ID, savedCredentialId(null));
   }

   @Test
   void orgAdminMayIntroduceNewSecretIdOnlyWithoutMultiTenancy() throws Exception {
      grantCreateOnly();
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         sutil.when(SUtil::isMultiTenant).thenReturn(true);

         assertThrows(MessageException.class, () -> service.createNewDataSource(
            definition(CLOUD, "mine", FOREIGN_ID), false, principal));
         verifyNothingSaved();

         sutil.when(SUtil::isMultiTenant).thenReturn(false);
         service.createNewDataSource(definition(CLOUD, "mine", FOREIGN_ID), false, principal);
         assertEquals(FOREIGN_ID, savedCredentialId(null));
      }
   }

   @Test
   void createRejectsAdditionalConnectionWithForeignSecretIdBeforeAnySave() {
      grantCreateOnly();
      DataSourceDefinition definition = definition(CLOUD, "mine", null);
      definition.setAdditionalConnections(List.of(definition(CLOUD, "child", FOREIGN_ID)));

      assertThrows(MessageException.class,
                   () -> service.createNewDataSource(definition, false, principal));

      verifyNothingSaved();
      verifyNeverResolved(FOREIGN_ID);
   }

   @Test
   void updateRejectsAdditionalConnectionWithForeignSecretIdBeforeAnySave() throws Exception {
      storeOwnSource();
      DataSourceDefinition definition = definition(CLOUD, "mine", OWN_ID);
      definition.setAdditionalConnections(List.of(definition(CLOUD, "child", FOREIGN_ID)));

      assertThrows(MessageException.class,
                   () -> service.updateDataSource("mine", definition, principal));

      verifyNothingSaved();
      verifyNeverResolved(FOREIGN_ID);
   }

   @Test
   void authorizedAdditionalConnectionIsResolvedOnceAndSavedAsChecked() throws Exception {
      grantCreateOnly();
      storeSharedSource(true);
      DataSourceDefinition definition = definition(CLOUD, "mine", null);
      definition.setAdditionalConnections(List.of(definition(CLOUD, "child", FOREIGN_ID)));

      service.createNewDataSource(definition, false, principal);

      // the additional connection is created once, so its secret is only fetched once
      verify(secretsManager, times(1))
         .getCredential(argThat(c -> FOREIGN_ID.equals(c.getId())));
      ArgumentCaptor<XDataSourceWrapper> child = ArgumentCaptor.forClass(XDataSourceWrapper.class);
      verify(registry).setObject(
         argThat(e -> e.getPath().endsWith("/child")), child.capture());
      assertEquals(FOREIGN_ID, ((CloudTestDs) child.getValue().getSource()).getCredentialId());
   }

   @Test
   void localModeIsUnaffected() throws Exception {
      grantCreateOnly();

      service.createNewDataSource(definition(LOCAL, "mine", FOREIGN_ID), false, principal);

      verify(repository).updateDataSource(any(XDataSource.class), isNull(), eq(false));
      verify(secretsManager, never()).getCredential(any());
      verify(securityEngine, never()).checkPermission(any(), eq(ResourceType.DATA_SOURCE),
                                                      anyString(), any(ResourceAction.class));
      verify(registry, never()).getDataSourceFullNames();
   }

   private void grantCreateOnly() {
      try {
         when(securityEngine.checkPermission(any(), eq(ResourceType.CREATE_DATA_SOURCE),
                                             anyString(), eq(ResourceAction.ACCESS)))
            .thenReturn(true);
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   private void storeOwnSource() throws Exception {
      when(repository.getDataSource("mine")).thenReturn(savedSource("mine", OWN_ID));
      when(securityEngine.checkPermission(any(), eq(ResourceType.DATA_SOURCE), eq("mine"),
                                          eq(ResourceAction.WRITE))).thenReturn(true);
      clearInvocations(secretsManager);
   }

   private void storeSharedSource(boolean writable) throws Exception {
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "shared" });
      when(registry.getDataSource("shared")).thenReturn(savedSource("shared", FOREIGN_ID));
      when(securityEngine.checkPermission(any(), eq(ResourceType.DATA_SOURCE), eq("shared"),
                                          eq(ResourceAction.WRITE))).thenReturn(writable);
      clearInvocations(secretsManager);
   }

   private static CloudTestDs savedSource(String name, String secretId) {
      CloudTestDs ds = new CloudTestDs();
      ds.setName(name);
      ds.setUseCredentialId(true);
      ds.setCredentialId(secretId);
      return ds;
   }

   private String savedCredentialId(String oldName) throws Exception {
      ArgumentCaptor<XDataSource> saved = ArgumentCaptor.forClass(XDataSource.class);

      if(oldName == null) {
         verify(repository).updateDataSource(saved.capture(), isNull(), eq(false));
      }
      else {
         verify(repository).updateDataSource(saved.capture(), eq(oldName), eq(false));
      }

      return ((CloudTestDs) saved.getValue()).getCredentialId();
   }

   private void verifyNothingSaved() {
      try {
         verify(repository, never()).updateDataSource(any(), any(), anyBoolean());
         verify(securityEngine, never()).setPermission(any(), anyString(), any());
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   private void verifyNeverResolved(String secretId) {
      verify(secretsManager, never()).getCredential(argThat(c -> secretId.equals(c.getId())));
   }

   private static DataSourceDefinition definition(String type, String name, String secretId) {
      TabularView root = new TabularView();
      root.addTabularView(view("useCredentialId", secretId != null, true));
      root.addTabularView(view("credentialId", secretId, secretId != null));
      root.addTabularView(view("clientId", null, false));
      root.addTabularView(view("clientSecret", null, false));
      DataSourceDefinition def = new DataSourceDefinition();
      def.setType(type);
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

   private static final String CLOUD = "CloudSavedSecretTest";
   private static final String LOCAL = "LocalSavedSecretTest";
   private static final String OWN_ID = "own-secret";
   private static final String FOREIGN_ID = "org-b-secret";
   static final ThreadLocal<AbstractSecretsManager> STORE = new ThreadLocal<>();
   static final ThreadLocal<DataSourceRegistry> REGISTRY = new ThreadLocal<>();

   public static class TestCloudClientCredentials extends CloudClientCredentials {
      @Override
      public AbstractSecretsManager getSecretsManager() {
         return STORE.get();
      }
   }

   @View(vertical = true, value = {
      @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
      @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
      @View1(value = "clientId", visibleMethod = "useCredential"),
      @View1(value = "clientSecret", visibleMethod = "useCredential")
   })
   public abstract static class TestDsBase<T extends TestDsBase<T>> extends TabularDataSource<T> {
      TestDsBase(String type, Class<T> cls) {
         super(type, cls);
      }

      @Override
      protected CredentialType getCredentialType() {
         return CredentialType.CLIENT;
      }

      @Override
      public String[] getDataSourceNames() {
         return new String[0];
      }

      @Override
      protected DataSourceRegistry getRegistry() {
         return REGISTRY.get();
      }

      @Property(label = "Client ID")
      public String getClientId() {
         return ((ClientCredentials) getCredential()).getClientId();
      }

      public void setClientId(String v) {
         ((ClientCredentials) getCredential()).setClientId(v);
      }

      @Property(label = "Client Secret", password = true)
      public String getClientSecret() {
         return ((ClientCredentials) getCredential()).getClientSecret();
      }

      public void setClientSecret(String v) {
         ((ClientCredentials) getCredential()).setClientSecret(v);
      }
   }

   public static class CloudTestDs extends TestDsBase<CloudTestDs> {
      public CloudTestDs() {
         super(CLOUD, CloudTestDs.class);
      }

      @Override
      protected Credential createCredential(boolean forceLocal) {
         return forceLocal ? new LocalClientCredentials() : new TestCloudClientCredentials();
      }

      @Override
      public boolean supportToggleCredential() {
         return true;
      }
   }

   public static class LocalTestDs extends TestDsBase<LocalTestDs> {
      public LocalTestDs() {
         super(LOCAL, LocalTestDs.class);
      }

      @Override
      protected Credential createCredential(boolean forceLocal) {
         return new LocalClientCredentials();
      }

      @Override
      public boolean supportToggleCredential() {
         return false;
      }
   }

   private DatasourcesService service;
   private XRepository repository;
   private SecurityEngine securityEngine;
   private DataSourceRegistry registry;
   private OrganizationManager orgManager;
   private XPrincipal principal;
   private AbstractSecretsManager secretsManager;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<LicenseManager> licenseManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<XUtil> xutil;
   private MockedStatic<Config> configStatic;
}
