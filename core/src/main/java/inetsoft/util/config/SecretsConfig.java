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

import inetsoft.util.config.crd.*;

import java.io.Serializable;

@InetsoftConfigBean
@CRDResources({
   @CRDResource(
      name = "local", type = "LocalSecrets",
      properties = {
         @CRDProperty(name = "masterPassword", type = String.class, description = "The master password used to generate a PBKDF2 cipher for encrypting secrets", secret = true),
         @CRDProperty(name = "masterSalt", type = String.class, description = "The salt to use when encrypting secrets in FIPS mode", secret = true)
      }
   )
})
public class SecretsConfig implements Serializable, Cloneable {
   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public boolean isFipsComplianceMode() {
      return fipsComplianceMode;
   }

   public void setFipsComplianceMode(boolean fipsComplianceMode) {
      this.fipsComplianceMode = fipsComplianceMode;
   }

   public AwsSecretsConfig getAwsSecrets() {
      return awsSecrets;
   }

   public void setAwsSecrets(AwsSecretsConfig awsSecrets) {
      this.awsSecrets = awsSecrets;
   }

   public AzureKeyVaultConfig getAzureKeyVault() {
      return azureKeyVault;
   }

   public void setAzureKeyVault(AzureKeyVaultConfig azureKeyVault) {
      this.azureKeyVault = azureKeyVault;
   }

   public GoogleCloudSecretsConfig getGoogleSecrets() {
      return googleSecrets;
   }

   public void setGoogleSecrets(GoogleCloudSecretsConfig googleSecrets) {
      this.googleSecrets = googleSecrets;
   }

   public HcpVaultSecretsConfig getHcpVaultSecrets() {
      return hcpVaultSecrets;
   }

   public void setHcpVaultSecrets(HcpVaultSecretsConfig hcpVaultSecrets) {
      this.hcpVaultSecrets = hcpVaultSecrets;
   }

   @Override
   public Object clone() {
      SecretsConfig config = new SecretsConfig();
      config.setType(type);
      config.setFipsComplianceMode(fipsComplianceMode);

      if(awsSecrets != null) {
         config.setAwsSecrets((AwsSecretsConfig) awsSecrets.clone());
      }

      if(azureKeyVault != null) {
         config.setAzureKeyVault((AzureKeyVaultConfig) azureKeyVault.clone());
      }

      if(googleSecrets != null) {
         config.setGoogleSecrets((GoogleCloudSecretsConfig) googleSecrets.clone());
      }

      if(hcpVaultSecrets != null) {
         config.setHcpVaultSecrets((HcpVaultSecretsConfig) hcpVaultSecrets.clone());
      }

      return config;
   }

   @CRDProperty(description = "The type of secrets manager", allowedValues = { "local", "aws", "azure", "google", "hcp" })
   private String type = SecretsType.LOCAL.getName();
   @CRDProperty(description = "A flag that indicates if FIPS compliance mode is activated")
   private boolean fipsComplianceMode;
   @CRDProperty(name = "awsSecretsManager", description = "The AWS Secrets Manager configuration")
   private AwsSecretsConfig awsSecrets;
   @CRDProperty(description = "The Azure Key Vault configuration")
   private AzureKeyVaultConfig azureKeyVault;
   @CRDProperty(name = "googleCloudSecrets", description = "The Google Cloud Secrets configuration")
   private GoogleCloudSecretsConfig googleSecrets;
   @CRDProperty(name = "hcpVaultSecrets", description = "The HCP Vault Secrets configuration")
   private HcpVaultSecretsConfig hcpVaultSecrets;
}
