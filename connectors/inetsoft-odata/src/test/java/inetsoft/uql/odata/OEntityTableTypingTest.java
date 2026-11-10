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
package inetsoft.uql.odata;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.TabularColumn;
import inetsoft.uql.tabular.TabularDatasetSchema;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.commons.api.format.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80961: the table built from an OData result types a date/time column from the value the
 * server sends, which is ISO text, so a column the described schema calls a date was built as a
 * string. A query typed by {@code $metadata} (the params {@code describeDataset} returns) types
 * the date/time columns from the schema instead, and also has every column the schema describes;
 * every other query builds exactly as before. The payloads are the untyped JSON the build gets
 * for an untyped Accept, deserialized by the same Olingo client the build uses.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class OEntityTableTypingTest {
   private static ClientEntitySet entitySet(String entities) throws Exception {
      String json = "{\"@odata.context\":\"http://localhost/svc/$metadata#Set\",\"value\":[" +
         entities + "]}";
      ODataClient client = ODataClientFactory.getClient();
      return client.getBinder().getODataEntitySet(client.getDeserializer(ContentType.JSON)
         .toEntitySet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
   }

   private static TabularDatasetSchema schema(TabularColumn... columns) {
      return new TabularDatasetSchema("Set", List.of(columns), List.of(),
                                      Map.of("entity", "Set", "typedBy", "metadata"));
   }

   private static List<String> names(OEntityTable table) {
      List<String> names = new ArrayList<>();

      for(int i = 0; i < table.getColCount(); i++) {
         names.add(table.getName(i));
      }

      return names;
   }

   private static int col(OEntityTable table, String name) {
      int col = names(table).indexOf(name);
      assertTrue(col >= 0, "no column " + name + " in " + names(table));
      return col;
   }

   private static List<Object> column(OEntityTable table, String name) {
      int col = col(table, name);
      List<Object> values = new ArrayList<>();
      table.rewind();

      while(table.next()) {
         values.add(table.getObject(col));
      }

      return values;
   }

   private static final String PRODUCTS =
      "{\"ID\":0,\"ReleaseDate\":\"1992-01-01T00:00:00Z\",\"DiscontinuedDate\":null}," +
      "{\"ID\":1,\"ReleaseDate\":\"2006-08-01T00:00:00+02:00\"," +
      "\"DiscontinuedDate\":\"2007-03-15T00:00:00Z\"}";

   private static final TabularDatasetSchema PRODUCT_SCHEMA =
      schema(new TabularColumn("ID", XSchema.LONG),
             new TabularColumn("ReleaseDate", XSchema.TIME_INSTANT),
             new TabularColumn("DiscontinuedDate", XSchema.TIME_INSTANT));

   @Test
   void queryWithoutTheFlagBuildsDatesAsText() throws Exception {
      OEntityTable table = new OEntityTable(entitySet(PRODUCTS), new ODataQuery());

      assertEquals(String.class, table.getType(col(table, "ReleaseDate")));
      assertEquals(String.class, table.getType(col(table, "DiscontinuedDate")));
      assertEquals(List.of("1992-01-01T00:00:00Z", "2006-08-01T00:00:00+02:00"),
                   column(table, "ReleaseDate"));
      // the pre-existing text behaviour: a null cell is the empty string
      assertEquals(List.of("", "2007-03-15T00:00:00Z"), column(table, "DiscontinuedDate"));
   }

   @Test
   void typedQueryBuildsDatesFromTheSchemaAndKeepsNulls() throws Exception {
      OEntityTable table =
         new OEntityTable(entitySet(PRODUCTS), new ODataQuery(), PRODUCT_SCHEMA);

      assertEquals(Timestamp.class, table.getType(col(table, "ReleaseDate")));
      assertEquals(Timestamp.class, table.getType(col(table, "DiscontinuedDate")));
      // the offset of the second value is applied
      assertEquals(List.of(Timestamp.from(java.time.Instant.parse("1992-01-01T00:00:00Z")),
                           Timestamp.from(java.time.Instant.parse("2006-07-31T22:00:00Z"))),
                   column(table, "ReleaseDate"));
      // the first row is null: the column is still a date column and the null stays null
      List<Object> discontinued = column(table, "DiscontinuedDate");
      assertNull(discontinued.get(0));
      assertEquals(Timestamp.class, discontinued.get(1).getClass());
      // a column the schema does not type as date/time is untouched
      assertEquals(Integer.class, table.getType(col(table, "ID")));
   }

   @Test
   void dateAndTimeOfDayColumnsAreTypedToo() throws Exception {
      OEntityTable table = new OEntityTable(
         entitySet("{\"Opened\":\"2020-03-04\",\"At\":\"13:45:10\"}"), new ODataQuery(),
         schema(new TabularColumn("Opened", XSchema.DATE), new TabularColumn("At", XSchema.TIME)));

      assertEquals(Date.class, table.getType(col(table, "Opened")));
      assertEquals(Date.valueOf("2020-03-04"), column(table, "Opened").get(0));
      assertEquals(java.sql.Time.class, table.getType(col(table, "At")));
      assertEquals(java.sql.Time.valueOf("13:45:10"), column(table, "At").get(0));
   }

   @Test
   void unparsableValueLeavesTheColumnAsText() throws Exception {
      String rows = "{\"ID\":0,\"ReleaseDate\":\"1992-01-01T00:00:00Z\"}," +
         "{\"ID\":1,\"ReleaseDate\":\"not a date\"},{\"ID\":2,\"ReleaseDate\":null}";
      OEntityTable table = new OEntityTable(
         entitySet(rows), new ODataQuery(),
         schema(new TabularColumn("ID", XSchema.LONG),
                new TabularColumn("ReleaseDate", XSchema.TIME_INSTANT)));

      // all or nothing per column, and then it behaves as an untyped one: values as sent, null ""
      assertEquals(String.class, table.getType(col(table, "ReleaseDate")));
      assertEquals(List.of("1992-01-01T00:00:00Z", "not a date", ""),
                   column(table, "ReleaseDate"));
   }

   @Test
   void dateInsideAComplexValueIsTypedByItsDottedName() throws Exception {
      OEntityTable table = new OEntityTable(
         entitySet("{\"ID\":1,\"Address\":{\"Street\":\"1 Main\"," +
                   "\"Since\":\"2001-02-03T04:05:06Z\"}}"),
         new ODataQuery(),
         schema(new TabularColumn("ID", XSchema.LONG),
                new TabularColumn("Address.Street", XSchema.STRING),
                new TabularColumn("Address.Since", XSchema.TIME_INSTANT)));

      assertEquals(List.of("ID", "Address.Street", "Address.Since"), names(table));
      assertEquals(Timestamp.class, table.getType(col(table, "Address.Since")));
   }

   @Test
   void describedDerivedTypeColumnsAreAddedWhenNoRowCarriesThem() throws Exception {
      // a filter that returns only base-type rows
      OEntityTable table = new OEntityTable(
         entitySet("{\"ID\":0,\"Name\":\"Bill\"},{\"ID\":1,\"Name\":\"Joe\"}"), new ODataQuery(),
         schema(new TabularColumn("ID", XSchema.LONG), new TabularColumn("Name", XSchema.STRING),
                new TabularColumn("TotalExpense", XSchema.DOUBLE),
                new TabularColumn("HireDate", XSchema.TIME_INSTANT)));

      assertEquals(List.of("ID", "Name", "TotalExpense", "HireDate"), names(table));
      assertEquals(Double.class, table.getType(col(table, "TotalExpense")));
      assertEquals(Timestamp.class, table.getType(col(table, "HireDate")));
      assertEquals(Arrays.asList(null, null), column(table, "HireDate"));
      assertEquals(List.of("Bill", "Joe"), column(table, "Name"));
   }

   @Test
   void derivedTypeColumnsFromRowsAreNotDuplicated() throws Exception {
      OEntityTable table = new OEntityTable(
         entitySet("{\"ID\":0,\"Name\":\"Bill\"}," +
                   "{\"@odata.type\":\"#ODataDemo.Employee\",\"ID\":1,\"Name\":\"Joe\"," +
                   "\"HireDate\":\"2000-05-30T00:00:00Z\"}"),
         new ODataQuery(),
         schema(new TabularColumn("ID", XSchema.LONG), new TabularColumn("Name", XSchema.STRING),
                new TabularColumn("HireDate", XSchema.TIME_INSTANT)));

      assertEquals(List.of("ID", "Name", "HireDate"), names(table));
      List<Object> hired = column(table, "HireDate");
      assertNull(hired.get(0));
      assertEquals(Timestamp.class, hired.get(1).getClass());
   }

   @Test
   void complexValueThatIsNullInEveryRowGetsItsDottedColumns() throws Exception {
      OEntityTable table = new OEntityTable(
         entitySet("{\"ID\":0,\"Address\":null},{\"ID\":1,\"Address\":null}"), new ODataQuery(),
         schema(new TabularColumn("ID", XSchema.LONG),
                new TabularColumn("Address.Street", XSchema.STRING),
                new TabularColumn("Address.City", XSchema.STRING)));

      // not the single text column "Address" the untyped build makes for it
      assertEquals(List.of("ID", "Address.Street", "Address.City"), names(table));
      assertEquals(Arrays.asList(null, null), column(table, "Address.City"));
   }

   @Test
   void untypedQueryDoesNotPad() throws Exception {
      OEntityTable table = new OEntityTable(
         entitySet("{\"ID\":0,\"Address\":null}"), new ODataQuery());

      assertEquals(List.of("ID", "Address"), names(table));
   }

   @Test
   void selectNarrowedQueryIsNotPadded() throws Exception {
      ODataQuery query = new ODataQuery();
      query.setSelect("ID,Name");
      OEntityTable table = new OEntityTable(
         entitySet("{\"ID\":0,\"Name\":\"Bill\"}"), query,
         schema(new TabularColumn("ID", XSchema.LONG), new TabularColumn("Name", XSchema.STRING),
                new TabularColumn("TotalExpense", XSchema.DOUBLE),
                new TabularColumn("HireDate", XSchema.TIME_INSTANT)));

      assertEquals(List.of("ID", "Name"), names(table));
   }

   @Test
   void typedQueryWithSelectStillTypesItsDates() throws Exception {
      ODataQuery query = new ODataQuery();
      query.setSelect("ID,ReleaseDate");
      OEntityTable table = new OEntityTable(
         entitySet("{\"ID\":0,\"ReleaseDate\":\"1992-01-01T00:00:00Z\"}"), query,
         PRODUCT_SCHEMA);

      assertEquals(List.of("ID", "ReleaseDate"), names(table));
      assertEquals(Timestamp.class, table.getType(col(table, "ReleaseDate")));
   }

   @Test
   void expandedArraysQueryIsNotPadded() throws Exception {
      ODataQuery query = new ODataQuery();
      query.setExpanded(true);
      OEntityTable table = new OEntityTable(
         entitySet("{\"ID\":0}"), query,
         schema(new TabularColumn("ID", XSchema.LONG), new TabularColumn("Tags", XSchema.STRING)));

      assertEquals(List.of("ID"), names(table));
   }

   @Test
   void convertTemporalAcceptsIsoTextAndRejectsGarbage() {
      assertNotNull(OEntityTable.convertTemporal("2001-02-03T04:05:06Z", Timestamp.class));
      assertNotNull(OEntityTable.convertTemporal("2001-02-03T04:05:06", Timestamp.class));
      assertNull(OEntityTable.convertTemporal("2001-02-03", Timestamp.class));
      assertNull(OEntityTable.convertTemporal("garbage", Date.class));
      assertNull(OEntityTable.convertTemporal(42, Date.class));
      Object already = new Timestamp(1L);
      assertSame(already, OEntityTable.convertTemporal(already, Timestamp.class));
   }
}
