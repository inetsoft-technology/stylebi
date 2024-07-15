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
package inetsoft.uql.rest.datasource.twitter;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.lang.invoke.MethodHandles;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TwitterAuthClient {
   public TwitterAuthClient() {
      final FormHttpMessageConverter formConverter = new FormHttpMessageConverter();
      final MediaType mediaType = new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8);
      formConverter.addSupportedMediaTypes(mediaType);
      restTemplate = new RestTemplate();
      final List<HttpMessageConverter<?>> messageConverters = restTemplate.getMessageConverters();
      messageConverters.add(formConverter);
      restTemplate.setMessageConverters(messageConverters);
   }

   public TwitterTokenResponse authorizeUser(String consumerKey, String consumerSecret) {
      final String url = REQUEST_TOKEN_URL;
      final HttpHeaders headers = new HttpHeaders();
      final String authorization = new TwitterAuthorizationString(url, consumerKey, consumerSecret)
         .setParameters(Collections.singletonMap("oauth_callback", "oob"))
         .setPost(true)
         .build();
      headers.set(HttpHeaders.AUTHORIZATION, authorization);
      final HttpEntity<Object> entity = new HttpEntity<>(headers);

      try {
         final URIBuilder uriBuilder = new URIBuilder(url);
         final ResponseEntity<String> response = restTemplate.exchange(uriBuilder.build(), HttpMethod.POST, entity, String.class);

         if(response.getStatusCode().is2xxSuccessful()) {
            final String body = response.getBody();
            final TwitterTokenResponse tokenResponse = TwitterTokenResponse.parseFromString(body);

            if(tokenResponse.isOauthCallbackConfirmed()) {
               return tokenResponse;
            }
         }
      }
      catch(URISyntaxException e) {
         LOG.error("Failed to build authorization URL", e);
      }

      return null;
   }

   public String createAuthorizationUrl(String requestToken) {
      try {
         final URI uri = new URIBuilder(AUTHORIZATION_URL).addParameter("oauth_token", requestToken).build();
         return uri.toString();
      }
      catch(URISyntaxException e) {
         LOG.error("Couldn't build authorization URL", e);
         return null;
      }
   }

   public TwitterTokenResponse swapTokens(String pin, String consumerKey, String consumerSecret, String requestToken) {
      try {
         final String url = ACCESS_TOKEN_URL;
         final URI uri = new URIBuilder(url).addParameter("oauth_consumer_key", consumerKey).addParameter("oauth_token", requestToken).addParameter("oauth_verifier", pin).build();
         final HttpHeaders headers = new HttpHeaders();
         final String authorization = new TwitterAuthorizationString(url, consumerKey, consumerSecret)
            .setPost(true)
            .build();
         headers.set(HttpHeaders.AUTHORIZATION, authorization);
         final HttpEntity<Object> entity = new HttpEntity<>(headers);
         final ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, entity, String.class);

         if(response.getStatusCode().is2xxSuccessful()) {
            return TwitterTokenResponse.parseFromString(response.getBody());
         }
         else {
            LOG.error("Access token request failed");
         }
      }
      catch(URISyntaxException e) {
         LOG.error("Couldn't build token URL", e);
      }

      return null;
   }

   private static final String REQUEST_TOKEN_URL = "https://api.twitter.com/oauth/request_token";
   private static final String AUTHORIZATION_URL = "https://api.twitter.com/oauth/authenticate";
   private static final String ACCESS_TOKEN_URL = "https://api.twitter.com/oauth/access_token";
   private final RestTemplate restTemplate;
   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
