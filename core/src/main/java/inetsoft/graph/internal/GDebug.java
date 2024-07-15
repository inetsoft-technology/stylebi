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
package inetsoft.graph.internal;

import inetsoft.graph.*;
import inetsoft.graph.data.*;
import inetsoft.util.CoreTool;
import inetsoft.util.FileSystemService;
import inetsoft.util.graphics.SVGSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Debugging related utility functions.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GDebug {
   /**
    * Get object's memory address in jvm, if two objects have same result,
    * they must be the same object.
    */
   public static String getMemoryAdd(Object obj) {
      String cls = obj.getClass().getName();
      int dot = cls.lastIndexOf(".");
      cls = dot >= 0 ? cls.substring(dot + 1) : cls;
      return cls + "["
         + Integer.toHexString(System.identityHashCode(obj)) + "]";
   }

   /**
    * Take a snapshot of the graph in logical space.
    */
   public static void snapLogical(VGraph graph, String fname) {
      snap(graph, fname, 0, 0, 1000, 1000);
   }

   /**
    * Take a snapshot of the graph.
    */
   public static void snap(VGraph graph, String fname) {
      Rectangle2D box = graph.getBounds();
      double x = box.getX();
      double y = box.getY();
      double w = box.getWidth();
      double h = box.getHeight();

      snap(graph, fname, x, y, w, h);
   }

   /**
    * Take a snapshot of the graph.
    */
   public static void snap(VGraph graph, String fname, double x, double y,
                           double w, double h)
   {
      int GAP = 20;
      File file = getFile(fname);
      int w2 = (int) (w + 2 * GAP);
      int h2 = (int) (h + 2 * GAP);
      Image img = new BufferedImage(w2, h2, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = (Graphics2D) img.getGraphics();

      g.setColor(Color.white);
      g.fill(new Rectangle2D.Double(0, 0, w2, h2));
      g.translate(GAP, GAP);
      g.setColor(new Color(230, 230, 230));
      g.fill(new Rectangle2D.Double(0, 0, w, h));

      g.translate(-x, h + 2 * y);
      g.transform(GDefaults.FLIPY);
      graph.paint(g, GraphPaintContext.getDefault());
      g.dispose();

      writeImage(file, img);
   }

   /**
    * Get an image for painting chart.
    */
   private static Image getImage(int w, int h) {
      return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
   }

   /**
    * Write an image to a file.
    */
   public static void writeImage(String fname, Image img) {
      writeImage(FileSystemService.getInstance().getFile(fname), img);
   }

   /**
    * Write an image to a file.
    */
   public static void writeImage(File file, Image img) {
      try(java.io.FileOutputStream fout = new java.io.FileOutputStream(file)) {
         CoreTool.writePNG(img, fout);
      }
      catch(Exception ex) {
         LOG.error("Failed to write image: " + file, ex);
      }
   }

   public static void writeGraphics(File file, Graphics2D svg) {
      try(FileOutputStream fout = new FileOutputStream(file)) {
         SVGSupport.getInstance().writeSVG(svg, fout);
      }
      catch(Exception ex) {
         LOG.error("Failed to write graphics: " + file, ex);
      }
   }

   /**
    * Get the image file name.
    */
   private static File getFile(String fname) {
      if(fname.indexOf("/") >= 0) {
         return FileSystemService.getInstance().getFile(fname);
      }

      String dir = System.getProperty("graph.image.dir");

      if(dir == null) {
         dir = ".";
      }

      return FileSystemService.getInstance().getFile(dir, fname);
   }

   /**
    * Check if the property is set to true.
    */
   public static void dumpGraph(EGraph egraph, DataSet data) {
      System.err.println("\n" + egraph);
      printDataSet(data, 0);
   }

   /**
    * Check if the property is set to true.
    */
   public static boolean isTrue(String key) {
      String value = System.getProperty(key);
      return "true".equals(value);
   }

   /**
    * print the data set.
    */
   public static void printDataSet(DataSet data, int n) {
      if(data == null) {
         System.err.println("null");
         return;
      }

      int count = data.getColCount();
      System.err.print("    ");

      for(int i = 0; i < count; i++) {
         System.err.print("[" + i + "]" + data.getHeader(i));
      }

      System.err.println();

      if(data.getRowCount() > 0) {
         System.err.print("    ");

         for(int j = 0; j < count; j++) {
            Object obj = data.getData(j, 0);
            String cls = obj == null ? "null" : obj.getClass().getName();
            int idx = cls.lastIndexOf(".");
            cls = idx < 0 ? cls : cls.substring(idx + 1);
            System.err.print("[" + j + "]" + cls);
         }

         System.err.println();
      }

      int max = data.getRowCount();

      if(n > 0) {
         max = Math.min(max, n);
      }

      for(int i = 0; i < max; i++) {
         System.err.print((i + "     ").substring(0, 4));

         for(int j = 0; j < count; j++) {
            System.err.print("[" + j + "]" + data.getData(j, i));
         }

         System.err.println();
      }
   }

   public static void printDataSets(DataSet data) {
      printDataSets(data, "");
   }

   private static void printDataSets(DataSet data, String indent) {
      System.err.print(indent + data + " rows: " + data.getRowCount());

      if(data instanceof AbstractDataSet) {
         AbstractDataSet data2 = (AbstractDataSet) data;
         System.err.println(" rows0: " + data2.getRowCountUnprojected() +
                               ": " + data2.getCalcColumns(true) + "; " + data2.getCalcRows());
      }

      if(data instanceof AbstractDataSetFilter) {
         printDataSets(((AbstractDataSetFilter) data).getDataSet(), indent + "   ");
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(GDebug.class);
}
