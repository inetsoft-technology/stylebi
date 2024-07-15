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

import inetsoft.util.Catalog;
import net.sourceforge.barbecue.*;
import net.sourceforge.barbecue.linear.code39.Code39Barcode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This presenter displays a string in the Code 39 symbology.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class BarcodeCode39Presenter extends AbstractBarcodePresenter {
   /**
    * Create a barcode object.
    */
   @Override
   protected Barcode createBarcode(String str) {
      try {
         return BarcodeFactory.createCode39(str, false);
      } 
      catch(BarcodeException ex) {
         try {
            Barcode barcode = new Code39Barcode(str, false, true);

            //Bug #24413 by @yiyangliang,
            //when barcode presenter is applied to report sheet metadata that contains
            //special characters like '', getPreferredSize() could throw NPE.
            barcode.getPreferredSize();

            return barcode;
         }
         catch(Exception ex2) {
            LOG.debug("Failed to create barcode for: " + str, ex2);
         }
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
      return Catalog.getCatalog().getString("Barcode Code 39");
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(BarcodeCode39Presenter.class);
}

