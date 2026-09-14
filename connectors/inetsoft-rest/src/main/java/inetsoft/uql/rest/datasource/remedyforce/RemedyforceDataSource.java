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
package inetsoft.uql.rest.datasource.remedyforce;

import inetsoft.uql.rest.datasource.salesforce.SalesforceDataSource;
import inetsoft.uql.tabular.*;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1("authType"),
   @View1(value = "user", visibleMethod = "useCredentialForPassword"),
   @View1(value = "password", visibleMethod = "useCredentialForPassword"),
   @View1(value = "securityToken", visibleMethod = "useCredentialForPassword"),
   @View1(value = "clientId", visibleMethod = "useCredentialForOauth"),
   @View1(value = "clientSecret", visibleMethod = "useCredentialForOauth"),
   @View1(type = ViewType.LABEL, text = "redirect.uri.description", colspan = 2,
          visibleMethod = "useCredentialForOauth"),
   @View1(
      type = ViewType.BUTTON,
      text = "Authorize",
      visibleMethod = "isOauth",
      button = @Button(
         type = ButtonType.OAUTH, method = "updateTokens", oauth = @Button.OAuth,
         dependsOn = { "clientId", "clientSecret", "credentialId" },
         enabledMethod = "authorizeEnabled"
      )
   ),
   @View1(value = "accessToken", visibleMethod = "isOauth"),
   @View1(value = "refreshToken", visibleMethod = "isOauth"),
   @View1(value = "instanceUrl", visibleMethod = "isOauth"),
})
public class RemedyforceDataSource extends SalesforceDataSource<RemedyforceDataSource> {
   static final String TYPE = "Rest.Remedyforce";

   public RemedyforceDataSource() {
      super(TYPE, RemedyforceDataSource.class);
   }

   @Override
   protected String getUrlSuffix() {
      return "/apexrest/BMCServiceDesk";
   }

   @Override
   protected String getTestSuffix() {
      return null;
   }
}
