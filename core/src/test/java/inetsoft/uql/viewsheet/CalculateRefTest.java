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
package inetsoft.uql.viewsheet;

import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A viewsheet calculated field (Test Case #80957, vscalc). {@code baseOnDetail} tells a
 * detail (row-level) field from an aggregate one: it defaults to true and is written to XML
 * only when false, so a saved field without the attribute is a detail field. Datatest's
 * storage scan relies on that reading.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CalculateRefTest {
   @Test
   void defaultConstructor_isDetail() {
      assertTrue(new CalculateRef().isBaseOnDetail());

      CalculateRef ref = new CalculateRef();
      ref.setDataRef(new ExpressionRef(null, "calc"));
      assertEquals(0, ref.getRefType() & DataRef.AGG_CALC);
   }

   @Test
   void aggregate_addsTheAggregateCalcRefType() {
      CalculateRef ref = calc(false, "field['Sum([a])']");
      assertFalse(ref.isBaseOnDetail());
      assertEquals(DataRef.AGG_CALC, ref.getRefType() & DataRef.AGG_CALC);
   }

   @Test
   void writeXML_writesBaseOnDetailOnlyWhenFalse() {
      assertFalse(xml(calc(true, "field['a'] + 1")).contains("baseOnDetail"));
      assertTrue(xml(calc(false, "field['Sum([a])']")).contains("baseOnDetail=\"false\""));
   }

   @Test
   void xmlRoundTrip_keepsDetailAndAggregate() throws Exception {
      CalculateRef detail = (CalculateRef) roundTrip(calc(true, "field['a'] + 1"));
      CalculateRef aggregate = (CalculateRef) roundTrip(calc(false, "field['Sum([a])'] * 2"));

      assertTrue(detail.isBaseOnDetail());
      assertFalse(aggregate.isBaseOnDetail());
      assertEquals("field['a'] + 1", ((ExpressionRef) detail.getDataRef()).getExpression());
      assertEquals("field['Sum([a])'] * 2",
                   ((ExpressionRef) aggregate.getDataRef()).getExpression());
   }

   // an aggregate field marks its expression as computed on the aggregates
   // (CalcFieldFormula), after setDataRef and after parsing alike
   @Test
   void aggregate_marksTheExpressionOnAggregateAndVirtual() throws Exception {
      CalculateRef aggregate = calc(false, "field['Sum([a])']");
      ExpressionRef exp = (ExpressionRef) aggregate.getDataRef();
      assertTrue(exp.isOnAggregate());
      assertTrue(exp.isVirtual());
      assertEquals(XSchema.DOUBLE, exp.getDataType());

      ExpressionRef parsed = (ExpressionRef) ((CalculateRef) roundTrip(aggregate)).getDataRef();
      assertTrue(parsed.isOnAggregate());
      assertTrue(parsed.isVirtual());

      ExpressionRef detail = (ExpressionRef) calc(true, "field['a'] + 1").getDataRef();
      assertFalse(detail.isOnAggregate());
   }

   @Test
   void fakeAndDcRuntime_writtenOnlyWhenTrueAndRoundTrip() throws Exception {
      CalculateRef ref = calc(true, "field['a']");
      assertFalse(xml(ref).contains("fake="));
      assertFalse(xml(ref).contains("dcRuntime="));

      ref.setFake(true);
      ref.setDcRuntime(true);
      CalculateRef parsed = (CalculateRef) roundTrip(ref);
      assertTrue(parsed.isFake());
      assertTrue(parsed.isDcRuntime());
   }

   // strict equality also compares baseOnDetail; the loose one does not
   @Test
   void equals_strictComparesBaseOnDetail() {
      CalculateRef detail = calc(true, "field['a']");
      CalculateRef aggregate = calc(false, "field['a']");
      assertFalse(detail.equals(aggregate, true));
      assertTrue(detail.equals(aggregate, false));
      assertTrue(detail.equals(calc(true, "field['a']"), true));
   }

   @Test
   @DisplayName("Clone keeps the aggregate kind, the flags, the expression and a copy of " +
      "the data ref")
   void clone_keepsAggregateKindAndFlags() {
      CalculateRef ref = calc(false, "field['Sum([a])'] * 2");
      ref.setFake(true);
      ref.setDcRuntime(true);
      CalculateRef copy = (CalculateRef) ref.clone();

      assertNotSame(ref, copy);
      assertFalse(copy.isBaseOnDetail());
      assertTrue(copy.isFake());
      assertTrue(copy.isDcRuntime());
      assertEquals(XSchema.DOUBLE, copy.getDataType());
      assertNotSame(ref.getDataRef(), copy.getDataRef());
      assertEquals("field['Sum([a])'] * 2", ((ExpressionRef) copy.getDataRef()).getExpression());
      assertTrue(((ExpressionRef) copy.getDataRef()).isOnAggregate());
   }

   @Test
   @DisplayName("An expression with CDATA breakers and a name with a quote and non-BMP " +
      "characters survive the XML round trip")
   void xmlRoundTrip_cdataBreakersAndSpecialNames() throws Exception {
      String[] expressions = {
         "field['a'] + \"]]>\" + '<&>' + \"\\u0001\"",
         "field['a'] + ']]>' + '<![CDATA[' + 'z'",
         "'<&>'" };

      for(String expression : expressions) {
         CalculateRef ref = new CalculateRef(true);
         ExpressionRef exp = new ExpressionRef(null, "O'Brien \u00fc\uD83D\uDE00");
         exp.setExpression(expression);
         ref.setDataType(XSchema.STRING);
         ref.setDataRef(exp);
         ExpressionRef back = (ExpressionRef) ((CalculateRef) roundTrip(ref)).getDataRef();
         assertEquals(expression, back.getExpression(), expression);
         assertEquals("O'Brien \u00fc\uD83D\uDE00", back.getName());
      }
   }

   @Test
   @DisplayName("PIN(current behavior, pending dev #80953): a raw U+0001 character cannot " +
      "be written to XML 1.0 and does not come back")
   void pin_rawControlCharacterIsLost() throws Exception {
      CalculateRef ref = calc(true, "'x" + (char) 1 + "y'");
      ExpressionRef back = (ExpressionRef) ((CalculateRef) roundTrip(ref)).getDataRef();
      assertFalse(back.getExpression().indexOf((char) 1) >= 0);
      assertNotEquals("'x" + (char) 1 + "y'", back.getExpression());
   }

   @Test
   @DisplayName("The sql flag is written only when false and round trips")
   void sqlFlagRoundTrip() throws Exception {
      CalculateRef sql = calc(true, "field['a']");
      assertTrue(sql.isSQL());
      assertTrue(((CalculateRef) roundTrip(sql)).isSQL());

      CalculateRef script = calc(true, "field['a']");
      script.setSQL(false);
      assertFalse(((CalculateRef) roundTrip(script)).isSQL());
   }

   @Test
   @DisplayName("Equal refs have the same hash code")
   void equalRefs_sameHashCode() {
      assertEquals(calc(true, "field['a']").hashCode(), calc(true, "field['a']").hashCode());
      assertEquals(calc(false, "field['Sum([a])']"), calc(false, "field['Sum([a])']"));
      assertEquals(calc(false, "field['Sum([a])']").hashCode(),
                   calc(false, "field['Sum([a])']").hashCode());
   }

   @Test
   @DisplayName("A data type set on an aggregate ref reaches its expression")
   void aggregateDataType_reachesTheExpression() {
      CalculateRef ref = new CalculateRef(false);
      ExpressionRef exp = new ExpressionRef(null, "calc");
      exp.setExpression("field['Sum([a])']");
      ref.setDataType(XSchema.STRING);
      ref.setDataRef(exp);
      assertEquals(XSchema.STRING, ref.getDataType());
      assertEquals(XSchema.STRING, ((ExpressionRef) ref.getDataRef()).getDataType());
   }

   /** A calculated field named "calc" of type double over the given expression. */
   private static CalculateRef calc(boolean baseOnDetail, String expression) {
      CalculateRef ref = new CalculateRef(baseOnDetail);
      ExpressionRef exp = new ExpressionRef(null, "calc");
      exp.setExpression(expression);
      ref.setDataType(XSchema.DOUBLE);
      ref.setDataRef(exp);
      return ref;
   }

   private static String xml(DataRef ref) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      ref.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private static DataRef roundTrip(DataRef ref) throws Exception {
      Document doc = Tool.parseXML(new ByteArrayInputStream(
         xml(ref).getBytes(StandardCharsets.UTF_8)));
      return AbstractDataRef.createDataRef(doc.getDocumentElement());
   }
}
