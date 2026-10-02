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
package inetsoft.web.portal.data;

import inetsoft.util.credential.CloudPasswordCredential;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77173: the secret ids of an imported data source are read from its XML without parsing
 * it, so that they can be checked before they are resolved.
 */
@Tag("core")
class SecretIdAuthorizerElementTest {
   @Test
   void findsSecretIdsOnDataSourceAndAdditionalConnections() throws Exception {
      Element root = parse(
         "<registry><datasource name=\"ds\"><ds>" +
            "<PasswordCredential cloud=\"true\" class=\"x.Unknown\" id=\"parent-id\"/>" +
            "</ds></datasource><additional name=\"child\" parent=\"ds\"><ds>" +
            "<PasswordCredential cloud=\"true\" id=\"child-id\"/></ds></additional></registry>");

      assertEquals(Set.of("parent-id", "child-id"), SecretIdAuthorizer.getCloudSecretIds(root));
   }

   @Test
   void findsCloudCredentialClassWithoutCloudFlag() throws Exception {
      // JDBC and XMLA sources create the credential class named in the XML
      Element root = parse(
         "<registry><datasource name=\"ds\"><ds><PasswordCredential class=\"" +
            CloudPasswordCredential.class.getName() + "\" id=\"jdbc-id\"/></ds></datasource>" +
            "</registry>");

      assertEquals(Set.of("jdbc-id"), SecretIdAuthorizer.getCloudSecretIds(root));
   }

   @Test
   void ignoresLocalCredentialsAndEmptyIds() throws Exception {
      Element root = parse(
         "<registry><datasource name=\"ds\"><ds><PasswordCredential class=\"" +
            LocalPasswordCredential.class.getName() + "\" id=\"local-id\"><user>u</user>" +
            "</PasswordCredential></ds></datasource><additional name=\"child\" parent=\"ds\">" +
            "<ds><PasswordCredential cloud=\"true\" id=\"\"/></ds></additional>" +
            "<additional name=\"child2\" parent=\"ds\"><ds><PasswordCredential " +
            "class=\"x.Missing\" id=\"missing-id\"/></ds></additional></registry>");

      assertTrue(SecretIdAuthorizer.getCloudSecretIds(root).isEmpty());
      assertTrue(SecretIdAuthorizer.getCloudSecretIds((Element) null).isEmpty());
   }

   private static Element parse(String xml) throws Exception {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
   }
}
