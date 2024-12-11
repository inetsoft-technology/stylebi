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
package inetsoft.uql.rest.datasource.twilio;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;
import inetsoft.util.credential.CredentialType;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "user", visibleMethod = "useCredential"),
   @View1(value = "password", visibleMethod = "useCredential")
})
public class TwilioDataSource extends EndpointJsonDataSource<TwilioDataSource> {
   static final String TYPE = "Rest.Twilio";
   
   public TwilioDataSource() {
      super(TYPE, TwilioDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD;
   }

   @Override
   public String getURL() {
      return monitorRequest ? "https://monitor.twilio.com" : "https://api.twilio.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   boolean isMonitorRequest() {
      return monitorRequest;
   }

   void setMonitorRequest(boolean monitorRequest) {
      this.monitorRequest = monitorRequest;
   }

   @Override
   protected String getTestSuffix() {
      return "/2010-04-01/Accounts.json";
   }

   private boolean monitorRequest;
}
