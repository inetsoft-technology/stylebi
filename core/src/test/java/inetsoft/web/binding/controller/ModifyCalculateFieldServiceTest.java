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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.BaseLayout;
import inetsoft.report.CellBinding;
import inetsoft.report.TableCellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.OutputVSAssembly;
import inetsoft.uql.viewsheet.ScalarBindingInfo;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.CalculateRefModel;
import inetsoft.web.binding.event.ModifyCalculateFieldEvent;
import inetsoft.web.binding.event.RefreshBindingTreeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.XRepository;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import inetsoft.web.viewsheet.controller.VSRefreshController;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.command.RefreshWizardTreeTriggerCommand;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.recommender.execution.WizardDataExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the NullPointerException a session-less caller (the wiz agent surface's
 * {@code CalcFieldAgentService}) hit live: {@code modifyCalculateField}'s non-wizard branch used
 * to ask {@code VSBindingTreeController} to refresh the binding tree, which re-derives the
 * runtime id from the WebSocket-session-scoped {@code RuntimeViewsheetRef} instead of using the
 * id already passed into this very method -- null for any caller with no live WebSocket session,
 * which then blew up in {@code VSBindingTreeControllerServiceProxy}'s cluster-affinity routing
 * before the real binding-tree read ever ran.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ModifyCalculateFieldServiceTest {
   /**
    * The exact shape CalcFieldAgentService drives live: create a new, non-wizard, non-SQL,
    * detail-level calc field with no target assembly named. Before the fix this threw
    * NullPointerException("Ouch! Argument cannot be null: key") from deep inside
    * VSBindingTreeControllerServiceProxy's cluster routing, because VSBindingTreeController
    * re-derived a null runtime id from RuntimeViewsheetRef rather than using the id already known
    * here. The fix calls vsBindingTreeService (VSBindingTreeControllerServiceProxy) directly with
    * this method's own id -- this test's core assertion is exactly that: the CORRECT, non-null id
    * reaches the binding-tree refresh, not a null one silently swallowed by mocking.
    */
   @Test
   void refreshesBindingTreeWithTheCallsOwnRuntimeIdNotANullSessionScopedOne() throws Exception {
      String id = "rt-calcfield-1";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      ExpressionRef exprRef = new ExpressionRef();
      exprRef.setName("NetTotal");
      exprRef.setExpression("1");
      CalculateRef cref = new CalculateRef(true);
      cref.setDataRef(exprRef);
      cref.setSQL(false);

      CalculateRefModel calcModel = mock(CalculateRefModel.class);
      when(calcModel.createDataRef()).thenReturn(cref);

      ModifyCalculateFieldEvent event = mock(ModifyCalculateFieldEvent.class);
      when(event.calculateRef()).thenReturn(calcModel);
      when(event.tableName()).thenReturn("Orders");
      when(event.refName()).thenReturn("NetTotal");
      when(event.create()).thenReturn(true);
      when(event.remove()).thenReturn(false);
      when(event.name()).thenReturn(null);
      when(event.wizard()).thenReturn(false);
      when(event.wizardOriginalMode()).thenReturn(null);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[0]);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(id), eq(principal))).thenReturn(rvs);

      VSBindingTreeControllerServiceProxy vsBindingTreeService =
         mock(VSBindingTreeControllerServiceProxy.class);

      ModifyCalculateFieldService service = new ModifyCalculateFieldService(
         mock(VSBindingService.class), vsBindingTreeService, mock(VSChartHandler.class),
         mock(XRepository.class), mock(VSWizardBindingHandler.class),
         mock(VSRefreshController.class), viewsheetService, mock(VSAssemblyInfoHandler.class),
         mock(DataSourceRegistry.class), mock(QueryManagerService.class));

      service.modifyCalculateField(id, event, principal, dispatcher, "");

      verify(vsBindingTreeService).getBinding(
         eq(id), any(RefreshBindingTreeEvent.class), eq(principal), eq(dispatcher));
      verify(vs).addCalcField(eq("Orders"), eq(cref));
   }

   private static CalculateRef calc(String name, String expression, boolean baseOnDetail) {
      ExpressionRef exprRef = new ExpressionRef();
      exprRef.setName(name);
      exprRef.setExpression(expression);
      CalculateRef cref = new CalculateRef(baseOnDetail);
      cref.setDataRef(exprRef);
      cref.setSQL(false);
      return cref;
   }

   private static String expression(Viewsheet vs, String table, String calc) {
      return ((ExpressionRef) vs.getCalcField(table, calc).getDataRef()).getExpression();
   }

   /**
    * Bug #76951 (VCF-002): renaming a detail calc field ('Net Sales' -> 'Net Revenue') left an
    * aggregate calc field that references it ('Discount Share' = ... field['Sum(Net Sales)'] ...)
    * and the Sum(Net Sales) aggregate field auto-created for it pointing at the old name. The
    * dependent then evaluated Sum(Net Sales) as null -- Discount Share = 1 in every crosstab cell,
    * with no error anywhere. The rename must carry both over to the new name.
    */
   @Test
   void renameRewritesDependentCalcFieldsAndTheirAggregateFields() throws Exception {
      String id = "rt-calcfield-rename";
      String table = "Orders";
      Principal principal = mock(Principal.class);

      Viewsheet vs = new Viewsheet();
      vs.addCalcField(table, calc("Net Sales", "field['Total'] * (1 - field['Discount'])", true));
      vs.addCalcField(table, calc("Discount Share",
         "(field['Sum(Total)'] - field['Sum(Net Sales)']) / field['Sum(Total)']", false));
      vs.addCalcField(table, calc("Tax", "field['Total'] * 0.08", true));
      AttributeRef netSalesAttr = new AttributeRef(null, "Net Sales");
      netSalesAttr.setDataType("double");
      vs.addAggrField(table, new AggregateRef(netSalesAttr, AggregateFormula.SUM));

      CalculateRef renamed = calc("Net Revenue", "field['Total'] * (1 - field['Discount'])", true);
      CalculateRefModel calcModel = mock(CalculateRefModel.class);
      when(calcModel.createDataRef()).thenReturn(renamed);

      ModifyCalculateFieldEvent event = mock(ModifyCalculateFieldEvent.class);
      when(event.calculateRef()).thenReturn(calcModel);
      when(event.tableName()).thenReturn(table);
      when(event.refName()).thenReturn("Net Sales");
      when(event.create()).thenReturn(false);
      when(event.remove()).thenReturn(false);
      when(event.wizard()).thenReturn(false);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(id), eq(principal))).thenReturn(rvs);

      ModifyCalculateFieldService service = new ModifyCalculateFieldService(
         mock(VSBindingService.class), mock(VSBindingTreeControllerServiceProxy.class),
         mock(VSChartHandler.class), mock(XRepository.class), mock(VSWizardBindingHandler.class),
         mock(VSRefreshController.class), viewsheetService, mock(VSAssemblyInfoHandler.class),
         mock(DataSourceRegistry.class), mock(QueryManagerService.class));

      service.modifyCalculateField(id, event, principal, mock(CommandDispatcher.class), "");

      assertNull(vs.getCalcField(table, "Net Sales"));
      assertNotNull(vs.getCalcField(table, "Net Revenue"));
      assertEquals("(field['Sum(Total)'] - field['Sum(Net Revenue)']) / field['Sum(Total)']",
         expression(vs, table, "Discount Share"));
      assertEquals("field['Total'] * 0.08", expression(vs, table, "Tax"));

      AggregateRef[] aggrs = vs.getAggrFields(table);
      assertEquals(1, aggrs.length);
      assertEquals("Net Revenue", aggrs[0].getDataRef().getAttribute());
      assertEquals("double", aggrs[0].getDataRef().getDataType());
   }

   /**
    * Bug #78978: applying a selection list re-executes the tables it filters. When the selection
    * list precedes a table in the viewsheet, that table was still bound to the old calc field name
    * at that point and had the column pruned. Every binding must be renamed before the first
    * assembly is applied.
    */
   @Test
   void renameUpdatesEveryBindingBeforeApplyingAnyAssembly() throws Exception {
      String id = "rt-calcfield-78978";
      String table = "Orders";
      Principal principal = mock(Principal.class);

      Viewsheet vs = new Viewsheet();
      vs.addCalcField(table, calc("Probe A", "field['Total']", true));

      TableVSAssembly sel = mock(TableVSAssembly.class);
      TableVSAssembly tbl = mock(TableVSAssembly.class);
      VSAssemblyInfo selInfo = mock(VSAssemblyInfo.class);
      VSAssemblyInfo tblInfo = mock(VSAssemblyInfo.class);

      for(Object[] o : new Object[][] { { sel, selInfo }, { tbl, tblInfo } }) {
         TableVSAssembly a = (TableVSAssembly) o[0];
         VSAssemblyInfo info = (VSAssemblyInfo) o[1];
         when(a.getTableName()).thenReturn(table);
         when(a.getName()).thenReturn("A" + System.identityHashCode(a));
         when(a.getVSAssemblyInfo()).thenReturn(info);
         when(info.clone()).thenReturn(info);
      }

      Viewsheet spy = spy(vs);
      doReturn(new Assembly[] { sel, tbl }).when(spy).getAssemblies();

      CalculateRef renamed = calc("Probe A2", "field['Total']", true);
      CalculateRefModel calcModel = mock(CalculateRefModel.class);
      when(calcModel.createDataRef()).thenReturn(renamed);

      ModifyCalculateFieldEvent event = mock(ModifyCalculateFieldEvent.class);
      when(event.calculateRef()).thenReturn(calcModel);
      when(event.tableName()).thenReturn(table);
      when(event.refName()).thenReturn("Probe A");
      when(event.create()).thenReturn(false);
      when(event.remove()).thenReturn(false);
      when(event.wizard()).thenReturn(false);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(spy);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(id), eq(principal))).thenReturn(rvs);

      VSAssemblyInfoHandler handler = mock(VSAssemblyInfoHandler.class);
      ModifyCalculateFieldService service = new ModifyCalculateFieldService(
         mock(VSBindingService.class), mock(VSBindingTreeControllerServiceProxy.class),
         mock(VSChartHandler.class), mock(XRepository.class), mock(VSWizardBindingHandler.class),
         mock(VSRefreshController.class), viewsheetService, handler,
         mock(DataSourceRegistry.class), mock(QueryManagerService.class));

      service.modifyCalculateField(id, event, principal, mock(CommandDispatcher.class), "");

      org.mockito.InOrder order = inOrder(sel, tbl, handler);
      order.verify(sel).renameBindingCol("Probe A", "Probe A2");
      order.verify(tbl).renameBindingCol("Probe A", "Probe A2");
      order.verify(handler, times(2)).apply(
         any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any(),
         any(), any(), any(), any());
   }

   /** Bug #78441: the dot form is rewritten, and an ambiguous case-different name is not. */
   @Test
   void renameRewritesTheDotFormAndLeavesAnAmbiguousNameAlone() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("Fee R", "field['Total']", true));
      vs.addCalcField("Orders", calc("fee", "field['Total']", true));
      vs.addCalcField("Orders", calc("Dot", "field.Fee * 2", true));
      vs.addCalcField("Orders", calc("Amb", "field['FEE']", true));

      ModifyCalculateFieldService.renameCalcFieldDependents(vs, "Orders", "Fee", "Fee R");

      assertEquals("field['Fee R'] * 2", expression(vs, "Orders", "Dot"));
      assertEquals("field['FEE']", expression(vs, "Orders", "Amb"));
   }

   /** A formula-only edit renames nothing, so it must leave the dependents exactly as they are. */
   @Test
   void editWithoutRenameLeavesDependentCalcFieldsUntouched() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("Discount Share", "field['Sum(Net Sales)']", false));

      ModifyCalculateFieldService.renameCalcFieldDependents(vs, "Orders", "Other", "Other2");

      assertEquals("field['Sum(Net Sales)']", expression(vs, "Orders", "Discount Share"));
   }

   /**
    * VBS-003: a calc field add against a crosstab whose base table shape (e.g. a multi-way join
    * with duplicate/aliased columns) makes the post-write crosstab AggregateInfo rebuild throw
    * used to let that exception escape modifyCalculateField uncaught, which
    * WizControllerErrorHandler.handleUnexpected then turned into a generic "unexpected server
    * error" -- even though vs.addCalcField(...) had already committed a few lines earlier. The fix
    * wraps that rebuild in its own try/catch so the write's own success is what reaches the
    * caller; this test proves the exception no longer propagates and the caller gets a warning
    * MessageCommand instead of silence or a false failure.
    */
   @Test
   void containsCrosstabAggregateInfoRefreshFailureInsteadOfPropagatingIt() throws Exception {
      String id = "rt-calcfield-2";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      ExpressionRef exprRef = new ExpressionRef();
      exprRef.setName("DoubleQty");
      exprRef.setExpression("field['QUANTITY'] * 2");
      CalculateRef cref = new CalculateRef(true);
      cref.setDataRef(exprRef);
      cref.setSQL(false);

      CalculateRefModel calcModel = mock(CalculateRefModel.class);
      when(calcModel.createDataRef()).thenReturn(cref);

      ModifyCalculateFieldEvent event = mock(ModifyCalculateFieldEvent.class);
      when(event.calculateRef()).thenReturn(calcModel);
      when(event.tableName()).thenReturn("OrdersJoined");
      when(event.refName()).thenReturn("DoubleQty");
      when(event.create()).thenReturn(true);
      when(event.remove()).thenReturn(false);
      when(event.name()).thenReturn("Crosstab1");
      when(event.wizard()).thenReturn(false);
      when(event.wizardOriginalMode()).thenReturn(null);

      CrosstabVSAssembly cross = mock(CrosstabVSAssembly.class);
      when(cross.getVSCrosstabInfo()).thenReturn(mock(VSCrosstabInfo.class));
      // the 1st call (the new-source permission check, Bug #77427) and the 2nd (initial-setup
      // check) succeed; the 3rd call, in the post-write refresh block this fix guards, is where
      // the join/duplicate-column-shaped table throws.
      when(cross.getSourceInfo())
         .thenReturn(mock(SourceInfo.class))
         .thenReturn(mock(SourceInfo.class))
         .thenThrow(new NullPointerException("simulated join-table aggregate-info refresh NPE"));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[0]);
      when(vs.getAssembly(eq("Crosstab1"))).thenReturn(cross);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(id), eq(principal))).thenReturn(rvs);

      ModifyCalculateFieldService service = new ModifyCalculateFieldService(
         mock(VSBindingService.class), mock(VSBindingTreeControllerServiceProxy.class),
         mock(VSChartHandler.class), mock(XRepository.class), mock(VSWizardBindingHandler.class),
         mock(VSRefreshController.class), viewsheetService, mock(VSAssemblyInfoHandler.class),
         mock(DataSourceRegistry.class), mock(QueryManagerService.class));

      assertDoesNotThrow(
         () -> service.modifyCalculateField(id, event, principal, dispatcher, ""));

      verify(vs).addCalcField(eq("OrdersJoined"), eq(cref));

      ArgumentCaptor<MessageCommand> captor = ArgumentCaptor.forClass(MessageCommand.class);
      verify(dispatcher, atLeastOnce()).sendCommand(captor.capture());
      boolean sawCrosstabWarning = captor.getAllValues().stream().anyMatch(
         c -> c.getType() == MessageCommand.Type.WARNING &&
            c.getMessage() != null && c.getMessage().contains("crosstab"));
      assertTrue(sawCrosstabWarning,
         "expected a WARNING MessageCommand about the crosstab refresh failure");
   }

   /**
    * Bug #76666: {@code refreshController.refreshViewsheet(...)} at this call site (fired
    * whenever {@code infoChanged || !create}, i.e. on essentially every edit/remove) is a
    * best-effort step -- if it fails for any reason, the calc field mutation above should still
    * be reported as a success rather than propagating a generic 500, exactly like the other
    * best-effort refresh steps in this method (crosstab/chart aggregate-info rebuild above). This
    * test simulates that call throwing for an unrelated reason and proves the write still
    * completes with a warning instead of the failure propagating. (Prior to the #76666 fix, this
    * exact call always threw NullPointerException("Ouch! Argument cannot be null: key") for a
    * session-less caller -- see {@link #refreshesViewsheetWithTheCallsOwnRuntimeIdNotANullSessionScopedOne()}
    * below for the regression test that the null id itself is no longer reached.)
    */
   @Test
   void containsRefreshViewsheetFailureOnEditInsteadOfPropagatingIt() throws Exception {
      String id = "rt-calcfield-3";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      ExpressionRef exprRef = new ExpressionRef();
      exprRef.setName("DoubleQty");
      exprRef.setExpression("field['QUANTITY'] * 3");
      CalculateRef cref = new CalculateRef(true);
      cref.setDataRef(exprRef);
      cref.setSQL(false);

      CalculateRef oldCalcRef = (CalculateRef) cref.clone();

      CalculateRefModel calcModel = mock(CalculateRefModel.class);
      when(calcModel.createDataRef()).thenReturn(cref);

      ModifyCalculateFieldEvent event = mock(ModifyCalculateFieldEvent.class);
      when(event.calculateRef()).thenReturn(calcModel);
      when(event.tableName()).thenReturn("OrderDetails");
      when(event.refName()).thenReturn("DoubleQty");
      when(event.create()).thenReturn(false);
      when(event.remove()).thenReturn(false);
      when(event.name()).thenReturn(null);
      when(event.wizard()).thenReturn(false);
      when(event.wizardOriginalMode()).thenReturn(null);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[0]);
      // 1st call resolves the pre-edit ref; the 2nd ("any real change?") call must come back
      // null so the pre-existing "no-op edit" short-circuit doesn't return before this fix's
      // guarded refreshViewsheet call is ever reached.
      when(vs.getCalcField(eq("OrderDetails"), eq("DoubleQty")))
         .thenReturn(oldCalcRef, (CalculateRef) null);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(id), eq(principal))).thenReturn(rvs);

      VSRefreshController refreshController = mock(VSRefreshController.class);
      doThrow(new NullPointerException("Ouch! Argument cannot be null: key"))
         .when(refreshController)
         .refreshViewsheet(eq(id), any(), eq(principal), eq(dispatcher), anyString());

      ModifyCalculateFieldService service = new ModifyCalculateFieldService(
         mock(VSBindingService.class), mock(VSBindingTreeControllerServiceProxy.class),
         mock(VSChartHandler.class), mock(XRepository.class), mock(VSWizardBindingHandler.class),
         refreshController, viewsheetService, mock(VSAssemblyInfoHandler.class),
         mock(DataSourceRegistry.class), mock(QueryManagerService.class));

      assertDoesNotThrow(
         () -> service.modifyCalculateField(id, event, principal, dispatcher, ""));

      verify(vs).addCalcField(eq("OrderDetails"), eq(cref));

      ArgumentCaptor<MessageCommand> captor = ArgumentCaptor.forClass(MessageCommand.class);
      verify(dispatcher, atLeastOnce()).sendCommand(captor.capture());
      boolean sawRefreshWarning = captor.getAllValues().stream().anyMatch(
         c -> c.getType() == MessageCommand.Type.WARNING &&
            c.getMessage() != null && c.getMessage().contains("viewsheet"));
      assertTrue(sawRefreshWarning,
         "expected a WARNING MessageCommand about the refreshViewsheet failure");
   }

   /**
    * Bug #76666 regression test. Before the fix, this call site asked
    * {@code VSRefreshController}'s STOMP-mapped {@code refreshViewsheet(event, principal,
    * dispatcher, linkUri)} to refresh the viewsheet, which re-derives the runtime id from
    * {@code RuntimeViewsheetRef} -- a WebSocket-session-scoped bean that is only ever populated
    * from a native STOMP header a real browser session sends. A session-less caller (the wiz
    * agent's {@code CalcFieldAgentService}, reached via plain HTTP with no STOMP session at all)
    * always got null back, which reached {@code RuntimeSheetCache.getAffinityKey} unguarded and
    * threw {@code NullPointerException("Ouch! Argument cannot be null: key")}.
    *
    * This test binds no {@code RuntimeViewsheetRef} / session state whatsoever (the mocked
    * {@code VSRefreshController} has none), simulating exactly that session-less caller, and
    * asserts that the refresh is actually invoked with the CORRECT, already-known runtime id --
    * via the explicit-id overload {@code refreshViewsheet(id, event, principal, dispatcher,
    * linkUri)} -- not merely that nothing throws. A test that only asserted "no exception" would
    * still pass against the pre-fix code once wrapped in the surrounding try/catch (see
    * {@link #containsRefreshViewsheetFailureOnEditInsteadOfPropagatingIt()} above), so it would
    * prove nothing about whether the null id was actually eliminated.
    */
   @Test
   void refreshesViewsheetWithTheCallsOwnRuntimeIdNotANullSessionScopedOne() throws Exception {
      String id = "rt-calcfield-4";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      ExpressionRef exprRef = new ExpressionRef();
      exprRef.setName("DoubleQty");
      exprRef.setExpression("field['QUANTITY'] * 4");
      CalculateRef cref = new CalculateRef(true);
      cref.setDataRef(exprRef);
      cref.setSQL(false);

      CalculateRef oldCalcRef = (CalculateRef) cref.clone();

      CalculateRefModel calcModel = mock(CalculateRefModel.class);
      when(calcModel.createDataRef()).thenReturn(cref);

      ModifyCalculateFieldEvent event = mock(ModifyCalculateFieldEvent.class);
      when(event.calculateRef()).thenReturn(calcModel);
      when(event.tableName()).thenReturn("OrderDetails");
      when(event.refName()).thenReturn("DoubleQty");
      when(event.create()).thenReturn(false);
      when(event.remove()).thenReturn(false);
      when(event.name()).thenReturn(null);
      when(event.wizard()).thenReturn(false);
      when(event.wizardOriginalMode()).thenReturn(null);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[0]);
      // 1st call resolves the pre-edit ref; the 2nd ("any real change?") call must come back
      // null so the pre-existing "no-op edit" short-circuit doesn't return before the
      // refreshViewsheet call under test is ever reached.
      when(vs.getCalcField(eq("OrderDetails"), eq("DoubleQty")))
         .thenReturn(oldCalcRef, (CalculateRef) null);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(id), eq(principal))).thenReturn(rvs);

      // No RuntimeViewsheetRef / session state bound anywhere -- simulates the wiz agent's
      // session-less HTTP caller exactly. If the fix regressed and this call site went back to
      // asking VSRefreshController to re-derive the id itself, this mock has no way to supply
      // one, and the verify() below (which requires the explicit id) would fail to match.
      VSRefreshController refreshController = mock(VSRefreshController.class);

      ModifyCalculateFieldService service = new ModifyCalculateFieldService(
         mock(VSBindingService.class), mock(VSBindingTreeControllerServiceProxy.class),
         mock(VSChartHandler.class), mock(XRepository.class), mock(VSWizardBindingHandler.class),
         refreshController, viewsheetService, mock(VSAssemblyInfoHandler.class),
         mock(DataSourceRegistry.class), mock(QueryManagerService.class));

      assertDoesNotThrow(
         () -> service.modifyCalculateField(id, event, principal, dispatcher, ""));

      verify(vs).addCalcField(eq("OrderDetails"), eq(cref));

      // The actual fix: the refresh is invoked via the explicit-id overload, using this
      // method's own already-live id -- not the STOMP-mapped overload that would have to
      // re-derive one from a session-scoped bean this caller never populates.
      verify(refreshController)
         .refreshViewsheet(eq(id), any(VSRefreshEvent.class), eq(principal), eq(dispatcher), anyString());
      verify(refreshController, never())
         .refreshViewsheet(any(VSRefreshEvent.class), eq(principal), eq(dispatcher), anyString());

      // No warning should have been raised -- the refresh actually ran, it didn't fail.
      ArgumentCaptor<MessageCommand> captor = ArgumentCaptor.forClass(MessageCommand.class);
      verify(dispatcher, atLeast(0)).sendCommand(captor.capture());
      boolean sawRefreshWarning = captor.getAllValues().stream().anyMatch(
         c -> c.getType() == MessageCommand.Type.WARNING &&
            c.getMessage() != null && c.getMessage().contains("viewsheet"));
      assertFalse(sawRefreshWarning,
         "refreshViewsheet should have succeeded with the correct id -- no warning expected");
   }

   /**
    * Bug #78282: the calc-table cell must follow a rename whichever assembly the caller names
    * (a chart, or none), not only when the named assembly is the calc table itself.
    */
   private void renameWithNamedAssembly(String named) throws Exception {
      String id = "rt-calcfield-rename-cell";
      String table = "Orders";
      Principal principal = mock(Principal.class);

      Viewsheet vs = new Viewsheet();
      vs.addCalcField(table, calc("NetRevenue", "field['Total']", true));

      TableLayout layout = new TableLayout();
      layout.setColCount(1);
      BaseLayout.Region region = layout.new Region();
      region.setRowCount(1);
      layout.addRegion(new DefaultTableLens(new Object[][] { { "a" } })
         .getDescriptor().getRowDataPath(0), region);
      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, "NetRevenue");
      cell.setBType(CellBinding.SUMMARY);
      layout.setCellBinding(0, 0, cell);

      CalcTableVSAssembly calcTable = new CalcTableVSAssembly(vs, "CalcTable1");
      calcTable.setTableLayout(layout);
      calcTable.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, table));
      vs.addAssembly(calcTable);
      vs.addAssembly(new ChartVSAssembly(vs, "Chart1"));

      CalculateRef renamed = calc("NetRevenue2", "field['Total']", true);
      CalculateRefModel calcModel = mock(CalculateRefModel.class);
      when(calcModel.createDataRef()).thenReturn(renamed);

      ModifyCalculateFieldEvent event = mock(ModifyCalculateFieldEvent.class);
      when(event.calculateRef()).thenReturn(calcModel);
      when(event.tableName()).thenReturn(table);
      when(event.refName()).thenReturn("NetRevenue");
      when(event.create()).thenReturn(false);
      when(event.remove()).thenReturn(false);
      when(event.wizard()).thenReturn(false);
      when(event.name()).thenReturn(named);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(id), eq(principal))).thenReturn(rvs);

      ModifyCalculateFieldService service = new ModifyCalculateFieldService(
         mock(VSBindingService.class), mock(VSBindingTreeControllerServiceProxy.class),
         mock(VSChartHandler.class), mock(XRepository.class), mock(VSWizardBindingHandler.class),
         mock(VSRefreshController.class), viewsheetService, mock(VSAssemblyInfoHandler.class),
         mock(DataSourceRegistry.class), mock(QueryManagerService.class));

      service.modifyCalculateField(id, event, principal, mock(CommandDispatcher.class), "");

      assertEquals("NetRevenue2", calcTable.getTableLayout().getCellBinding(0, 0).getValue());
   }

   @Test
   void renameRewritesCalcTableCellWhenAnotherAssemblyIsNamed() throws Exception {
      renameWithNamedAssembly("Chart1");
   }

   @Test
   void renameRewritesCalcTableCellWhenNoAssemblyIsNamed() throws Exception {
      renameWithNamedAssembly(null);
   }

   @Test
   void renameRewritesCalcTableCellWhenTheCalcTableIsNamed() throws Exception {
      renameWithNamedAssembly("CalcTable1");
   }

   // ---- Bug #78682: removing or changing an unrelated calc field must not unbind a Calendar or
   // an Output assembly (Text/Gauge/...) bound to a different column of the same table.

   /** Bug #78756: a SelectionList's measure named the removed calc field and was left dangling. */
   @Test
   void removeCalcFieldUsedAsSelectionListMeasureClearsTheMeasureAndKeepsTheTable()
      throws Exception
   {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("VCF20X", "field['Total'] * 2", true));
      SelectionListVSAssembly list = selectionList(vs, "SelectionList1", "Orders", "State");
      list.getSelectionListInfo().setMeasure("VCF20X");
      list.getSelectionListInfo().setMeasureValue("VCF20X");

      modify78682(vs, "Orders", "VCF20X", null);

      assertNull(list.getMeasure());
      assertNull(list.getSelectionListInfo().getMeasureValue());
      assertEquals("Orders", list.getTableName());
   }

   @Test
   void removeCalcFieldKeepsASelectionListMeasureOfAnotherField() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("VCF20X", "field['Total'] * 2", true));
      SelectionListVSAssembly list = selectionList(vs, "SelectionList1", "Orders", "State");
      list.getSelectionListInfo().setMeasure("Total");
      list.getSelectionListInfo().setMeasureValue("Total");

      modify78682(vs, "Orders", "VCF20X", null);

      assertEquals("Total", list.getMeasure());
      assertEquals("Orders", list.getTableName());
   }

   @Test
   void removeCalcFieldUsedAsSelectionListColumnStillUnbindsTheList() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("VCF20X", "field['Total'] * 2", true));
      SelectionListVSAssembly list = selectionList(vs, "SelectionList1", "Orders", "VCF20X");

      modify78682(vs, "Orders", "VCF20X", null);

      assertNull(list.getTableName());
   }

   /** Bug #78758: a rename must carry a SelectionList's and SelectionTree's measure along. */
   @Test
   void renameCalcFieldUsedAsSelectionMeasureMovesTheMeasureOfListAndTree() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("VCF26X", "field['Total'] * 2", true));
      SelectionListVSAssembly list = selectionList(vs, "SelectionList1", "Orders", "State");
      list.getSelectionListInfo().setMeasure("VCF26X");
      list.getSelectionListInfo().setMeasureValue("VCF26X");
      SelectionTreeVSAssembly tree = selectionTree(vs, "SelectionTree1", "Orders", "State");
      tree.getSelectionTreeInfo().setMeasure("VCF26X");
      tree.getSelectionTreeInfo().setMeasureValue("VCF26X");

      modify78682(vs, "Orders", "VCF26X", calc("VCF26Y", "field['Total'] * 2", true));

      assertEquals("VCF26Y", list.getMeasure());
      assertEquals("VCF26Y", list.getSelectionListInfo().getMeasureValue());
      assertEquals("VCF26Y", tree.getMeasure());
      assertEquals("VCF26Y", tree.getSelectionTreeInfo().getMeasureValue());
   }

   @Test
   void renameUnrelatedCalcFieldLeavesSelectionMeasuresAlone() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("VCF26X", "field['Total'] * 2", true));
      SelectionListVSAssembly list = selectionList(vs, "SelectionList1", "Orders", "State");
      list.getSelectionListInfo().setMeasure("Total");
      list.getSelectionListInfo().setMeasureValue("Total");
      SelectionTreeVSAssembly tree = selectionTree(vs, "SelectionTree1", "Orders", "State");
      tree.getSelectionTreeInfo().setMeasure("Total");
      tree.getSelectionTreeInfo().setMeasureValue("Total");

      modify78682(vs, "Orders", "VCF26X", calc("VCF26Y", "field['Total'] * 2", true));

      assertEquals("Total", list.getMeasure());
      assertEquals("Total", list.getSelectionListInfo().getMeasureValue());
      assertEquals("Total", tree.getMeasure());
      assertEquals("Total", tree.getSelectionTreeInfo().getMeasureValue());
   }

   private static SelectionTreeVSAssembly selectionTree(Viewsheet vs, String name, String table,
                                                        String column)
   {
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, name);
      tree.setTableName(table);
      tree.getSelectionTreeInfo().setDataRefs(new DataRef[] {new AttributeRef(null, column)});
      vs.addAssembly(tree);
      return tree;
   }

   private static SelectionListVSAssembly selectionList(Viewsheet vs, String name, String table,
                                                        String column)
   {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      list.setTableName(table);
      list.setDataRef(new AttributeRef(null, column));
      vs.addAssembly(list);
      return list;
   }

   private static CalendarVSAssembly calendar(Viewsheet vs, String name, String table,
                                              String column)
   {
      CalendarVSAssembly cal = new CalendarVSAssembly(vs, name);
      cal.setTableName(table);
      cal.setDataRef(new AttributeRef(null, column));
      vs.addAssembly(cal);
      return cal;
   }

   /** Binds an Output; {@code withRef=false} mimics a persisted binding (columnValue only). */
   private static <T extends OutputVSAssembly> T output(Viewsheet vs, T out, String table,
                                                         String column, boolean withRef)
   {
      ScalarBindingInfo sbinfo = new ScalarBindingInfo();
      out.setScalarBindingInfo(sbinfo);
      out.setTableName(table);
      sbinfo.setColumnValue(column);

      if(withRef) {
         sbinfo.setColumn(new AttributeRef(null, column));
      }

      vs.addAssembly(out);
      return out;
   }

   /** Runs the real modifyCalculateField for {@code refName}; {@code ncalc == null} removes it. */
   private static void modify78682(Viewsheet vs, String table, String refName,
                                   CalculateRef ncalc) throws Exception
   {
      String id = "rt-calcfield-78682";
      Principal principal = mock(Principal.class);
      CalculateRefModel calcModel = null;

      if(ncalc != null) {
         calcModel = mock(CalculateRefModel.class);
         when(calcModel.createDataRef()).thenReturn(ncalc);
      }

      ModifyCalculateFieldEvent event = mock(ModifyCalculateFieldEvent.class);
      when(event.calculateRef()).thenReturn(calcModel);
      when(event.tableName()).thenReturn(table);
      when(event.refName()).thenReturn(refName);
      when(event.create()).thenReturn(false);
      when(event.remove()).thenReturn(ncalc == null);
      when(event.wizard()).thenReturn(false);
      when(event.name()).thenReturn(null);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(id), eq(principal))).thenReturn(rvs);

      ModifyCalculateFieldService service = new ModifyCalculateFieldService(
         mock(VSBindingService.class), mock(VSBindingTreeControllerServiceProxy.class),
         mock(VSChartHandler.class), mock(XRepository.class), mock(VSWizardBindingHandler.class),
         mock(VSRefreshController.class), viewsheetService, mock(VSAssemblyInfoHandler.class),
         mock(DataSourceRegistry.class), mock(QueryManagerService.class));

      service.modifyCalculateField(id, event, principal, mock(CommandDispatcher.class), "");
   }

   @Test
   void removeUnrelatedCalcFieldKeepsCalendarAndOutputsBound() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("VCF20X", "field['Total'] * 2", true));
      CalendarVSAssembly cal = calendar(vs, "Calendar1", "Orders", "Date");
      TextVSAssembly text = output(vs, new TextVSAssembly(vs, "Text1"), "Orders", "Total", true);
      GaugeVSAssembly gauge =
         output(vs, new GaugeVSAssembly(vs, "Gauge1"), "Orders", "Total", true);
      TextVSAssembly persisted =
         output(vs, new TextVSAssembly(vs, "Text2"), "Orders", "Total", false);

      modify78682(vs, "Orders", "VCF20X", null);

      assertNull(vs.getCalcField("Orders", "VCF20X"));
      assertEquals("Orders", cal.getTableName());
      assertEquals("Orders", text.getTableName());
      assertEquals("Orders", gauge.getTableName());
      assertEquals("Orders", persisted.getTableName());
   }

   @Test
   void removeBoundCalcFieldStillUnbindsCalendarAndOutputs() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("VCF20X", "field['Total'] * 2", true));
      CalendarVSAssembly cal = calendar(vs, "Calendar1", "Orders", "VCF20X");
      TextVSAssembly text = output(vs, new TextVSAssembly(vs, "Text1"), "Orders", "VCF20X", true);
      GaugeVSAssembly gauge =
         output(vs, new GaugeVSAssembly(vs, "Gauge1"), "Orders", "VCF20X", true);
      // persisted Text/Gauge have only the column value; the column ref is resolved lazily
      TextVSAssembly persisted =
         output(vs, new TextVSAssembly(vs, "Text2"), "Orders", "VCF20X", false);
      GaugeVSAssembly persistedGauge =
         output(vs, new GaugeVSAssembly(vs, "Gauge2"), "Orders", "VCF20X", false);
      assertNull(persisted.getScalarBindingInfo().getColumn());

      modify78682(vs, "Orders", "VCF20X", null);

      assertNull(cal.getTableName());
      assertNull(text.getTableName());
      assertNull(gauge.getTableName());
      assertNull(persisted.getTableName());
      assertNull(persistedGauge.getTableName());
   }

   @Test
   void removeCalcFieldUsedOnlyAsSecondaryColumnUnbindsOutput() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("VCF20X", "field['Total'] * 2", true));
      TextVSAssembly text = output(vs, new TextVSAssembly(vs, "Text1"), "Orders", "Total", true);
      text.getScalarBindingInfo().setColumn2Value("VCF20X");

      modify78682(vs, "Orders", "VCF20X", null);

      assertNull(text.getTableName());
   }

   @Test
   void removeUnrelatedCalcFieldDropsItsConditionAndKeepsOutputBound() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("VCF20X", "field['Total'] * 2", true));
      TextVSAssembly text = output(vs, new TextVSAssembly(vs, "Text1"), "Orders", "Total", true);
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new AttributeRef(null, "VCF20X"), new Condition(), 0));
      ((OutputVSAssemblyInfo) text.getVSAssemblyInfo()).setPreConditionList(conds);

      modify78682(vs, "Orders", "VCF20X", null);

      assertEquals("Orders", text.getTableName());
      ConditionList left = text.getPreConditionList();
      assertTrue(left == null || left.isEmpty(), "condition on the removed field: " + left);
   }

   @Test
   void unrelatedDetailToAggregateKeepsCalendarBound() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("VCF20X", "field['Total'] * 2", true));
      CalendarVSAssembly other = calendar(vs, "Calendar1", "Orders", "Date");
      CalendarVSAssembly own = calendar(vs, "Calendar2", "Orders", "VCF20X");

      modify78682(vs, "Orders", "VCF20X", calc("VCF20X", "sum(field['Total'])", false));

      assertEquals("Orders", other.getTableName());
      // a calendar bound to the field itself can't use an aggregate field and still unbinds
      assertNull(own.getTableName());
   }

   @Test
   void unrelatedRenameAndDetailToAggregateKeepsCalendarBound() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("Orders", calc("VCF20X", "field['Total'] * 2", true));
      CalendarVSAssembly other = calendar(vs, "Calendar1", "Orders", "Date");
      CalendarVSAssembly own = calendar(vs, "Calendar2", "Orders", "VCF20X");
      TextVSAssembly text = output(vs, new TextVSAssembly(vs, "Text1"), "Orders", "Total", true);

      modify78682(vs, "Orders", "VCF20X", calc("VCF20Y", "sum(field['Total'])", false));

      assertNotNull(vs.getCalcField("Orders", "VCF20Y"));
      assertEquals("Orders", other.getTableName());
      assertEquals("Orders", text.getTableName());
      assertNull(own.getTableName());
   }

   /**
    * Bug #78557: removing (or renaming, or changing the type of) a calc field did nothing when
    * the viewsheet held a wizard _temp_crosstab_N probe on the same table. The probe has no
    * AggregateInfo, CrosstabVSAssembly.removeBindingCol threw an NPE, and the catch sent an
    * empty MessageCommand that crashed the client.
    */
   @Nested
   class WizardProbe {
      private static final String TABLE = "Order Model";
      private static final String CALC = "Scratch";

      @BeforeEach
      void setUp() throws Exception {
         vs = new Viewsheet();
         ChartVSAssembly chart = new ChartVSAssembly(vs, VSWizardConstants.TEMP_CHART_NAME);
         chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, TABLE));
         vs.addAssembly(chart);

         CalculateRef calc = new CalculateRef();
         calc.setDataRef(new ExpressionRef(null, CALC));
         calc.setName(CALC);
         vs.addCalcField(TABLE, calc);

         box = mock(ViewsheetSandbox.class);
         when(box.getViewsheet()).thenReturn(vs);
         principal = mock(Principal.class);
         ViewsheetService engine = mock(ViewsheetService.class);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
         when(engine.getViewsheet("vs1", principal)).thenReturn(rvs);
         handler = mock(VSAssemblyInfoHandler.class);
         dispatcher = mock(CommandDispatcher.class);
         service = new ModifyCalculateFieldService(
            null, mock(VSBindingTreeControllerServiceProxy.class), null, null, null,
            mock(VSRefreshController.class), engine, handler, null, mock(QueryManagerService.class));
      }

      @Test
      void removeWithWizardProbePresentRemovesCalcField() throws Exception {
         CrosstabVSAssembly probe = addProbe();

         service.modifyCalculateField("vs1", removeEvent(true), principal, dispatcher, null);

         assertNull(vs.getCalcField(TABLE, CALC));
         List<ViewsheetCommand> commands = sentCommands();
         assertTrue(commands.stream().anyMatch(c -> c instanceof RefreshWizardTreeTriggerCommand));
         assertTrue(commands.stream().noneMatch(c -> c instanceof MessageCommand));
         // the probe is skipped entirely: not synced and not applied
         assertEquals(List.of(VSWizardConstants.TEMP_CHART_NAME), appliedNames());
         assertEquals(2, probe.getVSCrosstabInfo().getDesignAggregates().length);
      }

      @Test
      void removeWithoutProbeRemovesCalcField() throws Exception {
         service.modifyCalculateField("vs1", removeEvent(true), principal, dispatcher, null);

         assertNull(vs.getCalcField(TABLE, CALC));
         assertTrue(sentCommands().stream()
                       .anyMatch(c -> c instanceof RefreshWizardTreeTriggerCommand));
      }

      @Test
      void removeOutsideWizardSkipsSavedProbe() throws Exception {
         // a leaked probe saved into the viewsheet and reopened in the regular composer
         addProbe();

         service.modifyCalculateField("vs1", removeEvent(false), principal, dispatcher, null);

         assertNull(vs.getCalcField(TABLE, CALC));
         assertTrue(sentCommands().stream().noneMatch(c -> c instanceof MessageCommand));
         assertEquals(List.of(VSWizardConstants.TEMP_CHART_NAME), appliedNames());
      }

      @Test
      void renameWithProbeAndUnboundCrosstabRenamesCalcField() throws Exception {
         addProbe();
         CrosstabVSAssembly cross = addCrosstabWithoutAggregateInfo();
         CalculateRef renamed = (CalculateRef) vs.getCalcField(TABLE, CALC).clone();
         renamed.setName("Scratch2");

         service.modifyCalculateField("vs1", modifyEvent(renamed), principal, dispatcher, null);

         assertNull(vs.getCalcField(TABLE, CALC));
         assertNotNull(vs.getCalcField(TABLE, "Scratch2"));
         assertTrue(sentCommands().stream().noneMatch(c -> c instanceof MessageCommand));
         assertEquals("Scratch2", ((VSAggregateRef) cross.getVSCrosstabInfo()
            .getDesignAggregates()[0]).getColumnValue());
         assertEquals(Set.of(VSWizardConstants.TEMP_CHART_NAME, "Crosstab1"),
                      new HashSet<>(appliedNames()));
      }

      @Test
      void changeCalcTypeWithProbeAndUnboundCrosstab() throws Exception {
         addProbe();
         CrosstabVSAssembly cross = addCrosstabWithoutAggregateInfo();
         CalculateRef old = vs.getCalcField(TABLE, CALC);
         CalculateRef changed = new CalculateRef(!old.isBaseOnDetail());
         changed.setDataRef(new ExpressionRef(null, CALC));
         changed.setName(CALC);

         service.modifyCalculateField("vs1", modifyEvent(changed), principal, dispatcher, null);

         assertEquals(changed.isBaseOnDetail(), vs.getCalcField(TABLE, CALC).isBaseOnDetail());
         assertTrue(sentCommands().stream().noneMatch(c -> c instanceof MessageCommand));
         assertEquals(0, cross.getVSCrosstabInfo().getDesignAggregates().length);
      }

      @Test
      void failureSendsErrorWithMessage() throws Exception {
         addCrosstabWithoutAggregateInfo();
         doThrow(new IllegalStateException()).when(handler).apply(
            any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            any(), any(), any(), any(), any());

         service.modifyCalculateField("vs1", removeEvent(false), principal, dispatcher, null);

         List<MessageCommand> messages = sentCommands().stream()
            .filter(MessageCommand.class::isInstance).map(MessageCommand.class::cast).toList();
         assertEquals(1, messages.size());
         assertEquals(MessageCommand.Type.ERROR, messages.get(0).getType());
         assertNotNull(messages.get(0).getMessage());
         assertNotNull(vs.getCalcField(TABLE, CALC));
      }

      @Test
      void crosstabWithoutAggregateInfoSyncsBindingCols() {
         CrosstabVSAssembly cross = addCrosstabWithoutAggregateInfo();
         assertNull(cross.getVSCrosstabInfo().getAggregateInfo());

         cross.renameBindingCol(CALC, "Scratch2");
         assertEquals("Scratch2", ((VSAggregateRef) cross.getVSCrosstabInfo()
            .getDesignAggregates()[0]).getColumnValue());

         cross.removeBindingCol("Scratch2");
         assertEquals(0, cross.getVSCrosstabInfo().getDesignAggregates().length);

         CrosstabVSAssembly probe = addProbe();
         assertDoesNotThrow(() -> probe.changeCalcType(CALC, vs.getCalcField(TABLE, CALC)));
         assertEquals(0, probe.getVSCrosstabInfo().getDesignAggregates().length);
      }

      @Test
      void removeOutsideWizardSyncsUnboundCrosstabAndSkipsProbe() throws Exception {
         CrosstabVSAssembly probe = addProbe();
         CrosstabVSAssembly cross = addCrosstabWithoutAggregateInfo();

         service.modifyCalculateField("vs1", removeEvent(false), principal, dispatcher, null);

         assertNull(vs.getCalcField(TABLE, CALC));
         assertTrue(sentCommands().stream().noneMatch(c -> c instanceof MessageCommand));
         assertEquals(0, cross.getVSCrosstabInfo().getDesignAggregates().length);
         assertEquals(2, probe.getVSCrosstabInfo().getDesignAggregates().length);
         assertEquals(Set.of(VSWizardConstants.TEMP_CHART_NAME, "Crosstab1"),
                      new HashSet<>(appliedNames()));
      }

      // AggregateInfo.addGroup rejects a column that is already an aggregate, even with
      // delSame false (#4920), so the aggregate and the group sync are checked on separate
      // crosstabs instead of one AggregateInfo holding the calc field in both lists.
      @Test
      void crosstabWithAggregateInfoStillSyncsIt() {
         CrosstabVSAssembly cross = addCrosstabWithoutAggregateInfo();
         AggregateInfo ainfo = new AggregateInfo();
         ainfo.addAggregate(new AggregateRef(new ColumnRef(new AttributeRef(CALC)),
                                             AggregateFormula.SUM));
         ainfo.addGroup(new GroupRef(new ColumnRef(new AttributeRef("Region"))));
         cross.getVSCrosstabInfo().setAggregateInfo(ainfo);

         cross.renameBindingCol(CALC, "Scratch2");
         assertEquals("Scratch2", ainfo.getAggregates()[0].getDataRef().getAttribute());

         cross.removeBindingCol("Scratch2");
         assertEquals(0, ainfo.getAggregates().length);
         assertEquals(1, ainfo.getGroups().length);
         assertEquals("Region", ainfo.getGroups()[0].getDataRef().getAttribute());
         assertEquals(0, cross.getVSCrosstabInfo().getDesignAggregates().length);
      }

      @Test
      void crosstabWithAggregateInfoStillSyncsItsGroups() {
         CrosstabVSAssembly cross = addCrosstabWithoutAggregateInfo();
         AggregateInfo ainfo = new AggregateInfo();
         ainfo.addGroup(new GroupRef(new ColumnRef(new AttributeRef("Region"))));
         ainfo.addGroup(new GroupRef(new ColumnRef(new AttributeRef(CALC))));
         cross.getVSCrosstabInfo().setAggregateInfo(ainfo);

         cross.renameBindingCol(CALC, "Scratch2");
         assertEquals("Scratch2", ainfo.getGroups()[1].getDataRef().getAttribute());

         cross.removeBindingCol("Scratch2");
         assertEquals(1, ainfo.getGroups().length);
         assertEquals("Region", ainfo.getGroups()[0].getDataRef().getAttribute());
      }

      // the shape CardinalityExecutor leaves behind: a hidden probe on the temp chart's source
      // with Count/Distinct Count aggregates and no AggregateInfo
      private CrosstabVSAssembly addProbe() {
         SourceInfo source = ((ChartVSAssembly) vs.getAssembly(VSWizardConstants.TEMP_CHART_NAME))
            .getSourceInfo();
         CrosstabVSAssembly probe = WizardDataExecutor.getTempCrosstab(box, source);
         VSAggregateRef count = aggregate(AggregateFormula.COUNT_ALL);
         VSAggregateRef distinct = aggregate(AggregateFormula.COUNT_DISTINCT);
         probe.getVSCrosstabInfo().setDesignAggregates(new DataRef[] { count, distinct });
         assertNull(probe.getVSCrosstabInfo().getAggregateInfo());
         return probe;
      }

      // a crosstab with a source and a binding whose AggregateInfo was never built
      private CrosstabVSAssembly addCrosstabWithoutAggregateInfo() {
         CrosstabVSAssembly cross = new CrosstabVSAssembly(vs, "Crosstab1");
         cross.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, TABLE));
         cross.getVSCrosstabInfo().setDesignAggregates(
            new DataRef[] { aggregate(AggregateFormula.SUM) });
         vs.addAssembly(cross);
         return cross;
      }

      private static VSAggregateRef aggregate(AggregateFormula formula) {
         VSAggregateRef ref = new VSAggregateRef();
         ref.setColumnValue(CALC);
         ref.setFormula(formula);
         return ref;
      }

      private static ModifyCalculateFieldEvent removeEvent(boolean wizard) {
         ModifyCalculateFieldEvent event = mock(ModifyCalculateFieldEvent.class);
         // not an existing assembly, so the chart/crosstab specific refresh is skipped
         when(event.name()).thenReturn("NoSuchAssembly");
         when(event.tableName()).thenReturn(TABLE);
         when(event.refName()).thenReturn(CALC);
         when(event.remove()).thenReturn(true);
         when(event.wizard()).thenReturn(wizard);
         return event;
      }

      private static ModifyCalculateFieldEvent modifyEvent(CalculateRef cref) {
         CalculateRefModel model = mock(CalculateRefModel.class);
         when(model.createDataRef()).thenReturn(cref);
         ModifyCalculateFieldEvent event = mock(ModifyCalculateFieldEvent.class);
         when(event.name()).thenReturn("NoSuchAssembly");
         when(event.tableName()).thenReturn(TABLE);
         when(event.refName()).thenReturn(CALC);
         when(event.calculateRef()).thenReturn(model);
         return event;
      }

      private List<ViewsheetCommand> sentCommands() {
         ArgumentCaptor<ViewsheetCommand> sent = ArgumentCaptor.forClass(ViewsheetCommand.class);
         verify(dispatcher, atLeast(0)).sendCommand(sent.capture());
         return sent.getAllValues();
      }

      private List<String> appliedNames() throws Exception {
         ArgumentCaptor<VSAssemblyInfo> infos = ArgumentCaptor.forClass(VSAssemblyInfo.class);
         verify(handler, atLeast(0)).apply(
            any(), infos.capture(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            any(), any(), any(), any(), any());
         return infos.getAllValues().stream().map(VSAssemblyInfo::getName)
            .collect(Collectors.toList());
      }

      private Viewsheet vs;
      private ViewsheetSandbox box;
      private Principal principal;
      private VSAssemblyInfoHandler handler;
      private CommandDispatcher dispatcher;
      private ModifyCalculateFieldService service;
   }
}
