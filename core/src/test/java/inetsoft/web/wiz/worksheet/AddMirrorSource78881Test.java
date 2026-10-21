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
package inetsoft.web.wiz.worksheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.table.SnapshotEmbeddedTableDataCache;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.WebConfig;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78881: add_mirror fails loudly, naming the problem, instead of a generic server error or
 * a reported success that created nothing. A same-worksheet source that is not a table names its
 * real kind, an absent one says how to get a table to mirror, and the cross-worksheet form
 * refuses a name that is already taken before it copies anything. Runs against the real asset
 * repository, as {@code MirrorResyncGuard78814Test} does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AddMirrorSource78881Test.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class AddMirrorSource78881Test {
   @Autowired
   private WorksheetService worksheetService;

   // a real org: the cross-worksheet form reads the source worksheet with a permission check
   private final Principal agent = principal();

   // ------------------------------------------------------------------- same-worksheet source

   @Test
   void aVariableSourceIsRefusedNamingItsKind() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(variable(ws, "V1"));
      List<String> before = names(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.addMirror("M", "V1")));

      assertTrue(ex.getMessage().contains("\"V1\" is a variable, not a table"), ex.getMessage());
      assertEquals(before, names(ws), "the refusal changed the worksheet");
   }

   @Test
   void aNamedGroupSourceIsRefusedNamingItsKind() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultNamedGroupAssembly(ws, "G1"));
      List<String> before = names(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.addMirror("M", "G1")));

      assertTrue(ex.getMessage().contains("\"G1\" is a named group, not a table"),
                 ex.getMessage());
      assertEquals(before, names(ws), "the refusal changed the worksheet");
   }

   @Test
   void anAbsentSourceIsRefusedWithAHint() throws Exception {
      Worksheet ws = new Worksheet();

      PairingException ex = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.addMirror("M", "ORDER_DETAILS")));

      assertTrue(ex.getMessage().contains("Source assembly not found: ORDER_DETAILS"),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("add_table"), ex.getMessage());
      assertTrue(ex.getMessage().contains("path"), ex.getMessage());
      assertEquals(List.of(), names(ws));
   }

   @Test
   void aTableSourceIsStillMirrored() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "T", "a", "b"));

      service(ws).apply("TOK", agent, ed -> ed.addMirror("M", "T"));

      assertInstanceOf(MirrorTableAssembly.class, ws.getAssembly("M"));
   }

   @Test
   void aMirrorNamedLikeItsSourceIsRefusedAndKeepsTheSource() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly source = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(source);

      PairingException ex = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.addMirror("T", "T")));

      assertTrue(ex.getMessage().contains("collides with one of its own source tables"),
                 ex.getMessage());
      assertSame(source, ws.getAssembly("T"));
      ws.checkValidity();
   }

   @Test
   void aMirrorNamedLikeATableItsSourceIsBuiltOnIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(table);
      service(ws).apply("TOK", agent, ed -> ed.addMirror("B", "T"));

      PairingException ex = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.addMirror("T", "B")));

      assertTrue(ex.getMessage().contains("\"T\" already names an existing assembly"),
                 ex.getMessage());
      assertSame(table, ws.getAssembly("T"));
      ws.checkValidity();
   }

   // ------------------------------------------------------------------- cross-worksheet form

   @Test
   void aCrossWorksheetMirrorOverADifferentKindIsRefusedBeforeCopying() throws Exception {
      saveSource("am78881_kind");
      Worksheet ws = new Worksheet();
      ws.addAssembly(variable(ws, "V1"));
      List<String> before = names(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> addWorksheetMirror(ws, "V1", "am78881_kind"));

      assertTrue(ex.getMessage().contains("\"V1\" already names an existing assembly"),
                 ex.getMessage());
      assertInstanceOf(DefaultVariableAssembly.class, ws.getAssembly("V1"));
      assertEquals(before, names(ws), "the refusal left source copies behind");
   }

   @Test
   void aCrossWorksheetMirrorOverATableOfTheSameNameIsRefusedInsteadOfReplacingIt()
      throws Exception
   {
      saveSource("am78881_same");
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly existing = TestWorksheets.tableWithColumns(ws, "ORDERS", "x");
      ws.addAssembly(existing);
      List<String> before = names(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> addWorksheetMirror(ws, "ORDERS", "am78881_same"));

      assertTrue(ex.getMessage().contains("\"ORDERS\" already names an existing assembly"),
                 ex.getMessage());
      assertSame(existing, ws.getAssembly("ORDERS"));
      assertEquals(before, names(ws), "the refusal left source copies behind");
   }

   @Test
   void aCrossWorksheetMirrorNamedLikeItsOwnSourceCopyIsRefused() throws Exception {
      // a space in the path, as in "Examples/Order Details"
      String path = "am78881 copy";
      saveSource(path);
      Worksheet ws = new Worksheet();
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        path, null);
      // the name copyOuterAssemblies gives its copy of the source's primary table
      String copyName = AssetUtil.createPrefix(entry) + "0";
      assertEquals("OUTER_am78881 copy_0", copyName);

      PairingException ex = assertThrows(PairingException.class,
         () -> addWorksheetMirror(ws, copyName, path));

      assertTrue(ex.getMessage().contains("\"" + copyName + "\""), ex.getMessage());
      assertEquals(List.of(), names(ws), "the refusal left source copies behind");

      // the worksheet is still usable: no self-referencing mirror was ever registered
      ws.checkValidity();
      addWorksheetMirror(ws, "M", path);
      assertInstanceOf(MirrorTableAssembly.class, ws.getAssembly("M"));
      ws.checkValidity();
   }

   @Test
   void aCrossWorksheetMirrorUnderAFreeNameIsCreated() throws Exception {
      saveSource("am78881_free");
      Worksheet ws = new Worksheet();

      addWorksheetMirror(ws, "M", "am78881_free");

      assertInstanceOf(MirrorTableAssembly.class, ws.getAssembly("M"));
   }

   // ------------------------------------------------------------------- reported result (#78917)

   @Test
   void aCrossWorksheetTableMirrorReportsItsNameAndKind() throws Exception {
      saveSource("am78917_table");
      Worksheet ws = new Worksheet();

      ResponseEntity<EditResponse> response = addWorksheetMirror(ws, "M", "am78917_table");

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertEquals(new EditResponse("M", "table"), response.getBody());
   }

   @Test
   void aCrossWorksheetVariableMirrorReportsItsNameAndKind() throws Exception {
      saveVariableSource("am78917_variable");
      Worksheet ws = new Worksheet();

      ResponseEntity<EditResponse> response = addWorksheetMirror(ws, "MV", "am78917_variable");

      assertInstanceOf(MirrorVariableAssembly.class, ws.getAssembly("MV"));
      assertEquals(new EditResponse("MV", "variable"), response.getBody());
   }

   @Test
   void aSameWorksheetMirrorReportsItsNameAndKind() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "T", "a", "b"));

      ResponseEntity<EditResponse> response =
         controller(ws).edit("TOK", request("add_mirror", "name", "M", "source", "T"), agent);

      assertEquals(new EditResponse("M", "table"), response.getBody());
   }

   @Test
   void anAddMirrorWithoutANameReportsTheNameItWasGiven() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "T", "a", "b"));

      ResponseEntity<EditResponse> response =
         controller(ws).edit("TOK", request("add_mirror", "source", "T"), agent);

      EditResponse body = response.getBody();
      assertNotNull(body);
      assertInstanceOf(MirrorTableAssembly.class, ws.getAssembly(body.assemblyName()));
      assertEquals("table", body.assemblyKind());
   }

   @Test
   void aKindIsLeftOutOfTheBodyOfOpsThatDoNotReportOne() throws Exception {
      ObjectMapper mapper = new WebConfig().objectMapper();

      assertEquals("{\"assemblyName\":\"T\"}",
                   mapper.writeValueAsString(new EditResponse("T", null)));

      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "T", "a", "b"));

      ResponseEntity<EditResponse> response =
         controller(ws).edit("TOK", request("delete_table", "table", "T"), agent);

      assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
      assertNull(response.getBody());
   }

   @Test
   void assemblyKindNamesEveryKindAMirrorCanTake() {
      Worksheet ws = new Worksheet();

      assertEquals("table", WorksheetEditService.Editor.assemblyKind(
         TestWorksheets.tableWithColumns(ws, "T", "a")));
      assertEquals("variable", WorksheetEditService.Editor.assemblyKind(variable(ws, "V")));
      assertEquals("named group", WorksheetEditService.Editor.assemblyKind(
         new DefaultNamedGroupAssembly(ws, "G")));
      assertEquals("condition", WorksheetEditService.Editor.assemblyKind(
         new DefaultConditionAssembly(ws, "C")));
      assertEquals("date range", WorksheetEditService.Editor.assemblyKind(
         new DefaultDateRangeAssembly(ws, "D")));
   }

   // ------------------------------------------------------------------- read model (#78949)

   @Test
   void aVariableMirrorIsListedAndItsHiddenCopyIsFlaggedUnderItsAssemblyName() throws Exception {
      saveVariableSource("am78949_read");
      Worksheet ws = new Worksheet();
      addWorksheetMirror(ws, "MV", "am78949_read");
      String outer = hiddenCopyName(ws);

      WorksheetModel model = new WorksheetReadService().read(rwsOf(ws));

      assertEquals(1, model.mirrors().size());
      WorksheetModel.MirrorModel mirror = model.mirrors().get(0);
      assertEquals("MV", mirror.name());
      assertEquals("variable", mirror.kind());
      assertEquals("am78949_read", mirror.source());
      assertTrue(mirror.outerMirror());

      // the copy is reported under the name every write tool resolves, and flagged hidden
      assertEquals(1, model.variables().size());
      WorksheetModel.VariableModel copy = model.variables().get(0);
      assertEquals(outer, copy.name());
      assertEquals(Boolean.TRUE, copy.hidden());
   }

   @Test
   void anOrdinaryVariableIsNotFlaggedHiddenAndNoMirrorsAreListed() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(variable(ws, "V1"));

      WorksheetModel model = new WorksheetReadService().read(rwsOf(ws));

      assertEquals(List.of(), model.mirrors());
      assertEquals("V1", model.variables().get(0).name());
      assertNull(model.variables().get(0).hidden());
   }

   // ------------------------------------------------------------------- variable tools (#78949)

   @Test
   void variableToolsRefuseAMirrorAndLeaveTheWorksheetUntouched() throws Exception {
      saveVariableSource("am78949_mirror");
      Worksheet ws = new Worksheet();
      addWorksheetMirror(ws, "MV", "am78949_mirror");
      List<String> before = names(ws);

      assertMirrorRefusal(
         assertThrows(PairingException.class, () -> service(ws).apply("TOK", agent,
            ed -> ed.editVariable("MV", null, "x", null, null))));
      assertMirrorRefusal(
         assertThrows(PairingException.class, () -> service(ws).apply("TOK", agent,
            ed -> ed.renameVariable("MV", "MV2"))));
      PairingException delete = assertThrows(PairingException.class,
         () -> service(ws).apply("TOK", agent, ed -> ed.deleteVariable("MV")));
      assertMirrorRefusal(delete);
      assertTrue(delete.getMessage().contains("delete_table"), delete.getMessage());
      assertMirrorRefusal(setVariableValues(ws, "MV"));
      assertEquals(before, names(ws));
   }

   @Test
   void variableToolsRefuseTheHiddenCopyBackingAMirror() throws Exception {
      saveVariableSource("am78949_outer");
      Worksheet ws = new Worksheet();
      addWorksheetMirror(ws, "MV", "am78949_outer");
      String outer = hiddenCopyName(ws);
      List<String> before = names(ws);

      assertHiddenCopyRefusal(
         assertThrows(PairingException.class, () -> service(ws).apply("TOK", agent,
            ed -> ed.editVariable(outer, null, "x", null, null))));
      assertHiddenCopyRefusal(
         assertThrows(PairingException.class, () -> service(ws).apply("TOK", agent,
            ed -> ed.renameVariable(outer, "renamed"))));
      assertHiddenCopyRefusal(
         assertThrows(PairingException.class, () -> service(ws).apply("TOK", agent,
            ed -> ed.deleteVariable(outer))));
      assertHiddenCopyRefusal(setVariableValues(ws, outer));
      assertEquals(before, names(ws));
   }

   @Test
   void deleteTableRemovesAVariableMirrorTogetherWithItsHiddenCopy() throws Exception {
      saveVariableSource("am78949_delete");
      Worksheet ws = new Worksheet();
      addWorksheetMirror(ws, "MV", "am78949_delete");

      service(ws).apply("TOK", agent, ed -> ed.deleteTable("MV"));

      assertEquals(List.of(), names(ws));
   }

   @Test
   void namedGroupToolsRefuseAMirrorAndItsHiddenCopy() throws Exception {
      saveNamedGroupSource("am78949_group");
      Worksheet ws = new Worksheet();
      addWorksheetMirror(ws, "MG", "am78949_group");
      String outer = hiddenCopyName(ws);
      List<String> before = names(ws);
      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("G2", List.of("c"), null));

      for(String name : List.of("MG", outer)) {
         PairingException edit = assertThrows(PairingException.class,
            () -> service(ws).apply("TOK", agent, ed -> ed.editNamedGroup(name, null, mappings, false)));
         PairingException delete = assertThrows(PairingException.class,
            () -> service(ws).apply("TOK", agent, ed -> ed.deleteNamedGroup(name)));

         for(PairingException ex : List.of(edit, delete)) {
            assertFalse(ex.getMessage().contains("not found"), ex.getMessage());
            assertTrue(ex.getMessage().contains("delete_table"), ex.getMessage());
            assertTrue(name.equals("MG") ? ex.getMessage().contains("is a mirror of am78949_group")
                       : ex.getMessage().contains("hidden copy that backs a mirror, not a named group"),
                       ex.getMessage());
         }
      }

      assertEquals(before, names(ws));
   }

   private static void assertMirrorRefusal(PairingException ex) {
      assertTrue(ex.getMessage().contains("'MV' is a mirror of am78949_"), ex.getMessage());
      assertTrue(ex.getMessage().contains("not editable"), ex.getMessage());
   }

   private static void assertHiddenCopyRefusal(PairingException ex) {
      assertTrue(ex.getMessage().contains("is a hidden copy that backs a mirror"),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("delete_table"), ex.getMessage());
   }

   private PairingException setVariableValues(Worksheet ws, String name) throws Exception {
      WorksheetAgentController controller = controller(ws, mock(AssetQuerySandbox.class));
      EditRequest request = new WebConfig().objectMapper().readValue(
         "{\"op\":\"set_variable_values\",\"variableValues\":{\"" + name + "\":\"x\"}}",
         EditRequest.class);

      return assertThrows(PairingException.class, () -> controller.edit("TOK", request, agent));
   }

   private static String hiddenCopyName(Worksheet ws) {
      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly instanceof WSAssembly wsa && wsa.isOuter()) {
            return assembly.getName();
         }
      }

      throw new AssertionError("no hidden copy in " + names(ws));
   }

   private static RuntimeWorksheet rwsOf(Worksheet ws) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      return rws;
   }

   // ------------------------------------------------------------------- fixtures

   private ResponseEntity<EditResponse> addWorksheetMirror(Worksheet ws, String name, String path)
      throws Exception
   {
      return controller(ws).edit("TOK", request("add_mirror", "name", name, "path", path), agent);
   }

   private static EditRequest request(String op, String... keysAndValues) throws Exception {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("op", op);

      for(int i = 0; i < keysAndValues.length; i += 2) {
         body.put(keysAndValues[i], keysAndValues[i + 1]);
      }

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   private WorksheetAgentController controller(Worksheet ws) throws Exception {
      return controller(ws, null);
   }

   private WorksheetAgentController controller(Worksheet ws, AssetQuerySandbox sandbox)
      throws Exception
   {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      return new WorksheetAgentController(
         feature, mock(SheetJoinService.class), sessions(), mock(WorksheetReadService.class),
         service(ws, sandbox), worksheetService, mock(WorksheetPreviewService.class),
         mock(SheetAgentBroadcastService.class), mock(inetsoft.uql.XRepository.class),
         readableRepository(), mock(inetsoft.web.wiz.service.MetadataApiService.class),
         mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
         mock(inetsoft.web.composer.ws.LayoutGraphService.class),
         mock(inetsoft.web.portal.controller.database.DataSourceService.class),
         mock(SecurityEngine.class), mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
         mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
         mock(inetsoft.report.composition.execution.AssetDataCache.class),
         mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
         mock(inetsoft.web.wiz.service.RawDataService.class));
   }

   private SheetSessionService sessions() throws Exception {
      SheetSessionService sessions = mock(SheetSessionService.class);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      return sessions;
   }

   private WorksheetEditService service(Worksheet ws) throws Exception {
      return service(ws, null);
   }

   private WorksheetEditService service(Worksheet ws, AssetQuerySandbox sandbox)
      throws Exception
   {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      // skips the mirror's design-time column initialization, which needs a query sandbox
      when(rws.isDisposed()).thenReturn(true);
      when(rws.getAssetQuerySandbox()).thenReturn(sandbox);
      when(runtimeAccess.getSheetForPairing(
         eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), eq(agent))).thenReturn(rws);
      return new WorksheetEditService(sessions(), runtimeAccess,
                                      mock(SheetAgentBroadcastService.class), securityEngine,
                                      new InnerJoinService(null, null));
   }

   private static Principal principal() {
      SRPrincipal principal = new SRPrincipal();
      principal.setUser(new ClientInfo(new IdentityID("alice", "host-org"), "127.0.0.1", null));
      principal.setOrgId("host-org");
      return principal;
   }

   /**
    * The real repository with the read-permission check left out: who may read the source
    * worksheet is not what these tests are about, and the test principal has no grants.
    */
   private static AssetRepository readableRepository() throws Exception {
      AssetRepository real = repository();
      AssetRepository repo = mock(AssetRepository.class);
      when(repo.getSheet(any(), any(), anyBoolean(), any(), anyBoolean())).thenAnswer(
         i -> real.getSheet(i.getArgument(0), null, false, i.getArgument(3)));
      when(repo.getSheet(any(), any(), anyBoolean(), any())).thenAnswer(
         i -> real.getSheet(i.getArgument(0), null, false, i.getArgument(3)));
      return repo;
   }

   private static AbstractAssetEngine repository() {
      return (AbstractAssetEngine) AssetUtil.getAssetRepository(false);
   }

   /** Saves a global worksheet whose primary assembly is a table T(a, b). */
   private void saveSource(String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        path, null);
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly table = new SnapshotEmbeddedTableAssembly(ws, "T");
      ws.addAssembly(table);
      ws.setPrimaryAssembly("T");
      XSwappableTable data = new XSwappableTable(2, false);
      data.addRow(new Object[] { "a", "b" });
      data.addRow(new Object[] { 1, 2 });
      data.complete();
      table.setEmbeddedData(new XEmbeddedTable(data));
      worksheetService.setWorksheet(ws, entry, null, true, true);
      repository().clearCache(entry);
      SnapshotEmbeddedTableDataCache.getInstance().clear();
   }

   /** Saves a global worksheet whose primary assembly is a variable. */
   private void saveVariableSource(String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        path, null);
      Worksheet ws = new Worksheet();
      ws.addAssembly(variable(ws, "V"));
      ws.setPrimaryAssembly("V");
      worksheetService.setWorksheet(ws, entry, null, true, true);
      repository().clearCache(entry);
   }

   /** Saves a global worksheet whose primary assembly is a named group. */
   private void saveNamedGroupSource(String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        path, null);
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultNamedGroupAssembly(ws, "G"));
      ws.setPrimaryAssembly("G");
      worksheetService.setWorksheet(ws, entry, null, true, true);
      repository().clearCache(entry);
   }

   /** A variable as add_variable creates it, with its definition. */
   private static DefaultVariableAssembly variable(Worksheet ws, String name) {
      DefaultVariableAssembly variable = new DefaultVariableAssembly(ws, name);
      variable.setVariable(new AssetVariable(name));
      return variable;
   }

   private static List<String> names(Worksheet ws) {
      List<String> names = new ArrayList<>();

      for(Assembly assembly : ws.getAssemblies()) {
         names.add(assembly.getName());
      }

      Collections.sort(names);
      return names;
   }

   @Configuration
   static class Beans {
      // the constructor is package private; saving a sheet updates the dependency storage
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }
   }
}
