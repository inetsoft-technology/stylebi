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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SreeHome(importResources = "34891-VSSelectionListControllerTest.zip")
@ExtendWith(MockitoExtension.class)
@Disabled
class VSSelectionListControllerTest {
   @BeforeEach
   void setup() {
      controller = new VSSelectionListController(
         controllers.getRuntimeViewsheetRef(),
         controllers.getVSSelectionServiceProxy());
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);
      return event;
   }

   // Bug #34891
   @Test
   @Disabled("Test failing due to NPE, needs to be fixed")
   void selectingCompositeValueWithPossibleNameCollision() throws Exception {
      when(viewsheetService.getViewsheet(viewsheetResource.getRuntimeId(), principal))
         .thenReturn(viewsheetResource.getRuntimeViewsheet());
      when(commandDispatcher.detach()).thenReturn(commandDispatcher);

      final String assemblyName = "SelectionTree1";
      final SelectionTreeVSAssembly assembly = (SelectionTreeVSAssembly) viewsheetResource
         .getRuntimeViewsheet().getViewsheet().getAssembly(assemblyName);

      final String val1 = "1", val2 = "2";

      final SelectionList selectionListBefore =
         assembly.getCompositeSelectionValue().getSelectionList();
      assertTrue(selectionListBefore.findValue(val1).isSelected());
      assertFalse(selectionListBefore.findValue(val2).isSelected());

      final ApplySelectionListEvent.Value value = new ApplySelectionListEvent.Value();
      value.setSelected(true);
      value.setValue(new String[]{ val2 });
      final ApplySelectionListEvent event = new ApplySelectionListEvent();
      event.setSelectEnd(-1);
      event.setSelectStart(-1);
      event.setType(ApplySelectionListEvent.Type.APPLY);
      event.setValues(Collections.singletonList(value));

      controller.applySelection(assemblyName, event, principal, commandDispatcher, "");

      final SelectionList selectionListAfter =
         assembly.getCompositeSelectionValue().getSelectionList();
      assertTrue(selectionListAfter.findValue(val1).isSelected());
      assertTrue(selectionListAfter.findValue(val2).isSelected());
   }

   @RegisterExtension
   @Order(1)
   ControllersExtension controllers = new ControllersExtension();
   @Mock
   ViewsheetService viewsheetService;
   @Mock
   Principal principal;
   @Mock
   CommandDispatcher commandDispatcher;
   private VSSelectionListController controller;

   @RegisterExtension
   @Order(2)
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent(), controllers);

   private static final String ASSET_ID = "1^128^__NULL__^34891";
}