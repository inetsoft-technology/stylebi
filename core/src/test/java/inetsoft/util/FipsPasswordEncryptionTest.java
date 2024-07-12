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

import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome()
@Disabled
class FipsPasswordEncryptionTest {
   private static FipsPasswordEncryption encryption;

   @BeforeAll
   static void createEncryption() {
      encryption = new FipsPasswordEncryption();
   }

   @AfterAll
   static void resetSecurityProvider() {
      Security.removeProvider("BCFIPS");
   }

   @Test
   void testHash() {
      String clearPassword = "secret";
      HashedPassword hashedPassword = encryption.hash(clearPassword, null, null, false);
      assertAll(
         () -> assertNotNull(hashedPassword),
         () -> assertNotNull(hashedPassword.getHash()),
         () -> assertNotEquals(clearPassword, hashedPassword.getHash()),
         () -> assertEquals("fips-hmacsha512", hashedPassword.getAlgorithm())
      );

      boolean actual = encryption.checkHashedPassword(
         hashedPassword.getHash(), clearPassword, hashedPassword.getAlgorithm(), null, false,
         Tool::encodeAscii85);
      assertTrue(actual);
   }

   @Test
   void testHashWithInvalidPassword() {
      String clearPassword = "my secret password";
      String invalidPassword = "foo";
      HashedPassword hashedPassword = encryption.hash(clearPassword, null, null, false);
      boolean actual = encryption.checkHashedPassword(
         hashedPassword.getHash(), invalidPassword, hashedPassword.getAlgorithm(), null, false,
         Tool::encodeAscii85);
      assertFalse(actual);
   }

   @Test
   void testHashWithUnsupportedPassword() {
      String clearPassword = "my secret password";
      boolean actual = encryption.checkHashedPassword(
         clearPassword, clearPassword, "none", null, false, Tool::encodeAscii85);
      assertTrue(actual);
   }

   @Test
   void testCreateJwtSigningKey() {
      SecretKey key = encryption.createJwtSigningKey();
      assertAll(
         () -> assertNotNull(key),
         () -> assertEquals("HmacSHA512", key.getAlgorithm()),
         () -> assertNotNull(key.getEncoded()),
         () -> assertEquals(16, key.getEncoded().length)
      );
   }

   @Test
   void testCreateSecretKey() {
      SecretKey key = encryption.createSecretKey();
      assertAll(
         () -> assertNotNull(key),
         () -> assertEquals("AES", key.getAlgorithm()),
         () -> assertEquals(16, key.getEncoded().length)
      );
   }

   void testEncrypt() throws Exception {
      byte[] input = "my secret password".getBytes(StandardCharsets.UTF_16);
      SecretKey key = encryption.createSecretKey();
      byte[][] encryptedData = encryption.encrypt(input, key);
      byte[] encryptedPassword = encryptedData[0];
      byte[] iv = encryptedData[1];
      assertFalse(Arrays.equals(input, encryptedPassword));
      byte[] encrypted = encryption.encryptSecretKey(key, encryption.getMasterKey());
      assertFalse(Arrays.equals(key.getEncoded(), encrypted));
      key = encryption.decryptSecretKey(
         Base64.getEncoder().encodeToString(encrypted), encryption.getMasterKey());
      byte[] decrypted = encryption.decrypt(encryptedPassword, iv, key);
      assertArrayEquals(input, decrypted);
   }

   @Test
   void testEncryptWithMaster() {
      byte[] input = "my secret password".getBytes(StandardCharsets.UTF_16);
      byte[] encrypted = encryption.encryptWithMaster(input, encryption.getMasterKey());
      assertFalse(Arrays.equals(input, encrypted));
      byte[] decrypted = encryption.decryptWithMaster(encrypted, encryption.getMasterKey());
      assertArrayEquals(input, decrypted);
   }
}
