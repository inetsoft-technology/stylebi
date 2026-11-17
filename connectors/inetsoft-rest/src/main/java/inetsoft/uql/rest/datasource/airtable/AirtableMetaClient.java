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
import inetsoft.sree.security.OrganizationManager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
      this(dataSource, Thread::sleep);
   }

   AirtableMetaClient(AirtableDataSource dataSource, Sleeper sleeper) {
      this.dataSource = dataSource;
      this.sleeper = sleeper;
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
      return listTables(baseId, false);
   }

   /**
    * @param useCache true to accept a schema fetched for the same data source and base within the
    *                 last {@value #SCHEMA_TTL_SECONDS} seconds, so describing many tables of one base
    *                 costs one fetch. A listing never reads the cache (it must see new tables) but
    *                 always refreshes it. A failure is never cached.
    */
   List<JsonNode> listTables(String baseId, boolean useCache) throws Exception {
      String key = cacheKey(baseId);

      if(useCache) {
         List<JsonNode> cached = SCHEMAS.getIfPresent(key);

         if(cached != null) {
            return cached;
         }
      }

      List<JsonNode> tables = fetchTables(baseId);
      SCHEMAS.put(key, tables);
      return tables;
   }

   private List<JsonNode> fetchTables(String baseId) throws Exception {
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

      // retried outside the quota so a waiting call does not hold a slot
      for(int attempt = 0;; attempt++) {
         try {
            return RestQuotaManager.withQuota(dataSource, () -> execute(query, what));
         }
         catch(HttpFailure e) {
            if(attempt >= MAX_RETRIES || !isTransient(e.status)) {
               throw e;
            }

            long wait = e.retryAfterSeconds >= 0 ? e.retryAfterSeconds * 1000L :
               1000L * (attempt + 1);
            sleeper.sleep(Math.min(wait, MAX_WAIT_MS));
         }
      }
   }

   private static boolean isTransient(int status) {
      return status == 429 || status == 502 || status == 503 || status == 504;
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
         int status = e.getResponse() == null ? -1 : e.getResponse().getResponseStatusCode();
         String retryAfter = e.getResponse() == null ? null :
            e.getResponse().getResponseHeaderValue("Retry-After");
         throw new HttpFailure(describeFailure(e, what), status, parseSeconds(retryAfter), e);
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

   /**
    * Organization, data source name, a hash of the token (never the token itself) and base: a
    * same-named data source of another organization, or the same one with another token, must
    * not be answered from this one's schema, since that would skip its own token's access check.
    */
   private String cacheKey(String baseId) {
      String token = dataSource.getApiToken();
      String digest;

      try {
         digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8)));
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }

      return OrganizationManager.getInstance().getCurrentOrgID() + "\0" +
         dataSource.getFullName() + "\0" + digest + "\0" + baseId;
   }

   private static long parseSeconds(String value) {
      try {
         return value == null ? -1 : Math.max(0, Long.parseLong(value.trim()));
      }
      catch(NumberFormatException e) {
         return -1;   // an HTTP-date form is not honored; the default back-off applies
      }
   }

   /** A non-2xx answer, keeping what the retry decision needs. */
   static final class HttpFailure extends IOException {
      HttpFailure(String message, int status, long retryAfterSeconds, Throwable cause) {
         super(message, cause);
         this.status = status;
         this.retryAfterSeconds = retryAfterSeconds;
      }

      final int status;
      final long retryAfterSeconds;
   }

   @FunctionalInterface
   interface Sleeper {
      void sleep(long millis) throws InterruptedException;
   }

   static final int MAX_RETRIES = 3;
   static final long MAX_WAIT_MS = 30_000L;
   static final int SCHEMA_TTL_SECONDS = 30;
   private static final Cache<String, List<JsonNode>> SCHEMAS = Caffeine.newBuilder()
      .expireAfterWrite(SCHEMA_TTL_SECONDS, TimeUnit.SECONDS).maximumSize(200).build();

   private final AirtableDataSource dataSource;
   private final Sleeper sleeper;
   private static final ObjectMapper MAPPER = new ObjectMapper();
}
