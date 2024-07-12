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

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import inetsoft.sree.SreeEnv;
import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.fips.*;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.util.Strings;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.rmi.dgc.VMID;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;
import java.util.function.Function;

/**
 * {@code FipsPasswordEncryption} is a FIPS 140-2, Level 1 compliant implementation of
 * {@link PasswordEncryption}.
 *
 * @see <a href="https://drive.google.com/open?id=1dHuFsRw6KgbVs_hggjyNuHp8OssH7MT2DVGCZgUp2MM">Implementation Details</a>
 * @see <a href="https://drive.google.com/open?id=1kR8Aytokyi6vUs0bdLN9gf3UVAxuLoLB">FIPS 140-2</a>
 * @see <a href="https://drive.google.com/open?id=10l6nxipUHiY6kauF2ezR-es8Bdj7DJW2">Bouncy Castle FIPS Module Security Policy</a>
 * @see <a href="https://drive.google.com/open?id=1Sp54eUcHvko9TIlgxgvLIuTtGuziGKpe">Bouncy Castle FIPS Module User Guide</a>
 */
class FipsPasswordEncryption extends LocalPasswordEncryption {

   FipsPasswordEncryption() {
      super(true);
      initializeProvider();
   }

   @Override
   protected boolean isFipsCompliant() {
      return true;
   }

   @Override
   public HashedPassword hash(String password, String algorithm, String salt, boolean appendSalt) {
      byte[] randomSalt = new byte[16];
      random.nextBytes(randomSalt);
      String hash = hashPassword(password.getBytes(StandardCharsets.UTF_16), randomSalt);
      return new HashedPassword(hash, "fips-hmacsha512");
   }

   @Override
   public boolean checkHashedPassword(String hashedPassword, String clearPassword, String algorithm,
                                      String salt, boolean appendSalt,
                                      Function<byte[], String> encoder)
   {
      if(!"fips-hmacsha512".equals(algorithm)) {
         LOG.warn(
            "Password has been hashed with an unapproved algorithm, reset passwords to maintain " +
            "FIPS 140-2, Level 1 compliance.");
         return super.checkHashedPassword(
            hashedPassword, clearPassword, algorithm, salt, appendSalt, encoder);
      }

      try {
         byte[] hashData = Encoder.decodeAscii85(hashedPassword);
         byte[] hashSalt = new byte[16];
         System.arraycopy(hashData, 0, hashSalt, 0, 16);
         String checkHash = hashPassword(clearPassword.getBytes(StandardCharsets.UTF_16), hashSalt);
         return checkHash.equals(hashedPassword);
      }
      // need to catch throwable, the security provider may throw an Error if the password is too
      // short
      catch(Throwable e) {
         LOG.debug("Failed to hash password to check", e);
         return false;
      }
   }

   @Override
   protected void updateMasterPassword(SecretKey oldMasterKey, SecretKey newMasterKey) {
      String encrypted = SreeEnv.getProperty("password.hash.key");

      if(encrypted != null) {
         SecretKey key = decryptJwtSigningKey(encrypted, oldMasterKey);
         encrypted = Base64.getEncoder().encodeToString(encryptJwtSigningKey(key, newMasterKey));
         SreeEnv.setProperty("password.hash.key", encrypted);
      }
   }

   @Override
   protected SecretKey createJwtSigningKey() {
      return createHmacKey();
   }

   private SecretKey createHmacKey() {
      try {
         KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA512", "BCFIPS");
         keyGenerator.init(128, random);
         return keyGenerator.generateKey();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to generate JWT signing key", e);
      }
   }

   @Override
   protected byte[] encryptJwtSigningKey(SecretKey key, SecretKey masterKey) {
      return encryptSecretKey(key, masterKey);
   }

   @Override
   protected SecretKey decryptJwtSigningKey(String encryptedKey, SecretKey masterKey) {
      try {
         SymmetricKey wrapperKey = new SymmetricSecretKey(FipsAES.KW, masterKey.getEncoded());
         FipsAES.KeyWrapOperatorFactory factory = new FipsAES.KeyWrapOperatorFactory();
         FipsKeyUnwrapper<FipsAES.WrapParameters> unwrapper =
            factory.createKeyUnwrapper(wrapperKey, FipsAES.KW);
         byte[] data = Base64.getDecoder().decode(encryptedKey);
         byte[] keyData = unwrapper.unwrap(data, 0, data.length);
         return new SecretKeySpec(keyData, "HmacSHA512");
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to unwrap encryption key", e);
      }
   }

//   @Override
//   public JwtParserBuilder createJwtParser() {
//      return new FipsJwtParser();
//   }
//
//   @Override
//   public JwtBuilder createJwtBuilder() {
//      return new FipsJwtBuilder();
//   }

   @Override
   public JWSVerifier createJwsVerifier(SecretKey signingKey) throws JOSEException {
      MACVerifier verifier = new MACVerifier(signingKey);
      verifier.getJCAContext().setProvider(Security.getProvider("BCFIPS"));
      return verifier;
   }

   @Override
   public JWSSigner createJwsSigner(SecretKey signingKey) throws JOSEException {
      MACSigner signer = new MACSigner(signingKey);
      signer.getJCAContext().setProvider(Security.getProvider("BCFIPS"));
      return signer;
   }

   private SecretKey getPasswordHashKey() {
      try {
         return withLock(() -> {
            SecretKey key;
            String property = SreeEnv.getProperty("password.hash.key");

            if(property == null || property.isEmpty()) {
               key = createHmacKey();
               String encrypted = Base64.getEncoder()
                  .encodeToString(encryptJwtSigningKey(key, masterKey));
               SreeEnv.setProperty("password.hash.key", encrypted);
               SreeEnv.save();
            }
            else {
               key = decryptJwtSigningKey(property, masterKey);
            }

            return key;
         });
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to get password hash key", e);
      }
   }

   @Override
   protected byte[][] encrypt(byte[] input, SecretKey secretKey) throws Exception {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BCFIPS");
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, random);
      byte[] encrypted = cipher.doFinal(input);
      byte[] iv = cipher.getIV();
      return new byte[][] { encrypted, iv };
   }

   @Override
   protected byte[] decrypt(byte[] encrypted, byte[] iv, SecretKey secretKey) throws Exception {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BCFIPS");
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv), random);
      return cipher.doFinal(encrypted);
   }

   @Override
   protected byte[] decryptWithMaster(byte[] encrypted, SecretKey masterKey) {
      try {
         byte[] iv = new byte[16];
         byte[] keyData = new byte[encrypted.length - 16];
         System.arraycopy(encrypted, 0, iv, 0, 16);
         System.arraycopy(encrypted, 16, keyData, 0, keyData.length);

         Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BCFIPS");
         GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
         cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec, random);

         return cipher.doFinal(keyData);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to decrypt data", e);
      }
   }

   @Override
   protected SecretKey createSecretKey() {
      try {
         KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", "BCFIPS");
         keyGenerator.init(128, random);
         return keyGenerator.generateKey();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create secret key", e);
      }
   }

   @Override
   protected byte[] encryptSecretKey(SecretKey secretKey, SecretKey masterKey) {
      try {
         SymmetricKey wrapperKey = new SymmetricSecretKey(FipsAES.KW, masterKey.getEncoded());
         FipsAES.KeyWrapOperatorFactory factory = new FipsAES.KeyWrapOperatorFactory();
         KeyWrapper<FipsAES.WrapParameters> wrapper =
            factory.createKeyWrapper(wrapperKey, FipsAES.KW);
         byte[] keyData = secretKey.getEncoded();
         return wrapper.wrap(keyData, 0, keyData.length);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to wrap encryption key", e);
      }
   }

   @Override
   protected SecretKey decryptSecretKey(String encryptedKey, SecretKey masterKey) {
      try {
         SymmetricKey wrapperKey = new SymmetricSecretKey(FipsAES.KW, masterKey.getEncoded());
         FipsAES.KeyWrapOperatorFactory factory = new FipsAES.KeyWrapOperatorFactory();
         FipsKeyUnwrapper<FipsAES.WrapParameters> unwrapper =
            factory.createKeyUnwrapper(wrapperKey, FipsAES.KW);
         byte[] data = Base64.getDecoder().decode(encryptedKey);
         byte[] keyData = unwrapper.unwrap(data, 0, data.length);
         return new SecretKeySpec(keyData, "AES");
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to unwrap encryption key", e);
      }
   }

   @Override
   protected SecretKey createMasterKey(char[] password) {
      return createMasterKey(password, getMasterSalt());
   }

   @Override
   protected byte[] encryptWithMaster(byte[] input, SecretKey masterKey) {
      try {
         byte[] iv = new byte[16];
         random.nextBytes(iv);
         Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BCFIPS");
         GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
         cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec, random);

         byte[] encrypted = cipher.doFinal(input);
         byte[] output = new byte[encrypted.length + 16];
         System.arraycopy(iv, 0, output, 0, 16);
         System.arraycopy(encrypted, 0, output, 16, encrypted.length);

         return output;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to encrypt value", e);
      }
   }

   @Override
   protected SecretKey getMasterKey() {
      return masterKey;
   }

   private String hashPassword(byte[] password, byte[] salt) {
      try {
         Mac mac = Mac.getInstance("HmacSHA512", "BCFIPS");
         mac.init(getPasswordHashKey());
         byte[] data = new byte[password.length + salt.length];
         System.arraycopy(password, 0, data, 0, password.length);
         System.arraycopy(salt, 0, data, password.length, salt.length);
         byte[] hash = mac.doFinal(data);
         byte[] result = new byte[hash.length + salt.length];
         System.arraycopy(salt, 0, result, 0, salt.length);
         System.arraycopy(hash, 0, result, salt.length, hash.length);
         return Tool.encodeAscii85(result);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to hash password", e);
      }
   }

   private static char[] getMasterPassword() {
      char[] password = masterPassword.get();

      if(password == null) {
         password = new char[] {
            'U', '7', 'k', '3', 'Z', 'o', 'A', 'E', 'T', 'u', '7', 'b', 'x', 'A'
         };
      }

      return password;
   }

   private static byte[] getMasterSalt() {
      byte[] salt;
      String property = System.getenv("INETSOFT_MASTER_SALT");

      if(property == null || property.isEmpty()) {
         salt = new byte[] {
            -124, -70, 7, 100, -128, -60, -25, -73, 125, 104, -8, 76, -107, -100, -100, -125
         };
      }
      else {
         salt = Hex.decode(property);
      }

      return salt;
   }

   private static SecretKey createMasterKey() {
      return createMasterKey(getMasterPassword(), getMasterSalt());
   }

   private static SecretKey createMasterKey(char[] password, byte[] salt) {
      try {
         SecretKeyFactory keyFactory =
            SecretKeyFactory.getInstance("PBKDF2WITHHMACSHA256", "BCFIPS");
         PBEKeySpec pbeKeySpec = new PBEKeySpec(password, salt, 1024, 256);
         return keyFactory.generateSecret(pbeKeySpec);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create master encryption key", e);
      }
   }

   private static SecureRandom createSecureRandom() {
      byte[] personalizationString = Strings.toUTF8ByteArray(new VMID().toString());
      SecureRandom entropySource = new SecureRandom();
      return FipsDRBG.SHA512.fromEntropySource(entropySource, true)
         .setPersonalizationString(personalizationString)
         .build(
            entropySource.generateSeed((256 / (2 * 8))), true,
            Strings.toByteArray(getMasterPassword()));
   }

   private static synchronized void initializeProvider() {
      if(!initialized) {
         initialized = true;

         if(Security.getProvider("BCFIPS") == null) {
            // not installed in the JRE
            System.setProperty("org.bouncycastle.fips.approved_only", "true");
            Security.addProvider(new BouncyCastleFipsProvider());
         }

         random = createSecureRandom();
         masterKey = createMasterKey();
      }
   }

   private static volatile boolean initialized = false;
   private static SecureRandom random;
   private static SecretKey masterKey;
   private static final Logger LOG = LoggerFactory.getLogger(FipsPasswordEncryption.class);

}
