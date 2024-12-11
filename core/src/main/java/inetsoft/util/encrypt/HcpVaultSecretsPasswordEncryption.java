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

package inetsoft.util.encrypt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.util.*;
import inetsoft.util.config.*;
import io.github.jopenlibs.vault.*;
import io.github.jopenlibs.vault.response.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HcpVaultSecretsPasswordEncryption extends AbstractSecretsManager {
   public HcpVaultSecretsPasswordEncryption(SecretsConfig secretsConfig) {
      super(secretsConfig);

      initVaultClient();
   }

   @Override
   protected boolean isFipsCompliant() {
      return secretsConfig.isFipsComplianceMode();
   }

   @Override
   protected String encryptPassword0(String input, String id) {
      if(input == null || input.isEmpty()) {
         return input;
      }

      if(PasswordEncryption.isForceMaster()) {
         return localEncryption.encryptMasterPassword(input);
      }

      return localEncryption.encryptPassword(input);
   }

   @Override
   public String decryptPassword(String input) {
      if(Tool.isEmptyString(input)) {
         return input;
      }
      else if(input.startsWith(MASTER_PREFIX) || input.startsWith(NEW_PREFIX)) {
         return localEncryption.decryptPassword(input);
      }

      setupOrRefreshVaultToken();
      String secretPath = getKVSecretsEnginePath() + input;

      try {
         LogicalResponse response = vault.logical().read(secretPath);
         ObjectMapper objectMapper = new ObjectMapper();
         return objectMapper.writeValueAsString(response.getData());
      }
      catch(Exception e) {
         LOG.error(Catalog.getCatalog().getString(
            "Failed to get secret value with secret name {0} from vault secrets engine", secretPath), e);
         return null;
      }
   }

   public String decryptDBPassword(String input, String dbType) {
      if(Tool.isEmptyString(input)) {
         return input;
      }
      else if(input.startsWith(MASTER_PREFIX) || input.startsWith(NEW_PREFIX)) {
         return localEncryption.decryptPassword(input);
      }

      setupOrRefreshVaultToken();
      List<String> dbTypes = Arrays.stream(getUseDatabaseSecretsEngineDBTypes())
         .map(String::toLowerCase)
         .toList();

      try {
         if(isUseDatabaseSecretsEngine() && dbTypes.contains(dbType.toLowerCase())) {
            DatabaseResponse response = vault.database(getDbSecretEnginePath()).creds(input);
            Map<String, String> data = response.getData();

            if(data.containsKey("username")) {
               data.put("user", data.remove("username"));
            }

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(data);
         }
         else {
            String secretPath = getKVSecretsEnginePath() + input;
            LogicalResponse response = vault.logical().read(secretPath);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(response.getData());
         }
      }
      catch(Exception e) {
         LOG.error(Catalog.getCatalog().getString(
            "Failed to get secret value with secret name {0} from vault secrets engine", input), e);
         return null;
      }
   }

   private void initVaultClient() {
      try {
         setupVaultConfig();
         setupOrRefreshVaultToken();
         vault = Vault.create(getVaultConfig());
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to initialize hcp vault client!", e);
      }
   }

   private void setupVaultConfig() {
      HcpVaultSecretsConfig config = getVaultConfiguration();

      vaultConfig = new VaultConfig()
         .address(config.getVaultAddress())
         .engineVersion(config.getKvSecretEngineVersion());

      if(config.isSslVerify()) {
         try {
            vaultConfig.sslConfig(getSslConfig());
         }
         catch(Exception e) {
            LOG.error("Failed to setup SSL config", e);
         }
      }
      else {
         vaultConfig.sslConfig(new SslConfig().verify(false));
      }
   }

   private void setupOrRefreshVaultToken() {
      VaultConfig config = getVaultConfig();

      if(config == null) {
         return;
      }

      if(vault == null || !verifyTokenValidity()) {
         try {
            config.token(getVaultToken());
         }
         catch(Exception e) {
            LOG.error("Failed to setup vault token!", e);
         }
      }
   }

   private boolean verifyTokenValidity() {
      try {
         LookupResponse response = vault.auth().lookupSelf();
         return response.getRestResponse().getStatus() == 200;
      }
      catch(Exception e) {
         return false;
      }
   }

   private String getVaultToken() throws Exception {
      HcpAuthMethodConfig authMethodConfig = getVaultConfiguration().getAuthMethodConfig();
      HcpAuthMethodType authType = HcpAuthMethodType.fromValue(authMethodConfig.getType());
      String token = null;

      switch(authType) {
         case TOKEN:
            byte[] tokenBytes = Base64.getDecoder().decode(authMethodConfig.getToken());
            token = new String(tokenBytes, StandardCharsets.UTF_8);
            break;
         case APPROLE:
            AuthResponse approleAuth = Vault.create(getVaultConfig()).auth()
               .loginByAppRole(authMethodConfig.getAppRoleId(), authMethodConfig.getAppRoleSecretId());
            token = approleAuth.getAuthClientToken();
            break;
         case USERPASS:
            byte[] passwordBytes = Base64.getDecoder().decode(authMethodConfig.getPassword());
            String password = new String(passwordBytes, StandardCharsets.UTF_8);
            AuthResponse userPassAuth = Vault.create(getVaultConfig()).auth()
               .loginByUserPass(authMethodConfig.getUsername(), password);
            token = userPassAuth.getAuthClientToken();
            break;
         default:
            throw new IllegalArgumentException("Unsupported authentication type");
      }

      return token;
   }

   private SslConfig getSslConfig() throws Exception {
      HcpVaultSecretsConfig config = getVaultConfiguration();

      if(!config.isSslVerify() || config.getSslConfig() == null) {
         return null;
      }

      HcpVaultSSLConfig vaultSSLConfig = config.getSslConfig();
      SslConfig sslConfig = new SslConfig();

      if(HcpVaultSSLConfig.SSLType.JKS.getValue().equals(vaultSSLConfig.getSslType())) {
         if(!Tool.isEmptyString(vaultSSLConfig.getTrustStoreFile())) {
            sslConfig.trustStoreFile(new File(vaultSSLConfig.getTrustStoreFile()));
         }

         String password = vaultSSLConfig.getKeystorePassword();

         if(!Tool.isEmptyString(password)) {
            password = new String(Base64.getDecoder().decode(password), StandardCharsets.UTF_8);
         }

         if(!Tool.isEmptyString(vaultSSLConfig.getKeyStoreFile())) {
            sslConfig.keyStoreFile(new File(vaultSSLConfig.getKeyStoreFile()), password);
         }
      }
      else if(HcpVaultSSLConfig.SSLType.PEM.getValue().equals(vaultSSLConfig.getSslType())) {
         if(!Tool.isEmptyString(vaultSSLConfig.getPemFile())) {
            sslConfig.pemFile(new File(vaultSSLConfig.getPemFile()));
         }

         if(!Tool.isEmptyString(vaultSSLConfig.getClientPemFile())) {
            sslConfig.clientPemFile(new File(vaultSSLConfig.getClientPemFile()));
         }

         if(!Tool.isEmptyString(vaultSSLConfig.getClientKeyPemFile())) {
            sslConfig.clientKeyPemFile(new File(vaultSSLConfig.getClientKeyPemFile()));
         }

         if(!Tool.isEmptyString(vaultSSLConfig.getPemUTF8())) {
            byte[] pemBytes = Base64.getDecoder().decode(vaultSSLConfig.getPemUTF8());
            sslConfig.pemUTF8(new String(pemBytes, StandardCharsets.UTF_8));
         }

         if(!Tool.isEmptyString(vaultSSLConfig.getClientPemUTF8())) {
            byte[] clientPemBytes = Base64.getDecoder().decode(vaultSSLConfig.getClientPemUTF8());
            sslConfig.clientPemUTF8(new String(clientPemBytes, StandardCharsets.UTF_8));
         }

         if(!Tool.isEmptyString(vaultSSLConfig.getClientKeyPemUTF8())) {
            byte[] clientKeyPemBytes = Base64.getDecoder().decode(vaultSSLConfig.getClientKeyPemUTF8());
            sslConfig.clientKeyPemUTF8(new String(clientKeyPemBytes, StandardCharsets.UTF_8));
         }
      }

      return sslConfig;
   }

   private String getKVSecretsEnginePath() {
      String enginePath = getVaultConfiguration().getKvSecretEnginePath();
      return Tool.isEmptyString(enginePath) ? enginePath : enginePath + "/";
   }

   private String getDbSecretEnginePath() {
      return getVaultConfiguration().getDbSecretEnginePath();
   }

   private boolean isUseDatabaseSecretsEngine() {
      return getVaultConfiguration().isUseDatabaseSecretsEngine();
   }

   private String[] getUseDatabaseSecretsEngineDBTypes() {
      String[] dbTypes = getVaultConfiguration().getUseDatabaseSecretsEngineDBTypes();
      return dbTypes != null ? dbTypes : new String[0];
   }

   private HcpVaultSecretsConfig getVaultConfiguration() {
      return secretsConfig.getHcpVaultSecrets();
   }

   private VaultConfig getVaultConfig() {
      return vaultConfig;
   }

   private VaultConfig vaultConfig;
   private Vault vault;
   private static final Logger LOG = LoggerFactory.getLogger(HcpVaultSecretsPasswordEncryption.class);
}
