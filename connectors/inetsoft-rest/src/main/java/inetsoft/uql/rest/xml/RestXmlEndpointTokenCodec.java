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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.uql.schema.XSchema;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
 *   "version": 1,
 *   "suffix": "/api/books",
 *   "xpath": "/bookstore/book",
 *   "columns": [
 *     { "name": "title", "type": "string", "description": "Book title" },
 *     { "name": "price", "type": "double" }
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code decode} rejects a structurally invalid token before any other processing, per the
 * charter's A7-A10 requirements -- see the validation order documented on {@link #decode}.
 */
final class RestXmlEndpointTokenCodec {
   private RestXmlEndpointTokenCodec() {
   }

   /**
    * One column of the token's flat column list.
    */
   record Column(String name, String type, String description) {
   }

   /**
    * The fully-decoded, validated payload of a token.
    */
   record DecodedToken(String suffix, String xpath, List<Column> columns) {
   }

   /**
    * Builds a syntactically-valid token from a {@link DecodedToken} -- test-support only, not
    * required by the SPI or any production caller. StyleBI never encodes a token; a future,
    * out-of-scope wiz-side pipeline would. Its only job is letting a test build a token without
    * hand-writing base64/JSON.
    */
   static String encode(DecodedToken token) {
      ObjectNode root = MAPPER.createObjectNode();
      root.put("version", CURRENT_VERSION);
      root.put("suffix", token.suffix());
      root.put("xpath", token.xpath());

      ArrayNode columns = root.putArray("columns");

      for(Column c : token.columns()) {
         ObjectNode column = columns.addObject();
         column.put("name", c.name());
         column.put("type", c.type());

         if(c.description() == null) {
            column.putNull("description");
         }
         else {
            column.put("description", c.description());
         }
      }

      return Base64.getEncoder().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8));
   }

   /**
    * Decodes and validates {@code token}, rejecting a structurally invalid one before any other
    * processing (charter: "before any other processing"). Validation order, this class's own
    * choice:
    *
    * <ol>
    *   <li>{@code token} null/blank -- rejected.</li>
    *   <li>Not valid base64 -- rejected.</li>
    *   <li>Base64-decoded bytes do not parse as JSON -- rejected.</li>
    *   <li>Parsed JSON is not an object (a bare array/string/number) -- rejected.</li>
    *   <li>{@code version} missing, not an integer, or not equal to {@link #CURRENT_VERSION} --
    *       rejected, naming both the expected and found value.</li>
    *   <li>{@code suffix} missing, null, or blank -- rejected (covers both A7's "missing field"
    *       and A10's "blank/null field" with one check).</li>
    *   <li>{@code xpath} same check.</li>
    *   <li>{@code columns} missing, not an array, or empty -- rejected (covers both A7 and A8).</li>
    *   <li>Each column, in order: {@code name} non-blank, {@code type} non-blank, {@code type} a
    *       member of {@link XSchema}'s declared constants (A9) -- first bad column fails the whole
    *       decode (fail-fast, not a batch of collected errors). {@code description} is read as
    *       nullable text: absent or JSON {@code null} becomes {@code null}; a JSON string is
    *       passed through verbatim, including a blank string, which stays {@code ""} rather than
    *       collapsing to {@code null} -- this token's data is presumed curated, not sampled from a
    *       messy live wire format the way Datagov's is. Anything else (a non-string, non-null
    *       value) is also treated as absent (mapped to {@code null}), never coerced.</li>
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
      JsonNode columnsNode = root.get("columns");

      if(columnsNode == null || !columnsNode.isArray() || columnsNode.isEmpty()) {
         throw new Exception("Rest.XML endpoint token must declare at least one column in " +
            "'columns'.");
      }

      List<Column> columns = new ArrayList<>();

      for(JsonNode columnNode : columnsNode) {
         String name = requireNonBlankText(columnNode, "name");
         String type = requireNonBlankText(columnNode, "type");

         if(!XSCHEMA_TYPES.contains(type)) {
            throw new Exception("Rest.XML endpoint token column '" + name + "' has type '" +
               type + "', which is not a recognized XSchema type constant.");
         }

         JsonNode descriptionNode = columnNode.get("description");
         String description = descriptionNode != null && descriptionNode.isTextual()
            ? descriptionNode.asText() : null;

         columns.add(new Column(name, type, description));
      }

      return new DecodedToken(suffix, xpath, columns);
   }

   private static String requireNonBlankText(JsonNode node, String field) throws Exception {
      JsonNode value = node == null ? null : node.get(field);

      if(value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
         throw new Exception("Rest.XML endpoint token field '" + field + "' is missing or " +
            "blank.");
      }

      return value.asText();
   }

   /**
    * Every {@code public static final String} constant {@link XSchema} itself declares --
    * reflected once rather than hand-copied, so this set cannot drift from {@code XSchema}'s own
    * vocabulary.
    */
   private static Set<String> reflectXSchemaConstants() {
      Set<String> types = new HashSet<>();

      for(Field field : XSchema.class.getDeclaredFields()) {
         if(field.getType() == String.class &&
            Modifier.isPublic(field.getModifiers()) &&
            Modifier.isStatic(field.getModifiers()) &&
            Modifier.isFinal(field.getModifiers()))
         {
            try {
               types.add((String) field.get(null));
            }
            catch(IllegalAccessException e) {
               throw new ExceptionInInitializerError(e);
            }
         }
      }

      return Set.copyOf(types);
   }

   private static final int CURRENT_VERSION = 1;
   private static final ObjectMapper MAPPER = new ObjectMapper();
   private static final Set<String> XSCHEMA_TYPES = reflectXSchemaConstants();
}
