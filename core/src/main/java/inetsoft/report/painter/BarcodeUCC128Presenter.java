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

import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;
import net.sourceforge.barbecue.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This presenter displays a string in the UCC128 symbology.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class BarcodeUCC128Presenter extends AbstractBarcodePresenter {
   /**
    * Create a barcode object.
    */
   @Override
   protected Barcode createBarcode(String str) {
      try {
         String appId =
            SreeEnv.getProperty("UCC128.application.identifier");
         return BarcodeFactory.createUCC128(appId, str);
      }
      catch(BarcodeException ex) {
         LOG.debug("Failed to create barcode for: " + str, ex);
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
      return Catalog.getCatalog().getString("Barcode UCC128");
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(BarcodeUCC128Presenter.class);
}

