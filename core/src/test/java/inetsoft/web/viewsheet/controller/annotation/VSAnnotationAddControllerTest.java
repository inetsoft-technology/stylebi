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
package inetsoft.web.viewsheet.controller.annotation;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.BaseAnnotationVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.viewsheet.event.annotation.AddAnnotationEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SreeHome()
class VSAnnotationAddControllerTest {
   @BeforeEach
   void setUp() throws Exception {
      runtimeViewsheetRef = mock(RuntimeViewsheetRef.class);
      placeholderService = mock(PlaceholderService.class);
      viewsheetService = mock(ViewsheetService.class);
      securityEngine = mock(SecurityEngine.class);
      service = new VSObjectService(placeholderService,
                                    viewsheetService,
                                    securityEngine);

      principal = mock(Principal.class);
      dispatcher = mock(CommandDispatcher.class);
      rvs = mock(RuntimeViewsheet.class);
      viewsheet = mock(Viewsheet.class);

      // mock service
      annotationService = new VSAnnotationService(service);

      // stub method calls
      when(viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssemblies(anyBoolean())).thenReturn(new Assembly[]{});
      when(viewsheet.getViewsheetInfo()).thenReturn(new ViewsheetInfo());
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      when(principal.getName()).thenReturn("test user");
      when(securityEngine.isActiveUser(principal)).thenReturn(true);
   }

   @AfterEach
   void tearDown() {
      runtimeViewsheetRef = null;
      placeholderService = null;
      viewsheetService = null;
      securityEngine = null;
      principal = null;
      dispatcher = null;
      rvs = null;
      viewsheet = null;
      service = null;
   }

   @Test
   void addViewsheetAnnotation() throws Exception {
      // mock method parameters
      AddAnnotationEvent event = AddAnnotationEvent.builder()
                                                   .x(0)
                                                   .y(0)
                                                   .row(-1)
                                                   .col(-1)
                                                   .content("test content")
                                                   .build();

      // call method to test
      VSAnnotationAddController controller =
         new VSAnnotationAddController(service, annotationService, runtimeViewsheetRef);
      controller.addViewsheetAnnotation(event, "", principal, dispatcher);

      // check annotation and annotation rectangle were created
      verify(placeholderService)
         .addDeleteVSObject(eq(rvs), isA(AnnotationVSAssembly.class), eq(dispatcher));
      verify(placeholderService)
         .addDeleteVSObject(eq(rvs), isA(AnnotationRectangleVSAssembly.class), eq(dispatcher));

      // check viewsheet was laid out
      final String id = rvs.getID();
      verify(placeholderService)
         .layoutViewsheet(eq(rvs), eq(id), nullable(String.class), eq(dispatcher));

      // check that parent assembly is null
      verify(placeholderService)
         .refreshVSAssembly(eq(rvs), (VSAssembly) isNull(), eq(dispatcher));
   }

   @Test
   void addAssemblyAnnotation() throws Exception {
      // Create parent assembly
      final VSAssembly parentAssembly = VSEventUtil.createVSAssembly(rvs, Viewsheet.CHART_ASSET);

      assertNotNull(parentAssembly);
      final VSAssemblyInfo parentInfo = parentAssembly.getVSAssemblyInfo();
      final String parentName = parentAssembly.getAbsoluteName();

      // Parent assembly info should always inherit from BaseAnnotationVSAssemblyInfo
      assertTrue(parentInfo instanceof BaseAnnotationVSAssemblyInfo);

      // mock method parameters
      AddAnnotationEvent event = AddAnnotationEvent.builder()
                                                   .x(0)
                                                   .y(0)
                                                   .row(-1)
                                                   .col(-1)
                                                   .content("test content")
                                                   .parent(parentName)
                                                   .build();

      // return parent assembly (since we're only mocking the viewsheet)
      when(viewsheet.getAssembly(parentName)).thenReturn(parentAssembly);

      // call method to test
      VSAnnotationAddController controller =
         new VSAnnotationAddController(service, annotationService, runtimeViewsheetRef);
      controller.addAssemblyAnnotation(event, "", principal, dispatcher);

      // check annotation and annotation rectangle were created
      verify(placeholderService)
         .addDeleteVSObject(eq(rvs), isA(AnnotationVSAssembly.class), eq(dispatcher));
      verify(placeholderService)
         .addDeleteVSObject(eq(rvs), isA(AnnotationRectangleVSAssembly.class), eq(dispatcher));

      // get the names of the assemblies created
      ArgumentCaptor<VSAssembly> annotationArg = ArgumentCaptor.forClass(VSAssembly.class);
      verify(placeholderService, times(2))
         .addDeleteVSObject(eq(rvs), annotationArg.capture(), eq(dispatcher));
      final List<VSAssembly> annotations = annotationArg.getAllValues();

      // check viewsheet was laid out
      final String id = rvs.getID();
      verify(placeholderService)
         .layoutViewsheet(eq(rvs), eq(id), nullable(String.class), eq(dispatcher));

      // check that our parent assembly is refreshed
      verify(placeholderService)
         .refreshVSAssembly(eq(rvs), eq(parentAssembly), eq(dispatcher));

      // check that the name of the created annotation is referenced in the parent assembly
      final List<String> annotationNames = ((BaseAnnotationVSAssemblyInfo) parentInfo).getAnnotations();
      assertEquals(1, annotationNames.size());
      assertEquals(annotationNames.get(0), annotations.get(0).getAbsoluteName());
   }

   private RuntimeViewsheetRef runtimeViewsheetRef;
   private PlaceholderService placeholderService;
   private ViewsheetService viewsheetService;
   private SecurityEngine securityEngine;
   private Principal principal;
   private CommandDispatcher dispatcher;
   private RuntimeViewsheet rvs;
   private Viewsheet viewsheet;
   private VSObjectService service;
   private VSAnnotationService annotationService;
}