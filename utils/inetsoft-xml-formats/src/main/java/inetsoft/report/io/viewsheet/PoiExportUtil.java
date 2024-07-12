/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
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