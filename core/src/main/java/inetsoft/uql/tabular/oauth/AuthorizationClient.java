/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.uql.tabular.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.awt.*;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.*;

public class AuthorizationClient {
   CreateJobResponse createJob(CreateJobRequest createJobRequest) {
      jwt = login();

      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.add("Authorization", "Bearer " + jwt);
      HttpEntity<CreateJobRequest> request = new HttpEntity<>(createJobRequest, headers);

      ResponseEntity<CreateJobResponse> response = template.exchange(
         "https://data.inetsoft.com/job", HttpMethod.POST, request, CreateJobResponse.class);

      if(!response.getStatusCode().is2xxSuccessful()) {
         throw new RuntimeException(
            "Failed to create authorization job, server responded with " +
            response.getStatusCode().value());
      }

      return response.getBody();
   }

   AuthorizationResult getAuthorizationResult(String url) {
      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
      headers.add("Authorization", "Bearer " + jwt);
      HttpEntity<String> request = new HttpEntity<>(headers);

      ResponseEntity<AuthorizationResult> response =
         template.exchange(url, HttpMethod.GET, request, AuthorizationResult.class);

      if(!response.getStatusCode().is2xxSuccessful()) {
         throw new RuntimeException(
            "Failed to get authorization result, server responded with " +
            response.getStatusCode().value());
      }

      if(response.getStatusCode().value() == 200) {
         return response.getBody();
      }

      // 204, still processing
      return null;
   }

   private AuthorizationResult refresh(RefreshTokenRequest tokenRequest) {
      String jwt = login();

      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.add("Authorization", "Bearer " + jwt);
      HttpEntity<RefreshTokenRequest> request = new HttpEntity<>(tokenRequest, headers);

      ResponseEntity<AuthorizationResult> response = template.exchange(
         "https://data.inetsoft.com/refresh", HttpMethod.POST, request, AuthorizationResult.class);

      if(!response.getStatusCode().is2xxSuccessful()) {
         throw new RuntimeException(
            "Failed to refresh token, server responded with " +
            response.getStatusCode().value());
      }

      return response.getBody();
   }

   public static Tokens refreshPasswordGrantToken(OAuthDataSource dataSource) {
      if(shouldNotRefreshToken(dataSource)) {
         return null;
      }

      final List<String> scope = Arrays.asList(Optional.ofNullable(dataSource.getScope())
                                                  .orElse("").split(" "));
      return doRefreshPasswordGrantToken(dataSource.getUser(), dataSource.getPassword(),
                                         dataSource.getClientId(), dataSource.getClientSecret(),
                                         scope, dataSource.getTokenUri(),
                                         dataSource.getRefreshToken());
   }

   private static boolean shouldNotRefreshToken(OAuthDataSource dataSource) {
      final long tokenExpiration = dataSource.getTokenExpiration();
      final String refreshToken = dataSource.getRefreshToken();

      return tokenExpiration == 0L || refreshToken == null || refreshToken.isEmpty() ||
         Instant.now().isBefore(Instant.ofEpochMilli(tokenExpiration));
   }

   private static Tokens doRefreshPasswordGrantToken(String user, String password, String clientId,
                                                     String clientSecret, List<String> scope,
                                                     String tokenUri, String refreshToken)
   {
      final HttpPost post = new HttpPost(tokenUri);
      post.setEntity(new UrlEncodedFormEntity(Arrays.asList(
         new BasicNameValuePair("grant_type", "refresh_token"),
         new BasicNameValuePair("refresh_token", refreshToken)
      )));

      try(CloseableHttpClient client = HttpClients.createDefault();
          final CloseableHttpResponse response = client.execute(post))
      {
         if(response.getCode() != 200) {
            LOG.info("Failed to refresh token, server responded with {}.\nAttempting to redo the " +
                     "password grant auth.",
                     response.getCode() + " " + response.getReasonPhrase());
            return doPasswordGrantAuth(user, password, clientId, clientSecret, scope, tokenUri);
         }

         final PasswordGrantResponse result = new ObjectMapper().readValue(
            response.getEntity().getContent(), PasswordGrantResponse.class);

         if(result == null) {
            throw new RuntimeException(
               "Failed to refresh token, server responded with null response body.");
         }

         return Tokens.builder()
            .accessToken(result.getAccessToken())
            .issued(null)
            .expiration(expiresInToExpiration(result.getExpiresIn()))
            .scope(result.getScope())
            .build();
      }
      catch(IOException ex) {
         throw new RuntimeException("Failed to refresh token grant password token.", ex);
      }
   }

   /**
    * @param expiresIn the number of seconds the access token expires in.
    *
    * @return the millisecond timestamp of when the access token expires.
    */
   private static long expiresInToExpiration(long expiresIn) {
      // Subtract 10 seconds to try to ensure that the auth server doesn't invalidate the access
      // token before we do.
      expiresIn -= 10;

      if(expiresIn > 0) {
         return Instant.now().plus(Duration.ofSeconds(expiresIn)).toEpochMilli();
      }

      return 0;
   }

   private String login() {
      String license = "";

      if(Tool.isServer() || "true".equals(System.getProperty("ScheduleServer"))) {
         license = SreeEnv.getProperty("license.key");
      }

      int index = license.indexOf(',');

      if(index >= 0) {
         license = license.substring(0, index);
      }

      LoginRequest loginRequest = new LoginRequest();
      loginRequest.setLicense(license);

      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, headers);
      LoginResponse response =
         template.postForObject("https://data.inetsoft.com/login", request, LoginResponse.class);
      return response == null ? null : response.getToken();
   }

   public static Tokens authorizeInBrowser(String clientId, String clientSecret, List<String> scope,
                                           String authorizationUri, String tokenUri,
                                           Set<String> flags,
                                           Map<String, String> additionalParameters)
      throws AuthorizationJobException
   {
      CreateJobRequest request = new CreateJobRequest(
         null, clientId, clientSecret, scope, authorizationUri, tokenUri, flags,
         additionalParameters);
      return doAuthorizeInBrowser(request);
   }

   public static Tokens authorizeInBrowser(String serviceName) throws AuthorizationJobException {
      CreateJobRequest request = new CreateJobRequest();
      request.setServiceName(serviceName);
      return doAuthorizeInBrowser(request);
   }

   private static Tokens doAuthorizeInBrowser(CreateJobRequest request)
      throws AuthorizationJobException
   {
      AuthorizationClient client = new AuthorizationClient();
      CreateJobResponse job = client.createJob(request);
      String dataUrl = job.getDataUrl();

      try {
         Desktop.getDesktop().browse(URI.create(job.getAuthorizationPageUrl()));
      }
      catch(IOException e) {
         throw new AuthorizationJobException("Failed to open browser", "client", null);
      }

      AuthorizationResult result = client.getAuthorizationResult(dataUrl);
      long timeout = System.currentTimeMillis() + 60000L;

      while(result == null && System.currentTimeMillis() < timeout) {
         try {
            Thread.sleep(500L);
         }
         catch(InterruptedException e) {
            throw new AuthorizationJobException("Authorization interrupted", "client", null);
         }

         result = client.getAuthorizationResult(dataUrl);
      }

      if(result == null) {
         throw new AuthorizationJobException("Authorization timed out", "client", null);
      }
      else {
         return createTokens(result);
      }
   }

   private static Tokens createTokens(AuthorizationResult result) throws AuthorizationJobException {
      Tokens tokens = null;

      if(result != null && result.getErrorType() != null) {
         throw new AuthorizationJobException(
            result.getErrorMessage(), result.getErrorType(), result.getErrorUri());
      }
      else if(result != null) {
         tokens = Tokens.builder()
            .accessToken(result.getAccessToken())
            .refreshToken(result.getRefreshToken())
            .issued(result.getIssued())
            .expiration(result.getExpiration())
            .scope(result.getScope())
            .properties(result.getProperties())
            .build();
      }

      return tokens;
   }

   public static Tokens doPasswordGrantAuth(String user, String password, String clientId,
                                            String clientSecret, List<String> scope,
                                            String tokenUri)
   {
      final HttpPost post = new HttpPost(tokenUri);

      final List<BasicNameValuePair> formData = new ArrayList<>();
      formData.add(new BasicNameValuePair("grant_type", "password"));
      formData.add(new BasicNameValuePair("username", user));
      formData.add(new BasicNameValuePair("password", password));

      if(clientId != null && !clientId.isEmpty()) {
         formData.add(new BasicNameValuePair("client_id", clientId));
      }

      if(clientSecret != null && !clientSecret.isEmpty()) {
         formData.add(new BasicNameValuePair("client_secret", clientSecret));
      }

      if(scope != null && !scope.isEmpty()) {
         formData.add(new BasicNameValuePair("scope", String.join(" ", scope)));
      }

      post.setEntity(new UrlEncodedFormEntity(formData));
      post.setHeader("Accept", MediaType.APPLICATION_JSON_VALUE);

      try(CloseableHttpClient client = HttpClients.createDefault();
          final CloseableHttpResponse response = client.execute(post))
      {
         if(response.getCode() != 200) {
            throw new RuntimeException(
               "Failed to do password grant authorization, server responded with " +
               response.getCode() + " " + response.getReasonPhrase());
         }

         final PasswordGrantResponse result = new ObjectMapper().readValue(
            response.getEntity().getContent(), PasswordGrantResponse.class);

         if(result == null) {
            throw new RuntimeException(
               "Failed to do password grant authorization, server responded with null response body.");
         }

         return Tokens.builder()
            .accessToken(result.getAccessToken())
            .refreshToken(result.getRefreshToken())
            .issued(null)
            .expiration(expiresInToExpiration(result.getExpiresIn()))
            .scope(result.getScope())
            .build();
      }
      catch(IOException ex) {
         throw new RuntimeException("Failed to do password grant authorization.", ex);
      }
   }

   public static Tokens refresh(String serviceName, String refreshToken,
                                Map<String, String> properties) throws Exception
   {
      return refresh(serviceName, refreshToken, properties, false);
   }

   public static Tokens refresh(String serviceName, String refreshToken,
                                Map<String, String> properties,
                                boolean useBasicAuth) throws Exception
   {
      RefreshTokenRequest request = new RefreshTokenRequest();
      request.setServiceName(serviceName);
      request.setRefreshToken(refreshToken);
      request.setUseBasicAuth(useBasicAuth);
      return doRefresh(request, properties);
   }

   public static Tokens refreshTokens(OAuthDataSource dataSource, boolean useBasicAuth) {
      if(shouldNotRefreshToken(dataSource)) {
         return null;
      }

      Tokens tokens;

      try {
         final Set<String> flagsSet = new HashSet<>();
         final String flags = dataSource.getOauthFlags();

         if(flags != null && !flags.isEmpty()) {
            flagsSet.addAll(Arrays.asList(flags.split(" ")));
         }

         final String serviceName = dataSource.getServiceName();
         final String refreshToken = dataSource.getRefreshToken();
         final String clientId = dataSource.getClientId();
         final String clientSecret = dataSource.getClientSecret();
         final String tokenUri = dataSource.getTokenUri();

         tokens = AuthorizationClient.refresh(serviceName, refreshToken, clientId, clientSecret,
                                              tokenUri, flagsSet, useBasicAuth, null);
      }
      catch(Exception e) {
         LOG.error("Failed to refresh access token", e);
         return null;
      }

      return tokens;
   }

   public static Tokens refresh(String serviceName, String refreshToken, String clientId,
                                String clientSecret, String tokenUri, Set<String> flags,
                                boolean useBasicAuth, Map<String, String> properties)
      throws Exception
   {
      RefreshTokenRequest request = new RefreshTokenRequest();
      request.setServiceName(serviceName);
      request.setClientId(clientId != null ? clientId : "");
      request.setClientSecret(clientSecret != null ? clientSecret : "");
      request.setTokenUri(tokenUri);
      request.setRefreshToken(refreshToken);
      request.setFlags(flags);
      request.setUseBasicAuth(useBasicAuth);
      return doRefresh(request, properties);
   }

   private static Tokens doRefresh(RefreshTokenRequest request, Map<String, String> properties)
      throws Exception
   {
      if(properties != null) {
         for(Map.Entry<String, String> e : properties.entrySet()) {
            request.setProperty(e.getKey(), e.getValue());
         }
      }

      AuthorizationResult result = new AuthorizationClient().refresh(request);

      if(result.getErrorType() != null) {
         throw new Exception(
            "Failed to refresh token [" + result.getErrorType() + "]: " +
            result.getErrorMessage());
      }

      return Tokens.builder()
         .accessToken(result.getAccessToken())
         .refreshToken(result.getRefreshToken())
         .issued(result.getIssued())
         .expiration(result.getExpiration())
         .scope(result.getScope())
         .properties(result.getProperties())
         .build();
   }

   private final RestTemplate template = new RestTemplate();
   private String jwt;
   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
