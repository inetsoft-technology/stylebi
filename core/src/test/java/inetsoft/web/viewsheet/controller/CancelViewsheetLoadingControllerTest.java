/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.CancelViewsheetLoadingEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.controller.CancelViewsheetLoadingController;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.PrincipalMethodArgumentResolver;

import java.security.Principal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CancelViewsheetLoadingControllerTest {
   @Mock ViewsheetService viewsheetService;
   @Mock CoreLifecycleService coreLifecycleService;
   MockedStatic<ConfigurationContext> staticConfigurationContext;
   @InjectMocks
   private CancelViewsheetLoadingController controller;
   private MockMvc mockMvc;
   private CancelViewsheetLoadingEvent event;
   private Principal principal;
   private CommandDispatcher commandDispatcher;
   private RuntimeViewsheet rvs;
   private ViewsheetSandbox box;
   private Viewsheet vs;
   private ViewsheetInfo vsInfo;
   private final String linkuri = "/composer/viewsheet/cancelViewsheet";

   @BeforeEach
   public void setUp() throws Exception {
      ConfigurationContext context = ConfigurationContext.getContext();
      ConfigurationContext  spyContext = Mockito.spy(context);
      staticConfigurationContext = Mockito.mockStatic(ConfigurationContext.class);
      staticConfigurationContext.when(ConfigurationContext::getContext)
         .thenReturn(spyContext);

      viewsheetService = mock(ViewsheetService.class);
      rvs = mock(RuntimeViewsheet.class);

      MockitoAnnotations.openMocks(this);
      mockMvc = MockMvcBuilders.standaloneSetup(controller)
         .setCustomArgumentResolvers(new PrincipalMethodArgumentResolver())
         .build();

      event = mock(CancelViewsheetLoadingEvent.class);
      when(event.getRuntimeViewsheetId()).thenReturn("test-rvs-id");

      principal = mock(Principal.class);
      commandDispatcher = mock(CommandDispatcher.class);
      rvs = mock(RuntimeViewsheet.class);
      box = mock(ViewsheetSandbox.class);
      vs = mock(Viewsheet.class);
      vsInfo = mock(ViewsheetInfo.class);

      when(vs.getViewsheetInfo()).thenReturn(vsInfo);
      when(vsInfo.isMetadata()).thenReturn(false);
      when(viewsheetService.getViewsheet(eq("test-rvs-id"), any(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheetSandbox()).thenReturn(box);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("test-rvs-id");

      CancelViewsheetLoadingService loadingService = new CancelViewsheetLoadingService(viewsheetService, coreLifecycleService);
      doReturn(loadingService).when(spyContext).getSpringBean(CancelViewsheetLoadingService.class);
      controller = new CancelViewsheetLoadingController(new CancelViewsheetLoadingServiceProxy());
   }

   @AfterEach
   void afterEach() throws Exception {
      staticConfigurationContext.close();
   }

   @Test
   public void cancelViewsheet_checkFirstIf() throws Exception {
      when(event.isMeta()).thenReturn(true);
      controller.cancelViewsheet(event, linkuri , principal, commandDispatcher);

      // check first if
      verify(box).cancelAllQueries();
      verify(vsInfo).setMetadata(eq(true));
      verify(coreLifecycleService).refreshViewsheet(
         eq(rvs), eq("test-rvs-id"), eq(linkuri), eq(commandDispatcher),
         eq(false), eq(true), eq(true), isA(ChangedAssemblyList.class)
      );
   }

   @Test
   public void cancelViewsheet_neverTriger() throws Exception {
      when(event.isMeta()).thenReturn(false);
      when(event.isIniting()).thenReturn(false);
      when(event.isPreview()).thenReturn(true);

      controller.cancelViewsheet(event, linkuri , principal, commandDispatcher);

      verify(coreLifecycleService, never()).refreshViewsheet(
         eq(rvs), eq("test-rvs-id"), eq(linkuri), eq(commandDispatcher),
         eq(false), eq(true), eq(true), isA(ChangedAssemblyList.class)
      );
   }
}
