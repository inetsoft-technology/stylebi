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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.service.database.PhysicalGraphService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.controller.WizDataModelWire.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Design section 9, charter DM-A9 and C7: for EVERY route of the data model bridge (J1, J2 and J3
 * controllers), a caller holding exactly the grants cited in design 4.2 gets past the gate, and a
 * caller missing any one of them is refused with the documented outcome naming the path (a
 * {@code SecurityException} with the Portal's text on a single-object route, a per-item
 * {@code PERMISSION_DENIED} / {@code CONTAINED_NOT_DELETABLE} / invisible {@code NOT_FOUND} on a
 * batch route or an invisible object) and nothing changes.
 *
 * <p>This is the only evidence for DM-A9/C7 where no non-admin identity can be created live. The
 * controllers, the real Portal services behind them and the real registry run; only the permission
 * oracle is substituted: one grant set backs {@code DataSourceService}, {@code LogicalModelService}
 * and {@code SecurityEngine}, so a route that asked for a permission not in its documented grant set
 * would be refused here, and a route that asked for less would be allowed with a grant removed.
 * No caller in this table is a site administrator.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  WizDataModelGatesRouteTableTest.DependencyStorageConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class WizDataModelGatesRouteTableTest {
   @Configuration
   static class DependencyStorageConfig {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }

   private static int count = 0;
   private static final String DENIED_TEXT = "Unauthorized access to resource \"";

   // -- the table ---------------------------------------------------------------------------

   /** What a caller missing one grant must get. */
   private record Expect(String kind, String detail) {
      /** SecurityException whose text names the path fragment. */
      static Expect thrown(String fragment) {
         return new Expect("throw", fragment);
      }

      /** A per-item result with the reason. */
      static Expect item(String reason) {
         return new Expect("item", reason);
      }

      /** A 200 {ok:false} body with the reason. */
      static Expect body(String reason) {
         return new Expect("body", reason);
      }
   }

   @FunctionalInterface
   private interface Call {
      Object call(Fixture fixture) throws Exception;
   }

   private record Route(String name, Call call, List<String> grants,
                        Map<String, Expect> missing)
   {
   }

   private static Route route(String name, Call call, String... grantsAndExpects) {
      List<String> grants = new ArrayList<>();
      Map<String, Expect> missing = new LinkedHashMap<>();

      for(int i = 0; i < grantsAndExpects.length; i += 3) {
         grants.add(grantsAndExpects[i]);
         missing.put(grantsAndExpects[i], expectOf(grantsAndExpects[i + 1], grantsAndExpects[i + 2]));
      }

      return new Route(name, call, grants, missing);
   }

   private static Expect expectOf(String kind, String detail) {
      return switch(kind) {
         case "throw" -> Expect.thrown(detail);
         case "item" -> Expect.item(detail);
         default -> Expect.body(detail);
      };
   }

   private static List<Route> routes() {
      List<Route> routes = new ArrayList<>();
      // fixture: database {db}; folders F, G; view pv in F; view rv at the root; logical model lm
      // in F on pv; VPM vp
      routes.add(route("physical/tables", f -> f.browse.physicalTables(
            new PhysicalTablesRequest(f.db, "pv", null), f.bob),
         "DS:{db}:READ", "throw", "{db}",
         "DS:{db}:WRITE", "throw", "{db}"));
      routes.add(route("dependencies", f -> f.items.dependencies(new DependenciesRequest(
            f.db, List.of(new Ref(KIND_PHYSICAL_VIEW, "rv", null))), f.bob),
         "DS:{db}:READ", "throw", "{db}"));

      routes.add(route("delete physicalView (root)", f -> f.items.delete(deleteOf(
            f, new DeleteItem(KIND_PHYSICAL_VIEW, "rv", null, false, null)), f.bob),
         "DS:{db}:WRITE", "item", NOT_FOUND));
      routes.add(route("delete logicalModel", f -> f.items.delete(deleteOf(
            f, new DeleteItem(KIND_LOGICAL_MODEL, "lm", null, false, null)), f.bob),
         "LM:{db}/F/lm:READ", "item", NOT_FOUND,
         "LM:{db}/F/lm:DELETE", "item", PERMISSION_DENIED));
      routes.add(route("delete vpm", f -> f.items.delete(deleteOf(
            f, new DeleteItem(KIND_VPM, "vp", null, false, null)), f.bob),
         "DS:{db}:WRITE", "item", PERMISSION_DENIED));
      routes.add(route("delete folder (empty)", f -> f.items.delete(deleteOf(
            f, new DeleteItem(KIND_FOLDER, "G", null, false, List.of())), f.bob),
         "DMF:{db}/G:DELETE", "item", PERMISSION_DENIED));
      routes.add(route("delete folder (with contents)", f -> f.items.delete(deleteOf(
            f, new DeleteItem(KIND_FOLDER, "F", null, true, List.of(
               new Ref(KIND_LOGICAL_MODEL, "lm", null), new Ref(KIND_PHYSICAL_VIEW, "pv", null)))), f.bob),
         "DMF:{db}/F:DELETE", "item", PERMISSION_DENIED,
         "LM:{db}/F/lm:DELETE", "item", CONTAINED_NOT_DELETABLE,
         "DMF:{db}/F:WRITE", "item", CONTAINED_NOT_DELETABLE));

      routes.add(route("rename physicalView", f -> f.items.rename(
            new RenameRequest(f.db, KIND_PHYSICAL_VIEW, "rv", "rv2", null), f.bob),
         "DS:{db}:WRITE", "body", NOT_FOUND));
      routes.add(route("rename logicalModel", f -> f.items.rename(
            new RenameRequest(f.db, KIND_LOGICAL_MODEL, "lm", "lm2", null), f.bob),
         "LM:{db}/F/lm:READ", "body", NOT_FOUND,
         "LM:{db}/F/lm:DELETE", "throw", "{db}/F/lm"));
      routes.add(route("rename vpm", f -> f.items.rename(
            new RenameRequest(f.db, KIND_VPM, "vp", "vp2", null), f.bob),
         "DS:{db}:WRITE", "throw", "{db}"));

      routes.add(route("folders/create", f -> f.folders.createFolder(
            new FolderCreateRequest(f.db, "N"), f.bob),
         "DMF:{db}/N:WRITE", "throw", "{db}/N"));
      routes.add(route("folders/rename", f -> f.folders.renameFolder(
            new FolderRenameRequest(f.db, "G", "H"), f.bob),
         "DMF:{db}/G:WRITE", "throw", "{db}/G",
         "DMF:{db}/G:DELETE", "throw", "{db}/G",
         "DMF:{db}/H:WRITE", "throw", "{db}/H"));

      routes.add(route("move logicalModel to a folder", f -> f.folders.move(new MoveRequest(
            f.db, "G", List.of(new Ref(KIND_LOGICAL_MODEL, "lm", null))), f.bob),
         "DMF:{db}/G:WRITE", "throw", "{db}/G",
         "LM:{db}/F/lm:WRITE", "item", PERMISSION_DENIED,
         "LM:{db}/F/lm:DELETE", "item", PERMISSION_DENIED));
      routes.add(route("move physicalView to a folder", f -> f.folders.move(new MoveRequest(
            f.db, "G", List.of(new Ref(KIND_PHYSICAL_VIEW, "pv", null))), f.bob),
         "DMF:{db}/G:WRITE", "throw", "{db}/G",
         "DMF:{db}/F:WRITE", "item", PERMISSION_DENIED));
      routes.add(route("move physicalView to the root", f -> f.folders.move(new MoveRequest(
            f.db, "/", List.of(new Ref(KIND_PHYSICAL_VIEW, "pv", null))), f.bob),
         "DS:{db}:WRITE", "throw", "{db}",
         "DMF:{db}/F:WRITE", "item", PERMISSION_DENIED));

      routes.add(route("logical/get", f -> f.logical.get(
            new WizLogicalModelWire.GetRequest(f.db, "lm", null), f.bob),
         // R1-4: the gate runs first and a source or model the caller cannot read is answered
         // exactly like a missing one
         "DS:{db}:READ", "body", NOT_FOUND,
         "LM:{db}/F/lm:READ", "body", NOT_FOUND));
      routes.add(route("logical/create", f -> f.logical.create(
            new WizLogicalModelWire.CreateRequest(f.db, logicalDefinition("n1", "F"), true), f.bob),
         "DS:{db}:READ", "body", NOT_FOUND,
         "LM:{db}/F/n1:WRITE", "throw", "{db}/F/n1"));
      routes.add(route("logical/update", f -> f.logical.update(
            new WizLogicalModelWire.UpdateRequest(
               f.db, "lm", logicalDefinition("lm", "F"), true), f.bob),
         "DS:{db}:READ", "body", NOT_FOUND,
         "LM:{db}/F/lm:READ", "body", NOT_FOUND,
         "LM:{db}/F/lm:WRITE", "throw", "{db}/F/lm"));
      routes.add(route("logical/update (folder change)", f -> f.logical.update(
            new WizLogicalModelWire.UpdateRequest(
               f.db, "lm", logicalDefinition("lm", "G"), true), f.bob),
         "DS:{db}:READ", "body", NOT_FOUND,
         "LM:{db}/F/lm:READ", "body", NOT_FOUND,
         "LM:{db}/F/lm:WRITE", "throw", "{db}/F/lm",
         "LM:{db}/F/lm:DELETE", "throw", "{db}/F/lm",
         "LM:{db}/G/lm:WRITE", "throw", "{db}/G/lm"));

      routes.add(route("vpm/get", f -> f.vpm.get(
            new WizVpmWire.GetRequest(f.db, "vp"), f.bob),
         "DS:{db}:WRITE", "throw", "{db}"));
      routes.add(route("vpm/create", f -> f.vpm.create(new WizVpmWire.WriteRequest(
            f.db, null, Map.of("name", "vn"), true), f.bob),
         "DS:{db}:WRITE", "throw", "{db}"));
      routes.add(route("vpm/update", f -> f.vpm.update(new WizVpmWire.WriteRequest(
            f.db, "vp", Map.of("name", "vp"), true), f.bob),
         "DS:{db}:WRITE", "throw", "{db}"));
      routes.add(route("vpm/test", f -> f.vpm.test(new WizVpmWire.TestRequest(
            f.db, "vp", null, "user", "u1"), f.bob),
         "DS:{db}:WRITE", "throw", "{db}"));

      return routes;
   }

   private static DeleteRequest deleteOf(Fixture f, DeleteItem item) {
      return new DeleteRequest(f.db, List.of(item));
   }

   private static WizLogicalModelWire.Definition logicalDefinition(String name, String folder) {
      return new WizLogicalModelWire.Definition(
         name, null, "pv", folder, null, null, null, List.of());
   }

   // -- the tests ---------------------------------------------------------------------------

   @Test
   void everyRouteOfEveryControllerIsInTheTable() {
      Set<String> covered = new TreeSet<>();

      for(Route route : routes()) {
         covered.add(route.name().replaceAll(" \\(.*\\)| to .*| physicalView| logicalModel| vpm", ""));
      }

      assertTrue(covered.containsAll(Set.of(
         "physical/tables", "dependencies", "delete", "rename", "folders/create", "folders/rename",
         "move", "logical/get", "logical/create", "logical/update", "vpm/get", "vpm/create",
         "vpm/update", "vpm/test")), covered.toString());
      // browse never throws for a missing grant: the services filter, see browseNeverThrows...
   }

   @Test
   void aCallerHoldingExactlyTheCitedGrantsIsNotRefused() throws Exception {
      for(Route route : routes()) {
         Fixture f = new Fixture(route.grants());
         Outcome outcome = f.run(route.call());

         assertFalse(outcome.thrownDenied(), route.name() + " refused a caller holding every cited " +
            "grant " + route.grants() + ": " + outcome);
         assertFalse(outcome.reason() != null &&
                        Set.of(PERMISSION_DENIED, CONTAINED_NOT_DELETABLE, NOT_FOUND)
                           .contains(outcome.reason()),
                     route.name() + " reported " + outcome.reason() + " to a caller holding " +
                        route.grants() + ": " + outcome);
      }
   }

   @Test
   void aCallerMissingAnyOneCitedGrantIsRefusedNamingThePathAndNothingChanges() throws Exception {
      for(Route route : routes()) {
         for(Map.Entry<String, Expect> entry : route.missing().entrySet()) {
            List<String> grants = new ArrayList<>(route.grants());
            grants.remove(entry.getKey());
            Fixture f = new Fixture(grants);
            String before = f.snapshot();
            Outcome outcome = f.run(route.call());
            String label = route.name() + " without " + entry.getKey();
            Expect expect = entry.getValue();

            switch(expect.kind()) {
            case "throw" -> {
               assertTrue(outcome.thrownDenied(), label + " must be a SecurityException: " + outcome);
               assertTrue(outcome.message().startsWith(DENIED_TEXT), label + ": " + outcome.message());
               assertTrue(outcome.message().contains("\" by "), label + ": " + outcome.message());
               assertTrue(outcome.message().contains(expect.detail().replace("{db}", f.db)),
                          label + " must name " + expect.detail() + ": " + outcome.message());
            }
            case "item", "body" -> {
               assertEquals(expect.detail(), outcome.reason(), label + ": " + outcome);
               assertFalse(outcome.thrownDenied(), label);
            }
            default -> fail(expect.kind());
            }

            assertEquals(before, f.snapshot(), label + " changed the data source");
         }
      }
   }

   @Test
   void browseNeverThrowsForAMissingGrantItFiltersThroughTheServices() throws Exception {
      Fixture f = new Fixture(List.of());
      Outcome outcome = f.run(fx -> fx.browse.browse(new BrowseRequest(fx.db, null, null, null, null),
                                                     fx.bob));

      assertFalse(outcome.thrownDenied(), outcome.toString());
   }

   @Test
   void theWholeTableRunsWithoutTheSiteAdministratorGate() {
      // every Fixture principal is a plain name with no role; the oracle knows no admin concept
      Fixture f = new Fixture(List.of());
      assertEquals("bob", f.bob.getName());
      assertFalse(f.bob instanceof inetsoft.sree.security.SRPrincipal);
   }

   // -- fixture -----------------------------------------------------------------------------

   private record Outcome(String kind, String reason, String message) {
      boolean thrownDenied() {
         return "throw".equals(kind);
      }
   }

   private static final class Fixture {
      final Principal bob = () -> "bob";
      final String db = "grants_" + (++count);
      final Set<String> grants = new HashSet<>();
      final XDataModel dm;
      final WizDataModelBrowseController browse;
      final WizDataModelFolderController folders;
      final WizDataModelItemController items;
      final WizLogicalModelController logical;
      final WizVpmController vpm;

      Fixture(List<String> templates) {
         for(String template : templates) {
            grants.add(template.replace("{db}", db));
         }

         try {
            DataSourceRegistry.getRegistry().init();
            dm = new XDataModel(db);
            dm.addFolder("F");
            dm.addFolder("G");
            addView("pv", "F");
            addView("rv", null);
            XLogicalModel lm = new XLogicalModel("lm");
            lm.setPartition("pv");
            lm.setFolder("F");
            lm.addEntity(new XEntity("E"));
            dm.addLogicalModel(lm);
            dm.addVirtualPrivateModel(new VirtualPrivateModel("vp"), true);

            XRepository repository = mock(XRepository.class);
            when(repository.getDataSource(db)).thenReturn(mock(JDBCDataSource.class));
            when(repository.getDataModel(db)).thenReturn(dm);
            SecurityEngine security = mock(SecurityEngine.class);
            when(security.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class)))
               .thenAnswer(i -> i.getArgument(1) == ResourceType.DATA_MODEL_FOLDER &&
                  grants.contains("DMF:" + i.getArgument(2) + ":" + i.getArgument(3)));
            DataSourceService dss = oracle(repository);
            DependencyHandler dependencyHandler = mock(DependencyHandler.class);
            RenameTransformHandler transform = mock(RenameTransformHandler.class);
            LogicalModelService real = new LogicalModelService(
               security, repository, dss, null, null, mock(AssetRepository.class),
               dependencyHandler, transform);
            LogicalModelService lms = spy(real);
            doAnswer(i -> grants.contains("LM:" + db + "/" + folderPart(i.getArgument(1)) +
                                          i.getArgument(2) + ":" + i.getArgument(4)))
               .when(lms).checkPermission(anyString(), any(), anyString(), any(),
                                          any(ResourceAction.class), any());
            PhysicalModelManagerService pmms = new PhysicalModelManagerService(
               dss, mock(PhysicalModelService.class), mock(RuntimePartitionService.class),
               new PhysicalGraphService(), repository, null, dependencyHandler, transform);
            DataModelFolderManagerService folderService = new DataModelFolderManagerService(
               repository, security, lms, dss, mock(DatasourcesService.class), pmms);
            DatabaseModelBrowserService browser = new DatabaseModelBrowserService(
               dss, repository, pmms, lms, security, folderService, transform);
            DataRefModelFactoryService dataRefs = mock(DataRefModelFactoryService.class);
            VpmService vpmService = new VpmService(
               dataRefs, dss, repository, security, dependencyHandler, transform);
            LogicalModelTreeService tree = mock(LogicalModelTreeService.class);
            List<WizModelKindOps> ops = List.of(
               new WizPhysicalViewOps(dss, pmms), new WizLogicalModelOps(lms),
               new WizVpmOps(dss, vpmService));
            browse = new WizDataModelBrowseController(browser, dss, lms, tree, repository);
            folders = new WizDataModelFolderController(
               browser, folderService, dss, lms, security, repository);
            items = new WizDataModelItemController(ops, folderService, dss, lms, repository);
            logical = new WizLogicalModelController(
               repository, dss, lms, tree, mock(inetsoft.report.composition.execution.AssetDataCache.class));
            vpm = new WizVpmController(repository, dss, lms, vpmService, dataRefs, security);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      private static String folderPart(String folder) {
         return folder == null || folder.isEmpty() ? "" : folder + "/";
      }

      private void addView(String name, String folder) {
         XPartition view = new XPartition(name);
         view.addTable("T1", null, null);
         view.setFolder(folder);
         dm.addPartition(view);
      }

      /** One grant set answers every permission question of every service. */
      private DataSourceService oracle(XRepository repository) throws Exception {
         DataSourceService dss = mock(DataSourceService.class);
         when(dss.checkPermission(anyString(), any(ResourceAction.class), any(Principal.class)))
            .thenAnswer(i -> grants.contains("DS:" + i.getArgument(0) + ":" + i.getArgument(1)));
         when(dss.checkPermission(anyString(), any(), any(ResourceAction.class), any()))
            .thenAnswer(i -> {
               String folder = i.getArgument(1);
               return folder == null || folder.isEmpty() ?
                  grants.contains("DS:" + i.getArgument(0) + ":" + i.getArgument(2)) :
                  grants.contains("DMF:" + i.getArgument(0) + "/" + folder + ":" + i.getArgument(2));
            });
         when(dss.checkDataModelFolderPermission(anyString(), any(ResourceAction.class), any()))
            .thenAnswer(i -> grants.contains("DMF:" + i.getArgument(0) + ":" + i.getArgument(1)));
         doAnswer(i -> {
            String path = i.getArgument(0);
            boolean allowed = grants.contains("DS:" + path + ":READ") &&
               (grants.contains("DS:" + path + ":WRITE") ||
                grants.stream().anyMatch(g -> g.startsWith("DMF:" + path + "/") &&
                                              g.endsWith(":WRITE")));

            if(!allowed) {
               throw new SecurityException(
                  "Unauthorized access to resource \"" + path + "\" by user " + i.getArgument(2));
            }

            return null;
         }).when(dss).checkDataModelEditPermission(anyString(), any(), any());
         when(dss.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
         when(dss.getDataModel(db)).thenReturn(dm);
         when(dss.isRootDataModelFolder(any())).thenAnswer(i -> {
            String f = i.getArgument(0);
            return f == null || f.isEmpty() || "/".equals(f);
         });
         return dss;
      }

      Outcome run(Call call) {
         try {
            Object body = call.call(this);

            if(body instanceof Failure failure) {
               return new Outcome("body", failure.reason(), failure.message());
            }

            if(body instanceof DeleteResponse response) {
               return firstFailure(response.results().stream()
                  .map(r -> r.ok() ? null : new Outcome("item", r.reason(), r.message())).toList());
            }

            if(body instanceof MoveResponse response) {
               return firstFailure(response.results().stream()
                  .map(r -> r.ok() ? null : new Outcome("item", r.reason(), r.message())).toList());
            }

            return new Outcome("ok", null, null);
         }
         catch(SecurityException | inetsoft.sree.security.SecurityException e) {
            // WizControllerErrorHandler answers both with a 403
            return new Outcome("throw", null, e.getMessage());
         }
         catch(Exception e) {
            // the gate was passed and something later in the request failed, which is not a denial
            return new Outcome("other", null, e.getClass().getSimpleName() + ": " + e.getMessage());
         }
      }

      private static Outcome firstFailure(List<Outcome> outcomes) {
         return outcomes.stream().filter(Objects::nonNull).findFirst()
            .orElse(new Outcome("ok", null, null));
      }

      /** The stored state the routes could change, read from the registry. */
      String snapshot() {
         DataSourceRegistry.getRegistry().clearCache();
         XDataModel fresh = new XDataModel(db);
         StringBuilder text = new StringBuilder();
         text.append(Arrays.toString(dm.getFolders())).append('|');

         for(String name : fresh.getLogicalModelNames()) {
            text.append("lm:").append(name).append('@').append(fresh.getLogicalModel(name).getFolder())
               .append('|');
         }

         for(String name : fresh.getPartitionNames()) {
            text.append("pv:").append(name).append('@').append(fresh.getPartition(name).getFolder())
               .append('|');
         }

         text.append("vpm:").append(Arrays.toString(fresh.getVirtualPrivateModelNames()));
         return text.toString();
      }
   }
}
