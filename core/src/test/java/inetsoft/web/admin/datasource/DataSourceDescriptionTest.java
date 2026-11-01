/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.datasource;

import inetsoft.uql.XDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The data source list entry must report the same type vocabulary as the detail endpoint
 * ({@code DataSourceService#getDataSource}): "jdbc" or "tabular" plus the connector in
 * {@code tabularType}, never the raw connector name.
 */
@Tag("core")
class DataSourceDescriptionTest {
   @Test
   void jdbcDataSourceIsReportedAsJdbcWithoutTabularType() {
      DataSourceDescription d = new DataSourceDescription(source("Examples/Orders", "jdbc"), "id1");

      assertEquals("jdbc", d.getType());
      assertNull(d.getTabularType());
   }

   @Test
   void restDataSourceIsReportedAsTabularWithConnectorType() {
      DataSourceDescription d = new DataSourceDescription(source("My Rest", "Rest"), "id2");

      assertEquals("tabular", d.getType());
      assertEquals("Rest", d.getTabularType());
   }

   @Test
   void everyNonJdbcConnectorIsReportedAsTabular() {
      for(String connector : new String[] { "Cassandra", "xmla", "Rest.XML", "MongoDB" }) {
         DataSourceDescription d = new DataSourceDescription(source("ds", connector), "id");

         assertEquals("tabular", d.getType(), connector);
         assertEquals(connector, d.getTabularType(), connector);
      }
   }

   @Test
   void listEntryMatchesDetailEntryForTabularSource() {
      XDataSource ds = source("My Rest", "Rest");
      DataSourceDescription list = new DataSourceDescription(ds, "id2");
      TabularDataSourceProperties detail = new TabularDataSourceProperties(ds, "id2");

      assertEquals(detail.getType().toString(), list.getType());
      assertEquals(detail.getTabularType(), list.getTabularType());
   }

   @Test
   void tabularTypeParticipatesInEqualsAndHashCode() {
      DataSourceDescription a = new DataSourceDescription(source("ds", "Rest"), "id");
      DataSourceDescription b = new DataSourceDescription(source("ds", "Rest"), "id");
      DataSourceDescription c = new DataSourceDescription(source("ds", "Cassandra"), "id");

      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
      assertNotEquals(a, c);
   }

   @Test
   void nullDataSourceLeavesTypeUnset() {
      DataSourceDescription d = new DataSourceDescription(null, "id");

      assertNull(d.getType());
      assertNull(d.getTabularType());
   }

   private static XDataSource source(String fullName, String type) {
      XDataSource ds = mock(XDataSource.class);
      when(ds.getFullName()).thenReturn(fullName);
      when(ds.getType()).thenReturn(type);
      return ds;
   }
}
