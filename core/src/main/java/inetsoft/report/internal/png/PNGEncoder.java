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
package inetsoft.report.internal.png;

import java.awt.*;
import java.io.*;

/**
 * Encode an image in png format.
 */
public class PNGEncoder implements PNGConstants {
   public PNGEncoder(Image image, boolean alpha) {
      this.alpha = alpha;
      this.image = new PNGImage(image, 8, alpha);
   }

   /**
    * Set the output resolution.
    */
   public void setResolution(int dpi) {
      this.dpi = Integer.valueOf(dpi);
   }
   
   public void encode(OutputStream out) throws IOException {
      DataOutputStream dout = 
         new DataOutputStream(new BufferedOutputStream(out));
      PNGUtil util = new PNGUtil(alpha);
      PNGWriter writer = new PNGWriter(image, PNG_ZBUF_SIZE, util, alpha);

      writer.writeSignature(dout);
      writer.writeIHDR(dout);

      if(dpi != null) {
         writer.writePHYS(dout, dpi.intValue());
      }
      
      writer.writeImageData(dout);
      writer.writeIEND(dout);

      dout.flush();
   }

   private PNGImage image;
   private boolean alpha;
   private Integer dpi;
}

