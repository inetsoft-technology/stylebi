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
package inetsoft.uql.asset;

import inetsoft.report.CellBinding;
import inetsoft.report.TableCellBinding;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77891, #77892: user text and data values holding {@code ]]>} or a control character
 * that XML 1.0 can't carry (such as U+0001) were written raw into CDATA, so the saved
 * viewsheet, worksheet or logical model could not be read back, and a bookmark holding one
 * restored no state. Each case saves the asset through the production storage
 * ({@link BlobIndexedStorage#putXMLSerializable}) and loads it with
 * {@link BlobIndexedStorage#getXMLSerializable}.
 * <p>
 * Data values (selected values, list values, alias keys, bookmark state and names) come back
 * exactly. Free text (scripts, labels, titles, descriptions, tooltips, aliases) comes back
 * with each control character replaced by a space; TAB, LF and CR are kept.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class InputValueCdataRoundTripTest {
   private static final String TEXT = "x ]]> y ]]>]]> z";
   private static final String CTRL = "a\u0001b\tc\u001Fd";
   private static final String CTRL_LOSSY = "a b\tc d";
   private static final String[] PAYLOADS = { TEXT, CTRL };

   @Autowired
   private BlobStorageManager blobStorageManager;

   private BlobIndexedStorage storage;
   private final List<String> keys = new ArrayList<>();

   @BeforeEach
   void setUp() {
      storage = new BlobIndexedStorage(blobStorageManager);
   }

   @AfterEach
   void tearDown() {
      keys.forEach(storage::remove);
      keys.clear();
   }

   @Test
   void textInputValueDefaultTextToolTipAndLabel() throws Exception {
      int n = 0;

      for(String p : PAYLOADS) {
         Viewsheet vs = new Viewsheet();
         TextInputVSAssembly ti = new TextInputVSAssembly(vs, "TextInput1");
         TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) ti.getVSAssemblyInfo();
         ti.setSelectedObject("v" + p);
         info.setDefaultTextValue("d" + p);
         info.setToolTipValue("t" + p);
         info.getLabelInfo().setLabelTextValue("l" + p);
         vs.addAssembly(ti);

         Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77891ti" + n++), vs);

         TextInputVSAssembly tback = (TextInputVSAssembly) back.getAssembly("TextInput1");
         TextInputVSAssemblyInfo iback = (TextInputVSAssemblyInfo) tback.getVSAssemblyInfo();
         assertEquals("v" + p, tback.getSelectedObject());
         assertEquals("d" + p, iback.getDefaultTextValue());
         assertEquals("t" + lossy(p), iback.getToolTipValue());
         assertEquals("l" + lossy(p), iback.getLabelInfo().getLabelTextValue());
      }
   }

   @Test
   void comboBoxRadioButtonAndCheckBoxSelectedValues() throws Exception {
      int n = 0;

      for(String p : PAYLOADS) {
         Viewsheet vs = new Viewsheet();
         ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "ComboBox1");
         combo.setTextEditable(true);
         combo.setSelectedObject("c" + p);
         vs.addAssembly(combo);
         RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RadioButton1");
         radio.setSelectedObject("r" + p);
         vs.addAssembly(radio);
         CheckBoxVSAssembly check = new CheckBoxVSAssembly(vs, "CheckBox1");
         CheckBoxVSAssemblyInfo cinfo = (CheckBoxVSAssemblyInfo) check.getVSAssemblyInfo();
         cinfo.setValues(new Object[] { "v" + p, "v2" });
         cinfo.setLabels(new String[] { "l" + p, "l2" });
         check.setSelectedObjects(new Object[] { "s" + p, "plain" });
         vs.addAssembly(check);

         Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77891sel" + n++), vs);

         assertEquals("c" + p, ((ComboBoxVSAssembly) back.getAssembly("ComboBox1"))
            .getSelectedObject());
         assertEquals("r" + p, ((RadioButtonVSAssembly) back.getAssembly("RadioButton1"))
            .getSelectedObject());
         CheckBoxVSAssembly cback = (CheckBoxVSAssembly) back.getAssembly("CheckBox1");
         assertArrayEquals(new Object[] { "s" + p, "plain" }, cback.getSelectedObjects());
         CheckBoxVSAssemblyInfo ciback = (CheckBoxVSAssemblyInfo) cback.getVSAssemblyInfo();
         assertArrayEquals(new Object[] { "v" + p, "v2" }, ciback.getValues());
         assertArrayEquals(new String[] { "l" + lossy(p), "l2" }, ciback.getLabels());
      }
   }

   @Test
   void chartLegendAndAxisAliasesTitleAndTooltip() throws Exception {
      int n = 0;

      for(String p : PAYLOADS) {
         Viewsheet vs = new Viewsheet();
         ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
         LegendDescriptor legend = info.getChartDescriptor().getLegendsDescriptor()
            .getColorLegendDescriptor();
         legend.setTitleValue("t" + p);
         // the key of an alias is the data value being aliased
         legend.setLabelAlias("k" + p, "a" + p);
         info.getVSChartInfo().getAxisDescriptor().setLabelAlias("ak" + p, "aa" + p);
         info.getVSChartInfo().setToolTipValue("tip" + p);
         vs.addAssembly(chart);

         Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77891ch" + n++), vs);

         ChartVSAssemblyInfo iback =
            (ChartVSAssemblyInfo) back.getAssembly("Chart1").getVSAssemblyInfo();
         LegendDescriptor lback = iback.getChartDescriptor().getLegendsDescriptor()
            .getColorLegendDescriptor();
         assertEquals("t" + lossy(p), lback.getTitleValue());
         assertEquals("a" + lossy(p), lback.getLabelAlias("k" + p));
         assertEquals("aa" + lossy(p),
                      iback.getVSChartInfo().getAxisDescriptor().getLabelAlias("ak" + p));
         assertEquals("tip" + lossy(p), iback.getVSChartInfo().getToolTipValue());
      }
   }

   @Test
   void chartFormatKeyCustomTooltipAndGaugeTooltip() throws Exception {
      int n = 0;

      for(String p : PAYLOADS) {
         Viewsheet vs = new Viewsheet();
         ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
         VSChartInfo cinfo = ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).getVSChartInfo();
         // the key of a column label format is a column name
         cinfo.getAxisDescriptor().setColumnLabelTextFormat("f" + p, new CompositeTextFormat());
         cinfo.setToolTip("c" + p);
         vs.addAssembly(chart);
         GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
         ((GaugeVSAssemblyInfo) gauge.getVSAssemblyInfo()).setCustomTooltipString("g" + p);
         vs.addAssembly(gauge);

         Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77891fmt" + n++), vs);

         VSChartInfo cback = ((ChartVSAssemblyInfo) back.getAssembly("Chart1")
            .getVSAssemblyInfo()).getVSChartInfo();
         assertEquals(Set.of("f" + lossy(p)),
                      cback.getAxisDescriptor().getColumnLabelTextFormatColumns());
         assertEquals("c" + lossy(p), cback.getCustomTooltip());
         assertEquals("g" + lossy(p), ((GaugeVSAssemblyInfo) back.getAssembly("Gauge1")
            .getVSAssemblyInfo()).getCustomTooltipString());
      }
   }

   @Test
   void outputTooltipDisplayValueAndMeasureLabel() throws Exception {
      int n = 0;

      for(String p : PAYLOADS) {
         Viewsheet vs = new Viewsheet();
         TextVSAssembly text = new TextVSAssembly(vs, "Text1");
         TextVSAssemblyInfo tinfo = (TextVSAssemblyInfo) text.getVSAssemblyInfo();
         tinfo.setCustomTooltipString("tip" + p);
         // the runtime value is written as <displayValue> and not read back
         tinfo.setValue("data" + p);
         vs.addAssembly(text);
         SelectionListVSAssembly sl = new SelectionListVSAssembly(vs, "SL1");
         SelectionList list = new SelectionList();
         SelectionValue value = new SelectionValue("lab" + p, "val" + p);
         // the measure label is written as <mlabel> and not read back
         value.setMeasureLabel("m" + p);
         list.addSelectionValue(value);
         ((SelectionListVSAssemblyInfo) sl.getVSAssemblyInfo()).setSelectionList(list);
         vs.addAssembly(sl);

         Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77891out" + n++), vs);

         assertEquals("tip" + lossy(p), ((TextVSAssemblyInfo) back.getAssembly("Text1")
            .getVSAssemblyInfo()).getCustomTooltipString());
         assertNotNull(back.getAssembly("SL1"));
      }
   }

   /**
    * The scripts, labels, titles and names that #77824/#77850 already split for ]]>, holding a
    * control character. TAB indentation in a script must survive.
    */
   @Test
   void viewsheetScriptsLabelsAndNamesWithControlCharacter() throws Exception {
      String script = "if(a) {\n\tvar s = '" + CTRL + "';\n}";
      String scriptBack = "if(a) {\n\tvar s = '" + CTRL_LOSSY + "';\n}";
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setOnInit(script);
      vs.getViewsheetInfo().setOnLoad("l" + CTRL);
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      TextVSAssemblyInfo tinfo = (TextVSAssemblyInfo) text.getVSAssemblyInfo();
      tinfo.setScript(script);
      tinfo.setOnClick("c" + CTRL);
      tinfo.setTextValue("t" + CTRL);
      tinfo.setDescription("d" + CTRL);
      tinfo.setVisibleValue("=v" + CTRL);
      tinfo.setEnabledValue("=e" + CTRL);
      vs.addAssembly(text);

      CheckBoxVSAssembly check = new CheckBoxVSAssembly(vs, "CheckBox1");
      CheckBoxVSAssemblyInfo cinfo = (CheckBoxVSAssemblyInfo) check.getVSAssemblyInfo();
      cinfo.setTitleValue("ti" + CTRL);
      ListData data = new ListData();
      data.setDataType(XSchema.STRING);
      data.setLabels(new String[] { "ll" + CTRL, "plain" });
      data.setValues(new Object[] { "lv" + CTRL, "v2" });
      cinfo.setListData(data);
      vs.addAssembly(check);

      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      ((TabVSAssemblyInfo) tab.getVSAssemblyInfo()).setLabelsValue(new String[] { "tb" + CTRL });
      vs.addAssembly(tab);
      SubmitVSAssembly submit = new SubmitVSAssembly(vs, "Submit1");
      ((SubmitVSAssemblyInfo) submit.getVSAssemblyInfo()).setLabelName("s" + CTRL);
      vs.addAssembly(submit);

      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ChartVSAssemblyInfo chinfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      chinfo.getChartDescriptor().getTitlesDescriptor().getXTitleDescriptor()
         .setTitleValue("x" + CTRL);
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue("g" + CTRL);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("m" + CTRL);
      chinfo.getVSChartInfo().addXField(dim);
      chinfo.getVSChartInfo().addYField(agg);
      vs.addAssembly(chart);

      CalculateRef cref = new CalculateRef(false);
      ExpressionRef eref = new ExpressionRef(null, "calc1");
      eref.setExpression("e" + CTRL);
      cref.setDataRef(eref);
      vs.addCalcField("T1", cref);
      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(calc);
      calc.getTableLayout().setCellBinding(
         0, 0, new TableCellBinding(CellBinding.BIND_FORMULA, "f" + CTRL));

      AssetEntry dependency = viewsheetEntry("dep77892");
      dependency.setAlias("al" + CTRL);
      vs.addOuterDependency(dependency);
      AssetEntry base = worksheetEntry("base77892");
      base.setProperty("_description_", "bd" + CTRL);
      vs.setBaseEntry(base);

      Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77892"), vs);

      assertEquals(scriptBack, back.getViewsheetInfo().getOnInit());
      assertEquals("l" + CTRL_LOSSY, back.getViewsheetInfo().getOnLoad());
      TextVSAssemblyInfo tback = (TextVSAssemblyInfo) back.getAssembly("Text1")
         .getVSAssemblyInfo();
      assertEquals(scriptBack, tback.getScript());
      assertEquals("c" + CTRL_LOSSY, tback.getOnClick());
      assertEquals("t" + CTRL_LOSSY, tback.getTextValue());
      assertEquals("d" + CTRL_LOSSY, tback.getDescription());
      assertEquals("=v" + CTRL_LOSSY, tback.getVisibleValue());
      assertEquals("=e" + CTRL_LOSSY, tback.getEnabledValue());

      CheckBoxVSAssemblyInfo cback = (CheckBoxVSAssemblyInfo) back.getAssembly("CheckBox1")
         .getVSAssemblyInfo();
      assertEquals("ti" + CTRL_LOSSY, cback.getTitleValue());
      assertArrayEquals(new String[] { "ll" + CTRL_LOSSY, "plain" },
                        cback.getListData().getLabels());
      // a list value is matched with the selected value, so it comes back exactly
      assertArrayEquals(new Object[] { "lv" + CTRL, "v2" }, cback.getListData().getValues());

      assertArrayEquals(new String[] { "tb" + CTRL_LOSSY },
                        ((TabVSAssemblyInfo) back.getAssembly("Tab1").getVSAssemblyInfo())
                           .getLabelsValue());
      assertEquals("s" + CTRL_LOSSY, ((SubmitVSAssemblyInfo) back.getAssembly("Submit1")
         .getVSAssemblyInfo()).getLabelName());

      ChartVSAssemblyInfo chback =
         (ChartVSAssemblyInfo) back.getAssembly("Chart1").getVSAssemblyInfo();
      assertEquals("x" + CTRL_LOSSY, chback.getChartDescriptor().getTitlesDescriptor()
         .getXTitleDescriptor().getTitleValue());
      assertEquals("g" + CTRL_LOSSY,
                   ((VSDimensionRef) chback.getVSChartInfo().getXField(0)).getGroupColumnValue());
      assertEquals("m" + CTRL_LOSSY,
                   ((VSAggregateRef) chback.getVSChartInfo().getYField(0)).getColumnValue());

      assertEquals("e" + CTRL_LOSSY,
                   ((ExpressionRef) back.getCalcField("T1", "calc1").getDataRef()).getExpression());
      assertEquals("f" + CTRL_LOSSY, ((CalcTableVSAssembly) back.getAssembly("Calc1"))
         .getTableLayout().getCellBinding(0, 0).getValue());

      assertEquals("al" + CTRL_LOSSY, back.getOuterDependencies()[0].getAlias());
      assertEquals("bd" + CTRL_LOSSY, back.getBaseEntry().getProperty("_description_"));
   }

   @Test
   void worksheetAndLogicalModelTextWithControlCharacter() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T1");
      table.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER },
                                               new Object[][] { { "id" }, { 1 } }));
      ws.addAssembly(table);
      table.getTableInfo().setDescription("td" + CTRL);
      ColumnSelection columns = table.getColumnSelection(false);
      ColumnRef column = (ColumnRef) columns.getAttribute("id");
      column.setDescription("cd" + CTRL);
      column.setCaption("cc" + CTRL);
      ExpressionRef exp = new ExpressionRef(null, "calc");
      exp.setExpression("x" + CTRL);
      ColumnRef calc = new ColumnRef(exp);
      calc.setDataType(XSchema.INTEGER);
      columns.addAttribute(calc);
      table.setColumnSelection(columns, false);
      ExpressionValue value = new ExpressionValue();
      value.setType(ExpressionValue.JAVASCRIPT);
      value.setExpression("ev" + CTRL);
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.setType(XSchema.INTEGER);
      condition.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(column, condition, 0));
      table.setPreConditionList(list);

      Worksheet wback = (Worksheet) roundTrip(worksheetEntry("ws77892"), ws);

      TableAssembly tback = (TableAssembly) wback.getAssembly("T1");
      assertEquals("td" + CTRL_LOSSY, tback.getTableInfo().getDescription());
      ColumnRef cback = (ColumnRef) tback.getColumnSelection(false).getAttribute("id");
      assertEquals("cd" + CTRL_LOSSY, cback.getDescription());
      assertEquals("cc" + CTRL_LOSSY, cback.getCaption());
      assertEquals("x" + CTRL_LOSSY, ((ExpressionRef) ((ColumnRef) tback.getColumnSelection(false)
         .getAttribute("calc")).getDataRef()).getExpression());
      ConditionItem item = (ConditionItem) tback.getPreConditionList().getItem(0);
      assertEquals("ev" + CTRL_LOSSY, ((ExpressionValue) ((AssetCondition) item.getXCondition())
         .getValue(0)).getExpression());

      XLogicalModel lm = new XLogicalModel("LM");
      lm.setDescription("m" + CTRL);
      XEntity entity = new XEntity("E");
      entity.setDescription("e" + CTRL);
      XAttribute attr = new XAttribute("A", "T", "C");
      attr.setDescription("a" + CTRL);
      entity.addAttribute(attr);
      ExpressionAttribute eattr = new ExpressionAttribute("X", "case when " + CTRL + " end");
      eattr.setDescription("xd" + CTRL);
      entity.addAttribute(eattr);
      lm.addEntity(entity);
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.LOGIC_MODEL, "DS/LM77892", null, orgID());

      XLogicalModel lback = (XLogicalModel) roundTrip(entry, lm);

      assertEquals("m" + CTRL_LOSSY, lback.getDescription());
      assertEquals("e" + CTRL_LOSSY, lback.getEntity("E").getDescription());
      assertEquals("a" + CTRL_LOSSY, lback.getEntity("E").getAttribute("A").getDescription());
      ExpressionAttribute eback = (ExpressionAttribute) lback.getEntity("E").getAttribute("X");
      assertEquals("case when " + CTRL_LOSSY + " end", eback.getExpression());
      assertEquals("xd" + CTRL_LOSSY, eback.getDescription());
   }

   /**
    * A viewer types into a text input or an editable combo box and saves a bookmark. The whole
    * bookmark state, including the other assemblies, must be restored.
    */
   @Test
   void bookmarkRestoresTypedTextInputAndComboBoxValues() throws Exception {
      int n = 0;

      for(String p : PAYLOADS) {
         Viewsheet vs = new Viewsheet();
         TextInputVSAssembly ti = new TextInputVSAssembly(vs, "TextInput1");
         vs.addAssembly(ti);
         ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "ComboBox1");
         combo.setTextEditable(true);
         vs.addAssembly(combo);
         TextInputVSAssembly other = new TextInputVSAssembly(vs, "TextInput2");
         vs.addAssembly(other);
         ti.setSelectedObject("typed" + p);
         combo.setSelectedObject("combo" + p);
         other.setSelectedObject("plain");

         AssetEntry vsEntry = viewsheetEntry("bm77891_" + n++);
         VSBookmark bookmark = new VSBookmark(vsEntry.toIdentifier(), user());
         bookmark.addBookmark("b1", vs, VSBookmarkInfo.ALLSHARE, false, true);
         VSBookmark bback = roundTripBookmark(vsEntry, bookmark);

         ti.setSelectedObject("other");
         combo.setSelectedObject("other");
         other.setSelectedObject("other");
         bback.getBookmark("b1", vs);

         assertEquals("typed" + p, ti.getSelectedObject());
         assertEquals("combo" + p, combo.getSelectedObject());
         assertEquals("plain", other.getSelectedObject());
      }
   }

   @Test
   void bookmarkNamesRoundTrip() throws Exception {
      Viewsheet vs = new Viewsheet();
      TextInputVSAssembly ti = new TextInputVSAssembly(vs, "TextInput1");
      vs.addAssembly(ti);
      AssetEntry vsEntry = viewsheetEntry("bm77891names");
      VSBookmark bookmark = new VSBookmark(vsEntry.toIdentifier(), user());
      String[] names = { "b]]>1", "b\u00011", "plain" };

      for(String name : names) {
         ti.setSelectedObject("v" + name);
         bookmark.addBookmark(name, vs, VSBookmarkInfo.ALLSHARE, false, true);
      }

      VSBookmark bback = roundTripBookmark(vsEntry, bookmark);

      for(String name : names) {
         assertNotNull(bback.getBookmarkInfo(name), name);
         ti.setSelectedObject("other");
         bback.getBookmark(name, vs);
         assertEquals("v" + name, ti.getSelectedObject(), name);
      }
   }

   /**
    * Values without ]]> or a control character, including backslashes and text that looks like
    * an escape, are written exactly as before (no marker, no split) and read back unchanged,
    * so files written before this change still read the same.
    */
   @Test
   void ordinaryAndBackslashValuesAreWrittenAsBefore() throws Exception {
      String value = "c:\\new \\u0001 [x] > y";
      Viewsheet vs = new Viewsheet();
      TextInputVSAssembly ti = new TextInputVSAssembly(vs, "TextInput1");
      ti.setSelectedObject(value);
      vs.addAssembly(ti);
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).getChartDescriptor()
         .getLegendsDescriptor().getColorLegendDescriptor().setLabelAlias(value, "alias");
      vs.addAssembly(chart);

      String xml = xml(vs::writeXML);
      assertFalse(xml.contains("ctrlEncoded"), xml);
      assertFalse(xml.contains("]]]]><![CDATA[>"), xml);
      assertTrue(xml.contains("<state_selectedObject><![CDATA[" + value +
                              "]]></state_selectedObject>"), xml);
      assertTrue(xml.contains("<key><![CDATA[" + value + "]]>"), xml);

      Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77891compat"), vs);

      assertEquals(value, ((TextInputVSAssembly) back.getAssembly("TextInput1"))
         .getSelectedObject());
      assertEquals("alias", ((ChartVSAssemblyInfo) back.getAssembly("Chart1").getVSAssemblyInfo())
         .getChartDescriptor().getLegendsDescriptor().getColorLegendDescriptor()
         .getLabelAlias(value));
   }

   private XMLSerializable roundTrip(AssetEntry entry, XMLSerializable obj) throws Exception {
      String key = entry.toIdentifier();
      keys.add(key);
      storage.putXMLSerializable(key, obj);
      XMLSerializable back = storage.getXMLSerializable(key, null, orgID());
      assertNotNull(back);
      return back;
   }

   private VSBookmark roundTripBookmark(AssetEntry vsEntry, VSBookmark bookmark)
      throws Exception
   {
      AssetEntry entry = new AssetEntry(AssetRepository.USER_SCOPE,
                                        AssetEntry.Type.VIEWSHEET_BOOKMARK,
                                        VSUtil.createBookmarkIdentifier(vsEntry), user());
      return (VSBookmark) roundTrip(entry, bookmark);
   }

   private static String lossy(String value) {
      StringBuilder buf = new StringBuilder(value);

      for(int i = 0; i < buf.length(); i++) {
         char c = buf.charAt(i);

         if(c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
            buf.setCharAt(i, ' ');
         }
      }

      return buf.toString();
   }

   private static IdentityID user() {
      return new IdentityID("admin", orgID());
   }

   private static AssetEntry viewsheetEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null,
                            orgID());
   }

   private static AssetEntry worksheetEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null,
                            orgID());
   }

   private static String orgID() {
      return OrganizationManager.getInstance().getCurrentOrgID();
   }

   private static String xml(java.util.function.Consumer<PrintWriter> writer) {
      StringWriter buf = new StringWriter();
      PrintWriter out = new PrintWriter(buf);
      writer.accept(out);
      out.flush();
      return buf.toString();
   }
}
