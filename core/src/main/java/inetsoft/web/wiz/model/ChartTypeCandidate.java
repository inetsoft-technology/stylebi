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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.model;

/**
 * One entry in the feasibility-filtered chart-type menu returned by
 * {@code POST /api/wiz/viewsheet/autoBinding}. Each candidate is a chart type the recommender
 * found feasible for the current fields, with its relative score, so the caller (the LLM) can make
 * the final type choice from a product-accurate menu instead of guessing blind.
 */
public class ChartTypeCandidate {
   public ChartTypeCandidate() {
   }

   public ChartTypeCandidate(String type, double score) {
      this.type = type;
      this.score = score;
   }

   /** The chart-type name (same vocabulary as {@code change_chart_type}, e.g. "bar", "line", "pie"). */
   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   /** Relative fit score in [0,1] (recommender score, normalized). Higher = better fit; for ordering only. */
   public double getScore() {
      return score;
   }

   public void setScore(double score) {
      this.score = score;
   }

   private String type;
   private double score;
}
