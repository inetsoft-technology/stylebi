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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class VSSelectionContainerServiceTest {
   @Mock VSObjectService objectService;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock ViewsheetService viewsheetService;
   @Mock VSOutputService vsOutputService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock RuntimeViewsheet rvs;
   @Mock CommandDispatcher dispatcher;

   private VSSelectionContainerService service;
   private Viewsheet vs;

   @BeforeEach
   void setUp() {
      service = new VSSelectionContainerService(objectService, coreLifecycleService,
                                                objectModelService, viewsheetService,
                                                vsOutputService, assemblyInfoHandler);
      vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("comfortable");
      when(rvs.getViewsheet()).thenReturn(vs);

      // VSObjectService.getSize: the layout size when a device layout set one, else the live
      // pixel size, which the service then writes in place
      when(objectService.getSize(any())).thenAnswer(inv -> {
         VSAssemblyInfo info = inv.getArgument(0);
         Dimension layout = info.getLayoutSize(false);
         return layout != null ? layout : vs.getPixelSize(info);
      });
   }

   @Test
   void expandingAMarkedListFitsItsRowsAtTheTier() throws Exception {
      container(VizMark.MODERN_LIGHT, 800, "SelectionList1");
      SelectionListVSAssembly list = list("SelectionList1", VizMark.MODERN_LIGHT, false, 30);

      service.applySelection(rvs, "SelectionList1", false, dispatcher, "");

      assertEquals(230, list.getPixelSize().height, "lane 30 + 6 rows of 28 + inset 16 + 16");
      assertEquals(SelectionVSAssemblyInfo.LIST_SHOW_TYPE,
                   list.getSelectionListInfo().getShowTypeValue());
   }

   @Test
   void expandingAnUnmarkedListKeepsTheLegacyHeight() throws Exception {
      container(null, 800, "SelectionList1");
      SelectionListVSAssembly list = list("SelectionList1", null, false, 20);

      service.applySelection(rvs, "SelectionList1", false, dispatcher, "");

      assertEquals(140, list.getPixelSize().height, "6 rows of 20 + lane 20, as before");
   }

   // the list's own mark decides, not the container's
   @Test
   void aMarkedListInAnUnmarkedContainerFitsItsRows() throws Exception {
      container(null, 800, "SelectionList1");
      SelectionListVSAssembly list = list("SelectionList1", VizMark.MODERN_LIGHT, false, 30);

      service.applySelection(rvs, "SelectionList1", false, dispatcher, "");

      assertEquals(230, list.getPixelSize().height);
   }

   @Test
   void anUnmarkedListInAMarkedContainerKeepsTheLegacyHeight() throws Exception {
      container(VizMark.MODERN_LIGHT, 800, "SelectionList1");
      SelectionListVSAssembly list = list("SelectionList1", null, false, 20);

      service.applySelection(rvs, "SelectionList1", false, dispatcher, "");

      assertEquals(140, list.getPixelSize().height);
   }

   @Test
   void expandingInADeviceLayoutWritesTheLayoutSize() throws Exception {
      container(VizMark.MODERN_LIGHT, 800, "SelectionList1");
      SelectionListVSAssembly list = list("SelectionList1", VizMark.MODERN_LIGHT, false, 30);
      list.getSelectionListInfo().setLayoutSize(new Dimension(300, 30));

      service.applySelection(rvs, "SelectionList1", false, dispatcher, "");

      assertEquals(230, list.getSelectionListInfo().getLayoutSize().height);
      assertEquals(30, list.getPixelSize().height, "the pixel size is left to the design layout");
   }

   // title 30 + two open lists of 230 is 490, past the 470 box. The old estimate counted the
   // opening list's body as 6 rows of 20, so it collapsed nothing and the container overflowed
   @Test
   void expandingCollapsesASiblingTheTierRowsWouldOverflow() throws Exception {
      container(VizMark.MODERN_LIGHT, 470, "SelectionList1", "SelectionList2");
      SelectionListVSAssembly open = list("SelectionList1", VizMark.MODERN_LIGHT, true, 230);
      SelectionListVSAssembly closed = list("SelectionList2", VizMark.MODERN_LIGHT, false, 30);

      service.applySelection(rvs, "SelectionList2", false, dispatcher, "");

      assertEquals(230, closed.getPixelSize().height);
      assertEquals(30, open.getPixelSize().height, "the open sibling collapses to its lane");
      assertEquals(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE,
                   open.getSelectionListInfo().getShowTypeValue());
   }

   // two collapsed rows are 60 at comfortable; the old estimate counted them as 40
   @Test
   void theOverflowEstimateCountsCollapsedRowsAtTheTierHeight() throws Exception {
      CurrentSelectionVSAssembly container =
         container(VizMark.MODERN_LIGHT, 580, "SelectionList1", "SelectionList2");
      list("Outside1", null, false, 20);
      list("Outside2", null, false, 20);
      SelectionListVSAssembly open = list("SelectionList1", VizMark.MODERN_LIGHT, true, 230);
      list("SelectionList2", VizMark.MODERN_LIGHT, false, 30);
      container.setShowCurrentSelectionValue(true);
      container.updateOutSelection();
      assertEquals(2, container.getOutSelectionTitles().length, "the two lists outside");

      service.applySelection(rvs, "SelectionList2", false, dispatcher, "");

      assertEquals(30, open.getPixelSize().height, "the open sibling collapses to make room");
   }

   CurrentSelectionVSAssembly container(VizMark mark, int height, String... children) {
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(container);
      container.getVSAssemblyInfo().setVizMark(mark);
      container.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      container.getVSAssemblyInfo().setPixelSize(new Dimension(300, height));
      container.setAssemblies(children);
      return container;
   }

   // the mark is pinned, because construction takes the org gate's
   SelectionListVSAssembly list(String name, VizMark mark, boolean expanded, int height) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      vs.addAssembly(list);
      SelectionListVSAssemblyInfo info = list.getSelectionListInfo();
      info.setVizMark(mark);
      info.initDefaultFormat();
      info.setListHeight(6);
      info.setShowTypeValue(expanded ? SelectionVSAssemblyInfo.LIST_SHOW_TYPE :
                               SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setPixelSize(new Dimension(300, height));
      return list;
   }
}
