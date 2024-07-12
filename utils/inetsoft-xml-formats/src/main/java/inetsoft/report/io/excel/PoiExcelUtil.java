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
package inetsoft.report.io.excel;

import inetsoft.report.Hyperlink;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.openxmlformats.schemas.drawingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTPicture;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTPictureNonVisual;

/**
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PoiExcelUtil extends ExcelUtil {
   /**
    * Add given hyperlink to the excel image via relationships.
    * @param picture the excel image to add link to
    * @param hyperlink the hyperlink info
    */
   public static void addLinkToImage(XSSFPicture picture, Hyperlink.Ref hyperlink) {
      if((hyperlink != null) && hyperlink.getLinkType() == Hyperlink.WEB_LINK)  {
         String url = ExcelVSUtil.getURL(hyperlink);

         XSSFDrawing drawing = picture.getSheet().createDrawingPatriarch();
         PackageRelationship relationship =
            drawing.getPackagePart()
               .addExternalRelationship(url, PackageRelationshipTypes.HYPERLINK_PART);
         String relationshipId = relationship.getId();

         CTPicture ctPicture = picture.getCTPicture();
         CTPictureNonVisual ctPictureNonVisual = ctPicture.getNvPicPr();

         if(ctPictureNonVisual == null) {
            ctPictureNonVisual = ctPicture.addNewNvPicPr();
         }

         CTNonVisualDrawingProps drawingProps = ctPictureNonVisual.getCNvPr();
         CTHyperlink link = drawingProps.getHlinkClick();

         if(link == null) {
            link = drawingProps.addNewHlinkClick();
         }

         link.setId(relationshipId);
      }
   }
}
