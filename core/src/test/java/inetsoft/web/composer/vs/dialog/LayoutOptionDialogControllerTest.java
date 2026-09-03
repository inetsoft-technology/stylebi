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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.LayoutOptionDialogModel;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.controller.GroupingService;
import inetsoft.web.composer.vs.objects.controller.VSTableService;
import inetsoft.web.composer.vs.objects.event.LockVSObjectEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class LayoutOptionDialogControllerTest {

   @BeforeEach
   void setup() throws Exception {
      controller = new LayoutOptionDialogController(runtimeViewsheetRef, groupingService,
                                                    vsObjectTreeService, engine, vsTableService, coreLifecycleService);
   }

   @Test
   void tabbedInterfaceTest() throws Exception {
      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(assembly);
      when(model.getSelectedValue()).thenReturn(2);
      when(model.getNewObjectType()).thenReturn(-1);
      when(model.getObject()).thenReturn("Table1");
      when(model.getTarget()).thenReturn("Table1");

      controller.setLayoutOptionDialogModel(model, principal, null, dispatcher);
      verify(vsObjectTreeService, times(1)).getObjectTree(rvs);
   }

   // Bug #76403: dragging a multi-selection into a container should group every
   // additional assembly into the same target, not just the primary one.
   @Test
   void additionalObjectsAreGroupedTest() throws Exception {
      VSAssembly targetAssembly = mock(VSAssembly.class);
      VSAssembly additionalAssembly1 = mock(VSAssembly.class);
      VSAssembly additionalAssembly2 = mock(VSAssembly.class);

      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly("Object1")).thenReturn(assembly);
      when(viewsheet.getAssembly("Container1")).thenReturn(targetAssembly);
      when(viewsheet.getAssembly("Object2")).thenReturn(additionalAssembly1);
      when(viewsheet.getAssembly("Object3")).thenReturn(additionalAssembly2);
      when(targetAssembly.getAbsoluteName()).thenReturn("Container1");
      when(model.getSelectedValue()).thenReturn(1);
      when(model.getNewObjectType()).thenReturn(-1);
      when(model.getObject()).thenReturn("Object1");
      when(model.getTarget()).thenReturn("Container1");
      when(model.getAdditionalObjects()).thenReturn(List.of("Object2", "Object3"));

      controller.setLayoutOptionDialogModel(model, principal, null, dispatcher);

      verify(groupingService, times(1))
         .groupComponents(rvs, targetAssembly, assembly, true, null, dispatcher);
      verify(groupingService, times(1))
         .groupComponents(rvs, targetAssembly, additionalAssembly1, true, null, dispatcher);
      verify(groupingService, times(1))
         .groupComponents(rvs, targetAssembly, additionalAssembly2, true, null, dispatcher);
   }

   // Bug #76403: an additionalObjects entry that duplicates the primary object or the
   // target itself must not be grouped a second time.
   @Test
   void additionalObjectsSkipDuplicatesTest() throws Exception {
      VSAssembly targetAssembly = mock(VSAssembly.class);

      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly("Object1")).thenReturn(assembly);
      when(viewsheet.getAssembly("Container1")).thenReturn(targetAssembly);
      when(targetAssembly.getAbsoluteName()).thenReturn("Container1");
      when(model.getSelectedValue()).thenReturn(1);
      when(model.getNewObjectType()).thenReturn(-1);
      when(model.getObject()).thenReturn("Object1");
      when(model.getTarget()).thenReturn("Container1");
      when(model.getAdditionalObjects()).thenReturn(List.of("Object1", "Container1"));

      controller.setLayoutOptionDialogModel(model, principal, null, dispatcher);

      verify(groupingService, times(1))
         .groupComponents(eq(rvs), eq(targetAssembly), any(VSAssembly.class), eq(true), isNull(),
                          eq(dispatcher));
   }

   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock GroupingService groupingService;
   @Mock ViewsheetService engine;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock VSAssembly assembly;
   @Mock LockVSObjectEvent event;
   @Mock Principal principal;
   @Mock CommandDispatcher dispatcher;
   @Mock LayoutOptionDialogModel model;
   @Mock VSTableService vsTableService;
   @Mock
   CoreLifecycleService coreLifecycleService;
   @Mock VSAssemblyInfoHandler infoHandler;
   @Mock ViewsheetService viewsheetService;

   private LayoutOptionDialogController controller;
}
