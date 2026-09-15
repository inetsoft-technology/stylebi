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

import inetsoft.test.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import inetsoft.util.credential.LocalPasswordAndOAuth2WithFlagCredentialsGrant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, RemedyforceDataSourceTest.CredentialConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class RemedyforceDataSourceTest {
   /**
    * {@code CredentialTestConfig} only stubs {@code CredentialType.PASSWORD}; this connector
    * uses {@code PASSWORD_OAUTH2_WITH_FLAGS}, so a real (non-mocked) credential is needed here
    * to actually exercise its {@code parseXML()} migration behavior.
    */
   @Configuration
   static class CredentialConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(
            eq(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS), anyBoolean()))
            .thenAnswer(invocation -> new LocalPasswordAndOAuth2WithFlagCredentialsGrant());
         return credentialService;
      }
   }

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

   private Element parse(String xml) throws Exception {
      Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      return document.getDocumentElement();
   }
}
