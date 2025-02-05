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

public class LocalSignatureCredential extends AbstractLocalCredential implements SignatureCredential {
   public LocalSignatureCredential() {
      super();
   }

   public String getAccountName() {
      return accountName;
   }

   public void setAccountName(String accountName) {
      this.accountName = accountName;
   }

   public String getAccountKey() {
      return accountKey;
   }

   public void setAccountKey(String accountKey) {
      this.accountKey = accountKey;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(accountName) && StringUtils.isEmpty(accountKey);
   }

   @Override
   public void reset() {
      super.reset();
      accountName = null;
      accountKey = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalSignatureCredential)) {
         return false;
      }

      return Tool.equals(((LocalSignatureCredential) obj).accountName, accountName) &&
         Tool.equals(((LocalSignatureCredential) obj).accountKey, accountKey);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getAccountName() != null) {
         writer.format(
            "<accountName><![CDATA[%s]]></accountName>%n", Tool.encryptPassword(getAccountName()));
      }

      if(getAccountKey() != null) {
         writer.format(
            "<accountKey><![CDATA[%s]]></accountKey>%n", Tool.encryptPassword(getAccountKey()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element accountNameNode = Tool.getChildNodeByTagName(elem, "accountName");

      if(accountNameNode != null) {
         setAccountName(Tool.decryptPassword(Tool.getValue(accountNameNode)));
      }

      Element accountKeyNode = Tool.getChildNodeByTagName(elem, "accountKey");

      if(accountKeyNode != null) {
         setAccountKey(Tool.decryptPassword(Tool.getValue(accountKeyNode)));
      }
   }

   private String accountName;
   private String accountKey;
}
