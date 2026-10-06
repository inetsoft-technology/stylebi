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

import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.internal.AssetFolder;
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
 * Bug #77850: user text holding {@code ]]>} (a Visible/Enabled/title expression such as
 * {@code =a[b[0]]>1}, a label, a column alias or description, a logical model description,
 * or the name/alias of a dashboard) was written raw into CDATA, so the saved viewsheet,
 * worksheet or logical model could not be read back. For an asset entry, every sheet that
 * embeds or depends on the asset could not be read back. Each case saves the whole asset
 * through the production storage ({@link BlobIndexedStorage#putXMLSerializable}), loads it
 * with {@link BlobIndexedStorage#getXMLSerializable} (which runs the owner's parseXML) and
 * checks the text comes back exactly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UserTextCdataRoundTripTest {
   private static final String JS = "=a[b[0]]>1";
   private static final String TEXT = "x ]]> y ]]>]]> z";
   private static final String NAME = "Q1]]>Q2";

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
   void viewsheetVisibleAndEnabledExpressions() throws Exception {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.getVSAssemblyInfo().setVisibleValue(JS);
      vs.addAssembly(text);
      TextVSAssembly text2 = new TextVSAssembly(vs, "Text2");
      text2.getVSAssemblyInfo().setEnabledValue(JS + " && " + JS);
      vs.addAssembly(text2);

      Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77850v"), vs);

      assertEquals(JS, back.getAssembly("Text1").getVSAssemblyInfo().getVisibleValue());
      assertEquals(JS + " && " + JS,
                   back.getAssembly("Text2").getVSAssemblyInfo().getEnabledValue());
   }

   @Test
   void viewsheetTitle() throws Exception {
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssembly check = new CheckBoxVSAssembly(vs, "CheckBox1");
      ((CheckBoxVSAssemblyInfo) check.getVSAssemblyInfo()).setTitleValue(TEXT);
      vs.addAssembly(check);

      Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77850t"), vs);

      assertEquals(TEXT, ((CheckBoxVSAssemblyInfo) back.getAssembly("CheckBox1")
         .getVSAssemblyInfo()).getTitleValue());
   }

   @Test
   void viewsheetEmbeddedListLabelsAndValues() throws Exception {
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssembly check = new CheckBoxVSAssembly(vs, "CheckBox1");
      ListData data = new ListData();
      data.setDataType(XSchema.STRING);
      data.setLabels(new String[] { TEXT, "plain" });
      data.setValues(new Object[] { "v]]>1", "v2" });
      ((CheckBoxVSAssemblyInfo) check.getVSAssemblyInfo()).setListData(data);
      vs.addAssembly(check);

      Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77850l"), vs);

      ListData dback = ((CheckBoxVSAssemblyInfo) back.getAssembly("CheckBox1")
         .getVSAssemblyInfo()).getListData();
      assertArrayEquals(new String[] { TEXT, "plain" }, dback.getLabels());
      assertArrayEquals(new Object[] { "v]]>1", "v2" }, dback.getValues());
   }

   @Test
   void viewsheetTabLabels() throws Exception {
      Viewsheet vs = new Viewsheet();
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      ((TabVSAssemblyInfo) tab.getVSAssemblyInfo()).setLabelsValue(new String[] { TEXT, "B" });
      vs.addAssembly(tab);

      Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77850tab"), vs);

      assertArrayEquals(new String[] { TEXT, "B" },
                        ((TabVSAssemblyInfo) back.getAssembly("Tab1").getVSAssemblyInfo())
                           .getLabelsValue());
   }

   @Test
   void viewsheetSubmitLabel() throws Exception {
      Viewsheet vs = new Viewsheet();
      SubmitVSAssembly submit = new SubmitVSAssembly(vs, "Submit1");
      ((SubmitVSAssemblyInfo) submit.getVSAssemblyInfo()).setLabelName(TEXT);
      vs.addAssembly(submit);

      Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77850s"), vs);

      assertEquals(TEXT, ((SubmitVSAssemblyInfo) back.getAssembly("Submit1")
         .getVSAssemblyInfo()).getLabelName());
   }

   @Test
   void viewsheetChartAxisTitle() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).getChartDescriptor()
         .getTitlesDescriptor().getXTitleDescriptor().setTitleValue(JS);
      vs.addAssembly(chart);

      Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77850ax"), vs);

      assertEquals(JS, ((ChartVSAssemblyInfo) back.getAssembly("Chart1").getVSAssemblyInfo())
         .getChartDescriptor().getTitlesDescriptor().getXTitleDescriptor().getTitleValue());
   }

   @Test
   void viewsheetChartBindingValues() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ChartVSAssemblyInfo chinfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue("d" + TEXT);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("m" + TEXT);
      agg.setSecondaryColumnValue("s" + TEXT);
      chinfo.getVSChartInfo().addXField(dim);
      chinfo.getVSChartInfo().addYField(agg);
      vs.addAssembly(chart);

      Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77850b"), vs);

      ChartVSAssemblyInfo chback =
         (ChartVSAssemblyInfo) back.getAssembly("Chart1").getVSAssemblyInfo();
      assertEquals("d" + TEXT,
                   ((VSDimensionRef) chback.getVSChartInfo().getXField(0)).getGroupColumnValue());
      VSAggregateRef aback = (VSAggregateRef) chback.getVSChartInfo().getYField(0);
      assertEquals("m" + TEXT, aback.getColumnValue());
      assertEquals("s" + TEXT, aback.getSecondaryColumnValue());
   }

   /**
    * The rvalue attribute of &lt;visible&gt; is set by a script ({@code Text1.visible = ...}
    * calls VSAssemblyInfo.setVisible(String)) and was written unescaped.
    */
   @Test
   void viewsheetVisibleRuntimeValueAttributeIsEscaped() throws Exception {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.getVSAssemblyInfo().setVisible("x<&\"y");
      vs.addAssembly(text);
      TextVSAssembly text2 = new TextVSAssembly(vs, "Text2");
      text2.getVSAssemblyInfo().setVisible("false");
      vs.addAssembly(text2);

      Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("vs77850r"), vs);

      assertNotNull(back.getAssembly("Text1"));
      // an ordinary runtime value is still restored
      assertFalse(back.getAssembly("Text2").getVSAssemblyInfo().isVisible());
   }

   @Test
   void worksheetColumnAliasCaptionDescriptionAndView() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T1");
      table.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER },
                                               new Object[][] { { "id" }, { 1 } }));
      ws.addAssembly(table);
      ColumnSelection columns = table.getColumnSelection(false);
      ColumnRef column = (ColumnRef) columns.getAttribute("id");
      column.setAlias("a" + TEXT);
      column.setCaption("c" + TEXT);
      column.setDescription("d" + TEXT);
      column.setView("v" + TEXT);
      table.setColumnSelection(columns, false);

      Worksheet back = (Worksheet) roundTrip(worksheetEntry("ws77850"), ws);
      ColumnRef cback = (ColumnRef) ((TableAssembly) back.getAssembly("T1"))
         .getColumnSelection(false).getAttribute("a" + TEXT);

      assertNotNull(cback, "column by alias");
      assertEquals("a" + TEXT, cback.getAlias());
      assertEquals("c" + TEXT, cback.getCaption());
      assertEquals("d" + TEXT, cback.getDescription());
      assertEquals("v" + TEXT, cback.getView());
   }

   @Test
   void logicalModelEntityModelAndAttributeDescriptions() throws Exception {
      XLogicalModel lm = new XLogicalModel("LM");
      lm.setDescription("m" + TEXT);
      XEntity entity = new XEntity("E");
      entity.setDescription("e" + TEXT);
      XAttribute attr = new XAttribute("A", "T", "C");
      attr.setDescription("a" + TEXT);
      entity.addAttribute(attr);
      lm.addEntity(entity);

      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.LOGIC_MODEL, "DS/LM", null, orgID());
      XLogicalModel back = (XLogicalModel) roundTrip(entry, lm);

      assertEquals("m" + TEXT, back.getDescription());
      assertEquals("e" + TEXT, back.getEntity("E").getDescription());
      assertEquals("a" + TEXT, back.getEntity("E").getAttribute("A").getDescription());
   }

   /**
    * A dashboard named or aliased with ]]> doesn't break the folder that holds it (folders are
    * not stored as XML), but every sheet that embeds, mirrors or depends on it writes its
    * AssetEntry, so those sheets could not be read back.
    */
   @Test
   void dependentViewsheetOfAssetNamedWithCdataEnd() throws Exception {
      AssetEntry dashboard = viewsheetEntry(NAME);
      dashboard.setAlias("alias " + NAME);
      AssetEntry base = worksheetEntry("base");
      base.setProperty("_description_", "d" + TEXT);

      Viewsheet vs = new Viewsheet();
      vs.setBaseEntry(base);
      vs.addOuterDependency(dashboard);

      Viewsheet back = (Viewsheet) roundTrip(viewsheetEntry("dependent77850"), vs);

      AssetEntry[] deps = back.getOuterDependencies();
      assertEquals(1, deps.length);
      assertEquals(NAME, deps[0].getName());
      assertEquals(dashboard.toIdentifier(), deps[0].toIdentifier());
      assertEquals("alias " + NAME, deps[0].getAlias());
      assertEquals("d" + TEXT, back.getBaseEntry().getProperty("_description_"));
   }

   @Test
   void assetFolderHoldingAssetNamedWithCdataEnd() throws Exception {
      AssetEntry dashboard = viewsheetEntry(NAME);
      dashboard.setAlias("alias " + NAME);
      AssetFolder folder = new AssetFolder();
      folder.addEntry(dashboard);
      folder.addEntry(viewsheetEntry("sibling"));

      // the legacy XML storage path (XMLIndexedStorage) writes the folder as XML
      AssetFolder back = new AssetFolder();
      back.parseXML(Tool.parseXML(new java.io.ByteArrayInputStream(
         AbstractIndexedStorage.encodeXMLSerializable(folder, "1^1^__NULL__^/^" + orgID())),
                                  "UTF-8", false, false).getDocumentElement());

      assertTrue(back.containsEntry(dashboard));
      assertEquals("alias " + NAME, back.getEntry(dashboard).getAlias());
      assertTrue(back.containsEntry(viewsheetEntry("sibling")));
   }

   @Test
   void ordinaryTextIsWrittenUnchanged() {
      AssetEntry entry = viewsheetEntry("Sales [Q1] > 0");
      entry.setAlias("a[b] > c");
      String xml = xml(entry::writeXML);
      assertTrue(xml.contains("<path><![CDATA[Sales [Q1] > 0]]></path>"), xml);
      assertTrue(xml.contains("<alias><![CDATA[a[b] > c]]></alias>"), xml);

      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setVisibleValue("=a[b[0]] > 1");
      info.setVisible("false");
      xml = xml(info::writeXML);
      assertTrue(xml.contains("rvalue=\"false\"><![CDATA[=a[b[0]] > 1]]></visible>"), xml);
      assertFalse(xml.contains("]]]]><![CDATA[>"), xml);
   }

   private XMLSerializable roundTrip(AssetEntry entry, XMLSerializable obj) throws Exception {
      String key = entry.toIdentifier();
      keys.add(key);
      storage.putXMLSerializable(key, obj);
      XMLSerializable back = storage.getXMLSerializable(key, null, orgID());
      assertNotNull(back);
      return back;
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
