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

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LinePropertyPaneModel implements Serializable {
   public LinePropPaneModel getLinePropPaneModel() {
      if(linePropPaneModel == null) {
         linePropPaneModel = new LinePropPaneModel();
      }

      return linePropPaneModel;
   }

   public void setLinePropPaneModel(
      LinePropPaneModel linePropPaneModel)
   {
      this.linePropPaneModel = linePropPaneModel;
   }

   public int getBegin() {
      return begin;
   }

   public void setBegin(int begin) {
      this.begin = begin;
   }

   public int getEnd() {
      return end;
   }

   public void setEnd(int end) {
      this.end = end;
   }

   @Override
   public String toString() {
      return "LinePropertyPaneModel{" +
         "begin=" + begin +
         ", end=" + end +
         ", linePropPaneModel=" + linePropPaneModel +
         '}';
   }

   private int begin;
   private int end;
   private LinePropPaneModel linePropPaneModel;
}
