/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.auth;

import inetsoft.uql.rest.HttpParameterHandler;
import inetsoft.uql.tabular.HttpParameter;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Authenticates the request by first making an HTTP request to an authentication endpoint to
 * retrieve an authentication token. This token is then added to the user query to authenticate it.
 */
public class TwoStepAuthenticator implements RestAuthenticator {
   TwoStepAuthenticator(TwoStepAuthConfig config, HttpAsyncClient client) {
      this.config = config;
      this.client = client;
   }

   @Override
   public void authenticateRequest(HttpRequestBase request, HttpClientContext context)
      throws IOException, URISyntaxException, InterruptedException
   {
      final String token = getToken();
      final List<HttpParameter> params = getProcessedHttpParams(token);
      addHttpParameters(request, params);
   }

   private String getToken() throws IOException, URISyntaxException, InterruptedException {
      HttpRequestBase request = createRequest();
      addHttpParameters(request, config.authenticationHttpParameters());

      try {
         org.apache.http.HttpResponse response = client.execute(request, null).get();

         if(response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() >= 300) {
            throw new IOException(
               "Authentication request was not successful: " +
                  response.getStatusLine().getReasonPhrase());
         }

         final String responseBody = EntityUtils.toString(response.getEntity());
         return getTokenFromResponseBody(responseBody);
      }
      catch(ExecutionException e) {
         if(e.getCause() instanceof IOException) {
            throw (IOException) e.getCause();
         }

         if(e.getCause() instanceof InterruptedException) {
            throw (InterruptedException) e.getCause();
         }

         throw new IOException("Failed to execute HTTP request", e);
      }
   }

   private List<HttpParameter> getProcessedHttpParams(String token) {
      return config.queryHttpParameters().stream()
         .filter(Objects::nonNull)
         .map((oldParam) -> {
            final String value = oldParam.getValue().replace(TOKEN_PLACEHOLDER, token);
            final HttpParameter newParam = new HttpParameter();
            newParam.setType(oldParam.getType());
            newParam.setName(oldParam.getName());
            newParam.setValue(value);
            return newParam;
         })
         .collect(Collectors.toList());
   }

   private HttpRequestBase createRequest() {
      HttpRequestBase request;

      switch(config.authMethod()) {
         case GET:
            request = new HttpGet();
            break;
         case POST:
            HttpPost post = new HttpPost();
            final String body = config.body() != null ? config.body() : "";
            post.setEntity(new StringEntity(
               body, ContentType.create(config.contentType(), "UTF-8")));
            request = post;
            break;
         default:
            throw new IllegalArgumentException("Unexpected value: " + config.authMethod());
      }

      request.setURI(URI.create(config.authURL()));
      return request;
   }

   private void addHttpParameters(HttpRequestBase request, List<HttpParameter> params)
      throws URISyntaxException
   {
      new HttpParameterHandler().addHttpParameters(request, params);
   }

   private String getTokenFromResponseBody(String responseBody) {
      final Pattern pattern = Pattern.compile(config.tokenPattern());
      final Matcher matcher = pattern.matcher(responseBody);

      if(!matcher.find()) {
         final String message =
            "Could not match the response body output to the token pattern: " + responseBody;
         throw new IllegalArgumentException(message);
      }

      if(matcher.groupCount() == 0) {
         throw new IllegalArgumentException("Could not match a group");
      }

      if(matcher.groupCount() > 1) {
         throw new IllegalArgumentException("Too many groups matched in the token pattern.");
      }

      final String token = matcher.group(1);
      LOG.info("Matched token: {}", token);
      return token;
   }

   @Override
   public boolean writesQueryParameters() {
      return true;
   }

   private final TwoStepAuthConfig config;
   private final HttpAsyncClient client;

   private static final String TOKEN_PLACEHOLDER = "{{token}}";
   private static final Logger LOG = LoggerFactory.getLogger(TwoStepAuthenticator.class.getName());
}
