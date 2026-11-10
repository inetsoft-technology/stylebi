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
package inetsoft.uql.elasticrest;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import inetsoft.sree.PropertiesEngine;
import inetsoft.uql.elasticrest.ElasticCatalog.MappedField;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.JsonTable;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwapper;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ElasticMappingTypes} on parsed search results, loaded into a real {@link JsonTable} the
 * way {@link ElasticRestRuntime#runQuery} loads them. Needs no Elasticsearch: the input is the JSON
 * a {@code _search} returns, parsed by the same provider the runtime uses. The end-to-end
 * counterpart, through the catalog's own {@code params} and a live server, is in
 * {@link ElasticCatalogTests}.
 */
class ElasticMappingTypesTests {
   @BeforeAll
   static void stubContext() {
      // JsonTable.load reaches XSwapper and PropertiesEngine through ConfigurationContext; see
      // ElasticCatalogTests.loadData for why a null bean there breaks the JVM for later tests.
      ApplicationContext context = mock(ApplicationContext.class);
      PropertiesEngine propertiesEngine = mock(PropertiesEngine.class);
      when(propertiesEngine.getProperty(anyString(), anyString(), anyBoolean()))
         .thenAnswer(invocation -> invocation.getArgument(1));
      when(context.getBean(PropertiesEngine.class)).thenReturn(propertiesEngine);
      when(context.getBean(XSwapper.class)).thenReturn(mock(XSwapper.class));
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @Test
   void isoStringsAndEpochMillisInOneDateColumnBecomeOneInstantColumn() {
      // The reported case: the default date format accepts both, and _source keeps whichever the
      // document was sent with. Unconverted, the mix widens to a string column and the number
      // reads back as "1700000000000".
      JsonTable table = load("""
         [{"d": "2026-03-04T10:00:00Z"}, {"d": "1970-01-01T00:00:00Z"}, {"d": null},
          {"d": 1700000000000}]""", Map.of("d", date(null)));

      assertEquals(XSchema.TIME_INSTANT, kind(table, "d"));
      assertEquals(Arrays.asList(Instant.parse("2026-03-04T10:00:00Z"),
                                 Instant.parse("1970-01-01T00:00:00Z"), null,
                                 Instant.parse("2023-11-14T22:13:20Z")),
                   instants(table, "d"));
   }

   @Test
   void theOrderOfStringsAndNumbersDoesNotMatter() {
      JsonTable table = load("[{\"d\": 1700000000000}, {\"d\": \"2026-03-04T10:00:00Z\"}]",
                             Map.of("d", date(null)));

      assertEquals(XSchema.TIME_INSTANT, kind(table, "d"));
      assertEquals(List.of(Instant.parse("2023-11-14T22:13:20Z"),
                           Instant.parse("2026-03-04T10:00:00Z")), instants(table, "d"));
   }

   @Test
   void aColumnOfEpochMillisOnlyIsAnInstantColumnNotALong() {
      JsonTable table = load("[{\"d\": 1700000000000}, {\"d\": 0}]", Map.of("d", date(null)));

      assertEquals(XSchema.TIME_INSTANT, kind(table, "d"));
      assertEquals(List.of(Instant.parse("2023-11-14T22:13:20Z"), Instant.EPOCH),
                   instants(table, "d"));
   }

   @Test
   void epochSecondIsScaledNotReadAsMillis() {
      // Read as millis, 1700000000 would be 1970-01-20 -- a confident wrong date.
      JsonTable table = load("[{\"d\": 1700000000}, {\"d\": \"1700000000.5\"}]",
                             Map.of("d", date("epoch_second")));

      assertEquals(XSchema.TIME_INSTANT, kind(table, "d"));
      assertEquals(List.of(Instant.parse("2023-11-14T22:13:20Z"),
                           Instant.parse("2023-11-14T22:13:20.500Z")), instants(table, "d"));
   }

   @Test
   void formatsAreTriedInTheMappingsOrder() {
      // epoch_second first: a number is seconds even though epoch_millis is also listed.
      assertEquals(Instant.parse("2023-11-14T22:13:20Z"), ElasticMappingTypes.toTimestamp(
         1700000000L, date("epoch_second||epoch_millis")).toInstant());
      assertEquals(Instant.parse("1970-01-20T16:13:20Z"), ElasticMappingTypes.toTimestamp(
         1700000000L, date("epoch_millis||epoch_second")).toInstant());
   }

   @Test
   void aCustomPatternIsParsedWithThatPattern() {
      JsonTable table = load("[{\"d\": \"2026/03/04 10:00:00\"}, {\"d\": \"2026/12/31\"}]",
                             Map.of("d", date("yyyy/MM/dd HH:mm:ss||yyyy/MM/dd")));

      assertEquals(XSchema.TIME_INSTANT, kind(table, "d"));
      assertEquals(List.of(Instant.parse("2026-03-04T10:00:00Z"),
                           Instant.parse("2026-12-31T00:00:00Z")), instants(table, "d"));
   }

   @Test
   void aCustomPatternOutsideTheIsoAndSlashFormsIsParsedToo() {
      // Neither BaseJsonTable's ISO parser nor its '/' branch reads dd.MM.yyyy; seeding the
      // column a date type without converting it first would have turned it into nulls.
      JsonTable table = load("[{\"d\": \"04.03.2026\"}]", Map.of("d", date("dd.MM.yyyy")));

      assertEquals(XSchema.TIME_INSTANT, kind(table, "d"));
      assertEquals(List.of(Instant.parse("2026-03-04T00:00:00Z")), instants(table, "d"));
   }

   @Test
   void anOffsetIsHonouredAndAStringWithoutOneIsUtc() {
      JsonTable table = load("""
         [{"d": "2026-03-04T12:00:00+02:00"}, {"d": "2026-03-04T10:00:00"}, {"d": "2026-03-04"},
          {"d": "2026-03"}, {"d": "2026"}]""", Map.of("d", date(null)));

      assertEquals(List.of(Instant.parse("2026-03-04T10:00:00Z"),
                           Instant.parse("2026-03-04T10:00:00Z"),
                           Instant.parse("2026-03-04T00:00:00Z"),
                           Instant.parse("2026-03-01T00:00:00Z"),
                           Instant.parse("2026-01-01T00:00:00Z")), instants(table, "d"));
   }

   @Test
   void dateNanosDefaultsToItsOwnFormat() {
      JsonTable table = load("[{\"d\": \"2026-03-04T10:00:00.123456789Z\"}, {\"d\": 1700000000000}]",
                             Map.of("d", new MappedField(XSchema.TIME_INSTANT, "date_nanos", null)));

      assertEquals(XSchema.TIME_INSTANT, kind(table, "d"));
      assertEquals(List.of(Instant.parse("2026-03-04T10:00:00.123456789Z"),
                           Instant.parse("2023-11-14T22:13:20Z")), instants(table, "d"));
   }

   @Test
   void aTextFieldHoldingDatesStaysText() {
      // earthquakes.@timestamp: mapped text, so described string; JsonTable's own date detection
      // would otherwise build it as a date.
      JsonTable table = load("[{\"t\": \"2016/01/04T21:18:48.64Z\"}, {\"t\": \"2026-03-04 10:00:00\"}]",
                             Map.of("t", text()));

      assertEquals(XSchema.STRING, kind(table, "t"));
      assertEquals(List.of("2016/01/04T21:18:48.64Z", "2026-03-04 10:00:00"), values(table, "t"));
   }

   @Test
   void aNumberInATextFieldBecomesItsTextNotANull() {
      JsonTable table = load("[{\"t\": 42}, {\"t\": \"abc\"}]", Map.of("t", text()));

      assertEquals(XSchema.STRING, kind(table, "t"));
      assertEquals(List.of("42", "abc"), values(table, "t"));
   }

   @Test
   void aValueOutsideTheFormatsLeavesTheWholeColumnUntouchedRatherThanNulled() {
      // ignore_malformed lets such a document in. Converting the rest and typing the column a
      // date would turn "not a date" into a null; the column keeps today's behaviour instead.
      String json = "[{\"d\": \"2026-03-04T10:00:00Z\"}, {\"d\": \"not a date\"}]";
      JsonTable withMapping = load(json, Map.of("d", date(null)));
      JsonTable withoutMapping = load(json, Map.of());

      assertEquals(values(withoutMapping, "d"), values(withMapping, "d"));
      assertEquals(kind(withoutMapping, "d"), kind(withMapping, "d"));
      assertTrue(values(withMapping, "d").stream().noneMatch(Objects::isNull));
   }

   @Test
   void anArrayValueLeavesTheColumnUntouchedRatherThanNulled() {
      String json = "[{\"d\": [\"2026-01-01T00:00:00Z\", \"2026-02-01T00:00:00Z\"]}, " +
         "{\"d\": \"2026-03-04T10:00:00Z\"}]";

      assertEquals(values(load(json, Map.of()), "d"), values(load(json, Map.of("d", date(null))), "d"));
      assertNotNull(values(load(json, Map.of("d", date(null))), "d").get(0));
   }

   @Test
   void aBuiltInFormatThisClassDoesNotReadLeavesTheColumnUntouched() {
      String json = "[{\"d\": \"20260304\"}]";

      assertEquals(values(load(json, Map.of()), "d"),
                   values(load(json, Map.of("d", date("basic_date"))), "d"));
   }

   @Test
   void numericFieldsAreLeftToInferenceSoAStrayStringIsNotNulled() {
      MappedField longField = new MappedField(XSchema.LONG, "long", null);
      JsonTable table = load("[{\"n\": 1}, {\"n\": \"n/a\"}]", Map.of("n", longField));

      assertEquals(List.of(1, "n/a"), values(table, "n"));
   }

   @Test
   void aDeclaredObjectsDateLeafIsAddressedByItsDottedName() {
      JsonTable table = load("[{\"o\": {\"at\": 1700000000000}}, {\"o\": {\"at\": \"2026-03-04T10:00:00Z\"}}]",
                             Map.of("o.at", date(null)));

      assertEquals(XSchema.TIME_INSTANT, kind(table, "o.at"));
      assertEquals(List.of(Instant.parse("2023-11-14T22:13:20Z"),
                           Instant.parse("2026-03-04T10:00:00Z")), instants(table, "o.at"));
   }

   private static JsonTable load(String json, Map<String, MappedField> fields) {
      Object rows = JsonPath.using(Configuration.builder().jsonProvider(new JacksonJsonProvider())
                                      .build()).parse(json).read("$");
      JsonTable table = new JsonTable();

      if(!fields.isEmpty()) {
         ElasticMappingTypes.apply(table, rows, fields);
      }

      table.load(rows);
      return table;
   }

   private static MappedField date(String format) {
      return new MappedField(XSchema.TIME_INSTANT, "date", format);
   }

   private static MappedField text() {
      return new MappedField(XSchema.STRING, "text", null);
   }

   /** The column's kind as a worksheet sees it: the XSchema type of its Java class. */
   private static String kind(JsonTable table, String column) {
      return Tool.getDataType(table.getType(index(table, column)));
   }

   private static List<Object> values(JsonTable table, String column) {
      int col = index(table, column);
      List<Object> values = new ArrayList<>();
      table.rewind();

      while(table.next()) {
         values.add(table.getObject(col));
      }

      return values;
   }

   private static List<Instant> instants(JsonTable table, String column) {
      return values(table, column).stream()
         .map(v -> v == null ? null : ((Timestamp) v).toInstant())
         .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
   }

   private static int index(JsonTable table, String column) {
      for(int i = 0; i < table.getColCount(); i++) {
         if(column.equals(table.getName(i))) {
            return i;
         }
      }

      throw new AssertionError("no column '" + column + "'");
   }
}
