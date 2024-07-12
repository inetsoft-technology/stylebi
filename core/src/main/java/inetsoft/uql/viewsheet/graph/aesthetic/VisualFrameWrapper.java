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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.report.composition.graph.BrushDataSet;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * This class defines the common API for all legend frames.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class VisualFrameWrapper implements AssetObject, ContentObject {
   /**
    * Create legend frame.
    * @param elem the specified element.
    */
   public static VisualFrameWrapper createVisualFrame(Element elem)
         throws Exception
   {
      String cls = Tool.getAttribute(elem, "classname");
      int dot = cls.lastIndexOf('.');

      cls = stripInnerName(cls.substring(dot + 1));
      VisualFrameWrapper lframe = createWrapper(cls);

      lframe.parseXML(elem);
      return lframe;
   }

   /**
    * Wrap a legend frame in a wrapper.
    */
   public static VisualFrameWrapper wrap(VisualFrame frame) throws Exception {
      String cls = frame.getClass().getName();
      int dot = cls.lastIndexOf('.');

      cls = stripInnerName(cls.substring(dot + 1));
      VisualFrameWrapper wrapper = createWrapper(cls);

      wrapper.frame = frame;
      return wrapper;
   }

   /**
    * Constructor.
    */
   public VisualFrameWrapper() {
      frame = createVisualFrame();
   }

   /**
    * Check whether the frame has been changed from the default state.
    */
   public boolean isChanged() {
      return changed;
   }

   /**
    * Set whether the frame has been changed from the default state.
    */
   public void setChanged(boolean changed) {
      this.changed = changed;
   }

   /**
    * Create the corresponding frame.
    */
   protected abstract VisualFrame createVisualFrame();

   /**
    * Get the wrapped legend frame.
    */
   public VisualFrame getVisualFrame() {
      return frame;
   }

   /**
    * Set the wrapped legend frame.
    */
   public void setVisualFrame(VisualFrame frame) {
      this.frame = frame;
   }

   /**
    * Parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseAttributes(tag);
      parseContents(tag);
   }

   /**
    * Parse the attributes.
    * @param tag the specified xml element.
    */
   protected void parseAttributes(Element tag) throws Exception {
      changed = "true".equals(Tool.getAttribute(tag, "changed"));
   }

   /**
    * Parse the contents.
    * @param tag the specified xml element.
    */
   protected void parseContents(Element tag) throws Exception {
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      String name = stripInnerName(frame.getClass().getName());
      int dot = name.lastIndexOf('.');

      name = name.substring(dot + 1);
      name = "inetsoft.graph.aesthetic." + name;
      writer.print("<legendFrame classname=\"" + name + "\" ");
      writeAttributes(writer);
      writer.print(">");
      writeContents(writer);
      writer.println("</legendFrame>");
   }

   /**
    * Write the attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" changed=\"" + changed + "\" ");
   }

   /**
    * Write the contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
   }

   /**
    * Check if equals another object in content.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof VisualFrameWrapper)) {
         return false;
      }

      VisualFrameWrapper vobj = (VisualFrameWrapper) obj;

      return frame.equalsContent(vobj.frame) && changed == vobj.changed;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      // do nothing, not throw unsupported exception
      return true;
   }

   /**
    * Check if equals another object. The default implementation will just
    * test whether class is equal.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof VisualFrameWrapper)) {
         return false;
      }

      VisualFrameWrapper vobj = (VisualFrameWrapper) obj;

      return frame.equals(vobj.frame) && changed == vobj.changed;
   }

   /**
    * Create a copy of this object.
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         VisualFrameWrapper obj = (VisualFrameWrapper) super.clone();
         obj.frame = (VisualFrame) frame.clone();
         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VisualFrameWrapper", ex);
         return null;
      }
   }

   /**
    * Strip inner class name.
    */
   private static String stripInnerName(String name) {
      int dollar = name.indexOf('$');

      // strip inner class suffix
      if(dollar > 0) {
         name = name.substring(0, dollar);
      }

      if(name.endsWith("Wrapper")) {
         name = name.substring(0, name.length() - 7);
      }

      return name;
   }

   /**
    * Get the column for brushed dataset.
    */
   protected String getBrushField(String name, String col) {
      if(col != null && col.startsWith(BrushDataSet.ALL_HEADER_PREFIX) &&
         name != null && !name.startsWith(BrushDataSet.ALL_HEADER_PREFIX))
      {
         name = BrushDataSet.ALL_HEADER_PREFIX + name;
      }

      return name;
   }

   private static VisualFrameWrapper createWrapper(String cls) throws Exception {
      // optimization, avoid Class.forName if necessary
      switch(cls) {
      case "BipolarColorFrame":
         return new BipolarColorFrameWrapper();
      case "BluesColorFrameW":
         return new BluesColorFrameWrapper();
      case "BrightnessColorFrame":
         return new BrightnessColorFrameWrapper();
      case "BuGnColorFrame":
         return new BuGnColorFrameWrapper();
      case "BuPuColorFrame":
         return new BuPuColorFrameWrapper();
      case "CategoricalColorFrame":
         return new CategoricalColorFrameWrapper();
      case "CategoricalLineFrame":
         return new CategoricalLineFrameWrapper();
      case "CategoricalShapeFrame":
         return new CategoricalShapeFrameWrapper();
      case "CategoricalSizeFrame":
         return new CategoricalSizeFrameWrapper();
      case "CategoricalTextureFrame":
         return new CategoricalTextureFrameWrapper();
      case "CircularColorFrame":
         return new CircularColorFrameWrapper();
      case "DefaultTextFrame":
         return new DefaultTextFrameWrapper();
      case "FillShapeFrame":
         return new FillShapeFrameWrapper();
      case "GnBuColorFrame":
         return new GnBuColorFrameWrapper();
      case "GradientColorFrame":
         return new GradientColorFrameWrapper();
      case "GreensColorFrame":
         return new GreensColorFrameWrapper();
      case "GreysColorFrame":
         return new GreysColorFrameWrapper();
      case "GridTextureFrame":
         return new GridTextureFrameWrapper();
      case "HeatColorFrame":
         return new HeatColorFrameWrapper();
      case "LeftTiltTextureFrame":
         return new LeftTiltTextureFrameWrapper();
      case "LinearLineFrame":
         return new LinearLineFrameWrapper();
      case "LinearSizeFrame":
         return new LinearSizeFrameWrapper();
      case "OrangesColorFrame":
         return new OrangesColorFrameWrapper();
      case "OrientationShapeFrame":
         return new OrientationShapeFrameWrapper();
      case "OrientationTextureFrame":
         return new OrientationTextureFrameWrapper();
      case "OrRdColorFrame":
         return new OrRdColorFrameWrapper();
      case "OvalShapeFrame":
         return new OvalShapeFrameWrapper();
      case "PiYGColorFrame":
         return new PiYGColorFrameWrapper();
      case "PolygonShapeFrame":
         return new PolygonShapeFrameWrapper();
      case "PRGnColorFrame":
         return new PRGnColorFrameWrapper();
      case "PuBuColorFrame":
         return new PuBuColorFrameWrapper();
      case "PuBuGnColorFrame":
         return new PuBuGnColorFrameWrapper();
      case "PuOrColorFrame":
         return new PuOrColorFrameWrapper();
      case "PuRdColorFrame":
         return new PuRdColorFrameWrapper();
      case "PurplesColorFrame":
         return new PurplesColorFrameWrapper();
      case "RainbowColorFrame":
         return new RainbowColorFrameWrapper();
      case "RdBuColorFrame":
         return new RdBuColorFrameWrapper();
      case "RdGyColorFrame":
         return new RdGyColorFrameWrapper();
      case "RdPuColorFrame":
         return new RdPuColorFrameWrapper();
      case "RdYlBuColorFrame":
         return new RdYlBuColorFrameWrapper();
      case "RdYlGnColorFrame":
         return new RdYlGnColorFrameWrapper();
      case "RedsColorFrame":
         return new RedsColorFrameWrapper();
      case "RGBCubeColorFrame":
         return new RGBCubeColorFrameWrapper();
      case "RightTiltTextureFrame":
         return new RightTiltTextureFrameWrapper();
      case "SaturationColorFrame":
         return new SaturationColorFrameWrapper();
      case "SpectralColorFrame":
         return new SpectralColorFrameWrapper();
      case "StaticColorFrame":
         return new StaticColorFrameWrapper();
      case "StaticLineFrame":
         return new StaticLineFrameWrapper();
      case "StaticShapeFrame":
         return new StaticShapeFrameWrapper();
      case "StaticSizeFrame":
         return new StaticSizeFrameWrapper();
      case "StaticTextureFrame":
         return new StaticTextureFrameWrapper();
      case "TriangleShapeFrame":
         return new TriangleShapeFrameWrapper();
      case "YlGnBuColorFrame":
         return new YlGnBuColorFrameWrapper();
      case "YlGnColorFrame":
         return new YlGnColorFrameWrapper();
      case "YlOrBrColorFrame":
         return new YlOrBrColorFrameWrapper();
      case "YlOrRdColorFrame":
         return new YlOrRdColorFrameWrapper();
      default:
         cls = "inetsoft.uql.viewsheet.graph.aesthetic." + cls + "Wrapper";

         return (VisualFrameWrapper) Class.forName(cls).newInstance();
      }
   }

   protected VisualFrame frame;
   private boolean changed;

   private static final Logger LOG =
      LoggerFactory.getLogger(VisualFrameWrapper.class);
}
