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
package inetsoft.report.io.viewsheet;

import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.drawingml.x2006.main.*;

import java.awt.*;

public class PoiExportUtil {

   /**
    * Drow shadow for text.
    */
   public static void drowTextShadow(XmlObject obj) {
      if(obj == null) {
         return;
      }

      if(obj instanceof CTRegularTextRun) {
         CTTextCharacterProperties rPr = ((CTRegularTextRun) obj).getRPr();

         if(rPr == null) {
            rPr = ((CTRegularTextRun) obj).addNewRPr();
         }

         CTEffectList effectLst = rPr.getEffectLst();

         if(effectLst == null) {
            effectLst = rPr.addNewEffectLst();
         }

         CTOuterShadowEffect outerShdw = effectLst.getOuterShdw();

         if(outerShdw == null) {
            outerShdw = effectLst.addNewOuterShdw();
         }

         outerShdw.setDir(2700000);
         outerShdw.setDist(38100);
         outerShdw.setBlurRad(38100);
         CTSRgbColor rgbColor = outerShdw.addNewSrgbClr();
         Color color = new Color(119, 119, 119);
         rgbColor.setVal(new byte[]{(byte) color.getRed(), (byte) color.getGreen(),
            (byte) color.getBlue()});
      }
   }
}