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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Bug #81981: the server numbers a group's children absolutely (the group's z-index + 1, ...)
 * and the client paints them as flat siblings with that value. When the group goes away, the
 * released children must keep their z-index, not get the group's z-index added, or they jump
 * over the top-level objects that were stacked above them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class ReleasedGroupChildrenZIndexTest {
   @Test
   void ungroupKeepsChildrenBelowLaterTopLevelObject() throws Exception {
      Viewsheet vs = createViewsheet();
      when(viewsheetService.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      ComposerGroupService service = new ComposerGroupService(
         coreLifecycleService, viewsheetService, vsObjectTreeService, null,
         new VSCompositionService(), vsLayoutService);

      service.ungroup("rid", "GroupContainer2", "", principal, dispatcher);

      assertReleasedChildrenKeepZIndex(vs, "Rectangle4", "Rectangle5");
   }

   @Test
   void groupLeftWithOneChildKeepsThatChildBelowLaterTopLevelObject() throws Exception {
      Viewsheet vs = createViewsheet();
      when(rvs.getViewsheet()).thenReturn(vs);
      VSObjectPropertyService service = new VSObjectPropertyService(
         coreLifecycleService, null, null, null, null, new VSCompositionService(), null, null,
         null);
      Method remove = VSObjectPropertyService.class.getDeclaredMethod(
         "removeObjectFromContainer", RuntimeViewsheet.class, String.class, String.class,
         CommandDispatcher.class, String[].class, java.util.List.class, java.util.List.class);
      remove.setAccessible(true);

      // Rectangle4 leaves the group (e.g. it is converted), so the group is dropped and
      // Rectangle5 is released
      remove.invoke(service, rvs, null, "Rectangle4", dispatcher, new String[] { "Rectangle4" },
                    new ArrayList<>(), new ArrayList<>());

      assertReleasedChildrenKeepZIndex(vs, "Rectangle5");
   }

   private static void assertReleasedChildrenKeepZIndex(Viewsheet vs, String... children) {
      int text = vs.getAssembly("Text2").getZIndex();

      for(String child : children) {
         int z = vs.getAssembly(child).getZIndex();
         assertEquals(ORIGINAL.get(child).intValue(), z, child + " keeps its absolute z-index");
         assertTrue(z < text, child + " must stay below Text2");
      }
   }

   /**
    * The values the server holds for the reported layout after an open: GroupContainer2 at 51,
    * its rectangles at 52 and 53, and a later top-level text at 102.
    */
   private static Viewsheet createViewsheet() {
      Viewsheet vs = new Viewsheet();
      GroupContainerVSAssembly group = add(vs, new GroupContainerVSAssembly(vs, "GroupContainer2"), 51);
      add(vs, new RectangleVSAssembly(vs, "Rectangle4"), 52);
      add(vs, new RectangleVSAssembly(vs, "Rectangle5"), 53);
      add(vs, new TextVSAssembly(vs, "Text2"), 102);
      group.setAssemblies(new String[] { "Rectangle4", "Rectangle5" });
      return vs;
   }

   private static final java.util.Map<String, Integer> ORIGINAL =
      java.util.Map.of("Rectangle4", 52, "Rectangle5", 53);

   private static <T extends VSAssembly> T add(Viewsheet vs, T assembly, int zIndex) {
      assembly.setZIndex(zIndex);
      vs.addAssembly(assembly, false);
      return assembly;
   }

   @Mock CoreLifecycleService coreLifecycleService;
   @Mock ViewsheetService viewsheetService;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock VSLayoutService vsLayoutService;
   @Mock RuntimeViewsheet rvs;
   @Mock CommandDispatcher dispatcher;
   @Mock Principal principal;
}
