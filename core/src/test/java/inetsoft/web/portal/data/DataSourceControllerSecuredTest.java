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
package inetsoft.web.portal.data;

/*
 * Bug #77134: the tabular editor helper endpoints (refreshView, oauth-params, oauth-tokens) had
 * no @Secured, so SecuredAspect never ran for them and any authenticated session could drive
 * the connector refresh path with a request-supplied definition. They must require Data tab
 * access, the same gate the XMLA editor helper endpoints use.
 */

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.FileSystemService;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.security.SecuredAspect;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class DataSourceControllerSecuredTest {
   private DatasourcesService datasourcesService;
   private AnalyticRepository repository;
   private HttpServletRequest request;
   private Principal user;
   private MockedStatic<SUtil> sUtilMock;
   private DataSourceController proxy;

   @BeforeEach
   void setUp() {
      datasourcesService = mock(DatasourcesService.class);
      repository = mock(AnalyticRepository.class);
      request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      user = () -> "alice";

      when(request.getSession()).thenReturn(session);
      when(session.getId()).thenReturn("session-1");
      when(request.getUserPrincipal()).thenReturn(user);
      when(request.getRequestURI()).thenReturn("/api/portal/data/datasources/refreshView");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

      sUtilMock = mockStatic(SUtil.class);
      sUtilMock.when(SUtil::getRepletRepository).thenReturn(repository);

      when(datasourcesService.refreshTabularView(any())).thenReturn(new DataSourceDefinition());
      when(datasourcesService.setOAuthTokens(any())).thenReturn(new DataSourceDefinition());

      DataSourceController controller = new DataSourceController(
         datasourcesService, mock(DataSourceBrowserService.class),
         mock(DatabaseDatasourcesService.class), mock(SecurityEngine.class),
         mock(DataSourceStatusService.class), mock(FileSystemService.class));
      AspectJProxyFactory factory = new AspectJProxyFactory(controller);
      factory.setProxyTargetClass(true);
      factory.addAspect(new SecuredAspect(mock(DataSourceRegistry.class),
                                          mock(ComponentAuthorizationService.class)));
      proxy = factory.getProxy();
   }

   @AfterEach
   void tearDown() {
      RequestContextHolder.resetRequestAttributes();
      sUtilMock.close();
   }

   @ParameterizedTest
   @ValueSource(strings = { "refreshTabularView", "getOAuthParameters", "setOAuthTokens" })
   void editorHelperEndpointsRequireDataTabAccess(String methodName) {
      Method method = findMethod(methodName);
      Secured secured = method.getAnnotation(Secured.class);
      assertNotNull(secured, methodName + " must be @Secured");
      assertEquals(1, secured.value().length);
      RequiredPermission permission = secured.value()[0];
      assertEquals(ResourceType.PORTAL_TAB, permission.resourceType());
      assertEquals("Data", permission.resource());
      assertArrayEquals(new ResourceAction[] { ResourceAction.ACCESS }, permission.actions());
   }

   @Test
   void deniedDataTabAccessBlocksAllEditorHelperEndpoints() throws Exception {
      when(repository.checkPermission(any(), eq(ResourceType.PORTAL_TAB), eq("Data"),
                                      eq(ResourceAction.ACCESS))).thenReturn(false);

      assertDenied(() -> proxy.refreshTabularView(new DataSourceDefinition(), request, user));
      assertDenied(() -> proxy.getOAuthParameters(mock(DataSourceOAuthParamsRequest.class), request));
      assertDenied(() -> proxy.setOAuthTokens(mock(DataSourceOAuthTokens.class), request));

      verify(datasourcesService, never()).refreshTabularView(any());
      verify(datasourcesService, never()).getOAuthParams(any());
      verify(datasourcesService, never()).setOAuthTokens(any());
   }

   @Test
   void grantedDataTabAccessReachesService() throws Exception {
      when(repository.checkPermission(any(), eq(ResourceType.PORTAL_TAB), eq("Data"),
                                      eq(ResourceAction.ACCESS))).thenReturn(true);

      assertNotNull(proxy.refreshTabularView(new DataSourceDefinition(), request, user));
      proxy.getOAuthParameters(mock(DataSourceOAuthParamsRequest.class), request);
      assertNotNull(proxy.setOAuthTokens(mock(DataSourceOAuthTokens.class), request));

      verify(datasourcesService).refreshTabularView(any());
      verify(datasourcesService).getOAuthParams(any());
      verify(datasourcesService).setOAuthTokens(any());
   }

   private static void assertDenied(Executable call) {
      // SecuredAspect throws the unchecked java.lang.SecurityException; the controller's package
      // also has inetsoft.sree.security.SecurityException, so the name must be fully qualified.
      assertThrows(java.lang.SecurityException.class, call);
   }

   private static Method findMethod(String name) {
      for(Method method : DataSourceController.class.getMethods()) {
         if(method.getName().equals(name)) {
            return method;
         }
      }

      throw new AssertionError("Method not found: " + name);
   }
}
