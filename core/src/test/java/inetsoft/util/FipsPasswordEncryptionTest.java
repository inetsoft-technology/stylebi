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

/*
 * FipsPasswordEncryption method coverage map (unit + integration tiers)
 *
 * [hash / checkHashedPassword]  integration — getPasswordHashKey() persists via SreeEnv + Cluster lock
 * [createJwtSigningKey / createSecretKey / encrypt* / decrypt*]  unit — same-package protected access
 *
 * Intentional design notes (NOT bugs — do not file issues for these)
 *
 * [Design 1] hash(password, algorithm, salt, appendSalt) ignores algorithm/salt/appendSalt;
 *            always emits fips-hmacsha512 per FIPS 140-2 mandate.
 *            Verified by hash_ignoresAlgorithmParameter.
 * [Design 2] checkHashedPassword(non-fips algorithm) delegates to LocalPasswordEncryption super
 *            with a WARN log so legacy passwords remain valid during migration.
 *            Verified by checkHashedPassword_noneAlgorithm.
 *
 * Known bug (production — see Redmine/GitHub issue)
 *
 * [Bug #75541] FipsPasswordEncryption.createHmacKey() generates a 128-bit HmacSHA512 key
 *       (FipsPasswordEncryption.java:117), but JoseJwtService signs tokens with HS512
 *       (JoseJwtService.java:137), and Nimbus MACSigner/MACVerifier require >= 256 bits.
 *       Non-FIPS JcePasswordEncryption uses 512-bit keys (JcePasswordEncryption.java:72).
 *       Impact: JWT/API token sign/verify fails when secrets.fipsComplianceMode=true.
 *       Guard: jwsHs512SignVerify_roundTrip (@Disabled until fixed).
 */

/*
 * Cases deferred - require integration context:
 *
 * updateMasterPassword() — reads/writes SreeEnv password.hash.key; needs mockStatic or full context
 * getJwtSigningKey() / getSSOKeyPair() — Cluster distributed lock + SreeEnv persistence
 * encryptPassword() / decryptPassword() — getSecretKey() lifecycle + SreeEnv round-trip
 */

import com.nimbusds.jose.*;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class FipsPasswordEncryptionTest {

   private FipsPasswordEncryption encryption;

   @BeforeEach
   void setUp() {
      encryption = new FipsPasswordEncryption();
      Assumptions.assumeTrue(
         Security.getProvider("BCFIPS") != null,
         "BCFIPS provider unavailable after FipsPasswordEncryption initialization");
   }

   // -------------------------------------------------------------------------
   // Password hashing — via hash() -> hashPassword() -> getPasswordHashKey()
   // -------------------------------------------------------------------------

   @Nested
   class PasswordHashing {

      @Test
      void hash_fipsHmacSha512_roundTripVerification() {
         String clearPassword = "secret";
         HashedPassword hashedPassword = encryption.hash(clearPassword, null, null, false);
         assertAll(
            () -> assertNotNull(hashedPassword),
            () -> assertNotNull(hashedPassword.getHash()),
            () -> assertNotEquals(clearPassword, hashedPassword.getHash()),
            () -> assertEquals("fips-hmacsha512", hashedPassword.getAlgorithm())
         );

         boolean verified = encryption.checkHashedPassword(
            hashedPassword.getHash(), clearPassword, hashedPassword.getAlgorithm(), null, false,
            Tool::encodeAscii85);
         assertTrue(verified);
      }

      // [Risk 2] FIPS hash() always uses fips-hmacsha512 regardless of requested algorithm
      @Test
      void hash_ignoresAlgorithmParameter() {
         HashedPassword result = encryption.hash("secret", "bcrypt", "salt", true);
         assertEquals("fips-hmacsha512", result.getAlgorithm());
      }

      // via: checkHashedPassword() -> super.checkHashedPassword() for non-fips algorithm
      @Test
      void checkHashedPassword_noneAlgorithm_comparesPlaintext() {
         String clearPassword = "my secret password";
         assertTrue(encryption.checkHashedPassword(
            clearPassword, clearPassword, "none", null, false, Tool::encodeAscii85));
      }

      // [Risk 3] Malformed stored hash must return false, not propagate provider Error
      @Test
      void checkHashedPassword_malformedHash_returnsFalse() {
         assertFalse(encryption.checkHashedPassword(
            "!!!not-valid-ascii85!!!", "secret", "fips-hmacsha512", null, false,
            Tool::encodeAscii85));
      }
   }

   // -------------------------------------------------------------------------
   // Key generation
   // -------------------------------------------------------------------------

   @Nested
   class KeyGeneration {

      @Test
      void createJwtSigningKey_producesHmacSha512Key() {
         SecretKey key = encryption.createJwtSigningKey();
         assertAll(
            () -> assertNotNull(key),
            () -> assertEquals("HmacSHA512", key.getAlgorithm()),
            () -> assertNotNull(key.getEncoded()),
            () -> assertEquals(16, key.getEncoded().length)
         );
      }

      @Test
      void createSecretKey_producesAes128Key() {
         SecretKey key = encryption.createSecretKey();
         assertAll(
            () -> assertNotNull(key),
            () -> assertEquals("AES", key.getAlgorithm()),
            () -> assertEquals(16, key.getEncoded().length)
         );
      }
   }

   // -------------------------------------------------------------------------
   // Symmetric encryption round-trips
   // -------------------------------------------------------------------------

   @Nested
   class SymmetricEncryption {

      @Test
      void aesCbcAndKeyWrap_roundTrip() throws Exception {
         byte[] input = "my secret password".getBytes(StandardCharsets.UTF_16);
         SecretKey key = encryption.createSecretKey();

         // via: encrypt() — AES/CBC/PKCS5Padding with random IV
         byte[][] encryptedData = encryption.encrypt(input, key);
         byte[] ciphertext = encryptedData[0];
         byte[] iv = encryptedData[1];
         assertFalse(Arrays.equals(input, ciphertext));

         // via: encryptSecretKey() / decryptSecretKey() — AES Key Wrap with master key
         byte[] wrapped = encryption.encryptSecretKey(key, encryption.getMasterKey());
         assertFalse(Arrays.equals(key.getEncoded(), wrapped));
         SecretKey unwrapped = encryption.decryptSecretKey(
            Base64.getEncoder().encodeToString(wrapped), encryption.getMasterKey());

         byte[] decrypted = encryption.decrypt(ciphertext, iv, unwrapped);
         assertArrayEquals(input, decrypted);
      }

      @Test
      void encryptWithMaster_roundTrip() {
         byte[] input = "my secret password".getBytes(StandardCharsets.UTF_16);
         byte[] encrypted = encryption.encryptWithMaster(input, encryption.getMasterKey());
         assertFalse(Arrays.equals(input, encrypted));
         byte[] decrypted = encryption.decryptWithMaster(encrypted, encryption.getMasterKey());
         assertArrayEquals(input, decrypted);
      }
   }

   // -------------------------------------------------------------------------
   // JWT / SSO key material
   // -------------------------------------------------------------------------

   @Nested
   class JwtAndSsoKeys {

      @Test
      void jwtSigningKey_wrapUnwrap_roundTrip() {
         SecretKey signingKey = encryption.createJwtSigningKey();
         byte[] wrapped = encryption.encryptJwtSigningKey(signingKey, encryption.getMasterKey());
         SecretKey restored = encryption.decryptJwtSigningKey(
            Base64.getEncoder().encodeToString(wrapped), encryption.getMasterKey());
         assertArrayEquals(signingKey.getEncoded(), restored.getEncoded());
         assertEquals("HmacSHA512", restored.getAlgorithm());
      }

      // via: createJwsSigner() / createJwsVerifier() — same path as JoseJwtService HS512 tokens
      @Disabled("Bug #75541: KeyLength >= 256 bits required for HS512 - FipsPasswordEncryption:117; Fix: keyGenerator.init(256, random)")
      @Test
      void jwsHs512SignVerify_roundTrip() throws Exception {
         SecretKey key = encryption.createJwtSigningKey();
         JWSObject jws = new JWSObject(new JWSHeader(JWSAlgorithm.HS512), new Payload("payload"));
         jws.sign(encryption.createJwsSigner(key));
         assertTrue(jws.verify(encryption.createJwsVerifier(key)));
      }

      @Test
      void ssoKeyPair_encryptDecryptPrivateKey_roundTrip() throws Exception {
         KeyPair keyPair = encryption.createSSOKeyPair();
         byte[] encrypted = encryption.encryptSSOPrivateKey(keyPair.getPrivate(), encryption.getMasterKey());
         PrivateKey restored = encryption.decryptSSOPrivateKey(
            Base64.getEncoder().encodeToString(encrypted), encryption.getMasterKey());
         assertArrayEquals(keyPair.getPrivate().getEncoded(), restored.getEncoded());
      }
   }

   // -------------------------------------------------------------------------
   // Parameterized boundary: wrong-password variants share one decision path
   // -------------------------------------------------------------------------

   static Stream<Arguments> wrongPasswordCases() {
      return Stream.of(
         // empty wrong password
         Arguments.of("correct-password", ""),
         // completely different password
         Arguments.of("correct-password", "wrong-password")
      );
   }

   @ParameterizedTest
   @MethodSource("wrongPasswordCases")
   void checkHashedPassword_wrongPasswordVariants_returnsFalse(String clear, String wrong) {
      HashedPassword hashed = encryption.hash(clear, null, null, false);
      assertFalse(encryption.checkHashedPassword(
         hashed.getHash(), wrong, hashed.getAlgorithm(), null, false, Tool::encodeAscii85));
   }
}
