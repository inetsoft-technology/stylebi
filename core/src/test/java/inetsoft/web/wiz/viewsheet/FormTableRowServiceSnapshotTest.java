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
package inetsoft.web.wiz.viewsheet;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.viewsheet.controller.table.VSFormTableService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Font;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78067, end to end below the HTTP layer: the real {@link FormTableRowService}, the real
 * {@link VSFormTableService} (addRow/deleteRows/changeFormInput bodies), the real
 * {@code BaseTableService.loadTableData} building the {@code LoadTableDataCommand}, the real
 * {@link CapturingCommandDispatcher}, and a real {@link FormTableLens}/{@link VSTableLens} over a
 * header + 3 data row table. Only the sandbox/session lookups are mocked.
 *
 * <p>{@code tableCells} in the real command start at lens row 0 (the header), while every input
 * index is a 0-based data row; the returned {@code rows} must use the input's space, with the
 * header surfaced as {@code columns}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormTableRowServiceSnapshotTest {
   @BeforeEach
   void setUp() throws Exception {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.initDefaultFormat();
      vs.addAssembly(table);

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setForm(true);
      info.setInsert(true);
      info.setDel(true);
      info.setEdit(true);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(formRef("ID", XSchema.INTEGER));
      cols.addAttribute(formRef("NAME", XSchema.STRING));
      info.setColumnSelection(cols);

      form = new FormTableLens(new DefaultTableLens(new Object[][] {
         { "ID", "NAME" },
         { 1, "Alice" },
         { 2, "Bob" },
         { 3, "Carol" }
      }));
      form.setEdit(true);
      form.setColumnSelection(cols);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getFormTableLens("Table1")).thenReturn(form);
      // a fresh VSTableLens per load, as resetDataMap would force in production. The attribute
      // lens stands in for the format pipeline, which supplies a font and line wrap in production
      // (FormTableLens itself returns a null font).
      when(box.getVSTableLens(eq("Table1"), anyBoolean())).thenAnswer(inv -> {
         AttributeTableLens formatted = new AttributeTableLens(form);
         formatted.setFont(new Font("Dialog", Font.PLAIN, 10));
         formatted.setLineWrap(false);
         return new VSTableLens(formatted);
      });
      when(box.getID()).thenReturn("box1");

      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.isRuntime()).thenReturn(true);

      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getViewsheet(anyString(), any())).thenReturn(rvs);
      VSFormTableService forms = new VSFormTableService(engine, mock(CoreLifecycleService.class));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(inv -> {
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(user, d -> {
            mutation.run(rvs, "rt1", d);
            return d.getWarnings();
         });
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      service = new FormTableRowService(sessions, forms);
   }

   @Test
   void insertAtDataIndexZeroReturnsTheBlankRowAtRowsZero() throws Exception {
      Map<String, Object> result = service.insertRow("tok", user, "Table1", 0, false, "");

      List<?> rows = (List<?>) result.get("rows");
      assertEquals(4, result.get("rowCount"));
      assertEquals(4, rows.size(), rows.toString());
      assertEquals(Arrays.asList(null, null), rows.get(0), rows.toString());
      assertEquals(List.of(1, "Alice"), rows.get(1), rows.toString());
      assertEquals(List.of(3, "Carol"), rows.get(3), rows.toString());
      assertEquals(List.of("ID", "NAME"), result.get("columns"));
   }

   @Test
   void setCellValueAppearsAtTheSameRowIndex() throws Exception {
      Map<String, Object> result = service.setCell("tok", user, "Table1", 1, 1, "Bobby", "");

      List<?> rows = (List<?>) result.get("rows");
      assertEquals(3, result.get("rowCount"));
      assertEquals(3, rows.size(), rows.toString());
      assertEquals(List.of(2, "Bobby"), rows.get(1), rows.toString());
      assertEquals(List.of(1, "Alice"), rows.get(0), rows.toString());
      assertEquals(List.of("ID", "NAME"), result.get("columns"));
   }

   @Test
   void deleteDataRowZeroLeavesTheNextDataRowAtRowsZero() throws Exception {
      Map<String, Object> result = service.deleteRows("tok", user, "Table1", List.of(0), "");

      List<?> rows = (List<?>) result.get("rows");
      assertEquals(2, result.get("rowCount"));
      assertEquals(List.of(List.of(2, "Bob"), List.of(3, "Carol")), rows);
      assertEquals(List.of("ID", "NAME"), result.get("columns"));
   }

   private static FormRef formRef(String name, String type) {
      ColumnRef column = new ColumnRef(new AttributeRef(null, name));
      column.setDataType(type);
      FormRef ref = FormRef.toFormRef(column);
      ref.getOption().setForm(true);
      return ref;
   }

   private final Principal user = () -> "admin";
   private FormTableLens form;
   private RuntimeViewsheet rvs;
   private FormTableRowService service;
}
