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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.DatabaseModelBrowserService;
import inetsoft.web.security.SecuredAspect;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78249: GET /api/portal/data/datasource/refresh-metadata had no permission check, so any
 * user could wipe and reload the meta-data of any data source. It must require READ on the data
 * source, the same as /api/data/datasources/refresh/**, with an additional connection P/add
 * checked as P::add.
 */
@Tag("core")
class DatabaseDatasourcesControllerRefreshMetadataTest {
   private AnalyticRepository repository;
   private XEngine xRepository;
   private MockedStatic<SUtil> sUtilMock;
   private DatabaseDatasourcesController proxy;
   private Principal user;

   @BeforeEach
   void setUp() {
      repository = mock(AnalyticRepository.class);
      xRepository = mock(XEngine.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      user = () -> "alice";

      when(request.getSession()).thenReturn(session);
      when(session.getId()).thenReturn("session-1");
      when(request.getUserPrincipal()).thenReturn(user);
      when(request.getRequestURI()).thenReturn("/api/portal/data/datasource/refresh-metadata");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

      sUtilMock = mockStatic(SUtil.class);
      sUtilMock.when(SUtil::getRepletRepository).thenReturn(repository);

      DataSourceRegistry registry = mock(DataSourceRegistry.class);
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "Secret", "Open" });

      DatabaseDatasourcesController controller = new DatabaseDatasourcesController(
         mock(DatabaseDatasourcesService.class), mock(DatabaseModelBrowserService.class),
         mock(DataModelFolderManagerService.class), mock(DataSourceService.class), xRepository);
      AspectJProxyFactory factory = new AspectJProxyFactory(controller);
      factory.setProxyTargetClass(true);
      factory.addAspect(new SecuredAspect(registry, mock(ComponentAuthorizationService.class)));
      proxy = factory.getProxy();
   }

   @AfterEach
   void tearDown() {
      RequestContextHolder.resetRequestAttributes();
      sUtilMock.close();
   }

   @Test
   void unreadableDataSourceIsNotRefreshed() throws Exception {
      // the raw additional connection path would get the root folder's READ for everyone
      when(repository.checkPermission(any(), eq(ResourceType.DATA_SOURCE), anyString(),
                                      eq(ResourceAction.READ)))
         .thenAnswer(inv -> !((String) inv.getArgument(2)).startsWith("Secret"));

      for(String path : new String[] { "Secret", "Secret/add", "Secret::add" }) {
         assertThrows(java.lang.SecurityException.class, () -> proxy.refreshMetadata(path), path);
      }

      verify(repository).checkPermission(user, ResourceType.DATA_SOURCE, "Secret",
                                         ResourceAction.READ);
      verify(repository, times(2)).checkPermission(user, ResourceType.DATA_SOURCE, "Secret::add",
                                                   ResourceAction.READ);
      verify(xRepository, never()).refreshMetaData(anyString());
   }

   @Test
   void readableDataSourceIsRefreshed() throws Exception {
      when(repository.checkPermission(any(), eq(ResourceType.DATA_SOURCE), anyString(),
                                      eq(ResourceAction.READ)))
         .thenAnswer(inv -> ((String) inv.getArgument(2)).startsWith("Open"));

      assertTrue(proxy.refreshMetadata("Open"));
      assertTrue(proxy.refreshMetadata("Open/add"));

      verify(xRepository).refreshMetaData("Open");
      verify(xRepository).refreshMetaData("Open/add");
   }
}
