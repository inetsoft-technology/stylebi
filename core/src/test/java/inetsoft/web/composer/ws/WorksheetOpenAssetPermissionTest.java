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
package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XCube;
import inetsoft.uql.XDomain;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.xmla.Domain;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.ImmutableOpenAssetEvent;
import inetsoft.web.composer.ws.event.OpenAssetEvent;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77400: dropping data source columns or tables into a worksheet builds the new table's
 * source from the client's entry properties, and nothing downstream checks it, so
 * {@link WorksheetOpenAssetService} must check the built source before the table is created.
 * Labels: O = STOMP /composer/worksheet/open-asset, C = POST
 * /api/composer-worksheet/open-asset/check-trap/{id}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetOpenAssetPermissionTest {
   private static final String ALLOWED = "DS_OK";
   private static final String DENIED = "DS1";
   private static final String MODEL = "LM";
   private static final String MODEL_FOLDER = "F";
   private static final String RUNTIME_ID = "ws1";

   private SecurityEngine securityEngine;
   private AssetRepository assetRepository;
   private Worksheet worksheet;
   private WorksheetOpenAssetService service;
   private final Principal principal = () -> "bob";

   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      XRepository repository = mock(XRepository.class);
      assetRepository = mock(AssetRepository.class);
      QueryManagerService queryManager = new QueryManagerService(
         mock(RuntimeQueryService.class), repository, mock(DataSourceService.class),
         securityEngine, mock(ColumnCache.class));

      worksheet = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(worksheet);
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getWorksheet(RUNTIME_ID, principal)).thenReturn(rws);
      when(viewsheetService.getAssetRepository()).thenReturn(assetRepository);
      service = new WorksheetOpenAssetService(viewsheetService, null, queryManager);

      grantRead(ALLOWED);
      grantPhysicalAccess(true);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_MODEL_FOLDER), anyString(), eq(ResourceAction.READ)))
         .thenReturn(true);

      // a logical model in a folder, with an explicit deny on the model itself
      XDataModel dataModel = mock(XDataModel.class);
      XLogicalModel logicalModel = mock(XLogicalModel.class);
      when(logicalModel.getFolder()).thenReturn(MODEL_FOLDER);
      when(dataModel.getLogicalModel(MODEL)).thenReturn(logicalModel);
      when(repository.getDataModel(ALLOWED)).thenReturn(dataModel);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.QUERY), anyString(), eq(ResourceAction.READ)))
         .thenAnswer(inv -> !(MODEL + "::" + ALLOWED + "^__^" + MODEL_FOLDER)
            .equals(inv.getArgument(2)));
   }

   private void grantRead(String... names) throws Exception {
      Set<String> readable = Set.of(names);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_SOURCE), nullable(String.class), eq(ResourceAction.READ)))
         .thenAnswer(inv -> readable.contains(inv.<String>getArgument(2)));
   }

   private void grantPhysicalAccess(boolean allowed) throws Exception {
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.PHYSICAL_TABLE), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(allowed);
   }

   private static AssetEntry physicalColumn(String prefix) {
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.PHYSICAL_COLUMN, prefix + "/T/C", null);
      entry.setProperty("prefix", prefix);
      entry.setProperty("source", "T");
      entry.setProperty("type", SourceInfo.PHYSICAL_TABLE + "");
      entry.setProperty("entity", "T");
      entry.setProperty("attribute", "C");
      entry.setProperty("table", "T");
      return entry;
   }

   private static AssetEntry modelColumn() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.COLUMN, ALLOWED + "/" + MODEL + "/E/A", null);
      entry.setProperty("prefix", ALLOWED);
      entry.setProperty("source", MODEL);
      entry.setProperty("type", SourceInfo.MODEL + "");
      entry.setProperty("entity", "E");
      entry.setProperty("attribute", "A");
      return entry;
   }

   private static OpenAssetEvent event(AssetEntry... entries) {
      return ImmutableOpenAssetEvent.builder().entries(entries).top(0).left(0).build();
   }

   private void openAsset(OpenAssetEvent event) throws Exception {
      service.openAsset(RUNTIME_ID, event, principal, mock(CommandDispatcher.class));
   }

   private void assertDeniedAndNothingAdded(Executable call) {
      try(MockedStatic<WorksheetEventUtil> util = mockStatic(WorksheetEventUtil.class)) {
         assertThrows(java.lang.SecurityException.class, call);
         util.verifyNoInteractions();
      }

      assertEquals(0, worksheet.getAssemblies().length);
   }

   @Test
   void oPhysicalColumnDeniedWithoutDataSourceRead() {
      assertDeniedAndNothingAdded(() -> openAsset(event(physicalColumn(DENIED))));
   }

   @Test
   void oPhysicalColumnDeniedWithoutPhysicalTableAccess() throws Exception {
      grantPhysicalAccess(false);
      assertDeniedAndNothingAdded(() -> openAsset(event(physicalColumn(ALLOWED))));
   }

   @Test
   void oModelColumnDeniedByExplicitModelPermissionInFolder() throws Exception {
      assertDeniedAndNothingAdded(() -> openAsset(event(modelColumn())));
      verify(securityEngine).checkPermission(
         principal, ResourceType.QUERY, MODEL + "::" + ALLOWED + "^__^" + MODEL_FOLDER,
         ResourceAction.READ);
   }

   @Test
   void oExpandedTableCheckedOnItsColumns() throws Exception {
      AssetEntry table = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.PHYSICAL_TABLE, DENIED + "/T", null);
      table.setProperty("prefix", DENIED);
      when(assetRepository.getEntries(same(table), same(principal), isNull()))
         .thenReturn(new AssetEntry[] { physicalColumn(DENIED) });

      assertDeniedAndNothingAdded(() -> openAsset(event(table)));
   }

   @Test
   void oPhysicalColumnAddedWithReadAndAccess() throws Exception {
      try(MockedStatic<WorksheetEventUtil> util = mockStatic(WorksheetEventUtil.class)) {
         openAsset(event(physicalColumn(ALLOWED)));

         Assembly[] assemblies = worksheet.getAssemblies();
         assertEquals(1, assemblies.length);
         assertInstanceOf(PhysicalBoundTableAssembly.class, assemblies[0]);
         assertEquals(ALLOWED,
                      ((BoundTableAssembly) assemblies[0]).getSourceInfo().getPrefix());
         util.verify(() -> WorksheetEventUtil.loadTableData(
            any(), eq(assemblies[0].getName()), eq(true), eq(true)));
      }
   }

   // ---- cube columns: the same READ that the asset tree's cube listing requires ----

   private static final String CUBE_SOURCE = "XMLA";
   private static final String CUBE = "Budget";

   private static AssetEntry cubeColumn() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.COLUMN,
         CUBE_SOURCE + "/" + CUBE + "/Measure/Amount", null);
      entry.setProperty("prefix", CUBE_SOURCE);
      entry.setProperty("source", CUBE);
      entry.setProperty("type", DataRef.CUBE_MEASURE + "");
      entry.setProperty("refType", DataRef.CUBE_MEASURE + "");
      entry.setProperty("attribute", "Amount");
      entry.setProperty("table", CUBE_SOURCE + "/" + CUBE);
      return entry;
   }

   /**
    * Runs the call with the static security engine and repository that the cube listing
    * decision uses, with an OLAP cube (CUBE READ) or a model cube (QUERY READ).
    */
   private void withCube(boolean olap, boolean cubeReadable, Executable call) throws Throwable {
      grantRead(ALLOWED, CUBE_SOURCE);
      XDomain domain = olap ? mock(Domain.class) : mock(XDomain.class);
      XCube cube = mock(XCube.class);
      when(domain.getCube(CUBE)).thenReturn(cube);
      XRepository repository = mock(XRepository.class);
      when(repository.getDomain(CUBE_SOURCE)).thenReturn(domain);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.CUBE), anyString(), eq(ResourceAction.READ)))
         .thenAnswer(inv -> cubeReadable || !(CUBE_SOURCE + "::" + CUBE).equals(inv.getArgument(2)));

      if(!olap) {
         when(securityEngine.checkPermission(
            any(Principal.class), eq(ResourceType.QUERY), anyString(), eq(ResourceAction.READ)))
            .thenAnswer(inv -> cubeReadable || !(CUBE + "::" + CUBE_SOURCE).equals(inv.getArgument(2)));
      }

      try(MockedStatic<SecurityEngine> security = mockStatic(SecurityEngine.class);
          MockedStatic<XRepository> repositoryStatic = mockStatic(XRepository.class))
      {
         security.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
         repositoryStatic.when(XRepository::getRepository).thenReturn(repository);
         call.execute();
      }
   }

   @Test
   void oCubeColumnDeniedWithoutCubeRead() throws Throwable {
      withCube(true, false, () -> assertDeniedAndNothingAdded(() -> openAsset(event(cubeColumn()))));
      verify(securityEngine).checkPermission(
         principal, ResourceType.CUBE, CUBE_SOURCE + "::" + CUBE, ResourceAction.READ);
   }

   @Test
   void oModelCubeColumnDeniedWithoutQueryRead() throws Throwable {
      withCube(false, false, () -> assertDeniedAndNothingAdded(() -> openAsset(event(cubeColumn()))));
      verify(securityEngine).checkPermission(
         principal, ResourceType.QUERY, CUBE + "::" + CUBE_SOURCE, ResourceAction.READ);
   }

   @Test
   void oCubeColumnDeniedWithoutDataSourceRead() throws Throwable {
      withCube(true, true, () -> {
         grantRead(ALLOWED);
         assertDeniedAndNothingAdded(() -> openAsset(event(cubeColumn())));
      });
   }

   @Test
   void cCheckTrapCubeDeniedWithoutCubeRead() throws Throwable {
      withCube(true, false, () -> assertDeniedAndNothingAdded(
         () -> service.checkTrap(RUNTIME_ID, event(cubeColumn()), principal)));
   }

   @Test
   void oCubeColumnAddedWithCubeRead() throws Throwable {
      withCube(true, true, () -> {
         try(MockedStatic<WorksheetEventUtil> util = mockStatic(WorksheetEventUtil.class)) {
            openAsset(event(cubeColumn()));

            Assembly[] assemblies = worksheet.getAssemblies();
            assertEquals(1, assemblies.length);
            assertInstanceOf(CubeTableAssembly.class, assemblies[0]);
            util.verify(() -> WorksheetEventUtil.loadTableData(
               any(), eq(assemblies[0].getName()), eq(true), eq(true)));
         }
      });
   }

   @Test
   void oModelCubeColumnAddedWithQueryRead() throws Throwable {
      withCube(false, true, () -> {
         try(MockedStatic<WorksheetEventUtil> util = mockStatic(WorksheetEventUtil.class)) {
            openAsset(event(cubeColumn()));
            assertInstanceOf(CubeTableAssembly.class, worksheet.getAssemblies()[0]);
         }
      });
   }

   @Test
   void cCheckTrapDeniedWithoutDataSourceRead() {
      assertDeniedAndNothingAdded(
         () -> service.checkTrap(RUNTIME_ID, event(physicalColumn(DENIED)), principal));
   }

   @Test
   void cCheckTrapPassesWithRead() throws Exception {
      // past the check the trap context is built; a single table has no trap to report
      assertNull(service.checkTrap(RUNTIME_ID, event(physicalColumn(ALLOWED)), principal));
      assertEquals(0, worksheet.getAssemblies().length);
   }
}
