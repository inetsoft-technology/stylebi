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
package inetsoft.sree.internal;

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.*;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.util.*;
import inetsoft.util.credential.*;
import inetsoft.util.dep.XAssetConfig;
import inetsoft.util.dep.XDataSourceAsset;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77173: importing a data source must not accept a cloud secret id that the importer does
 * not already manage. The same rule as saving a data source applies: the id is accepted only if
 * the data source being overwritten already stores it, a writable data source in the current
 * organization stores it, or the importer may introduce new secret ids. A rejected entry is not
 * parsed, so its secret is never resolved.
 */
@Tag("core")
class DeployManagerServiceSecretIdImportTest {
   @BeforeEach
   void setUp() throws Exception {
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
      configStatic = mockStatic(Config.class);
      configStatic.when(Config::getConfig).thenReturn(config);

      TransformerManager transformer = mock(TransformerManager.class);
      when(transformer.transform(any())).thenAnswer(inv -> inv.getArgument(0));
      transformerStatic = mockStatic(TransformerManager.class);
      transformerStatic.when(() -> TransformerManager.getManager(anyString()))
         .thenReturn(transformer);

      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orga");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      registry = mock(DataSourceRegistry.class, CALLS_REAL_METHODS);
      doReturn(false).when(registry).containDatasource(anyString());
      doReturn(new String[0]).when(registry).getDataSourceFullNames();
      doReturn(null).when(registry).getDataSource(anyString());
      // no data source folder is in the way of an import
      doReturn(false).when(registry).containObject(any());
      doNothing().when(registry).parseDomain(any());
      doNothing().when(registry).setExistQueryFolders(any());
      doNothing().when(registry).setDataSource(any(XDataSource.class), anyBoolean());
      doNothing().when(registry).updateDataSource(anyString(), any(), anyBoolean());
      registryStatic = mockStatic(DataSourceRegistry.class);
      registryStatic.when(DataSourceRegistry::getRegistry).thenReturn(registry);

      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      // the importer may deploy data sources, which is checked before the secret ids
      when(securityEngine.checkPermission(any(Principal.class), eq(ResourceType.DATA_SOURCE),
                                          anyString(), eq(ResourceAction.ADMIN))).thenReturn(true);

      service = new DeployManagerService(
         securityEngine, mock(DependencyHandler.class), registry,
         mock(DashboardRegistryManager.class), mock(LibManagerProvider.class),
         mock(DashboardManager.class), mock(XRepository.class), mock(FileSystemService.class),
         mock(DataSpace.class), mock(EmbeddedTableStorage.class),
         mock(RepletRegistryManager.class));

      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("alice", "orga").convertToKey());
   }

   @AfterEach
   void tearDown() {
      STORE.remove();
      registryStatic.close();
      orgManagerStatic.close();
      transformerStatic.close();
      configStatic.close();
   }

   @Test
   void rejectsForeignSecretIdWithoutResolvingIt() throws Exception {
      List<String> failed = importEntry(entry(source(FOREIGN_ID)), false);

      assertRejected(failed);
   }

   @Test
   void rejectsAdditionalConnectionWithForeignSecretId() throws Exception {
      CloudTestDs parent = localSource();
      CloudTestDs child = source(FOREIGN_ID);
      child.setName("child");
      String xml = "<datasource name=\"imported\" type=\"" + CLOUD + "\">" + xml(parent) +
         "</datasource><additional name=\"child\" type=\"" + CLOUD +
         "\" parent=\"imported\">" + xml(child) + "</additional>";

      List<String> failed = importEntry(registryXml(xml), false);

      assertRejected(failed);
   }

   @Test
   void rejectsSecretIdStoredOnlyOnSourceWithoutWrite() throws Exception {
      storeSharedSource(false);

      List<String> failed = importEntry(entry(source(FOREIGN_ID)), false);

      assertRejected(failed);
   }

   @Test
   void acceptsSecretIdStoredOnWritableSourceInCurrentOrg() throws Exception {
      storeSharedSource(true);

      List<String> failed = importEntry(entry(source(FOREIGN_ID)), false);

      assertTrue(failed.isEmpty(), failed.toString());
      verify(registry).setDataSource(any(XDataSource.class), eq(true));
   }

   @Test
   void siteAdminMayImportNewSecretId() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      List<String> failed = importEntry(entry(source(FOREIGN_ID)), false);

      assertTrue(failed.isEmpty(), failed.toString());
      verify(registry).setDataSource(any(XDataSource.class), eq(true));
   }

   @Test
   void reimportOfExistingSourceWithUnchangedSecretIdIsAccepted() throws Exception {
      CloudTestDs stored = source(OWN_ID);
      doReturn(true).when(registry).containDatasource("imported");
      doReturn(stored).when(registry).getDataSource("imported");
      clearInvocations(secretsManager);

      List<String> failed = importEntry(entry(source(OWN_ID)), true);

      assertTrue(failed.isEmpty(), failed.toString());
      verify(registry).updateDataSource(eq("imported"), any(), eq(true));
      verifyNoOrgScan();
   }

   @Test
   void exportedSourceWithLocalCredentialIsUnaffected() throws Exception {
      // an exported JAR carries the secret values in a local credential, not a secret id
      String entry = entry(localSource());
      assertFalse(entry.contains("cloud=\"true\""), entry);

      List<String> failed = importEntry(entry, false);

      assertTrue(failed.isEmpty(), failed.toString());
      verify(registry).setDataSource(any(XDataSource.class), eq(true));
      verify(secretsManager, never()).getCredential(any());
      verifyNoOrgScan();
   }

   private void verifyNoOrgScan() throws Exception {
      verify(securityEngine, never()).checkPermission(
         any(Principal.class), any(ResourceType.class), anyString(), eq(ResourceAction.WRITE));
   }

   private void assertRejected(List<String> failed) throws Exception {
      String expected = Catalog.getCatalog().getString(
         "em.import.file.failed.secretIdNotAllowed", XDataSourceAsset.XDATASOURCE + " imported");
      verify(secretsManager, never()).getCredential(any());
      verify(registry, never()).setDataSource(any(XDataSource.class), anyBoolean());
      verify(registry, never()).updateDataSource(anyString(), any(), anyBoolean());
      assertEquals(List.of(expected), failed);
      assertTrue(expected.contains("Secret ID"), expected);
   }

   private void storeSharedSource(boolean writable) throws Exception {
      CloudTestDs shared = source(FOREIGN_ID);
      shared.setName("shared");
      doReturn(new String[] { "shared" }).when(registry).getDataSourceFullNames();
      doReturn(shared).when(registry).getDataSource("shared");
      when(securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE, "shared",
                                          ResourceAction.WRITE)).thenReturn(writable);
      clearInvocations(secretsManager);
   }

   /**
    * Imports one XDATASOURCE entry through the private per-asset import step.
    */
   private List<String> importEntry(String content, boolean overwriting) throws Exception {
      File file = tempDir.resolve("f" + (++fileCount)).toFile();
      Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
      Map<String, String> names = new HashMap<>();
      names.put(file.getName(), XDataSourceAsset.XDATASOURCE + "_" +
         XDataSourceAsset.class.getName() + "^imported");
      DeploymentInfo info = mock(DeploymentInfo.class);
      when(info.getNames()).thenReturn(names);
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(overwriting);
      List<String> failed = new ArrayList<>();

      Method method = Arrays.stream(DeployManagerService.class.getDeclaredMethods())
         .filter(m -> m.getName().equals("importAsset") && m.getParameterCount() == 14)
         .findFirst().orElseThrow();
      method.setAccessible(true);
      method.invoke(service, file, new XDataSourceAsset("imported"), new ArrayList<>(), failed,
                    null, new ArrayList<>(), new ArrayList<>(), overwriting, null, info, false,
                    config, null, principal);
      return failed;
   }

   private static CloudTestDs source(String secretId) {
      CloudTestDs ds = new CloudTestDs();
      ds.setName("imported");
      ds.setUseCredentialId(true);
      ((CloudCredential) ds.getCredential()).setId(secretId);
      return ds;
   }

   private static CloudTestDs localSource() {
      CloudTestDs ds = new CloudTestDs();
      ds.setName("imported");
      LocalClientCredentials local = new LocalClientCredentials();
      // a secret would be encrypted with the master key, which needs a running server
      local.setClientId("exported-client");
      ds.setCredential(local);
      return ds;
   }

   private static String entry(CloudTestDs ds) {
      return registryXml("<datasource name=\"imported\" type=\"" + CLOUD + "\">" + xml(ds) +
                            "</datasource>");
   }

   private static String registryXml(String content) {
      return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><registry><Version>" +
         FileVersions.DATASOURCE + "</Version>" + content + "</registry>";
   }

   private static String xml(CloudTestDs ds) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      ds.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   static final String CLOUD = "CloudImportTest";
   static final String FOREIGN_ID = "org-b-secret";
   static final String OWN_ID = "own-secret";
   static final ThreadLocal<AbstractSecretsManager> STORE = new ThreadLocal<>();

   public static class StubCloudClientCredentials extends CloudClientCredentials {
      @Override
      public AbstractSecretsManager getSecretsManager() {
         return STORE.get();
      }
   }

   @View(vertical = true, value = {
      @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
      @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
      @View1(value = "clientSecret", visibleMethod = "useCredential")
   })
   public static class CloudTestDs extends TabularDataSource<CloudTestDs> {
      public CloudTestDs() {
         super(CLOUD, CloudTestDs.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return CredentialType.CLIENT;
      }

      @Override
      protected Credential createCredential(boolean forceLocal) {
         return forceLocal ? new LocalClientCredentials() : new StubCloudClientCredentials();
      }

      @Override
      public boolean supportToggleCredential() {
         return true;
      }

      @Override
      public String[] getDataSourceNames() {
         return new String[0];
      }

      @Property(label = "Client Secret", password = true)
      public String getClientSecret() {
         return ((ClientCredentials) getCredential()).getClientSecret();
      }

      public void setClientSecret(String v) {
         ((ClientCredentials) getCredential()).setClientSecret(v);
      }
   }

   @TempDir
   Path tempDir;
   private int fileCount;
   private AbstractSecretsManager secretsManager;
   private DataSourceRegistry registry;
   private SecurityEngine securityEngine;
   private OrganizationManager orgManager;
   private DeployManagerService service;
   private XPrincipal principal;
   private MockedStatic<DataSourceRegistry> registryStatic;
   private MockedStatic<TransformerManager> transformerStatic;
   private MockedStatic<Config> configStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
