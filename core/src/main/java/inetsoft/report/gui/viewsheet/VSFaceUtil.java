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
package inetsoft.report.gui.viewsheet;

import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.uql.viewsheet.internal.VSUtil;

import java.awt.*;
import java.awt.image.*;
import java.util.List;
import java.util.*;

/**
 * VSFaceUtil is a utility class for viewsheet face object.
 *
 * @version 8.5, 07/26/2006
 * @author InetSoft Technology Corp
 */
public class VSFaceUtil {
   /**
    * Get all the available face ids.
    * @return all the available face ids.
    */
   public static String[] getIDs(Map map) {
      if(map == null) {
         return null;
      }

      Set keys = map.keySet();
      String[] ids = new String[keys.size()];
      keys.toArray(ids);

      Arrays.sort(ids, new Comparator() {
         @Override
         public int compare(Object s1, Object s2) {
            String ss1 = (String) s1;
            String ss2 = (String) s2;
            return Integer.parseInt(ss1) - Integer.parseInt(ss2);
         }
      });

      return ids;
   }

   /**
    * Get all the available prefix face ids excluding the theme.
    * @return all the available prefix face ids.
    */
   public static String[] getPrefixIDs(Map map) {
      String[] ids = getIDs(map);

      if(ids == null) {
         return null;
      }

      List list = new ArrayList();

      for(int i = 0; i < ids.length; i++) {
         String prefix = ids[i].substring(0, ids[i].length() - 1) + "0";

         if(!list.contains(prefix)) {
            list.add(prefix);
         }
      }

      String[] prefixes = new String[list.size()];
      list.toArray(prefixes);

      return prefixes;
   }

   /**
    * Get equal X-Y scale size according to the defaultSize.
    * @param dsize the default size.
    * @param nsize the customized size.
    */
   public static Dimension getEqualScaleSize(Dimension dsize, Dimension csize) {
      if(dsize == null || csize == null || dsize.width == 0 ||
         dsize.height == 0)
      {
         return csize;
      }

      double xscale = (double) csize.width / dsize.width;
      double yscale = (double) csize.height / dsize.height;
      double scale = Math.min(xscale, yscale);

      int width = (int) (dsize.width * scale);
      int height = (int) (dsize.height * scale);

      return new Dimension(width, height);
   } 

   /**
    * Add drop shadow to the image.
    */
   public static BufferedImage addShadow(BufferedImage img, int edge) {
      int w = img.getWidth();
      int h = img.getHeight();

      BufferedImage out = new BufferedImage(w + edge, h + edge,
                                            BufferedImage.TYPE_INT_ARGB);
      BufferedImage shadow = createDropShadow(img, edge / 3);

      Graphics g = out.getGraphics();
      g.drawImage(shadow, 0, 0, null);
      g.drawImage(img, 0, 0, null);
      g.dispose();

      return out;
   }

   /**
    * Create a shadow of the image.
    */
   public static BufferedImage createDropShadow(BufferedImage image,
                                                int size)
   {
      BufferedImage shadow = new BufferedImage(
         image.getWidth() + 4 * size,
         image.getHeight() + 4 * size,
         BufferedImage.TYPE_INT_ARGB);
        
      Graphics2D g2 = shadow.createGraphics();
      g2.drawImage(image, size * 2, size * 2, null);
        
      g2.setComposite(AlphaComposite.SrcIn);
      g2.setColor(Color.GRAY);
      g2.fillRect(0, 0, shadow.getWidth(), shadow.getHeight());       
      g2.dispose();
      
      shadow = getGaussianBlurFilter(size, true).filter(shadow, null);
      shadow = getGaussianBlurFilter(size, false).filter(shadow, null);
      
      return shadow;
   }
    
   private static ConvolveOp getGaussianBlurFilter(int radius,
                                                   boolean horizontal) 
   {
      if(radius < 1) {
         throw new IllegalArgumentException("Radius must be >= 1");
      }
      
      int size = radius * 2 + 1;
      float[] data = new float[size];
      
      float sigma = radius / 3.0f;
      float twoSigmaSquare = 2.0f * sigma * sigma;
      float sigmaRoot = (float) Math.sqrt(twoSigmaSquare * Math.PI);
      float total = 0.0f;
      
      for(int i = -radius; i <= radius; i++) {
         float distance = i * i;
         int index = i + radius;
         data[index] = (float) Math.exp(-distance / twoSigmaSquare) / sigmaRoot;
         total += data[index];
      }
      
      for(int i = 0; i < data.length; i++) {
         data[i] /= total;
      }        
      
      Kernel kernel = null;

      if(horizontal) {
         kernel = new Kernel(size, 1, data);
      }
      else {
         kernel = new Kernel(1, size, data);
      }

      return new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
   }

   /**
    * Get theme id from theme name.
    * @param dsize the default size.
    * @param nsize the customized size.
    */
   public static int getThemeIDByName(String theme) {
      List list = Arrays.asList(themes);

      return list.indexOf(theme);
   }

   /**
    * Get the current theme id.
    */
   public static int getCurrentThemeID() {
      String theme = VSUtil.themeHelper.getTheme();
      return getThemeIDByName(theme);
   }

   static {
      VSUtil.themeHelper = PortalThemesManager::getColorTheme;
   }
   private static String[] themes = {
      "unknown", "blue", "green", "alloy", "orange", "vista", "granite"};
}
