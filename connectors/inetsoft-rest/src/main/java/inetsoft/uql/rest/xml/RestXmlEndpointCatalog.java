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

import inetsoft.uql.tabular.TabularColumn;
import inetsoft.uql.tabular.TabularDatasetSchema;

import java.util.ArrayList;
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
    *         matches the token's flat column list in order, {@code keyColumns()} is always empty
    *         (the token format carries no key-column designation), {@code params()} carries
    *         exactly {@code suffix}/{@code xpath}, {@code columnsMayBeIncomplete()} is always
    *         {@code true} (every column comes from an LLM-extraction-plus-human-review pipeline,
    *         never the source's own declared, machine-read metadata), and {@code description()}
    *         is always {@code null} (the token format carries no dataset-level description).
    * @throws Exception if {@code token} is structurally invalid -- see
    *                   {@link RestXmlEndpointTokenCodec#decode}. Never makes a network call.
    */
   static TabularDatasetSchema describeDataset(String token) throws Exception {
      RestXmlEndpointTokenCodec.DecodedToken decoded = RestXmlEndpointTokenCodec.decode(token);
      List<TabularColumn> columns = new ArrayList<>();

      for(RestXmlEndpointTokenCodec.Column c : decoded.columns()) {
         // label/isDimension are always null: the token carries only name/type/description
         // (charter A4), and this connector has no source-declared basis for the other two --
         // guessing either would violate TabularColumn's own "never invented" contract.
         columns.add(new TabularColumn(c.name(), c.type(), c.description(), null, null));
      }

      Map<String, String> params = new LinkedHashMap<>();
      params.put("suffix", decoded.suffix());
      params.put("xpath", decoded.xpath());

      // datasetId is the ORIGINAL `token` argument, not a re-serialization of `decoded` -- A3
      // requires verbatim echo, and re-encoding risks a byte-for-byte mismatch (whitespace, key
      // order, base64 padding) even when the content is semantically identical.
      return new TabularDatasetSchema(token, columns, List.of(), params, true);
   }
}
