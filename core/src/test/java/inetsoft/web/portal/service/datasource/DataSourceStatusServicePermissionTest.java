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

   private XRepository repository;
   private SecurityEngine securityEngine;
   private Principal principal;
   private JDBCDataSource readable;
   private JDBCDataSource denied;
   private DataSourceStatusService service;

   @BeforeEach
   void setUp() throws Exception {
      repository = mock(XRepository.class);
      securityEngine = mock(SecurityEngine.class);
      principal = new SRPrincipal(new IdentityID("alice", "host-org"));
      readable = mock(JDBCDataSource.class);
      denied = mock(JDBCDataSource.class);

      when(repository.getDataSource(READABLE)).thenReturn(readable);
      when(repository.getDataSource(DENIED)).thenReturn(denied);
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

      service = new DataSourceStatusService(repository, securityEngine);
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

   private static DataSourceConnectionStatusRequest request(boolean update, String... paths) {
      return ImmutableDataSourceConnectionStatusRequest.builder()
         .paths(List.of(paths))
         .updateStatus(update)
         .timeZone("UTC")
         .build();
   }
}
