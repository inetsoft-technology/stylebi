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
package inetsoft.web.admin.content.repository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.XSessionManager;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularView;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.XUtil;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.credential.*;
import inetsoft.util.dep.XAssetConfig;
import inetsoft.util.dep.XDataSourceAsset;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.content.repository.model.NewRepositoryFolderRequest;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77691: a data source and a data source folder must not be created at the same path. The
 * data sources in the folder would be stored at the paths of additional connections of the data
 * source, and a save of the data source removed them. Every create path refuses a path in use,
 * also at the root, and a save of a data source that already shares its path with a folder keeps
 * the data sources of the folder. The registry, the repository and the services are the real
 * ones, with a real tabular data source class.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourcePathClashTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourcePathClashTest {
   private static final String URL = "jdbc:derby:memory:bug77691;create=true";
   private static final String TAB = "Bug77691Tab";
   // a type that isn't registered, a data source of this type can't be read
   private static final String GONE = "Bug77691Gone";

   public static class Tab77691 extends TabularDataSource<Tab77691> {
      public Tab77691() {
         this(TAB);
      }

      Tab77691(String type) {
         super(type, Tab77691.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return CredentialType.CLIENT;
      }

      @Override
      protected Credential createCredential(boolean forceLocal) {
         return new LocalClientCredentials();
      }
   }

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private RepositoryObjectService objectService;
   private DataSourceBrowserService browserService;
   private DatabaseDatasourcesService databaseService;
   private DatasourcesService datasourcesService;
   private XmlaDatasourceService xmlaService;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      // the permission checks of the services throw if denied, a mock lets everything pass
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      objectService = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class),
         provider, mock(ResourcePermissionService.class), repository,
         mock(RepositoryDashboardService.class), mock(DataModelFolderManagerService.class),
         registry, mock(LibManagerProvider.class), mock(RecycleBin.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class), repletRegistries,
         mock(DashboardRegistryManager.class));
      browserService = new DataSourceBrowserService(
         security, objectService, repository, mock(DataSourceService.class), registry,
         mock(Config.class), mock(RenameTransformHandler.class));
      databaseService = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         security, mock(DatabaseSettingsService.class), repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
      datasourcesService = new DatasourcesService(
         repository, security, mock(DataSourceStatusService.class), registry, Config.getConfig());
      xmlaService = new XmlaDatasourceService(
         repository, security, mock(DataSourceStatusService.class), registry, Config.getConfig(),
         mock(DependencyHandler.class), mock(XSessionManager.class));
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   // 1. the portal tabular create refuses a path in use with every root parent value the UI
   // sends, in a folder, under a data source, and for an additional connection
   @Test
   void tabularCreateRefusesAPathInUse() throws Exception {
      folder("tcX");
      folder("tcG");
      folder("tcG/tcY");
      // a data source that shares its path with a folder (older data)
      folder("tcP");
      tab("tcP");
      // a folder at the path of an additional connection of a new data source
      folder("tcA/tcAdd");

      assertNotEquals("Valid", XUtil.isDataSourceNameValid(repository, "tcX", "/"));
      assertNotEquals("Valid", XUtil.isDataSourceNameValid(repository, "tcX", ""));
      assertNotEquals("Valid", XUtil.isDataSourceNameValid(repository, "tcX", null));
      assertEquals("Valid", XUtil.isDataSourceNameValid(repository, "tcFree", ""));

      for(String parent : new String[] { "/", "", null }) {
         assertThrows(MessageException.class, () -> datasourcesService.createNewDataSource(
            definition("tcX", parent), false, principal), "parent " + parent);
      }

      assertThrows(MessageException.class, () -> datasourcesService.createNewDataSource(
         definition("tcY", "tcG"), false, principal));
      MessageException under = assertThrows(
         MessageException.class, () -> datasourcesService.createNewDataSource(
            definition("tcNew", "tcP"), false, principal));
      assertTrue(under.getMessage().contains("tcP"), under.getMessage());
      DataSourceDefinition withAdditional = definition("tcA", "/");
      withAdditional.setAdditionalConnections(new ArrayList<>(List.of(definition("tcAdd", null))));
      assertThrows(MessageException.class, () -> datasourcesService.createNewDataSource(
         withAdditional, false, principal));

      registry.clearCache();
      assertNull(registry.getDataSource("tcX"));
      assertNull(registry.getDataSource("tcG/tcY"));
      assertNull(registry.getDataSource("tcP/tcNew"));
      assertNull(registry.getDataSource("tcA"));
      assertNotNull(registry.getDataSourceFolder("tcX"));

      // a free path is still created
      datasourcesService.createNewDataSource(definition("tcFree", ""), false, principal);
      assertNotNull(registry.getDataSource("tcFree"));
   }

   // the portal tabular create at the root, with the parent values the tree ("/") and the
   // browser ("") send, next to a folder of the same name is refused, and the data sources of
   // the folder (also in a subfolder) are kept
   @Test
   void tabularRootCreateKeepsTheDataSourcesOfTheFolder() throws Exception {
      folder("rcF");
      tab("rcF/rcKid");
      folder("rcF/rcSub");
      tab("rcF/rcSub/rcDeep");

      for(String parent : new String[] { "/", "" }) {
         assertThrows(MessageException.class, () -> datasourcesService.createNewDataSource(
            definition("rcF", parent), false, principal), "parent " + parent);
      }

      registry.clearCache();
      assertFalse(registry.containObject(dsEntry("rcF")));
      assertNotNull(registry.getDataSourceFolder("rcF"));
      assertNotNull(registry.getDataSource("rcF/rcKid"));
      assertNotNull(registry.getDataSourceFolder("rcF/rcSub"));
      assertNotNull(registry.getDataSource("rcF/rcSub/rcDeep"));
      assertFalse(registry.getDataSourcePathClashes().contains("rcF"));
   }

   // the portal XMLA (cube) create refuses a path in use at the root ("/" and ""), in a folder
   // and under a data source, and still creates a free path
   @Test
   void xmlaCreateRefusesAPathInUse() throws Exception {
      folder("xmX");
      folder("xmG");
      folder("xmG/xmY");
      folder("xmP");
      jdbc("xmP");

      for(String parent : new String[] { "/", "" }) {
         assertThrows(MessageException.class, () -> xmlaService.createNewDataSource(
            xmla("xmX", parent), true, principal), "parent " + parent);
      }

      assertThrows(MessageException.class, () -> xmlaService.createNewDataSource(
         xmla("xmY", "xmG"), true, principal));
      assertThrows(MessageException.class, () -> xmlaService.createNewDataSource(
         xmla("xmNew", "xmP"), true, principal));

      registry.clearCache();
      assertFalse(registry.containObject(dsEntry("xmX")));
      assertFalse(registry.containObject(dsEntry("xmG/xmY")));
      assertFalse(registry.containObject(dsEntry("xmP/xmNew")));

      xmlaService.createNewDataSource(xmla("xmFree", "/"), true, principal);
      registry.clearCache();
      assertTrue(registry.containObject(dsEntry("xmFree")));
   }

   // 2. the editor rename of a root data source onto the name of a root folder is refused, the
   // data sources of the folder are kept
   @Test
   void tabularRenameOntoARootFolderIsRefused() throws Exception {
      tab("rnR");
      folder("rnX");
      tab("rnX/rnKid");

      assertThrows(MessageException.class, () -> datasourcesService.updateDataSource(
         "rnR", definition("rnX", ""), principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("rnR"));
      assertNull(registry.getDataSource("rnX"));
      assertNotNull(registry.getDataSource("rnX/rnKid"));
   }

   // 3. a plain save that keeps the name and the additional connections is not refused, at the
   // root and in a folder
   @Test
   void plainTabularSaveIsAccepted() throws Exception {
      tabParent("psP", "psAdd");
      folder("psF");
      tabParent("psF/psQ", "psAdd2");

      DataSourceDefinition root = definition("psP", "");
      root.setDescription("edited");
      root.setAdditionalConnections(new ArrayList<>(List.of(definition("psAdd", null))));
      datasourcesService.updateDataSource("psP", root, principal);
      DataSourceDefinition nested = definition("psQ", "psF");
      nested.setDescription("edited");
      nested.setAdditionalConnections(new ArrayList<>(List.of(definition("psAdd2", null))));
      datasourcesService.updateDataSource("psQ", nested, principal);

      registry.clearCache();
      assertEquals("edited", registry.getDataSource("psP").getDescription());
      assertEquals("edited", registry.getDataSource("psF/psQ").getDescription());
      assertNames("psP", "psAdd");
      assertNames("psF/psQ", "psAdd2");
   }

   // 4. JDBC: a plain save of a data source with additional connections is accepted, a rename
   // or a create onto a folder is refused, and so is an additional connection at a folder's path
   @Test
   void jdbcSaveAndCreateChecks() throws Exception {
      addParent("jpP", "jpAdd");
      folder("jpF");
      addParent("jpF/jpQ", "jpAdd2");
      jdbc("jrA");
      folder("jrB");
      folder("jcX");
      folder("jaF");
      jdbc("jaF/jaP");
      // folders at the paths of additional connections, without a folder at the parent path
      folder("jaF/jaP/jaAdd");
      folder("jaF/jaNew/jaAdd2");
      // a folder that is also a data source (older data)
      folder("jcuP");
      jdbc("jcuP");
      folder("jcuP/jcuSub");

      assertNull(saveEdit("jpP", "jpP", "jpAdd"));
      assertNull(saveEdit("jpF/jpQ", "jpQ", "jpAdd2"));
      assertNames("jpP", "jpAdd");
      assertNames("jpF/jpQ", "jpAdd2");

      assertEquals("Duplicate Folder", saveEdit("jrA", "jrB").getStatus());
      assertEquals("Duplicate Folder", saveCreate("", "jcX").getStatus());
      assertEquals("Duplicate Folder", saveEdit("jaF/jaP", "jaP", "jaAdd").getStatus());
      assertEquals("Duplicate Folder", saveCreate("jaF", "jaNew", "jaAdd2").getStatus());
      // a create in the clashed folder resolves to the data source, which isn't renamed
      assertEquals("Duplicate Folder", saveCreate("jcuP", "jcuNew").getStatus());
      assertEquals("Invalid Folder", saveCreate("jcuP/jcuSub", "jcuNew2").getStatus());

      registry.clearCache();
      assertNotNull(registry.getDataSource("jrA"));
      assertNull(registry.getDataSource("jrB"));
      assertNull(registry.getDataSource("jcX"));
      assertFalse(registry.containObject(dsEntry("jaF/jaP/jaAdd")));
      assertNull(registry.getDataSource("jaF/jaNew"));
      assertNotNull(registry.getDataSource("jcuP"));
      assertNull(registry.getDataSource("jcuNew"));
      assertFalse(registry.containObject(dsEntry("jcuP/jcuNew")));
      assertFalse(registry.containObject(dsEntry("jcuP/jcuSub/jcuNew2")));
   }

   // 4b. a JDBC "create" into a clashed folder P is resolved by the controller to an edit of data
   // source P. It's refused, and P keeps its name, its additional connections and the folder
   @Test
   void jdbcCreateInAClashedFolderDoesNotRenameTheDataSource() throws Exception {
      folder("jkP");
      addParent("jkP", "jkAdd");
      jdbc("jkP/jkKid");

      DatabaseDefinition definition = edit(source("jkNew"));
      String action = databaseService.getActionName("jkP", definition.getName());
      assertEquals(ActionRecord.ACTION_NAME_EDIT, action);
      ConnectionStatus status = databaseService.saveDatabase("jkP", DataSourceSettingsModel
         .builder().uploadEnabled(false).dataSource(definition)
         .additionalDataSources(new DatabaseDefinition[0]).build(), action, principal);

      assertNotNull(status);
      assertEquals("Duplicate Folder", status.getStatus());
      registry.clearCache();
      assertNotNull(registry.getDataSource("jkP"));
      assertNull(registry.getDataSource("jkNew"));
      assertFalse(registry.containObject(dsEntry("jkNew")));
      assertNames("jkP", "jkAdd");
      assertNotNull(registry.getDataSource("jkP/jkKid"));
      assertTrue(registry.containObject(folderEntry("jkP")));
   }

   // 4c. normal data: a JDBC create at the root and in a folder, and an edit, a rename and a save
   // with additional connections, as the controller sends them, all succeed
   @Test
   void normalJdbcCreateAndEditSaveSucceed() throws Exception {
      folder("jnF");

      assertNull(controllerSave("", "jnA"));
      assertNull(controllerSave("/", "jnRoot"));
      assertNull(controllerSave("jnF", "jnB", "jnBAdd"));
      registry.clearCache();
      assertNotNull(registry.getDataSource("jnA"));
      assertNotNull(registry.getDataSource("jnRoot"));
      assertNames("jnF/jnB", "jnBAdd");

      // edit with an unchanged name, add an additional connection, then rename
      assertEquals(ActionRecord.ACTION_NAME_EDIT, databaseService.getActionName("jnF/jnB", "jnB"));
      assertNull(saveEdit("jnF/jnB", "jnB", "jnBAdd", "jnBAdd2"));
      assertNames("jnF/jnB", "jnBAdd", "jnBAdd2");
      assertNull(saveEdit("jnF/jnB", "jnC", "jnBAdd", "jnBAdd2"));
      registry.clearCache();
      assertNull(registry.getDataSource("jnF/jnB"));
      assertNames("jnF/jnC", "jnBAdd", "jnBAdd2");
      assertNull(saveEdit("jnA", "jnA2"));
      registry.clearCache();
      assertNotNull(registry.getDataSource("jnA2"));
      assertNull(registry.getDataSource("jnA"));
      assertTrue(registry.getDataSourcePathClashes().stream().noneMatch(p -> p.startsWith("jn")));
   }

   // 4d. the clash WARN is logged once per organization and suggests no rename or delete
   @Test
   void clashWarningIsLoggedOncePerOrganization() throws Exception {
      folder("wnP");
      jdbc("wnP");
      Field field = DataSourceRegistry.class.getDeclaredField("clashesReported");
      field.setAccessible(true);
      ((Set<?>) field.get(registry)).clear();
      Logger logger = (Logger) LoggerFactory.getLogger(DataSourceRegistry.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      Principal old = ThreadContext.getContextPrincipal();

      try {
         registry.init();
         registry.init();
         registry.init();

         // another organization is reported on its own. Its storage is created first, then the
         // set of reported organizations is cleared again, as after a restart
         String org = "o77691";
         ThreadContext.setContextPrincipal(new SRPrincipal(
            new IdentityID("admin", org), new IdentityID[0], new String[0], org,
            Tool.getSecureRandom().nextLong()));
         registry.init();
         folder("wnQ");
         jdbc("wnQ");
         assertEquals(List.of("wnQ"), registry.getDataSourcePathClashes());
         ((Set<?>) field.get(registry)).remove(org);
         registry.init();
         registry.init();
      }
      finally {
         ThreadContext.setContextPrincipal(old);
         logger.detachAppender(appender);
      }

      List<String> warnings = appender.list.stream()
         .filter(e -> e.getLevel() == Level.WARN)
         .map(ILoggingEvent::getFormattedMessage)
         .filter(m -> m.contains("share these paths"))
         .toList();
      assertEquals(2, warnings.size(), warnings.toString());
      String host = warnings.get(0);
      assertTrue(host.contains("wnP"), host);
      assertTrue(warnings.get(1).contains("o77691") && warnings.get(1).contains("wnQ"),
                 warnings.get(1));

      for(String warning : warnings) {
         assertFalse(warning.contains("Rename the folder"), warning);
         assertFalse(warning.toLowerCase().matches(".*\\. (rename|delete|move|remove) .*"),
                     warning);
         // Bug #77725, the operations that would act on the other side are refused, and the way
         // out is given
         assertTrue(warning.contains("is refused while the folder holds data sources"), warning);
         assertTrue(warning.contains("separated by moving the folder's data sources and " +
                                        "subfolders out of it and then renaming the data " +
                                        "source"), warning);
      }
   }

   // 5. the portal and EM "New Folder" refuse a data source path and a path under a data source.
   // The EM auto name skips a data source's name, and a named folder isn't compared with a root
   // folder of the same name any more
   @Test
   void folderCreateChecks() throws Exception {
      jdbc("pfP");
      jdbc("pfQ");
      folder("pfF");

      assertThrows(MessageException.class,
                   () -> browserService.addDatasourceFolder("pfP", null, principal, false));
      assertThrows(MessageException.class,
                   () -> browserService.addDatasourceFolder("pfQ/pfSub", null, principal, false));
      assertThrows(MessageException.class,
                   () -> browserService.addDatasourceFolder("pfF", null, principal, false));
      browserService.addDatasourceFolder("pfF/pfNew", null, principal, false);

      folder("emG");
      jdbc("emG/Folder1");
      objectService.addFolder(emFolder("emG", null), false, principal);

      folder("emDup");
      objectService.addFolder(emFolder("emG", "emDup"), false, principal);

      folder("emP");
      jdbc("emP");
      assertThrows(MessageException.class,
                   () -> objectService.addFolder(emFolder("emP", null), false, principal));
      assertThrows(MessageException.class,
                   () -> objectService.addFolder(emFolder("emP", "emSub"), false, principal));
      assertThrows(RuntimeException.class,
                   () -> objectService.addFolder(emFolder("emG", "Folder1"), false, principal));

      registry.clearCache();
      assertFalse(registry.containObject(folderEntry("pfP")));
      assertFalse(registry.containObject(folderEntry("pfQ/pfSub")));
      assertNotNull(registry.getDataSourceFolder("pfF/pfNew"));
      assertFalse(registry.containObject(folderEntry("emG/Folder1")));
      assertNotNull(registry.getDataSourceFolder("emG/Folder2"));
      assertNotNull(registry.getDataSourceFolder("emG/emDup"));
      assertFalse(registry.containObject(folderEntry("emP/Folder1")));
      assertFalse(registry.containObject(folderEntry("emP/emSub")));
   }

   // 6. an import of a data source at a folder's path writes nothing, not even its additional
   // connections or data model. Imports to a free path or over an existing data source work
   @Test
   void importAtAFolderPathIsSkipped() throws Exception {
      folder("imP");
      jdbc("imP/imKid");

      assertThrows(MessageException.class, () -> importDataSource("imP", "imAdd", false));

      registry.clearCache();
      assertFalse(registry.containObject(dsEntry("imP")));
      assertFalse(registry.containObject(dsEntry("imP/imAdd")));
      assertFalse(registry.containObject(new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_MODEL, "imP", null)));
      assertNotNull(registry.getDataSource("imP/imKid"));
      assertNotNull(registry.getDataSourceFolder("imP"));

      importDataSource("imQ", "imQAdd", false);
      registry.clearCache();
      assertNotNull(registry.getDataSource("imQ"));
      assertNames("imQ", "imQAdd");

      importDataSource("imQ", "imQAdd", true);
      registry.clearCache();
      assertNotNull(registry.getDataSource("imQ"));
      assertNames("imQ", "imQAdd");
   }

   // 7. a data source that already shares its path with a folder: a plain save keeps the data
   // sources of the folder, also one of another class and one that can't be read, and the clash
   // is reported
   @Test
   void saveOfAClashedDataSourceKeepsTheFolder() throws Exception {
      folder("ecP");
      tab("ecP/ecKid");
      folder("ecP/ecSub");
      tab("ecP/ecSub/ecDeep");
      jdbc("ecP/ecJdbc");
      // stored with a bare name, as an additional connection is, but of a type that isn't
      // registered
      Tab77691 gone = new Tab77691(GONE);
      gone.setName("ecGone");
      registry.setObject(dsEntry("ecP/ecGone"), new XDataSourceWrapper(gone));
      tabParent("ecP", "ecAdd");
      registry.clearCache();

      assertNames("ecP", "ecAdd");
      assertTrue(registry.getDataSourcePathClashes().contains("ecP"));

      DataSourceDefinition definition = definition("ecP", "");
      definition.setDescription("edited");
      definition.setAdditionalConnections(new ArrayList<>(List.of(definition("ecAdd", null))));
      datasourcesService.updateDataSource("ecP", definition, principal);

      registry.clearCache();
      assertEquals("edited", registry.getDataSource("ecP").getDescription());
      assertNotNull(registry.getDataSource("ecP/ecKid"));
      assertNotNull(registry.getDataSource("ecP/ecSub/ecDeep"));
      assertNotNull(registry.getDataSource("ecP/ecJdbc"));
      assertTrue(registry.containObject(dsEntry("ecP/ecGone")));
      assertNotNull(registry.getDataSourceFolder("ecP/ecSub"));
      assertNames("ecP", "ecAdd");
   }

   // 8. normal data: the names are exactly the additional connections, for JDBC and tabular
   @Test
   void namesOfNormalData() throws Exception {
      addParent("ndP", "ndA", "ndB");
      tabParent("ndT", "ndTA");
      folder("ndF");
      addParent("ndF/ndQ", "ndC");

      assertNames("ndP", "ndA", "ndB");
      assertNames("ndT", "ndTA");
      assertNames("ndF/ndQ", "ndC");
      assertFalse(registry.getDataSourcePathClashes().contains("ndP"));
   }

   private void importDataSource(String name, String additional, boolean overwrite)
      throws Exception
   {
      JDBCDataSource source = source(name);
      JDBCDataSource child = source(additional);
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
      writer.println("<registry>");
      writer.println("<datasource name=\"" + name + "\" type=\"" + source.getType() + "\">");
      source.writeXML(writer);
      writer.println("</datasource>");
      writer.println("<additional name=\"" + additional + "\" type=\"" + child.getType() +
                        "\" parent=\"" + name + "\">");
      child.writeXML(writer);
      writer.println("</additional>");
      new XDataModel(name).writeXML(writer);
      writer.println("</registry>");
      writer.flush();

      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(overwrite);
      new XDataSourceAsset(name).parseContent(
         new ByteArrayInputStream(buffer.toString().getBytes(StandardCharsets.UTF_8)), config,
         true, true);
   }

   private ConnectionStatus saveEdit(String path, String name, String... additionals)
      throws Exception
   {
      JDBCDataSource dataSource = (JDBCDataSource) repository.getDataSource(path);
      DatabaseDefinition definition = edit(dataSource);
      definition.setName(name);
      List<DatabaseDefinition> list = new ArrayList<>();

      for(String additional : additionals) {
         JDBCDataSource child = dataSource.getDataSource(additional);
         DatabaseDefinition def = edit(child != null ? child : source(additional));

         if(child != null) {
            def.setOldName(additional);
         }

         list.add(def);
      }

      return databaseService.saveDatabase(path, DataSourceSettingsModel.builder()
         .uploadEnabled(false).dataSource(definition)
         .additionalDataSources(list.toArray(new DatabaseDefinition[0]))
         .build(), ActionRecord.ACTION_NAME_EDIT, principal);
   }

   private ConnectionStatus saveCreate(String folder, String name, String... additionals)
      throws Exception
   {
      DatabaseDefinition definition = edit(source(name));
      DatabaseDefinition[] list = Arrays.stream(additionals)
         .map(additional -> edit(source(additional)))
         .toArray(DatabaseDefinition[]::new);
      return databaseService.saveDatabase(folder, DataSourceSettingsModel.builder()
         .uploadEnabled(false).dataSource(definition).additionalDataSources(list)
         .build(), ActionRecord.ACTION_NAME_CREATE, principal);
   }

   // a save as the portal controller sends it, the action is worked out from the path
   private ConnectionStatus controllerSave(String path, String name, String... additionals)
      throws Exception
   {
      DatabaseDefinition definition = edit(source(name));
      DatabaseDefinition[] list = Arrays.stream(additionals)
         .map(additional -> edit(source(additional)))
         .toArray(DatabaseDefinition[]::new);
      String action = databaseService.getActionName(path, name);
      return databaseService.saveDatabase(path, DataSourceSettingsModel.builder()
         .uploadEnabled(false).dataSource(definition).additionalDataSources(list)
         .build(), action, principal);
   }

   private void assertNames(String parentPath, String... names) {
      // read from the storage, not from instances cached by a save
      registry.clearCache();
      XDataSource parent = registry.getDataSource(parentPath);
      assertNotNull(parent, parentPath);
      String[] expected = names.clone();
      Arrays.sort(expected);
      String[] children = ((AdditionalConnectionDataSource<?>) parent).getDataSourceNames();
      Arrays.sort(children);
      assertArrayEquals(expected, children, "additional connections of " + parentPath);
   }

   private static DataSourceDefinition definition(String name, String parentPath) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setType(TAB);
      definition.setName(name);
      definition.setParentPath(parentPath);
      definition.setTabularView(new TabularView());
      return definition;
   }

   private static DataSourceXmlaDefinition xmla(String name, String parentPath) {
      DataSourceXmlaDefinition definition = new DataSourceXmlaDefinition();
      definition.setName(name);
      definition.setParentPath(parentPath);
      definition.setUrl("http://localhost/xmla");
      return definition;
   }

   private static NewRepositoryFolderRequest emFolder(String parent, String name) {
      NewRepositoryFolderRequest request = new NewRepositoryFolderRequest();
      request.setParentFolder(parent);
      request.setType(RepositoryEntry.DATA_SOURCE_FOLDER);
      request.setFolderName(name);
      return request;
   }

   private static AssetEntry dsEntry(String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null);
   }

   private static AssetEntry folderEntry(String path) {
      return new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, path, null);
   }

   private void folder(String path) {
      registry.setDataSourceFolder(new DataSourceFolder(path, LocalDateTime.now(), null));
   }

   private void tab(String path) throws Exception {
      Tab77691 dataSource = new Tab77691();
      dataSource.setName(path);
      registry.setDataSource(dataSource, false);
   }

   private void tabParent(String path, String... additionals) throws Exception {
      tab(path);
      Tab77691 parent = (Tab77691) registry.getDataSource(path);

      for(String name : additionals) {
         Tab77691 child = new Tab77691();
         child.setName(name);
         parent.addDatasource(child);
      }
   }

   private void jdbc(String path) throws Exception {
      registry.setDataSource(source(path), false);
   }

   // the names of the additional connections start with a prefix of their own, since every test
   // of the class saves to the same storage
   private void addParent(String path, String... additionals) throws Exception {
      jdbc(path);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(path);

      for(String name : additionals) {
         parent.addDatasource(source(name));
      }
   }

   private static JDBCDataSource source(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }

   // the definition the data source editor loads
   private static DatabaseDefinition edit(JDBCDataSource dataSource) {
      return JDBCUtil.buildDatabaseDefinition(
         dataSource, JDBCUtil.getJDBCDatabaseType(CustomDatabaseType.TYPE));
   }

   @Configuration
   static class Beans {
      // the registry reads data sources back through Config, register the tabular class there
      @Bean
      public static BeanPostProcessor configSpy() {
         return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
               if(bean instanceof Config config) {
                  try {
                     Config spy = spy(config);
                     doReturn(Tab77691.class.getName()).when(spy).getDataSourceClass(TAB);
                     doReturn(Tab77691.class).when(spy).getClass(TAB, Tab77691.class.getName());
                     return spy;
                  }
                  catch(ClassNotFoundException e) {
                     throw new IllegalStateException(e);
                  }
               }

               return bean;
            }
         };
      }

      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      // the constructors of these beans are package private
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      // loads the embedded Derby driver of the test sources
      @Bean
      @Primary
      public Drivers testDrivers() throws Exception {
         Drivers drivers = mock(Drivers.class);
         when(drivers.getDriverClass(anyString()))
            .thenAnswer(inv -> Class.forName(inv.<String>getArgument(0)));
         return drivers;
      }
   }
}
