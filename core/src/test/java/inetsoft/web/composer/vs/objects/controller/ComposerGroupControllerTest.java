/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComposerGroupControllerTest {

   @BeforeEach
   void setup() throws Exception {
      controller = new ComposerGroupController(runtimeViewsheetRef, placeholderService,
                                               viewsheetService, vsObjectTreeService,
                                               vsObjectPropertyService);
      assemblies[0] = tab;
      assemblies[1] = image;
      assemblies[2] = calendar;
      assemblies[3] = group;
      when(viewsheet.getAssemblies()).thenReturn(assemblies);
      when(tab.getAbsoluteName()).thenReturn("tab");
      when(group.getAbsoluteName()).thenReturn("group");
      when(image.getAbsoluteName()).thenReturn("image");
      when(calendar.getAbsoluteName()).thenReturn("calendar");
      when(tab.getAssemblies()).thenReturn(tabAssemblies);
      when(group.getAssemblies()).thenReturn(groupAssemblies);
      when(viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
   }

   @Test
   void checkDependencyTest() throws Exception {
      tabAssemblies[0] = "group";
      tabAssemblies[1] = "group";
      groupAssemblies[0] = "tab";
      groupAssemblies[1] = "image";
      groupAssemblies[2] = "calendar";

      assertEquals(controller.checkDependency(viewsheet), true);

      tabAssemblies[0] = "calendar";
      tabAssemblies[1] = "image";

      assertEquals(controller.checkDependency(viewsheet), false);
   }

   // Bug #10760 Update the component tree after a grouping action
   @Test
   void sendsPopulateObjectTreeCommandTest() throws Exception {
      when(viewsheet.getAssembly("group")).thenReturn(group);
      controller.ungroup("group", "", principal, commandDispatcher);
      verify(commandDispatcher).sendCommand(any(PopulateVSObjectTreeCommand.class));
   }

   // Bug #17458 link uri is not null
   @Test
   void linkUriNotNull() throws Exception {
      when(viewsheet.getAssembly("group")).thenReturn(group);
      controller.ungroup("group", "linkUri", principal, commandDispatcher);
      verify(placeholderService).removeVSAssembly(any(RuntimeViewsheet.class),
                                                  eq("linkUri"),
                                                  any(VSAssembly.class),
                                                  any(CommandDispatcher.class),
                                                  anyBoolean(),
                                                  anyBoolean());
   }

   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock PlaceholderService placeholderService;
   @Mock ViewsheetService viewsheetService;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock Viewsheet viewsheet;
   @Mock TabVSAssembly tab;
   @Mock ImageVSAssembly image;
   @Mock CalendarVSAssembly calendar;
   @Mock GroupContainerVSAssembly group;
   @Mock Principal principal;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   String[] tabAssemblies = new String[2];
   String[] groupAssemblies = new String[3];
   Assembly[] assemblies = new Assembly[4];

   private ComposerGroupController controller;
}
