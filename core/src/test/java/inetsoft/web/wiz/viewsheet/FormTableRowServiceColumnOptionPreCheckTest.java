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
import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.table.VSFormTableService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redmine #80812: {@code form_table_set_cell} refuses a value the column option rejects (Text
 * pattern, Date range) before the mutation starts, with only the configured message. Inside
 * {@code mutate} the same refusal arrived as an ERROR command that was relabelled "the edit may be
 * partially applied ... one undo step covers whatever was applied", after a no-op checkpoint.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormTableRowServiceColumnOptionPreCheckTest {
   private static final String MSG = "Names only contain letters: {0}";

   @Test
   void textPatternViolationIsRefusedBeforeTheMutation() throws Exception {
      Fixture f = new Fixture(new TextColumnOption("[A-Za-z ]{1,30}", MSG, true), "string",
                              "Router");

      Exception e = assertThrows(Exception.class, () -> f.setCell("Gadget#1"));

      assertEquals("Names only contain letters: Gadget#1", e.getMessage());
      assertFalse(e.getMessage().contains("partially applied"));
      f.assertMutationNeverStarted();
      assertEquals("Router", f.pending());
   }

   @Test
   void dateRangeViolationIsRefusedBeforeTheMutation() throws Exception {
      Fixture f = new Fixture(new DateColumnOption("2030-12-31", "2020-01-01", MSG, true), "date",
                              java.sql.Date.valueOf("2021-03-15"));

      Exception e = assertThrows(Exception.class, () -> f.setCell("2019-06-01"));

      assertTrue(e.getMessage().startsWith("Names only contain letters: "), e.getMessage());
      f.assertMutationNeverStarted();
      assertEquals(java.sql.Date.valueOf("2021-03-15"), f.pending());
   }

   @Test
   void aValueTheOptionAcceptsIsWritten() throws Exception {
      Fixture f = new Fixture(new TextColumnOption("[A-Za-z ]{1,30}", MSG, true), "string",
                              "Router");

      f.setCell("Gadget");

      assertEquals("Gadget", f.pending());
   }

   @Test
   void aDateInsideTheRangeIsWritten() throws Exception {
      Fixture f = new Fixture(new DateColumnOption("2030-12-31", "2020-01-01", MSG, true), "date",
                              java.sql.Date.valueOf("2021-03-15"));

      f.setCell("2022-06-01");

      assertEquals(java.sql.Date.valueOf("2022-06-01"), f.pending());
   }

   /** One Form column "Name", data row 1, with the given column option and worksheet type. */
   private static final class Fixture {
      Fixture(ColumnOption option, String wsType, Object seed) {
         Class<?> cls = seed.getClass();
         DefaultTableLens data = new DefaultTableLens(new Object[][] { { "Name" }, { seed } }) {
            @Override
            public Class<?> getColType(int col) {
               return cls;
            }
         };
         lens = new FormTableLens(data);

         FormRef ref = new FormRef();
         ref.setDataRef(new AttributeRef(null, "Name"));
         ref.setDataType(wsType);
         ref.setOption(option);
         ColumnSelection columns = new ColumnSelection();
         columns.addAttribute(ref);
         lens.setColumnSelection(columns);
         lens.setEdit(true);

         TableVSAssembly table = mock(TableVSAssembly.class);
         TableVSAssemblyInfo info = mock(TableVSAssemblyInfo.class);
         when(table.getVSAssemblyInfo()).thenReturn(info);
         when(table.getInfo()).thenReturn(info);
         when(info.clone(anyBoolean())).thenReturn(info);
         when(info.isForm()).thenReturn(true);
         when(info.getColumnSelection()).thenReturn(columns);

         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getAssembly(anyString())).thenReturn(table);

         ViewsheetSandbox box = mock(ViewsheetSandbox.class);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.isRuntime()).thenReturn(true);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

         ViewsheetService viewsheets = mock(ViewsheetService.class);
         CoreLifecycleService lifecycle = mock(CoreLifecycleService.class);
         sessions = mock(ViewsheetSessionService.class);

         try {
            when(box.getFormTableLens(anyString())).thenReturn(lens);
            when(viewsheets.getViewsheet(anyString(), any())).thenReturn(rvs);
            when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

            // CoreLifecycleService.sendMessage's own body
            doAnswer(inv -> {
               MessageCommand command = new MessageCommand();
               command.setMessage(inv.getArgument(0));
               command.setType(inv.getArgument(1));
               ((CommandDispatcher) inv.getArgument(2)).sendCommand(command);
               return null;
            }).when(lifecycle).sendMessage(any(), any(), any());

            doAnswer(inv -> {
               ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
               CapturingCommandDispatcher.withCapturingDispatcher(inv.getArgument(1), d -> {
                  mutation.run(rvs, "rt1", d);
                  return null;
               });
               return List.of();
            }).when(sessions).mutate(anyString(), any(Principal.class), any());
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }

         service = new FormTableRowService(sessions, new VSFormTableService(viewsheets, lifecycle));
      }

      void setCell(String value) throws Exception {
         service.setCell("tok", mock(Principal.class), "T", 0, 0, value, "");
      }

      /** The lens's pending value of the data row. */
      Object pending() {
         return lens.rows()[1].get(0);
      }

      void assertMutationNeverStarted() throws Exception {
         verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
      }

      private final FormTableLens lens;
      private final ViewsheetSessionService sessions;
      private final FormTableRowService service;
   }
}
