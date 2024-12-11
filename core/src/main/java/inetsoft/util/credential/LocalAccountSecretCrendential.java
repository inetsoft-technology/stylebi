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

public class LocalAccountSecretCrendential extends AbstractLocalCredential
   implements AccountSecretCrendential
{
   public LocalAccountSecretCrendential() {
      super();
   }

   public String getAccountToken() {
      return accountToken;
   }

   public void setAccountToken(String accountToken) {
      this.accountToken = accountToken;
   }

   public String getSecretKey() {
      return secretKey;
   }

   public void setSecretKey(String secretKey) {
      this.secretKey = secretKey;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(accountToken) && StringUtils.isEmpty(secretKey);
   }

   @Override
   public void reset() {
      super.reset();
      accountToken = null;
      secretKey = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalAccountSecretCrendential)) {
         return false;
      }

      return Tool.equals(((LocalAccountSecretCrendential) obj).accountToken, accountToken) &&
         Tool.equals(((LocalAccountSecretCrendential) obj).secretKey, secretKey);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getAccountToken() != null) {
         writer.format(
            "<accountToken><![CDATA[%s]]></accountToken>%n", Tool.encryptPassword(getAccountToken()));
      }

      if(getSecretKey() != null) {
         writer.format(
            "<secretKey><![CDATA[%s]]></secretKey>%n", Tool.encryptPassword(getSecretKey()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element accountTokenNode = Tool.getChildNodeByTagName(elem, "accountToken");

      if(accountTokenNode != null) {
         setAccountToken(Tool.decryptPassword(Tool.getValue(accountTokenNode)));
      }

      Element secretKeyNode = Tool.getChildNodeByTagName(elem, "secretKey");

      if(secretKeyNode != null) {
         setSecretKey(Tool.decryptPassword(Tool.getValue(secretKeyNode)));
      }
   }

   private String accountToken;
   private String secretKey;
}
