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

   private String tenantId;
   private String clientId;
   private String clientSecret;
   private String keyVaultURI;
}
