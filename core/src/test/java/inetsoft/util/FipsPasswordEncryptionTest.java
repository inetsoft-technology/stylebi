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
 * Fixed bug (see Redmine #75541)
 *
 * [Bug #75541] FipsPasswordEncryption.createHmacKey() previously generated a 128-bit
 *       HmacSHA512 key, but JoseJwtService signs tokens with HS512 (JoseJwtService.java:137),
 *       and Nimbus MACSigner/MACVerifier require >= 256 bits. The key is now generated at
 *       512 bits, matching non-FIPS JcePasswordEncryption.
 *       Guard: jwsHs512SignVerify_roundTrip.
 */

/*
 * Cases deferred - require integration context:
 *
 * updateMasterPassword() — reads/writes SreeEnv password.hash.key; needs mockStatic or full context
 * getSSOKeyPair() — Cluster distributed lock + SreeEnv persistence
 * encryptPassword() / decryptPassword() — getSecretKey() lifecycle + SreeEnv round-trip
 *
 * getJwtSigningKey() self-heal path (Bug #75541) is covered by
 * getJwtSigningKey_undersizedLegacyKey_regeneratesAndPersists.
 */

import com.nimbusds.jose.*;
import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.SreeEnv;
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
import javax.crypto.spec.SecretKeySpec;
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

      // Documents current key material (algorithm + length), not HS512 correctness.
      // Companion: jwsHs512SignVerify_roundTrip is the real HS512 guard.
      @Test
      void createJwtSigningKey_producesHmacSha512Key() {
         SecretKey key = encryption.createJwtSigningKey();
         assertAll(
            () -> assertNotNull(key),
            () -> assertEquals("HmacSHA512", key.getAlgorithm()),
            () -> assertNotNull(key.getEncoded()),
            // Bug #75541: key must be 512 bits (64 bytes); HS512 requires >= 256 bits.
            () -> assertEquals(64, key.getEncoded().length,
               "Bug #75541: key must be 512-bit to satisfy HS512")
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

      // Companion: createJwtSigningKey_producesHmacSha512Key documents the 512-bit key length.
      // via: createJwsSigner() / createJwsVerifier() — same path as JoseJwtService HS512 tokens
      // Bug #75541: HS512 requires a key of >= 256 bits; guards the 512-bit key fix.
      @Test
      void jwsHs512SignVerify_roundTrip() throws Exception {
         SecretKey key = encryption.createJwtSigningKey();
         JWSObject jws = new JWSObject(new JWSHeader(JWSAlgorithm.HS512), new Payload("payload"));
         jws.sign(encryption.createJwsSigner(key));
         assertTrue(jws.verify(encryption.createJwsVerifier(key)));
      }

      // Clustered cold start: the cluster lock serializes nodes, but SreeEnv.getProperty() reads
      // this node's in-memory snapshot, which is refreshed asynchronously from key-value storage.
      // A node could therefore hold the lock, read a stale null for a key another node had
      // already persisted, and generate and store a second one — leaving each pod signing JWTs
      // with a different key. getJwtSigningKey() must consult the backing store before deciding
      // that no key exists.
      // Setup: persist a key, then evict it from the in-memory properties only, simulating the
      // node whose snapshot has not caught up yet.
      @Test
      void getJwtSigningKey_staleInMemoryCache_readsStorageInsteadOfRegenerating() throws Exception {
         SecretKey original = encryption.getJwtSigningKey();
         String persisted = SreeEnv.getProperty("jwt.signing.key");
         assertNotNull(persisted, "precondition: a key must have been persisted");

         try {
            evictFromInMemoryProperties("jwt.signing.key");
            Assumptions.assumeTrue(SreeEnv.getProperty("jwt.signing.key") == null,
               "could not simulate a stale in-memory property snapshot");
            Assumptions.assumeTrue(SreeEnv.getPropertyFromStorage("jwt.signing.key") != null,
               "the backing store is not readable in this test environment");

            SecretKey reread = encryption.getJwtSigningKey();

            assertArrayEquals(original.getEncoded(), reread.getEncoded(),
               "a stale in-memory null must not cause a second signing key to be generated");
         }
         finally {
            // Undo the in-memory eviction first so the property is in a known state, then clear
            // it the same way the sibling test does.
            SreeEnv.setProperty("jwt.signing.key", persisted);
            SreeEnv.remove("jwt.signing.key");
         }
      }

      // Removes a property from the in-memory snapshot without touching the backing store, so the
      // node looks like one whose asynchronous property refresh has not arrived yet.
      private void evictFromInMemoryProperties(String name) throws Exception {
         PropertiesEngine engine = PropertiesEngine.getInstance();
         java.lang.reflect.Method method =
            PropertiesEngine.class.getDeclaredMethod("getInternalProperties");
         method.setAccessible(true);
         ((java.util.Properties) method.invoke(engine)).remove(name);
      }

      // Bug #75541: self-heal path — an upgraded FIPS deployment with a persisted 128-bit
      // key must have getJwtSigningKey() regenerate and re-persist a 512-bit (64-byte) key.
      @Test
      void getJwtSigningKey_undersizedLegacyKey_regeneratesAndPersists() throws Exception {
         SecretKey legacyKey = new SecretKeySpec(new byte[16], "HmacSHA512");
         byte[] encrypted = encryption.encryptJwtSigningKey(legacyKey, encryption.getMasterKey());
         SreeEnv.setProperty("jwt.signing.key", Base64.getEncoder().encodeToString(encrypted));
         // save() as well as setProperty: in the scenario this covers, an older build had
         // *persisted* the undersized key, and getJwtSigningKey() reads the backing store rather
         // than the in-memory snapshot so that a stale snapshot cannot drive key regeneration.
         SreeEnv.save();

         try {
            SecretKey healed = encryption.getJwtSigningKey();
            assertEquals(64, healed.getEncoded().length,
               "Bug #75541: undersized key must be regenerated at 512 bits");

            // the regenerated key must be re-persisted, not just returned in-memory
            String persisted = SreeEnv.getProperty("jwt.signing.key");
            SecretKey reloaded = encryption.decryptJwtSigningKey(persisted, encryption.getMasterKey());
            assertEquals(64, reloaded.getEncoded().length);
         }
         finally {
            SreeEnv.remove("jwt.signing.key");
         }
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
