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
package inetsoft.uql.rest.datasource.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.auth.RestAuthenticator;
import inetsoft.uql.rest.auth.RestAuthenticatorFactory;
import inetsoft.uql.tabular.HttpParameter;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The I/O shell for GraphQL introspection: sends the fixed introspection query against {@link
 * AbstractGraphQLDataSource}'s own URL and auth, and classifies the result into one of three
 * outcomes -- unreachable, HTTP-level rejection (commonly auth failure), or a GraphQL-level
 * {@code errors} array with no usable {@code data.__schema} (commonly introspection disabled).
 * {@link GraphQLCatalog} holds all the pure schema-walking logic that consumes what this class
 * returns; nothing here decides what is a dataset or a column.
 *
 * <h2>The {@code addParams} trap -- and why this is a SEPARATE request path</h2>
 * {@link GraphQLRuntime#addParams} ends with {@code dataSource.setQueryHttpParameters(...)} -- a
 * WRITE into the data source's own shared {@code queryHttpParameters}, which the user's real query
 * execution reuses on every subsequent {@code runQuery}. This class never calls that method, never
 * calls any setter on {@code dataSource}, and never touches a {@link GraphQLQuery} at all: it
 * builds its own {@link HttpPost} directly from the data source, so there is no query object whose
 * {@code queryString}/{@code variables} could be clobbered. If this were folded back into {@code
 * runQuery}'s own path to "avoid duplicating the request-building code", it would re-trigger {@code
 * addParams} on the SAME shared data source object and silently corrupt the user's configured
 * query -- that is the whole reason introspection gets its own request path instead of reusing
 * {@code runQuery}'s.
 *
 * <p>What IS shared, deliberately, is the same two read-only auth layers a real query applies, so
 * the introspection request is indistinguishable from a real authenticated call to the same
 * endpoint:
 * <ol>
 *   <li>The connector-specific fixed header -- {@link AbstractGraphQLDataSource#getRequestParameters()}
 *       ({@code OAuthDataSource}'s default-interface method; an empty array for plain {@code
 *       GraphQL}, one fixed header each for {@code shopify}/{@code monday.com}). Applied directly
 *       to the {@link HttpPost}.</li>
 *   <li>The generic {@code AuthType}-driven header -- {@link RestAuthenticatorFactory#createFrom}.
 *       {@link RestAuthenticator#authenticateRequest} only decorates the request object handed to
 *       it; it does not write back to the data source, with one stated, orthogonal exception: an
 *       expired OAuth token's refresh (via {@code OAuthDataSource.updateTokens}) persists a new
 *       token, the same as it would for any other authenticated call with an expired token -- that
 *       is ordinary connection maintenance, not "reusing the query-execution parameter path".</li>
 * </ol>
 *
 * <h2>No cache, and what that costs</h2>
 * Every call -- {@code listDatasets} AND {@code describeDataset} -- re-sends the full introspection
 * query; there is no cache between the two phases, and none across calls (an explicit non-goal
 * this round). For a source with a large schema (Shopify's Admin API schema is multi-megabyte),
 * this means describing N datasets pays N full introspection round trips, not one per annotation
 * run. Caching is possible in principle for {@code shopify} and {@code monday.com} specifically --
 * their schemas are vendor-fixed, not tenant-authored, unlike a user-supplied {@code GraphQL} data
 * source whose schema is whatever their own server declares -- but the cache key would have to be
 * {@code sourceType + apiVersion}, never {@code sourceType} alone, since Shopify versions its
 * schema per {@code ShopifyDataSource#getApiVersion()}. Not implemented this round.
 */
final class GraphQLIntrospectionClient {
   private GraphQLIntrospectionClient() {
   }

   /**
    * @return the {@code data.__schema} node of a successful introspection response.
    * @throws Exception naming which of three failure modes occurred: unreachable/network,
    *         HTTP-level rejection, or a GraphQL {@code errors} array with no usable {@code
    *         data.__schema}.
    */
   static JsonNode introspect(AbstractGraphQLDataSource<?> dataSource) throws Exception {
      String url = dataSource.getURL();

      if(url == null || url.isEmpty()) {
         throw new IllegalStateException("GraphQL data source has no URL configured.");
      }

      CloseableHttpAsyncClient client = HttpAsyncClients.custom()
         .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(READ_TIMEOUT_MS)
            .build())
         .build();
      client.start();

      try {
         HttpPost post = new HttpPost(url);
         post.setEntity(new StringEntity(requestBody(), ContentType.APPLICATION_JSON));

         // The connector-specific fixed header (§2 item 1 above) -- read-only, applied directly.
         for(HttpParameter param : dataSource.getRequestParameters()) {
            if(param.getType() == HttpParameter.ParameterType.HEADER) {
               post.addHeader(param.getName(), param.getValue());
            }
         }

         HttpClientContext context = HttpClientContext.create();
         RestAuthenticator authenticator = RestAuthenticatorFactory.createFrom(dataSource, client);
         authenticator.authenticateRequest(post, context);

         HttpResponse response;

         try {
            response = client.execute(post, context, null)
               .get(READ_TIMEOUT_MS * 2L, TimeUnit.MILLISECONDS);
         }
         catch(Exception e) {
            throw new Exception("Failed to connect to GraphQL endpoint '" + url +
               "' for introspection: " + rootMessage(e), e);
         }

         int status = response.getStatusLine().getStatusCode();
         String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());

         if(status < 200 || status >= 300) {
            throw new Exception("GraphQL endpoint '" + url + "' rejected the introspection " +
               "request (HTTP " + status + ").");
         }

         JsonNode root;

         try {
            root = MAPPER.readTree(body);
         }
         catch(IOException e) {
            throw new Exception("GraphQL endpoint '" + url + "' returned a non-JSON response to " +
               "the introspection request.", e);
         }

         if(root == null) {
            throw new Exception("GraphQL endpoint '" + url + "' returned an empty response body " +
               "to the introspection request.");
         }

         JsonNode errors = root.path("errors");

         if(errors.isArray() && !errors.isEmpty()) {
            StringBuilder messages = new StringBuilder();

            for(JsonNode error : errors) {
               if(messages.length() > 0) {
                  messages.append("; ");
               }

               messages.append(error.path("message").asText("(no message)"));
            }

            // A distinct, actionable message from the unreachable/auth-failure cases above --
            // "introspection is disabled" is an ordinary production security posture (arriving as
            // HTTP 200 with a GraphQL-level errors array), not a connectivity or credentials
            // problem, and telling those apart is exactly what a person fixing this needs first.
            throw new Exception("GraphQL endpoint '" + url + "' returned introspection errors, " +
               "which usually means introspection is disabled on this server: " + messages + ".");
         }

         JsonNode schema = root.path("data").path("__schema");

         if(schema.isMissingNode() || schema.isNull()) {
            throw new Exception("GraphQL endpoint '" + url + "' returned no usable data.__schema " +
               "from the introspection request, and no errors array explaining why.");
         }

         return schema;
      }
      finally {
         client.close();
      }
   }

   private static String requestBody() throws IOException {
      // No "variables" key: the introspection query takes no arguments, so omitting it is the
      // simpler of two spec-legal choices (over sending "variables": null).
      return MAPPER.writeValueAsString(Map.of("query", INTROSPECTION_QUERY));
   }

   private static String rootMessage(Throwable t) {
      Throwable cause = t;

      while(cause.getCause() != null) {
         cause = cause.getCause();
      }

      return cause.getMessage() != null ? cause.getMessage() : cause.toString();
   }

   private static final int CONNECT_TIMEOUT_MS = 15_000;
   private static final int READ_TIMEOUT_MS = 30_000;
   private static final ObjectMapper MAPPER = new ObjectMapper();

   /**
    * The fixed introspection query. {@code queryType { name }} locates the root Query type among
    * {@code types} (nothing guarantees it is literally named {@code "Query"}). {@code args { name
    * }} on every field is read for pagination-argument detection (names only -- no query ever
    * needs to construct an argument VALUE at introspection time). The {@code ofType} chain is 6
    * levels deep (see {@code GraphQLCatalog}'s {@code MAX_UNWRAP_DEPTH}) -- real schemas rarely
    * exceed 2-3 wrapper levels; a field wrapped deeper unwraps to an unrecognized kind/name and is
    * treated as an unresolved type, never a crash. {@code includeDeprecated: true} because a
    * deprecated-but-still-queryable field is still a real, usable column -- deprecation is a UI
    * hint, not an access restriction. Deliberately omits {@code interfaces}/{@code possibleTypes}/
    * {@code enumValues}/{@code inputFields}/{@code directives}: none are needed (see {@code
    * GraphQLCatalog}'s treatment of interface/union/enum-typed fields).
    */
   static final String INTROSPECTION_QUERY = """
      query StyleBIIntrospection {
        __schema {
          queryType { name }
          types {
            kind
            name
            description
            fields(includeDeprecated: true) {
              name
              description
              args { name }
              type {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                    ofType {
                      kind
                      name
                      ofType {
                        kind
                        name
                        ofType {
                          kind
                          name
                          ofType {
                            kind
                            name
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      """;
}
