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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.SelectionContainerPropertyDialogModel;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Ticking Follow default density on a container must put the tier size into the model before
 * the children are resized from it, or they stay as wide as the old box.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class SelectionContainerSizeFollowTest {
   @BeforeEach
   void setup() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      service = new SelectionContainerPropertyDialogService(
         vsObjectPropertyService, dialogService, engine);
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void tickingPutsTheTierSizeInTheModelBeforeTheChildrenAreResized() throws Exception {
      CurrentSelectionVSAssemblyInfo info = new CurrentSelectionVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(400, 500));
      info.setUserSize(true);
      lenient().when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      lenient().when(rvs.getViewsheet()).thenReturn(viewsheet);
      lenient().when(viewsheet.getAssembly(anyString())).thenReturn(containerAssembly);
      lenient().when(containerAssembly.getVSAssemblyInfo()).thenReturn(info);
      lenient().when(containerAssembly.getAssemblies()).thenReturn(new String[0]);
      lenient().when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      lenient().when(dialogService.getAssemblySize(any(), any())).thenReturn(new Dimension(400, 500));

      SelectionContainerPropertyDialogModel model =
         service.getSelectionContainerPropertyModel("Viewsheet1", "CurrentSelection1", null);
      SizePositionPaneModel sizeModel =
         model.getSelectionContainerGeneralPaneModel().getSizePositionPaneModel();
      assertEquals(Boolean.FALSE, sizeModel.getSizeFollowsDensity());
      sizeModel.setSizeFollowsDensity(true);
      model.getSelectionContainerGeneralPaneModel().getGeneralPropPaneModel()
         .getBasicGeneralPaneModel().setName("CurrentSelection1");

      AtomicReference<Dimension> seenBySetSize = new AtomicReference<>();
      doAnswer(inv -> {
         SizePositionPaneModel m = inv.getArgument(1);
         seenBySetSize.set(new Dimension(m.getWidth(), m.getHeight()));
         return null;
      }).when(dialogService).setContainerSize(any(), any(), any(), any());

      service.setSelectionContainerPropertyModel("Viewsheet1", "CurrentSelection1", model, "", null,
                                                 commandDispatcher);

      assertEquals(new Dimension(300, 360), seenBySetSize.get());
      ArgumentCaptor<CurrentSelectionVSAssemblyInfo> captor =
         ArgumentCaptor.forClass(CurrentSelectionVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), any(), any(), any(),
         nullable(Principal.class), any(CommandDispatcher.class));
      assertFalse(captor.getValue().isUserSize());
   }

   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetService engine;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock CurrentSelectionVSAssembly containerAssembly;
   @Mock VSDialogService dialogService;
   @Mock Viewsheet viewsheet;
   private SelectionContainerPropertyDialogService service;
}
