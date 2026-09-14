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
package inetsoft.uql.rest.datasource.remedyforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.auth.AuthType;
import inetsoft.util.Tool;
import inetsoft.util.credential.AbstractCloudCredential;
import inetsoft.util.credential.Credential;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome
class RemedyforceDataSourceTest {
   /**
    * Data sources saved before the shared {@code SalesforceDataSource} base class switched to
    * {@code CredentialType.PASSWORD_OAUTH2_WITH_FLAGS} persisted their security token nested
    * inside the legacy {@code <PasswordCredential>} node (written by the old
    * {@code LocalSecurityTokenCredential}). Upgrading must not silently drop it.
    */
   @Test
   void legacySecurityTokenIsMigratedFromPasswordCredentialNode() throws Exception {
      String xml =
         "<ds_" + RemedyforceDataSource.TYPE + " name=\"test\">" +
         "<PasswordCredential class=\"inetsoft.util.credential.LocalSecurityTokenCredential\">" +
         "<user><![CDATA[myuser]]></user>" +
         "<password><![CDATA[" + Tool.encryptPassword("mypassword") + "]]></password>" +
         "<securityToken><![CDATA[" + Tool.encryptPassword("mytoken") + "]]></securityToken>" +
         "</PasswordCredential>" +
         "</ds_" + RemedyforceDataSource.TYPE + ">";

      Element root = parse(xml);
      RemedyforceDataSource dataSource = new RemedyforceDataSource();
      dataSource.parseXML(root);

      assertEquals("myuser", dataSource.getUser());
      assertEquals("mypassword", dataSource.getPassword());
      assertEquals("mytoken", dataSource.getSecurityToken());
   }

   /**
    * Data sources that used "Use Secret ID" before the shared base class switched to
    * {@code PASSWORD_OAUTH2_WITH_FLAGS} stored the security token inside the vault secret's own
    * JSON, under the legacy {@code CloudSecurityTokenCredential} schema ({@code security_token}).
    * The new cloud credential type never reads that key, so it must be migrated by reading the
    * raw vault secret directly instead.
    */
   @Test
   void legacySecurityTokenIsMigratedFromCloudSecret() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode vaultSecret = mapper.readTree(
         "{\"user\":\"myuser\",\"password\":\"mypassword\",\"security_token\":\"mytoken\"}");

      RemedyforceDataSource dataSource = new RemedyforceDataSource() {
         @Override
         protected JsonNode loadCloudSecurityToken(String credentialId) {
            assertEquals("secret-123", credentialId);
            return vaultSecret;
         }
      };
      dataSource.setCredential(new FakeCloudCredential("secret-123"));

      // no <PasswordCredential> node at all: the data source's own local XML never carried the
      // security token when it was backed by a cloud secret.
      Element root = parse("<ds_" + RemedyforceDataSource.TYPE + " name=\"test\"/>");
      dataSource.parseXML(root);

      assertEquals("mytoken", dataSource.getSecurityToken());
   }

   /**
    * OAuth-mode data sources never had a security token to migrate, and the vault fallback
    * reaches a live, billed-per-call secrets manager API — it must not fire for them.
    */
   @Test
   void oauthModeNeverConsultsTheVaultForALegacySecurityToken() throws Exception {
      RemedyforceDataSource dataSource = new RemedyforceDataSource() {
         @Override
         protected JsonNode loadCloudSecurityToken(String credentialId) {
            fail("OAuth-mode data sources must not query the vault for a legacy security token");
            return null;
         }
      };
      dataSource.setAuthType(AuthType.OAUTH);
      dataSource.setCredential(new FakeCloudCredential("secret-123"));

      Element root = parse("<ds_" + RemedyforceDataSource.TYPE + " name=\"test\"/>");
      dataSource.parseXML(root);

      assertNull(dataSource.getSecurityToken());
   }

   @Test
   void securityTokenStaysVisibleInLegacyModeRegardlessOfCredentialSource() {
      RemedyforceDataSource dataSource = new RemedyforceDataSource();
      dataSource.setAuthType(AuthType.NONE);
      assertTrue(dataSource.showSecurityToken());

      dataSource.setAuthType(AuthType.OAUTH);
      assertFalse(dataSource.showSecurityToken());
   }

   private static final class FakeCloudCredential extends AbstractCloudCredential {
      FakeCloudCredential(String id) {
         setId(id);
      }

      @Override
      public Credential createLocal() {
         return null;
      }

      @Override
      public void copyToLocal(Credential credential) {
      }
   }

   private Element parse(String xml) throws Exception {
      Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      return document.getDocumentElement();
   }
}
