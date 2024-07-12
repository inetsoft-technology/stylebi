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
package inetsoft.graph.mxgraph.util;

import java.util.Hashtable;
import java.util.Map;

/**
 * Maps from keys to base64 encoded images or file locations. All values must
 * be URLs or use the format data:image/format followed by a comma and the base64
 * encoded image data, eg. "data:image/gif,XYZ", where XYZ is the base64 encoded
 * image data.
 * <p>
 * To add a new image bundle to an existing graph, the following code is used:
 *
 * <code>
 * mxImageBundle bundle = new mxImageBundle();
 * bundle.PutImage("myImage", "data:image/gif,R0lGODlhEAAQAMIGAAAAAICAAICAgP" +
 * "//AOzp2O3r2////////yH+FUNyZWF0ZWQgd2l0aCBUaGUgR0lNUAAh+QQBCgAHACwAAAAA" +
 * "EAAQAAADTXi63AowynnAMDfjPUDlnAAJhmeBFxAEloliKltWmiYCQvfVr6lBPB1ggxN1hi" +
 * "laSSASFQpIV5HJBDyHpqK2ejVRm2AAgZCdmCGO9CIBADs=");
 * graph.addImageBundle(bundle);
 * </code>
 * <p>
 * The image can then be referenced in any cell style using image=myImage.
 * If you are using mxOutline, you should use the same image bundles in the
 * graph that renders the outline.
 * <p>
 * To convert a given BufferedImage to a base64 encoded String, the following
 * code can be used:
 *
 * <code>
 * ByteArrayOutputStream bos = new ByteArrayOutputStream();
 * ImageIO.write(image, "png", bos);
 * System.out.println("base64=" + mxBase64.encodeToString(
 * bos.toByteArray(), false));
 * </code>
 * <p>
 * The value is decoded in mxUtils.loadImage. The keys for images are resolved
 * and the short format above is converted to a data URI in
 * mxGraph.postProcessCellStyle.
 */
public class mxImageBundle {

   /**
    * Maps from keys to images.
    */
   protected Map<String, String> images = new Hashtable<String, String>();

   /**
    * Returns the images.
    */
   public Map<String, String> getImages()
   {
      return images;
   }

   /**
    * Adds the specified entry to the map.
    */
   public void putImage(String key, String value)
   {
      images.put(key, value);
   }

   /**
    * Returns the value for the given key.
    */
   public String getImage(String key)
   {
      if(key != null) {
         return images.get(key);
      }

      return null;
   }

}
