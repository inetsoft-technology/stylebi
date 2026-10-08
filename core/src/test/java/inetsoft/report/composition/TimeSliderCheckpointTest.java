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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.composition.execution.TimeSliderVSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderSelection;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.RangeSliderPropertyDialogService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77980: an undo checkpoint of a range slider must keep the TimeSliderSelection (value
 * format, date levels) that matches its own selection list, and a selection that can't be
 * parsed must not fail the load.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TimeSliderCheckpointTest {
   @BeforeEach
   void capture() {
      tssLogger = (Logger) LoggerFactory.getLogger(TimeSliderSelection.class);
      sheetLogger = (Logger) LoggerFactory.getLogger(RuntimeSheet.class);
      appender = new ListAppender<>();
      appender.start();
      tssLogger.addAppender(appender);
      sheetLogger.addAppender(appender);
   }

   @AfterEach
   void detach() {
      tssLogger.detachAppender(appender);
      sheetLogger.detachAppender(appender);
   }

   /**
    * A checkpoint taken before a Month -> Year unit change is serialized late (at saveState
    * time). It must still be read back as the Month state, and undo/redo must reach it after
    * the checkpoints are rebuilt from the runtime cache.
    */
   @Test
   void checkpointBeforeUnitChangeSurvivesCacheRebuild() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createDateSlider(vs);
      RuntimeSheet.XSwappableSheetList points = new RuntimeSheet.XSwappableSheetList(null);

      query(vs, new Object[] { date(2023, 3), date(2026, 12) });
      select(ts, 0, 5);
      Viewsheet c0 = vs.prepareCheckpoint();
      points.add(c0);
      assertNotSame(tss(ts), tss((TimeSliderVSAssembly) c0.getAssembly(SLIDER)),
                    "the checkpoint owns its TimeSliderSelection");
      String monthFirst = ts.getSelectionList().getSelectionValue(0).getValue();
      int monthCount = ts.getSelectionList().getSelectionValueCount();

      changeColumn(ts, XSchema.DATE, TimeInfo.YEAR);
      query(vs, new Object[] { date(2023, 3), date(2026, 12) });
      select(ts, 0, 1);
      points.add(vs.prepareCheckpoint());
      select(ts, 0, 0);
      points.add(vs.prepareCheckpoint());
      String yearFirst = ts.getSelectionList().getSelectionValue(0).getValue();
      assertNotEquals(monthFirst, yearFirst);

      // what saveState writes, and what the runtime cache rebuild reads back
      RuntimeSheetState state = new RuntimeSheetState();
      List<String> vals = new ArrayList<>();

      for(int i = 0; i < points.size(); i++) {
         String[] xml = points.getXmlForState(i);
         vals.add(xml[0]);
         vals.add(xml[1]);
      }

      state.setPoints(vals);
      String sheetXml = xml(vs);
      RuntimeSheet.encodePointDeltas(state, sheetXml);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class, CALLS_REAL_METHODS);
      rvs.decodePointDeltas(state.getPointDeltas(), sheetXml,
                            x -> RuntimeSheet.loadXml(new Viewsheet(), x));

      assertEquals(List.of(), warnings());
      assertEquals(3, rvs.points.size(), "no checkpoint is dropped");

      // undo/redo across the unit change, as after a rebuild at the latest point
      doReturn(null).when(rvs).getAssetRepository();
      doNothing().when(rvs).updateLayoutInfo(any());
      doNothing().when(rvs).setViewsheet(any());
      doNothing().when(rvs).resetRuntime();
      rvs.point = 2;

      assertTrue(rvs.undo(null));
      assertTrue(rvs.undo(null));
      Viewsheet restored = lastRestored(rvs, 2);
      SelectionList month = ((TimeSliderVSAssembly) restored.getAssembly(SLIDER)).getSelectionList();
      assertEquals(TimeInfo.MONTH, rangeType(restored));
      assertEquals(monthCount, month.getSelectionValueCount());
      assertEquals(monthFirst, month.getSelectionValue(0).getValue());

      assertTrue(rvs.redo(null));
      Viewsheet redone = lastRestored(rvs, 3);
      assertEquals(TimeInfo.YEAR, rangeType(redone));
      assertEquals(yearFirst, ((TimeSliderVSAssembly) redone.getAssembly(SLIDER))
         .getSelectionList().getSelectionValue(0).getValue());
      assertEquals(List.of(), warnings());
   }

   /**
    * Values that don't match the value format (e.g. written with another date level) are
    * loaded as they were parsed, and the next query rebuilds the full range.
    */
   @Test
   void mismatchedValueFormatDoesNotFailTheLoad() throws Exception {
      SelectionList written = new SelectionList();

      for(int m = 3; m <= 8; m++) {
         Date d = date(2023, m);
         SelectionValue value = new SelectionValue(Tool.monthFmt.get().format(d) + "",
                                                   Tool.monthFmt.get().format(d));
         value.setState(m <= 5 ? SelectionValue.STATE_SELECTED : 0);
         written.addSelectionValue(value);
      }

      TimeSliderSelection tss = new TimeSliderSelection();
      tss.setLabelFormat(Tool.createDateFormat(TimeSliderVSAssembly.LABEL_YEAR_PATTERN, Locale.US));
      tss.setValueFormat(TimeSliderVSAssembly.VALUE_YEAR_FORMAT.get());
      tss.setIncrement(1);
      tss.setDateLevels(new int[] { Calendar.YEAR });
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      tss.writeXML(writer, written);
      writer.flush();

      SelectionList parsed = (SelectionList) written.clone();
      TimeSliderSelection read = new TimeSliderSelection();
      assertDoesNotThrow(() -> read.parseXML(
         Tool.parseXML(new StringReader(buf.toString())).getDocumentElement(), parsed));
      assertEquals(6, parsed.getSelectionValueCount(), "parseXML leaves the passed-in list unchanged");
      assertEquals(written.getSelectionValue(0).getValue(), parsed.getSelectionValue(0).getValue());
   }

   /**
    * Changing a date slider to a number column in the dialog and giving it a number format
    * must not keep the old date levels, or the live sheet can't be read back.
    */
   @Test
   void dateToNumberWithNumberFormatRoundTrips() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createDateSlider(vs);
      query(vs, new Object[] { date(2023, 3), date(2026, 12) });

      changeColumn(ts, XSchema.INTEGER, TimeInfo.NUMBER);
      VSCompositeFormat fmt = ts.getVSAssemblyInfo().getFormat();
      fmt.getUserDefinedFormat().setFormatValue("DecimalFormat");
      fmt.getUserDefinedFormat().setFormatExtentValue("#,##0.00");
      query(vs, new Object[] { 0, 100 });
      int count = ts.getSelectionList().getSelectionValueCount();
      assertTrue(count > 1);
      assertNull(tss(ts).getDateLevels());

      Viewsheet loaded = RuntimeSheet.loadXml(new Viewsheet(), xml(vs));
      assertNotNull(loaded, "the live sheet is read back");
      assertEquals(count, ((TimeSliderVSAssembly) loaded.getAssembly(SLIDER))
         .getSelectionList().getSelectionValueCount());
      assertEquals(List.of(), warnings());
   }

   /**
    * An in-place title edit after a checkpoint must not change the checkpoint.
    */
   @Test
   void titleEditDoesNotChangeEarlierCheckpoint() {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createDateSlider(vs);
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) ts.getVSAssemblyInfo();
      info.setTitleValue("Before");
      Viewsheet checkpoint = vs.prepareCheckpoint();

      info.setTitleValue("After");

      assertEquals("Before", ((TimeSliderVSAssemblyInfo) checkpoint.getAssembly(SLIDER)
         .getInfo()).getTitleValue());
   }

   /**
    * A stored sheet whose slider values don't match its value format is read back (not
    * dropped), and the next query rebuilds the full range.
    */
   @Test
   void unparseableStoredSheetLoadsAndQueryRebuilds() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createDateSlider(vs);
      query(vs, new Object[] { date(2023, 3), date(2026, 12) });
      select(ts, 0, 5);
      String stored = xml(vs);
      // the Month values with the Year value format
      String bad = stored.replace("''yyyy-MM''", "''yyyy''");
      assertNotEquals(stored, bad);

      Viewsheet loaded = RuntimeSheet.loadXml(new Viewsheet(), bad);

      assertNotNull(loaded, "the sheet is read back");
      assertTrue(warnings().stream().noneMatch(w -> w.contains("Failed to load")));
      query(loaded, new Object[] { date(2023, 3), date(2026, 12) });
      assertEquals(ts.getSelectionList().getSelectionValueCount(),
                   ((TimeSliderVSAssembly) loaded.getAssembly(SLIDER))
                      .getSelectionList().getSelectionValueCount());
   }

   /**
    * Querying a viewsheet clone (bookmark, export) with another unit must not change the
    * original's slider selection or title.
    */
   @Test
   void queryingCloneDoesNotChangeOriginal() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createDateSlider(vs);
      ((TimeSliderVSAssemblyInfo) ts.getInfo()).setTitleValue("Original");
      query(vs, new Object[] { date(2023, 3), date(2026, 12) });
      String before = xml(vs);

      Viewsheet copy = (Viewsheet) vs.clone();
      TimeSliderVSAssembly copySlider = (TimeSliderVSAssembly) copy.getAssembly(SLIDER);
      changeColumn(copySlider, XSchema.DATE, TimeInfo.YEAR);
      query(copy, new Object[] { date(2023, 3), date(2026, 12) });
      ((TimeSliderVSAssemblyInfo) copySlider.getInfo()).setTitleValue("Copy");

      assertEquals(before, xml(vs));
      assertNotNull(RuntimeSheet.loadXml(new Viewsheet(), xml(vs)));
      assertNotNull(RuntimeSheet.loadXml(new Viewsheet(), xml(copy)));
      assertEquals(List.of(), warnings());
   }

   private static Date date(int year, int month) {
      return new GregorianCalendar(year, month - 1, 1).getTime();
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

   /** the real query refresh with the min/max of the bound column */
   private static void query(Viewsheet vs, Object[] minMax) throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);
      new TimeSliderVSAQuery(box, SLIDER).refreshSelectionValue(minMax);
   }

   private static void select(TimeSliderVSAssembly ts, int from, int to) {
      SelectionList slist = ts.getSelectionList();

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         slist.getSelectionValue(i).setState(
            i >= from && i <= to ? SelectionValue.STATE_SELECTED : 0);
      }
   }

   /** the range slider dialog's column/unit change: setTimeInfo + setVSAssemblyInfo */
   private static void changeColumn(TimeSliderVSAssembly ts, String type, int unit)
      throws Exception
   {
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) Tool.clone(ts.getVSAssemblyInfo());
      RangeSliderDataPaneModel data = new RangeSliderDataPaneModel();
      OutputColumnRefModel col = new OutputColumnRefModel();
      col.setEntity("Order");
      col.setAttribute(XSchema.DATE.equals(type) ? "Date" : "Quantity");
      col.setDataType(type);
      data.setSelectedColumns(new OutputColumnRefModel[] { col });
      RangeSliderSizePaneModel size = new RangeSliderSizePaneModel();
      size.setRangeType(unit);
      RangeSliderPropertyDialogService service =
         new RangeSliderPropertyDialogService(null, null, null, null, null, null, null, null);
      Method setTimeInfo = RangeSliderPropertyDialogService.class.getDeclaredMethod(
         "setTimeInfo", TimeSliderVSAssemblyInfo.class, RangeSliderDataPaneModel.class,
         RangeSliderSizePaneModel.class);
      setTimeInfo.setAccessible(true);
      setTimeInfo.invoke(service, info, data, size);
      ts.setVSAssemblyInfo(info);
   }

   private static TimeSliderSelection tss(TimeSliderVSAssembly ts) {
      return ((TimeSliderVSAssemblyInfo) ts.getInfo()).getTimeSliderSelection();
   }

   private static int rangeType(Viewsheet vs) {
      return ((SingleTimeInfo) ((TimeSliderVSAssembly) vs.getAssembly(SLIDER)).getTimeInfo())
         .getRangeType();
   }

   private static Viewsheet lastRestored(RuntimeViewsheet rvs, int calls) {
      ArgumentCaptor<Viewsheet> captor = ArgumentCaptor.forClass(Viewsheet.class);
      verify(rvs, times(calls)).setViewsheet(captor.capture());
      return captor.getValue();
   }

   private static String xml(AbstractSheet sheet) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      sheet.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private List<String> warnings() {
      return appender.list.stream()
         .filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN))
         .map(ILoggingEvent::getFormattedMessage)
         .toList();
   }

   private static final String SLIDER = "RangeSlider1";
   private Logger tssLogger;
   private Logger sheetLogger;
   private ListAppender<ILoggingEvent> appender;
}
