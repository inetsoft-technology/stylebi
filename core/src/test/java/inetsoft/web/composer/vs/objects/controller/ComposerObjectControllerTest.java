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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.event.LockVSObjectEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class ComposerObjectControllerTest {
   @BeforeEach
   void setup() throws Exception {
      controller = new ComposerObjectController(runtimeViewsheetRef, new ComposerObjectServiceProxy());
   }

   @Test
   void imageLockTest() throws Exception {
      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(assembly);
      when(event.getName()).thenReturn("Assembly1");

      controller.changeLockState(event, principal, dispatcher);
      verify(vsObjectTreeService, times(1)).getObjectTree(rvs);
   }

   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock
   CoreLifecycleService coreLifecycleService;
   @Mock ViewsheetService engine;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock VSAssembly assembly;
   @Mock LockVSObjectEvent event;
   @Mock Principal principal;
   @Mock CommandDispatcher dispatcher;
   @Mock VSAssemblyInfoHandler assemblyHandler;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock VSObjectService vsObjectService;
   @Mock VSCompositionService vsCompositionService;

   private ComposerObjectController controller;
}
