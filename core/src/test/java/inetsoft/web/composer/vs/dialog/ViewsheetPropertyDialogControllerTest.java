/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@SreeHome()
@ExtendWith(MockitoExtension.class)
public class ViewsheetPropertyDialogControllerTest {
   @BeforeEach
   public void setup() throws Exception {
      controller = new ViewsheetPropertyDialogController(
         runtimeViewsheetRef, placeholderService, viewsheetService,
         layoutService, viewsheetSettingsService);
   }

   // Bug #16756 Update layout info if it has same id as incoming layout
   @Test
   public void layoutIsUpdated() throws Exception {
      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("Viewsheet1");
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(viewsheetSandbox);
      ViewsheetInfo viewsheetInfo = new ViewsheetInfo();
      when(viewsheet.getViewsheetInfo()).thenReturn(viewsheetInfo);
      LayoutInfo layoutInfo = new LayoutInfo();
      List<ViewsheetLayout> viewsheetLayoutList = new ArrayList<>();
      ViewsheetLayout viewsheetLayout = new ViewsheetLayout();
      viewsheetLayout.setID("VSLayout001");
      List<VSAssemblyLayout> assemblyLayouts = new ArrayList<>();
      assemblyLayouts.add(new VSAssemblyLayout(
         "Bar001", new Point(0, 0), new Dimension(0, 0)));
      viewsheetLayout.setVSAssemblyLayouts(assemblyLayouts);
      viewsheetLayoutList.add(viewsheetLayout);
      layoutInfo.setViewsheetLayouts(viewsheetLayoutList);
      when(viewsheet.getLayoutInfo()).thenReturn(layoutInfo);

      ViewsheetPropertyDialogModel model = ViewsheetPropertyDialogModel.builder().build();
      ScreensPaneModel screensPaneModel = model.screensPane();
      FiltersPaneModel filtersPaneModel = model.filtersPane();
      List<VSDeviceLayoutDialogModel> deviceLayouts = screensPaneModel.getDeviceLayouts();
      VSDeviceLayoutDialogModel deviceLayout = new VSDeviceLayoutDialogModel();
      deviceLayout.setId("VSLayout001");
      deviceLayout.setName("Foo001");
      deviceLayout.setSelectedDevices(new ArrayList<>());
      deviceLayouts.add(deviceLayout);
      model.vsOptionsPane().getViewsheetParametersDialogModel().setDisabledParameters(new String[0]);
      filtersPaneModel.setSharedFilters(new ArrayList<>());
      filtersPaneModel.setFilters(new ArrayList<>());
      screensPaneModel.setDevices(new ArrayList<>());

      if(model.localizationPane() != null) {
         model.localizationPane().setLocalized(new ArrayList<>());
      }

      controller.setViewsheetInfo(model, null, commandDispatcher, null);
      viewsheetLayoutList = layoutInfo.getViewsheetLayouts();
      assertEquals(1, viewsheetLayoutList.size());
      viewsheetLayout = viewsheetLayoutList.get(0);
      assertEquals("VSLayout001", viewsheetLayout.getID());
      assertEquals("Foo001", viewsheetLayout.getName());
      assertNotNull(viewsheetLayout.getVSAssemblyLayout("Bar001"));
   }

   @Mock ViewsheetService viewsheetService;
   @Mock ViewsheetSettingsService viewsheetSettingsService;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock PlaceholderService placeholderService;
   @Mock VSLayoutService layoutService;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock ViewsheetSandbox viewsheetSandbox;
   @Mock CommandDispatcher commandDispatcher;

   private ViewsheetPropertyDialogController controller;
}
