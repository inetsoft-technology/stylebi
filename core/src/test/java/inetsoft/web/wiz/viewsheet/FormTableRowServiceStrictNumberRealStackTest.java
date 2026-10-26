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
import inetsoft.uql.util.XEmbeddedTable;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redmine #79189, real stack below the plugin: the real {@link FormTableRowService#setCell} drives
 * the real {@code VSFormTableService.changeFormInput} against a real {@link FormTableLens}, real
 * {@code FormRef}/{@code ColumnOption}s and a real {@link CapturingCommandDispatcher}; "apply" is
 * what {@code ViewsheetSandbox.writeBackFormData} does per cell, a real
 * {@link XEmbeddedTable#setObject}. Mocked: the session service (it only runs the mutation inside
 * the real capturing dispatcher), the runtime viewsheet/sandbox, and the lifecycle service's
 * four-line {@code sendMessage}.
 *
 * <p>Before the fix an Integer column accepted {@code 2.5} (pending text {@code "2.5"}, apply wrote
 * 2), {@code NaN}/{@code 50%}/{@code 2.5d} the same way, and a Long column silently dropped even
 * {@code "2.0"}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormTableRowServiceStrictNumberRealStackTest {
   private static final String MSG = "Qty {0} bad";

   /** (option type, worksheet column type) pairs where the cell must hold an integral number. */
   static Stream<Arguments> integralPairs() {
      return Stream.of(
         Arguments.of(ColumnOption.INTEGER, "integer"),
         Arguments.of(ColumnOption.INTEGER, "long"),
         Arguments.of(ColumnOption.FLOAT, "integer"),
         Arguments.of(ColumnOption.TEXT, "integer"),
         Arguments.of(ColumnOption.TEXT, "long"));
   }

   static Stream<Arguments> refusedCases() {
      List<String> bad = List.of("2.5", "NaN", "50%", "2.5d", "abc", "2,5", "999.9", "0x10", " ");
      return integralPairs().flatMap(p -> bad.stream().map(
         v -> Arguments.of(p.get()[0], p.get()[1], v)));
   }

   static Stream<Arguments> canonicalCases() {
      List<String[]> ok = List.of(new String[] { "2.0", "2" }, new String[] { "1e2", "100" },
                                  new String[] { " 3 ", "3" });
      return integralPairs().flatMap(p -> ok.stream().map(
         v -> Arguments.of(p.get()[0], p.get()[1], v[0], v[1])));
   }

   @ParameterizedTest(name = "{0}/{1} refuses [{2}]")
   @MethodSource("refusedCases")
   void refusesNonIntegralTextAndLeavesTheCellUntouched(String optionType, String wsType,
                                                        String entered) throws Exception
   {
      Fixture f = new Fixture(optionType, wsType);

      Exception e = assertThrows(Exception.class, () -> f.setCell(entered));

      assertEquals("Qty " + entered + " bad", e.getMessage()); // {0} = text as entered
      assertEquals(5, ((Number) f.pending()).intValue());
      assertEquals(5, ((Number) f.apply()).intValue());
   }

   @ParameterizedTest(name = "{0}/{1} accepts [{2}] as {3}")
   @MethodSource("canonicalCases")
   void acceptsIntegralTextAsItsCanonicalForm(String optionType, String wsType, String entered,
                                              String canonical) throws Exception
   {
      Fixture f = new Fixture(optionType, wsType);

      f.setCell(entered);

      assertEquals(canonical, f.pending());
      Object written = f.apply();
      assertEquals("long".equals(wsType) ? Long.class : Integer.class, written.getClass());
      assertEquals(Long.parseLong(canonical), ((Number) written).longValue());
   }

   @ParameterizedTest(name = "{0}/{1} nulls the cell")
   @MethodSource("integralPairs")
   void nullStillClearsTheCell(String optionType, String wsType) throws Exception {
      Fixture f = new Fixture(optionType, wsType);

      f.setCell(null);

      assertNull(f.pending());
      assertNull(f.apply());
   }

   @ParameterizedTest(name = "{0}/{1} still range-checks 1000")
   @MethodSource("integralPairs")
   void theOptionRangeStillApplies(String optionType, String wsType) throws Exception {
      if(ColumnOption.TEXT.equals(optionType)) {
         return; // a Text option has no range
      }

      Fixture f = new Fixture(optionType, wsType);

      Exception e = assertThrows(Exception.class, () -> f.setCell("1000"));

      assertEquals("Qty 1000 bad", e.getMessage());
      assertEquals(5, ((Number) f.pending()).intValue());
   }

   @ParameterizedTest(name = "Float/double refuses [{0}]")
   @ValueSource(strings = { "NaN", "50%", "2.5d", "abc", "-5" })
   void floatColumnRefusesWhatIsNotAFiniteNumber(String entered) throws Exception {
      Fixture f = new Fixture(ColumnOption.FLOAT, "double");

      Exception e = assertThrows(Exception.class, () -> f.setCell(entered));

      assertEquals("Qty " + entered + " bad", e.getMessage());
      assertEquals(5.0, ((Number) f.pending()).doubleValue());
   }

   @Test
   void floatColumnKeepsAFraction() throws Exception {
      Fixture f = new Fixture(ColumnOption.FLOAT, "double");

      f.setCell("2.5");

      assertEquals("2.5", f.pending());
      assertEquals(2.5, ((Number) f.apply()).doubleValue());
   }

   /** One Form column "Qty", data row 5, with the given column option and worksheet type. */
   private static final class Fixture {
      Fixture(String optionType, String wsType) {
         Object seed = switch(wsType) {
            case "long" -> 5L;
            case "double" -> 5.0d;
            default -> 5;
         };
         Class<?> cls = seed.getClass();
         DefaultTableLens data = new DefaultTableLens(new Object[][] { { "Qty" }, { seed } }) {
            @Override
            public Class<?> getColType(int col) {
               return cls;
            }
         };
         lens = new FormTableLens(data);

         FormRef ref = new FormRef();
         ref.setDataRef(new AttributeRef(null, "Qty"));
         ref.setDataType(wsType);
         ColumnOption option = switch(optionType) {
            case ColumnOption.INTEGER -> new IntegerColumnOption(999, 0, MSG, true);
            case ColumnOption.FLOAT -> new FloatColumnOption("999", "0", MSG, true);
            default -> new TextColumnOption(null, MSG, true);
         };
         ref.setOption(option);
         ColumnSelection columns = new ColumnSelection();
         columns.addAttribute(ref);
         lens.setColumnSelection(columns);
         lens.setEdit(true);
         embedded = new XEmbeddedTable(new String[] { wsType },
                                       new Object[][] { { "Qty" }, { seed } });

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
         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

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

      /** writeBackFormData's per-cell write into the worksheet's embedded table, then read back. */
      Object apply() {
         embedded.setObject(1, 0, lens.rows()[1].get(0));
         return embedded.getObject(1, 0);
      }

      private final FormTableLens lens;
      private final XEmbeddedTable embedded;
      private final FormTableRowService service;
   }
}
