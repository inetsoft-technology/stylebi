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
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.web.composer.model.vs.HyperlinkModel;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSGaugeModel extends VSOutputModel<GaugeVSAssembly> {
   public VSGaugeModel(GaugeVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Hyperlink.Ref[] hrefs = info.getHyperlinks();
      this.face = info.getFace();
      this.paddingTop = info.getPadding().top;
      this.paddingLeft = info.getPadding().left;
      this.paddingBottom = info.getPadding().bottom;
      this.paddingRight = info.getPadding().right;

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

   public HyperlinkModel[] getHyperlinks() {
      return hyperlinks;
   }

   public int getFace() {
      return face;
   }

   public int getPaddingTop() {
      return paddingTop;
   }

   public int getPaddingLeft() {
      return paddingLeft;
   }

   public int getPaddingBottom() {
      return paddingBottom;
   }

   public int getPaddingRight() {
      return paddingRight;
   }

   @Component
   public static final class VSGaugeModelFactory
      extends VSObjectModelFactory<GaugeVSAssembly, VSGaugeModel>
   {
      public VSGaugeModelFactory() {
         super(GaugeVSAssembly.class);
      }

      @Override
      public VSGaugeModel createModel(GaugeVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSGaugeModel(assembly, rvs);
      }
   }

   private final HyperlinkModel[] hyperlinks;
   private int face;
   private int paddingTop, paddingLeft, paddingBottom, paddingRight;
}
