/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.auth.RestAuthenticator;
import inetsoft.uql.rest.auth.RestAuthenticatorFactory;
import inetsoft.uql.tabular.HttpParameter;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.*;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.client.HttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Class which handles building and executing HTTP requests.
 */
public class HttpHandler implements IHttpHandler {
   public HttpHandler() {
      this("application/json");
   }

   public HttpHandler(String acceptHeader) {
      this.acceptHeader = acceptHeader;
   }

   @Override
   public HttpResponse executeRequest(RestRequest request)
      throws IOException, URISyntaxException, InterruptedException
   {
      final URL url = URLCreator.fromRestRequest(request);
      return executeHttpQueryWithUrl(request.query(), url);
   }

   private HttpResponse executeHttpQueryWithUrl(AbstractRestQuery query, URL url)
      throws IOException, URISyntaxException, InterruptedException
   {
      final HttpClientContext context = HttpClientContext.create();
      final HttpRequestBase request = createRequest(query, url, context);
      return executeMethod(request, context);
   }

   @Override
   public void close() throws IOException {
      if(client != null) {
         client.close();
         client = null;
      }
   }

   private HttpRequestBase createRequest(AbstractRestQuery query, URL url, HttpClientContext context)
      throws IOException, URISyntaxException, InterruptedException
   {
      final HttpRequestBase request = getHttpRequest(query, url);
      // missing User-Agent may cause 403 error (jsonplaceholder.typicode.com)
      request.addHeader("User-Agent", "Mozilla/4.76");
      final RestAuthenticator restAuthenticator = getRestAuthenticator(query);
      addDataSourceHttpParameters(query, request, restAuthenticator);
      restAuthenticator.authenticateRequest(request, context);

      if(request.getFirstHeader("Accept") == null) {
         request.addHeader("Accept", this.acceptHeader);
      }

      return request;
   }

   private void addDataSourceHttpParameters(AbstractRestQuery query,
                                            HttpRequestBase request,
                                            RestAuthenticator restAuthenticator)
      throws URISyntaxException
   {
      if(!restAuthenticator.writesQueryParameters()) {
         final AbstractRestDataSource dataSource = getDataSource(query);
         final HttpParameter[] params = dataSource.getQueryHttpParameters();

         if(params != null) {
            new HttpParameterHandler().addHttpParameters(request, Arrays.asList(params));
         }
      }
   }

   private AbstractRestDataSource getDataSource(AbstractRestQuery query) {
      return (AbstractRestDataSource) query.getDataSource();
   }

   private RestAuthenticator getRestAuthenticator(AbstractRestQuery query) {
      AbstractRestDataSource ds = getDataSource(query);
      return RestAuthenticatorFactory.createFrom(ds, getClient());
   }

   private HttpRequestBase getHttpRequest(AbstractRestQuery query, URL url) {
      HttpRequestBase request;

      if(query.isPostRequest()) {
         request = preparePostRequest(query, url);
      }
      else {
         request = new HttpGet(url.toString());
      }

      return request;
   }

   private HttpPost preparePostRequest(AbstractRestQuery query, URL url) {
      HttpPost post = new HttpPost(url.toExternalForm());
      String requestBody = query.getRequestBody() != null ? query.getRequestBody() : "";
      post.setEntity(new StringEntity(requestBody, ContentType.create(query.getContentType())));
      return post;
   }

   private HttpResponse executeMethod(HttpRequestBase request, HttpClientContext context)
      throws IOException, InterruptedException
   {
      try {
         // Use an async call and then immediately call get() on the future. This allows the current
         // thread to be interrupted if the query is cancelled. If blocking I/O is used, the thread
         // cannot be interrupted until any socket wait time is completed, which could make the
         // application unresponsive.
         org.apache.http.HttpResponse response = getClient().execute(request, context, null).get();
         log(request, response);

         if(response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() >= 300)
         {
            String msg = String.format(
                  "Http request for resource %s was not successful: %d %s",
                  request.getURI(), response.getStatusLine().getStatusCode(),
                  response.getStatusLine().getReasonPhrase());
            final HttpEntity entity = response.getEntity();

            if(entity != null && entity.getContentLength() > 0) {
               try {
                  BufferedReader reader = new BufferedReader(
                     new InputStreamReader(entity.getContent()));
                  msg += "\n" + reader.lines().collect(Collectors.joining("\n"));
               }
               catch(Exception ex) {
                  // ignore
               }
            }

            throw new RestResponseException(msg, new HttpResponseWrapper(request, response));
         }

         return new HttpResponseWrapper(request, response);
      }
      catch(ExecutionException e) {
         if(e.getCause() instanceof IOException) {
            throw (IOException) e.getCause();
         }
         else if(e.getCause() instanceof InterruptedException) {
            throw (InterruptedException) e.getCause();
         }
         else {
            throw new IOException("Failed to execute HTTP request", e);
         }
      }
   }

   private synchronized HttpAsyncClient getClient() {
      if(client == null) {
         client = HttpAsyncClients.createDefault();
         client.start();
      }

      return client;
   }

   private void log(HttpRequestBase request, org.apache.http.HttpResponse response) {
      if(LOG.isDebugEnabled()) {
         StringBuilder requestHeaders = new StringBuilder();
         StringBuilder responseHeaders = new StringBuilder();

         for(Header header : request.getAllHeaders()) {
            String value = header.getValue();

            if("Authorization".equalsIgnoreCase(header.getName())) {
               int index = value.indexOf(' ');

               if(index < 0) {
                  //noinspection ReplaceAllDot
                  requestHeaders.append("\n  ").append(header.getName())
                     .append(": ").append(value.replaceAll(".", "*"));
               }
               else {
                  //noinspection ReplaceAllDot
                  requestHeaders.append("\n ").append(header.getName()).append(": ")
                     .append(value, 0, index + 1)
                     .append(value.substring(index + 1).replaceAll(".", "*"));
               }
            }
            else {
               requestHeaders.append("\n ").append(header.getName()).append(": ")
                  .append(header.getValue());
            }
         }

         for(Header header : response.getAllHeaders()) {
            responseHeaders.append("\n ").append(header.getName()).append(": ")
               .append(header.getValue());
         }

         Header contentType = response.getEntity().getContentType();

         if(contentType != null && contentType.getValue() != null &&
            contentType.getValue().startsWith("application/json"))
         {
            try {
               if(!(response.getEntity() instanceof BufferedHttpEntity)) {
                  response.setEntity(new BufferedHttpEntity(response.getEntity()));
               }

               ObjectMapper mapper = new ObjectMapper();
               JsonNode json = mapper.readTree(response.getEntity().getContent());
               String body = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);

               LOG.debug(
                  "\nHTTP request: {} {}\nRequest headers: {}\nHTTP response: {}" +
                     "\nResponse headers: {}\nResponse body: {}",
                  request.getMethod(), request.getURI(), requestHeaders, response.getStatusLine(),
                  responseHeaders, body);
               return;
            }
            catch(IOException e) {
               LOG.debug("Failed to log JSON response body", e);
            }
         }

         LOG.debug(
            "\nHTTP request: {} {}\nRequest headers: {}\nHTTP response: {}\nResponse headers: {}",
            request.getMethod(), request.getURI(), requestHeaders, response.getStatusLine(),
            responseHeaders);
      }
   }

   private CloseableHttpAsyncClient client;
   private final String acceptHeader;
   private static final Logger LOG = LoggerFactory.getLogger(HttpHandler.class.getName());
}
