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
package inetsoft.util.config;

import inetsoft.util.config.crd.CRDProperty;

import java.io.Serializable;

@InetsoftConfigBean
public class HcpVaultSecretsConfig implements Serializable, Cloneable {
   public String getVaultAddress() {
      return vaultAddress;
   }

   public void setVaultAddress(String vaultAddress) {
      this.vaultAddress = vaultAddress;
   }

   public int getKvSecretEngineVersion() {
      return kvSecretEngineVersion;
   }

   public void setKvSecretEngineVersion(int kvSecretEngineVersion) {
      this.kvSecretEngineVersion = kvSecretEngineVersion;
   }

   public String getKvSecretEnginePath() {
      return kvSecretEnginePath;
   }

   public void setKvSecretEnginePath(String kvSecretEnginePath) {
      this.kvSecretEnginePath = kvSecretEnginePath;
   }

   public boolean isUseDatabaseSecretsEngine() {
      return useDatabaseSecretsEngine;
   }

   public void setUseDatabaseSecretsEngine(boolean useDatabaseSecretsEngine) {
      this.useDatabaseSecretsEngine = useDatabaseSecretsEngine;
   }

   public String getDbSecretEnginePath() {
      return dbSecretEnginePath;
   }

   public void setDbSecretEnginePath(String dbSecretEnginePath) {
      this.dbSecretEnginePath = dbSecretEnginePath;
   }

   public String[] getUseDatabaseSecretsEngineDBTypes() {
      return useDatabaseSecretsEngineDBTypes;
   }

   public void setUseDatabaseSecretsEngineDBTypes(String[] useDatabaseSecretsEngineDBTypes) {
      this.useDatabaseSecretsEngineDBTypes = useDatabaseSecretsEngineDBTypes;
   }

   public boolean isSslVerify() {
      return sslVerify;
   }

   public void setSslVerify(boolean sslVerify) {
      this.sslVerify = sslVerify;
   }

   public HcpAuthMethodConfig getAuthMethodConfig() {
      return authMethodConfig;
   }

   public void setAuthMethodConfig(HcpAuthMethodConfig authMethodConfig) {
      this.authMethodConfig = authMethodConfig;
   }

   public HcpVaultSSLConfig getSslConfig() {
      return sslConfig;
   }

   public void setSslConfig(HcpVaultSSLConfig sslConfig) {
      this.sslConfig = sslConfig;
   }

   @Override
   public Object clone()  {
      HcpVaultSecretsConfig clone = new HcpVaultSecretsConfig();
      clone.setVaultAddress(vaultAddress);
      clone.setKvSecretEngineVersion(kvSecretEngineVersion);
      clone.setKvSecretEnginePath(kvSecretEnginePath);
      clone.setUseDatabaseSecretsEngine(useDatabaseSecretsEngine);
      clone.setDbSecretEnginePath(dbSecretEnginePath);
      clone.setSslVerify(sslVerify);
      clone.setAuthMethodConfig((HcpAuthMethodConfig) authMethodConfig.clone());
      clone.setSslConfig((HcpVaultSSLConfig) sslConfig.clone());

      if(useDatabaseSecretsEngineDBTypes != null) {
         clone.useDatabaseSecretsEngineDBTypes = new String[useDatabaseSecretsEngineDBTypes.length];
         System.arraycopy(useDatabaseSecretsEngineDBTypes, 0,
            clone.useDatabaseSecretsEngineDBTypes, 0, useDatabaseSecretsEngineDBTypes.length);
      }

      return clone;
   }

   @CRDProperty(description = "The vault address")
   private String vaultAddress;
   @CRDProperty(description = "The version of the KV Secret Engine (2 by default)")
   private int kvSecretEngineVersion = 2;
   @CRDProperty(description = "The path to the KV Secret Engine")
   private String kvSecretEnginePath;
   @CRDProperty(description = "Flag to use database secrets engine (false by default)")
   private boolean useDatabaseSecretsEngine = false;
   @CRDProperty(description = "The types of the databases that using vault database secrets engine")
   private String[] useDatabaseSecretsEngineDBTypes;
   @CRDProperty(description = "The path to the database Secret Engine")
   private String dbSecretEnginePath;
   @CRDProperty(description = "Flag to verify SSL certificates (true by default)")
   private boolean sslVerify = true;
   @CRDProperty(description = "The authentication method configuration")
   private HcpAuthMethodConfig authMethodConfig;
   @CRDProperty(description = "The SSL configuration for Vault")
   private HcpVaultSSLConfig sslConfig;
}
