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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.ImageVSAssembly;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.web.composer.model.vs.HyperlinkModel;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSImageModel extends VSOutputModel<ImageVSAssembly> {
   public VSImageModel(ImageVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      ImageVSAssemblyInfo assemblyInfo =
         (ImageVSAssemblyInfo) assembly.getVSAssemblyInfo();
      noImageFlag = assemblyInfo.getImage() == null;
      locked = assemblyInfo.getLocked();
      animateGif = assemblyInfo.isAnimateGIF();
      shadow = assemblyInfo.isShadow();
      scaleInfo = ScaleInfoModel.of(assemblyInfo.isMaintainAspectRatio(),
                                    assemblyInfo.isTile(),
                                    assemblyInfo.isScaleImage());

      try {
        alpha = "" + Integer.parseInt(assemblyInfo.getImageAlpha()) / 100.0;
      }
      catch (NumberFormatException ignore) {
      }

      Hyperlink.Ref[] hrefs = assemblyInfo.getHyperlinks();

      if(hrefs == null) {
         this.hyperlinks = new HyperlinkModel[0];
      }
      else {
         this.hyperlinks = new HyperlinkModel[hrefs.length];

         for(int i = 0; i < hyperlinks.length; i++) {
            hyperlinks[i] = HyperlinkModel.createHyperlinkModel(hrefs[i]);
         }
      }
   }

   public boolean getNoImageFlag() {
      return noImageFlag;
   }

   public String getAlpha() {
      return alpha;
   }

   public boolean isLocked() {
      return locked;
   }

   public boolean isAnimateGif() {
      return animateGif;
   }

   public boolean isShadow() {
      return shadow;
   }

   public ScaleInfoModel getScaleInfo() {
      return scaleInfo;
   }

   public HyperlinkModel[] getHyperlinks() {
      return hyperlinks;
   }

   private boolean noImageFlag;
   private String alpha;
   private boolean locked;
   private boolean animateGif;
   private boolean shadow;
   private final HyperlinkModel[] hyperlinks;
   private final ScaleInfoModel scaleInfo;

   @Component
   public static final class VSImageModelFactory
      extends VSObjectModelFactory<ImageVSAssembly, VSImageModel>
   {
      public VSImageModelFactory() {
         super(ImageVSAssembly.class);
      }

      @Override
      public VSImageModel createModel(ImageVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSImageModel(assembly, rvs);
      }
   }
}
