/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.script.viewsheet;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

@SreeHome(importResources = "ViewsheetScopeTest.vso")
public class ViewsheetScopeTest {
   @RegisterExtension
   @Order(1)
   ControllersExtension controllers = new ControllersExtension();

   @RegisterExtension
   @Order(2)
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent(), controllers);

   private ViewsheetScope viewsheetScope;
   private ViewsheetSandbox sandbox;
   ViewsheetService viewsheetService = mock(ViewsheetService.class);

   @BeforeEach
   void setUp() throws Exception {
      openMocks(this);
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      sandbox = rvs.getViewsheetSandbox();
      Principal principal = mock(Principal.class);
      when(viewsheetService.getViewsheet(viewsheetResource.getRuntimeId(), principal))
         .thenReturn(viewsheetResource.getRuntimeViewsheet());

      viewsheetScope = new ViewsheetScope(sandbox, false);
   }

   private XEmbeddedTable getXEmbeddedTable() {
       Worksheet worksheet = sandbox.getViewsheet().getOriginalWorksheet();
       EmbeddedTableAssembly tableAssembly = (EmbeddedTableAssembly) worksheet.getAssembly("Query1");
       if (tableAssembly == null) {
           throw new RuntimeException("EmbeddedTableAssembly 'Query1' not found.");
       }
       viewsheetScope.refreshData();
       return tableAssembly.getEmbeddedData();
   }

   /**
    * test toList with array of objects
    */
   @Test
   void testToListWithObj() {
      Object arrObj = new Object[]{"banana", "apple", "apple", "cherry"};
      Object result = viewsheetScope.toList(arrObj, "sort=asc, distinct=true");
      assertArrayEquals(new Object[]{"apple", "banana", "cherry"}, (Object[]) result);

      Object arrObj1 = new Object[]{"apple", "banana", "cherry"};
      Object result1 = viewsheetScope.toList(arrObj1, "maxrows=2, remainder=other");
      assertArrayEquals(new Object[]{"apple", "banana", "other"}, (Object[]) result1);

      // Test case with null values
      Object arrObj4 = new Object[]{"apple", null, "banana"};
      Object result4 = viewsheetScope.toList(arrObj4, "sort=asc");
      assertArrayEquals(new Object[]{null, "apple", "banana"}, (Object[]) result4);
   }

   /**
    * test toList with array of date objects
    */
   @Test
   void testToListWithDateObj() {
      // Test case with different date format, Assuming the date format is "yyyy-MM-dd"
      Object arrObj1 = new Object[]{"2022-01-01", "2023-02-02", "2022-01-03"};
      Object result1 = viewsheetScope.toList(arrObj1, "date=year, sort=asc");
      assertArrayEquals(new Object[]{"2022-01-01", "2022-01-03", "2023-02-02"}, (Object[]) result1);

      // Test data is date type
      Object[] dateArray1 ={
         new Date(2021 - 1900, 0, 1),
         new Date(2023 - 1900, 5, 15),
         new Date(2025 - 1900, 11, 31)
      };

      Object[] dateArray2 = dateArray1.clone();

      Object result2 = viewsheetScope.toList(dateArray1, "date=month, sort=desc");
      assertArrayEquals(new Object[]{12, 6, 1}, (Object[])result2);

      Object[] expArray2 = {
         new Date(2021 - 1900, 0, 1),
         new Date(2022 - 1900, 0, 1),
         new Date(2023 - 1900, 0, 1),
         new Date(2024 - 1900, 0, 1),
         new Date(2025 - 1900, 0, 1),
      };
      Object result3 = viewsheetScope.toList(dateArray2, "rounddate=year,interval=1");
      assertArrayEquals(expArray2, (Object[])result3);
   }

   /**
    * test toList with DefaultTableLens
    */
   @Test
   void testToListWithXTable() {
      DefaultTableLens table1 = new DefaultTableLens(new Object[][]{
         {"col1", "col2", "date"},
         {"banana", "1", new Date(2021 - 1900, 0, 1)},
         {"apple", "2",  new Date(2023 - 1900, 5, 15)},
         {"banana", "3", new Date(2025 - 1900, 11, 31)},
         {"peach", "2",  new Date(2021 - 1900, 2, 1)},
         });
      DefaultTableLens table2 = table1.clone();
      Object result = viewsheetScope.toList(table1, "field=col1, sort=asc, sorton=col1, distinct=true");
      assertArrayEquals(new Object[]{"apple", "banana", "peach"}, (Object[]) result);

      // Test topN and timeSeries
      Object result2 = viewsheetScope.toList(table2, "field=date, sort=desc,,sort2=desc, rounddate=quarter, sorton=col2,timeseries=true,maxrows=2, remainder=other");
      assertArrayEquals(new Object[]{new Date(2023 - 1900, 3, 1),
                                     new Date(2025 - 1900, 9, 1),
                                     "other"}, (Object[]) result2);

      // Test sort by options
      Object result3 = viewsheetScope.toList(table2, "field=date,sort=asc,rounddate=year,sorton2=col2,interval=2.0,timeseries=false");
      assertArrayEquals(new Object[]{new Date(2020 - 1900, 0, 1),
                                     new Date(2022 - 1900, 0, 1),
                                     new Date(2024 - 1900, 0, 1)}, (Object[]) result3);
   }

   @Test
   void testAppendRowWithTypeMismatchThrowsException() {
      RuntimeException exception = assertThrows(RuntimeException.class, () ->
         viewsheetScope.appendRow("Query1", new Object[]{2, true, null, null}));
      assertEquals("Failed to append row. Check order and data type!", exception.getMessage());
   }

   @Test
   void testAppendRowWithColCountMismatchThrowsException() {
      RuntimeException exception = assertThrows(RuntimeException.class, () ->
         viewsheetScope.appendRow("Query1", new Object[]{2, true, null}));
      assertEquals("Failed to append row. Check order and data type!", exception.getMessage());
   }

   /**
    * test appendRow with a normal value
    */
   @Test
   void testAppendValidRow() {
      String dateStr = "2025-04-24 01:01:01";
      LocalDateTime dateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      viewsheetScope.appendRow("Query1", new Object[]{false, dateTime, 2, "b"});

      assertEquals(3, getXEmbeddedTable().getRowCount());
      assertEquals(4, getXEmbeddedTable().getColCount());
   }

   /**
    * test setCellValue with a normal value
    */
   @Test
   void testSetCellValueUpdatesExistingRow() {
      // when row <= rowCount, it will update the row cell
      viewsheetScope.setCellValue("Query1", 2, 0, true);
      assertEquals(true, getXEmbeddedTable().getObject(2,0));

      // when row > rowCount, it will append a new row
      viewsheetScope.setCellValue("Query1", 3, 2, 3);
      assertEquals(4, getXEmbeddedTable().getRowCount());

      viewsheetScope.refreshData();
      assertEquals(4, getXEmbeddedTable().getRowCount());
      assertEquals(4, getXEmbeddedTable().getColCount());

      //test save worksheet
     /* viewsheetScope.saveWorksheet();
      System.err.println("====" + getXEmbeddedTable().getRowCount() + ", " + getXEmbeddedTable().getColCount());
      assertEquals(4, getXEmbeddedTable().getRowCount());
      assertEquals(4, getXEmbeddedTable().getColCount());*/
   }

   /**
    * test execute script with a normal value
    */
   @Test
   void testExecuteScript() throws Exception {
      Object result = viewsheetScope.execute("fields","TableView1", false);

      if (result instanceof List<?> resultList) {
         List<String> actual = resultList.stream()
            .map(ref -> ((ColumnRef) ref).getName())
            .collect(Collectors.toList());

         List<String> expected = List.of("int", "string", "boolean", "datetime");

         assertEquals(expected, actual);
      }
      else {
         System.err.println("result not List is: " + result.getClass().getName());
         System.out.println("value: " + result);
      }
   }

   @Test
   void testExecuteThrowRuntimeException() {
      RuntimeException exception = assertThrows(RuntimeException.class, () ->
         viewsheetScope.execute("test","TableView1", true));
      assert exception.getMessage().replace("\n", " ")
         .contains("Script execution error in assembly: TableView1");
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);
      return event;
   }

   public static final String ASSET_ID = "1^128^__NULL__^ViewsheetScopeTest";
}
