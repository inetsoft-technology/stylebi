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

public class LocalOAuth2CredentialsGrant extends LocalClientCredentialsGrant
   implements OAuth2CredentialsGrant
{
   public LocalOAuth2CredentialsGrant() {
      super();
   }

   @Override
   public String getAuthorizationUri() {
      return authorizationUri;
   }

   @Override
   public void setAuthorizationUri(String authorizationUri) {
      this.authorizationUri = authorizationUri;
   }

   @Override
   public String getTokenUri() {
      return tokenUri;
   }

   @Override
   public void setTokenUri(String tokenUri) {
      this.tokenUri = tokenUri;
   }

   @Override
   public String getScope() {
      return scope;
   }

   @Override
   public void setScope(String scope) {
      this.scope = scope;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(scope) &&
         StringUtils.isEmpty(tokenUri) && StringUtils.isEmpty(authorizationUri);
   }

   @Override
   public void reset() {
      super.reset();
      scope = null;
      tokenUri = null;
      authorizationUri = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalOAuth2CredentialsGrant)) {
         return false;
      }

      return Tool.equals(((LocalOAuth2CredentialsGrant) obj).scope, scope) &&
         Tool.equals(((LocalOAuth2CredentialsGrant) obj).tokenUri, tokenUri) &&
         Tool.equals(((LocalOAuth2CredentialsGrant) obj).authorizationUri, authorizationUri);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      super.writeContent(writer);

      if(getScope() != null) {
         writer.format("<scope><![CDATA[%s]]></scope>%n", getScope());
      }

      if(getTokenUri() != null) {
         writer.format(
            "<tokenUri><![CDATA[%s]]></tokenUri>%n", getTokenUri());
      }

      if(getAuthorizationUri() != null) {
         writer.format(
            "<authorizationUri><![CDATA[%s]]></authorizationUri>%n", getAuthorizationUri());
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);

      Element scopeNode = Tool.getChildNodeByTagName(elem, "scope");

      if(scopeNode != null) {
         setScope(Tool.getValue(scopeNode));
      }

      Element tokenUriNode = Tool.getChildNodeByTagName(elem, "tokenUri");

      if(tokenUriNode != null) {
         setTokenUri(Tool.getValue(tokenUriNode));
      }

      Element authorizationUriNode = Tool.getChildNodeByTagName(elem, "authorizationUri");

      if(authorizationUriNode != null) {
         setAuthorizationUri(Tool.getValue(authorizationUriNode));
      }
   }

   private String scope;
   private String tokenUri;
   private String authorizationUri;
}
