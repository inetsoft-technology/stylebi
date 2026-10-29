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
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import inetsoft.web.WebConfig;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.BoundQuery;
import inetsoft.uql.VariableTable;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.asset.internal.TabularTableAssemblyInfo;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.uql.asset.sync.RenameDependencyInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.XAttribute;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.JDBCSelection;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.web.wiz.model.DatabaseTableMeta;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.WSCollectVariablesCommand;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.service.FakeColumnProducingCustomRestQuery;
import inetsoft.web.wiz.service.FakeColumnProducingNamedConnectorQuery;
import inetsoft.web.wiz.service.FakeCustomRestQuery;
import inetsoft.web.wiz.service.FakeNamedConnectorQuery;
import inetsoft.web.wiz.service.MetadataApiService;
import inetsoft.web.wiz.service.RawDataService;
import inetsoft.web.wiz.service.RenderNotReadyException;
import inetsoft.web.wiz.service.RuntimeHeartbeatKeepAlive;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import inetsoft.web.wiz.worksheet.model.WorksheetPropertiesModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@WizAgentTestSupport
class WorksheetAgentControllerTest {

   // ---------------------------------------------------------------------------
   // Helpers
   // ---------------------------------------------------------------------------

   private static JoinSession session(String token) {
      return new JoinSession(token, "Worksheet/ws-1", "alice~;~host-org",
                             SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                             JoinSession.ConnectionMode.PAIRED, null, null, null);
   }

   /**
    * Builds a real (non-mocked) {@link SRPrincipal} for the same logical user but a distinct
    * session -- a concrete {@link ClientInfo} (ip/session) and a distinct {@code secureId}, the
    * exact three fields {@code ClientInfo.equals()}/{@code SRPrincipal.equals()} compare (see
    * {@code SRPrincipal.java:444-465}, {@code ClientInfo.java:156-176}). Mirrors both VBM-005 P1
    * investigators' throwaway JUnit fixture technique (see
    * docs/teams/2026-09-06-bug-vbm-005/01-hypothesis-continuity.md and
    * 01-hypothesis-mismatch.md, both "Finding 4"): {@code SRPrincipal}'s Ignite-safe no-arg
    * constructor (it explicitly documents skipping the parameterized constructors specifically to
    * avoid calling {@code XSessionService.getService()}, which needs a live Spring context) plus
    * its own public {@code setUser(ClientInfo)}/{@code setOrgId(String)} setters, and reflection
    * only for {@code secureID} (private, no public setter). {@code SRPrincipal.getName()} is
    * derived from {@code client.getUserIdentity()}, so the inherited {@code XPrincipal.name}
    * field need not be set separately for identity/equality purposes.
    */
   private static SRPrincipal realSessionPrincipal(String name, String org, String ip,
                                                    String httpSession, long secureId)
      throws ReflectiveOperationException
   {
      SRPrincipal principal = new SRPrincipal();
      principal.setUser(new ClientInfo(new IdentityID(name, org), ip, httpSession));
      principal.setOrgId(org);

      Field secureIdField = SRPrincipal.class.getDeclaredField("secureID");
      secureIdField.setAccessible(true);
      secureIdField.set(principal, secureId);

      return principal;
   }

   /**
    * Reflectively sets {@link RuntimeSheet}'s protected {@code user} field (the field
    * {@code RuntimeSheet.matches(Principal)} compares via {@code Tool.equals(this.user, user)} --
    * see {@code RuntimeSheet.java:369-371}) -- there is no public setter, because in production
    * this is only ever populated by a runtime's own constructor/open path, never reassigned.
    * Lets a {@code mock(RuntimeViewsheet.class, CALLS_REAL_METHODS)} fixture's real,
    * un-stubbed {@code matches()} run against a real owning principal, instead of a mocked
    * return value.
    */
   private static void setRuntimeSheetOwner(RuntimeSheet sheet, Principal owner)
      throws ReflectiveOperationException
   {
      Field userField = RuntimeSheet.class.getDeclaredField("user");
      userField.setAccessible(true);
      userField.set(sheet, owner);
   }

   private static WorksheetAgentController controller(SheetAgentFeature feature,
                                                       SheetJoinService join,
                                                       SheetSessionService sessions,
                                                       WorksheetReadService read,
                                                       WorksheetEditService edit,
                                                       WorksheetService ws)
   {
      return controller(feature, join, sessions, read, edit, ws,
                        mock(inetsoft.uql.asset.sync.RenameTransformHandler.class));
   }

   private static WorksheetAgentController controller(SheetAgentFeature feature,
                                                       SheetJoinService join,
                                                       SheetSessionService sessions,
                                                       WorksheetReadService read,
                                                       WorksheetEditService edit,
                                                       WorksheetService ws,
                                                       inetsoft.uql.asset.sync.RenameTransformHandler renameTransformHandler)
   {
      return new WorksheetAgentController(feature, join, sessions, read, edit, ws,
                                          mock(WorksheetPreviewService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(inetsoft.uql.XRepository.class),
                                          mock(inetsoft.uql.asset.AssetRepository.class),
                                          mock(inetsoft.web.wiz.service.MetadataApiService.class),
                                          mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
                                          mock(inetsoft.web.composer.ws.LayoutGraphService.class),
                                          mock(inetsoft.web.portal.controller.database.DataSourceService.class),
                                          mock(inetsoft.sree.security.SecurityEngine.class),
                                          renameTransformHandler,
                                          mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
                                          mock(inetsoft.report.composition.execution.AssetDataCache.class),
                                          mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
                                          mock(inetsoft.web.wiz.service.RawDataService.class));
   }

   /** Like the 6-arg {@code controller}, but lets a {@code dependents} test control the
    *  {@link AssetRepository} instead of getting an unstubbed mock. */
   private static WorksheetAgentController controller(SheetAgentFeature feature,
                                                       SheetJoinService join,
                                                       SheetSessionService sessions,
                                                       WorksheetReadService read,
                                                       WorksheetEditService edit,
                                                       WorksheetService ws,
                                                       AssetRepository assetRepository)
   {
      return new WorksheetAgentController(feature, join, sessions, read, edit, ws,
                                          mock(WorksheetPreviewService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(inetsoft.uql.XRepository.class),
                                          assetRepository,
                                          mock(inetsoft.web.wiz.service.MetadataApiService.class),
                                          mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
                                          mock(inetsoft.web.composer.ws.LayoutGraphService.class),
                                          mock(inetsoft.web.portal.controller.database.DataSourceService.class),
                                          mock(inetsoft.sree.security.SecurityEngine.class),
                                          mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
                                          mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
                                          mock(inetsoft.report.composition.execution.AssetDataCache.class),
                                          mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
                                          mock(inetsoft.web.wiz.service.RawDataService.class));
   }

   /** Like the 6-arg {@code controller}, but lets a {@code refresh_data} test observe/stub the
    *  {@link inetsoft.report.composition.execution.AssetDataCache} instead of getting an
    *  unstubbed mock. */
   private static WorksheetAgentController controller(SheetAgentFeature feature,
                                                       SheetJoinService join,
                                                       SheetSessionService sessions,
                                                       WorksheetReadService read,
                                                       WorksheetEditService edit,
                                                       WorksheetService ws,
                                                       inetsoft.report.composition.execution.AssetDataCache assetDataCache)
   {
      return new WorksheetAgentController(feature, join, sessions, read, edit, ws,
                                          mock(WorksheetPreviewService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(inetsoft.uql.XRepository.class),
                                          mock(inetsoft.uql.asset.AssetRepository.class),
                                          mock(inetsoft.web.wiz.service.MetadataApiService.class),
                                          mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
                                          mock(inetsoft.web.composer.ws.LayoutGraphService.class),
                                          mock(inetsoft.web.portal.controller.database.DataSourceService.class),
                                          mock(inetsoft.sree.security.SecurityEngine.class),
                                          mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
                                          mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
                                          assetDataCache,
                                          mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
                                          mock(inetsoft.web.wiz.service.RawDataService.class));
   }

   /** Like the 6-arg {@code controller}, but lets a {@code preview} test control the
    *  {@link WorksheetPreviewService} instead of getting an unstubbed mock. */
   private static WorksheetAgentController controller(SheetAgentFeature feature,
                                                       SheetJoinService join,
                                                       SheetSessionService sessions,
                                                       WorksheetReadService read,
                                                       WorksheetEditService edit,
                                                       WorksheetService ws,
                                                       WorksheetPreviewService previewService)
   {
      return new WorksheetAgentController(feature, join, sessions, read, edit, ws,
                                          previewService,
                                          mock(SheetAgentBroadcastService.class),
                                          mock(inetsoft.uql.XRepository.class),
                                          mock(inetsoft.uql.asset.AssetRepository.class),
                                          mock(inetsoft.web.wiz.service.MetadataApiService.class),
                                          mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
                                          mock(inetsoft.web.composer.ws.LayoutGraphService.class),
                                          mock(inetsoft.web.portal.controller.database.DataSourceService.class),
                                          mock(inetsoft.sree.security.SecurityEngine.class),
                                          mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
                                          mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
                                          mock(inetsoft.report.composition.execution.AssetDataCache.class),
                                          mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
                                          mock(inetsoft.web.wiz.service.RawDataService.class));
   }

   /** Like the 6-arg {@code controller}, but lets an {@code export} (worksheet table CSV export)
    *  test control the {@link RawDataService} instead of getting an unstubbed mock. */
   private static WorksheetAgentController controller(SheetAgentFeature feature,
                                                       SheetJoinService join,
                                                       SheetSessionService sessions,
                                                       WorksheetReadService read,
                                                       WorksheetEditService edit,
                                                       WorksheetService ws,
                                                       RawDataService rawDataService)
   {
      return new WorksheetAgentController(feature, join, sessions, read, edit, ws,
                                          mock(WorksheetPreviewService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(inetsoft.uql.XRepository.class),
                                          mock(inetsoft.uql.asset.AssetRepository.class),
                                          mock(inetsoft.web.wiz.service.MetadataApiService.class),
                                          mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
                                          mock(inetsoft.web.composer.ws.LayoutGraphService.class),
                                          mock(inetsoft.web.portal.controller.database.DataSourceService.class),
                                          mock(inetsoft.sree.security.SecurityEngine.class),
                                          mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
                                          mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
                                          mock(inetsoft.report.composition.execution.AssetDataCache.class),
                                          mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
                                          rawDataService);
   }

   /** Like the 6-arg {@code controller}, but lets a {@code condition/date-ranges} test control
    *  the {@link inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy} instead of
    *  getting an unstubbed mock. */
   private static WorksheetAgentController controller(SheetAgentFeature feature,
                                                       SheetJoinService join,
                                                       SheetSessionService sessions,
                                                       WorksheetReadService read,
                                                       WorksheetEditService edit,
                                                       WorksheetService ws,
                                                       inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy dialogServiceProxy)
   {
      return new WorksheetAgentController(feature, join, sessions, read, edit, ws,
                                          mock(WorksheetPreviewService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(inetsoft.uql.XRepository.class),
                                          mock(inetsoft.uql.asset.AssetRepository.class),
                                          mock(inetsoft.web.wiz.service.MetadataApiService.class),
                                          mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
                                          mock(inetsoft.web.composer.ws.LayoutGraphService.class),
                                          mock(inetsoft.web.portal.controller.database.DataSourceService.class),
                                          mock(inetsoft.sree.security.SecurityEngine.class),
                                          mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
                                          mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
                                          mock(inetsoft.report.composition.execution.AssetDataCache.class),
                                          dialogServiceProxy,
                                          mock(inetsoft.web.wiz.service.RawDataService.class));
   }

   private static SheetAgentFeature featureOn() {
      SheetAgentFeature f = mock(SheetAgentFeature.class);
      when(f.isEnabled()).thenReturn(true);
      return f;
   }

   private static SheetAgentFeature featureOff() {
      SheetAgentFeature f = mock(SheetAgentFeature.class);
      when(f.isEnabled()).thenReturn(false);
      return f;
   }

   /**
    * Builds a controller (feature flag ON) exposing the specific collaborators a permission
    * test needs to stub/verify; every other dependency is a fresh mock.
    */
   private static WorksheetAgentController securityController(
      WorksheetEditService edit,
      DataSourceService dataSourceService,
      SecurityEngine securityEngine,
      MetadataApiService metadataApiService,
      XRepository xrepository,
      QueryManagerService queryManagerService)
   {
      return new WorksheetAgentController(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), edit, mock(WorksheetService.class),
         mock(WorksheetPreviewService.class), mock(SheetAgentBroadcastService.class),
         xrepository, mock(AssetRepository.class), metadataApiService,
         queryManagerService, mock(LayoutGraphService.class),
         dataSourceService, securityEngine,
         mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
         mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
         mock(inetsoft.report.composition.execution.AssetDataCache.class),
         mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
         mock(inetsoft.web.wiz.service.RawDataService.class));
   }

   /**
    * WSQ-006: stubs a {@code mockConstruction}-produced {@code UniformSQL} so that
    * {@code addSqlQuery}/{@code editSqlQuery}'s {@code new UniformSQL()} + real
    * {@code setSQLString}/grammar-parse machinery is bypassed entirely -- {@code getParseResult()}
    * is set directly to {@code parseResult} rather than left to a real SQL string's parse
    * behavior (which, per two contested refute rounds on this bug, is not a reliable way to
    * reach a particular branch; the lead's decision was to test via direct mocking instead).
    * {@code getSelection()} is stubbed with an empty real {@code JDBCSelection} so
    * {@code JDBCUtil.fixUniformSQLInfo}'s in-memory shortcut (which the mock's default
    * {@code getTableCount() == 0} always takes) doesn't NPE. {@code setSQLString} spawns a
    * background thread that acquires the mock's monitor and calls {@code notifyAll()} once the
    * caller's {@code synchronized(sql) { ...; sql.wait(10_000); }} block actually starts
    * waiting, so the call returns immediately instead of blocking for the real 10s timeout --
    * {@code wait()} itself is {@code final} on {@code Object} and cannot be stubbed directly.
    */
   private static void stubUniformSqlConstruction(UniformSQL mock, int parseResult) {
      when(mock.getParseResult()).thenReturn(parseResult);
      when(mock.getSelection()).thenReturn(new JDBCSelection());
      doAnswer(inv -> {
         new Thread(() -> {
            synchronized(mock) {
               mock.notifyAll();
            }
         }).start();
         return null;
      }).when(mock).setSQLString(anyString(), anyBoolean());
   }

   /** Builds an {@code add_table} EditRequest that routes to addBoundTable() (no logicalModel). */
   private static EditRequest addBoundTableRequest(String table, String datasource) {
      return new EditRequest(
         "add_table", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, datasource, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /**
    * Builds an {@code add_table} EditRequest that routes to {@code addTabularTable()} -- either
    * the named-connector shape ({@code endpoint} + optional {@code lookup}) or the generic/custom
    * shape ({@code suffix} + optional {@code customLookups}), or a deliberately-contradictory
    * combination of these with {@code logicalModel}/{@code schema}/{@code catalog} for the
    * dispatch-guard tests.
    */
   private static EditRequest addTabularTableRequest(
      String table, String datasource, String logicalModel, String schema, String catalog,
      String endpoint, Map<String, String> parameters, List<String> lookup,
      Boolean lookupExpandArrays, Boolean lookupTopLevelOnly, String suffix,
      List<WorksheetMutationSupport.CustomLookupSpec> customLookups)
   {
      return new EditRequest(
         "add_table", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, datasource, schema,
         catalog, logicalModel, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, endpoint, parameters, lookup, lookupExpandArrays,
         lookupTopLevelOnly, suffix, customLookups
      );
   }

   /**
    * Builds an {@code add_table} EditRequest carrying {@code queryParams} -- the fourth,
    * connector-agnostic form routed to {@code addQueryParamsTable()}. Built through the real app
    * {@link ObjectMapper} rather than the canonical record constructor: {@code queryParams} is
    * appended as the LAST field of a 70+ parameter positional record, and hand-counting nulls out
    * to that position is exactly the kind of silent off-by-one this sidesteps entirely -- see
    * {@link EditRequestParametersDeserializationTest} for the same technique used to prove the
    * field deserializes at all.
    */
   private static EditRequest addQueryParamsTableRequest(
      String table, String datasource, String logicalModel, String schema, String catalog,
      String endpoint, String suffix, Map<String, Object> queryParams) throws Exception
   {
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "add_table");
      if(table != null) body.put("table", table);
      if(datasource != null) body.put("datasource", datasource);
      if(logicalModel != null) body.put("logicalModel", logicalModel);
      if(schema != null) body.put("schema", schema);
      if(catalog != null) body.put("catalog", catalog);
      if(endpoint != null) body.put("endpoint", endpoint);
      if(suffix != null) body.put("suffix", suffix);
      if(queryParams != null) body.put("queryParams", queryParams);

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   /**
    * Same {@code add_table} endpoint/suffix shape as {@link #addTabularTableRequest}, but also
    * carrying {@code maxRows} -- built through the real app {@link ObjectMapper}, the same
    * technique {@link #addQueryParamsTableRequest} uses, since hand-counting nulls to a new
    * position in the 70+ field positional record risks the exact silent off-by-one that technique
    * exists to avoid.
    */
   private static EditRequest addTabularTableRequestWithMaxRows(
      String table, String datasource, String endpoint, String suffix, Integer maxRows)
      throws Exception
   {
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "add_table");
      if(table != null) body.put("table", table);
      if(datasource != null) body.put("datasource", datasource);
      if(endpoint != null) body.put("endpoint", endpoint);
      if(suffix != null) body.put("suffix", suffix);
      if(maxRows != null) body.put("maxRows", maxRows);

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   /** Same as {@link #addQueryParamsTableRequest}, but also carrying {@code maxRows}. */
   private static EditRequest addQueryParamsTableRequestWithMaxRows(
      String table, String datasource, Map<String, Object> queryParams, Integer maxRows)
      throws Exception
   {
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "add_table");
      if(table != null) body.put("table", table);
      if(datasource != null) body.put("datasource", datasource);
      if(queryParams != null) body.put("queryParams", queryParams);
      if(maxRows != null) body.put("maxRows", maxRows);

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   /**
    * Same as {@link #addQueryParamsTableRequest}, but also carrying {@code parameters}/
    * {@code lookup}/{@code customLookups} -- the three endpoint/suffix-only fields
    * {@code addQueryParamsTable} silently ignores unless {@code editOp} rejects the
    * combination up front.
    */
   private static EditRequest addQueryParamsTableRequestWithConflictingField(
      String table, String datasource, Map<String, Object> queryParams,
      Map<String, String> parameters, List<String> lookup,
      List<Map<String, Object>> customLookups) throws Exception
   {
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "add_table");
      if(table != null) body.put("table", table);
      if(datasource != null) body.put("datasource", datasource);
      if(queryParams != null) body.put("queryParams", queryParams);
      if(parameters != null) body.put("parameters", parameters);
      if(lookup != null) body.put("lookup", lookup);
      if(customLookups != null) body.put("customLookups", customLookups);

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   /**
    * Same {@code add_table} endpoint/suffix shape as {@link #addTabularTableRequest}, but also
    * carrying {@code extraProperties} (and optionally {@code parameters}, for the required-param
    * case) -- built through the real app {@link ObjectMapper}, the same technique
    * {@link #addQueryParamsTableRequest} uses, since hand-counting nulls to a new position in the
    * 70+ field positional record risks the exact silent off-by-one that technique exists to
    * avoid.
    */
   private static EditRequest addTabularTableRequestWithExtraProperties(
      String table, String datasource, String endpoint, String suffix,
      Map<String, Object> extraProperties, Map<String, String> parameters) throws Exception
   {
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "add_table");
      if(table != null) body.put("table", table);
      if(datasource != null) body.put("datasource", datasource);
      if(endpoint != null) body.put("endpoint", endpoint);
      if(suffix != null) body.put("suffix", suffix);
      if(extraProperties != null) body.put("extraProperties", extraProperties);
      if(parameters != null) body.put("parameters", parameters);

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   /**
    * Same as {@link #addQueryParamsTableRequest}, but also carrying {@code extraProperties} --
    * proves the two forms are rejected together even though queryParams itself never reads it.
    */
   private static EditRequest addQueryParamsTableRequestWithExtraProperties(
      String table, String datasource, Map<String, Object> queryParams,
      Map<String, Object> extraProperties) throws Exception
   {
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "add_table");
      if(table != null) body.put("table", table);
      if(datasource != null) body.put("datasource", datasource);
      if(queryParams != null) body.put("queryParams", queryParams);
      if(extraProperties != null) body.put("extraProperties", extraProperties);

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   /**
    * Same as {@link #addTabularTableRequestWithExtraProperties}, but also carrying {@code
    * maxRows} -- needed for the row-cap-guard-recheck regression, where the guard must accept a
    * pagination property set THROUGH {@code extraProperties} once a {@code maxRows} is supplied,
    * the same way it already accepts one set directly via {@code endpoint}/{@code suffix}.
    */
   private static EditRequest addTabularTableRequestWithExtraPropertiesAndMaxRows(
      String table, String datasource, String endpoint, String suffix,
      Map<String, Object> extraProperties, Integer maxRows) throws Exception
   {
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "add_table");
      if(table != null) body.put("table", table);
      if(datasource != null) body.put("datasource", datasource);
      if(endpoint != null) body.put("endpoint", endpoint);
      if(suffix != null) body.put("suffix", suffix);
      if(extraProperties != null) body.put("extraProperties", extraProperties);
      if(maxRows != null) body.put("maxRows", maxRows);

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   /** add_table carrying ONLY extraProperties (+datasource/table) -- no endpoint/suffix/queryParams. */
   private static EditRequest addTableRequestWithBareExtraProperties(
      String table, String datasource, Map<String, Object> extraProperties) throws Exception
   {
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "add_table");
      if(table != null) body.put("table", table);
      if(datasource != null) body.put("datasource", datasource);
      if(extraProperties != null) body.put("extraProperties", extraProperties);

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   /**
    * Builds a {@code delete_table} EditRequest -- a plainly destructive op that routes through
    * {@code editService}, so a scope guard that failed to fire would show up as a real write
    * attempt rather than an early return.
    */
   private static EditRequest deleteTableRequest(String table) {
      return new EditRequest(
         "delete_table", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /** Builds an {@code insert_column} EditRequest that routes to insertColumn(). */
   private static EditRequest insertColumnRequest(String table) {
      return new EditRequest(
         "insert_column", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /** Builds a {@code reorder_concat_subtables} EditRequest that routes to reorderConcatSubtables(). */
   private static EditRequest reorderConcatSubtablesRequest(String table, List<String> subtables) {
      return new EditRequest(
         "reorder_concat_subtables", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, subtables,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /** Builds a {@code refresh_data} EditRequest that routes to refreshData(). */
   private static EditRequest refreshDataRequest(String table) {
      return new EditRequest(
         "refresh_data", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /** Builds an {@code edit_sql_query} EditRequest that routes to editSqlQuery(). */
   private static EditRequest editSqlQueryRequest(String table, String expression) {
      return new EditRequest(
         "edit_sql_query", table, null, null, null, null, null, null, null, null,
         null, null, expression, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /**
    * Builds an {@code edit_table} EditRequest -- built through the real app {@link ObjectMapper},
    * same technique {@link #addQueryParamsTableRequest} uses, since hand-counting nulls to a new
    * position in the 70+ field positional record risks a silent off-by-one.
    */
   private static EditRequest editTableRequest(
      String table, String endpoint, Map<String, String> parameters, List<String> lookup,
      String suffix, Map<String, Object> queryParams, Map<String, Object> extraProperties,
      Integer maxRows) throws Exception
   {
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "edit_table");
      if(table != null) body.put("table", table);
      if(endpoint != null) body.put("endpoint", endpoint);
      if(parameters != null) body.put("parameters", parameters);
      if(lookup != null) body.put("lookup", lookup);
      if(suffix != null) body.put("suffix", suffix);
      if(queryParams != null) body.put("queryParams", queryParams);
      if(extraProperties != null) body.put("extraProperties", extraProperties);
      if(maxRows != null) body.put("maxRows", maxRows);

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   // ---------------------------------------------------------------------------
   // edit_table (bug #76777): in-place edit of an existing tabular table's endpoint/lookup/
   // suffix/queryParams -- previously only reachable via delete_table + add_table, which
   // discarded the assembly's identity and was refused outright if anything depended on it.
   // ---------------------------------------------------------------------------

   @Test
   void editTableRequiresTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetAgentController ctrl = securityController(mock(WorksheetEditService.class),
         mock(DataSourceService.class), mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editTableRequest(null, "Issues", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ET1", req, agent));
      assertTrue(ex.getMessage().contains("table is required"), ex.getMessage());
   }

   @Test
   void editTableRequiresAtLeastOneSourceForm() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetAgentController ctrl = securityController(mock(WorksheetEditService.class),
         mock(DataSourceService.class), mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editTableRequest("Calendars", null, null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ET2", req, agent));
      assertTrue(ex.getMessage().contains("requires one of queryParams, endpoint, or suffix"),
         ex.getMessage());
   }

   @Test
   void editTableRejectsQueryParamsTogetherWithEndpoint() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetAgentController ctrl = securityController(mock(WorksheetEditService.class),
         mock(DataSourceService.class), mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editTableRequest(
         "Calendars", "Issues", null, null, null, Map.of("foo", "bar"), null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ET3", req, agent));
      assertTrue(ex.getMessage().contains("cannot carry queryParams together with endpoint"),
         ex.getMessage());
   }

   @Test
   void editTableRejectsNonTabularAssembly() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly notTabular = new SQLBoundTableAssembly(ws, "NotTabular");
      ws.addAssembly(notTabular);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET4"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         mock(DataSourceService.class), mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editTableRequest("NotTabular", "Issues", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ET4", req, agent));
      assertTrue(ex.getMessage().contains("Not a tabular"), ex.getMessage());
   }

   @Test
   void editTableDeniedByDatasourceReadThrowsPairingException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeNamedConnectorQuery liveQuery = new FakeNamedConnectorQuery();
      liveQuery.setEndpoint("Repos");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Calendars");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET5"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editTableRequest("Calendars", "Issues", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ET5", req, agent));
      assertTrue(ex.getMessage().contains("READ permission"), ex.getMessage());
   }

   /**
    * Core regression for bug #76777: edit_table rewrites an existing TabularTableAssembly's own
    * endpoint IN PLACE -- same assembly instance, same worksheet -- instead of requiring
    * delete_table + add_table. Uses {@link FakeColumnProducingNamedConnectorQuery} (unlike
    * {@link FakeNamedConnectorQuery}, which never reports any columns) so switching "Repos" to
    * "Issues" actually produces a DIFFERENT column set, proving the live assembly's query and
    * columns really changed rather than the test merely not looking.
    */
   @Test
   void editTableRewritesEndpointInPlaceKeepingSameAssembly() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingNamedConnectorQuery liveQuery = new FakeColumnProducingNamedConnectorQuery();
      liveQuery.setColumnsByEndpoint(Map.of(
         "Repos", List.of("repoId", "repoName"),
         "Issues", List.of("issueId", "issueTitle")));
      liveQuery.setEndpoint("Repos");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Calendars");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      // Seed the assembly's initial column selection the way add_table's own live call would --
      // loadColumnSelection(addnew=true) reads whatever loadOutputColumns just produced.
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);
      assertEquals(2, liveAssembly.getColumnSelection(false).getAttributeCount(),
         "test setup sanity check: 'Repos' must seed exactly 2 columns");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET6"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(mock(TabularDataSource.class));

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = editTableRequest("Calendars", "Issues", null, null, null, null, null, null);

      assertDoesNotThrow(() -> ctrl.edit("TOK-ET6", req, agent));

      assertSame(liveAssembly, ws.getAssembly("Calendars"),
         "edit_table must rewrite the EXISTING assembly, not delete/recreate it");

      TabularQuery updatedQuery =
         ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).getQuery();
      assertEquals("Issues", ((FakeColumnProducingNamedConnectorQuery) updatedQuery).getEndpoint());

      ColumnSelection newColumns = liveAssembly.getColumnSelection(false);
      assertEquals(2, newColumns.getAttributeCount());
      assertNotNull(newColumns.getAttribute("issueId"),
         "columns must reflect the NEW endpoint's output, got: " + newColumns);
      assertNull(newColumns.getAttribute("repoId"),
         "the OLD endpoint's columns must not linger after the edit");
   }

   /**
    * Core rollback regression for bug #76777: when the new endpoint's live call returns no
    * columns, edit_table must refuse the whole edit and leave the live assembly's query/columns
    * completely UNCHANGED -- proving the scratch-clone-first pattern actually works, rather than
    * mutating the live assembly and only then discovering the new query is unusable.
    */
   @Test
   void editTableRollsBackWhenNewEndpointReturnsNoColumns() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingNamedConnectorQuery liveQuery = new FakeColumnProducingNamedConnectorQuery();
      liveQuery.setColumnsByEndpoint(Map.of("Repos", List.of("repoId", "repoName")));
      liveQuery.setEndpoint("Repos");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Calendars");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET7"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(mock(TabularDataSource.class));

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      // "Comments" is a real endpoint on FakeNamedConnectorQuery's endpoint graph (so it passes
      // assertKnownEndpoint) but has no entry in columnsByEndpoint, so the scratch query reports
      // zero columns for it.
      EditRequest req = editTableRequest("Calendars", "Comments", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ET7", req, agent));
      assertTrue(ex.getMessage().contains("returned no columns"), ex.getMessage());

      // Nothing must have been committed to the live assembly.
      assertSame(liveQuery,
         ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).getQuery(),
         "a rejected edit must not replace the live query object");
      assertEquals("Repos", liveQuery.getEndpoint(),
         "a rejected edit must not mutate the live query's own endpoint either");
      ColumnSelection unchangedColumns = liveAssembly.getColumnSelection(false);
      assertEquals(2, unchangedColumns.getAttributeCount());
      assertNotNull(unchangedColumns.getAttribute("repoId"),
         "the OLD columns must still be present after a rejected edit");
   }

   /**
    * Code-review follow-up (PR #5391, finding 2): edit_table's headline safety property is that
    * it refuses to drop a column a dependent JOIN still keys on -- the exact protection
    * delete_table + add_table never provided (delete_table refuses outright if ANYTHING depends
    * on the table; add_table can't repoint an existing join at all). Mirrors
    * WorksheetEditServiceMutatorsTest#removeColumnRefusesAJoinKeyStillUsedByADependentJoin's
    * construction, but drives edit_table through the real WorksheetAgentController/
    * WorksheetEditService pair (not the mocked applyOnRuntime shortcut the other edit_table tests
    * use) so the join is wired through the SAME production dependency-tracking path
    * WorksheetControllerService.allowsDeletion reads.
    */
   @Test
   void editTableRefusesWhenDependentJoinStillKeysOnADroppedColumn() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingNamedConnectorQuery liveQuery = new FakeColumnProducingNamedConnectorQuery();
      liveQuery.setColumnsByEndpoint(Map.of(
         "Repos", List.of("repoId", "repoName"),
         "Issues", List.of("issueId", "issueTitle")));
      liveQuery.setEndpoint("Repos");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Calendars");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);

      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "repoId");
      ws.addAssembly(right);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ET8"), any())).thenReturn(session("TOK-ET8"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      SecurityEngine joinSecurityEngine = mock(SecurityEngine.class);
      when(joinSecurityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), joinSecurityEngine, mock(InnerJoinService.class));

      // Build a REAL join depending on "Calendars"."repoId" via the production Editor path (not
      // by hand-constructing a join assembly), so the worksheet's own dependency graph is wired
      // exactly the way WorksheetControllerService.allowsDeletion expects to read it.
      editSvc.apply("TOK-ET8", agent,
         ed -> ed.addJoin("J", "Calendars", "repoId", "R", "repoId", "INNER", null, null));

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(mock(TabularDataSource.class));

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      // Switching to "Issues" drops repoId/repoName entirely -- the join still keys on repoId.
      EditRequest req = editTableRequest("Calendars", "Issues", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ET8", req, agent));
      assertTrue(ex.getMessage().contains("repoId"), ex.getMessage());

      // Nothing must have been committed to the live assembly -- the join must still resolve too.
      assertEquals("Repos", liveQuery.getEndpoint(),
         "a refused edit must not mutate the live query's own endpoint");
      assertNotNull(liveAssembly.getColumnSelection(false).getAttribute("repoId"),
         "the join key must survive the refusal");
      assertNotNull(ws.getAssembly("J"), "the join must still be there");
   }

   /**
    * Bug #78346: edit_table re-detects every column's type from the connector, so an edit that
    * leaves a joined key with a different type than the one the join was built on is refused
    * before anything is committed -- including a type the user had set explicitly.
    */
   @Test
   void editTableRefusesWhenTheRedetectedTypeBreaksADependentJoin() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingNamedConnectorQuery liveQuery = new FakeColumnProducingNamedConnectorQuery();
      liveQuery.setColumnsByEndpoint(Map.of(
         "Repos", List.of("repoId", "repoName"), "Issues", List.of("repoId", "issueTitle")));
      liveQuery.setEndpoint("Repos");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Calendars");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);

      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "R", "repoId");
      ws.addAssembly(right);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ET78346"), any())).thenReturn(session("TOK-ET78346"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);
      SecurityEngine joinSecurityEngine = mock(SecurityEngine.class);
      when(joinSecurityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), joinSecurityEngine, mock(InnerJoinService.class));

      // The connector reports string; the user had retyped both key sides to integer explicitly,
      // which is what lets the join be built on a mergeable pair.
      ((ColumnRef) liveAssembly.getColumnSelection(false).getAttribute("repoId"))
         .setDataType("integer");
      ((ColumnRef) right.getColumnSelection(false).getAttribute("repoId")).setDataType("integer");
      editSvc.apply("TOK-ET78346", agent,
         ed -> ed.addJoin("J", "Calendars", "repoId", "R", "repoId", "INNER", null, null));

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(mock(TabularDataSource.class));
      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = editTableRequest("Calendars", "Issues", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ET78346", req, agent));
      assertTrue(ex.getMessage().contains("join \"J\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("integer to string"), ex.getMessage());
      assertTrue(ex.getMessage().contains("re-detected"), ex.getMessage());

      assertEquals("Repos", liveQuery.getEndpoint(),
         "a refused edit must not mutate the live query's own endpoint");
      assertEquals("integer",
         liveAssembly.getColumnSelection(false).getAttribute("repoId").getDataType());
      assertNotNull(ws.getAssembly("J"));
   }

   /**
    * Code-review follow-up (PR #5391, finding 2): a success test for the suffix form -- only the
    * endpoint form had one. Uses FakeColumnProducingCustomRestQuery (a generic/custom REST-JSON
    * connector with no endpoint property, so editTabularTable dispatches to the suffix branch).
    */
   @Test
   void editTableRewritesSuffixInPlaceKeepingSameAssembly() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingCustomRestQuery liveQuery = new FakeColumnProducingCustomRestQuery();
      liveQuery.setColumnsBySuffix(Map.of(
         "/widgets", List.of("widgetId"),
         "/gadgets", List.of("gadgetId", "gadgetName")));
      liveQuery.setSuffix("/widgets");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Widgets");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET9"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(mock(TabularDataSource.class));

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = editTableRequest("Widgets", null, null, null, "/gadgets", null, null, null);

      assertDoesNotThrow(() -> ctrl.edit("TOK-ET9", req, agent));

      assertSame(liveAssembly, ws.getAssembly("Widgets"));
      TabularQuery updatedQuery = ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).getQuery();
      assertEquals("/gadgets", ((FakeColumnProducingCustomRestQuery) updatedQuery).getSuffix());
      ColumnSelection newColumns = liveAssembly.getColumnSelection(false);
      assertEquals(2, newColumns.getAttributeCount());
      assertNotNull(newColumns.getAttribute("gadgetId"));
      assertNull(newColumns.getAttribute("widgetId"),
         "the OLD suffix's columns must not linger after the edit");
   }

   /**
    * Code-review follow-up (PR #5391, finding 2): a success test for the queryParams form -- only
    * the endpoint form had one, mirroring how addQueryParamsTable's own tests reuse
    * FakeNamedConnectorQuery's "endpoint" property THROUGH the generic queryParams map rather
    * than the dedicated endpoint field (see addQueryParamsTableEditResponseDisclosesResolvedNameOnCollision).
    */
   @Test
   void editTableRewritesQueryParamsInPlaceKeepingSameAssembly() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingNamedConnectorQuery liveQuery = new FakeColumnProducingNamedConnectorQuery();
      liveQuery.setColumnsByEndpoint(Map.of(
         "Repos", List.of("repoId"),
         "Issues", List.of("issueId", "issueTitle")));
      liveQuery.setEndpoint("Repos");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      when(ds.getType()).thenReturn("FakeColumnProducingNamedConnector");
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Products");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET10"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      TabularDataSource<?> xds = mock(TabularDataSource.class);
      when(xds.getType()).thenReturn("FakeColumnProducingNamedConnector");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(xds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = editTableRequest(
         "Products", null, null, null, null, Map.of("endpoint", "Issues"), null, null);

      // TabularSchemaExtractor.extract (reached by the queryParams branch) needs Config.getConfig(),
      // a Spring-bean-backed singleton -- same stub addQueryParamsTableEditResponseDisclosesResolvedNameOnCollision uses.
      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try {
         assertDoesNotThrow(() -> ctrl.edit("TOK-ET10", req, agent));
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }

      assertSame(liveAssembly, ws.getAssembly("Products"));
      ColumnSelection newColumns = liveAssembly.getColumnSelection(false);
      assertNotNull(newColumns.getAttribute("issueId"));
      assertNull(newColumns.getAttribute("repoId"));
   }

   /**
    * Code-review follow-up (PR #5391, finding 2): proves `lookup` is actually threaded into
    * TabularEndpointBindingSupport.applyLookupChain by edit_table, not just accepted and dropped
    * -- mirrors addTabularTableThreadsParametersIntoTheSharedHelper's "read the property back"
    * verification style for `parameters`.
    */
   @Test
   void editTableAppliesLookupChain() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingNamedConnectorQuery liveQuery = new FakeColumnProducingNamedConnectorQuery();
      liveQuery.setColumnsByEndpoint(Map.of("Repos", List.of("repoId")));
      liveQuery.setEndpoint("Repos");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Calendars");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET11"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(mock(TabularDataSource.class));

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      // "Repos" -> "Issues" is a real lookup edge in FakeNamedConnectorQuery's ENDPOINT_MAP.
      EditRequest req = editTableRequest(
         "Calendars", "Repos", Map.of("id", "42"), List.of("Issues"), null, null, null, null);

      assertDoesNotThrow(() -> ctrl.edit("TOK-ET11", req, agent));

      TabularQuery updatedQuery = ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).getQuery();
      assertEquals("Issues", ((FakeColumnProducingNamedConnectorQuery) updatedQuery).getLookupEndpoint0(),
         "edit_table must actually apply the lookup chain, not just accept and drop it");
   }

   /**
    * edit_table restates the whole query, so omitting lookupExpandArrays / lookupTopLevelOnly
    * resets them to the connector default (true) instead of keeping the live query's earlier
    * false -- the same default add_table gives a fresh query.
    */
   @Test
   void editTableOmittedLookupFlagsResetToConnectorDefault() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingNamedConnectorQuery liveQuery = new FakeColumnProducingNamedConnectorQuery();
      liveQuery.setColumnsByEndpoint(Map.of("Repos", List.of("repoId")));
      liveQuery.setEndpoint("Repos");
      liveQuery.setLookupExpanded(false);
      liveQuery.setLookupTopLevelOnly(false);
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Calendars");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET11b"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(mock(TabularDataSource.class));

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = editTableRequest(
         "Calendars", "Repos", Map.of("id", "42"), List.of("Issues"), null, null, null, null);

      assertDoesNotThrow(() -> ctrl.edit("TOK-ET11b", req, agent));

      FakeColumnProducingNamedConnectorQuery updatedQuery = (FakeColumnProducingNamedConnectorQuery)
         ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).getQuery();
      assertTrue(updatedQuery.isLookupExpanded(),
         "an omitted lookupExpandArrays must reset to the connector default, not keep false");
      assertTrue(updatedQuery.isLookupTopLevelOnly(),
         "an omitted lookupTopLevelOnly must reset to the connector default, not keep false");
   }

   /**
    * Code-review follow-up (PR #5391, finding 2): the customLookups analogue of
    * {@link #editTableAppliesLookupChain}, for the suffix form.
    */
   @Test
   void editTableAppliesCustomLookupChain() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingCustomRestQuery liveQuery = new FakeColumnProducingCustomRestQuery();
      liveQuery.setColumnsBySuffix(Map.of("/widgets", List.of("widgetId")));
      liveQuery.setSuffix("/widgets");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Widgets");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET12"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(mock(TabularDataSource.class));

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      // customLookups is only settable through the ObjectMapper JSON route (see
      // addTabularTableRequest's own customLookups param) -- reuse that shape here too.
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "edit_table");
      body.put("table", "Widgets");
      body.put("suffix", "/widgets");
      body.put("customLookups", List.of(Map.of(
         "url", "/widgets/{param1}/parts", "jsonPath", "$.parts", "key", "id")));
      ObjectMapper mapper = new WebConfig().objectMapper();
      EditRequest req = mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);

      assertDoesNotThrow(() -> ctrl.edit("TOK-ET12", req, agent));

      TabularQuery updatedQuery = ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).getQuery();
      assertEquals("/widgets/{param1}/parts",
         ((FakeColumnProducingCustomRestQuery) updatedQuery).getLookupUrl0(),
         "edit_table must actually apply customLookups, not just accept and drop it");
   }

   /**
    * Code-review follow-up (PR #5391, finding B2): {@code EndpointJsonQuery.setEndpoint} only
    * clears a previously-set lookup chain when the endpoint value actually CHANGES, so an edit
    * that keeps the same endpoint but supplies a SHORTER chain must not leave the dropped
    * trailing level active -- otherwise a table edited from a 2-level "Join With" chain down to
    * 1 level would silently keep querying the old, deeper lookup. Two sequential edits against
    * the SAME live assembly: the first establishes a 2-level chain, the second (same endpoint)
    * supplies only 1 level.
    */
   @Test
   void editTableShortensLookupChainClearingTrailingLevel() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingNamedConnectorQuery liveQuery = new FakeColumnProducingNamedConnectorQuery();
      liveQuery.setColumnsByEndpoint(Map.of("Repos", List.of("repoId")));
      liveQuery.setEndpoint("Repos");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      // Two edit_table calls against the SAME live assembly (unlike this file's other edit_table
      // tests, which only ever edit once): XQuery.clone() deep-clones datasource, so the first
      // edit's commit hands the SECOND edit a query whose datasource is whatever ds.clone()
      // returns -- an unstubbed Mockito mock method returns null, which would otherwise fail the
      // second edit's own "has no bound datasource" guard.
      when(ds.clone()).thenReturn(ds);
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Calendars");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(any(), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(mock(TabularDataSource.class));

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      // First edit: "Repos" -> "Issues" -> "Comments" is a real 2-level chain in
      // FakeNamedConnectorQuery's ENDPOINT_MAP. "id" is Repos' own required parameter
      // (PARAM_MAP/REQUIRED_PARAM_NAMES).
      EditRequest first = editTableRequest("Calendars", "Repos", Map.of("id", "42"),
         List.of("Issues", "Comments"), null, null, null, null);
      assertDoesNotThrow(() -> ctrl.edit("TOK-ET13a", first, agent));

      TabularQuery afterFirst = ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).getQuery();
      assertEquals("Issues", ((FakeColumnProducingNamedConnectorQuery) afterFirst).getLookupEndpoint0());
      assertEquals("Comments", ((FakeColumnProducingNamedConnectorQuery) afterFirst).getLookupEndpoint1());

      // Second edit: same endpoint, chain shortened to 1 level -- the dropped level 1 must clear.
      EditRequest second = editTableRequest("Calendars", "Repos", Map.of("id", "42"),
         List.of("Issues"), null, null, null, null);
      assertDoesNotThrow(() -> ctrl.edit("TOK-ET13b", second, agent));

      TabularQuery afterSecond = ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).getQuery();
      assertEquals("Issues", ((FakeColumnProducingNamedConnectorQuery) afterSecond).getLookupEndpoint0(),
         "the kept level must survive");
      assertNull(((FakeColumnProducingNamedConnectorQuery) afterSecond).getLookupEndpoint1(),
         "the dropped trailing level must not linger from the prior, longer chain");
   }

   /**
    * Code-review follow-up (PR #5391, finding B2): unlike the named-connector form, nothing
    * resets a custom lookup chain when {@code suffix} changes -- a table edited from
    * {@code /widgets} (with a customLookups chain keyed on widget ids) to {@code /gadgets}
    * must not keep querying the old, {@code /widgets}-shaped lookup URL.
    */
   @Test
   void editTableSuffixChangeClearsStaleCustomLookupChain() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingCustomRestQuery liveQuery = new FakeColumnProducingCustomRestQuery();
      liveQuery.setColumnsBySuffix(Map.of(
         "/widgets", List.of("widgetId"),
         "/gadgets", List.of("gadgetId")));
      liveQuery.setSuffix("/widgets");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      // See editTableShortensLookupChainClearingTrailingLevel's comment: two edit_table calls
      // against the same live assembly need datasource to survive XQuery.clone().
      when(ds.clone()).thenReturn(ds);
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Widgets");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(any(), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(mock(TabularDataSource.class));

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      // First edit: establish a customLookups chain on /widgets.
      Map<String, Object> firstBody = new java.util.LinkedHashMap<>();
      firstBody.put("op", "edit_table");
      firstBody.put("table", "Widgets");
      firstBody.put("suffix", "/widgets");
      firstBody.put("customLookups", List.of(Map.of(
         "url", "/widgets/{param1}/parts", "jsonPath", "$.parts", "key", "id")));
      ObjectMapper mapper = new WebConfig().objectMapper();
      EditRequest first = mapper.readValue(mapper.writeValueAsString(firstBody), EditRequest.class);
      assertDoesNotThrow(() -> ctrl.edit("TOK-ET14a", first, agent));

      TabularQuery afterFirst = ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).getQuery();
      assertEquals("/widgets/{param1}/parts",
         ((FakeColumnProducingCustomRestQuery) afterFirst).getLookupUrl0());

      // Second edit: suffix changes to /gadgets, customLookups omitted -- the old chain must clear.
      EditRequest second = editTableRequest(
         "Widgets", null, null, null, "/gadgets", null, null, null);
      assertDoesNotThrow(() -> ctrl.edit("TOK-ET14b", second, agent));

      TabularQuery afterSecond = ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).getQuery();
      assertEquals("/gadgets", ((FakeColumnProducingCustomRestQuery) afterSecond).getSuffix());
      assertNull(((FakeColumnProducingCustomRestQuery) afterSecond).getLookupUrl0(),
         "the OLD suffix's customLookups chain must not survive a suffix change");
   }

   /**
    * Code-review follow-up (PR #5391, finding B1): the scratch assembly used to compute the new
    * column set is brand new, so {@code TabularTableAssembly.loadColumnSelection}'s alias/
    * expression-column preservation logic -- which reads the ASSEMBLY's own prior selection, not
    * the query's -- saw nothing to preserve and dropped a prior {@code rename_column} alias and
    * {@code add_expression_column} column when the new columns were committed. Sets up both
    * directly (the same mutation {@code WorksheetEditService.Editor#renameColumn} and {@code
    * WorksheetMutationSupport#addExpressionColumn} perform), then edits the table to a different
    * endpoint and asserts both survive alongside the new endpoint's own column.
    */
   @Test
   void editTablePreservesColumnAliasAndExpressionColumnAcrossAnEdit() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeColumnProducingNamedConnectorQuery liveQuery = new FakeColumnProducingNamedConnectorQuery();
      liveQuery.setColumnsByEndpoint(Map.of(
         "Repos", List.of("repoId", "repoName"),
         "Issues", List.of("repoId", "repoName", "issueId")));
      liveQuery.setEndpoint("Repos");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("MyDatasource");
      liveQuery.setDataSource(ds);

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Repos");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);
      liveAssembly.loadColumnSelection(new VariableTable(), true, null);

      // Simulate a prior rename_column("repoName" -> "projectName") ...
      ColumnRef repoNameRef = (ColumnRef) liveAssembly.getColumnSelection(false).getAttribute("repoName");
      repoNameRef.setAlias("projectName");

      // ... and a prior add_expression_column("fullName", ...).
      WorksheetMutationSupport.addExpressionColumn(
         liveAssembly, "fullName", "field['repoId']", "string", false);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET16"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(mock(TabularDataSource.class));

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = editTableRequest("Repos", "Issues", null, null, null, null, null, null);
      assertDoesNotThrow(() -> ctrl.edit("TOK-ET16", req, agent));

      ColumnSelection newColumns = liveAssembly.getColumnSelection(false);
      ColumnRef newRepoName = (ColumnRef) newColumns.getAttribute("repoName");
      assertNotNull(newRepoName, "the renamed column must still be present under its raw name");
      assertEquals("projectName", newRepoName.getAlias(),
         "the prior rename_column alias must survive edit_table");
      assertNotNull(newColumns.getAttribute("fullName"),
         "the prior add_expression_column column must survive edit_table");
      assertNotNull(newColumns.getAttribute("issueId"),
         "the new endpoint's own column must still be present");
   }

   /**
    * Code-review follow-up (PR #5391, finding 2): the "Table has no query"/"no bound datasource"
    * branches editTabularTable itself guards on had no direct test.
    */
   @Test
   void editTableThrowsWhenTableHasNoQuery() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Calendars");
      // Deliberately no setQuery() call -- info.getQuery() stays null.
      ws.addAssembly(liveAssembly);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET13"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         mock(DataSourceService.class), mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editTableRequest("Calendars", "Issues", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ET13", req, agent));
      assertTrue(ex.getMessage().contains("has no query"), ex.getMessage());
   }

   @Test
   void editTableThrowsWhenTableHasNoBoundDatasource() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      FakeNamedConnectorQuery liveQuery = new FakeNamedConnectorQuery();
      liveQuery.setEndpoint("Repos");
      // Deliberately no setDataSource() call -- getDataSource() stays null.

      Worksheet ws = new Worksheet();
      TabularTableAssembly liveAssembly = new TabularTableAssembly(ws, "Calendars");
      ((TabularTableAssemblyInfo) liveAssembly.getTableInfo()).setQuery(liveQuery);
      ws.addAssembly(liveAssembly);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ET14"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         mock(DataSourceService.class), mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editTableRequest("Calendars", "Issues", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ET14", req, agent));
      assertTrue(ex.getMessage().contains("no bound datasource"), ex.getMessage());
   }

   // ---------------------------------------------------------------------------
   // join
   // ---------------------------------------------------------------------------

   @Test
   void joinReturnsSessionToken() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-1");

      SheetJoinService joinSvc = mock(SheetJoinService.class);
      when(joinSvc.join(eq("CODE"), eq(agent)))
         .thenReturn(new SheetJoinService.JoinOutcome(s, "Sales Analysis"));

      WorksheetAgentController ctrl = controller(featureOn(), joinSvc,
         mock(SheetSessionService.class), mock(WorksheetReadService.class),
         mock(WorksheetEditService.class), mock(WorksheetService.class));

      WorksheetAgentController.JoinResponse resp = ctrl.join(new WorksheetAgentController.JoinRequest("CODE"), agent);

      assertEquals("TOK-1", resp.sessionToken());
      assertEquals("Worksheet/ws-1", resp.runtimeId());
      assertEquals("alice~;~host-org", resp.ownerIdentity());
      assertEquals("Sales Analysis", resp.sheetLabel());
   }

   /**
    * The join response must carry the session's editorContext through -- otherwise a dropped
    * context doesn't surface as an error, it reads as an ordinary whole-sheet session,
    * indistinguishable from a legitimate toolbar mint, on the exact route an agent reads.
    */
   @Test
   void joinReturnsTheSessionsEditorContext() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      EditorContext ctx = new EditorContext("worksheetExpression", "T", "Calc1", null);
      JoinSession s = new JoinSession("TOK-EC", "Worksheet/ws-1", "alice~;~host-org",
         SheetType.WORKSHEET, 0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED,
         null, null, ctx);

      SheetJoinService joinSvc = mock(SheetJoinService.class);
      when(joinSvc.join(eq("CODE"), eq(agent)))
         .thenReturn(new SheetJoinService.JoinOutcome(s, null));

      WorksheetAgentController ctrl = controller(featureOn(), joinSvc,
         mock(SheetSessionService.class), mock(WorksheetReadService.class),
         mock(WorksheetEditService.class), mock(WorksheetService.class));

      WorksheetAgentController.JoinResponse resp = ctrl.join(new WorksheetAgentController.JoinRequest("CODE"), agent);

      assertEquals(ctx, resp.editorContext());
   }

   /**
    * PSM-001 Change B: the service-layer concurrentSessionCount must reach the actual HTTP-facing
    * JoinResponse DTO, not just JoinOutcome -- this is the controller-level test §7 of the design
    * calls for, since a unit test on SheetJoinService alone would not catch a field dropped while
    * building the response.
    */
   @Test
   void joinResponseCarriesTheConcurrentSessionCount() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-2");

      SheetJoinService joinSvc = mock(SheetJoinService.class);
      when(joinSvc.join(eq("CODE"), eq(agent)))
         .thenReturn(new SheetJoinService.JoinOutcome(s, "Sales Analysis", 3));

      WorksheetAgentController ctrl = controller(featureOn(), joinSvc,
         mock(SheetSessionService.class), mock(WorksheetReadService.class),
         mock(WorksheetEditService.class), mock(WorksheetService.class));

      WorksheetAgentController.JoinResponse resp = ctrl.join(new WorksheetAgentController.JoinRequest("CODE"), agent);

      assertEquals(3, resp.concurrentSessionCount());
   }

   @Test
   void joinRejectsFlagOff() {
      WorksheetAgentController ctrl = controller(featureOff(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.join(new WorksheetAgentController.JoinRequest("CODE"), TestPrincipals.user("alice", "host-org")));
      assertEquals(403, ex.getStatusCode().value());
   }

   // ---------------------------------------------------------------------------
   // assetExists (#76818)
   // ---------------------------------------------------------------------------

   /**
    * Regression for Bug #76818: {@code add_table}'s blank-embedded-table form had no way to
    * check whether a bare name it was about to create already named a saved asset, so a
    * collision was silently shadowed rather than refused. This endpoint answers that question,
    * session-independently, via {@link WorksheetService#isDuplicatedEntry}.
    */
   @Test
   void assetExistsReturnsTrueWhenAssetAlreadyExists() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetService worksheetService = mock(WorksheetService.class);
      when(worksheetService.isDuplicatedEntry(any(), any())).thenReturn(true);

      AssetRepository repository = mock(AssetRepository.class);
      when(worksheetService.getAssetRepository()).thenReturn(repository);
      when(repository.containsEntry(argThat(e -> e.getType() == AssetEntry.Type.WORKSHEET)))
         .thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class), worksheetService);

      AssetExistsResponse resp = ctrl.assetExists("CollisionProbe76818", null, null, agent);

      assertTrue(resp.exists());
   }

   @Test
   void assetExistsReturnsFalseForANovelName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetService worksheetService = mock(WorksheetService.class);
      when(worksheetService.isDuplicatedEntry(any(), any())).thenReturn(false);

      AssetRepository repository = mock(AssetRepository.class);
      when(worksheetService.getAssetRepository()).thenReturn(repository);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class), worksheetService);

      AssetExistsResponse resp = ctrl.assetExists("BrandNewTable", null, null, agent);

      assertFalse(resp.exists());
   }

   /**
    * Bug #76956 (p1): a FOLDER-only match (no worksheet by that name) must be reported as
    * {@code folder:true, worksheet:false} instead of collapsing into the same {@code exists}
    * boolean a real worksheet collision would set.
    */
   @Test
   void assetExistsReportsFolderOnlyMatchSeparately() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetService worksheetService = mock(WorksheetService.class);
      when(worksheetService.isDuplicatedEntry(any(), any())).thenReturn(true);

      AssetRepository repository = mock(AssetRepository.class);
      when(worksheetService.getAssetRepository()).thenReturn(repository);
      when(repository.containsEntry(argThat(e -> e.getType() == AssetEntry.Type.FOLDER)))
         .thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class), worksheetService);

      AssetExistsResponse resp = ctrl.assetExists("FolderOnly76956", null, null, agent);

      assertTrue(resp.exists());
      assertTrue(resp.folder());
      assertFalse(resp.worksheet());
   }

   /** Bug #76956 (p1): a real WORKSHEET match is reported as {@code worksheet:true, folder:false}. */
   @Test
   void assetExistsReportsWorksheetMatchSeparately() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetService worksheetService = mock(WorksheetService.class);
      when(worksheetService.isDuplicatedEntry(any(), any())).thenReturn(true);

      AssetRepository repository = mock(AssetRepository.class);
      when(worksheetService.getAssetRepository()).thenReturn(repository);
      when(repository.containsEntry(argThat(e -> e.getType() == AssetEntry.Type.WORKSHEET)))
         .thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class), worksheetService);

      AssetExistsResponse resp = ctrl.assetExists("WorksheetOnly76956", null, null, agent);

      assertTrue(resp.exists());
      assertTrue(resp.worksheet());
      assertFalse(resp.folder());
   }

   /** Bug #76956 (p1): no match at all reports all three fields false. */
   @Test
   void assetExistsReportsNeitherWhenNothingMatches() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetService worksheetService = mock(WorksheetService.class);
      when(worksheetService.isDuplicatedEntry(any(), any())).thenReturn(false);

      AssetRepository repository = mock(AssetRepository.class);
      when(worksheetService.getAssetRepository()).thenReturn(repository);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class), worksheetService);

      AssetExistsResponse resp = ctrl.assetExists("NovelName76956", null, null, agent);

      assertFalse(resp.exists());
      assertFalse(resp.worksheet());
      assertFalse(resp.folder());
   }

   /** Defaults: {@code type} -> WORKSHEET, {@code scope} -> GLOBAL_SCOPE (no owner). */
   @Test
   void assetExistsChecksWorksheetTypeAndGlobalScopeByDefault() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetService worksheetService = mock(WorksheetService.class);
      when(worksheetService.isDuplicatedEntry(any(), any())).thenReturn(false);

      AssetRepository repository = mock(AssetRepository.class);
      when(worksheetService.getAssetRepository()).thenReturn(repository);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class), worksheetService);

      ctrl.assetExists("BrandNewTable", null, null, agent);

      ArgumentCaptor<AssetEntry> captor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(worksheetService).isDuplicatedEntry(any(), captor.capture());
      AssetEntry entry = captor.getValue();
      assertEquals(AssetEntry.Type.WORKSHEET, entry.getType());
      assertEquals("BrandNewTable", entry.getPath());
      assertEquals(AssetRepository.GLOBAL_SCOPE, entry.getScope());
      assertNull(entry.getUser());
   }

   @Test
   void assetExistsHonorsUserScope() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetService worksheetService = mock(WorksheetService.class);
      when(worksheetService.isDuplicatedEntry(any(), any())).thenReturn(false);

      AssetRepository repository = mock(AssetRepository.class);
      when(worksheetService.getAssetRepository()).thenReturn(repository);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class), worksheetService);

      ctrl.assetExists("MyPrivateTable", null, "user", agent);

      ArgumentCaptor<AssetEntry> captor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(worksheetService).isDuplicatedEntry(any(), captor.capture());
      AssetEntry entry = captor.getValue();
      assertEquals(AssetRepository.USER_SCOPE, entry.getScope());
      assertEquals(new IdentityID("alice", "host-org"), entry.getUser());
   }

   @Test
   void assetExistsHonorsExplicitType() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetService worksheetService = mock(WorksheetService.class);
      when(worksheetService.isDuplicatedEntry(any(), any())).thenReturn(false);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class), worksheetService);

      AssetExistsResponse resp = ctrl.assetExists("Some Viewsheet", "viewsheet", null, agent);

      ArgumentCaptor<AssetEntry> captor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(worksheetService).isDuplicatedEntry(any(), captor.capture());
      assertEquals(AssetEntry.Type.VIEWSHEET, captor.getValue().getType());
      // A non-WORKSHEET/FOLDER type never has a worksheet/folder counterpart to check.
      assertFalse(resp.worksheet());
      assertFalse(resp.folder());
   }

   @Test
   void assetExistsRejectsBlankPath() {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.assetExists("   ", null, null, agent));
      assertTrue(ex.getMessage().contains("path"), ex.getMessage());
   }

   @Test
   void assetExistsRejectsUnknownType() {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.assetExists("Foo", "NOT_A_TYPE", null, agent));
      assertTrue(ex.getMessage().contains("NOT_A_TYPE"), ex.getMessage());
   }

   @Test
   void assetExistsRejectsFlagOff() {
      WorksheetAgentController ctrl = controller(featureOff(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.assetExists("Foo", null, null, TestPrincipals.user("alice", "host-org")));
      assertEquals(403, ex.getStatusCode().value());
   }

   // ---------------------------------------------------------------------------
   // read
   // ---------------------------------------------------------------------------

   @Test
   void readReturnsModel() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      WorksheetReadService readSvc = new WorksheetReadService();

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         readSvc, editSvc, mock(WorksheetService.class));

      WorksheetModel model = ctrl.read("TOK", agent);

      assertNotNull(model);
      assertFalse(model.tables().isEmpty());
      assertEquals("T", model.tables().get(0).name());
   }

   // ---------------------------------------------------------------------------
   // preview
   // ---------------------------------------------------------------------------

   /**
    * Bug #76574 (VTB-002): {@code offset} must be forwarded to
    * {@link WorksheetPreviewService#preview}, and defaults to 0 when the caller omits it
    * (the {@code @RequestParam(defaultValue = "0")} on the controller method, exercised here
    * by simply not passing an offset argument at the call site, mirroring how Spring itself
    * would bind an absent query parameter).
    */
   @Test
   void previewForwardsOffsetDefaultingToZero() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      WorksheetPreviewService previewSvc = mock(WorksheetPreviewService.class);
      WorksheetPreviewService.PreviewResult expected =
         new WorksheetPreviewService.PreviewResult(List.of(Map.of("x", "r1")), List.of());
      when(previewSvc.preview(eq(rws), eq("T"), eq(0), eq(50))).thenReturn(expected);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class),
         previewSvc);

      WorksheetPreviewService.PreviewResult result = ctrl.preview("TOK", "T", 0, 50, agent);

      assertEquals(expected, result);
      verify(previewSvc).preview(rws, "T", 0, 50);
   }

   @Test
   void previewForwardsNonZeroOffset() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      WorksheetPreviewService previewSvc = mock(WorksheetPreviewService.class);
      WorksheetPreviewService.PreviewResult expected =
         new WorksheetPreviewService.PreviewResult(List.of(Map.of("x", "r201")), List.of());
      when(previewSvc.preview(eq(rws), eq("T"), eq(200), eq(50))).thenReturn(expected);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class),
         previewSvc);

      WorksheetPreviewService.PreviewResult result = ctrl.preview("TOK", "T", 200, 50, agent);

      assertEquals(expected, result);
      verify(previewSvc).preview(rws, "T", 200, 50);
   }

   /**
    * WBS-059 part 2 (live-JVM-debugger-confirmed): {@code preview_worksheet_data} must warn when
    * the previewed table's own query references a variable that IS declared but has no explicit
    * session value yet -- because {@code JDBCHandler.execute}'s call into
    * {@code XUtil.validateConditions}/{@code removeNoParamConditions} silently drops the whole
    * condition using that variable ({@code usql.setWhere(null)}), not merely leaves it
    * unfiltered. Before this fix, {@code preview}'s response had no signal of this at all
    * ({@code warnings: []}), even though the rows returned were NOT filtered by that condition.
    */
   @Test
   void previewWarnsWhenReferencedVariableHasNoSessionValueYet() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      DefaultVariableAssembly declaredVar = new DefaultVariableAssembly(ws, "declaredVar");
      ws.addAssembly(declaredVar);

      TableAssembly table = mock(TableAssembly.class);
      when(table.getName()).thenReturn("T");
      when(table.getAssemblyType()).thenReturn(AbstractSheet.TABLE_ASSET);
      when(table.isVisible()).thenReturn(true);
      when(table.getAllVariables())
         .thenReturn(new UserVariable[]{new UserVariable("declaredVar")});
      ws.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      WorksheetPreviewService previewSvc = mock(WorksheetPreviewService.class);
      WorksheetPreviewService.PreviewResult unfiltered =
         new WorksheetPreviewService.PreviewResult(
            List.of(Map.of("x", "unfiltered-row")), List.of());
      when(previewSvc.preview(eq(rws), eq("T"), eq(0), eq(50))).thenReturn(unfiltered);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class),
         previewSvc);

      VariableAssemblyModelInfo needsValue = new VariableAssemblyModelInfo();
      needsValue.setName("declaredVar");
      WSCollectVariablesCommand command = WSCollectVariablesCommand.builder()
         .varInfos(List.of(needsValue))
         .build();

      try(MockedStatic<WorksheetEventUtil> eventUtil =
             mockStatic(WorksheetEventUtil.class, CALLS_REAL_METHODS)) {
         eventUtil.when(() -> WorksheetEventUtil.refreshVariables(eq(rws), any(), eq(false)))
            .thenReturn(command);

         WorksheetPreviewService.PreviewResult result = ctrl.preview("TOK", "T", 0, 50, agent);

         assertEquals(unfiltered.rows(), result.rows());
         assertEquals(1, result.warnings().size());
         assertTrue(result.warnings().get(0).contains("declaredVar"),
            "warning must name the specific variable: " + result.warnings());
      }
   }

   @Test
   void previewKeepsColumnLimitWhenDroppedConditionWarningsAreAppended() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      DefaultVariableAssembly declaredVar = new DefaultVariableAssembly(ws, "declaredVar");
      ws.addAssembly(declaredVar);

      TableAssembly table = mock(TableAssembly.class);
      when(table.getName()).thenReturn("T");
      when(table.getAssemblyType()).thenReturn(AbstractSheet.TABLE_ASSET);
      when(table.isVisible()).thenReturn(true);
      when(table.getAllVariables())
         .thenReturn(new UserVariable[]{new UserVariable("declaredVar")});
      ws.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      WorksheetPreviewService previewSvc = mock(WorksheetPreviewService.class);
      WorksheetPreviewService.PreviewResult unfiltered =
         new WorksheetPreviewService.PreviewResult(
            List.of(Map.of("x", "unfiltered-row")), List.of("limited"),
            new WorksheetPreviewService.ColumnLimit(true, 200, 250));
      when(previewSvc.preview(eq(rws), eq("T"), eq(0), eq(50))).thenReturn(unfiltered);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class),
         previewSvc);

      VariableAssemblyModelInfo needsValue = new VariableAssemblyModelInfo();
      needsValue.setName("declaredVar");
      WSCollectVariablesCommand command = WSCollectVariablesCommand.builder()
         .varInfos(List.of(needsValue))
         .build();

      try(MockedStatic<WorksheetEventUtil> eventUtil =
             mockStatic(WorksheetEventUtil.class, CALLS_REAL_METHODS)) {
         eventUtil.when(() -> WorksheetEventUtil.refreshVariables(eq(rws), any(), eq(false)))
            .thenReturn(command);

         WorksheetPreviewService.PreviewResult result = ctrl.preview("TOK", "T", 0, 50, agent);

         assertEquals(unfiltered.rows(), result.rows());
         assertEquals(2, result.warnings().size());
         assertEquals(unfiltered.columnLimit(), result.columnLimit());
         assertTrue(result.warnings().get(1).contains("declaredVar"),
            "warning must name the specific variable: " + result.warnings());
      }
   }

   /**
    * Positive-case counterpart: once {@code set_variable_values} has given "declaredVar" a
    * session value, {@code WSCollectVariablesCommand.varInfos()} no longer lists it (the whole
    * point of {@code AssetQuerySandbox.getAllVariables}'s "still needs a value" filter) -- so no
    * dropped-condition warning should be added.
    */
   @Test
   void previewDoesNotWarnWhenVariableAlreadyHasSessionValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      DefaultVariableAssembly declaredVar = new DefaultVariableAssembly(ws, "declaredVar");
      ws.addAssembly(declaredVar);

      TableAssembly table = mock(TableAssembly.class);
      when(table.getName()).thenReturn("T");
      when(table.getAssemblyType()).thenReturn(AbstractSheet.TABLE_ASSET);
      when(table.isVisible()).thenReturn(true);
      when(table.getAllVariables())
         .thenReturn(new UserVariable[]{new UserVariable("declaredVar")});
      ws.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      WorksheetPreviewService previewSvc = mock(WorksheetPreviewService.class);
      WorksheetPreviewService.PreviewResult filtered =
         new WorksheetPreviewService.PreviewResult(
            List.of(Map.of("x", "filtered-row")), List.of());
      when(previewSvc.preview(eq(rws), eq("T"), eq(0), eq(50))).thenReturn(filtered);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class),
         previewSvc);

      // No pending "needs a value" entries at all -- refreshVariables returns null exactly like
      // WorksheetEventUtil.refreshVariables does once every referenced variable already has an
      // explicit session value (WorksheetEventUtil.java:150-153).
      try(MockedStatic<WorksheetEventUtil> eventUtil =
             mockStatic(WorksheetEventUtil.class, CALLS_REAL_METHODS)) {
         eventUtil.when(() -> WorksheetEventUtil.refreshVariables(eq(rws), any(), eq(false)))
            .thenReturn(null);

         WorksheetPreviewService.PreviewResult result = ctrl.preview("TOK", "T", 0, 50, agent);

         assertEquals(filtered, result);
      }
   }

   /**
    * A variable can be "declared but no session value yet" for a DIFFERENT table in the same
    * worksheet without affecting this preview -- the warning must only fire for variables the
    * PREVIEWED table itself references, not every such variable anywhere in the worksheet.
    */
   @Test
   void previewDoesNotWarnAboutAnUnrelatedTablesVariable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      DefaultVariableAssembly declaredVar = new DefaultVariableAssembly(ws, "otherTableVar");
      ws.addAssembly(declaredVar);

      TableAssembly table = mock(TableAssembly.class);
      when(table.getName()).thenReturn("T");
      when(table.getAssemblyType()).thenReturn(AbstractSheet.TABLE_ASSET);
      when(table.isVisible()).thenReturn(true);
      when(table.getAllVariables()).thenReturn(new UserVariable[0]);
      ws.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      WorksheetPreviewService previewSvc = mock(WorksheetPreviewService.class);
      WorksheetPreviewService.PreviewResult unrelated =
         new WorksheetPreviewService.PreviewResult(
            List.of(Map.of("x", "row")), List.of());
      when(previewSvc.preview(eq(rws), eq("T"), eq(0), eq(50))).thenReturn(unrelated);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class),
         previewSvc);

      VariableAssemblyModelInfo needsValue = new VariableAssemblyModelInfo();
      needsValue.setName("otherTableVar");
      WSCollectVariablesCommand command = WSCollectVariablesCommand.builder()
         .varInfos(List.of(needsValue))
         .build();

      try(MockedStatic<WorksheetEventUtil> eventUtil =
             mockStatic(WorksheetEventUtil.class, CALLS_REAL_METHODS)) {
         eventUtil.when(() -> WorksheetEventUtil.refreshVariables(eq(rws), any(), eq(false)))
            .thenReturn(command);

         WorksheetPreviewService.PreviewResult result = ctrl.preview("TOK", "T", 0, 50, agent);

         assertEquals(unrelated, result);
      }
   }

   // ---------------------------------------------------------------------------
   // export (WBS-057) -- exports a worksheet table's CURRENT LIVE data (including any unsaved
   // edits made in this session) as a downloadable CSV file, streamed directly to a mocked
   // HttpServletResponse. Mirrors ViewsheetAssemblyAgentControllerTest's own
   // capturingOutputStream/mockServletResponse helpers for the same direct-servlet-write pattern.
   // ---------------------------------------------------------------------------

   /** A ServletOutputStream backed by a plain ByteArrayOutputStream so tests can capture what
    *  the controller writes -- mirrors ViewsheetAssemblyAgentControllerTest's own helper of the
    *  same shape, for the same direct-servlet-write pattern. */
   private static ServletOutputStream capturingOutputStream(ByteArrayOutputStream sink) {
      return new ServletOutputStream() {
         @Override
         public boolean isReady() {
            return true;
         }

         @Override
         public void setWriteListener(WriteListener writeListener) {
         }

         @Override
         public void write(int b) {
            sink.write(b);
         }
      };
   }

   private static HttpServletResponse mockServletResponse(ByteArrayOutputStream written) throws Exception {
      HttpServletResponse response = mock(HttpServletResponse.class);
      when(response.getOutputStream()).thenReturn(capturingOutputStream(written));
      return response;
   }

   @Test
   void exportTableStreamsLiveWorksheetTableCsvToTheResponse() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      RawDataService rawDataService = mock(RawDataService.class);
      byte[] fakeCsv = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);
      doAnswer(invocation -> {
         OutputStream out = invocation.getArgument(2);
         out.write(fakeCsv);
         return null;
      }).when(rawDataService).writeLiveWorksheetTableCsvStream(eq(rws), eq("T"), any());

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class),
         rawDataService);

      ByteArrayOutputStream written = new ByteArrayOutputStream();
      HttpServletResponse servletResponse = mockServletResponse(written);

      ctrl.exportTable("TOK", "T", agent, servletResponse);

      verify(servletResponse).setContentType("text/csv");
      verify(servletResponse).setHeader(eq("Content-Disposition"), contains("T.csv"));
      verify(rawDataService).writeLiveWorksheetTableCsvStream(eq(rws), eq("T"), any());
      assertArrayEquals(fakeCsv, written.toByteArray());
   }

   /**
    * A table name absent from the live worksheet must fail loudly (the same
    * {@code RawDataService} error, naming the table) rather than silently produce an empty or
    * wrong file -- mirroring {@code preview_worksheet_data}'s own "Table not found" failure shape.
    */
   @Test
   void exportTableFailsLoudlyWhenTableNotFound() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      RawDataService rawDataService = mock(RawDataService.class);
      doThrow(new RuntimeException("Table missing not found."))
         .when(rawDataService).writeLiveWorksheetTableCsvStream(eq(rws), eq("missing"), any());

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class),
         rawDataService);

      HttpServletResponse servletResponse = mockServletResponse(new ByteArrayOutputStream());

      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> ctrl.exportTable("TOK", "missing", agent, servletResponse));
      assertTrue(ex.getMessage().contains("missing"));
   }

   /** Redmine 78271: the export failure must reach the client as a body naming the cause. */
   @Test
   void exportTableReportsTheFailureCauseAsAResponseStatusException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      RawDataService rawDataService = mock(RawDataService.class);
      doThrow(new RuntimeException("Failed to export worksheet table 'T': undefinedFn is not defined"))
         .when(rawDataService).writeLiveWorksheetTableCsvStream(eq(rws), eq("T"), any());

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class),
         rawDataService);

      HttpServletResponse servletResponse = mockServletResponse(new ByteArrayOutputStream());

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.exportTable("TOK", "T", agent, servletResponse));
      assertTrue(ex.getReason().contains("undefinedFn is not defined"));
      verify(servletResponse).reset();
   }

   /**
    * Bug #78972: a CHECKED exception from the raw data service (e.g. the raw
    * {@link ExecutionException} a join/concatenation's first sub-query future used to let
    * escape uncaught before Bug #78972's own {@code JoinQuery}/{@code ConcatenatedQuery} fix)
    * must not become a bare, body-less HTTP 500 -- {@code catch(RuntimeException e)} here used
    * to miss it entirely, since {@link ExecutionException} is not a {@link RuntimeException}.
    */
   @Test
   void exportTableReportsACheckedExceptionAsAResponseStatusException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      RawDataService rawDataService = mock(RawDataService.class);
      doThrow(new ExecutionException("sub-query failed", new IllegalStateException("root cause")))
         .when(rawDataService).writeLiveWorksheetTableCsvStream(eq(rws), eq("T"), any());

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class),
         rawDataService);

      HttpServletResponse servletResponse = mockServletResponse(new ByteArrayOutputStream());

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.exportTable("TOK", "T", agent, servletResponse));
      assertTrue(ex.getReason().contains("sub-query failed"));
      verify(servletResponse).reset();
   }

   // ---------------------------------------------------------------------------
   // dependents
   // ---------------------------------------------------------------------------

   @Test
   void dependentsRejectsFlagOff() {
      WorksheetAgentController ctrl = controller(featureOff(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.dependents("TOK-DEP-OFF", TestPrincipals.user("alice", "host-org")));
      assertEquals(403, ex.getStatusCode().value());
   }

   /** An unsaved worksheet can have no dependents -- nothing can point at an asset that was
    *  never saved, so this must not attempt the repository lookup at all. */
   @Test
   void dependentsReportsUnsavedWorksheetAsHavingNone() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry tempEntry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
         AssetEntry.Type.WORKSHEET, "__TEMPORARY__/ws-1", null);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(tempEntry);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK-DEP-TEMP"), eq(agent))).thenReturn(rws);

      AssetRepository repo = mock(AssetRepository.class);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class), repo);

      Map<String, Object> result = ctrl.dependents("TOK-DEP-TEMP", agent);

      assertEquals(Boolean.FALSE, result.get("saved"));
      assertEquals(List.of(), result.get("dependents"));
      verifyNoInteractions(repo);
   }

   @Test
   void dependentsReportsNoneForASavedWorksheetNothingDependsOn() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK-DEP-NONE"), eq(agent))).thenReturn(rws);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      Map<String, Object> result;

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(entry.toIdentifier()))
            .thenReturn(List.of());

         result = ctrl.dependents("TOK-DEP-NONE", agent);
      }

      assertEquals(Boolean.TRUE, result.get("saved"));
      assertEquals("Orders WS", result.get("path"));
      assertEquals(List.of(), result.get("dependents"));
      assertTrue(result.get("summary").toString().contains("No saved asset"));
   }

   /** The actual "who uses this worksheet" case: a saved viewsheet depends on it, and that must
    *  be named by path and type rather than left for the caller to take on faith. */
   @Test
   void dependentsListsDependentAssetsByPathAndType() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK-DEP-SOME"), eq(agent))).thenReturn(rws);

      AssetEntry dependentVs = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "Sales Dashboard", null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      Map<String, Object> result;

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(entry.toIdentifier()))
            .thenReturn(List.of(dependentVs));

         result = ctrl.dependents("TOK-DEP-SOME", agent);
      }

      assertEquals(Boolean.TRUE, result.get("saved"));
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> dependents = (List<Map<String, Object>>) result.get("dependents");
      assertEquals(1, dependents.size());
      assertEquals("Sales Dashboard", dependents.get(0).get("path"));
      assertEquals("viewsheet", dependents.get(0).get("type"));
      assertTrue(result.get("summary").toString().contains("Sales Dashboard"));
   }

   // ---------------------------------------------------------------------------
   // condition/date-ranges — bug #76571
   // ---------------------------------------------------------------------------

   @Test
   void conditionDateRangesRejectsFlagOff() {
      WorksheetAgentController ctrl = controller(featureOff(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.conditionDateRanges("TOK-DR-OFF", TestPrincipals.user("alice", "host-org")));
      assertEquals(403, ex.getStatusCode().value());
   }

   /**
    * Bug #76571: a worksheet-only session must be able to list date ranges without being routed
    * to the viewsheet-domain endpoint. Resolves the session to a runtimeId via
    * {@link WorksheetEditService#resolveWithSession}, then forwards to
    * {@link inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy#getDateRanges}
    * -- the same service the interactive Composer condition dialog uses -- and returns its result
    * verbatim, so both built-in {@code DateCondition} names and any custom
    * {@code DateRangeAssembly} defined on the worksheet come through unchanged.
    */
   @Test
   void conditionDateRangesResolvesSessionAndForwardsBuiltinAndCustomRanges() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolveWithSession(eq("TOK-DR"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "RUNTIME-DR-1"));

      BrowseDataModel expected = BrowseDataModel.builder()
         .values(new Object[] { "today", "MyCustomDateRange" })
         .labels(new Object[] { "Today", "MyCustomDateRange" })
         .build();

      inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy dialogServiceProxy =
         mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class);
      when(dialogServiceProxy.getDateRanges(eq("RUNTIME-DR-1"), eq(agent))).thenReturn(expected);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class),
         dialogServiceProxy);

      Object result = ctrl.conditionDateRanges("TOK-DR", agent);

      assertSame(expected, result);
      verify(dialogServiceProxy).getDateRanges("RUNTIME-DR-1", agent);
   }

   // ---------------------------------------------------------------------------
   // properties — read
   // ---------------------------------------------------------------------------

   /**
    * The read counterpart of the properties POST. Without it an agent could set a worksheet's
    * alias or description but never read it back, and /model carries neither field.
    */
   @Test
   void getPropertiesReturnsTheSheetsOwnProperties() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      ws.getWorksheetInfo().setAlias("Quarterly revenue");
      ws.getWorksheetInfo().setDescription("Set by the agent");

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "Folder/ws-1", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getEntry()).thenReturn(entry);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         new WorksheetReadService(), editSvc, mock(WorksheetService.class));

      WorksheetPropertiesModel props = ctrl.getProperties("TOK", agent);

      assertEquals("ws-1", props.name());
      assertEquals("Quarterly revenue", props.alias());
      assertEquals("Set by the agent", props.description());
      assertTrue(props.dataSource(),
         "AssetEntry.isReportDataSource() reads true unless the property is explicitly \"false\", "
            + "so an untouched entry is a data source -- the same value the Composer dialog shows");
   }

   /**
    * Pinned to the exact 403 {@code requireEnabled()} throws, the way {@code joinRejectsFlagOff}
    * is. A bare {@code assertThrows(Exception.class, ...)} would not test the flag at all: with
    * both collaborators mocked, dropping {@code requireEnabled()} leaves the endpoint throwing
    * anyway -- {@code requireWholeSheetSession} passes an unresolvable token straight through by
    * design, so {@code editService.resolve} returns {@code null} and {@code readProperties}
    * NPEs. Asserting the status is what tells those two outcomes apart.
    */
   @Test
   void getPropertiesIsRefusedWhenTheFeatureIsOff() {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetAgentController ctrl = controller(featureOff(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         new WorksheetReadService(), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.getProperties("TOK", agent));
      assertEquals(403, ex.getStatusCode().value());
   }

   // ---------------------------------------------------------------------------
   // edit — dispatch
   // ---------------------------------------------------------------------------

   @Test
   void editDispatchesRemoveColumn() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-E");
      when(sessions.resolve(eq("TOK-E"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      EditRequest req = new EditRequest("remove_column", "T", "x",
         null, null, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null,
         null, null,
         null, null, null, null, null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-E", req, agent);

      assertNull(t.getColumnSelection(false).getAttribute("x"),
                 "column 'x' should have been removed");
      assertNotNull(t.getColumnSelection(false).getAttribute("y"),
                    "column 'y' should still be present");
   }

   /**
    * Confirms {@code crosstab} on a {@code set_group_aggregate} {@link EditRequest} reaches
    * {@link AggregateInfo#isCrosstab} through the controller's dispatch switch, not just at the
    * {@code WorksheetEditService.Editor} layer already covered by
    * {@code WorksheetEditServiceMutatorsTest#setGroupAggregateCrosstabTogglesAggregateInfo}.
    */
   @Test
   void editDispatchesSetGroupAggregateWithCrosstab() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cust", "store", "amount");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-XTAB");
      when(sessions.resolve(eq("TOK-XTAB"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      List<WorksheetMutationSupport.GroupSpec> groups = List.of(
         new WorksheetMutationSupport.GroupSpec("cust", null),
         new WorksheetMutationSupport.GroupSpec("store", null));
      List<WorksheetMutationSupport.AggregateSpec> aggregates = List.of(
         new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null));

      EditRequest req = new EditRequest(
         "set_group_aggregate", // op
         "T",                   // table
         null,                  // column
         null,                  // name
         null,                  // type
         null,                  // newName
         null,                  // field
         null,                  // operation
         null,                  // values
         null,                  // direction
         groups,                // groups
         aggregates,            // aggregates
         null,                  // expression
         false,                 // sql
         null,                  // leftTable
         null,                  // leftKey
         null,                  // rightTable
         null,                  // rightKey
         null,                  // joinType
         null,                  // visible
         null,                  // tables
         null,                  // source
         null,                  // concatType
         null,                  // conditions
         null,                  // ranking
         null,                  // headerColumns
         null,                  // dateOption
         null,                  // boundaries
         null,                  // datasource
         null,                  // schema
         null,                  // catalog
         null,                  // logicalModel
         null,                  // leftKeys
         null,                  // rightKeys
         null,                  // row
         null,                  // col
         null,                  // value
         null,                  // index
         null,                  // alias
         null,                  // description
         null,                  // maxRows
         null,                  // distinct
         null,                  // columnOrder
         null,                  // groupMappings
         null,                  // groupOthers
         null,                  // variableValues
         null,                  // x
         null,                  // y
         null,                  // label
         null,                  // defaultValue
         null,                  // mode
         null,                  // insert
         null,                  // subtables
         null,                  // sourceTable
         null,                  // attribute
         null,                  // endpoint
         null,                  // parameters
         null,                  // lookup
         null,                  // lookupExpandArrays
         null,                  // lookupTopLevelOnly
         null,                  // suffix
         null,                  // customLookups
         true,                  // crosstab
         null,                  // labels
         null,                  // choices
         null,                  // joinPaths
         null,                  // mergeable
         null,                  // visibleInViewsheet
         null,                  // confirmed
         null,                  // rowCount
         null                   // concatDistinct
      );

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-XTAB", req, agent);

      assertTrue(t.getAggregateInfo().isCrosstab(),
                 "crosstab on the EditRequest should reach AggregateInfo through the controller");
   }

   /**
    * Bug #76626: no {@code mcp__composer-chat__*} tool (nor, until now, the agent-bridge
    * itself) could read or write a worksheet table's MV incremental-refresh condition fields.
    * Confirms {@code set_mv_conditions} reaches {@link WorksheetEditService.Editor#setMVConditions}
    * through the controller's dispatch switch, and that all five MV fields land on the real
    * {@link inetsoft.uql.asset.TableAssembly} getters/setters
    * ({@code TableAssembly.java:181-232}) the human Composer's own MV Condition pane
    * ({@code MVConditionPaneModel}/{@code AssemblyConditionDialogService}) already writes for a
    * human user -- this op is the agent-bridge's own path to the same fields, built directly
    * against {@code TableAssembly}, not through that dialog service.
    */
   @Test
   void editDispatchesSetMVConditions() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-MV"), any())).thenReturn(session("TOK-MV"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      List<WorksheetMutationSupport.ConditionNode> updatePre = List.of(
         new WorksheetMutationSupport.ConditionNode(
            new WorksheetMutationSupport.ConditionSpec(
               "a", "=", List.of("1"), false, null), null, 0));
      List<WorksheetMutationSupport.ConditionNode> updatePost = List.of(
         new WorksheetMutationSupport.ConditionNode(
            new WorksheetMutationSupport.ConditionSpec(
               "a", "=", List.of("2"), false, null), null, 0));
      List<WorksheetMutationSupport.ConditionNode> deletePre = List.of(
         new WorksheetMutationSupport.ConditionNode(
            new WorksheetMutationSupport.ConditionSpec(
               "a", "=", List.of("3"), false, null), null, 0));
      List<WorksheetMutationSupport.ConditionNode> deletePost = List.of(
         new WorksheetMutationSupport.ConditionNode(
            new WorksheetMutationSupport.ConditionSpec(
               "a", "=", List.of("4"), false, null), null, 0));

      EditRequest req = new EditRequest(
         "set_mv_conditions",   // op
         "T",                   // table
         null, null, null, null, null, null, null, null, // column..direction
         null, null,             // groups, aggregates
         null, false,            // expression, sql
         null, null, null, null, null, // leftTable..joinType
         null, null, null, null, // visible, tables, source, concatType
         null, null,             // conditions, ranking
         null, null, null,       // headerColumns, dateOption, boundaries
         null, null, null, null, // datasource, schema, catalog, logicalModel
         null, null,             // leftKeys, rightKeys
         null, null, null, null, // row, col, value, index
         null, null, null, null, // alias, description, maxRows, distinct
         null, null, null,       // columnOrder, groupMappings, groupOthers
         null, null, null,       // variableValues, x, y
         null, null, null, null, null, // label, defaultValue, mode, insert, subtables
         null, null, null, null, // sourceTable, attribute, endpoint, parameters
         null, null, null, null, // lookup, lookupExpandArrays, lookupTopLevelOnly, suffix
         null, null, null, null, null, // customLookups, crosstab, labels, choices, joinPaths
         null, null, null, null, null, // mergeable, visibleInViewsheet, confirmed, rowCount, concatDistinct
         null, null, null,       // rankings, queryParams, extraProperties
         updatePre, updatePost, deletePre, deletePost, true // the five MV fields under test
      );

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-MV", req, agent);

      assertNotNull(t.getMVUpdatePreConditionList());
      assertNotNull(t.getMVUpdatePostConditionList());
      assertNotNull(t.getMVDeletePreConditionList());
      assertNotNull(t.getMVDeletePostConditionList());
      assertTrue(t.isMVForceAppendUpdates());
      assertEquals(1, t.getMVUpdatePreConditionList().getConditionSize());
      assertEquals(1, t.getMVUpdatePostConditionList().getConditionSize());
      assertEquals(1, t.getMVDeletePreConditionList().getConditionSize());
      assertEquals(1, t.getMVDeletePostConditionList().getConditionSize());
   }

   /**
    * Bug #78790 item 2: {@code edit_unpivot}'s {@code confirmed} reaches
    * {@link WorksheetEditService.Editor#editUnpivot(String, int, boolean)} through the
    * dispatch switch, and an absent {@code confirmed} reaches it as false.
    */
   @Test
   void editDispatchesEditUnpivotConfirmed() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(eq("TOK-UP"), any())).thenReturn(session("TOK-UP"));
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      WorksheetEditService.Editor editor = mock(WorksheetEditService.Editor.class);
      doAnswer(inv -> {
         inv.<ThrowingConsumer<WorksheetEditService.Editor>>getArgument(2).accept(editor);
         return null;
      }).when(editSvc).apply(eq("TOK-UP"), any(), any());

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         sessions, mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));
      ObjectMapper mapper = new WebConfig().objectMapper();

      ctrl.edit("TOK-UP", mapper.readValue(
         "{\"op\":\"edit_unpivot\",\"table\":\"UP\",\"headerColumns\":1,\"confirmed\":true}",
         EditRequest.class), agent);
      verify(editor).editUnpivot("UP", 1, true);

      ctrl.edit("TOK-UP", mapper.readValue(
         "{\"op\":\"edit_unpivot\",\"table\":\"UP\",\"headerColumns\":1}",
         EditRequest.class), agent);
      verify(editor).editUnpivot("UP", 1, false);
   }

   /**
    * The snapshot guard for {@code insert_column}, pinned here rather than in
    * {@code WorksheetEditServiceMutatorsTest} because this op is the one that does not live in
    * the {@code Editor}: it manipulates {@link inetsoft.uql.util.XEmbeddedTable} directly from
    * the controller. That is exactly why it was missed when the sibling column ops were first
    * swept, so it is the guard most likely to be dropped again -- and the only one no test
    * covered.
    */
   @Test
   void editInsertColumnRefusesSnapshotEmbeddedTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly t =
         TestWorksheets.snapshotTableWithColumns(ws, "S", "a", "b");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-IC"), any())).thenReturn(session("TOK-IC"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-IC", insertColumnRequest("S"), agent));

      assertTrue(ex.getMessage().startsWith("insert_column"), ex.getMessage());
      assertTrue(ex.getMessage().contains("EMBEDDED_SNAPSHOT"), ex.getMessage());
      assertTrue(ex.getMessage().contains("add_expression_column"),
         "the refusal must name the column kind that does work here: " + ex.getMessage());
      assertEquals(2, t.getColumnSelection(false).getAttributeCount(),
         "nothing may be added to the selection when the data cannot follow");
   }

   /**
    * Bug #76350 follow-on (item A): {@code refresh_data} on an explicit table called
    * {@code refreshColumnSelection} (the call that actually executes a crosstab/grouped table's
    * query, via {@code AssetQuerySandbox}'s internal {@code query.getTableLens}) with no bound at
    * all -- a table whose query hadn't run yet in this runtime (e.g. PWA-005's cross-join-grouped
    * crosstab) blocked this request for however long that query took. Mirrors
    * {@code ViewsheetEditServiceTest#resizeThrowsRenderNotReadyWhenTableDataIsSlowAndDoesNotDelegate}.
    */
   @Test
   void refreshDataThrowsRenderNotReadyWhenColumnSelectionIsSlow() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      TableAssembly crosstab = TestWorksheets.withGroupSumAndSort(
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "Crosstab1", "cust", "amount"),
         "cust", "amount");
      // refreshData()'s new checkValidity() call needs a real-looking source -- nonEmbeddedTableWithColumns
      // leaves SourceInfo unset, which used to be invisible here because neither
      // refreshColumnSelection nor loadTableData's own internal checkValidity() (which swallows the
      // failure) ever surfaced it.
      ((BoundTableAssembly) crosstab).setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      ws.addAssembly(crosstab);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      doAnswer(invocation -> {
         Thread.sleep(3_000);
         return null;
      }).when(box).refreshColumnSelection(eq("Crosstab1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD"), any())).thenReturn(session("TOK-RD"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // clearBoundQueryCache (bug #76711 round 2) reaches AssetQuery.createAssetQuery, which
      // needs live Spring beans (XRepository/XSessionManager) this lightweight test context
      // doesn't wire -- mock it away to a no-op default (null, not a BoundQuery) since this test
      // isn't about that mechanism.
      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         assertThrows(RenderNotReadyException.class,
            () -> ctrl.edit("TOK-RD", refreshDataRequest("Crosstab1"), agent));
      }
   }

   /** A table whose data is already warm (or warms within the bound) refreshes normally. */
   @Test
   void refreshDataSucceedsWhenColumnSelectionIsFast() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      TableAssembly crosstab = TestWorksheets.withGroupSumAndSort(
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "Crosstab1", "cust", "amount"),
         "cust", "amount");
      // See the sibling "Slow" test above for why this is needed now.
      ((BoundTableAssembly) crosstab).setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      ws.addAssembly(crosstab);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(new VariableTable());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD2"), any())).thenReturn(session("TOK-RD2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // See the sibling "Slow" test above for why this is needed now.
      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         ctrl.edit("TOK-RD2", refreshDataRequest("Crosstab1"), agent);
      }

      verify(box, atLeastOnce()).refreshColumnSelection(eq("Crosstab1"), anyBoolean());
   }

   // ---------------------------------------------------------------------------
   // refresh_data -- WSQueryService.runQuery (the UI's own "Run Query" action this tool mirrors)
   // clears the query cache, checks validity, removes the row cap, and discovers tabular columns --
   // none of which the agent path used to do.
   // ---------------------------------------------------------------------------

   /** #7: a single-table refresh must clear the cached query result, not just resetTableLens. */
   @Test
   void refreshDataClearsTheAssetDataCacheEntry() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      ws.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(new VariableTable());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD3"), any())).thenReturn(session("TOK-RD3"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      inetsoft.report.composition.execution.AssetDataCache assetDataCache =
         mock(inetsoft.report.composition.execution.AssetDataCache.class);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class), assetDataCache);

      // See refreshDataThrowsRenderNotReadyWhenColumnSelectionIsSlow for why this is needed now.
      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         ctrl.edit("TOK-RD3", refreshDataRequest("Table1"), agent);
      }

      // Bug #76711 round 4 also clears the RUNTIME_MODE slot for this DESIGN_MODE table (see
      // refreshDataAlsoInvalidatesTheRuntimeModeSlotForADesignModeTable), so this is now 2 calls.
      verify(assetDataCache, atLeastOnce()).remove(any());
   }

   /** Bug 76873: refreshing a table must also drop the cached results of its dependents. */
   @Test
   void refreshDataInvalidatesTransitiveDependentsOfTheRefreshedTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      ws.addAssembly(table);
      MirrorTableAssembly dependent = new MirrorTableAssembly(ws, "Dep1", table);
      ws.addAssembly(dependent);
      MirrorTableAssembly nested = new MirrorTableAssembly(ws, "Dep2", dependent);
      ws.addAssembly(nested);
      // Reachable through both Dep1 and Dep2, so the walk must visit it once.
      TableAssemblyOperator union = new TableAssemblyOperator();
      TableAssemblyOperator.Operator unionOp = new TableAssemblyOperator.Operator();
      unionOp.setOperation(TableAssemblyOperator.UNION);
      unionOp.setLeftTable("Dep1");
      unionOp.setRightTable("Dep2");
      union.addOperator(unionOp);
      ws.addAssembly(new ConcatenatedTableAssembly(ws, "Dep3",
         new TableAssembly[] { dependent, nested }, new TableAssemblyOperator[] { union }));

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(new VariableTable());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD76873"), any())).thenReturn(session("TOK-RD76873"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      inetsoft.report.composition.execution.AssetDataCache assetDataCache =
         mock(inetsoft.report.composition.execution.AssetDataCache.class);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class), assetDataCache);

      java.util.Map<String, inetsoft.report.composition.execution.DataKey> keys =
         new java.util.HashMap<>();

      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class);
          MockedStatic<inetsoft.report.composition.execution.AssetDataCache> cache =
             mockStatic(inetsoft.report.composition.execution.AssetDataCache.class))
      {
         cache.when(() -> inetsoft.report.composition.execution.AssetDataCache.getCacheKey(
               any(TableAssembly.class), any(), any(), anyInt(), anyBoolean()))
            .thenAnswer(inv -> keys.computeIfAbsent(
               ((TableAssembly) inv.getArgument(0)).getName() + "/" + inv.getArgument(3),
               k -> mock(inetsoft.report.composition.execution.DataKey.class)));
         ctrl.edit("TOK-RD76873", refreshDataRequest("Table1"), agent);
      }

      for(String dep : new String[] { "Dep1", "Dep2", "Dep3" }) {
         verify(box, times(1)).resetTableLens(dep);
         verify(assetDataCache).remove(
            keys.get(dep + "/" + WorksheetEventUtil.getMode((TableAssembly) ws.getAssembly(dep))));
         verify(assetDataCache).remove(
            keys.get(dep + "/" + AssetQuerySandbox.RUNTIME_MODE));
      }
   }

   /**
    * Bug #76711 round 4: for an ordinary table (not runtime, not live-data -- the state an
    * {@code add_table}'d table is left in, per {@code WorksheetEventUtil.getMode}), refreshData's
    * own computed {@code mode} is DESIGN_MODE, but every real reader of this table's data
    * (preview_worksheet_data's WorksheetPreviewService, RawDataService, WorksheetTableService)
    * always reads under a hardcoded RUNTIME_MODE. Round 3's fix only invalidated the DESIGN_MODE
    * slot in AssetQuerySandbox.tmap/AssetDataCache, leaving RUNTIME_MODE's slot stale forever.
    * This test would FAIL against round 3's code, which calls resetTableLens/assetDataCache.remove
    * exactly once, for DESIGN_MODE only.
    */
   @Test
   void refreshDataAlsoInvalidatesTheRuntimeModeSlotForADesignModeTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      // Deliberately not runtime, not live-data: WorksheetEventUtil.getMode(table) returns
      // DESIGN_MODE here, matching an ordinary add_table'd table.
      ws.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(new VariableTable());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD10"), any())).thenReturn(session("TOK-RD10"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      inetsoft.report.composition.execution.AssetDataCache assetDataCache =
         mock(inetsoft.report.composition.execution.AssetDataCache.class);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class), assetDataCache);

      // See refreshDataThrowsRenderNotReadyWhenColumnSelectionIsSlow for why this is needed now.
      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         ctrl.edit("TOK-RD10", refreshDataRequest("Table1"), agent);
      }

      verify(box).resetTableLens("Table1", AssetQuerySandbox.DESIGN_MODE);
      verify(box).resetTableLens("Table1", AssetQuerySandbox.RUNTIME_MODE);
      verify(assetDataCache, times(2)).remove(any());
   }

   /**
    * Bug #76711 round 5: XSessionManager.dataCache's key includes the query's own
    * maxrows/timeout, so a reload executed WITH the row-cap hint present writes into a
    * different cache entry than the one RUNTIME_MODE readers (preview_worksheet_data, etc.)
    * consult -- they always execute with the hint removed. The row-cap hint used to only be
    * removed when mode == RUNTIME_MODE (mirroring WSQueryService.runQuery), but an ordinary
    * add_table'd table's own mode is DESIGN_MODE, so that guard never fired and the hint stayed
    * set through the whole reload. This test would FAIL against round 4's code, which leaves
    * the hint in place for a DESIGN_MODE table.
    */
   @Test
   void refreshDataRemovesRowCapEvenInDesignMode() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      // Deliberately not runtime, not live-data: WorksheetEventUtil.getMode(table) returns
      // DESIGN_MODE here, matching an ordinary add_table'd table.
      ws.addAssembly(table);

      VariableTable vars = new VariableTable();
      vars.put(inetsoft.uql.XQuery.HINT_MAX_ROWS, "100");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(vars);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD11"), any())).thenReturn(session("TOK-RD11"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // See refreshDataThrowsRenderNotReadyWhenColumnSelectionIsSlow for why this is needed now.
      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         ctrl.edit("TOK-RD11", refreshDataRequest("Table1"), agent);
      }

      assertNull(vars.get(inetsoft.uql.XQuery.HINT_MAX_ROWS),
         "the row-cap hint must be removed even for a DESIGN_MODE table, not only RUNTIME_MODE");
   }

   /**
    * Bug #76711 round 6: resetTableLens/AssetDataCache.remove only clear the RUNTIME_MODE slot's
    * stale entry -- they never themselves run a query, so the RUNTIME_MODE-shaped
    * XSessionManager.dataCache entry any real RUNTIME_MODE reader consults is only ever
    * repopulated by whichever caller happens to read under RUNTIME_MODE next. refresh_data itself
    * never does, so a table that's never independently read under RUNTIME_MODE stays stuck
    * serving pre-refresh data forever. This test would FAIL against round 5's code, which never
    * calls getTableLens(table, RUNTIME_MODE) at all.
    */
   @Test
   void refreshDataForcesARuntimeModeExecutionForADesignModeTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      // Deliberately not runtime, not live-data: WorksheetEventUtil.getMode(table) returns
      // DESIGN_MODE here, matching an ordinary add_table'd table.
      ws.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(new VariableTable());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD12"), any())).thenReturn(session("TOK-RD12"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // See refreshDataThrowsRenderNotReadyWhenColumnSelectionIsSlow for why this is needed now.
      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         ctrl.edit("TOK-RD12", refreshDataRequest("Table1"), agent);
      }

      verify(box).getTableLens("Table1", AssetQuerySandbox.RUNTIME_MODE);
   }

   // ---------------------------------------------------------------------------
   // Bug #79074: refreshData's single-assembly branch must also keep the runtime's heartbeat
   // alive while its (potentially long-running, background-continuing) reload is in flight.
   // ---------------------------------------------------------------------------
   //
   // RuntimeHeartbeatKeepAlive.INTERVAL_MS is shrunk for the duration of these tests so the
   // periodic refresh can be observed without waiting out the real (minutes-long) production
   // interval.

   /**
    * Happy path: a reload slow enough to need several heartbeat ticks, but fast enough to finish
    * inside RenderWaitSupport.awaitOrRetry's own bound, must refresh the runtime's heartbeat while
    * it runs and must not touch the idle/accessed clock (access(true)) -- that call happens
    * earlier, at pairing-access grant time, which this test harness bypasses entirely by handing
    * {@code rws} to the mocked {@code runtimeAccess} directly.
    */
   @Test
   void refreshDataKeepsHeartbeatAliveWhileReloadIsInFlight() throws Exception {
      long saved = RuntimeHeartbeatKeepAlive.INTERVAL_MS;
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 20L;

      try {
         Principal agent = TestPrincipals.user("alice", "host-org");

         Worksheet ws = new Worksheet();
         TableAssembly crosstab = TestWorksheets.withGroupSumAndSort(
            TestWorksheets.nonEmbeddedTableWithColumns(ws, "Crosstab1", "cust", "amount"),
            "cust", "amount");
         ((BoundTableAssembly) crosstab).setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
         ws.addAssembly(crosstab);

         RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
         when(rws.getWorksheet()).thenReturn(ws);
         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         when(rws.getAssetQuerySandbox()).thenReturn(box);
         when(box.getWorksheet()).thenReturn(ws);
         when(box.getVariableTable()).thenReturn(new VariableTable());

         CountDownLatch heartbeats = new CountDownLatch(3);
         doAnswer(inv -> {
            heartbeats.countDown();
            return null;
         }).when(rws).access(false);

         doAnswer(invocation -> {
            // Blocks until the heartbeat has ticked a few times, then lets the reload finish --
            // well inside RenderWaitSupport.awaitOrRetry's own multi-second bound.
            assertTrue(heartbeats.await(2, TimeUnit.SECONDS),
                       "expected the runtime's heartbeat to be refreshed repeatedly while the " +
                       "reload is still running");
            return null;
         }).when(box).refreshColumnSelection(eq("Crosstab1"), anyBoolean());

         SheetSessionService sessions = mock(SheetSessionService.class);
         SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
         when(sessions.resolve(eq("TOK-RD79074a"), any())).thenReturn(session("TOK-RD79074a"));
         when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

         WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
            mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

         WorksheetAgentController ctrl = controller(featureOn(),
            mock(SheetJoinService.class), mock(SheetSessionService.class),
            mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

         try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
            ctrl.edit("TOK-RD79074a", refreshDataRequest("Crosstab1"), agent);
         }

         verify(rws, atLeastOnce()).access(false);
         verify(rws, never()).access(true);
      }
      finally {
         RuntimeHeartbeatKeepAlive.INTERVAL_MS = saved;
      }
   }

   /**
    * The hard case the round-2 review flagged: RenderWaitSupport.awaitOrRetry does not cancel the
    * reload on timeout, so a reload slower than its own bound keeps running on a background
    * thread well past the point where the controller call itself has already thrown
    * RenderNotReadyException. The heartbeat must survive that client-visible timeout -- it is
    * started before the wait begins but stopped from INSIDE the reload's own callable, not by the
    * outer catch(RenderNotReadyException) branch, which must leave it running.
    */
   @Test
   void refreshDataKeepsHeartbeatRunningPastAClientVisibleTimeout() throws Exception {
      long saved = RuntimeHeartbeatKeepAlive.INTERVAL_MS;
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 20L;

      try {
         Principal agent = TestPrincipals.user("alice", "host-org");

         Worksheet ws = new Worksheet();
         TableAssembly crosstab = TestWorksheets.withGroupSumAndSort(
            TestWorksheets.nonEmbeddedTableWithColumns(ws, "Crosstab1", "cust", "amount"),
            "cust", "amount");
         ((BoundTableAssembly) crosstab).setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
         ws.addAssembly(crosstab);

         RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
         when(rws.getWorksheet()).thenReturn(ws);
         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         when(rws.getAssetQuerySandbox()).thenReturn(box);
         when(box.getWorksheet()).thenReturn(ws);
         when(box.getVariableTable()).thenReturn(new VariableTable());

         // Longer than RenderWaitSupport's own bound here (TABLE_WARM_MAX_ATTEMPTS *
         // TABLE_WARM_RETRY_SLEEP_MS = 2000ms), so the controller call itself times out and
         // throws while this is still sleeping in the background.
         doAnswer(invocation -> {
            Thread.sleep(2_500);
            return null;
         }).when(box).refreshColumnSelection(eq("Crosstab1"), anyBoolean());

         SheetSessionService sessions = mock(SheetSessionService.class);
         SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
         when(sessions.resolve(eq("TOK-RD79074b"), any())).thenReturn(session("TOK-RD79074b"));
         when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

         WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
            mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

         WorksheetAgentController ctrl = controller(featureOn(),
            mock(SheetJoinService.class), mock(SheetSessionService.class),
            mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

         try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
            assertThrows(RenderNotReadyException.class,
               () -> ctrl.edit("TOK-RD79074b", refreshDataRequest("Crosstab1"), agent));
         }

         // The controller call has already returned (thrown), ~2000ms in, but the background
         // reload still has roughly 500ms left to run -- the heartbeat must still be ticking.
         clearInvocations(rws);
         Thread.sleep(300L);
         verify(rws, atLeastOnce()).access(false);

         // Once the background reload genuinely finishes (its own finally stops the heartbeat),
         // no further ticks should arrive.
         Thread.sleep(500L);
         clearInvocations(rws);
         Thread.sleep(150L);
         verify(rws, never()).access(false);
      }
      finally {
         RuntimeHeartbeatKeepAlive.INTERVAL_MS = saved;
      }
   }

   /**
    * Bug #76711 round 6 (bulk branch): the "refresh all tables" branch (no table name given) had
    * the exact same gap as the single-assembly branch did before round 6 -- it invalidated the
    * outer AssetQuerySandbox.tmap/AssetDataCache slots but never forced a real RUNTIME_MODE
    * execution, so it never actually refreshed the XSessionManager.dataCache entry RUNTIME_MODE
    * readers consult, confirmed live. This test would FAIL against the pre-round-6 bulk branch,
    * which never calls getTableLens(table, RUNTIME_MODE) or removes the row-cap hint.
    */
   @Test
   void refreshDataAllTablesAlsoForcesARuntimeModeExecution() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      // Deliberately not runtime, not live-data: WorksheetEventUtil.getMode(table) returns
      // DESIGN_MODE here, matching an ordinary add_table'd table.
      ws.addAssembly(table);

      VariableTable vars = new VariableTable();
      vars.put(inetsoft.uql.XQuery.HINT_MAX_ROWS, "100");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(vars);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD13"), any())).thenReturn(session("TOK-RD13"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // See refreshDataThrowsRenderNotReadyWhenColumnSelectionIsSlow for why this is needed now.
      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         ctrl.edit("TOK-RD13", refreshDataRequest(null), agent);
      }

      verify(box).getTableLens("Table1", AssetQuerySandbox.RUNTIME_MODE);
      assertNull(vars.get(inetsoft.uql.XQuery.HINT_MAX_ROWS),
         "the bulk refresh-all branch must also remove the row-cap hint unconditionally");
   }

   // ---------------------------------------------------------------------------
   // Bug #79074: refreshData's bulk ("refresh all tables") branch must also keep the runtime's
   // heartbeat alive while its sequential, synchronous per-table reloads are in flight -- more
   // exposed than the single-assembly branch, since N slow tables compound into one even longer
   // blocking call.
   // ---------------------------------------------------------------------------

   @Test
   void refreshDataAllTablesKeepsHeartbeatAliveWhileReloadIsInFlight() throws Exception {
      long saved = RuntimeHeartbeatKeepAlive.INTERVAL_MS;
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 20L;

      try {
         Principal agent = TestPrincipals.user("alice", "host-org");

         Worksheet ws = new Worksheet();
         BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
            ws, "Table1", "cust", "amount");
         table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
         ws.addAssembly(table);

         VariableTable vars = new VariableTable();

         RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
         when(rws.getWorksheet()).thenReturn(ws);
         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         when(rws.getAssetQuerySandbox()).thenReturn(box);
         when(box.getWorksheet()).thenReturn(ws);
         when(box.getVariableTable()).thenReturn(vars);

         CountDownLatch heartbeats = new CountDownLatch(3);
         doAnswer(inv -> {
            heartbeats.countDown();
            return null;
         }).when(rws).access(false);

         doAnswer(invocation -> {
            assertTrue(heartbeats.await(2, TimeUnit.SECONDS),
                       "expected the runtime's heartbeat to be refreshed repeatedly while the " +
                       "bulk reload is still running");
            return null;
         }).when(box).getTableLens("Table1", AssetQuerySandbox.RUNTIME_MODE);

         SheetSessionService sessions = mock(SheetSessionService.class);
         SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
         when(sessions.resolve(eq("TOK-RD79074c"), any())).thenReturn(session("TOK-RD79074c"));
         when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

         WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
            mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

         WorksheetAgentController ctrl = controller(featureOn(),
            mock(SheetJoinService.class), mock(SheetSessionService.class),
            mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

         try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
            ctrl.edit("TOK-RD79074c", refreshDataRequest(null), agent);
         }

         verify(rws, atLeastOnce()).access(false);
         verify(rws, never()).access(true);
      }
      finally {
         RuntimeHeartbeatKeepAlive.INTERVAL_MS = saved;
      }
   }

   /** The periodic task must be unregistered once the bulk reload completes -- no leak. */
   @Test
   void refreshDataAllTablesStopsRefreshingHeartbeatOnceReloadCompletes() throws Exception {
      long saved = RuntimeHeartbeatKeepAlive.INTERVAL_MS;
      RuntimeHeartbeatKeepAlive.INTERVAL_MS = 15L;

      try {
         Principal agent = TestPrincipals.user("alice", "host-org");

         Worksheet ws = new Worksheet();
         BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
            ws, "Table1", "cust", "amount");
         table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
         ws.addAssembly(table);

         RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
         when(rws.getWorksheet()).thenReturn(ws);
         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         when(rws.getAssetQuerySandbox()).thenReturn(box);
         when(box.getWorksheet()).thenReturn(ws);
         when(box.getVariableTable()).thenReturn(new VariableTable());

         SheetSessionService sessions = mock(SheetSessionService.class);
         SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
         when(sessions.resolve(eq("TOK-RD79074d"), any())).thenReturn(session("TOK-RD79074d"));
         when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

         WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
            mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

         WorksheetAgentController ctrl = controller(featureOn(),
            mock(SheetJoinService.class), mock(SheetSessionService.class),
            mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

         try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
            ctrl.edit("TOK-RD79074d", refreshDataRequest(null), agent);
         }

         clearInvocations(rws);
         Thread.sleep(150L);
         verify(rws, never()).access(anyBoolean());
      }
      finally {
         RuntimeHeartbeatKeepAlive.INTERVAL_MS = saved;
      }
   }

   /**
    * Bug #78940: LIVE/RUNTIME AssetDataCache entries are stored under the key of
    * AssetQueryCacheNormalizer's prepared clone, so refreshData must clear through
    * AssetQueryCacheNormalizer.clearCache (all modes) for the refreshed table, its dependents,
    * and every table in the bulk branch -- the plain getCacheKey(original assembly) removal
    * misses the entry whenever the clone differs (unsorted columns, hidden column, alias).
    */
   @Test
   void refreshDataClearsNormalizedCacheForTableAndDependents78940() throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "zeta", "alpha");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      ws.addAssembly(table);
      MirrorTableAssembly dependent = new MirrorTableAssembly(ws, "Dep1", table);
      ws.addAssembly(dependent);

      for(String req : new String[] { "Table1", null }) {
         refreshAndVerifyNormalizedClear(ws, "TOK-78940-" + req, req);
      }
   }

   private void refreshAndVerifyNormalizedClear(Worksheet ws, String token, String reqTable)
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(new VariableTable());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq(token), any())).thenReturn(session(token));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      inetsoft.report.composition.execution.AssetDataCache assetDataCache =
         mock(inetsoft.report.composition.execution.AssetDataCache.class);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class), assetDataCache);

      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class);
          MockedStatic<inetsoft.report.composition.execution.AssetQueryCacheNormalizer> norm =
             mockStatic(inetsoft.report.composition.execution.AssetQueryCacheNormalizer.class))
      {
         ctrl.edit(token, refreshDataRequest(reqTable), agent);

         norm.verify(() -> inetsoft.report.composition.execution.AssetQueryCacheNormalizer
            .clearCache(eq((TableAssembly) ws.getAssembly("Table1")), eq(box)));
         norm.verify(() -> inetsoft.report.composition.execution.AssetQueryCacheNormalizer
            .clearCache(eq((TableAssembly) ws.getAssembly("Dep1")), eq(box)), atLeastOnce());
      }
   }

   /**
    * Bug #76711 round 6 re-review finding 1: unlike the single-assembly branch, the bulk branch
    * never calls loadTableData/refreshColumnSelection to execute a query under the table's own
    * mode first -- resetTableLens/AssetDataCache.remove only clear the cache shell, they never
    * run anything. So a table whose own mode is ALREADY RUNTIME_MODE (e.g. one interactively
    * open in the Composer canvas) had nothing in the bulk branch that actually re-executes its
    * query, reproducing bug #76711 for that specific table/mode combination. This test would
    * FAIL against the first round-6 bulk-branch fix, which guarded the forced read with
    * {@code mode != RUNTIME_MODE} -- correct for the single-assembly branch (loadTableData there
    * already covers the RUNTIME_MODE case) but wrong here, where there is no such fallback.
    */
   @Test
   void refreshDataAllTablesForcesRuntimeModeExecutionEvenWhenAlreadyRuntimeMode() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      table.setRuntime(true);
      ws.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(new VariableTable());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD14"), any())).thenReturn(session("TOK-RD14"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         ctrl.edit("TOK-RD14", refreshDataRequest(null), agent);
      }

      verify(box).getTableLens("Table1", AssetQuerySandbox.RUNTIME_MODE);
   }

   /**
    * #8: an invalid table (here, the same missing-SourceInfo shape the sibling tests above now
    * avoid) must refuse loudly instead of silently proceeding -- mirrors
    * {@code WorksheetEventUtil.refreshAssembly}'s own {@code checkValidity()} gate, which the UI's
    * "Run Query" reaches and the agent path previously never called at all.
    */
   @Test
   void refreshDataRefusesAnInvalidTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      // Deliberately no setSourceInfo -- SourceInfo.checkValidity() throws "Prefix is null!".
      TableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      ws.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD4"), any())).thenReturn(session("TOK-RD4"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-RD4", refreshDataRequest("Table1"), agent));
      assertTrue(ex.getMessage().contains("Cannot refresh"), ex.getMessage());
      verify(box, never()).resetTableLens(anyString(), anyInt());
   }

   /** #9: RUNTIME_MODE must drop the row cap before re-running the query, same as Run Query. */
   @Test
   void refreshDataRemovesRowCapInRuntimeMode() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      table.setRuntime(true);
      ws.addAssembly(table);

      VariableTable vars = new VariableTable();
      vars.put(inetsoft.uql.XQuery.HINT_MAX_ROWS, "100");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(vars);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD5"), any())).thenReturn(session("TOK-RD5"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      assertNotNull(vars.get(inetsoft.uql.XQuery.HINT_MAX_ROWS), "sanity: cap set before refresh");

      // See refreshDataThrowsRenderNotReadyWhenColumnSelectionIsSlow for why this is needed now.
      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         ctrl.edit("TOK-RD5", refreshDataRequest("Table1"), agent);
      }

      assertNull(vars.get(inetsoft.uql.XQuery.HINT_MAX_ROWS),
         "Run Query's own row-cap removal must be mirrored for RUNTIME_MODE tables");
   }

   /** #11: a not-yet-run tabular query must have its columns discovered, same as Run Query. */
   @Test
   void refreshDataDiscoversColumnsForANeverRunTabularQuery() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      TabularTableAssembly table = spy(new TabularTableAssembly(ws, "Table1"));
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      doNothing().when(table).loadColumnSelection(any(), anyBoolean(), any());
      ws.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(new VariableTable());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD6"), any())).thenReturn(session("TOK-RD6"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // See refreshDataThrowsRenderNotReadyWhenColumnSelectionIsSlow for why this is needed now.
      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         ctrl.edit("TOK-RD6", refreshDataRequest("Table1"), agent);
      }

      verify(table).loadColumnSelection(any(), eq(true), any());
   }

   /**
    * Bug #76711: a table whose {@code XSessionManager.dataCache} entry is already populated
    * (e.g. a SERVER_FILE/tabular {@code BoundQuery}) must have {@code __refresh_report__} set for
    * the duration of the reload, same as {@code WSQueryService.runQuery} -- otherwise
    * {@code resetTableLens}/{@code AssetDataCache} above still leave that deeper, key-stable cache
    * serving pre-refresh rows forever, silently, even though {@code refresh_data} reports
    * {@code {"ok":true}}.
    */
   @Test
   void refreshDataSetsRefreshReportFlagAroundReload() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      ws.addAssembly(table);

      VariableTable vars = new VariableTable();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(vars);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD7"), any())).thenReturn(session("TOK-RD7"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // clearBoundQueryCache (bug #76711 round 2) reaches AssetQuery.createAssetQuery, which
      // needs live Spring beans this lightweight test context doesn't wire -- mock it away to a
      // no-op default (null, not a BoundQuery) since this test isn't about that mechanism.
      try(MockedStatic<WorksheetEventUtil> eventUtil =
             mockStatic(WorksheetEventUtil.class, CALLS_REAL_METHODS);
          MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         eventUtil.when(() ->
               WorksheetEventUtil.loadTableData(eq(rws), eq("Table1"), anyBoolean(), anyBoolean()))
            .thenAnswer(invocation -> {
               assertEquals("true", vars.get("__refresh_report__"),
                  "flag must be set for the duration of the reload, same as Run Query");
               return null;
            });

         ctrl.edit("TOK-RD7", refreshDataRequest("Table1"), agent);
      }

      assertNull(vars.get("__refresh_report__"),
         "flag must be removed again after the reload, same as Run Query's finally block");
   }

   /**
    * Bug #76711, review round 2: {@code RenderWaitSupport.awaitOrRetry}'s reload work runs on a
    * separate virtual thread that is NOT cancelled on timeout (see its own doc comment) -- so a
    * reload slower than the timeout keeps running after {@code refreshData}'s own {@code finally}
    * block has already removed {@code __refresh_report__}, which can race that still-running
    * thread and silently fall back to the stale {@code XSessionManager} cache (this is exactly
    * what a {@code claude-review} CI bot caught, and the lead independently reproduced live, in
    * this bug's round-1 fix). The actual fix is {@code clearBoundQueryCache} -- a synchronous
    * {@code AssetQuery.createAssetQuery(...)} + {@code BoundQuery.clearQueryCache(...)} call made
    * BEFORE the bounded reload is even submitted, so its correctness cannot depend on winning that
    * race. This test simulates a reload slower than the 2000ms timeout (same technique as
    * {@code insertColumnThrowsRenderNotReadyWhenColumnSelectionIsSlow} -- an instance stub on
    * {@code box}, not a static mock, since {@code MockedStatic} is thread-confined and
    * {@code RenderWaitSupport} runs the reload on a different (virtual) thread) and asserts
    * clearQueryCache was already invoked -- in order, before the slow reload started -- by the
    * time {@code RenderNotReadyException} propagates out, proving the invalidation does not
    * depend on the reload finishing in time.
    */
   @Test
   void refreshDataClearsBoundQueryCacheBeforeSlowReload() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      ws.addAssembly(table);

      VariableTable vars = new VariableTable();
      List<String> callOrder = Collections.synchronizedList(new ArrayList<>());

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(vars);
      doAnswer(invocation -> {
         callOrder.add("reload");
         // Slower than RenderWaitSupport's 2000ms timeout -- keeps running in the background
         // (not cancelled) after awaitOrRetry has already thrown. An instance stub (not a static
         // mock) so it fires correctly from RenderWaitSupport's virtual worker thread.
         Thread.sleep(3_000);
         return null;
      }).when(box).refreshColumnSelection(eq("Table1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD8"), any())).thenReturn(session("TOK-RD8"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      BoundQuery boundQuery = mock(BoundQuery.class);

      doAnswer(invocation -> {
         callOrder.add("clearQueryCache");
         return null;
      }).when(boundQuery).clearQueryCache(any());

      // clearBoundQueryCache runs synchronously on this (the test) thread, before awaitOrRetry
      // submits the reload -- a MockedStatic registered here is visible for that call, unlike the
      // reload work above which runs on a different thread.
      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         assetQuery.when(() -> AssetQuery.createAssetQuery(
               any(), anyInt(), eq(box), eq(false), eq(-1L), eq(true), eq(false)))
            .thenReturn(boundQuery);

         assertThrows(RenderNotReadyException.class,
            () -> ctrl.edit("TOK-RD8", refreshDataRequest("Table1"), agent));
      }

      assertEquals(List.of("clearQueryCache", "reload"), callOrder,
         "the query cache must be cleared synchronously before the reload is even submitted, " +
         "not merely before/after it happens to finish within the timeout");
   }

   /**
    * Bug #76711, round 3: rounds 1 and 2 both removed {@code __refresh_report__} in a
    * {@code finally} scoped to {@code RenderWaitSupport.awaitOrRetry}'s own return -- which fires
    * the instant the caller's bounded wait gives up (2000ms here), NOT when the reload it wraps
    * actually finishes running on its own, uncancelled virtual thread. A live IDEA debugger session
    * confirmed round 2's {@code clearBoundQueryCache} is a silent no-op in deployments where
    * {@code XSessionManager}'s {@code service} is a dynamic proxy rather than a concrete
    * {@code XEngine}, so the flag's own lifetime is the only thing this method's correctness
    * depends on. Same slow-reload technique as {@code refreshDataClearsBoundQueryCacheBeforeSlowReload}
    * (an instance stub on {@code box}, since the reload runs on a different thread than a
    * {@code MockedStatic} would be visible from) -- but this test asserts on the flag's own state,
    * from inside the stubbed slow call itself and immediately after the caller times out, not on
    * {@code clearQueryCache}'s call order.
    */
   @Test
   void refreshDataKeepsRefreshReportFlagSetUntilSlowReloadActuallyFinishes() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      BoundTableAssembly table = TestWorksheets.nonEmbeddedTableWithColumns(
         ws, "Table1", "cust", "amount");
      table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ds", "Query1"));
      ws.addAssembly(table);

      VariableTable vars = new VariableTable();
      AtomicBoolean flagSetDuringSlowReload = new AtomicBoolean(false);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getVariableTable()).thenReturn(vars);
      doAnswer(invocation -> {
         // Slower than RenderWaitSupport's 2000ms timeout -- runs on its own virtual thread well
         // past the point the caller below has already given up and thrown RenderNotReadyException.
         Thread.sleep(3_000);
         flagSetDuringSlowReload.set("true".equals(vars.get("__refresh_report__")));
         return null;
      }).when(box).refreshColumnSelection(eq("Table1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD9"), any())).thenReturn(session("TOK-RD9"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      try(MockedStatic<AssetQuery> assetQuery = mockStatic(AssetQuery.class)) {
         assertThrows(RenderNotReadyException.class,
            () -> ctrl.edit("TOK-RD9", refreshDataRequest("Table1"), agent));

         assertEquals("true", vars.get("__refresh_report__"),
            "flag must still be set immediately after the caller times out -- the reload is " +
            "still running in the background, not finished");

         long deadline = System.currentTimeMillis() + 5_000;

         while(vars.get("__refresh_report__") != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
         }
      }

      assertTrue(flagSetDuringSlowReload.get(),
         "flag must still have been set at the moment the slow reload actually ran, well past " +
         "the caller's own 2000ms timeout");
      assertNull(vars.get("__refresh_report__"),
         "flag must finally be removed once the background reload actually completes, not " +
         "merely once the caller stops waiting for it");
   }

   /**
    * Bug #76350 follow-on (item A), review round 1: {@code insertColumn} calls the same unwrapped
    * {@code refreshColumnSelection}/{@code loadTableData} pair 3a already bounds for
    * {@code refresh_data} -- a prior {@code set_group_aggregate(crosstab=true)} on this same
    * embedded table makes it pay the same expensive query-execution path.
    */
   @Test
   void insertColumnThrowsRenderNotReadyWhenColumnSelectionIsSlow() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      // Row 0 is the header row insertColumn's setEmbeddedData/getEmbeddedData work off of --
      // TestWorksheets.tableWithColumns only sets a ColumnSelection, no actual XEmbeddedTable
      // rows, so insertColumn's real column-insertion logic needs a table built this way instead
      // (mirrors WorksheetEditServiceMutatorsTest#embeddedWithData).
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, "T");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.DOUBLE },
         new Object[][] { { "cust", "amount" }, { "x1", 1.0 } }));
      TestWorksheets.withGroupSumAndSort(t, "cust", "amount");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      doAnswer(invocation -> {
         Thread.sleep(3_000);
         return null;
      }).when(box).refreshColumnSelection(eq("T"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ICS"), any())).thenReturn(session("TOK-ICS"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      assertThrows(RenderNotReadyException.class,
         () -> ctrl.edit("TOK-ICS", insertColumnRequest("T"), agent));
   }

   /**
    * Bug #76350 follow-on (item A), review round 1: {@code reorderConcatSubtables} calls the same
    * unwrapped pair -- a prior {@code set_group_aggregate(crosstab=true)} on this same
    * concatenated table makes it pay the same expensive query-execution path.
    */
   @Test
   void reorderConcatSubtablesThrowsRenderNotReadyWhenColumnSelectionIsSlow() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "k", "v");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "k", "w");
      ws.addAssembly(a);
      ws.addAssembly(b);

      TableAssemblyOperator top = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.UNION);
      top.addOperator(op);

      ConcatenatedTableAssembly concat = new ConcatenatedTableAssembly(
         ws, "Concat1", new TableAssembly[]{ a, b }, new TableAssemblyOperator[]{ top });
      TestWorksheets.withGroupSumAndSort(concat, "k", "v");
      ws.addAssembly(concat);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      doAnswer(invocation -> {
         Thread.sleep(3_000);
         return null;
      }).when(box).refreshColumnSelection(eq("Concat1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RC"), any())).thenReturn(session("TOK-RC"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // the concatenation holds an aggregate, which a new first subtable would drop, so the
      // reorder is confirmed to reach the refresh
      EditRequest confirmed = new WebConfig().objectMapper().readValue(
         "{\"op\":\"reorder_concat_subtables\",\"table\":\"Concat1\",\"subtables\":[\"B\",\"A\"]," +
         "\"confirmed\":true}", EditRequest.class);
      assertThrows(RenderNotReadyException.class, () -> ctrl.edit("TOK-RC", confirmed, agent));
   }

   /**
    * Bug #78846: reorder_concat_subtables refuses a new first subtable that would drop what the
    * concatenation holds unless {@code confirmed}, and refuses a list that is not a permutation of
    * its subtables, leaving the worksheet as it was. The concatenation sums {@code v}, which the
    * new first subtable B (columns k, w) lacks; a state on a name B keeps would not be refused
    * (Bug #78888).
    */
   @Test
   void reorderConcatSubtablesRefusesUnconfirmedLossAndANonPermutation() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "k", "v");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "k", "w");
      ws.addAssembly(a);
      ws.addAssembly(b);

      TableAssemblyOperator top = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.UNION);
      top.addOperator(op);

      ConcatenatedTableAssembly concat = new ConcatenatedTableAssembly(
         ws, "Concat1", new TableAssembly[]{ a, b }, new TableAssemblyOperator[]{ top });
      TestWorksheets.withGroupSumAndSort(concat, "k", "v");
      ws.addAssembly(concat);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RC"), any())).thenReturn(session("TOK-RC"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException unconfirmed = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-RC", reorderConcatSubtablesRequest("Concat1", List.of("B", "A")), agent));
      assertTrue(unconfirmed.getMessage().contains("confirmed:true"), unconfirmed.getMessage());

      PairingException duplicate = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-RC", reorderConcatSubtablesRequest("Concat1", List.of("A", "A")), agent));
      assertTrue(duplicate.getMessage().contains("exactly once"), duplicate.getMessage());

      assertArrayEquals(new String[]{ "A", "B" }, concat.getTableNames());
   }

   @Test
   void editAddColumnAutoNamesOnEmbeddedTableWithoutName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-AC");
      when(sessions.resolve(eq("TOK-AC"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      // "add_column" with no 'name' on an EMBEDDED table must auto-generate the
      // next available "col" + N, matching the Composer UI's own insert-column
      // behavior (InsertDataService.insertData) — a brand-new spreadsheet-style
      // column has no pre-existing identity to name it after.
      EditRequest req = new EditRequest("add_column", "T", null,
         null, null, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null,
         null, null,
         null, null, null, null, null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-AC", req, agent);

      assertNotNull(t.getColumnSelection(false).getAttribute("col1"),
                    "auto-generated column 'col1' should have been added");
   }

   @Test
   void editRejectsAddColumnWithoutNameOnNonEmbeddedTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-ACM");
      when(sessions.resolve(eq("TOK-ACM"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      editSvc.apply("TOK-ACM", agent, editor -> editor.addMirror("M", "T"));

      // "add_column" with no 'name' on a NON-embedded table (here, a mirror) has
      // no embedded grid to insert a blank column into — it means re-adding an
      // existing-but-hidden column, so there is no unambiguous default and it
      // must fail loud rather than poison the column selection with a null name.
      EditRequest req = new EditRequest("add_column", "M", null,
         null, null, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null,
         null, null,
         null, null, null, null, null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ACM", req, agent));
      assertTrue(ex.getMessage().contains("name"));
   }

   @Test
   void editRejectsAddExpressionColumnWithoutName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-AEC");
      when(sessions.resolve(eq("TOK-AEC"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      // Regression for the "alias" vs "name" mixup: calling add_expression_column
      // without 'name' used to silently succeed and create an unreferenceable
      // null-named expression column that broke every subsequent field lookup
      // (set_conditions, set_sort, etc.) on the table.
      EditRequest req = new EditRequest("add_expression_column", "T", null,
         null, null, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null,
         null, null,
         null, null, null, null, null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AEC", req, agent));
      assertTrue(ex.getMessage().contains("name"));

      assertTrue(t.getColumnSelection(false).getAttributeCount() == 2,
         "no column should have been added when 'name' was missing");
   }

   @Test
   void editRejectsEditExpressionWithoutName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-EE");
      when(sessions.resolve(eq("TOK-EE"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      EditRequest req = new EditRequest("edit_expression", "T", null,
         null, null, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null,
         null, null,
         null, null, null, null, null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-EE", req, agent));
      assertTrue(ex.getMessage().contains("name"));
   }

   /** Builds an {@code add_variable} EditRequest that routes to addVariableFromEdit(). */
   private static EditRequest addVariableRequest(String name, String type) {
      return addVariableRequest(name, type, null);
   }

   /** Same as {@link #addVariableRequest(String, String)}, with an explicit default value. */
   private static EditRequest addVariableRequest(String name, String type, String defaultValue) {
      return new EditRequest(
         "add_variable", null, null, name, type, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, defaultValue,
         null, null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   @Test
   void editAddVariableCreatesVariableWhenNameIsNew() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AV"), any())).thenReturn(session("TOK-AV"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-AV", addVariableRequest("minTotal", "double"), agent);

      Assembly a = ws.getAssembly("minTotal");
      assertTrue(a instanceof VariableAssembly, "a variable assembly should have been created");
   }

   // ---------------------------------------------------------------------------
   // add_mirror -- same-worksheet (source) and cross-worksheet (path/scope) forms
   // ---------------------------------------------------------------------------

   /** Builds the same-worksheet {@code add_mirror} EditRequest form ({@code name}/{@code source}). */
   private static EditRequest addMirrorRequest(String name, String source) throws Exception {
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "add_mirror");
      body.put("name", name);
      body.put("source", source);

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   /**
    * Builds the cross-worksheet {@code add_mirror} EditRequest form ({@code name}, {@code path},
    * optional {@code scope}) via the real app {@link ObjectMapper} -- same technique as
    * {@link #addQueryParamsTableRequest}, since hand-counting nulls to path/scope's new position
    * in the 70+ field positional record risks the exact silent off-by-one that technique exists
    * to avoid.
    */
   private static EditRequest addWorksheetMirrorRequest(String name, String path, String scope)
      throws Exception
   {
      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "add_mirror");
      body.put("name", name);
      if(path != null) body.put("path", path);
      if(scope != null) body.put("scope", scope);

      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   private WorksheetAgentController setPrimaryController(Worksheet ws) throws Exception {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SP"), any())).thenReturn(session("TOK-SP"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);
      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class),
         mock(InnerJoinService.class));
      return controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), editSvc,
         mock(WorksheetService.class));
   }

   private static EditRequest setPrimaryRequest(String table) throws Exception {
      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(
         mapper.writeValueAsString(Map.of("op", "set_primary_assembly", "table", table)),
         EditRequest.class);
   }

   /** Bug #78874: making a hidden table primary reports it in a 200 body, key absent otherwise. */
   @Test
   void setPrimaryAssemblyReportsMadeVisibleOnlyWhenAHiddenTableWasShown() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "B", "id"));
      ws.setPrimaryAssembly("B");
      EmbeddedTableAssembly hidden = TestWorksheets.tableWithColumns(ws, "A", "id");
      ws.addAssembly(hidden);
      ((inetsoft.uql.asset.internal.TableAssemblyInfo) hidden.getTableInfo())
         .setVisibleTable(false);
      WorksheetAgentController ctrl = setPrimaryController(ws);
      ObjectMapper mapper = new WebConfig().objectMapper();

      ResponseEntity<EditResponse> shown =
         ctrl.edit("TOK-SP", setPrimaryRequest("A"), agent);
      assertEquals(org.springframework.http.HttpStatus.OK, shown.getStatusCode());
      assertEquals("{\"madeVisible\":true}", mapper.writeValueAsString(shown.getBody()));

      // already visible and already primary: nothing to report
      ResponseEntity<EditResponse> again =
         ctrl.edit("TOK-SP", setPrimaryRequest("A"), agent);
      assertEquals(org.springframework.http.HttpStatus.NO_CONTENT, again.getStatusCode());
   }

   private static EditRequest setSortRequest(String table, String field, String direction)
      throws Exception
   {
      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(
         mapper.writeValueAsString(Map.of("op", "set_sort", "table", table, "field", field,
                                          "direction", direction)),
         EditRequest.class);
   }

   /**
    * Feature #79222: set_sort NONE removes the sort through the controller, and NONE on an
    * unsorted column says so in a 200 body (a real removal keeps the 204 No Content).
    *
    * <p>The existing channel for "something noteworthy happened" is {@link EditResponse}'s 200
    * body (see {@code setPrimaryAssemblyReportsMadeVisible...}); it has no warnings field today,
    * so this test pins only that the no-op is NOT a silent 204 and that the body mentions "sort".
    * The field name is the builder's choice.</p>
    */
   @Test
   void setSortNoneRemovesSortAndReportsNoOpWhenColumnHadNone() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      TableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);
      WorksheetAgentController ctrl = setPrimaryController(ws);
      ObjectMapper mapper = new WebConfig().objectMapper();

      ctrl.edit("TOK-SP", setSortRequest("T", "a", "ASC"), agent);

      ResponseEntity<EditResponse> removed =
         ctrl.edit("TOK-SP", setSortRequest("T", "a", "NONE"), agent);
      assertEquals(org.springframework.http.HttpStatus.NO_CONTENT, removed.getStatusCode());
      assertEquals(0, t.getSortInfo().getSorts().length);

      ResponseEntity<EditResponse> noOp =
         ctrl.edit("TOK-SP", setSortRequest("T", "b", "NONE"), agent);
      assertEquals(org.springframework.http.HttpStatus.OK, noOp.getStatusCode(),
         "a no-op NONE must be reported, not a silent 204");
      assertTrue(mapper.writeValueAsString(noOp.getBody()).toLowerCase().contains("sort"),
         "the no-op report must say the column had no sort");
      assertEquals(0, t.getSortInfo().getSorts().length);
   }

   @Test
   void editAddMirrorWithSourceStillResolvesSameWorksheetAssembly() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = TestWorksheets.tableWithColumns(ws, "T", "id", "name");
      ws.addAssembly(table);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AM"), any())).thenReturn(session("TOK-AM"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // Regression: today's same-worksheet add_mirror(name, source) must keep working unchanged
      // now that a new path/scope form exists -- the new dispatch guard must not intercept a
      // request with no 'path'.
      EditResponse response = ctrl.edit("TOK-AM", addMirrorRequest("M", "T"), agent).getBody();
      assertEquals(new EditResponse("M", "table"), response);

      Assembly created = ws.getAssembly("M");
      assertTrue(created instanceof MirrorTableAssembly, "expected an inner mirror assembly");
      assertNull(((MirrorTableAssembly) created).getEntry(),
         "same-worksheet add_mirror must stay an inner mirror (no AssetEntry)");
   }

   @Test
   void editAddMirrorWithPathCreatesOuterMirrorFromSavedWorksheet() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      // Source, saved worksheet asset: one embedded table WITH real backing data, auto-primary.
      // initColumnSelection re-derives columns from the assembly's own AssetQuery in DESIGN_MODE --
      // for an embedded table with no real XEmbeddedTable rows (TestWorksheets.tableWithColumns),
      // that re-derivation sees 0 live columns and discards the hand-set selection; a bound/JDBC
      // table's AssetQuery instead needs a live XRepository, unavailable here. Real embedded data
      // (mirrors insertColumnThrowsRenderNotReadyWhenColumnSelectionIsSlow's fixture) avoids both.
      Worksheet sourceWs = new Worksheet();
      EmbeddedTableAssembly sourceTable = new EmbeddedTableAssembly(sourceWs, "SRC");
      sourceTable.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING },
         new Object[][] { { "id", "name" }, { "1", "Alice" } }));
      sourceWs.addAssembly(sourceTable);

      // Target (current) worksheet -- empty.
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(new AssetQuerySandbox(ws));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AWM"), any())).thenReturn(session("TOK-AWM"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      AssetRepository assetRepository = mock(AssetRepository.class);
      when(assetRepository.getSheet(any(AssetEntry.class), any(), eq(true), eq(AssetContent.ALL),
         eq(false))).thenReturn(sourceWs);
      when(assetRepository.getSheet(any(AssetEntry.class), any(), eq(false), eq(AssetContent.ALL)))
         .thenReturn(sourceWs);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class), assetRepository);

      EditResponse response =
         ctrl.edit("TOK-AWM", addWorksheetMirrorRequest("M", "Sample Queries/SRC", null), agent)
            .getBody();
      assertEquals(new EditResponse("M", "table"), response);

      Assembly created = ws.getAssembly("M");
      assertTrue(created instanceof MirrorTableAssembly, "expected an outer mirror assembly");
      MirrorTableAssembly mirror = (MirrorTableAssembly) created;
      assertNotNull(mirror.getEntry(),
         "a cross-worksheet mirror must carry a non-null AssetEntry");
      assertEquals(2, mirror.getColumnSelection(false).getAttributeCount(),
         "mirror should carry over the source table's columns");
   }

   @Test
   void editAddMirrorWithPathRequiresName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AWMN"), any())).thenReturn(session("TOK-AWMN"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AWMN", addWorksheetMirrorRequest(null, "Sample Queries/SRC", null),
            agent));
      assertTrue(ex.getMessage().contains("name"));
   }

   @Test
   void editAddMirrorWithPathRejectsUnknownWorksheet() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AWMU"), any())).thenReturn(session("TOK-AWMU"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      AssetRepository assetRepository = mock(AssetRepository.class);
      when(assetRepository.getSheet(any(AssetEntry.class), any(), eq(true), eq(AssetContent.ALL),
         eq(false))).thenReturn(null);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class), assetRepository);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AWMU", addWorksheetMirrorRequest("M", "Nonexistent/WS", null),
            agent));
      assertTrue(ex.getMessage().contains("Nonexistent/WS"));
   }

   @Test
   void editSetMirrorAutoUpdateTogglesOffForCrossWorksheetMirror() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet sourceWs = new Worksheet();
      EmbeddedTableAssembly sourceTable = new EmbeddedTableAssembly(sourceWs, "SRC");
      sourceTable.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING },
         new Object[][] { { "id", "name" }, { "1", "Alice" } }));
      sourceWs.addAssembly(sourceTable);

      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(new AssetQuerySandbox(ws));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SMAU"), any())).thenReturn(session("TOK-SMAU"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      AssetRepository assetRepository = mock(AssetRepository.class);
      when(assetRepository.getSheet(any(AssetEntry.class), any(), eq(true), eq(AssetContent.ALL),
         eq(false))).thenReturn(sourceWs);
      when(assetRepository.getSheet(any(AssetEntry.class), any(), eq(false), eq(AssetContent.ALL)))
         .thenReturn(sourceWs);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class), assetRepository);

      ctrl.edit("TOK-SMAU", addWorksheetMirrorRequest("M", "Sample Queries/SRC", null), agent);

      Map<String, Object> body = new java.util.LinkedHashMap<>();
      body.put("op", "set_mirror_auto_update");
      body.put("table", "M");
      body.put("visible", false);
      ObjectMapper mapper = new WebConfig().objectMapper();
      EditRequest toggleReq = mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);

      // set_mirror_auto_update's own tool description asserts this closes the loop: only a
      // mirror of another worksheet's asset (a non-null AssetEntry) can turn auto-update off.
      ctrl.edit("TOK-SMAU", toggleReq, agent);

      MirrorTableAssembly mirror = (MirrorTableAssembly) ws.getAssembly("M");
      assertFalse(mirror.isAutoUpdate(),
         "set_mirror_auto_update should be able to disable auto-update on a mirror created via " +
         "add_mirror's new path/scope form");
   }

   @Test
   void editRejectsAddVariableWithDuplicateName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      // Regression for Bug #75991: a second add_variable call with a name that
      // already exists used to silently replace the existing variable assembly
      // (including its type and default value) via Worksheet.addAssembly's
      // generic same-name replace semantics. add_variable must fail loud instead
      // and point the caller at edit_variable, which is the explicit modify path.
      Worksheet ws = new Worksheet();
      AssetVariable existingVar = new AssetVariable("minTotal");
      existingVar.setTypeNode(XSchema.createPrimitiveType(XSchema.DOUBLE));
      DefaultVariableAssembly existing = new DefaultVariableAssembly(ws, "minTotal");
      existing.setVariable(existingVar);
      ws.addAssembly(existing);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVD"), any())).thenReturn(session("TOK-AVD"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AVD", addVariableRequest("minTotal", "date"), agent));
      assertTrue(ex.getMessage().contains("already exists"));
      assertTrue(ex.getMessage().contains("edit_variable"));

      Assembly a = ws.getAssembly("minTotal");
      assertSame(existing, a, "the original variable assembly must not be replaced");
      assertEquals(XSchema.DOUBLE, ((VariableAssembly) a).getVariable().getTypeNode().getType(),
                   "the original variable's type must be unchanged");
   }

   /**
    * L2-Group7 regressions: add_variable previously accepted a name the Composer's own
    * Variable dialog would refuse ({@code doesNotStartWithNumber}/{@code variableSpecialCharacters}
    * are Angular-only, no Java match anywhere), a name colliding case-insensitively with an
    * existing variable ({@code Worksheet.getAssembly} is case-sensitive, unlike the dialog's own
    * {@code FormValidators.exists(..., {ignoreCase:true})}), an unrecognized {@code type}
    * (silently leaving the variable with no type node at all), and a {@code defaultValue} that
    * doesn't parse under the declared type (silently storing the raw, unparsed string).
    */
   @ParameterizedTest
   @CsvSource({
      "1BadName, start with a digit",
      "'Bad*Name', contains characters",
   })
   void editRejectsAddVariableWithInvalidName(String badName, String expectedFragment)
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVN"), any())).thenReturn(session("TOK-AVN"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AVN", addVariableRequest(badName, "string"), agent));
      assertTrue(ex.getMessage().contains(expectedFragment), ex.getMessage());
      assertNull(ws.getAssembly(badName));
   }

   @Test
   void editRejectsAddVariableWithCaseInsensitiveDuplicateName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      AssetVariable existingVar = new AssetVariable("TestVar");
      existingVar.setTypeNode(XSchema.createPrimitiveType(XSchema.STRING));
      DefaultVariableAssembly existing = new DefaultVariableAssembly(ws, "TestVar");
      existing.setVariable(existingVar);
      ws.addAssembly(existing);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVCI"), any())).thenReturn(session("TOK-AVCI"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AVCI", addVariableRequest("testvar", "string"), agent));
      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
      assertNull(ws.getAssembly("testvar"),
         "a second, differently-cased variable must not have been created");
   }

   @Test
   void editRejectsAddVariableWithUnrecognizedType() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVT"), any())).thenReturn(session("TOK-AVT"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AVT", addVariableRequest("TypeTest", "totally_bogus_type"), agent));
      assertTrue(ex.getMessage().contains("totally_bogus_type"), ex.getMessage());
      assertNull(ws.getAssembly("TypeTest"),
         "an invalid type must reject the whole call, not create a typeless variable");
   }

   @Test
   void editRejectsAddVariableWithUnparsableDefaultValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVD2"), any())).thenReturn(session("TOK-AVD2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AVD2",
            addVariableRequest("IntTest", "integer", "not_a_number"), agent));
      assertTrue(ex.getMessage().contains("not_a_number"), ex.getMessage());
      assertNull(ws.getAssembly("IntTest"),
         "an unparsable default value must reject the whole call");
   }

   /**
    * PR #4901 round-2 review follow-up: {@code createVariable} builds a
    * {@code DefaultVariableAssembly} and adds it directly, bypassing
    * {@code WorksheetEditService.Editor.placeAssembly}'s {@code requireStorableName} guard the
    * same way {@code addJoin}/{@code duplicateAssembly}/{@code renameVariable} did before the
    * round-1 fix. {@code AssemblyInfo:254} writes the name into a CDATA section verbatim, so a
    * name containing the terminator closes it early and leaves malformed XML in storage.
    */
   @Test
   void editRejectsAddVariableWithANameThatWouldBreakTheStoredXml() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AV-CDATA"), any())).thenReturn(session("TOK-AV-CDATA"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AV-CDATA", addVariableRequest("bad]]>name", "double"), agent));
      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      assertNull(ws.getAssembly("bad]]>name"));
   }

   @Test
   void editAddVariableWiresChoicesThrough() throws Exception {
      // Regression for Bug #76328: add_variable/edit_variable had no way to populate
      // UserVariable.setValues()/setChoices()/setDisplayStyle() (the Composer's own Variable
      // dialog "Values" picker). Verifies the edit-dispatch add_variable path wires 'choices'
      // all the way through to the created variable.
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVC"), any())).thenReturn(session("TOK-AVC"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      WorksheetMutationSupport.VariableChoicesSpec choices =
         new WorksheetMutationSupport.VariableChoicesSpec(
            List.of("1", "5", "10"), List.of("One", "Five", "Ten"), null, null, null, "list");

      EditRequest req = new EditRequest(
         "add_variable",              // op
         null,                        // table
         null,                        // column
         "topN",                      // name
         "integer",                   // type
         null,                        // newName
         null,                        // field
         null,                        // operation
         null,                        // values
         null,                        // direction
         null,                        // groups
         null,                        // aggregates
         null,                        // expression
         false,                       // sql
         null, null, null, null, null,  // leftTable, leftKey, rightTable, rightKey, joinType
         null, null, null, null,         // visible, tables, source, concatType
         null, null, null, null, null,   // conditions, ranking, headerColumns, dateOption, boundaries
         null, null, null, null,         // datasource, schema, catalog, logicalModel
         null, null,                     // leftKeys, rightKeys
         null, null, null, null,         // row, col, value, index
         null, null, null, null,         // alias, description, maxRows, distinct
         null, null, null, null,         // columnOrder, groupMappings, groupOthers, variableValues
         null, null, null, null,         // x, y, label, defaultValue
         null, null, null,               // mode, insert, subtables
         null, null,                     // sourceTable, attribute
         null, null, null, null, null, null, null,  // endpoint, parameters, lookup,
                                                      // lookupExpandArrays, lookupTopLevelOnly,
                                                      // suffix, customLookups
         null,                        // crosstab
         null,                        // labels
         choices,                     // choices
         null,                        // joinPaths
         null,                        // mergeable
         null,                        // visibleInViewsheet
         null,                        // confirmed
         null,                        // rowCount
         null                         // concatDistinct
      );

      ctrl.edit("TOK-AVC", req, agent);

      Assembly a = ws.getAssembly("topN");
      assertTrue(a instanceof VariableAssembly);
      AssetVariable var = ((VariableAssembly) a).getVariable();
      assertArrayEquals(new Object[] {"One", "Five", "Ten"}, var.getChoices());
      assertArrayEquals(new Object[] {1, 5, 10}, var.getValues(),
         "values must be typed to the variable's data type (Integer), not raw strings");
      assertTrue(var.isMultipleSelection());
      assertEquals(UserVariable.LIST, var.getDisplayStyle());
   }

   // ---------------------------------------------------------------------------
   // edit — rename_variable / delete_variable dispatch (Bug #75994)
   // ---------------------------------------------------------------------------

   /** Builds a {@code rename_variable} EditRequest that routes to renameVariable(). */
   private static EditRequest renameVariableRequest(String name, String newName) {
      return new EditRequest(
         "rename_variable", null, null, name, null, newName, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /** Builds a {@code delete_variable} EditRequest that routes to deleteVariable(). */
   private static EditRequest deleteVariableRequest(String name) {
      return new EditRequest(
         "delete_variable", null, null, name, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   private static DefaultVariableAssembly addVariable(Worksheet ws, String name) {
      AssetVariable var = new AssetVariable(name);
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, name);
      assembly.setVariable(var);
      ws.addAssembly(assembly);
      return assembly;
   }

   @Test
   void editDispatchesRenameVariable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      addVariable(ws, "minTotal");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RV"), any())).thenReturn(session("TOK-RV"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-RV", renameVariableRequest("minTotal", "employee"), agent);

      assertNull(ws.getAssembly("minTotal"), "old variable name should no longer exist");
      assertTrue(ws.getAssembly("employee") instanceof DefaultVariableAssembly,
                 "renamed variable should exist under the new name");
   }

   @Test
   void editRenameVariableThrowsWhenAssemblyNotFound() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RV2"), any())).thenReturn(session("TOK-RV2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-RV2", renameVariableRequest("noSuchVar", "employee"), agent));
      assertTrue(ex.getMessage().contains("noSuchVar"));
   }

   @Test
   void editDispatchesDeleteVariable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      addVariable(ws, "minTotal");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-DV"), any())).thenReturn(session("TOK-DV"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-DV", deleteVariableRequest("minTotal"), agent);

      assertNull(ws.getAssembly("minTotal"), "variable should have been removed");
   }

   @Test
   void editDeleteVariableThrowsWhenAssemblyNotFound() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-DV2"), any())).thenReturn(session("TOK-DV2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // "T" exists but is a table, not a variable — delete_variable must reject it
      // rather than silently deleting a same-named table assembly.
      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-DV2", deleteVariableRequest("T"), agent));
      assertTrue(ex.getMessage().contains("T"));
      assertNotNull(ws.getAssembly("T"), "table should not have been deleted");
   }

   // ---------------------------------------------------------------------------
   // Bug #78822 -- delete/rename/type-change must sync the sandbox VariableTable
   // ---------------------------------------------------------------------------

   private static EditRequest editVariableTypeRequest(String name, String type) {
      return new EditRequest(
         "edit_variable", null, null, name, type, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   private RuntimeWorksheet lastSyncRuntime;

   private WorksheetAgentController variableSyncController(
      String token, Worksheet ws, VariableTable vtable, AssetQuerySandbox box)
      throws Exception
   {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      lastSyncRuntime = rws;
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getVariableTable()).thenReturn(vtable);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq(token), any())).thenReturn(session(token));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class),
         mock(InnerJoinService.class));

      return controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), editSvc,
         mock(WorksheetService.class));
   }

   @Test
   void deleteVariableDropsOnlyThatVariablesRuntimeValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      addVariable(ws, "state");
      addVariable(ws, "other");
      VariableTable vtable = new VariableTable();
      vtable.put("state", "MD");
      vtable.put("other", "keep");
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-78822A", ws, vtable, box);

      ctrl.edit("TOK-78822A", deleteVariableRequest("state"), agent);

      assertFalse(vtable.contains("state"),
         "a deleted variable's stale value must not survive to a re-created variable");
      assertEquals("keep", vtable.get("other"), "other variables' values must be untouched");
      verify(box).reset();
   }

   @Test
   void refusedDeleteKeepsRuntimeValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      VariableTable vtable = new VariableTable();
      vtable.put("ghost", "v");
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-78822B", ws, vtable, box);

      assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-78822B", deleteVariableRequest("ghost"), agent));
      assertTrue(vtable.contains("ghost"));
   }

   @Test
   void renameVariableMovesRuntimeValueToNewName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      addVariable(ws, "state");
      VariableTable vtable = new VariableTable();
      vtable.put("state", "MD");
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-78822C", ws, vtable, box);

      ctrl.edit("TOK-78822C", renameVariableRequest("state", "region"), agent);

      assertFalse(vtable.contains("state"), "old name must not keep the value");
      assertEquals("MD", vtable.get("region"), "value must follow the variable to its new name");
   }

   @Test
   void typeChangeDropsStaleRuntimeValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly va = addVariable(ws, "n");
      va.getVariable().setTypeNode(XSchema.createPrimitiveType(XSchema.STRING));
      VariableTable vtable = new VariableTable();
      vtable.put("n", "MD");
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-78822D", ws, vtable, box);

      ctrl.edit("TOK-78822D", editVariableTypeRequest("n", XSchema.INTEGER), agent);

      assertFalse(vtable.contains("n"),
         "a value of the old type must not survive a variable type change");
   }

   // ---------------------------------------------------------------------------
   // Bug #79001 -- every removal path must drop the variable's runtime value
   // ---------------------------------------------------------------------------

   private static EditRequest opRequest(String op, String table, String name, String newName) {
      return new EditRequest(
         op, table, null, name, null, newName, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   private VariableTable valuesFor79001() {
      VariableTable vtable = new VariableTable();
      vtable.put("state", "MD");
      vtable.put("other", "keep");
      vtable.put("__refresh_report__", Boolean.TRUE);
      return vtable;
   }

   private void assertOnlyStateDropped(VariableTable vtable) throws Exception {
      assertFalse(vtable.contains("state"), "the removed variable's value must be dropped");
      assertEquals("keep", vtable.get("other"), "other variables' values must survive");
      assertEquals(Boolean.TRUE, vtable.get("__refresh_report__"), "system keys must survive");
   }

   @Test
   void deleteTableOfVariableDropsRuntimeValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      addVariable(ws, "state");
      addVariable(ws, "other");
      VariableTable vtable = valuesFor79001();
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-79001A", ws, vtable, box);

      ctrl.edit("TOK-79001A", opRequest("delete_table", "state", null, null), agent);

      assertOnlyStateDropped(vtable);
      verify(box).reset();
   }

   // ---------------------------------------------------------------------------
   // Bug #79223 -- edit_join takes an edge selector and remove_join_edge is routed
   // ---------------------------------------------------------------------------

   private WorksheetAgentController joinEdgeController(String token, Worksheet ws) throws Exception {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getVariableTable()).thenReturn(new VariableTable());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq(token), any())).thenReturn(session(token));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class),
         new InnerJoinService(null, null));

      return controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), editSvc,
         mock(WorksheetService.class));
   }

   private static Worksheet chainTables79223() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "A", "a1", "a2"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "B", "b1", "b2"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "C", "c1"));
      return ws;
   }

   /** J = A-B on a1=b1 and a2=b2, B-C on b1=c1, all INNER, built through the add_join op. */
   private void addChainJoin79223(WorksheetAgentController ctrl, String token, Principal agent)
      throws Exception
   {
      ctrl.edit(token, joinEdgeRequest(
         "{\"op\":\"add_join\",\"name\":\"J\",\"joinPaths\":[" +
         "{\"leftTable\":\"A\",\"leftKey\":\"a1\",\"rightTable\":\"B\",\"rightKey\":\"b1\",\"joinType\":\"INNER\"}," +
         "{\"leftTable\":\"A\",\"leftKey\":\"a2\",\"rightTable\":\"B\",\"rightKey\":\"b2\",\"joinType\":\"INNER\"}," +
         "{\"leftTable\":\"B\",\"leftKey\":\"b1\",\"rightTable\":\"C\",\"rightKey\":\"c1\",\"joinType\":\"INNER\"}]}"),
         agent);
   }

   private static EditRequest joinEdgeRequest(String json) throws Exception {
      return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, EditRequest.class);
   }

   @Test
   void editJoinRoutesTheEdgeSelectorToTheNamedPair() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = chainTables79223();
      WorksheetAgentController ctrl = joinEdgeController("TOK-79223A", ws);
      addChainJoin79223(ctrl, "TOK-79223A", agent);
      RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) ws.getAssembly("J");

      ctrl.edit("TOK-79223A", joinEdgeRequest(
         "{\"op\":\"edit_join\",\"name\":\"J\",\"joinType\":\"LEFT\"," +
         "\"leftTable\":\"C\",\"rightTable\":\"B\"}"), agent);

      assertEquals(TableAssemblyOperator.RIGHT_JOIN,
                   join.getOperator("B", "C").getOperator(0).getOperation(),
                   "C LEFT B is stored as B RIGHT C");
      assertEquals(TableAssemblyOperator.INNER_JOIN,
                   join.getOperator("A", "B").getOperator(0).getOperation(),
                   "the other pair must not change");
      assertThrows(PairingException.class, () -> ctrl.edit("TOK-79223A", joinEdgeRequest(
         "{\"op\":\"edit_join\",\"name\":\"J\",\"joinType\":\"LEFT\"}"), agent),
         "no selector on a two-edge join must be refused");
   }

   @Test
   void removeJoinEdgeIsRoutedAndDropsOneKey() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = chainTables79223();
      WorksheetAgentController ctrl = joinEdgeController("TOK-79223B", ws);
      addChainJoin79223(ctrl, "TOK-79223B", agent);
      RelationalJoinTableAssembly join = (RelationalJoinTableAssembly) ws.getAssembly("J");

      ctrl.edit("TOK-79223B", joinEdgeRequest(
         "{\"op\":\"remove_join_edge\",\"name\":\"J\",\"leftTable\":\"A\"," +
         "\"rightTable\":\"B\",\"leftKey\":\"a2\",\"rightKey\":\"b2\"}"), agent);

      assertEquals(1, join.getOperator("A", "B").getOperatorCount());
      assertEquals("a1", join.getOperator("A", "B").getOperator(0).getLeftAttribute().getAttribute());
      assertNotNull(join.getOperator("B", "C"));
      assertSame(join, ws.getAssembly("J"));
   }

   @Test
   void removeJoinOfVariableDropsRuntimeValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      addVariable(ws, "state");
      addVariable(ws, "other");
      VariableTable vtable = valuesFor79001();
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-79001B", ws, vtable, box);

      ctrl.edit("TOK-79001B", opRequest("remove_join", null, "state", null), agent);

      assertOnlyStateDropped(vtable);
   }

   @Test
   void renameTableOfVariableDropsRuntimeValueOfOldName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      addVariable(ws, "state");
      addVariable(ws, "other");
      VariableTable vtable = valuesFor79001();
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-79001C", ws, vtable, box);

      ctrl.edit("TOK-79001C", opRequest("rename_table", "state", null, "region"), agent);

      assertOnlyStateDropped(vtable);
   }

   @Test
   void undoOfAddVariableDropsRuntimeValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      addVariable(ws, "state");
      addVariable(ws, "other");
      VariableTable vtable = valuesFor79001();
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-79001E", ws, vtable, box);
      when(lastSyncRuntime.undo(any())).thenAnswer(inv -> {
         ws.removeAssembly("state");
         return true;
      });

      ctrl.undo("TOK-79001E", agent);

      assertOnlyStateDropped(vtable);
   }

   @Test
   void undoOfRenameVariableDropsValueMovedToNewName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      addVariable(ws, "region");
      addVariable(ws, "other");
      VariableTable vtable = new VariableTable();
      vtable.put("region", "MD");
      vtable.put("other", "keep");
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-79001F", ws, vtable, box);
      when(lastSyncRuntime.undo(any())).thenAnswer(inv -> {
         ws.removeAssembly("region");
         addVariable(ws, "state");
         return true;
      });

      ctrl.undo("TOK-79001F", agent);

      assertFalse(vtable.contains("region"));
      assertFalse(vtable.contains("state"));
      assertEquals("keep", vtable.get("other"));
   }

   @Test
   void redoThatRemovesAVariableDropsRuntimeValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      addVariable(ws, "state");
      addVariable(ws, "other");
      VariableTable vtable = valuesFor79001();
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-79001G", ws, vtable, box);
      when(lastSyncRuntime.redo(any())).thenAnswer(inv -> {
         ws.removeAssembly("state");
         return true;
      });

      ctrl.redo("TOK-79001G", agent);

      assertOnlyStateDropped(vtable);
   }

   @Test
   void undoThatRemovesNoVariableKeepsRuntimeValues() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      addVariable(ws, "state");
      VariableTable vtable = valuesFor79001();
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-79001H", ws, vtable, box);
      when(lastSyncRuntime.undo(any())).thenReturn(true);

      ctrl.undo("TOK-79001H", agent);

      assertEquals("MD", vtable.get("state"));
      verify(box, never()).reset();
   }

   @Test
   void addVariableDropsStaleValueLeftUnderItsName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      VariableTable vtable = valuesFor79001();
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = variableSyncController("TOK-79001I", ws, vtable, box);

      ctrl.edit("TOK-79001I", addVariableRequest("state", XSchema.STRING), agent);

      assertOnlyStateDropped(vtable);
   }

   // ---------------------------------------------------------------------------
   // set_variable_values (L2-Group8)
   // ---------------------------------------------------------------------------

   /** Builds a {@code set_variable_values} EditRequest that routes to setVariableValues(). */
   private static EditRequest setVariableValuesRequest(Map<String, Object> variableValues) {
      return new EditRequest(
         "set_variable_values", null, null, null, null, null, null, null, null, null,
         null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, variableValues,
         null, null,
         null, null,
         null, null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   private static DefaultVariableAssembly boundedChoiceVariable(
      Worksheet ws, String name, String type, Object[] choiceValues, int displayStyle)
   {
      AssetVariable var = new AssetVariable(name);
      var.setTypeNode(XSchema.createPrimitiveType(type));
      var.setChoices(choiceValues);
      var.setValues(choiceValues);
      var.setDisplayStyle(displayStyle);
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, name);
      assembly.setVariable(var);
      ws.addAssembly(assembly);
      return assembly;
   }

   @Test
   void setVariableValuesRejectsUnknownVariableName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV1"), any())).thenReturn(session("TOK-SVV1"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // L2-Group8: a typo'd/nonexistent variable name previously returned {ok:true} and did
      // nothing -- the canonical CLAUDE.md tool-misuse shape (silent no-op on a natural mistake).
      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVV1", setVariableValuesRequest(Map.of("TotallyNonexistentVar", "x")), agent));
      assertTrue(ex.getMessage().contains("TotallyNonexistentVar"), ex.getMessage());
   }

   @Test
   void setVariableValuesCoercesDeclaredType() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      variableAssemblyForValues(ws, "NumVar", XSchema.INTEGER);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV2"), any())).thenReturn(session("TOK-SVV2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // L2-Group8: previously put every value into the VariableTable as a raw string, with no
      // type conversion at all -- VariableInputDialogService.initVariableInfos converts every
      // submitted string through this same CoreTool.getData(type, val, true) call first.
      ctrl.edit("TOK-SVV2", setVariableValuesRequest(Map.of("NumVar", "42")), agent);

      ArgumentCaptor<VariableTable> captor = ArgumentCaptor.forClass(VariableTable.class);
      verify(box).refreshVariableTable(captor.capture());
      assertEquals(42, captor.getValue().get("NumVar"),
         "the declared-integer variable's value must be a typed Integer, not the raw string");
   }

   @Test
   void setVariableValuesRejectsValueNotInBoundedChoiceList() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "ChoiceVar", XSchema.STRING,
         new Object[] {"East", "West"}, UserVariable.LIST);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV3"), any())).thenReturn(session("TOK-SVV3"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // L2-Group8: a bounded-picker variable (combobox/list/radio/checkboxes) can only ever be
      // given one of its declared values through the native "Enter Parameters" prompt -- the
      // widget itself cannot render anything else.
      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVV3", setVariableValuesRequest(Map.of("ChoiceVar", "NotInList")), agent));
      assertTrue(ex.getMessage().contains("NotInList"), ex.getMessage());
      assertTrue(ex.getMessage().contains("ChoiceVar"), ex.getMessage());
   }

   @Test
   void setVariableValuesAllowsAnyValueForFreeTextVariable() throws Exception {
      // A free-text variable (displayStyle NONE) has no such restriction natively either -- a
      // backend guard must not make this tool *more* restrictive than the UI it is compared
      // against, even though the variable happens to carry a (non-enforced) choices list.
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "FreeVar", XSchema.STRING,
         new Object[] {"East", "West"}, UserVariable.NONE);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV4"), any())).thenReturn(session("TOK-SVV4"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-SVV4", setVariableValuesRequest(Map.of("FreeVar", "AnythingGoes")), agent);

      ArgumentCaptor<VariableTable> captor = ArgumentCaptor.forClass(VariableTable.class);
      verify(box).refreshVariableTable(captor.capture());
      assertEquals("AnythingGoes", captor.getValue().get("FreeVar"));
   }

   @Test
   void setVariableValuesSingleStringValueOnMultiSelectVariableStillWorks() throws Exception {
      // WBS-029 regression guard: a plain string on a checkboxes/list variable must keep
      // behaving exactly as before -- a multi-select variable still accepts exactly one value
      // via a plain string, unconditionally on display style.
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "Reasons", XSchema.STRING,
         new Object[] {"Item defective", "No longer needed"}, UserVariable.CHECKBOXES);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV5"), any())).thenReturn(session("TOK-SVV5"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-SVV5",
         setVariableValuesRequest(Map.of("Reasons", "Item defective")), agent);

      ArgumentCaptor<VariableTable> captor = ArgumentCaptor.forClass(VariableTable.class);
      verify(box).refreshVariableTable(captor.capture());
      assertEquals("Item defective", captor.getValue().get("Reasons"));
   }

   @Test
   void setVariableValuesArrayOfTwoValuesOnCheckboxesVariableSucceeds() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "Reasons", XSchema.STRING,
         new Object[] {"Item defective", "No longer needed", "Other"}, UserVariable.CHECKBOXES);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV6"), any())).thenReturn(session("TOK-SVV6"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-SVV6", setVariableValuesRequest(
         Map.of("Reasons", List.of("Item defective", "No longer needed"))), agent);

      ArgumentCaptor<VariableTable> captor = ArgumentCaptor.forClass(VariableTable.class);
      verify(box).refreshVariableTable(captor.capture());
      Object value = captor.getValue().get("Reasons");
      assertInstanceOf(Object[].class, value, "a genuine multi-value assignment must be an array");
      assertArrayEquals(new Object[] {"Item defective", "No longer needed"}, (Object[]) value);
      assertTrue(captor.getValue().isAsIs("Reasons"),
         "asIs must be set so a downstream ONE_OF condition doesn't re-split each element");
   }

   @Test
   void setVariableValuesArrayOfTwoValuesOnListVariableSucceeds() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "Region", XSchema.STRING,
         new Object[] {"East", "West", "North"}, UserVariable.LIST);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV7"), any())).thenReturn(session("TOK-SVV7"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-SVV7",
         setVariableValuesRequest(Map.of("Region", List.of("East", "West"))), agent);

      ArgumentCaptor<VariableTable> captor = ArgumentCaptor.forClass(VariableTable.class);
      verify(box).refreshVariableTable(captor.capture());
      Object value = captor.getValue().get("Region");
      assertInstanceOf(Object[].class, value);
      assertArrayEquals(new Object[] {"East", "West"}, (Object[]) value);
   }

   @Test
   void setVariableValuesArrayWithOneInvalidTokenIsRejectedNamingThatToken() throws Exception {
      // The literal "each token checked individually, not the whole joined string as one
      // candidate" fix -- the second element is bad, and must be named specifically, not the
      // first element or the whole array.
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "Reasons", XSchema.STRING,
         new Object[] {"Item defective", "No longer needed"}, UserVariable.CHECKBOXES);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV8"), any())).thenReturn(session("TOK-SVV8"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit("TOK-SVV8",
         setVariableValuesRequest(Map.of(
            "Reasons", List.of("Item defective", "Not a real reason"))), agent));
      assertTrue(ex.getMessage().contains("Not a real reason"), ex.getMessage());
      assertFalse(ex.getMessage().contains("Item defective"), ex.getMessage());
   }

   @Test
   void setVariableValuesArrayOnComboboxVariableIsRejected() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "Region", XSchema.STRING,
         new Object[] {"East", "West"}, UserVariable.COMBOBOX);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV9"), any())).thenReturn(session("TOK-SVV9"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit("TOK-SVV9",
         setVariableValuesRequest(Map.of("Region", List.of("East", "West"))), agent));
      assertTrue(ex.getMessage().contains("Region"), ex.getMessage());
      assertTrue(ex.getMessage().contains("combobox"), ex.getMessage());
   }

   @Test
   void setVariableValuesSingleElementArrayTreatedAsScalarRegardlessOfStyle() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "Region", XSchema.STRING,
         new Object[] {"East", "West"}, UserVariable.COMBOBOX);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV10"), any())).thenReturn(session("TOK-SVV10"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-SVV10",
         setVariableValuesRequest(Map.of("Region", List.of("East"))), agent);

      ArgumentCaptor<VariableTable> captor = ArgumentCaptor.forClass(VariableTable.class);
      verify(box).refreshVariableTable(captor.capture());
      assertEquals("East", captor.getValue().get("Region"),
         "a 1-element array must be unwrapped to a scalar, not rejected as multi-value-on-combobox");
   }

   @Test
   void setVariableValuesEmptyArrayIsRejected() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      variableAssemblyForValues(ws, "Region", XSchema.STRING);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV11"), any())).thenReturn(session("TOK-SVV11"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit("TOK-SVV11",
         setVariableValuesRequest(Map.of("Region", List.of())), agent));
      assertTrue(ex.getMessage().contains("Region"), ex.getMessage());
   }

   @Test
   void setVariableValuesArrayWithNonStringElementIsRejected() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "Reasons", XSchema.STRING,
         new Object[] {"Item defective", "No longer needed"}, UserVariable.CHECKBOXES);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV12"), any())).thenReturn(session("TOK-SVV12"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      List<Object> reasons = new ArrayList<>();
      reasons.add("Item defective");
      reasons.add(42);

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit("TOK-SVV12",
         setVariableValuesRequest(Map.of("Reasons", reasons)), agent));
      assertTrue(ex.getMessage().contains("Integer"), ex.getMessage());
   }

   @Test
   void setVariableValuesUnknownVariableStillRejectedWithArrayValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV13"), any())).thenReturn(session("TOK-SVV13"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit("TOK-SVV13",
         setVariableValuesRequest(Map.of(
            "TotallyNonexistentVar", List.of("a", "b"))), agent));
      assertTrue(ex.getMessage().contains("TotallyNonexistentVar"), ex.getMessage());
   }

   // ---------------------------------------------------------------------------
   // Bug #78397: strict value validation for worksheet variables (D1/D2/defaultValue)
   // ---------------------------------------------------------------------------

   private WorksheetAgentController strictVariableController(
      Worksheet ws, AssetQuerySandbox box, String token) throws Exception
   {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq(token), any())).thenReturn(session(token));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class),
         mock(InnerJoinService.class));

      return controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));
   }

   @ParameterizedTest
   @CsvSource({
      "integer, 3000000000",
      "integer, 1.5",
      "short, 40000",
      "byte, 300",
      "long, 9223372036854775808",
      "date, 2026-02-30",
      "date, 2026-13-45",
      "timeInstant, 2026-02-30 10:00:00",
      "time, 25:61:00",
      "double, 0x10",
      "float, 1e50",
      "boolean, maybe",
      "integer, abc"
   })
   void setVariableValuesRefusesValueTheStrictParserRejects(String type, String bad)
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      variableAssemblyForValues(ws, "wVar", type);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = strictVariableController(ws, box, "TOK-SVS1");

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVS1", setVariableValuesRequest(Map.of("wVar", bad)), agent));
      assertTrue(ex.getMessage().contains("wVar"), ex.getMessage());
      assertTrue(ex.getMessage().contains(bad), ex.getMessage());
      verify(box, never()).refreshVariableTable(any());
   }

   @ParameterizedTest
   @CsvSource({"integer", "double", "date"})
   void setVariableValuesRefusesBlankForNonStringType(String type) throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      variableAssemblyForValues(ws, "wVar", type);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = strictVariableController(ws, box, "TOK-SVS1B");

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVS1B", setVariableValuesRequest(Map.of("wVar", "")), agent));
      assertTrue(ex.getMessage().contains("wVar"), ex.getMessage());
      verify(box, never()).refreshVariableTable(any());
   }

   @Test
   void setVariableValuesListElementIsValidatedAndNamedWithItsIndex() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "wMDbl", XSchema.DOUBLE,
         new Object[] {1.0, 2.0}, UserVariable.CHECKBOXES);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = strictVariableController(ws, box, "TOK-SVS2");

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVS2", setVariableValuesRequest(Map.of("wMDbl", List.of("1", "0x10"))), agent));
      assertTrue(ex.getMessage().contains("wMDbl"), ex.getMessage());
      assertTrue(ex.getMessage().contains("[1]"), ex.getMessage());
      assertTrue(ex.getMessage().contains("0x10"), ex.getMessage());
      verify(box, never()).refreshVariableTable(any());
   }

   @Test
   void setVariableValuesStillAcceptsValidTypedValuesAndNullClear() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      variableAssemblyForValues(ws, "wDate", XSchema.DATE);
      variableAssemblyForValues(ws, "wDbl", XSchema.DOUBLE);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      WorksheetAgentController ctrl = strictVariableController(ws, box, "TOK-SVS3");

      java.util.Map<String, Object> req = new java.util.HashMap<>();
      req.put("wDate", "2026-03-01");
      req.put("wDbl", "1.5");
      ctrl.edit("TOK-SVS3", setVariableValuesRequest(req), agent);

      ArgumentCaptor<VariableTable> captor = ArgumentCaptor.forClass(VariableTable.class);
      verify(box).refreshVariableTable(captor.capture());
      assertEquals(1.5, captor.getValue().get("wDbl"));
      assertTrue(captor.getValue().get("wDate") instanceof java.util.Date);

      java.util.Map<String, Object> clear = new java.util.HashMap<>();
      clear.put("wDbl", null);
      ctrl.edit("TOK-SVS3", setVariableValuesRequest(clear), agent);
      verify(box, times(2)).refreshVariableTable(any());
   }

   /**
    * A worksheet whose tables reference the given variables: stands in for a SQL table with a
    * {@code $(name)} condition, which is what makes {@code getAllVariables} list a variable.
    */
   private static Worksheet worksheetReferencing(String... names) {
      return new Worksheet() {
         @Override
         public synchronized UserVariable[] getAllVariables() {
            return java.util.Arrays.stream(names).map(UserVariable::new)
               .toArray(UserVariable[]::new);
         }
      };
   }

   /** Names of declared variables still reported as needing a value, as the warning path sees them. */
   private static List<String> unsetVariableNames(AssetQuerySandbox box) {
      return java.util.Arrays.stream(box.getAllVariables(box.getVariableTable()))
         .map(v -> v.getName()).toList();
   }

   // Bug #79083: a top-level null must clear the variable back to "no session value yet", i.e.
   // remove the key from the sandbox table, not leave key -> null (which hides the variable from
   // getAllVariables and so suppresses the "no session value yet ... dropped" warning).
   @Test
   void setVariableValuesNullClearsKeyAndMatchesNeverSet() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = worksheetReferencing("pState", "pCity");
      variableAssemblyForValues(ws, "pState", XSchema.STRING);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      WorksheetAgentController ctrl = strictVariableController(ws, box, "TOK-SVC1");

      List<String> neverSet = unsetVariableNames(box);
      assertTrue(neverSet.contains("pState"), "a never-set variable is reported as needing a value");

      ctrl.edit("TOK-SVC1", setVariableValuesRequest(Map.of("pState", "x")), agent);
      assertEquals("x", box.getVariableTable().get("pState"));
      assertFalse(unsetVariableNames(box).contains("pState"));

      java.util.Map<String, Object> clear = new java.util.HashMap<>();
      clear.put("pState", null);
      ctrl.edit("TOK-SVC1", setVariableValuesRequest(clear), agent);

      assertFalse(box.getVariableTable().contains("pState"),
         "null must remove the key, not leave key -> null");
      assertEquals(neverSet, unsetVariableNames(box),
         "a cleared variable must be reported exactly like a never-set one");
   }

   @Test
   void setVariableValuesCanSetAgainAfterClear() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = worksheetReferencing("pState", "pCity");
      variableAssemblyForValues(ws, "pState", XSchema.STRING);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      WorksheetAgentController ctrl = strictVariableController(ws, box, "TOK-SVC2");

      ctrl.edit("TOK-SVC2", setVariableValuesRequest(Map.of("pState", "x")), agent);
      java.util.Map<String, Object> clear = new java.util.HashMap<>();
      clear.put("pState", null);
      ctrl.edit("TOK-SVC2", setVariableValuesRequest(clear), agent);
      ctrl.edit("TOK-SVC2", setVariableValuesRequest(Map.of("pState", "y")), agent);

      assertEquals("y", box.getVariableTable().get("pState"));
      assertFalse(unsetVariableNames(box).contains("pState"));
   }

   @Test
   void setVariableValuesNullClearLeavesOtherVariablesAndEmptyStringIsLiteral() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = worksheetReferencing("pState", "pCity");
      variableAssemblyForValues(ws, "pState", XSchema.STRING);
      variableAssemblyForValues(ws, "pCity", XSchema.STRING);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      WorksheetAgentController ctrl = strictVariableController(ws, box, "TOK-SVC3");

      java.util.Map<String, Object> req = new java.util.HashMap<>();
      req.put("pState", "x");
      req.put("pCity", "");
      ctrl.edit("TOK-SVC3", setVariableValuesRequest(req), agent);
      java.util.Map<String, Object> clear = new java.util.HashMap<>();
      clear.put("pState", null);
      ctrl.edit("TOK-SVC3", setVariableValuesRequest(clear), agent);

      assertFalse(box.getVariableTable().contains("pState"));
      assertTrue(box.getVariableTable().contains("pCity"),
         "an empty string is a literal value, not a clear");
      assertEquals("", box.getVariableTable().get("pCity"));
   }

   // Bug #79307: the "__null__" sentinel, a lone [null] and a lone ["__null__"] are the same
   // clear request as a top-level null.
   @ParameterizedTest
   @CsvSource({"SCALAR", "SENTINEL_LIST", "NULL_LIST"})
   void setVariableValuesSentinelAndLoneNullClearLikeNull(String form) throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = worksheetReferencing("pState", "pCity");
      variableAssemblyForValues(ws, "pState", XSchema.STRING);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      WorksheetAgentController ctrl = strictVariableController(ws, box, "TOK-SVC4A");
      List<String> neverSet = unsetVariableNames(box);

      ctrl.edit("TOK-SVC4A", setVariableValuesRequest(Map.of("pState", "x")), agent);
      assertEquals("x", box.getVariableTable().get("pState"));

      Object clearValue = switch(form) {
         case "SCALAR" -> "__null__";
         case "SENTINEL_LIST" -> List.of("__null__");
         default -> Collections.singletonList(null);
      };
      Map<String, Object> clear = new java.util.HashMap<>();
      clear.put("pState", clearValue);
      ctrl.edit("TOK-SVC4A", setVariableValuesRequest(clear), agent);

      assertFalse(box.getVariableTable().contains("pState"),
         "the sentinel must remove the key, not leave key -> null");
      assertEquals(neverSet, unsetVariableNames(box),
         "a variable cleared with the sentinel must be reported like a never-set one");
   }

   @ParameterizedTest
   @CsvSource(value = {"NY|__null__", "__null__|NY", "__null__|__null__"}, delimiter = '|')
   void setVariableValuesNullInsideMultiValueListIsRefusedAndKeepsPreviousValue(
      String first, String second) throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = worksheetReferencing("pState", "pCity");
      variableAssemblyForValues(ws, "pState", XSchema.STRING)
         .getVariable().setDisplayStyle(inetsoft.uql.schema.UserVariable.CHECKBOXES);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      WorksheetAgentController ctrl = strictVariableController(ws, box, "TOK-SVC4B");

      ctrl.edit("TOK-SVC4B", setVariableValuesRequest(Map.of("pState", "NY")), agent);

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVC4B", setVariableValuesRequest(Map.of("pState", List.of(first, second))), agent));
      int badIndex = "__null__".equals(first) ? 0 : 1;
      assertTrue(ex.getMessage().contains("variableValues['pState'][" + badIndex + "]"),
         ex.getMessage());
      assertTrue(ex.getMessage().contains("pass null alone"), ex.getMessage());
      assertEquals("NY", box.getVariableTable().get("pState"),
         "a refused call must leave the previous value in effect");
   }

   @Test
   void setVariableValuesNullElementInsideMultiValueListIsRefused() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = worksheetReferencing("pState", "pCity");
      variableAssemblyForValues(ws, "pState", XSchema.STRING)
         .getVariable().setDisplayStyle(inetsoft.uql.schema.UserVariable.CHECKBOXES);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      WorksheetAgentController ctrl = strictVariableController(ws, box, "TOK-SVC4C");

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVC4C", setVariableValuesRequest(
            Map.of("pState", java.util.Arrays.asList("NY", null))), agent));
      assertTrue(ex.getMessage().contains("variableValues['pState'][1]"), ex.getMessage());
      assertFalse(box.getVariableTable().contains("pState"));
   }

   // Bug #79307: the empty-list refusal must point at null as the clear and must not claim that
   // omitting the variable unsets it (omitting keeps the current value).
   @Test
   void setVariableValuesEmptyListMessageNamesVariableAndPointsAtNull() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = worksheetReferencing("pState", "pCity");
      variableAssemblyForValues(ws, "pState", XSchema.STRING);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      WorksheetAgentController ctrl = strictVariableController(ws, box, "TOK-SVC4D");

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVC4D", setVariableValuesRequest(Map.of("pState", List.of())), agent));
      assertTrue(ex.getMessage().contains("variableValues['pState']"), ex.getMessage());
      assertTrue(ex.getMessage().contains("pass null"), ex.getMessage());
      assertFalse(ex.getMessage().contains("leave it unset"), ex.getMessage());
   }

   // ---------------------------------------------------------------------------
   // Bug #79307 -- a mirrored variable's value is keyed by the variable's own name
   // ---------------------------------------------------------------------------

   /** A saved worksheet declaring one variable assembly VAR_SRC whose variable is RATE. */
   private static Worksheet rateSourceWorksheet() {
      Worksheet sourceWs = new Worksheet();
      addRateVariable(sourceWs, "VAR_SRC", "RATE");
      return sourceWs;
   }

   private static void addRateVariable(Worksheet ws, String assemblyName, String variableName) {
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, assemblyName);
      AssetVariable var = new AssetVariable(variableName);
      var.setTypeNode(XSchema.createPrimitiveType(XSchema.DOUBLE));
      assembly.setVariable(var);
      ws.addAssembly(assembly);
   }

   private AssetQuerySandbox mirrorBox;

   /** Controller over {@code ws} whose repository serves {@code sourceWs} for any path. */
   private WorksheetAgentController mirrorVariableController(
      String token, Worksheet ws, Worksheet sourceWs) throws Exception
   {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      mirrorBox = new AssetQuerySandbox(ws, null, new VariableTable());
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mirrorBox);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq(token), any())).thenReturn(session(token));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      AssetRepository assetRepository = mock(AssetRepository.class);
      when(assetRepository.getSheet(any(AssetEntry.class), any(), eq(true), eq(AssetContent.ALL),
         eq(false))).thenReturn(sourceWs);
      when(assetRepository.getSheet(any(AssetEntry.class), any(), eq(false), eq(AssetContent.ALL)))
         .thenReturn(sourceWs);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class),
         mock(InnerJoinService.class));

      return controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), editSvc,
         mock(WorksheetService.class), assetRepository);
   }

   private static EditRequest updateMirrorRequest(String table) throws Exception {
      ObjectMapper mapper = new WebConfig().objectMapper();
      return mapper.readValue(
         mapper.writeValueAsString(Map.of("op", "update_mirror", "table", table)),
         EditRequest.class);
   }

   @Test
   void deletingMirroredVariableDropsValueKeyedByVariableName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl =
         mirrorVariableController("TOK-79307A", ws, rateSourceWorksheet());

      ctrl.edit("TOK-79307A", addWorksheetMirrorRequest("M_RATE", "Src/VAR", null), agent);
      assertTrue(ws.getAssembly("M_RATE") instanceof MirrorVariableAssembly);
      mirrorBox.getVariableTable().put("RATE", "0.3");
      mirrorBox.getVariableTable().put("other", "keep");

      ctrl.edit("TOK-79307A", opRequest("delete_table", "M_RATE", null, null), agent);

      assertFalse(mirrorBox.getVariableTable().contains("RATE"),
         "the value keyed by the mirrored variable's own name must go with its last declarer");
      assertEquals("keep", mirrorBox.getVariableTable().get("other"));
   }

   @Test
   void deletingOneOfTwoMirrorsKeepsValueUntilTheLastDeclarerGoes() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl =
         mirrorVariableController("TOK-79307B", ws, rateSourceWorksheet());

      ctrl.edit("TOK-79307B", addWorksheetMirrorRequest("M1", "Src/VAR", null), agent);
      mirrorBox.getVariableTable().put("RATE", "0.3");
      ctrl.edit("TOK-79307B", addWorksheetMirrorRequest("M2", "Src/VAR", null), agent);
      assertEquals("0.3", mirrorBox.getVariableTable().get("RATE"),
         "adding a second declarer of an already declared variable must keep its value");

      ctrl.edit("TOK-79307B", opRequest("delete_table", "M1", null, null), agent);
      assertEquals("0.3", mirrorBox.getVariableTable().get("RATE"),
         "M2 still declares RATE");

      ctrl.edit("TOK-79307B", opRequest("delete_table", "M2", null, null), agent);
      assertFalse(mirrorBox.getVariableTable().contains("RATE"));
   }

   @Test
   void addMirrorDropsAStaleValueForTheVariableItNewlyDeclares() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl =
         mirrorVariableController("TOK-79307C", ws, rateSourceWorksheet());
      mirrorBox.getVariableTable().put("RATE", "0.3");

      ctrl.edit("TOK-79307C", addWorksheetMirrorRequest("M_RATE", "Src/VAR", null), agent);

      assertFalse(mirrorBox.getVariableTable().contains("RATE"),
         "a freshly mirrored variable starts with no session value");
   }

   @Test
   void refusedAddMirrorKeepsTheLiveValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl =
         mirrorVariableController("TOK-79307D", ws, rateSourceWorksheet());
      ctrl.edit("TOK-79307D", addWorksheetMirrorRequest("M_RATE", "Src/VAR", null), agent);
      mirrorBox.getVariableTable().put("RATE", "0.3");

      assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-79307D", addWorksheetMirrorRequest("M_RATE", "Src/VAR", null), agent));

      assertEquals("0.3", mirrorBox.getVariableTable().get("RATE"));
   }

   @Test
   void updateMirrorDropsValueOfVariableTheResyncRemoved() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      Worksheet sourceWs = rateSourceWorksheet();
      WorksheetAgentController ctrl = mirrorVariableController("TOK-79307E", ws, sourceWs);
      ctrl.edit("TOK-79307E", addWorksheetMirrorRequest("M_RATE", "Src/VAR", null), agent);
      mirrorBox.getVariableTable().put("RATE", "0.3");

      // The source now declares the variable under another name; the resync re-copies it.
      sourceWs.removeAssembly("VAR_SRC");
      addRateVariable(sourceWs, "VAR_SRC", "RATE2");
      ctrl.edit("TOK-79307E", updateMirrorRequest("M_RATE"), agent);

      assertFalse(mirrorBox.getVariableTable().contains("RATE"),
         "a variable the resync no longer declares must lose its value");
   }

   @ParameterizedTest
   @CsvSource({
      "date, 2026-02-30",
      "date, 2026-13-45",
      "time, 25:61:00",
      "boolean, maybe",
      "float, 1e50",
      "character, ab"
   })
   void addVariableRefusesDefaultValueTheStrictParserRejects(String type, String bad)
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = strictVariableController(
         ws, mock(AssetQuerySandbox.class), "TOK-SVS4");

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVS4", addVariableRequest("dVar", type, bad), agent));
      assertTrue(ex.getMessage().contains(bad), ex.getMessage());
      assertNull(ws.getAssembly("dVar"));
   }

   @Test
   void addVariableRefusesBlankDefaultOnNumericVariable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = strictVariableController(
         ws, mock(AssetQuerySandbox.class), "TOK-SVS4B");

      assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVS4B", addVariableRequest("dVar", "integer", ""), agent));
      assertNull(ws.getAssembly("dVar"));
   }

   @Test
   void addVariableAcceptsValidDefaultValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = strictVariableController(
         ws, mock(AssetQuerySandbox.class), "TOK-SVS5");

      ctrl.edit("TOK-SVS5", addVariableRequest("dVar", "date", "2026-03-01"), agent);
      assertNotNull(ws.getAssembly("dVar"));
   }

   private static DefaultVariableAssembly variableAssemblyForValues(
      Worksheet ws, String name, String type)
   {
      AssetVariable var = new AssetVariable(name);
      var.setTypeNode(XSchema.createPrimitiveType(type));
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, name);
      assembly.setVariable(var);
      ws.addAssembly(assembly);
      return assembly;
   }

   // ---------------------------------------------------------------------------
   // setProperties (L2-Group9)
   // ---------------------------------------------------------------------------

   @ParameterizedTest
   @CsvSource({
      "'Bad/Alias', does not allow",
      "'Bad<Alias>', does not allow",
      "'_LeadingUnderscore', letter or digit",
      // Review finding on PR #4920: Character.isLetterOrDigit is Unicode-aware and would accept
      // a Cyrillic/Greek/Hangul/etc. leading character, which the Angular validator this method
      // mirrors (assetNameStartWithCharDigit) never allowed -- only ASCII letters/digits, Latin-1
      // Supplement + Latin Extended-A, and CJK Unified Ideographs.
      "'Пример', letter or digit",
   })
   void setPropertiesRejectsInvalidAlias(String badAlias, String expectedFragment)
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SP1"), any())).thenReturn(session("TOK-SP1"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // L2-Group9: assetEntryBannedCharacters/assetNameStartWithCharDigit are Angular-only --
      // not even WorksheetPropertyDialogService.process() re-validated before this fix.
      PairingException ex = assertThrows(PairingException.class, () -> ctrl.setProperties(
         "TOK-SP1", new WorksheetAgentController.WorksheetPropertiesRequest(badAlias, null),
         agent));
      assertTrue(ex.getMessage().toLowerCase().contains(expectedFragment.toLowerCase()),
         ex.getMessage());
      assertNull(ws.getWorksheetInfo().getAlias(),
         "a rejected alias must not have been applied");
   }

   @Test
   void setPropertiesAcceptsValidAlias() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SP2"), any())).thenReturn(session("TOK-SP2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.setProperties("TOK-SP2",
         new WorksheetAgentController.WorksheetPropertiesRequest("Good Alias 1", null), agent);

      assertEquals("Good Alias 1", ws.getWorksheetInfo().getAlias());
   }

   /**
    * Review finding on PR #4920: the fixed start-character check must still accept everything
    * the Angular validator (assetNameStartWithCharDigit) allows -- CJK Unified Ideographs and
    * the Latin-1 Supplement/Latin Extended-A block -- not just plain ASCII.
    */
   @ParameterizedTest
   @CsvSource({
      "'报表别名'",
      "'Ā_LatinExtendedA'",
   })
   void setPropertiesAcceptsNonAsciiAliasWithinAllowedRanges(String goodAlias) throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SP3"), any())).thenReturn(session("TOK-SP3"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.setProperties("TOK-SP3",
         new WorksheetAgentController.WorksheetPropertiesRequest(goodAlias, null), agent);

      assertEquals(goodAlias, ws.getWorksheetInfo().getAlias());
   }

   // ---------------------------------------------------------------------------
   // importCsv / importExcel
   // ---------------------------------------------------------------------------

   private static WorksheetAgentController importController(String token, RuntimeWorksheet rws) throws Exception {
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq(token), any())).thenReturn(session(token));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      return controller(featureOn(), mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));
   }

   @Test
   void importCsvCreatesEmbeddedTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      WorksheetAgentController ctrl = importController("TOK-CSV", rws);

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv(
         "TOK-CSV", new WorksheetAgentController.ImportCsvRequest("Imported", "a,b\n1,x\n2,y"), agent);

      assertEquals("Imported", resp.tableName());
      assertEquals(2, resp.rows());
      assertEquals(2, resp.columns());
      assertNotNull(ws.getAssembly("Imported"));
   }

   /**
    * PR #4901 round-2 review follow-up: an audit for other bypasses of the round-1
    * {@code requireStorableName} guard (added for {@code placeAssembly}/{@code renameTable}/
    * {@code addJoin}/{@code duplicateAssembly}/{@code renameVariable}) turned up a third site:
    * {@code createEmbeddedTable}'s {@code name} param -- the import's caller-supplied table
    * name -- is used verbatim and, unlike {@code add_table}'s datasource-bound paths, is never
    * run through {@code AssetUtil.normalizeTable} either. {@code AssemblyInfo:254} writes the
    * name into a CDATA section verbatim, so a name containing the terminator closes it early and
    * leaves malformed XML in storage.
    */
   @Test
   void importCsvRefusesANameThatWouldBreakTheStoredXml() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      WorksheetAgentController ctrl = importController("TOK-CSV-CDATA", rws);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.importCsv("TOK-CSV-CDATA",
            new WorksheetAgentController.ImportCsvRequest("bad]]>name", "a,b\n1,x\n2,y"), agent));
      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      assertNull(ws.getAssembly("bad]]>name"));
   }

   /**
    * Bug #76788 (WBS-065): {@code createEmbeddedTable}'s caller-supplied {@code name} is used
    * verbatim and, like {@code addJoin}, never checked against an existing, unrelated assembly
    * before {@link Worksheet#addAssembly} silently evicts and replaces it. A second, non-replace
    * {@code import_csv_table} call naming a pre-existing table must be rejected rather than
    * silently destroying it -- this is distinct from the intentional in-place {@code replaceTable}
    * path exercised by {@code importCsvReplaceTableOverwritesAnExistingEmbeddedTableInPlace}.
    */
   @Test
   void importCsvRejectsNameCollisionWithUnrelatedExistingAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-CSV-COLLIDE");
      Principal agent = TestPrincipals.user("alice", "host-org");

      ctrl.importCsv("TOK-CSV-COLLIDE",
         new WorksheetAgentController.ImportCsvRequest("Existing", "a,b\n1,x\n2,y"), agent);
      EmbeddedTableAssembly original = importedTable(ws, "Existing");

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.importCsv("TOK-CSV-COLLIDE",
            new WorksheetAgentController.ImportCsvRequest("Existing", "a,b\n9,z"), agent));
      assertTrue(ex.getMessage().contains("Existing"), ex.getMessage());

      assertSame(original, ws.getAssembly("Existing"));
      assertEquals(1, ws.getAssemblies().length);
   }

   /**
    * The import settings the Composer's Import Data File dialog exposes, now reachable through the
    * agent too. Before this, the CSV route had its own hand-rolled parser that could honour none of
    * them -- the origin of a cluster of L2 findings. These assert the settings actually reach
    * {@code CSVLoader}, the loader the dialog itself uses.
    */
   private static WorksheetAgentController.ImportCsvRequest csvRequest(
      String name, String csv, String encoding, String delimiter, Boolean tab, Boolean detectType,
      Boolean firstRow, Boolean removeQuotes, Boolean unpivot, Integer headerCols)
   {
      return new WorksheetAgentController.ImportCsvRequest(
         name, csv, encoding, delimiter, tab, detectType, firstRow, removeQuotes, unpivot,
         headerCols, null);
   }

   private static EmbeddedTableAssembly importedTable(Worksheet ws, String name) {
      Assembly a = ws.getAssembly(name);
      assertNotNull(a, "expected an imported table named " + name);
      assertInstanceOf(EmbeddedTableAssembly.class, a);
      return (EmbeddedTableAssembly) a;
   }

   private static WorksheetAgentController importCtrl(Worksheet ws, String token) throws Exception {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));
      return importController(token, rws);
   }

   /**
    * The guard the owner asked for explicitly: an agent-imported table must stay a plain
    * {@link EmbeddedTableAssembly}. The dialog builds a {@link SnapshotEmbeddedTableAssembly} from
    * the same loader, and re-importing through this route is the documented way to get an editable
    * copy of snapshot data -- so copying the dialog's choice here would quietly delete the only
    * workaround L2 Finding 5 has.
    */
   @Test
   void importCsvStaysAPlainEmbeddedTableRatherThanASnapshot() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-PLAIN");

      ctrl.importCsv("TOK-PLAIN",
         new WorksheetAgentController.ImportCsvRequest("Plain", "a,b\n1,x"),
         TestPrincipals.user("alice", "host-org"));

      EmbeddedTableAssembly t = importedTable(ws, "Plain");
      assertFalse(t instanceof SnapshotEmbeddedTableAssembly,
         "an agent import must remain editable; a snapshot refuses edit_cell/insert_row/delete_row");
   }

   /**
    * Bug #76080: the agent had no way to replace an existing table's data, only create new
    * ones. Replacing must reuse the same assembly object rather than swap in a new one under
    * the same name -- {@link Worksheet#addAssembly} would do that silently -- so anything built
    * on top of the table (a join, a filter, a chart binding) keeps pointing at something real.
    */
   @Test
   void importCsvReplaceTableOverwritesAnExistingEmbeddedTableInPlace() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-REPLACE");
      Principal agent = TestPrincipals.user("alice", "host-org");

      ctrl.importCsv("TOK-REPLACE",
         new WorksheetAgentController.ImportCsvRequest("Query1", "a,b\n1,x\n2,y"), agent);
      EmbeddedTableAssembly before = importedTable(ws, "Query1");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv("TOK-REPLACE",
         new WorksheetAgentController.ImportCsvRequest(
            null, "a,b\n9,z", null, null, null, null, null, null, null, null, "Query1"),
         agent);

      assertEquals("Query1", resp.tableName());
      assertEquals(1, resp.rows());
      assertEquals(1, ws.getAssemblies().length, "replacing must not leave a second table behind");

      EmbeddedTableAssembly after = importedTable(ws, "Query1");
      assertSame(before, after,
         "a replace must keep the same assembly instance, not swap in a new one under the same name");
      assertEquals("9", String.valueOf(after.getEmbeddedData().getObject(1, 0)),
         "the new file's data must have landed on the table");
   }

   // ---------------------------------------------------------------------------
   // Bug #78526: an import capped at the organization's column limit says so.
   // ---------------------------------------------------------------------------

   /** A CSV line of {@code n} fields: {@code prefix1,prefix2,...}. */
   private static String csvFields(String prefix, int n) {
      StringBuilder line = new StringBuilder();

      for(int i = 1; i <= n; i++) {
         line.append(i > 1 ? "," : "").append(prefix).append(i);
      }

      return line.toString();
   }

   /** {@code ncols} columns C1..Cn, three data rows. */
   private static String wideCsv(int ncols) {
      return csvFields("C", ncols) + "\n" + csvFields("a", ncols) + "\n" +
         csvFields("b", ncols) + "\n" + csvFields("c", ncols);
   }

   @Test
   void importCsvOverTheColumnLimitReportsTheDroppedColumns() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78526-A");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv("TOK-78526-A",
         new WorksheetAgentController.ImportCsvRequest("Over", wideCsv(210)),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(200, resp.columns());
      assertEquals(3, resp.rows());
      assertEquals(new WorksheetPreviewService.ColumnLimit(true, 200, 210), resp.columnLimit());
   }

   @Test
   void importCsvAtTheColumnLimitReportsNothing() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78526-B");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv("TOK-78526-B",
         new WorksheetAgentController.ImportCsvRequest("AtLimit", wideCsv(200)),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(200, resp.columns());
      assertNull(resp.columnLimit());
      assertFalse(new ObjectMapper().writeValueAsString(resp).contains("columnLimit"),
         "an import that dropped nothing must not carry the field at all");
   }

   /**
    * One wide data row under a narrow header sets the loader's own exceedLimit flag, but no
    * column was dropped from the table: the extra fields are the plugin's rowsWithExtraFields
    * audit's to report.
    */
   @Test
   void importCsvWithOneWideRowUnderANarrowHeaderReportsNothing() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78526-C");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv("TOK-78526-C",
         new WorksheetAgentController.ImportCsvRequest(
            "Narrow", csvFields("C", 10) + "\n" + csvFields("a", 250) + "\n" + csvFields("b", 10)),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(10, resp.columns());
      assertNull(resp.columnLimit());
   }

   /** The loader skips leading blank lines before the header; the uncapped count must too. */
   @Test
   void importCsvWithLeadingBlankLinesStillReportsTheDroppedColumns() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78526-D");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv("TOK-78526-D",
         new WorksheetAgentController.ImportCsvRequest("Blank", "\n\n" + wideCsv(210)),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(200, resp.columns());
      assertEquals(new WorksheetPreviewService.ColumnLimit(true, 200, 210), resp.columnLimit());
   }

   @Test
   void importCsvFileAndReplaceReportTheDroppedColumnsToo() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78526-E");
      ctrl.importCsv("TOK-78526-E",
         new WorksheetAgentController.ImportCsvRequest("Query1", "a,b\n1,x"),
         TestPrincipals.user("alice", "host-org"));

      WorksheetAgentController.ImportCsvResponse file =
         replaceCsvFile(ctrl, "TOK-78526-E", "Query1", wideCsv(205));
      assertEquals(new WorksheetPreviewService.ColumnLimit(true, 200, 205), file.columnLimit());

      WorksheetAgentController.ImportCsvResponse inline =
         replaceCsv(ctrl, "TOK-78526-E", "Query1", wideCsv(201));
      assertEquals("Query1", inline.tableName());
      assertEquals(200, inline.columns());
      assertEquals(new WorksheetPreviewService.ColumnLimit(true, 200, 201), inline.columnLimit());
   }

   /** Unpivot reshapes the columns; the limit is about the file's own columns, before that. */
   @Test
   void importCsvUnpivotReportsTheSourceColumnsDropped() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78526-F");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv("TOK-78526-F",
         csvRequest("Pivot", wideCsv(210), null, null, null, null, null, null, true, 1),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(new WorksheetPreviewService.ColumnLimit(true, 200, 210), resp.columnLimit());
   }

   /**
    * Fakes the Excel reader: {@code importHeader} returns {@code cappedCols} columns when called
    * with the column limit and {@code totalCols} when called without one (-1), the way the XLSX
    * reader behaves; an XLS reader ignores the limit, so both are its full width.
    */
   private static WorksheetAgentController.ImportCsvResponse importExcelWithWidths(
      WorksheetAgentController ctrl, String token, String fileType, int cappedCols,
      int totalCols, inetsoft.uql.util.filereader.ExcelFileReader reader) throws Exception
   {
      inetsoft.uql.schema.XTypeNode capped = excelMeta(cappedCols);
      inetsoft.uql.schema.XTypeNode total = excelMeta(totalCols);
      inetsoft.uql.XTableNode data = mock(inetsoft.uql.XTableNode.class);
      when(data.next()).thenReturn(true, false);
      when(data.getObject(anyInt())).thenReturn("v");

      when(reader.importHeader(any(), any(), any(), anyInt(), anyInt())).thenReturn(capped);
      when(reader.importHeader(any(), any(), any(), anyInt(), eq(-1))).thenReturn(total);
      when(reader.read(any(), any(), any(), any(), anyInt(), anyInt(), anyBoolean(), any(),
                       anyBoolean())).thenReturn(data);
      inetsoft.uql.util.filereader.ExcelFileSupport support =
         mock(inetsoft.uql.util.filereader.ExcelFileSupport.class);
      when(support.createXLSXReader()).thenReturn(reader);
      when(support.createXLSReader()).thenReturn(reader);

      try(MockedStatic<inetsoft.uql.util.filereader.ExcelFileSupport> st =
             mockStatic(inetsoft.uql.util.filereader.ExcelFileSupport.class))
      {
         st.when(inetsoft.uql.util.filereader.ExcelFileSupport::getInstance).thenReturn(support);
         return ctrl.importExcel(token, excelFile(new byte[]{1, 2, 3}), fileType, null, "Sheet",
                                 null, null, null, TestPrincipals.user("alice", "host-org"));
      }
   }

   private static inetsoft.uql.schema.XTypeNode excelMeta(int ncols) {
      inetsoft.uql.schema.XTypeNode meta = mock(inetsoft.uql.schema.XTypeNode.class);
      when(meta.getChildCount()).thenReturn(ncols);

      for(int c = 0; c < ncols; c++) {
         inetsoft.uql.schema.XTypeNode col = mock(inetsoft.uql.schema.XTypeNode.class);
         when(col.getName()).thenReturn("C" + (c + 1));
         when(col.getType()).thenReturn(inetsoft.uql.schema.XSchema.STRING);
         when(meta.getChild(c)).thenReturn(col);
      }

      return meta;
   }

   @Test
   void importExcelOverTheColumnLimitReportsTheDroppedColumns() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78526-G");
      inetsoft.uql.util.filereader.ExcelFileReader reader =
         mock(inetsoft.uql.util.filereader.ExcelFileReader.class);

      WorksheetAgentController.ImportCsvResponse resp =
         importExcelWithWidths(ctrl, "TOK-78526-G", "XLSX", 200, 250, reader);

      assertEquals(200, resp.columns());
      assertEquals(new WorksheetPreviewService.ColumnLimit(true, 200, 250), resp.columnLimit());
   }

   /** The XLS reader never applies the limit, so a 210-column sheet keeps all 210. */
   @Test
   void importExcelThatKeptEveryColumnReportsNothing() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78526-H");
      inetsoft.uql.util.filereader.ExcelFileReader reader =
         mock(inetsoft.uql.util.filereader.ExcelFileReader.class);

      WorksheetAgentController.ImportCsvResponse resp =
         importExcelWithWidths(ctrl, "TOK-78526-H", "XLS", 210, 210, reader);

      assertEquals(210, resp.columns());
      assertNull(resp.columnLimit());
   }

   /** Under the limit nothing can have been dropped, so the sheet is not read a second time. */
   @Test
   void importExcelUnderTheColumnLimitDoesNotRecountTheSheet() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78526-I");
      inetsoft.uql.util.filereader.ExcelFileReader reader =
         mock(inetsoft.uql.util.filereader.ExcelFileReader.class);

      WorksheetAgentController.ImportCsvResponse resp =
         importExcelWithWidths(ctrl, "TOK-78526-I", "XLSX", 12, 12, reader);

      assertNull(resp.columnLimit());
      verify(reader, never()).importHeader(any(), any(), any(), anyInt(), eq(-1));
   }

   // ---------------------------------------------------------------------------
   // Bug #78346: replaceTable must not retype a join key into an unmergeable pair.
   // ---------------------------------------------------------------------------

   private static void addTestJoin(Worksheet ws, String join, String left, String leftKey,
                                   String right, String rightKey) throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-JN"), any())).thenReturn(session("TOK-JN"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);
      SecurityEngine se = mock(SecurityEngine.class);
      when(se.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), se, mock(InnerJoinService.class));
      svc.apply("TOK-JN", agent,
                ed -> ed.addJoin(join, left, leftKey, right, rightKey, "INNER", null, null));
   }

   private static WorksheetAgentController.ImportCsvResponse replaceCsv(
      WorksheetAgentController ctrl, String token, String table, String csv) throws Exception
   {
      return ctrl.importCsv(token, new WorksheetAgentController.ImportCsvRequest(
         null, csv, null, null, null, null, null, null, null, null, table),
         TestPrincipals.user("alice", "host-org"));
   }

   /** Q1(id integer, v) joined on id to Q2(k integer) through join J. */
   private static WorksheetAgentController joinedImport(Worksheet ws, String token)
      throws Exception
   {
      WorksheetAgentController ctrl = importCtrl(ws, token);
      Principal agent = TestPrincipals.user("alice", "host-org");
      ctrl.importCsv(token, new WorksheetAgentController.ImportCsvRequest(
         "Q1", "id,v\n1,a\n2,b"), agent);
      ctrl.importCsv(token, new WorksheetAgentController.ImportCsvRequest(
         "Q2", "k\n1\n2"), agent);
      addTestJoin(ws, "J", "Q1", "id", "Q2", "k");
      return ctrl;
   }

   private static String typeOf(Worksheet ws, String table, String col) {
      return importedTable(ws, table).getColumnSelection(false).getAttribute(col).getDataType();
   }

   @Test
   void importReplaceRefusesRetypingAJoinedKeyAndLeavesTheTableUntouched() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78346-A");

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78346-A", "Q1", "id,v\nx1,a\nx2,b"));

      assertTrue(ex.getMessage().contains("join \"J\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("integer to string"), ex.getMessage());
      assertTrue(ex.getMessage().contains("re-detects"), ex.getMessage());
      assertFalse(ex.getMessage().contains("stringColumns"), ex.getMessage());
      assertEquals("integer", typeOf(ws, "Q1", "id"));
      assertEquals("1", String.valueOf(importedTable(ws, "Q1").getEmbeddedData().getObject(1, 0)));
      assertNotNull(ws.getAssembly("J"));
   }

   @Test
   void importReplaceAllowsUnchangedMergeableAndNonKeyRetypes() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78346-B");

      replaceCsv(ctrl, "TOK-78346-B", "Q1", "id,v\n7,z");
      replaceCsv(ctrl, "TOK-78346-B", "Q1", "id,v\n7,5");
      assertEquals("integer", typeOf(ws, "Q1", "v"));
      replaceCsv(ctrl, "TOK-78346-B", "Q1", "id,v\n1.5,5");
      assertEquals("double", typeOf(ws, "Q1", "id"));
   }

   @Test
   void importReplaceShapeChangeKeepingTheKeyTypeIsAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78346-C");

      replaceCsv(ctrl, "TOK-78346-C", "Q1", "id,w,x\n1,a,b");

      assertEquals(3, importedTable(ws, "Q1").getColumnSelection(false).getAttributeCount());
   }

   @Test
   void importReplaceShapeChangeRetypingTheKeyByHeaderIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78346-D");

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78346-D", "Q1", "id,w\nx,y"));

      assertTrue(ex.getMessage().contains("\"id\""), ex.getMessage());
      assertEquals(2, importedTable(ws, "Q1").getColumnSelection(false).getAttributeCount());
      assertNotNull(importedTable(ws, "Q1").getColumnSelection(false).getAttribute("v"));
   }

   @Test
   void importReplaceOfAnExplicitlyRetypedStringKeyPointsAtStringColumns() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78346-E");
      // change_column_type made both sides string (a mergeable pair); the importer would
      // re-detect integer for the numeric-looking values.
      ((ColumnRef) importedTable(ws, "Q1").getColumnSelection(false).getAttribute("id"))
         .setDataType("string");
      ((ColumnRef) importedTable(ws, "Q2").getColumnSelection(false).getAttribute("k"))
         .setDataType("string");

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78346-E", "Q1", "id,v\n1,a"));

      assertTrue(ex.getMessage().contains("string to integer"), ex.getMessage());
      assertTrue(ex.getMessage().contains("discarded"), ex.getMessage());
      assertTrue(ex.getMessage().contains("stringColumns"), ex.getMessage());
      assertEquals("string", typeOf(ws, "Q1", "id"));
   }

   @Test
   void importReplaceListsEveryBrokenKeyColumn() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78346-F");
      Principal agent = TestPrincipals.user("alice", "host-org");
      ctrl.importCsv("TOK-78346-F", new WorksheetAgentController.ImportCsvRequest(
         "Q1", "a,b\n1,2"), agent);
      ctrl.importCsv("TOK-78346-F", new WorksheetAgentController.ImportCsvRequest(
         "Q2", "k\n1"), agent);
      ctrl.importCsv("TOK-78346-F", new WorksheetAgentController.ImportCsvRequest(
         "Q3", "m\n1"), agent);
      addTestJoin(ws, "J1", "Q1", "a", "Q2", "k");
      addTestJoin(ws, "J2", "Q1", "b", "Q3", "m");

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78346-F", "Q1", "a,b\nx,y"));

      assertTrue(ex.getMessage().contains("\"a\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("\"b\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("J1") && ex.getMessage().contains("J2"),
         ex.getMessage());
   }

   @Test
   void importReplaceWithoutAJoinAllowsAnyRetype() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78346-G");
      ctrl.importCsv("TOK-78346-G", new WorksheetAgentController.ImportCsvRequest(
         "Q1", "id,v\n1,a"), TestPrincipals.user("alice", "host-org"));

      replaceCsv(ctrl, "TOK-78346-G", "Q1", "id,v\nx,a");

      assertEquals("string", typeOf(ws, "Q1", "id"));
   }

   // ---------------------------------------------------------------------------
   // Bug #78357: replaceTable must not drop a column another table still uses.
   // ---------------------------------------------------------------------------

   private static void applyEd(Worksheet ws, ThrowingConsumer<
      WorksheetEditService.Editor> mutation) throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ED"), any())).thenReturn(session("TOK-ED"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);
      SecurityEngine se = mock(SecurityEngine.class);
      when(se.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      WorksheetEditService svc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), se, mock(InnerJoinService.class));
      svc.apply("TOK-ED", agent, mutation);
   }

   private static WorksheetAgentController.ImportCsvResponse replaceCsvFile(
      WorksheetAgentController ctrl, String token, String table, String csv) throws Exception
   {
      return ctrl.importCsvFile(token,
         new MockMultipartFile("file", "d.csv", "text/csv", csv.getBytes()),
         null, null, null, null, null, null, null, null, null, table, null, null,
         TestPrincipals.user("alice", "host-org"));
   }

   /**
    * The XLSX reader lives in a module the core test classpath does not have, so the reader is
    * faked: a header row and one data row, which is all {@code importExcel} consumes before it
    * hands the table to the same {@code replaceEmbeddedTable} the CSV routes use.
    */
   private static WorksheetAgentController.ImportCsvResponse replaceExcel(
      WorksheetAgentController ctrl, String token, String table, String[] headers,
      Object[] row) throws Exception
   {
      inetsoft.uql.schema.XTypeNode meta = mock(inetsoft.uql.schema.XTypeNode.class);
      when(meta.getChildCount()).thenReturn(headers.length);

      for(int c = 0; c < headers.length; c++) {
         inetsoft.uql.schema.XTypeNode col = mock(inetsoft.uql.schema.XTypeNode.class);
         when(col.getName()).thenReturn(headers[c]);
         when(col.getType()).thenReturn(row[c] instanceof Integer
            ? inetsoft.uql.schema.XSchema.INTEGER : inetsoft.uql.schema.XSchema.STRING);
         when(meta.getChild(c)).thenReturn(col);
      }

      inetsoft.uql.XTableNode data = mock(inetsoft.uql.XTableNode.class);
      when(data.next()).thenReturn(true, false);

      for(int c = 0; c < row.length; c++) {
         when(data.getObject(c)).thenReturn(row[c]);
      }

      inetsoft.uql.util.filereader.ExcelFileReader reader =
         mock(inetsoft.uql.util.filereader.ExcelFileReader.class);
      when(reader.importHeader(any(), any(), any(), anyInt(), anyInt())).thenReturn(meta);
      when(reader.read(any(), any(), any(), any(), anyInt(), anyInt(), anyBoolean(), any(),
                       anyBoolean())).thenReturn(data);
      inetsoft.uql.util.filereader.ExcelFileSupport support =
         mock(inetsoft.uql.util.filereader.ExcelFileSupport.class);
      when(support.createXLSXReader()).thenReturn(reader);

      try(MockedStatic<inetsoft.uql.util.filereader.ExcelFileSupport> st =
             mockStatic(inetsoft.uql.util.filereader.ExcelFileSupport.class))
      {
         st.when(inetsoft.uql.util.filereader.ExcelFileSupport::getInstance).thenReturn(support);
         return ctrl.importExcel(token, excelFile(new byte[]{1, 2, 3}), "XLSX", null, null, table,
                                 null, null, TestPrincipals.user("alice", "host-org"));
      }
   }

   /** Q1(id, v) and its join J must be exactly as {@link #joinedImport} left them. */
   private static void assertJoinedQ1Untouched(Worksheet ws) {
      ColumnSelection cols = importedTable(ws, "Q1").getColumnSelection(false);
      assertEquals(2, cols.getAttributeCount());
      assertNotNull(cols.getAttribute("id"));
      assertNotNull(cols.getAttribute("v"));
      assertEquals("1", String.valueOf(importedTable(ws, "Q1").getEmbeddedData().getObject(1, 0)));
      assertNotNull(ws.getAssembly("J"));
   }

   @Test
   void importReplaceRefusesDroppingAJoinedKeyColumnAndLeavesTheWorksheetUntouched()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78357-A");

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78357-A", "Q1", "other,v\n1,a"));

      assertTrue(ex.getMessage().contains("'J'"), ex.getMessage());
      assertTrue(ex.getMessage().contains("\"id\""), ex.getMessage());
      assertJoinedQ1Untouched(ws);
   }

   @Test
   void importReplaceRefusesDroppingAColumnUsedByDownstreamConditionExpressionAndAggregate()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78357-B");
      ctrl.importCsv("TOK-78357-B", new WorksheetAgentController.ImportCsvRequest(
         "Q1", "id,v,w\n1,a,2\n2,b,3"), TestPrincipals.user("alice", "host-org"));
      applyEd(ws, ed -> {
         ed.addMirror("M1", "Q1");
         ed.addMirror("M2", "Q1");
         ed.addMirror("M3", "Q1");
      });
      applyEd(ws, ed -> {
         ed.addFilter("M1", "v", "=", "a");
         ed.addExpressionColumn("M2", "x", "field['w']*2", "double", false);
         ed.setGroupAggregate("M3", List.of(new WorksheetMutationSupport.GroupSpec("v")),
            List.of(new WorksheetMutationSupport.AggregateSpec("w", "Sum", null)), false, false);
      });

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78357-B", "Q1", "id\n1"));

      assertTrue(ex.getMessage().contains("'M1'"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'M2'"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'M3'"), ex.getMessage());
      assertTrue(ex.getMessage().contains("\"v\"") && ex.getMessage().contains("\"w\""),
                 ex.getMessage());
      assertEquals(3, importedTable(ws, "Q1").getColumnSelection(false).getAttributeCount());
   }

   @Test
   void importReplaceAllowsDroppingAnUnreferencedColumnWhileAJoinExistsOnAKeptKey()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78357-C");

      replaceCsv(ctrl, "TOK-78357-C", "Q1", "id\n1");

      assertEquals(1, importedTable(ws, "Q1").getColumnSelection(false).getAttributeCount());
      assertNotNull(ws.getAssembly("J"));
   }

   @Test
   void importReplaceAllowsRenamingAnUnusedHeaderButRefusesRenamingAJoinedOne() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78357-D");

      replaceCsv(ctrl, "TOK-78357-D", "Q1", "id,vv\n1,a");
      assertNotNull(importedTable(ws, "Q1").getColumnSelection(false).getAttribute("vv"));

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78357-D", "Q1", "key,vv\n1,a"));
      assertTrue(ex.getMessage().contains("\"id\""), ex.getMessage());
   }

   @Test
   void importReplaceRefusesACaseOnlyRenameOfAJoinedKey() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78357-E");

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78357-E", "Q1", "ID,v\n1,a"));

      assertTrue(ex.getMessage().contains("\"id\""), ex.getMessage());
      assertJoinedQ1Untouched(ws);
   }

   @Test
   void importReplaceWithDuplicateHeadersKeepingTheKeyIsAccepted() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78357-F");

      replaceCsv(ctrl, "TOK-78357-F", "Q1", "id,id\n1,2");

      assertNotNull(ws.getAssembly("J"));
   }

   @Test
   void importReplaceWithoutAHeaderRowDropsTheKeyAndIsRefused() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78357-G");

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.importCsv("TOK-78357-G", new WorksheetAgentController.ImportCsvRequest(
            null, "1,a", null, null, null, null, false, null, null, null, "Q1", null, null),
            TestPrincipals.user("alice", "host-org")));

      assertTrue(ex.getMessage().contains("\"id\""), ex.getMessage());
      assertJoinedQ1Untouched(ws);
   }

   @Test
   void importCsvFileReplaceRefusesDroppingAJoinedKeyAndAllowsAnUnusedColumn() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78357-H");

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsvFile(ctrl, "TOK-78357-H", "Q1", "other,v\n1,a"));
      assertTrue(ex.getMessage().contains("'J'") && ex.getMessage().contains("\"id\""),
                 ex.getMessage());
      assertJoinedQ1Untouched(ws);

      replaceCsvFile(ctrl, "TOK-78357-H", "Q1", "id\n1");
      assertEquals(1, importedTable(ws, "Q1").getColumnSelection(false).getAttributeCount());
   }

   @Test
   void importExcelReplaceRefusesDroppingAJoinedKeyAndAllowsAnUnusedColumn() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78357-I");
      PairingException ex = assertThrows(PairingException.class,
         () -> replaceExcel(ctrl, "TOK-78357-I", "Q1", new String[]{"other", "v"},
                            new Object[]{1, "a"}));
      assertTrue(ex.getMessage().contains("'J'") && ex.getMessage().contains("\"id\""),
                 ex.getMessage());
      assertJoinedQ1Untouched(ws);

      replaceExcel(ctrl, "TOK-78357-I", "Q1", new String[]{"id"}, new Object[]{1});
      assertEquals(1, importedTable(ws, "Q1").getColumnSelection(false).getAttributeCount());
   }

   @Test
   void importReplaceKeepsTheRetypeGuardIndependentOfTheDroppedColumnGuard() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78357-J");
      Principal agent = TestPrincipals.user("alice", "host-org");
      ctrl.importCsv("TOK-78357-J", new WorksheetAgentController.ImportCsvRequest(
         "Q1", "a,b\n1,2"), agent);
      ctrl.importCsv("TOK-78357-J", new WorksheetAgentController.ImportCsvRequest(
         "Q2", "k\n1"), agent);
      ctrl.importCsv("TOK-78357-J", new WorksheetAgentController.ImportCsvRequest(
         "Q3", "m\n1"), agent);
      addTestJoin(ws, "J1", "Q1", "a", "Q2", "k");
      addTestJoin(ws, "J2", "Q1", "b", "Q3", "m");

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78357-J", "Q1", "a,c\nx,1"));

      assertTrue(ex.getMessage().contains("integer to string"), ex.getMessage());
   }

   // ---------------------------------------------------------------------------
   // Bug #78527: replaceTable keeps the table's own expression and range columns.
   // ---------------------------------------------------------------------------

   /** T(id, name) with its own expression column idx = id * 10 (WAT-008). */
   private static WorksheetAgentController importWithExpression(Worksheet ws, String token)
      throws Exception
   {
      WorksheetAgentController ctrl = importCtrl(ws, token);
      ctrl.importCsv(token, new WorksheetAgentController.ImportCsvRequest(
         "T", "id,name\n1,a\n2,b"), TestPrincipals.user("alice", "host-org"));
      applyEd(ws, ed -> ed.addExpressionColumn("T", "idx", "field['id'] * 10", "integer", false));
      return ctrl;
   }

   private static List<String> columnNames(Worksheet ws, String table) {
      ColumnSelection cols = importedTable(ws, table).getColumnSelection(false);
      List<String> names = new ArrayList<>();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         names.add(cols.getAttribute(i).getName());
      }

      return names;
   }

   private static String expressionOf(Worksheet ws, String table, String col) {
      ColumnRef ref = (ColumnRef) importedTable(ws, table).getColumnSelection(false)
         .getAttribute(col);
      assertNotNull(ref, col + " was dropped from " + table);
      return ((inetsoft.uql.erm.ExpressionRef) ref.getDataRef()).getExpression();
   }

   @Test
   void importReplaceWithTheSameColumnsKeepsTheTablesOwnExpressionColumn() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importWithExpression(ws, "TOK-78527-A");

      replaceCsv(ctrl, "TOK-78527-A", "T", "id,name\n4,c\n5,d");

      assertEquals(List.of("id", "name", "idx"), columnNames(ws, "T"));
      assertEquals("field['id'] * 10", expressionOf(ws, "T", "idx"));
      assertEquals("4", String.valueOf(importedTable(ws, "T").getEmbeddedData().getObject(1, 0)));
   }

   @Test
   void importReplaceAddingOrDroppingAnUnreadColumnKeepsTheTablesOwnExpressionColumn()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importWithExpression(ws, "TOK-78527-B");

      replaceCsv(ctrl, "TOK-78527-B", "T", "id,name,extra\n4,c,x");
      assertEquals(List.of("id", "name", "extra", "idx"), columnNames(ws, "T"));

      replaceCsv(ctrl, "TOK-78527-B", "T", "id\n4");
      assertEquals(List.of("id", "idx"), columnNames(ws, "T"));
      assertEquals("field['id'] * 10", expressionOf(ws, "T", "idx"));
   }

   @Test
   void importReplaceDroppingAnInputOfTheTablesOwnExpressionIsRefusedNamingIt() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importWithExpression(ws, "TOK-78527-C");

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78527-C", "T", "name\nc"));

      assertEquals("Replacing the data of 'T' would drop column(s) \"id\" that other tables " +
                   "still use: table 'T' itself (expression column 'idx'). Keep these columns, " +
                   "under the same names, in the file, or remove or rewrite those dependents " +
                   "first. Nothing was changed.", ex.getMessage());
      assertEquals(List.of("id", "name", "idx"), columnNames(ws, "T"));
      assertEquals("1", String.valueOf(importedTable(ws, "T").getEmbeddedData().getObject(1, 0)));
   }

   @Test
   void importReplaceDroppingTheBaseOfTheTablesOwnRangeColumnIsRefusedNamingIt() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-78527-D");
      ctrl.importCsv("TOK-78527-D", new WorksheetAgentController.ImportCsvRequest(
         "T", "amount,name\n1,a\n20,b"), TestPrincipals.user("alice", "host-org"));
      applyEd(ws, ed -> ed.addNumericRangeColumn("T", "amount", new double[] { 10 }, null));
      String range = columnNames(ws, "T").get(2);

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78527-D", "T", "name\nc"));

      assertTrue(ex.getMessage().contains("table 'T' itself (range column '" + range + "')"),
                 ex.getMessage());
      assertEquals(List.of("amount", "name", range), columnNames(ws, "T"));
   }

   /** reorder_columns can put a derived column anywhere; it is still not a file column. */
   @Test
   void importReplaceWithTheSameColumnsAndAnInterleavedExpressionKeepsTheOrderAndRetypes()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importWithExpression(ws, "TOK-78527-E");
      applyEd(ws, ed -> ed.reorderColumns("T", List.of("idx", "id", "name")));

      replaceCsv(ctrl, "TOK-78527-E", "T", "id,name\n1.5,c");

      assertEquals(List.of("idx", "id", "name"), columnNames(ws, "T"));
      assertEquals("double", typeOf(ws, "T", "id"));
      assertEquals("field['id'] * 10", expressionOf(ws, "T", "idx"));
   }

   /** The positional type check of a same-shape replace must skip the derived column (B1). */
   @Test
   void importReplaceWithAnInterleavedExpressionStillGuardsTheJoinKeyType() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = joinedImport(ws, "TOK-78527-F");
      applyEd(ws, ed -> ed.addExpressionColumn("Q1", "e", "field['v'] + 'x'", "string", false));
      applyEd(ws, ed -> ed.reorderColumns("Q1", List.of("e", "id", "v")));

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78527-F", "Q1", "id,v\nx1,a"));
      assertTrue(ex.getMessage().contains("join \"J\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("integer to string"), ex.getMessage());

      replaceCsv(ctrl, "TOK-78527-F", "Q1", "id,v\n7,z");
      assertEquals(List.of("e", "id", "v"), columnNames(ws, "Q1"));
      assertNotNull(ws.getAssembly("J"));
   }

   @Test
   void importReplaceWhoseFileHasAColumnNamedLikeTheExpressionIsRefusedBeforeTheTypeCheck()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importWithExpression(ws, "TOK-78527-G");

      PairingException ex = assertThrows(PairingException.class,
         () -> replaceCsv(ctrl, "TOK-78527-G", "T", "id,name,idx\n1,a,x"));

      assertEquals("Cannot replace the data of 'T': the file has a column named \"idx\", " +
                   "which is also the name of this table's expression column. Rename one of " +
                   "them first. Nothing was changed.", ex.getMessage());
      assertEquals(List.of("id", "name", "idx"), columnNames(ws, "T"));
   }

   @Test
   void importCsvFileAndExcelReplacesKeepTheTablesOwnExpressionColumn() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importWithExpression(ws, "TOK-78527-H");

      replaceCsvFile(ctrl, "TOK-78527-H", "T", "id,name\n4,c");
      assertEquals(List.of("id", "name", "idx"), columnNames(ws, "T"));

      replaceExcel(ctrl, "TOK-78527-H", "T", new String[]{"id", "name", "extra"},
                   new Object[]{4, "c", "x"});
      assertEquals(List.of("id", "name", "extra", "idx"), columnNames(ws, "T"));
   }

   @Test
   void importCsvReplaceTableWithDifferentColumnsUpdatesTheColumnSelection() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-REPLACE-SHAPE");
      Principal agent = TestPrincipals.user("alice", "host-org");

      ctrl.importCsv("TOK-REPLACE-SHAPE",
         new WorksheetAgentController.ImportCsvRequest("Query1", "a,b\n1,2"), agent);
      ctrl.importCsv("TOK-REPLACE-SHAPE",
         new WorksheetAgentController.ImportCsvRequest(
            null, "x,y,z\n1,2,3", null, null, null, null, null, null, null, null, "Query1"),
         agent);

      ColumnSelection cols = importedTable(ws, "Query1").getColumnSelection(false);
      assertEquals(3, cols.getAttributeCount());
      assertNotNull(cols.getAttribute("x"));
      assertNotNull(cols.getAttribute("z"));
   }

   @Test
   void importCsvReplaceTableFailsLoudWhenTheNamedTableDoesNotExist() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-REPLACE-MISS");

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.importCsv("TOK-REPLACE-MISS",
            new WorksheetAgentController.ImportCsvRequest(
               null, "a,b\n1,2", null, null, null, null, null, null, null, null, "NoSuchTable"),
            TestPrincipals.user("alice", "host-org")));

      assertTrue(ex.getMessage().contains("NoSuchTable"), ex.getMessage());
   }

   @Test
   void importCsvRejectsBothNameAndReplaceTable() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-REPLACE-BOTH");

      ctrl.importCsv("TOK-REPLACE-BOTH",
         new WorksheetAgentController.ImportCsvRequest("Query1", "a,b\n1,2"),
         TestPrincipals.user("alice", "host-org"));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-REPLACE-BOTH",
            new WorksheetAgentController.ImportCsvRequest(
               "Other", "a,b\n1,2", null, null, null, null, null, null, null, null, "Query1"),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
   }

   @Test
   void importCsvDetectTypeFalseMakesEveryColumnString() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-DT");

      // The same file twice, differing only in detectType. "299.99" and "$499.99" mix numeric
      // sub-formats (plain vs. currency) that CSVLoader can't represent with the single cached
      // Format it picks during type detection -- #4689 (Bug #76203) demotes a column like this to
      // string rather than silently losing whichever values don't match that cached format, so
      // detectType=true and detectType=false converge on the same string result here.
      ctrl.importCsv("TOK-DT", csvRequest("Typed", "price\n299.99\n$499.99",
                                          null, null, null, true, null, null, null, null),
                     TestPrincipals.user("alice", "host-org"));
      ctrl.importCsv("TOK-DT", csvRequest("AsText", "price\n299.99\n$499.99",
                                          null, null, null, false, null, null, null, null),
                     TestPrincipals.user("alice", "host-org"));

      ColumnSelection typed = importedTable(ws, "Typed").getColumnSelection(false);
      ColumnSelection text = importedTable(ws, "AsText").getColumnSelection(false);

      assertEquals(XSchema.STRING, ((ColumnRef) text.getAttribute(0)).getDataType(),
         "detectType=false must leave the column as string");
      assertEquals(XSchema.STRING, ((ColumnRef) typed.getAttribute(0)).getDataType(),
         "detectType=true demotes a mixed numeric sub-format column (plain + currency) to string");
   }

   @Test
   void importCsvDetectTypeTrueDetectsANumericColumn() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-DT2");

      // Unlike the mixed plain/currency case above, both values here share the same currency
      // sub-format, so CSVLoader's cached Format never changes between rows and the column is
      // free to be detected as numeric.
      ctrl.importCsv("TOK-DT2", csvRequest("Typed", "price\n$299.99\n$499.99",
                                           null, null, null, true, null, null, null, null),
                     TestPrincipals.user("alice", "host-org"));

      ColumnSelection typed = importedTable(ws, "Typed").getColumnSelection(false);

      assertNotEquals(XSchema.STRING, ((ColumnRef) typed.getAttribute(0)).getDataType(),
         "detectType=true must still detect a numeric column when every value shares one format");
   }

   /**
    * Bug #76753/WBS-056: removeQuotes left unset silently mis-parsed a standard, fully-quoted
    * CSV -- literal quote characters landed in both column names and values, and the numeric
    * columns were left as strings because CSVLoader's cached Format never matched a
    * quote-wrapped value. csvSettings() resolved the unset case to false, unlike its sibling
    * detectType/firstRowAsHeader fields (both default true) and unlike the native "Import Data
    * File" dialog's own default of true for the identical CSVLoader.readCSV call.
    */
   @Test
   void importCsvDefaultsToStrippingQuotesOnAFullyQuotedFile() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-RQ1");

      ctrl.importCsv("TOK-RQ1",
         new WorksheetAgentController.ImportCsvRequest("Quoted",
            "\"STATE\",\"2020\",\"2021\"\n\"AZ\",\"120\",\"140\"\n\"CA\",\"300\",\"310\""),
         TestPrincipals.user("alice", "host-org"));

      EmbeddedTableAssembly t = importedTable(ws, "Quoted");
      ColumnSelection cols = t.getColumnSelection(false);

      assertNotNull(cols.getAttribute("STATE"),
         "column names must not carry literal quote characters when removeQuotes is left unset");
      assertNull(cols.getAttribute("\"STATE\""),
         "the raw quoted header must not survive as a column name");
      assertNotEquals(XSchema.STRING, ((ColumnRef) cols.getAttribute("2020")).getDataType(),
         "a numeric column's type detection must not be defeated by quote wrapping");
      assertEquals("AZ", String.valueOf(t.getEmbeddedData().getObject(1, 0)),
         "values must have their literal quotes stripped by default");
   }

   /**
    * Companion to the default-flip test above: an explicit removeQuotes:false caller must keep
    * seeing quotes preserved, proving the fix only changes the unset/null default.
    */
   @Test
   void importCsvRemoveQuotesFalseKeepsLiteralQuotesWhenExplicitlyRequested() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-RQ2");

      ctrl.importCsv("TOK-RQ2",
         csvRequest("Quoted",
            "\"STATE\",\"2020\",\"2021\"\n\"AZ\",\"120\",\"140\"\n\"CA\",\"300\",\"310\"",
            null, null, null, null, null, false, null, null),
         TestPrincipals.user("alice", "host-org"));

      EmbeddedTableAssembly t = importedTable(ws, "Quoted");
      ColumnSelection cols = t.getColumnSelection(false);

      assertNotNull(cols.getAttribute("\"STATE\""),
         "an explicit removeQuotes:false must still preserve literal quotes in column names");
      assertEquals("\"AZ\"", String.valueOf(t.getEmbeddedData().getObject(1, 0)),
         "an explicit removeQuotes:false must still preserve literal quotes in values");
   }

   // ---------------------------------------------------------------------------
   // stringColumns / stringColumnIndexes (L2-Group6 flagship finding)
   // ---------------------------------------------------------------------------

   @Test
   void importCsvStringColumnsPreservesLeadingZerosWithoutBreakingOtherNumericColumn()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC1");

      // The audit's exact reproduction: a zero-padded ZIP code alongside a genuinely-numeric
      // column. detectType=true alone would strip ZIP's leading zeros; detectType=false alone
      // would turn AMOUNT into a string too. stringColumns lets ZIP opt out without touching
      // AMOUNT.
      ctrl.importCsv("TOK-SC1", new WorksheetAgentController.ImportCsvRequest(
            "ZipTest", "ZIP,AMOUNT\n00501,10.50\n00544,20.75\n10001,30.00",
            null, null, null, true, null, null, null, null, null,
            List.of("ZIP"), null),
         TestPrincipals.user("alice", "host-org"));

      EmbeddedTableAssembly t = importedTable(ws, "ZipTest");
      ColumnSelection cols = t.getColumnSelection(false);
      assertEquals(XSchema.STRING, ((ColumnRef) cols.getAttribute("ZIP")).getDataType(),
         "ZIP must stay a string column");
      assertNotEquals(XSchema.STRING, ((ColumnRef) cols.getAttribute("AMOUNT")).getDataType(),
         "AMOUNT must still be detected as numeric -- the whole point of a per-column override");
      assertEquals("00501", String.valueOf(t.getEmbeddedData().getObject(1, 0)),
         "the leading zeros must survive");
   }

   @Test
   void importCsvStringColumnIndexesResolvesSameColumnAsStringColumns() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC2");
      String csv = "ZIP,AMOUNT\n00501,10.50";

      ctrl.importCsv("TOK-SC2", new WorksheetAgentController.ImportCsvRequest(
            "ByName", csv, null, null, null, true, null, null, null, null, null,
            List.of("ZIP"), null),
         TestPrincipals.user("alice", "host-org"));
      ctrl.importCsv("TOK-SC2", new WorksheetAgentController.ImportCsvRequest(
            "ByIndex", csv, null, null, null, true, null, null, null, null, null,
            null, List.of(0)),
         TestPrincipals.user("alice", "host-org"));

      assertEquals("00501",
         String.valueOf(importedTable(ws, "ByName").getEmbeddedData().getObject(1, 0)));
      assertEquals("00501",
         String.valueOf(importedTable(ws, "ByIndex").getEmbeddedData().getObject(1, 0)),
         "stringColumnIndexes must resolve to the same result as the equivalent stringColumns");
   }

   @Test
   void importCsvStringColumnsRejectsUnknownColumnName() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC3");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-SC3", new WorksheetAgentController.ImportCsvRequest(
               "X", "ZIP,AMOUNT\n00501,10.50", null, null, null, null, null, null, null, null,
               null, List.of("NoSuchColumn"), null),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("NoSuchColumn"), ex.getReason());
      assertTrue(ex.getReason().contains("ZIP") && ex.getReason().contains("AMOUNT"),
         "the refusal should name the file's actual columns: " + ex.getReason());
   }

   @Test
   void importCsvStringColumnIndexesRejectsOutOfRangeIndex() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC4");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-SC4", new WorksheetAgentController.ImportCsvRequest(
               "X", "ZIP,AMOUNT\n00501,10.50", null, null, null, null, null, null, null, null,
               null, null, List.of(5)),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("5"), ex.getReason());
   }

   @Test
   void importCsvStringColumnsRejectedWithUnpivot() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC5");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-SC5", new WorksheetAgentController.ImportCsvRequest(
               "X", "id,q1,q2\n1,10,20", null, null, null, null, null, null, true, null,
               null, List.of("q1"), null),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("unpivot"), ex.getReason());
   }

   @Test
   void importCsvFileHonoursStringColumns() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC6");

      ctrl.importCsvFile("TOK-SC6",
         new MockMultipartFile("file", "d.csv", "text/csv",
                               "ZIP,AMOUNT\n00501,10.50".getBytes()),
         "ZipFile", null, null, null, true, null, null, null, null, null,
         List.of("ZIP"), null,
         TestPrincipals.user("alice", "host-org"));

      assertEquals("00501",
         String.valueOf(importedTable(ws, "ZipFile").getEmbeddedData().getObject(1, 0)),
         "the multipart route must plumb stringColumns through the same way the JSON route does");
   }

   @Test
   void importCsvFirstRowAsHeaderFalseGeneratesColumnNamesAndKeepsLineOne() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-FR");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv(
         "TOK-FR", csvRequest("NoHeader", "a,b\n1,x", null, null, null, null, false, null, null,
                              null),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(2, resp.rows(), "line 1 counts as data when it is not the header");
      ColumnSelection cs = importedTable(ws, "NoHeader").getColumnSelection(false);
      assertNotNull(cs.getAttribute("col0"), "generated names replace the missing header");
      assertNotNull(cs.getAttribute("col1"));
   }

   @Test
   void importCsvHonoursAnAlternateDelimiter() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-DELIM");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv(
         "TOK-DELIM", csvRequest("Semi", "a;b;c\n1;2;3", null, ";", null, null, null, null, null,
                                 null),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(3, resp.columns(), "a semicolon file is three columns, not one");
      assertNotNull(importedTable(ws, "Semi").getColumnSelection(false).getAttribute("a"));
   }

   @Test
   void importCsvHonoursTabAsTheDelimiter() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-TAB");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv(
         "TOK-TAB", csvRequest("Tabbed", "a\tb\n1\t2", null, null, true, null, null, null, null,
                               null),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(2, resp.columns());
   }

   @Test
   void importCsvUnpivotReshapesCrosstabInput() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-UP");

      // A crosstab: one identifier column plus two measure columns. Unpivoting with one header
      // column turns each measure cell into its own row.
      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv(
         "TOK-UP", csvRequest("Unpivoted", "region,q1,q2\nEast,1,2\nWest,3,4",
                              null, null, null, null, null, null, true, 1),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(3, resp.columns(),
         "unpivot yields the header column plus a name/value pair");
      assertEquals(4, resp.rows(), "two rows times two measures");
      importedTable(ws, "Unpivoted");
   }

   @Test
   void importCsvRejectsAnUnsupportedEncoding() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-ENC");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsvFile("TOK-ENC",
            new MockMultipartFile("file", "d.csv", "text/csv", "a,b\n1,2".getBytes()),
            "Bad", "NOT-A-CHARSET", null, null, null, null, null, null, null, null,
            null, null,
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("NOT-A-CHARSET"), ex.getReason());
   }

   /**
    * A name that is not merely unrecognized but syntactically illegal takes a different route out
    * of {@code Charset}: it throws {@code IllegalCharsetNameException} instead of answering false.
    * Unguarded that escapes as a 500; the caller made the same mistake either way, so it has to
    * land on the same 400 as {@code importCsvRejectsAnUnsupportedEncoding}.
    */
   @Test
   void importCsvRejectsAMalformedEncodingNameRatherThanThrowing() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-BADENC");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsvFile("TOK-BADENC",
            new MockMultipartFile("file", "d.csv", "text/csv", "a,b\n1,2".getBytes()),
            "Bad", "not a charset", null, null, null, null, null, null, null, null,
            null, null,
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("not a charset"), ex.getReason());
   }

   /**
    * The JSON route cannot honour an encoding at all -- its text was decoded before the request
    * existed. Refusing says so; silently ignoring it would leave a caller with mojibake and no
    * indication of why their setting did nothing.
    */
   @Test
   void importCsvRejectsANonUtf8EncodingOnTheJsonRoute() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-JSONENC");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-JSONENC",
            csvRequest("X", "a,b\n1,2", "ISO-8859-1", null, null, null, null, null, null, null),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("import-csv-file"),
         "the refusal should point at the route that can honour it: " + ex.getReason());
   }

   /**
    * UTF-8 spelled any of the ways the JDK accepts is what this route already does, so it is not a
    * setting being ignored and must not be refused.
    */
   @Test
   void importCsvAcceptsUtf8SpelledAsAnAliasOnTheJsonRoute() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-UTF8");

      ctrl.importCsv("TOK-UTF8",
         csvRequest("Utf8", "a,b\n1,2", "utf8", null, null, null, null, null, null, null),
         TestPrincipals.user("alice", "host-org"));

      importedTable(ws, "Utf8");
   }

   @Test
   void importCsvRejectsAMultiCharacterDelimiter() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-BADDELIM");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-BADDELIM",
            csvRequest("X", "a,b\n1,2", null, "\\t", null, null, null, null, null, null),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("delimiterTab"),
         "the refusal should point at the flag that does what the caller meant: " + ex.getReason());
   }

   /**
    * The whole reason the multipart route exists: text handed over as JSON has already been
    * decoded, so a non-UTF-8 file can only survive if its bytes make the trip. This is L2
    * Finding 13.
    */
   @Test
   void importCsvFileDecodesTheBytesWithTheGivenEncoding() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-GBK");
      // Built from code points so this source file stays ASCII: two CJK ideographs, which no
      // single-byte charset can represent, so a wrong decode cannot round-trip by accident.
      String header = new String(new char[] { 0x4ef7, 0x683c });
      byte[] utf16 = (header + ",b\n1,2").getBytes(java.nio.charset.StandardCharsets.UTF_16LE);

      ctrl.importCsvFile("TOK-GBK",
         new MockMultipartFile("file", "d.csv", "text/csv", utf16),
         "Encoded", "UTF-16LE", null, null, null, null, null, null, null, null,
         null, null,
         TestPrincipals.user("alice", "host-org"));

      ColumnSelection cs = importedTable(ws, "Encoded").getColumnSelection(false);
      assertNotNull(cs.getAttribute(header),
         "the header should decode back to its original characters");
   }

   @Test
   void importCsvRejectsBlankCsv() {
      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK", new WorksheetAgentController.ImportCsvRequest(null, " "),
                              TestPrincipals.user("alice", "host-org")));
      assertEquals(400, ex.getStatusCode().value());
   }

   // NOTE: there is no test here exercising a real .xlsx round-trip through importExcel().
   // ExcelFileSupport.getInstance() reflectively loads PoiExcelFileSupport from the
   // inetsoft-xml-formats module, which itself depends on inetsoft-core — core cannot
   // depend on it back (even at test scope) without creating a cyclic Maven reactor
   // reference. This mirrors the pre-existing gap in ImportCSVDialogService's own Excel
   // path, which has no core-module test coverage for the same structural reason; both
   // are exercised only where core and inetsoft-xml-formats are both on the classpath,
   // i.e. the packaged server.

   // NOTE: the framework-level blanket cap (spring.servlet.multipart.max-file-size /
   // max-request-size, application.yaml) is enforced by Spring/Tomcat's multipart parsing
   // before this controller method is ever invoked, converting an oversized upload to a
   // MaxUploadSizeExceededException (handled by handleMaxUploadSizeException() below). That
   // parsing isn't exercised by calling the controller method directly, so it has no unit
   // test here — it's covered by the same structural gap noted above.

   private static MockMultipartFile excelFile(byte[] content) {
      return new MockMultipartFile("file", "workbook.xlsx",
         "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
   }

   @Test
   void importExcelRejectsMissingFile() {
      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importExcel("TOK", null, "XLSX", null, null, null, null, null,
                                TestPrincipals.user("alice", "host-org")));
      assertEquals(400, ex.getStatusCode().value());
   }

   @Test
   void importExcelRejectsUnknownFileType() {
      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      MockMultipartFile file = excelFile(new byte[]{1, 2, 3, 4});

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importExcel("TOK", file, "PDF", null, null, null, null, null,
                                TestPrincipals.user("alice", "host-org")));
      assertEquals(400, ex.getStatusCode().value());
   }

   @Test
   void importExcelRejectsFileTooLarge() {
      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      MockMultipartFile file = excelFile(new byte[10]);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("excel.import.max")).thenReturn("5");

         ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> ctrl.importExcel("TOK", file, "XLSX", null, null, null, null, null,
                                   TestPrincipals.user("alice", "host-org")));
         assertEquals(400, ex.getStatusCode().value());
      }
   }

   // ---------------------------------------------------------------------------
   // Import file-size cap precedence (CSV must not honour excel.import.max)
   // ---------------------------------------------------------------------------

   @Test
   void importCsvIgnoresExcelImportMax() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      WorksheetAgentController ctrl = importController("TOK-CSV-NOEXCELMAX", rws);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class, CALLS_REAL_METHODS)) {
         // Tiny enough that honouring it (the pre-fix, excel-first-then-csv behavior) would
         // reject this CSV. csv.import.max is left unset.
         sreeEnv.when(() -> SreeEnv.getProperty("excel.import.max")).thenReturn("1");

         WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv("TOK-CSV-NOEXCELMAX",
            new WorksheetAgentController.ImportCsvRequest("Imported", "a,b\n1,x\n2,y"), agent);

         assertEquals("Imported", resp.tableName());
      }
   }

   @Test
   void importCsvStillHonorsCsvImportMax() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      WorksheetAgentController ctrl = importController("TOK-CSV-CSVMAX", rws);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class, CALLS_REAL_METHODS)) {
         sreeEnv.when(() -> SreeEnv.getProperty("csv.import.max")).thenReturn("1");

         ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> ctrl.importCsv("TOK-CSV-CSVMAX",
               new WorksheetAgentController.ImportCsvRequest("Imported", "a,b\n1,x\n2,y"), agent));
         assertEquals(400, ex.getStatusCode().value());
      }
   }

   @Test
   void importCsvTreatsMalformedCsvImportMaxAsUnbounded() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      WorksheetAgentController ctrl = importController("TOK-CSV-BADMAX", rws);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class, CALLS_REAL_METHODS)) {
         sreeEnv.when(() -> SreeEnv.getProperty("csv.import.max")).thenReturn("not-a-number");

         WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv("TOK-CSV-BADMAX",
            new WorksheetAgentController.ImportCsvRequest("Imported", "a,b\n1,x\n2,y"), agent);

         assertEquals("Imported", resp.tableName());
      }
   }

   // ---------------------------------------------------------------------------
   // Exception handling
   // ---------------------------------------------------------------------------

   @Test
   void handleResponseStatusExceptionIncludesReasonInBody() {
      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = new ResponseStatusException(
         HttpStatus.BAD_REQUEST, "fileType must be either \"XLS\" or \"XLSX\"");

      ResponseEntity<Map<String, String>> response = ctrl.handleResponseStatusException(ex);

      assertEquals(400, response.getStatusCode().value());
      assertEquals("fileType must be either \"XLS\" or \"XLSX\"", response.getBody().get("error"));
   }

   // ---------------------------------------------------------------------------
   // detach
   // ---------------------------------------------------------------------------

   @Test
   void detachClosesSession() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      // resolve must return a non-null session so the ownership check passes
      when(sessions.resolve(eq("TOK-D"), any())).thenReturn(session("TOK-D"));

      // feature is OFF — detach must still work
      WorksheetAgentController ctrl = controller(featureOff(),
         mock(SheetJoinService.class), sessions,
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ctrl.detach("TOK-D", agent);

      verify(sessions).close("TOK-D");
   }

   /** C2(a): detach must also notify the tab bar the agent is no longer attached. */
   @Test
   void detachNotifiesTabBar() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-D");
      when(sessions.resolve(eq("TOK-D"), any())).thenReturn(s);

      WorksheetAgentController ctrl = new WorksheetAgentController(featureOff(),
         mock(SheetJoinService.class), sessions,
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class), mock(WorksheetPreviewService.class), broadcast,
         mock(inetsoft.uql.XRepository.class), mock(inetsoft.uql.asset.AssetRepository.class),
         mock(inetsoft.web.wiz.service.MetadataApiService.class),
         mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
         mock(inetsoft.web.composer.ws.LayoutGraphService.class),
         mock(inetsoft.web.portal.controller.database.DataSourceService.class),
         mock(inetsoft.sree.security.SecurityEngine.class),
         mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
         mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
         mock(inetsoft.report.composition.execution.AssetDataCache.class),
         mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
         mock(inetsoft.web.wiz.service.RawDataService.class));

      ctrl.detach("TOK-D", agent);

      verify(broadcast).sendAgentInactive(s);
   }

   // ---------------------------------------------------------------------------
   // create_worksheet -- mints a blank worksheet runtime by reusing an already-paired
   // session's browser connection. Symmetric to
   // ViewsheetAssemblyAgentController.createViewsheet; SheetOpenService.createWorksheet performs
   // every guard, this endpoint only translates its result into the same JoinResponse shape join
   // already returns.
   // ---------------------------------------------------------------------------

   /** Feature enabled, only {@code openService} wired -- for the create_worksheet tests. */
   private static WorksheetAgentController controllerWithOpenService(
      inetsoft.web.wiz.viewsheet.SheetOpenService openService)
   {
      return controllerWithOpenService(featureOn(), openService);
   }

   private static WorksheetAgentController controllerWithOpenService(
      SheetAgentFeature feature, inetsoft.web.wiz.viewsheet.SheetOpenService openService)
   {
      return new WorksheetAgentController(feature,
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class), mock(WorksheetPreviewService.class),
         mock(SheetAgentBroadcastService.class), mock(inetsoft.uql.XRepository.class),
         mock(inetsoft.uql.asset.AssetRepository.class),
         mock(inetsoft.web.wiz.service.MetadataApiService.class),
         mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
         mock(inetsoft.web.composer.ws.LayoutGraphService.class),
         mock(inetsoft.web.portal.controller.database.DataSourceService.class),
         mock(inetsoft.sree.security.SecurityEngine.class),
         mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
         openService,
         mock(inetsoft.report.composition.execution.AssetDataCache.class),
         mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
         mock(inetsoft.web.wiz.service.RawDataService.class));
   }

   @Test
   void createWorksheetReturnsTheNewlyMintedWorksheetSession() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      JoinSession created = new JoinSession("tok-ws-new", "ws-runtime-new", "alice~;~host-org",
         SheetType.WORKSHEET, 0L, SheetSessionService.TTL_MILLIS,
         JoinSession.ConnectionMode.PAIRED, "sock-1", "alice-browser", null);

      inetsoft.web.wiz.viewsheet.SheetOpenService openService =
         mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class);
      when(openService.createWorksheet(eq("tok-acting"), eq(agent))).thenReturn(created);

      WorksheetAgentController ctrl = controllerWithOpenService(openService);

      WorksheetAgentController.JoinResponse response = ctrl.createWorksheet(
         new WorksheetAgentController.CreateWorksheetRequest("tok-acting"), agent);

      assertEquals("worksheet", response.sheetType());
      assertEquals("ws-runtime-new", response.runtimeId());
      verify(openService).createWorksheet(eq("tok-acting"), eq(agent));
   }

   @Test
   void createWorksheetWrapsAnIllegalArgumentExceptionFromSheetOpenServiceAsPairingException()
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");

      inetsoft.web.wiz.viewsheet.SheetOpenService openService =
         mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class);
      when(openService.createWorksheet(any(), any()))
         .thenThrow(new IllegalArgumentException("invalid or expired session"));

      WorksheetAgentController ctrl = controllerWithOpenService(openService);

      PairingException ex = assertThrows(PairingException.class, () ->
         ctrl.createWorksheet(
            new WorksheetAgentController.CreateWorksheetRequest("tok-acting"), agent));
      assertTrue(ex.getMessage().contains("invalid or expired session"));
   }

   @Test
   void createWorksheetRefusesWhenTheFeatureIsDisabled() {
      inetsoft.web.wiz.viewsheet.SheetOpenService openService =
         mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class);
      WorksheetAgentController ctrl = controllerWithOpenService(featureOff(), openService);

      assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
         ctrl.createWorksheet(new WorksheetAgentController.CreateWorksheetRequest("tok-acting"),
            TestPrincipals.user("alice", "host-org")));
      verifyNoInteractions(openService);
   }

   // ---------------------------------------------------------------------------
   // addBoundTable — PHYSICAL_TABLE / ACCESS permission gate
   // ---------------------------------------------------------------------------

   @Test
   void addBoundTableDeniedThrowsSecurityException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      MetadataApiService metadataApiService = mock(MetadataApiService.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(editSvc,
         mock(DataSourceService.class), securityEngine, metadataApiService,
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addBoundTableRequest("dbo.orders", "MyDatasource");

      assertThrows(SecurityException.class, () -> ctrl.edit("TOK-BT", req, agent));

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verifyNoInteractions(metadataApiService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addBoundTableGrantedPassesPermissionGate() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      MetadataApiService metadataApiService = mock(MetadataApiService.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // Datasource READ is now checked before the JDBC metadata probe; grant it so execution
      // reaches getJDBCDatasource.
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, metadataApiService,
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addBoundTableRequest("dbo.orders", "MyDatasource");

      // Gets past the security gate; fails further downstream because metadataApiService is
      // an unstubbed mock (getJDBCDatasource/getTableMetaData return null), which is expected —
      // we only assert that the PHYSICAL_TABLE check let execution proceed.
      Exception ex = assertThrows(Exception.class, () -> ctrl.edit("TOK-BT2", req, agent));
      assertFalse(ex instanceof SecurityException,
                   "should not fail on the permission check when granted");

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verify(metadataApiService).getJDBCDatasource("MyDatasource");
   }

   @Test
   void addBoundTableDeniedByDatasourceReadThrowsPairingExceptionBeforeProbe() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      MetadataApiService metadataApiService = mock(MetadataApiService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      // Physical-table action granted, but datasource READ denied.
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(mock(WorksheetEditService.class),
         dataSourceService, securityEngine, metadataApiService,
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addBoundTableRequest("dbo.orders", "MyDatasource");

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-BT3", req, agent));
      assertTrue(ex.getMessage().contains("READ permission"));

      // The READ denial must short-circuit before any JDBC metadata probe.
      verifyNoInteractions(metadataApiService);
   }

   /**
    * Regression for Bug PVA-005: {@code addBoundTable} used to call the 2-arg
    * {@code AssetUtil.getNextName(ws, prefix)} overload, which hardcodes {@code preferred = null}
    * and so always appended a "1" suffix even when the plain table name was free. With no
    * colliding assembly on the worksheet, {@code add_table(table:"ORDERS", ...)} must create an
    * assembly literally named {@code "ORDERS"}, not {@code "ORDERS1"}.
    */
   @Test
   void addBoundTableUsesPlainNameWhenNoCollision() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      MetadataApiService metadataApiService = mock(MetadataApiService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(metadataApiService.getJDBCDatasource("MyDatasource")).thenReturn(jdbcDs);

      XNode tableMetaData = new XNode("ORDERS");
      tableMetaData.setAttribute("type", "TABLE");
      when(metadataApiService.getTableMetaData(eq(jdbcDs), isNull(), isNull(), eq("ORDERS")))
         .thenReturn(tableMetaData);

      DatabaseTableMeta tableMeta = new DatabaseTableMeta();
      tableMeta.setColumns(new ArrayList<>());
      when(metadataApiService.getTableDetails(eq("MyDatasource"), eq("ORDERS"), isNull(), isNull(),
                                              eq(agent)))
         .thenReturn(tableMeta);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(editSvc.applyOnRuntime(eq("TOK-BT4"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, metadataApiService,
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addBoundTableRequest("ORDERS", "MyDatasource");

      try(MockedStatic<SQLTypes> sqlTypes = mockStatic(SQLTypes.class)) {
         SQLTypes types = mock(SQLTypes.class);
         sqlTypes.when(() -> SQLTypes.getSQLTypes(jdbcDs)).thenReturn(types);
         when(types.getQualifiedName(eq(tableMetaData), eq(jdbcDs))).thenReturn("ORDERS");

         ctrl.edit("TOK-BT4", req, agent);
      }

      assertNotNull(ws.getAssembly("ORDERS"),
         "with no collision the new table must keep the plain name, not 'ORDERS1'");
      assertNull(ws.getAssembly("ORDERS1"),
         "no 'ORDERS1'-suffixed assembly should be created when 'ORDERS' was free");
   }

   /**
    * Companion to {@link #addBoundTableUsesPlainNameWhenNoCollision()}: proves the fix did not
    * remove the genuine-collision fallback — when {@code "ORDERS"} is already taken, the new
    * table must still be named {@code "ORDERS1"}.
    */
   @Test
   void addBoundTableStillSuffixesOnRealCollision() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      MetadataApiService metadataApiService = mock(MetadataApiService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(metadataApiService.getJDBCDatasource("MyDatasource")).thenReturn(jdbcDs);

      XNode tableMetaData = new XNode("ORDERS");
      tableMetaData.setAttribute("type", "TABLE");
      when(metadataApiService.getTableMetaData(eq(jdbcDs), isNull(), isNull(), eq("ORDERS")))
         .thenReturn(tableMetaData);

      DatabaseTableMeta tableMeta = new DatabaseTableMeta();
      tableMeta.setColumns(new ArrayList<>());
      when(metadataApiService.getTableDetails(eq("MyDatasource"), eq("ORDERS"), isNull(), isNull(),
                                              eq(agent)))
         .thenReturn(tableMeta);

      Worksheet ws = new Worksheet();
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "ORDERS"));
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(editSvc.applyOnRuntime(eq("TOK-BT5"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, metadataApiService,
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addBoundTableRequest("ORDERS", "MyDatasource");

      try(MockedStatic<SQLTypes> sqlTypes = mockStatic(SQLTypes.class)) {
         SQLTypes types = mock(SQLTypes.class);
         sqlTypes.when(() -> SQLTypes.getSQLTypes(jdbcDs)).thenReturn(types);
         when(types.getQualifiedName(eq(tableMetaData), eq(jdbcDs))).thenReturn("ORDERS");

         ctrl.edit("TOK-BT5", req, agent);
      }

      assertNotNull(ws.getAssembly("ORDERS1"),
         "with a real collision on 'ORDERS' the new table must fall back to 'ORDERS1'");
   }

   /**
    * Regression for WBT-009 (#76691): {@code add_table} used to silently rename on a name
    * collision (see {@link #addBoundTableStillSuffixesOnRealCollision}) with nothing in the
    * {@code edit} response disclosing it -- {@code edit} returned {@code void}, so there was no
    * wire response to read regardless of what the caller did. {@code edit}'s response now carries
    * the resolved assembly name for {@code add_table}; this proves it for {@code addBoundTable}'s
    * collision path specifically.
    */
   @Test
   void addBoundTableEditResponseDisclosesResolvedNameOnCollision() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      MetadataApiService metadataApiService = mock(MetadataApiService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(metadataApiService.getJDBCDatasource("MyDatasource")).thenReturn(jdbcDs);

      XNode tableMetaData = new XNode("ORDERS");
      tableMetaData.setAttribute("type", "TABLE");
      when(metadataApiService.getTableMetaData(eq(jdbcDs), isNull(), isNull(), eq("ORDERS")))
         .thenReturn(tableMetaData);

      DatabaseTableMeta tableMeta = new DatabaseTableMeta();
      tableMeta.setColumns(new ArrayList<>());
      when(metadataApiService.getTableDetails(eq("MyDatasource"), eq("ORDERS"), isNull(), isNull(),
                                              eq(agent)))
         .thenReturn(tableMeta);

      Worksheet ws = new Worksheet();
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "ORDERS"));
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(editSvc.applyOnRuntime(eq("TOK-BT6"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, metadataApiService,
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addBoundTableRequest("ORDERS", "MyDatasource");
      ResponseEntity<EditResponse> response;

      try(MockedStatic<SQLTypes> sqlTypes = mockStatic(SQLTypes.class)) {
         SQLTypes types = mock(SQLTypes.class);
         sqlTypes.when(() -> SQLTypes.getSQLTypes(jdbcDs)).thenReturn(types);
         when(types.getQualifiedName(eq(tableMetaData), eq(jdbcDs))).thenReturn("ORDERS");

         response = ctrl.edit("TOK-BT6", req, agent);
      }

      assertNotNull(response.getBody(),
         "add_table's response must disclose the resolved assembly name");
      assertEquals("ORDERS1", response.getBody().assemblyName(),
         "the response must report the ACTUAL resolved name, not the requested 'ORDERS'");
   }

   // ---------------------------------------------------------------------------
   // add_table with endpoint/suffix — dispatch guards + permission gate for addTabularTable()
   // ---------------------------------------------------------------------------

   @Test
   void addTabularTableRequiresDatasource() {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", null, null, null, null,
         "Charges", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT1", req, agent));
      assertTrue(ex.getMessage().contains("datasource is required"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableRejectsLogicalModelTogetherWithEndpoint() {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", "Order Model", null, null,
         "Charges", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT2", req, agent));
      assertTrue(ex.getMessage().contains("logicalModel"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableRejectsSchemaCatalogTogetherWithEndpoint() {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, "dbo", null,
         "Charges", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT3", req, agent));
      assertTrue(ex.getMessage().contains("schema/catalog"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableRejectsBothEndpointAndSuffix() {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, null, null,
         "Charges", null, null, null, null, "/v1/widgets/{id}", null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT4", req, agent));
      assertTrue(ex.getMessage().contains("both endpoint and suffix"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableDeniedByDatasourceReadThrowsPairingException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, null, null,
         "Charges", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT5", req, agent));
      assertTrue(ex.getMessage().contains("no READ permission on datasource MyDatasource"));

      // The READ denial must short-circuit before the data source (and its connector) is
      // resolved at all -- the next step dials a real, metered endpoint.
      verifyNoInteractions(xrepository);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableProceedsPastPermissionGateWhenReadGranted() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, null, null,
         "Charges", null, null, null, null, null, null);

      // xrepository is an unstubbed mock, so getDataSource returns null and the request fails
      // with "Data source not found" -- proof execution proceeded past the permission gate into
      // addTabularTable's own datasource resolution, exactly the shape
      // createTableTabularTableProceedsWhenDatasourceReadGranted already asserts on the
      // wiz-services side.
      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT6", req, agent));
      assertTrue(ex.getMessage().contains("Data source not found"), ex.getMessage());
      verify(xrepository).getDataSource(eq("MyDatasource"));
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableRejectsNonTabularDatasource() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      // A JDBC datasource is an XDataSource that is NOT a TabularDataSource.
      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(jdbcDs.getType()).thenReturn("JDBC");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(jdbcDs);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, null, null,
         "Charges", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT7", req, agent));
      assertTrue(ex.getMessage().contains("not a tabular/REST one"), ex.getMessage());
      verifyNoInteractions(editSvc);
   }

   /**
    * Proves {@code req.parameters()} actually reaches
    * {@code TabularEndpointBindingSupport.applyEndpointContract} through {@code addTabularTable}
    * -- not just that the field exists on {@code EditRequest} and deserializes (see
    * {@code EditRequestParametersDeserializationTest}). {@code Repos} is given a REQUIRED
    * parameter ("id") with no value; supplying it via {@code req.parameters()} must satisfy
    * that requirement and let execution reach {@code editService.applyOnRuntime} -- with the
    * parameter hardcoded to {@code null} (the bug this test guards against), the same request
    * would instead fail with "requires parameter(s) ... id" before ever reaching it.
    */
   @Test
   void addTabularTableThreadsParametersIntoTheSharedHelper() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      RestParameter idParam = new RestParameter();
      idParam.setName("id");
      idParam.setRequired(true);
      query.getParameters().getParameters().add(idParam);

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, null, null,
         "Repos", Map.of("id", "42"), null, null, null, null, null);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         assertDoesNotThrow(() -> ctrl.edit("TOK-TT8", req, agent),
            "the supplied 'id' parameter must satisfy Repos' required contract");
         // editService.applyOnRuntime is only reached AFTER applyEndpointContract succeeds --
         // if 'id' had not been threaded through, a PairingException would have fired first and
         // this mock would never have been touched.
         verify(editSvc).applyOnRuntime(eq("TOK-TT8"), eq(agent), any());
      }
   }

   /**
    * Regression for round-1 review finding 2 on the row-cap PR: proves
    * {@code TabularEndpointBindingSupport.requireRowCapWhenPaged} is actually WIRED into
    * {@code addTabularTable}'s endpoint branch, not just exercised in isolation by
    * {@code TabularEndpointBindingSupportTest}. {@code FakeNamedConnectorQuery}'s "Paged" endpoint
    * (see its own doc) reports {@code isPaged() == true} with no {@code maxRows} supplied.
    */
   @Test
   void addTabularTableRejectsPagedEndpointWithoutMaxRows() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      EditRequest req = addTabularTableRequestWithMaxRows(
         "t1", "MyDatasource", "Paged", null, null);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ctrl.edit("TOK-TT9", req, agent));
         assertTrue(ex.getMessage().contains("paginated"), ex.getMessage());
         verifyNoInteractions(editSvc);
      }
   }

   /** Same wiring as {@link #addTabularTableRejectsPagedEndpointWithoutMaxRows}, but a positive
    *  {@code maxRows} must satisfy the check and let execution reach {@code editService}. */
   @Test
   void addTabularTableAllowsPagedEndpointWithMaxRows() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      EditRequest req = addTabularTableRequestWithMaxRows(
         "t1", "MyDatasource", "Paged", null, 500);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         assertDoesNotThrow(() -> ctrl.edit("TOK-TT10", req, agent),
            "a positive maxRows must satisfy the row-cap check on a paginated endpoint");
         verify(editSvc).applyOnRuntime(eq("TOK-TT10"), eq(agent), any());
      }
   }

   /**
    * Same regression as {@link #addTabularTableRejectsPagedEndpointWithoutMaxRows}, for the
    * generic/custom (suffix) branch instead of the named-connector (endpoint) one.
    * {@code FakeCustomRestQuery} has no {@code endpoint} property (so {@code addTabularTable}
    * dispatches to the suffix branch) and reports {@code isPaged() == true} only for
    * {@code suffix == "/paged"} -- see its own doc.
    */
   @Test
   void addTabularTableRejectsPagedSuffixWithoutMaxRows() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeCustomRestQuery query = new FakeCustomRestQuery();

      EditRequest req = addTabularTableRequestWithMaxRows(
         "t1", "MyDatasource", null, "/paged", null);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ctrl.edit("TOK-TT11", req, agent));
         assertTrue(ex.getMessage().contains("paginated"), ex.getMessage());
         verifyNoInteractions(editSvc);
      }
   }

   /** Same wiring as {@link #addTabularTableRejectsPagedSuffixWithoutMaxRows}, but a positive
    *  {@code maxRows} must satisfy the check and let execution reach {@code editService}. */
   @Test
   void addTabularTableAllowsPagedSuffixWithMaxRows() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeCustomRestQuery query = new FakeCustomRestQuery();

      EditRequest req = addTabularTableRequestWithMaxRows(
         "t1", "MyDatasource", null, "/paged", 500);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         assertDoesNotThrow(() -> ctrl.edit("TOK-TT12", req, agent),
            "a positive maxRows must satisfy the row-cap check on a paginated suffix");
         verify(editSvc).applyOnRuntime(eq("TOK-TT12"), eq(agent), any());
      }
   }

   /**
    * Regression for the final whole-branch review's Important finding 1: {@code
    * requireRowCapWhenPaged} ran ONLY before the new {@code extraProperties} block, so a caller
    * could set a pagination-triggering property THROUGH {@code extraProperties} (not through
    * {@code endpoint}/{@code suffix} directly) and reach the live call fully uncapped -- the
    * exact failure the guard exists to prevent. The suffix here ({@code "/v1/widgets"}) is
    * deliberately NOT {@code "/paged"}, so {@code FakeCustomRestQuery.isPaged()} is false when
    * the FIRST guard check runs (right after the suffix is applied); only the {@code
    * paginationMode} property set via {@code extraProperties} afterward flips it to paged, which
    * only a SECOND guard call -- re-run after the {@code extraProperties} block -- can catch.
    */
   @Test
   void addTabularTableRejectsPaginationTriggeredByExtraPropertiesWithoutMaxRows() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeCustomRest");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeCustomRestQuery query = new FakeCustomRestQuery();

      EditRequest req = addTabularTableRequestWithExtraProperties(
         "t1", "MyDatasource", null, "/v1/widgets",
         Map.of("paginationMode", "PAGE_COUNT"), null);

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ctrl.edit("TOK-TT13", req, agent));
         assertTrue(ex.getMessage().contains("paginated"), ex.getMessage());
         verifyNoInteractions(editSvc);
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }
   }

   /** Same wiring as {@link #addTabularTableRejectsPaginationTriggeredByExtraPropertiesWithoutMaxRows},
    *  but a positive {@code maxRows} must satisfy the re-run guard and let execution reach {@code
    *  editService} -- proving the fix does not simply reject every {@code extraProperties} call
    *  that touches pagination, only the uncapped ones. */
   @Test
   void addTabularTableAllowsPaginationTriggeredByExtraPropertiesWithMaxRows() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeCustomRest");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeCustomRestQuery query = new FakeCustomRestQuery();

      EditRequest req = addTabularTableRequestWithExtraPropertiesAndMaxRows(
         "t1", "MyDatasource", null, "/v1/widgets",
         Map.of("paginationMode", "PAGE_COUNT"), 500);

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         assertDoesNotThrow(() -> ctrl.edit("TOK-TT14", req, agent),
            "a positive maxRows must satisfy the re-run row-cap check on a suffix paginated " +
            "only via extraProperties");
         verify(editSvc).applyOnRuntime(eq("TOK-TT14"), eq(agent), any());
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }
   }

   // ---------------------------------------------------------------------------
   // add_table with queryParams — naming for addQueryParamsTable()
   // ---------------------------------------------------------------------------

   @Test
   void addQueryParamsTableRejectsQueryParamsTogetherWithEndpoint() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addQueryParamsTableRequest("t1", "MyDatasource", null, null, null,
         "Charges", null, Map.of("entitySet", "Orders"));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-QP1", req, agent));
      assertTrue(ex.getMessage().contains("queryParams together with endpoint or suffix"),
         ex.getMessage());
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addQueryParamsTableRejectsQueryParamsTogetherWithSuffix() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addQueryParamsTableRequest("t1", "MyDatasource", null, null, null,
         null, "/v1/widgets", Map.of("entitySet", "Orders"));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-QP2", req, agent));
      assertTrue(ex.getMessage().contains("queryParams together with endpoint or suffix"),
         ex.getMessage());
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addQueryParamsTableRejectsQueryParamsTogetherWithParameters() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addQueryParamsTableRequestWithConflictingField("t1", "MyDatasource",
         Map.of("entitySet", "Orders"), Map.of("id", "123"), null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-QP7", req, agent));
      assertTrue(ex.getMessage().contains("queryParams together with parameters"),
         ex.getMessage());
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addQueryParamsTableRejectsQueryParamsTogetherWithLookup() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addQueryParamsTableRequestWithConflictingField("t1", "MyDatasource",
         Map.of("entitySet", "Orders"), null, List.of("Issue Event"), null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-QP8", req, agent));
      assertTrue(ex.getMessage().contains("queryParams together with lookup"),
         ex.getMessage());
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addQueryParamsTableRejectsQueryParamsTogetherWithCustomLookups() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addQueryParamsTableRequestWithConflictingField("t1", "MyDatasource",
         Map.of("entitySet", "Orders"), null, null,
         List.of(Map.of("url", "/v1/events", "jsonPath", "$.data", "key", "id")));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-QP9", req, agent));
      assertTrue(ex.getMessage().contains("queryParams together with customLookups"),
         ex.getMessage());
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTableRejectsExtraPropertiesTogetherWithQueryParams() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = addQueryParamsTableRequestWithExtraProperties(
         "t1", "OData/Northwind", Map.of("entitySet", "Orders"), Map.of("jsonPath", "$.items"));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-EP1", req, agent));
      assertTrue(ex.getMessage().contains("extraProperties"), ex.getMessage());
      assertTrue(ex.getMessage().contains("queryParams"), ex.getMessage());
      verifyNoInteractions(dataSourceService, editSvc, xrepository);
   }

   @Test
   void addTableRejectsExtraPropertiesWithoutEndpointOrSuffix() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = addTableRequestWithBareExtraProperties(
         "t1", "OData/Northwind", Map.of("jsonPath", "$.items"));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-EP2", req, agent));
      assertTrue(ex.getMessage().contains("extraProperties"), ex.getMessage());
      assertTrue(ex.getMessage().contains("endpoint"), ex.getMessage());
      verifyNoInteractions(dataSourceService, editSvc, xrepository);
   }

   @Test
   void addTabularTableRejectsExtraPropertiesContainingEndpointKey() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      EditRequest req = addTabularTableRequestWithExtraProperties(
         "t1", "MyDatasource", "Comments", null,
         Map.of("endpoint", "Issues"), null);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.edit("TOK-EP3", req, agent));
         assertTrue(ex.getMessage().contains("endpoint"), ex.getMessage());
         verifyNoInteractions(editSvc);
      }
   }

   @Test
   void addTabularTableRejectsExtraPropertiesContainingSuffixKey() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeCustomRestQuery query = new FakeCustomRestQuery();

      EditRequest req = addTabularTableRequestWithExtraProperties(
         "t1", "MyDatasource", null, "/v1/widgets",
         Map.of("suffix", "/v1/other"), null);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.edit("TOK-EP4", req, agent));
         assertTrue(ex.getMessage().contains("suffix"), ex.getMessage());
         verifyNoInteractions(editSvc);
      }
   }

   /**
    * Proves extraProperties actually reaches TabularQueryContractSupport.applyQueryContract
    * through addTabularTable's endpoint branch. TabularSchemaExtractor.extract resolves
    * connector labels through a Spring-bean Config.getConfig(), not registered in this class's
    * minimal test context -- wraps the real installed ApplicationContext with a delegating spy
    * for this test's duration only, matching addQueryParamsTableThreadsQueryParamsIntoTheSharedHelper's
    * own established workaround (restored in every case, including failure).
    */
   @Test
   void addTabularTableThreadsExtraPropertiesIntoTheSharedHelperViaEndpoint() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeNamedConnector");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      EditRequest req = addTabularTableRequestWithExtraProperties(
         "t1", "MyDatasource", "Comments", null,
         Map.of("jsonPath", "$.items[*]"), null);

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         assertDoesNotThrow(() -> ctrl.edit("TOK-EP5", req, agent));
         assertEquals("$.items[*]", query.getJsonPath());
         verify(editSvc).applyOnRuntime(eq("TOK-EP5"), eq(agent), any());
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }
   }

   /** Same as the endpoint-form test above, but through the suffix (generic/custom) branch. */
   @Test
   void addTabularTableThreadsExtraPropertiesIntoTheSharedHelperViaSuffix() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeCustomRest");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeCustomRestQuery query = new FakeCustomRestQuery();

      EditRequest req = addTabularTableRequestWithExtraProperties(
         "t1", "MyDatasource", null, "/v1/widgets",
         Map.of("jsonPath", "$.data[*]"), null);

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         assertDoesNotThrow(() -> ctrl.edit("TOK-EP6", req, agent));
         assertEquals("$.data[*]", query.getJsonPath());
         verify(editSvc).applyOnRuntime(eq("TOK-EP6"), eq(agent), any());
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }
   }

   /** Proves extraProperties genuinely reuses applyQueryContract's own validation, not a no-op. */
   @Test
   void addTabularTableRejectsAnUnknownExtraPropertiesKey() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeCustomRest");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeCustomRestQuery query = new FakeCustomRestQuery();

      EditRequest req = addTabularTableRequestWithExtraProperties(
         "t1", "MyDatasource", null, "/v1/widgets",
         Map.of("notARealProperty", "x"), null);

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ctrl.edit("TOK-EP7", req, agent));
         assertTrue(ex.getMessage().contains("notARealProperty"), ex.getMessage());
         verifyNoInteractions(editSvc);
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }
   }

   /**
    * Regression for the final whole-branch review's Important finding 2: the endpoint/suffix
    * "returned no columns" message discarded {@code applyQueryContract}'s own applied-properties
    * summary, unlike {@code addQueryParamsTable}'s equivalent message (which reports {@code
    * "Properties sent: " + applied}). {@code FakeNamedConnectorQuery.loadOutputColumns} is
    * overridden to produce zero output columns whenever no response shape is staged (see its own
    * doc), so routing this through a REAL {@code editService.applyOnRuntime} answer (rather than
    * the unstubbed mock the other extraProperties tests use, which never invokes its function
    * argument at all) reaches the empty-column branch with {@code jsonPath} genuinely applied.
    */
   @Test
   void addTabularTableNoColumnsErrorIncludesAppliedExtraProperties() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeNamedConnector");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      EditRequest req = addTabularTableRequestWithExtraProperties(
         "t1", "MyDatasource", "Comments", null,
         Map.of("jsonPath", "$.items[*]"), null);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(editSvc.applyOnRuntime(eq("TOK-EP8"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.edit("TOK-EP8", req, agent));
         assertTrue(ex.getMessage().contains("returned no columns"), ex.getMessage());
         assertTrue(ex.getMessage().contains("URL suffix sent:"), ex.getMessage());
         assertTrue(ex.getMessage().contains("Extra properties sent:"), ex.getMessage());
         assertTrue(ex.getMessage().contains("jsonPath"), ex.getMessage());
         assertTrue(ex.getMessage().contains("$.items[*]"), ex.getMessage());
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }
   }

   /**
    * WBT-007 root cause 2 (residual broken table): {@code addTabularTable}'s lambda used to call
    * {@code ws.addAssembly(assembly)} BEFORE probing the query for columns, so a query that
    * genuinely produces zero columns left the just-added, zero-column assembly in the live
    * worksheet even though the overall {@code add_table} call reported failure. Reuses the same
    * real-{@code Worksheet}-through-a-mocked-{@code applyOnRuntime} wiring as {@link
    * #addTabularTableNoColumnsErrorIncludesAppliedExtraProperties} -- {@code
    * FakeNamedConnectorQuery.loadOutputColumns} produces zero columns whenever no response shape
    * is staged, which is exactly this case.
    */
   @Test
   void addTabularTableNoColumnsDoesNotLeaveResidualAssembly() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeNamedConnector");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      EditRequest req = addTabularTableRequestWithExtraProperties(
         "t1", "MyDatasource", "Comments", null,
         Map.of("jsonPath", "$.items[*]"), null);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(editSvc.applyOnRuntime(eq("TOK-EP9"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         assertThrows(PairingException.class, () -> ctrl.edit("TOK-EP9", req, agent));
         assertEquals(0, ws.getAssemblies().length,
            "a failed add_table probe must not leave a zero-column assembly in the worksheet");
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }
   }

   @Test
   void addQueryParamsTableRequiresDatasource() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addQueryParamsTableRequest("t1", null, null, null, null,
         null, null, Map.of("entitySet", "Orders"));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-QP3", req, agent));
      assertTrue(ex.getMessage().contains("datasource is required"), ex.getMessage());
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addQueryParamsTableRejectsLogicalModelTogether() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addQueryParamsTableRequest("t1", "MyDatasource", "Order Model", null,
         null, null, null, Map.of("entitySet", "Orders"));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-QP4", req, agent));
      assertTrue(ex.getMessage().contains("queryParams and logicalModel"), ex.getMessage());
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addQueryParamsTableRejectsSchemaCatalogTogether() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addQueryParamsTableRequest("t1", "MyDatasource", null, "dbo", null,
         null, null, Map.of("entitySet", "Orders"));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-QP5", req, agent));
      assertTrue(ex.getMessage().contains("schema/catalog"), ex.getMessage());
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addQueryParamsTableRejectsNonTabularDatasource() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(jdbcDs.getType()).thenReturn("JDBC");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(jdbcDs);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = addQueryParamsTableRequest("t1", "MyDatasource", null, null, null,
         null, null, Map.of("entitySet", "Orders"));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-QP6", req, agent));
      assertTrue(ex.getMessage().contains("not a tabular"), ex.getMessage());
      verifyNoInteractions(editSvc);
   }

   /**
    * Proves {@code req.queryParams()} actually reaches
    * {@code TabularQueryContractSupport.applyQueryContract} through {@code addQueryParamsTable}
    * -- the same connector-agnostic reflection path
    * {@code WorksheetTableService.buildTabularTable} (wiz-services' write path) uses. Fills
    * {@code FakeNamedConnectorQuery}'s plain, non-composite {@code jsonPath} property (no
    * {@code dependsOn}, no {@code tagsMethod}) rather than {@code endpoint}, since queryParams'
    * whole point is addressing a connector that has no {@code endpoint}/{@code suffix} property
    * at all -- reaching {@code editService.applyOnRuntime} is what proves the fill succeeded.
    *
    * <p>{@code TabularSchemaExtractor.extract} resolves connector labels through a Spring-bean
    * {@code Config.getConfig()}, which this class's real (but minimal) {@code @WizAgentTestSupport}
    * context does not register -- unlike {@code TabularQueryContractSupportTest}, which owns its
    * whole context and can install a mock one for the class's entire lifetime, this test wraps
    * the REAL installed {@link ApplicationContext} with a delegating spy for its own duration
    * only, restoring the original context in every case (including failure) so the ~160 other
    * tests in this class keep the real Spring context they depend on.</p>
    */
   @Test
   void addQueryParamsTableThreadsQueryParamsIntoTheSharedHelper() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeNamedConnector");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      // "endpoint" supplied alongside "jsonPath" -- otherwise required-param enforcement (added
      // since) reports the missing "endpoint" first, which is correct but not what this test
      // isolates (a plain, non-composite property filling cleanly).
      EditRequest req = addQueryParamsTableRequest("t1", "MyDatasource", null, null, null,
         null, null, Map.of("endpoint", "Repos", "jsonPath", "$.data"));

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         assertDoesNotThrow(() -> ctrl.edit("TOK-QP7", req, agent),
            "a plain, non-composite property with no dependsOn/tagsMethod must fill cleanly");
         assertEquals("$.data", query.getJsonPath());
         // editService.applyOnRuntime is only reached AFTER applyQueryContract succeeds -- if
         // jsonPath had not been threaded through at all, this mock would never be touched.
         verify(editSvc).applyOnRuntime(eq("TOK-QP7"), eq(agent), any());
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }
   }

   /**
    * WBT-007 root cause 2 (residual broken table), {@code addQueryParamsTable}'s side of the
    * identical defect fixed in {@link #addTabularTableNoColumnsDoesNotLeaveResidualAssembly} --
    * same real-{@code Worksheet}-through-a-mocked-{@code applyOnRuntime} wiring, adapted to the
    * queryParams form.
    */
   @Test
   void addQueryParamsTableNoColumnsDoesNotLeaveResidualAssembly() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeNamedConnector");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      EditRequest req = addQueryParamsTableRequest("t1", "MyDatasource", null, null, null,
         null, null, Map.of("endpoint", "Repos", "jsonPath", "$.data"));

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(editSvc.applyOnRuntime(eq("TOK-QP10"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         assertThrows(PairingException.class, () -> ctrl.edit("TOK-QP10", req, agent));
         assertEquals(0, ws.getAssemblies().length,
            "a failed add_table probe must not leave a zero-column assembly in the worksheet");
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }
   }

   /**
    * Regression for WBT-009 (#76691): {@code addQueryParamsTable} shares the same
    * {@code AssetUtil.getNextName} collision-suffixing as {@code addBoundTable} (see
    * {@link #addBoundTableEditResponseDisclosesResolvedNameOnCollision}), and its {@code edit}
    * response must disclose the resolved name too. Unlike
    * {@link #addQueryParamsTableThreadsQueryParamsIntoTheSharedHelper}, {@code editSvc} is
    * stubbed to actually invoke the {@code applyOnRuntime} lambda against a real
    * {@link Worksheet} (a pre-existing "Products" assembly forces the collision), and the fake
    * query is staged with a non-empty output column so the empty-column check does not throw
    * before the assembly is added -- none of the existing {@code addQueryParamsTable*} tests
    * drive it this far.
    */
   @Test
   void addQueryParamsTableEditResponseDisclosesResolvedNameOnCollision() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeNamedConnector");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      query.setOutputColumns(new XTypeNode[] { new XTypeNode("id") });

      EditRequest req = addQueryParamsTableRequest("Products", "MyDatasource", null, null, null,
         null, null, Map.of("endpoint", "Repos", "jsonPath", "$.data"));

      Worksheet ws = new Worksheet();
      ws.addAssembly(new TabularTableAssembly(ws, "Products"));
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(editSvc.applyOnRuntime(eq("TOK-QP8"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      ResponseEntity<EditResponse> response;

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         response = ctrl.edit("TOK-QP8", req, agent);
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }

      assertNotNull(ws.getAssembly("Products1"),
         "with a real collision on 'Products' the new table must fall back to 'Products1'");
      assertNotNull(response.getBody(),
         "add_table's response must disclose the resolved assembly name");
      assertEquals("Products1", response.getBody().assemblyName(),
         "the response must report the ACTUAL resolved name, not the requested 'Products'");
   }

   /**
    * Regression for round-1 review finding 2 on the row-cap PR: proves
    * {@code TabularEndpointBindingSupport.requireRowCapWhenPaged} is actually wired into
    * {@code addQueryParamsTable} too, not just {@code addTabularTable}'s two branches.
    * {@code queryParams.endpoint = "Paged"} drives {@code FakeNamedConnectorQuery.isPaged()} to
    * {@code true} the same way the endpoint-form test above does; the connector-agnostic
    * queryParams form has no {@code endpoint} concept of its own, but reuses the same fixture
    * property here purely as a paged/unpaged toggle.
    */
   @Test
   void addQueryParamsTableRejectsPagedConnectorWithoutMaxRows() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeNamedConnector");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      EditRequest req = addQueryParamsTableRequestWithMaxRows(
         "t1", "MyDatasource", Map.of("endpoint", "Paged"), null);

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ctrl.edit("TOK-QP8", req, agent));
         assertTrue(ex.getMessage().contains("paginated"), ex.getMessage());
         verifyNoInteractions(editSvc);
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }
   }

   /** Same wiring as {@link #addQueryParamsTableRejectsPagedConnectorWithoutMaxRows}, but a
    *  positive {@code maxRows} must satisfy the check and let execution reach {@code editService}. */
   @Test
   void addQueryParamsTableAllowsPagedConnectorWithMaxRows() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(ds.getType()).thenReturn("FakeNamedConnector");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      EditRequest req = addQueryParamsTableRequestWithMaxRows(
         "t1", "MyDatasource", Map.of("endpoint", "Paged"), 500);

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         assertDoesNotThrow(() -> ctrl.edit("TOK-QP9", req, agent),
            "a positive maxRows must satisfy the row-cap check on a paginated connector");
         verify(editSvc).applyOnRuntime(eq("TOK-QP9"), eq(agent), any());
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }
   }

   // ---------------------------------------------------------------------------
   // add_table with a logicalModel — naming for addLogicalModelTable()
   // ---------------------------------------------------------------------------

   /**
    * Regression for Bug PVA-005: {@code addLogicalModelTable} carries the identical
    * wrong-overload {@code AssetUtil.getNextName(ws, prefix)} bug as {@code addBoundTable}
    * (same file, a different call site) -- with no colliding assembly on the worksheet,
    * {@code add_table(table:"Customer", datasource:..., logicalModel:...)} must create an
    * assembly literally named {@code "Customer"}, not {@code "Customer1"}.
    */
   @Test
   void addLogicalModelTableUsesPlainNameWhenNoCollision() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      XEntity customerEntity = new XEntity("Customer");
      customerEntity.addAttribute(new XAttribute("Name", "SA.CUSTOMERS", "NAME", XSchema.STRING));
      XLogicalModel orderModel = new XLogicalModel("Order Model");
      orderModel.addEntity(customerEntity);

      // See addNamedGroupWithLogicalModelBuildsRealDatasourceSourceInfo() above for why the
      // XDataModel/logical model lookup is mocked directly rather than exercised through a real
      // DataSourceRegistry-backed XDataModel.
      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getLogicalModel("Order Model")).thenReturn(orderModel);

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("Examples/Orders"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      when(dataSourceService.getDataModel("Examples/Orders")).thenReturn(dataModel);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSourceService.checkPermission(any(AssetEntry.class), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-LM1"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("Customer", "Examples/Orders", "Order Model",
         null, null, null, null, null, null, null, null, null);

      ctrl.edit("TOK-LM1", req, agent);

      assertNotNull(ws.getAssembly("Customer"),
         "with no collision the new entity table must keep the plain name, not 'Customer1'");
      assertNull(ws.getAssembly("Customer1"),
         "no 'Customer1'-suffixed assembly should be created when 'Customer' was free");
   }

   /**
    * Companion to {@link #addLogicalModelTableUsesPlainNameWhenNoCollision()}: proves the fix did
    * not remove the genuine-collision fallback for this call site either.
    */
   @Test
   void addLogicalModelTableStillSuffixesOnRealCollision() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      XEntity customerEntity = new XEntity("Customer");
      customerEntity.addAttribute(new XAttribute("Name", "SA.CUSTOMERS", "NAME", XSchema.STRING));
      XLogicalModel orderModel = new XLogicalModel("Order Model");
      orderModel.addEntity(customerEntity);

      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getLogicalModel("Order Model")).thenReturn(orderModel);

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("Examples/Orders"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      when(dataSourceService.getDataModel("Examples/Orders")).thenReturn(dataModel);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSourceService.checkPermission(any(AssetEntry.class), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      Worksheet ws = new Worksheet();
      ws.addAssembly(new BoundTableAssembly(ws, "Customer"));
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-LM2"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("Customer", "Examples/Orders", "Order Model",
         null, null, null, null, null, null, null, null, null);

      ctrl.edit("TOK-LM2", req, agent);

      assertNotNull(ws.getAssembly("Customer1"),
         "with a real collision on 'Customer' the new entity table must fall back to 'Customer1'");
   }

   // ---------------------------------------------------------------------------
   // add_named_group — datasource-scoped "Only For" mode (Bug #76097)
   // ---------------------------------------------------------------------------

   /**
    * Builds an {@code add_named_group} EditRequest scoped to a datasource/logical-model or
    * physical-table path, routing to {@code addDatasourceScopedNamedGroup()}.
    */
   private static EditRequest namedGroupDatasourceRequest(
      String name, String datasource, String logicalModel, String schema, String catalog,
      String sourceTable, String attribute,
      List<WorksheetMutationSupport.GroupMapping> groupMappings, Boolean groupOthers)
   {
      return new EditRequest(
         "add_named_group",   // op
         null,                 // table
         null,                 // column
         name,                 // name
         null,                 // type
         null,                 // newName
         null,                 // field
         null,                 // operation
         null,                 // values
         null,                 // direction
         null,                 // groups
         null,                 // aggregates
         null,                 // expression
         false,                // sql
         null,                 // leftTable
         null,                 // leftKey
         null,                 // rightTable
         null,                 // rightKey
         null,                 // joinType
         null,                 // visible
         null,                 // tables
         null,                 // source
         null,                 // concatType
         null,                 // conditions
         null,                 // ranking
         null,                 // headerColumns
         null,                 // dateOption
         null,                 // boundaries
         datasource,           // datasource
         schema,               // schema
         catalog,              // catalog
         logicalModel,         // logicalModel
         null,                 // leftKeys
         null,                 // rightKeys
         null,                 // row
         null,                 // col
         null,                 // value
         null,                 // index
         null,                 // alias
         null,                 // description
         null,                 // maxRows
         null,                 // distinct
         null,                 // columnOrder
         groupMappings,        // groupMappings
         groupOthers,          // groupOthers
         null,                 // variableValues
         null,                 // x
         null,                 // y
         null,                 // label
         null,                 // defaultValue
         null,                 // mode
         null,                 // insert
         null,                 // subtables
         sourceTable,          // sourceTable
         attribute,            // attribute
         null,                 // endpoint
         null,                 // parameters
         null,                 // lookup
         null,                 // lookupExpandArrays
         null,                 // lookupTopLevelOnly
         null,                 // suffix
         null                  // customLookups
      );
   }

   @Test
   void addNamedGroupRejectsDatasourceCombinedWithTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = new EditRequest(
         "add_named_group",   // op
         "Customer1",          // table
         "State",              // column
         "G",                  // name
         null,                 // type
         null,                 // newName
         null,                 // field
         null,                 // operation
         null,                 // values
         null,                 // direction
         null,                 // groups
         null,                 // aggregates
         null,                 // expression
         false,                // sql
         null,                 // leftTable
         null,                 // leftKey
         null,                 // rightTable
         null,                 // rightKey
         null,                 // joinType
         null,                 // visible
         null,                 // tables
         null,                 // source
         null,                 // concatType
         null,                 // conditions
         null,                 // ranking
         null,                 // headerColumns
         null,                 // dateOption
         null,                 // boundaries
         "Examples/Orders",    // datasource
         null,                 // schema
         null,                 // catalog
         "Order Model",        // logicalModel
         null,                 // leftKeys
         null,                 // rightKeys
         null,                 // row
         null,                 // col
         null,                 // value
         null,                 // index
         null,                 // alias
         null,                 // description
         null,                 // maxRows
         null,                 // distinct
         null,                 // columnOrder
         List.<WorksheetMutationSupport.GroupMapping>of(), // groupMappings
         false,                // groupOthers
         null,                 // variableValues
         null,                 // x
         null,                 // y
         null,                 // label
         null,                 // defaultValue
         null,                 // mode
         null,                 // insert
         null,                 // subtables
         "Customer",           // sourceTable
         "State",              // attribute
         null,                 // endpoint
         null,                 // parameters
         null,                 // lookup
         null,                 // lookupExpandArrays
         null,                 // lookupTopLevelOnly
         null,                 // suffix
         null                  // customLookups
      );

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD1", req, agent));
      assertTrue(ex.getMessage().contains("mutually exclusive"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addNamedGroupRequiresSourceTableWhenDatasourceGiven() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = namedGroupDatasourceRequest(
         "G", "Examples/Orders", "Order Model", null, null, null, "State", List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD2", req, agent));
      assertTrue(ex.getMessage().contains("sourceTable"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addNamedGroupRequiresAttributeWhenDatasourceGiven() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = namedGroupDatasourceRequest(
         "G", "Examples/Orders", "Order Model", null, null, "Customer", null, List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD3", req, agent));
      assertTrue(ex.getMessage().contains("attribute"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   /**
    * PR #4765 review follow-up: {@code sourceTable}/{@code attribute}/{@code logicalModel}/
    * {@code schema}/{@code catalog} must require {@code datasource} server-side too, not only in
    * the plugin's own client-side validation -- without this guard, omitting {@code datasource}
    * (by mistake, or via any caller that bypasses the plugin) fell through to the legacy
    * {@code Editor.addNamedGroup(table, column, type, ...)} path with everything null, silently
    * creating a standalone string-typed grouping and discarding what the caller asked for.
    */
   @Test
   void addNamedGroupRejectsSourceTableWithoutDatasource() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = namedGroupDatasourceRequest(
         "G", null, null, null, null, "Customer", "State", List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD9", req, agent));
      assertTrue(ex.getMessage().contains("datasource"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addNamedGroupRejectsAttributeWithoutDatasource() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = namedGroupDatasourceRequest(
         "G", null, null, null, null, null, "State", List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD10", req, agent));
      assertTrue(ex.getMessage().contains("datasource"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   /**
    * End-to-end regression for Bug #76097: {@code add_named_group} with {@code datasource} +
    * {@code logicalModel} + {@code sourceTable} (entity) + {@code attribute} must produce the
    * same kind of {@code attachedSource}/{@code attachedAttribute} a human creates via the
    * Composer's own "Add Grouping" dialog — {@code SourceInfo(MODEL, datasource, logicalModel)},
    * and an {@code AttributeRef(entity, attribute)} — so "Only For" and "Attribute" resolve
    * correctly, unlike the {@code table}+{@code column} worksheet-attached mode.
    */
   @Test
   void addNamedGroupWithLogicalModelBuildsRealDatasourceSourceInfo() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      XAttribute stateAttr = new XAttribute("State", "SA.CUSTOMERS", "STATE", XSchema.STRING);
      XEntity customerEntity = new XEntity("Customer");
      customerEntity.addAttribute(stateAttr);
      XLogicalModel orderModel = new XLogicalModel("Order Model");
      orderModel.addEntity(customerEntity);

      // XDataModel.addLogicalModel() reaches for a Spring-managed DataSourceRegistry bean
      // (via XDataModel.getRegistry()) that isn't available outside a running application
      // context, so the model is mocked and getLogicalModel() stubbed directly instead of
      // actually registering orderModel on a real XDataModel.
      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getLogicalModel("Order Model")).thenReturn(orderModel);

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("Examples/Orders"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      when(dataSourceService.getDataModel("Examples/Orders")).thenReturn(dataModel);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSourceService.checkPermission(any(AssetEntry.class), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-NGD4"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("N", List.of("NJ", "NY", "NV")));
      EditRequest req = namedGroupDatasourceRequest(
         "State N Group", "Examples/Orders", "Order Model", null, null,
         "Customer", "State", mappings, true);

      ctrl.edit("TOK-NGD4", req, agent);

      DefaultNamedGroupAssembly nga = (DefaultNamedGroupAssembly) ws.getAssembly("State N Group");
      assertNotNull(nga, "the datasource-scoped grouping must be created in the worksheet");

      SourceInfo attached = nga.getAttachedSource();
      assertEquals(SourceInfo.MODEL, attached.getType());
      assertEquals("Examples/Orders", attached.getPrefix());
      assertEquals("Order Model", attached.getSource());

      DataRef ref = nga.getAttachedAttribute();
      assertInstanceOf(AttributeRef.class, ((ColumnRef) ref).getDataRef());
      AttributeRef attrRef = (AttributeRef) ((ColumnRef) ref).getDataRef();
      assertEquals("Customer", attrRef.getEntity());
      assertEquals("State", attrRef.getAttribute());
      assertEquals(XSchema.STRING, ref.getDataType());

      assertEquals(AttachedAssembly.COLUMN_ATTACHED, nga.getAttachedType());
      assertNotNull(nga.getNamedGroupInfo().getGroupCondition("N"),
         "group mappings must still be applied");
   }

   /**
    * Bug #76788 (WBS-065): {@code addDatasourceScopedNamedGroup} builds a
    * {@code DefaultNamedGroupAssembly} and adds it to the worksheet without ever checking whether
    * that name already identifies a different, existing assembly -- {@link Worksheet#addAssembly}
    * would silently evict and replace it. The check must run inside the {@code applyOnRuntime}
    * callback (where {@code ws} first comes into scope), after the assembly is built and before
    * {@code ws.addAssembly}.
    */
   @Test
   void addNamedGroupRejectsNameCollisionWithUnrelatedExistingAssembly() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      XAttribute stateAttr = new XAttribute("State", "SA.CUSTOMERS", "STATE", XSchema.STRING);
      XEntity customerEntity = new XEntity("Customer");
      customerEntity.addAttribute(stateAttr);
      XLogicalModel orderModel = new XLogicalModel("Order Model");
      orderModel.addEntity(customerEntity);

      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getLogicalModel("Order Model")).thenReturn(orderModel);

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("Examples/Orders"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      when(dataSourceService.getDataModel("Examples/Orders")).thenReturn(dataModel);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSourceService.checkPermission(any(AssetEntry.class), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly preExisting = TestWorksheets.tableWithColumns(ws, "State N Group", "id");
      ws.addAssembly(preExisting);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-NGD-COLLIDE"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = namedGroupDatasourceRequest(
         "State N Group", "Examples/Orders", "Order Model", null, null,
         "Customer", "State", List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD-COLLIDE", req, agent));
      assertTrue(ex.getMessage().contains("State N Group"), ex.getMessage());

      assertSame(preExisting, ws.getAssembly("State N Group"));
      assertEquals(1, ws.getAssemblies().length);
   }

   /**
    * PR #4901 round-2 review follow-up: {@code addDatasourceScopedNamedGroup} builds a
    * {@code DefaultNamedGroupAssembly} and adds it directly, the same unescaped-CDATA write path
    * {@code createVariable} and the round-1 sites ({@code addJoin}/{@code duplicateAssembly}/
    * {@code renameVariable}) had before being guarded. Checked up front, before any permission
    * check or datasource/logical-model lookup, so a doomed name fails fast without touching
    * either mock.
    */
   @Test
   void addNamedGroupRefusesANameThatWouldBreakTheStoredXml() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = namedGroupDatasourceRequest(
         "bad]]>name", "Examples/Orders", "Order Model", null, null,
         "Customer", "State", List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD-CDATA", req, agent));
      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   // ---------------------------------------------------------------------------
   // edit_named_group — datasource-scoped retarget (Redmine #76902 WBS-074)
   // ---------------------------------------------------------------------------

   /** Same shape as {@link #namedGroupDatasourceRequest}, but for {@code edit_named_group}. */
   private static EditRequest editNamedGroupDatasourceRequest(
      String name, String datasource, String logicalModel, String schema, String catalog,
      String sourceTable, String attribute,
      List<WorksheetMutationSupport.GroupMapping> groupMappings, Boolean groupOthers)
   {
      return new EditRequest(
         "edit_named_group",  // op
         null,                 // table
         null,                 // column
         name,                 // name
         null,                 // type
         null,                 // newName
         null,                 // field
         null,                 // operation
         null,                 // values
         null,                 // direction
         null,                 // groups
         null,                 // aggregates
         null,                 // expression
         false,                // sql
         null,                 // leftTable
         null,                 // leftKey
         null,                 // rightTable
         null,                 // rightKey
         null,                 // joinType
         null,                 // visible
         null,                 // tables
         null,                 // source
         null,                 // concatType
         null,                 // conditions
         null,                 // ranking
         null,                 // headerColumns
         null,                 // dateOption
         null,                 // boundaries
         datasource,           // datasource
         schema,               // schema
         catalog,              // catalog
         logicalModel,         // logicalModel
         null,                 // leftKeys
         null,                 // rightKeys
         null,                 // row
         null,                 // col
         null,                 // value
         null,                 // index
         null,                 // alias
         null,                 // description
         null,                 // maxRows
         null,                 // distinct
         null,                 // columnOrder
         groupMappings,        // groupMappings
         groupOthers,          // groupOthers
         null,                 // variableValues
         null,                 // x
         null,                 // y
         null,                 // label
         null,                 // defaultValue
         null,                 // mode
         null,                 // insert
         null,                 // subtables
         sourceTable,          // sourceTable
         attribute,            // attribute
         null,                 // endpoint
         null,                 // parameters
         null,                 // lookup
         null,                 // lookupExpandArrays
         null,                 // lookupTopLevelOnly
         null,                 // suffix
         null                  // customLookups
      );
   }

   /**
    * Builds a worksheet with a table T (columns state/amount), a {@code DefaultNamedGroupAssembly}
    * "NortheastGroup" COLUMN_ATTACHED to T's "state" column, and a {@link GroupRef} on T that
    * already references it and resolves cleanly -- the pre-retarget state both new tests below
    * start from.
    */
   private static EmbeddedTableAssembly buildTableWithResolvedNamedGroupReference(Worksheet ws)
      throws Exception
   {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "state", "amount");
      ws.addAssembly(t);

      DataRef stateRef = t.getColumnSelection(false).getAttribute("state");

      NamedGroupInfo ngi = new NamedGroupInfo();
      ngi.setOthers(XConstants.LEAVE_OTHERS);
      DefaultNamedGroupAssembly nga = new DefaultNamedGroupAssembly(ws, "NortheastGroup");
      nga.setNamedGroupInfo(ngi);
      nga.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
      nga.setAttachedSource(new SourceInfo(SourceInfo.ASSET, null, "T"));
      nga.setAttachedAttribute(stateRef);
      ws.addAssembly(nga);

      GroupRef gr = new GroupRef(stateRef);
      gr.setNamedGroupAssembly("NortheastGroup");
      gr.update(ws);
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(gr);
      t.setAggregateInfo(ainfo);

      assertNotNull(gr.getNamedGroupInfo(),
         "sanity: the pre-existing reference must resolve before the retarget under test");
      return t;
   }

   /**
    * WBS-074's canonical scenario: redrawing "NortheastGroup" onto a different source column
    * ("Region" instead of its current "state" attachment) while a worksheet aggregate still
    * groups by "state" via it. Without the pre-check, {@code GroupRef.update()}'s own
    * {@code COLUMN_ATTACHED} mismatch branch would silently null out {@code getNamedGroupInfo()}
    * for that reference and report the call a success. Must instead refuse loud and leave both
    * the attachment and the reference untouched.
    */
   @Test
   void editNamedGroupDatasourceRetargetRefusesWhenExistingReferenceWouldMismatch() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      XAttribute regionAttr = new XAttribute("Region", "SA.CUSTOMERS", "REGION", XSchema.STRING);
      XEntity customerEntity = new XEntity("Customer");
      customerEntity.addAttribute(regionAttr);
      XLogicalModel orderModel = new XLogicalModel("Order Model");
      orderModel.addEntity(customerEntity);

      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getLogicalModel("Order Model")).thenReturn(orderModel);

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("Examples/Orders"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      when(dataSourceService.getDataModel("Examples/Orders")).thenReturn(dataModel);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSourceService.checkPermission(any(AssetEntry.class), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      Worksheet ws = new Worksheet();
      buildTableWithResolvedNamedGroupReference(ws);
      NamedGroupAssembly ngaBefore = (NamedGroupAssembly) ws.getAssembly("NortheastGroup");
      DataRef attachedBefore = ngaBefore.getAttachedAttribute();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ENG-MISMATCH"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editNamedGroupDatasourceRequest(
         "NortheastGroup", "Examples/Orders", "Order Model", null, null,
         "Customer", "Region", List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ENG-MISMATCH", req, agent));
      assertTrue(ex.getMessage().contains("NortheastGroup"), ex.getMessage());
      assertTrue(ex.getMessage().contains("T"), ex.getMessage());

      TableAssembly t = (TableAssembly) ws.getAssembly("T");
      GroupRef gr = t.getAggregateInfo().getGroups()[0];
      assertSame(attachedBefore, ngaBefore.getAttachedAttribute(),
         "the attachment must be untouched when the retarget is refused");
      assertNotNull(gr.getNamedGroupInfo(),
         "the pre-existing reference must still resolve -- nothing may be mutated on refusal");
   }

   /**
    * Companion to the mismatch-refusal test above: when no worksheet-side reference would be
    * broken (here, nothing groups by "NortheastGroup" at all), the same retarget must go through
    * and actually move the attachment.
    */
   @Test
   void editNamedGroupDatasourceRetargetSucceedsWhenNoConflictingReference() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      XAttribute regionAttr = new XAttribute("Region", "SA.CUSTOMERS", "REGION", XSchema.STRING);
      XEntity customerEntity = new XEntity("Customer");
      customerEntity.addAttribute(regionAttr);
      XLogicalModel orderModel = new XLogicalModel("Order Model");
      orderModel.addEntity(customerEntity);

      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getLogicalModel("Order Model")).thenReturn(orderModel);

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("Examples/Orders"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      when(dataSourceService.getDataModel("Examples/Orders")).thenReturn(dataModel);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSourceService.checkPermission(any(AssetEntry.class), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      Worksheet ws = new Worksheet();
      NamedGroupInfo ngi = new NamedGroupInfo();
      ngi.setOthers(XConstants.LEAVE_OTHERS);
      DefaultNamedGroupAssembly nga = new DefaultNamedGroupAssembly(ws, "NortheastGroup");
      nga.setNamedGroupInfo(ngi);
      nga.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
      nga.setAttachedSource(new SourceInfo(SourceInfo.ASSET, null, "T"));
      nga.setAttachedAttribute(new AttributeRef(null, "state"));
      ws.addAssembly(nga);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-ENG-OK"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      List<WorksheetMutationSupport.GroupMapping> mappings =
         List.of(new WorksheetMutationSupport.GroupMapping("East", List.of("NY", "NJ")));
      EditRequest req = editNamedGroupDatasourceRequest(
         "NortheastGroup", "Examples/Orders", "Order Model", null, null,
         "Customer", "Region", mappings, true);

      ctrl.edit("TOK-ENG-OK", req, agent);

      NamedGroupAssembly ngaAfter = (NamedGroupAssembly) ws.getAssembly("NortheastGroup");
      assertEquals("Region", ngaAfter.getAttachedAttribute().getAttribute());
      assertNotNull(ngaAfter.getNamedGroupInfo().getGroupCondition("East"),
         "the new mapping must actually be applied");
   }

   // ---------------------------------------------------------------------------
   // editSqlQuery — FREE_FORM_SQL / ACCESS permission gate
   // ---------------------------------------------------------------------------

   @Test
   void editSqlQueryDeniedThrowsSecurityException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(editSvc,
         mock(DataSourceService.class), securityEngine, mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editSqlQueryRequest("SqlTable1", "SELECT * FROM foo");

      assertThrows(SecurityException.class, () -> ctrl.edit("TOK-ES", req, agent));

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verifyNoInteractions(editSvc);
   }

   @Test
   void editSqlQueryGrantedPassesPermissionGate() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      WorksheetAgentController ctrl = securityController(editSvc,
         mock(DataSourceService.class), securityEngine, mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editSqlQueryRequest("SqlTable1", "SELECT * FROM foo");

      // editSvc is a plain mock: applyOnRuntime() is never actually executed, so the call
      // completes without exception — proving the permission check did not block it.
      ctrl.edit("TOK-ES2", req, agent);

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verify(editSvc).applyOnRuntime(eq("TOK-ES2"), eq(agent), any());
   }

   @Test
   void editSqlQueryDeniedByDatasourceReadThrowsPairingException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      // Real runtime worksheet holding a SQL-bound table whose query is bound to datasource "myds".
      // The datasource/query are mocked: constructing a real JDBCDataSource pulls a
      // CredentialService bean the lightweight test context does not provide, and the READ-denial
      // path throws before the query's SQL definition is ever touched.
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("myds");
      JDBCQuery q = mock(JDBCQuery.class);
      when(q.getDataSource()).thenReturn(ds);
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(q);
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ES3"), any())).thenReturn(session("TOK-ES3"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      // Free-Form SQL right is granted, so we get past the action gate and into the lambda.
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      // ...but READ on the assembly's bound datasource is denied.
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(editSvc, dataSourceService, securityEngine,
         mock(MetadataApiService.class), mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editSqlQueryRequest("SqlTable1", "SELECT * FROM foo");

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ES3", req, agent));
      assertTrue(ex.getMessage().contains("READ permission"),
                 "error should name the denied datasource READ, got: " + ex.getMessage());

      verify(dataSourceService).checkPermission(eq("myds"), eq(ResourceAction.READ), eq(agent));
   }

   /**
    * Bug #76350 follow-on (item A), review round 1: {@code editSqlQuery} calls
    * {@code refreshColumnSelection} unwrapped after committing the new SQL/column selection --
    * the same unbounded-hang shape as {@code refreshData}'s single-table branch (3a), reachable
    * on any SQL-bound table since {@code set_group_aggregate} can make one crosstab-shaped.
    * {@code queryManagerService.getColumnSelection} is stubbed directly (bypassing real JDBC
    * metadata resolution) and the SQL string has no {@code FROM} clause, so
    * {@code JDBCUtil.fixUniformSQLInfo}'s in-memory shortcut ({@code sql.getTableCount() <= 0})
    * fires without a live datasource or a {@code Config} Spring bean -- letting the test reach
    * the wrap without a live JDBC connection, matching this class's existing "stop before real
    * query execution" convention for SQL-query tests (e.g.
    * {@code addSqlQueryGrantedPassesBothPermissionGates}, which also uses a table-less
    * {@code "SELECT 1"}).
    */
   @Test
   void editSqlQueryThrowsRenderNotReadyWhenColumnSelectionIsSlow() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      doAnswer(invocation -> {
         Thread.sleep(3_000);
         return null;
      }).when(box).refreshColumnSelection(eq("SqlTable1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ES4"), any())).thenReturn(session("TOK-ES4"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      ColumnSelection newColumns = new ColumnSelection();
      newColumns.addAttribute(new ColumnRef(new AttributeRef(null, "a")));
      newColumns.addAttribute(new ColumnRef(new AttributeRef(null, "b")));
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(newColumns);

      WorksheetAgentController ctrl = securityController(editSvc, mock(DataSourceService.class),
         securityEngine, mock(MetadataApiService.class), mock(XRepository.class),
         queryManagerService);

      EditRequest req = editSqlQueryRequest("SqlTable1", "SELECT 1");

      assertThrows(RenderNotReadyException.class,
         () -> ctrl.edit("TOK-ES4", req, agent));
   }

   /**
    * Bug #76500 (WSQ-002): {@code editSqlQuery} used to end its {@code applyOnRuntime} lambda
    * with a bare {@code return null;}, never calling {@code detectUndeclaredVariables} the way
    * {@code addSqlQuery} does -- so an unresolved {@code $(name)} placeholder in an edited SQL
    * string was silently accepted with no signal, unlike {@code add_sql_query}. Exercises the
    * new bespoke {@code PUT .../sql-query} endpoint (mirroring {@code addSqlQuery}'s shape)
    * rather than the shared void {@code /edit} dispatcher, since only the bespoke endpoint can
    * carry a typed response back to the caller.
    */
   @Test
   void editSqlQueryEndpointReturnsUndeclaredVariables() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      // No sleep this time -- refreshColumnSelection completes immediately so execution reaches
      // the tail of the lambda instead of timing out (contrast
      // editSqlQueryThrowsRenderNotReadyWhenColumnSelectionIsSlow).
      doNothing().when(box).refreshColumnSelection(eq("SqlTable1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ES5"), any())).thenReturn(session("TOK-ES5"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      ColumnSelection newColumns = new ColumnSelection();
      newColumns.addAttribute(new ColumnRef(new AttributeRef(null, "a")));
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(newColumns);

      WorksheetAgentController ctrl = securityController(editSvc, mock(DataSourceService.class),
         securityEngine, mock(MetadataApiService.class), mock(XRepository.class),
         queryManagerService);

      VariableAssemblyModelInfo undeclared = new VariableAssemblyModelInfo();
      undeclared.setName("MV.ORDER_DATE.Max");
      WSCollectVariablesCommand command = WSCollectVariablesCommand.builder()
         .varInfos(List.of(undeclared))
         .build();

      // Table-less "SELECT 1" (no FROM clause), same as editSqlQueryThrowsRenderNotReadyWhenColumn-
      // SelectionIsSlow above -- fixUniformSQLInfo's in-memory shortcut (sql.getTableCount() <= 0)
      // fires without a live datasource/Config bean, which this lightweight test context does not
      // provide. WorksheetEventUtil.refreshVariables is mocked below regardless of SQL content, so
      // detecting a $(name) placeholder in the real SQL text is not what this test is verifying --
      // that detection is detectUndeclaredVariables'/VarSQL's own job, already covered elsewhere.
      WorksheetAgentController.EditSqlQueryRequest body =
         new WorksheetAgentController.EditSqlQueryRequest("SqlTable1", "SELECT 1");

      try(MockedStatic<WorksheetEventUtil> eventUtil =
             mockStatic(WorksheetEventUtil.class, CALLS_REAL_METHODS)) {
         eventUtil.when(() -> WorksheetEventUtil.refreshVariables(eq(rws), any(), eq(false)))
            .thenReturn(command);

         WorksheetAgentController.SqlQueryResponse response =
            ctrl.editSqlQuery("TOK-ES5", body, agent);

         assertEquals("SqlTable1", response.tableName());
         assertEquals(List.of("MV.ORDER_DATE.Max"), response.undeclaredVariables());
         assertEquals(List.of(), response.variablesNeedingValues());
      }
   }

   /**
    * WBS-059 regression: a variable that IS declared (a {@code DefaultVariableAssembly} exists)
    * but has no explicit session value yet must NOT be reported as undeclared -- it is a
    * different signal ({@code variablesNeedingValues}), not an error. Before this fix,
    * {@code detectUndeclaredVariables} returned every name in {@code varInfos()} verbatim,
    * regardless of whether a matching assembly existed.
    */
   @Test
   void editSqlQueryEndpointDoesNotReportDeclaredVariableAsUndeclared() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(sqlt);

      DefaultVariableAssembly declaredVar = new DefaultVariableAssembly(ws, "declaredVar");
      ws.addAssembly(declaredVar);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      doNothing().when(box).refreshColumnSelection(eq("SqlTable1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ES6"), any())).thenReturn(session("TOK-ES6"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      ColumnSelection newColumns = new ColumnSelection();
      newColumns.addAttribute(new ColumnRef(new AttributeRef(null, "a")));
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(newColumns);

      WorksheetAgentController ctrl = securityController(editSvc, mock(DataSourceService.class),
         securityEngine, mock(MetadataApiService.class), mock(XRepository.class),
         queryManagerService);

      // Simulates WSCollectVariablesCommand reporting "declaredVar" because it has no explicit
      // session value yet in AssetQuerySandbox.vars -- even though a DefaultVariableAssembly for
      // it already exists in the worksheet (added above).
      VariableAssemblyModelInfo needsValue = new VariableAssemblyModelInfo();
      needsValue.setName("declaredVar");
      WSCollectVariablesCommand command = WSCollectVariablesCommand.builder()
         .varInfos(List.of(needsValue))
         .build();

      WorksheetAgentController.EditSqlQueryRequest body =
         new WorksheetAgentController.EditSqlQueryRequest("SqlTable1", "SELECT 1");

      try(MockedStatic<WorksheetEventUtil> eventUtil =
             mockStatic(WorksheetEventUtil.class, CALLS_REAL_METHODS)) {
         eventUtil.when(() -> WorksheetEventUtil.refreshVariables(eq(rws), any(), eq(false)))
            .thenReturn(command);

         WorksheetAgentController.SqlQueryResponse response =
            ctrl.editSqlQuery("TOK-ES6", body, agent);

         assertEquals("SqlTable1", response.tableName());
         assertEquals(List.of(), response.undeclaredVariables());
         assertEquals(List.of("declaredVar"), response.variablesNeedingValues());
      }
   }

   /**
    * Bug #79062: a variable mirrored from another worksheet is declared under a hidden-copy
    * assembly name (here OUTER1_0) that differs from the variable's own name (RATE), the name a
    * `$(RATE)` reference carries. It must count as declared, not as undeclared.
    */
   @Test
   void editSqlQueryEndpointTreatsAMirroredVariableAsDeclaredByItsOwnName() throws Exception {
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly hiddenCopy = new DefaultVariableAssembly(ws, "OUTER1_0");
      hiddenCopy.setVariable(new AssetVariable("RATE"));
      ws.addAssembly(hiddenCopy);

      WorksheetAgentController.SqlQueryResponse response =
         editSqlQueryReportingVariables(ws, "RATE");

      assertEquals(List.of(), response.undeclaredVariables());
      assertEquals(List.of("RATE"), response.variablesNeedingValues());
   }

   @Test
   void editSqlQueryEndpointStillReportsANameNoVariableDeclaresAsUndeclared() throws Exception {
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly hiddenCopy = new DefaultVariableAssembly(ws, "OUTER1_0");
      hiddenCopy.setVariable(new AssetVariable("RATE"));
      ws.addAssembly(hiddenCopy);

      WorksheetAgentController.SqlQueryResponse response =
         editSqlQueryReportingVariables(ws, "RATE", "typo");

      assertEquals(List.of("typo"), response.undeclaredVariables());
      assertEquals(List.of("RATE"), response.variablesNeedingValues());
   }

   /** Runs editSqlQuery on a worksheet whose variable collection reports the given names. */
   private WorksheetAgentController.SqlQueryResponse editSqlQueryReportingVariables(
      Worksheet ws, String... names) throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      doNothing().when(box).refreshColumnSelection(eq("SqlTable1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-79062"), any())).thenReturn(session("TOK-79062"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      ColumnSelection newColumns = new ColumnSelection();
      newColumns.addAttribute(new ColumnRef(new AttributeRef(null, "a")));
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(newColumns);

      WorksheetAgentController ctrl = securityController(editSvc, mock(DataSourceService.class),
         securityEngine, mock(MetadataApiService.class), mock(XRepository.class),
         queryManagerService);

      List<VariableAssemblyModelInfo> infos = new ArrayList<>();

      for(String name : names) {
         VariableAssemblyModelInfo info = new VariableAssemblyModelInfo();
         info.setName(name);
         infos.add(info);
      }

      WSCollectVariablesCommand command = WSCollectVariablesCommand.builder()
         .varInfos(infos)
         .build();

      try(MockedStatic<WorksheetEventUtil> eventUtil =
             mockStatic(WorksheetEventUtil.class, CALLS_REAL_METHODS)) {
         eventUtil.when(() -> WorksheetEventUtil.refreshVariables(eq(rws), any(), eq(false)))
            .thenReturn(command);

         return ctrl.editSqlQuery("TOK-79062",
            new WorksheetAgentController.EditSqlQueryRequest("SqlTable1", "SELECT 1"), agent);
      }
   }

   // ---------------------------------------------------------------------------
   // editSqlQuery -- differentiated "no columns" guard message (bug #76500, WSQ-006)
   // ---------------------------------------------------------------------------

   /**
    * WSQ-006: a genuine grammar failure should surface the syntax-specific message, not the old
    * generic "SQL could not be parsed or no columns detected" wording. {@code sql.getParseResult()}
    * is stubbed directly to {@code PARSE_FAILED} via {@code stubUniformSqlConstruction} -- see its
    * javadoc for why real SQL-string-driven parsing is not used here.
    */
   @Test
   void editSqlQueryThrowsParseFailedMessageForMalformedSql() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-EPF"), any())).thenReturn(session("TOK-EPF"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(new ColumnSelection());

      WorksheetAgentController ctrl = securityController(editSvc, mock(DataSourceService.class),
         securityEngine, mock(MetadataApiService.class), mock(XRepository.class),
         queryManagerService);

      EditRequest req = editSqlQueryRequest("SqlTable1", "SELECT 1");

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> stubUniformSqlConstruction(m, UniformSQL.PARSE_FAILED)))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.edit("TOK-EPF", req, agent));
         assertTrue(ex.getMessage().contains("SQL could not be parsed — check syntax"),
                    "should surface the parse-failed variant, got: " + ex.getMessage());
      }
   }

   /**
    * WSQ-006: when the SQL parses fine but the guard still fires (empty columns), a captured
    * driver error should be surfaced instead of the generic message. {@code sql.getParseResult()}
    * is stubbed to {@code PARSE_SUCCESS} via {@code stubUniformSqlConstruction}, and
    * {@code lastQueryError} is set directly on the real {@code scratchQuery} instance the mocked
    * {@code queryManagerService.getColumnSelection} is invoked with -- exactly mirroring what
    * {@code QueryManagerService.getColumnSelection}'s real fallback branch does
    * (QueryManagerService.java:1955). Per the lead's decision, no known real SQL shape reliably
    * reaches this branch (per two contested refute rounds), so both signals are mocked directly.
    */
   @Test
   void editSqlQueryThrowsDriverErrorMessageWhenLastQueryErrorIsSet() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-EDE"), any())).thenReturn(session("TOK-EDE"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenAnswer(inv -> {
            JDBCQuery q = inv.getArgument(0);
            q.setLastQueryError(new Exception("Column \"PRODUCT_ID\" not found in ORDERS"));
            return new ColumnSelection();
         });

      WorksheetAgentController ctrl = securityController(editSvc, mock(DataSourceService.class),
         securityEngine, mock(MetadataApiService.class), mock(XRepository.class),
         queryManagerService);

      EditRequest req = editSqlQueryRequest("SqlTable1", "SELECT 1");

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> stubUniformSqlConstruction(m, UniformSQL.PARSE_SUCCESS)))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.edit("TOK-EDE", req, agent));
         assertTrue(ex.getMessage().contains("syntactically valid but could not be resolved"),
                    "should surface the driver-error variant, got: " + ex.getMessage());
         assertTrue(ex.getMessage().contains("Column \"PRODUCT_ID\" not found in ORDERS"),
                    "should include the captured driver error text, got: " + ex.getMessage());
      }
   }

   /**
    * WSQ-006: if the guard fires but neither {@code parseResult} nor {@code lastQueryError}
    * explain why, the original generic message must still be the fallback.
    */
   @Test
   void editSqlQueryFallsBackToGenericMessageWhenNeitherSignalExplainsIt() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-EGEN"), any())).thenReturn(session("TOK-EGEN"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // lastQueryError is left unset (defaults to null on a freshly-constructed JDBCQuery) --
      // this is the "driver call succeeded but returned nothing" outcome, not exercised above.
      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(new ColumnSelection());

      WorksheetAgentController ctrl = securityController(editSvc, mock(DataSourceService.class),
         securityEngine, mock(MetadataApiService.class), mock(XRepository.class),
         queryManagerService);

      EditRequest req = editSqlQueryRequest("SqlTable1", "SELECT 1");

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> stubUniformSqlConstruction(m, UniformSQL.PARSE_SUCCESS)))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.edit("TOK-EGEN", req, agent));
         assertTrue(
            ex.getMessage().contains("SQL could not be parsed or no columns detected"),
            "should fall back to the original generic message, got: " + ex.getMessage());
      }
   }

   // ---------------------------------------------------------------------------
   // addSqlQuery — FREE_FORM_SQL / ACCESS + datasource READ permission gates
   // ---------------------------------------------------------------------------

   @Test
   void addSqlQueryDeniedByFreeFormSqlThrowsSecurityException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(mock(WorksheetEditService.class),
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", null);

      assertThrows(SecurityException.class, () -> ctrl.addSqlQuery("TOK-SQ", body, agent));

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(xrepository);
   }

   @Test
   void addSqlQueryDeniedByDatasourceReadThrowsPairingException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(mock(WorksheetEditService.class),
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.addSqlQuery("TOK-SQ2", body, agent));
      assertTrue(ex.getMessage().contains("READ permission"));

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verify(dataSourceService).checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent));
      verifyNoInteractions(xrepository);
   }

   @Test
   void addSqlQueryGrantedPassesBothPermissionGates() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      WorksheetAgentController ctrl = securityController(mock(WorksheetEditService.class),
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", null);

      // Both gates pass; fails further downstream because xrepository (unstubbed mock)
      // returns null for getDataSource(), which is expected — we only assert that both
      // permission checks let execution proceed past them.
      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.addSqlQuery("TOK-SQ3", body, agent));
      assertTrue(ex.getMessage().contains("Datasource not found"));

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verify(dataSourceService).checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent));
   }

   /**
    * PR #4901 round-2 review follow-up: like {@code createEmbeddedTable}, {@code addSqlQuery}'s
    * {@code name} is the caller-supplied table name used verbatim -- never run through
    * {@code AssetUtil.normalizeTable} -- and is written directly into a new
    * {@code SQLBoundTableAssembly} added to the worksheet. Checked as the very first thing inside
    * the {@code applyOnRuntime} callback, before any SQL parsing or JDBC metadata work, so a
    * doomed name fails fast without needing the SQL to actually parse.
    */
   @Test
   void addSqlQueryRefusesANameThatWouldBreakTheStoredXml() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      // Mocked: constructing a real JDBCDataSource pulls a CredentialService bean the
      // lightweight test context does not provide (see editSqlQueryDeniedByDatasourceReadThrows-
      // PairingException above), and requireStorableName throws before this datasource's
      // metadata is ever touched.
      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(xrepository.getDataSource("MyDatasource")).thenReturn(jdbcDs);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-SQ-CDATA"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", "bad]]>name");

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.addSqlQuery("TOK-SQ-CDATA", body, agent));
      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      assertNull(ws.getAssembly("bad]]>name"));
   }

   /**
    * Bug #76788 (WBS-065): {@code addSqlQuery}'s caller-supplied {@code name} is used verbatim,
    * like {@code createEmbeddedTable}, and was never checked against an existing, unrelated
    * assembly before {@link Worksheet#addAssembly} silently evicts and replaces it. The check must
    * run immediately after constructing {@code SQLBoundTableAssembly} -- before the {@code
    * JDBCQuery}/{@code UniformSQL} construction, the up-to-10s synchronous parse wait, and the real
    * {@code QueryManagerService.getColumnSelection} JDBC round-trip -- so a doomed call fails fast.
    * {@code queryManagerService} must never be touched: that is the proof the check runs before
    * those expensive steps, not merely somewhere before {@code ws.addAssembly}.
    */
   @Test
   void addSqlQueryRejectsNameCollisionWithUnrelatedExistingAssembly() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);
      QueryManagerService queryManagerService = mock(QueryManagerService.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(xrepository.getDataSource("MyDatasource")).thenReturn(jdbcDs);

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly preExisting = TestWorksheets.tableWithColumns(ws, "MyTable", "id");
      ws.addAssembly(preExisting);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-SQ-COLLIDE"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, queryManagerService);

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", "MyTable");

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.addSqlQuery("TOK-SQ-COLLIDE", body, agent));
      assertTrue(ex.getMessage().contains("MyTable"), ex.getMessage());

      assertSame(preExisting, ws.getAssembly("MyTable"));
      verifyNoInteractions(queryManagerService);
   }

   // ---------------------------------------------------------------------------
   // addSqlQuery -- differentiated "no columns" guard message (bug #76500, WSQ-006)
   // ---------------------------------------------------------------------------

   /**
    * WSQ-006: a genuine grammar failure should surface the syntax-specific message, not the old
    * generic "SQL could not be parsed or no columns detected" wording. {@code sql.getParseResult()}
    * is stubbed directly to {@code PARSE_FAILED} via {@code stubUniformSqlConstruction} -- see its
    * javadoc for why real SQL-string-driven parsing is not used here.
    */
   @Test
   void addSqlQueryThrowsParseFailedMessageForMalformedSql() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(xrepository.getDataSource("MyDatasource")).thenReturn(jdbcDs);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-SQ-PF"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(new ColumnSelection());

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, queryManagerService);

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", null);

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> stubUniformSqlConstruction(m, UniformSQL.PARSE_FAILED)))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.addSqlQuery("TOK-SQ-PF", body, agent));
         assertTrue(ex.getMessage().contains("SQL could not be parsed — check syntax"),
                    "should surface the parse-failed variant, got: " + ex.getMessage());
      }
   }

   /**
    * WSQ-006: when the SQL parses fine but the guard still fires (empty columns), a captured
    * driver error should be surfaced instead of the generic message. {@code sql.getParseResult()}
    * is stubbed to {@code PARSE_SUCCESS} via {@code stubUniformSqlConstruction}, and
    * {@code lastQueryError} is set directly on the real {@code JDBCQuery} instance the mocked
    * {@code queryManagerService.getColumnSelection} is invoked with, exactly mirroring what
    * {@code QueryManagerService.getColumnSelection}'s real fallback branch does
    * (QueryManagerService.java:1955). Per the lead's decision, no known real SQL shape reliably
    * reaches this branch (per two contested refute rounds), so both signals are mocked directly.
    */
   @Test
   void addSqlQueryThrowsDriverErrorMessageWhenLastQueryErrorIsSet() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(xrepository.getDataSource("MyDatasource")).thenReturn(jdbcDs);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-SQ-DE"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenAnswer(inv -> {
            JDBCQuery q = inv.getArgument(0);
            q.setLastQueryError(new Exception("Column \"PRODUCT_ID\" not found in ORDERS"));
            return new ColumnSelection();
         });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, queryManagerService);

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", null);

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> stubUniformSqlConstruction(m, UniformSQL.PARSE_SUCCESS)))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.addSqlQuery("TOK-SQ-DE", body, agent));
         assertTrue(ex.getMessage().contains("syntactically valid but could not be resolved"),
                    "should surface the driver-error variant, got: " + ex.getMessage());
         assertTrue(ex.getMessage().contains("Column \"PRODUCT_ID\" not found in ORDERS"),
                    "should include the captured driver error text, got: " + ex.getMessage());
      }
   }

   /**
    * WSQ-006: if the guard fires but neither {@code parseResult} nor {@code lastQueryError}
    * explain why (the "driver call succeeded but returned nothing" case both refute rounds
    * identified), the original generic message must still be the fallback.
    */
   @Test
   void addSqlQueryFallsBackToGenericMessageWhenNeitherSignalExplainsIt() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(xrepository.getDataSource("MyDatasource")).thenReturn(jdbcDs);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-SQ-GEN"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      // lastQueryError is left unset (defaults to null on a freshly-constructed JDBCQuery) --
      // this is the "driver call succeeded but returned nothing" outcome, not exercised above.
      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(new ColumnSelection());

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, queryManagerService);

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", null);

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> stubUniformSqlConstruction(m, UniformSQL.PARSE_SUCCESS)))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.addSqlQuery("TOK-SQ-GEN", body, agent));
         assertTrue(
            ex.getMessage().contains("SQL could not be parsed or no columns detected"),
            "should fall back to the original generic message, got: " + ex.getMessage());
      }
   }

   /**
    * Bug #79097: the variable scans in {@code addSqlQuery} run after the table is added, so a
    * failure there used to leave the table in the worksheet (and a retry added a duplicate).
    * The post-add failure must roll the new table back and still surface the error.
    */
   @Test
   void addSqlQueryRemovesTheTableWhenTheVariableScanFails() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(xrepository.getDataSource("MyDatasource")).thenReturn(jdbcDs);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-SQ-ROLLBACK"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "a")));
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(columns);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, queryManagerService);

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", null);

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
             (m, ctx) -> stubUniformSqlConstruction(m, UniformSQL.PARSE_SUCCESS));
          MockedStatic<WorksheetEventUtil> eventUtil =
             mockStatic(WorksheetEventUtil.class, CALLS_REAL_METHODS))
      {
         eventUtil.when(() -> WorksheetEventUtil.refreshVariables(eq(rws), any(), anyBoolean()))
            .thenThrow(new IllegalStateException("variable scan failed"));

         for(int i = 0; i < 2; i++) {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
               () -> ctrl.addSqlQuery("TOK-SQ-ROLLBACK", body, agent));
            assertEquals("variable scan failed", ex.getMessage());
            assertEquals(0, ws.getAssemblies().length,
                         "a failed add must not leave a table behind, retries included");
         }
      }
   }

   // ---------------------------------------------------------------------------
   // Whole-branch review finding 1 (CRITICAL) -- a pane-scoped code is not a
   // whole-sheet write handle
   // ---------------------------------------------------------------------------

   /**
    * A pane-scoped session names ONE script location. This controller's {@code edit} op
    * dispatcher can delete a table, add a join, or rewrite any column's expression, and it
    * resolves the same session token without ever reading {@code editorContext} -- so before this
    * guard, a code minted from one chart's Script tab was, in practice, an unscoped write handle
    * on the whole worksheet.
    *
    * <p>Refusal must land BEFORE the edit service is touched: {@code verifyNoInteractions} is the
    * load-bearing half of this test, since a refusal thrown after a partial mutation would leave
    * the worksheet changed and still report an error.
    */
   @Test
   void editRefusesAPaneScopedSessionBeforeReachingTheEditService() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(eq("tok-pane"), anyString())).thenReturn(paneScopedSession("tok-pane"));

      WorksheetEditService edit = mock(WorksheetEditService.class);
      WorksheetAgentController controller = controller(
         featureOn(), mock(SheetJoinService.class), sessions, mock(WorksheetReadService.class),
         edit, mock(WorksheetService.class));

      PairingException thrown = assertThrows(PairingException.class,
         () -> controller.edit("tok-pane", deleteTableRequest("Query1"), agent()));

      assertTrue(thrown.getMessage().contains("scoped to one script location"),
                 thrown.getMessage());
      // The one action that fixes it. A refusal an agent cannot act on just becomes a retry loop.
      assertTrue(thrown.getMessage().contains("Connect to Claude"), thrown.getMessage());
      verifyNoInteractions(edit);
   }

   /**
    * The regression that would be worse than the bug: whole-sheet toolbar pairing is in use by
    * other agents today, and it must be completely unaffected. A session with no
    * {@code editorContext} passes the same guard untouched and reaches the edit service.
    */
   @Test
   void aWholeSheetToolbarSessionIsUnaffectedByTheGuard() throws Exception {
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(eq("tok-toolbar"), anyString())).thenReturn(session("tok-toolbar"));

      WorksheetEditService edit = mock(WorksheetEditService.class);
      WorksheetAgentController controller = controller(
         featureOn(), mock(SheetJoinService.class), sessions, mock(WorksheetReadService.class),
         edit, mock(WorksheetService.class));

      controller.edit("tok-toolbar", deleteTableRequest("Query1"), agent());

      verify(edit).apply(eq("tok-toolbar"), any(Principal.class), any());
   }

   /**
    * An expired/foreign token must still report what is actually wrong with it. Answering "you
    * are pane-scoped" to a token that never resolved would send the user to re-pair from the
    * toolbar when the real problem is that this session is gone.
    */
   @Test
   void anUnresolvableTokenStillReportsExpiryNotScope() throws Exception {
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(anyString(), anyString())).thenReturn(null);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      WorksheetAgentController controller = controller(
         featureOn(), mock(SheetJoinService.class), sessions, mock(WorksheetReadService.class),
         edit, mock(WorksheetService.class));

      // The guard passes it through untouched; the edit service's own resolution is what
      // decides, and reports "invalid or expired session" rather than a scope problem.
      assertDoesNotThrow(() -> controller.edit("gone", deleteTableRequest("Query1"), agent()));
      verify(edit).apply(eq("gone"), any(Principal.class), any());
   }

   /** The agent principal these guard tests drive the controller with. */
   private static Principal agent() {
      return TestPrincipals.user("alice", "host-org");
   }

   private static JoinSession paneScopedSession(String token) {
      return new JoinSession(token, "Worksheet/ws-1", "alice~;~host-org",
                             SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                             JoinSession.ConnectionMode.PAIRED, "sock-1", "alice",
                             new EditorContext("worksheetExpression", "Query1", "Margin", null));
   }

   // ---------------------------------------------------------------------------
   // save -- PVA-003: Save-As silently orphans a connected viewsheet's base pointer
   // ---------------------------------------------------------------------------

   /**
    * Regression for Bug PVA-003: {@code save(name:...)} on an already-saved worksheet used to
    * never look at any connected {@link RuntimeViewsheet}, so a viewsheet whose
    * {@code Viewsheet.getBaseEntry()} pointed at the pre-Save-As entry kept pointing at it
    * indefinitely, with no warning. The fix does not repoint the viewsheet (no such tool exists
    * yet) but must surface a warning naming the affected, still-open viewsheet.
    */
   @Test
   void saveAsWarnsWhenAConnectedViewsheetStillPointsAtTheOldEntry() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(2);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE1"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-1"));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(oldEntry);
      RuntimeViewsheet connectedRvs = mock(RuntimeViewsheet.class);
      when(connectedRvs.getViewsheet()).thenReturn(vs);
      when(connectedRvs.getID()).thenReturn("rt-vs-1");
      when(connectedRvs.getUser()).thenReturn(agent);

      WorksheetService ws = mock(WorksheetService.class);
      // connectedViewsheets() now fetches every live runtime sheet (getRuntimeSheets(null)) and
      // filters manually via PairingUtil.sameLogicalUser(rvs.getUser(), user) -- see VBM-005 fix
      // (docs/teams/2026-09-06-bug-vbm-005/02-root-cause.md) -- so this stub must match that call,
      // and connectedRvs.getUser() above must resolve to the same logical user as agent.
      when(ws.getRuntimeSheets(any()))
         .thenReturn(new RuntimeSheet[]{ connectedRvs });

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result = ctrl.save("TOK-SAVE1",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      Object warning = result.get("warning");
      assertNotNull(warning, "a connected viewsheet still pointing at the old entry must be " +
         "surfaced as a warning");
      assertTrue(warning.toString().contains("rt-vs-1"),
         "the warning should name the affected viewsheet runtime: " + warning);
   }

   /** No connected viewsheet, no warning -- the common case stays a plain success. */
   @Test
   void saveAsWithNoConnectedViewsheetHasNoWarning() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(1);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE2"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-2"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result = ctrl.save("TOK-SAVE2",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      assertNull(result.get("warning"));
   }

   /** A first save out of TEMPORARY_SCOPE is not a Save-As -- no connected-viewsheet lookup at all. */
   @Test
   void firstSaveOutOfTemporaryScopeDoesNotCheckForConnectedViewsheets() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry tempEntry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
         AssetEntry.Type.WORKSHEET, "__TEMPORARY__/ws-1", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(tempEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(0);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE3"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-3"));

      WorksheetService ws = mock(WorksheetService.class);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result = ctrl.save("TOK-SAVE3",
         new WorksheetAgentController.SaveRequest("agent_ws_1", null), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      assertNull(result.get("warning"));
      verify(ws, never()).getRuntimeSheets(any());
   }

   /**
    * Regression for Bug VBM-005: a first save out of TEMPORARY_SCOPE whose target name COLLIDES
    * with (and, via {@code confirmed:true}, overwrites) an asset that is already a connected
    * viewsheet's base entry must still reset that viewsheet's runtime -- the sibling test above
    * ({@code firstSaveOutOfTemporaryScopeDoesNotCheckForConnectedViewsheets}) only covers a
    * genuinely new, non-colliding name, where skipping the scan is correct and intentional.
    *
    * <p>Before this fix, {@code save()}'s {@code if(!isSaveAs && !wasTemporary)} gate skipped the
    * {@code connectedViewsheets(entry, user).resetRuntime()} scan unconditionally whenever
    * {@code wasTemporary} was true, regardless of whether the target name collided with an
    * already-connected viewsheet's base worksheet -- leaving that viewsheet silently serving stale
    * data forever, with {@code ok:true} on every step and no warning anywhere. The fix makes the
    * {@code wasTemporary} half of the gate collision-aware via the already-computed
    * {@code isDuplicatedEntry} check.
    */
   @Test
   void firstSaveOutOfTemporaryScopeResetsConnectedViewsheetOnCollision() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry tempEntry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
         AssetEntry.Type.WORKSHEET, "__TEMPORARY__/ws-2", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(tempEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(0);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE3-COLLIDE"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-3-collide"));

      // The target entry this save writes to -- already the base entry of a connected viewsheet.
      AssetEntry targetEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(targetEntry);
      RuntimeViewsheet connectedRvs = mock(RuntimeViewsheet.class);
      when(connectedRvs.getViewsheet()).thenReturn(vs);
      when(connectedRvs.getUser()).thenReturn(agent);

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(any())).thenReturn(new RuntimeSheet[]{ connectedRvs });
      when(ws.isDuplicatedEntry(any(), any())).thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result;

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString())).thenReturn(List.of());

         result = ctrl.save("TOK-SAVE3-COLLIDE",
            new WorksheetAgentController.SaveRequest("Orders WS", null, true), agent);
      }

      assertEquals(Boolean.TRUE, result.get("ok"));
      verify(connectedRvs).resetRuntime();
   }

   // ---------------------------------------------------------------------------
   // save -- L2-Group10: duplicate-name/overwrite confirmation, control-char sanitization
   // ---------------------------------------------------------------------------

   @Test
   void saveRejectsDuplicateNameWithoutConfirmation() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(1);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-DUP1"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-dup1"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);
      when(ws.isDuplicatedEntry(any(), any())).thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      // L2-Group10: SaveWorksheetDialogService.process0()'s Save-As dialog refuses (pending
      // confirmation) when isDuplicatedEntry() reports the target already exists -- this agent
      // path previously called setWorksheet unconditionally, silently overwriting whatever
      // "Orders WS Copy" already held.
      PairingException ex = assertThrows(PairingException.class, () -> ctrl.save("TOK-SAVE-DUP1",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null), agent));
      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
      verify(ws, never()).setWorksheet(any(), any(), any(), anyBoolean(), anyBoolean());
   }

   @Test
   void saveOverwritesDuplicateNameWhenConfirmed() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(1);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-DUP2"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-dup2"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);
      when(ws.isDuplicatedEntry(any(), any())).thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result = ctrl.save("TOK-SAVE-DUP2",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null, true), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      verify(ws).setWorksheet(any(), any(), any(), anyBoolean(), anyBoolean());
   }

   @Test
   void saveSanitizesControlCharactersInName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry tempEntry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
         AssetEntry.Type.WORKSHEET, "__TEMPORARY__/ws-1", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(tempEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(0);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-CTRL"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-ctrl"));

      WorksheetService ws = mock(WorksheetService.class);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      // L2-Group10: SaveWorksheetDialogService.process()/process0() both strip control
      // characters via SUtil.removeControlChars(name) before using it -- this agent path
      // previously only trimmed surrounding whitespace, letting an embedded control character
      // (here, a literal tab) straight into the saved asset's name.
      ctrl.save("TOK-SAVE-CTRL",
         new WorksheetAgentController.SaveRequest("L2\tCtrlTab", null), agent);

      ArgumentCaptor<AssetEntry> entryCaptor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(ws).setWorksheet(any(), entryCaptor.capture(), any(), anyBoolean(), anyBoolean());
      assertEquals("L2CtrlTab", entryCaptor.getValue().getName(),
         "the control character must be stripped from the saved name");
   }

   /**
    * Regression: {@code AssetEntry.toIdentifier()} joins its fields with a literal, unescaped
    * '^', and {@code createAssetEntry(identifier)} recovers {@code orgID} using the FIRST '^'
    * after the user field rather than the last -- so a caret embedded in {@code name} corrupts
    * the orgID recovered on any later round-trip. Refused before any {@link AssetEntry} is built
    * or the repository is touched, mirroring the identical fix in
    * {@code ViewsheetAssemblyAgentController.attachBaseWorksheet}.
    */
   @Test
   void saveRefusesANameContainingACaret() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry tempEntry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
         AssetEntry.Type.WORKSHEET, "__TEMPORARY__/ws-1", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(tempEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-CARET"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-caret"));

      WorksheetService ws = mock(WorksheetService.class);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.save("TOK-SAVE-CARET",
         new WorksheetAgentController.SaveRequest("Sample^WS", null), agent));
      assertTrue(ex.getMessage().contains("name"));

      verifyNoInteractions(ws);
   }

   // ---------------------------------------------------------------------------
   // save -- PVA-002: a plain re-save must proactively invalidate a connected viewsheet's
   // stale bindable-fields cache instead of relying on the lazy timestamp check
   //
   // NOTE: PVA-009 was originally paired with PVA-002 under this same mechanism, but that
   // pairing was invalidated on recheck -- PVA-009's actual failing call
   // (set_table_source/set_chart_source) goes through VSBindingService.createSourceTables, a
   // different, cache-free code path this fix does not touch. See
   // docs/teams/2026-08-29-bugs-plugin-composer-corpus/cluster-C/03-escalate-pva008.md.
   // PVA-009 is excluded from this fix pass.
   // ---------------------------------------------------------------------------

   /**
    * Regression for Bug PVA-002: {@code list_bindable_fields} reads a connected viewsheet's base
    * worksheet through a lazy {@code Worksheet.getLastModified()} comparison
    * ({@code BindableFieldsService.list()} / {@code CubeTreeModelBuilder}) that only re-checks on
    * the next read -- a plain re-save used to never proactively tell a connected viewsheet its
    * base worksheet changed. A plain save-in-place (no name) must now call
    * {@code resetRuntime()} on every connected viewsheet synchronously.
    */
   @Test
   void plainSaveInPlaceResetsConnectedViewsheetRuntimes() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(5);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE4"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-4"));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(entry);
      RuntimeViewsheet connectedRvs = mock(RuntimeViewsheet.class);
      when(connectedRvs.getViewsheet()).thenReturn(vs);
      when(connectedRvs.getUser()).thenReturn(agent);

      WorksheetService ws = mock(WorksheetService.class);
      // connectedViewsheets() now fetches every live runtime sheet (getRuntimeSheets(null)) and
      // filters manually via PairingUtil.sameLogicalUser(rvs.getUser(), user) -- see VBM-005 fix
      // (docs/teams/2026-09-06-bug-vbm-005/02-root-cause.md) -- so this stub must match that call,
      // and connectedRvs.getUser() above must resolve to the same logical user as agent.
      when(ws.getRuntimeSheets(any())).thenReturn(new RuntimeSheet[]{ connectedRvs });

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      // Unrelated to this test's own concern (PVA-002's reset), but every plain in-place save now
      // also runs PVA-011's rename check (DependencyTransformer.createRenameInfo), which always
      // calls DependencyTool.getDependencies(id) once even when there is nothing to rename --
      // stub it so this test doesn't reach the real Spring-backed dependency storage service.
      Map<String, Object> result;

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString())).thenReturn(List.of());

         result = ctrl.save("TOK-SAVE4", new WorksheetAgentController.SaveRequest(null, null), agent);
      }

      assertEquals(Boolean.TRUE, result.get("ok"));
      verify(connectedRvs).resetRuntime();
   }

   /**
    * Regression for VBM-005 -- currently RED (fails), on purpose, until the P4 fix lands.
    *
    * <p>{@code connectedViewsheets()} finds its connected viewsheets via
    * {@code worksheetService.getRuntimeSheets(user)}, which (per
    * {@code WorksheetEngine.getRuntimeSheets}) filters with
    * {@code user == null || rvs.matches(user)} -- i.e. {@code RuntimeSheet.matches()} ->
    * {@code Tool.equals(this.user, user)} -> plain {@code SRPrincipal.equals()}. That comparison
    * requires exact {@code ClientInfo} (ip+session) and {@code secureId} equality, not merely the
    * same logical user.
    *
    * <p>An agent's JWT-derived {@code SRPrincipal} is minted from whichever browser
    * {@code HttpSession} happened to service {@code /sso/authorize} at {@code login_start}/
    * {@code login_complete} time (see {@code SSOTokenController.authorize()} /
    * {@code SSOTokenService.createSSOToken()}), and is then reused, frozen, for every later
    * request in that agent session. A browser-paired {@code RuntimeViewsheet}'s owning principal
    * is whatever {@code HttpSession} was live when the browser opened/paired that specific
    * viewsheet -- a separate event that can be backed by a different {@code HttpSession} for the
    * exact same human (session renewal, a different browser/incognito window --
    * {@code login_start}'s own tool description explicitly offers this as an option -- or simply a
    * long-lived cached token outliving the browser session that originally minted it; see
    * docs/teams/2026-09-06-bug-vbm-005/02-root-cause.md). When that happens, the two
    * {@code SRPrincipal}s are the same logical user but different objects, and
    * {@code RuntimeSheet.matches()} returns {@code false} -- so {@code connectedViewsheets()}
    * silently drops the viewsheet, and the PVA-002 {@code resetRuntime()} call this test's
    * sibling ({@code plainSaveInPlaceResetsConnectedViewsheetRuntimes}) covers never fires for it.
    * {@code PairingUtil.sameLogicalUser()} already exists and is wired into every other
    * runtime-access path in this same package (see {@code SheetRuntimeAccess
    * .grantOwnershipBypass()}'s javadoc, which documents this exact failure mode as an
    * already-observed production incident) -- {@code connectedViewsheets()} is the one path that
    * was never given the same treatment.
    *
    * <p>Unlike {@code plainSaveInPlaceResetsConnectedViewsheetRuntimes} (which stubs
    * {@code getRuntimeSheets} to hand back the connected viewsheet for the literal same
    * {@code Principal} object, and so cannot detect an identity mismatch either way), this test
    * uses two distinct, realistically-shaped real {@code SRPrincipal}s (real {@code ClientInfo},
    * real {@code secureId}) and a {@code WorksheetService} stub that replicates
    * {@code WorksheetEngine.getRuntimeSheets(user)}'s own real filtering predicate
    * (rather than returning an already-filtered canned list) against a
    * {@code mock(RuntimeViewsheet.class, CALLS_REAL_METHODS)} fixture whose real, un-stubbed
    * {@code matches()} runs for real.
    *
    * <p>This test asserts the CORRECT/desired behavior -- the same-logical-user viewsheet's
    * runtime IS reset -- exactly like {@code plainSaveInPlaceResetsConnectedViewsheetRuntimes}
    * does for the continuity case. That final {@code verify(connectedRvs).resetRuntime()} is
    * expected to FAIL today: {@code connectedViewsheets()} has no {@code PairingUtil
    * .sameLogicalUser()} fallback yet, so the real {@code getRuntimeSheets(agentPrincipal)}
    * filtering predicate above silently returns an empty array for this fixture, and
    * {@code resetRuntime()} is never invoked. Once P4 gives {@code connectedViewsheets()} that
    * fallback (per {@code 02-root-cause.md}'s fix direction), this assertion should pass without
    * any other change to this test.
    */
   @Test
   void plainSaveInPlaceResetsConnectedViewsheetAcrossIdentitySessionBoundary() throws Exception {
      // "browser" principal: owns the paired viewsheet, as if established when a human's browser
      // opened/paired the Composer session that owns it.
      SRPrincipal browserPrincipal = realSessionPrincipal(
         "alice", "host-org", "10.0.0.5", "browser-http-session-A", 111L);

      // "agent" principal: same logical user (alice/host-org), but reconstructed by
      // WizServiceAuthenticationFilter from a JWT minted at a DIFFERENT HttpSession's
      // /sso/authorize -- e.g. an earlier login_start/login_complete bootstrap, a session
      // renewal, or a different browser/incognito window used for the authorize click.
      SRPrincipal agentPrincipal = realSessionPrincipal(
         "alice", "host-org", "10.0.0.9", "agent-jwt-http-session-B", 222L);

      // Fixture sanity check: these must be genuinely different sessions for this test to prove
      // anything -- if this ever fails, the fixture itself is broken, not the code under test.
      assertNotEquals(browserPrincipal, agentPrincipal,
         "fixture bug: browser/agent principals must be different sessions of the same logical "
            + "user for this test to mean anything");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(5);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-IDBOUNDARY"), eq(agentPrincipal)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-idboundary"));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(entry);

      // Real fixture: an un-stubbed matches() call runs RuntimeSheet's actual
      // Tool.equals(this.user, user) body, against the real "browser" SRPrincipal reflectively
      // installed as its owner -- not a canned/stubbed return value.
      RuntimeViewsheet connectedRvs = mock(RuntimeViewsheet.class, CALLS_REAL_METHODS);
      when(connectedRvs.getViewsheet()).thenReturn(vs);
      setRuntimeSheetOwner(connectedRvs, browserPrincipal);

      // Confirm the fixture's premise by executing the real chain directly, before routing it
      // through the controller -- if either of these ever flips, the fixture (not the production
      // code) is what broke.
      assertTrue(connectedRvs.matches(browserPrincipal),
         "fixture bug: the browser-owned runtime must match its own owning principal");
      assertFalse(connectedRvs.matches(agentPrincipal),
         "fixture bug: this mismatch is exactly what this test exists to demonstrate -- if "
            + "SRPrincipal.equals() ever starts returning true here, either the fix already "
            + "landed (re-point this test at PairingUtil.sameLogicalUser() instead) or something "
            + "changed underneath it that needs investigating before trusting the rest of this "
            + "test");

      WorksheetService ws = mock(WorksheetService.class);
      // Replicates WorksheetEngine.getRuntimeSheets(user)'s own real filtering predicate
      // ("user == null || rvs.matches(user)") against the fixture above, rather than a
      // pre-filtered canned list -- so the real RuntimeSheet.matches()/SRPrincipal.equals() chain,
      // not the test author, determines what comes back.
      List<RuntimeSheet> allLiveRuntimeSheets = List.of(connectedRvs);
      when(ws.getRuntimeSheets(any())).thenAnswer(invocation -> {
         Principal requestUser = invocation.getArgument(0);
         return allLiveRuntimeSheets.stream()
            .filter(sheet -> requestUser == null || sheet.matches(requestUser))
            .toArray(RuntimeSheet[]::new);
      });

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result;

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString())).thenReturn(List.of());

         result = ctrl.save("TOK-SAVE-IDBOUNDARY",
            new WorksheetAgentController.SaveRequest(null, null), agentPrincipal);
      }

      assertEquals(Boolean.TRUE, result.get("ok"));

      // THE BUG (VBM-005) -- this is the assertion expected to be RED today: the browser-owned
      // viewsheet's runtime should be reset (same logical user, PVA-002's whole point), but
      // WorksheetService.getRuntimeSheets(agentPrincipal) silently drops it, because
      // connectedViewsheets() has no logical-identity fallback the way SheetRuntimeAccess
      // .grantOwnershipBypass()'s sibling paths do. Once connectedViewsheets() is fixed to also
      // match via PairingUtil.sameLogicalUser(), this passes with no other change to this test.
      verify(connectedRvs).resetRuntime();
   }

   /** A Save-As must not reset anything -- see the PVA-003 warning test for that path instead. */
   @Test
   void saveAsDoesNotResetAnyConnectedViewsheetRuntime() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(5);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE5"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-5"));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(oldEntry);
      RuntimeViewsheet connectedRvs = mock(RuntimeViewsheet.class);
      when(connectedRvs.getViewsheet()).thenReturn(vs);
      when(connectedRvs.getID()).thenReturn("rt-vs-5");

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[]{ connectedRvs });

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      ctrl.save("TOK-SAVE5",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null), agent);

      verify(connectedRvs, never()).resetRuntime();
   }

   /**
    * Regression for Bug #76482 (VBM-005 residual): {@code save(name:<the worksheet's own path>,
    * confirmed:true)} builds an entry {@code equals()} to the session's own entry, yet used to be
    * classified as a Save-As purely because a name was given -- so the connected viewsheet was
    * never reset (it kept serving pre-save data) and the response carried a false PVA-003
    * "created a new copy" warning naming the same path twice. It must behave as a plain save.
    */
   @Test
   void sameEntryNamedConfirmedSaveIsAnInPlaceSave() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null, "host-org");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(5);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-SELF"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-self"));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(oldEntry);
      RuntimeViewsheet connectedRvs = mock(RuntimeViewsheet.class);
      when(connectedRvs.getViewsheet()).thenReturn(vs);
      when(connectedRvs.getID()).thenReturn("rt-vs-self");
      when(connectedRvs.getUser()).thenReturn(agent);

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(any())).thenReturn(new RuntimeSheet[]{ connectedRvs });
      // the session's own asset exists, so the overwrite guard needs confirmed:true
      when(ws.isDuplicatedEntry(any(), any())).thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result;

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString())).thenReturn(List.of());

         result = ctrl.save("TOK-SAVE-SELF",
            new WorksheetAgentController.SaveRequest("Orders WS", null, true), agent);
      }

      assertEquals(Boolean.TRUE, result.get("ok"));
      assertNull(result.get("warning"),
         "a save onto the session's own entry creates no copy -- no Save-As warning");
      verify(connectedRvs).resetRuntime();
      verify(ws).setWorksheet(any(), same(oldEntry), any(), anyBoolean(), anyBoolean());
   }

   /**
    * Negative control for {@link #sameEntryNamedConfirmedSaveIsAnInPlaceSave}: a confirmed save
    * onto a DIFFERENT existing entry is still a genuine Save-As -- PVA-003 warning, no reset.
    */
   @Test
   void confirmedSaveAsOntoADifferentEntryStillWarnsAndDoesNotReset() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null, "host-org");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(5);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-OTHER"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-other"));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(oldEntry);
      RuntimeViewsheet connectedRvs = mock(RuntimeViewsheet.class);
      when(connectedRvs.getViewsheet()).thenReturn(vs);
      when(connectedRvs.getID()).thenReturn("rt-vs-other");
      when(connectedRvs.getUser()).thenReturn(agent);

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(any())).thenReturn(new RuntimeSheet[]{ connectedRvs });
      when(ws.isDuplicatedEntry(any(), any())).thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result = ctrl.save("TOK-SAVE-OTHER",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null, true), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      Object warning = result.get("warning");
      assertNotNull(warning, "a genuine Save-As must keep the PVA-003 warning");
      assertTrue(warning.toString().contains("overwritten") &&
                 warning.toString().contains("rt-vs-other"), warning.toString());
      verify(connectedRvs, never()).resetRuntime();
   }


   /**
    * Bug #78593: a confirmed Save-As onto a different EXISTING worksheet overwrites that target's
    * content, so the target's connected viewsheets are reset (and reported); a viewsheet on the
    * OLD entry is not reset and is warned, with wording that does not claim a new copy.
    */
   @Test
   void confirmedSaveAsOverwriteResetsTargetViewsheetsAndWarnsOldEntryOnes() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null, "host-org");
      AssetEntry targetEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Target WS", null, "host-org");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(5);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-OVR"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-ovr"));

      RuntimeViewsheet onTarget = connectedVs(targetEntry, "rt-vs-target", agent);
      RuntimeViewsheet onOld = connectedVs(oldEntry, "rt-vs-old", agent);

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(any())).thenReturn(new RuntimeSheet[]{ onTarget, onOld });
      when(ws.isDuplicatedEntry(any(), any())).thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result = ctrl.save("TOK-SAVE-OVR",
         new WorksheetAgentController.SaveRequest("Target WS", null, true), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      verify(onTarget).resetRuntime();
      verify(onOld, never()).resetRuntime();
      assertEquals(List.of("rt-vs-target"), result.get("resetViewsheets"));
      String warnings = String.valueOf(result.get("warnings"));
      assertTrue(warnings.contains("rt-vs-target") && warnings.contains("not rewritten"),
                 warnings);
      assertFalse(warnings.contains("rt-vs-old"), warnings);
      String warning = String.valueOf(result.get("warning"));
      assertTrue(warning.contains("rt-vs-old") && warning.contains("overwritten"), warning);
      assertFalse(warning.contains("new copy") || warning.contains("rt-vs-target"), warning);
      assertNull(result.get("dependenciesUpdated"), "the rename cascade stays skipped");
   }

   /** Bug #78593: no connected viewsheet on the overwritten target -> no reset report at all. */
   @Test
   void confirmedSaveAsOverwriteWithNoTargetViewsheetsReportsNothingReset() throws Exception {
      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null, "host-org");
      SelfSaveFixture f = selfSaveFixture(oldEntry);

      Map<String, Object> result = f.save(new WorksheetAgentController.SaveRequest(
         "Other WS", null, true));

      assertEquals(Boolean.TRUE, result.get("ok"));
      assertNull(result.get("resetViewsheets"));
      assertNull(result.get("warnings"));
      verify(f.connectedRvs, never()).resetRuntime();
   }

   /** Bug #78593: "/", leading/trailing "/" and "//" are refused by field name; "A/B" is legal. */
   @Test
   void saveRefusesEmptyPathSegmentsInNameButAllowsNestedFolder() throws Exception {
      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null, "host-org");
      SelfSaveFixture f = selfSaveFixture(oldEntry);

      for(String bad : new String[]{ "/W", "W/", "A//B", "/" }) {
         PairingException e = assertThrows(PairingException.class,
            () -> f.save(new WorksheetAgentController.SaveRequest(bad, null, true)), bad);
         assertTrue(e.getMessage().contains("'name'") && e.getMessage().contains("'/'"),
                    bad + " -> " + e.getMessage());
      }

      verify(f.ws, never()).setWorksheet(any(), any(), any(), anyBoolean(), anyBoolean());
      assertEquals(Boolean.TRUE,
         f.save(new WorksheetAgentController.SaveRequest("A/B", null, true)).get("ok"));
   }

   /**
    * Bug #78593 (refuter r1): a Save-As must clear the session's oldName markers, otherwise the next
    * in-place save cascades pre-Save-As renames onto the new entry's dependents -- while an
    * ordinary rename made after the Save-As still cascades exactly once.
    */
   @Test
   void saveAsThenRenameThenInPlaceSaveCascadesOnlyPostSaveAsRenames() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null, "host-org");

      Worksheet worksheet = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(worksheet, "T", "DISCOUNT");
      worksheet.addAssembly(t);

      // a rename made BEFORE the Save-As: display name != oldName
      ColumnRef discount = (ColumnRef) t.getColumnSelection(false).getAttribute("DISCOUNT");
      discount.setOldName("DISCOUNT");
      discount.setAlias("DISCOUNT_PCT");

      AssetEntry[] current = { oldEntry };
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenAnswer(inv -> current[0]);
      doAnswer(inv -> { current[0] = inv.getArgument(0); return null; })
         .when(rws).setEntry(any(AssetEntry.class));
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(rws.getCurrent()).thenReturn(3);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-SEQ"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-seq"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(any())).thenReturn(new RuntimeSheet[0]);

      RenameTransformHandler renameTransformHandler = mock(RenameTransformHandler.class);
      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws,
         renameTransformHandler);

      AssetEntry dependent = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "Orders VS", null);

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString()))
            .thenReturn(List.of(dependent));

         ctrl.save("TOK-SAVE-SEQ",
            new WorksheetAgentController.SaveRequest("Orders WS Copy", null), agent);
         assertEquals("DISCOUNT_PCT", discount.getOldName(),
            "the Save-As must reset the oldName marker to the current name");

         // in-place save with no new rename: the pre-Save-As rename must not cascade
         ctrl.save("TOK-SAVE-SEQ", new WorksheetAgentController.SaveRequest(null, null), agent);
         verifyNoInteractions(renameTransformHandler);

         // a genuine post-Save-As rename still cascades, once
         discount.setAlias("DISCOUNT_2");
         Map<String, Object> result = ctrl.save("TOK-SAVE-SEQ",
            new WorksheetAgentController.SaveRequest(null, null), agent);
         assertEquals(Boolean.TRUE, result.get("dependenciesUpdated"));
         verify(renameTransformHandler, times(1))
            .addTransformTask(any(RenameDependencyInfo.class), eq(true));
         assertEquals("DISCOUNT_2", discount.getOldName());
      }
   }

   private static RuntimeViewsheet connectedVs(AssetEntry base, String id, Principal user) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(base);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn(id);
      when(rvs.getUser()).thenReturn(user);
      return rvs;
   }

   /**
    * Bug #76482: the same path is not the same entry. A USER-scope worksheet saved under its own
    * path with scope omitted (default GLOBAL) is a genuine Save-As to a shared GLOBAL copy -- it
    * must not be narrowed to a path compare and overwrite the private asset in place.
    */
   @Test
   void samePathDifferentScopeIsStillASaveAs() throws Exception {
      AssetEntry oldEntry = new AssetEntry(AssetRepository.USER_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", new IdentityID("alice", "host-org"), "host-org");
      SelfSaveFixture f = selfSaveFixture(oldEntry);

      Map<String, Object> result = f.save(new WorksheetAgentController.SaveRequest(
         "Orders WS", null, true));

      assertNotNull(result.get("warning"), "a USER->GLOBAL copy is a genuine Save-As");
      verify(f.connectedRvs, never()).resetRuntime();
      verify(f.ws).setWorksheet(any(),
         argThat(e -> e != oldEntry && e.getScope() == AssetRepository.GLOBAL_SCOPE),
         any(), anyBoolean(), anyBoolean());
   }

   /** Bug #76482: the owner saving a USER-scope worksheet under its own path and scope is in place. */
   @Test
   void samePathSameUserScopeIsAnInPlaceSave() throws Exception {
      AssetEntry oldEntry = new AssetEntry(AssetRepository.USER_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", new IdentityID("alice", "host-org"), "host-org");
      SelfSaveFixture f = selfSaveFixture(oldEntry);

      Map<String, Object> result = f.save(new WorksheetAgentController.SaveRequest(
         "Orders WS", "user", true));

      assertNull(result.get("warning"));
      verify(f.connectedRvs).resetRuntime();
      verify(f.ws).setWorksheet(any(), same(oldEntry), any(), anyBoolean(), anyBoolean());
   }

   /** Bug #76482: the same path in another org is a different entry -- a Save-As, not in place. */
   @Test
   void samePathDifferentOrgIsStillASaveAs() throws Exception {
      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null, "other-org");
      SelfSaveFixture f = selfSaveFixture(oldEntry);

      Map<String, Object> result = f.save(new WorksheetAgentController.SaveRequest(
         "Orders WS", null, true));

      assertNotNull(result.get("warning"));
      verify(f.connectedRvs, never()).resetRuntime();
      verify(f.ws).setWorksheet(any(),
         argThat(e -> e != oldEntry && "host-org".equals(e.getOrgID())),
         any(), anyBoolean(), anyBoolean());
   }

   /**
    * Bug #76482: an unconfirmed save under the worksheet's own location is still refused, but must
    * point at the in-place save instead of asking to "overwrite" the asset the caller has open.
    */
   @Test
   void unconfirmedSameEntrySaveRefusalPointsAtThePlainSave() throws Exception {
      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null, "host-org");
      SelfSaveFixture f = selfSaveFixture(oldEntry);

      PairingException self = assertThrows(PairingException.class,
         () -> f.save(new WorksheetAgentController.SaveRequest("Orders WS", null)));
      assertTrue(self.getMessage().contains("own location") &&
                 self.getMessage().contains("save_worksheet()"), self.getMessage());
      assertFalse(self.getMessage().contains("already exists"), self.getMessage());

      PairingException other = assertThrows(PairingException.class,
         () -> f.save(new WorksheetAgentController.SaveRequest("Orders WS Copy", null)));
      assertTrue(other.getMessage().contains("already exists"), other.getMessage());
      assertFalse(other.getMessage().contains("save_worksheet()"), other.getMessage());
      verify(f.ws, never()).setWorksheet(any(), any(), any(), anyBoolean(), anyBoolean());
   }

   /** A saved worksheet session for {@code alice@host-org} with one connected viewsheet on it. */
   private SelfSaveFixture selfSaveFixture(AssetEntry oldEntry) throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(5);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-FIX"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-fix"));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(oldEntry);
      RuntimeViewsheet connectedRvs = mock(RuntimeViewsheet.class);
      when(connectedRvs.getViewsheet()).thenReturn(vs);
      when(connectedRvs.getID()).thenReturn("rt-vs-fix");
      when(connectedRvs.getUser()).thenReturn(agent);

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(any())).thenReturn(new RuntimeSheet[]{ connectedRvs });
      when(ws.isDuplicatedEntry(any(), any())).thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);
      return new SelfSaveFixture(ctrl, agent, ws, connectedRvs);
   }

   private record SelfSaveFixture(WorksheetAgentController ctrl, Principal agent,
                                  WorksheetService ws, RuntimeViewsheet connectedRvs)
   {
      Map<String, Object> save(WorksheetAgentController.SaveRequest request) throws Exception {
         try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
            dependencyTool.when(() -> DependencyTool.getDependencies(anyString())).thenReturn(List.of());
            return ctrl.save("TOK-SAVE-FIX", request, agent);
         }
      }
   }

   // ---------------------------------------------------------------------------
   // save -- PVA-011: a plain re-save must cascade a column/table rename to already-saved
   // dependent assets (e.g. a bound viewsheet), the same "Dependencies Changed" update the
   // Composer UI's own Save button performs -- there is no user in an MCP session to answer
   // that dialog, so this must happen unconditionally.
   // ---------------------------------------------------------------------------

   /**
    * Regression for Bug PVA-011: {@code save()} used to call only
    * {@code worksheetService.setWorksheet(...)}, never {@code DependencyTransformer.createRenameInfo}
    * / {@code RenameTransformHandler.addTransformTask} -- so a column renamed via
    * {@code rename_column} and then saved through this endpoint left every already-saved dependent
    * viewsheet's binding silently pointing at the old name. A rename with a real persisted
    * dependent must now synchronously cascade (waitDone=true) and say so in the response.
    */
   @Test
   void plainSaveInPlaceCascadesRenamedColumnToDependentAsset() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      Worksheet worksheet = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(worksheet, "T", "DISCOUNT");
      worksheet.addAssembly(t);

      // Simulate rename_column("T", "DISCOUNT", "DISCOUNT_PCT"): the display name changes (here,
      // via alias) but oldName is left untouched -- exactly what DependencyTransformer.
      // addAssemblyRenameInfo diffs against to detect the rename.
      ColumnRef discount = (ColumnRef) t.getColumnSelection(false).getAttribute("DISCOUNT");
      discount.setOldName("DISCOUNT");
      discount.setAlias("DISCOUNT_PCT");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(rws.getCurrent()).thenReturn(3);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE6"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-6"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);

      RenameTransformHandler renameTransformHandler = mock(RenameTransformHandler.class);

      // DependencyTransformer.createRenameInfo(rws) resolves dependents through
      // DependencyTool.getDependencies(entryId), which is backed by a live cluster/storage
      // service; stub it to report the worksheet has one dependent asset (a viewsheet), the way a
      // real installation's persisted dependency graph would.
      AssetEntry dependentViewsheet = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "Orders VS", null);

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString()))
            .thenReturn(List.of(dependentViewsheet));

         WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
            mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws,
            renameTransformHandler);

         Map<String, Object> result = ctrl.save("TOK-SAVE6",
            new WorksheetAgentController.SaveRequest(null, null), agent);

         assertEquals(Boolean.TRUE, result.get("ok"));
         assertEquals(Boolean.TRUE, result.get("dependenciesUpdated"),
            "a save that found a renamed column with a real dependent must report the cascade");
         assertNotNull(result.get("message"));
         verify(renameTransformHandler)
            .addTransformTask(any(RenameDependencyInfo.class), eq(true));
      }
   }

   /**
    * An ordinary re-save with no rename must not invoke the cascade at all -- the common case
    * (the vast majority of saves) stays exactly as fast/simple as before this fix.
    */
   @Test
   void plainSaveInPlaceDoesNotCascadeWhenNoColumnWasRenamed() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      Worksheet worksheet = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(worksheet, "T", "DISCOUNT");
      worksheet.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(rws.getCurrent()).thenReturn(3);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE7"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-7"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);

      RenameTransformHandler renameTransformHandler = mock(RenameTransformHandler.class);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws,
         renameTransformHandler);

      Map<String, Object> result;

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString())).thenReturn(List.of());

         result = ctrl.save("TOK-SAVE7", new WorksheetAgentController.SaveRequest(null, null), agent);
      }

      assertEquals(Boolean.TRUE, result.get("ok"));
      assertNull(result.get("dependenciesUpdated"));
      verifyNoInteractions(renameTransformHandler);
   }

   /** A Save-As must not cascade -- a brand-new entry has no pre-existing dependent to update. */
   @Test
   void saveAsDoesNotCascadeRenames() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      Worksheet worksheet = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(worksheet, "T", "DISCOUNT");
      worksheet.addAssembly(t);

      ColumnRef discount = (ColumnRef) t.getColumnSelection(false).getAttribute("DISCOUNT");
      discount.setOldName("DISCOUNT");
      discount.setAlias("DISCOUNT_PCT");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(rws.getCurrent()).thenReturn(4);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE8"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-8"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);

      RenameTransformHandler renameTransformHandler = mock(RenameTransformHandler.class);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws,
         renameTransformHandler);

      Map<String, Object> result = ctrl.save("TOK-SAVE8",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      assertNull(result.get("dependenciesUpdated"));
      verifyNoInteractions(renameTransformHandler);
   }

   // ---------------------------------------------------------------------------
   // Bug #78662 -- an open viewsheet runtime stays bound to the old table after the cascade
   // ---------------------------------------------------------------------------

   private static final class CascadeCase {
      Map<String, Object> result;
      RuntimeViewsheet rvs;
      WorksheetService ws;
      AssetRepository repo;
      Viewsheet stored;
   }

   /**
    * Runs an in-place save that triggers the rename cascade with one connected viewsheet runtime
    * whose entry/save-point state the caller chooses.
    */
   private CascadeCase cascadeSave(AssetEntry vsEntry, int savePoint, int current,
                                   boolean storedVsMissing) throws Exception {
      return cascadeSave(vsEntry, savePoint, current, storedVsMissing, rvs -> { });
   }

   private CascadeCase cascadeSave(AssetEntry vsEntry, int savePoint, int current,
                                   boolean storedVsMissing,
                                   java.util.function.Consumer<RuntimeViewsheet> tweak)
      throws Exception
   {
      return cascadeSave(vsEntry, savePoint, current, storedVsMissing, tweak, null);
   }

   private CascadeCase cascadeSave(AssetEntry vsEntry, int savePoint, int current,
                                   boolean storedVsMissing,
                                   java.util.function.Consumer<RuntimeViewsheet> tweak,
                                   inetsoft.uql.viewsheet.vslayout.LayoutInfo storedLayout)
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      AssetEntry wsEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      Worksheet worksheet = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(worksheet, "T", "DISCOUNT");
      worksheet.addAssembly(t);
      ColumnRef discount = (ColumnRef) t.getColumnSelection(false).getAttribute("DISCOUNT");
      discount.setOldName("DISCOUNT");
      discount.setAlias("DISCOUNT_PCT");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(wsEntry);
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(rws.getCurrent()).thenReturn(3);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-78662"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-78662"));

      CascadeCase c = new CascadeCase();
      c.stored = mock(Viewsheet.class);
      when(c.stored.clone()).thenReturn(c.stored);
      when(c.stored.getLayoutInfo()).thenReturn(storedLayout);
      c.repo = mock(AssetRepository.class);

      if(storedVsMissing) {
         when(c.repo.getSheet(any(), any(), anyBoolean(), any()))
            .thenThrow(new RuntimeException("storage unavailable"));
      }
      else {
         when(c.repo.getSheet(any(), any(), anyBoolean(), any())).thenReturn(c.stored);
      }

      c.rvs = connectedVs(wsEntry, "rt-vs-78662", agent);
      when(c.rvs.getEntry()).thenReturn(vsEntry);
      when(c.rvs.getSavePoint()).thenReturn(savePoint);
      when(c.rvs.getCurrent()).thenReturn(current);
      tweak.accept(c.rvs);

      c.ws = mock(WorksheetService.class);
      when(c.ws.getRuntimeSheets(any())).thenReturn(new RuntimeSheet[]{ c.rvs });
      when(c.ws.getAssetRepository()).thenReturn(c.repo);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, c.ws,
         mock(RenameTransformHandler.class));

      AssetEntry dependent = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "Orders VS", null);

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class);
          MockedStatic<inetsoft.analytic.composition.event.VSEventUtil> vsUtil =
             mockStatic(inetsoft.analytic.composition.event.VSEventUtil.class))
      {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString()))
            .thenReturn(List.of(dependent));
         c.result = ctrl.save("TOK-78662", new WorksheetAgentController.SaveRequest(null, null),
                              agent);
      }

      return c;
   }

   private static AssetEntry savedVsEntry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "Orders VS", null);
   }

   /** A saved runtime with no edits since its last save follows the rename: reloaded from storage. */
   @Test
   void renameCascadeReloadsCleanSavedViewsheetRuntimeFromStorage78662() throws Exception {
      CascadeCase c = cascadeSave(savedVsEntry(), 2, 2, false);

      assertEquals(Boolean.TRUE, c.result.get("dependenciesUpdated"));
      assertEquals(List.of("rt-vs-78662"), c.result.get("reloadedViewsheets"));
      assertNull(c.result.get("staleViewsheets"));
      assertNull(c.result.get("warning"));
      verify(c.repo).getSheet(any(), any(), eq(false), eq(AssetContent.ALL));
      verify(c.rvs).setViewsheet(any(Viewsheet.class));
      verify(c.rvs).resetRuntime();
      // undo/redo history restarts from the reloaded sheet, so undo cannot restore the stale one
      verify(c.rvs, times(2)).resetCheckpoints(any());
      // layout undo stack restarts too, and the write revision moves so a stale dialog is refused
      verify(c.rvs).resetLayoutUndoRedo();
      verify(c.rvs).bumpWriteRevision();
      verify(c.ws).clearRenameDep("rt-vs-78662");
   }

   /** A runtime with unsaved edits is kept as is and named as stale; the edits are not discarded. */
   @Test
   void renameCascadeKeepsDirtyViewsheetRuntimeAndReportsItStale78662() throws Exception {
      CascadeCase c = cascadeSave(savedVsEntry(), 2, 3, false);

      verify(c.rvs, never()).setViewsheet(any());
      verify(c.rvs, never()).resetCheckpoints(any());
      verify(c.repo, never()).getSheet(any(), any(), anyBoolean(), any());
      verify(c.rvs).resetRuntime();
      assertNull(c.result.get("reloadedViewsheets"));
      List<?> stale = (List<?>) c.result.get("staleViewsheets");
      assertEquals(1, stale.size());
      Map<?, ?> item = (Map<?, ?>) stale.get(0);
      assertEquals("rt-vs-78662", item.get("runtimeId"));
      assertEquals("Orders VS", item.get("name"));
      assertTrue(item.get("reason").toString().contains("changes since its last save"));
      String warning = (String) c.result.get("warning");
      assertTrue(warning.contains("rt-vs-78662") && warning.contains("save_viewsheet"), warning);
   }

   /**
    * Layout edits checkpoint a separate stack that is cleared on a layout switch, so dirtiness is
    * judged by comparing the runtime's layouts with the stored copy: edit layout A, read layout B
    * (stack cleared), cascade -> A's edit must survive (B-1).
    */
   @Test
   void renameCascadeKeepsViewsheetRuntimeWithUnsavedLayoutEditsEvenAfterTheStackWasCleared78662()
      throws Exception
   {
      inetsoft.uql.viewsheet.vslayout.LayoutInfo edited =
         new inetsoft.uql.viewsheet.vslayout.LayoutInfo();
      edited.setPrintLayout(new inetsoft.uql.viewsheet.vslayout.PrintLayout());

      // layout stack size is 0 (as after a layout read-switch); only the definitions differ
      CascadeCase c = cascadeSave(savedVsEntry(), 2, 2, false,
         rvs -> when(rvs.getViewsheet().getLayoutInfo()).thenReturn(edited));

      verify(c.rvs, never()).setViewsheet(any());
      verify(c.rvs, never()).resetLayoutUndoRedo();
      verify(c.rvs, never()).resetCheckpoints(any());
      verify(c.rvs).resetRuntime();
      List<?> stale = (List<?>) c.result.get("staleViewsheets");
      assertEquals(1, stale.size());
      assertTrue(((Map<?, ?>) stale.get(0)).get("reason").toString().contains("layouts"));
   }

   /** A layout edit that was SAVED equals the stored copy, so it is not stale and reloads normally. */
   @Test
   void renameCascadeReloadsViewsheetRuntimeWhoseLayoutsMatchTheStoredCopy78662() throws Exception {
      inetsoft.uql.viewsheet.vslayout.LayoutInfo saved =
         new inetsoft.uql.viewsheet.vslayout.LayoutInfo();
      saved.setPrintLayout(new inetsoft.uql.viewsheet.vslayout.PrintLayout());
      CascadeCase c = cascadeSave(savedVsEntry(), 2, 2, false, rvs -> {
         when(rvs.getViewsheet().getLayoutInfo()).thenReturn(saved);
         // layout stack is non-empty after the saved edit; that alone must not make it stale
         when(rvs.getLayoutPointsSize()).thenReturn(2);
      }, saved);

      assertEquals(List.of("rt-vs-78662"), c.result.get("reloadedViewsheets"));
      assertNull(c.result.get("staleViewsheets"));
   }

   /** Binding-editor / viewer / preview runtimes only get the plain reset: never reloaded, not reported (I-2). */
   @Test
   void renameCascadeNeverReloadsBindingViewerOrPreviewRuntimes78662() throws Exception {
      List<java.util.function.Consumer<RuntimeViewsheet>> kinds = List.of(
         rvs -> when(rvs.isBinding()).thenReturn(true),
         rvs -> when(rvs.isViewer()).thenReturn(true),
         rvs -> when(rvs.isPreview()).thenReturn(true),
         rvs -> when(rvs.isRuntime()).thenReturn(true));

      for(java.util.function.Consumer<RuntimeViewsheet> kind : kinds) {
         CascadeCase c = cascadeSave(savedVsEntry(), 2, 2, false, kind);

         verify(c.rvs, never()).setViewsheet(any());
         verify(c.rvs, never()).resetCheckpoints(any());
         verify(c.rvs).resetRuntime();
         assertNull(c.result.get("staleViewsheets"));
         assertNull(c.result.get("reloadedViewsheets"));
      }
   }

   /** A never-saved (temporary) viewsheet looks clean by save point but has no stored copy. */
   @Test
   void renameCascadeTreatsUnsavedViewsheetRuntimeAsStale78662() throws Exception {
      AssetEntry temp = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
         AssetEntry.Type.VIEWSHEET, "__TEMPORARY__/vs-1", null);
      CascadeCase c = cascadeSave(temp, 0, 0, false);

      verify(c.rvs, never()).setViewsheet(any());
      verify(c.repo, never()).getSheet(any(), any(), anyBoolean(), any());
      List<?> stale = (List<?>) c.result.get("staleViewsheets");
      assertEquals(1, stale.size());
      assertTrue(((Map<?, ?>) stale.get(0)).get("reason").toString().contains("never been saved"));
   }

   /** A failed reload must not fail the worksheet save; it is reported stale with the cause. */
   @Test
   void renameCascadeReportsStaleWhenReloadFails78662() throws Exception {
      CascadeCase c = cascadeSave(savedVsEntry(), 2, 2, true);

      assertEquals(Boolean.TRUE, c.result.get("ok"));
      verify(c.rvs, never()).resetCheckpoints(any());
      List<?> stale = (List<?>) c.result.get("staleViewsheets");
      assertEquals(1, stale.size());
      assertTrue(((Map<?, ?>) stale.get(0)).get("reason").toString()
                    .contains("storage unavailable"));
      verify(c.rvs).resetRuntime();
   }

   // ---------------------------------------------------------------------------
   // Whole-branch review finding 2 (Important) -- the guard is 12 hand calls, not a chokepoint
   // ---------------------------------------------------------------------------

   /** Methods that must NOT enforce the whole-sheet guard, and why. */
   private static final Set<String> WHOLE_SHEET_GUARD_EXEMPT = Set.of(
      // Ending a pane-scoped session is exactly what detach is for -- see its own javadoc.
      "detach"
   );

   private static String sessionTokenMappingPath(Method m) {
      GetMapping get = m.getAnnotation(GetMapping.class);

      if(get != null && get.value().length > 0) {
         return get.value()[0];
      }

      PostMapping post = m.getAnnotation(PostMapping.class);

      if(post != null && post.value().length > 0) {
         return post.value()[0];
      }

      return null;
   }

   /**
    * Synthesizes a dummy argument per parameter. Safe because every guarded endpoint calls
    * {@code requireWholeSheetSession} as its first or second statement (right after
    * {@code requireEnabled()}), before any parameter other than {@code sessionToken}/
    * {@code user} is ever dereferenced -- so a null request body or unused query param never
    * gets the chance to NPE ahead of the guard.
    */
   private static Object[] paneScopeProbeArgs(Method m, Principal agent, String token) {
      Parameter[] params = m.getParameters();
      Object[] args = new Object[params.length];

      for(int i = 0; i < params.length; i++) {
         Parameter p = params[i];
         Class<?> t = p.getType();

         if(t == Principal.class) {
            args[i] = agent;
         }
         else if(p.isAnnotationPresent(PathVariable.class)) {
            args[i] = token;
         }
         else if(t == int.class) {
            args[i] = 0;
         }
         else if(t == boolean.class) {
            args[i] = false;
         }
         else {
            args[i] = null;
         }
      }

      return args;
   }

   /**
    * Mirror of {@code PaneScopeServiceTest#everyKindIsClassifiedByExactlyOneGuard} for THIS
    * controller. The viewsheet/binding side funnels every endpoint through ONE resolution
    * ({@code ViewsheetSessionService#requireSession}), so a new endpoint there cannot skip the
    * pane-scope refusal even by accident. This controller cannot do the same: {@code editOp} is
    * deliberately shared with the pane-scoped {@code WorksheetScriptService} caller, which has
    * ALREADY run its own narrow {@code PaneScopeService.check} and must not be refused again by
    * the broad whole-sheet check -- see {@link WorksheetAgentController#editOp} javadoc. So the
    * guard here is 14 hand-written {@code requireWholeSheetSession(sessionToken, user)} calls,
    * one per endpoint, and nothing before this test failed the build the day one of them went
    * missing.
    *
    * <p>This closes that gap the way the classification guard closes its own: by reflectively
    * enumerating every {@code @GetMapping}/{@code @PostMapping} method whose path contains
    * {@code {sessionToken}} and proving each one actually refuses a pane-scoped session, rather
    * than trusting that the next endpoint's author remembers to add the call. {@code join} never
    * matches (no {@code {sessionToken}} in its path -- that is where one is minted); {@code
    * detach} matches but is explicitly exempted above, for the reason its own javadoc gives.
    * Endpoint 15 fails THIS test the moment it omits the guard.
    */
   @Test
   void everySessionTokenEndpointRefusesAPaneScopedSessionOrIsExplicitlyExempt() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(anyString(), anyString())).thenReturn(paneScopedSession("tok-refl"));

      WorksheetAgentController controller = controller(featureOn(), mock(SheetJoinService.class),
         sessions, mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      Principal agent = agent();
      List<String> notRefused = new ArrayList<>();
      int checked = 0;

      for(Method m : WorksheetAgentController.class.getDeclaredMethods()) {
         if(m.isSynthetic()) {
            continue;
         }

         String path = sessionTokenMappingPath(m);

         if(path == null || !path.contains("{sessionToken}") ||
            WHOLE_SHEET_GUARD_EXEMPT.contains(m.getName()))
         {
            continue;
         }

         checked++;
         m.setAccessible(true);
         Object[] args = paneScopeProbeArgs(m, agent, "tok-refl");

         try {
            m.invoke(controller, args);
            notRefused.add(m.getName());
         }
         catch(InvocationTargetException e) {
            Throwable cause = e.getCause();

            if(!(cause instanceof PairingException) ||
               !cause.getMessage().contains("scoped to one script location") ||
               !cause.getMessage().contains("Connect to Claude"))
            {
               throw new AssertionError("Endpoint '" + m.getName() + "' rejected the pane-" +
                  "scoped session, but not with the whole-sheet-guard reason -- got: " + cause,
                  cause);
            }
         }
         catch(IllegalAccessException e) {
            throw new AssertionError("Could not invoke '" + m.getName() + "' reflectively", e);
         }
      }

      assertTrue(checked >= 14, "Expected at least the 14 known session-scoped endpoints to " +
         "be enumerated, found " + checked + " -- did WorksheetAgentController's mapping " +
         "annotations change shape?");
      assertTrue(notRefused.isEmpty(), "Endpoint(s) mapped with {sessionToken} did not refuse " +
         "a pane-scoped session: " + notRefused + ". Add requireWholeSheetSession(sessionToken," +
         " user) as their first call (right after requireEnabled()), or add them to " +
         "WHOLE_SHEET_GUARD_EXEMPT above with a reason like detach's.");
   }

   // ---------------------------------------------------------------------------
   // Bug #78590 P1: insert_column index range
   // ---------------------------------------------------------------------------

   private static EditRequest insertColumnAt(Integer index, Boolean insert) throws Exception {
      String json = "{\"op\":\"insert_column\",\"table\":\"T\"" +
         (index == null ? "" : ",\"index\":" + index) +
         (insert == null ? "" : ",\"insert\":" + insert) + "}";
      return new WebConfig().objectMapper().readValue(json, EditRequest.class);
   }

   /** header [a, b] + 1 data row; the column selection is a, b. */
   private static EmbeddedTableAssembly insertColumnTable(Worksheet ws, boolean expressionFirst) {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, "T");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING },
         new Object[][] { { "a", "b" }, { "x1", "y1" } }));
      ColumnSelection cs = new ColumnSelection();

      if(expressionFirst) {
         inetsoft.uql.erm.ExpressionRef expr = new inetsoft.uql.erm.ExpressionRef(null, "ex");
         expr.setExpression("1");
         cs.addAttribute(new ColumnRef(expr));
      }

      cs.addAttribute(new ColumnRef(new AttributeRef(null, "a")));
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "b")));
      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      return t;
   }

   private static void runInsertColumn(Worksheet ws, EditRequest req) throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-IC2"), any())).thenReturn(session("TOK-IC2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);
      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));
      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));
      ctrl.edit("TOK-IC2", req, agent);
   }

   private static String csNames(EmbeddedTableAssembly t) {
      ColumnSelection cs = t.getColumnSelection(false);
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         sb.append(i == 0 ? "" : ",").append(cs.getAttribute(i).getName());
      }

      return sb.toString();
   }

   private static String dataHeader(EmbeddedTableAssembly t) {
      XEmbeddedTable data = t.getEmbeddedData();
      StringBuilder sb = new StringBuilder();

      for(int c = 0; c < data.getColCount(); c++) {
         sb.append(c == 0 ? "" : ",").append(data.getObject(0, c));
      }

      return sb.toString();
   }

   @Test
   void insertColumnRefusesAnOutOfRangeIndexAndLeavesTheTableUnchanged() throws Exception {
      Object[][] cases = {
         { 99, true, "0..2" }, { 3, true, "0..2" }, { -1, true, "0..2" },
         { 99, false, "0..1" }, { 2, false, "0..1" }, { -1, false, "0..1" } };

      for(Object[] c : cases) {
         Worksheet ws = new Worksheet();
         EmbeddedTableAssembly t = insertColumnTable(ws, false);

         PairingException ex = assertThrows(PairingException.class,
            () -> runInsertColumn(ws, insertColumnAt((Integer) c[0], (Boolean) c[1])),
            Arrays.toString(c));

         assertTrue(ex.getMessage().contains("Column index out of range: " + c[0]),
                    ex.getMessage());
         assertTrue(ex.getMessage().contains((String) c[2]), ex.getMessage());
         assertEquals("a,b", dataHeader(t));
         assertEquals("a,b", csNames(t));
      }
   }

   @Test
   void insertColumnAcceptsTheBoundaryIndexes() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = insertColumnTable(ws, false);
      runInsertColumn(ws, insertColumnAt(2, true));
      assertEquals("a,b,col1", dataHeader(t));
      assertEquals("a,b,col1", csNames(t));

      Worksheet ws2 = new Worksheet();
      EmbeddedTableAssembly t2 = insertColumnTable(ws2, false);
      runInsertColumn(ws2, insertColumnAt(0, true));
      assertEquals("col1,a,b", dataHeader(t2));
      assertEquals("col1,a,b", csNames(t2));

      Worksheet ws3 = new Worksheet();
      EmbeddedTableAssembly t3 = insertColumnTable(ws3, false);
      runInsertColumn(ws3, insertColumnAt(1, false));
      assertEquals("a,b,col1", dataHeader(t3));
      assertEquals("a,b,col1", csNames(t3));
   }

   @Test
   void insertColumnAfterAnOmittedIndexAppendsAfterTheLastColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = insertColumnTable(ws, false);
      runInsertColumn(ws, insertColumnAt(null, false));
      assertEquals("a,b,col1", dataHeader(t));
      assertEquals("a,b,col1", csNames(t));
   }

   @Test
   void insertColumnAfterPlainColumnZeroKeepsDataAndColumnSelectionConsistent() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = insertColumnTable(ws, false);
      runInsertColumn(ws, insertColumnAt(0, false));
      assertEquals("a,col1,b", dataHeader(t));
      assertEquals("a,col1,b", csNames(t));
   }

   @Test
   void insertColumnAfterAnExpressionColumnZeroStillWorks() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = insertColumnTable(ws, true);
      runInsertColumn(ws, insertColumnAt(0, false));
      assertEquals("col1,a,b", dataHeader(t));
      assertEquals("ex,col1,a,b", csNames(t));
   }

   // ---------------------------------------------------------------------------
   // Bug #79078: add_table / edit_table must store maxRows on the table assembly (the field the
   // model read and every render honour), not only on the query. The edit service is mocked so
   // its applyOnRuntime runs the captured lambda against a REAL Worksheet.
   // ---------------------------------------------------------------------------

   private static final String MAXROWS_DS = "MyDatasource";

   private record MaxRowsFixture(WorksheetAgentController ctrl, Worksheet ws, Principal agent) {}

   private static MaxRowsFixture maxRowsFixture(String token, Worksheet ws) throws Exception {
      return maxRowsFixture(token, ws, null);
   }

   private static MaxRowsFixture maxRowsFixture(String token, Worksheet ws, AssetQuerySandbox box)
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq(token), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq(MAXROWS_DS), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      XRepository xrepository = mock(XRepository.class);
      TabularDataSource<?> xds = mock(TabularDataSource.class);
      when(xds.getType()).thenReturn("FakeColumnProducingNamedConnector");
      when(xrepository.getDataSource(eq(MAXROWS_DS))).thenReturn(xds);

      return new MaxRowsFixture(securityController(editSvc, dataSourceService,
         mock(SecurityEngine.class), mock(MetadataApiService.class), xrepository,
         mock(QueryManagerService.class)), ws, agent);
   }

   private static FakeColumnProducingNamedConnectorQuery maxRowsNamedQuery() {
      FakeColumnProducingNamedConnectorQuery q = new FakeColumnProducingNamedConnectorQuery();
      q.setColumnsByEndpoint(Map.of(
         "Repos", List.of("repoId"), "Issues", List.of("issueId"), "Paged", List.of("pagedId")));
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn(MAXROWS_DS);
      q.setDataSource(ds);
      return q;
   }

   /** A live tabular table whose assembly and query both already carry the given cap. */
   private static TabularTableAssembly maxRowsLiveTable(
      Worksheet ws, String endpoint, int assemblyCap, int queryCap) throws Exception
   {
      FakeColumnProducingNamedConnectorQuery q = maxRowsNamedQuery();
      q.setEndpoint(endpoint);
      q.setMaxRows(queryCap);
      TabularTableAssembly t = new TabularTableAssembly(ws, "Issues1");
      ((TabularTableAssemblyInfo) t.getTableInfo()).setQuery(q);
      t.setMaxRows(assemblyCap);
      ws.addAssembly(t);
      t.loadColumnSelection(new VariableTable(), true, null);
      return t;
   }

   private static void runAddTabular(
      MaxRowsFixture f, String token, EditRequest req, TabularQuery query) throws Exception
   {
      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq(MAXROWS_DS))).thenReturn(query);
         f.ctrl().edit(token, req, f.agent());
      }
   }

   // ---------------------------------------------------------------------------
   // Bug #79593: the column-discovery probe of add_table / edit_table must see the variable
   // values set through set_variable_values (they live in the runtime sandbox's variable table),
   // otherwise an unquoted $(var) is substituted as null and the connector rejects the query.
   // ---------------------------------------------------------------------------

   private static AssetQuerySandbox sandboxWith(VariableTable vars) {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getVariableTable()).thenReturn(vars);
      return box;
   }

   private static VariableTable sandboxVars() {
      VariableTable vars = new VariableTable();
      vars.put("cid", 7);
      vars.put("region", "west");
      vars.put("since", java.sql.Date.valueOf("2026-01-02"));
      return vars;
   }

   private static void assertProbeSawSandboxValues(VariableTable sandbox) throws Exception {
      VariableTable probe = FakeNamedConnectorQuery.getLastProbeVariables();
      assertNotNull(probe, "the column-discovery probe must have run");
      assertNotSame(sandbox, probe, "the probe gets a clone, never the live session table");
      assertEquals(7, probe.get("cid"));
      assertEquals("west", probe.get("region"));
      assertEquals(java.sql.Date.valueOf("2026-01-02"), probe.get("since"));
      assertEquals(3, sandbox.size(), "the sandbox table is left exactly as it was");
      assertNull(sandbox.get(inetsoft.uql.XQuery.HINT_MAX_ROWS));
   }

   @Test
   void addTableEndpointFormProbeReceivesSandboxVariables() throws Exception {
      VariableTable vars = sandboxVars();
      Worksheet ws = new Worksheet();
      MaxRowsFixture f = maxRowsFixture("TOK-V1", ws, sandboxWith(vars));
      FakeNamedConnectorQuery.clearLastProbeVariables();

      runAddTabular(f, "TOK-V1",
         addTabularTableRequestWithMaxRows("Iss", MAXROWS_DS, "Issues", null, null),
         maxRowsNamedQuery());

      assertProbeSawSandboxValues(vars);
   }

   @Test
   void addTableQueryParamsFormProbeReceivesSandboxVariables() throws Exception {
      VariableTable vars = sandboxVars();
      Worksheet ws = new Worksheet();
      MaxRowsFixture f = maxRowsFixture("TOK-V2", ws, sandboxWith(vars));
      EditRequest req = addQueryParamsTableRequestWithMaxRows(
         "Iss", MAXROWS_DS, Map.of("endpoint", "Issues"), null);
      FakeNamedConnectorQuery.clearLastProbeVariables();

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try {
         runAddTabular(f, "TOK-V2", req, maxRowsNamedQuery());
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }

      assertProbeSawSandboxValues(vars);
   }

   @Test
   void editTableProbeReceivesSandboxVariables() throws Exception {
      VariableTable vars = sandboxVars();
      Worksheet ws = new Worksheet();
      maxRowsLiveTable(ws, "Repos", 100, 100);
      MaxRowsFixture f = maxRowsFixture("TOK-V3", ws, sandboxWith(vars));
      FakeNamedConnectorQuery.clearLastProbeVariables();

      f.ctrl().edit("TOK-V3",
         editTableRequest("Issues1", "Issues", null, null, null, null, null, null), f.agent());

      assertProbeSawSandboxValues(vars);
   }

   @Test
   void probeVariablesFallsBackToAnEmptyTableWithoutASandbox() {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      assertEquals(0, WorksheetAgentController.probeVariables(rws).size());
   }

   @Test
   void addTableEndpointFormStoresMaxRowsOnTheAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      MaxRowsFixture f = maxRowsFixture("TOK-MR1", ws);
      FakeColumnProducingNamedConnectorQuery q = maxRowsNamedQuery();

      runAddTabular(f, "TOK-MR1",
         addTabularTableRequestWithMaxRows("Iss", MAXROWS_DS, "Paged", null, 100), q);

      assertEquals(100, ((TableAssembly) ws.getAssembly("Iss")).getMaxRows());
      assertEquals(100, q.getMaxRows(), "the query-level cap the guard reads is kept");
   }

   @Test
   void addTableSuffixFormStoresMaxRowsOnTheAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      MaxRowsFixture f = maxRowsFixture("TOK-MR2", ws);
      FakeColumnProducingCustomRestQuery q = new FakeColumnProducingCustomRestQuery();
      q.setColumnsBySuffix(Map.of("/paged", List.of("id")));

      runAddTabular(f, "TOK-MR2",
         addTabularTableRequestWithMaxRows("Iss", MAXROWS_DS, null, "/paged", 30), q);

      assertEquals(30, ((TableAssembly) ws.getAssembly("Iss")).getMaxRows());
   }

   @Test
   void addTableWithoutMaxRowsLeavesTheAssemblyUncapped() throws Exception {
      Worksheet ws = new Worksheet();
      MaxRowsFixture f = maxRowsFixture("TOK-MR3", ws);

      runAddTabular(f, "TOK-MR3",
         addTabularTableRequestWithMaxRows("Iss", MAXROWS_DS, "Issues", null, null),
         maxRowsNamedQuery());

      assertEquals(-1, ((TableAssembly) ws.getAssembly("Iss")).getMaxRows(),
         "no maxRows supplied: no default is invented");
   }

   @Test
   void addTableMaxRowsZeroOnAnUnpagedEndpointStoresUnlimited() throws Exception {
      Worksheet ws = new Worksheet();
      MaxRowsFixture f = maxRowsFixture("TOK-MR4", ws);

      runAddTabular(f, "TOK-MR4",
         addTabularTableRequestWithMaxRows("Iss", MAXROWS_DS, "Issues", null, 0),
         maxRowsNamedQuery());

      assertEquals(-1, ((TableAssembly) ws.getAssembly("Iss")).getMaxRows());
   }

   @Test
   void addTableNegativeMaxRowsIsRefusedBeforeAnythingIsAdded() throws Exception {
      Worksheet ws = new Worksheet();
      MaxRowsFixture f = maxRowsFixture("TOK-MR5", ws);

      PairingException ex = assertThrows(PairingException.class, () -> runAddTabular(f, "TOK-MR5",
         addTabularTableRequestWithMaxRows("Iss", MAXROWS_DS, "Issues", null, -5),
         maxRowsNamedQuery()));

      assertTrue(ex.getMessage().contains("maxRows cannot be negative (got -5)"), ex.getMessage());
      assertNull(ws.getAssembly("Iss"));
   }

   @Test
   void addTableQueryParamsFormStoresMaxRowsOnTheAssembly() throws Exception {
      Worksheet ws = new Worksheet();
      MaxRowsFixture f = maxRowsFixture("TOK-MR6", ws);
      EditRequest req = addQueryParamsTableRequestWithMaxRows(
         "Iss", MAXROWS_DS, Map.of("endpoint", "Issues"), 40);

      inetsoft.util.ConfigurationContext configContext = inetsoft.util.ConfigurationContext.getContext();
      org.springframework.context.ApplicationContext realAppContext = configContext.getApplicationContext();
      inetsoft.uql.util.Config configStub = mock(inetsoft.uql.util.Config.class);
      when(configStub.getResourceBundle(any())).thenReturn(null);
      org.springframework.context.ApplicationContext delegatingContext =
         mock(org.springframework.context.ApplicationContext.class,
              org.mockito.AdditionalAnswers.delegatesTo(realAppContext));
      doReturn(configStub).when(delegatingContext).getBean(inetsoft.uql.util.Config.class);
      configContext.setApplicationContext(delegatingContext);

      try {
         runAddTabular(f, "TOK-MR6", req, maxRowsNamedQuery());
      }
      finally {
         configContext.setApplicationContext(realAppContext);
      }

      assertEquals(40, ((TableAssembly) ws.getAssembly("Iss")).getMaxRows());
   }

   @Test
   void editTableLowersTheAssemblyMaxRows() throws Exception {
      Worksheet ws = new Worksheet();
      TabularTableAssembly t = maxRowsLiveTable(ws, "Repos", 100, 100);
      MaxRowsFixture f = maxRowsFixture("TOK-MR7", ws);

      f.ctrl().edit("TOK-MR7",
         editTableRequest("Issues1", "Issues", null, null, null, null, null, 30), f.agent());

      assertEquals(30, t.getMaxRows());
   }

   @Test
   void editTableSuffixFormLowersTheAssemblyMaxRows() throws Exception {
      Worksheet ws = new Worksheet();
      FakeColumnProducingCustomRestQuery q = new FakeColumnProducingCustomRestQuery();
      q.setColumnsBySuffix(Map.of("/a", List.of("a"), "/b", List.of("b")));
      q.setSuffix("/a");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn(MAXROWS_DS);
      q.setDataSource(ds);
      TabularTableAssembly t = new TabularTableAssembly(ws, "Issues1");
      ((TabularTableAssemblyInfo) t.getTableInfo()).setQuery(q);
      t.setMaxRows(100);
      ws.addAssembly(t);
      t.loadColumnSelection(new VariableTable(), true, null);
      MaxRowsFixture f = maxRowsFixture("TOK-MR8", ws);

      f.ctrl().edit("TOK-MR8",
         editTableRequest("Issues1", null, null, null, "/b", null, null, 30), f.agent());

      assertEquals(30, t.getMaxRows());
   }

   @Test
   void editTableWithoutMaxRowsKeepsTheExistingAssemblyCap() throws Exception {
      Worksheet ws = new Worksheet();
      TabularTableAssembly t = maxRowsLiveTable(ws, "Repos", 100, 100);
      MaxRowsFixture f = maxRowsFixture("TOK-MR9", ws);

      f.ctrl().edit("TOK-MR9",
         editTableRequest("Issues1", "Issues", null, null, null, null, null, null), f.agent());

      assertEquals(100, t.getMaxRows());
   }

   @Test
   void editTableMaxRowsZeroOnAnUnpagedTableStoresUnlimited() throws Exception {
      Worksheet ws = new Worksheet();
      TabularTableAssembly t = maxRowsLiveTable(ws, "Repos", 100, 100);
      MaxRowsFixture f = maxRowsFixture("TOK-MR10", ws);

      f.ctrl().edit("TOK-MR10",
         editTableRequest("Issues1", "Issues", null, null, null, null, null, 0), f.agent());

      assertEquals(-1, t.getMaxRows());
   }

   @Test
   void editTableMaxRowsZeroOnAPagedTableIsStillRefusedAndKeepsTheCap() throws Exception {
      Worksheet ws = new Worksheet();
      TabularTableAssembly t = maxRowsLiveTable(ws, "Paged", 100, 100);
      MaxRowsFixture f = maxRowsFixture("TOK-MR11", ws);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         f.ctrl().edit("TOK-MR11",
            editTableRequest("Issues1", "Paged", null, null, null, null, null, 0), f.agent()));

      assertTrue(ex.getMessage().contains("paginated"), ex.getMessage());
      assertEquals(100, t.getMaxRows());
   }

   @Test
   void editTableNegativeMaxRowsIsRefusedBeforeAnyMutation() throws Exception {
      Worksheet ws = new Worksheet();
      TabularTableAssembly t = maxRowsLiveTable(ws, "Repos", 100, 100);
      MaxRowsFixture f = maxRowsFixture("TOK-MR12", ws);

      PairingException ex = assertThrows(PairingException.class, () ->
         f.ctrl().edit("TOK-MR12",
            editTableRequest("Issues1", "Issues", null, null, null, null, null, -1), f.agent()));

      assertTrue(ex.getMessage().contains("maxRows cannot be negative (got -1)"), ex.getMessage());
      assertEquals(100, t.getMaxRows());
      assertEquals("Repos", ((FakeColumnProducingNamedConnectorQuery)
         ((TabularTableAssemblyInfo) t.getTableInfo()).getQuery()).getEndpoint());
   }

   /**
    * Bug #79530: a wiz-tagged principal is approved for every permission check, so the engine's
    * own WRITE check in setSheet never refused a READ-only user; the save pre-check repeats it on
    * a copy of the principal without the tag.
    */
   @Test
   void saveWithoutWritePermissionIsRefusedAndNothingIsWritten79530() throws Exception {
      IdentityID id = IdentityID.getIdentityIDFromKey("reader");
      SRPrincipal wiz = new SRPrincipal(id, new IdentityID[0], new String[0], id.orgID, 1L);
      wiz.setProperty("wiz", "true");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-79530"), any()))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-79530"));

      AssetRepository repo = mock(AssetRepository.class);
      when(repo.containsEntry(any())).thenReturn(true);
      doThrow(new inetsoft.util.MessageException("Write access denied: Orders WS"))
         .when(repo).checkAssetPermission(any(), any(), eq(ResourceAction.WRITE));
      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getAssetRepository()).thenReturn(repo);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      PairingException ex = assertThrows(PairingException.class, () -> ctrl.save("TOK-79530",
         new WorksheetAgentController.SaveRequest(null, null), wiz));

      assertTrue(ex.getMessage().contains("Write access denied: Orders WS"), ex.getMessage());
      verify(ws, never()).setWorksheet(any(), any(), any(), anyBoolean(), anyBoolean());
   }
}
