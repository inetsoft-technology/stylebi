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
package inetsoft.report.painter;

import inetsoft.util.Catalog;
import net.sourceforge.barbecue.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This presenter displays a string in the USPS symbology.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class BarcodeUSPSPresenter extends AbstractBarcodePresenter {
   /**
    * Create a barcode object.
    */
   @Override
   protected Barcode createBarcode(String str) {
      try {
         return BarcodeFactory.createUSPS(str);
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
      return Catalog.getCatalog().getString("Barcode USPS");
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(BarcodeUSPSPresenter.class);
}

