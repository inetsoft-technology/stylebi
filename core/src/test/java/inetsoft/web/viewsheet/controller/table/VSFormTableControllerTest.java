/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.viewsheet.event.table.ChangeFormTableCellInputEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class VSFormTableControllerTest {

   @BeforeEach
   void setup() throws Exception {
      controller = new VSFormTableController(viewsheetService, runtimeViewsheetRef,
                                             placeholderService);
   }

   // Empty input is valid, set form object to data
   @Test
   void setFormObjectToEmptyInput() throws Exception {
      when(viewsheetService.getViewsheet(any(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(rvs.getViewsheetSandbox()).thenReturn(box);
      FormTableLens form = mock(FormTableLens.class);
      when(box.getFormTableLens(anyString())).thenReturn(form);
      TableVSAssembly assembly = spy(new TableVSAssembly());
      when(viewsheet.getAssembly(anyString())).thenReturn(assembly);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getInfo();
      ColumnSelection columnSelection = new ColumnSelection();
      final FormRef formRef = new FormRef();
      formRef.setDataRef(new AttributeRef());
      columnSelection.addAttribute(formRef);
      info.setColumnSelection(columnSelection);
      ChangeFormTableCellInputEvent event = new ChangeFormTableCellInputEvent.Builder()
         .row(0)
         .col(0)
         .data("")
         .assemblyName("")
         .start(0)
         .build();
      controller.changeFormInput(event, "", commandDispatcher, principal);

      verify(form, times(1)).setObject(0, 0, "");
   }

   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock PlaceholderService placeholderService;
   @Mock ViewsheetService viewsheetService;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock CommandDispatcher commandDispatcher;
   @Mock Principal principal;

   private VSFormTableController controller;
}
