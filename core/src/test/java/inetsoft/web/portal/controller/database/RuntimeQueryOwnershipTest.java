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
package inetsoft.web.portal.controller.database;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.ColumnCache;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.dialog.SQLQueryDialogService;
import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;
import inetsoft.web.portal.model.database.events.AddQueryTableEvent;
import inetsoft.web.portal.model.database.events.GetGraphModelEvent;
import inetsoft.web.portal.model.database.graph.TableDetailJoinInfo;
import inetsoft.web.portal.model.database.graph.TableJoinInfo;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.cache.Cache;
import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77190: runtime queries (SQL query dialog / query manager) are bound to the login
 * session that created them. Another session can neither read, overwrite nor remove them, and
 * closing one user's dialog removes only that dialog's runtime query.
 * <p>
 * The tests only use API that already existed before the fix, and select the caller through
 * the thread context principal, the same as the REST, STOMP and cluster proxy threads.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RuntimeQueryOwnershipTest {
   private static final String DS = "DS_OK";

   private Map<String, RuntimeQueryService.RuntimeXQuery> store;
   private Cache<String, RuntimeQueryService.RuntimeXQuery> cache;
   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private SecurityEngine securityEngine;
   private XRepository repository;
   private DataSourceService dataSourceService;
   private ViewsheetService wsEngine;
   private Principal oldContextPrincipal;

   private final SRPrincipal alice = principal("alice", 1001L);
   private final SRPrincipal bob = principal("bob", 2002L);

   @SuppressWarnings("unchecked")
   @BeforeEach
   void setUp() throws Exception {
      oldContextPrincipal = ThreadContext.getContextPrincipal();
      store = new ConcurrentHashMap<>();
      cache = mock(Cache.class);
      when(cache.get(any())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
      doAnswer(i -> { store.put(i.getArgument(0), i.getArgument(1)); return null; })
         .when(cache).put(any(), any());
      when(cache.putIfAbsent(any(), any()))
         .thenAnswer(i -> store.putIfAbsent(i.getArgument(0), i.getArgument(1)) == null);
      when(cache.remove(any())).thenAnswer(i -> store.remove(i.<String>getArgument(0)) != null);
      doAnswer(i -> { store.clear(); return null; }).when(cache).clear();
      Cluster cluster = mock(Cluster.class);
      when(cluster.getCache(anyString(), anyBoolean(), any())).thenReturn((Cache) cache);
      rqs = new RuntimeQueryService(cluster);
      rqs.init();

      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(Principal.class), any(ResourceType.class),
                                          nullable(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      repository = mock(XRepository.class);
      when(repository.getDataSourceFullNames()).thenReturn(new String[0]);
      dataSourceService = mock(DataSourceService.class);
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn(DS);
      when(dataSourceService.getDataSource(DS)).thenReturn(ds);
      qms = spy(new QueryManagerService(rqs, repository, dataSourceService, securityEngine,
                                        mock(ColumnCache.class)));
      wsEngine = mock(ViewsheetService.class);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(oldContextPrincipal);
   }

   private static SRPrincipal principal(String name, long secureID) {
      String org = Organization.getDefaultOrganizationID();
      return new SRPrincipal(new IdentityID(name, org), new IdentityID[0], new String[0], org,
                             secureID);
   }

   private static void as(Principal principal) {
      ThreadContext.setContextPrincipal(principal);
   }

   /** Opens a runtime query as the given user, as the SQL query dialog does. */
   private String open(Principal user) throws Exception {
      as(user);
      JDBCQuery query = new JDBCQuery();
      query.setName("Q");
      return rqs.createRuntimeQuery(null, query, DS, null).getId();
   }

   private RuntimeQueryService.RuntimeXQuery lookup(Principal user, String id) {
      as(user);
      return rqs.getRuntimeQuery(id);
   }

   // ---- closing a dialog ----

   @Test
   void closingOneUsersDialogKeepsAnotherUsersEntry() throws Exception {
      String aliceId = open(alice);
      String bobId = open(bob);

      as(bob);
      setModelAndClose(bobId, bob);

      assertNotNull(lookup(alice, aliceId), "alice's runtime query was removed by bob's close");
      assertTrue(rqs.touch(aliceId), "alice's heartbeat reports expired");
      assertNull(lookup(bob, bobId), "bob's own runtime query is removed on close");
   }

   /** Applies the SQL query dialog with closeDialog = true (STOMP setModel). */
   private void setModelAndClose(String runtimeQueryId, Principal user) throws Exception {
      SQLQueryDialogModel model = new SQLQueryDialogModel();
      model.setName("T1");
      model.setRuntimeId(runtimeQueryId);
      model.setDataSource(DS);
      model.setAdvancedEdit(false);
      BasicSQLQueryModel simple = new BasicSQLQueryModel();
      simple.setColumns(new SQLQueryDialogColumnModel[0]);
      model.setSimpleModel(simple);
      model.setCloseDialog(true);
      applyDialog(model, user);
   }

   /** Applies the SQL query dialog to the existing assembly T1 (STOMP setModel). */
   private void applyDialog(SQLQueryDialogModel model, Principal user) throws Exception {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class, RETURNS_DEEP_STUBS);
      Worksheet ws = mock(Worksheet.class);
      SQLBoundTableAssembly assembly = mock(SQLBoundTableAssembly.class);
      when(wsEngine.getWorksheet("ws-1", user)).thenReturn(rws);
      when(rws.getWorksheet()).thenReturn(ws);
      when(ws.getAssembly("T1")).thenReturn(assembly);
      when(ws.getWorksheetInfo()).thenReturn(mock(WorksheetInfo.class));
      when(assembly.getAggregateInfo()).thenReturn(new AggregateInfo());
      when(assembly.getInfo()).thenReturn(mock(SQLBoundTableAssemblyInfo.class));
      when(assembly.getColumnSelection()).thenReturn(new ColumnSelection());
      when(wsEngine.getAssetRepository()).thenReturn(mock(AssetRepository.class));
      doReturn(new ColumnSelection()).when(qms)
         .getColumnSelection(any(), any(), any(), any(), any());

      SQLQueryDialogService service = new SQLQueryDialogService(
         wsEngine, qms, mock(QueryGraphModelService.class), repository, securityEngine);

      try(MockedStatic<JDBCUtil> ignored1 = mockStatic(JDBCUtil.class);
          MockedStatic<WorksheetEventUtil> ignored2 = mockStatic(WorksheetEventUtil.class);
          MockedStatic<AssetEventUtil> ignored3 = mockStatic(AssetEventUtil.class))
      {
         service.setModel("ws-1", model, user, mock(CommandDispatcher.class));
      }
   }

   // ---- clear ----

   @Test
   void clearOnOwnExpiredIdReturnsTheSameId() throws Exception {
      String aliceId = open(alice);
      store.remove(aliceId); // expired

      as(alice);
      SQLQueryDialogModel model = qms.clearQuery(aliceId, DS, "T1", false, alice);

      assertEquals(aliceId, model.getRuntimeId());
      assertNotNull(lookup(alice, aliceId));
   }

   @Test
   void clearOnOwnLiveIdReplacesInPlace() throws Exception {
      String aliceId = open(alice);
      RuntimeQueryService.RuntimeXQuery before = lookup(alice, aliceId);

      as(alice);
      SQLQueryDialogModel model = qms.clearQuery(aliceId, DS, "T1", false, alice);

      assertEquals(aliceId, model.getRuntimeId());
      RuntimeQueryService.RuntimeXQuery after = lookup(alice, aliceId);
      assertNotNull(after);
      assertNotSame(before, after);
   }

   @Test
   void clearOnForeignIdIsRefusedAndLeavesTheEntryUnchanged() throws Exception {
      String aliceId = open(alice);
      RuntimeQueryService.RuntimeXQuery before = store.get(aliceId);

      as(bob);
      assertThrows(MessageException.class,
                   () -> qms.clearQuery(aliceId, DS, "T1", false, bob));

      assertSame(before, store.get(aliceId));
      assertNotNull(lookup(alice, aliceId));
   }

   @Test
   void clearOnBlankIdGetsANewIdPerUser() throws Exception {
      as(alice);
      String aliceId = qms.clearQuery("", DS, "T1", false, alice).getRuntimeId();
      as(bob);
      String bobId = qms.clearQuery("", DS, "T1", false, bob).getRuntimeId();

      assertUuid(aliceId);
      assertUuid(bobId);
      assertNotEquals(aliceId, bobId);
      assertNotNull(lookup(alice, aliceId));
      assertNull(lookup(bob, aliceId));
   }

   // ---- reads and writes by id ----

   @Test
   void foreignIdIsNotReadableTouchableOrRemovable() throws Exception {
      String aliceId = open(alice);

      as(bob);
      assertNull(rqs.getRuntimeQuery(aliceId));
      assertNull(qms.getRuntimeQuery(aliceId));
      assertFalse(rqs.touch(aliceId));
      assertTrue(rqs.isExpired(aliceId));
      rqs.destroy(aliceId);
      qms.destroyRuntimeQuery(aliceId);

      assertNotNull(lookup(alice, aliceId));
   }

   @Test
   void graphModelEventWithForeignIdsIsRefused() throws Exception {
      String aliceId = open(alice);
      String bobId = open(bob);
      RuntimeQueryService.RuntimeXQuery before = store.get(aliceId);
      QueryGraphModelController controller = graphController();
      clearInvocations(cache);

      as(bob);
      GetGraphModelEvent event = new GetGraphModelEvent();
      event.setRuntimeID(aliceId);
      assertNull(controller.queryGraphModel(event, bob));

      TableJoinInfo joinInfo = new TableJoinInfo();
      joinInfo.setRuntimeId(bobId);
      event.setTableJoinInfo(joinInfo);
      assertNull(controller.queryGraphModel(event, bob));

      event.setRuntimeID(bobId);
      joinInfo.setRuntimeId(aliceId);
      TableDetailJoinInfo detail = new TableDetailJoinInfo();
      detail.setRuntimeId(aliceId);
      controller.createJoin(detail);

      verify(cache, never()).put(eq(aliceId), any());
      assertSame(before, store.get(aliceId));
   }

   @Test
   void addTableEventWithForeignIdIsRefused() throws Exception {
      String aliceId = open(alice);
      QueryGraphModelController controller = graphController();
      clearInvocations(cache);

      as(bob);
      AddQueryTableEvent event = new AddQueryTableEvent();
      event.setId(aliceId);
      event.setTables(List.of(new AssetEntry(AssetRepository.QUERY_SCOPE,
                                             AssetEntry.Type.PHYSICAL_TABLE, "T1", null)));
      event.setPosition(new Point(0, 0));
      controller.addTable(event, bob);

      verify(cache, never()).put(eq(aliceId), any());
      assertNull(lookup(alice, aliceId).getSelectedTables());
   }

   @Test
   void clearTableWithForeignIdIsANoOp() throws Exception {
      String aliceId = open(alice);
      RuntimeQueryService.RuntimeXQuery before = store.get(aliceId);
      QueryGraphModelController controller = graphController();
      clearInvocations(cache);

      as(bob);
      assertDoesNotThrow(() -> controller.clearTable(aliceId));

      verify(cache, never()).put(eq(aliceId), any());
      assertSame(before, store.get(aliceId));
   }

   @Test
   void advancedQueryUpdateWithForeignIdReportsSessionExpired() throws Exception {
      String aliceId = open(alice);
      RuntimeQueryService.RuntimeXQuery before = store.get(aliceId);

      as(bob);
      assertThrows(MessageException.class,
                   () -> qms.updateQuery(aliceId, new AdvancedSQLQueryModel(), null, true));
      assertSame(before, store.get(aliceId));
   }

   @Test
   void advancedDialogApplyWithForeignIdReportsSessionExpired() throws Exception {
      String aliceId = open(alice);
      RuntimeQueryService.RuntimeXQuery before = store.get(aliceId);

      SQLQueryDialogModel model = new SQLQueryDialogModel();
      model.setName("T1");
      model.setRuntimeId(aliceId);
      model.setDataSource(DS);
      model.setAdvancedEdit(true);
      model.setAdvancedModel(new AdvancedSQLQueryModel());

      as(bob);
      assertThrows(MessageException.class, () -> applyDialog(model, bob));
      assertSame(before, store.get(aliceId));
   }

   private QueryGraphModelController graphController() {
      QueryGraphModelService graphService = new QueryGraphModelService(
         rqs, dataSourceService, qms, mock(DataSourceRegistry.class));
      return new QueryGraphModelController(rqs, qms, graphService);
   }

   @Test
   void joinEditOpenAndCloseRequireOwnershipOfBothIds() throws Exception {
      String aliceId = open(alice);
      String bobId = open(bob);
      RuntimeQueryService.RuntimeXQuery before = store.get(aliceId);

      as(bob);
      assertThrows(MessageException.class, () -> rqs.openNewRuntimeQuery(aliceId));

      String bobCopy = rqs.openNewRuntimeQuery(bobId);
      rqs.closeRuntimeQuery(aliceId, bobCopy, true);

      assertSame(before, store.get(aliceId));
      assertNotNull(lookup(alice, aliceId));

      // own ids still work
      as(bob);
      rqs.closeRuntimeQuery(bobId, bobCopy, true);
      assertNotNull(lookup(bob, bobId));
      assertNull(lookup(bob, bobCopy));
   }

   // ---- owner key ----

   @Test
   void sameIdentityWithADifferentSessionIsRefused() throws Exception {
      String aliceId = open(alice);
      SRPrincipal otherSession = principal("alice", 3003L);

      assertNull(lookup(otherSession, aliceId));
      assertFalse(rqs.touch(aliceId));
   }

   @Test
   void sameSessionWithAnotherPrincipalInstanceIsAllowed() throws Exception {
      String aliceId = open(alice);

      // e.g. the principal deserialized on another cluster node
      assertNotNull(lookup(new SRPrincipal(alice), aliceId));
   }

   @Test
   void nullContextPrincipalDoesNotMatchAnOwnedEntry() throws Exception {
      String aliceId = open(alice);

      as(null);
      assertNull(rqs.getRuntimeQuery(aliceId));
      assertFalse(rqs.touch(aliceId));
      rqs.destroy(aliceId);

      assertNotNull(lookup(alice, aliceId));
   }

   @Test
   void entryWithoutOwnerIsNotMatched() {
      rqs.saveRuntimeQuery(new RuntimeQueryService.RuntimeXQuery(null, "legacy", DS));

      assertNull(lookup(alice, "legacy"));
      as(null);
      assertNull(rqs.getRuntimeQuery("legacy"));
   }

   // ---- worksheet ids ----

   @Test
   void browseDataWithAWorksheetIdIsUnaffected() throws Exception {
      IllegalStateException reached = new IllegalStateException("worksheet looked up");
      when(wsEngine.getWorksheet("ws-1", bob)).thenThrow(reached);
      SQLQueryDialogService service = new SQLQueryDialogService(
         wsEngine, qms, mock(QueryGraphModelService.class), repository, securityEngine);

      as(bob);
      assertSame(reached, assertThrows(IllegalStateException.class,
         () -> service.browseData("ws-1", DS, new ColumnRefModel(), bob)));
      verify(cache, never()).get("ws-1");
   }

   // ---- ids ----

   @Test
   void idsAreRandomUuids() throws Exception {
      String first = open(alice);
      String second = open(alice);

      assertUuid(first);
      assertUuid(second);
      assertNotEquals(first, second);

      as(alice);
      assertUuid(rqs.openNewRuntimeQuery(first));
   }

   private static void assertUuid(String id) {
      assertNotNull(id);
      assertEquals(id, UUID.fromString(id).toString(), id);
   }
}
