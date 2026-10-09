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
import inetsoft.test.*;
import inetsoft.uql.viewsheet.CheckBoxVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class VSInputServiceDetailHeightTest {
   @Test
   void aDraggedOptionRowIsTheAuthors() throws Exception {
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssembly assembly = new CheckBoxVSAssembly(vs, "CheckBox1");
      vs.addAssembly(assembly);
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      VSInputService service = new VSInputService(
         vsObjectService, coreLifecycleService, viewsheetService, vsObjectPropertyService,
         dialogService, trapService, dataRefModelFactoryService, assemblyInfoHandler,
         columnHandler, queryManagerService);

      service.setDetailHeight("Viewsheet1", "CheckBox1", 33, null);

      CheckBoxVSAssemblyInfo info = (CheckBoxVSAssemblyInfo) assembly.getVSAssemblyInfo();
      assertEquals(33, info.getCellHeightValue());
      assertTrue(info.isUserCellHeight());
   }

   @Mock VSObjectService vsObjectService;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock ViewsheetService viewsheetService;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSDialogService dialogService;
   @Mock VSTrapService trapService;
   @Mock DataRefModelFactoryService dataRefModelFactoryService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock VSColumnHandler columnHandler;
   @Mock QueryManagerService queryManagerService;
   @Mock RuntimeViewsheet rvs;
}
