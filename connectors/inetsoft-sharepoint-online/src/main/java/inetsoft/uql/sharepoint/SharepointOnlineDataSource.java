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
package inetsoft.uql.sharepoint;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "user", visibleMethod = "useCredential"),
   @View1(value = "password", visibleMethod = "useCredential"),
   @View1(value = "clientId", visibleMethod = "useCredential"),
   @View1(value = "tenantId", visibleMethod = "useCredential"),
   @View1(value = "clientSecret", visibleMethod = "useCredential")
})
public class SharepointOnlineDataSource extends TabularDataSource<SharepointOnlineDataSource> {
   public static final String TYPE = "SharepointOnline";

   public SharepointOnlineDataSource() {
      super(TYPE, SharepointOnlineDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.ROPC;
   }

   @Property(label = "User", required = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getUser() {
      if(getCredential() instanceof PasswordCredential) {
         return ((PasswordCredential) getCredential()).getUser();
      }

      return null;
   }

   @SuppressWarnings("unused")
   public void setUser(String username) {
      if(getCredential() instanceof PasswordCredential) {
         ((PasswordCredential) getCredential()).setUser(username);
      }
   }

   @Property(label = "Password", password = true, required = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getPassword() {
      if(getCredential() instanceof PasswordCredential) {
         return ((PasswordCredential) getCredential()).getPassword();
      }

      return null;
   }

   @SuppressWarnings("unused")
   public void setPassword(String password) {
      if(getCredential() instanceof PasswordCredential) {
         ((PasswordCredential) getCredential()).setPassword(password);
      }
   }

   @Property(label = "Client ID", required = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getClientId() {
      if(getCredential() instanceof ClientCredentials) {
         return ((ClientCredentials) getCredential()).getClientId();
      }

      return null;
   }

   @SuppressWarnings("unused")
   public void setClientId(String clientId) {
      if(getCredential() instanceof ClientCredentials) {
         ((ClientCredentials) getCredential()).setClientId(clientId);
      }
   }

   @Property(label = "Tenant ID", required = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getTenantId() {
      if(getCredential() instanceof ResourceOwnerPasswordCredentials) {
         return ((ResourceOwnerPasswordCredentials) getCredential()).getTenantId();
      }

      return null;
   }

   @SuppressWarnings("unused")
   public void setTenantId(String tenantId) {
      if(getCredential() instanceof ResourceOwnerPasswordCredentials) {
         ((ResourceOwnerPasswordCredentials) getCredential()).setTenantId(tenantId);
      }
   }

   @Property(label = "Client Secret", password = true, required = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getClientSecret() {
      if(getCredential() instanceof ClientCredentials) {
         return ((ClientCredentials) getCredential()).getClientSecret();
      }

      return null;
   }

   @SuppressWarnings("unused")
   public void setClientSecret(String clientSecret) {
      if(getCredential() instanceof ClientCredentials) {
         ((ClientCredentials) getCredential()).setClientSecret(clientSecret);
      }
   }

   String getAccessToken() {
      if(getCredential() instanceof AccessTokenCredential) {
         return ((AccessTokenCredential) getCredential()).getAccessToken();
      }

      return null;
   }

   void setAccessToken(String accessToken) {
      if(getCredential() instanceof AccessTokenCredential) {
         ((AccessTokenCredential) getCredential()).setAccessToken(accessToken);
      }
   }

   String getRefreshToken() {
      if(getCredential() instanceof RefreshTokenCredential) {
         return ((RefreshTokenCredential) getCredential()).getRefreshToken();
      }

      return null;
   }

   void setRefreshToken(String refreshToken) {
      if(getCredential() instanceof RefreshTokenCredential) {
         ((RefreshTokenCredential) getCredential()).setRefreshToken(refreshToken);
      }
   }

   Instant getTokenExpires() {
      return tokenExpires;
   }

   void setTokenExpires(Instant tokenExpires) {
      this.tokenExpires = tokenExpires;
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(tokenExpires != null) {
         writer.format("<token-expires>%s</token-expires>%n", tokenExpires);
      }
   }

   @Override
   protected void parseContents(Element element) throws Exception {
      super.parseContents(element);
      String expires = Tool.getChildValueByTagName(element, "token-expires");

      if(expires != null) {
         tokenExpires = Instant.parse(expires);
      }
   }

   @Override
   public boolean equals(Object obj) {
      try {
         SharepointOnlineDataSource ds = (SharepointOnlineDataSource) obj;

         return Objects.equals(tokenExpires, ds.tokenExpires) &&
            Objects.equals(getCredential(), ds.getCredential());
      }
      catch(Exception ex) {
         return false;
      }
   }

   private Instant tokenExpires;
}
