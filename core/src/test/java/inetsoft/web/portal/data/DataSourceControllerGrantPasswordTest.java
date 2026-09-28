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
 * Bug #77150: the OAuth password-grant endpoint had no @Secured, so SecuredAspect never ran for
 * it and any authenticated session could make the server perform the token request. It must
 * require Data tab access (portal data source editor) or worksheet access (composer worksheet
 * tabular query dialog).
 */

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.FileSystemService;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.composer.model.ws.TabularOAuthParams;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.security.SecuredAspect;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class DataSourceControllerGrantPasswordTest {
   private AnalyticRepository repository;
   private MockedStatic<SUtil> sUtilMock;
   private MockedStatic<AuthorizationClient> authClientMock;
   private Tokens tokens;
   private DataSourceController proxy;

   @BeforeEach
   void setUp() {
      repository = mock(AnalyticRepository.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      Principal user = () -> "alice";

      when(request.getSession()).thenReturn(session);
      when(session.getId()).thenReturn("session-1");
      when(request.getUserPrincipal()).thenReturn(user);
      when(request.getRequestURI()).thenReturn("/api/portal/data/datasources/grant-password");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

      sUtilMock = mockStatic(SUtil.class);
      sUtilMock.when(SUtil::getRepletRepository).thenReturn(repository);

      tokens = mock(Tokens.class);
      authClientMock = mockStatic(AuthorizationClient.class);
      authClientMock.when(() -> AuthorizationClient.doPasswordGrantAuth(
         any(), any(), any(), any(), any(), any())).thenReturn(tokens);

      DataSourceController controller = new DataSourceController(
         mock(DatasourcesService.class), mock(DataSourceBrowserService.class),
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
      authClientMock.close();
      sUtilMock.close();
   }

   @Test
   void grantPasswordRequiresDataTabOrWorksheetAccess() throws Exception {
      Secured secured = DataSourceController.class
         .getMethod("getPasswordGrantResponse", TabularOAuthParams.class)
         .getAnnotation(Secured.class);
      assertNotNull(secured, "getPasswordGrantResponse must be @Secured");
      assertEquals("OR", secured.operator());
      assertEquals(2, secured.value().length);

      RequiredPermission data = secured.value()[0];
      assertEquals(ResourceType.PORTAL_TAB, data.resourceType());
      assertEquals("Data", data.resource());
      assertArrayEquals(new ResourceAction[] { ResourceAction.ACCESS }, data.actions());

      RequiredPermission worksheet = secured.value()[1];
      assertEquals(ResourceType.WORKSHEET, worksheet.resourceType());
      assertEquals("*", worksheet.resource());
      assertArrayEquals(new ResourceAction[] { ResourceAction.ACCESS }, worksheet.actions());
   }

   @Test
   void deniedDataTabAndWorksheetAccessBlocksOutboundCall() throws Exception {
      grant(false, false);

      // SecuredAspect throws the unchecked java.lang.SecurityException; the controller's package
      // also has inetsoft.sree.security.SecurityException, so the name must be fully qualified.
      assertThrows(java.lang.SecurityException.class,
                   () -> proxy.getPasswordGrantResponse(params()));

      authClientMock.verify(() -> AuthorizationClient.doPasswordGrantAuth(
         any(), any(), any(), any(), any(), any()), never());
   }

   @Test
   void dataTabAccessAloneReachesOutboundCall() throws Exception {
      grant(true, false);
      assertReachesOutboundCall();
   }

   @Test
   void worksheetAccessAloneReachesOutboundCall() throws Exception {
      grant(false, true);
      assertReachesOutboundCall();
   }

   private void assertReachesOutboundCall() {
      assertSame(tokens, proxy.getPasswordGrantResponse(params()));
      authClientMock.verify(() -> AuthorizationClient.doPasswordGrantAuth(
         eq("user"), eq("secret"), eq("client"), eq("client-secret"), eq(List.of("scope")),
         eq("https://auth.example.com/token")));
   }

   private void grant(boolean dataTab, boolean worksheet) throws Exception {
      when(repository.checkPermission(any(), eq(ResourceType.PORTAL_TAB), eq("Data"),
                                      eq(ResourceAction.ACCESS))).thenReturn(dataTab);
      when(repository.checkPermission(any(), eq(ResourceType.WORKSHEET), eq("*"),
                                      eq(ResourceAction.ACCESS))).thenReturn(worksheet);
   }

   private static TabularOAuthParams params() {
      return TabularOAuthParams.builder()
         .license("license")
         .user("user")
         .password("secret")
         .clientId("client")
         .clientSecret("client-secret")
         .scope(List.of("scope"))
         .tokenUri("https://auth.example.com/token")
         .build();
   }
}
