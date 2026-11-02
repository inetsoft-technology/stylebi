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
import inetsoft.web.composer.model.vs.SelectionTreePropertyDialogModel;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Switching a selection tree from dropdown to list rebuilds its box. A marked tree the author
 * never sized takes its tier's density box; one the author sized makes room for its rows, title
 * lane and card inset at its density; an unmarked tree keeps the legacy 20px arithmetic.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class SelectionTreeShowTypeSizeTest {
   @BeforeEach
   void setup() {
      service = new SelectionTreePropertyDialogService(
         vsObjectPropertyService, vsOutputService, engine, trapService, dialogService,
         vsSelectionService, selectionDialogService, assemblyInfoHandler, dataRefService,
         dataSourceRegistry, queryManagerService);
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   // never sized: the List box is the tier box, and the dropdown keeps its own six rows
   @ParameterizedTest
   @CsvSource({ "comfortable, 202", "compact, 170", "dense, 136" })
   void aFreshMarkedTreeSwitchedToListTakesItsTierBox(String density, int height)
      throws Exception
   {
      SelectionTreeVSAssemblyInfo saved = switchedToList(seededTree(density));

      assertEquals(height, saved.getPixelSize().height);
      assertEquals(6, saved.getListHeight());
   }

   // title hidden: the tier box already holds the lane, so the box does not come back a lane
   // short; and the dropdown's eight rows do not size it
   @ParameterizedTest
   @CsvSource({ "comfortable, 202", "compact, 170", "dense, 136" })
   void aMarkedTreeWithAHiddenTitleSwitchedToListTakesItsTierBox(String density, int height)
      throws Exception
   {
      SelectionTreeVSAssemblyInfo info = seededTree(density);
      info.setTitleVisibleValue(false);
      info.setListHeight(8);

      assertEquals(height, switchedToList(info).getPixelSize().height);
   }

   // the author's box, holding six rows: lane 30 + 6 x 28 + 16 + 16
   @Test
   void anAuthorSizedMarkedTreeSwitchedToListKeepsItsBox() throws Exception {
      SelectionTreeVSAssemblyInfo info = seededTree("comfortable");
      info.setPixelSize(new Dimension(132, 230));
      info.setUserSize(true);
      info.setListHeight(6);

      assertEquals(230, switchedToList(info).getPixelSize().height);
   }

   // title hidden: the rebuild still counts the title lane, as a list's does, so the author's box
   // does not come back a lane short
   @Test
   void anAuthorSizedMarkedTreeWithAHiddenTitleKeepsTheTitleLane() throws Exception {
      SelectionTreeVSAssemblyInfo info = seededTree("comfortable");
      info.setTitleVisibleValue(false);
      info.setUserSize(true);
      info.setListHeight(6);

      assertEquals(30 + 6 * 28 + 32, switchedToList(info).getPixelSize().height);
   }

   // unmarked: six 20px rows and a 20px cell for the title, exactly as before
   @Test
   void switchingAnUnmarkedTreeToListKeepsTheLegacyHeight() throws Exception {
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();

      assertEquals(6 * 20 + 20, switchedToList(info).getPixelSize().height);
   }

   // dragged to 342, then Follow the default density ticked: the box goes back to 202 and the
   // dropdown's ten rows stay
   @Test
   void followingTheDensitySizeLeavesATreesRowsAlone() throws Exception {
      SelectionTreeVSAssemblyInfo info = seededTree("comfortable");
      info.setPixelSize(new Dimension(132, 342));
      info.setUserSize(true);
      info.setListHeight(10);
      wire(info);

      SelectionTreePropertyDialogModel model =
         service.getSelectionTreePropertyModel("Viewsheet1", "SelectionTree1", null);
      model.getSelectionGeneralPaneModel().getSizePositionPaneModel().setSizeFollowsDensity(true);
      SelectionTreeVSAssemblyInfo saved = submit(model);

      assertEquals(new Dimension(132, 202), saved.getPixelSize());
      assertEquals(10, saved.getListHeight());
   }

   // a typed height refits the rows as a list's does: 300 - 30 holds nine whole 28px rows (an author-sized box takes no vertical inset, #79565),
   // so the box comes back from Dropdown at 30 + 9 x 28 = 282, not the 300 typed
   @Test
   void aHeightTypedInSizeAndPositionRefitsATreesRows() throws Exception {
      SelectionTreeVSAssemblyInfo info = seededTree("comfortable");
      wire(info);
      lenient().when(dialogService.getAssemblySize(any(), any()))
         .thenAnswer(inv -> ((AssemblyInfo) inv.getArgument(0)).getPixelSize());
      lenient().doAnswer(inv -> {
         SizePositionPaneModel typed = inv.getArgument(1);
         ((VSAssemblyInfo) inv.getArgument(0))
            .setPixelSize(new Dimension(typed.getWidth(), typed.getHeight()));
         return null;
      }).when(dialogService).setAssemblySize(any(VSAssemblyInfo.class), any(SizePositionPaneModel.class));
      SelectionTreePropertyDialogModel model =
         service.getSelectionTreePropertyModel("Viewsheet1", "SelectionTree1", null);
      SizePositionPaneModel size = model.getSelectionGeneralPaneModel().getSizePositionPaneModel();
      size.setSizeFollowsDensity(false);
      size.setHeight(300);

      SelectionTreeVSAssemblyInfo saved = submit(model);

      assertTrue(saved.isUserSize());
      assertEquals(new Dimension(132, 300), saved.getPixelSize());
      assertEquals(9, saved.getListHeight());

      Mockito.reset(dialogService, viewsheet);
      clearInvocations(vsObjectPropertyService);
      SelectionTreeVSAssemblyInfo listed = switchedToList(saved);

      assertEquals(282, listed.getPixelSize().height);
      assertEquals(9, listed.getListHeight());
   }

   private SelectionTreeVSAssemblyInfo seededTree(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      VizModernizeUtil.reseedAfterRestore(info);
      return info;
   }

   // a dropdown, its box its title lane, switched to a list with the rows it has. The real
   // getPixelSize hands back the info's own size, which is what the show-type transition resizes
   // in place
   private SelectionTreeVSAssemblyInfo switchedToList(SelectionTreeVSAssemblyInfo info)
      throws Exception
   {
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setPixelSize(new Dimension(info.getPixelSize().width, info.getTitleHeight()));
      wire(info);
      SelectionTreePropertyDialogModel model =
         service.getSelectionTreePropertyModel("Viewsheet1", "SelectionTree1", null);
      model.getSelectionGeneralPaneModel().setShowType(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      return submit(model);
   }

   private SelectionTreeVSAssemblyInfo submit(SelectionTreePropertyDialogModel model)
      throws Exception
   {
      model.getSelectionGeneralPaneModel().getGeneralPropPaneModel().getBasicGeneralPaneModel()
         .setName("SelectionTree1");
      service.setSelectionTreePropertyModel("Viewsheet1", "SelectionTree1", model, "", null,
                                            commandDispatcher);
      ArgumentCaptor<SelectionTreeVSAssemblyInfo> captor =
         ArgumentCaptor.forClass(SelectionTreeVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), any(), any(), any(),
         nullable(Principal.class), any(CommandDispatcher.class));
      return captor.getValue();
   }

   private void wire(SelectionTreeVSAssemblyInfo info) throws Exception {
      lenient().when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      lenient().when(rvs.getViewsheet()).thenReturn(viewsheet);
      lenient().when(viewsheet.getAssembly(anyString())).thenReturn(selectionTreeAssembly);
      lenient().when(viewsheet.getAssemblies()).thenReturn(new Assembly[0]);
      lenient().when(viewsheet.getPixelSize(any()))
         .thenAnswer(inv -> ((AssemblyInfo) inv.getArgument(0)).getPixelSize());
      lenient().when(selectionTreeAssembly.getVSAssemblyInfo()).thenReturn(info);
      lenient().when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      lenient().when(dialogService.getAssemblySize(any(), any())).thenReturn(new Dimension(200, 140));
   }

   @Mock VSOutputService vsOutputService;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetService engine;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSTrapService trapService;
   @Mock SelectionTreeVSAssembly selectionTreeAssembly;
   @Mock VSDialogService dialogService;
   @Mock VSSelectionService vsSelectionService;
   @Mock SelectionDialogService selectionDialogService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock DataRefModelFactoryService dataRefService;
   @Mock DataSourceRegistry dataSourceRegistry;
   @Mock QueryManagerService queryManagerService;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS)
   private Viewsheet viewsheet;
   private SelectionTreePropertyDialogService service;
}
