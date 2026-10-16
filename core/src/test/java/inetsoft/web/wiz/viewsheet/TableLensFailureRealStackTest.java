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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.util.script.ScriptException;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
import inetsoft.web.composer.vs.dialog.HighlightDialogService;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.wiz.binding.TableBindingMutator;
import inetsoft.web.wiz.binding.TableBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78406 (round 2): the table writes that bypass {@code apply()} relay the script failure
 * {@code mutate} captured, and every wiz reader of a table's rendered lens turns "the table did
 * not render" into a named {@link IllegalArgumentException} instead of an NPE, an
 * {@link IllegalStateException} or a raw {@link ScriptException}. Runs the real services on the
 * real stack (EmbeddedVS1, a table whose JS pre-condition throws) and covers both outcomes the
 * sandbox produces for that table: the first read after the data map was cleared rethrows the
 * {@code ScriptException}, every later read returns the cached {@code null}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class TableLensFailureRealStackTest {
   // -- the mechanism ---------------------------------------------------------

   @Test
   void aFailedTableThrowsOnTheFirstReadThenReadsAsNull() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();

      assertThrows(ScriptException.class, () -> box.getVSTableLens(TABLE, false));
      assertNull(box.getVSTableLens(TABLE, false));
   }

   @Test
   void theGuardNamesTheTableForBothOutcomes() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);

      IllegalArgumentException first = assertThrows(IllegalArgumentException.class,
         () -> require(rvs));
      assertTrue(first.getMessage().contains("'" + TABLE + "' did not render"),
                 first.getMessage());
      assertTrue(first.getMessage().contains("undefinedFn"), first.getMessage());

      IllegalArgumentException cached = assertThrows(IllegalArgumentException.class,
         () -> require(rvs));
      assertTrue(cached.getMessage().contains("'" + TABLE + "' did not render"),
                 cached.getMessage());
      // The cached read has no ScriptException to quote, so it must not claim one.
      assertFalse(cached.getMessage().contains("undefinedFn"), cached.getMessage());
   }

   @Test
   void theGuardReturnsTheLensWhenTheTableRenders() throws Exception {
      assertNotNull(require(open()));
   }

   @Test
   void lensOrNullReadsAFailedTableAsNullForBothOutcomes() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();

      assertNull(TableLensGuard.lensOrNull(box, TABLE, false));
      assertNull(TableLensGuard.lensOrNull(box, TABLE, false));
   }

   @Test
   void aFailureThatIsNotAScriptFailureKeepsItsOwnHandling() throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      IllegalStateException boom = new IllegalStateException("boom");
      when(box.getVSTableLens(anyString(), anyBoolean())).thenThrow(boom);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn(TABLE);

      assertSame(boom, assertThrows(IllegalStateException.class,
         () -> TableLensGuard.requireRenderedLens(rvs, assembly, TABLE, "x")));
      assertSame(boom, assertThrows(IllegalStateException.class,
         () -> TableLensGuard.lensOrNull(box, TABLE, false)));
   }

   @Test
   void aScriptFailureWrappedInAnotherExceptionIsStillFound() throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getVSTableLens(anyString(), anyBoolean()))
         .thenThrow(new RuntimeException("wrapper", new ScriptException("inner failure")));

      assertNull(TableLensGuard.lensOrNull(box, TABLE, false));
   }

   // -- set_column_labels relays the warning ----------------------------------

   @Test
   void setColumnLabelsRelaysTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);

      TableBindingService.TableWrite write = labelsService(rvs)
         .setColumnLabels("tok", principal(), TABLE, Map.of("type", "Kind"), null);

      assertEquals(1, write.warnings().size(), write.warnings().toString());
      assertTrue(write.warnings().get(0).startsWith(
         BaseTableService.SCRIPT_FAILURE_PREFIX + TABLE), write.warnings().get(0));
      assertTrue(write.warnings().get(0).contains("undefinedFn"), write.warnings().get(0));
   }

   @Test
   void setColumnLabelsIsSilentWhenTheScriptIsFine() throws Exception {
      TableBindingService.TableWrite write = labelsService(open())
         .setColumnLabels("tok", principal(), TABLE, Map.of("type", "Kind"), null);

      assertEquals(List.of("type -> Kind"), write.applied());
      assertEquals(List.of(), write.warnings());
   }

   // -- set_column_widths -----------------------------------------------------

   @Test
   void setColumnWidthsOnAFailedTableIsANamedRefusalForBothOutcomes() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      TableBindingService service = widthsService(rvs);

      for(int attempt = 0; attempt < 2; attempt++) {
         IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> service.setColumnWidths("tok", principal(), TABLE, Map.of("type", 120.0)));
         assertTrue(thrown.getMessage().contains("'" + TABLE + "' did not render"),
                    thrown.getMessage());
      }
   }

   @Test
   void setColumnWidthsOnAHealthyTableAppliesAndRelaysNothing() throws Exception {
      RuntimeViewsheet rvs = open();
      VSTableLens lens = rvs.getViewsheetSandbox().orElseThrow().getVSTableLens(TABLE, false);
      String header = String.valueOf(lens.getObject(0, 0));

      TableBindingService.TableWrite write = widthsService(rvs)
         .setColumnWidths("tok", principal(), TABLE, Map.of(header, 120.0));

      assertEquals(List.of(header + " -> 120px"), write.applied());
      assertEquals(List.of(), write.warnings());
   }

   // -- highlights ------------------------------------------------------------

   @Test
   void listHighlightsOnAFailedTableIsANamedRefusalForBothOutcomes() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      HighlightDialogService dialogs = mock(HighlightDialogService.class);
      AssemblyHighlightService service = new AssemblyHighlightService(seam(rvs), dialogs);

      for(int attempt = 0; attempt < 2; attempt++) {
         IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> service.list("tok", principal(), TABLE, null));
         assertTrue(thrown.getMessage().contains("'" + TABLE + "' did not render"),
                    thrown.getMessage());
      }

      verifyNoInteractions(dialogs);
   }

   @Test
   void setHighlightOnAFailedTableIsANamedRefusalForBothOutcomes() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      HighlightDialogService dialogs = mock(HighlightDialogService.class);
      AssemblyHighlightService service = new AssemblyHighlightService(seam(rvs), dialogs);
      AssemblyHighlightService.Highlight highlight = new AssemblyHighlightService.Highlight(
         "H", "#FF0000", null, null, List.of(), false);

      for(int attempt = 0; attempt < 2; attempt++) {
         assertTrue(assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", principal(), TABLE, null, highlight, false, ""))
                       .getMessage().contains("did not render"));
      }

      verifyNoInteractions(dialogs);
   }

   /** A fresh runtime, so the first delete is the one that meets the first-read throw. */
   @Test
   void deleteHighlightOnAFailedTableIsANamedRefusalForBothOutcomes() throws Exception {
      RuntimeViewsheet rvs = open();
      addFailingExpressionCondition(rvs);
      HighlightDialogService dialogs = mock(HighlightDialogService.class);
      AssemblyHighlightService service = new AssemblyHighlightService(seam(rvs), dialogs);

      for(int attempt = 0; attempt < 2; attempt++) {
         assertTrue(assertThrows(IllegalArgumentException.class,
            () -> service.delete("tok", principal(), TABLE, null, "H", ""))
                       .getMessage().contains("did not render"));
      }

      verifyNoInteractions(dialogs);
   }

   // -- seams -----------------------------------------------------------------

   private VSTableLens require(RuntimeViewsheet rvs) throws Exception {
      TableVSAssembly table = (TableVSAssembly) rvs.getViewsheet().getAssembly(TABLE);
      return TableLensGuard.requireRenderedLens(rvs, table, TABLE, "its columns cannot be resolved");
   }

   /** The real {@code setBinding} tail that captures the failure: load the table's data. */
   private TableBindingService labelsService(RuntimeViewsheet rvs) throws Exception {
      VSBindingModelService bindings = mock(VSBindingModelService.class);
      when(bindings.setBinding(anyString(), any(), any(), any())).thenAnswer(inv -> {
         BaseTableService.loadTableData(rvs, TABLE, 0, 0, 100, null, inv.getArgument(3));
         return null;
      });

      return tableService(rvs, bindings);
   }

   private TableBindingService widthsService(RuntimeViewsheet rvs) throws Exception {
      return tableService(rvs, mock(VSBindingModelService.class));
   }

   private TableBindingService tableService(RuntimeViewsheet rvs, VSBindingModelService bindings)
      throws Exception
   {
      TableBindingModel model = new TableBindingModel();
      TableBindingMutator.setShelf(
         model, "details", List.of(new FieldRef("type", "dimension", null, null, null)));
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);

      return new TableBindingService(
         seam(rvs), binding, bindings, mock(DataRefModelFactoryService.class),
         mock(HideColumnsDialogService.class), mock(VSAssemblyInfoHandler.class));
   }

   /** Does around the mutation what {@code mutate} does: run it under the capturing dispatcher. */
   private static ViewsheetSessionService seam(RuntimeViewsheet rvs) throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any())).thenReturn(rvs);
      when(sessions.mutate(anyString(), any(), any())).thenAnswer(inv -> {
         Principal agent = inv.getArgument(1);
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(agent, dispatcher -> {
            mutation.run(rvs, rvs.getID(), dispatcher);
            return dispatcher.getWarnings();
         });
      });
      return sessions;
   }

   private static void addFailingExpressionCondition(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      TableVSAssembly table = (TableVSAssembly) box.getViewsheet().getAssembly(TABLE);
      DataRef type = table.getColumnSelection().getAttribute("type");
      assertNotNull(type, "type column");

      ExpressionValue expression = new ExpressionValue();
      expression.setExpression("undefinedFn(1)");
      expression.setType(ExpressionValue.JAVASCRIPT);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setType(XSchema.STRING);
      cond.addValue(expression);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(type, cond, 0));
      table.setPreConditionList(list);
      box.resetDataMap(TABLE);
   }

   private RuntimeViewsheet open() {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      user = rvs.getUser();
      return rvs;
   }

   private XPrincipal principal() {
      return (XPrincipal) user;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private Principal user;
   private static final String TABLE = "TableView1";
}
