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
package inetsoft.web.wiz.binding;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.test.*;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.VSCrosstabBindingFactory;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #79022 review r1 #2: a crosstab bound to another viewsheet component through the agent
 * tool path renders the component's data.
 *
 * <p>Runs on a really opened runtime viewsheet (an embedded worksheet table shown by the table
 * assembly {@code TableView1}). The crosstab's binding is written by the real
 * {@link TableBindingService#setShelf} with the source string {@code set_table_fields} resolves
 * for a component ({@code __vs_assembly__TableView1}), applied onto the live assembly by the real
 * {@link VSCrosstabBindingFactory} plus the same {@code SourceInfo.toSourceAttr} step
 * {@code VSBindingService.updateAssembly} runs, and then executed by the real
 * {@link ViewsheetSandbox#getData} query path. Only the Spring services this context does not
 * carry (the session lookup, the binding-model service wrapper, its model read) are stand-ins,
 * each delegating to the same real factory.
 *
 * <p>The measure is sent with no aggregate, so the type of the component's column must also be
 * resolved from its real output (review r1 #1): a Count would render a different header and
 * values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class TableBindingComponentSourceRenderTest {
   @Test
   void aCrosstabBoundToATableComponentRendersTheComponentsData() throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      String worksheetTable =
         ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().getSource();
      CrosstabVSAssembly viaComponent = new CrosstabVSAssembly(vs, "CrosstabVS");
      CrosstabVSAssembly viaWorksheet = new CrosstabVSAssembly(vs, "CrosstabWS");
      vs.addAssembly(viaComponent);
      vs.addAssembly(viaWorksheet);
      TableBindingService service = service(rvs);
      FieldRef wind = new FieldRef("wind", "measure", null, null, null);
      FieldRef type = new FieldRef("type", "dimension", null, null, null);

      service.setShelf("tok", principal(), "CrosstabVS", "rows", List.of(type),
                       BindingSources.componentSource(TABLE));
      service.setShelf("tok", principal(), "CrosstabVS", "aggregates", List.of(wind), null);
      service.setShelf("tok", principal(), "CrosstabWS", "rows", List.of(type), worksheetTable);
      // stated: this stand-in model read carries no worksheet tables to type the column from
      service.setShelf("tok", principal(), "CrosstabWS", "aggregates",
                       List.of(new FieldRef("wind", "measure", "Sum", null, null)), null);

      assertEquals(SourceInfo.VS_ASSEMBLY, viaComponent.getSourceInfo().getType());
      assertEquals("__vs_assembly__" + TABLE, viaComponent.getSourceInfo().getSource());

      box.resetDataMap("CrosstabVS");
      box.resetDataMap("CrosstabWS");
      String rendered = cells(box.getData("CrosstabVS"));

      assertEquals(EXPECTED, rendered, "crosstab bound to the component");
      assertEquals(cells(box.getData("CrosstabWS")), rendered,
                   "the same binding on the component's own worksheet table");
   }

   /**
    * The pieces of {@code TableBindingService}'s runtime this Spring context does not carry,
    * each delegating to the real crosstab binding factory on the live assemblies.
    */
   private static TableBindingService service(RuntimeViewsheet rvs) throws Exception {
      DataRefModelFactoryService refService = refService();
      VSCrosstabBindingFactory factory = new VSCrosstabBindingFactory(refService);
      Viewsheet vs = rvs.getViewsheet();

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", mock(CapturingCommandDispatcher.class));
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      // VSBindingService.createModel: the factory's model plus the assembly's source
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenAnswer(invocation -> {
         CrosstabVSAssembly assembly = invocation.getArgument(0);
         CrosstabBindingModel model = factory.createModel(assembly);
         SourceInfo source = assembly.getSourceInfo();
         model.setSource(source == null || source.isEmpty() ? null
            : new inetsoft.web.binding.model.SourceInfo(source));
         return model;
      });

      // VSBindingService.updateAssembly: the factory, then updateSourceInfo's toSourceAttr
      VSBindingModelService bindings = mock(VSBindingModelService.class);
      doAnswer(invocation -> {
         ApplyVSAssemblyInfoEvent event = invocation.getArgument(1);
         CrosstabBindingModel model = (CrosstabBindingModel) event.getBinding();
         CrosstabVSAssembly assembly = (CrosstabVSAssembly) vs.getAssembly(event.getName());
         factory.updateAssembly(model, assembly);
         assembly.setSourceInfo(model.getSource().toSourceAttr(assembly.getSourceInfo()));
         return null;
      }).when(bindings).setBinding(anyString(), any(), any(Principal.class), any());

      return new TableBindingService(sessions, binding, bindings, refService,
                                     mock(HideColumnsDialogService.class),
                                     mock(VSAssemblyInfoHandler.class));
   }

   private static DataRefModelFactoryService refService() {
      BDimensionRefModel.VSDimensionRefModelFactory dimensionFactory =
         new BDimensionRefModel.VSDimensionRefModelFactory();
      DataRefModelFactoryService service = new DataRefModelFactoryService(
         List.of(dimensionFactory, new AttributeRefModel.AttributeRefModelFactory()));
      dimensionFactory.setDataRefModelFactoryService(service);
      return service;
   }

   private static String cells(Object data) {
      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      assertNotNull(lens, "no data");
      StringBuilder sb = new StringBuilder();
      lens.moreRows(Integer.MAX_VALUE);

      for(int r = 0; r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            sb.append(lens.getObject(r, c)).append(c < lens.getColCount() - 1 ? "|" : "\n");
         }
      }

      return sb.toString();
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private static final String TABLE = "TableView1";
   // The source table's data, as CrosstabVSAssemblySourceRealChainTest reads it too.
   private static final String EXPECTED = "type|Sum(wind)\nNAMED|630312.0\nUNNAMED|23420.0\n";
}
