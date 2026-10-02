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
package inetsoft.web.admin;

/*
 * Bug #77375: DeniedMultiTenancyOrgUserAspect and PluginsService refuse access by throwing the
 * checked UnauthorizedAccessException. Through the CGLIB proxy of a controller method that does
 * not declare it, the exception arrives wrapped in an UndeclaredThrowableException; when the
 * method declares "throws Exception" it arrives unwrapped. Both used to fall through to the
 * generic handler (HTTP 500 and an ERROR log). These tests drive the real controllers, through a
 * real class proxy where the refusal comes from the aspect, and check that AdminExceptionHandler
 * returns a sanitized 403 without logging an error.
 */

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.*;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.content.plugins.PluginsController;
import inetsoft.web.admin.content.plugins.PluginsService;
import inetsoft.web.admin.security.SecurityConfigController;
import inetsoft.web.admin.server.ServerMonitoringController;
import inetsoft.web.reportviewer.service.HttpServletRequestWrapperArgumentResolver;
import inetsoft.web.security.DeniedMultiTenancyOrgUserAspect;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("core")
class AdminExceptionHandlerUnauthorizedAccessTest {
   private SecurityEngine securityEngine;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private Principal user;
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;

   @BeforeEach
   void setUp() {
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      user = mock(Principal.class);
      when(user.getName()).thenReturn("plain~;~host-org");

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      sutil = mockStatic(SUtil.class);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);

      logger = (Logger) LoggerFactory.getLogger(AdminExceptionHandler.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      sutil.close();
      orgManagerStatic.close();
   }

   // the reported case: no throws clause, so the proxy wraps the checked exception
   @Test
   void serverSummaryDeniedByAspectReturnsForbidden() throws Exception {
      ScheduleClient scheduleClient = mock(ScheduleClient.class);
      ServerMonitoringController controller;

      // the constructor reads server.type
      try(MockedStatic<SreeEnv> ignored = mockStatic(SreeEnv.class)) {
         controller = new ServerMonitoringController(
            null, null, null, null, null, null, null, null, null, null, null, scheduleClient,
            null, null, securityEngine);
      }

      MockMvc mvc = mvc(proxy(controller));

      expectForbidden(mvc.perform(get("/em/monitoring/server/summary")
                                     .principal(user)
                                     .accept(MediaType.APPLICATION_JSON)));
   }

   // declared "throws Exception", so the proxy passes the checked exception through unwrapped
   @Test
   void setEnableSecurityDeniedByAspectReturnsForbidden() throws Exception {
      SecurityConfigController controller =
         new SecurityConfigController(securityEngine, null, null);
      MockMvc mvc = mvc(proxy(controller));

      expectForbidden(mvc.perform(post("/api/em/security/set-enable-security")
                                     .principal(user)
                                     .contentType(MediaType.APPLICATION_JSON)
                                     .accept(MediaType.APPLICATION_JSON)
                                     .content("{\"enable\":false}")));
      verify(securityEngine, never()).disableSecurity();
   }

   // PluginsService.checkPermission refuses a caller without drivers-and-plugins access
   @Test
   void pluginsDeniedByServiceReturnsForbidden() throws Exception {
      when(securityEngine.checkPermission(any(Principal.class), any(ResourceType.class),
                                          anyString(), any(ResourceAction.class)))
         .thenReturn(false);
      MockMvc mvc = mvc(new PluginsController(pluginsService()));

      expectForbidden(mvc.perform(get("/api/em/settings/content/plugins/drivers/scan/{id}", "x")
                                     .principal(user)
                                     .accept(MediaType.APPLICATION_JSON)));
   }

   // a provider failure during the permission check stays fail-closed
   @Test
   void pluginsPermissionCheckFailureReturnsForbidden() throws Exception {
      when(securityEngine.checkPermission(any(Principal.class), any(ResourceType.class),
                                          anyString(), any(ResourceAction.class)))
         .thenThrow(new inetsoft.sree.security.SecurityException("provider unavailable"));
      MockMvc mvc = mvc(new PluginsController(pluginsService()));

      expectForbidden(mvc.perform(get("/api/data/plugins")
                                     .principal(user)
                                     .accept(MediaType.APPLICATION_JSON)));
   }

   private PluginsService pluginsService() {
      return new PluginsService(null, securityEngine, null, null, null, null, null);
   }

   private Object proxy(Object controller) {
      AspectJProxyFactory factory = new AspectJProxyFactory(controller);
      factory.setProxyTargetClass(true);
      factory.addAspect(new DeniedMultiTenancyOrgUserAspect(securityEngine));
      return factory.getProxy();
   }

   private MockMvc mvc(Object controller) {
      return MockMvcBuilders.standaloneSetup(controller)
         .setCustomArgumentResolvers(new HttpServletRequestWrapperArgumentResolver())
         .setControllerAdvice(new AdminExceptionHandler(mock(LogManager.class)))
         .build();
   }

   private void expectForbidden(ResultActions result) throws Exception {
      result
         .andExpect(status().isForbidden())
         .andExpect(jsonPath("$.type").value("UnauthorizedAccessException"))
         // sanitized: neither the user nor the resource is echoed back
         .andExpect(jsonPath("$.message", not(containsString("plain"))))
         .andExpect(jsonPath("$.message", not(containsString("/api/"))))
         .andExpect(jsonPath("$.message", not(containsString("/em/"))));

      assertTrue(appender.list.stream().noneMatch(e -> e.getLevel() == Level.ERROR),
                 "access denial must not be logged as an error");
   }
}
