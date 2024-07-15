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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.Callable;
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
         return decryptPassword(input.substring(4), keySpec);
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
         return decryptPassword(input.substring(4), keySpec);
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
            if(LOG.isDebugEnabled()) {
               LOG.warn("Failed to decrypt password, assuming that it was saved as clear text.", e);
            }
            else {
               LOG.warn("Failed to decrypt password, assuming that it was saved as clear text.");
            }

            return input;
         }
      }

      // clear text
      return input;
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
      SecretKey signingKey;
      String property = SreeEnv.getProperty("jwt.signing.key");

      if(property == null) {
         signingKey = createJwtSigningKey();
         byte[] encryptedKey = encryptJwtSigningKey(signingKey, getMasterKey());
         String encoded = Base64.getEncoder().encodeToString(encryptedKey);
         SreeEnv.setProperty("jwt.signing.key", encoded);
         SreeEnv.save();
      }
      else {
         signingKey = decryptJwtSigningKey(property, getMasterKey());
      }

      return signingKey;
   }

   private String decryptPassword(String input, SecretKey secretKey) {
      int index = input.indexOf(':', 4);

      if(index < 0) {
         return input;
      }

      try {
         Base64.Decoder decoder = Base64.getDecoder();
         byte[] iv = decoder.decode(input.substring(0, index));
         byte[] encrypted = decoder.decode(input.substring(index + 1));
         byte[] decrypted = decrypt(encrypted, iv, secretKey);

         return new String(decrypted, StandardCharsets.UTF_16);
      }
      catch(Exception e) {
         if(LOG.isDebugEnabled()) {
            LOG.warn("Failed to decrypt password, assuming that it was saved as clear text.", e);
         }
         else {
            LOG.warn("Failed to decrypt password, assuming that it was saved as clear text.");
         }

         return input;
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

         String keyProperty = SreeEnv.getProperty("password.encryption.key");

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
         String property = SreeEnv.getProperty("password.encryption.key");

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

   protected final boolean isMasterPasswordInvalid(SecretKey masterKey) {
      String home = ConfigurationContext.getContext().getHome();
      File file = FileSystemService.getInstance().getFile(home, "dbProp.properties");

      if(file.exists()) {
         Properties properties = new Properties();

         try(InputStream input = new FileInputStream(file)) {
            properties.load(input);
         }
         catch(Exception e) {
            LOG.debug("Failed to validate master password", e);
            return true;
         }

         String string = properties.getProperty("master.password.check");

         if(string != null && !string.isEmpty()) {
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
   private static final String LOCK_NAME = LocalPasswordEncryption.class.getName() + ".lock";

   private static final Logger LOG = LoggerFactory.getLogger(LocalPasswordEncryption.class);

   static ThreadLocal<char[]> masterPassword =
      ThreadLocal.withInitial(LocalPasswordEncryption::getMasterPassword);

   private static char[] getMasterPassword() {
      String password = System.getenv("INETSOFT_MASTER_PASSWORD");
      return password == null ? null : password.toCharArray();
   }
}
