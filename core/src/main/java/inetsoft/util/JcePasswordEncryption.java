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
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.util.Base64;

/**
 * {@code JcePasswordEncryption} is an implementation of {@link PasswordEncryption} that uses the
 * standard JCE implementation.
 */
class JcePasswordEncryption extends LocalPasswordEncryption {
   /**
    * Creates a new instance of {@code DefaultPasswordEncryption}.
    */
   JcePasswordEncryption() {
      super(false);
      char[] password = masterPassword.get();

      if(password == null) {
         password = new char[] { 's', 'u', 'c', 'c', 'e', 's', 's', '1', '2', '3' };
      }

      masterKey = createMasterKey(password);

      if(isMasterPasswordInvalid(masterKey)) {
         invalidMasterPassword = true;
         LOG.error(
            "The master password supplied in the INETSOFT_MASTER_PASSWORD environment variable " +
            "is incorrect.");
      }
      else {
         invalidMasterPassword = false;
      }
   }

   @Override
   protected boolean isFipsCompliant() {
      return false;
   }

   @Override
   protected SecretKey createJwtSigningKey() {
      try {
         SecureRandom random = Tool.getSecureRandom();
         random.nextBytes(new byte[64]);
         KeyGenerator keyGenerator = KeyGenerator.getInstance(JWT_SIGNING_ALGORITHM);
         keyGenerator.init(512, random);
         return keyGenerator.generateKey();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to generate JWT signing key", e);
      }
   }

   @Override
   protected byte[] encryptJwtSigningKey(SecretKey key, SecretKey masterKey) {
      return key.getEncoded();
   }

   @Override
   protected SecretKey decryptJwtSigningKey(String encryptedKey, SecretKey masterKey) {
      byte[] encoded = Base64.getDecoder().decode(encryptedKey);
      return new SecretKeySpec(encoded, JWT_SIGNING_ALGORITHM);
   }

   @Override
   public JWSVerifier createJwsVerifier(SecretKey signingKey) throws JOSEException {
      return new MACVerifier(signingKey);
   }

   @Override
   public JWSSigner createJwsSigner(SecretKey signingKey) throws JOSEException {
      return new MACSigner(signingKey);
   }

   @Override
   protected byte[][] encrypt(byte[] input, SecretKey secretKey) throws Exception {
      Cipher pbeCipher = Cipher.getInstance(PASSWORD_ALGORITHM);
      pbeCipher.init(Cipher.ENCRYPT_MODE, secretKey);
      AlgorithmParameters parameters = pbeCipher.getParameters();
      IvParameterSpec ivParameterSpec = parameters.getParameterSpec(IvParameterSpec.class);
      byte[] cryptoText = pbeCipher.doFinal(input);
      byte[] iv = ivParameterSpec.getIV();
      return new byte[][] { cryptoText, iv };
   }

   @Override
   protected byte[] decrypt(byte[] encrypted, byte[] iv, SecretKey secretKey) throws Exception {
      Cipher cipher = Cipher.getInstance(PASSWORD_ALGORITHM);
      IvParameterSpec parameterSpec = new IvParameterSpec(iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
      return cipher.doFinal(encrypted);
   }

   @Override
   protected SecretKey decryptSecretKey(String encryptedKey, SecretKey masterKey) {
      if(invalidMasterPassword) {
         return null;
      }

      byte[] data = Base64.getDecoder().decode(encryptedKey);
      byte[] keyData = decryptWithMaster(data, masterKey);
      return new SecretKeySpec(keyData, "AES");
   }

   @Override
   protected byte[] encryptSecretKey(SecretKey secretKey, SecretKey masterKey) {
      return encryptWithMaster(secretKey.getEncoded(), masterKey);
   }

   @Override
   protected byte[] encryptWithMaster(byte[] input, SecretKey masterKey) {
      byte[] iv = new byte[16];
      Tool.getSecureRandom().nextBytes(iv);

      try {
         Cipher cipher = Cipher.getInstance(MASTER_ALGORITHM);
         GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
         cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);

         byte[] encrypted = cipher.doFinal(input);
         byte[] output = new byte[encrypted.length + 16];
         System.arraycopy(iv, 0, output, 0, 16);
         System.arraycopy(encrypted, 0, output, 16, encrypted.length);

         return output;
      }
      catch(GeneralSecurityException e) {
         throw new RuntimeException(
            "Failed to encrypt secret key. The required algorithms may not be supported by the " +
            "current JVM or installed security packages.", e);
      }
   }

   @Override
   protected byte[] decryptWithMaster(byte[] encrypted, SecretKey masterKey) {
      try {
         byte[] iv = new byte[16];
         byte[] keyData = new byte[encrypted.length - 16];
         System.arraycopy(encrypted, 0, iv, 0, 16);
         System.arraycopy(encrypted, 16, keyData, 0, keyData.length);

         Cipher cipher = Cipher.getInstance(MASTER_ALGORITHM);
         GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
         cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);

         return cipher.doFinal(keyData);
      }
      catch(GeneralSecurityException e) {
         throw new RuntimeException(
            "Failed to decrypt secret key. The required algorithms may not be supported by the " +
            "current JVM or installed security packages.", e);
      }
   }

   @Override
   protected SecretKey createMasterKey(char[] password) {
      String passwordStr = new String(password);

      if(passwordStr.equals(masterPassword0)) {
         return masterKey0;
      }

      try {
         byte[] salt = {
            -124, -70, 7, 100, -128, -60, -25, -73, 125, 104, -8, 76, -107, -100, -100, -125
         };

         PBEKeySpec keySpec = new PBEKeySpec(password, salt, 1024, 256);
         SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
         SecretKey key = factory.generateSecret(keySpec);
         masterPassword0 = passwordStr;
         return masterKey0 = new SecretKeySpec(key.getEncoded(), "AES");
      }
      catch(GeneralSecurityException e) {
         throw new RuntimeException(
            "Failed to create the master encryption key. The required algorithms may not be " +
            "supported by the current JVM or installed security packages.", e);
      }
   }

   @Override
   protected SecretKey createSecretKey() {
      KeyGenerator keyGen;

      try {
         keyGen = KeyGenerator.getInstance("AES");
      }
      catch(NoSuchAlgorithmException e) {
         throw new RuntimeException(
            "The AES encryption key algorithm is not supported in this JVM. This may be a " +
            "problem with the Java version or the installed security packages.", e);
      }

      keyGen.init(128, Tool.getSecureRandom());
      return new SecretKeySpec(keyGen.generateKey().getEncoded(), "AES");
   }

   @Override
   protected SecretKey getMasterKey() {
      return masterKey;
   }

   private final SecretKey masterKey;
   private static SecretKey masterKey0;
   private static String masterPassword0 = "";
   private final boolean invalidMasterPassword;
   private static final Logger LOG = LoggerFactory.getLogger(JcePasswordEncryption.class);
   private static final String MASTER_ALGORITHM = "AES/GCM/NoPadding";
   private static final String PASSWORD_ALGORITHM = "AES/CBC/PKCS5Padding"; // NOSONAR
   private static final String JWT_SIGNING_ALGORITHM = "HmacSHA512";
}
