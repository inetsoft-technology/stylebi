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
package inetsoft.uql.rest.xml;

import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link RestXmlEndpointTokenCodec} in isolation -- no {@code TabularCatalog}/
 * {@code TabularDatasetSchema} types, only JSON/base64 fixtures. Malformed cases are hand-written
 * base64/JSON strings, since a malformed token by definition cannot come from
 * {@link RestXmlEndpointTokenCodec#encode}.
 */
class RestXmlEndpointTokenCodecTest {
   // ----- valid tokens -----

   @Test
   void decode_roundTripsAValidToken() throws Exception {
      RestXmlEndpointTokenCodec.DecodedToken original = new RestXmlEndpointTokenCodec.DecodedToken(
         "/api/books", "/bookstore/book",
         List.of(new RestXmlEndpointTokenCodec.Column("title", XSchema.STRING, "Book title"),
                 new RestXmlEndpointTokenCodec.Column("price", XSchema.DOUBLE, null)));

      String token = RestXmlEndpointTokenCodec.encode(original);
      RestXmlEndpointTokenCodec.DecodedToken decoded = RestXmlEndpointTokenCodec.decode(token);

      assertEquals(original, decoded);
   }

   @Test
   void decode_columnOrderPreserved() throws Exception {
      RestXmlEndpointTokenCodec.DecodedToken original = new RestXmlEndpointTokenCodec.DecodedToken(
         "/api/x", "/x",
         List.of(new RestXmlEndpointTokenCodec.Column("c1", XSchema.STRING, null),
                 new RestXmlEndpointTokenCodec.Column("c2", XSchema.LONG, null),
                 new RestXmlEndpointTokenCodec.Column("c3", XSchema.BOOLEAN, null),
                 new RestXmlEndpointTokenCodec.Column("c4", XSchema.DATE, null)));

      RestXmlEndpointTokenCodec.DecodedToken decoded =
         RestXmlEndpointTokenCodec.decode(RestXmlEndpointTokenCodec.encode(original));

      assertEquals(List.of("c1", "c2", "c3", "c4"),
         decoded.columns().stream().map(RestXmlEndpointTokenCodec.Column::name).toList());
   }

   // ----- malformed tokens (A7): rejected before any other processing -----

   @Test
   void decode_notBase64_rejected() {
      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode("not-base64-!!!"));
      assertTrue(thrown.getMessage().contains("base64"), thrown.getMessage());
   }

   @Test
   void decode_validBase64NotJson_rejected() {
      String token = base64("not json");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("JSON"), thrown.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = {"[1,2,3]", "\"a string\"", "42"})
   void decode_validJsonNotAnObject_rejected(String json) {
      String token = base64(json);

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token), "json=" + json);
      assertTrue(thrown.getMessage().contains("JSON object"),
         "json=" + json + ": " + thrown.getMessage());
   }

   @Test
   void decode_versionMissing_rejected() {
      String token = base64("{\"suffix\":\"/s\",\"xpath\":\"/x\",\"columns\":[" +
         column("c", "string", null) + "]}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("version"), thrown.getMessage());
   }

   @Test
   void decode_versionWrongType_rejected() {
      String token = base64("{\"version\":\"1\",\"suffix\":\"/s\",\"xpath\":\"/x\",\"columns\":[" +
         column("c", "string", null) + "]}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("version"), thrown.getMessage());
   }

   @Test
   void decode_versionWrongValue_rejected() {
      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"xpath\":\"/x\",\"columns\":[" +
         column("c", "string", null) + "]}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("version"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("2"), thrown.getMessage());
   }

   @Test
   void decode_suffixKeyAbsent_rejected() {
      String token = base64("{\"version\":1,\"xpath\":\"/x\",\"columns\":[" +
         column("c", "string", null) + "]}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("suffix"), thrown.getMessage());
   }

   @Test
   void decode_xpathKeyAbsent_rejected() {
      String token = base64("{\"version\":1,\"suffix\":\"/s\",\"columns\":[" +
         column("c", "string", null) + "]}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("xpath"), thrown.getMessage());
   }

   @Test
   void decode_columnsKeyAbsent_rejected() {
      String token = base64("{\"version\":1,\"suffix\":\"/s\",\"xpath\":\"/x\"}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("column"), thrown.getMessage());
   }

   // ----- blank/null suffix or xpath (A10) -----

   @ParameterizedTest
   @ValueSource(strings = {"null", "\"\"", "\"   \""})
   void decode_blankOrNullSuffix_rejected(String suffixLiteral) {
      String token = base64("{\"version\":1,\"suffix\":" + suffixLiteral + ",\"xpath\":\"/x\"," +
         "\"columns\":[" + column("c", "string", null) + "]}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token), "suffix=" + suffixLiteral);
      assertTrue(thrown.getMessage().contains("suffix"),
         "suffix=" + suffixLiteral + ": " + thrown.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = {"null", "\"\"", "\"   \""})
   void decode_blankOrNullXpath_rejected(String xpathLiteral) {
      String token = base64("{\"version\":1,\"suffix\":\"/s\",\"xpath\":" + xpathLiteral + "," +
         "\"columns\":[" + column("c", "string", null) + "]}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token), "xpath=" + xpathLiteral);
      assertTrue(thrown.getMessage().contains("xpath"),
         "xpath=" + xpathLiteral + ": " + thrown.getMessage());
   }

   // ----- empty columns array (A8) -----

   @Test
   void decode_emptyColumnsArray_rejected() {
      String token = base64("{\"version\":1,\"suffix\":\"/s\",\"xpath\":\"/x\",\"columns\":[]}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("column"), thrown.getMessage());
   }

   // ----- column type validation (A9) -----

   @Test
   void decode_columnTypeNotAnXSchemaConstant_rejected() {
      String token = base64("{\"version\":1,\"suffix\":\"/s\",\"xpath\":\"/x\",\"columns\":[" +
         column("badcol", "not_a_real_type", null) + "]}");

      // Assert on the thrown exception, never a returned value -- a wrong implementation that
      // silently degrades to XSchema.STRING (Datagov-style) instead of throwing must fail this
      // test, not pass it.
      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("badcol"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("not_a_real_type"), thrown.getMessage());
   }

   @ParameterizedTest
   @MethodSource("everyXSchemaConstant")
   void decode_everyXSchemaConstantIsAccepted(String xschemaType) throws Exception {
      RestXmlEndpointTokenCodec.DecodedToken original = new RestXmlEndpointTokenCodec.DecodedToken(
         "/s", "/x", List.of(new RestXmlEndpointTokenCodec.Column("c", xschemaType, null)));

      RestXmlEndpointTokenCodec.DecodedToken decoded =
         RestXmlEndpointTokenCodec.decode(RestXmlEndpointTokenCodec.encode(original));

      assertEquals(xschemaType, decoded.columns().get(0).type(), "type=" + xschemaType);
   }

   /**
    * Reflects {@link XSchema}'s own fields in THIS test too (not copy-pasted from the codec) so
    * A9's "valid vocabulary" claim stays tied to {@code XSchema} itself rather than to a copy in
    * either file that could silently drift.
    */
   static Stream<String> everyXSchemaConstant() throws IllegalAccessException {
      List<String> values = new java.util.ArrayList<>();

      for(Field field : XSchema.class.getDeclaredFields()) {
         if(field.getType() == String.class &&
            Modifier.isPublic(field.getModifiers()) &&
            Modifier.isStatic(field.getModifiers()) &&
            Modifier.isFinal(field.getModifiers()))
         {
            values.add((String) field.get(null));
         }
      }

      return values.stream();
   }

   // ----- column description: null vs. absent vs. blank -----

   @Test
   void decode_columnDescriptionNullVsAbsentVsBlank() throws Exception {
      String token = base64("{\"version\":1,\"suffix\":\"/s\",\"xpath\":\"/x\",\"columns\":[" +
         "{\"name\":\"absent\",\"type\":\"string\"}," +
         "{\"name\":\"jsonNull\",\"type\":\"string\",\"description\":null}," +
         "{\"name\":\"blank\",\"type\":\"string\",\"description\":\"\"}," +
         "{\"name\":\"real\",\"type\":\"string\",\"description\":\"a real description\"}" +
         "]}");

      RestXmlEndpointTokenCodec.DecodedToken decoded = RestXmlEndpointTokenCodec.decode(token);

      assertNull(decoded.columns().get(0).description(), "absent key -> null");
      assertNull(decoded.columns().get(1).description(), "JSON null -> null");
      assertEquals("", decoded.columns().get(2).description(),
         "a blank string is preserved as '', not collapsed to null -- this token's data is " +
         "presumed pre-curated, unlike Datagov's messy live wire format");
      assertEquals("a real description", decoded.columns().get(3).description());
   }

   // ----- missing/blank token -----

   @ParameterizedTest
   @NullAndEmptySource
   @ValueSource(strings = {"   "})
   void decode_missingOrBlankToken_rejected(String token) {
      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("missing or blank"), thrown.getMessage());
   }

   // ----- helpers -----

   private static String base64(String json) {
      return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
   }

   private static String column(String name, String type, String description) {
      return "{\"name\":\"" + name + "\",\"type\":\"" + type + "\"" +
         (description != null ? ",\"description\":\"" + description + "\"" : "") + "}";
   }
}
