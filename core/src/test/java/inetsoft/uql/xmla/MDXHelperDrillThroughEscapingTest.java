/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.xmla;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.XCondition;
import inetsoft.uql.XNode;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80104: the drill-through statement is pasted verbatim into the SOAP
 * &lt;Statement&gt; element, so the generator must XML-escape the whole statement exactly
 * once (cube name, measures and dimension members alike).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MDXHelperDrillThroughEscapingTest {
   /** Query whose filter node is supplied directly, avoiding a registered data source. */
   private static class TestQuery extends XMLAQuery {
      TestQuery(String cube, XNode where) {
         setCube(cube);
         this.where = where;
      }

      @Override
      public XNode getFilterNode() {
         return where;
      }

      private final XNode where;
   }

   private static XMLANode member(String dim, String unique, String value) {
      AttributeRef ref = new AttributeRef(dim, unique);
      Condition cond = new Condition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(value);
      XMLANode node = new XMLANode();
      node.setValue(new ConditionItem(ref, cond, 0));
      node.setAttribute("dimension", dim);
      return node;
   }

   private static XMLANode measure(String name) {
      AttributeRef ref = new AttributeRef("[Measures]", name);
      ref.setRefType(DataRef.MEASURE);
      Condition cond = new Condition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("1");
      XMLANode node = new XMLANode();
      node.setValue(new ConditionItem(ref, cond, 0));
      node.setAttribute("dimension", "[Measures]");
      return node;
   }

   private static XMLASet and(XNode a, XNode b) {
      XMLASet set = new XMLASet(XMLASet.AND);
      set.addChild(a, false, false);
      set.addChild(b, false, false);
      return set;
   }

   private static String[] generate(String cube) {
      XNode where = and(member("[Dim]", "[Dim].[Name]", "[Dim].[A&B]"),
                        measure("[Measures].[Sales & Cost]"));
      return new MDXHelper(new TestQuery(cube, where)).generateDrillThrough();
   }

   @Test
   void cubeMeasureAndMemberAreEscapedExactlyOnce() throws Exception {
      String[] stmts = generate("R&D <x>");
      assertEquals(1, stmts.length);
      String stmt = stmts[0];

      assertTrue(stmt.contains("FROM [R&amp;D &lt;x&gt;]"), stmt);
      assertTrue(stmt.contains("[Measures].[Sales &amp; Cost]"), stmt);
      assertTrue(stmt.contains("[Dim].[A&amp;B]"), stmt);
      assertFalse(stmt.contains("&amp;amp;"), stmt);

      // what the server sees after parsing the SOAP body is the true MDX
      String body = "<Statement>" + stmt + "</Statement>";
      String parsed = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement().getTextContent();
      assertTrue(parsed.contains("FROM [R&D <x>]"), parsed);
      assertTrue(parsed.contains("[Measures].[Sales & Cost]"), parsed);
      assertTrue(parsed.contains("[Dim].[A&B]"), parsed);
   }

   @Test
   void nameAlreadyContainingEntityIsEscapedOnce() {
      String stmt = generate("R&amp;D")[0];
      assertTrue(stmt.contains("FROM [R&amp;amp;D]"), stmt);
   }
}
