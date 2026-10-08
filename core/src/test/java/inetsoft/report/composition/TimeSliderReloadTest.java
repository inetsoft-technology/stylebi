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

package inetsoft.report.composition;

import inetsoft.report.composition.execution.TimeSliderVSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderSelection;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.service.VSSelectionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77998: a saved range slider must reload with the value list the query built, or the
 * next query keeps another range (wrong filter, no error).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TimeSliderReloadTest {
   /**
    * A number slider with a format switched to log scale after a linear query keeps its
    * selection [8..32] and its condition after save and reopen.
    */
   @Test
   void logScaleReloadKeepsSelectionAndCondition() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createNumberSlider(vs);
      Object[] data = { 1, 1000 };
      query(vs, data);
      ((TimeSliderVSAssemblyInfo) ts.getInfo()).setLogScaleValue(true);
      query(vs, data);
      assertEquals("8", ts.getSelectionList().getSelectionValue(3).getValue());
      select(ts, 3, 5);
      query(vs, data);

      Viewsheet loaded = RuntimeSheet.loadXml(new Viewsheet(), xml(vs));
      query(loaded, data);

      assertEquals(List.of("8", "16", "32"), state(ts));
      assertSameSlider(ts, slider(loaded));
   }

   /**
    * A decimal-step slider with the last 3 ticks selected still rolls with the data after
    * save and reopen.
    */
   @Test
   void decimalLastNRollsAfterReopen() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createNumberSlider(vs);
      query(vs, new Object[] { 0, 1 });
      int n = ts.getSelectionList().getSelectionValueCount();
      assertEquals("1", ts.getSelectionList().getSelectionValue(n - 1).getValue());
      select(ts, n - 3, n - 1);
      query(vs, new Object[] { 0, 1 });

      Viewsheet loaded = RuntimeSheet.loadXml(new Viewsheet(), xml(vs));
      assertEquals(n, slider(loaded).getSelectionList().getSelectionValueCount(),
                   "the reloaded list keeps the last tick");

      // the data grows
      Object[] grown = { 0, 1.2 };
      query(vs, grown);
      query(loaded, grown);

      List<String> live = state(ts);
      assertEquals("1.2", live.get(live.size() - 1), "the live window rolls");
      assertSameSlider(ts, slider(loaded));
   }

   /**
    * A single-value number slider (min == max) with a format reloads in bounded time.
    */
   @Test
   void singleValueNumberSliderReloads() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createNumberSlider(vs);
      Object[] data = { 5, 5 };
      query(vs, data);
      String saved = xml(vs);

      Viewsheet loaded = assertTimeoutPreemptively(
         Duration.ofSeconds(2), () -> RuntimeSheet.loadXml(new Viewsheet(), saved));
      assertNotNull(loaded);
      assertSameSlider(ts, slider(loaded));

      query(vs, data);
      query(loaded, data);
      assertSameSlider(ts, slider(loaded));
   }

   /**
    * A range written before #77998 with a zero increment (min == max) is not rebuilt, and the
    * read ends.
    */
   @Test
   void oldZeroIncrementRangeIsNotRebuilt() {
      SelectionList written = numbers(new String[] { "5" }, 0, 0);
      String tss = writeTss(written, 0);
      SelectionList parsed = new SelectionList();

      assertTimeoutPreemptively(Duration.ofSeconds(2), () -> parseTss(tss, parsed));
      assertEquals(0, parsed.getSelectionValueCount(), "the next query rebuilds the values");
   }

   /**
    * A date slider with a partial selection reads back the exact selected range.
    */
   @Test
   void partialDateSelectionReadsBackExactly() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createDateSlider(vs);
      query(vs, new Object[] { date(2023, 1), date(2023, 9) });
      select(ts, 3, 4);
      SelectionList vlist = ts.getSelectionList();
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) ts.getInfo();
      String infoXml = xml(info);
      assertTrue(infoXml.contains("<TimeSliderSelection"), "a date range is written compactly");

      TimeSliderVSAssemblyInfo read = new TimeSliderVSAssemblyInfo();
      read.parseXML(Tool.parseXML(new StringReader(infoXml)).getDocumentElement());
      SelectionList rlist = read.getSelectionList();

      assertEquals(values(vlist), values(rlist));
      assertEquals(List.of(3, 4), selected(rlist));
   }

   /**
    * A number range written before #77998 (decimal step, last ticks selected) is rebuilt with
    * every tick and only the selected range.
    */
   @Test
   void oldDecimalNumberRangeRebuildsEveryTick() throws Exception {
      String[] vals = new String[51];

      for(int i = 0; i < vals.length; i++) {
         vals[i] = Tool.toString(new java.math.BigDecimal("0.02")
            .multiply(java.math.BigDecimal.valueOf(i)).doubleValue());
      }

      SelectionList written = numbers(vals, 48, 50);
      SelectionList parsed = new SelectionList();
      parseTss(writeTss(written, 0.02), parsed);

      assertEquals(values(written), values(parsed));
      assertEquals(List.of(48, 49, 50), selected(parsed));
   }

   /**
    * A log scale slider saved before #77998 (with the stale linear range) reopens with its
    * selection and condition.
    */
   @Test
   void oldLogScaleSaveReloadsSelectionAndCondition() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createNumberSlider(vs);
      Object[] data = { 1, 1000 };
      query(vs, data);
      double linearIncrement = tss(ts).getIncrement();
      ((TimeSliderVSAssemblyInfo) ts.getInfo()).setLogScaleValue(true);
      query(vs, data);
      select(ts, 3, 5);
      query(vs, data);

      Viewsheet loaded = RuntimeSheet.loadXml(new Viewsheet(), oldFormat(vs, ts, linearIncrement));
      query(loaded, data);

      assertEquals(List.of("8", "16", "32"), state(ts));
      assertSameSlider(ts, slider(loaded));
   }

   /**
    * A linear number slider saved before #77998 (compact range) reopens with its selection
    * and condition.
    */
   @Test
   void oldLinearSaveReloadsSelectionAndCondition() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createNumberSlider(vs);
      Object[] data = { 0, 1 };
      query(vs, data);
      select(ts, 10, 12);
      query(vs, data);

      Viewsheet loaded = RuntimeSheet.loadXml(
         new Viewsheet(), oldFormat(vs, ts, tss(ts).getIncrement()));
      assertEquals(values(ts.getSelectionList()), values(slider(loaded).getSelectionList()));
      assertEquals(List.of(10, 11, 12), selected(slider(loaded).getSelectionList()));

      query(loaded, data);
      assertSameSlider(ts, slider(loaded));
   }

   /**
    * An undo checkpoint of a decimal-step slider with the last 3 ticks selected, read back
    * when the checkpoints are rebuilt from the runtime cache, keeps the last-N window when the
    * data shrinks.
    */
   @Test
   void checkpointRebuildKeepsDecimalLastN() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createNumberSlider(vs);
      Object[] data = { 0, 1 };
      query(vs, data);
      int n = ts.getSelectionList().getSelectionValueCount();
      select(ts, n - 3, n - 1);
      query(vs, data);

      RuntimeSheet.XSwappableSheetList points = new RuntimeSheet.XSwappableSheetList(null);
      points.add(vs.prepareCheckpoint());
      String[] point = points.getXmlForState(0);
      RuntimeSheetState state = new RuntimeSheetState();
      state.setPoints(List.of(point[0], point[1]));
      String sheetXml = xml(vs);
      RuntimeSheet.encodePointDeltas(state, sheetXml);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class, CALLS_REAL_METHODS);
      rvs.decodePointDeltas(state.getPointDeltas(), sheetXml,
                            x -> RuntimeSheet.loadXml(new Viewsheet(), x));
      assertEquals(1, rvs.points.size());
      Viewsheet restored = (Viewsheet) rvs.points.get(0);
      assertSameSlider(ts, slider(restored));

      // the data shrinks
      Object[] shrunk = { 0, 0.8 };
      query(vs, shrunk);
      query(restored, shrunk);

      assertEquals(List.of("0.76", "0.78", "0.8"), state(ts));
      assertSameSlider(ts, slider(restored));
   }

   /**
    * Going to a bookmark of a log scale slider with a format, over the saved sheet, keeps the
    * bookmarked range on the next query.
    */
   @Test
   void bookmarkOfLogScaleKeepsRange() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createNumberSlider(vs);
      Object[] data = { 1, 1000 };
      query(vs, data);
      ((TimeSliderVSAssemblyInfo) ts.getInfo()).setLogScaleValue(true);
      query(vs, data);
      select(ts, 0, 1);
      query(vs, data);
      String saved = xml(vs);

      select(ts, 3, 5);
      query(vs, data);
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      vs.writeState(writer, true);
      writer.flush();

      Viewsheet opened = RuntimeSheet.loadXml(new Viewsheet(), saved);
      opened.parseState(Tool.parseXML(new StringReader(buf.toString())).getDocumentElement());
      query(opened, data);

      assertEquals(List.of("8", "16", "32"), state(ts));
      assertSameSlider(ts, slider(opened));
   }

   /**
    * A 200-value number slider with a format is written in full, about the size of the same
    * slider without a format (which was always written in full).
    */
   @Test
   void fullNumberListSize() throws Exception {
      String formatted = saved200(true);
      String plain = saved200(false);

      assertFalse(formatted.contains("<TimeSliderSelection"));
      assertTrue(formatted.length() < 2 * plain.length() && formatted.length() < 200_000,
                 "sheet xml length " + formatted.length() + ", without a format " + plain.length());
   }

   private static String saved200(boolean format) throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createNumberSlider(vs);

      if(!format) {
         ts.getVSAssemblyInfo().getFormat().getUserDefinedFormat().setFormatValue(null);
         ts.getVSAssemblyInfo().getFormat().getUserDefinedFormat().setFormatExtentValue(null);
      }

      ((SingleTimeInfo) ts.getTimeInfo()).setRangeSizeValue(1);
      query(vs, new Object[] { 0, 199 });
      assertEquals(200, ts.getSelectionList().getSelectionValueCount());
      return xml(vs);
   }

   private static TimeSliderVSAssembly createNumberSlider(Viewsheet vs) {
      TimeSliderVSAssembly ts = new TimeSliderVSAssembly(vs, SLIDER);
      SingleTimeInfo tinfo = new SingleTimeInfo();
      ColumnRef ref = new ColumnRef(new AttributeRef("Order", "Quantity"));
      ref.setDataType(XSchema.DOUBLE);
      tinfo.setDataRef(ref);
      tinfo.setRangeTypeValue(TimeInfo.NUMBER);
      ts.setTimeInfo(tinfo);
      ts.setUpperInclusiveValue(true);
      VSCompositeFormat fmt = ts.getVSAssemblyInfo().getFormat();
      fmt.getUserDefinedFormat().setFormatValue("DecimalFormat");
      fmt.getUserDefinedFormat().setFormatExtentValue("#,##0.00");
      vs.addAssembly(ts);
      return ts;
   }

   private static TimeSliderVSAssembly createDateSlider(Viewsheet vs) {
      TimeSliderVSAssembly ts = new TimeSliderVSAssembly(vs, SLIDER);
      SingleTimeInfo tinfo = new SingleTimeInfo();
      ColumnRef ref = new ColumnRef(new AttributeRef("Order", "Date"));
      ref.setDataType(XSchema.DATE);
      tinfo.setDataRef(ref);
      tinfo.setRangeTypeValue(TimeInfo.MONTH);
      ts.setTimeInfo(tinfo);
      vs.addAssembly(ts);
      return ts;
   }

   private static Date date(int year, int month) {
      return new GregorianCalendar(year, month - 1, 1).getTime();
   }

   /** the real query refresh (viewer mode) with the min/max of the bound column */
   private static void query(Viewsheet vs, Object[] minMax) throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getMode()).thenReturn(RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE);
      new TimeSliderVSAQuery(box, SLIDER).refreshSelectionValue(minMax);
   }

   /** the user's handle move: the selection service's own slider update */
   private static void select(TimeSliderVSAssembly ts, int from, int to) throws Exception {
      SelectionList slist = ts.getSelectionList();

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         slist.getSelectionValue(i).setSelected(i >= from && i <= to);
      }

      Method apply = VSSelectionService.class.getDeclaredMethod(
         "applySelection", SelectionVSAssembly.class, SelectionList.class, boolean.class);
      apply.setAccessible(true);
      apply.invoke(mock(VSSelectionService.class), ts, slist, true);
   }

   /**
    * The sheet as written before #77998: the slider's value list as a compact range (with the
    * given increment) and no values.
    */
   private static String oldFormat(Viewsheet vs, TimeSliderVSAssembly ts, double increment) {
      String saved = xml(vs);

      // written by the code before #77998
      if(saved.contains("<TimeSliderSelection")) {
         return saved;
      }

      SelectionList vlist = ts.getSelectionList();
      String full = xml(vlist);
      int at = saved.indexOf(full);
      assertTrue(at >= 0 && saved.indexOf(full, at + 1) < 0, "one value list in the sheet");

      SelectionList empty = (SelectionList) vlist.clone();
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      empty.writeXML(writer, false);
      writer.flush();

      return saved.substring(0, at) + writeTss(vlist, increment) + buf +
         saved.substring(at + full.length());
   }

   private static String writeTss(SelectionList list, double increment) {
      TimeSliderSelection tss = new TimeSliderSelection();
      tss.setLabelFormat(new DecimalFormat("#,##0.00"));
      tss.setIncrement(increment);
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      tss.writeXML(writer, list);
      writer.flush();
      return buf.toString();
   }

   private static void parseTss(String xml, SelectionList list) throws Exception {
      new TimeSliderSelection().parseXML(
         Tool.parseXML(new StringReader(xml)).getDocumentElement(), list);
   }

   private static SelectionList numbers(String[] vals, int from, int to) {
      SelectionList list = new SelectionList();

      for(int i = 0; i < vals.length; i++) {
         SelectionValue value = new SelectionValue(vals[i], vals[i]);
         value.setSelected(i >= from && i <= to);
         list.addSelectionValue(value);
      }

      return list;
   }

   private static void assertSameSlider(TimeSliderVSAssembly live, TimeSliderVSAssembly loaded) {
      assertEquals(values(live.getSelectionList()), values(loaded.getSelectionList()));
      assertEquals(selected(live.getSelectionList()), selected(loaded.getSelectionList()));
      assertEquals(state(live), state(loaded));
      assertEquals(condition(live), condition(loaded));
   }

   private static String condition(TimeSliderVSAssembly ts) {
      ConditionList conds = ts.getConditionList();
      return conds == null ? null : conds.toString();
   }

   private static List<String> state(TimeSliderVSAssembly ts) {
      SelectionList state = ts.getStateSelectionList();
      return state == null ? List.of() : values(state);
   }

   private static List<String> values(SelectionList list) {
      List<String> vals = new ArrayList<>();

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         vals.add(list.getSelectionValue(i).getValue());
      }

      return vals;
   }

   private static List<Integer> selected(SelectionList list) {
      List<Integer> idx = new ArrayList<>();

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         if(list.getSelectionValue(i).isSelected()) {
            idx.add(i);
         }
      }

      return idx;
   }

   private static TimeSliderVSAssembly slider(Viewsheet vs) {
      return (TimeSliderVSAssembly) vs.getAssembly(SLIDER);
   }

   private static TimeSliderSelection tss(TimeSliderVSAssembly ts) {
      return ((TimeSliderVSAssemblyInfo) ts.getInfo()).getTimeSliderSelection();
   }

   private static String xml(Object obj) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);

      if(obj instanceof AbstractSheet sheet) {
         sheet.writeXML(writer);
      }
      else if(obj instanceof SelectionList list) {
         list.writeXML(writer, true);
      }
      else {
         ((TimeSliderVSAssemblyInfo) obj).writeXML(writer);
      }

      writer.flush();
      return buf.toString();
   }

   private static final String SLIDER = "RangeSlider1";
}
