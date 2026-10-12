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

import inetsoft.report.LibManagerProvider;
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
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.util.Drivers;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import inetsoft.web.admin.security.ConnectionStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77725: a data source and a data source folder at the same path (a "clash", older data)
 * share the prefix {@code P/}. An operation on one side must not act on the other side's
 * entries: a data source delete, rename or move is refused while the folder side holds anything,
 * and a folder delete, rename or move is refused while the data source side holds anything, at
 * the folder's path or at a clash below it. A refusal comes before anything is written. When the
 * other side holds nothing the operation goes on, so the way out of a clash stays open. A save
 * of a data source never re-adds an entry under it whose stored name is not a bare name.
 * <p>
 * The registry, the repository and the EM service are the real ones. The permissions are kept in
 * a map behind a mocked security engine and provider. The state is read back from the storage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourcePathClashOperationsTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourcePathClashOperationsTest {
   private static final String URL = "jdbc:derby:memory:bug77725;create=true";
   // a type that isn't registered, a data source of this type can't be read
   private static final String GONE = "Bug77725Gone";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private final Map<String, Permission> store = new HashMap<>();
   private MockedStatic<SecurityEngine> securityStatic;
   private RepositoryObjectService objectService;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      store.clear();
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.isVirtual()).thenReturn(false);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      doAnswer(inv -> store.remove(key(inv.getArgument(0), inv.getArgument(1))))
         .when(provider).removePermission(any(ResourceType.class), anyString());
      // a non-virtual engine over the map, which the registry also gets from getSecurity()
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(security.getSecurityProvider()).thenReturn(provider);
      when(security.isSecurityEnabled()).thenReturn(true);
      when(security.getPermission(any(ResourceType.class), anyString()))
         .thenAnswer(inv -> store.get(key(inv.getArgument(0), inv.getArgument(1))));
      doAnswer(inv -> store.put(key(inv.getArgument(0), inv.getArgument(1)), inv.getArgument(2)))
         .when(security).setPermission(any(ResourceType.class), anyString(), any());
      doAnswer(inv -> store.remove(key(inv.getArgument(0), inv.getArgument(1))))
         .when(security).removePermission(any(ResourceType.class), anyString());
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);

      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.DATA_SOURCE_FOLDER, inv.<String>getArgument(1)));
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      objectService = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class), provider,
         permissions, repository, mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), registry, mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         repletRegistries, mock(DashboardRegistryManager.class));
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      registry.clearCache();
      securityStatic.close();
   }

   // ---- a data source delete while the folder side holds data sources and a subfolder ----

   @Test
   void registryDataSourceDeleteIsRefused() {
      clash("rdF", true, true);
      assertRefused(() -> registry.removeDataSource("rdF"), "rdF");
   }

   @Test
   void repositoryDataSourceDeleteIsRefused() {
      clash("xdF", true, true);
      assertRefused(() -> repository.removeDataSource("xdF"), "xdF");
   }

   @Test
   void emDataSourceDeleteIsRefused() {
      clash("edF", true, true);
      assertRefusedStatus(() -> objectService.deleteNodes(
         new TreeNodeInfo[] { node("edF", RepositoryEntry.DATA_SOURCE) }, principal, false,
         false), "edF");
   }

   // a multi-item EM delete is refused as a whole, the first item isn't deleted either
   @Test
   void emMultiDeleteIsRefusedAsAWhole() {
      addSource("mdOther");
      grant(ResourceType.DATA_SOURCE, "mdOther");
      clash("mdF", true, true);
      assertRefusedStatus(() -> objectService.deleteNodes(new TreeNodeInfo[] {
         node("mdOther", RepositoryEntry.DATA_SOURCE),
         node("mdF", RepositoryEntry.DATA_SOURCE) }, principal, false, false),
                          "mdOther", "mdF");
   }

   // the folder holds a data source that can't be read: it can't be told apart from an
   // additional connection, so it is refused, not guessed
   @Test
   void dataSourceDeleteWithAnUnreadableFolderMemberIsRefused() {
      addFolder("udF");
      addUnreadable("udF/udU", "udF/udU");
      addParent("udF", "udFAdd");
      grant(ResourceType.DATA_SOURCE, "udF");
      grant(ResourceType.DATA_SOURCE, "udF::udFAdd");
      grant(ResourceType.DATA_SOURCE, "udF/udU");
      registry.clearCache();
      assertTrue(registry.containObject(dsEntry("udF/udU")), "not seeded");
      assertNull(registry.getDataSource("udF/udU"), "not seeded unreadable");

      assertRefused(() -> registry.removeDataSource("udF"), "udF");
   }

   // ---- a data source rename or move while the folder side holds data sources ----

   // the rename also writes "Q/P/x" in the same call and hides the clash from the detector
   @Test
   void repositoryDataSourceRenameIsRefused() {
      clash("rnP", true, true);
      assertTrue(registry.getDataSourcePathClashes().contains("rnP"), "not seeded");

      assertRefused(() -> {
         XDataSource dataSource = (XDataSource) registry.getDataSource("rnP").clone();
         dataSource.setName("rnQ");
         repository.updateDataSource(dataSource, "rnP");
      }, "rnP", "rnQ");
      assertTrue(registry.getDataSourcePathClashes().contains("rnP"),
                 "the clash is no longer reported: " + registry.getDataSourcePathClashes());
   }

   @Test
   void emDataSourceMoveIsRefused() {
      addFolder("mvDest");
      clash("mvP", true, false);
      assertRefused(() -> objectService.moveFiles(
         move("mvDest", tree("mvP", RepositoryEntry.DATA_SOURCE)), true, principal),
                    "mvP", "mvDest");
   }

   // ---- a folder delete while the data source side holds an additional connection ----

   @Test
   void registryFolderDeleteIsRefused() {
      clash("fdP", true, false);
      assertRefused(() -> registry.removeDataSourceFolder("fdP"), "fdP");
   }

   // the folder is already empty, its delete still removes the additional connection
   @Test
   void registryEmptyFolderDeleteIsRefused() {
      clash("feP", false, false);
      assertRefused(() -> registry.removeDataSourceFolder("feP"), "feP");
   }

   @Test
   void repositoryFolderDeleteIsRefused() {
      clash("xfP", true, false);
      assertRefused(() -> repository.removeDataSourceFolder("xfP"), "xfP");
   }

   @Test
   void emFolderDeleteIsRefused() {
      clash("efP", true, false);
      assertRefusedStatus(() -> objectService.deleteNodes(
         new TreeNodeInfo[] { node("efP", RepositoryEntry.DATA_SOURCE_FOLDER) }, principal,
         false, false), "efP");
   }

   // ---- a folder rename or move while the data source side holds entries ----

   @Test
   void registryFolderRenameIsRefused() {
      clash("frF", true, false);
      assertRefused(() -> registry.renameDataSourceFolder("frF", "frG"), "frF", "frG");
   }

   // the data source has no additional connection, but a logical model stored under P/
   @Test
   void folderRenameWithALogicalModelOfTheDataSourceIsRefused() {
      addFolder("lmF");
      addSource("lmF/lmKid");
      addSource("lmF");
      XDataModel model = registry.getDataModel("lmF");

      if(model == null) {
         model = new XDataModel("lmF");
         registry.setDataModel(model);
      }

      model.addLogicalModel(new XLogicalModel("lmLm"));
      registry.clearCache();
      assertEquals(List.of("lmLm"),
                   List.of(registry.getDataModel("lmF").getLogicalModelNames()), "not seeded");

      assertRefused(() -> registry.renameDataSourceFolder("lmF", "lmG"), "lmF", "lmG");
   }

   @Test
   void repositoryFolderRenameIsRefused() {
      clash("xrF", true, false);
      assertRefused(() -> repository.updateDataSourceFolder(
         new DataSourceFolder("xrG", LocalDateTime.now(), null), "xrF"), "xrF", "xrG");
   }

   @Test
   void emFolderMoveIsRefused() {
      addFolder("emDest");
      clash("emF", true, false);
      assertRefused(() -> objectService.moveFiles(
         move("emDest", tree("emF", RepositoryEntry.DATA_SOURCE_FOLDER)), true, principal),
                    "emF", "emDest");
   }

   // a multi-item EM move is refused as a whole, the first folder isn't moved either
   @Test
   void emMultiMoveIsRefusedAsAWhole() {
      addFolder("mmDest");
      addFolder("mmA");
      addSource("mmA/mmX");
      grant(ResourceType.DATA_SOURCE, "mmA/mmX");
      clash("mmF", true, false);
      assertRefused(() -> objectService.moveFiles(
         move("mmDest", tree("mmA", RepositoryEntry.DATA_SOURCE_FOLDER),
              tree("mmF", RepositoryEntry.DATA_SOURCE_FOLDER)), true, principal),
                    "mmA", "mmF", "mmDest");
   }

   // the folder holds only a data source that can't be read: it can't be told apart from an
   // additional connection of the data source, so it is refused, not guessed
   @Test
   void folderRenameWithAnUnreadableMemberIsRefused() {
      addFolder("urF");
      addUnreadable("urF/urU", "urF/urU");
      addSource("urF");
      registry.clearCache();
      assertNull(registry.getDataSource("urF/urU"), "not seeded unreadable");

      assertRefused(() -> registry.renameDataSourceFolder("urF", "urG"), "urF", "urG");
   }

   // ---- an ancestor folder rename or move with a clash below it ----

   @Test
   void ancestorFolderRenameIsRefused() {
      addFolder("anG");
      clash("anG/anP", true, false);
      assertRefused(() -> registry.renameDataSourceFolder("anG", "anH"), "anG", "anH");
   }

   @Test
   void emAncestorFolderMoveIsRefused() {
      addFolder("amDest");
      addFolder("amG");
      clash("amG/amP", true, false);
      assertRefused(() -> objectService.moveFiles(
         move("amDest", tree("amG", RepositoryEntry.DATA_SOURCE_FOLDER)), true, principal),
                    "amG", "amDest");
   }

   // ---- a save of a data source with an entry under it stored under a full-path name ----

   @Test
   void saveDoesNotAddAnEntryUnderItsStoredName() throws Exception {
      addParent("svP", "svAdd");
      registry.setObject(dsEntry("svP/svX"), new XDataSourceWrapper(source("svQ/svX")));
      registry.clearCache();
      assertEquals("svQ/svX", registry.getDataSource("svP/svX").getFullName(), "not seeded");

      repository.updateDataSource(registry.getDataSource("svP"), "svP");

      registry.clearCache();
      assertFalse(containsDataSource("svP/svQ/svX"),
                  "the save wrote svP/svQ/svX: " + state("svP"));
      assertEquals("svAdd", registry.getDataSource("svP/svAdd").getFullName());
      assertTrue(containsDataSource("svP/svX"));
   }

   // ---- the other side holds nothing: the operation goes on (the way out of a clash) ----

   // the data source owns nothing under P/, the folder is renamed with its subtree
   @Test
   void folderRenameWhenTheDataSourceOwnsNothing() {
      addFolder("okF");
      addFolder("okF/okSub");
      addSource("okF/okX");
      addSource("okF/okSub/okY");
      addSource("okF");
      grant(ResourceType.DATA_SOURCE, "okF");
      grant(ResourceType.DATA_SOURCE, "okF/okX");

      registry.renameDataSourceFolder("okF", "okG");

      registry.clearCache();
      assertStoredName("okF", "okF");
      assertStoredName("okG/okX", "okG/okX");
      assertStoredName("okG/okSub/okY", "okG/okSub/okY");
      assertNotNull(registry.getDataSourceFolder("okG/okSub"));
      assertNull(registry.getDataSourceFolder("okF"));
      assertFalse(containsDataSource("okF/okX"));
      assertNotNull(perm(ResourceType.DATA_SOURCE, "okF"));
      assertNotNull(perm(ResourceType.DATA_SOURCE, "okG/okX"));
      assertFalse(registry.getDataSourcePathClashes().contains("okF"));
   }

   // an empty folder and a data source that owns nothing: a folder rename, and in another clash
   // a data source delete
   @Test
   void operationsWhenBothSidesAreEmpty() {
      addFolder("ebF");
      addSource("ebF");
      addFolder("ecF");
      addSource("ecF");

      registry.renameDataSourceFolder("ebF", "ebG");
      registry.removeDataSource("ecF");

      registry.clearCache();
      assertStoredName("ebF", "ebF");
      assertNotNull(registry.getDataSourceFolder("ebG"));
      assertNull(registry.getDataSourceFolder("ebF"));
      assertFalse(containsDataSource("ecF"));
      assertNotNull(registry.getDataSourceFolder("ecF"));
      assertTrue(state("ebF", "ebG", "ecF").stream().noneMatch(line -> line.contains("/")),
                 () -> "unexpected entries: " + state("ebF", "ebG", "ecF"));
   }

   // the folder side is emptied first, then the data source is renamed and the folder deleted
   @Test
   void exitSequence() throws Exception {
      addFolder("exOut");
      clash("exP", true, true);

      for(String path : new String[] { "exP/exPX", "exP/exPSub/exPY" }) {
         XDataSource dataSource = (XDataSource) registry.getDataSource(path).clone();
         dataSource.setName("exOut/" + path.substring(path.lastIndexOf('/') + 1));
         repository.updateDataSource(dataSource, path);
      }

      registry.removeDataSourceFolder("exP/exPSub");
      XDataSource dataSource = (XDataSource) registry.getDataSource("exP").clone();
      dataSource.setName("exQ");
      repository.updateDataSource(dataSource, "exP");
      registry.removeDataSourceFolder("exP");

      registry.clearCache();
      assertStoredName("exQ", "exQ");
      assertStoredName("exQ/exPAdd", "exPAdd");
      assertStoredName("exOut/exPX", "exOut/exPX");
      assertStoredName("exOut/exPY", "exOut/exPY");
      assertEquals(List.of("exPAdd"),
                   List.of(((AdditionalConnectionDataSource<?>) registry.getDataSource("exQ"))
                              .getDataSourceNames()));
      assertNotNull(perm(ResourceType.DATA_SOURCE, "exQ"));
      assertNotNull(perm(ResourceType.DATA_SOURCE, "exQ::exPAdd"));
      assertNotNull(perm(ResourceType.DATA_SOURCE, "exOut/exPX"));
      assertNotNull(perm(ResourceType.DATA_SOURCE, "exOut/exPY"));
      assertNull(registry.getDataSourceFolder("exP"));
      assertFalse(registry.getDataSourcePathClashes().contains("exP"));
      assertEquals(List.of(), state("exP"), "left at the old path");
   }

   // the operation must throw a MessageException and leave the stored state as it was
   private void assertRefused(Action action, String... roots) {
      List<String> before = state(roots);
      Throwable thrown = attempt(action);
      assertUnchanged(before, state(roots), thrown);
      assertInstanceOf(MessageException.class, thrown, "not refused");
   }

   // an EM delete may also refuse with the status it returns for a denial
   private void assertRefusedStatus(StatusAction action, String... roots) {
      List<String> before = state(roots);
      Object[] status = new Object[1];
      Throwable thrown = attempt(() -> status[0] = action.run());
      assertUnchanged(before, state(roots), thrown);

      if(thrown == null) {
         assertInstanceOf(ConnectionStatus.class, status[0], "not refused");
      }
      else {
         assertInstanceOf(MessageException.class, thrown, "not refused");
      }
   }

   private static Throwable attempt(Action action) {
      try {
         action.run();
         return null;
      }
      catch(Throwable e) {
         return e;
      }
   }

   private static void assertUnchanged(List<String> before, List<String> after, Throwable thrown) {
      List<String> removed = new ArrayList<>(before);
      removed.removeAll(after);
      List<String> added = new ArrayList<>(after);
      added.removeAll(before);
      assertTrue(removed.isEmpty() && added.isEmpty(),
                 () -> "the stored state changed (thrown: " + thrown + "); removed: " + removed +
                    "; added: " + added);
   }

   // the stored entries at or under the roots, with the stored names of the data sources and
   // the data sources and logical models of the data models, and every permission key
   private List<String> state(String... roots) {
      registry.clearCache();
      List<String> lines = new ArrayList<>();

      for(AssetEntry entry : registry.getEntries("")) {
         String path = entry.getPath();

         if(Arrays.stream(roots).noneMatch(root -> Tool.isSameOrDescendantPath(root, path))) {
            continue;
         }

         String line = entry.getType() + " " + path;

         if(entry.getType() == AssetEntry.Type.DATA_SOURCE) {
            XDataSource dataSource = registry.getDataSource(path);
            line += " [" + (dataSource != null ? dataSource.getFullName() :
               registry.containObject(entry) ? "unreadable" : "missing") + "]";
         }
         else if(entry.getType() == AssetEntry.Type.DATA_MODEL) {
            XDataModel model = registry.getDataModel(path);
            line += model == null ? " [missing]" : " [" + model.getDataSource() + " " +
               new TreeSet<>(List.of(model.getLogicalModelNames())) + "]";
         }

         lines.add(line);
      }

      for(String key : store.keySet()) {
         String resource = key.substring(key.indexOf('|') + 1).replaceFirst("::.*", "");

         if(Arrays.stream(roots).anyMatch(root -> Tool.isSameOrDescendantPath(root, resource))) {
            lines.add("grant " + key);
         }
      }

      Collections.sort(lines);
      return lines;
   }

   // a data source P with an additional connection "<P>Add" (bare name) and a folder P. The
   // folder holds "P/<P>X" and, if subfolder, "P/<P>Sub/<P>Y". Every object has a grant.
   private void clash(String path, boolean member, boolean subfolder) {
      String name = path.substring(path.lastIndexOf('/') + 1);
      addFolder(path);
      grant(ResourceType.DATA_SOURCE_FOLDER, path);

      if(member) {
         addSource(path + "/" + name + "X");
         grant(ResourceType.DATA_SOURCE, path + "/" + name + "X");
      }

      if(subfolder) {
         addFolder(path + "/" + name + "Sub");
         addSource(path + "/" + name + "Sub/" + name + "Y");
         grant(ResourceType.DATA_SOURCE_FOLDER, path + "/" + name + "Sub");
         grant(ResourceType.DATA_SOURCE, path + "/" + name + "Sub/" + name + "Y");
      }

      addParent(path, name + "Add");
      grant(ResourceType.DATA_SOURCE, path);
      grant(ResourceType.DATA_SOURCE, path + "::" + name + "Add");
      registry.clearCache();
      assertTrue(registry.getDataSourcePathClashes().contains(path), "not seeded: " + path);
      assertEquals(List.of(name + "Add"), List.of(
         ((AdditionalConnectionDataSource<?>) registry.getDataSource(path)).getDataSourceNames()),
         "not seeded: " + path);
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   private void addSource(String path) {
      registry.setDataSource(source(path), false);
   }

   private void addParent(String path, String... additionals) {
      addSource(path);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(path);

      for(String name : additionals) {
         parent.addDatasource(source(name));
      }
   }

   // a data source of a type that isn't registered, stored at the path with the name
   private void addUnreadable(String path, String name) {
      GoneDataSource dataSource = new GoneDataSource();
      dataSource.setName(name);
      registry.setObject(dsEntry(path), new XDataSourceWrapper(dataSource));
   }

   private void assertStoredName(String path, String name) {
      XDataSource dataSource = registry.getDataSource(path);
      assertNotNull(dataSource, path);
      assertEquals(name, dataSource.getFullName(), "the stored name of " + path);
   }

   private boolean containsDataSource(String path) {
      return registry.containObject(dsEntry(path));
   }

   private static AssetEntry dsEntry(String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null);
   }

   private static String key(ResourceType type, String path) {
      return type + "|" + path;
   }

   private void grant(ResourceType type, String path) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ, Set.of(new Permission.PermissionIdentity(
         "alice", Organization.getDefaultOrganizationID())));
      store.put(key(type, path), permission);
   }

   private Permission perm(ResourceType type, String path) {
      return store.get(key(type, path));
   }

   private static TreeNodeInfo node(String path, int type) {
      int index = path.lastIndexOf('/');
      return TreeNodeInfo.builder()
         .label(index < 0 ? path : path.substring(index + 1))
         .path(path)
         .type(type)
         .build();
   }

   private static ContentRepositoryTreeNode tree(String path, int type) {
      int index = path.lastIndexOf('/');
      return ContentRepositoryTreeNode.builder()
         .label(index < 0 ? path : path.substring(index + 1))
         .path(path)
         .type(type)
         .build();
   }

   private static MoveCopyTreeNodesRequest move(String destination,
                                                ContentRepositoryTreeNode... sources)
   {
      return MoveCopyTreeNodesRequest.builder()
         .source(List.of(sources))
         .destination(tree(destination, RepositoryEntry.DATA_SOURCE_FOLDER))
         .build();
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

   @FunctionalInterface
   private interface Action {
      void run() throws Exception;
   }

   @FunctionalInterface
   private interface StatusAction {
      Object run() throws Exception;
   }

   public static class GoneDataSource extends TabularDataSource<GoneDataSource> {
      public GoneDataSource() {
         super(GONE, GoneDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }
   }

   @Configuration
   static class Beans {
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
