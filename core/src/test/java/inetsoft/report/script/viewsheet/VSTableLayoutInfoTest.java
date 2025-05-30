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

import inetsoft.report.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SreeHome(importResources = "VSTableLayoutInfoTest.vso")
public class VSTableLayoutInfoTest {
   private VSTableLayoutInfo vsTableLayoutInfo;
   private CalcTableVSAScriptable calcTableVSAScriptable;

   private ViewsheetSandbox viewsheetSandbox;
   private CalcTableVSAssembly calcTableVSAssembly;
   @Mock
   private ViewsheetService viewsheetService = mock(ViewsheetService.class);

   @BeforeEach
   void setUp() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      viewsheetSandbox = rvs.getViewsheetSandbox();
      Principal principal = mock(Principal.class);
      when(viewsheetService.getViewsheet(viewsheetResource.getRuntimeId(), principal))
         .thenReturn(viewsheetResource.getRuntimeViewsheet());

      calcTableVSAssembly = (CalcTableVSAssembly) viewsheetResource
         .getRuntimeViewsheet().getViewsheet().getAssembly("FreehandTable1");

      calcTableVSAScriptable = new CalcTableVSAScriptable(viewsheetSandbox);
      calcTableVSAScriptable.setAssembly(calcTableVSAssembly.getName());

      vsTableLayoutInfo = new VSTableLayoutInfo(calcTableVSAScriptable);
   }

   /**
    * test setCellBinding,the type should be:
    * CellBinding.BIND_TEXT,CellBinding.BIND_COLUMN,CellBinding.BIND_FORMULA
    */
   @Test
   void testAllSetFunctions() {
      vsTableLayoutInfo.setCellBinding(0, 0, CellBinding.BIND_TEXT, "value1");
      assertEquals("value1", getCellInfo(0,0).getValue());

      vsTableLayoutInfo.setCellBinding(0, 1, "column", "Employee");
      vsTableLayoutInfo.setCellName(0,1, "CellName_Employee");
      vsTableLayoutInfo.setExpansion(0, 1, TableCellBinding.EXPAND_V);

      vsTableLayoutInfo.setSpan(0, 1, 2, 1);

      vsTableLayoutInfo.setColGroup(0, 1, "Group1");
      vsTableLayoutInfo.setRowGroup(0, 1, "Group2");

      vsTableLayoutInfo.setMergeCells(0,1,false);
      vsTableLayoutInfo.setMergeColGroup(0,1,"Group1");
      vsTableLayoutInfo.setMergeRowGroup(0,1,"Group2");

      assertEquals("Employee", getCellInfo(0,1).getValue());
      assertEquals(TableCellBinding.EXPAND_V, getCellInfo(0,1).getExpansion());
   }

   @Test
   void testSetExpansion() {
      vsTableLayoutInfo.setCellBinding(1, 0,"formula", "toList(data['Employee'])");
      vsTableLayoutInfo.setExpansion(1, 0, "H");
      vsTableLayoutInfo.setSpan(1, 0, 1, 2);
      assertEquals("toList(data['Employee'])", getCellInfo(1,0).getValue());
      assertEquals(TableCellBinding.EXPAND_H, getCellInfo(1,0).getExpansion());
   }

   private CellBindingInfo getCellInfo(int row, int col) {
      return vsTableLayoutInfo.getTableLayout(false).getCellInfo(row,col);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);

      return event;
   }

   public static final String ASSET_ID = "1^128^__NULL__^VSTableLayoutInfoTest";

   @RegisterExtension
   @Order(1)
   ControllersExtension controllers = new ControllersExtension();

   @RegisterExtension
   @Order(2)
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent(), controllers);
}

