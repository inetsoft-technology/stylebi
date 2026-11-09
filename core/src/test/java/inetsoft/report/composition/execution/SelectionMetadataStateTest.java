/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.report.composition.execution;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80814: in Composer, turning Use Metadata on and then off again dropped the selection
 * of a Selection List / Selection Tree. While metadata is on the selection query runs on
 * placeholder rows ({@code XXXXXXXX}), and the refresh rebuilt the persistent state selection
 * from them, so nothing was selected any more once the real data came back. A refresh in
 * metadata design mode must only update what is displayed and keep the state.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionMetadataStateTest {
   @BeforeEach
   void setUp() {
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(viewsheet);
      when(box.getMode()).thenReturn(RuntimeViewsheet.VIEWSHEET_DESIGN_MODE);
   }

   @Test
   void selectionListKeepsSelectionAcrossMetadataToggle() throws Exception {
      SelectionListVSAssembly list = createList(false);
      AbstractSelectionVSAQuery query = new SelectionListVSAQuery(box, NAME);

      refresh(query, list, realData());
      clickList(list, GOLF);
      refresh(query, list, realData());
      assertEquals(Set.of(GOLF), getSelected(list));

      viewsheet.getViewsheetInfo().setMetadata(true);
      refresh(query, list, placeholder());
      assertEquals(Set.of(GOLF), getSelected(list),
                   "the placeholder refresh must not replace the state selection list");
      assertEquals(PLACEHOLDER, list.getSelectionList().getSelectionValue(0).getValue(),
                   "the view list still shows the placeholder row");

      viewsheet.getViewsheetInfo().setMetadata(false);
      refresh(query, list, realData());
      assertEquals(Set.of(GOLF), getSelected(list));
      assertTrue(list.getSelectionList().findValue(GOLF, false).isSelected());
   }

   @Test
   void singleSelectionListKeepsItsChoiceAcrossMetadataToggle() throws Exception {
      SelectionListVSAssembly list = createList(true);
      AbstractSelectionVSAQuery query = new SelectionListVSAQuery(box, NAME);

      refresh(query, list, realData());
      clickList(list, GOLF);
      refresh(query, list, realData());
      assertEquals(Set.of(GOLF), getSelected(list));

      viewsheet.getViewsheetInfo().setMetadata(true);
      refresh(query, list, placeholder());
      assertEquals(Set.of(GOLF), getSelected(list),
                   "the placeholder row must not be auto-selected into the state");

      viewsheet.getViewsheetInfo().setMetadata(false);
      refresh(query, list, realData());
      assertEquals(Set.of(GOLF), getSelected(list),
                   "the user's choice must not be replaced by the first item");
   }

   @Test
   void selectionListKeepsSelectionWhenPlaceholderIsEmpty() throws Exception {
      SelectionListVSAssembly list = createList(false);
      AbstractSelectionVSAQuery query = new SelectionListVSAQuery(box, NAME);

      refresh(query, list, realData());
      clickList(list, GOLF);
      refresh(query, list, realData());

      viewsheet.getViewsheetInfo().setMetadata(true);
      refresh(query, list, new DefaultTableLens(new Object[][] { { COL } }));
      assertEquals(Set.of(GOLF), getSelected(list));
   }

   @Test
   void selectionListOutsideMetadataStillFollowsTheData() throws Exception {
      SelectionListVSAssembly list = createList(false);
      AbstractSelectionVSAQuery query = new SelectionListVSAQuery(box, NAME);

      refresh(query, list, realData());
      clickList(list, GOLF);
      refresh(query, list, realData());

      // metadata off: an empty result still clears the state, as before
      refresh(query, list, new DefaultTableLens(new Object[][] { { COL } }));
      assertEquals(Set.of(), getSelected(list));
   }

   @Test
   void selectionTreeKeepsSelectionAcrossMetadataToggle() throws Exception {
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(viewsheet, NAME);
      tree.setTableName(TABLE);
      tree.setDataRefs(new DataRef[] { createRef(COL) });
      viewsheet.addAssembly(tree);
      AbstractSelectionVSAQuery query = SelectionTreeVSAQuery.createVSAQuery(box, NAME);
      assertEquals(SelectionTreeVSAQuery.class, query.getClass());

      refresh(query, tree, realData());
      clickTree(tree, GOLF);
      refresh(query, tree, realData());
      assertEquals(Set.of(GOLF), getSelected(tree));

      viewsheet.getViewsheetInfo().setMetadata(true);
      refresh(query, tree, placeholder());
      assertEquals(Set.of(GOLF), getSelected(tree),
                   "the placeholder refresh must not replace the state composite value");

      viewsheet.getViewsheetInfo().setMetadata(false);
      refresh(query, tree, realData());
      assertEquals(Set.of(GOLF), getSelected(tree));
      assertTrue(tree.getSelectionList().findValue(GOLF, false).isSelected());
   }

   @Test
   void idModeSelectionTreeKeepsSelectionAcrossMetadataToggle() throws Exception {
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(viewsheet, NAME);
      tree.setTableName(TABLE);
      tree.setMode(SelectionTreeVSAssemblyInfo.ID);
      tree.setIDValue(ID_COL);
      tree.setParentIDValue(PID_COL);
      tree.setLabelValue(COL);
      tree.setDataRefs(new DataRef[] {
         createRef(ID_COL), createRef(PID_COL), createRef(COL) });
      viewsheet.addAssembly(tree);
      AbstractSelectionVSAQuery query = SelectionTreeVSAQuery.createVSAQuery(box, NAME);
      assertEquals(SelectionTreeVSAQuery2.class, query.getClass());

      refresh(query, tree, idData());
      clickTree(tree, "2");
      refresh(query, tree, idData());
      assertEquals(Set.of("2"), getSelected(tree));

      viewsheet.getViewsheetInfo().setMetadata(true);
      refresh(query, tree, new DefaultTableLens(new Object[][] {
         { ID_COL, PID_COL, COL }, { PLACEHOLDER, PLACEHOLDER, PLACEHOLDER } }));
      assertEquals(Set.of("2"), getSelected(tree),
                   "the placeholder refresh must not replace the state composite value");

      viewsheet.getViewsheetInfo().setMetadata(false);
      refresh(query, tree, idData());
      assertEquals(Set.of("2"), getSelected(tree));
   }

   private SelectionListVSAssembly createList(boolean single) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(viewsheet, NAME);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) list.getInfo();
      info.setTableName(TABLE);
      info.setDataRef(createRef(COL));
      info.setSingleSelection(single);
      viewsheet.addAssembly(list);
      return list;
   }

   private static ColumnRef createRef(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType("string");
      return ref;
   }

   /** Selects one value the way VSSelectionService does a plain click on a list. */
   private static void clickList(SelectionListVSAssembly list, String value) {
      list.setStateSelectionList(select(list.getSelectionList(), value));
   }

   /** Selects one top-level value the way VSSelectionService does a plain click on a tree. */
   private static void clickTree(SelectionTreeVSAssembly tree, String value) {
      CompositeSelectionValue cvalue = new CompositeSelectionValue();
      cvalue.setLevel(-1);
      cvalue.setSelectionList(select(tree.getSelectionList(), value));
      tree.setStateCompositeSelectionValue(cvalue);
   }

   private static SelectionList select(SelectionList view, String value) {
      assertNotNull(view);
      SelectionValue svalue = (SelectionValue) view.findValue(value, false).clone();
      svalue.setSelected(true);
      SelectionList state = new SelectionList();
      state.addSelectionValue(svalue);
      return state;
   }

   /** Runs one refresh the way ViewsheetSandbox.refreshSelectionValue feeds the query. */
   private static void refresh(AbstractSelectionVSAQuery query, SelectionVSAssembly assembly,
                               XTable data) throws Exception
   {
      Map<String, Map<String, Collection<Object>>> all = new HashMap<>();
      Map<String, Map<String, Collection<Object>>> applied = new HashMap<>();
      assembly.getSelection(all, false);
      assembly.getSelection(applied, true);
      Map<String, Set<Object>> values = new HashMap<>();
      query.refreshSelectionValue0(data, all, applied, values,
         new SelectionMeasureAggregation(
            assembly instanceof SelectionTreeVSAssembly &&
            ((SelectionTreeVSAssembly) assembly).isIDMode()));
   }

   /** The values the persistent state selects, read the way the sandbox reads them. */
   private static Set<Object> getSelected(SelectionVSAssembly assembly) {
      Map<String, Map<String, Collection<Object>>> map = new HashMap<>();
      assembly.getSelection(map, false);
      Set<Object> result = new HashSet<>();
      Map<String, Collection<Object>> table = map.getOrDefault(TABLE, Map.of());

      table.forEach((key, vals) -> {
         if(!key.startsWith(SelectionVSAssembly.SELECTION_PATH)) {
            vals.forEach(v -> result.add(v == null ? null : String.valueOf(v)));
         }
      });

      return result;
   }

   private static XTable realData() {
      return new DefaultTableLens(new Object[][] {
         { COL }, { "Camping Equipment" }, { GOLF }, { "Mountaineering Equipment" },
         { "Outdoor Protection" }, { "Personal Accessories" } });
   }

   private static XTable placeholder() {
      return new DefaultTableLens(new Object[][] { { COL }, { PLACEHOLDER } });
   }

   private static XTable idData() {
      return new DefaultTableLens(new Object[][] {
         { ID_COL, PID_COL, COL }, { "1", null, "Camping Equipment" }, { "2", null, GOLF },
         { "3", "1", "Tents" }, { "4", "2", "Irons" } });
   }

   private static final String NAME = "Selection1";
   private static final String TABLE = "Query1";
   private static final String COL = "Product Line";
   private static final String ID_COL = "id";
   private static final String PID_COL = "pid";
   private static final String GOLF = "Golf Equipment";
   private static final String PLACEHOLDER = "XXXXXXXX";

   private Viewsheet viewsheet;
   private ViewsheetSandbox box;
}
