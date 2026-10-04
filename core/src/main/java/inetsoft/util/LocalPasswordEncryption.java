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

import at.favre.lib.crypto.bcrypt.BCrypt;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.config.InetsoftConfig;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

abstract class LocalPasswordEncryption extends AbstractPasswordEncryption {
   LocalPasswordEncryption(boolean throwExceptions) {
      this.throwExceptions = throwExceptions;
   }

   @Override
   @SuppressWarnings({ "deprecation", "squid:CallToDeprecatedMethod"})
   public final String encryptPassword(String input) {
      if(input == null || input.isEmpty()) {
         return input;
      }

      if(PasswordEncryption.isForceMaster()) {
         return encryptMasterPassword(input);
      }

      if(isEncryptedWithSecretKey(input) && !isDecryptableWithCurrentKey(input)) {
         // Bug #77722, decryptPassword() returns a value that the current key cannot decrypt
         // (e.g. it was encrypted with another password.encryption.key) as is. Keep it as is
         // here too, so that saving its owner again does not bury the original ciphertext,
         // and it can still be decrypted once the right key is back. A value that the current
         // key can decrypt is never returned by decryptPassword(), so it is clear text that
         // only looks encrypted and is encrypted like any other text below; passing it through
         // would let a known ciphertext stand in for the secret it encrypts.
         return input;
      }

      try {
         SecretKey masterKey = getMasterKey();
         SecretKey secretKey = getSecretKey(masterKey);
         byte[] inputBytes = input.getBytes(StandardCharsets.UTF_16);
         byte[][] result = encrypt(inputBytes, secretKey);
         Base64.Encoder encoder = Base64.getEncoder();
         return NEW_PREFIX + encoder.encodeToString(result[1]) + ":" +
            encoder.encodeToString(result[0]);
      }
      catch(Exception e) {
         if(throwExceptions) {
            throw new RuntimeException("Failed to encrypt password", e);
         }
         else {
            LOG.warn(
               "Failed to encrypt password. The required algorithms may not be supported by the " +
               "current JVM or installed security packages. Using the simple encryption mechanism",
               e);
            return Tool.hashPassword(input);
         }
      }
   }

   @Override
   @SuppressWarnings({ "deprecation", "squid:CallToDeprecatedMethod"})
   public final String encryptMasterPassword(String input) {
      try {
         return MASTER_PREFIX + encryptWithMaster(input, getMasterKey());
      }
      catch(Exception e) {
         if(throwExceptions) {
            throw new RuntimeException("Failed to encrypt password", e);
         }
         else {
            LOG.warn(
               "Failed to encrypt password. The required algorithms may not be supported by the " +
               "current JVM or installed security packages. Using the simple encryption mechanism",
               e);
            return Tool.hashPassword(input);
         }
      }
   }

   @Override
   public final String decryptPassword(String input, String encryptedKey) {
      if(input.startsWith(NEW_PREFIX)) {
         SecretKey keySpec = decryptSecretKey(encryptedKey, getMasterKey());
         return decryptPassword(input, keySpec);
      }
      else if(input.startsWith(MASTER_PREFIX)) {
         return decryptMasterPassword(input);
      }

      return input;
   }

   @Override
   @SuppressWarnings({ "deprecation", "squid:CallToDeprecatedMethod"})
   public final String decryptPassword(String input) {
      if(input == null || input.isEmpty()) {
         return input;
      }

      if(input.startsWith(OLD_PREFIX)) {
         return Tool.unhashPassword(input);
      }

      if(input.startsWith(MASTER_PREFIX)) {
         return decryptMasterPassword(input);
      }

      if(input.startsWith(NEW_PREFIX)) {
         SecretKey keySpec = getSecretKey(getMasterKey());
         return decryptPassword(input, keySpec);
      }

      // clear text
      return input;
   }

   /**
    * Decrypts a password directly with the system master password.
    *
    * @param input the base 64-encoded, encrypted password.
    *
    * @return the clear text password.
    */
   @Override
   @SuppressWarnings({ "deprecation", "squid:CallToDeprecatedMethod"})
   public final String decryptMasterPassword(String input) {
      if(input == null || input.isEmpty()) {
         return input;
      }

      if(input.startsWith(OLD_PREFIX)) {
         return Tool.unhashPassword(input);
      }

      if(input.startsWith(NEW_PREFIX) || input.startsWith(MASTER_PREFIX)) {
         String encrypted;

         if(input.startsWith(NEW_PREFIX)) {
            encrypted = input.substring(4);
         }
         else {
            encrypted = input.substring(7);
         }

         try {
            return decryptWithMaster(encrypted, getMasterKey());
         }
         catch(Exception e) {
            String message;

            if(input.startsWith(MASTER_PREFIX)) {
               // Bug #77628, a master-encrypted value is never clear text. It most likely was
               // encrypted with a different master password, e.g. it is in an export from another
               // server. The value is still returned as is, so that callers checking for the
               // prefix keep working, but an import counts it to tell the user.
               message = "Failed to decrypt a master-encrypted password, it was most likely " +
                  "encrypted with a different master password. The encrypted value is used as is.";
               AtomicInteger failures = PasswordEncryption.getMasterDecryptFailures();

               if(failures != null) {
                  failures.incrementAndGet();
               }
            }
            else {
               message = "Failed to decrypt a password with the master password. The value is " +
                  "used as is.";
            }

            if(LOG.isDebugEnabled()) {
               LOG.warn(message, e);
            }
            else {
               LOG.warn(message);
            }

            return input;
         }
      }

      // clear text
      return input;
   }

   @Override
   public String decryptDBPassword(String input, String dbType) {
      return null;
   }

   @Override
   public HashedPassword hash(String password, String algorithm, String salt, boolean appendSalt) {
      try {
         if(algorithm == null || algorithm.equalsIgnoreCase("none")) {
            return new HashedPassword(password, null);
         }
         else if(algorithm.equalsIgnoreCase("bcrypt")) {
            byte[] saltBytes = new byte[16];
            Tool.getSecureRandom().nextBytes(saltBytes);
            String hash = new String(
               BCrypt.with(Tool.getSecureRandom()).hashToChar(10, password.toCharArray()));
            return new HashedPassword(hash, "bcrypt");
         }
         else {
            String hash =
               hashWithDigest(password, algorithm, salt, appendSalt, Tool::encodeAscii85);
            return new HashedPassword(hash, algorithm);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to hash password", e);
      }

      return new HashedPassword(null, null);
   }

   @Override
   public boolean checkHashedPassword(String hashedPassword, String clearPassword, String algorithm,
                                      String salt, boolean appendSalt,
                                      Function<byte[], String> encoder)
   {
      try {
         if(algorithm == null || algorithm.equalsIgnoreCase("none")) {
            return Tool.equals(hashedPassword, clearPassword);
         }
         else if(algorithm.equalsIgnoreCase("bcrypt")) {
            return BCrypt.verifyer()
               .verify(clearPassword.toCharArray(), hashedPassword.toCharArray()).verified;
         }
         else {
            String test = hashWithDigest(clearPassword, algorithm, salt, appendSalt, encoder);
            return test.equals(hashedPassword);
         }
      }
      catch(Exception e) {
         // unsupported algorithm, wrong algorithm, etc. should be logged
         LOG.error("Failed to hash password", e);
         return false;
      }
   }

   @Override
   public final SecretKey getJwtSigningKey() throws IOException {
      // Guard the read-check-regenerate sequence with the cluster lock, matching
      // getSSOKeyPair(). Without it, an upgraded clustered FIPS deployment could have every
      // node independently regenerate and persist a different key at once (the self-heal
      // branch below), causing transient cross-node JWT verification failures.
      //
      // The lock only serializes the nodes; it does not make the check correct on its own.
      // SreeEnv.getProperty() serves this node's in-memory snapshot, which the key-value storage
      // change listener refreshes asynchronously (debounced). Read the backing store FIRST and
      // treat the snapshot only as a fallback for when storage is unreadable. Storage-first
      // matters in two places:
      //   - a simultaneous multi-node start, where a node can hold the lock and still see a stale
      //     null for a key another node just persisted, and generate a second one;
      //   - a re-read triggered by the property-change listener, which fires BEFORE the debounced
      //     snapshot refresh, so the snapshot still holds the OLD key at that point. Checking the
      //     store only when the snapshot is null would silently keep the stale key.
      Lock lock = Cluster.getInstance().getLock(LOCK_NAME);
      lock.lock();

      try {
         SecretKey signingKey;
         String property = SreeEnv.getPropertyFromStorage("jwt.signing.key");

         if(property == null) {
            property = SreeEnv.getProperty("jwt.signing.key");
         }

         if(property == null) {
            signingKey = createAndStoreJwtSigningKey();
         }
         else {
            signingKey = decryptJwtSigningKey(property, getMasterKey());

            // Bug #75541: earlier FIPS builds persisted an undersized (128-bit) HmacSHA512
            // signing key, which HS512 sign/verify rejects (>= 256 bits required). Regenerate
            // it so upgraded deployments recover. JWTs are short-lived and do not survive a
            // restart, so replacing the key has no impact.
            byte[] encoded = signingKey == null ? null : signingKey.getEncoded();

            if(encoded == null || encoded.length < JWT_SIGNING_KEY_MIN_BYTES) {
               signingKey = createAndStoreJwtSigningKey();
            }
         }

         return signingKey;
      }
      finally {
         lock.unlock();
      }
   }

   private SecretKey createAndStoreJwtSigningKey() throws IOException {
      SecretKey signingKey = createJwtSigningKey();
      byte[] encryptedKey = encryptJwtSigningKey(signingKey, getMasterKey());
      String encoded = Base64.getEncoder().encodeToString(encryptedKey);
      SreeEnv.setProperty("jwt.signing.key", encoded);
      SreeEnv.save();
      return signingKey;
   }

   @Override
   public final KeyPair getSSOKeyPair() throws IOException {
      Lock lock = Cluster.getInstance().getLock(LOCK_NAME);
      lock.lock();

      try {
         // Read the backing store first; the in-memory snapshot is only a fallback for when
         // storage is unreadable. See getJwtSigningKey() for why storage-first (rather than
         // storage-as-a-null-fallback) is required: the snapshot is stale both during a
         // simultaneous multi-node start and at the moment the property-change listener fires.
         String privateKeyProperty = SreeEnv.getPasswordFromStorage("sso.rsa.private.key");
         String publicKeyProperty = SreeEnv.getPropertyFromStorage("sso.rsa.public.key");

         if(privateKeyProperty == null) {
            privateKeyProperty = SreeEnv.getPassword("sso.rsa.private.key");
         }

         if(publicKeyProperty == null) {
            publicKeyProperty = SreeEnv.getProperty("sso.rsa.public.key");
         }

         if(privateKeyProperty == null || publicKeyProperty == null) {
            KeyPair keyPair = createSSOKeyPair();
            byte[] encryptedPrivateKey = encryptSSOPrivateKey(keyPair.getPrivate(), getMasterKey());
            String encodedPrivateKey = Base64.getEncoder().encodeToString(encryptedPrivateKey);
            String encodedPublicKey = Base64.getEncoder().encodeToString(
               keyPair.getPublic().getEncoded());

            SreeEnv.setPassword("sso.rsa.private.key", encodedPrivateKey);
            SreeEnv.setProperty("sso.rsa.public.key", encodedPublicKey);
            SreeEnv.save();

            return keyPair;
         }
         else {
            PrivateKey privateKey = decryptSSOPrivateKey(privateKeyProperty, getMasterKey());
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyProperty);
            X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(pubSpec);

            return new KeyPair(publicKey, privateKey);
         }
      }
      catch(GeneralSecurityException e) {
         throw new IOException("Failed to get SSO key pair", e);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Decrypts a value encrypted with the secret key (password.encryption.key).
    *
    * @param input     the encrypted value, including the {@link #NEW_PREFIX} prefix.
    * @param secretKey the secret key, may be {@code null}.
    *
    * @return the clear text password, or {@code input} as is if it cannot be decrypted.
    */
   private String decryptPassword(String input, SecretKey secretKey) {
      try {
         String decrypted = decryptWithSecretKey(input, secretKey);
         return decrypted == null ? input : decrypted;
      }
      catch(Exception e) {
         // Bug #77722, the value is never clear text. Return it with its prefix, so that it is
         // not saved again as the clear text password (see encryptPassword()).
         String message = "Failed to decrypt a password, it was most likely encrypted with a " +
            "different password.encryption.key (e.g. after a storage restore), or the master " +
            "password (INETSOFT_MASTER_PASSWORD) is wrong. The encrypted value is used as is.";

         if(LOG.isDebugEnabled()) {
            LOG.warn(message, e);
         }
         else {
            LOG.warn(message);
         }

         return input;
      }
   }

   /**
    * Decrypts a value encrypted with the secret key, without logging.
    *
    * @param input     the encrypted value, including the {@link #NEW_PREFIX} prefix.
    * @param secretKey the secret key, may be {@code null}.
    *
    * @return the clear text password, or {@code null} if it cannot be decrypted with the key.
    */
   private String tryDecrypt(String input, SecretKey secretKey) {
      try {
         return decryptWithSecretKey(input, secretKey);
      }
      catch(Exception e) {
         return null;
      }
   }

   /**
    * Decrypts a value encrypted with the secret key.
    *
    * @param input     the encrypted value, including the {@link #NEW_PREFIX} prefix.
    * @param secretKey the secret key, may be {@code null}.
    *
    * @return the clear text password, or {@code null} if the value has no IV separator.
    *
    * @throws Exception if the value cannot be decrypted with the key.
    */
   private String decryptWithSecretKey(String input, SecretKey secretKey) throws Exception {
      String encryptedValue = input.substring(NEW_PREFIX.length());
      int index = encryptedValue.indexOf(':', 4);

      if(index < 0) {
         return null;
      }

      Base64.Decoder decoder = Base64.getDecoder();
      byte[] iv = decoder.decode(encryptedValue.substring(0, index));
      byte[] encrypted = decoder.decode(encryptedValue.substring(index + 1));
      byte[] decrypted = decrypt(encrypted, iv, secretKey);

      // Bug #77722, AES/CBC/PKCS5Padding is not authenticated, so a wrong key decrypts
      // without an error about once in 256 tries. Every value is encrypted from
      // String.getBytes(UTF_16), which always starts with the big-endian byte order mark,
      // so anything else was decrypted with the wrong key.
      if(decrypted.length < 2 || decrypted.length % 2 != 0 ||
         (decrypted[0] & 0xff) != 0xfe || (decrypted[1] & 0xff) != 0xff)
      {
         throw new GeneralSecurityException("The decrypted password is not UTF-16 text");
      }

      return new String(decrypted, StandardCharsets.UTF_16);
   }

   /**
    * Determines if a value can be decrypted with the current secret key. If the secret key
    * cannot be read (e.g. the master password is wrong), the value cannot be decrypted, so
    * this returns {@code false}.
    */
   private boolean isDecryptableWithCurrentKey(String input) {
      SecretKey secretKey;

      try {
         secretKey = getSecretKey(getMasterKey());
      }
      catch(Exception e) {
         LOG.debug("Failed to read the secret key", e);
         return false;
      }

      if(secretKey == null) {
         return false;
      }

      return tryDecrypt(input, secretKey) != null;
   }

   /**
    * Determines if a value has the exact form written by {@link #encryptPassword(String)}:
    * the {@link #NEW_PREFIX} prefix, the Base64 16-byte IV, a colon, and the Base64 ciphertext,
    * a non-empty multiple of the 16-byte AES block.
    */
   private static boolean isEncryptedWithSecretKey(String input) {
      if(!input.startsWith(NEW_PREFIX)) {
         return false;
      }

      String encryptedValue = input.substring(NEW_PREFIX.length());
      int index = encryptedValue.indexOf(':');

      if(index < 0) {
         return false;
      }

      byte[] iv = decodeBase64Strictly(encryptedValue.substring(0, index));
      byte[] encrypted = decodeBase64Strictly(encryptedValue.substring(index + 1));
      return iv != null && iv.length == AES_BLOCK_SIZE && encrypted != null &&
         encrypted.length > 0 && encrypted.length % AES_BLOCK_SIZE == 0;
   }

   /**
    * Decodes Base64 text, returning {@code null} unless it is in the canonical, padded form.
    */
   private static byte[] decodeBase64Strictly(String text) {
      try {
         byte[] data = Base64.getDecoder().decode(text);
         return Base64.getEncoder().encodeToString(data).equals(text) ? data : null;
      }
      catch(IllegalArgumentException e) {
         return null;
      }
   }

   @Override
   public final void changeMasterPassword(char[] oldPassword, char[] newPassword) {
      Lock lock = Cluster.getInstance().getLock(LOCK_NAME);
      lock.lock();

      try {
         SecretKey oldMasterKey = createMasterKey(oldPassword);
         SecretKey newMasterKey = createMasterKey(newPassword);

         if(isMasterPasswordInvalid(oldMasterKey)) {
            throw new IllegalArgumentException("The master password is incorrect.");
         }

         // Storage first, for the same reason as getSecretKey(): a stale null from the
         // in-memory snapshot here would skip re-encrypting the stored key under the new
         // master password, leaving it decryptable only with the old one. A read failure is
         // propagated -- this is an explicit administrative action, so refusing it is far
         // better than half-applying it.
         String keyProperty = SreeEnv.getPropertyFromStorage("password.encryption.key");

         if(keyProperty == null) {
            keyProperty = SreeEnv.getProperty("password.encryption.key");
         }

         if(keyProperty != null) {
            SecretKey key = decryptSecretKey(keyProperty, oldMasterKey);
            byte[] data = encryptSecretKey(key, newMasterKey);
            keyProperty = Base64.getEncoder().encodeToString(data);
            SreeEnv.setProperty("password.encryption.key", keyProperty);

            try {
               SreeEnv.save();
            }
            catch(IOException e) {
               throw new RuntimeException("Failed to save sree.properties", e);
            }
         }

         updateMasterPassword(oldMasterKey, newMasterKey);
         masterPassword.set(newPassword);
         InetsoftConfig config = InetsoftConfig.getInstance();

         try {
            InetsoftConfig.save(config);
         }
         finally {
            masterPassword.remove();
         }
      }
      finally {
         lock.unlock();
      }
   }

   protected abstract SecretKey getMasterKey();

   protected abstract SecretKey createMasterKey(char[] password);

   protected final SecretKey getSecretKey(SecretKey masterKey) {
      Lock lock = Cluster.getInstance().getLock(LOCK_NAME);
      lock.lock();

      try {
         SecretKey key;
         // Storage first, snapshot only as a fallback, matching getJwtSigningKey() and
         // getSSOKeyPair(): the in-memory snapshot refreshes asynchronously, so the cluster
         // lock alone does not stop two nodes from each seeing "absent" and generating a
         // different key. Of the four keys read this way, this one is the highest-stakes: a
         // second password.encryption.key would leave every already-stored password
         // undecryptable.
         //
         // Unlike the other three, a storage read failure here does NOT propagate. This method
         // is on the path of every password encrypt/decrypt in the process, so failing closed
         // on a transient read error would take down unrelated work that the already-loaded
         // snapshot can serve correctly. The safety property that must hold is the narrower
         // one: never generate a new key while the stored state is unknown. So a read failure
         // falls back to the snapshot, and if the snapshot has no key either, the failure is
         // rethrown rather than answered by minting a second key.
         String property;
         RuntimeException storageError = null;

         try {
            property = SreeEnv.getPropertyFromStorage("password.encryption.key");
         }
         catch(RuntimeException e) {
            storageError = e;
            property = null;
         }

         if(property == null) {
            property = SreeEnv.getProperty("password.encryption.key");
         }

         if(property == null && storageError != null) {
            throw storageError;
         }

         if(property == null) {
            key = createSecretKey();
            byte[] data = encryptSecretKey(key, masterKey);
            String encoded = Base64.getEncoder().encodeToString(data);
            SreeEnv.setProperty("password.encryption.key", encoded);
            SreeEnv.save();
         }
         else {
            key = decryptSecretKey(property, masterKey);
         }

         return key;
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to save sree.properties", e);
      }
      finally {
         lock.unlock();
      }
   }

   protected abstract SecretKey createSecretKey();

   protected abstract SecretKey createJwtSigningKey();

   protected abstract byte[] encryptWithMaster(byte[] input, SecretKey masterKey);

   protected final String encryptWithMaster(String password, SecretKey masterKey) {
      if(password == null || password.isEmpty()) {
         return password;
      }

      byte[] data = password.getBytes(StandardCharsets.UTF_16);
      byte[] encrypted = encryptWithMaster(data, masterKey);
      return Base64.getEncoder().encodeToString(encrypted);
   }

   protected final String decryptWithMaster(String input, SecretKey masterKey) {
      byte[] data = Base64.getDecoder().decode(input);
      byte[] decrypted = decryptWithMaster(data, masterKey);
      return new String(decrypted, StandardCharsets.UTF_16);
   }

   protected abstract byte[] decryptWithMaster(byte[] encrypted, SecretKey masterKey);

   protected abstract byte[][] encrypt(byte[] input, SecretKey secretKey) throws Exception;

   protected abstract byte[] decrypt(byte[] encrypted, byte[] iv, SecretKey secretKey)
      throws Exception;

   protected abstract byte[] encryptSecretKey(SecretKey secretKey, SecretKey masterKey);

   protected abstract SecretKey decryptSecretKey(String encryptedKey, SecretKey masterKey);

   protected abstract byte[] encryptJwtSigningKey(SecretKey key, SecretKey masterKey);

   protected abstract SecretKey decryptJwtSigningKey(String encryptedKey, SecretKey masterKey);

   protected abstract KeyPair createSSOKeyPair();

   protected abstract byte[] encryptSSOPrivateKey(PrivateKey key, SecretKey masterKey);

   protected abstract PrivateKey decryptSSOPrivateKey(String encryptedKey, SecretKey masterKey);

   protected final boolean isMasterPasswordInvalid(SecretKey masterKey) {
      if(InetsoftConfig.BOOTSTRAP_INSTANCE != null) {
         String string = (String) InetsoftConfig.BOOTSTRAP_INSTANCE
            .getAdditionalProperties().get("masterPasswordCheck");

         if(!StringUtils.isBlank(string)) {
            return isMasterPasswordInvalid(string, masterKey);
         }
      }

      return false;
   }

   private boolean isMasterPasswordInvalid(String test, SecretKey masterKey) {
      try {
         String encrypted;

         if(test.startsWith(NEW_PREFIX)) {
            encrypted = test.substring(4);
         }
         else {
            encrypted = test.substring(7);
         }

         if(!"INETSOFT_MASTER_PASSWORD".equals(decryptWithMaster(encrypted, masterKey))) {
            return true;
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to validate master password", e);
         return true;
      }

      return false;
   }

   protected void updateMasterPassword(SecretKey oldMasterKey, SecretKey newMasterKey) {
      // no-op
   }

   private String hashWithDigest(String password, String algorithm, String salt, boolean appendSalt,
                                 Function<byte[], String> encoder)
      throws NoSuchAlgorithmException
   {
      String salted;

      if(salt == null) {
         salted = password;
      }
      else if(appendSalt) {
         salted = password + salt;
      }
      else {
         salted = salt + password;
      }

      MessageDigest md = MessageDigest.getInstance(algorithm);
      return encoder.apply(md.digest(salted.getBytes(StandardCharsets.UTF_8)));
   }

   protected final <T> T withLock(Callable<T> fn) throws Exception {
      Lock lock = Cluster.getInstance().getLock(LOCK_NAME);
      lock.lock();

      try {
         return fn.call();
      }
      finally {
         lock.unlock();
      }
   }

   private final boolean throwExceptions;
   // Minimum JWT signing key size in bytes (256 bits) required by the HS512 algorithm.
   private static final int JWT_SIGNING_KEY_MIN_BYTES = 32;
   private static final int AES_BLOCK_SIZE = 16;
   private static final String LOCK_NAME = LocalPasswordEncryption.class.getName() + ".lock";

   private static final Logger LOG = LoggerFactory.getLogger(LocalPasswordEncryption.class);

   static ThreadLocal<char[]> masterPassword = ThreadLocal.withInitial(LocalPasswordEncryption::getMasterPassword);

   private static char[] getMasterPassword() {
      String password = System.getenv("INETSOFT_MASTER_PASSWORD");
      return password == null ? null : password.toCharArray();
   }
}
