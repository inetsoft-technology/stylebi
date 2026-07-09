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
import inetsoft.sree.security.SecurityException;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.model.WorksheetConstructionModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Coverage for the authorization gates added to {@link GenerateWsService}:
 * <ul>
 *   <li>the "Visual Composer -> Data Worksheet" action-level gate checked at the top of
 *       {@code generateWs}</li>
 *   <li>the datasource READ check inside {@code applySourceInfo}, reached while building the
 *       primary physical table for a new (non-worksheet-origin, non-join) query</li>
 * </ul>
 *
 * <p>These tests only assert on the gate itself: denial short-circuits before any worksheet
 * load/mutation or datasource metadata lookup, and grant lets execution proceed past the check
 * (verified by the mock interaction actually happening), regardless of what unrelated exception
 * the deliberately-minimal downstream mocking then produces.
 *
 * <p>Needs the full Sree bootstrap because {@code new Worksheet()} (constructed by
 * {@code buildFreshWorksheet} once the field-selection check passes) reads {@code SreeEnv} in its
 * constructor.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GenerateWsServicePermissionTest {
   private static class Deps {
      final ViewsheetService viewsheetService = mock(ViewsheetService.class);
      final MetadataApiService metadataApiService = mock(MetadataApiService.class);
      final InnerJoinService innerJoinService = mock(InnerJoinService.class);
      final LayoutGraphService layoutGraphService = mock(LayoutGraphService.class);
      final WsMergeService wsMergeService = mock(WsMergeService.class);
      final ObjectMapper objectMapper = new ObjectMapper();
      final DataSourceService dataSourceService = mock(DataSourceService.class);
      final SecurityEngine securityEngine = mock(SecurityEngine.class);

      GenerateWsService service() {
         return new GenerateWsService(viewsheetService, metadataApiService, innerJoinService,
                                      layoutGraphService, wsMergeService, objectMapper,
                                      dataSourceService, securityEngine);
      }
   }

   private static final Principal USER = mock(Principal.class);

   /** A single-field, single-table (DATABASE, not WORKSHEET), no-join construction model. */
   private static WorksheetConstructionModel physicalFieldModel(String datasourcePath) {
      WorksheetConstructionModel.SourceInfo source = new WorksheetConstructionModel.SourceInfo();
      source.setType("DATABASE");
      source.setPath(datasourcePath);
      source.setSchema("public");

      WorksheetConstructionModel.TableInfo table = new WorksheetConstructionModel.TableInfo();
      table.setName("customers");
      table.setSource(source);

      WorksheetConstructionModel.QueryField field =
         new WorksheetConstructionModel.QueryField(table, "id");
      field.setVisible(true);

      WorksheetConstructionModel model = new WorksheetConstructionModel();
      model.setName("newTable");
      model.setFields(List.of(field));
      return model;
   }

   // ─── generateWs: WORKSHEET/ACCESS gate ─────────────────────────────────────

   @Test
   void generateWsThrowsWhenWorksheetAccessDenied() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      // Content doesn't matter: denial must short-circuit before the model is even inspected.
      WorksheetConstructionModel model = new WorksheetConstructionModel();

      assertThrows(SecurityException.class, () -> deps.service().generateWs(model, USER));

      verifyNoInteractions(deps.viewsheetService);
      verifyNoInteractions(deps.metadataApiService);
      verifyNoInteractions(deps.dataSourceService);
   }

   @Test
   void generateWsProceedsWhenWorksheetAccessGranted() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // No fields => fails for an unrelated reason once past the gate.
      WorksheetConstructionModel model = new WorksheetConstructionModel();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> deps.service().generateWs(model, USER));
      assertTrue(ex.getMessage().contains("field"),
                 "expected the 'at least one field' error, got: " + ex.getMessage());

      verify(deps.securityEngine).checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                                   eq(ResourceAction.ACCESS));
   }

   // ─── generateWs -> applySourceInfo: datasource READ gate ───────────────────

   @Test
   void generateWsThrowsWhenDatasourceReadDenied() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(false);

      WorksheetConstructionModel model = physicalFieldModel("myds");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> deps.service().generateWs(model, USER));
      assertTrue(ex.getMessage().contains("myds"),
                 "error should name the denied datasource, got: " + ex.getMessage());

      verifyNoInteractions(deps.metadataApiService);
   }

   @Test
   void generateWsProceedsWhenDatasourceReadGranted() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);

      WorksheetConstructionModel model = physicalFieldModel("myds");

      // metadataApiService is an unstubbed mock: getJDBCDatasource/getTableMetaData both return
      // null, so this fails downstream with "Table does not exist" — proof execution proceeded
      // past the datasource gate all the way into applySourceInfo's metadata lookup.
      assertThrows(IllegalArgumentException.class, () -> deps.service().generateWs(model, USER));

      verify(deps.metadataApiService).getJDBCDatasource(eq("myds"));
      verify(deps.dataSourceService).checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER));
   }
}
