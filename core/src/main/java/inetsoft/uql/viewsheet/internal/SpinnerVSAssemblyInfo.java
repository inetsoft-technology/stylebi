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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.util.css.CSSConstants;

import java.awt.*;

/**
 * SpinnerVSAssemblyInfo stores basic spinner assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SpinnerVSAssemblyInfo extends NumericRangeVSAssemblyInfo {
   /**
    * Constructor.
    */
   public SpinnerVSAssemblyInfo() {
      super();

      setPixelSize(new Dimension(AssetUtil.defw, AssetUtil.defh));
      setIncrementValue("1");
   }

   /**
    * Set the default vsobject format.
    * @param bcolor border color.
    */
   @Override
   protected void setDefaultFormat(boolean border) {
      VSCompositeFormat format = new VSCompositeFormat();
      // avoid text being clipped in default size
      format.getDefaultFormat().setFontValue(getDefaultFont(Font.PLAIN, 12));
      format.getDefaultFormat().setBackgroundValue("0xffffff");
      format.getCSSFormat().setCSSType(getObjCSSType());
      setFormat(format);
      setCSSDefaults();
      seedChromeDefaults(VizContext.of(this));
   }

   /**
    * Seed the modern-gated round corner. This type bypasses the base chrome hook (see
    * VSAssemblyInfo.bypassesBaseChrome()) so it seeds its own — form-input modernization,
    * tracked as its own follow-on project from the card-corner work.
    */
   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      super.seedChromeDefaults(ctx); // no-op: this type bypasses the base hook

      VSCompositeFormat objFormat = getFormat();

      if(objFormat != null) {
         objFormat.getDefaultFormat().setRoundCornerValue(
            ctx.modern ? VSObjectChromeDefaults.cardCornerRadius() : 0);
      }

      if(ctx.modern && getPixelSize().height == AssetUtil.defh) {
         setPixelSize(new Dimension(getPixelSize().width, VSDensityDefaults.controlHeight(ctx)));
      }
      else if(!ctx.modern && VSDensityDefaults.isControlHeight(getPixelSize().height)) {
         setPixelSize(new Dimension(getPixelSize().width, AssetUtil.defh));
      }
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.SPINNER;
   }

   @Override
   protected int getDefaultIncrement() {
      return 1;
   }
}
