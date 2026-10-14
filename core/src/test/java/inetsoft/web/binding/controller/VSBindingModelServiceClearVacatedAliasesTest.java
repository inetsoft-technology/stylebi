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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.handler.CrosstabBindingHandler;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78530: the wiz table tools ask setBinding to clear the header aliases of vacated crosstab
 * header paths, with the same processor native drag-and-drop passes. The native setbinding event
 * leaves the flag off and must keep using the processor-less apply.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSBindingModelServiceClearVacatedAliasesTest {
   @Test
   void passesTheClearAliasProcessorOnlyWhenTheEventAsksForIt() throws Exception {
      VSAssemblyInfoHandler handler = mock(VSAssemblyInfoHandler.class);
      VSBindingModelService service = service(handler);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      service.setBinding("rt1", event(true), principal(), dispatcher);

      verify(handler).apply(any(RuntimeViewsheet.class), any(VSAssemblyInfo.class),
                            any(ViewsheetService.class), eq(false), eq(false), eq(false),
                            eq(false), eq(dispatcher), isNull(), isNull(), isNull(), isNull(),
                            notNull());
      verify(handler, never()).apply(any(RuntimeViewsheet.class), any(VSAssemblyInfo.class),
                                     any(ViewsheetService.class), anyBoolean(), anyBoolean(),
                                     anyBoolean(), anyBoolean(), any(CommandDispatcher.class));
   }

   @Test
   void theNativeEventKeepsTheProcessorLessApply() throws Exception {
      VSAssemblyInfoHandler handler = mock(VSAssemblyInfoHandler.class);
      VSBindingModelService service = service(handler);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      service.setBinding("rt1", event(false), principal(), dispatcher);

      verify(handler).apply(any(RuntimeViewsheet.class), any(VSAssemblyInfo.class),
                            any(ViewsheetService.class), eq(false), eq(false), eq(false),
                            eq(false), eq(dispatcher));
      verify(handler, never()).apply(any(RuntimeViewsheet.class), any(VSAssemblyInfo.class),
                                     any(ViewsheetService.class), anyBoolean(), anyBoolean(),
                                     anyBoolean(), anyBoolean(), any(CommandDispatcher.class),
                                     any(), any(), any(), any(), any());
   }

   private static ApplyVSAssemblyInfoEvent event(boolean clearVacatedAliases) {
      ApplyVSAssemblyInfoEvent event = new ApplyVSAssemblyInfoEvent();
      event.setName("Text1");
      event.setBinding(mock(BindingModel.class));
      event.setCheckTrap(false);
      event.setClearVacatedAliases(clearVacatedAliases);
      return event;
   }

   private static VSBindingModelService service(VSAssemblyInfoHandler handler) throws Exception {
      VSAssemblyInfo info = mock(VSAssemblyInfo.class);
      when(info.clone()).thenReturn(info);
      TextVSAssembly assembly = mock(TextVSAssembly.class);
      when(assembly.getInfo()).thenReturn(info);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.clone()).thenReturn(assembly);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Text1")).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getViewsheet(anyString(), any())).thenReturn(rvs);

      VSBindingService bfactory = mock(VSBindingService.class);
      when(bfactory.updateAssembly(any(), any(), any())).thenReturn(assembly);

      return new VSBindingModelService(bfactory, handler, mock(CrosstabBindingHandler.class),
                                       mock(VSObjectModelFactoryService.class),
                                       mock(VSWizardTemporaryInfoService.class), engine);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
