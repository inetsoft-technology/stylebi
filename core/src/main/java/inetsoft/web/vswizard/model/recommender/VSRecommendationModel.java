/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.vswizard.model.recommender;

import java.util.ArrayList;
import java.util.List;

public class VSRecommendationModel {
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

   private List<VSObjectRecommendation> recommendationList = new ArrayList<>();
   private VSRecommendType selectedType;
   private VSRecommendType originalType;
}
