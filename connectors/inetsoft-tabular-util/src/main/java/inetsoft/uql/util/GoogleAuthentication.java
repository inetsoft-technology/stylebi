/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.util;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Clock;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Class that handles authenticating with the Google web services.
 *
 * @author InetSoft Technology
 * @since  12.0
 */
public class GoogleAuthentication implements CredentialRefreshListener {
   /**
    * Creates a new instance of <tt>GoogleAnalyticsClient</tt>.
    *
    * @param ds the data source to authenticate.
    *
    * @throws GeneralSecurityException if a security error occurs.
    * @throws IOException if an I/O error occurs.
    */
   public GoogleAuthentication(GoogleDataSource ds, String clientId,
                               String clientSecret)
      throws GeneralSecurityException, IOException
   {
      this.ds = ds;
      HttpTransport httpTransport =
         GoogleNetHttpTransport.newTrustedTransport();
      JsonFactory jsonFactory = new GsonFactory();

      ClientParametersAuthentication clientAuthentication =
         new ClientParametersAuthentication(clientId, clientSecret);

      Credential.AccessMethod method =
         BearerToken.authorizationHeaderAccessMethod();
      Credential.Builder builder = new Credential.Builder(method)
         .setTransport(httpTransport)
         .setJsonFactory(jsonFactory)
         .setTokenServerEncodedUrl(GoogleOAuthConstants.TOKEN_SERVER_URL)
         .setClientAuthentication(clientAuthentication)
         .setClock(Clock.SYSTEM)
         .addRefreshListener(this);
      Credential credential = builder.build();

      credential.setAccessToken(ds.getAccessToken());
      credential.setRefreshToken(ds.getRefreshToken());
      credential.setExpirationTimeMilliseconds(ds.getExpiration());
      userId = ds.getUserId();
   }

   @Override
   public void onTokenResponse(Credential credential,
                               TokenResponse tokenResponse)
      throws IOException
   {
      ds.setUserId(userId);
      ds.setAccessToken(credential.getAccessToken());
      ds.setRefreshToken(credential.getRefreshToken());
      ds.setExpiration(credential.getExpirationTimeMilliseconds());
   }

   @Override
   public void onTokenErrorResponse(Credential credential,
                                    TokenErrorResponse tokenErrorResponse)
      throws IOException
   {
      // NO-OP
   }

   /**
    * Authorizes the data loader component to access the Google Analytics web
    * services for a user.
    *
    * @param code authorization code
    *
    * @return the authorized user identifier.
    *
    * @throws GeneralSecurityException if a security error occurs.
    * @throws IOException if an I/O error occurs.
    */
   public String authorize(String code, String clientId, String clientSecret,
                           String ... scopes) throws Exception
   {
      HttpTransport httpTransport =
         GoogleNetHttpTransport.newTrustedTransport();
      JsonFactory jsonFactory = new GsonFactory();
      DataStore<StoredCredential> credentialStore = MemoryDataStoreFactory.
         getDefaultInstance().getDataStore("googledoc");
      GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
         httpTransport, jsonFactory, clientId, clientSecret, Arrays.asList(
         scopes)).setCredentialDataStore(credentialStore).build();
      TokenResponse response = flow.newTokenRequest(code).setRedirectUri(
         REDIRECT_URI).execute();
      Credential credential = flow.createAndStoreCredential(response, "user");

      JsonParser parser = new JsonParser();
      URL url = new URL(
         "https://www.googleapis.com/oauth2/v1/userinfo?access_token=" +
            credential.getAccessToken());

      JsonObject profile = (JsonObject)
         parser.parse(new InputStreamReader(url.openStream()));

      ds.setUserId(profile.get("id").getAsString());
      ds.setAccessToken(credential.getAccessToken());
      ds.setRefreshToken(credential.getRefreshToken());
      ds.setExpiration(credential.getExpirationTimeMilliseconds());

      return ds.getUserId();
   }

   public static String getAuthorizationUrl(String clientId, String clientSecret,
                                            String ... scopes)
   {
      try {
         HttpTransport httpTransport =
            GoogleNetHttpTransport.newTrustedTransport();
         JsonFactory jsonFactory = new GsonFactory();
         DataStore<StoredCredential> credentialStore = MemoryDataStoreFactory.
            getDefaultInstance().getDataStore("googledoc");
         GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            httpTransport, jsonFactory, clientId, clientSecret, Arrays.asList(scopes))
            .setCredentialDataStore(credentialStore).build();

         return flow.newAuthorizationUrl().setRedirectUri(REDIRECT_URI).toURL().
            toString();
      }
      catch(Exception ignore) {
      }

      return "";
   }

   private final String userId;
   private GoogleDataSource ds;

   private static final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";
}
