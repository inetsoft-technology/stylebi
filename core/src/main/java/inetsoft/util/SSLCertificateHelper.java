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

package inetsoft.util;

import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * {@code SSLCertificateHelper} is an interface for classes that handle loading, saving, and
 * generating keys and certificates.
 */
public interface SSLCertificateHelper {
   /**
    * Loads a PEM encrypted private key file.
    *
    * @param filePath the path to the file.
    * @param password the password for the key.
    *
    * @return the private key.
    *
    * @throws Exception if the key could not be loaded.
    */
   default PrivateKey loadPrivateKeyFile(String filePath, char[] password) throws Exception {
      try(Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.US_ASCII)) {
         return loadPrivateKey(reader, password);
      }
   }

   /**
    * Loads a PEM encrypted private key.
    *
    * @param content  the PEM encoded key content.
    * @param password the password for the key.
    *
    * @return the private key.
    *
    * @throws Exception if the key could not be loaded.
    */
   default PrivateKey loadPrivateKeyPEM(String content, char[] password) throws Exception {
      return loadPrivateKey(new StringReader(content), password);
   }

   /**
    * Loads a PEM encrypted private key.
    *
    * @param in       the input stream from which to read the key.
    * @param password the password for the private key.
    *
    * @return the private key.
    *
    * @throws Exception if the PEM data could not be read.
    */
   PrivateKey loadPrivateKey(Reader in, char[] password) throws Exception;

   /**
    * Loads a PEM encoded X509 certificate file.
    *
    * @param filePath the path to the certificate file.
    *
    * @return the certificate.
    *
    * @throws Exception if the certificate could not be loaded.
    */
   default X509Certificate loadCertificateFile(String filePath) throws Exception {
      try(Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.US_ASCII)) {
         return loadCertificate(reader);
      }
   }

   /**
    * Loads a PEM encoded X509 certificate.
    *
    * @param content the content of the certificate.
    *
    * @return the certificate.
    *
    * @throws Exception if the certificate could not be loaded.
    */
   default X509Certificate loadCertificatePEM(String content) throws Exception {
      return loadCertificate(new StringReader(content));
   }

   /**
    * Loads a PEM encrypted certificate.
    *
    * @param in the input stream from which to read the certificate.
    *
    * @return the certificate.
    *
    * @throws Exception if the certificate could not be loaded.
    */
   X509Certificate loadCertificate(Reader in) throws Exception;

   /**
    * Generates a new SSL certificate.
    *
    * @param issuerCertificate the issuer (CA) certificate.
    * @param issuerKey         the issuer (CA) private key.
    *
    * @return a new certificate.
    *
    * @throws Exception if the certificate could not be created.
    */
   CertificateAndKey generateCertificate(X509Certificate issuerCertificate, PrivateKey issuerKey,
                                         String subjectDN, char[] password) throws Exception;

   /**
    * Creates a DN from an issuer certificate's subject DN, but with the CN replaced.
    *
    * @param issuerCertificate the issuer certificate.
    * @param cn                the child CN.
    *
    * @return the child DN.
    *
    * @throws Exception if an error occurred reading the DN from the issuer.
    */
   String getChildDN(X509Certificate issuerCertificate, String cn) throws Exception;

   /**
    * Saves a key store containing an SSL certificate and private key.
    *
    * @param filePath          the path to the key store file.
    * @param certificate       the certificate to add.
    * @param privateKey        the private key to add.
    * @param issuerCertificate the certificate of the issuer (CA).
    * @param password          the password for the key and key store.
    *
    * @throws Exception if the key store could not be saved.
    */
   void saveKeyStore(String filePath, X509Certificate certificate, PrivateKey privateKey,
                     X509Certificate issuerCertificate, char[] password) throws Exception;

   /**
    * Saves a key store containing an SSL certificate.
    *
    * @param filePath    the path to the key store file.
    * @param certificate the certificate to add.
    * @param password    the password for the key store.
    *
    * @throws Exception if the key store could not be saved.
    */
   void saveKeyStore(String filePath, X509Certificate certificate, char[] password)
      throws Exception;

   /**
    * Container for a X509 certificate and the associated private key.
    *
    * @param certificate the certificate.
    * @param privateKey  the private key.
    */
   record CertificateAndKey(X509Certificate certificate, PrivateKey privateKey) {
   }

   /**
    * Interface for classes that create instances of {@link SSLCertificateHelper}.
    */
   interface Factory {
      /**
       * Gets a flag indicating if the created loader is FIPS compliant.
       *
       * @return {@code true} if FIPS compliant or {@code false} if not.
       */
      boolean isFipsCompliant();

      /**
       * Creates an SSL certificate helper instance.
       *
       * @return a new SSL certificate helper.
       */
      SSLCertificateHelper createSSLCertificateHelper();
   }
}
