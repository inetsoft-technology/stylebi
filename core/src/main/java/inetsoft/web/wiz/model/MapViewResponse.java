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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The saved zoom/pan of a map chart (design descriptor), returned by the map-view endpoints. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapViewResponse {
   public double getZoom() {
      return zoom;
   }

   public void setZoom(double zoom) {
      this.zoom = zoom;
   }

   public int getZoomLevel() {
      return zoomLevel;
   }

   public void setZoomLevel(int zoomLevel) {
      this.zoomLevel = zoomLevel;
   }

   public double getPanX() {
      return panX;
   }

   public void setPanX(double panX) {
      this.panX = panX;
   }

   public double getPanY() {
      return panY;
   }

   public void setPanY(double panY) {
      this.panY = panY;
   }

   public boolean isWebMap() {
      return webMap;
   }

   public void setWebMap(boolean webMap) {
      this.webMap = webMap;
   }

   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   private double zoom;
   private int zoomLevel;
   private double panX;
   private double panY;
   private boolean webMap;
   /** Set only when the runtime was reaped and reopened under a new id. */
   private String runtimeId;
}
