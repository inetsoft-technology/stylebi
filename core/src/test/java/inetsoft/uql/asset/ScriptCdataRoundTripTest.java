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
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77824: script, expression, formula and description text holding {@code ]]>} (for
 * example {@code a[i[0]]>1}) was written raw into CDATA, and an expression attribute
 * description was written with no CDATA or escaping at all, so the saved worksheet,
 * viewsheet or logical model could not be read back. Each case writes the whole asset with
 * the storage writer ({@link AbstractIndexedStorage#encodeXMLSerializable}), reads it with
 * the storage reader ({@code Tool.parseXML(in, "UTF-8", false, false)}, as
 * AbstractIndexedStorage.parseData does) and parses it with the asset's parseXML.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptCdataRoundTripTest {
   private static final String JS = "a[i[0]]>1";
   private static final String TEXT = "x ]]> y ]]>]]> z";

   @Test
   void worksheetExpressionColumnDescriptionAndExpressionCondition() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = embedded(ws);
      expression(table, "calc", JS);
      table.getTableInfo().setDescription(TEXT);

      ExpressionValue value = new ExpressionValue();
      value.setType(ExpressionValue.JAVASCRIPT);
      value.setExpression(JS + " ? 1 : 0");
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.setType(XSchema.INTEGER);
      condition.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(table.getColumnSelection(false).getAttribute("id"),
                                    condition, 0));
      table.setPreConditionList(list);

      Worksheet back = new Worksheet();
      back.parseXML(storageRoundTrip(ws, "1^2^__NULL__^ws1"));
      EmbeddedTableAssembly t2 = (EmbeddedTableAssembly) back.getAssembly("T1");

      assertEquals(JS, expressionOf(t2, "calc"));
      assertEquals(TEXT, t2.getTableInfo().getDescription());
      ConditionItem item = (ConditionItem) t2.getPreConditionList().getItem(0);
      ExpressionValue backValue =
         (ExpressionValue) ((AssetCondition) item.getXCondition()).getValue(0);
      assertEquals(JS + " ? 1 : 0", backValue.getExpression());
   }

   @Test
   void viewsheetScriptsTextDescriptionCalcFieldAndCalcCells() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setOnInit("var x = " + JS + ";");
      vs.getViewsheetInfo().setOnLoad("var y = " + JS + ";");

      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      TextVSAssemblyInfo tinfo = (TextVSAssemblyInfo) text.getVSAssemblyInfo();
      tinfo.setScript("if(" + JS + ") { Text1.text = 'x'; }");
      tinfo.setOnClick("if(" + JS + ") { alert(1); }");
      tinfo.setTextValue("=" + JS);
      tinfo.setDescription(TEXT);
      vs.addAssembly(text);

      CalculateRef cref = new CalculateRef(false);
      ExpressionRef eref = new ExpressionRef(null, "calc1");
      eref.setExpression(JS);
      cref.setDataRef(eref);
      vs.addCalcField("T1", cref);

      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(calc);
      calc.getTableLayout().setCellBinding(0, 0, new TableCellBinding(CellBinding.BIND_FORMULA, JS));
      TableCellBinding agg = new TableCellBinding(CellBinding.BIND_COLUMN, "col");
      agg.setExpression("sum(" + JS + ")");
      calc.getTableLayout().setCellBinding(0, 1, agg);

      Viewsheet back = new Viewsheet();
      back.parseXML(storageRoundTrip(vs, "1^128^__NULL__^vs1"));

      assertEquals("var x = " + JS + ";", back.getViewsheetInfo().getOnInit());
      assertEquals("var y = " + JS + ";", back.getViewsheetInfo().getOnLoad());
      TextVSAssemblyInfo tback =
         (TextVSAssemblyInfo) ((TextVSAssembly) back.getAssembly("Text1")).getVSAssemblyInfo();
      assertEquals("if(" + JS + ") { Text1.text = 'x'; }", tback.getScript());
      assertEquals("if(" + JS + ") { alert(1); }", tback.getOnClick());
      assertEquals("=" + JS, tback.getTextValue());
      assertEquals(TEXT, tback.getDescription());
      assertEquals(JS, ((ExpressionRef) back.getCalcField("T1", "calc1").getDataRef())
         .getExpression());
      CalcTableVSAssembly cback = (CalcTableVSAssembly) back.getAssembly("Calc1");
      assertEquals(JS, cback.getTableLayout().getCellBinding(0, 0).getValue());
      assertEquals("sum(" + JS + ")",
                   ((TableCellBinding) cback.getTableLayout().getCellBinding(0, 1)).getExpression());
   }

   @Test
   void logicalModelExpressionAttribute() throws Exception {
      for(String desc : new String[] { "Sales & Returns", "x<y", TEXT, "a & b < c ]]> d" }) {
         XLogicalModel lm = new XLogicalModel("LM");
         XEntity entity = new XEntity("E");
         ExpressionAttribute attr = new ExpressionAttribute("A", "case when " + JS + " then 1 end");
         attr.setDescription(desc);
         entity.addAttribute(attr);
         lm.addEntity(entity);

         XLogicalModel back = new XLogicalModel();
         back.parseXML(storageRoundTrip(lm, "1^4^__NULL__^DS^LM"));
         ExpressionAttribute aback = (ExpressionAttribute) back.getEntity("E").getAttribute("A");

         assertEquals("case when " + JS + " then 1 end", aback.getExpression(), desc);
         assertEquals(desc, aback.getDescription());
      }
   }

   @Test
   void expressionAttributeOrdinaryAndOldFormatDescriptions() throws Exception {
      ExpressionAttribute attr = new ExpressionAttribute("A", "col1 + 1");
      String xml = xml(attr::writeXML);

      // no description is written as before
      assertTrue(xml.contains("<description></description>"), xml);
      assertTrue(xml.contains("<expr><![CDATA[col1 + 1]]></expr>"), xml);

      // a description written by the old writer (no CDATA) still reads the same
      String old = "<attribute class=\"inetsoft.uql.erm.ExpressionAttribute\" name=\"A\" " +
         "type=\"string\" browse=\"true\" refType=\"0\" parseable=\"true\" aggregate=\"false\">" +
         "<description>Total sales per region</description>" +
         "<expr><![CDATA[col1 + 1]]></expr></attribute>";
      ExpressionAttribute back = new ExpressionAttribute();
      back.parseXML(Tool.parseXML(new ByteArrayInputStream(old.getBytes(StandardCharsets.UTF_8)),
                                  "UTF-8", false, false).getDocumentElement());
      assertEquals("Total sales per region", back.getDescription());
      assertEquals("col1 + 1", back.getExpression());

      String empty = old.replace("Total sales per region", "");
      back = new ExpressionAttribute();
      back.parseXML(Tool.parseXML(new ByteArrayInputStream(empty.getBytes(StandardCharsets.UTF_8)),
                                  "UTF-8", false, false).getDocumentElement());
      assertNull(back.getDescription());
   }

   @Test
   void ordinaryTextIsWrittenUnchanged() {
      ExpressionRef ref = new ExpressionRef(null, "calc");
      ref.setExpression("a[i[0]] > 1");
      String xml = xml(ref::writeXML);

      assertTrue(xml.contains("<![CDATA[a[i[0]] > 1]]>"), xml);
      assertFalse(xml.contains("]]]]><![CDATA[>"), xml);
   }

   /**
    * A rename transform rewrites a CDATA node of the stored document with
    * DependencyTransformer.replaceElementCDATANode and saves the document with
    * XMLTool.writeAssets (as AbstractIndexedStorage.putDocument does). A new value holding
    * ]]> must not make the worksheet unreadable.
    */
   @Test
   void expressionSurvivesRenameTransformAndResave() throws Exception {
      String expr = JS + " ? field['Region'] : ''";
      String renamed = JS + " ? field['Area'] : ''";
      Worksheet ws = new Worksheet();
      expression(embedded(ws), "calc", expr);

      byte[] data = AbstractIndexedStorage.encodeXMLSerializable(ws, "1^2^__NULL__^ws1");
      Document doc = Tool.parseXML(new ByteArrayInputStream(data), "UTF-8", false, false);
      Element elem = findCdataOwner(doc.getDocumentElement(), expr);
      assertNotNull(elem, "expression element not found");

      DependencyTransformer.replaceElementCDATANode(elem, renamed);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      XMLTool.writeAssets(doc, out, Worksheet.class.getName(), "1^2^__NULL__^ws1");
      Document doc2 = Tool.parseXML(new ByteArrayInputStream(out.toByteArray()),
                                    "UTF-8", false, false);

      Worksheet back = new Worksheet();
      back.parseXML(doc2.getDocumentElement());
      assertEquals(renamed, expressionOf((TableAssembly) back.getAssembly("T1"), "calc"));
   }

   @Test
   void xmlToolWritesOrdinaryCdataUnchanged() throws Exception {
      String xml = "<a><b><![CDATA[x < y & z]]></b></a>";
      Document doc = Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                                   "UTF-8", false, false);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      XMLTool.write(doc, out);

      assertTrue(out.toString(StandardCharsets.UTF_8).contains("<b><![CDATA[x < y & z]]></b>"),
                 out.toString(StandardCharsets.UTF_8));
   }

   private static Element storageRoundTrip(XMLSerializable obj, String key) throws Exception {
      byte[] data = AbstractIndexedStorage.encodeXMLSerializable(obj, key);
      assertNotNull(data);
      // the read AbstractIndexedStorage.parseData makes
      return Tool.parseXML(new ByteArrayInputStream(data), "UTF-8", false, false)
         .getDocumentElement();
   }

   private static EmbeddedTableAssembly embedded(Worksheet ws) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T1");
      table.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER },
                                               new Object[][] { { "id" }, { 1 } }));
      ws.addAssembly(table);
      return table;
   }

   private static void expression(TableAssembly table, String name, String script) {
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(script);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.INTEGER);
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
   }

   private static String expressionOf(TableAssembly table, String name) {
      ColumnRef column = (ColumnRef) table.getColumnSelection(false).getAttribute(name);
      assertNotNull(column, name);
      return ((ExpressionRef) column.getDataRef()).getExpression();
   }

   private static Element findCdataOwner(Element elem, String value) {
      NodeList children = elem.getChildNodes();
      boolean hasCdata = false;

      for(int i = 0; i < children.getLength(); i++) {
         hasCdata |= children.item(i).getNodeType() == Node.CDATA_SECTION_NODE;
      }

      if(hasCdata && value.equals(Tool.getValue(elem))) {
         return elem;
      }

      for(int i = 0; i < children.getLength(); i++) {
         if(children.item(i) instanceof Element child) {
            Element found = findCdataOwner(child, value);

            if(found != null) {
               return found;
            }
         }
      }

      return null;
   }

   private static String xml(java.util.function.Consumer<PrintWriter> writer) {
      StringWriter buf = new StringWriter();
      PrintWriter out = new PrintWriter(buf);
      writer.accept(out);
      out.flush();
      return buf.toString();
   }
}
