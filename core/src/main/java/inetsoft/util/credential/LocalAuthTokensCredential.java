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

public class LocalAuthTokensCredential extends AbstractLocalCredential
   implements AuthTokensCredential
{
   public LocalAuthTokensCredential() {
      super();
   }

   @Override
   public String getAccessToken() {
      return accessToken;
   }

   @Override
   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @Override
   public String getRefreshToken() {
      return refreshToken;
   }

   @Override
   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }


   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(refreshToken) && StringUtils.isEmpty(accessToken);
   }

   @Override
   public void reset() {
      super.reset();
      accessToken = null;
      refreshToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalAuthTokensCredential)) {
         return false;
      }

      return Tool.equals(((LocalAuthTokensCredential) obj).accessToken, accessToken) &&
         Tool.equals(((LocalAuthTokensCredential) obj).refreshToken, refreshToken);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getAccessToken() != null) {
         writer.format("<accessToken><![CDATA[%s]]></accessToken>%n", Tool.encryptPassword(getAccessToken()));
      }

      if(getRefreshToken() != null) {
         writer.format("<refreshToken><![CDATA[%s]]></refreshToken>%n", Tool.encryptPassword(getRefreshToken()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element accessTokenNode = Tool.getChildNodeByTagName(elem, "accessToken");

      if(accessTokenNode != null) {
         setAccessToken(Tool.decryptPassword(Tool.getValue(accessTokenNode)));
      }

      Element refreshTokenNode = Tool.getChildNodeByTagName(elem, "refreshToken");

      if(refreshTokenNode != null) {
         setRefreshToken(Tool.decryptPassword(Tool.getValue(refreshTokenNode)));
      }
   }

   private String accessToken;
   private String refreshToken;
}
