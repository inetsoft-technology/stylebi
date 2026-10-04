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

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.util.*;
import inetsoft.util.credential.*;
import inetsoft.util.dep.XDataSourceAsset;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77628: a data source exported from a server with another master password has secrets
 * that cannot be decrypted on import. The import must tell the user with a warning, not a
 * failure, and keep importing the data source.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DeployManagerServiceUndecryptableSecretImportTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DeployManagerServiceUndecryptableSecretImportTest {
   @BeforeEach
   void setUp() throws Exception {
      Config config = mock(Config.class);
      when(config.getDataSourceClass(TYPE)).thenReturn(ClientTestDs.class.getName());
      doReturn(ClientTestDs.class).when(config).getClass(TYPE, ClientTestDs.class.getName());
      configStatic = mockStatic(Config.class);
      configStatic.when(Config::getConfig).thenReturn(config);

      TransformerManager transformer = mock(TransformerManager.class);
      when(transformer.transform(any())).thenAnswer(inv -> inv.getArgument(0));
      transformerStatic = mockStatic(TransformerManager.class);
      transformerStatic.when(() -> TransformerManager.getManager(anyString()))
         .thenReturn(transformer);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      when(orgManager.getCurrentOrgID(any())).thenReturn("host-org");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      registry = mock(DataSourceRegistry.class, CALLS_REAL_METHODS);
      doReturn(false).when(registry).containDatasource(anyString());
      doReturn(new String[0]).when(registry).getDataSourceFullNames();
      doReturn(null).when(registry).getDataSource(anyString());
      doReturn(false).when(registry).containObject(any());
      doNothing().when(registry).parseDomain(any());
      doNothing().when(registry).setExistQueryFolders(any());
      doNothing().when(registry).setDataSource(any(XDataSource.class), anyBoolean());
      registryStatic = mockStatic(DataSourceRegistry.class);
      registryStatic.when(DataSourceRegistry::getRegistry).thenReturn(registry);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.checkPermission(any(Principal.class), eq(ResourceType.DATA_SOURCE),
                                          anyString(), eq(ResourceAction.ADMIN))).thenReturn(true);

      LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
      when(libManagerProvider.getManager(any(Principal.class))).thenReturn(mock(LibManager.class));

      service = new DeployManagerService(
         securityEngine, mock(DependencyHandler.class), registry,
         mock(DashboardRegistryManager.class), libManagerProvider,
         mock(DashboardManager.class), mock(XRepository.class), mock(FileSystemService.class),
         mock(DataSpace.class), mock(EmbeddedTableStorage.class),
         mock(RepletRegistryManager.class));

      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("admin", "host-org").convertToKey());
   }

   @AfterEach
   void tearDown() {
      registryStatic.close();
      orgManagerStatic.close();
      transformerStatic.close();
      configStatic.close();
   }

   @Test
   void foreignSecretIsImportedWithWarningNotFailure() throws Exception {
      String foreign = ForeignMasterSecret.encrypt("another-master-pw", "s3cret");
      DeploymentInfo info = info(foreign);
      List<String> failed = new ArrayList<>();

      importAssets(info, failed);

      // the data source is still imported, with the undecryptable value kept as is
      ArgumentCaptor<XDataSource> imported = ArgumentCaptor.forClass(XDataSource.class);
      verify(registry).setDataSource(imported.capture(), anyBoolean());
      assertEquals(foreign, ((ClientTestDs) imported.getValue()).getClientSecret());

      assertTrue(failed.isEmpty(), failed.toString());
      String expected = Catalog.getCatalog().getString(
         "em.import.undecryptableSecrets.assets", "imported");
      assertEquals(List.of(expected), info.getImportWarnings());
      assertTrue(expected.contains("different master password"), expected);
      assertTrue(expected.endsWith(": imported"), expected);
      assertNull(PasswordEncryption.getMasterDecryptFailures());
   }

   @Test
   void decryptableSecretHasNoWarning() throws Exception {
      String own = Tool.encryptPassword("s3cret");
      DeploymentInfo info = info(own);
      List<String> failed = new ArrayList<>();

      importAssets(info, failed);

      ArgumentCaptor<XDataSource> imported = ArgumentCaptor.forClass(XDataSource.class);
      verify(registry).setDataSource(imported.capture(), anyBoolean());
      assertEquals("s3cret", ((ClientTestDs) imported.getValue()).getClientSecret());
      assertTrue(failed.isEmpty(), failed.toString());
      assertTrue(info.getImportWarnings().isEmpty(), info.getImportWarnings().toString());
      assertNull(PasswordEncryption.getMasterDecryptFailures());
   }

   private void importAssets(DeploymentInfo info, List<String> failed) throws Exception {
      service.importAssets(false, new ArrayList<>(), info, false, principal, new ArrayList<>(),
                           null, failed, null, new ArrayList<>());
   }

   private DeploymentInfo info(String encryptedSecret) throws Exception {
      File unzip = Files.createDirectories(tempDir.resolve("unzip" + (++count))).toFile();
      File file = new File(unzip, "f1");
      Files.writeString(file.toPath(), entry(encryptedSecret), StandardCharsets.UTF_8);
      Map<String, String> names = new HashMap<>();
      names.put(file.getName(), XDataSourceAsset.XDATASOURCE + "_" +
         XDataSourceAsset.class.getName() + "^imported");

      DeploymentInfo info = mock(DeploymentInfo.class);
      List<String> warnings = new ArrayList<>();
      when(info.getFiles()).thenReturn(new File[] { file });
      when(info.getNames()).thenReturn(names);
      when(info.getUnzipFolderPath()).thenReturn(unzip.getAbsolutePath());
      when(info.getDependentAssets()).thenReturn(new ArrayList<>());
      when(info.getSelectedEntries()).thenReturn(new ArrayList<>());
      when(info.getIgnoredQueries()).thenReturn(new HashSet<>());
      when(info.getImportWarnings()).thenReturn(warnings);
      return info;
   }

   /**
    * Writes the data source the way an export does, with the client secret set to the given
    * encrypted value.
    */
   private static String entry(String encryptedSecret) {
      ClientTestDs ds = new ClientTestDs();
      ds.setName("imported");
      LocalClientCredentials credential = new LocalClientCredentials();
      credential.setClientId("exported-client");
      ds.setCredential(credential);

      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      ds.writeXML(writer);
      writer.flush();
      String clientId = "<clientId><![CDATA[exported-client]]></clientId>";
      String xml = out.toString();
      assertTrue(xml.contains(clientId), xml);
      xml = xml.replace(clientId, clientId + "<clientSecret><![CDATA[" + encryptedSecret +
         "]]></clientSecret>");

      return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><registry><Version>" +
         FileVersions.DATASOURCE + "</Version><datasource name=\"imported\" type=\"" + TYPE +
         "\">" + xml + "</datasource></registry>";
   }

   static final String TYPE = "UndecryptableImportTest";

   @Configuration
   static class Beans {
      // the import gets the asset repository after the assets, no viewsheet is imported here
      @Bean
      AnalyticRepository analyticRepository() {
         return mock(AnalyticRepository.class);
      }
   }

   @View(vertical = true, value = {
      @View1("clientSecret")
   })
   public static class ClientTestDs extends TabularDataSource<ClientTestDs> {
      public ClientTestDs() {
         super(TYPE, ClientTestDs.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return CredentialType.CLIENT;
      }

      @Override
      protected Credential createCredential(boolean forceLocal) {
         return new LocalClientCredentials();
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
   private int count;
   private DataSourceRegistry registry;
   private DeployManagerService service;
   private XPrincipal principal;
   private MockedStatic<DataSourceRegistry> registryStatic;
   private MockedStatic<TransformerManager> transformerStatic;
   private MockedStatic<Config> configStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
