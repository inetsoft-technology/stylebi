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
package inetsoft.web.portal.service.datasource;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.portal.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77429: POST /api/data/datasources/statuses tested, saved and reported the status of any
 * data source the client named. A data source the user cannot read must get a null entry, the
 * same as one that does not exist, and must be neither tested nor saved.
 *
 * Bug #78249: an additional connection named by its registry path (Secret/add) was checked as a
 * data source in a folder "Secret", which falls back to the root folder's READ for everyone. It
 * must be checked as Secret::add, which inherits the restriction on Secret.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DataSourceStatusServicePermissionTest {
   private static final String READABLE = "Readable";
   private static final String DENIED = "Secret";
   private static final String MISSING = "NoSuchSource";
   private static final String DENIED_ADDITIONAL = DENIED + "/add";

   private XRepository repository;
   private SecurityEngine securityEngine;
   private Principal principal;
   private JDBCDataSource readable;
   private JDBCDataSource denied;
   private JDBCDataSource deniedAdditional;
   private DataSourceStatusService service;

   @BeforeEach
   void setUp() throws Exception {
      repository = mock(XRepository.class);
      securityEngine = mock(SecurityEngine.class);
      principal = new SRPrincipal(new IdentityID("alice", "host-org"));
      readable = mock(JDBCDataSource.class);
      denied = mock(JDBCDataSource.class);
      deniedAdditional = mock(JDBCDataSource.class);
      DataSourceRegistry registry = mock(DataSourceRegistry.class);
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { READABLE, DENIED });

      when(repository.getDataSource(READABLE)).thenReturn(readable);
      when(repository.getDataSource(DENIED)).thenReturn(denied);
      when(repository.getDataSource(DENIED_ADDITIONAL)).thenReturn(deniedAdditional);
      when(deniedAdditional.getStatus()).thenReturn(new XDataSource.Status(null, true, 0L));
      when(readable.getStatus()).thenReturn(new XDataSource.Status(null, true, 0L));
      when(denied.getStatus()).thenReturn(new XDataSource.Status(null, true, 0L));
      when(securityEngine.checkPermission(
         any(), eq(ResourceType.DATA_SOURCE), anyString(), eq(ResourceAction.READ)))
         .thenReturn(false);
      when(securityEngine.checkPermission(
         any(), eq(ResourceType.DATA_SOURCE), eq(READABLE), eq(ResourceAction.READ)))
         .thenReturn(true);
      // READ may be inherited for a name that does not exist, it still must not be reported
      when(securityEngine.checkPermission(
         any(), eq(ResourceType.DATA_SOURCE), eq(MISSING), eq(ResourceAction.READ)))
         .thenReturn(true);
      // the raw additional connection path inherits the root folder's READ for everyone
      when(securityEngine.checkPermission(
         any(), eq(ResourceType.DATA_SOURCE), eq(DENIED_ADDITIONAL), eq(ResourceAction.READ)))
         .thenReturn(true);

      service = new DataSourceStatusService(repository, securityEngine, registry);
   }

   @Test
   void unreadableAndMissingSourcesGetNoStatus() throws Exception {
      List<DataSourceStatus> result = service.getDataSourceConnectionStatuses(
         request(true, READABLE, DENIED, MISSING), principal);

      assertEquals(3, result.size());
      assertNotNull(result.get(0));
      assertTrue(result.get(0).connected());
      assertNull(result.get(1));
      assertNull(result.get(2));

      verify(repository).testDataSource(any(), same(readable), isNull());
      verify(repository).updateDataSourceStatus(same(readable));
      verify(repository, never()).testDataSource(any(), same(denied), any());
      verify(repository, never()).updateDataSourceStatus(same(denied));
      verify(repository, never()).getDataSource(DENIED);
      verify(repository, never()).testDataSource(any(), isNull(), any());
   }

   @Test
   void unreadableSourceStoredStatusIsNotReported() throws Exception {
      List<DataSourceStatus> result = service.getDataSourceConnectionStatuses(
         request(false, READABLE, DENIED, MISSING), principal);

      assertNotNull(result.get(0));
      assertNull(result.get(1));
      assertNull(result.get(2));
      verify(repository, never()).testDataSource(any(), any(), any());
      verify(repository, never()).updateDataSourceStatus(any());
   }

   @Test
   void additionalConnectionOfUnreadableSourceGetsNoStatus() throws Exception {
      for(boolean update : new boolean[] { true, false }) {
         List<DataSourceStatus> result = service.getDataSourceConnectionStatuses(
            request(update, DENIED_ADDITIONAL, DENIED, DENIED + "::add"), principal);

         assertEquals(3, result.size());
         assertNull(result.get(0), "P/add, update " + update);
         assertNull(result.get(1), "P, update " + update);
         assertNull(result.get(2), "P::add, update " + update);
      }

      verify(securityEngine, times(4)).checkPermission(
         any(), eq(ResourceType.DATA_SOURCE), eq(DENIED + "::add"), eq(ResourceAction.READ));
      verify(repository, never()).getDataSource(DENIED_ADDITIONAL);
      verify(repository, never()).testDataSource(any(), any(), any());
      verify(repository, never()).updateDataSourceStatus(any());
   }

   private static DataSourceConnectionStatusRequest request(boolean update, String... paths) {
      return ImmutableDataSourceConnectionStatusRequest.builder()
         .paths(List.of(paths))
         .updateStatus(update)
         .timeZone("UTC")
         .build();
   }
}
