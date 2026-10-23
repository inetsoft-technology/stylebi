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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.security.Principal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RangeSliderDensityDialogTest {
   @BeforeEach
   void setup() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      viewsheetService = mock(ViewsheetService.class);
      rvs = mock(RuntimeViewsheet.class);
      viewsheet = mock(Viewsheet.class);
      assembly = mock(TimeSliderVSAssembly.class);
      propertyService = mock(VSObjectPropertyService.class);
      VSOutputService outputService = mock(VSOutputService.class);
      SelectionDialogService selectionDialogService = mock(SelectionDialogService.class);
      VSAssemblyInfoHandler handler = mock(VSAssemblyInfoHandler.class);

      dialogService = new VSDialogService(null, null);
      service = new RangeSliderPropertyDialogService(
         propertyService, outputService, viewsheetService, dialogService, mock(VSTrapService.class),
         selectionDialogService, handler, mock(QueryManagerService.class));

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(assembly);
      when(viewsheet.getPixelSize(any(VSAssemblyInfo.class)))
         .thenAnswer(inv -> ((VSAssemblyInfo) inv.getArgument(0)).getPixelSize());
      when(viewsheet.getPixelPosition(any(VSAssemblyInfo.class))).thenReturn(new Point(0, 0));
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private TimeSliderVSAssemblyInfo slider(VizMark mark) {
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      info.setVizMark(mark);

      if(mark != null) {
         VizContext ctx = VizContext.of(info);
         info.resetSize(ctx);
         info.resetPadding(ctx);
      }

      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      return info;
   }

   private RangeSliderPropertyDialogModel read() throws Exception {
      return service.getRangeSliderPropertyModel("vs", "RangeSlider1", null);
   }

   private TimeSliderVSAssemblyInfo write(RangeSliderPropertyDialogModel model) throws Exception {
      model.getRangeSliderGeneralPaneModel().getGeneralPropPaneModel().getBasicGeneralPaneModel()
         .setName("RangeSlider1");
      service.setRangeSliderPropertyModel(
         "vs", "RangeSlider1", model, "", null, mock(CommandDispatcher.class));
      ArgumentCaptor<VSAssemblyInfo> captor = ArgumentCaptor.forClass(VSAssemblyInfo.class);
      verify(propertyService).editObjectProperty(
         any(), captor.capture(), any(), any(), any(), nullable(Principal.class), any());
      return (TimeSliderVSAssemblyInfo) captor.getValue();
   }

   @Test
   void readOffersTheHeightCheckboxOnAStandaloneMarkedSlider() throws Exception {
      slider(VizMark.MODERN_LIGHT);
      SizePositionPaneModel size = read().getRangeSliderGeneralPaneModel().getSizePositionPaneModel();

      assertEquals(Boolean.TRUE, size.getSizeFollowsDensity());
      assertTrue(size.isDensityHeightOnly());
   }

   @Test
   void readHidesTheHeightCheckboxInAContainer() throws Exception {
      slider(VizMark.MODERN_LIGHT);
      when(assembly.getContainer()).thenReturn(mock(CurrentSelectionVSAssembly.class));
      SizePositionPaneModel size = read().getRangeSliderGeneralPaneModel().getSizePositionPaneModel();

      assertNull(size.getSizeFollowsDensity());
   }

   @Test
   void readShowsThePaddingAndItsFollowCheckbox() throws Exception {
      slider(VizMark.MODERN_LIGHT);
      PaddingPaneModel padding = read().getRangeSliderGeneralPaneModel().getPaddingPaneModel();

      assertEquals(16, padding.getTop());
      assertEquals(16, padding.getLeft());
      assertEquals(16, padding.getBottom());
      assertEquals(16, padding.getRight());
      assertEquals(Boolean.TRUE, padding.getFollowsDefault());

      slider(null);
      assertNull(read().getRangeSliderGeneralPaneModel().getPaddingPaneModel().getFollowsDefault());
   }

   @Test
   void tickingTheHeightCheckboxRestoresTheTierHeightAndKeepsATypedWidth() throws Exception {
      TimeSliderVSAssemblyInfo info = slider(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(400, 45));
      info.setUserSize(true);
      RangeSliderPropertyDialogModel model = read();
      SizePositionPaneModel size = model.getRangeSliderGeneralPaneModel().getSizePositionPaneModel();
      size.setSizeFollowsDensity(true);
      size.setWidth(500);

      TimeSliderVSAssemblyInfo written = write(model);

      assertEquals(new Dimension(500, 82), written.getPixelSize());
      assertFalse(written.isUserSize());
   }

   @Test
   void anUntouchedApplyFlagsNothing() throws Exception {
      slider(VizMark.MODERN_LIGHT);
      TimeSliderVSAssemblyInfo written = write(read());

      assertFalse(written.isUserSize());
      assertFalse(written.isUserPadding());
      assertEquals(new Dimension(232, 82), written.getPixelSize());
      assertEquals(new Insets(16, 16, 16, 16), written.getPadding());
   }

   // a format.css padding on a legacy slider stays unclaimed
   @Test
   void anUnmarkedSlidersUntouchedApplyStoresNothing() throws Exception {
      TimeSliderVSAssemblyInfo info = slider(null);
      info.setPadding(new Insets(5, 5, 5, 5));
      TimeSliderVSAssemblyInfo written = write(read());

      assertFalse(written.isUserPadding());
      assertFalse(written.isUserSize());
      assertEquals(new Insets(5, 5, 5, 5), written.getPadding());
      assertEquals(new Dimension(200, 40), written.getPixelSize());
   }

   // a seeded height off the tier's would move if the checkbox path ran
   @Test
   void aContainedSlidersApplyLeavesItsHeightToTheContainer() throws Exception {
      TimeSliderVSAssemblyInfo info = slider(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(300, 66));
      when(assembly.getContainer()).thenReturn(mock(CurrentSelectionVSAssembly.class));
      TimeSliderVSAssemblyInfo written = write(read());

      assertEquals(new Dimension(300, 66), written.getPixelSize());
      assertFalse(written.isUserSize());
   }

   @Test
   void anAuthorPaddingIsStored() throws Exception {
      slider(VizMark.MODERN_LIGHT);
      RangeSliderPropertyDialogModel model = read();
      PaddingPaneModel padding = model.getRangeSliderGeneralPaneModel().getPaddingPaneModel();
      padding.setFollowsDefault(false);
      padding.setTop(0);
      padding.setLeft(0);
      padding.setBottom(0);
      padding.setRight(0);

      TimeSliderVSAssemblyInfo written = write(model);

      assertEquals(new Insets(0, 0, 0, 0), written.getPadding());
      assertTrue(written.isUserPadding());
   }

   private RangeSliderPropertyDialogService service;
   private VSDialogService dialogService;
   private ViewsheetService viewsheetService;
   private RuntimeViewsheet rvs;
   private Viewsheet viewsheet;
   private TimeSliderVSAssembly assembly;
   private VSObjectPropertyService propertyService;
}
