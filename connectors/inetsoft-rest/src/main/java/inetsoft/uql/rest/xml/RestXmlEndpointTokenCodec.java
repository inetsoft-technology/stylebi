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
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.uql.schema.XSchema;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Encodes/decodes the opaque, self-describing endpoint token {@link RestXmlEndpointCatalog}
 * assembles a {@code TabularDatasetSchema} from. Package-private, final, no instance state --
 * same shape as {@code MongoCatalog}/{@code DatagovCatalog} (private no-arg constructor, static
 * methods only). Pure: no {@code TabularCatalog}/{@code TabularDatasetSchema} types cross into
 * this class, so its own tests need zero SPI-shape fixtures, only JSON/base64 ones.
 *
 * <p>The token is standard (non-URL-safe) base64 wrapping a JSON object:
 * <pre>{@code
 * {
 *   "version": 2,
 *   "suffix": "/api/books",
 *   "xpath": "/bookstore/book",
 *   "responseSchema": {
 *     "title": "string",
 *     "price": "double",
 *     "author": { "name": "string", "id": "integer" },
 *     "reviews": [ { "rating": "integer", "comment": "string" } ]
 *   }
 * }
 * }</pre>
 *
 * <p>{@code responseSchema} is a curated-schema-shaped tree -- a non-empty object, a length-1
 * array (the element shape), or a bare {@link #COLUMN_TYPES} string leaf, matching wiz-services'
 * {@code curatedResponseSchema.ts}'s {@code ResponseSchemaNode}/{@code validateCuratedSchema}
 * exactly (that TS representation is the one this format reuses, not a new one -- see this
 * round's design doc). v1's flat {@code columns:[{name,type,description}]} is gone: {@code
 * version:1} tokens are rejected outright, not migrated.
 *
 * <p>{@code decode} rejects a structurally invalid token before any other processing, per the
 * charter's A1/A2 requirements -- see the validation order documented on {@link #decode}.
 */
final class RestXmlEndpointTokenCodec {
   private RestXmlEndpointTokenCodec() {
   }

   /**
    * The fully-decoded, validated payload of a token. {@code responseSchema} is the raw, already-
    * validated Jackson tree -- {@link RestXmlEndpointCatalog} walks it directly to flatten it into
    * columns.
    */
   record DecodedToken(String suffix, String xpath, JsonNode responseSchema) {
   }

   /**
    * Builds a syntactically-valid token -- test-support only, not required by the SPI or any
    * production caller. StyleBI never encodes a token; wiz-services' own
    * {@code encodeRestXmlEndpointToken} is the real encoder. Its only job is letting a test build a
    * token without hand-writing base64/JSON.
    */
   static String encode(String suffix, String xpath, JsonNode responseSchema) {
      ObjectNode root = MAPPER.createObjectNode();
      root.put("version", CURRENT_VERSION);
      root.put("suffix", suffix);
      root.put("xpath", xpath);
      root.set("responseSchema", responseSchema);
      return Base64.getEncoder().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8));
   }

   /**
    * Decodes and validates {@code token}, rejecting a structurally invalid one before any other
    * processing. Validation order, this class's own choice:
    *
    * <ol>
    *   <li>{@code token} null/blank -- rejected.</li>
    *   <li>Not valid base64 -- rejected.</li>
    *   <li>Base64-decoded bytes do not parse as JSON -- rejected.</li>
    *   <li>Parsed JSON is not an object (a bare array/string/number) -- rejected.</li>
    *   <li>{@code version} missing, not an integer, or not equal to {@link #CURRENT_VERSION}
    *       (now {@code 2}) -- rejected, naming both the expected and found value. A {@code
    *       version:1} token (the old flat format) is simply invalid -- there is no migration
    *       path.</li>
    *   <li>{@code suffix} missing, null, or blank -- rejected.</li>
    *   <li>{@code xpath} same check.</li>
    *   <li>{@code responseSchema} missing or JSON {@code null} -- rejected.</li>
    *   <li>{@code responseSchema} recursively validated by {@link #validateTree} -- a line-for-
    *       line Java port of {@code curatedResponseSchema.ts}'s {@code walk()}: a non-empty
    *       object (every value recursively valid), a length-1 array (the element recursively
    *       valid), or a string leaf that is one of {@link #COLUMN_TYPES}. Fail-fast on the first
    *       bad node, same discipline as {@code walk()}'s own early return.</li>
    * </ol>
    *
    * @throws Exception naming what is wrong, before any network access or further processing.
    */
   static DecodedToken decode(String token) throws Exception {
      if(token == null || token.isBlank()) {
         throw new Exception("Rest.XML endpoint token is missing or blank.");
      }

      byte[] bytes;

      try {
         bytes = Base64.getDecoder().decode(token);
      }
      catch(IllegalArgumentException e) {
         throw new Exception("Rest.XML endpoint token is not valid base64.", e);
      }

      JsonNode root;

      try {
         root = MAPPER.readTree(bytes);
      }
      catch(Exception e) {
         throw new Exception("Rest.XML endpoint token does not decode to valid JSON.", e);
      }

      if(root == null || !root.isObject()) {
         throw new Exception("Rest.XML endpoint token must decode to a JSON object.");
      }

      JsonNode version = root.get("version");

      if(version == null || !version.isInt() || version.asInt() != CURRENT_VERSION) {
         throw new Exception("Rest.XML endpoint token must have version=" + CURRENT_VERSION +
            "; found " + (version == null ? "no version field" : version));
      }

      String suffix = requireNonBlankText(root, "suffix");
      String xpath = requireNonBlankText(root, "xpath");
      JsonNode responseSchema = root.get("responseSchema");

      if(responseSchema == null || responseSchema.isNull()) {
         throw new Exception("Rest.XML endpoint token is missing the 'responseSchema' field.");
      }

      validateTree(responseSchema, "$", new int[]{0}, 0);

      return new DecodedToken(suffix, xpath, responseSchema);
   }

   /**
    * A line-for-line Java port of {@code curatedResponseSchema.ts}'s {@code walk()} -- same
    * branch order, same messages, same caps -- so the two implementations validate the exact same
    * tree shapes as each other (A2). An {@code int[]} of length 1 stands in for the TS
    * {@code Budget} mutable-counter object, since Java has no closure over a plain {@code int}.
    */
   private static void validateTree(JsonNode node, String at, int[] nodeCount, int depth)
      throws Exception
   {
      nodeCount[0]++;

      if(nodeCount[0] > MAX_NODES) {
         throw new Exception("Rest.XML endpoint token responseSchema at '" + at +
            "' exceeds the " + MAX_NODES + "-node cap.");
      }

      if(depth > MAX_DEPTH) {
         throw new Exception("Rest.XML endpoint token responseSchema at '" + at +
            "' exceeds the " + MAX_DEPTH + "-level depth cap.");
      }

      if(node.isTextual()) {
         if(!COLUMN_TYPES.contains(node.asText())) {
            throw new Exception("Rest.XML endpoint token responseSchema at '" + at + "': '" +
               node.asText() + "' is not one of " + String.join(", ", COLUMN_TYPES) + ".");
         }

         return;
      }

      if(node.isArray()) {
         if(node.size() != 1) {
            throw new Exception("Rest.XML endpoint token responseSchema at '" + at + "': " +
               (node.isEmpty()
                  ? "an empty array is indistinguishable from \"not declared\"."
                  : "array must have exactly one element (the element shape), has " +
                     node.size() + "."));
         }

         validateTree(node.get(0), at + "[*]", nodeCount, depth + 1);
         return;
      }

      if(node.isObject()) {
         List<String> keys = new ArrayList<>();
         node.fieldNames().forEachRemaining(keys::add);   // Jackson ObjectNode: insertion order

         if(keys.isEmpty()) {
            throw new Exception("Rest.XML endpoint token responseSchema at '" + at + "': an " +
               "empty object is indistinguishable from \"not declared\".");
         }

         if(keys.size() > 1 && keys.contains("*")) {
            List<String> named = keys.stream().filter(k -> !k.equals("*")).toList();
            throw new Exception("Rest.XML endpoint token responseSchema at '" + at + "': '*' " +
               "stands for every key of a data-keyed object, so it cannot appear beside named " +
               "keys (" + String.join(", ", named) + ").");
         }

         for(String key : keys) {
            validateTree(node.get(key), "$".equals(at) ? key : at + "." + key, nodeCount, depth + 1);
         }

         return;
      }

      String kind = node.isNull() ? "null" : node.isBoolean() ? "boolean" : node.isNumber() ?
         "number" : node.getNodeType().toString().toLowerCase();
      throw new Exception("Rest.XML endpoint token responseSchema at '" + at + "': a " + kind +
         " literal is not a type name -- did a sample value get pasted in?");
   }

   private static String requireNonBlankText(JsonNode node, String field) throws Exception {
      JsonNode value = node == null ? null : node.get(field);

      if(value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
         throw new Exception("Rest.XML endpoint token field '" + field + "' is missing or " +
            "blank.");
      }

      return value.asText();
   }

   private static final int CURRENT_VERSION = 2;
   private static final ObjectMapper MAPPER = new ObjectMapper();

   /**
    * Node and depth caps for {@code responseSchema}, copied FROM {@code curatedResponseSchema.ts}'s
    * own exported {@code MAX_NODES}/{@code MAX_DEPTH} (presently 2000/16). There is no cross-
    * language constant-sharing mechanism in this repo in either direction: that TS file already
    * copies ITS OWN caps from {@code JsonShapeDistiller.DEFAULT_MAX_NODES}/{@code DEFAULT_MAX_DEPTH}
    * (kept {@code >=}, not equal), and this is now a second, independent copy in the other
    * direction, for a different purpose (exact parity, not a floor). If either the TS file's
    * caps or the distiller's caps change, this must be updated by hand to match.
    */
   private static final int MAX_NODES = 2000;
   private static final int MAX_DEPTH = 16;

   /**
    * The exact 6-word leaf vocabulary {@code curatedResponseSchema.ts}'s own {@code COLUMN_TYPES}
    * (`shared/types/worksheet.ts`) declares -- a deliberate reversal of v1's reflection-based
    * approach ({@code reflectXSchemaConstants()}, deleted). v1 needed reflection because ANY
    * {@code XSchema} constant was a legal column type structurally; v2's legal leaf vocabulary is
    * this specific 6-word list, which happens to equal 6 particular {@code XSchema} constants'
    * VALUES. Reflecting over the whole class and filtering would still need a hand-written filter
    * naming these same six fields, so it buys no drift-safety over naming them directly -- while
    * referencing {@code XSchema}'s own constants (rather than hand-typed string literals) keeps
    * each value wired to {@code XSchema} automatically if one of its literal strings ever changed.
    */
   private static final Set<String> COLUMN_TYPES = Set.of(
      XSchema.STRING, XSchema.INTEGER, XSchema.DOUBLE, XSchema.DATE, XSchema.TIME_INSTANT,
      XSchema.BOOLEAN);
}
