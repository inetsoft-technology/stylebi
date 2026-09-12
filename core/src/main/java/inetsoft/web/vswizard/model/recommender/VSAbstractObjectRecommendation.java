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
package inetsoft.web.vswizard.model.recommender;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public abstract class VSAbstractObjectRecommendation implements VSObjectRecommendation {
   /**
    * Set recommender type of this object.
    */
   @Override
   public void setType(VSRecommendType type) {
      this.type = type;
   }

   /**
    * Get recommender type of this object.
    */
   @Override
   public VSRecommendType getType() {
      return this.type;
   }

   /**
    * Set sub types of the recommender type.
    */
   @Override
   public void setSubTypes(List<VSSubType> subTypes) {
      this.subTypes = subTypes;
   }

   /**
    * Get sub types of recommender type.
    */
   @Override
   public List<VSSubType> getSubTypes() {
      return this.subTypes;
   }

   /**
    * Get selected sub type index of recommender type.
    */
   @Override
   public int getSelectedIndex() {
      return this.selectedIndex;
   }

   /**
    * Set selected sub type index of the recommender type.
    */
   @Override
   public void setSelectedIndex(int index) {
      this.selectedIndex = index;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<vsObjectRecommendation class=\"" + getClass().getName() + "\"");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</vsObjectRecommendation>");
   }

   protected void writeAttributes(PrintWriter writer) {
      if(type != null) {
         writer.print(" type=\"" + type.name() + "\"");
      }

      writer.print(" selectedIndex=\"" + selectedIndex + "\"");
   }

   protected void writeContents(PrintWriter writer) {
      // do nothing
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   protected void parseAttributes(Element elem) {
      String typeVal = Tool.getAttribute(elem, "type");

      if(!Tool.isEmptyString(typeVal)) {
         type = VSRecommendType.valueOf(typeVal);
      }

      String selectedIndexVal = Tool.getAttribute(elem, "selectedIndex");

      if(!Tool.isEmptyString(selectedIndexVal)) {
         selectedIndex = Integer.parseInt(selectedIndexVal);
      }
   }

   protected void parseContents(Element elem) throws Exception {
      // do nothing
   }

   private VSRecommendType type;
   private List<VSSubType> subTypes = new ArrayList<>();
   private int selectedIndex;
}
