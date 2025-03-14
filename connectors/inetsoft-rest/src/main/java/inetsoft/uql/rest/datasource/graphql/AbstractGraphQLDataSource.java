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
package inetsoft.uql.rest.datasource.graphql;

import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialType;

@View(value = {
   @View1("URL"),
   @View1("authType"),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(
      type = ViewType.PANEL,
      vertical = true,
      colspan = 2,
      elements = {
         @View2(value = "clientId", visibleMethod = "useCredentialForOauth"),
         @View2(value = "clientSecret", visibleMethod = "useCredentialForOauth"),
         @View2(value = "authorizationUri", visibleMethod = "useCredentialForOauth"),
         @View2(value = "tokenUri", visibleMethod = "useCredentialForOauth"),
         @View2(value = "scope", visibleMethod = "useCredentialForOauth"),
         @View2(value = "oauthFlags", visibleMethod = "useCredentialForOauth"),
         @View2(
            type = ViewType.BUTTON,
            text = "Authorize",
            visibleMethod = "isOauth",
            col = 1,
            button = @Button(
               type = ButtonType.OAUTH,
               method = "updateTokens",
               oauth = @Button.OAuth)
         ),
         @View2(type = ViewType.LABEL, text = "em.license.communityAPIKeyRequired", align = ViewAlign.FILL,
            wrap = true, colspan = 2, visibleMethod ="displayAPIKeyTip"),
         @View2(value = "accessToken", visibleMethod = "isOauth"),
         @View2(value = "refreshToken", visibleMethod = "isOauth")
      }
   ),
   @View1(value = "user", visibleMethod = "useCredentialForBasicAuth"),
   @View1(value = "password", visibleMethod = "useCredentialForBasicAuth"),
   @View1(value = "authURL", visibleMethod = "useCredentialForTwoStepAuth"),
   @View1(value = "authenticationHttpParameters", visibleMethod = "isTwoStepAuth",
      verticalAlign = ViewAlign.TOP),
   @View1(value = "authMethod", visibleMethod = "isTwoStepAuth"),
   @View1(value = "contentType", visibleMethod = "isBodyVisible"),
   @View1(value = "body", visibleMethod = "isBodyVisible"),
   @View1(type = ViewType.LABEL, text = "auth.token.example", col = 1, visibleMethod = "isTwoStepAuth"),
   @View1(value = "tokenPattern", visibleMethod = "isTwoStepAuth"),
   @View1(type = ViewType.LABEL, text = "auth.token.help", col = 1, visibleMethod = "isTwoStepAuth"),
   @View1(value = "queryHttpParameters"),
}, vertical = true)
public abstract class AbstractGraphQLDataSource<SELF extends AbstractGraphQLDataSource<SELF>>
   extends AbstractRestDataSource<SELF>
{
   protected AbstractGraphQLDataSource(String type, Class<SELF> selfClass) {
      super(type, selfClass);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD_OAUTH2_WITH_FLAGS;
   }

   public static final String VARIABLE_KEY = "variables";
}
