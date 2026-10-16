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
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.SliderVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.InputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.dispatch.CommandErrorException;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78579: {@code set_input_value} on a write-back input (writeBackDirectly) whose runtime row
 * is out of range reached the caller as the opaque "StyleBI-side bug" 500, and the refused value
 * was stored anyway (the input is set before the write-back guard throws).
 *
 * <p>Post-fix behaviour asserted here: the wiz path refuses up front with an
 * {@code IllegalArgumentException} naming the assembly, table and row (or column) and leaves the
 * input untouched; the decision is made against the worksheet the write itself uses, which for an
 * input inside an embedded viewsheet is the EMBEDDED viewsheet's worksheet. When only the sandbox
 * guard fires (pre-check bypassed), the capturing dispatcher turns it into a named
 * {@code CommandErrorException}.
 *
 * <p>The stack is the real {@code VSInputService} and the real {@code ViewsheetSandbox} write,
 * with {@code CoreLifecycleService.getSandbox} the real method and everything else a mock.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               InputValueServiceWriteBackTest.Config.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class InputValueServiceWriteBackTest {
   /** The post-write refresh of an embedded viewsheet looks up the data cache bean. */
   @Configuration
   static class Config {
      @Bean
      AssetDataCache assetDataCache() {
         return mock(AssetDataCache.class);
      }
   }

   private static final String TABLE = "T78579";
   private static final String SLIDER = "Slider1";
   private static final double PRIOR = 30;
   private static final double NEW = 40;

   // ── top-level input ──────────────────────────────────────────────────────

   @Test
   void outOfRangeDynamicRowIsRefusedByNameAndTheInputIsUnchanged() throws Exception {
      Stack s = Stack.topLevel();
      s.bind("=5", 5);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> s.setValue(SLIDER, NEW));
      assertNames(e, SLIDER, TABLE, "5");
      assertEquals(PRIOR, s.selected(), "the refused value must not be stored");
      assertEquals(3, s.data().getRowCount());
   }

   /**
    * Row L3 at unit level: whatever the refusal looks like, the refused value must not be stored.
    * Today the slider is set before the write-back guard throws (partial apply).
    */
   @Test
   void refusedWriteDoesNotStoreTheValue() throws Exception {
      Stack s = Stack.topLevel();
      s.bind("=5", 5);

      assertThrows(Exception.class, () -> s.setValue(SLIDER, NEW));
      assertEquals(PRIOR, s.selected(), "the refused value must not be stored");
   }

   /** Control (row L7): a valid row is accepted and the cell is written. */
   @Test
   void validRowIsWritten() throws Exception {
      Stack s = Stack.topLevel();
      s.bind("1", null);

      assertDoesNotThrow(() -> s.setValue(SLIDER, NEW));
      assertEquals(NEW, s.selected());
      assertEquals(40, ((Number) s.data().getObject(1, 1)).intValue());
   }

   /** Row L-C6: a column the table does not have is refused by name through the wiz path. */
   @Test
   void unknownColumnIsRefusedByNameAndTheInputIsUnchanged() throws Exception {
      Stack s = Stack.topLevel();
      s.bind("1", null);
      s.info().setColumnValue("nosuch");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> s.setValue(SLIDER, NEW));
      assertNames(e, SLIDER, TABLE, "nosuch");
      assertEquals(PRIOR, s.selected(), "the refused value must not be stored");
   }

   /** Control (row L8): writeBackDirectly with no column stays a silent no-op; the value is set. */
   @Test
   void nullColumnStaysANoOpAndTheValueIsStored() throws Exception {
      Stack s = Stack.topLevel();
      s.bind("1", null);
      s.info().setColumnValue(null);

      assertDoesNotThrow(() -> s.setValue(SLIDER, NEW));
      assertEquals(NEW, s.selected());
      assertEquals(10, ((Number) s.data().getObject(1, 1)).intValue());
   }

   /**
    * Row L11: a row expression that depends on the input itself is evaluated at the last refresh
    * (here: 1, valid), and both the pre-check and the write use that cached row -- so the write is
    * accepted at row 1. Control, green today.
    */
   @Test
   void selfReferencingRowUsesTheCachedRowForPreCheckAndWriteAlike() throws Exception {
      Stack s = Stack.topLevel();
      s.bind("=" + SLIDER + ".value", 1);

      assertDoesNotThrow(() -> s.setValue(SLIDER, NEW));
      assertEquals(40, ((Number) s.data().getObject(1, 1)).intValue());
   }

   /** Row L11, refusing side: the cached row (5) decides, not what the new value would compute. */
   @Test
   void selfReferencingRowWithAnInvalidCachedRowIsRefusedByName() throws Exception {
      Stack s = Stack.topLevel();
      s.bind("=" + SLIDER + ".value", 5);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> s.setValue(SLIDER, 2));
      assertNames(e, SLIDER, TABLE, "5");
      assertEquals(PRIOR, s.selected(), "the refused value must not be stored");
   }

   // ── embedded viewsheet input (rows L5 / L6) ──────────────────────────────

   /**
    * Row L5: the table exists only in the embedded viewsheet's worksheet. The refusal must be the
    * row refusal decided against THAT worksheet -- not a "not an embedded table" refusal, which
    * would mean the pre-check looked at the root viewsheet's worksheet.
    */
   @Test
   void embeddedInputOutOfRangeRowIsRefusedAgainstTheEmbeddedWorksheet() throws Exception {
      Stack s = Stack.embedded();
      s.bind("=5", 5);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> s.setValue("EVS." + SLIDER, NEW));
      assertNames(e, SLIDER, TABLE, "5");
      assertFalse(e.getMessage().contains("not an embedded"), e.getMessage());
      assertEquals(PRIOR, s.selected(), "the refused value must not be stored");
   }

   /** Row L6 (control): a valid row on the embedded input is accepted and written there. */
   @Test
   void embeddedInputValidRowIsWrittenToTheEmbeddedWorksheet() throws Exception {
      Stack s = Stack.embedded();
      s.bind("1", null);

      assertDoesNotThrow(() -> s.setValue("EVS." + SLIDER, NEW));
      assertEquals(40, ((Number) s.data().getObject(1, 1)).intValue());
   }

   // ── backstop (row L-M3) ──────────────────────────────────────────────────

   /**
    * Row L-M3: with the wiz pre-check bypassed (VSInputService called directly under the
    * capturing dispatcher), the sandbox refusal itself must surface as a CommandErrorException
    * carrying the row/table text -- and nothing dispatched earlier may replace that text.
    */
   @Test
   void sandboxRefusalBecomesANamedCommandErrorUnderTheCapturingDispatcher() throws Exception {
      Stack s = Stack.topLevel();
      s.bind("=5", 5);

      Exception e = assertThrows(Exception.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(s.principal, d -> {
            s.inputs.singleApplySelection("rt1", SLIDER, NEW, s.principal, d, null);
            return null;
         }));

      assertInstanceOf(CommandErrorException.class, e,
                       "expected CommandErrorException but was " + e.getClass().getName() +
                       ": " + e.getMessage());
      List<String> errors = ((CommandErrorException) e).getErrors();
      assertEquals(1, errors.size(), String.valueOf(errors));
      assertTrue(errors.get(0).contains("5") && errors.get(0).contains(TABLE), errors.get(0));
   }

   // ── harness ──────────────────────────────────────────────────────────────

   private static void assertNames(Exception e, String... parts) {
      for(String part : parts) {
         assertTrue(e.getMessage() != null && e.getMessage().contains(part),
                    "message should name '" + part + "': " + e.getMessage());
      }
   }

   private static final class Stack {
      final Viewsheet root;
      final Viewsheet owner; // the viewsheet holding the slider and the write-back table
      final SliderVSAssembly slider;
      final VSInputService inputs;
      final InputValueService service;
      final Principal principal = mock(Principal.class);

      static Stack topLevel() throws Exception {
         Viewsheet root = new Viewsheet();
         setBaseWorksheet(root, worksheetWithTable());
         root.setEntry(entry("root"));
         return new Stack(root, root);
      }

      /** Root VS whose own worksheet has NO table T78579; the embedded EVS's worksheet has it. */
      static Stack embedded() throws Exception {
         Viewsheet root = new Viewsheet();
         setBaseWorksheet(root, new Worksheet());
         root.setEntry(entry("root"));
         Viewsheet evs = new Viewsheet();
         setBaseWorksheet(evs, worksheetWithTable());
         evs.setEntry(entry("evs"));
         evs.createVSAssembly("EVS");
         root.addAssembly(evs);
         return new Stack(root, evs);
      }

      private Stack(Viewsheet root, Viewsheet owner) throws Exception {
         this.root = root;
         this.owner = owner;
         slider = new SliderVSAssembly(owner, SLIDER);
         owner.addAssembly(slider);
         info().setSelectedObject(PRIOR);

         ViewsheetSandbox box = new ViewsheetSandbox(
            root, AbstractSheet.SHEET_RUNTIME_MODE, null, false, root.getEntry());
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(root);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
         when(rvs.getID()).thenReturn("rt1");

         VSObjectService objects = mock(VSObjectService.class);
         when(objects.getRuntimeViewsheet(anyString(), any())).thenReturn(rvs);
         // refreshVS after a successful write needs a real change list
         when(objects.createList(anyBoolean(), any(), any(), any()))
            .thenAnswer(inv -> new ChangedAssemblyList());
         CoreLifecycleService lifecycle = mock(CoreLifecycleService.class);
         when(lifecycle.getSandbox(any(), anyString())).thenCallRealMethod();
         ViewsheetService viewsheets = mock(ViewsheetService.class);

         inputs = new VSInputService(
            objects, lifecycle, viewsheets, mock(VSObjectPropertyService.class),
            mock(VSDialogService.class), mock(VSTrapService.class),
            mock(DataRefModelFactoryService.class), mock(VSAssemblyInfoHandler.class),
            mock(VSColumnHandler.class), mock(QueryManagerService.class));

         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         when(sessions.mutate(anyString(), any(), any())).thenAnswer(inv -> {
            ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
            return CapturingCommandDispatcher.withCapturingDispatcher(principal, d -> {
               mutation.run(rvs, "rt1", d);
               return d.getWarnings();
            });
         });
         service = new InputValueService(sessions, inputs);
      }

      SliderVSAssemblyInfo info() {
         return (SliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      }

      /** Binds the slider to TABLE.qty with writeBackDirectly; runtimeRow != null fixes the row. */
      void bind(String rowValue, Integer runtimeRow) {
         InputVSAssemblyInfo info = info();
         info.setTableName(TABLE);
         info.setColumnValue("qty");
         info.setRowValue(rowValue);
         info.setWriteBackValue(true);

         if(runtimeRow != null) {
            DynamicValue row = info.getDynamicValues().stream()
               .filter(v -> rowValue.equals(v.getDValue()))
               .reduce((a, b) -> b) // rowValue is added after columnValue
               .orElseThrow();
            row.setRValue(runtimeRow);
         }

         assertEquals(runtimeRow == null ? Integer.parseInt(rowValue) : runtimeRow, info.getRow());
      }

      void setValue(String assembly, double value) throws Exception {
         service.setValue("tok", principal, assembly, List.of(value), null);
      }

      double selected() {
         return ((Number) info().getSelectedObject()).doubleValue();
      }

      XEmbeddedTable data() {
         return ((EmbeddedTableAssembly) owner.getOriginalWorksheet().getAssembly(TABLE))
            .getEmbeddedData();
      }
   }

   private static Worksheet worksheetWithTable() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly embedded = new EmbeddedTableAssembly(ws, TABLE);
      embedded.setEmbeddedData(new XEmbeddedTable(
         new String[]{ XSchema.STRING, XSchema.INTEGER },
         new Object[][] {
            { "state", "qty" },
            { "CA", 10 },
            { "NY", 20 }
         }));
      ws.addAssembly(embedded);
      return ws;
   }

   private static void setBaseWorksheet(Viewsheet vs, Worksheet ws) throws Exception {
      // private; it populates both the base and the original worksheet the write-back reads
      Method m = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      m.setAccessible(true);
      m.invoke(vs, ws);
   }

   private static AssetEntry entry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "test/InputValueServiceWriteBackTest/" + name, null,
                            OrganizationManager.getInstance().getCurrentOrgID());
   }
}
