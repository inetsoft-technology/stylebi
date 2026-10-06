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
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.SelectionListPropertyDialogModel;
import inetsoft.web.composer.model.vs.SelectionTreePropertyDialogModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/**
 * The size checkbox is offered only for a marked list or tree shown as a list and not inside a
 * selection container.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class SizeFollowsDensityReadTest {
   @BeforeEach
   void setup() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      treeService = new SelectionTreePropertyDialogService(
         vsObjectPropertyService, vsOutputService, engine, trapService, dialogService,
         vsSelectionService, selectionDialogService, assemblyInfoHandler, dataRefService,
         dataSourceRegistry, queryManagerService);
      listService = new SelectionListPropertyDialogService(
         vsObjectPropertyService, vsOutputService, engine, trapService, dialogService,
         selectionDialogService, assemblyInfoHandler, dataRefService, dataSourceRegistry,
         queryManagerService);
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void treeIsOfferedOnlyWhenMarkedShownAsAListAndOutsideAContainer() throws Exception {
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();
      seed(info, SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      wire(info, treeAssembly);
      assertEquals(Boolean.TRUE, treeRead());

      lenient().when(treeAssembly.getContainer()).thenReturn(container);
      assertNull(treeRead());

      lenient().when(treeAssembly.getContainer()).thenReturn(null);
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      assertNull(treeRead());
   }

   @Test
   void listIsOfferedOnlyWhenMarkedShownAsAListAndOutsideAContainer() throws Exception {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      seed(info, SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      wire(info, listAssembly);
      assertEquals(Boolean.TRUE, listRead());

      lenient().when(listAssembly.getContainer()).thenReturn(container);
      assertNull(listRead());

      lenient().when(listAssembly.getContainer()).thenReturn(null);
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      assertNull(listRead());
   }

   private static void seed(SelectionBaseVSAssemblyInfo info, int showType) {
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setShowTypeValue(showType);
      info.setPixelSize(VSDensityDefaults.selectionSize(VizContext.of(info)));
   }

   private Boolean treeRead() throws Exception {
      SelectionTreePropertyDialogModel model =
         treeService.getSelectionTreePropertyModel("Viewsheet1", "SelectionTree1", null);
      return model.getSelectionGeneralPaneModel().getSizePositionPaneModel()
         .getSizeFollowsDensity();
   }

   private Boolean listRead() throws Exception {
      SelectionListPropertyDialogModel model =
         listService.getSelectionListPropertyModel("Viewsheet1", "SelectionList1", null);
      return model.getSelectionGeneralPaneModel().getSizePositionPaneModel()
         .getSizeFollowsDensity();
   }

   private void wire(VSAssemblyInfo info, VSAssembly assembly) throws Exception {
      lenient().when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      lenient().when(rvs.getViewsheet()).thenReturn(viewsheet);
      lenient().when(viewsheet.getAssembly(anyString())).thenReturn(assembly);
      lenient().when(viewsheet.getAssemblies()).thenReturn(new Assembly[0]);
      lenient().when(viewsheet.getPixelSize(any()))
         .thenAnswer(inv -> ((AssemblyInfo) inv.getArgument(0)).getPixelSize());
      lenient().when(assembly.getVSAssemblyInfo()).thenReturn(info);
      lenient().when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      lenient().when(dialogService.getAssemblySize(any(), any()))
         .thenAnswer(inv -> ((AssemblyInfo) inv.getArgument(0)).getPixelSize());
   }

   @Mock VSOutputService vsOutputService;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetService engine;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSTrapService trapService;
   @Mock SelectionTreeVSAssembly treeAssembly;
   @Mock SelectionListVSAssembly listAssembly;
   @Mock CurrentSelectionVSAssembly container;
   @Mock VSDialogService dialogService;
   @Mock VSSelectionService vsSelectionService;
   @Mock SelectionDialogService selectionDialogService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock DataRefModelFactoryService dataRefService;
   @Mock DataSourceRegistry dataSourceRegistry;
   @Mock QueryManagerService queryManagerService;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS)
   private Viewsheet viewsheet;
   private SelectionTreePropertyDialogService treeService;
   private SelectionListPropertyDialogService listService;
}
