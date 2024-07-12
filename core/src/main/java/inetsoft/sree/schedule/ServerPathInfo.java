/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.schedule;

import inetsoft.sree.internal.HttpXMLSerializable;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.model.ServerPathInfoModel;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

public class ServerPathInfo implements Cloneable, Serializable, HttpXMLSerializable, Comparable{
   public ServerPathInfo() {
   }

   public ServerPathInfo(String path) {
      this.path = path;
      checkFTP();
   }

   public ServerPathInfo(String path, String username, String password) {
      this.path = path;
      this.username = username;
      this.password = password;
      checkFTP();
   }

   public ServerPathInfo(ServerPathInfoModel model) {
      this.path = model.path();

      if(model.ftp()) {
         this.username = model.username();
         this.password = model.password();
      }

      checkFTP();
   }

   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
      checkFTP();
   }

   public String getUsername() {
      return username;
   }

   public void setUsername(String username) {
      this.username = username;
      checkFTP();
   }

   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
      checkFTP();
   }

   public boolean isFTP() {
      return ftp;
   }

   public boolean isSFTP() {
      return sftp;
   }

   private void checkFTP() {
      if((username != null && !username.isEmpty()) || path.toLowerCase().startsWith("ftp://")) {
         ftp = true;
      }

      if(path.toLowerCase().startsWith("sftp://")) {
         ftp = true;
         sftp = true;
      }
   }


   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<ServerPath ");

      if(getPath() != null) {
         writer.print(" path=\"" + Tool.escape(byteEncode(getPath())) +
                         "\"");
      }

      if(getUsername() != null && !getUsername().isEmpty()) {
         writer.print(" username=\"" + Tool.escape(byteEncode(getUsername())) +
                         "\"");
      }

      if(getPassword() != null && !getPassword().isEmpty()) {
         writer.print(" password=\"" + Tool.encryptPassword(getPassword()) +
                         "\"");
      }

      writer.print("/> ");
   }

   /**
    * Parse the replet action definition from xml.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      path = tag.getAttribute("path");
      path = byteDecode(path);
      username = tag.getAttribute("username");
      username = byteDecode(username);
      password = tag.getAttribute("password");
      password = Tool.decryptPassword(password);
      checkFTP();
   }

   @Override
   public boolean equals(Object val) {
      if(!(val instanceof ServerPathInfo)) {
         return false;
      }

      ServerPathInfo info = (ServerPathInfo) val;
      return Tool.equals(path, info.path) && Tool.equals(username, info.username) &&
         Tool.equals(password, info.password);
   }


   @Override
   public int compareTo(Object val) {
      if(!(val instanceof ServerPathInfo)) {
         return -1;
      }

      ServerPathInfo info = (ServerPathInfo) val;
      return path.compareTo(info.path);
   }

   @Override
   public String byteEncode(String source) {
      return encoding ? Tool.byteEncode2(source) : source;
   }

   @Override
   public String byteDecode(String encString) {
      return encoding ? Tool.byteDecode(encString) : encString;
   }

   @Override
   public boolean isEncoding() {
      return encoding;
   }

   @Override
   public void setEncoding(boolean encoding) {
      this.encoding = encoding;
   }

   private String path;
   private String username;
   private String password;
   private transient boolean ftp = false;
   private transient boolean sftp = false;
   private transient boolean encoding = false;
}
