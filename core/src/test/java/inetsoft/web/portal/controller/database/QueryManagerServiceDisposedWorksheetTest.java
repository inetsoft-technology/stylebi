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

import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.ColumnCache;
import inetsoft.web.composer.model.ws.SQLQueryDialogModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80903, RuntimeWorksheet.dispose() nulls the worksheet (tab closed, session expired), so
 * opening the edit query dialog on a disposed sheet failed with a NullPointerException
 * ("Cannot invoke Worksheet.getAssemblies() because ws is null") in getVariableList. The dialog
 * now reads the worksheet once and reports a disposed sheet as an expired sheet.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceDisposedWorksheetTest {
   private static final String TABLE = "T";

   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private JDBCDataSource ds;
   private final Principal principal = mock(Principal.class);

   @BeforeEach
   void setUp() throws Exception {
      ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("ds");
      when(ds.getRuntimeProductName()).thenReturn("hsql");
      rqs = mock(RuntimeQueryService.class);
      SecurityEngine security = mock(SecurityEngine.class);
      XRepository repository = mock(XRepository.class);
      when(repository.getDataSourceFullNames()).thenReturn(new String[0]);
      when(security.checkPermission(any(), any(ResourceType.class), any(String.class),
                                    any(ResourceAction.class))).thenReturn(true);
      qms = new QueryManagerService(rqs, repository, mock(DataSourceService.class), security,
                                    mock(ColumnCache.class));
      when(rqs.createRuntimeQuery(any(), any(), any(), any())).thenAnswer(inv -> {
         JDBCQuery query = inv.<JDBCQuery>getArgument(1).clone();
         query.setDataSource(ds);
         return new RuntimeQueryService.RuntimeXQuery(query, "rq-80903", "ds");
      });
   }

   // the worksheet is already disposed when the dialog is requested
   @Test
   void disposedWorksheetIsReportedAsExpired() {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getID()).thenReturn("ws-80903-1");
      when(rws.getWorksheet()).thenReturn(null);

      assertThrows(ExpiredSheetException.class,
                   () -> qms.getSqlQueryDialogModel(rws, TABLE, "ds", principal));
   }

   // the worksheet is disposed (by another request) while the dialog model is being built, the
   // variable list must come from the worksheet read at the start instead of a second read
   @Test
   void worksheetDisposedWhileBuildingModelDoesNotFail() throws Exception {
      JDBCQuery query = new JDBCQuery();
      query.setName(TABLE);
      query.setDataSource(ds);
      query.setSQLDefinition(new UniformSQL());

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly assembly = new SQLBoundTableAssembly(ws, TABLE);
      ((SQLBoundTableAssemblyInfo) assembly.getTableInfo()).setQuery(query);
      assembly.setAdvancedEditing(true);
      ws.addAssembly(assembly);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getWorksheet()).thenReturn(ws).thenReturn(null);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getVariableTable()).thenReturn(new VariableTable());

      SQLQueryDialogModel model = assertDoesNotThrow(
         () -> qms.getSqlQueryDialogModel(rws, TABLE, "ds", principal));
      assertNotNull(model);
      assertEquals(0, model.getVariableNames().length);
   }
}
