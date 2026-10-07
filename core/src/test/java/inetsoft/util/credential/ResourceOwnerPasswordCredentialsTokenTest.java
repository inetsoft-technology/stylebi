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

package inetsoft.util.credential;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77730: the ROPC credential of SharePoint Online could not hold the access and refresh
 * tokens, so they were dropped and a password grant was made for every request. The local
 * credential now saves them encrypted; the cloud credential keeps them only at runtime, and
 * compares them so that saving them onto a stored data source is detected as a change.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ResourceOwnerPasswordCredentialsTokenTest {
   @Test
   void bothCredentialsHoldTokens() {
      ResourceOwnerPasswordCredentialsFactory factory = new ResourceOwnerPasswordCredentialsFactory();
      assertInstanceOf(RefreshTokenCredential.class, factory.createCredential(true));
      assertInstanceOf(RefreshTokenCredential.class, new CloudResourceOwnerPasswordCredentials());
   }

   @Test
   void localTokensAreSavedEncrypted() throws Exception {
      LocalResourceOwnerPasswordCredentials credential = local();
      credential.setAccessToken("access-123");
      credential.setRefreshToken("refresh-456");

      String xml = write(credential);
      assertTrue(xml.contains("<accessToken>"), xml);
      assertTrue(xml.contains("<refreshToken>"), xml);
      assertFalse(xml.contains("access-123"), "the access token is encrypted");
      assertFalse(xml.contains("refresh-456"), "the refresh token is encrypted");

      LocalResourceOwnerPasswordCredentials loaded = new LocalResourceOwnerPasswordCredentials();
      loaded.parseXML(parse(xml));
      assertEquals("access-123", loaded.getAccessToken());
      assertEquals("refresh-456", loaded.getRefreshToken());
      assertEquals("alice", loaded.getUser());
      assertEquals("secret", loaded.getClientSecret());
      assertEquals(credential, loaded);
   }

   // a credential saved before the tokens were kept has no token elements
   @Test
   void localWithoutTokensLoadsNoTokens() throws Exception {
      String xml = write(local());
      assertFalse(xml.contains("Token>"), xml);

      LocalResourceOwnerPasswordCredentials loaded = new LocalResourceOwnerPasswordCredentials();
      loaded.parseXML(parse(xml));
      assertNull(loaded.getAccessToken());
      assertNull(loaded.getRefreshToken());
      assertEquals("alice", loaded.getUser());
   }

   @Test
   void localEqualsComparesTokens() {
      LocalResourceOwnerPasswordCredentials a = local();
      LocalResourceOwnerPasswordCredentials b = local();
      assertEquals(a, b);

      b.setAccessToken("access-123");
      assertNotEquals(a, b);
      a.setAccessToken("access-123");
      assertEquals(a, b);

      b.setRefreshToken("refresh-456");
      assertNotEquals(a, b);
   }

   // the tokens alone don't make a credential non-empty, and a reset discards them
   @Test
   void localTokensDontCountAsContentAndAreReset() {
      LocalResourceOwnerPasswordCredentials credential = new LocalResourceOwnerPasswordCredentials();
      credential.setAccessToken("access-123");
      credential.setRefreshToken("refresh-456");
      assertTrue(credential.isEmpty());

      credential = local();
      credential.setAccessToken("access-123");
      credential.setRefreshToken("refresh-456");
      credential.reset();
      assertNull(credential.getAccessToken());
      assertNull(credential.getRefreshToken());
   }

   // the tokens are compared, so that XEngine.updateDataSourceTokens skips the save of a cloud
   // credential, which would store the new expiration without the tokens (Bug #77699)
   @Test
   void cloudEqualsComparesTokens() {
      CloudResourceOwnerPasswordCredentials a = cloud();
      CloudResourceOwnerPasswordCredentials b = (CloudResourceOwnerPasswordCredentials) a.clone();
      assertEquals(a, b);

      b.setAccessToken("access-123");
      assertNotEquals(a, b);
      a.setAccessToken("access-123");
      assertEquals(a, b);

      b.setRefreshToken("refresh-456");
      assertNotEquals(a, b);
   }

   // the data source stores only the id of a cloud credential, never its tokens
   @Test
   void cloudTokensAreNotWritten() {
      CloudResourceOwnerPasswordCredentials credential = cloud();
      credential.setAccessToken("access-123");
      credential.setRefreshToken("refresh-456");

      String xml = write(credential);
      assertTrue(xml.contains("id=\"secret-id\""), xml);
      assertFalse(xml.contains("Token"), xml);
      assertFalse(xml.contains("access-123"), xml);
   }

   // the secret holds the account, not the tokens: loading it again keeps the runtime tokens
   @Test
   void cloudSecretDoesNotCarryTokens() throws Exception {
      String secret = "{\"user\":\"alice\",\"password\":\"pw\",\"client_id\":\"client\"," +
         "\"client_secret\":\"secret\",\"tenant_id\":\"tenant\"," +
         "\"access_token\":\"from-secret\",\"refresh_token\":\"from-secret\"}";
      ObjectMapper mapper = new ObjectMapper();
      CloudResourceOwnerPasswordCredentials fetched =
         mapper.convertValue(mapper.readTree(secret), CloudResourceOwnerPasswordCredentials.class);
      assertEquals("alice", fetched.getUser());
      assertEquals("tenant", fetched.getTenantId());
      assertNull(fetched.getAccessToken());
      assertNull(fetched.getRefreshToken());

      CloudResourceOwnerPasswordCredentials credential = cloud();
      credential.setAccessToken("access-123");
      credential.setRefreshToken("refresh-456");
      credential.refreshCredential(fetched);
      assertEquals("access-123", credential.getAccessToken());
      assertEquals("refresh-456", credential.getRefreshToken());

      credential.reset();
      assertNull(credential.getAccessToken());
      assertNull(credential.getRefreshToken());
   }

   private static LocalResourceOwnerPasswordCredentials local() {
      LocalResourceOwnerPasswordCredentials credential = new LocalResourceOwnerPasswordCredentials();
      credential.setUser("alice");
      credential.setPassword("pw");
      credential.setClientId("client");
      credential.setClientSecret("secret");
      credential.setTenantId("tenant");
      return credential;
   }

   private static CloudResourceOwnerPasswordCredentials cloud() {
      CloudResourceOwnerPasswordCredentials credential = new CloudResourceOwnerPasswordCredentials();
      credential.setId("secret-id");
      credential.setUser("alice");
      credential.setPassword("pw");
      credential.setClientId("client");
      credential.setClientSecret("secret");
      credential.setTenantId("tenant");
      return credential;
   }

   private static String write(Credential credential) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      credential.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private static org.w3c.dom.Element parse(String xml) throws Exception {
      Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      return document.getDocumentElement();
   }
}
