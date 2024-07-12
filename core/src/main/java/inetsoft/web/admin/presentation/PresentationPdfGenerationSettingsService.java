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
package inetsoft.web.admin.presentation;

import inetsoft.sree.SreeEnv;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.presentation.model.PresentationPdfGenerationSettingsModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

@Service
public class PresentationPdfGenerationSettingsService {
   public PresentationPdfGenerationSettingsModel getModel(boolean globalProperty) {
      String openFirst = null;

      if("true".equals(SreeEnv.getProperty("pdf.open.bookmark", false, !globalProperty))) {
         openFirst = "bookmark";
      }
      else if("true".equals(SreeEnv.getProperty("pdf.open.thumbnail", false, !globalProperty))) {
         openFirst = "thumbnail";
      }

      return PresentationPdfGenerationSettingsModel.builder()
         .compressText("true".equals(SreeEnv.getProperty("pdf.compress.text", false, !globalProperty)))
         .compressImage("true".equals(SreeEnv.getProperty("pdf.compress.image", false, !globalProperty)))
         .asciiOnly("true".equals(SreeEnv.getProperty("pdf.output.ascii", false, !globalProperty)))
         .mapSymbols("true".equals(SreeEnv.getProperty("pdf.map.symbols", false, !globalProperty)))
         .pdfEmbedCmap("true".equals(SreeEnv.getProperty("pdf.embed.cmap", false, !globalProperty)))
         .pdfEmbedFont("true".equals(SreeEnv.getProperty("pdf.embed.font", false, !globalProperty)))
         .cidFontPath(SreeEnv.getProperty("font.truetype.path", false, !globalProperty))
         .afmFontPath(SreeEnv.getProperty("font.afm.path", false, !globalProperty))
         .openFirst(openFirst)
         .browserEmbedPdf("embed".equals(SreeEnv.getProperty("pdf.output.attachment", false, !globalProperty)))
         .pdfHyperlinks("true".equals(SreeEnv.getProperty("pdf.generate.links", false, !globalProperty)))
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-PDF Generation",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PresentationPdfGenerationSettingsModel model, boolean globalSettings) throws Exception {
      SreeEnv.setProperty("pdf.compress.text", model.compressText() + "", !globalSettings);
      SreeEnv.setProperty("pdf.compress.image", model.compressImage() + "", !globalSettings);
      SreeEnv.setProperty("pdf.output.ascii", model.asciiOnly() + "", !globalSettings);
      SreeEnv.setProperty("pdf.map.symbols", model.mapSymbols() + "", !globalSettings);
      SreeEnv.setProperty("pdf.embed.cmap", model.pdfEmbedCmap() + "", !globalSettings);
      SreeEnv.setProperty("pdf.embed.font", model.pdfEmbedFont() + "", !globalSettings);
      SreeEnv.setProperty("font.truetype.path", model.cidFontPath().trim(), !globalSettings);
      SreeEnv.setProperty("font.afm.path", model.afmFontPath().trim(), !globalSettings);
      SreeEnv.setProperty("pdf.open.bookmark", "bookmark".equals(model.openFirst()) + "", !globalSettings);
      SreeEnv.setProperty("pdf.open.thumbnail", "thumbnail".equals(model.openFirst()) + "", !globalSettings);
      SreeEnv.setProperty("pdf.output.attachment", model.browserEmbedPdf() ? "embed" : "true", !globalSettings);
      SreeEnv.setProperty("pdf.generate.links", model.pdfHyperlinks() + "", !globalSettings);
      SreeEnv.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-PDF Generation",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean globalSettings) throws Exception {
      SreeEnv.resetProperty("pdf.compress.text",  !globalSettings);
      SreeEnv.resetProperty("pdf.compress.image",  !globalSettings);
      SreeEnv.resetProperty("pdf.output.ascii",  !globalSettings);
      SreeEnv.resetProperty("pdf.map.symbols",  !globalSettings);
      SreeEnv.resetProperty("pdf.embed.cmap",  !globalSettings);
      SreeEnv.resetProperty("pdf.embed.font",  !globalSettings);
      SreeEnv.resetProperty("font.truetype.path",  !globalSettings);
      SreeEnv.resetProperty("font.afm.path",  !globalSettings);
      SreeEnv.resetProperty("pdf.open.bookmark",  !globalSettings);
      SreeEnv.resetProperty("pdf.open.thumbnail",  !globalSettings);
      SreeEnv.resetProperty("pdf.output.attachment",  !globalSettings);
      SreeEnv.resetProperty("pdf.generate.links",  !globalSettings);

      SreeEnv.save();
   }
}
