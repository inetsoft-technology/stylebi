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
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VSRecommendationModel implements Serializable, XMLSerializable {
   /**
    * Getter for object recommendation list.
    */
   public List<VSObjectRecommendation> getRecommendationList() {
       return recommendationList;
   }

   /**
    * Find the target type recommendation.
    */
   public VSObjectRecommendation findRecommendation(VSRecommendType type, boolean useDefault) {
      if(type == null || recommendationList == null || recommendationList.size() == 0) {
         return null;
      }

      VSObjectRecommendation recommendation = useDefault ? recommendationList.get(0) : null;

      return this.recommendationList.stream()
         .filter(r -> type.equals(r.getType()))
         .findFirst()
         .orElse(recommendation);
   }

   /**
    * Find the selected (or default) recommendation.
    */
   public VSObjectRecommendation findSelectedRecommendation() {
      return findRecommendation(this.selectedType, true);
   }

   /**
    * Get selected type of recommender type.
    */
   public VSRecommendType getSelectedType() {
      return this.selectedType;
   }

   /**
    * Set selected type of the recommender type.
    */
   public void setSelectedType(VSRecommendType type) {
      this.selectedType = type;
   }

   public void addVSObjectRecommendation(VSObjectRecommendation vsObjectRecommendation) {
      if(vsObjectRecommendation != null) {
         recommendationList.add(vsObjectRecommendation);
      }
   }

   /**
    * Setter for original type before editing in object wizard.
    */
   public void setOriginalType(VSRecommendType originalType) {
       this.originalType = originalType;
   }

   /**
    * Getter for original type before editing in object wizard..
    */
   public VSRecommendType getOriginalType() {
       return originalType;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<vsRecommendationModel class=\"" + getClass().getName() + "\"");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</vsRecommendationModel>");
   }

   protected void writeAttributes(PrintWriter writer) {
      if(selectedType != null) {
         writer.print(" selectedType=\"" + selectedType.name() + "\"");
      }

      if(originalType != null) {
         writer.print(" originalType=\"" + originalType.name() + "\"");
      }
   }

   protected void writeContents(PrintWriter writer) {
      if(recommendationList.isEmpty()) {
         return;
      }

      for(int i = 0; i < recommendationList.size(); i++) {
         VSObjectRecommendation recommendation = recommendationList.get(i);
         recommendation.writeXML(writer);
      }
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   protected void parseAttributes(Element elem) {
      String selectedTypeVal = Tool.getAttribute(elem, "selectedType");

      if(!Tool.isEmptyString(selectedTypeVal)) {
         selectedType = VSRecommendType.valueOf(selectedTypeVal);
      }

      String originalTypeVal = Tool.getAttribute(elem, "originalType");

      if(!Tool.isEmptyString(originalTypeVal)) {
         originalType = VSRecommendType.valueOf(originalTypeVal);
      }
   }

   protected void parseContents(Element elem) throws Exception {
      NodeList list = Tool.getChildNodesByTagName(elem, "vsObjectRecommendation");
      recommendationList = new ArrayList<>();

      for(int i = 0; i < list.getLength(); i++) {
         Element item = (Element) list.item(i);

         if(item != null) {
            recommendationList.add(VSObjectRecommendation.createVSObjectRecommendation(item));
         }
      }
   }

   private List<VSObjectRecommendation> recommendationList = new ArrayList<>();
   private VSRecommendType selectedType;
   private VSRecommendType originalType;
}
