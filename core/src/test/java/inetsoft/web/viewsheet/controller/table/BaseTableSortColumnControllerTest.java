/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.binding.DimensionRef;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.event.table.SortColumnEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BaseTableSortColumnControllerTest {

   @BeforeEach
   void setup() throws Exception {
      controller = new BaseTableSortColumnController(
         runtimeViewsheetRef, placeholderService, viewsheetService, bindingFactory);
   }

   // Bug #17147 Create new sort info when sort event multi flag is false
   @Test
   void removePreviousSortedColumnWhenMultiIsFalse() throws Exception {
      when(viewsheetService.getViewsheet(any(), nullable(Principal.class)))
         .thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(box);
      TableVSAssembly assembly = spy(new TableVSAssembly());
      when(viewsheet.getAssembly(anyString())).thenReturn(assembly);
      when(viewsheet.getRuntimeEntry()).thenReturn(new AssetEntry());
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getInfo();
      ColumnSelection columnSelection = new ColumnSelection();
      DimensionRef dimensionRef = new DimensionRef();
      dimensionRef.setCube("foo1");
      ColumnRef columnRef = new ColumnRef(dimensionRef);
      ColumnRef ref = new ColumnRef(columnRef);
      columnSelection.addAttribute(ref);
      SortInfo sortInfo = new SortInfo();
      // add sort refs to tables sort info
      dimensionRef = (DimensionRef) dimensionRef.clone();
      dimensionRef.setCube("foo2");
      sortInfo.addSort(new SortRef(new ColumnRef(dimensionRef)));
      dimensionRef = (DimensionRef) dimensionRef.clone();
      dimensionRef.setCube("foo3");
      sortInfo.addSort(new SortRef(new ColumnRef(dimensionRef)));
      assembly.setSortInfo(sortInfo);
      info.setColumnSelection(columnSelection);

      SortColumnEvent event = mock(SortColumnEvent.class);
      when(event.getCol()).thenReturn(0);
      when(event.getAssemblyName()).thenReturn("");
      when(event.multi()).thenReturn(false);

      // Initial sort info has 2 sort refs
      assertEquals(2, assembly.getSortInfo().getSortCount());
      controller.eventHandler(event, principal, commandDispatcher, "");

      assertNotEquals(sortInfo, assembly.getSortInfo());
      // should only have one sort ref
      assertEquals(1, assembly.getSortInfo().getSortCount());
   }

   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock PlaceholderService placeholderService;
   @Mock ViewsheetService viewsheetService;
   @Mock VSBindingService bindingFactory;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock CommandDispatcher commandDispatcher;
   @Mock Principal principal;
   @Mock ViewsheetSandbox box;

   private BaseTableSortColumnController controller;
}
