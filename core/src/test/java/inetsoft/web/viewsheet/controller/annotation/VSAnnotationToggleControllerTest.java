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
package inetsoft.web.viewsheet.controller.annotation;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.UserEnv;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.event.annotation.ToggleAnnotationStatusEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SreeHome()
class VSAnnotationToggleControllerTest {
   @BeforeEach
   void setUp() throws Exception {
      runtimeViewsheetRef = mock(RuntimeViewsheetRef.class);
      placeholderService = mock(PlaceholderService.class);
      viewsheetService = mock(ViewsheetService.class);
      securityEngine = mock(SecurityEngine.class);

      principal = mock(Principal.class);
      dispatcher = mock(CommandDispatcher.class);
      rvs = mock(RuntimeViewsheet.class);
      viewsheet = mock(Viewsheet.class);

      // mock service
      service = new VSObjectService(placeholderService,
                                    viewsheetService,
                                    securityEngine);

      // stub method calls
      when(viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
   }

   @AfterEach
   void tearDown() {
      runtimeViewsheetRef = null;
      viewsheet = null;
      placeholderService = null;
      viewsheetService = null;
      securityEngine = null;
      principal = null;
      dispatcher = null;
      rvs = null;
      service = null;
   }

   @Test
   void toggleAnnotationStatus() throws Exception {
      ToggleAnnotationStatusEvent event = () -> true;

      // call method to test
      VSAnnotationToggleController controller =
         new VSAnnotationToggleController(service, runtimeViewsheetRef);
      controller.toggleAnnotationStatus(event, principal, null, dispatcher);

      // check userenv value was set to the same as the event status
      final boolean showAnno = "true".equals(UserEnv.getProperty(principal, "annotation"));
      assertTrue(showAnno);

      // check that the viewsheet was updated with the status as well
      verify(viewsheet).setAnnotationsVisible(event.getStatus());
   }

   private RuntimeViewsheetRef runtimeViewsheetRef;
   private Viewsheet viewsheet;
   private PlaceholderService placeholderService;
   private ViewsheetService viewsheetService;
   private SecurityEngine securityEngine;
   private Principal principal;
   private CommandDispatcher dispatcher;
   private RuntimeViewsheet rvs;
   private VSObjectService service;
}