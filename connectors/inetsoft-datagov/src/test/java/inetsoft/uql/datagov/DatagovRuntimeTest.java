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
package inetsoft.uql.datagov;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit test cases for <tt>DatagovRuntime</tt>.
 */
class DatagovRuntimeTest {
   @BeforeAll
   static void mockService() {
      MockedStatic<CredentialService> mockedCredentialService = Mockito.mockStatic(CredentialService.class);
      mockedCredentialService.when(() -> CredentialService.newCredential(CredentialType.PASSWORD))
         .thenReturn(mock(LocalPasswordCredential.class));
      mockedCredentialService.when(() -> CredentialService.newCredential(CredentialType.PASSWORD, false))
         .thenReturn(mock(LocalPasswordCredential.class));
   }

   /**
    * Tests the <tt>runQuery()</tt> method for proper operation.
    *
    * @throws AssertionError if the test fails.
    * @throws Exception if an unexpected error occurs.
    */
   @Test
   void testRunQuery() throws Exception {
      URL url = getClass().getResource("rows.json");
      DatagovDataSource dataSource = new DatagovDataSource();
      dataSource.setURL(url.toExternalForm());

      DatagovQuery query = new DatagovQuery();
      query.setDataSource(dataSource);

      DatagovRuntime runtime = new DatagovRuntime();
      XTableNode data = runtime.runQuery(query, new VariableTable());
      assertNotNull(data);

      assertEquals(15, data.getColCount());
      assertEquals("sid", data.getName(0));
      assertEquals("id", data.getName(1));
      assertEquals("position", data.getName(2));
      assertEquals("created_at", data.getName(3));
      assertEquals("created_meta", data.getName(4));
      assertEquals("updated_at", data.getName(5));
      assertEquals("updated_meta", data.getName(6));
      assertEquals("meta", data.getName(7));
      assertEquals("Year", data.getName(8));
      assertEquals("Leading Cause", data.getName(9));
      assertEquals("Sex", data.getName(10));
      assertEquals("Race_Ethnicity", data.getName(11));
      assertEquals("Deaths", data.getName(12));
      assertEquals("Death Rate", data.getName(13));
      assertEquals("Age Adjusted Death Rate", data.getName(14));

      List<Object[]> expectedData = new ArrayList<>();

      try(BufferedReader reader = new BufferedReader(new InputStreamReader(
             getClass().getResourceAsStream("rows.csv"))))
      {
         String line;

         while((line = reader.readLine()) != null) {
            line = line.trim();

            if(!line.isEmpty()) {
               String[] row = line.split("\t");
               Object[] rowData = new Object[row.length];

               for(int i = 0; i < row.length; i++) {
                  if(i == 0 || i == 2 || i == 3 || i == 5) {
                     rowData[i] = new BigDecimal(row[i]);
                  }
                  else {
                     rowData[i] = row[i];
                  }
               }

               expectedData.add(rowData);
            }
         }
      }

      int rowCount = 0;
      Object[] row = new Object[data.getColCount()];

      while(data.next()) {
         assertThat(expectedData.size(), greaterThan(rowCount));

         for(int i = 0; i < row.length; i++) {
            Object value = data.getObject(i);
            assertNotNull(value);
            Class<?> expectedType = (i == 0 || i == 2 || i == 3 || i == 5) ?
               BigDecimal.class : String.class;
            assertThat(value, instanceOf(expectedType));
            row[i] = value;
         }

         assertArrayEquals(expectedData.get(rowCount++), row);
      }

      assertEquals(expectedData.size(), rowCount);
   }
}
