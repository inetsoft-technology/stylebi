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

import inetsoft.uql.asset.*;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code generateWs} must refuse a table name it cannot honor: a blank name, or a name already
 * held by an assembly of the source worksheet. {@code Worksheet.addAssembly} reports a holder of
 * another kind by returning false (and replaces a table in place); the service ignored that and
 * reported success for a table that was never added.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GenerateWsServiceNameClashTest {
   private static final Principal USER = mock(Principal.class);
   private final ViewsheetService viewsheetService = mock(ViewsheetService.class);
   private final AssetRepository assetRepository = mock(AssetRepository.class);
   private final SecurityEngine securityEngine = mock(SecurityEngine.class);
   private final GenerateWsService service = new GenerateWsService(
      viewsheetService, mock(MetadataApiService.class), mock(InnerJoinService.class),
      mock(LayoutGraphService.class), mock(WsMergeService.class), new ObjectMapper(),
      mock(DataSourceService.class), securityEngine);

   /** A saved worksheet with tables ORDERS and OTHER, variable V and named group G. */
   private Worksheet sourceWorksheet() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "ORDERS"));
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "OTHER"));
      DefaultVariableAssembly variable = new DefaultVariableAssembly(ws, "V");
      variable.setVariable(new AssetVariable("V"));
      ws.addAssembly(variable);
      ws.addAssembly(new DefaultNamedGroupAssembly(ws, "G"));

      when(securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                          eq(ResourceAction.ACCESS))).thenReturn(true);
      when(viewsheetService.getAssetRepository()).thenReturn(assetRepository);
      when(assetRepository.getSheet(any(), any(), anyBoolean(), any())).thenReturn(ws);
      return ws;
   }

   /** One field of table ORDERS from a worksheet source, no joins, building a table called name. */
   private static WorksheetConstructionModel model(String name) {
      WorksheetConstructionModel.SourceInfo source = new WorksheetConstructionModel.SourceInfo();
      source.setType("WORKSHEET");
      source.setPath("src");

      WorksheetConstructionModel.TableInfo table = new WorksheetConstructionModel.TableInfo();
      table.setName("ORDERS");
      table.setSource(source);

      WorksheetConstructionModel.QueryField field =
         new WorksheetConstructionModel.QueryField(table, "id");
      field.setVisible(true);

      WorksheetConstructionModel model = new WorksheetConstructionModel();
      model.setName(name);
      model.setFields(List.of(field));
      return model;
   }

   private void assertRefused(String name, String expectedHolder) throws Exception {
      Worksheet source = sourceWorksheet();
      Assembly holder = source.getAssembly(name);
      int before = source.getAssemblies().length;

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.generateWs(model(name), USER));

      assertTrue(ex.getMessage().contains("'" + name + "' already names a " + expectedHolder),
                 "got: " + ex.getMessage());
      assertEquals(before, source.getAssemblies().length, "the source worksheet must not change");
      assertSame(holder, source.getAssembly(name), "the holder must not be replaced");
      verify(assetRepository, never()).setSheet(any(), any(), any(), anyBoolean());
   }

   @Test
   void refusesNameHeldByVariable() throws Exception {
      assertRefused("V", "variable");
   }

   @Test
   void refusesNameHeldByNamedGroup() throws Exception {
      assertRefused("G", "named group");
   }

   /** Reusing the source table's own name would replace it with a mirror of itself. */
   @Test
   void refusesNameOfTheSourceTableItIsBuiltOn() throws Exception {
      assertRefused("ORDERS", "Physical");
   }

   @Test
   void refusesNameOfAnotherSourceTable() throws Exception {
      assertRefused("OTHER", "Physical");
   }

   @Test
   void refusesBlankName() throws Exception {
      sourceWorksheet();

      for(String name : new String[] { "", "  ", null }) {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.generateWs(model(name), USER));
         assertTrue(ex.getMessage().contains("name is required"), "got: " + ex.getMessage());
      }

      verify(assetRepository, never()).getSheet(any(), any(), anyBoolean(), any());
   }
}
