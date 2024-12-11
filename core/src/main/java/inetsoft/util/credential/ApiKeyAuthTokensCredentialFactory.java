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

import com.google.auto.service.AutoService;

@AutoService(CredentialFactory.class)
public class ApiKeyAuthTokensCredentialFactory implements CredentialFactory {
   public ApiKeyAuthTokensCredentialFactory() {
      super();
   }

   @Override
   public CredentialType getType() {
      return CredentialType.API_KEY_AUTH_TOKENS;
   }

   @Override
   public Credential createCredential(boolean forceLocal) {
      return isLocal() || forceLocal ?
         new LocalAdobeAnalyticsCredential() : new CloudAdobeAnalyticsCredential();
   }
}