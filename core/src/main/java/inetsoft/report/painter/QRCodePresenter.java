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
package inetsoft.report.painter;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This presenter displays a string in the QR code.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class QRCodePresenter extends Abstract2DCodePresenter {
   /**
    * Create a qrcode object.
    */
   @Override
   protected BitMatrix createMatrix(String str, int width) {
      try {
         return new MultiFormatWriter().encode(str,
            BarcodeFormat.QR_CODE, width, width);
      }
      catch(WriterException ex) {
         LOG.warn("Failed to create QR code matrix with size " +
            width + "x" + width + " for: " + str, ex);
      }

      return null;
   }

   /**
    * Get the display name of this presenter.
    *
    * @return a user-friendly name for this presenter.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("QR Code");
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(QRCodePresenter.class);
}

