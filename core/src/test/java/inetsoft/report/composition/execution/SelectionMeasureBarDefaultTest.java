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
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82153: the Selection List measure bar default is resolved from the list's own mark, at
 * creation and on every data load, never from the org gate.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SelectionMeasureBarDefaultTest {
   @BeforeEach
   void setUp() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void anUnmarkedListStaysClassicAfterALoadEvenInAGateOnOrg() throws Exception {
      Fixture f = new Fixture(null);
      // gate on, but the list's own mark is absent: it must not turn grey
      assertEquals(BLUE, bar(f.info, 0));
      f.load();
      assertEquals(BLUE, bar(f.info, 0));
   }

   @Test
   void aMarkedLightListTakesTheNeutralGreyOnLoad() throws Exception {
      Fixture f = new Fixture(VizMark.MODERN_LIGHT);
      setBar(f.info, BLUE);
      f.load();

      for(int i = 0; i < 5; i++) {
         assertEquals(LIGHT_GREY, bar(f.info, i), "level " + i);
      }
   }

   @Test
   void aMarkedDarkListTakesTheNeutralGreyOnLoad() throws Exception {
      Fixture f = new Fixture(VizMark.MODERN_DARK);
      setBar(f.info, BLUE);
      f.load();
      assertEquals(DARK_GREY, bar(f.info, 0));
   }

   @Test
   void theBarDefaultIsTheSharedDirectionNeutralToken() throws Exception {
      for(VizMark mark : new VizMark[] { VizMark.MODERN_LIGHT, VizMark.MODERN_DARK }) {
         Fixture f = new Fixture(mark);
         setBar(f.info, BLUE);
         f.load();
         assertEquals(VSChartPaletteDefaults.directionNeutral(VizContext.of(mark)).getRGB() + "",
                      bar(f.info, 0), mark.toString());
      }
   }

   @Test
   void theHelperIsClassicWhenUnmarkedAndTheNeutralWhenMarked() {
      assertEquals(BLUE, VSObjectChromeDefaults.measureBarForegroundValue(
         VizContext.of((VizMark) null)));
      assertEquals(LIGHT_GREY, VSObjectChromeDefaults.measureBarForegroundValue(
         VizContext.of(VizMark.MODERN_LIGHT)));
      assertEquals(DARK_GREY, VSObjectChromeDefaults.measureBarForegroundValue(
         VizContext.of(VizMark.MODERN_DARK)));
   }

   @Test
   void creationWritesTheDefaultForTheMark() {
      assertEquals(BLUE, bar(new Fixture(null).info, 0));
      assertEquals(LIGHT_GREY, bar(new Fixture(VizMark.MODERN_LIGHT).info, 0));
      assertEquals(DARK_GREY, bar(new Fixture(VizMark.MODERN_DARK).info, 4));
   }

   @Test
   void anAuthorsBarColorSurvivesLoadsRevertAndAFlip() throws Exception {
      Fixture f = new Fixture(VizMark.MODERN_LIGHT);
      VSCompositeFormat fmt = f.info.getFormatInfo().getFormat(
         SelectionBaseVSAssemblyInfo.getMeasureBarPath(0));
      fmt.getUserDefinedFormat().setForegroundValue("0x10A8AB");

      f.load();
      assertEquals("0x10A8AB", fmt.getUserDefinedFormat().getForegroundValue() + "");
      VizModernizeUtil.applyMark(f.vs, VizMark.MODERN_DARK);
      f.load();
      assertEquals("0x10A8AB", fmt.getUserDefinedFormat().getForegroundValue() + "");
      VizModernizeUtil.revert(f.vs);
      f.load();
      assertEquals("0x10A8AB", fmt.getUserDefinedFormat().getForegroundValue() + "");
   }

   @Test
   void aListWideForegroundKeepsTheStoredBar() throws Exception {
      Fixture f = new Fixture(VizMark.MODERN_LIGHT);
      setBar(f.info, BLUE);
      f.info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH).getUserDefinedFormat()
         .setForegroundValue("0x123456");
      // the refresh does not write the grey for it: the list-wide foreground reaches the bar
      // through FormatInfo.copyDefaultFormat, which is existing behavior
      new SelectionListVSAQuery(f.box, NAME).refreshMeasureFormat();
      assertNotEquals(LIGHT_GREY, bar(f.info, 0));
      assertEquals("0x123456", bar(f.info, 0));
   }

   @Test
   void revertGivesTheClassicBlueNeverGrey() throws Exception {
      Fixture f = new Fixture(VizMark.MODERN_LIGHT);
      f.load();
      assertEquals(LIGHT_GREY, bar(f.info, 0));

      VizModernizeUtil.revert(f.vs);
      f.load();
      assertEquals(BLUE, bar(f.info, 0));
   }

   @Test
   void aDarkFlipMovesTheBarAndBack() throws Exception {
      Fixture f = new Fixture(VizMark.MODERN_LIGHT);
      f.load();
      assertEquals(LIGHT_GREY, bar(f.info, 0));

      VizModernizeUtil.applyMark(f.vs, VizMark.MODERN_DARK);
      f.load();
      assertEquals(DARK_GREY, bar(f.info, 0));

      VizModernizeUtil.applyMark(f.vs, VizMark.MODERN_LIGHT);
      f.load();
      assertEquals(LIGHT_GREY, bar(f.info, 0));
   }

   @Test
   void parsingAnUnmarkedListThenLoadingKeepsTheClassicBlue() throws Exception {
      Fixture f = new Fixture(null);
      setBar(f.info, BLUE);
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      f.info.writeXML(writer);
      writer.flush();

      f.info.parseXML(Tool.parseXML(new StringReader(buf.toString())).getDocumentElement());
      assertNull(f.info.getVizMark(), "premise: the file carries no mark");
      assertEquals(BLUE, bar(f.info, 0));
      f.load();
      assertEquals(BLUE, bar(f.info, 0));
   }

   private static String bar(SelectionListVSAssemblyInfo info, int level) {
      return info.getFormatInfo().getFormat(SelectionBaseVSAssemblyInfo.getMeasureBarPath(level))
         .getDefaultFormat().getForegroundValue() + "";
   }

   private static void setBar(SelectionListVSAssemblyInfo info, String value) {
      for(int i = 0; i < 5; i++) {
         info.getFormatInfo().getFormat(SelectionBaseVSAssemblyInfo.getMeasureBarPath(i))
            .getDefaultFormat().setForegroundValue(value);
      }
   }

   /** A sheet holding one list, built under the given mark. */
   private static final class Fixture {
      Fixture(VizMark mark) {
         vs = new Viewsheet();
         vs.getVSAssemblyInfo().setName("vs1");
         vs.getVSAssemblyInfo().setVizMark(mark);
         box = mock(ViewsheetSandbox.class);
         when(box.getID()).thenReturn("vs1");
         when(box.getViewsheet()).thenReturn(vs);
         when(box.getMode()).thenReturn(RuntimeViewsheet.VIEWSHEET_DESIGN_MODE);
         list = new SelectionListVSAssembly(vs, NAME);
         info = (SelectionListVSAssemblyInfo) list.getInfo();
         info.setTableName(TABLE);
         ColumnRef ref = new ColumnRef(new AttributeRef(null, COL));
         ref.setDataType("string");
         info.setDataRef(ref);
         vs.addAssembly(list);
         // an unmarked fixture is a classic list, whatever the gate says
         info.setVizMark(mark);
         info.initDefaultFormat();
      }

      /** One data load, the entry the sandbox reset runs for a list. */
      void load() throws Exception {
         XTable data = new DefaultTableLens(new Object[][] { { COL }, { "a" }, { "b" } });
         new SelectionListVSAQuery(box, NAME).refreshSelectionValue0(
            data, new HashMap<>(), new HashMap<>(), new HashMap<>(),
            new SelectionMeasureAggregation(false));
      }

      final Viewsheet vs;
      final ViewsheetSandbox box;
      final SelectionListVSAssembly list;
      final SelectionListVSAssemblyInfo info;
   }

   private static final String NAME = "Selection1";
   private static final String TABLE = "Query1";
   private static final String COL = "Product Line";
   // values from the issue: the classic palette head, and the two direction-neutral tokens
   private static final String BLUE = new Color(0x518DB9).getRGB() + "";
   private static final String LIGHT_GREY = new Color(0x767676).getRGB() + "";
   private static final String DARK_GREY = new Color(0xA8A5AD).getRGB() + "";
}
