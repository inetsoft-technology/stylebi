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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
   void aCrossWorksheetMirrorUnderAFreeNameIsCreated() throws Exception {
      saveSource("am78881_free");
      Worksheet ws = new Worksheet();

      addWorksheetMirror(ws, "M", "am78881_free");

      assertInstanceOf(MirrorTableAssembly.class, ws.getAssembly("M"));
   }

   // ------------------------------------------------------------------- fixtures

   private void addWorksheetMirror(Worksheet ws, String name, String path) throws Exception {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("op", "add_mirror");
      body.put("name", name);
      body.put("path", path);
      ObjectMapper mapper = new WebConfig().objectMapper();
      EditRequest req = mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
      controller(ws).edit("TOK", req, agent);
   }

   private WorksheetAgentController controller(Worksheet ws) throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      return new WorksheetAgentController(
         feature, mock(SheetJoinService.class), sessions(), mock(WorksheetReadService.class),
         service(ws), worksheetService, mock(WorksheetPreviewService.class),
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
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      // skips the mirror's design-time column initialization, which needs a query sandbox
      when(rws.isDisposed()).thenReturn(true);
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
