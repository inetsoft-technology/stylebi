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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
class VSObjectPropertyServiceTest {

   @BeforeEach
   void setup() {
      controller = new VSObjectPropertyService(coreLifecycleService,
                                               vsColumnHandler,
                                               vsObjectTreeService,
                                               infoHandler,
                                               temporaryInfoService,
                                               vsCompositionService,
                                               sharedFilterService,
                                               dataSourceRegistry);
   }

   @Test
   void popComponentListTest() {
      String textAssemblyName = "TextAssembly";
      String selectionListAssemblyName = "SelectionListAssembly";
      TextVSAssembly textVSAssembly = new TextVSAssembly(viewsheet, textAssemblyName);
      Assembly[] assemblies = new Assembly[] {
         textVSAssembly,
         new SelectionListVSAssembly(viewsheet, selectionListAssemblyName)
      };

      when(viewsheet.getAssemblies()).thenReturn(assemblies);
      when(viewsheet.getAssemblies(anyBoolean())).thenReturn(assemblies);
      when(viewsheet.getAssembly(textAssemblyName)).thenReturn(textVSAssembly);
      String[] popComponents = controller.getSupportedPopComponents(viewsheet, textAssemblyName);
      assertEquals(1, popComponents.length);
      assertEquals(selectionListAssemblyName, popComponents[0]);
   }

   @Mock CoreLifecycleService coreLifecycleService;
   @Mock VSColumnHandler vsColumnHandler;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock VSAssemblyInfoHandler infoHandler;
   @Mock Viewsheet viewsheet;
   @Mock VSWizardTemporaryInfoService temporaryInfoService;
   @Mock VSCompositionService vsCompositionService;
   @Mock SharedFilterService sharedFilterService;
   @Mock DataSourceRegistry dataSourceRegistry;

   private VSObjectPropertyService controller;
}
