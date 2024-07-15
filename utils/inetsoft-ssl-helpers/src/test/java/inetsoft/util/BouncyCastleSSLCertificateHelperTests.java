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

package inetsoft.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class BouncyCastleSSLCertificateHelperTests {
   private static final BouncyCastleSSLCertificateHelper helper =
      new BouncyCastleSSLCertificateHelper();
   @TempDir
   static Path tempDir;

   @Test
   void shouldLoadPrivateKey() throws Exception {
      PrivateKey key = loadPrivateKey();
      assertNotNull(key);
   }

   @Test
   void shouldLoadCertificate() throws Exception {
      X509Certificate certificate = loadCertificate();
      assertNotNull(certificate);
   }

   @Test
   void shouldGetChildDN() throws Exception {
      X509Certificate certificate = loadCertificate();
      String actual = helper.getChildDN(certificate, "127.0.0.1");
      String expected =
         "C=US,ST=New Jersey,L=Piscataway,O=InetSoft Technology Corp,OU=StyleBI,CN=127.0.0.1";
      assertEquals(expected, actual);
   }

   @Test
   void shouldGenerateCertificate() throws Exception {
      X509Certificate issuerCertificate = loadCertificate();
      PrivateKey issuerKey = loadPrivateKey();
      String subjectDN = helper.getChildDN(issuerCertificate, "127.0.0.1");
      char[] password = "password".toCharArray();

      SSLCertificateHelper.CertificateAndKey certificate =
         helper.generateCertificate(issuerCertificate, issuerKey, subjectDN, password);

      assertNotNull(certificate);
      assertNotNull(certificate.certificate());
      assertNotNull(certificate.privateKey());
   }

   @Test
   void shouldSaveKeyStore() throws Exception {
      X509Certificate issuerCertificate = loadCertificate();
      PrivateKey issuerKey = loadPrivateKey();
      String subjectDN = helper.getChildDN(issuerCertificate, "127.0.0.1");
      char[] password = "password".toCharArray();

      SSLCertificateHelper.CertificateAndKey certificate =
         helper.generateCertificate(issuerCertificate, issuerKey, subjectDN, password);

      File keyStoreFile = tempDir.resolve("keyStore.jks").toFile();
      helper.saveKeyStore(
         keyStoreFile.getAbsolutePath(), certificate.certificate(), certificate.privateKey(),
         issuerCertificate, password);

      assertTrue(keyStoreFile.exists());
   }

   @Test
   void shouldSaveTrustStore() throws Exception {
      X509Certificate issuerCertificate = loadCertificate();
      char[] password = "password".toCharArray();

      File trustStoreFile = tempDir.resolve("trustStore.jks").toFile();
      helper.saveKeyStore(trustStoreFile.getAbsolutePath(), issuerCertificate, password);

      assertTrue(trustStoreFile.exists());
   }

   private PrivateKey loadPrivateKey() throws Exception {
      try(Reader r = openPrivateKey()) {
         return helper.loadPrivateKey(r, "password".toCharArray());
      }
   }

   private Reader openPrivateKey() {
      return new InputStreamReader(
         Objects.requireNonNull(getClass().getResourceAsStream("root.key")),
         StandardCharsets.US_ASCII);
   }

   private X509Certificate loadCertificate() throws Exception {
      try(Reader r = openCertificate()) {
         return helper.loadCertificate(r);
      }
   }

   private Reader openCertificate() {
      return new InputStreamReader(
         Objects.requireNonNull(getClass().getResourceAsStream("root.crt")),
         StandardCharsets.US_ASCII);
   }
}
