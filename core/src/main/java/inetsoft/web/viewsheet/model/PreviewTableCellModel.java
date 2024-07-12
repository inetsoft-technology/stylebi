/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.web.composer.model.vs.HyperlinkModel;

import javax.annotation.Nullable;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PreviewTableCellModel {
   public PreviewTableCellModel(Object cellLabel, HyperlinkModel[] hyperlinks, VSFormatModel fmt) {
      this.cellLabel = cellLabel;
      this.hyperlinks = hyperlinks;
      this.format = fmt;
   }

   @Nullable
   public Object getCellLabel() {
      return cellLabel;
   }

   @Nullable
   public HyperlinkModel[] getHyperlinks() {
      return hyperlinks;
   }

   @Nullable
   public VSFormatModel getVsFormatModel() {
      return format;
   }

   public PrototypedPreviewTableCellModel createModelPrototype() {
      return new PrototypedPreviewTableCellModel(this);
   }

   public void setModelPrototypeIndex(int index) {
      this.prototypeIndex = index;
      this.format = null;
   }

   public int getProtoIdx() {
      return prototypeIndex;
   }

   /**
    * Concrete prototype implementation with non-prototyped properties omitted.
    */
   public static class PrototypedPreviewTableCellModel {
      public PrototypedPreviewTableCellModel(PreviewTableCellModel prototype) {
         this.vsFormatModel = prototype.getVsFormatModel();
      }

      public VSFormatModel getVsFormatModel() {
         return vsFormatModel;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }
         if(o == null || getClass() != o.getClass()) {
            return false;
         }
         PrototypedPreviewTableCellModel that = (PrototypedPreviewTableCellModel) o;
         return Objects.equals(vsFormatModel, that.vsFormatModel);
      }

      @Override
      public int hashCode() {
         return Objects.hash(vsFormatModel);
      }

      private VSFormatModel vsFormatModel;
   }

   private Object cellLabel;
   private HyperlinkModel[] hyperlinks;
   private VSFormatModel format;
   private int prototypeIndex;
}
