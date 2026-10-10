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
package inetsoft.web.admin.content.repository;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.admin.content.database.*;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76697 (AA-001): a data source persisted as Custom must reload as Custom even when its
 * driver class also belongs to a known database type.
 */
@Tag("core")
class DatabaseDatasourcesServiceCustomTypeTest {
   @BeforeEach
   void setUp() {
      sreeEnv = mockStatic(SreeEnv.class);
      DatabaseTypeService typeService = new DatabaseTypeService(
         List.of(new MySQLDatabaseType(), new CustomDatabaseType()));
      repository = mock(XRepository.class);
      service = new DatabaseDatasourcesService(
         typeService, mock(SecurityEngine.class), mock(DatabaseSettingsService.class),
         repository, mock(ResourcePermissionService.class),
         mock(DataSourceStatusService.class), mock(IgniteSessionRepository.class),
         mock(DataSourceRegistry.class), mock(RenameTransformHandler.class));
   }

   @AfterEach
   void tearDown() {
      sreeEnv.close();
   }

   @Test
   void customFlagWithKnownDriverReloadsAsCustom() throws Exception {
      JDBCDataSource ds = source("com.mysql.jdbc.Driver", "jdbc:mysql://h:3306/db?x=1", true);

      DatabaseDefinition def = service.getDatabaseDefinition(ds, null);

      assertEquals(CustomDatabaseType.TYPE, def.getType());
      assertTrue(def.getInfo() instanceof CustomDatabaseType.CustomDatabaseInfo);
      assertEquals("jdbc:mysql://h:3306/db?x=1", def.getInfo().getCustomUrl());
   }

   @Test
   void customFlagWithUnknownDriverReloadsAsCustom() throws Exception {
      JDBCDataSource ds = source("org.example.Unknown", "jdbc:unknown://h/db", true);

      assertEquals(CustomDatabaseType.TYPE, service.getDatabaseDefinition(ds, null).getType());
   }

   @Test
   void nonCustomKnownDriverStaysKnownType() throws Exception {
      JDBCDataSource ds = source("com.mysql.jdbc.Driver", "jdbc:mysql://h:3306/db", false);

      assertEquals(MySQLDatabaseType.TYPE, service.getDatabaseDefinition(ds, null).getType());
   }

   @Test
   void nonCustomUnknownDriverFallsBackToCustom() throws Exception {
      JDBCDataSource ds = source("org.example.Unknown", "jdbc:unknown://h/db", false);

      assertEquals(CustomDatabaseType.TYPE, service.getDatabaseDefinition(ds, null).getType());
   }

   @Test
   void additionalDatabasesHonourCustomFlagPerChild() throws Exception {
      JDBCDataSource custom = source("com.mysql.jdbc.Driver", "jdbc:mysql://h/a", true);
      JDBCDataSource plain = source("com.mysql.jdbc.Driver", "jdbc:mysql://h/b", false);
      JDBCDataSource parent = mock(JDBCDataSource.class);
      when(parent.getDataSourceNames()).thenReturn(new String[] { "a", "b" });
      when(parent.getDataSource("a")).thenReturn(custom);
      when(parent.getDataSource("b")).thenReturn(plain);
      when(repository.getDataSource("parent")).thenReturn(parent);

      DatabaseDefinition[] defs = service.getAdditionalDatabaseDefinition("parent", null);

      assertEquals(2, defs.length);
      assertEquals(CustomDatabaseType.TYPE, defs[0].getType());
      assertEquals(MySQLDatabaseType.TYPE, defs[1].getType());
   }

   private static JDBCDataSource source(String driver, String url, boolean custom) {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getName()).thenReturn("ds");
      when(ds.getFullName()).thenReturn("ds");
      when(ds.getDriver()).thenReturn(driver);
      when(ds.getURL()).thenReturn(url);
      when(ds.isCustom()).thenReturn(custom);
      return ds;
   }

   private MockedStatic<SreeEnv> sreeEnv;
   private XRepository repository;
   private DatabaseDatasourcesService service;
}
