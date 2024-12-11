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

public class LocalTwitterCredential extends AbstractLocalCredential
   implements TwitterCredential
{
   public LocalTwitterCredential() {
      super();
   }

   public String getOauthToken() {
      return oauthToken;
   }

   public void setOauthToken(String oauthToken) {
      this.oauthToken = oauthToken;
   }

   public String getTokenSecret() {
      return tokenSecret;
   }

   public void setTokenSecret(String tokenSecret) {
      this.tokenSecret = tokenSecret;
   }

   public String getConsumerKey() {
      return consumerKey;
   }

   public void setConsumerKey(String consumerKey) {
      this.consumerKey = consumerKey;
   }

   public String getConsumerSecret() {
      return consumerSecret;
   }

   public void setConsumerSecret(String consumerSecret) {
      this.consumerSecret = consumerSecret;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(oauthToken) && StringUtils.isEmpty(tokenSecret)
         && StringUtils.isEmpty(consumerKey) && StringUtils.isEmpty(consumerSecret);
   }

   @Override
   public void reset() {
      super.reset();
      oauthToken = null;
      tokenSecret = null;
      consumerKey = null;
      consumerSecret = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalTwitterCredential)) {
         return false;
      }

      return Tool.equals(((LocalTwitterCredential) obj).oauthToken, oauthToken) &&
         Tool.equals(((LocalTwitterCredential) obj).tokenSecret, tokenSecret) &&
         Tool.equals(((LocalTwitterCredential) obj).consumerKey, consumerKey) &&
         Tool.equals(((LocalTwitterCredential) obj).consumerSecret, consumerSecret);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getOauthToken() != null) {
         writer.format(
            "<oauthToken><![CDATA[%s]]></oauthToken>%n", Tool.encryptPassword(getOauthToken()));
      }

      if(getTokenSecret() != null) {
         writer.format(
            "<tokenSecret><![CDATA[%s]]></tokenSecret>%n", Tool.encryptPassword(getTokenSecret()));
      }

      if(getConsumerKey() != null) {
         writer.format(
            "<consumerKey><![CDATA[%s]]></consumerKey>%n", Tool.encryptPassword(getConsumerKey()));
      }

      if(getConsumerSecret() != null) {
         writer.format(
            "<consumerSecret><![CDATA[%s]]></consumerSecret>%n", Tool.encryptPassword(getConsumerSecret()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element oauthTokenNode = Tool.getChildNodeByTagName(elem, "oauthToken");

      if(oauthTokenNode != null) {
         setOauthToken(getDecryptPassword(oauthTokenNode));
      }

      Element tokenSecretNode = Tool.getChildNodeByTagName(elem, "tokenSecret");

      if(tokenSecretNode != null) {
         setTokenSecret(getDecryptPassword(tokenSecretNode));
      }

      Element consumerKeyNode = Tool.getChildNodeByTagName(elem, "consumerKey");

      if(consumerKeyNode != null) {
         setConsumerKey(getDecryptPassword(consumerKeyNode));
      }

      Element consumerSecretNode = Tool.getChildNodeByTagName(elem, "consumerSecret");

      if(consumerSecretNode != null) {
         setConsumerSecret(getDecryptPassword(consumerSecretNode));
      }
   }

   private String oauthToken;
   // twitter allows token secret to be empty string when signing requests
   private String tokenSecret = "";
   private String consumerKey;
   private String consumerSecret;
}
