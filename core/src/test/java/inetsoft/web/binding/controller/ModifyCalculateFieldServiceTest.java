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
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.OutputVSAssembly;
import inetsoft.uql.viewsheet.ScalarBindingInfo;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
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
import inetsoft.web.viewsheet.controller.VSRefreshController;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Optional;

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
}
