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
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.model.WorksheetTableRequest;
import inetsoft.web.wiz.model.WorksheetTableResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78881: a /ws/table mirror table into an existing worksheet fails naming the problem when
 * its base is not a table, or when its name is taken by an assembly of another kind -- and the
 * failed table's rollback leaves that existing assembly in place.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceMirrorBase78881Test {
   private static final Principal USER = mock(Principal.class);

   @Test
   void aMirrorOverAVariableFailsNamingItsKind() throws Exception {
      Worksheet ws = worksheet();

      WorksheetTableResponse result = createMirror(ws, "M", "V1");

      assertFalse(result.isSuccess());
      assertTrue(result.getErrorMessage().contains("'V1' is a variable, not a table"),
                 result.getErrorMessage());
      assertNull(ws.getAssembly("M"));
      assertInstanceOf(DefaultVariableAssembly.class, ws.getAssembly("V1"));
   }

   @Test
   void aMirrorNamedLikeAVariableFailsAndKeepsTheVariable() throws Exception {
      Worksheet ws = worksheet();

      WorksheetTableResponse result = createMirror(ws, "V1", "T");

      assertFalse(result.isSuccess());
      assertTrue(result.getErrorMessage().contains("'V1' already names a variable"),
                 result.getErrorMessage());
      assertInstanceOf(DefaultVariableAssembly.class, ws.getAssembly("V1"),
                       "the failed table's rollback removed the existing variable");
   }

   /** A worksheet holding a table T and a variable V1. */
   private static Worksheet worksheet() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "T"));
      DefaultVariableAssembly variable = new DefaultVariableAssembly(ws, "V1");
      variable.setVariable(new AssetVariable("V1"));
      ws.addAssembly(variable);
      return ws;
   }

   private static WorksheetTableResponse createMirror(Worksheet ws, String name, String base)
      throws Exception
   {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository repository = mock(AssetRepository.class);
      when(viewsheetService.getAssetRepository()).thenReturn(repository);
      when(repository.getSheet(any(), any(), anyBoolean(), any())).thenReturn(ws);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(USER), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      WorksheetTableService service = new WorksheetTableService(
         viewsheetService, mock(MetadataApiService.class), mock(InnerJoinService.class),
         mock(LayoutGraphService.class), mock(QueryManagerService.class),
         mock(XRepository.class), new ObjectMapper(), mock(DataSourceService.class),
         securityEngine);

      String worksheetId = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "ws78881", null).toIdentifier();
      WorksheetTableRequest request = new ObjectMapper().readValue(
         "{ \"worksheetId\": \"" + worksheetId + "\", \"tables\": [ { \"tableName\": \"" + name +
         "\", \"tableType\": \"mirror table\", \"baseTables\": [ \"" + base + "\" ] } ] }",
         WorksheetTableRequest.class);
      var tables = service.createTables(request, USER).getTables();
      assertEquals(1, tables.size());
      return tables.get(0);
   }
}
