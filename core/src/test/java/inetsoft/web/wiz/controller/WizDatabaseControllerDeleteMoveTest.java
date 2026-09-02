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
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyException;
import inetsoft.uql.util.Config;
import inetsoft.util.MessageException;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.data.CheckDuplicateResponse;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.data.MoveCommand;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.WizDatasourceDeleteRequest;
import inetsoft.web.wiz.request.WizDatasourceRef;
import inetsoft.web.wiz.request.WizMoveCheckDuplicateRequest;
import inetsoft.web.wiz.request.WizMoveRequest;
import inetsoft.web.wiz.service.EndpointCatalogReader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the delete/move/dependency-check endpoints added to {@code WizDatabaseController} for
 * bug-76139 (AA-003/AA-005) -- see {@code 01-design.md} section 6's numbered StyleBI test list.
 *
 * <p>Kept separate from {@code WizDatabaseControllerSecurityTest} so that file's own scope
 * description (frozen contract + the pre-existing endpoints' authorization gates) stays accurate.
 * The "real registry, not mocked" cases the design also calls for (assertion 3's corrected
 * dependency-based rule, and the nested-folder-orphan counter-assertion) live in
 * {@link WizDatabaseControllerRealRegistryTest} instead -- everything here mocks
 * {@code dataSourceBrowserService}/{@code datasourcesService} to isolate the controller's own
 * translation logic (permission gate, per-item try/catch, exception mapping).</p>
 */
@Tag("core")
class WizDatabaseControllerDeleteMoveTest {
   // ---- delete ----------------------------------------------------------------------------

   /**
    * Regression test for the section 1.2 finding: {@code datasourcesService.deleteDataSource} has
    * no permission check anywhere in its own call chain, so the controller's own
    * {@code securityEngine.checkPermission} guard is the only thing standing between an
    * unauthorized caller and a real delete. Must fail against a naive just-call-the-service
    * implementation.
    */
   @Test
   void delete_deniesADatasourceWithoutDeletePermissionAndNeverCallsTheService() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq("/orders"), eq(ResourceAction.DELETE)))
         .thenReturn(false);

      WizDatasourceDeleteResult result = fixture.controller.deleteDatasources(
         new WizDatasourceDeleteRequest(List.of(new WizDatasourceRef("/orders", "orders", false)), false),
         fixture.principal);

      assertEquals(1, result.results().size());
      WizDatasourceDeleteItemResult item = result.results().get(0);
      assertFalse(item.ok());
      assertEquals(WizDatasourceDeleteItemResult.PERMISSION_DENIED, item.reason());
      verify(fixture.datasourcesService, never()).deleteDataSource(any(), any(), anyBoolean());
   }

   /**
    * Same as above for a folder: the regression test for the missing folder-level guard
    * ({@code dataSourceBrowserService.deleteDataSourceFolder} has no permission check either).
    */
   @Test
   void delete_deniesAFolderWithoutDeletePermissionAndNeverCallsTheService() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("Examples"),
         eq(ResourceAction.DELETE)))
         .thenReturn(false);

      WizDatasourceDeleteResult result = fixture.controller.deleteDatasources(
         new WizDatasourceDeleteRequest(
            List.of(new WizDatasourceRef("Examples", "Examples", true)), false),
         fixture.principal);

      WizDatasourceDeleteItemResult item = result.results().get(0);
      assertFalse(item.ok());
      assertEquals(WizDatasourceDeleteItemResult.PERMISSION_DENIED, item.reason());
      verify(fixture.dataSourceBrowserService, never())
         .deleteDataSourceFolder(any(), any(), anyBoolean(), any());
   }

   @Test
   void delete_forceFalseReportsHasDependenciesWithTheMessagePreserved() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq("/orders"), eq(ResourceAction.DELETE)))
         .thenReturn(true);
      when(fixture.datasourcesService.deleteDataSource(eq("/orders"), any(), eq(false)))
         .thenReturn(new ConnectionStatus("used by Worksheet1"));

      WizDatasourceDeleteResult result = fixture.controller.deleteDatasources(
         new WizDatasourceDeleteRequest(List.of(new WizDatasourceRef("/orders", "orders", false)), false),
         fixture.principal);

      WizDatasourceDeleteItemResult item = result.results().get(0);
      assertFalse(item.ok());
      assertEquals(WizDatasourceDeleteItemResult.HAS_DEPENDENCIES, item.reason());
      assertEquals("used by Worksheet1", item.dependencyMessage());
   }

   @Test
   void delete_forceTrueBypassesTheDependencyConflict() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq("/orders"), eq(ResourceAction.DELETE)))
         .thenReturn(true);
      when(fixture.datasourcesService.deleteDataSource(eq("/orders"), any(), eq(true)))
         .thenReturn(null);

      WizDatasourceDeleteResult result = fixture.controller.deleteDatasources(
         new WizDatasourceDeleteRequest(List.of(new WizDatasourceRef("/orders", "orders", false)), true),
         fixture.principal);

      WizDatasourceDeleteItemResult item = result.results().get(0);
      assertTrue(item.ok());
      assertNull(item.reason());
   }

   /**
    * Decision D2's regression test: one item succeeding, one denied and one throwing must all be
    * reported independently in the SAME response, not abort the whole batch.
    */
   @Test
   void delete_mixedBatchReportsThreeIndependentResults() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq("/ok"), eq(ResourceAction.DELETE)))
         .thenReturn(true);
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq("/denied"), eq(ResourceAction.DELETE)))
         .thenReturn(false);
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq("/boom"), eq(ResourceAction.DELETE)))
         .thenReturn(true);
      when(fixture.datasourcesService.deleteDataSource(eq("/ok"), any(), eq(false))).thenReturn(null);
      when(fixture.datasourcesService.deleteDataSource(eq("/boom"), any(), eq(false)))
         .thenThrow(new RuntimeException("boom"));

      WizDatasourceDeleteResult result = fixture.controller.deleteDatasources(
         new WizDatasourceDeleteRequest(List.of(
            new WizDatasourceRef("/ok", "ok", false),
            new WizDatasourceRef("/denied", "denied", false),
            new WizDatasourceRef("/boom", "boom", false)), false),
         fixture.principal);

      assertEquals(3, result.results().size());
      assertTrue(result.results().get(0).ok());
      assertFalse(result.results().get(1).ok());
      assertEquals(WizDatasourceDeleteItemResult.PERMISSION_DENIED, result.results().get(1).reason());
      assertFalse(result.results().get(2).ok());
      assertEquals(WizDatasourceDeleteItemResult.UNKNOWN, result.results().get(2).reason());
   }

   // ---- move --------------------------------------------------------------------------------

   /**
    * {@code moveDataSource} already fully self-authorizes (WRITE on target, DELETE on source) and
    * throws {@code MessageException} on a denial -- this must surface as a reported
    * PERMISSION_DENIED result, never an uncaught exception or a 500.
    */
   @Test
   void move_messageExceptionSurfacesAsPermissionDenied() throws Exception {
      Fixture fixture = new Fixture();
      doThrow(new MessageException("no write authority"))
         .when(fixture.dataSourceBrowserService).moveDataSource(any(MoveCommand[].class), any());

      WizMoveResult result = fixture.controller.moveDatasources(
         new WizMoveRequest(List.of(new WizDatasourceRef("/orders", "orders", false)), "Examples"),
         fixture.principal);

      WizMoveItemResult item = result.results().get(0);
      assertFalse(item.ok());
      assertEquals(WizMoveItemResult.PERMISSION_DENIED, item.reason());
   }

   /**
    * The self-descendant guard is wiz-side only -- {@code moveDataSource} has no such check, per
    * section 1.2 -- so this must be a short-circuit verified by a never-called assertion, not just
    * a final outcome that could coincidentally also result from some other path.
    */
   @Test
   void move_folderIntoOwnDescendantIsRejectedWithoutCallingMoveDataSource() throws Exception {
      Fixture fixture = new Fixture();

      WizMoveResult result = fixture.controller.moveDatasources(
         new WizMoveRequest(
            List.of(new WizDatasourceRef("folder", "folder", true)), "folder/sub"),
         fixture.principal);

      WizMoveItemResult item = result.results().get(0);
      assertFalse(item.ok());
      assertEquals(WizMoveItemResult.SELF_DESCENDANT, item.reason());
      verify(fixture.dataSourceBrowserService, never())
         .moveDataSource(any(MoveCommand[].class), any());
   }

   /**
    * Decision D2's twin for move: the native {@code moveDataSource(MoveCommand[])} aborts its
    * whole batch on the first exception (section 1.4) -- the wiz wrapper issues one call per item
    * instead, so a failure on item 2 of 3 must not prevent item 3 from being attempted.
    */
   @Test
   void move_bulkRequestStillAttemptsLaterItemsAfterAnEarlierFailure() throws Exception {
      Fixture fixture = new Fixture();
      doThrow(new MessageException("no write authority"))
         .when(fixture.dataSourceBrowserService).moveDataSource(
            argThat(cmds -> cmds.length == 1 && "/b".equals(cmds[0].getOldPath())), any());

      WizMoveResult result = fixture.controller.moveDatasources(
         new WizMoveRequest(List.of(
            new WizDatasourceRef("/a", "a", false),
            new WizDatasourceRef("/b", "b", false),
            new WizDatasourceRef("/c", "c", false)), "Examples"),
         fixture.principal);

      assertEquals(3, result.results().size());
      assertTrue(result.results().get(0).ok());
      assertFalse(result.results().get(1).ok());
      assertTrue(result.results().get(2).ok());
      verify(fixture.dataSourceBrowserService, times(3))
         .moveDataSource(any(MoveCommand[].class), any());
   }

   @Test
   void checkMoveDuplicate_neverChecksPermission() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.dataSourceBrowserService.checkItemsDuplicate(any(), any()))
         .thenReturn(new CheckDuplicateResponse(true));

      WizMoveCheckDuplicateResult result = fixture.controller.checkMoveDuplicate(
         new WizMoveCheckDuplicateRequest(
            List.of(new WizDatasourceRef("/orders", "orders", false)), "Examples"));

      assertTrue(result.duplicate());
      verifyNoInteractions(fixture.securityEngine);
   }

   // ---- checkOuterDependencies ---------------------------------------------------------------

   @Test
   void checkOuterDependencies_reportsOnlyItemsWithAConflict() throws Exception {
      Fixture fixture = new Fixture();
      DependencyException conflict =
         new DependencyException(new AssetEntry(AssetRepository.QUERY_SCOPE,
                                                 AssetEntry.Type.DATA_SOURCE, "/orders", null));
      conflict.addDependency(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null));
      doThrow(conflict).when(fixture.datasourcesService).checkDataSourceOuterDependencies("/orders");
      doNothing().when(fixture.datasourcesService).checkDataSourceOuterDependencies("/clean");

      WizDependencyCheckResult result = fixture.controller.checkOuterDependencies(new WizDatasourceRef[]{
         new WizDatasourceRef("/orders", "orders", false),
         new WizDatasourceRef("/clean", "clean", false)
      });

      assertTrue(result.messagesByPath().containsKey("/orders"));
      assertFalse(result.messagesByPath().containsKey("/clean"));
   }

   /** The controller with every collaborator mocked. */
   private static final class Fixture {
      Fixture() {
         controller = new WizDatabaseController(
            dataSourceBrowserService, dataSourceStatusService, databaseDatasourcesService,
            databaseTypeService, securityEngine, uqlConfig, xrepository, endpointCatalogReader,
            datasourcesService);
      }

      final DataSourceBrowserService dataSourceBrowserService = mock(DataSourceBrowserService.class);
      final DataSourceStatusService dataSourceStatusService = mock(DataSourceStatusService.class);
      final DatabaseDatasourcesService databaseDatasourcesService =
         mock(DatabaseDatasourcesService.class);
      final DatabaseTypeService databaseTypeService = mock(DatabaseTypeService.class);
      final SecurityEngine securityEngine = mock(SecurityEngine.class);
      final Config uqlConfig = mock(Config.class);
      final XRepository xrepository = mock(XRepository.class);
      final EndpointCatalogReader endpointCatalogReader = mock(EndpointCatalogReader.class);
      final DatasourcesService datasourcesService = mock(DatasourcesService.class);
      final Principal principal = mock(Principal.class);
      final WizDatabaseController controller;
   }
}
