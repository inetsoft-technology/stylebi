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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redmine #79324, real stack below the plugin: the real {@link FormTableRowService#setCell} drives
 * the real {@code VSFormTableService.changeFormInput} against a real {@link FormTableLens} for a
 * Date, Timestamp and Time column. Before the fix {@code 2026-02-30} was stored as 2026-02-28,
 * {@code 2026-13-01} as 2027-01-01, and {@code abc} was refused with the option's message
 * unreplaced.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormTableRowServiceTemporalRealStackTest {
   private static final String MSG = "Value {0} bad";

   @ParameterizedTest(name = "Date refuses [{0}]")
   @ValueSource(strings = { "2026-02-30", "2026-02-31", "2026-04-31", "2025-02-29", "2026-13-01",
                            "2026-02-30 10:00:00", "abc", "20260230", "13/01/2026", "02/30/2026",
                            "2026-03-15 25:00:00", " ", "Feb 30, 2026", "Feb 31, 2026" })
   void dateColumnRefusesWhatIsNotARealDate(String entered) throws Exception {
      Fixture f = new Fixture("date", true);

      Exception e = assertThrows(Exception.class, () -> f.setCell(entered));

      assertEquals("Value " + entered + " bad", e.getMessage()); // {0} = text as entered
      assertEquals(java.sql.Date.valueOf("2026-01-16"), f.pending());
      f.assertNoCheckpoint();
   }

   @ParameterizedTest(name = "Date accepts [{0}] as {1}")
   @ValueSource(strings = { "2026-03-15|2026-03-15", "2026-3-5|2026-03-05",
                            " 2026-03-15 |2026-03-15", "2024-02-29|2024-02-29",
                            "01/02/2026|2026-01-02", "2026-03-15 10:30:00|2026-03-15",
                            "Mar 05, 2026|2026-03-05", "March 5, 2026|2026-03-05" })
   void dateColumnStillAcceptsARealDate(String pair) throws Exception {
      String[] p = pair.split("\\|");
      Fixture f = new Fixture("date", true);

      f.setCell(p[0]);

      assertEquals(p[1], f.pending().toString());
   }

   @Test
   void aDateOptionOnATextLikeLensColumnIsStillChecked() throws Exception {
      Fixture f = new Fixture("string", true);

      assertThrows(Exception.class, () -> f.setCell("2026-02-30"));
      assertEquals("2026-01-16", String.valueOf(f.pending()));
   }

   @ParameterizedTest(name = "Timestamp refuses [{0}]")
   @ValueSource(strings = { "2026-02-30 10:30:00", "2026-03-15 25:00:00", "2026-03-15 10:61:00",
                            "abc", "2026-13-01 10:00:00" })
   void timestampColumnRefusesWhatIsNotARealInstant(String entered) throws Exception {
      Fixture f = new Fixture("timeInstant", false);

      Exception e = assertThrows(Exception.class, () -> f.setCell(entered));

      assertEquals("Value " + entered + " bad", e.getMessage());
      f.assertNoCheckpoint();
   }

   @Test
   void timestampColumnStillAcceptsARealInstant() throws Exception {
      Fixture f = new Fixture("timeInstant", false);

      f.setCell("2026-03-15 10:30:00");

      assertEquals(java.sql.Timestamp.valueOf("2026-03-15 10:30:00"), f.pending());
   }

   @ParameterizedTest(name = "Time refuses [{0}]")
   @ValueSource(strings = { "25:00:00", "10:61", "abc", "2026-03-15", "01/02/2026" })
   void timeColumnRefusesWhatIsNotAClockTime(String entered) throws Exception {
      Fixture f = new Fixture("time", false);

      Exception e = assertThrows(Exception.class, () -> f.setCell(entered));

      assertEquals("Value " + entered + " bad", e.getMessage());
      f.assertNoCheckpoint();
   }

   @ParameterizedTest(name = "Time accepts [{0}]")
   @ValueSource(strings = { "10:30:00", "9:05:07", "10:30:00.250" })
   void timeColumnStillAcceptsAClockTime(String entered) throws Exception {
      Fixture f = new Fixture("time", false);

      f.setCell(entered);

      assertInstanceOf(java.sql.Time.class, f.pending());
   }

   @Test
   void nullAndEmptyStillClearTheCell() throws Exception {
      Fixture f = new Fixture("date", true);

      f.setCell(null);

      assertNull(f.pending());
   }

   /** One Form column "D", data row 2026-01-16 (or the matching Timestamp/Time). */
   private static final class Fixture {
      Fixture(String wsType, boolean dateOption) {
         Object seed = switch(wsType) {
            case "timeInstant" -> java.sql.Timestamp.valueOf("2026-01-16 09:00:00");
            case "time" -> java.sql.Time.valueOf("09:00:00");
            case "string" -> "2026-01-16";
            default -> java.sql.Date.valueOf("2026-01-16");
         };
         Class<?> cls = seed.getClass();
         DefaultTableLens data = new DefaultTableLens(new Object[][] { { "D" }, { seed } }) {
            @Override
            public Class<?> getColType(int col) {
               return cls;
            }
         };
         lens = new FormTableLens(data);

         FormRef ref = new FormRef();
         ref.setDataRef(new AttributeRef(null, "D"));
         ref.setDataType(wsType);
         ref.setOption(dateOption ? new DateColumnOption("2100-12-31", "2000-01-01", MSG, true)
                          : new TextColumnOption(null, MSG, true));
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

      /** A refusal that runs before {@code mutate} opens no undo checkpoint. */
      void assertNoCheckpoint() throws Exception {
         verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
      }

      private final FormTableLens lens;
      private final ViewsheetSessionService sessions;
      private final FormTableRowService service;
   }
}
