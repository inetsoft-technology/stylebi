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
package inetsoft.web.wiz.service;

import inetsoft.uql.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core")
class MetadataApiServicePartitionTest {

   @Test
   void isPostgresDriver_returnsTrueForPostgresDriver() {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDriver()).thenReturn("org.postgresql.Driver");
      assertTrue(MetadataApiService.isPostgresDriver(ds));
   }

   @Test
   void isPostgresDriver_returnsFalseForMysqlDriver() {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDriver()).thenReturn("com.mysql.cj.jdbc.Driver");
      assertFalse(MetadataApiService.isPostgresDriver(ds));
   }

   @Test
   void isPostgresDriver_returnsFalseForNullDriver() {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getDriver()).thenReturn(null);
      assertFalse(MetadataApiService.isPostgresDriver(ds));
   }
}
