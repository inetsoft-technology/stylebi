/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.model;

import java.util.List;

/**
 * Response body for {@code POST /api/wiz/viewsheet/autoBinding}.
 */
public class AutoBindingResponse {
   public List<RecommendedVisualization> getRecommendations() {
      return recommendations;
   }

   public void setRecommendations(List<RecommendedVisualization> recommendations) {
      this.recommendations = recommendations;
   }

   public RecommendedVisualization getPrimary() {
      return primary;
   }

   public void setPrimary(RecommendedVisualization primary) {
      this.primary = primary;
   }

   /** All candidate visualizations, charts ordered by vsWizard score followed by crosstab/table/output. */
   private List<RecommendedVisualization> recommendations;
   /** Best-fit visualization built from user preferences; null when no preference can be determined. */
   private RecommendedVisualization primary;
}
