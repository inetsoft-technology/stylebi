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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.gui.viewsheet.gauge.VSGauge;
import inetsoft.uql.asset.AbstractSheet;

import java.io.Serializable;

/**
 * Data transfer object that represents the {@link FacePaneModel} for the
 * gauge property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacePaneModel implements Serializable {
   public int getFace() {
      return face;
   }

   public void setFace(int face) {
      this.face = face;
   }

   public String[] getFaceIds() {
      return faceIds;
   }

   public void setFaceIds(String[] faceIds) {
      this.faceIds = faceIds;
   }

   public int getFaceType() {
      return faceType;
   }

   public void setFaceType(int faceType) {
      this.faceType = faceType;
   }

   @Override
   public String toString() {
      return "FacePaneModel{face: " +  face + "}";
   }

   private int face;
   private int faceType = AbstractSheet.GAUGE_ASSET;
   private String[] faceIds = VSGauge.getPrefixIDs();
}
