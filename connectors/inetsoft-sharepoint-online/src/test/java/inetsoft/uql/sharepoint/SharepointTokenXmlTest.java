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

package inetsoft.uql.sharepoint;

import inetsoft.test.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77730: the tokens of a local ROPC credential are saved with the data source XML, so a
 * data source loaded from storage reuses them. A data source saved before the fix has only the
 * token expiration and no tokens; it still loads and signs in with the password.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SharepointTokenSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class SharepointTokenXmlTest {
   // the stored data source XML keeps the tokens encrypted, and the loaded copy reuses them
   @Test
   void localTokensRoundTripThroughDataSourceXml() throws Exception {
      Instant expires = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
      SharepointOnlineDataSource ds = source();
      ds.setAccessToken("tok-saved-123");
      ds.setRefreshToken("refresh-saved-456");
      ds.setTokenExpires(expires);

      String xml = write(ds);
      assertTrue(xml.contains("<accessToken>"), xml);
      assertTrue(xml.contains("<refreshToken>"), xml);
      assertFalse(xml.contains("tok-saved-123"), "the access token is encrypted");
      assertFalse(xml.contains("refresh-saved-456"), "the refresh token is encrypted");

      SharepointOnlineDataSource loaded = parse(xml);
      assertEquals("tok-saved-123", loaded.getAccessToken());
      assertEquals("refresh-saved-456", loaded.getRefreshToken());
      assertEquals(expires, loaded.getTokenExpires());
      assertEquals("alice", loaded.getUser());
      assertEquals("secret", loaded.getClientSecret());

      List<String> grants = new ArrayList<>();
      assertEquals("tok-saved-123", authorize(loaded, grants, "tok-new"));
      assertEquals(List.of(), grants, "a stored valid token is reused without a grant");
   }

   // before the fix the tokens were dropped but the expiration was saved, so an upgraded data
   // source has a future expiration and no tokens: it must sign in, not send a null token
   @Test
   void dataSourceSavedWithoutTokensSignsIn() throws Exception {
      SharepointOnlineDataSource ds = source();
      ds.setTokenExpires(Instant.now().plus(1, ChronoUnit.HOURS));
      String xml = write(ds);
      assertFalse(xml.contains("Token>"), xml);
      assertTrue(xml.contains("<token-expires>"), xml);

      SharepointOnlineDataSource loaded = parse(xml);
      assertNull(loaded.getAccessToken());
      assertNull(loaded.getRefreshToken());
      assertEquals("alice", loaded.getUser());

      List<String> grants = new ArrayList<>();
      assertEquals("tok-new", authorize(loaded, grants, "tok-new"));
      assertEquals(List.of("password"), grants);
      assertEquals("tok-new", loaded.getAccessToken());
   }

   private static String authorize(SharepointOnlineDataSource ds, List<String> grants,
                                   String newToken) throws Exception
   {
      CloseableHttpClient http = mock(CloseableHttpClient.class);
      when(http.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
         .thenAnswer(inv -> {
            ClassicHttpRequest post = inv.getArgument(0);
            String body = EntityUtils.toString(
               ((org.apache.hc.core5.http.HttpEntityContainer) post).getEntity());
            grants.add(body.contains("grant_type=refresh_token") ? "refresh_token" : "password");
            BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
            response.setEntity(new StringEntity(
               "{\"token_type\":\"Bearer\",\"expires_in\":3600,\"access_token\":\"" + newToken +
               "\",\"refresh_token\":\"R-new\"}", ContentType.APPLICATION_JSON));
            return inv.<HttpClientResponseHandler<?>>getArgument(1).handleResponse(response);
         });

      try(MockedStatic<HttpClients> clients = mockStatic(HttpClients.class)) {
         clients.when(HttpClients::createDefault).thenReturn(http);
         return new SharepointAuthenticator(ds, false)
            .getAuthorizationTokenAsync(new URL("https://graph.microsoft.com/v1.0/sites/root"))
            .get();
      }
   }

   private static String write(SharepointOnlineDataSource ds) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      ds.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private static SharepointOnlineDataSource parse(String xml) throws Exception {
      Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      SharepointOnlineDataSource ds = new SharepointOnlineDataSource();
      ds.parseXML(document.getDocumentElement());
      return ds;
   }

   private static SharepointOnlineDataSource source() {
      SharepointOnlineDataSource ds = new SharepointOnlineDataSource();
      ds.setName("spXml");
      ds.setUser("alice");
      ds.setPassword("password");
      ds.setClientId("client");
      ds.setTenantId("acme");
      ds.setClientSecret("secret");
      return ds;
   }
}
