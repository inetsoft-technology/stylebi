/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.*;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.objects.event.ChangeVSObjectBindingEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import static org.mockito.ArgumentMatchers.nullable;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComposerBindingControllerTest {

   @BeforeEach
   void setup() throws Exception {
      ConfigurationContext context = ConfigurationContext.getContext();
      ConfigurationContext  spyContext = Mockito.spy(context);
      staticConfigurationContext = Mockito.mockStatic(ConfigurationContext.class);
      staticConfigurationContext.when(ConfigurationContext::getContext)
         .thenReturn(spyContext);
      vsutil = Mockito.mockStatic(VSUtil.class);

      VSBindingService vsBindService = new VSBindingService(trapService, vsTableService, groupingService,
                                                            viewsheetService, factories, dataRefService,
                                                            objectModelService, wizardTemporaryInfoService,
                                                            vsSelectionContainerService, analyticAssistant,
                                                            assemblyHandler, vsObjectTreeService, coreLifecycleService);
      doReturn(vsBindService)
         .when(spyContext)
            .getSpringBean(VSBindingService.class);

      controller = spy(new ComposerBindingController(runtimeViewsheetRef, new VSBindingServiceProxy()));

      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("Viewsheet1");
      when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(viewsheetService.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);

      SelectionListVSAssembly mockAssembly = mock(SelectionListVSAssembly.class);
      VSAssemblyInfo listInfoSpy = spy(new SelectionListVSAssemblyInfo());
      when(vsTableService.createSelectionVSAssembly(
         nullable(Viewsheet.class), anyInt(), anyString(), anyString(), anyList(),
         nullable(ColumnSelection.class)))
         .thenReturn(mockAssembly);
      when(mockAssembly.getInfo()).thenReturn(listInfoSpy);
      TableVSAssembly mockTableAssembly = mock(TableVSAssembly.class);
      when(vsTableService.createTable(nullable(RuntimeViewsheet.class), nullable(ViewsheetService.class),
                                      nullable(AssetEntry.class), anyInt(), anyInt()))
         .thenReturn(mockTableAssembly);
      VSAssemblyInfo infoSpy = spy(new TableVSAssemblyInfo());
      when(mockTableAssembly.getInfo()).thenReturn(infoSpy);
      when(vsBindingService.getNewAssemblyFromBindings(
         nullable(List.class), anyInt(), anyInt(), nullable(RuntimeViewsheet.class),
         nullable(Principal.class)))
         .thenReturn(mockAssembly);
      VSAssembly vsAssembly = mock(VSAssembly.class);
      VSAssemblyInfo vsAssemblyInfo = mock(VSAssemblyInfo.class);
      when(vsAssembly.getInfo()).thenReturn(vsAssemblyInfo);
   }

   @AfterEach
   void afterEach() throws Exception {
      staticConfigurationContext.close();
      vsutil.close();
   }

   @Test
   void calendarDateBinding() throws Exception {
      CalendarVSAssemblyInfo infoSpy = spy(new CalendarVSAssemblyInfo());
      CalendarVSAssembly calendarVSAssembly = spy(new CalendarVSAssembly());
      when(calendarVSAssembly.getVSAssemblyInfo()).thenReturn(infoSpy);

      when(viewsheet.getAssembly(anyString())).thenReturn(calendarVSAssembly);

      ChangeVSObjectBindingEvent eventModel = new ChangeVSObjectBindingEvent();
      List<AssetEntry> entries = new ArrayList<>();
      AssetEntry bindingEntry = new AssetEntry(0, AssetEntry.Type.COLUMN, "", null);
      entries.add(bindingEntry);
      bindingEntry.setProperty("assembly", "");
      bindingEntry.setProperty("entity", "");
      bindingEntry.setProperty("attribute", "");
      bindingEntry.setProperty("refType", "0");
      bindingEntry.setProperty("caption", "");
      bindingEntry.setProperty("dtype", Tool.DATE);
      eventModel.setBinding(entries);
      eventModel.setName("Calendar1");
      TimeSliderVSAssembly rangeSliderAssembly = spy(new TimeSliderVSAssembly());
      when(vsBindingService.getNewAssemblyFromBindings(
         eventModel.getBinding(), 0, 0, rvs, null)).thenReturn(rangeSliderAssembly);

      controller.changeBinding(eventModel, null, dispatcher, linkUri);

      verify(infoSpy).setTableName(anyString());
   }

   // Bug #16854 Populate object tree after creating assembly from binding
   @SuppressWarnings("unchecked")
   @Test
   void populateObjectTree() throws Exception {
      ChangeVSObjectBindingEvent eventModel = new ChangeVSObjectBindingEvent();
      List<AssetEntry> entries = new ArrayList<>();
      AssetEntry bindingEntry = new AssetEntry(0, AssetEntry.Type.COLUMN, "", null);
      entries.add(bindingEntry);
      bindingEntry.setProperty("assembly", "");
      bindingEntry.setProperty("entity", "");
      bindingEntry.setProperty("attribute", "");
      bindingEntry.setProperty("refType", "0");
      bindingEntry.setProperty("caption", "");
      bindingEntry.setProperty("dtype", Tool.DATE);
      eventModel.setBinding(entries);

      controller.changeBinding(eventModel, null, dispatcher, linkUri);

      verify(dispatcher).sendCommand(any(PopulateVSObjectTreeCommand.class));
   }

   // Bug #17667 Don't add assembly when checking trap
   @Test
   void doNotAddAssemblyWhenTrapCheck() throws Exception {
      ChangeVSObjectBindingEvent eventModel = new ChangeVSObjectBindingEvent();
      List<AssetEntry> entries = new ArrayList<>();
      AssetEntry bindingEntry = spy(new AssetEntry(0, AssetEntry.Type.TABLE, "/foobar", null));
      entries.add(bindingEntry);
      bindingEntry.setProperty("assembly", "");
      bindingEntry.setProperty("entity", "");
      bindingEntry.setProperty("attribute", "");
      bindingEntry.setProperty("refType", "0");
      bindingEntry.setProperty("caption", "");
      bindingEntry.setProperty("source", VSEventUtil.BASE_WORKSHEET);
      eventModel.setBinding(entries);
      when(bindingEntry.getName()).thenReturn("foobar");
      Worksheet worksheet = spy(new Worksheet());
      QueryBoundTableAssembly tableAssembly = spy(new QueryBoundTableAssembly());
      TimeSliderVSAssembly rangeSliderAssembly = spy(new TimeSliderVSAssembly());
      when(tableAssembly.getName()).thenReturn("foobar");
      when(worksheet.getAssembly(anyString())).thenReturn(tableAssembly);
      when(viewsheet.getBaseWorksheet()).thenReturn(worksheet);
      when(trapService.checkTrap(nullable(RuntimeViewsheet.class), nullable(VSAssemblyInfo.class),
                                 nullable(VSAssemblyInfo.class)))
         .thenReturn(null);
      when(vsBindingService.getNewAssemblyFromBindings(
         eventModel.getBinding(), 0, 0, rvs, null)).thenReturn(rangeSliderAssembly);

      controller.checkVSTrap(eventModel, "Viewsheet1", linkUri, null);

      verify(viewsheet, never()).addAssembly(nullable(VSAssembly.class));
   }

   // Bug #17667 Don't add assembly when checking trap
   @Test
   void variableBindingWorksProperly() throws Exception {
      ChangeVSObjectBindingEvent eventModel = new ChangeVSObjectBindingEvent();

      List<AssetEntry> entries = new ArrayList<>();
      AssetEntry bindingEntry = new AssetEntry(0, AssetEntry.Type.VARIABLE, "", null);
      bindingEntry.setProperty("assembly", "");
      bindingEntry.setProperty("entity", "");
      bindingEntry.setProperty("attribute", "");
      bindingEntry.setProperty("refType", "0");
      bindingEntry.setProperty("caption", "");
      bindingEntry.setProperty("source", VSEventUtil.BASE_WORKSHEET);
      entries.add(bindingEntry);

      eventModel.setBinding(entries);

      Worksheet worksheet = spy(new Worksheet());
      when(viewsheet.getBaseWorksheet()).thenReturn(worksheet);
      when(rvs.getViewsheetSandbox()).thenReturn(viewsheetSandbox);
      AssetVariable variable = new AssetVariable();
      VariableAssembly variableAssembly =
         new DefaultVariableAssembly(worksheet, "Variable");
      variableAssembly.setVariable(variable);
      when(worksheet.getAssembly(anyString()))
         .thenReturn(variableAssembly);

      controller.changeBinding(eventModel, null, dispatcher, "");

      verify(coreLifecycleService)
         .execute(eq(rvs), anyString(), eq(""), anyInt(), eq(dispatcher));
   }

   @Mock ViewsheetEngine viewsheetEngine;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock CommandDispatcher dispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock
   CoreLifecycleService coreLifecycleService;
   @Mock VSTableService vsTableService;
   @Mock GroupingService groupingService;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock VSTrapService trapService;
   @Mock ViewsheetSandbox viewsheetSandbox;
   @Mock VSAssemblyInfoHandler assemblyHandler;
   @Mock VSBindingService vsBindingService;
   @Mock AnalyticAssistant analyticAssistant;
   @Mock ViewsheetService viewsheetService;
   @Mock List<VSBindingFactory<?, ?>> factories;
   @Mock DataRefModelFactoryService dataRefService;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock VSWizardTemporaryInfoService wizardTemporaryInfoService;
   @Mock VSSelectionContainerService vsSelectionContainerService;
   MockedStatic<ConfigurationContext> staticConfigurationContext;
   MockedStatic<VSUtil> vsutil;

   private ComposerBindingController controller;
   private final String linkUri = "http://localhost:18080/sree/";
}
