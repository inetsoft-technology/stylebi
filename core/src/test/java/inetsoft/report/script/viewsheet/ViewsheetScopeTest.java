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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
   private Worksheet worksheet;
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
      worksheet = sandbox.getViewsheet().getOriginalWorksheet();
      return ((EmbeddedTableAssembly) worksheet.getAssembly("Query1")).getEmbeddedData();
   }

   @Test
   void testToListWithObj() {
      Object arrObj = new Object[]{"banana", "apple", "apple", "cherry"};
      Object result = viewsheetScope.toList(arrObj, "sort=asc, distinct=true");
      assert Arrays.equals((Object[]) result, new Object[]{"apple", "banana", "cherry"});

      Object arrObj1 = new Object[]{"apple", "banana", "cherry"};
      Object result1 = viewsheetScope.toList(arrObj1, "maxrows=2, remainder=other");
      assert Arrays.equals((Object[]) result1, new Object[]{"apple", "banana", "other"});

      Object arrObj2 = new Object[]{"2022-01-01", "2022-01-02", "2022-01-03"};
      Object result2 = viewsheetScope.toList(arrObj2, "date=day");
      assert Arrays.equals((Object[]) result2, new Object[]{"2022-01-01", "2022-01-02", "2022-01-03"});
   }

   @Test
   void testToListWithXTable() {
      DefaultTableLens table = new DefaultTableLens(new Object[][]{
         {"col1", "col2", "date"},
         {"banana", "1", "2022-01-06"},
         {"apple", "2", "2022-01-04"},
         {"banana", "3", "2022-01-04"},
         {"peach", "2", "2022-01-01"},
         });
      Object result = viewsheetScope.toList(table, "field=col1, sort=asc, sorton=col1, distinct=true");
      assert Arrays.equals((Object[]) result, new Object[]{"apple", "banana", "peach"});

      // Test topN and timeSeries
      Object result2 = viewsheetScope.toList(table, "field=date,sort=desc,,sort2=desc, rounddate=day,sorton=col2,interval=1.0,timeseries=true,maxrows=2, remainder=other");
      assert Arrays.equals((Object[]) result2, new Object[]{"2022-01-01","2022-01-04", "other"});

      // Test sort by options
      Object result3 = viewsheetScope.toList(table, "field=date,sort=asc,rounddate=day,sorton2=col2,interval=1.0,timeseries=true");
      assert Arrays.equals((Object[]) result3, new Object[]{"2022-01-06","2022-01-01", "2022-01-04"});
   }

   @Test
   void testAppendRowWithTypeMismatchThrowsException() {
      RuntimeException exception = assertThrows(RuntimeException.class, () -> {
         viewsheetScope.appendRow("Query1", new Object[]{2, true, null, null});
      });
      assertEquals("Failed to append row. Check order and data type!", exception.getMessage());
   }

   @Test
   void testAppendRowWithColCountMismatchThrowsException() {
      RuntimeException exception = assertThrows(RuntimeException.class, () -> {
         viewsheetScope.appendRow("Query1", new Object[]{2, true, null});
      });
      assertEquals("Failed to append row. Check order and data type!", exception.getMessage());
   }

   @Test
   void testAppendValidRow() {
      String dateStr = "2025-04-24 01:01:01";
      LocalDateTime dateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      viewsheetScope.appendRow("Query1", new Object[]{false, dateTime, 2, "b"});

      assertEquals(3, getXEmbeddedTable().getRowCount());
      assertEquals(4, getXEmbeddedTable().getColCount());
   }

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

   @Test
   void testExecuteScript() throws Exception {
      Object result = viewsheetScope.execute("fields","TableView1", false);

      if (result instanceof List<?>) {
         List<?> resultList = (List<?>) result;
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
      RuntimeException exception = assertThrows(RuntimeException.class, () -> {
         viewsheetScope.execute("sadad","TableView1", true);
      });
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
