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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ViewsheetVSAssemblyInfo;
import inetsoft.web.composer.model.vs.HyperlinkModel;
import org.springframework.stereotype.Component;

import java.awt.*;

/**
 * Model that represents a viewsheet assembly.
 *
 * @since 12.3
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VSViewsheetModel extends VSObjectModel<Viewsheet> {
   /**
    * Creates a new instance of <tt>VSViewsheetModel</tt>.
    *
    * @param assembly the viewsheet assembly.
    * @param info     the "fixed" viewsheet assembly info.
    */
   public VSViewsheetModel(Viewsheet assembly, ViewsheetVSAssemblyInfo info, RuntimeViewsheet rvs) {
      super(assembly, info, rvs);
      bounds = assembly.getPreferredBounds(true, false);
      id = assembly.getEntry().toIdentifier();
      this.hyperlinkModel = HyperlinkModel.createHyperlinkModel(new Hyperlink.Ref(id));

      boolean embedded = info.isEmbedded() && assembly.getAbsoluteName().contains(".");
      embeddedIconVisible = isEnabled() && !embedded;
      embeddedOpenIconVisible = (info.getPrimaryCount() < info.getAssemblyCount()) &&
         info.isActionVisible("Open");
      maxMode = assembly.isMaxMode();

      if(info.getDescription() == null) {
         embeddedIconTooltip = assembly.getEntry().getDescription();
      }
      else {
         embeddedIconTooltip = info.getDescription();
      }
   }

   public Rectangle getBounds() {
      return bounds;
   }

   public int getIconHeight() {
      return iconHeight;
   }

   public String getId() {
      return id;
   }

   public boolean isEmbeddedIconVisible() {
      return embeddedIconVisible;
   }

   public boolean isEmbeddedOpenIconVisible() {
      return embeddedOpenIconVisible;
   }

   public String getEmbeddedIconTooltip() {
      return embeddedIconTooltip;
   }

   public HyperlinkModel getHyperlinkModel() {
      return hyperlinkModel;
   }

   public boolean isMaxMode() {
      return maxMode;
   }

   private final Rectangle bounds;
   private final int iconHeight = 14;
   private final String id;
   private final boolean embeddedIconVisible;
   private final boolean embeddedOpenIconVisible;
   private final String embeddedIconTooltip;
   private final HyperlinkModel hyperlinkModel;
   private boolean maxMode = false;

   @Component
   public static final class VSViewsheetModelFactory
      extends VSObjectModelFactory<Viewsheet, VSViewsheetModel>
   {
      public VSViewsheetModelFactory() {
         super(Viewsheet.class);
      }

      @Override
      public VSViewsheetModel createModel(Viewsheet assembly, RuntimeViewsheet rvs) {
         try {
            ViewsheetVSAssemblyInfo info = (ViewsheetVSAssemblyInfo)
               VSEventUtil.getAssemblyInfo(rvs, assembly);

            return new VSViewsheetModel(assembly, info, rvs);
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to create viewsheet model", e);
         }
      }
   }
}
