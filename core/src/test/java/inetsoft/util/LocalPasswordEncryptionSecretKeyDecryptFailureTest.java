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
package inetsoft.util;

import org.junit.jupiter.api.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77722: an {@code \aes} value encrypted with another password.encryption.key, or read
 * while the secret key is unavailable, must come back as is (with its prefix), never as the
 * stripped ciphertext, and a wrong key that happens to pass the CBC padding check must not
 * yield garbage. No Spring context is needed: the secret key is passed in wrapped, as
 * {@link PasswordEncryption#decryptPassword(String, String)} takes it.
 */
@Tag("core")
class LocalPasswordEncryptionSecretKeyDecryptFailureTest {
   @BeforeEach
   void setUp() throws Exception {
      LocalPasswordEncryption.masterPassword.set(MASTER.toCharArray());
      encryption = new JcePasswordEncryption();
      keyA = encryption.createSecretKey();
      value = encryptWith(keyA, PASSWORD);
   }

   @AfterEach
   void tearDown() {
      LocalPasswordEncryption.masterPassword.remove();
   }

   @Test
   void rightKeyDecrypts() {
      assertEquals(PASSWORD, encryption.decryptPassword(value, wrap(keyA)));
   }

   @Test
   void wrongKeyReturnsPrefixedInput() {
      SecretKey keyB = encryption.createSecretKey();
      assertEquals(value, encryption.decryptPassword(value, wrap(keyB)));
   }

   @Test
   void nullKeyReturnsPrefixedInput() {
      // JcePasswordEncryption.decryptSecretKey() returns null when the master password in
      // INETSOFT_MASTER_PASSWORD does not match the check in inetsoft.yaml
      JcePasswordEncryption invalidMaster = new JcePasswordEncryption() {
         @Override
         protected SecretKey decryptSecretKey(String encryptedKey, SecretKey masterKey) {
            return null;
         }
      };

      assertEquals(value, invalidMaster.decryptPassword(value, wrap(keyA)));
   }

   @Test
   void wrongKeyPassingPaddingCheckIsRejected() throws Exception {
      String[] parts = value.substring(AbstractPasswordEncryption.NEW_PREFIX.length()).split(":");
      byte[] iv = Base64.getDecoder().decode(parts[0]);
      byte[] encrypted = Base64.getDecoder().decode(parts[1]);
      SecretKey paddingValidKey = null;

      // about one wrong key in 256 decrypts without a padding error
      for(int i = 0; i < 20000 && paddingValidKey == null; i++) {
         SecretKey key = encryption.createSecretKey();

         try {
            encryption.decrypt(encrypted, iv, key);
            paddingValidKey = key;
         }
         catch(Exception ignore) {
            // wrong key detected by the padding check
         }
      }

      assertNotNull(paddingValidKey, "no wrong key passed the padding check");
      assertEquals(value, encryption.decryptPassword(value, wrap(paddingValidKey)));
   }

   @Test
   void malformedAesValuesAreReturnedAsIs() {
      String wrapped = wrap(keyA);
      String noColon = AbstractPasswordEncryption.NEW_PREFIX + "foo";
      String badBase64 = AbstractPasswordEncryption.NEW_PREFIX + "!!!!!!!!:@@@@@@@@";

      assertEquals(noColon, encryption.decryptPassword(noColon, wrapped));
      assertEquals(badBase64, encryption.decryptPassword(badBase64, wrapped));
   }

   @Test
   void unprefixedAndMasterValuesAreUnchanged() {
      assertEquals("plainpw", encryption.decryptPassword("plainpw", wrap(keyA)));
      assertEquals("plainpw", encryption.decryptPassword("plainpw"));
      String master = encryption.encryptMasterPassword(PASSWORD);
      assertEquals(PASSWORD, encryption.decryptPassword(master, wrap(keyA)));
   }

   @Test
   void wellFormedAesValueIsKeptWhenSecretKeyCannotBeRead() {
      // the secret key cannot be read, so the value cannot be decrypted either: keep it as is
      // (the master key fails before the secret key is read, so no context is needed)
      JcePasswordEncryption noMasterKey = new JcePasswordEncryption() {
         @Override
         protected SecretKey getMasterKey() {
            throw new IllegalStateException("no master key");
         }
      };

      assertEquals(value, noMasterKey.encryptPassword(value));
   }

   private String encryptWith(SecretKey key, String password) throws Exception {
      byte[][] result = encryption.encrypt(password.getBytes(StandardCharsets.UTF_16), key);
      Base64.Encoder encoder = Base64.getEncoder();
      return AbstractPasswordEncryption.NEW_PREFIX + encoder.encodeToString(result[1]) + ":" +
         encoder.encodeToString(result[0]);
   }

   private String wrap(SecretKey key) {
      return Base64.getEncoder().encodeToString(
         encryption.encryptSecretKey(key, encryption.getMasterKey()));
   }

   private static final String MASTER = "bug-77722-master-pw";
   private static final String PASSWORD = "s3cret";
   private JcePasswordEncryption encryption;
   private SecretKey keyA;
   private String value;
}
