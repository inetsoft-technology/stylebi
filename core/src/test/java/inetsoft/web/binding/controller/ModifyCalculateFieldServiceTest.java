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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.drm.CalculateRefModel;
import inetsoft.web.binding.event.ModifyCalculateFieldEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import inetsoft.web.viewsheet.controller.VSRefreshController;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.command.RefreshWizardTreeTriggerCommand;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.recommender.execution.WizardDataExecutor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78557: removing (or renaming, or changing the type of) a calc field did nothing when
 * the viewsheet held a wizard _temp_crosstab_N probe on the same table. The probe has no
 * AggregateInfo, CrosstabVSAssembly.removeBindingCol threw an NPE, and the catch sent an
 * empty MessageCommand that crashed the client.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModifyCalculateFieldServiceTest {
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
         null, mock(VSBindingTreeController.class), null, null, null,
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

   @Test
   void crosstabWithAggregateInfoStillSyncsIt() {
      CrosstabVSAssembly cross = addCrosstabWithoutAggregateInfo();
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addAggregate(new AggregateRef(new ColumnRef(new AttributeRef(CALC)),
                                          AggregateFormula.SUM));
      ainfo.addGroup(new GroupRef(new ColumnRef(new AttributeRef("Region"))));
      ainfo.addGroup(new GroupRef(new ColumnRef(new AttributeRef(CALC))), false);
      cross.getVSCrosstabInfo().setAggregateInfo(ainfo);

      cross.renameBindingCol(CALC, "Scratch2");
      assertEquals("Scratch2", ainfo.getAggregates()[0].getDataRef().getAttribute());
      assertEquals("Scratch2", ainfo.getGroups()[1].getDataRef().getAttribute());

      cross.removeBindingCol("Scratch2");
      assertEquals(0, ainfo.getAggregates().length);
      assertEquals(1, ainfo.getGroups().length);
      assertEquals("Region", ainfo.getGroups()[0].getDataRef().getAttribute());
      assertEquals(0, cross.getVSCrosstabInfo().getDesignAggregates().length);
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
