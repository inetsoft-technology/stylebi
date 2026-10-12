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
package inetsoft.uql.viewsheet;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Selection list, selection tree and time slider selections saved (in a viewsheet or a
 * bookmark) on a th_TH server before Bug #77605 hold Buddhist years, which are read back as the
 * same Gregorian dates. Values of other types are kept.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SelectionStateGregorianCompatTest {
   @BeforeEach
   void enableCompat() {
      SreeEnv.setProperty(COMPAT_PROPERTY, "true");
   }

   @AfterEach
   void restore() {
      SreeEnv.remove(COMPAT_PROPERTY);
   }

   @Test
   void selectionListStateOfDateColumn() throws Exception {
      SelectionListVSAssembly legacy = selectionList(XSchema.DATE);
      legacy.setStateSelectionList(list(0, "2539-02-29", "2569-01-01 00:00:00"));

      SelectionListVSAssembly assembly = selectionList(XSchema.DATE);
      assembly.parseState(stateXML(legacy));

      assertEquals(List.of("1996-02-29", "2026-01-01 00:00:00"),
                   values(assembly.getStateSelectionList()));
      assertEquals(date(1996, 2, 29).getTime(),
                   ((Date) assembly.getSelectedObjects().get(0)).getTime());
   }

   @Test
   void selectionListStateOfStringColumnIsKept() throws Exception {
      SelectionListVSAssembly legacy = selectionList(XSchema.STRING);
      legacy.setStateSelectionList(list(0, "2539-02-29"));

      SelectionListVSAssembly assembly = selectionList(XSchema.STRING);
      assembly.parseState(stateXML(legacy));

      assertEquals(List.of("2539-02-29"), values(assembly.getStateSelectionList()));
   }

   @Test
   void selectionTreeStateOnlyChangesDateLevels() throws Exception {
      SelectionTreeVSAssembly legacy = selectionTree();
      CompositeSelectionValue parent = new CompositeSelectionValue("2539", "2539");
      parent.setLevel(0);
      parent.setSelected(true);
      parent.setSelectionList(list(1, "2539-02-29"));
      CompositeSelectionValue root = new CompositeSelectionValue();
      root.setLevel(-1);
      SelectionList rootList = new SelectionList();
      rootList.addSelectionValue(parent);
      root.setSelectionList(rootList);
      legacy.setStateCompositeSelectionValue(root);

      SelectionTreeVSAssembly assembly = selectionTree();
      assembly.parseState(stateXML(legacy));

      SelectionValue level0 =
         assembly.getStateCompositeSelectionValue().getSelectionList().getSelectionValue(0);
      SelectionValue level1 =
         ((CompositeSelectionValue) level0).getSelectionList().getSelectionValue(0);

      // level 0 is a string column, level 1 a date column
      assertEquals("2539", level0.getValue());
      assertEquals("1996-02-29", level1.getValue());
   }

   @Test
   void timeSliderStateOfDateRange() throws Exception {
      TimeSliderVSAssembly legacy = timeSlider(TimeInfo.YEAR);
      legacy.setStateSelectionList(list(0, "{y '2568'}", "{y '2569'}"));

      TimeSliderVSAssembly assembly = timeSlider(TimeInfo.YEAR);
      assembly.parseState(stateXML(legacy));

      assertEquals(List.of("{y '2025'}", "{y '2026'}"), values(assembly.getStateSelectionList()));
   }

   @Test
   void timeSliderStateOfNumberRangeIsKept() throws Exception {
      TimeSliderVSAssembly legacy = timeSlider(TimeInfo.NUMBER);
      legacy.setStateSelectionList(list(0, "2568", "2569"));

      TimeSliderVSAssembly assembly = timeSlider(TimeInfo.NUMBER);
      assembly.parseState(stateXML(legacy));

      assertEquals(List.of("2568", "2569"), values(assembly.getStateSelectionList()));
   }

   private static SelectionListVSAssembly selectionList(String type) {
      SelectionListVSAssembly assembly = new SelectionListVSAssembly(new Viewsheet(), "list");
      assembly.setDataRef(column("d", type));
      return assembly;
   }

   private static SelectionTreeVSAssembly selectionTree() {
      SelectionTreeVSAssembly assembly = new SelectionTreeVSAssembly(new Viewsheet(), "tree");
      assembly.setDataRefs(new DataRef[] { column("s", XSchema.STRING), column("d", XSchema.DATE) });
      return assembly;
   }

   private static TimeSliderVSAssembly timeSlider(int unit) {
      TimeSliderVSAssembly assembly = new TimeSliderVSAssembly(new Viewsheet(), "slider");
      SingleTimeInfo info = new SingleTimeInfo();
      info.setDataRef(column("d", unit == TimeInfo.NUMBER ? XSchema.INTEGER : XSchema.DATE));
      info.setRangeType(unit);
      assembly.setTimeInfo(info);
      return assembly;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef column = new ColumnRef(new AttributeRef(null, name));
      column.setDataType(type);
      return column;
   }

   private static SelectionList list(int level, String... values) {
      SelectionList list = new SelectionList();

      for(String value : values) {
         SelectionValue sval = new SelectionValue(value, value);
         sval.setLevel(level);
         sval.setSelected(true);
         list.addSelectionValue(sval);
      }

      return list;
   }

   private static List<String> values(SelectionList list) {
      List<String> values = new ArrayList<>();

      for(SelectionValue sval : list.getSelectionValues()) {
         values.add(sval.getValue());
      }

      return values;
   }

   private static Element stateXML(AbstractVSAssembly assembly) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      assembly.writeState(writer, false);
      writer.flush();
      return Tool.parseXML(new StringReader(buf.toString())).getDocumentElement();
   }

   private static Date date(int year, int month, int day) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, month - 1, day);
      return cal.getTime();
   }

   private static final String COMPAT_PROPERTY = "date.legacy.buddhist.compat";
}
