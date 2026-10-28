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

   /**
    * Bug #79331: on a crosstab the processor keeps a vacated summary header path of a measure
    * that is still bound, and still clears a corner label (bug #78530).
    */
   @Test
   @SuppressWarnings("unchecked")
   void aCrosstabKeepsTheLabelOfAStillBoundMeasure() throws Exception {
      inetsoft.uql.viewsheet.VSAggregateRef sum = new inetsoft.uql.viewsheet.VSAggregateRef();
      sum.setDataRef(new inetsoft.uql.asset.ColumnRef(
         new inetsoft.uql.erm.AttributeRef(null, "REVENUE")));
      sum.setColumnValue("REVENUE");
      sum.setFormulaValue("Sum");
      inetsoft.uql.viewsheet.VSCrosstabInfo cinfo = new inetsoft.uql.viewsheet.VSCrosstabInfo();
      cinfo.setRuntimeAggregates(new inetsoft.uql.erm.DataRef[] { sum });
      String agg = inetsoft.uql.viewsheet.internal.VSUtil.buildName(
         cinfo.getRuntimeAggregates(), true)[0];

      VSAssemblyInfo info = mock(VSAssemblyInfo.class);
      when(info.clone()).thenReturn(info);
      inetsoft.uql.viewsheet.CrosstabVSAssembly crosstab =
         mock(inetsoft.uql.viewsheet.CrosstabVSAssembly.class);
      when(crosstab.getInfo()).thenReturn(info);
      when(crosstab.getVSAssemblyInfo()).thenReturn(info);
      when(crosstab.clone()).thenReturn(crosstab);
      when(crosstab.getVSCrosstabInfo()).thenReturn(cinfo);
      VSAssemblyInfoHandler handler = mock(VSAssemblyInfoHandler.class);

      service(handler, crosstab).setBinding("rt1", event(true), principal(),
                                            mock(CommandDispatcher.class));

      org.mockito.ArgumentCaptor<java.util.function.BiConsumer<java.util.Map<
         inetsoft.report.TableDataPath, inetsoft.uql.viewsheet.VSCompositeFormat>,
         inetsoft.report.TableDataPath>> processor =
         org.mockito.ArgumentCaptor.forClass(java.util.function.BiConsumer.class);
      verify(handler).apply(any(RuntimeViewsheet.class), any(VSAssemblyInfo.class),
                            any(ViewsheetService.class), eq(false), eq(false), eq(false),
                            eq(false), any(), isNull(), isNull(), isNull(), isNull(),
                            processor.capture());

      inetsoft.uql.viewsheet.FormatInfo formats = new inetsoft.uql.viewsheet.FormatInfo();
      inetsoft.report.TableDataPath measure = header("Year", "Quarter", agg);
      inetsoft.report.TableDataPath corner = header("Cell [0,1]");
      inetsoft.web.binding.handler.SetTableHeaderAliasHandler.setAlias(measure, formats, "Rev");
      inetsoft.web.binding.handler.SetTableHeaderAliasHandler.setAlias(corner, formats, "Q");

      processor.getValue().accept(formats.getFormatMap(), measure);
      processor.getValue().accept(formats.getFormatMap(), corner);

      org.junit.jupiter.api.Assertions.assertEquals(
         "Rev", formats.getFormatMap().get(measure).getUserDefinedFormat()
            .getFormatExtentValue());
      org.junit.jupiter.api.Assertions.assertNotEquals(
         inetsoft.uql.viewsheet.VSFormat.MESSAGE_FORMAT,
         formats.getFormatMap().get(corner).getUserDefinedFormat().getFormatValue());
   }

   private static inetsoft.report.TableDataPath header(String... segments) {
      return new inetsoft.report.TableDataPath(-1, inetsoft.report.TableDataPath.HEADER,
                                               inetsoft.uql.schema.XSchema.STRING, segments);
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
      return service(handler, assembly);
   }

   private static VSBindingModelService service(VSAssemblyInfoHandler handler,
                                                inetsoft.uql.viewsheet.VSAssembly assembly)
      throws Exception
   {

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
