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
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;

import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

@SreeHome(importResources = "SelectionTreeVSAScriptableTest.vso")
public class SelectionTreeVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private SelectionTreeVSAScriptable selectionTreeVSAScriptable, selectionTreeVSAScriptable1;
   private SelectionTreeVSAssemblyInfo selectionTreeVSAssemblyInfo;
   private SelectionTreeVSAssembly selectionTreeVSAssembly, selectionTreeVSAssembly1;
   private VSAScriptable vsaScriptable;

   @Mock
   ViewsheetService viewsheetService;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      selectionTreeVSAssembly = new SelectionTreeVSAssembly();
      selectionTreeVSAssemblyInfo =
         (SelectionTreeVSAssemblyInfo) selectionTreeVSAssembly.getVSAssemblyInfo();
      selectionTreeVSAssemblyInfo.setName("SelectionTree1");
      viewsheet.addAssembly(selectionTreeVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      selectionTreeVSAScriptable = new SelectionTreeVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      selectionTreeVSAScriptable.setAssembly("SelectionTree1");
      vsaScriptable.setAssembly("SelectionTree1");
   }

   @Test
   void testGetClassName() {
      assertEquals("SelectionTreeVSA", selectionTreeVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      selectionTreeVSAScriptable.addProperties();
      String[] keys = {"dropdown", "singleSelection", "selectFirstItemOnLoad",
                       "submitOnChange", "wrapping", "suppressBlank", "expandAll"};

      for (String key : keys) {
         assert selectionTreeVSAScriptable.get(key, selectionTreeVSAScriptable) instanceof Boolean;
      }

      assertEquals("SelectionTree",
                   selectionTreeVSAScriptable.get("title", selectionTreeVSAScriptable));
      assertEquals(XConstants.SORT_SPECIFIC,
                   selectionTreeVSAScriptable.get("sortType", selectionTreeVSAScriptable));
      assertNull(selectionTreeVSAScriptable.get("value", selectionTreeVSAScriptable));
   }

   @Test
   void testGetSetCellValue(){
      selectionTreeVSAScriptable.setCellValue("value1");
      assertEquals("value1", selectionTreeVSAScriptable.getCellValue());
   }

   @Test
   void testGet() {
      assertNull(selectionTreeVSAScriptable.get("value", selectionTreeVSAScriptable));
      selectionTreeVSAScriptable.setCellValue("value1");
      assertEquals("value1",
                   selectionTreeVSAScriptable.get("value", selectionTreeVSAScriptable));
      assertEquals("SelectionTree",
                   selectionTreeVSAScriptable.get("title", selectionTreeVSAScriptable));
      assertNull(selectionTreeVSAScriptable.get("drillMember", selectionTreeVSAScriptable));
      assertNull(selectionTreeVSAScriptable.get("drillMembers", selectionTreeVSAScriptable));
   }

   @Test
   void testHas() {
      assertFalse(selectionTreeVSAScriptable.has("property1", selectionTreeVSAScriptable));
      selectionTreeVSAScriptable.setCellValue("value1");
      assertTrue(selectionTreeVSAScriptable.has("value", selectionTreeVSAScriptable));
      assertTrue(selectionTreeVSAScriptable.has("titleVisible", selectionTreeVSAScriptable));
   }

   @Test
   void testGetSetShowType() {
      selectionTreeVSAScriptable.setShowType(true);
      assertTrue(selectionTreeVSAScriptable.getShowType());
   }

   @Test
   void testSetSingleSelection() {
      assertFalse(selectionTreeVSAScriptable.isSingleSelection());
      selectionTreeVSAScriptable.setSingleSelection(true);
      assertTrue(selectionTreeVSAScriptable.isSingleSelection());
      selectionTreeVSAScriptable.setSingleSelection(false);
      assertFalse(selectionTreeVSAScriptable.isSingleSelection());
   }

   @Test
   void testGetSetFields() {
      selectionTreeVSAScriptable.setFields(new String[]{});
      assertArrayEquals(new String[] {}, selectionTreeVSAScriptable.getFields());

      selectionTreeVSAScriptable.setFields(new String[] {"field1", "field2"});
      assertArrayEquals(new String[] {"field1", "field2"}, selectionTreeVSAScriptable.getFields());

      ColumnRef columnRef = new ColumnRef();
      columnRef.setDataRef(new AttributeRef("entity", "attribute"));
      DataRef [] dataRefs = new DataRef[] {columnRef};
      selectionTreeVSAssemblyInfo.setDataRefs(dataRefs);
      selectionTreeVSAScriptable.setFields(new String[] {"attribute"});
      assertArrayEquals(new String[] {"attribute"}, selectionTreeVSAScriptable.getFields());

      ColumnRef columnRef1 = new ColumnRef();
      columnRef1.setDataRef(new AttributeRef());
      DataRef [] dataRefs1 = new DataRef[] {columnRef, columnRef1};
      selectionTreeVSAssemblyInfo.setDataRefs(dataRefs1);
      selectionTreeVSAScriptable.setFields(new String[] {"attribute", "field2"});
      assertArrayEquals(new String[] {"attribute", "field2"}, selectionTreeVSAScriptable.getFields());
   }

   /**
    * import snapshot to test set single selection levels for selection tree
    */
   @Test
   void testGetSetSingleSelectionLevels() throws Exception {
      processAssembly("SelectionTree1");

      //null levels
      selectionTreeVSAScriptable.setSingleSelectionLevels(null);
      assertNull(selectionTreeVSAScriptable.getSingleSelectionLevels());

      //one single level and multiple single levels
      selectionTreeVSAScriptable1.setSingleSelection(true);
      selectionTreeVSAScriptable1.setSingleSelectionLevels(new String[] {"STATE"});
      assertArrayEquals(new String[] {"STATE"},
                        selectionTreeVSAScriptable1.getSingleSelectionLevels());
      selectionTreeVSAScriptable1.setSingleSelectionLevels(new String[] {"STATE", "CITY"});
      assertArrayEquals(new String[] {"STATE", "CITY"},
                        selectionTreeVSAScriptable1.getSingleSelectionLevels());
   }

   /**
    * import snapshot to test set single selection for parent-child selection tree
    */
   @Test
   void testGetSetSingleSelection() throws Exception {
      processAssembly("SelectionTree2");

      assertNull(selectionTreeVSAScriptable.getSingleSelectionLevels());
      selectionTreeVSAScriptable1.setSingleSelection(true);
      assertArrayEquals(new String[] {"parent_id", "child_id", "label_string"},
                        selectionTreeVSAScriptable1.getSingleSelectionLevels());
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);

      return event;
   }

   /**
    * Processes the specified assembly by retrieving it from the runtime viewsheet
    * and initializing the `SelectionTreeVSAScriptable` instance with the assembly name.
    *
    * @param assemblyName the name of the assembly to process
    * @throws Exception if an error occurs during the processing of the assembly
    */
   private void processAssembly(String assemblyName) throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox();
      Principal principal = mock(Principal.class);
      when(viewsheetService.getViewsheet(viewsheetResource.getRuntimeId(), principal))
         .thenReturn(viewsheetResource.getRuntimeViewsheet());

      selectionTreeVSAssembly1 = (SelectionTreeVSAssembly) viewsheetResource
         .getRuntimeViewsheet().getViewsheet().getAssembly(assemblyName);
      selectionTreeVSAScriptable1 = new SelectionTreeVSAScriptable(sandbox);
      selectionTreeVSAScriptable1.setAssembly(selectionTreeVSAssembly1.getName());
   }

   public static final String ASSET_ID = "1^128^__NULL__^SelectionTreeVSAScriptableTest";

   @RegisterExtension
   @Order(1)
   ControllersExtension controllers = new ControllersExtension();

   @RegisterExtension
   @Order(2)
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent(), controllers);
}