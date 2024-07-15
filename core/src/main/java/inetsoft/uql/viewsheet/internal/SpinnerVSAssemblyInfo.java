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
