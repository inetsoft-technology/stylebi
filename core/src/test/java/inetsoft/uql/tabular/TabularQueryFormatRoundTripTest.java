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
package inetsoft.uql.tabular;

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79608: {@code TabularQuery.parseContents} read {@code <columnFormatExtent>} into the
 * format-type map, so a saved and reloaded query lost its format extent and its format type
 * became the pattern. The round trip here is write XML, parse XML (an in-session clone hides it).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TabularQueryFormatRoundTripTest {
   static final class TestQuery extends TabularQuery {
      TestQuery() {
         super("test");
      }

      @Override
      public XTypeNode getOutputType(Object session, boolean full) {
         return null;
      }

      @Override
      public XSelection getSelection() {
         return null;
      }
   }

   /** Reaches the protected {@code transform}. */
   static final class TestRuntime extends TabularRuntime {
      @Override
      public inetsoft.uql.XTableNode runQuery(TabularQuery query, VariableTable params) {
         return null;
      }

      @Override
      public void testDataSource(TabularDataSource<?> ds, VariableTable params) {
      }

      Object convert(TabularQuery query, String header, Object value) {
         return transform(query, header, value);
      }
   }

   private static TestQuery roundTrip(TestQuery query) throws Exception {
      StringWriter sw = new StringWriter();
      PrintWriter writer = new PrintWriter(sw);
      query.writeXML(writer);
      writer.flush();
      Document doc = Tool.parseXML(new java.io.StringReader(sw.toString()));
      TestQuery loaded = new TestQuery();
      loaded.parseXML(doc.getDocumentElement());
      return loaded;
   }

   private static TestQuery createQuery() {
      TestQuery query = new TestQuery();
      query.setName("q");
      query.setColumnType("d", "date");
      query.setColumnFormat("d", "DateFormat");
      query.setColumnFormatExtent("d", "dd/MM/yyyy");
      query.setColumnType("n", "double");
      query.setColumnFormat("n", "DecimalFormat");
      query.setColumnFormatExtent("n", "#,##0.00");
      query.setColumnType("p", "double");
      query.setColumnFormat("p", "PercentFormat");
      query.setColumnType("e", "date");
      query.setColumnFormatExtent("e", "dd/MM/yyyy");
      return query;
   }

   @Test
   void formatAndExtentSurviveRoundTrip() throws Exception {
      TestQuery loaded = roundTrip(createQuery());

      assertEquals("DateFormat", loaded.getColumnFormat("d"));
      assertEquals("dd/MM/yyyy", loaded.getColumnFormatExtent("d"));
      assertEquals("DecimalFormat", loaded.getColumnFormat("n"));
      assertEquals("#,##0.00", loaded.getColumnFormatExtent("n"));
   }

   @Test
   void formatWithoutExtentSurvivesRoundTrip() throws Exception {
      TestQuery loaded = roundTrip(createQuery());

      assertEquals("PercentFormat", loaded.getColumnFormat("p"));
      assertNull(loaded.getColumnFormatExtent("p"));
   }

   @Test
   void extentWithoutFormatDoesNotInventFormat() throws Exception {
      TestQuery loaded = roundTrip(createQuery());

      assertNull(loaded.getColumnFormat("e"));
      assertEquals("dd/MM/yyyy", loaded.getColumnFormatExtent("e"));
   }

   @Test
   void secondRoundTripIsStable() throws Exception {
      TestQuery loaded = roundTrip(roundTrip(createQuery()));

      assertEquals("DateFormat", loaded.getColumnFormat("d"));
      assertEquals("dd/MM/yyyy", loaded.getColumnFormatExtent("d"));
   }

   @Test
   void transformParsesDateAfterRoundTrip() throws Exception {
      TestRuntime runtime = new TestRuntime();
      TestQuery original = createQuery();
      Object before = runtime.convert(original, "d", "31/12/2020");
      Object after = runtime.convert(roundTrip(original), "d", "31/12/2020");

      assertNotNull(before);
      assertInstanceOf(java.util.Date.class, before);
      assertEquals(before, after);
   }
}
