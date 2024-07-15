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
