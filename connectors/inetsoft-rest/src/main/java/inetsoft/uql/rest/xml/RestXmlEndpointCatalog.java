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
import inetsoft.uql.tabular.TabularColumn;
import inetsoft.uql.tabular.TabularDatasetSchema;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles {@link RestXMLRuntime}'s {@link inetsoft.uql.tabular.TabularCatalogProvider}
 * {@code describeDataset} answer out of a decoded opaque endpoint token. Mirrors {@code
 * MongoCatalog}/{@code DatagovCatalog}'s role: package-private, static methods, no state of its
 * own.
 */
final class RestXmlEndpointCatalog {
   private RestXmlEndpointCatalog() {
   }

   /**
    * @param token the opaque, self-describing endpoint token -- see
    *              {@link RestXmlEndpointTokenCodec}.
    * @return a schema whose {@code datasetId()} echoes {@code token} verbatim, {@code columns()}
    *         is the token's {@code responseSchema} tree flattened depth-first in JSON key order
    *         (see {@link #flatten}), {@code keyColumns()} is always empty (the token format
    *         carries no key-column designation), {@code params()} carries exactly {@code
    *         suffix}/{@code xpath}, {@code columnsMayBeIncomplete()} is always {@code true}
    *         (every column comes from an LLM-extraction-plus-human-review pipeline, never the
    *         source's own declared, machine-read metadata), and {@code description()} is always
    *         {@code null} (the tree grammar carries no per-leaf or dataset-level description --
    *         a capability v1's flat format had and v2 deliberately drops, see this round's design
    *         doc).
    * @throws Exception if {@code token} is structurally invalid -- see
    *                   {@link RestXmlEndpointTokenCodec#decode} -- or if {@code responseSchema}
    *                   flattens to zero concrete columns (a lone {@code "*"}-keyed object, whose
    *                   real key names are only known at query time). Never makes a network call.
    */
   static TabularDatasetSchema describeDataset(String token) throws Exception {
      RestXmlEndpointTokenCodec.DecodedToken decoded = RestXmlEndpointTokenCodec.decode(token);
      List<TabularColumn> columns = new ArrayList<>();
      flatten(decoded.responseSchema(), null, columns);

      if(columns.isEmpty()) {
         // TabularDatasetSchema's own contract (javadoc on the `columns` param): "An implementation
         // that finds no columns throws rather than returning an empty list." Reachable when
         // responseSchema is a lone wildcard object (see flatten()'s "*" handling) with nothing
         // else to flatten.
         throw new Exception("Rest.XML endpoint token's responseSchema describes no concrete " +
            "columns (its only content is a dynamically-keyed '*' object, whose real key names " +
            "are only known at query time, not at describe time).");
      }

      Map<String, String> params = new LinkedHashMap<>();
      params.put("suffix", decoded.suffix());
      params.put("xpath", decoded.xpath());

      // datasetId is the ORIGINAL `token` argument, not a re-serialization of `decoded` -- A3
      // requires verbatim echo, and re-encoding risks a byte-for-byte mismatch (whitespace, key
      // order, base64 padding) even when the content is semantically identical.
      return new TabularDatasetSchema(token, columns, List.of(), params, true);
   }

   /**
    * Depth-first, in JSON key order (Jackson {@code ObjectNode} preserves insertion order == source
    * -document order) -- the same order the token's author (a human, or the wiz LLM extraction
    * pipeline) wrote the tree's keys in, and the same order the TS-side encoder's own
    * {@code JSON.stringify} preserves, so the same token flattens to the identical column order on
    * both sides with zero extra bookkeeping.
    *
    * <p>Object-nesting and array-of-1-repeating use the IDENTICAL dot-join naming rule here,
    * mirroring {@code ExpandedJsonTable}'s own real flattening algorithm exactly (a column-set
    * flattener never needs to distinguish "nested object" from "repeating element", only a row-
    * counter would -- and this method only ever reports columns, never rows).
    */
   private static void flatten(JsonNode node, String prefix, List<TabularColumn> out) {
      if(node.isTextual()) {
         // prefix == null only at the root (a bare-string responseSchema, no object/array wrapper).
         // "Column" mirrors ExpandedJsonTable.processJson's own literal for exactly this case
         // ("for loading array of literal values" -- ExpandedJsonTable.java:212-214). label/
         // isDimension are always null: this connector has no source-declared basis for either --
         // guessing would violate TabularColumn's own "never invented" contract.
         String name = prefix == null ? "Column" : prefix;
         out.add(new TabularColumn(name, node.asText(), null, null, null));
         return;
      }

      if(node.isArray()) {
         // Already validated (RestXmlEndpointTokenCodec.decode) as length exactly 1. A repeating
         // element's own sub-fields flatten under the SAME prefix the array itself was keyed
         // under -- row-multiplication is invisible to column identity, so no "[*]" marker (that
         // marker exists only in the codec's own diagnostic `at` path for error messages, never
         // as part of a real column name).
         flatten(node.get(0), prefix, out);
         return;
      }

      // isObject(): validated non-empty, and "*" (if present) is the ONLY key.
      Iterator<String> keys = node.fieldNames();

      while(keys.hasNext()) {
         String key = keys.next();

         if("*".equals(key)) {
            // A dynamically-keyed subtree has no real key name to dot-join with at describe time.
            // Skipped entirely, not recursed into -- columnsMayBeIncomplete is already
            // unconditionally true for this connector, which is exactly the honest signal for
            // "there are more columns than what's listed here." Not a violation of "never
            // silently drop a declared column": a "*" node does not declare a real, nameable
            // column, it declares "an enumerable-only-at-runtime region exists here."
            continue;
         }

         String name = prefix == null ? key : prefix + "." + key;
         flatten(node.get(key), name, out);
      }
   }
}
