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
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;
import inetsoft.util.credential.CredentialType;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "user", visibleMethod = "useCredential"),
   @View1(value = "password", visibleMethod = "useCredential"),
   @View1(value = "securityToken", visibleMethod = "useCredential"),
})
public class RemedyforceDataSource extends SalesforceDataSource<RemedyforceDataSource> {
   static final String TYPE = "Rest.Remedyforce";

   public RemedyforceDataSource() {
      super(TYPE, RemedyforceDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD_SECURITY_TOKEN;
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
