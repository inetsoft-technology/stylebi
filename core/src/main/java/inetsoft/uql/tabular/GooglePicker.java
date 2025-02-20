/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.tabular;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Objects;

public class GooglePicker implements Serializable, XMLSerializable, Cloneable {
   public GooglePicker() {
   }

   public GooglePicker(GoogleFile selectedFile) {
      this.selectedFile = selectedFile;
   }

   public String getOauthToken() {
      return oauthToken;
   }

   public void setOauthToken(String oauthToken) {
      this.oauthToken = oauthToken;
   }

   public GoogleFile getSelectedFile() {
      return selectedFile;
   }

   public void setSelectedFile(GoogleFile selectedFile) {
      this.selectedFile = selectedFile;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.format("<googlePicker>");

      if(selectedFile != null) {
         selectedFile.writeXML(writer);
      }

      writer.format("</googlePicker>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      Element element = Tool.getChildNodeByTagName(tag, "googleFile");

      if(element != null) {
         selectedFile = new GoogleFile();
         selectedFile.parseXML(element);
      }
   }

   @Override
   public Object clone() {
      try {
         GooglePicker picker = (GooglePicker) super.clone();
         picker.selectedFile = (GoogleFile) selectedFile.clone();
         return picker;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      GooglePicker that = (GooglePicker) o;
      return Objects.equals(oauthToken, that.oauthToken) && Objects.equals(selectedFile, that.selectedFile);
   }

   @Override
   public int hashCode() {
      return Objects.hash(oauthToken, selectedFile);
   }

   private String oauthToken;
   private GoogleFile selectedFile;
   private static final Logger LOG = LoggerFactory.getLogger(GooglePicker.class);
}
