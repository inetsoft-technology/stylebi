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

   public String getAccountKey() {
      return accountKey;
   }

   public void setAccountKey(String accountKey) {
      this.accountKey = accountKey;
   }

   @Override
   public String getSignature() {
      return signature;
   }

   @Override
   public void setSignature(String signature) {
      this.signature = signature;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(accountKey) && StringUtils.isEmpty(signature);
   }

   @Override
   public void reset() {
      super.reset();
      accountKey = null;
      signature = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof LocalSignatureCredential)) {
         return false;
      }

      return Tool.equals(((LocalSignatureCredential) obj).accountKey, accountKey) &&
         Tool.equals(((LocalSignatureCredential) obj).signature, signature);
   }

   @Override
   protected void writeContent(PrintWriter writer) {
      if(getAccountKey() != null) {
         writer.format(
            "<accountKey><![CDATA[%s]]></accountKey>%n", Tool.encryptPassword(getAccountKey()));
      }

      if(getSignature() != null) {
         writer.format(
            "<signature><![CDATA[%s]]></signature>%n", Tool.encryptPassword(getSignature()));
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      super.parseXML(elem);
      Element accountKeyNode = Tool.getChildNodeByTagName(elem, "accountKey");

      if(accountKeyNode != null) {
         setAccountKey(Tool.decryptPassword(Tool.getValue(accountKeyNode)));
      }

      Element signatureNode = Tool.getChildNodeByTagName(elem, "signature");

      if(signatureNode != null) {
         setSignature(Tool.decryptPassword(Tool.getValue(signatureNode)));
      }
   }

   private String accountKey;
   private String signature;
}
