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

import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class LayoutOptionDialogControllerTest {

   @BeforeEach
   void setup() throws Exception {
      controller = new LayoutOptionDialogController(runtimeViewsheetRef,
                                                    new LayoutOptionDialogServiceProxy());
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
