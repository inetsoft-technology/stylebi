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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.GroupContainerVSAssembly;
import inetsoft.uql.viewsheet.RectangleVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.vs.objects.controller.ClipboardControllerService;
import inetsoft.web.composer.vs.objects.controller.ComposerGroupService;
import inetsoft.web.composer.vs.objects.controller.ComposerObjectService;
import inetsoft.web.composer.vs.objects.controller.GroupingService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.event.ResizeVSObjectEvent;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.viewsheet.model.AssemblyNode;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78901: the {@code resize} refusal against a real {@code Viewsheet} lookup. The real
 * assembly constructors read {@code SreeEnv}, hence the Spring context, and that context makes
 * the class slow. The guard itself is covered fast, with mocks, in
 * {@code ViewsheetEditServiceTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class ViewsheetEditServiceResizeTest {
   /**
    * The same refusal against a real {@code Viewsheet} lookup: a member inside a group still
    * resizes (the group re-fits to it), while the group itself and an embedded viewsheet are
    * refused before anything is written.
    */
   @Test
   void resizeOnARealGroupRefusesTheGroupButStillResizesItsMember() throws Exception {
      Viewsheet vs = new Viewsheet();
      RectangleVSAssembly a = new RectangleVSAssembly(vs, "RectA");
      RectangleVSAssembly b = new RectangleVSAssembly(vs, "RectB");
      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "GroupContainer1");
      Viewsheet embedded = new Viewsheet();
      embedded.getVSAssemblyInfo().setName("Viewsheet1");
      vs.addAssembly(a);
      vs.addAssembly(b);
      vs.addAssembly(group);
      vs.addAssembly(embedded);
      group.setAssemblies(new String[]{ "RectA", "RectB" });

      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetEditService service = serviceWith(vs, objects,
         new AssemblyNode("RectA", "Rectangle", 20, 380, 220, 80, 0, "GroupContainer1", true),
         new AssemblyNode("GroupContainer1", "GroupContainer", 20, 380, 300, 220, 0, null, true),
         new AssemblyNode("Viewsheet1", "Viewsheet", 400, 60, 100, 50, 0, null, true));

      assertThrows(IllegalArgumentException.class,
         () -> service.apply("tok", principal(), sized("GroupContainer1", 600, 400), ""));
      assertThrows(IllegalArgumentException.class,
         () -> service.apply("tok", principal(), sized("Viewsheet1", 400, 300), ""));
      verifyNoInteractions(objects);

      service.apply("tok", principal(), sized("RectA", 260, 90), "");

      ArgumentCaptor<ResizeVSObjectEvent> captor =
         ArgumentCaptor.forClass(ResizeVSObjectEvent.class);
      verify(objects).resizeObject(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                   anyString());
      assertEquals("RectA", captor.getValue().getName());
      assertEquals(260, captor.getValue().getWidth());
      assertEquals(90, captor.getValue().getHeight());
   }

   private static ViewsheetEditService serviceWith(Viewsheet vs, ComposerObjectService objects,
                                                   AssemblyNode... nodes)
   {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return serviceWith(rvs, objects, List.of(nodes));
   }

   private static ViewsheetEditService serviceWith(RuntimeViewsheet rvs,
                                                   ComposerObjectService objects,
                                                   List<AssemblyNode> nodes)
   {
      ViewsheetReadService reader = mock(ViewsheetReadService.class);
      when(reader.read(any())).thenReturn(new ViewsheetModel("vs", nodes));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new ViewsheetEditService(sessions, objects, mock(ClipboardControllerService.class),
                                      mock(VSObjectPropertyService.class), reader,
                                      mock(ComposerGroupService.class),
                                      mock(GroupingService.class),
                                      mock(VSRefreshService.class),
                                      mock(CoreLifecycleService.class));
   }

   private static EditRequest sized(String assembly, Integer width, Integer height) {
      return new EditRequest("resize", assembly, null, null, width, height, null, null, null,
                             null, null, null, null, null);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
