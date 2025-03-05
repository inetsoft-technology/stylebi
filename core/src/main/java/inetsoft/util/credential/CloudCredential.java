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

import inetsoft.util.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public interface CloudCredential extends Credential {
   default void writeXML(PrintWriter writer) {
      if(isEmpty()) {
         return;
      }

      if(PasswordEncryption.isForceLocal()) {
         Credential newCredential = Tool.decryptPasswordToCredential(
            getId(), getClass(), getDBType());

         if(newCredential != null) {
            newCredential.setId(getId());
            refreshCredential(newCredential);
         }

         Credential localCredential = convertToLocal();

         if(localCredential != null) {
            localCredential.writeXML(writer);
            return;
         }
      }

      StringBuilder builder = new StringBuilder();
      builder.append("<PasswordCredential cloud=\"true\"");
      builder.append(" class=\"");
      builder.append(this.getClass().getName());
      builder.append("\" id=\"");
      builder.append(getId());
      builder.append("\" dbType=\"");
      builder.append(getDBType() != null && !getDBType().isEmpty() ? getDBType() : "");
      builder.append("\">");
      builder.append("</PasswordCredential>");
      writer.write(builder.toString());
   }

   default void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      setId(elem.getAttribute("id"));
      setDBType(elem.getAttribute("dbType"));
      fetchCredential();
   }

   default void fetchCredential() {
      if(Tool.isEmptyString(getId())) {
         return;
      }

      Credential credential = getSecretsManager().getCredential(this);

      if(credential != null) {
         refreshCredential(credential);
      }
   }

   void refreshCredential(Credential credential);

   default AbstractSecretsManager getSecretsManager() {
      PasswordEncryption encryption = PasswordEncryption.newInstance();

      if(encryption instanceof AbstractSecretsManager) {
         return (AbstractSecretsManager) encryption;
      }

      throw new RuntimeException("There's no secrets manager for cloud password credential!");
   }

   default Credential createLocal() {
      return null;
   }

   default void copyToLocal(Credential credential) {
   }

   default Credential convertToLocal() {
      Credential local = createLocal();

      if(local != null) {
         copyToLocal(local);
      }

      return local;
   }
}
