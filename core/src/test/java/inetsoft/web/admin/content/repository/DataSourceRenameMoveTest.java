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

import inetsoft.report.XSessionManager;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularView;
import inetsoft.uql.util.Config;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.InvalidPathException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77836: a save or update of a data source must not move it to another folder. Moves go
 * through the move endpoint, which checks WRITE on the target folder and DELETE on the data
 * source. A JDBC save with a '/' in the new name and a tabular or XMLA update whose URL name has
 * a folder segment are refused, and nothing is stored or overwritten. The registry, the
 * repository and the services are the real ones. The security engine denies WRITE on every data
 * source folder and the creation of data sources, as for a user who may only edit the data
 * sources.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourcePathClashTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceRenameMoveTest {
   private static final String URL = "jdbc:derby:memory:bug77836;create=true";
   private static final String TAB = "Bug77691Tab";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private DatabaseDatasourcesService databaseService;
   private DatasourcesService datasourcesService;
   private XmlaDatasourceService xmlaService;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), nullable(String.class), any()))
         .thenAnswer(inv -> {
            ResourceType type = inv.getArgument(1);
            ResourceAction action = inv.getArgument(3);
            return !(type == ResourceType.DATA_SOURCE_FOLDER && action == ResourceAction.WRITE) &&
               type != ResourceType.CREATE_DATA_SOURCE;
         });
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

   // (a) a JDBC rename with a '/' in the name is refused: into a folder, into a folder that
   // doesn't exist, with a leading '/', and with a '..' segment
   @Test
   void jdbcRenameWithASlashIsRefused() throws Exception {
      folder("jsT");
      jdbc("jsX");
      folder("jsP");
      folder("jsP/jsS");
      jdbc("jsP/jsS/jsY");

      assertThrows(MessageException.class, () -> saveEdit("jsX", "jsT/jsX"));
      assertThrows(MessageException.class, () -> saveEdit("jsX", "jsNoSuch/jsX"));
      assertThrows(MessageException.class, () -> saveEdit("jsX", "/jsX2"));
      assertThrows(MessageException.class, () -> saveEdit("jsX", "jsX\\jsSub"));
      assertThrows(MessageException.class, () -> saveEdit("jsP/jsS/jsY", "../jsT2/jsY"));

      registry.clearCache();
      assertNotNull(registry.getDataSource("jsX"));
      assertNotNull(registry.getDataSource("jsP/jsS/jsY"));
      assertNull(registry.getDataSource("jsT/jsX"));
      assertNull(registry.getDataSource("jsNoSuch/jsX"));
      assertNull(registry.getDataSource("/jsX2"));
      assertNull(registry.getDataSource("jsP/jsS/../jsT2/jsY"));
   }

   // (a) the additional connections of a JDBC data source can't be renamed or added with a '/'
   // in the name either
   @Test
   void jdbcAdditionalConnectionWithASlashIsRefused() throws Exception {
      addParent("jaP", "jaAdd");

      assertThrows(MessageException.class, () -> saveEdit(
         "jaP", "jaP", additional("jaAdd", "jaT/jaAdd")));
      assertThrows(MessageException.class, () -> saveEdit(
         "jaP", "jaP", additional("jaAdd", "jaAdd"), additional(null, "jaT/jaNew")));
      // the additional connection edited on its own
      assertThrows(MessageException.class, () -> saveEdit("jaP/jaAdd", "jaT/jaAdd"));

      registry.clearCache();
      assertNames("jaP", "jaAdd");
   }

   // (a') a data source and an additional connection whose names have a character the name
   // check refuses (older data) are still saved under their names. The save writes metadata cache
   // files named after them, and Windows refuses these characters in a file name, so on Windows
   // only the name check is verified: the save gets past it and fails writing that file
   @Test
   void jdbcLegacyNamesStillSave() throws Exception {
      jdbc("jl:X?");
      addParent("jlP", "jl|Add");

      boolean saved = saveLegacy(() -> assertNull(saveEditDescription("jl:X?", "jl:X?", "edited")));
      saved &= saveLegacy(() -> assertNull(saveEdit("jlP", "jlP", additional("jl|Add", "jl|Add"))));
      Assumptions.assumeTrue(saved, "metadata cache file names, Windows");

      registry.clearCache();
      assertEquals("edited", registry.getDataSource("jl:X?").getDescription());
      assertNames("jlP", "jl|Add");
   }

   // (b) a tabular update whose URL name has a folder segment is refused and doesn't move the
   // data source to an ancestor folder or to the root
   @Test
   void tabularUpdateWithAFolderSegmentIsRefused() throws Exception {
      folder("tbT");
      folder("tbT/tbU");
      tab("tbT/tbU/tbX");
      folder("tbA");
      tab("tbA/tbY");

      assertThrows(MessageException.class, () -> datasourcesService.updateDataSource(
         "tbU/tbX", definition("tbX", "tbT"), principal));
      assertThrows(MessageException.class, () -> datasourcesService.updateDataSource(
         "tbA/tbY", definition("tbY", ""), principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("tbT/tbU/tbX"));
      assertNull(registry.getDataSource("tbT/tbX"));
      assertNotNull(registry.getDataSource("tbA/tbY"));
      assertNull(registry.getDataSource("tbY"));
   }

   // (b2) such an update doesn't overwrite a data source of the same name in the ancestor folder
   @Test
   void tabularUpdateDoesNotOverwriteADataSource() throws Exception {
      folder("owT");
      folder("owT/owU");
      tab("owT/owU/owX");
      tab("owT/owX");
      XDataSource victim = registry.getDataSource("owT/owX");
      victim.setDescription("VICTIM");
      registry.setDataSource(victim, false);

      assertThrows(MessageException.class, () -> datasourcesService.updateDataSource(
         "owU/owX", definition("owX", "owT"), principal));

      registry.clearCache();
      assertEquals("VICTIM", registry.getDataSource("owT/owX").getDescription());
      assertNotNull(registry.getDataSource("owT/owU/owX"));
   }

   // (b) the same for an XMLA data source
   @Test
   void xmlaUpdateWithAFolderSegmentIsRefused() throws Exception {
      folder("xbT");
      folder("xbT/xbU");
      allowFolderWrite(() -> xmlaService.createNewDataSource(
         xmla("xbX", "xbT/xbU"), true, principal));
      registry.clearCache();
      assertNotNull(registry.getDataSource("xbT/xbU/xbX"));

      assertThrows(MessageException.class, () -> xmlaService.updateDataSource(
         "xbU/xbX", xmla("xbX", "xbT"), principal));

      registry.clearCache();
      assertNotNull(registry.getDataSource("xbT/xbU/xbX"));
      assertNull(registry.getDataSource("xbT/xbX"));
   }

   // a plain rename in the same folder still works, for tabular and JDBC, also to a name with
   // spaces, '-' and '()'
   @Test
   void renameInTheSameFolderStillWorks() throws Exception {
      folder("nrF");
      tab("nrF/nrA");
      jdbc("nrF/nrJ");
      jdbc("nrR");

      DataSourceDefinition definition = definition("nrB", "nrF");
      definition.setDescription("renamed");
      datasourcesService.updateDataSource("nrA", definition, principal);
      assertNull(saveEdit("nrF/nrJ", "nrK"));
      assertNull(saveEdit("nrR", "nr R-1 (copy)"));

      registry.clearCache();
      assertNull(registry.getDataSource("nrF/nrA"));
      assertEquals("renamed", registry.getDataSource("nrF/nrB").getDescription());
      assertNull(registry.getDataSource("nrF/nrJ"));
      assertNotNull(registry.getDataSource("nrF/nrK"));
      assertNull(registry.getDataSource("nrR"));
      assertNotNull(registry.getDataSource("nr R-1 (copy)"));
   }

   private interface Action {
      void run() throws Exception;
   }

   // runs a save of a legacy name, false if it was refused by the file system of Windows after
   // the name check
   private static boolean saveLegacy(Action action) throws Exception {
      try {
         action.run();
         return true;
      }
      catch(InvalidPathException e) {
         if(!System.getProperty("os.name", "").startsWith("Windows")) {
            throw e;
         }

         return false;
      }
   }

   // creates with a security engine that allows everything
   private void allowFolderWrite(Action action) throws Exception {
      XmlaDatasourceService saved = xmlaService;
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), nullable(String.class), any())).thenReturn(true);
      xmlaService = new XmlaDatasourceService(
         repository, security, mock(DataSourceStatusService.class), registry, Config.getConfig(),
         mock(DependencyHandler.class), mock(XSessionManager.class));

      try {
         action.run();
      }
      finally {
         xmlaService = saved;
      }
   }

   // an additional connection in a save of its parent: its old name (null if new) and its name
   private static String[] additional(String oldName, String name) {
      return new String[] { oldName, name };
   }

   private ConnectionStatus saveEdit(String path, String name, String[]... additionals)
      throws Exception
   {
      return save(path, name, null, additionals);
   }

   private ConnectionStatus saveEditDescription(String path, String name, String description)
      throws Exception
   {
      return save(path, name, description);
   }

   // a save as the editor sends it, the name is set as a REST client would set it
   private ConnectionStatus save(String path, String name, String description,
                                 String[]... additionals)
      throws Exception
   {
      JDBCDataSource dataSource = (JDBCDataSource) repository.getDataSource(path);
      assertNotNull(dataSource, path);
      DatabaseDefinition definition = edit(dataSource);
      definition.setName(name);

      if(description != null) {
         definition.setDescription(description);
      }

      List<DatabaseDefinition> list = new ArrayList<>();

      for(String[] additional : additionals) {
         JDBCDataSource child = additional[0] == null ?
            null : dataSource.getDataSource(additional[0]);
         DatabaseDefinition def = edit(child != null ? child : source(additional[1]));
         def.setOldName(additional[0]);
         def.setName(additional[1]);
         list.add(def);
      }

      String action = databaseService.getActionName(path, name);
      assertEquals(ActionRecord.ACTION_NAME_EDIT, action);
      return databaseService.saveDatabase(path, DataSourceSettingsModel.builder()
         .uploadEnabled(false).dataSource(definition)
         .additionalDataSources(list.toArray(new DatabaseDefinition[0]))
         .build(), action, principal);
   }

   private void assertNames(String parentPath, String... names) {
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

   private void folder(String path) {
      registry.setDataSourceFolder(new DataSourceFolder(path, LocalDateTime.now(), null));
   }

   private void tab(String path) throws Exception {
      DataSourcePathClashTest.Tab77691 dataSource = new DataSourcePathClashTest.Tab77691();
      dataSource.setName(path);
      registry.setDataSource(dataSource, false);
   }

   private void jdbc(String path) throws Exception {
      registry.setDataSource(source(path), false);
   }

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
}
