/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.datasource;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Tool;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.datasource.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.idgen.MD5IdentifierGenerator;
import inetsoft.web.security.auth.ResourceExistsException;
import org.junit.jupiter.api.*;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80186: the admin change planner must refuse plans whose paths clash with each other or with
 * the registry. Unlike {@link DataSourceChangePlanServiceTest}, which mocks DataSourceService, this
 * runs the planner and the real DataSourceService logic over an in-memory registry that answers
 * {@code isDataSourcePathInUse}/{@code getDataSourceAncestor} with the real exact-path semantics,
 * so the entity-qualified names ("Examples/Census") and folder paths are the real shapes. The live
 * registry is the one thing this cannot exercise: case-insensitive backends are unverified.
 */
@Tag("core")
class DataSourcePlanPathClashTest {
   @BeforeEach
   void setUp() throws Exception {
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      OrganizationManager orgManager = mock(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      tool = mockStatic(Tool.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      dependencyTool = mockStatic(DependencyTool.class, withSettings().lenient());
      dependencyTool.when(() -> DependencyTool.getDependencies(anyString())).thenReturn(List.of());
      sutil = mockStatic(SUtil.class);
      sutil.when(() -> SUtil.getUserName(any())).thenReturn("alice");

      dataSources.clear();
      folders.clear();
      dataSources.addAll(List.of("sakila", "Orders", "Examples/Census", "Examples/Sales"));
      folders.addAll(List.of("Examples", "Archive"));

      DataSourceRegistry registry = mock(DataSourceRegistry.class);
      when(registry.isDataSourcePathInUse(any())).thenAnswer(inv -> {
         String p = inv.getArgument(0);
         return p != null && (dataSources.contains(p) || folders.contains(p));
      });
      when(registry.getDataSourceAncestor(any())).thenAnswer(inv -> {
         String path = inv.getArgument(0);

         for(int i = path.indexOf('/'); i != -1; i = path.indexOf('/', i + 1)) {
            if(dataSources.contains(path.substring(0, i))) {
               return path.substring(0, i);
            }
         }

         return null;
      });

      XRepository repository = mock(XRepository.class);
      when(repository.getDataSourceFolder(any())).thenAnswer(inv ->
         folders.contains((String) inv.getArgument(0)) ? mock(DataSourceFolder.class) : null);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      user = mock(XPrincipal.class);
      when(user.getName()).thenReturn(new IdentityID("alice", "host-org").convertToKey());

      realService = new DataSourceService(repository, securityEngine,
         mock(DatabaseTypeService.class), mock(MD5IdentifierGenerator.class), registry);
      service = spy(realService);

      for(String name : dataSources) {
         String id = "id-" + name;
         DataSourceDescription description = new DataSourceDescription();
         description.setId(id);
         description.setName(name);
         description.setType("jdbc");
         DataSourceList list = new DataSourceList();
         list.setDataSources(List.of(description));
         doReturn(list).when(service).getDataSources(name, user);
         doReturn(jdbc(id, name)).when(service).getDataSource(id, user);
         doReturn("pw").when(service).getCurrentPassword(id, user);
      }

      planner = new DataSourceChangePlanService(service, mock(DatabaseSettingsService.class));
   }

   @AfterEach
   void tearDown() {
      sutil.close();
      dependencyTool.close();
      tool.close();
      orgManagerStatic.close();
   }

   // ---- S1: two renames onto one free name

   @Test
   void s1TwoRenamesOntoOneFreeNameAreRefused() {
      IllegalArgumentException ex = refused(rename("Orders", "Free"),
                                            rename("Examples/Census", "Free"));
      assertTrue(ex.getMessage().contains("changes[1].spec.name"), ex.getMessage());
      assertTrue(ex.getMessage().contains("\"Free\""), ex.getMessage());
   }

   @Test
   void s1RenameOntoAPathUnderAnEarlierRenameTargetIsRefused() {
      IllegalArgumentException ex = refused(rename("Orders", "Free"),
                                            rename("Examples/Census", "Free/Census"));
      assertTrue(ex.getMessage().contains("\"Free\""), ex.getMessage());
   }

   @Test
   void renameOntoAFolderImpliedByAnEarlierRenameIsRefused() {
      IllegalArgumentException ex = refused(rename("Orders", "Free/Orders"),
                                            rename("sakila", "Free"));
      assertTrue(ex.getMessage().contains("\"Free\""), ex.getMessage());
   }

   @Test
   void twoRenamesIntoOneNewFolderAreAllowed() {
      assertDoesNotThrow(() -> plan(rename("Orders", "Free/Orders"),
                                    rename("sakila", "Free/sakila")));
   }

   // ---- S2: folder create + rename onto the same path, both orders

   @Test
   void s2FolderCreateThenRenameOntoItIsRefused() {
      IllegalArgumentException ex = refused(folder("Fresh"), rename("Orders", "Fresh"));
      assertTrue(ex.getMessage().contains("changes[1].spec.name"), ex.getMessage());
      assertTrue(ex.getMessage().contains("\"Fresh\""), ex.getMessage());
   }

   @Test
   void s2RenameThenFolderCreateAtItIsRefused() {
      IllegalArgumentException ex = refused(rename("Orders", "Fresh"), folder("Fresh"));
      assertTrue(ex.getMessage().contains("changes[1].folderPath"), ex.getMessage());
      assertTrue(ex.getMessage().contains("\"Fresh\""), ex.getMessage());
   }

   @Test
   void folderCreateUnderAnEarlierRenameTargetIsRefused() {
      IllegalArgumentException ex = refused(rename("Orders", "Fresh"), folder("Fresh/Sub"));
      assertTrue(ex.getMessage().contains("\"Fresh\""), ex.getMessage());
   }

   @Test
   void renameUnderAnEarlierCreatedFolderIsAllowed() {
      assertDoesNotThrow(() -> plan(folder("Fresh"), rename("Orders", "Fresh/Orders")));
   }

   @Test
   void folderCreateOfAFolderImpliedByAnEarlierCreateIsRefused() {
      refused(folder("Fresh/Sub"), folder("Fresh"));
   }

   // ---- S3 / S4: folder create against the live registry

   @Test
   void s3FolderCreateAtAnExistingDataSourceIsRefused() {
      IllegalArgumentException ex = refused(folder("sakila"));
      assertTrue(ex.getMessage().contains("\"sakila\""), ex.getMessage());
   }

   @Test
   void s3FolderCreateAtAQualifiedDataSourceIsRefused() {
      IllegalArgumentException ex = refused(folder("Examples/Census"));
      assertTrue(ex.getMessage().contains("Examples/Census"), ex.getMessage());
   }

   @Test
   void s4FolderCreateUnderADataSourceIsRefused() {
      IllegalArgumentException ex = refused(folder("sakila/Probe-Sub"));
      assertTrue(ex.getMessage().contains("\"sakila\""), ex.getMessage());
   }

   @Test
   void folderCreateDeepUnderADataSourceIsRefusedAtTheAncestor() {
      IllegalArgumentException ex = refused(folder("Examples/Census/A/B"));
      assertTrue(ex.getMessage().contains("Examples/Census"), ex.getMessage());
   }

   @Test
   void folderCreateAtAnExistingFolderStillSaysAlreadyExists() throws Exception {
      doReturn(true).when(service).dataSourceFolderExists("Archive");
      IllegalArgumentException ex = refused(folder("Archive"));
      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
   }

   // the apply primitive refuses the same paths, so a clash that appears after the preview is
   // still refused before any folder is created
   @Test
   void createDataSourceFolderRefusesADataSourcePathAndAncestor() throws Exception {
      assertThrows(ResourceExistsException.class,
                   () -> realService.createDataSourceFolder("sakila", user));
      assertThrows(ResourceExistsException.class,
                   () -> realService.createDataSourceFolder("sakila/Sub/Deep", user));
      assertThrows(ResourceExistsException.class,
                   () -> realService.checkFolderCreateTarget("Archive", Set.of()));
   }

   @Test
   void checkFolderCreateTargetAllowsVacatedPaths() {
      assertDoesNotThrow(() -> realService.checkFolderCreateTarget("sakila", Set.of("sakila")));
      assertDoesNotThrow(
         () -> realService.checkFolderCreateTarget("sakila/Sub", Set.of("sakila")));
   }

   // ---- controls

   @Test
   void controlSingleRenameOntoAFreeNamePasses() {
      assertDoesNotThrow(() -> plan(rename("Orders", "Free")));
   }

   @Test
   void controlDeleteThenRenameOntoTheDeletedNamePasses() {
      assertDoesNotThrow(() -> plan(delete("sakila"), rename("Orders", "sakila")));
   }

   @Test
   void controlRenameAwayThenRenameOntoTheVacatedNamePasses() {
      assertDoesNotThrow(() -> plan(rename("Orders", "Free"), rename("sakila", "Orders")));
   }

   @Test
   void controlDeleteThenFolderCreateAtTheDeletedNamePasses() {
      assertDoesNotThrow(() -> plan(delete("sakila"), folder("sakila")));
   }

   @Test
   void controlFreshFolderCreatePasses() {
      assertDoesNotThrow(() -> plan(folder("Fresh"), folder("Fresh2/Nested")));
   }

   @Test
   void vacatedThenClaimedPathIsClaimedForLaterChanges() {
      refused(delete("sakila"), rename("Orders", "sakila"), folder("sakila"));
   }

   // ---- helpers

   private IllegalArgumentException refused(DataSourceChangeRequest... changes) {
      return assertThrows(IllegalArgumentException.class, () -> plan(changes));
   }

   private void plan(DataSourceChangeRequest... changes) throws Exception {
      DataSourceChangePlanRequest req = new DataSourceChangePlanRequest();
      req.setTask("task");
      req.setChanges(List.of(changes));
      planner.resolve(req, user);
   }

   private static DataSourceChangeRequest rename(String name, String newName) {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_UPDATE);
      change.setName(name);
      change.setSpec(Map.of("name", newName));
      change.setConfirmRename(true);
      return change;
   }

   private static DataSourceChangeRequest delete(String name) {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_DELETE);
      change.setName(name);
      return change;
   }

   private static DataSourceChangeRequest folder(String path) {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_CREATE);
      change.setFolderPath(path);
      return change;
   }

   private static JdbcDataSourceProperties jdbc(String id, String name) {
      JdbcDataSourceProperties p = new JdbcDataSourceProperties();
      p.setId(id);
      p.setName(name);
      p.setUrl("jdbc:h2:mem:test");
      p.setDriver("org.h2.Driver");
      p.setDefaultDatabase("APP");
      p.setTableName(JdbcDataSourceProperties.DEFAULT_OPTION);
      p.setIsolation(JdbcDataSourceProperties.IsolationLevel.DEFAULT);
      p.setAnsiJoin(false);
      p.setRequireLogin(false);
      p.setUseCredentialId(false);
      return p;
   }

   private final Set<String> dataSources = new HashSet<>();
   private final Set<String> folders = new HashSet<>();
   private DataSourceService realService;
   private DataSourceService service;
   private DataSourceChangePlanService planner;
   private XPrincipal user;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Tool> tool;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<DependencyTool> dependencyTool;
}
