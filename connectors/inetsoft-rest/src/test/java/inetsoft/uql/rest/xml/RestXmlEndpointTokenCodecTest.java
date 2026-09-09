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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link RestXmlEndpointTokenCodec} in isolation -- no {@code TabularCatalog}/
 * {@code TabularDatasetSchema} types, only JSON/base64 fixtures. Malformed cases are hand-written
 * base64/JSON strings, since a malformed token by definition cannot come from
 * {@link RestXmlEndpointTokenCodec#encode}.
 *
 * <p>The {@code responseSchema} fixture JSON text below is COPY-PASTED VERBATIM into
 * {@code restXmlEndpointToken.test.ts} on the wiz-services side (same constant names in a comment
 * there) -- per this round's reconcile doc, this is what makes A2 ("Java validation agrees exactly
 * with {@code validateCuratedSchema}'s TS rules") provable rather than merely asserted: both sides
 * see the literal same input, not independently re-authored fixtures that could silently diverge.
 */
class RestXmlEndpointTokenCodecTest {
   // ----- shared responseSchema fixtures (verbatim in both languages' test files) -----

   private static final String HAPPY_PATH = "{\"title\":\"string\",\"price\":\"double\"}";
   private static final String HAPPY_PATH_WITH_ARRAY = "{\"title\":\"string\",\"price\":\"double\"," +
      "\"author\":{\"name\":\"string\",\"id\":\"integer\"}," +
      "\"reviews\":[{\"rating\":\"integer\",\"comment\":\"string\"}]}";
   private static final String EMPTY_OBJECT = "{}";
   private static final String EMPTY_ARRAY = "{\"data\":[]}";
   private static final String ARRAY_LENGTH_TWO = "{\"data\":[\"string\",\"string\"]}";
   private static final String BAD_LEAF_TYPE = "{\"amount\":\"decimal\"}";
   private static final String NUMBER_LITERAL = "{\"amount\":42}";
   private static final String BOOLEAN_LITERAL = "{\"paid\":true}";
   private static final String NULL_LITERAL = "{\"description\":null}";
   private static final String WILDCARD_BESIDE_NAMED = "{\"*\":\"string\",\"id\":\"string\"}";
   private static final String WILDCARD_ALONE = "{\"metadata\":{\"*\":\"string\"}}";

   // ----- valid tokens -----

   @Test
   void decode_roundTripsANestedAndRepeatingTree() throws Exception {
      JsonNode tree = json(HAPPY_PATH_WITH_ARRAY);
      String token = RestXmlEndpointTokenCodec.encode("/api/books", "/bookstore/book", tree);
      RestXmlEndpointTokenCodec.DecodedToken decoded = RestXmlEndpointTokenCodec.decode(token);

      assertEquals("/api/books", decoded.suffix());
      assertEquals("/bookstore/book", decoded.xpath());
      assertEquals(tree, decoded.responseSchema());
   }

   @Test
   void decode_bareStringRootAccepted() throws Exception {
      String token = RestXmlEndpointTokenCodec.encode("/s", "/x", MAPPER.valueToTree("string"));
      RestXmlEndpointTokenCodec.DecodedToken decoded = RestXmlEndpointTokenCodec.decode(token);

      assertTrue(decoded.responseSchema().isTextual());
      assertEquals("string", decoded.responseSchema().asText());
   }

   @ParameterizedTest
   @MethodSource("everyColumnType")
   void decode_everyColumnTypeIsAccepted(String columnType) throws Exception {
      String token = RestXmlEndpointTokenCodec.encode("/s", "/x", MAPPER.valueToTree(columnType));
      RestXmlEndpointTokenCodec.DecodedToken decoded = RestXmlEndpointTokenCodec.decode(token);

      assertEquals(columnType, decoded.responseSchema().asText());
   }

   /** The 6-value COLUMN_TYPES vocabulary -- replaces v1's 21-value reflected-XSchema coverage. */
   static Stream<String> everyColumnType() {
      return Stream.of("string", "integer", "double", "date", "timeInstant", "boolean");
   }

   @Test
   void decode_loneWildcardRootAcceptedAtDecodeTime() throws Exception {
      // A2's "must agree exactly": validateCuratedSchema accepts a lone "*" (see
      // curatedResponseSchema.test.ts's "still accepts \"*\" as the only key"), so Java's decode()
      // must too -- rejecting only happens later, at describeDataset (RestXmlEndpointCatalogTest),
      // which is a Rest.XML-specific choice, not a codec-level one.
      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"xpath\":\"/x\",\"responseSchema\":" +
         WILDCARD_ALONE + "}");

      assertDoesNotThrow(() -> RestXmlEndpointTokenCodec.decode(token));
   }

   // ----- malformed tokens: rejected before any other processing -----

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

   // ----- version (A1) -----

   @Test
   void decode_versionMissing_rejected() {
      String token = base64("{\"suffix\":\"/s\",\"xpath\":\"/x\",\"responseSchema\":" + HAPPY_PATH + "}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("version"), thrown.getMessage());
   }

   @Test
   void decode_versionWrongType_rejected() {
      String token = base64("{\"version\":\"2\",\"suffix\":\"/s\",\"xpath\":\"/x\"," +
         "\"responseSchema\":" + HAPPY_PATH + "}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("version"), thrown.getMessage());
   }

   @Test
   void decode_version1Rejected_theOldFlatFormatIsDeletedNotMigrated() {
      // The old v1 shape (`columns` instead of `responseSchema`) is hand-built here since
      // v1-shaped payloads can no longer come from this class's own encode().
      String token = base64("{\"version\":1,\"suffix\":\"/s\",\"xpath\":\"/x\",\"columns\":" +
         "[{\"name\":\"title\",\"type\":\"string\"}]}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("version"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("2"), thrown.getMessage());
   }

   @Test
   void decode_versionWrongValue_rejected() {
      String token = base64("{\"version\":3,\"suffix\":\"/s\",\"xpath\":\"/x\"," +
         "\"responseSchema\":" + HAPPY_PATH + "}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("version"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("3"), thrown.getMessage());
   }

   // ----- suffix/xpath -----

   @Test
   void decode_suffixKeyAbsent_rejected() {
      String token = base64("{\"version\":2,\"xpath\":\"/x\",\"responseSchema\":" + HAPPY_PATH + "}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("suffix"), thrown.getMessage());
   }

   @Test
   void decode_xpathKeyAbsent_rejected() {
      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"responseSchema\":" + HAPPY_PATH + "}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("xpath"), thrown.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = {"null", "\"\"", "\"   \""})
   void decode_blankOrNullSuffix_rejected(String suffixLiteral) {
      String token = base64("{\"version\":2,\"suffix\":" + suffixLiteral + ",\"xpath\":\"/x\"," +
         "\"responseSchema\":" + HAPPY_PATH + "}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token), "suffix=" + suffixLiteral);
      assertTrue(thrown.getMessage().contains("suffix"),
         "suffix=" + suffixLiteral + ": " + thrown.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = {"null", "\"\"", "\"   \""})
   void decode_blankOrNullXpath_rejected(String xpathLiteral) {
      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"xpath\":" + xpathLiteral + "," +
         "\"responseSchema\":" + HAPPY_PATH + "}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token), "xpath=" + xpathLiteral);
      assertTrue(thrown.getMessage().contains("xpath"),
         "xpath=" + xpathLiteral + ": " + thrown.getMessage());
   }

   // ----- responseSchema envelope presence -----

   @Test
   void decode_responseSchemaKeyAbsent_rejected() {
      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"xpath\":\"/x\"}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("responseSchema"), thrown.getMessage());
   }

   @Test
   void decode_responseSchemaNull_rejected() {
      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"xpath\":\"/x\",\"responseSchema\":null}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("responseSchema"), thrown.getMessage());
   }

   // ----- responseSchema structural rules (A2): one case per validateCuratedSchema branch -----

   @ParameterizedTest
   @MethodSource("rejectedResponseSchemas")
   void decode_rejectsEveryStructuralViolation(String label, String responseSchemaJson, String expectedSubstring) {
      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"xpath\":\"/x\",\"responseSchema\":" +
         responseSchemaJson + "}");

      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointTokenCodec.decode(token), label);
      assertTrue(thrown.getMessage().contains(expectedSubstring),
         label + ": " + thrown.getMessage());
   }

   static Stream<org.junit.jupiter.params.provider.Arguments> rejectedResponseSchemas() {
      return Stream.of(
         org.junit.jupiter.params.provider.Arguments.of("empty object", EMPTY_OBJECT, "empty object"),
         org.junit.jupiter.params.provider.Arguments.of("empty array", EMPTY_ARRAY, "empty array"),
         org.junit.jupiter.params.provider.Arguments.of("wrong-length (2) array", ARRAY_LENGTH_TWO, "exactly one element"),
         org.junit.jupiter.params.provider.Arguments.of("bad leaf type", BAD_LEAF_TYPE, "is not one of"),
         org.junit.jupiter.params.provider.Arguments.of("number literal", NUMBER_LITERAL, "number literal"),
         org.junit.jupiter.params.provider.Arguments.of("boolean literal", BOOLEAN_LITERAL, "boolean literal"),
         org.junit.jupiter.params.provider.Arguments.of("null literal", NULL_LITERAL, "null literal"),
         org.junit.jupiter.params.provider.Arguments.of("wildcard beside named key", WILDCARD_BESIDE_NAMED, "id")
      );
   }

   @Test
   void decode_rejectsOverNodeCap() {
      StringBuilder wide = new StringBuilder("{");

      for(int i = 0; i < 2010; i++) {
         if(i > 0) {
            wide.append(",");
         }

         wide.append("\"field").append(i).append("\":\"string\"");
      }

      wide.append("}");

      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"xpath\":\"/x\",\"responseSchema\":" +
         wide + "}");

      Exception thrown = assertThrows(Exception.class, () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("node cap"), thrown.getMessage());
   }

   @Test
   void decode_rejectsOverDepthCap() {
      String tree = "\"string\"";

      for(int i = 0; i < 18; i++) {
         tree = "{\"level\":" + tree + "}";
      }

      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"xpath\":\"/x\",\"responseSchema\":" +
         tree + "}");

      Exception thrown = assertThrows(Exception.class, () -> RestXmlEndpointTokenCodec.decode(token));
      assertTrue(thrown.getMessage().contains("depth cap"), thrown.getMessage());
   }

   @Test
   void decode_acceptsTreeAtExactlyTheDepthCap() throws Exception {
      // MAX_DEPTH (16) nested single-key objects, bottoming out in a leaf -- exactly at the
      // boundary, not past it (mirrors curatedResponseSchema.test.ts's own "at exactly the caps"
      // case).
      String tree = "\"string\"";

      for(int i = 0; i < 16; i++) {
         tree = "{\"level\":" + tree + "}";
      }

      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"xpath\":\"/x\",\"responseSchema\":" +
         tree + "}");

      assertDoesNotThrow(() -> RestXmlEndpointTokenCodec.decode(token));
   }

   @Test
   void decode_acceptsTreeAtExactlyTheNodeCap() throws Exception {
      // MAX_NODES (2000) total nodes: the root object itself counts as node #1, so exactly 1999
      // leaf fields brings the tree to precisely 2000 -- at the boundary, not past it (mirrors
      // decode_acceptsTreeAtExactlyTheDepthCap's style; decode_rejectsOverNodeCap already covers
      // the over-cap side with 2010 fields, which is asymmetric without this positive control).
      StringBuilder wide = new StringBuilder("{");

      for(int i = 0; i < 1999; i++) {
         if(i > 0) {
            wide.append(",");
         }

         wide.append("\"field").append(i).append("\":\"string\"");
      }

      wide.append("}");

      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"xpath\":\"/x\",\"responseSchema\":" +
         wide + "}");

      assertDoesNotThrow(() -> RestXmlEndpointTokenCodec.decode(token));
   }

   // ----- design §3.5 / reconcile item 4: duplicate flattened column names are a deliberately
   // accepted, deferred corner case -- neither this codec nor validateCuratedSchema rejects two
   // tree paths that flatten to the identical dot-joined name, and rejecting it Java-only would
   // violate A2 by making Java's validation stricter than the TS side's. This test exists so a
   // future well-intentioned fix that "closes" this gap on only one side gets caught here. -----

   @Test
   void decode_acceptsDuplicateFlattenedColumnName() throws Exception {
      String responseSchema = "{\"a\":{\"b\":\"string\"},\"a.b\":\"integer\"}";
      String token = base64("{\"version\":2,\"suffix\":\"/s\",\"xpath\":\"/x\",\"responseSchema\":" +
         responseSchema + "}");

      assertDoesNotThrow(() -> RestXmlEndpointTokenCodec.decode(token));
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

   private static final ObjectMapper MAPPER = new ObjectMapper();

   private static JsonNode json(String text) throws Exception {
      return MAPPER.readTree(text);
   }

   private static String base64(String json) {
      return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
   }
}
