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
import inetsoft.util.config.SecretsConfig;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Function;

public abstract class AbstractSecretsManager extends AbstractPasswordEncryption {
   public AbstractSecretsManager(SecretsConfig secretsConfig) {
      this.secretsConfig = secretsConfig;
      localEncryption = secretsConfig.isFipsComplianceMode() ?
         new FipsPasswordEncryption() : new JcePasswordEncryption();
   }

   @Override
   public String encryptPassword(String input) {
      if(input == null || input.isEmpty()) {
         return input;
      }

      if(PasswordEncryption.isForceMaster()) {
         return encryptMasterPassword(input);
      }

      return encryptPassword0(input);
   }

   protected abstract String encryptPassword0(String input);

   /**
    * One other consideration is that when exported, we encrypt the passwords with the master password.
    * Whether we want to do this only for the default encryption or to also do it for external
    * security managers is to be determined.
    *
    * @param input the clear text password to encrypt.
    */
   @Override
   public String encryptMasterPassword(String input) {
      return localEncryption.encryptMasterPassword(input);
   }

   @Override
   public String decryptPassword(String input, String encryptedKey) {
      if(input.startsWith(MASTER_PREFIX)) {
         return decryptMasterPassword(input);
      }

      return decryptPassword(input);
   }

   @Override
   public String decryptMasterPassword(String input) {
      return localEncryption.decryptMasterPassword(input);
   }

   @Override
   public void changeMasterPassword(char[] oldPassword, char[] newPassword) {
      localEncryption.changeMasterPassword(oldPassword, newPassword);
   }

   @Override
   public HashedPassword hash(String password, String algorithm, String salt, boolean appendSalt) {
      return localEncryption.hash(password, algorithm, salt, appendSalt);
   }

   @Override
   public boolean checkHashedPassword(String hashedPassword, String clearPassword, String algorithm,
                                      String salt, boolean appendSalt, Function<byte[], String> encoder)
   {
      return localEncryption.checkHashedPassword(
         hashedPassword, clearPassword, algorithm, salt, appendSalt, encoder);
   }

   @Override
   public SecretKey getJwtSigningKey() throws IOException {
      return localEncryption.getJwtSigningKey();
   }

   @Override
   public JWSVerifier createJwsVerifier(SecretKey signingKey) throws JOSEException {
      return localEncryption.createJwsVerifier(signingKey);
   }

   @Override
   public JWSSigner createJwsSigner(SecretKey signingKey) throws JOSEException {
      return localEncryption.createJwsSigner(signingKey);
   }

   protected String generateSecretName() {
      UUID uuid = UUID.randomUUID();
      return uuid.toString();
   }

   protected SecretsConfig secretsConfig;
   protected PasswordEncryption localEncryption;
}
