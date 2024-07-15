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

import inetsoft.uql.rest.json.RestJsonQuery;
import org.apache.http.client.utils.URIBuilder;

import java.net.*;
import java.util.Map;

/**
 * Class concerned with creating URLs.
 */
public class URLCreator {
   private URLCreator(RestRequest restRequest) {
      this.restRequest = restRequest;
   }

   /**
    * @return the url corresponding to the rest request.
    */
   public static URL fromRestRequest(RestRequest request) throws MalformedURLException, URISyntaxException {
      if(request.url() != null) {
         return request.url();
      }

      return new URLCreator(request).createUrl();
   }

   /**
    * @return the url corresponding to the rest query.
    */
   public static URL fromQuery(AbstractRestQuery query) throws MalformedURLException, URISyntaxException {
      return fromRestRequest(RestRequest.fromQuery(query));
   }

   URL createUrl() throws MalformedURLException, URISyntaxException {
      return new URL(createUrlString());
   }

   String createUrlString() throws URISyntaxException {
      String urlString = combineDataSourceAndQueryUrlFragments();
      urlString = replaceUrlParameters(urlString);
      urlString = addQueryParameters(urlString);

      return urlString;
   }

   private String combineDataSourceAndQueryUrlFragments() {
      String url = restRequest.query().getURL();
      String suffix = restRequest.query().getSuffix();

      if(suffix != null) {
         if(restRequest.query() instanceof RestJsonQuery &&
            ((RestJsonQuery) restRequest.query()).isIgnoreBaseUrl())
         {
            url = suffix;
         }
         else if(url.endsWith("/") && suffix.startsWith("/")) {
            url += suffix.substring(1);
         }
         else if(url.endsWith("?")) {
            url += suffix;
         }
         else if(!url.endsWith("/") && !suffix.startsWith("/")) {
            url += "/" + suffix;
         }
         else {
            url += suffix;
         }
      }

      // only encode space (which is never allowed in url) but other characters
      // would need to be encoded explicitly to avoid confusion
      url = url.replace(" ", "%20");
      return url;
   }

   private String replaceUrlParameters(String urlString) {
      final Map<String, String> variables = restRequest.urlVariables();

      for(final Map.Entry<String, String> entry : variables.entrySet()) {
         final String replaceStr = String.format("{{%s}}", entry.getKey());
         urlString = urlString.replace(replaceStr, entry.getValue());
      }

      return urlString;
   }

   private String addQueryParameters(String urlString) throws URISyntaxException {
      final URIBuilder uriBuilder = new URIBuilder(urlString);
      final Map<String, String> params = restRequest.queryParameters();

      for(final Map.Entry<String, String> entry : params.entrySet()) {
         uriBuilder.setParameter(entry.getKey(), entry.getValue());
      }

      return uriBuilder.toString();
   }

   private final RestRequest restRequest;
}
