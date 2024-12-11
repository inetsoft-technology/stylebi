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
package inetsoft.uql.onedrive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.uql.*;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.*;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OneDriveRuntimeTests {
   private OneDriveDataSource dataSource;
   private OneDriveRuntime runtime;

   @BeforeAll
   static void mockService() {
      MockedStatic<CredentialService> mockedCredentialService = Mockito.mockStatic(CredentialService.class);
      mockedCredentialService.when(() -> CredentialService.newCredential(CredentialType.CLIENT_GRANT))
         .thenReturn(mock(LocalClientCredentialsGrant.class));
      mockedCredentialService.when(() -> CredentialService.newCredential(CredentialType.CLIENT_GRANT, false))
         .thenReturn(mock(LocalClientCredentialsGrant.class));
   }

   @BeforeEach
   void setupDataSource() {
      dataSource = new OneDriveDataSource();
      dataSource.setName("OneDrive Test");
      dataSource.setAccessToken("AnyAccessToken");
      runtime = new OneDriveRuntime();
   }

   @Test
   void shouldReadCSV() throws Exception {
      Object[][] expected = readExpected("shouldReadCSV.expected.json");

      OneDriveQuery query0 = new OneDriveQuery();
      OneDriveQuery query = spy(query0);
      query.setDataSource(dataSource);
      query.setName("OneDrive Test Query");
      query.setPath("Test/TestCSV.csv");

      doAnswer((invocation) -> readFile("TestCSV.csv")).when(query).getFile();
      assert OneDriveRuntime.getFileURL(query)
         .equals("https://graph.microsoft.com/v1.0/me/drive/root:/Test%2FTestCSV.csv:/content");
      XNodeTableLens table = getTable(query);

      try {
         Object[][] actual = getData(table);
         assertArrayEquals(expected, actual);
      }
      finally {
         table.dispose();
      }
   }

   @Test
   void shouldReadExcel() throws Exception {
      Object[][] expected = readExpected("shouldReadExcel.expected.json");

      OneDriveQuery query0 = new OneDriveQuery();
      OneDriveQuery query = spy(query0);
      query.setDataSource(dataSource);
      query.setName("OneDrive Test Query");
      query.setPath("Test/TestExcel.xlsx");

      doAnswer((invocation) -> readFile("TestExcel.xlsx")).when(query).getFile();
      query.setExcelSheet("Sheet2");
      assert OneDriveRuntime.getFileURL(query)
         .equals("https://graph.microsoft.com/v1.0/me/drive/root:/Test%2FTestExcel.xlsx:/content");
      XNodeTableLens table = getTable(query);

      try {
         Object[][] actual = getData(table);
         assertArrayEquals(expected, actual);
      }
      finally {
         table.dispose();
      }
   }

   private Object[][] readExpected(String file) throws IOException {
      try(InputStream input = getClass().getResourceAsStream(file)) {
         ObjectMapper mapper = new ObjectMapper();
         ArrayNode root = (ArrayNode) mapper.readTree(input);
         Object[][] expected = new Object[root.size()][];

         for(int i = 0; i < expected.length; i++) {
            ArrayNode array = (ArrayNode) root.get(i);
            expected[i] = mapper.treeToValue(array, Object[].class);
         }

         return expected;
      }
   }

   private InputStream readFile(String file) {
      InputStream input = getClass().getResourceAsStream(file);
      assert input != null;
      return input;
   }

   private XNodeTableLens getTable(OneDriveQuery query) {
      XTableNode node = runtime.runQuery(query, new VariableTable());
      assertNotNull(node);

      XNodeTableLens table = new XNodeTableLens(node);

      return table;
   }

   private Object[][] getData(XNodeTableLens table) {
      table.moreRows(XTable.EOT);
      Object[][] actual = new Object[table.getRowCount()][];

      for(int i = 0; table.moreRows(i); i++) {
         actual[i] = new Object[table.getColCount()];

         for(int j = 0; j < table.getColCount(); j++) {
            actual[i][j] = table.getObject(i, j);
         }
      }

      return actual;
   }

}
