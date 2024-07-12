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
package inetsoft.util.config;

import java.io.Serializable;

@InetsoftConfigBean
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

      return config;
   }

   private String type = "local";
   private boolean fipsComplianceMode;
   private AwsSecretsConfig awsSecrets;
   private AzureKeyVaultConfig azureKeyVault;
   private GoogleCloudSecretsConfig googleSecrets;
}
