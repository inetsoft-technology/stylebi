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
package inetsoft.report.composition.graph;

import inetsoft.graph.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GDebug;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * SCOHelper, the file helper use to read/write style chart objects.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class SCOHelper {
   /**
    * Create the visual graph by reading the sco file specified as argument,
    * then print it.
    */
   // @by louis, pass the security scanning
   /*public static void main(String args[]) {
      File file = new File(args[0]);
      SCOHelper helper = createRHelper(file);
      VGraph graph = helper.createVGraph();
      Rectangle2D bounds = new Rectangle2D.Double(x, y, cw, ch);
      final BufferedImage img =
         new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = (Graphics2D) img.getGraphics();

      g.setColor(Color.white);
      g.fill(bounds);
      graph.layout(x, y, cw, ch);
      graph.paintGraph(g, true);

      JFrame win = new JFrame();
      win.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      Canvas pane = new Canvas() {
         public Dimension getPreferredSize() {
            return new Dimension(w, h);
         }

         public void paint(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.drawImage(img, 0, 0, this);
            g.dispose();
         }
      };

      win.getContentPane().setLayout(new BorderLayout());
      win.getContentPane().add(pane, "Center");
      win.setBackground(Color.white);
      win.pack();
      win.setVisible(true);
   }*/

   /**
    * Create the SCOHelper for read.
    */
   public static SCOHelper createRHelper(File file) {
      try {
         InputStream fin = new FileInputStream(file);
         return new SCOHelper(fin);
      }
      catch(Exception ex) {
         LOG.debug("Failed to create a read helper for file: " + file, ex);
      }

      return null;
   }

   /**
    * Create the SCOHelper for write.
    */
   public static SCOHelper createWHelper(File file) {
      try {
         OutputStream fout = new FileOutputStream(file);
         return new SCOHelper(fout);
      }
      catch(Exception ex) {
         LOG.debug("Failed to create a write helper for file: " + file,
            ex);
      }

      return null;
   }

   /**
    * Create a SCOHelper to read chart objects.
    */
   public SCOHelper(InputStream in) {
      this.in = in;
   }

   /**
    * Create a SCOHelper to write chart objects.
    */
   public SCOHelper(OutputStream out) {
      this.out = out;
   }

   /**
    * Write chart objects.
    * @param cinfo the specified chart assembly info.
    * @param adata the data generated without brushing, null if no brushing.
    * @param ddata the data generated with brushing.
    * @param vars the specified variable table.
    */
   public void write(ChartVSAssemblyInfo cinfo, DataSet adata, DataSet ddata,
                     VariableTable vars) {
      try {
         ObjectOutputStream oo = new ObjectOutputStream(out);
         oo.writeObject(cinfo);
         oo.writeObject(adata);
         oo.writeObject(ddata);
         oo.writeObject(vars);
         oo.close();
      }
      catch(Exception ex) {
         LOG.debug("Failed to write the chart objects", ex);
      }
   }

   /**
    * Create the visual graph object by reading the sco file.
    */
   public VGraph createVGraph() {
      Object[] arr = read();

      if(arr == null) {
         return null;
      }

      ChartVSAssemblyInfo cinfo = (ChartVSAssemblyInfo) arr[0];
      DataSet adata = (DataSet) arr[1];
      DataSet data = (DataSet) arr[2];
      VariableTable vars = (VariableTable) arr[3];
      GraphGenerator gen = GraphGenerator.getGenerator(cinfo, adata, data, vars,
                                                       null, XSourceInfo.NONE, null);
      EGraph egraph = gen.createEGraph();
      GDebug.dumpGraph(egraph, gen.getData());
      return Plotter.getPlotter(egraph).plot(gen.getData());
   }

   /**
    * Read chart objects.
    * @return the object array contains the chart objects, namely chart assembly
    * info, the data without brushing (null no brushing), the data with brushing,
    * and the variable table.
    */
   public Object[] read() {
      try {
         Object[] arr = new Object[4];
         ObjectInputStream oin = new ObjectInputStream(in);
         arr[0] = oin.readObject();
         arr[1] = oin.readObject();
         arr[2] = oin.readObject();
         arr[3] = oin.readObject();
         oin.close();
         return arr;
      }
      catch(Exception ex) {
         LOG.error("Failed to read the chart objects", ex);
      }

      return null;
   }

   private static final int x = 20, y = 20;
   private static final int cw = 560, ch = 400;
   private static final int w = 600, h = 440;
   private InputStream in;
   private OutputStream out;

   private static final Logger LOG =
      LoggerFactory.getLogger(SCOHelper.class);
}
