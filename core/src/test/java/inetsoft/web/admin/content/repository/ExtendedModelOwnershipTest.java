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

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.model.database.LogicalModel;
import inetsoft.web.portal.model.database.PhysicalModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77842: an extended model is stored at {@code <ds>/<model>/<child>} and an extended view
 * at {@code <ds>/<view>/<child>}. A logical model and a physical view may have the same name,
 * and a model name or child name may have "/" in it, so the entries under the path of a model
 * may be another model's. A rename, remove or listing of a model must take only its own: the
 * entries of the matching type stored with the rest of the path as their name. An entry that
 * can't be read goes with its model if no other model's path is above it, and is refused if
 * one is; a listing leaves it out.
 * <p>
 * The registry, the data models and the portal service are the real ones. The security engine
 * is mocked and allows everything. The state is read back from the storage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourcePathClashTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExtendedModelOwnershipTest {
   private static final String URL = "jdbc:derby:memory:bug77842;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private MockedStatic<SecurityEngine> securityStatic;
   private SecurityEngine security;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.isVirtual()).thenReturn(false);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(security.getSecurityProvider()).thenReturn(provider);
      when(security.isSecurityEnabled()).thenReturn(true);
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);
      String org = Organization.getDefaultOrganizationID();
      principal = new SRPrincipal(new IdentityID("admin", org), new IdentityID[0], new String[0],
                                  org, 1L);
   }

   @AfterEach
   void tearDown() {
      registry.clearCache();
      securityStatic.close();
   }

   // a logical model and a view named Orders: renaming one moves only its own extended
   // entries, not the other's or the models Orders/x and Orders/y
   @Test
   void crossTypeRenameMovesOnlyOwnExtendedEntries() {
      seedSameName("ctP");
      List<String> before = state("ctP");

      model("ctP").renamePartition("Orders", "Orders2", null);

      List<String> after = state("ctP");
      assertEquals(List.of("EXTENDED_PARTITION ctP/Orders/pe", "PARTITION ctP/Orders"),
                   diff(before, after));
      assertEquals(List.of("EXTENDED_PARTITION ctP/Orders2/pe", "PARTITION ctP/Orders2"),
                   diff(after, before));
      assertEquals(List.of("ext"), extendedModels("ctP", "Orders"));
      assertEquals(List.of("pe"), extendedViews("ctP", "Orders2"));

      before = after;
      model("ctP").renameLogicalModel("Orders", "Orders3", null);

      after = state("ctP");
      assertEquals(List.of("DATA_MODEL ctP [ctP [Orders, Orders/x]]",
                           "EXTENDED_LOGIC_MODEL ctP/Orders/ext", "LOGIC_MODEL ctP/Orders"),
                   diff(before, after));
      assertEquals(List.of("DATA_MODEL ctP [ctP [Orders/x, Orders3]]",
                           "EXTENDED_LOGIC_MODEL ctP/Orders3/ext", "LOGIC_MODEL ctP/Orders3"),
                   diff(after, before));
      assertEquals(List.of("ext"), extendedModels("ctP", "Orders3"));
      assertEquals(List.of("pe"), extendedViews("ctP", "Orders2"));
   }

   // the mirror case: renaming the logical model first leaves the view's extended view
   @Test
   void crossTypeRenameOfLogicalModelLeavesView() {
      seedSameName("cmP");
      List<String> before = state("cmP");

      model("cmP").renameLogicalModel("Orders", "Orders2", null);

      List<String> after = state("cmP");
      assertEquals(List.of("DATA_MODEL cmP [cmP [Orders, Orders/x]]",
                           "EXTENDED_LOGIC_MODEL cmP/Orders/ext", "LOGIC_MODEL cmP/Orders"),
                   diff(before, after));
      assertEquals(List.of("pe"), extendedViews("cmP", "Orders"));
   }

   // the slash-named siblings Orders/x and Orders/y have their own extended entries: the
   // listings of Orders don't claim them, and a rename or remove of Orders leaves them
   @Test
   void slashNamedSiblingKeepsItsExtendedEntries() {
      seedSlashSiblings("ssP");

      assertEquals(List.of("ext"), extendedModels("ssP", "Orders"));
      assertEquals(List.of("e"), extendedModels("ssP", "Orders/x"));
      assertEquals(List.of("pe"), extendedViews("ssP", "Orders"));
      assertEquals(List.of("ye"), extendedViews("ssP", "Orders/y"));
      List<String> before = state("ssP");

      model("ssP").renameLogicalModel("Orders", "O2", null);
      model("ssP").renamePartition("Orders", "V2", null);

      List<String> after = state("ssP");
      assertEquals(List.of("DATA_MODEL ssP [ssP [Orders, Orders/x]]",
                           "EXTENDED_LOGIC_MODEL ssP/Orders/ext",
                           "EXTENDED_PARTITION ssP/Orders/pe", "LOGIC_MODEL ssP/Orders",
                           "PARTITION ssP/Orders"), diff(before, after));
      assertEquals(List.of("e"), extendedModels("ssP", "Orders/x"));
      assertEquals(List.of("ye"), extendedViews("ssP", "Orders/y"));
   }

   @Test
   void slashNamedSiblingSurvivesRemove() {
      seedSlashSiblings("srP");

      model("srP").removeLogicalModel("Orders", true);
      model("srP").removePartition("Orders");

      assertEquals(List.of("DATA_MODEL srP [srP [Orders/x]]", "DATA_SOURCE srP [srP]",
                           "EXTENDED_LOGIC_MODEL srP/Orders/x/e",
                           "EXTENDED_PARTITION srP/Orders/y/ye", "LOGIC_MODEL srP/Orders/x",
                           "PARTITION srP/Orders/y"), state("srP"));
      assertEquals(List.of("e"), extendedModels("srP", "Orders/x"));
      assertEquals(List.of("ye"), extendedViews("srP", "Orders/y"));
   }

   // Orders has the extended model "x/e" at q2P/Orders/x/e while a model Orders/x exists: it
   // is Orders's by its stored name, not the longer base's
   @Test
   void slashNamedChildOfShorterBaseStaysWithIt() {
      addSource("q2P");
      addLogicalModel("q2P", "Orders");
      addLogicalModel("q2P", "Orders/x");
      model("q2P").getLogicalModel("Orders").addLogicalModel(new XLogicalModel("x/e"), true);
      registry.clearCache();

      assertEquals(List.of("x/e"), extendedModels("q2P", "Orders"));
      assertEquals(List.of(), extendedModels("q2P", "Orders/x"));
      List<String> before = state("q2P");

      model("q2P").removeLogicalModel("Orders/x", true);

      assertEquals(List.of("DATA_MODEL q2P [q2P [Orders, Orders/x]]",
                           "LOGIC_MODEL q2P/Orders/x"), diff(before, state("q2P")));

      addLogicalModel("q2P", "Orders/x");
      before = state("q2P");
      model("q2P").renameLogicalModel("Orders", "O2", null);

      List<String> after = state("q2P");
      assertEquals(List.of("DATA_MODEL q2P [q2P [Orders, Orders/x]]",
                           "EXTENDED_LOGIC_MODEL q2P/Orders/x/e", "LOGIC_MODEL q2P/Orders"),
                   diff(before, after));
      assertEquals(List.of("DATA_MODEL q2P [q2P [O2, Orders/x]]",
                           "EXTENDED_LOGIC_MODEL q2P/O2/x/e", "LOGIC_MODEL q2P/O2"),
                   diff(after, before));
      assertEquals(List.of("x/e"), extendedModels("q2P", "O2"));
   }

   // an extended model and view that can't be read under m and v, with no other model above
   // them: a listing leaves them out, a rename moves them and completes, a remove deletes them
   @Test
   void unreadableExtendedEntryWithOneOwner() throws Exception {
      addSource("uP");
      addLogicalModel("uP", "m");
      model("uP").getLogicalModel("m").addLogicalModel(new XLogicalModel("ok"), true);
      addPartition("uP", "v");
      model("uP").getPartition("v").addPartition(new XPartition("pok"), false);
      setUnreadableEntry(AssetEntry.Type.EXTENDED_LOGIC_MODEL, "uP/m/bad");
      setUnreadableEntry(AssetEntry.Type.EXTENDED_PARTITION, "uP/v/bad");

      assertEquals(List.of("ok"), extendedModels("uP", "m"));
      assertEquals(List.of("pok"), extendedViews("uP", "v"));
      assertEquals(List.of("ok"), portalExtendedModels("uP", "m"));
      assertEquals(List.of("pok"), portalExtendedViews("uP", "v"));
      List<String> before = state("uP");

      model("uP").renameLogicalModel("m", "m2", null);
      model("uP").renamePartition("v", "v2", null);

      List<String> after = state("uP");
      assertEquals(List.of("DATA_MODEL uP [uP [m]]", "EXTENDED_LOGIC_MODEL uP/m/bad",
                           "EXTENDED_LOGIC_MODEL uP/m/ok", "EXTENDED_PARTITION uP/v/bad",
                           "EXTENDED_PARTITION uP/v/pok", "LOGIC_MODEL uP/m", "PARTITION uP/v"),
                   diff(before, after));
      assertEquals(List.of("ok"), extendedModels("uP", "m2"));

      model("uP").removeLogicalModel("m2", true);
      model("uP").removePartition("v2");

      assertEquals(List.of("DATA_MODEL uP [uP []]", "DATA_SOURCE uP [uP]"), state("uP"));
   }

   // an extended model that can't be read at uaP/Orders/x/bad may be Orders's or Orders/x's:
   // a rename or remove of either is refused before anything is changed, a listing leaves it
   // out
   @Test
   void unreadableExtendedEntryWithTwoPossibleOwnersIsRefused() {
      addSource("uaP");
      addLogicalModel("uaP", "Orders");
      addLogicalModel("uaP", "Orders/x");
      model("uaP").getLogicalModel("Orders").addLogicalModel(new XLogicalModel("ext"), true);
      model("uaP").getLogicalModel("Orders/x").addLogicalModel(new XLogicalModel("e"), true);
      addPartition("uaP", "Orders");
      addPartition("uaP", "Orders/x");
      setUnreadableEntry(AssetEntry.Type.EXTENDED_LOGIC_MODEL, "uaP/Orders/x/bad");
      setUnreadableEntry(AssetEntry.Type.EXTENDED_PARTITION, "uaP/Orders/x/bad");

      assertEquals(List.of("ext"), extendedModels("uaP", "Orders"));
      assertEquals(List.of("e"), extendedModels("uaP", "Orders/x"));
      assertEquals(List.of(), extendedViews("uaP", "Orders"));
      assertEquals(List.of(), extendedViews("uaP", "Orders/x"));
      List<String> before = state("uaP");

      assertThrows(MessageException.class,
                   () -> model("uaP").renameLogicalModel("Orders", "O2", null));
      assertThrows(MessageException.class,
                   () -> model("uaP").renameLogicalModel("Orders/x", "Z", null));
      assertThrows(MessageException.class,
                   () -> model("uaP").removeLogicalModel("Orders", true));
      assertThrows(MessageException.class,
                   () -> model("uaP").removeLogicalModel("Orders/x", true));
      assertThrows(MessageException.class,
                   () -> model("uaP").renamePartition("Orders", "O2", null));
      assertThrows(MessageException.class, () -> model("uaP").removePartition("Orders/x"));

      assertEquals(before, state("uaP"));
   }

   // the portal names an extended model by the rest of its path and gives it its own
   // connection, in any order of the entries, and lists only the base's own
   @Test
   void portalListsExtendedModelsByNameWithTheirConnections() throws Exception {
      addSource("pP");
      addLogicalModel("pP", "m");
      addLogicalModel("pP", "m/z");
      Map<String, String> expected = new TreeMap<>();
      String[] names = { "a", "b/1", "c", "d/2", "e", "f/3", "g", "h/4" };

      for(int i = 0; i < names.length; i++) {
         XLogicalModel ext = new XLogicalModel(names[i]);
         ext.setConnection("conn" + i);
         model("pP").getLogicalModel("m").addLogicalModel(ext, true);
         expected.put(names[i], "conn" + i);
      }

      // m's "z/y" is under the path of m/z, and m/z's own "w" too
      XLogicalModel zy = new XLogicalModel("z/y");
      zy.setConnection("connZY");
      model("pP").getLogicalModel("m").addLogicalModel(zy, true);
      expected.put("z/y", "connZY");
      XLogicalModel w = new XLogicalModel("w");
      w.setConnection("connW");
      model("pP").getLogicalModel("m/z").addLogicalModel(w, true);
      addPartition("pP", "v");
      model("pP").getPartition("v").addPartition(new XPartition("p/q"), false);
      registry.clearCache();

      Map<String, String> actual = new TreeMap<>();

      for(LogicalModel ext : portalModel("pP", "m").getExtendModels()) {
         actual.put(ext.getName(), ext.getConnection());
      }

      assertEquals(expected, actual);
      assertEquals(List.of("w"), portalExtendedModels("pP", "m/z"));
      assertEquals("connW", portalModel("pP", "m/z").getExtendModels().get(0).getConnection());
      assertEquals(List.of("p/q"), portalExtendedViews("pP", "v"));
   }

   // control: without a shared name or "/" a rename and remove take everything of the model
   @Test
   void noSlashControl() {
      addSource("ncP");
      addLogicalModel("ncP", "m");
      model("ncP").getLogicalModel("m").addLogicalModel(new XLogicalModel("e1"), true);
      model("ncP").getLogicalModel("m").addLogicalModel(new XLogicalModel("e2"), true);
      addPartition("ncP", "V");
      model("ncP").getPartition("V").addPartition(new XPartition("pe"), false);
      registry.clearCache();
      List<String> before = state("ncP");

      model("ncP").renameLogicalModel("m", "n", null);

      List<String> after = state("ncP");
      assertEquals(List.of("DATA_MODEL ncP [ncP [m]]", "EXTENDED_LOGIC_MODEL ncP/m/e1",
                           "EXTENDED_LOGIC_MODEL ncP/m/e2", "LOGIC_MODEL ncP/m"),
                   diff(before, after));
      assertEquals(List.of("e1", "e2"), extendedModels("ncP", "n"));

      model("ncP").removeLogicalModel("n", true);
      model("ncP").removePartition("V");

      assertEquals(List.of("DATA_MODEL ncP [ncP []]", "DATA_SOURCE ncP [ncP]"), state("ncP"));
   }

   // logical models and views Orders (extended "ext" and "pe"), and logical model Orders/x and
   // view Orders/y without extended entries
   private void seedSameName(String ds) {
      addSource(ds);
      addLogicalModel(ds, "Orders");
      model(ds).getLogicalModel("Orders").addLogicalModel(new XLogicalModel("ext"), true);
      addPartition(ds, "Orders");
      model(ds).getPartition("Orders").addPartition(new XPartition("pe"), false);
      addLogicalModel(ds, "Orders/x");
      addPartition(ds, "Orders/y");
      registry.clearCache();
   }

   // as seedSameName, and Orders/x has extended model "e", Orders/y extended view "ye"
   private void seedSlashSiblings(String ds) {
      seedSameName(ds);
      model(ds).getLogicalModel("Orders/x").addLogicalModel(new XLogicalModel("e"), true);
      model(ds).getPartition("Orders/y").addPartition(new XPartition("ye"), false);
      registry.clearCache();
   }

   private List<String> extendedModels(String ds, String name) {
      registry.clearCache();
      String[] names = model(ds).getLogicalModel(name).getLogicalModelNames();
      return Arrays.stream(names).sorted().toList();
   }

   private List<String> extendedViews(String ds, String name) {
      registry.clearCache();
      String[] names = model(ds).getPartition(name).getPartitionNames();
      return Arrays.stream(names).sorted().toList();
   }

   private LogicalModel portalModel(String ds, String name) throws Exception {
      registry.clearCache();
      return portal().getLogicalModels(ds, null, principal, false).stream()
         .filter(model -> (ds + "/" + name).equals(model.getPath()))
         .findFirst().orElseThrow();
   }

   private List<String> portalExtendedModels(String ds, String name) throws Exception {
      return portalModel(ds, name).getExtendModels().stream().map(LogicalModel::getName)
         .sorted().toList();
   }

   private List<String> portalExtendedViews(String ds, String name) throws Exception {
      registry.clearCache();
      return portal().getPhysicalModels(ds, null, principal, false).stream()
         .filter(view -> (ds + "/" + name).equals(view.getPath()))
         .findFirst().orElseThrow()
         .getExtendViews().stream().map(PhysicalModel::getName).sorted().toList();
   }

   private DataSourceService portal() {
      return new DataSourceService(mock(AssetRepository.class), security, repository, registry);
   }

   // an entry whose stored object is not what its type says
   private void setUnreadableEntry(AssetEntry.Type type, String path) {
      registry.setObject(new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null),
                         new DataSourceFolder("bad", LocalDateTime.now(), null));
      registry.clearCache();
   }

   private void addLogicalModel(String ds, String name) {
      model(ds).addLogicalModel(new XLogicalModel(name));
      registry.clearCache();
   }

   private void addPartition(String ds, String name) {
      model(ds).addPartition(new XPartition(name));
      registry.clearCache();
   }

   private XDataModel model(String ds) {
      XDataModel model = registry.getDataModel(ds);

      if(model == null) {
         model = new XDataModel(ds);
         registry.setDataModel(model);
      }

      return model;
   }

   // the stored entries at or under the root, read back from storage
   private List<String> state(String root) {
      registry.clearCache();
      List<String> lines = new ArrayList<>();

      for(AssetEntry entry : registry.getEntries("")) {
         String path = entry.getPath();

         if(!Tool.isSameOrDescendantPath(root, path)) {
            continue;
         }

         String line = entry.getType() + " " + path;

         if(entry.getType() == AssetEntry.Type.DATA_SOURCE) {
            line += " [" + registry.getDataSource(path).getFullName() + "]";
         }
         else if(entry.getType() == AssetEntry.Type.DATA_MODEL) {
            XDataModel model = registry.getDataModel(path);
            line += " [" + model.getDataSource() + " " +
               new TreeSet<>(List.of(model.getLogicalModelNames())) + "]";
         }

         lines.add(line);
      }

      Collections.sort(lines);
      return lines;
   }

   private static List<String> diff(List<String> a, List<String> b) {
      List<String> result = new ArrayList<>(a);
      result.removeAll(b);
      return result;
   }

   private void addSource(String path) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(path);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      registry.setDataSource(dataSource, false);
   }
}
