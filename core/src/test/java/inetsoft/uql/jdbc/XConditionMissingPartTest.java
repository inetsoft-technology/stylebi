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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.util.Tool;
import inetsoft.util.TransformerManager;
import inetsoft.util.dep.ImportedAssetProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77586, a condition leaf (XBinaryCondition, XJoin, XUnaryCondition, XTrinaryCondition)
 * whose XML lacks a required part loaded without error, then made the worksheet impossible to
 * save (writeXML NPE) or, through clone(), silently lost the whole WHERE. Such XML must be
 * refused when it is parsed, and a condition that is missing a part in memory must still
 * clone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XConditionMissingPartTest {
   // ---- the four fuzzer-found worksheets (Redmine attachments 116318-116321) ----

   /**
    * The seeds are not in {@code inetsoft/util/dep/asset-seeds}: {@code AssetSeedTest} calls
    * {@code ImportedAssetProperties.check(seed, true)}, which fails any seed that does not
    * parse, so it cannot express "must be refused". Here the refusal is asserted directly, and
    * the fuzzer mode of the same harness ({@code requireParse=false}) must accept the seed as a
    * rejected input instead of failing on the write.
    */
   @ParameterizedTest(name = "{0}")
   @MethodSource("seeds")
   void attachmentIsRefusedOnImport(String name, byte[] seed) throws Exception {
      String tag = name.startsWith("min-XJoin") ? "XJoin" : name.substring(0, name.indexOf('-'));
      Exception ex = assertThrows(Exception.class, () -> parseWorksheet(seed), name);
      assertEquals(tag + " is missing <expression1>", ex.getMessage(), name);
      assertTrue(ImportedAssetProperties.check(seed, false), name);
   }

   static Stream<Arguments> seeds() throws Exception {
      return SeedCorpus.load(XConditionMissingPartTest.class, "missing-condition-part-seeds");
   }

   // parse a worksheet entry the way import does (AbstractSheetAsset.parseContent)
   private static void parseWorksheet(byte[] content) throws Exception {
      Document doc = Tool.parseXML(new ByteArrayInputStream(content));
      TransformerManager.getManager(TransformerManager.WORKSHEET).transform(doc);
      new Worksheet().parseXML(doc.getDocumentElement(), false);
   }

   // ---- XBinaryCondition and XJoin ----

   @Test
   void binaryCompleteParses() throws Exception {
      XBinaryCondition cond = parse(new XBinaryCondition(), binary("XBinaryCondition",
         EXP1 + EXP2 + OP_EQ));
      assertEquals("t.a", cond.getExpression1().getValue());
      assertEquals("1", cond.getExpression2().getValue());
      assertEquals("=", cond.getOp());
   }

   @Test
   void binaryMissingPartIsRefused() {
      for(String tag : new String[] { "XBinaryCondition", "XJoin" }) {
         assertRefused(newBinary(tag), binary(tag, EXP2 + OP_EQ), tag, "expression1");
         assertRefused(newBinary(tag), binary(tag, EXP1 + OP_EQ), tag, "expression2");
         assertRefused(newBinary(tag), binary(tag, EXP1 + EXP2), tag, "op");
         // an <expression1> without its <expression> child is the same missing operand
         assertRefused(newBinary(tag), binary(tag, "<expression1></expression1>" + EXP2 + OP_EQ),
                       tag, "expression1");
         assertRefused(newBinary(tag), binary(tag, ""), tag, "expression1");
      }
   }

   @Test
   void binaryEmptyValuesStillParse() throws Exception {
      for(String tag : new String[] { "XBinaryCondition", "XJoin" }) {
         XBinaryCondition cond = parse(newBinary(tag), binary(tag,
            "<expression1>" + EMPTY_EXP + "</expression1>" +
            "<expression2>" + EMPTY_EXP + "</expression2>" + OP_EQ));
         assertEquals("", cond.getExpression1().getValue(), tag);
         assertEquals("", cond.getExpression2().getValue(), tag);

         // writeXML() writes a null op as the text "null", which must keep loading
         cond = parse(newBinary(tag), binary(tag, EXP1 + EXP2 + OP_NULL));
         assertEquals("null", cond.getOp(), tag);
      }
   }

   // ---- XUnaryCondition ----

   @Test
   void unaryMissingExpressionIsRefused() {
      assertRefused(new XUnaryCondition(), unary(OP_NULL_CHECK), "XUnaryCondition", "expression1");
      assertRefused(new XUnaryCondition(), unary("<expression1></expression1>" + OP_NULL_CHECK),
                    "XUnaryCondition", "expression1");
   }

   @Test
   void unaryEmptyValuesAndMissingOpStillParse() throws Exception {
      XUnaryCondition cond = parse(new XUnaryCondition(), unary(EXP1 + OP_NULL_CHECK));
      assertEquals("t.a", cond.getExpression1().getValue());

      cond = parse(new XUnaryCondition(),
                   unary("<expression1>" + EMPTY_EXP + "</expression1>" + OP_NULL_CHECK));
      assertEquals("", cond.getExpression1().getValue());

      // a missing <op> is not refused, the operator defaults to "" (as IS TRUE writes it)
      cond = parse(new XUnaryCondition(), unary(EXP1));
      assertEquals("", cond.getOp());

      // the stored text "null" still loads (setOp maps the catalog name "null" to IS NULL)
      cond = parse(new XUnaryCondition(), unary(EXP1 + OP_NULL));
      assertNotNull(cond.getOp());
   }

   // ---- XTrinaryCondition ----

   @Test
   void trinaryMissingPartIsRefused() {
      assertRefused(new XTrinaryCondition(), trinary(EXP2 + EXP3 + OP_BETWEEN),
                    "XTrinaryCondition", "expression1");
      assertRefused(new XTrinaryCondition(), trinary(EXP1 + EXP3 + OP_BETWEEN),
                    "XTrinaryCondition", "expression2");
      assertRefused(new XTrinaryCondition(), trinary(EXP1 + EXP2 + OP_BETWEEN),
                    "XTrinaryCondition", "expression3");
      assertRefused(new XTrinaryCondition(), trinary(EXP1 + EXP2 + EXP3),
                    "XTrinaryCondition", "op");
      assertRefused(new XTrinaryCondition(), trinary(""), "XTrinaryCondition", "expression1");
   }

   @Test
   void trinaryCompleteAndEmptyValuesStillParse() throws Exception {
      XTrinaryCondition cond = parse(new XTrinaryCondition(),
                                     trinary(EXP1 + EXP2 + EXP3 + OP_BETWEEN));
      assertEquals("2", cond.getExpression3().getValue());
      assertEquals("BETWEEN", cond.getOp());

      cond = parse(new XTrinaryCondition(), trinary(
         "<expression1>" + EMPTY_EXP + "</expression1>" +
         "<expression2>" + EMPTY_EXP + "</expression2>" +
         "<expression3>" + EMPTY_EXP + "</expression3>" + OP_BETWEEN));
      assertEquals("", cond.getExpression3().getValue());

      cond = parse(new XTrinaryCondition(), trinary(EXP1 + EXP2 + EXP3 + OP_NULL));
      assertEquals("null", cond.getOp());
   }

   // ---- the same refusal through the WHERE of a stored query ----

   @Test
   void nestedIncompleteConditionRefusesTheQuery() throws Exception {
      UniformSQL sql = parseSql("select t.a from t where t.a = 1 and t.b = 2");
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      // drop the <op> of the second conjunct only
      String xml = buffer.toString();
      int second = xml.lastIndexOf("<op>");
      xml = xml.substring(0, second) + xml.substring(xml.indexOf("</op>", second) + 5);
      Element elem = Tool.parseXML(new StringReader(xml)).getDocumentElement();

      Exception ex = assertThrows(Exception.class, () -> new UniformSQL().parseXML(elem));
      assertEquals("XBinaryCondition is missing <op>", ex.getMessage());
   }

   // ---- complete conditions keep loading and writing the same XML ----

   /**
    * The refusal must not change how a complete condition, or one with an empty value or a
    * stored {@code null} op, loads: writing it, parsing that output and writing again, and
    * writing a clone of it, all give the same XML.
    */
   @ParameterizedTest(name = "{0}")
   @MethodSource("completeConditions")
   void completeConditionRoundTripsUnchanged(String name, String xml) throws Exception {
      XFilterNode node = XFilterNode.createConditionNode(
         Tool.parseXML(new StringReader(xml)).getDocumentElement());
      String written = write(node);
      XFilterNode reloaded = XFilterNode.createConditionNode(
         Tool.parseXML(new StringReader(written)).getDocumentElement());

      assertEquals(written, write(reloaded), name);
      assertEquals(written, write((XFilterNode) node.clone()), name);
   }

   static Stream<Arguments> completeConditions() {
      String fieldExp2 =
         "<expression2><expression type=\"Field\"><![CDATA[u.b]]></expression></expression2>";
      String emptyExp1 = "<expression1>" + EMPTY_EXP + "</expression1>";
      String emptyExp2 = "<expression2>" + EMPTY_EXP + "</expression2>";
      String emptyExp3 = "<expression3>" + EMPTY_EXP + "</expression3>";

      return Stream.of(
         Arguments.of("binary", binary("XBinaryCondition", EXP1 + EXP2 + OP_EQ)),
         Arguments.of("binary null op", binary("XBinaryCondition", EXP1 + EXP2 + OP_NULL)),
         Arguments.of("binary empty values",
                      binary("XBinaryCondition", emptyExp1 + emptyExp2 + OP_EQ)),
         Arguments.of("join", binary("XJoin", EXP1 + fieldExp2 + OP_EQ)),
         Arguments.of("join null op", binary("XJoin", EXP1 + fieldExp2 + OP_NULL)),
         Arguments.of("unary", unary(EXP1 + OP_NULL_CHECK)),
         Arguments.of("unary empty value", unary(emptyExp1 + OP_NULL)),
         Arguments.of("trinary", trinary(EXP1 + EXP2 + EXP3 + OP_BETWEEN)),
         Arguments.of("trinary null op", trinary(EXP1 + EXP2 + EXP3 + OP_NULL)),
         Arguments.of("trinary empty values",
                      trinary(emptyExp1 + emptyExp2 + emptyExp3 + OP_BETWEEN)));
   }

   // ---- VPM conditions parse through the same classes ----

   @Test
   void vpmConditionLoadsCompleteAndRefusesIncomplete() throws Exception {
      String set = "<XSet relation=\"and\" isnot=\"false\">" +
         binary("XBinaryCondition", EXP1 + EXP2 + OP_EQ) +
         binary("XJoin", EXP1 + EXP2 + OP_EQ) + unary(EXP1 + OP_NULL_CHECK) +
         trinary(EXP1 + EXP2 + EXP3 + OP_BETWEEN) + "</XSet>";
      VpmCondition vpm = new VpmCondition("c1");
      vpm.setTable("t");
      vpm.setCondition(
         XFilterNode.createConditionNode(Tool.parseXML(new StringReader(set)).getDocumentElement()));
      String written = writeVpm(vpm);

      VpmCondition loaded = new VpmCondition();
      loaded.parseXML(Tool.parseXML(new StringReader(written)).getDocumentElement());
      assertEquals(written, writeVpm(loaded));
      assertEquals(4, leaves(loaded.getCondition()).size());

      // drop the first <expression2>, which belongs to the XBinaryCondition
      int start = written.indexOf("<expression2>");
      String broken = written.substring(0, start) +
         written.substring(written.indexOf("</expression2>", start) + "</expression2>".length());
      Element elem = Tool.parseXML(new StringReader(broken)).getDocumentElement();
      Exception ex = assertThrows(
         Exception.class, () -> new VpmCondition().parseXML(elem));
      assertEquals("XBinaryCondition is missing <expression2>", ex.getMessage());
   }

   private static String write(XFilterNode node) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         node.writeXML(writer);
      }

      return buffer.toString();
   }

   private static String writeVpm(VpmCondition vpm) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         vpm.writeXML(writer);
      }

      return buffer.toString();
   }

   // ---- clone() of a condition that lacks a part in memory ----

   @Test
   void conditionMissingAnExpressionClones() {
      XBinaryCondition binary = new XBinaryCondition(field("t.a"), null, "=");
      XBinaryCondition binaryClone = (XBinaryCondition) binary.clone();
      assertNotNull(binaryClone);
      assertNotSame(binary.getExpression1(), binaryClone.getExpression1());
      assertNull(binaryClone.getExpression2());

      XJoin join = new XJoin(field("t.a"), field("u.b"), "=");
      join.setExpression1(null);
      XJoin joinClone = (XJoin) join.clone();
      assertNotNull(joinClone);
      assertNull(joinClone.getExpression1());
      assertEquals("u.b", joinClone.getExpression2().getValue());

      XTrinaryCondition trinary = new XTrinaryCondition(field("t.a"), value("1"), null, "between");
      XTrinaryCondition trinaryClone = (XTrinaryCondition) trinary.clone();
      assertNotNull(trinaryClone);
      assertEquals("1", trinaryClone.getExpression2().getValue());
      assertNull(trinaryClone.getExpression3());
   }

   /**
    * Composer opens a stored worksheet as a {@code Worksheet.clone()}
    * ({@code AbstractAssetEngine.getSheet} with {@code AssetContent.ALL}). A null clone of one
    * leaf used to null the whole XSet, so the clone's query had no WHERE at all, valid
    * conjuncts included, and saving persisted that.
    */
   @Test
   void worksheetCloneKeepsWhereWithIncompleteBinary() throws Exception {
      assertWorksheetCloneKeepsWhere("select t.a from t where t.a = 1 and t.b = 2",
         XBinaryCondition.class, c -> ((XBinaryCondition) c).setExpression2(null));
   }

   @Test
   void worksheetCloneKeepsWhereWithIncompleteJoin() throws Exception {
      assertWorksheetCloneKeepsWhere("select t.a from t, u where t.a = 1 and t.x = u.y",
         XJoin.class, c -> ((XJoin) c).setExpression2(null));
   }

   @Test
   void worksheetCloneKeepsWhereWithIncompleteTrinary() throws Exception {
      assertWorksheetCloneKeepsWhere("select t.a from t where t.a = 1 and t.b between 1 and 2",
         XTrinaryCondition.class, c -> ((XTrinaryCondition) c).setExpression3(null));
   }

   private static void assertWorksheetCloneKeepsWhere(String text, Class<?> type,
                                                      Consumer<XFilterNode> breakIt)
      throws Exception
   {
      UniformSQL sql = parseSql(text);
      XFilterNode where = sql.getWhere();
      List<XFilterNode> leaves = leaves(where);
      assertEquals(2, leaves.size(), text + ": " + where);
      XFilterNode last = leaves.get(1);
      assertInstanceOf(type, last, text);
      breakIt.accept(last);

      JDBCQuery query = new JDBCQuery();
      query.setName("q");
      query.setSQLDefinition(sql);
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "t1");
      ((SQLBoundTableAssemblyInfo) table.getTableInfo()).setQuery(query);
      ws.addAssembly(table);

      Worksheet copy = (Worksheet) ws.clone();
      SQLBoundTableAssembly copyTable = (SQLBoundTableAssembly) copy.getAssembly("t1");
      UniformSQL copySql = (UniformSQL) ((SQLBoundTableAssemblyInfo) copyTable.getTableInfo())
         .getQuery().getSQLDefinition();

      XFilterNode copyWhere = copySql.getWhere();
      assertNotNull(copyWhere, text);
      assertNotSame(where, copyWhere, text);
      List<XFilterNode> copyLeaves = leaves(copyWhere);
      assertEquals(2, copyLeaves.size(), text);
      assertEquals(leaves.get(0).toString(), copyLeaves.get(0).toString(), text);
      assertInstanceOf(type, copyLeaves.get(1), text);
   }

   // the condition leaves of a WHERE tree, in order
   private static List<XFilterNode> leaves(XNode node) {
      List<XFilterNode> list = new ArrayList<>();

      if(node instanceof XSet) {
         for(int i = 0; i < node.getChildCount(); i++) {
            list.addAll(leaves(node.getChild(i)));
         }
      }
      else if(node != null) {
         list.add((XFilterNode) node);
      }

      return list;
   }

   // ---- helpers ----

   private static UniformSQL parseSql(String text) {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      return sql;
   }

   private static XBinaryCondition newBinary(String tag) {
      return "XJoin".equals(tag) ? new XJoin() : new XBinaryCondition();
   }

   private static <T extends XFilterNode> T parse(T node, String xml) throws Exception {
      node.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return node;
   }

   private static void assertRefused(XFilterNode node, String xml, String tag, String element) {
      Exception ex = assertThrows(Exception.class, () -> parse(node, xml), xml);
      assertEquals(tag + " is missing <" + element + ">", ex.getMessage(), xml);
   }

   private static String binary(String tag, String body) {
      return "<" + tag + " isnot=\"false\" clause=\"\" containsNull=\"false\">" + body +
         "</" + tag + ">";
   }

   private static String unary(String body) {
      return "<XUnaryCondition isnot=\"false\" clause=\"\">" + body + "</XUnaryCondition>";
   }

   private static String trinary(String body) {
      return "<XTrinaryCondition isnot=\"false\" clause=\"\">" + body + "</XTrinaryCondition>";
   }

   private static XExpression field(String name) {
      return new XExpression(name, XExpression.FIELD);
   }

   private static XExpression value(String value) {
      return new XExpression(value, XExpression.VALUE);
   }

   private static final String EXP1 =
      "<expression1><expression type=\"Field\"><![CDATA[t.a]]></expression></expression1>";
   private static final String EXP2 =
      "<expression2><expression type=\"Value\"><![CDATA[1]]></expression></expression2>";
   private static final String EXP3 =
      "<expression3><expression type=\"Value\"><![CDATA[2]]></expression></expression3>";
   private static final String EMPTY_EXP = "<expression type=\"Expression\"><![CDATA[]]></expression>";
   private static final String OP_EQ = "<op><![CDATA[=]]></op>";
   private static final String OP_NULL = "<op><![CDATA[null]]></op>";
   private static final String OP_NULL_CHECK = "<op><![CDATA[is null]]></op>";
   private static final String OP_BETWEEN = "<op><![CDATA[between]]></op>";
}
