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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.util.Config;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.model.WorksheetTableRequest;
import inetsoft.web.wiz.model.WorksheetTableResponse;
import inetsoft.web.wiz.model.WorksheetTablesResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The two-branch empty-columns message ({@code WorksheetTableService.emptyColumnsMessage}):
 * whether {@code TabularQuery.getResponseShape()} is non-null decides whether the probe's zero
 * columns mean "the request never got a parseable response" (today's parameters/credentials
 * wording, unchanged) or "the request succeeded and genuinely selected zero rows" (new wording,
 * carrying the response shape, the row path in effect, and any recovered connector exception).
 *
 * <p>Same skeleton as {@code WorksheetTableServiceProbeHintTest} -- same {@code TestConfig}, same
 * {@code service(...)} helper -- adapted to (1) pre-configure the {@code FakeNamedConnectorQuery}
 * fixture BEFORE the build, since the shape/exception a real connector would have left behind must
 * be in place before {@code loadOutputColumns} runs, and (2) support more than one table per batch,
 * needed only by the cross-table leak test below.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, WorksheetTableServiceEmptyResultMessageTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceEmptyResultMessageTest {
   @Configuration
   static class TestConfig {
      @Bean
      public Config config() {
         Config config = mock(Config.class);
         when(config.getResourceBundle(ArgumentMatchers.any())).thenReturn(null);
         return config;
      }
   }

   private static final ObjectMapper MAPPER = new ObjectMapper();
   private static final Principal USER = mock(Principal.class);

   private static WorksheetTableResponse only(WorksheetTablesResponse response) {
      assertEquals(1, response.getTables().size(), "expected exactly one table result");
      return response.getTables().get(0);
   }

   private WorksheetTableService service(XRepository xrepository,
                                         DataSourceService dataSourceService,
                                         SecurityEngine securityEngine)
      throws Exception
   {
      // Stubbed (unlike WorksheetTableServiceProbeHintTest's identical helper) because this test
      // class, unlike that one, also builds a genuinely successful table (A8) -- createTables then
      // reaches WsServiceHelper.persistWorksheet, which dereferences getAssetRepository() directly.
      // Harmless for the failure-path tests: a rolled-back table never gets that far.
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      when(assetRepository.containsEntry(any())).thenReturn(true);
      when(viewsheetService.getAssetRepository()).thenReturn(assetRepository);

      return new WorksheetTableService(viewsheetService, mock(MetadataApiService.class),
         mock(InnerJoinService.class), mock(LayoutGraphService.class),
         mock(QueryManagerService.class), xrepository, new ObjectMapper(), dataSourceService,
         securityEngine);
   }

   /**
    * Build one tabular table per fixture given, all in one {@code createTables} batch, against a
    * shared mocked {@code myds} data source. Each fixture is expected to have already been
    * configured (shape/exception/output columns) by the caller -- unlike
    * {@code WorksheetTableServiceProbeHintTest.build}, which constructs its own fixture, this one
    * takes pre-built fixtures because the whole point here is to control what a real connector's
    * probe would have left on the query BEFORE {@code loadOutputColumns} runs.
    */
   private WorksheetTablesResponse build(FakeNamedConnectorQuery... queries) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                          eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(xrepository.getDataSource(eq("myds"))).thenReturn(ds);

      StringBuilder tablesJson = new StringBuilder();

      for(int i = 0; i < queries.length; i++) {
         if(i > 0) {
            tablesJson.append(",");
         }

         tablesJson.append("""
            { "tableName": "t%d", "tableType": "tabular table",
              "tabularSource": { "datasourcePath": "myds", "queryParams": { "endpoint": "Repos" } } }
            """.formatted(i + 1));
      }

      WorksheetTableRequest request =
         MAPPER.readValue("{ \"tables\": [ " + tablesJson + " ] }", WorksheetTableRequest.class);

      try(MockedStatic<TabularUtil> tabularUtil =
             mockStatic(TabularUtil.class, CALLS_REAL_METHODS))
      {
         TabularQuery first = queries[0];
         TabularQuery[] rest = Arrays.copyOfRange(queries, 1, queries.length);
         tabularUtil.when(() -> TabularUtil.createQuery(eq("myds"))).thenReturn(first, rest);

         return service(xrepository, dataSourceService, securityEngine).createTables(request, USER);
      }
   }

   // ─── A1: shape present -> success-framed message ──────────────────────────────────────────

   @Test
   void shapePresentReportsSuccessAndTheShape() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      query.reportResponseShape(Map.of("data", List.of()), false);

      String message = only(build(query)).getErrorMessage();

      assertTrue(message.contains("completed successfully"),
         "shape present must state the request succeeded: " + message);
      // Not a broad contains("credentials") -- the A1 branch legitimately says "this is not a
      // connection or credentials problem" as reassurance, which contains the word without
      // repeating the A2 branch's actual guidance. The sentinel below is A2's specific advisory
      // phrase; see shapePresenceAloneFlipsTheMessageBranch for the exact mutual-exclusion check.
      assertFalse(message.contains("credentials are valid"),
         "the success-framed branch must not repeat the A2 branch's credentials guidance: " +
            message);
      assertTrue(message.contains("data="),
         "the rendered response shape must appear in the message: " + message);
   }

   @Test
   void shapePresentWithRowPathNamesThePathInEffect() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      query.reportResponseShape(Map.of("data", List.of()), false);
      query.setJsonPath("$.data[*]");

      String message = only(build(query)).getErrorMessage();

      assertTrue(message.contains("Rows were read from '$.data[*]'"),
         "the effective row path must be named in the message: " + message);
   }

   @Test
   void truncatedShapeIsFlagged() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      query.reportResponseShape(Map.of("data", List.of()), true);

      String message = only(build(query)).getErrorMessage();

      assertTrue(message.contains("capped"),
         "a truncated shape must say so, per TabularQuery's own not-present/not-reached " +
            "distinction: " + message);
   }

   // ─── A2: shape absent -> unchanged wording ─────────────────────────────────────────────────

   @Test
   void noShapeKeepsTodaysWording() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      String message = only(build(query)).getErrorMessage();

      assertTrue(message.contains(
         "Check them against GET /api/wiz/tabular/query-schema"),
         "the no-shape branch's wording must be preserved verbatim for existing consumers: " +
            message);
   }

   // ─── A9: the no-shape behaviour generalizes past SERVER_FILE to any connector that never ──
   // ─── populates a shape, including a generic non-endpoint REST source (reconcile amendment) ─

   /**
    * BR1 (the verifier's plan, {@code test/docs/tabular/2026-08-25-tabular-module-test-plan.md}
    * §3.3.1) asserted this invariant for {@code SERVER_FILE} specifically. The architect's task 5
    * found the affected surface is wider: a generic non-endpoint REST source (plain
    * {@code RestJsonQuery}, base {@code RestJsonQueryRunner.runStream()} with no
    * {@code shapeResponse} call) never populates a shape either, even on a genuine, parseable,
    * zero-row success — same as {@code SERVER_FILE}/OneDrive.
    *
    * <p>One fixture suffices for all three: {@code emptyColumnsMessage} never inspects connector
    * type, only {@code query.getResponseShape() == null}, which the design confirmed is
    * non-null if and only if {@code EndpointJsonQueryRunner.shapeResponse} ran. Nothing here
    * distinguishes SERVER_FILE (no {@code getValidJsonPath()} method at all) from a generic REST
    * source (has the method, but nothing ever calls {@code setResponseShape}) -- the reflective
    * {@code effectiveRowPath} lookup is not even reached on this branch, since the no-shape branch
    * returns before calling it. So the load-bearing thing to prove is the invariant itself: no
    * shape, whatever the reason, must never crash and must never claim success.</p>
    */
   @Test
   void noShapeBehavesTheSameForAnyConnectorThatNeverPopulatesOne() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      WorksheetTableResponse response = only(build(query));

      assertFalse(response.isSuccess(),
         "zero columns must never become a successful build (C1), regardless of connector kind");
      String message = response.getErrorMessage();
      assertNotNull(message, "the no-shape path must produce a readable message, not a crash");
      assertFalse(message.contains("completed successfully"),
         "a connector that never reports a shape must not be told it succeeded: " + message);
      assertTrue(message.contains("credentials are valid"), message);
   }

   // ─── A3 + A6: recovered exception detail, sanitized to one line ───────────────────────────

   @Test
   void exceptionDetailReachesTheNoShapeMessage() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      query.reportException("timeout");

      String message = only(build(query)).getErrorMessage();

      assertTrue(message.contains("The connector reported: Error executing Rest query: timeout"),
         "the recovered exception detail must reach the thrown message: " + message);
      assertFalse(message.contains("\n"), "the thrown message must never contain a newline " +
         "-- rootMessage() truncates at the first one: " + message);
      assertFalse(message.contains("\r"), message);
   }

   @Test
   void exceptionDetailReachesTheShapePresentMessageToo() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      query.reportResponseShape(Map.of("data", List.of()), false);
      query.reportException("timeout");

      String message = only(build(query)).getErrorMessage();

      assertTrue(message.contains("completed successfully"), message);
      assertTrue(message.contains("The connector reported"), message);
      assertFalse(message.contains("\n"), message);
   }

   // ─── Pairwise swap (reconcile amendment 1): the two branches must actually differ ──────────

   /**
    * Two fixtures identical but for {@code getResponseShape()} nullity. Run as separate tests (as
    * the cases above are), a future reword that drops "credentials" from BOTH branches would pass
    * every substring assertion above vacuously without anyone noticing the branches had drifted
    * into agreement. This one catches exactly that: it fails unless the two messages differ AND
    * the no-shape sentinel appears in exactly one of them.
    */
   @Test
   void shapePresenceAloneFlipsTheMessageBranch() throws Exception {
      FakeNamedConnectorQuery withShape = new FakeNamedConnectorQuery();
      withShape.reportResponseShape(Map.of("data", List.of()), false);
      FakeNamedConnectorQuery withoutShape = new FakeNamedConnectorQuery();

      String withShapeMessage = only(build(withShape)).getErrorMessage();
      String withoutShapeMessage = only(build(withoutShape)).getErrorMessage();

      assertNotEquals(withShapeMessage, withoutShapeMessage,
         "shape presence alone must change the message");

      boolean shapeHasSentinel = withShapeMessage.contains("credentials are valid");
      boolean noShapeHasSentinel = withoutShapeMessage.contains("credentials are valid");
      assertTrue(noShapeHasSentinel ^ shapeHasSentinel,
         "the credentials sentinel must appear in exactly one branch's message -- shape: " +
            withShapeMessage + " | no-shape: " + withoutShapeMessage);
      assertTrue(noShapeHasSentinel, "the no-shape branch is the one that must carry it");
   }

   // ─── A7: a stale message from an earlier table in the same batch must not leak forward ────

   @Test
   void exceptionFromOneTableIsNotAttributedToTheNext() throws Exception {
      FakeNamedConnectorQuery firstTable = new FakeNamedConnectorQuery();
      firstTable.reportException("first table's own failure");

      FakeNamedConnectorQuery secondTable = new FakeNamedConnectorQuery();
      secondTable.reportResponseShape(Map.of("data", List.of()), false);

      WorksheetTablesResponse response = build(firstTable, secondTable);
      assertEquals(2, response.getTables().size());

      String secondMessage = response.getTables().get(1).getErrorMessage();
      assertFalse(secondMessage.contains("first table's own failure"),
         "table 2's message must not carry table 1's recovered exception: " + secondMessage);
      assertTrue(secondMessage.contains("completed successfully"), secondMessage);
   }

   // ─── A8: a successful build is unaffected by the new clearUserMessage() call ──────────────

   @Test
   void successfulBuildIsUnaffectedByTheClear() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      XTypeNode idColumn = XSchema.createPrimitiveType(XSchema.STRING);
      idColumn.setName("id");
      query.setOutputColumns(new XTypeNode[] { idColumn });

      WorksheetTableResponse response = only(build(query));

      assertTrue(response.isSuccess(), "columns present must still succeed: " +
         response.getErrorMessage());
      assertNull(response.getErrorMessage());
      assertNotNull(response.getColumns());
      assertEquals(1, response.getColumns().size());
   }
}
