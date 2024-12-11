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

import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public class LocalAuthorizationCodeGrant extends LocalClientCredentialsGrant implements AuthorizationCodeGrant {

   @Override
   public String getAuthorizationCode() {
      return authorizationCode;
   }

   @Override
   public void setAuthorizationCode(String authorizationCode) {
      this.authorizationCode = authorizationCode;
   }

   @Override
   public String getAccountDomain() {
      return this.accountDomain;
   }

   @Override
   public void setAccountDomain(String accountDomain) {
      this.accountDomain = accountDomain;
   }


   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(authorizationCode);
   }

   @Override
   public void reset() {
      super.reset();
      authorizationCode = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalAuthorizationCodeGrant)) {
         return false;
      }

      return Tool.equals(((LocalAuthorizationCodeGrant) obj).authorizationCode, authorizationCode);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      super.writeContent(writer);

      if(getAuthorizationCode() != null) {
         writer.format("<authorizationCode><![CDATA[%s]]></authorizationCode>%n", getAuthorizationCode());
      }

      if(getAccountDomain() != null) {
         writer.format("<accountDomain><![CDATA[%s]]></accountDomain>%n", getAccountDomain());
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);

      Element node = Tool.getChildNodeByTagName(elem, "authorizationCode");

      if(node != null) {
         setAuthorizationCode(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "accountDomain");

      if(node != null) {
         setAccountDomain(Tool.getValue(node));
      }
   }

   private String authorizationCode;
   private String accountDomain;
}
