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
// only consume secrets, so don't need to add secret to cloud secrets manager.
//      if(!isUseCredential() || getId() == null) {
//         setId(getSecretsManager().encryptCredential(this));
//      }

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
}
