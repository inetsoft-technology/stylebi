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
package inetsoft.uql.rest.datasource.airtable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.HttpHandler;
import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.RestResponseException;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.rest.json.RestQuotaManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The I/O shell for Airtable's Meta API: {@code GET /v0/meta/bases} (every base the token can see,
 * paged by {@code offset}) and {@code GET /v0/meta/bases/{baseId}/tables} (one base's tables with
 * their fields, not paged). {@link AirtableCatalog} holds the logic that reads what comes back.
 *
 * <p>Both calls need a token carrying the {@code schema.bases:read} scope, which a token made only
 * to read records does not have. Airtable answers such a token with 403, and that status is
 * translated here into a message that names the scope -- an empty catalog, or a bare "403", would
 * send the user looking in the wrong place.
 *
 * <p>Requests go through the same {@link HttpHandler} and the same data source authentication a
 * record query uses (base URL, bearer header, proxy, request quota), built from a throwaway
 * {@link RestJsonQuery}. Nothing is written to the data source or to any user query.
 */
final class AirtableMetaClient {
   AirtableMetaClient(AirtableDataSource dataSource) {
      this.dataSource = dataSource;
   }

   /**
    * @return every base the token can see, in Airtable's order, across all pages. Never null.
    */
   List<JsonNode> listBases() throws Exception {
      List<JsonNode> bases = new ArrayList<>();
      Set<String> seenOffsets = new HashSet<>();
      String offset = null;

      do {
         String suffix = "v0/meta/bases" +
            (offset == null ? "" : "?offset=" + URLEncoder.encode(offset, StandardCharsets.UTF_8));
         JsonNode body = get(suffix, "list the bases");
         JsonNode page = body.get("bases");

         if(page == null || !page.isArray()) {
            throw new IllegalStateException("Airtable's response to the list-bases request had no 'bases' " +
                                  "array; refusing to report an empty catalog.");
         }

         page.forEach(bases::add);
         JsonNode next = body.get("offset");
         offset = next == null || next.isNull() || next.asText().isEmpty() ? null : next.asText();

         if(offset != null && !seenOffsets.add(offset)) {
            throw new IllegalStateException("Airtable returned the same page offset twice while listing " +
                                  "bases; stopping instead of looping.");
         }
      }
      while(offset != null);

      return bases;
   }

   /**
    * @return the tables of one base, each with its fields. Never null.
    */
   List<JsonNode> listTables(String baseId) throws Exception {
      JsonNode body = get("v0/meta/bases/" + baseId + "/tables", "list the tables of base " + baseId);
      JsonNode tables = body.get("tables");

      if(tables == null || !tables.isArray()) {
         throw new IllegalStateException("Airtable's response for base " + baseId + " had no 'tables' " +
                               "array; refusing to report an empty catalog.");
      }

      List<JsonNode> result = new ArrayList<>();
      tables.forEach(result::add);
      return result;
   }

   private JsonNode get(String suffix, String what) throws Exception {
      RestJsonQuery query = new RestJsonQuery(dataSource.getType()) {};
      query.setDataSource(dataSource);
      query.setSuffix(suffix);

      return RestQuotaManager.withQuota(dataSource, () -> execute(query, what));
   }

   private JsonNode execute(RestJsonQuery query, String what) throws Exception {
      try(HttpHandler handler = new HttpHandler();
          HttpResponse response = handler.executeRequest(RestRequest.fromQuery(query)))
      {
         try(InputStream in = response.getResponseBodyAsStream()) {
            JsonNode body = in == null ? null : MAPPER.readTree(in);

            if(body == null || !body.isObject()) {
               throw new IllegalStateException("Airtable returned no JSON object while trying to " + what + ".");
            }

            return body;
         }
      }
      catch(RestResponseException e) {
         throw new IOException(describeFailure(e, what), e);
      }
      catch(IOException e) {
         Throwable root = e;

         while(root.getCause() != null) {
            root = root.getCause();
         }

         throw new IOException("The request to Airtable failed while trying to " + what + ": " +
                               (root.getMessage() == null ? root.toString() : root.getMessage()), e);
      }
   }

   /**
    * One line, so it survives the layers that truncate at the first newline. {@code
    * HttpHandler} appends the response body to the exception message after a newline, which is
    * deliberately not echoed here.
    */
   static String describeFailure(RestResponseException e, String what) {
      int status = e.getResponse() == null ? -1 : e.getResponse().getResponseStatusCode();

      return switch(status) {
         case 401 -> "Airtable rejected the API token (HTTP 401) while trying to " + what +
            ". Check that the data source's API token is valid and has not been revoked.";
         case 403 -> "Airtable refused to " + what + " (HTTP 403). Listing tables needs an API " +
            "token with the 'schema.bases:read' scope and access to the base; a token made only " +
            "for reading records does not have it. Add the scope to the token and grant it the " +
            "bases, then retry.";
         case 404 -> "Airtable could not find what it was asked to " + what + " (HTTP 404). The " +
            "base may not exist or the token may not have access to it.";
         case 429 -> "Airtable rate-limited the request to " + what + " (HTTP 429). Retry in a " +
            "moment.";
         default -> "Airtable failed to " + what + " (HTTP " + status + ").";
      };
   }

   private final AirtableDataSource dataSource;
   private static final ObjectMapper MAPPER = new ObjectMapper();
}
