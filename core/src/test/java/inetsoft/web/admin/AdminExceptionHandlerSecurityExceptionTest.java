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
 * Bug #77815: EM controllers and services refuse access by throwing the checked
 * inetsoft.sree.security.SecurityException. The throwing method must declare it, so it reaches
 * AdminExceptionHandler unwrapped, even through a class proxy. Only the wrapped form was mapped,
 * so a refused request fell through to the generic handler: HTTP 500, an ERROR log, and a body
 * that echoed the exception message, which can embed the principal. These tests drive the real
 * controllers and check that AdminExceptionHandler returns a sanitized 403 without logging an
 * error.
 */

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.sree.security.*;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.RoleController;
import inetsoft.web.admin.security.user.UserTreeService;
import inetsoft.web.admin.upload.UploadController;
import inetsoft.web.admin.upload.UploadService;
import inetsoft.web.factory.DecodePathVariableResolver;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import java.lang.reflect.UndeclaredThrowableException;
import java.security.Principal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("core")
class AdminExceptionHandlerSecurityExceptionTest {
   private SecurityProvider securityProvider;
   private UserTreeService userTreeService;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private Principal user;
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;

   @BeforeEach
   void setUp() {
      securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getOrganization(anyString())).thenReturn(mock(Organization.class));
      when(securityProvider.checkPermission(any(Principal.class), any(ResourceType.class),
                                            anyString(), any(ResourceAction.class)))
         .thenReturn(false);
      userTreeService = mock(UserTreeService.class);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // the refusal message embeds principal.toString(), which for SRPrincipal lists the
      // client, host, roles, groups, and organization
      user = new Principal() {
         @Override
         public String getName() {
            return "alice~;~host-org";
         }

         @Override
         public String toString() {
            return "Principal[alice,10.1.2.3]Roles:[secretRole,] Groups:[g1,]";
         }
      };

      logger = (Logger) LoggerFactory.getLogger(AdminExceptionHandler.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      orgManagerStatic.close();
   }

   // the reported case: RoleController.editRole throws the checked SecurityException
   @Test
   void editRoleDeniedReturnsForbidden() throws Exception {
      expectForbidden(editRole(mvc(roleController())));
      verifyNoInteractions(userTreeService);
   }

   // editRole declares "throws Exception", so the class proxy does not wrap the exception
   @Test
   void editRoleDeniedThroughClassProxyReturnsForbidden() throws Exception {
      ProxyFactory factory = new ProxyFactory(roleController());
      factory.setProxyTargetClass(true);
      factory.addAdvice((MethodInterceptor) invocation -> invocation.proceed());

      expectForbidden(editRole(mvc(factory.getProxy())));
      verifyNoInteractions(userTreeService);
   }

   // a second, unrelated EM endpoint that refuses with the same exception type
   @Test
   void uploadMavenDeniedReturnsForbidden() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(Principal.class), any(ResourceType.class),
                                          anyString(), any(ResourceAction.class)))
         .thenReturn(false);
      UploadService uploadService = mock(UploadService.class);
      MockMvc mvc = mvc(new UploadController(uploadService, securityEngine));

      expectForbidden(mvc.perform(post("/api/em/upload/maven")
                                     .principal(user)
                                     .contentType(MediaType.APPLICATION_JSON)
                                     .accept(MediaType.APPLICATION_JSON)
                                     .content("{\"gav\":\"a:b:1\"}")));
      verifyNoInteractions(uploadService);
   }

   @Test
   void springResolvesEachSecurityExceptionToItsHandler() {
      ExceptionHandlerMethodResolver resolver =
         new ExceptionHandlerMethodResolver(AdminExceptionHandler.class);

      assertEquals("handleCheckedSecurityException", resolver.resolveMethodByThrowable(
         new inetsoft.sree.security.SecurityException("x")).getName());
      assertEquals("handleUndeclaredThrowable", resolver.resolveMethodByThrowable(
         new UndeclaredThrowableException(
            new inetsoft.sree.security.SecurityException("x"))).getName());
      assertEquals("handleAccessDenied", resolver.resolveMethodByThrowable(
         new java.lang.SecurityException("x")).getName());
      assertEquals("handleSecurityConfigError", resolver.resolveMethodByThrowable(
         new SRSecurityException("x")).getName());
      assertEquals("handleException", resolver.resolveMethodByThrowable(
         new Exception("x")).getName());
   }

   private RoleController roleController() {
      return new RoleController(securityProvider, mock(IdentityService.class), userTreeService,
                                null, null, null);
   }

   private ResultActions editRole(MockMvc mvc) throws Exception {
      return mvc.perform(post("/api/em/security/user/edit-role/{provider}", "Primary")
                            .principal(user)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"r1\",\"oldName\":\"r1\"," +
                                        "\"organization\":\"host-org\"," +
                                        "\"isSysAdmin\":false,\"isOrgAdmin\":false}"));
   }

   private MockMvc mvc(Object controller) {
      return MockMvcBuilders.standaloneSetup(controller)
         .setPatternParser(null)
         .setCustomArgumentResolvers(new DecodePathVariableResolver())
         .setControllerAdvice(new AdminExceptionHandler(mock(LogManager.class)))
         .build();
   }

   private void expectForbidden(ResultActions result) throws Exception {
      result
         .andExpect(status().isForbidden())
         .andExpect(jsonPath("$.type").value("SecurityException"))
         // sanitized: neither the principal nor its roles, groups, or host are echoed back
         .andExpect(jsonPath("$.message", not(containsString("alice"))))
         .andExpect(jsonPath("$.message", not(containsString("secretRole"))))
         .andExpect(jsonPath("$.message", not(containsString("10.1.2.3"))))
         .andExpect(jsonPath("$.message", not(containsString("\"r1\""))));

      assertTrue(appender.list.stream().noneMatch(e -> e.getLevel() == Level.ERROR),
                 "access denial must not be logged as an error");
   }
}
