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

import com.nimbusds.jose.*;
import inetsoft.util.config.*;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * {@code PasswordEncryption} handles encrypting passwords using a global master password.
 *
 * @since 2020
 */
@SingletonManager.Singleton(PasswordEncryption.Reference.class)
public interface PasswordEncryption {
   /**
    * Encrypts a password.
    *
    * @param input the clear text password to encrypt.
    *
    * @return the base 64-encoded, encrypted password.
    */
   String encryptPassword(String input);

   /**
    * Encrypts a password directly with the system master password.
    *
    * @param input the clear text password to encrypt.
    *
    * @return the base 64-encoded, encrypted password.
    */
   String encryptMasterPassword(String input);

   /**
    * Decrypts a password.
    *
    * @param input        the password to decrypt.
    * @param encryptedKey the encryption key, encrypted by the master password.
    *
    * @return the decrypted password.
    */
   String decryptPassword(String input, String encryptedKey);

   /**
    * Decrypts a password.
    *
    * @param input the base 64-encoded, encrypted password.
    *
    * @return the decrypted password.
    */
   String decryptPassword(String input);

   /**
    * Decrypts a database credential stored by Vault secrets engine.
    *
    * @param input the password to decrypt.
    * @param dbType the database type.
    *
    * @return the decrypted password.
    */
   String decryptDBPassword(String input, String dbType);

   /**
    * Decrypts a password directly with the system master password.
    *
    * @param input the base 64-encoded, encrypted password.
    *
    * @return the clear text password.
    */
   String decryptMasterPassword(String input);

   /**
    * Changes the master from the one specified in the {@code INETSOFT_MASTER_PASSWORD} environment
    * variable to that specified.
    *
    * @param oldPassword the current master password.
    * @param newPassword the new master password.
    */
   void changeMasterPassword(char[] oldPassword, char[] newPassword);

   /**
    * Hashes a password.
    *
    * @param password   the clear-text password to hash.
    * @param algorithm  the hash algorithm.
    * @param salt       the salt to add to the clear text password. The salt is ignore if it is
    *                   {@code null} or the bcrypt algorithm is used.
    * @param appendSalt {@code true} to append the salt to the clear text password;
    *                   {@code false} to prepend the salt to the clear text password.
    *
    * @return the hashed password.
    */
   HashedPassword hash(String password, String algorithm, String salt, boolean appendSalt);

   /**
    * Checks if a given clear text password matches a hashed password.
    *
    * @param hashedPassword the hashed password string.
    * @param clearPassword  the clear text password string.
    * @param algorithm      the hash algorithm.
    * @param salt           the password salt.
    * @param appendSalt     {@code true} to append the salt to the clear text password;
    *                       {@code false} to prepend the salt to the clear text password.
    * @param encoder        a function that converts the hashed password bytes into a string.
    *
    * @return {@code true} if the passwords match, {@code false} otherwise.
    */
   boolean checkHashedPassword(String hashedPassword, String clearPassword, String algorithm,
                               String salt, boolean appendSalt, Function<byte[], String> encoder);

   /**
    * Gets the signing key for JSON web tokens (JWT).
    *
    * @return the signing key.
    *
    * @throws IOException if the signing key could not be saved.
    */
   SecretKey getJwtSigningKey() throws IOException;

   /**
    * Creates a new instance of {@link JWSVerifier}.
    *
    * @param signingKey the secret key used to generate the signature.
    *
    * @return a new verifier.
    */
   JWSVerifier createJwsVerifier(SecretKey signingKey) throws JOSEException;

   /**
    * Creates a new instance of {@link JWSSigner}.
    *
    * @param signingKey the secret key used to generate the signature.
    *
    * @return a new signer.
    */
   JWSSigner createJwsSigner(SecretKey signingKey) throws JOSEException;

   /**
    * Gets the SSL certificate helper.
    *
    * @return an SSL certificate helper instance.
    */
   SSLCertificateHelper getSSLCertificateHelper();

   /**
    * Creates a new instance of {@code PasswordEncryption}.
    *
    * @return a new instance.
    */
   static PasswordEncryption newInstance() {
      return SingletonManager.getInstance(PasswordEncryption.class);
   }

   static PasswordEncryption newLocalInstance(boolean encrypt) {
      if(encrypt && isEncryptForceLocal() || !encrypt && isDecryptForceLocal()) {
         return newInstance(new SecretsConfig());
      }

      return newLocalInstance(InetsoftConfig.getInstance().getSecrets());
   }

   static PasswordEncryption newLocalInstance() {
      return newLocalInstance(InetsoftConfig.getInstance().getSecrets());
   }

   static PasswordEncryption newLocalInstance(SecretsConfig secretsConfig) {
      if(!Tool.equals(secretsConfig.getType(), SecretsType.LOCAL.getName())) {
         SecretsConfig localSecretsConfig = new SecretsConfig();
         localSecretsConfig.setType(SecretsType.LOCAL.getName());
         localSecretsConfig.setFipsComplianceMode(secretsConfig.isFipsComplianceMode());
         secretsConfig = localSecretsConfig;
         return PasswordEncryption.newInstance(secretsConfig);
      }

      return newInstance();
   }

   /**
    * Creates a new instance {@code PasswordEncryption}.
    *
    * @return a new instance.
    */
   static PasswordEncryption newInstance(boolean fipsCompliant) {
      SecretsConfig config = new SecretsConfig();
      config.setFipsComplianceMode(fipsCompliant);
      return SingletonManager.getInstance(PasswordEncryption.class, config);
   }

   /**
    * Creates a new instance {@code PasswordEncryption}.
    *
    * @return a new instance.
    */
   static PasswordEncryption newInstance(SecretsConfig secretsConfig) {
      return  SingletonManager.getInstance(PasswordEncryption.class, secretsConfig);
   }

   /**
    * Determines if the password encryption is in FIPS 140-2, Level 1 compliance mode.
    *
    * @return {@code true} if FIPS compliant, {@code false} if not.
    */
   static boolean isFipsCompliant() {
      return InetsoftConfig.getInstance().isFipsComplianceMode();
   }

   /**
    * Gets a flag that indicates if the encryption should use the master password-derived key or the
    * the standard encryption key.
    *
    * @return {@code true} to use the system master key; {@code false} to use the instance key.
    */
   static boolean isForceMaster() {
      Boolean value = getConfigContext("inetsoft.util.PasswordEncryption.forceMaster");

      return value != null && value;
   }

   /**
    * Gets a flag that indicates if the encryption should use the master password-derived key or the
    * the standard encryption key.
    *
    * @param forceMaster {@code true} to use the system master key; {@code false} to use the
    *                    instance key.
    */
   static void setForceMaster(boolean forceMaster) {
      setConfigContext("inetsoft.util.PasswordEncryption.forceMaster", forceMaster);
   }

   /**
    * Gets a flag that indicates if decrypting should use the local encryption
    *
    * @return {@code true} to use the local encryption; {@code false} to use the config encryption.
    */
   static boolean isDecryptForceLocal() {
      Boolean value = getConfigContext("inetsoft.util.PasswordEncryption.forceLocal.decrypt");

      return value != null && value;
   }

   /**
    * Gets a flag that indicates if decrypting the local encryption
    *
    * @param forceLocal {@code true} to use the local encryption; {@code false} to use the
    *                    cofing encryption.
    */
   static void setDecryptForceLocal(boolean forceLocal) {
      setConfigContext("inetsoft.util.PasswordEncryption.forceLocal.decrypt", forceLocal);
   }

   /**
    * Gets a flag that indicates if encrypting should use the local encryption
    *
    * @return {@code true} to use the local encryption; {@code false} to use the config encryption.
    */
   static boolean isEncryptForceLocal() {
      Boolean value = getConfigContext("inetsoft.util.PasswordEncryption.forceLocal.encrypt");

      return value != null && value;
   }

   /**
    * Gets a flag that indicates if encrypting use the local encryption
    *
    * @param forceLocal {@code true} to use the local encryption; {@code false} to use the
    *                    cofing encryption.
    */
   static void setEncryptForceLocal(boolean forceLocal) {
      setConfigContext("inetsoft.util.PasswordEncryption.forceLocal.encrypt", forceLocal);
   }

   private static <T> void setConfigContext(String name, T value) {
      ThreadLocal<T> threadLocal;

      synchronized(PasswordEncryption.class) {
         ConfigurationContext context = ConfigurationContext.getContext();
         threadLocal = context.get(name);

         if(threadLocal == null) {
            threadLocal = new ThreadLocal<>();
            context.put(name, threadLocal);
         }
      }

      threadLocal.set(value);
   }

   private static <T> T getConfigContext(String name) {
      ThreadLocal<T> forceLocal;

      synchronized(PasswordEncryption.class) {
         ConfigurationContext context = ConfigurationContext.getContext();
         forceLocal = context.get(name);

         if(forceLocal == null) {
            forceLocal = new ThreadLocal<>();
            context.put(name, forceLocal);
         }
      }

      return forceLocal.get();
   }

   final class Reference extends SingletonManager.Reference<PasswordEncryption> {
      @Override
      public synchronized PasswordEncryption get(Object... parameters) {
         SecretsConfig secretsConfig = null;

         if(parameters.length > 0 && parameters[0] instanceof SecretsConfig) {
            secretsConfig = (SecretsConfig) parameters[0];
         }
         else {
            InetsoftConfig config = InetsoftConfig.getInstance();
            secretsConfig = config.getSecrets();
         }

         String type = secretsConfig.getType();
         PasswordEncryption encryption = map.get(secretsConfig);

         if(encryption == null) {
            for(PasswordEncryptionFactory factory : ServiceLoader.load(PasswordEncryptionFactory.class)) {
               if(factory.getType().equals(type)) {
                  encryption = factory.createPasswordEncryption(secretsConfig);
                  break;
               }
            }

            if(encryption == null) {
               throw new RuntimeException("Failed to get password encryption of type " + type);
            }

            map.put(secretsConfig, encryption);
         }

         return encryption;
      }

      @Override
      public synchronized void dispose() {
         map.clear();
      }

      private Map<SecretsConfig, PasswordEncryption> map = new HashMap<>();
   }
}
