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
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ExtendedDateFormat;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A viewsheet and a bookmark saved on a th_TH server before Bug #77605 hold Buddhist years in
 * the selections of date selection lists, selection trees and time sliders. They are read as
 * the same Gregorian dates when the date.legacy.buddhist.compat gate is on (true, or auto with
 * a Buddhist default calendar or a Thai locale in locale.available), and are kept when it is
 * off. String lists, id mode trees and composite time sliders are never changed. Unlike
 * SelectionStateGregorianCompatTest, the legacy XML goes through Viewsheet.parseState (the
 * bookmark path) and Viewsheet.parseXML (the design-time lists, including the compressed
 * TimeSliderSelection of a time slider).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SelectionLegacyBuddhistViewsheetTest {
   @AfterEach
   void restore() {
      Locale.setDefault(Locale.US);
      Locale.setDefault(Locale.Category.FORMAT, Locale.US);
      SreeEnv.remove("date.legacy.buddhist.compat");
      SreeEnv.remove("locale.available");
   }

   // name, default locale, compat property ("-" = unset), locale.available ("-" = unset), on
   @ParameterizedTest
   @CsvSource({
      "true-en,  en-US, true,  -,           true",
      "auto-th,  th-TH, auto,  -,           true",
      "unset-th, th-TH, -,     -,           true",
      "auto-avl, en-US, auto,  en_US:th_TH, true",
      "false-th, th-TH, false, -,           false",
      "false-avl,en-US, false, en_US:th_TH, false",
      "unset-en, en-US, -,     -,           false",
   })
   void legacyBookmarkState(String name, String tag, String prop, String avail, boolean on) throws Exception {
      Viewsheet vs = build(true);
      StringWriter buf = new StringWriter();
      PrintWriter w = new PrintWriter(buf);
      vs.writeState(w, false);
      w.flush();
      String legacy = toBuddhist(buf.toString());
      assertTrue(legacy.contains(enc("2539-02-29")) && legacy.contains(enc("2567-02-29")), legacy);

      Locale.setDefault(Locale.forLanguageTag(tag));
      if(!"-".equals(prop)) {
         SreeEnv.setProperty("date.legacy.buddhist.compat", prop);
      }

      if(!"-".equals(avail)) {
         SreeEnv.setProperty("locale.available", avail);
      }

      Viewsheet vs2 = build(false);
      vs2.parseState(Tool.parseXML(new StringReader(legacy)).getDocumentElement(), true);

      SelectionListVSAssembly l = (SelectionListVSAssembly) vs2.getAssembly("L");
      SelectionListVSAssembly s = (SelectionListVSAssembly) vs2.getAssembly("S");
      SelectionTreeVSAssembly t = (SelectionTreeVSAssembly) vs2.getAssembly("T");
      SelectionTreeVSAssembly id = (SelectionTreeVSAssembly) vs2.getAssembly("I");
      TimeSliderVSAssembly d = (TimeSliderVSAssembly) vs2.getAssembly("D");
      TimeSliderVSAssembly c = (TimeSliderVSAssembly) vs2.getAssembly("C");

      String y1 = on ? "1996" : "2539", y2 = on ? "2024" : "2567";
      assertEquals(List.of(y1 + "-02-29", y2 + "-02-29"), values(l.getStateSelectionList()), name);

      if(on) {
         List<Object> sel = l.getSelectedObjects();
         assertEquals(date(1996, 2, 29), ((Date) sel.get(0)).getTime(), name);
         assertEquals(date(2024, 2, 29), ((Date) sel.get(1)).getTime(), name);
      }

      assertEquals(List.of("2569-06-15"), values(s.getStateSelectionList()), name);

      SelectionValue t0 = t.getStateCompositeSelectionValue().getSelectionList().getSelectionValue(0);
      SelectionValue t1 = ((CompositeSelectionValue) t0).getSelectionList().getSelectionValue(0);
      assertEquals("x", t0.getValue(), name);
      assertEquals(y1 + "-02-29", t1.getValue(), name);

      SelectionValue i0 = id.getStateCompositeSelectionValue().getSelectionList().getSelectionValue(0);
      assertEquals("2569-06-15", i0.getValue(), name);

      List<String> dv = values(d.getStateSelectionList());
      assertEquals(List.of(dayFmt(y1, "02-029"), dayFmt(y1, "03-001")), dv, name);

      if(on) {
         assertEquals(date(1996, 2, 29), Tool.dayFmt.get().parse(dv.get(0)).getTime(), name);
      }

      assertEquals(List.of("{d '2569-06-15'}"), values(c.getStateSelectionList()), name);
   }

   @ParameterizedTest
   @CsvSource({ "true, true", "false, false" })
   void legacyViewsheetDesign(String prop, boolean on) throws Exception {
      Viewsheet vs = build(true);
      String xml = toBuddhist(toXML(vs));
      Viewsheet gregorian = new Viewsheet();
      gregorian.parseXML(Tool.parseXML(new StringReader(toXML(vs))).getDocumentElement());
      SreeEnv.setProperty("date.legacy.buddhist.compat", prop);
      Viewsheet vs2 = new Viewsheet();
      vs2.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      String y1 = on ? "1996" : "2539";

      SelectionListVSAssemblyInfo linfo = (SelectionListVSAssemblyInfo) vs2.getAssembly("L").getInfo();
      assertEquals(y1 + "-02-29", linfo.getSelectionList().getSelectionValue(0).getValue());
      SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo) vs2.getAssembly("S").getInfo();
      assertEquals("2569-06-15", sinfo.getSelectionList().getSelectionValue(0).getValue());

      SelectionTreeVSAssemblyInfo tinfo = (SelectionTreeVSAssemblyInfo) vs2.getAssembly("T").getInfo();
      SelectionValue t0 = tinfo.getCompositeSelectionValue().getSelectionList().getSelectionValue(0);
      assertEquals(y1 + "-02-29",
         ((CompositeSelectionValue) t0).getSelectionList().getSelectionValue(0).getValue());
      SelectionTreeVSAssemblyInfo iinfo = (SelectionTreeVSAssemblyInfo) vs2.getAssembly("I").getInfo();
      assertEquals("2569-06-15",
         iinfo.getCompositeSelectionValue().getSelectionList().getSelectionValue(0).getValue());

      // compressed slider selection (TimeSliderSelection start/end/first/last)
      TimeSliderVSAssemblyInfo dinfo = (TimeSliderVSAssemblyInfo) vs2.getAssembly("D").getInfo();
      assertTrue(xml.contains("TimeSliderSelection"), xml);
      List<String> selected = new ArrayList<>();
      List<String> all = new ArrayList<>();

      for(SelectionValue v : dinfo.getSelectionList().getSelectionValues()) {
         all.add(v.getValue());

         if(v.isSelected()) {
            selected.add(v.getValue());
         }
      }

      if(on) {
         // the same list as a viewsheet saved with Gregorian years
         assertEquals(sliderValues(gregorian, false), all);
         assertEquals(sliderValues(gregorian, true), selected);
         assertTrue(all.contains(dayFmt("1996", "02-029")), all.toString());
      }
      else {
         // nothing rewritten: the year stays 2539 (read as CE 2539)
         assertFalse(all.isEmpty());
         assertTrue(all.stream().allMatch(v -> v.startsWith("{d '2539-")), all.toString());
      }

      TimeSliderVSAssemblyInfo cinfo = (TimeSliderVSAssemblyInfo) vs2.getAssembly("C").getInfo();
      assertEquals("{d '2569-06-15'}", cinfo.getSelectionList().getSelectionValue(0).getValue());
   }

   private static List<String> sliderValues(Viewsheet vs, boolean selectedOnly) {
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) vs.getAssembly("D").getInfo();
      List<String> values = new ArrayList<>();

      for(SelectionValue v : info.getSelectionList().getSelectionValues()) {
         if(!selectedOnly || v.isSelected()) {
            values.add(v.getValue());
         }
      }

      return values;
   }

   private static String dayFmt(String year, String rest) {
      return "{d '" + year + "-" + rest + "'}";
   }

   private static String toBuddhist(String xml) {
      return xml.replace("1996-", "2539-").replace("2024-", "2567-")
         .replace(enc("1996-"), enc("2539-")).replace(enc("2024-"), enc("2567-"));
   }

   // selection values are written byte encoded, one ~_xx_~ per character
   private static String enc(String s) {
      StringBuilder buf = new StringBuilder();

      for(char ch : s.toCharArray()) {
         buf.append(String.format("~_%02x_~", (int) ch));
      }

      return buf.toString();
   }

   private static String toXML(Viewsheet vs) {
      StringWriter buf = new StringWriter();
      PrintWriter w = new PrintWriter(buf);
      vs.writeXML(w);
      w.flush();
      return buf.toString();
   }

   private static Viewsheet build(boolean withState) throws Exception {
      Viewsheet vs = new Viewsheet();

      SelectionListVSAssembly l = new SelectionListVSAssembly(vs, "L");
      l.setDataRef(column("d", XSchema.DATE));
      SelectionListVSAssembly s = new SelectionListVSAssembly(vs, "S");
      s.setDataRef(column("s", XSchema.STRING));
      SelectionTreeVSAssembly t = new SelectionTreeVSAssembly(vs, "T");
      t.setDataRefs(new DataRef[] { column("s", XSchema.STRING), column("d", XSchema.DATE) });
      SelectionTreeVSAssembly id = new SelectionTreeVSAssembly(vs, "I");
      id.setDataRefs(new DataRef[] { column("d", XSchema.DATE), column("d2", XSchema.DATE) });
      id.setMode(SelectionTreeVSAssemblyInfo.ID);
      TimeSliderVSAssembly d = new TimeSliderVSAssembly(vs, "D");
      SingleTimeInfo dinfo = new SingleTimeInfo();
      dinfo.setDataRef(column("d", XSchema.DATE));
      dinfo.setRangeType(TimeInfo.DAY);
      d.setTimeInfo(dinfo);
      TimeSliderVSAssembly c = new TimeSliderVSAssembly(vs, "C");
      CompositeTimeInfo cti = new CompositeTimeInfo();
      cti.setDataRefs(new DataRef[] { column("d", XSchema.DATE), column("d2", XSchema.DATE) });
      c.setTimeInfo(cti);
      ((TimeSliderVSAssemblyInfo) c.getInfo()).setComposite(true);

      if(withState) {
         SelectionList ll = list(0, "1996-02-29", "2024-02-29");
         l.setStateSelectionList(ll);
         ((SelectionListVSAssemblyInfo) l.getInfo()).setSelectionList(list(0, "1996-02-29"));
         s.setStateSelectionList(list(0, "2569-06-15"));
         ((SelectionListVSAssemblyInfo) s.getInfo()).setSelectionList(list(0, "2569-06-15"));

         t.setStateCompositeSelectionValue(tree("x", "1996-02-29"));
         ((SelectionTreeVSAssemblyInfo) t.getInfo()).setCompositeSelectionValue(tree("x", "1996-02-29"));
         CompositeSelectionValue iroot = new CompositeSelectionValue();
         iroot.setLevel(-1);
         iroot.setSelectionList(list(0, "2569-06-15"));
         id.setStateCompositeSelectionValue(iroot);
         CompositeSelectionValue iroot2 = new CompositeSelectionValue();
         iroot2.setLevel(-1);
         iroot2.setSelectionList(list(0, "2569-06-15"));
         ((SelectionTreeVSAssemblyInfo) id.getInfo()).setCompositeSelectionValue(iroot2);

         d.setStateSelectionList(list(0, dayFmt("1996", "02-029"), dayFmt("1996", "03-001")));
         // design-time slider list 02-27..03-02 with 02-28..03-01 selected, written compressed
         SelectionList dl = new SelectionList();
         String[] days = { "02-027", "02-028", "02-029", "03-001", "03-002" };

         for(int i = 0; i < days.length; i++) {
            SelectionValue v = new SelectionValue(days[i], dayFmt("1996", days[i]));
            v.setLevel(0);
            v.setSelected(i >= 1 && i <= 3);
            dl.addSelectionValue(v);
         }

         TimeSliderVSAssemblyInfo dvi = (TimeSliderVSAssemblyInfo) d.getInfo();
         dvi.setSelectionList(dl);
         TimeSliderSelection tss = new TimeSliderSelection();
         tss.setLabelFormat(new ExtendedDateFormat("yyyy-MM-dd"));
         tss.setValueFormat(new ExtendedDateFormat("{'d' ''yyyy-MM-ddd''}"));
         tss.setIncrement(1);
         tss.setDateLevels(new int[] { Calendar.DATE, Calendar.MONTH, Calendar.YEAR });
         dvi.setTimeSliderSelection(tss);

         c.setStateSelectionList(list(0, "{d '2569-06-15'}"));
         ((TimeSliderVSAssemblyInfo) c.getInfo()).setSelectionList(list(0, "{d '2569-06-15'}"));
      }

      for(VSAssembly a : new VSAssembly[] { l, s, t, id, d, c }) {
         vs.addAssembly(a);
      }

      return vs;
   }

   private static CompositeSelectionValue tree(String v0, String v1) {
      CompositeSelectionValue parent = new CompositeSelectionValue(v0, v0);
      parent.setLevel(0);
      parent.setSelected(true);
      parent.setSelectionList(list(1, v1));
      CompositeSelectionValue root = new CompositeSelectionValue();
      root.setLevel(-1);
      SelectionList rootList = new SelectionList();
      rootList.addSelectionValue(parent);
      root.setSelectionList(rootList);
      return root;
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

   private static long date(int year, int month, int day) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, month - 1, day);
      return cal.getTimeInMillis();
   }
}
