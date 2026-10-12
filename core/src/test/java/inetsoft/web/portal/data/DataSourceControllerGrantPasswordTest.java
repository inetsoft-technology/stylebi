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
 * it and any authenticated session could make the server perform the token request.
 *
 * Bug #78243: the server posts the password-grant form to the token URI that the request names.
 * Data tab access alone, or worksheet access, let a user who can't save any data source make the
 * server post to any address. The endpoint now requires Data tab access and the permission to save
 * the data source that the grant is for: the permission to create it in its folder if it is new,
 * or write permission on it (or on the parent of an additional connection) if it is saved. Whether
 * it is saved is decided by the stored data sources, never by the request.
 */

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.uql.util.Config;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceControllerGrantPasswordTest {
   private AnalyticRepository repository;
   private XRepository dataSources;
   private SecurityEngine securityEngine;
   private DataSourceRegistry registry;
   private MockedStatic<SUtil> sUtilMock;
   private MockedStatic<AuthorizationClient> authClientMock;
   private Tokens tokens;
   private Principal user;
   private DataSourceController proxy;

   @BeforeEach
   void setUp() {
      repository = mock(AnalyticRepository.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      user = () -> "alice";

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

      dataSources = mock(XRepository.class);
      securityEngine = mock(SecurityEngine.class);
      registry = mock(DataSourceRegistry.class);
      DatasourcesService datasourcesService = new DatasourcesService(
         dataSources, securityEngine, mock(DataSourceStatusService.class), registry,
         mock(Config.class));

      DataSourceController controller = new DataSourceController(
         datasourcesService, mock(DataSourceBrowserService.class),
         mock(DatabaseDatasourcesService.class), securityEngine,
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
   void grantPasswordRequiresDataTabAccess() throws Exception {
      Secured secured = DataSourceController.class
         .getMethod("getPasswordGrantResponse", TabularOAuthParams.class, Principal.class)
         .getAnnotation(Secured.class);
      assertNotNull(secured, "getPasswordGrantResponse must be @Secured");
      assertEquals(1, secured.value().length,
                   "worksheet access must not be an alternative to Data tab access");

      RequiredPermission data = secured.value()[0];
      assertEquals(ResourceType.PORTAL_TAB, data.resourceType());
      assertEquals("Data", data.resource());
      assertArrayEquals(new ResourceAction[] { ResourceAction.ACCESS }, data.actions());
   }

   @Test
   void deniedDataTabAccessBlocksOutboundCall() throws Exception {
      grantTabs(false, false);
      // the caller could create the data source, but the Data tab gate still applies
      grantCreate(true);

      // SecuredAspect throws the unchecked java.lang.SecurityException; the controller's package
      // also has inetsoft.sree.security.SecurityException, so the name must be fully qualified.
      assertThrows(java.lang.SecurityException.class,
                   () -> proxy.getPasswordGrantResponse(params(null, "ds", null, ""), user));
      assertNoOutboundCall();
   }

   @Test
   void worksheetAccessAloneBlocksOutboundCall() throws Exception {
      grantTabs(false, true);
      grantCreate(true);

      assertThrows(java.lang.SecurityException.class,
                   () -> proxy.getPasswordGrantResponse(params(null, "ds", null, ""), user));
      assertNoOutboundCall();
   }

   @Test
   void dataTabAccessAloneBlocksOutboundCall() throws Exception {
      grantTabs(true, false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> proxy.getPasswordGrantResponse(params(null, "ds", null, ""), user));
      assertNoOutboundCall();
   }

   @Test
   void dataTabAccessWithoutDataSourceIdentityBlocksOutboundCall() throws Exception {
      grantTabs(true, false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> proxy.getPasswordGrantResponse(params(null, null, null, null), user));
      assertNoOutboundCall();
   }

   @Test
   void newDataSourceWithCreatePermissionReachesOutboundCall() throws Exception {
      grantTabs(true, false);
      grantCreate(true);

      assertReachesOutboundCall(params(null, "ds", null, ""));
   }

   @Test
   void newDataSourceInWritableFolderReachesOutboundCall() throws Exception {
      grantTabs(true, false);
      folder("folder");
      grantFolderWrite("folder", true);

      assertReachesOutboundCall(params(null, "ds", null, "folder"));
   }

   @Test
   void newDataSourceInFolderNeedsWriteOnThatFolder() throws Exception {
      grantTabs(true, false);
      // the create data source permission only covers the root folder
      grantCreate(true);
      folder("folder");
      grantFolderWrite("folder", false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> proxy.getPasswordGrantResponse(params(null, "ds", null, "folder"), user));
      assertNoOutboundCall();
   }

   @Test
   void existingDataSourceWithWritePermissionReachesOutboundCall() throws Exception {
      grantTabs(true, false);
      stored("folder/ds");
      grantWrite("folder/ds", true);

      assertReachesOutboundCall(params(null, "ds", null, "folder"));
   }

   @Test
   void existingDataSourceWithoutWritePermissionBlocksOutboundCall() throws Exception {
      grantTabs(true, false);
      // the permission to create a data source in the folder does not cover a saved one
      grantCreate(true);
      folder("folder");
      grantFolderWrite("folder", true);
      stored("folder/ds");
      grantWrite("folder/ds", false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> proxy.getPasswordGrantResponse(params(null, "ds", null, "folder"), user));
      assertNoOutboundCall();
   }

   @Test
   void renamedDataSourceIsCheckedUnderItsSavedName() throws Exception {
      grantTabs(true, false);
      grantCreate(true);
      stored("ds");
      grantWrite("ds", false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> proxy.getPasswordGrantResponse(params(null, "renamed", "ds", ""), user));
      assertNoOutboundCall();
   }

   @Test
   void additionalConnectionWithParentWritePermissionReachesOutboundCall() throws Exception {
      grantTabs(true, false);
      stored("folder/parent");
      grantWrite("folder/parent", true);

      assertReachesOutboundCall(params("parent", "connection", null, "folder"));
   }

   @Test
   void additionalConnectionWithoutParentWritePermissionBlocksOutboundCall() throws Exception {
      grantTabs(true, false);
      grantCreate(true);
      folder("folder");
      grantFolderWrite("folder", true);
      stored("folder/parent");
      grantWrite("folder/parent", false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> proxy.getPasswordGrantResponse(
                      params("parent", "connection", null, "folder"), user));
      assertNoOutboundCall();
   }

   private void assertReachesOutboundCall(TabularOAuthParams params) throws Exception {
      assertSame(tokens, proxy.getPasswordGrantResponse(params, user));
      authClientMock.verify(() -> AuthorizationClient.doPasswordGrantAuth(
         eq("user"), eq("secret"), eq("client"), eq("client-secret"), eq(List.of("scope")),
         eq("https://auth.example.com/token")));
   }

   private void assertNoOutboundCall() {
      authClientMock.verify(() -> AuthorizationClient.doPasswordGrantAuth(
         any(), any(), any(), any(), any(), any()), never());
   }

   private void grantTabs(boolean dataTab, boolean worksheet) throws Exception {
      when(repository.checkPermission(any(), eq(ResourceType.PORTAL_TAB), eq("Data"),
                                      eq(ResourceAction.ACCESS))).thenReturn(dataTab);
      when(repository.checkPermission(any(), eq(ResourceType.WORKSHEET), eq("*"),
                                      eq(ResourceAction.ACCESS))).thenReturn(worksheet);
   }

   private void grantCreate(boolean allowed) throws Exception {
      when(securityEngine.checkPermission(any(), eq(ResourceType.CREATE_DATA_SOURCE), eq("*"),
                                          eq(ResourceAction.ACCESS))).thenReturn(allowed);
   }

   private void grantFolderWrite(String folder, boolean allowed) throws Exception {
      when(securityEngine.checkPermission(any(), eq(ResourceType.DATA_SOURCE_FOLDER), eq(folder),
                                          eq(ResourceAction.WRITE))).thenReturn(allowed);
   }

   private void grantWrite(String path, boolean allowed) throws Exception {
      when(securityEngine.checkPermission(any(), eq(ResourceType.DATA_SOURCE), eq(path),
                                          eq(ResourceAction.WRITE))).thenReturn(allowed);
   }

   private void folder(String path) {
      when(registry.getDataSourceFolder(path)).thenReturn(mock(DataSourceFolder.class));
   }

   private void stored(String path) throws Exception {
      XDataSource dataSource = mock(XDataSource.class);
      when(dataSource.getFullName()).thenReturn(path);
      when(dataSources.getDataSource(path)).thenReturn(dataSource);
   }

   private static TabularOAuthParams params(String parentDataSource, String name, String oldName,
                                            String parentPath)
   {
      return TabularOAuthParams.builder()
         .license("license")
         .user("user")
         .password("secret")
         .clientId("client")
         .clientSecret("client-secret")
         .scope(List.of("scope"))
         .tokenUri("https://auth.example.com/token")
         .parentDataSource(parentDataSource)
         .dataSourceName(name)
         .dataSourceOldName(oldName)
         .dataSourceParentPath(parentPath)
         .build();
   }
}
