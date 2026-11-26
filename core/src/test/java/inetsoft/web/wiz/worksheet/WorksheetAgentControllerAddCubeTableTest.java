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
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.report.composition.WorksheetService;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.web.WebConfig;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.service.MetadataApiService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The {@code add_cube_table} edit op at the controller: the request shape, and the refusals that
 * must come before the worksheet is touched (permission, data source kind, missing fields, the
 * fields of the other {@code add_table} forms). The columns themselves are
 * {@link WorksheetCubeTableBuilderTest}.
 */
@Tag("core")
class WorksheetAgentControllerAddCubeTableTest {
   @Test
   void theRequestShapeDeserializesIntoTheNewFields() throws Exception {
      EditRequest req = request(Map.of(
         "datasource", "OLAP/FoodMart", "cube", "Sales", "table", "UnitSalesByTime",
         "levels", List.of(Map.of("dimension", "Time", "hierarchy", "[Time]", "level", "Year"),
                           Map.of("dimension", "Time", "level", "Quarter")),
         "measures", List.of("Unit Sales")));

      assertEquals("add_cube_table", req.op());
      assertEquals("OLAP/FoodMart", req.datasource());
      assertEquals("Sales", req.cube());
      assertEquals("UnitSalesByTime", req.table());
      assertEquals(List.of(new WorksheetMutationSupport.CubeLevelSpec("Time", "[Time]", "Year"),
                           new WorksheetMutationSupport.CubeLevelSpec("Time", null, "Quarter")),
                   req.levels());
      assertEquals(List.of("Unit Sales"), req.measures());
   }

   @Test
   void theOlderPositionalConstructorStillBuildsARequestWithTheNewFieldsNull() {
      // the compatibility constructor of the previous arity: every caller built before
      // add_cube_table keeps compiling and gets null cube, levels and measures
      EditRequest req = new EditRequest(
         "add_table", "T", null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, "ds", null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null);

      assertNull(req.cube());
      assertNull(req.levels());
      assertNull(req.measures());
      assertEquals("ds", req.datasource());
   }

   @Test
   void withoutAReadPermission_nothingIsResolvedOrTouched() throws Exception {
      Fixture f = new Fixture();
      when(f.dataSourceService.checkPermission(eq("OLAP/FoodMart"), eq(ResourceAction.READ),
                                               eq(f.agent))).thenReturn(false);

      PairingException ex = assertThrows(PairingException.class,
         () -> f.controller.edit("TOK", cubeRequest("OLAP/FoodMart", "Sales"), f.agent));

      assertTrue(ex.getMessage().contains("no READ permission on datasource OLAP/FoodMart"),
                 ex.getMessage());
      verifyNoInteractions(f.xrepository);
      verifyNoInteractions(f.edit);
   }

   @Test
   void aDataSourceThatIsNotXmla_isRefusedPointingAtAddTable() throws Exception {
      Fixture f = new Fixture();
      JDBCDataSource jdbc = mock(JDBCDataSource.class);
      when(jdbc.getType()).thenReturn("jdbc");
      when(f.xrepository.getDataSource("db/orders")).thenReturn(jdbc);

      PairingException ex = assertThrows(PairingException.class,
         () -> f.controller.edit("TOK", cubeRequest("db/orders", "Sales"), f.agent));

      assertTrue(ex.getMessage().contains("is a jdbc data source, not an XMLA (cube) data " +
                                             "source; use add_table"), ex.getMessage());
      verifyNoInteractions(f.edit);
   }

   @Test
   void anUnknownDataSource_isNamed() throws Exception {
      Fixture f = new Fixture();

      PairingException ex = assertThrows(PairingException.class,
         () -> f.controller.edit("TOK", cubeRequest("Nope", "Sales"), f.agent));

      assertTrue(ex.getMessage().startsWith("datasource: no data source at \"Nope\""),
                 ex.getMessage());
   }

   @Test
   void aSourceWithNoStoredCubes_saysHowToLoadThem() throws Exception {
      Fixture f = new Fixture();
      when(f.xrepository.getDataSource("OLAP/FoodMart")).thenReturn(mock(XMLADataSource.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> f.controller.edit("TOK", cubeRequest("OLAP/FoodMart", "Sales"), f.agent));

      assertTrue(ex.getMessage().startsWith("cube: no cube \"Sales\" in data source " +
                                               "\"OLAP/FoodMart\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("refresh_data_source_metadata"), ex.getMessage());
      verifyNoInteractions(f.edit);
   }

   @Test
   void missingDatasourceOrCube_isRefusedNamingTheField() throws Exception {
      Fixture f = new Fixture();

      PairingException noDs = assertThrows(PairingException.class,
         () -> f.controller.edit("TOK", cubeRequest(null, "Sales"), f.agent));
      PairingException noCube = assertThrows(PairingException.class,
         () -> f.controller.edit("TOK", cubeRequest("OLAP/FoodMart", " "), f.agent));

      assertTrue(noDs.getMessage().startsWith("datasource is required"), noDs.getMessage());
      assertTrue(noCube.getMessage().startsWith("cube is required"), noCube.getMessage());
      verifyNoInteractions(f.edit);
   }

   @Test
   void theFieldsOfOtherAddTableForms_areRefusedNotIgnored() throws Exception {
      Fixture f = new Fixture();

      for(String key : new String[] { "logicalModel", "schema", "catalog", "endpoint",
                                      "suffix" })
      {
         Map<String, Object> body = new LinkedHashMap<>();
         body.put("datasource", "OLAP/FoodMart");
         body.put("cube", "Sales");
         body.put("measures", List.of("Unit Sales"));
         body.put(key, "x");

         PairingException ex = assertThrows(PairingException.class,
            () -> f.controller.edit("TOK", request(body), f.agent), key);

         assertTrue(ex.getMessage().startsWith("add_cube_table cannot carry " + key),
                    ex.getMessage());
      }

      verifyNoInteractions(f.edit);
   }

   // ---- helpers ----------------------------------------------------------------------------

   private static EditRequest cubeRequest(String datasource, String cube) throws Exception {
      Map<String, Object> body = new LinkedHashMap<>();

      if(datasource != null) {
         body.put("datasource", datasource);
      }

      body.put("cube", cube);
      body.put("measures", List.of("Unit Sales"));

      return request(body);
   }

   private static EditRequest request(Map<String, Object> fields) throws Exception {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("op", "add_cube_table");
      body.putAll(fields);
      ObjectMapper mapper = new WebConfig().objectMapper();

      return mapper.readValue(mapper.writeValueAsString(body), EditRequest.class);
   }

   private static final class Fixture {
      Fixture() throws Exception {
         SheetAgentFeature feature = mock(SheetAgentFeature.class);
         when(feature.isEnabled()).thenReturn(true);
         when(dataSourceService.checkPermission(anyString(), eq(ResourceAction.READ), any()))
            .thenReturn(true);
         controller = new WorksheetAgentController(
            feature, mock(SheetJoinService.class), mock(SheetSessionService.class),
            mock(WorksheetReadService.class), edit, mock(WorksheetService.class),
            mock(WorksheetPreviewService.class), mock(SheetAgentBroadcastService.class),
            xrepository, mock(AssetRepository.class), mock(MetadataApiService.class),
            mock(QueryManagerService.class), mock(LayoutGraphService.class), dataSourceService,
            mock(SecurityEngine.class), mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
            mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
            mock(inetsoft.report.composition.execution.AssetDataCache.class),
            mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
            mock(inetsoft.web.composer.ws.dialog.ShowPlanServiceProxy.class),
            mock(inetsoft.web.wiz.service.RawDataService.class));
      }

      final Principal agent = TestPrincipals.user("alice", "host-org");
      final WorksheetEditService edit = mock(WorksheetEditService.class);
      final DataSourceService dataSourceService = mock(DataSourceService.class);
      final XRepository xrepository = mock(XRepository.class);
      final WorksheetAgentController controller;
   }
}
