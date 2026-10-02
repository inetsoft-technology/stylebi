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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
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
import static org.mockito.Mockito.verify;

/**
 * Drives the real list dialog service, with its collaborators mocked, to check the two padding
 * panes on the shared selection general pane.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class SelectionPaddingDialogTest {
   @BeforeEach
   void setup() {
      service = new SelectionListPropertyDialogService(
         vsObjectPropertyService, vsOutputService, engine, trapService, dialogService,
         selectionDialogService, assemblyInfoHandler, dataRefService, dataSourceRegistry);
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void theLoadSideShowsTheSeededValuesAndTheCheckbox() throws Exception {
      SelectionListVSAssemblyInfo info = seededList("comfortable");

      SelectionListPropertyDialogModel model = load(info);
      PaddingPaneModel pane = model.getSelectionGeneralPaneModel().getPaddingPaneModel();

      assertEquals(info.getPadding().top, pane.getTop(), "the pane shows the seeded card inset");
      assertEquals(Boolean.TRUE, pane.getFollowsDefault(), "seeded means it is following the default");
      PaddingPaneModel cellPane = model.getSelectionGeneralPaneModel().getCellPaddingPaneModel();
      assertEquals(6, cellPane.getTop());
      assertEquals(8, cellPane.getLeft());
      assertEquals(Boolean.TRUE, cellPane.getFollowsDefault());
   }

   @Test
   void anUnmarkedSelectionHidesTheCheckbox() throws Exception {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();

      SelectionListPropertyDialogModel model = load(info);

      assertNull(model.getSelectionGeneralPaneModel().getPaddingPaneModel().getFollowsDefault(),
                 "null hides it: an unmarked selection has no default to follow");
      assertNull(model.getSelectionGeneralPaneModel().getCellPaddingPaneModel().getFollowsDefault());
   }

   @Test
   void checkingTheBoxClearsTheOpinionRatherThanPinningTheTier() throws Exception {
      SelectionListVSAssemblyInfo info = seededList("comfortable");
      info.setCellPadding(new Insets(9, 9, 9, 9), CompositeValue.Type.USER);

      SelectionListVSAssemblyInfo saved =
         writeCellPadding(info, new Insets(9, 9, 9, 9), Boolean.TRUE);

      assertFalse(saved.isUserCellPadding());
      assertEquals(new Insets(6, 8, 6, 8), saved.getCellPadding());
   }

   @Test
   void clearingTheBoxStoresTheEditedValue() throws Exception {
      SelectionListVSAssemblyInfo info = seededList("comfortable");

      SelectionListVSAssemblyInfo saved =
         writeCellPadding(info, new Insets(9, 9, 9, 9), Boolean.FALSE);

      assertTrue(saved.isUserCellPadding());
      assertEquals(new Insets(9, 9, 9, 9), saved.getCellPadding());
   }

   @Test
   void tickingTheCardInsetBoxClearsTheOpinion() throws Exception {
      SelectionListVSAssemblyInfo info = seededList("comfortable");
      Insets seeded = info.getPadding();
      info.setUserPadding(true);
      info.setPadding(new Insets(3, 3, 3, 3));

      SelectionListPropertyDialogModel model = load(info);
      PaddingPaneModel pane = model.getSelectionGeneralPaneModel().getPaddingPaneModel();
      assertEquals(Boolean.FALSE, pane.getFollowsDefault());
      pane.setFollowsDefault(true);
      SelectionListVSAssemblyInfo saved = write(info, model);

      assertFalse(saved.isUserPadding());
      assertEquals(seeded, saved.getPadding());
   }

   @Test
   void anUnmarkedSelectionKeepsZeroCellPaddingUnpinned() throws Exception {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();

      SelectionListVSAssemblyInfo saved = write(info, load(info));

      assertFalse(saved.isUserCellPadding(), "all zeros with no checkbox means none, not a pinned 0");
   }

   // comfortable: a 30px lane, six 28px rows and 16 + 16 of inset
   @Test
   void switchingAMarkedListToListMakesRoomForItsRowsAndInset() throws Exception {
      SelectionListVSAssemblyInfo info = seededList("comfortable");

      assertEquals(30 + 6 * 28 + 32, switchedToList(info).getPixelSize().height);
   }

   // unmarked: a 20px title and six 20px rows, with no inset, exactly as before
   @Test
   void switchingAnUnmarkedListToListKeepsTheLegacyHeight() throws Exception {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();

      assertEquals(20 + 6 * 20, switchedToList(info).getPixelSize().height);
   }

   private SelectionListVSAssemblyInfo seededList(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      VizModernizeUtil.reseedAfterRestore(info);
      return info;
   }

   private void wire(SelectionListVSAssemblyInfo info) throws Exception {
      lenient().when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      lenient().when(rvs.getViewsheet()).thenReturn(viewsheet);
      lenient().when(viewsheet.getAssembly(anyString())).thenReturn(selectionListAssembly);
      lenient().when(viewsheet.getAssemblies()).thenReturn(new Assembly[0]);
      lenient().when(viewsheet.getPixelSize(any())).thenReturn(new Dimension(200, 140));
      lenient().when(selectionListAssembly.getVSAssemblyInfo()).thenReturn(info);
      lenient().when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      lenient().when(dialogService.getAssemblySize(any(), any())).thenReturn(new Dimension(200, 140));
   }

   private SelectionListPropertyDialogModel load(SelectionListVSAssemblyInfo info)
      throws Exception
   {
      wire(info);
      return service.getSelectionListPropertyModel("Viewsheet1", "SelectionList1", null);
   }

   /** The service edits a clone, so the saved state is what reaches editObjectProperty. */
   private SelectionListVSAssemblyInfo write(SelectionListVSAssemblyInfo info,
                                             SelectionListPropertyDialogModel model)
      throws Exception
   {
      wire(info);
      return submit(model);
   }

   // a dropdown with six list rows switched to a list. The real getPixelSize hands back the info's
   // own size, which is what the show-type transition resizes in place
   private SelectionListVSAssemblyInfo switchedToList(SelectionListVSAssemblyInfo info)
      throws Exception
   {
      info.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setListHeight(6);
      SelectionListPropertyDialogModel model = load(info);
      model.getSelectionGeneralPaneModel().setShowType(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      model.getSelectionGeneralPaneModel().setListHeight(6);
      wire(info);
      lenient().when(viewsheet.getPixelSize(any()))
         .thenAnswer(inv -> ((AssemblyInfo) inv.getArgument(0)).getPixelSize());
      return submit(model);
   }

   private SelectionListVSAssemblyInfo submit(SelectionListPropertyDialogModel model)
      throws Exception
   {
      model.getSelectionGeneralPaneModel().getGeneralPropPaneModel().getBasicGeneralPaneModel()
         .setName("SelectionList1");
      service.setSelectionListPropertyModel("Viewsheet1", "SelectionList1", model, "", null,
                                            commandDispatcher);
      ArgumentCaptor<SelectionListVSAssemblyInfo> captor =
         ArgumentCaptor.forClass(SelectionListVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), any(), any(), any(),
         nullable(Principal.class), any(CommandDispatcher.class));
      return captor.getValue();
   }

   private SelectionListVSAssemblyInfo writeCellPadding(SelectionListVSAssemblyInfo info,
                                                        Insets insets, Boolean followsDefault)
      throws Exception
   {
      SelectionListPropertyDialogModel model = load(info);
      PaddingPaneModel pane = model.getSelectionGeneralPaneModel().getCellPaddingPaneModel();
      pane.setTop(insets.top);
      pane.setLeft(insets.left);
      pane.setBottom(insets.bottom);
      pane.setRight(insets.right);
      pane.setFollowsDefault(followsDefault);
      return write(info, model);
   }

   @Mock VSOutputService vsOutputService;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetService engine;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSTrapService trapService;
   @Mock SelectionListVSAssembly selectionListAssembly;
   @Mock VSDialogService dialogService;
   @Mock SelectionDialogService selectionDialogService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock DataRefModelFactoryService dataRefService;
   @Mock DataSourceRegistry dataSourceRegistry;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS)
   private Viewsheet viewsheet;
   private SelectionListPropertyDialogService service;
}
