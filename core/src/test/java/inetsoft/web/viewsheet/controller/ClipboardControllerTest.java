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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.ClipboardService;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.controller.*;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class ClipboardControllerTest {

   @BeforeEach
   void setup() throws Exception {
      ConfigurationContext context = ConfigurationContext.getContext();
      ConfigurationContext  spyContext = Mockito.spy(context);
      staticConfigurationContext = Mockito.mockStatic(ConfigurationContext.class);
      staticConfigurationContext.when(ConfigurationContext::getContext)
         .thenReturn(spyContext);

      ClipboardControllerService clipboardService = new ClipboardControllerService(coreLifecycleService, vsObjectTreeService,
                                                                                   viewsheetService, assemblyHandler, vsObjectPropertyService);
      doReturn(clipboardService).when(spyContext).getSpringBean(ClipboardControllerService.class);
      controller = new ClipboardController(runtimeViewsheetRef, new ClipboardControllerServiceProxy());
   }

   // Bug #16764 make sure the top left is calculated correctly.
   @Test
   void pastedObjectPosition() throws Exception {
      when(viewsheetService.getViewsheet(any(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(sandbox);

      List<Assembly> assemblies = new ArrayList<>();
      ImageVSAssembly image = new ImageVSAssembly(viewsheet, "Image");
      image.setPixelOffset(new Point(100, 100));
      GaugeVSAssembly gauge = new GaugeVSAssembly(viewsheet, "Gauge");
      gauge.setPixelOffset(new Point(200, 200));
      assemblies.add(image);
      assemblies.add(gauge);

      when(viewsheet.getAssemblies())
         .thenReturn(assemblies.toArray(new Assembly[assemblies.size()]));

      when(clipboardService.paste()).thenReturn(assemblies);
      Map<String, Object> headers = new HashMap<>();
      headers.put(ClipboardService.CLIPBOARD, clipboardService);

      MockedStatic<MessageContextHolder> contextHolder = Mockito.mockStatic(MessageContextHolder.class);
      MessageAttributes messageAttributes = mock(MessageAttributes.class);
      StompHeaderAccessor stompHeaderAccessor = mock(StompHeaderAccessor.class);

      contextHolder.when(MessageContextHolder::getMessageAttributes).thenReturn(messageAttributes);
      when(messageAttributes.getHeaderAccessor()).thenReturn(stompHeaderAccessor);
      when(stompHeaderAccessor.getSessionAttributes()).thenReturn(headers);

      controller.pasteObject(0, 0, null, commandDispatcher, headerAccessor, linkUri);

      verify(coreLifecycleService, times(2))
         .addDeleteVSObject(any(RuntimeViewsheet.class), argCaptor.capture(),
                            any(CommandDispatcher.class));

      List<VSAssembly> commands = argCaptor.getAllValues();
      VSAssembly image2 = commands.get(0);
      Point positionImage = image2.getPixelOffset();
      VSAssembly gauge2 = commands.get(1);
      Point positionGauge = gauge2.getPixelOffset();
      assertEquals(0, positionImage.x);
      assertEquals(0, positionImage.y);
      assertEquals(100, positionGauge.y);
      assertEquals(100, positionGauge.y);
   }

   @Captor
   ArgumentCaptor<VSAssembly> argCaptor;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock
   CoreLifecycleService coreLifecycleService;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock ViewsheetService viewsheetService;
   @Mock ClipboardService clipboardService;
   @Mock SimpMessageHeaderAccessor headerAccessor;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock CommandDispatcher commandDispatcher;
   @Mock VSAssemblyInfoHandler assemblyHandler;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock ViewsheetSandbox sandbox;
   MockedStatic<ConfigurationContext> staticConfigurationContext;

   private ClipboardController controller;
   private final String linkUri = "http://localhost:18080/sree/";
}
