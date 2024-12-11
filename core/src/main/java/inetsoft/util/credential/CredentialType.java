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

package inetsoft.util.credential;

public enum CredentialType {
   ACCESS_TOKEN("access_token"),
   ACCOUNT_SECRET("account_token"),
   CLIENT_TOKEN("Client_token"),

   API_KEY("apiKey"),
   API_KEY_AUTH_TOKENS("apikey_auth_tokens"),
   API_SECRET("apiSecret"),
   API_TOKEN("apiToken"),
   AUTH_TOKENS("auth_tokens"),
   AUTHORIZATION_CODE("authorization_code"),
   AUTHORIZATION_TOKEN("authorizationToken"),
   CLINET("client_credentials"),
   CLIENT_GRANT("client_credentials_grant"),
   CONSUMER_SECRET("consumerSecret"),
   OAUTH2("oauth2"),
   PASSWORD("password"),
   PASSWORD_APITOKEN("password_and_apitoken"),
   PASSWORD_CLIENT_KEY("password_and_clientkey"),
   PASSWORD_SECURITY_TOKEN("password_and_securitytoken"),
   PASSWORD_OAUTH2("password_and_oauth2"),
   PASSWORD_OAUTH2_WITH_FLAGS("password_and_oauth2_with_flags"),
   ROPC("resource_owner_password"),
   SIGNATURE("signature"),
   SITE_TOKEN("siteToken"),
   TOKEN("token");

   CredentialType(String type) {
      this.type = type;
   }

   public String getName() {
      return type;
   }
   private String type;
}
