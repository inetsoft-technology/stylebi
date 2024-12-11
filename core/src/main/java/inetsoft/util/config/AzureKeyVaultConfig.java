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

@InetsoftConfigBean
public class AzureKeyVaultConfig {

   public String getTenantId() {
      return tenantId;
   }

   public void setTenantId(String tenantId) {
      this.tenantId = tenantId;
   }

   public String getClientId() {
      return clientId;
   }

   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   public String getClientSecret() {
      return clientSecret;
   }

   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   public String getKeyVaultURI() {
      return keyVaultURI;
   }

   public void setKeyVaultURI(String keyVaultURI) {
      this.keyVaultURI = keyVaultURI;
   }

   @Override
   public Object clone() {
      AzureKeyVaultConfig config = new AzureKeyVaultConfig();
      config.setTenantId(tenantId);
      config.setClientId(clientId);
      config.setClientSecret(clientSecret);
      config.setKeyVaultURI(keyVaultURI);

      return config;
   }

   @CRDProperty(description = "The tenant ID", secret = true)
   private String tenantId;
   @CRDProperty(description = "The client ID", secret = true)
   private String clientId;
   @CRDProperty(description = "The client secret", secret = true)
   private String clientSecret;
   @CRDProperty(name = "keyVaultUri", description = "The key value URI")
   private String keyVaultURI;
}
