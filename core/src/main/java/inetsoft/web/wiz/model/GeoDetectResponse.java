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
 * Response for {@code POST /api/wiz/viewsheet/geo/detect}.
 */
public class GeoDetectResponse {
   public String getGeoType() {
      return geoType;
   }

   public void setGeoType(String geoType) {
      this.geoType = geoType;
   }

   public int getLayer() {
      return layer;
   }

   public void setLayer(int layer) {
      this.layer = layer;
   }

   public int getMatchedCount() {
      return matchedCount;
   }

   public void setMatchedCount(int matchedCount) {
      this.matchedCount = matchedCount;
   }

   public List<String> getUnmatched() {
      return unmatched;
   }

   public void setUnmatched(List<String> unmatched) {
      this.unmatched = unmatched;
   }

   public String getLayerName() {
      return layerName;
   }

   public void setLayerName(String layerName) {
      this.layerName = layerName;
   }

   public List<String> getCandidateFeatures() {
      return candidateFeatures;
   }

   public void setCandidateFeatures(List<String> candidateFeatures) {
      this.candidateFeatures = candidateFeatures;
   }

   /**
    * The live runtimeId, echoed only when a reaped runtime was transparently restored during detect
    * (its id changed). Null on the normal path; the client keeps its existing id when this is absent.
    */
   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   private String geoType;
   private int layer;
   private String layerName;
   private int matchedCount;
   private List<String> unmatched;
   private List<String> candidateFeatures;
   private String runtimeId;
}
