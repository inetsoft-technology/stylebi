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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.report.TableDataPath;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * AbstractLayout is base class for ViewsheetLayout and PrintLayout.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public abstract class AbstractLayout implements AssetObject {
   /**
    * Create an assembly layout from an xml element.
    * @param elem the specified xml element.
    * @return the created assembly layout.
    */
   public static VSAssemblyLayout createAssemblyLayout(Element elem)
      throws Exception
   {
      String cls = Tool.getAttribute(elem, "class");
      VSAssemblyLayout alayout =
         (VSAssemblyLayout) Class.forName(cls).newInstance();
      alayout.parseXML(elem);

      return alayout;
   }

   /**
    * Constructor.
    */
   public AbstractLayout() {
      super();
   }

   /**
    * Get scale font.
    */
   public float getScaleFont() {
      return scaleFont;
   }

   /**
    * Set scale font.
    */
   public void setScaleFont(float scaleFont) {
      this.scaleFont = scaleFont;
   }

   /**
    * Get horizontal screen.
    */
   public boolean isHorizontalScreen() {
      return horizontalScreen;
   }

   /**
    * Set horizontal screen.
    */
   public void setHorizontalScreen(boolean horizontalScreen) {
      this.horizontalScreen = horizontalScreen;
   }

   /**
    * Get vs assembly layout.
    */
   public List<VSAssemblyLayout> getVSAssemblyLayouts() {
      return vsAssemblyLayouts;
   }

   /**
    * Set vs assembly layout.
    */
   public void setVSAssemblyLayouts(List<VSAssemblyLayout> vsAssemblyLayouts) {
      this.vsAssemblyLayouts = vsAssemblyLayouts;
   }

   /**
    * Get the layout id.
    */
   public String getID() {
      return id;
   }

   /**
    * Set the layout id.
    */
   public void setID(String id) {
      this.id = id;
   }

   /**
    * Get one vs assembly layout by name.
    */
   public VSAssemblyLayout getVSAssemblyLayout(String name) {
      for(VSAssemblyLayout assemblyLayout : vsAssemblyLayouts) {
         if(assemblyLayout.getName().equals(name)) {
            return assemblyLayout;
         }
      }

      return null;
   }

   /**
    * Get assemblies that are manufactured through the layout and do not exist
    * on the viewsheet.
    */
   public ArrayList<VSAssemblyLayout> getEditableAssemblyLayouts() {
      ArrayList<VSAssemblyLayout> layouts = new ArrayList<>();

      for(VSAssemblyLayout assemblyLayout : vsAssemblyLayouts) {
         if(assemblyLayout instanceof VSEditableAssemblyLayout) {
            layouts.add(assemblyLayout);
         }
      }

      return layouts;
   }

   /**
    * Check if the layout is empty.
    */
   public boolean isEmpty() {
      return vsAssemblyLayouts == null || vsAssemblyLayouts.isEmpty();
   }

   /**
    * Apply layout.
    */
   public Viewsheet apply(Viewsheet viewsheet) {
      Viewsheet viewsheet0 = viewsheet.clone();
      viewsheet0.clearLayoutState();
      Assembly[] assemblies = viewsheet0.getAssemblies();

      for(Assembly assembly : assemblies) {
         VSAssembly vsAssembly = (VSAssembly) assembly;

         if(AnnotationVSUtil.isAnnotation(vsAssembly)) {
            continue;
         }

         if(vsAssembly.getContainer() != null) {
            continue;
         }

         VSAssemblyLayout assemblyLayout = getVSAssemblyLayout(vsAssembly.getName());

         if(assemblyLayout != null) {
            applyAssembly(vsAssembly, assemblyLayout);
         }
         else {
            // Explicitly set size and position to zero so that it does not
            // affect scaling. There may be elements that are not visible
            // but included in the layout, so the visible property cannot be
            // used to determine the scaling ratio.
            if(VSUtil.isPopComponent(vsAssembly.getAbsoluteName(), viewsheet) ||
               VSUtil.isTipView(vsAssembly.getAbsoluteName(), viewsheet))
            {
               vsAssembly.getVSAssemblyInfo().setLayoutPosition(new Point(0, 0));
            }
            else {
               applyAssembly(vsAssembly, new Point(0, 0), new Dimension(0, 0));
            }

            vsAssembly.getVSAssemblyInfo().setLayoutVisible(VSAssembly.ALWAYS_HIDE);
         }
      }

      viewsheet0.setRScaleFont(getScaleFont());

      return viewsheet0;
   }

   public void applyAssembly(VSAssembly vsAssembly, VSAssemblyLayout assemblyLayout) {
      Point newPos = assemblyLayout.getPosition();
      Dimension newSize = assemblyLayout.getSize();

      if(assemblyLayout.isDoubleCalendar()) {
         newSize = new Dimension(newSize.width / 2, newSize.height);
      }

      applyAssembly(vsAssembly, newPos, newSize);
   }

   private void applyAssembly(VSAssembly assembly, Point npos, Dimension nsize) {
      if(assembly instanceof GroupContainerVSAssembly) {
         applyGroup((GroupContainerVSAssembly) assembly, npos, nsize);
      }
      else if(assembly instanceof TabVSAssembly) {
         applyTab((TabVSAssembly) assembly, npos, nsize);
      }
      else if(assembly instanceof CurrentSelectionVSAssembly) {
         applySelection((CurrentSelectionVSAssembly) assembly, npos, nsize);
      }
      else if(assembly instanceof Viewsheet) {
         applyViewsheet((Viewsheet) assembly, npos, nsize);
      }
      else if(assembly instanceof LineVSAssembly) {
         applyLine((LineVSAssembly) assembly, npos, nsize);
      }
      else {
         applyBaseAssembly(assembly, npos, nsize);
      }
   }

   /**
    * Apply base assembly layout.
    */
   private void applyBaseAssembly(VSAssembly assembly, Point pos, Dimension size) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      info.setLayoutPosition(pos);

      if(info instanceof CalendarVSAssemblyInfo) {
         CalendarVSAssemblyInfo cinfo = (CalendarVSAssemblyInfo) info;

         if(cinfo.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE) {
            size = new Dimension(size.width * 2, size.height);
         }
      }

      info.setLayoutSize(size);
      applyScaleFont(assembly);
   }

   /**
    * Apply base Line assembly.
    */
   private void applyLine(LineVSAssembly assembly, Point pos, Dimension size) {
      LineVSAssemblyInfo linfo =
         (LineVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Dimension psize = getComponentSize(assembly);

      if(psize != null) {
         double xscale = size.width == 0 ? 1 : ((double) size.width) / ((double) psize.width);
         double yscale = size.height == 0 ? 1 : ((double) size.height) / ((double) psize.height);
         Point spos = linfo.getStartPos();
         Point epos = linfo.getEndPos();

         linfo.setStartPos(new Point((int) (spos.x * xscale),
            (int) (spos.y * yscale)));
         linfo.setEndPos(new Point((int) (epos.x * xscale),
            (int) (epos.y * yscale)));
      }

      applyBaseAssembly(assembly, pos, size);
   }


   /**
    *  Apply the font scale.
    */
   private void applyScaleFont(VSAssembly assembly) {
      applyScaleFont(assembly, getScaleFont());
   }

   /**
    *  Apply the font scale.
    */
   public static void applyScaleFont(VSAssembly assembly, float scaleFont) {
      if(!(assembly instanceof TabVSAssembly)) {
         FormatInfo formatInfo = assembly.getFormatInfo();
         TableDataPath[] paths = formatInfo.getPaths();

         for(TableDataPath path : paths) {
            if(!(assembly instanceof TableDataVSAssembly) ||
               path.getType() == TableDataPath.TITLE)
            {
               VSCompositeFormat format = formatInfo.getFormat(path);
               format.setRScaleFont(scaleFont);
            }
         }
      }
   }

   /**
    * Get the component size.
    */
   private Dimension getComponentSize(VSAssembly assembly) {
      Dimension psize = getSize(assembly);

      if(assembly instanceof TabVSAssembly) {
         Dimension maxComponentSize = getMaxSizeInTab((TabVSAssembly) assembly);
         psize.width = Math.max(psize.width, maxComponentSize.width);
         psize.height += maxComponentSize.height;
      }

      return psize;
   }

   private Dimension getSize(VSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Viewsheet vs = info.getViewsheet();
      Dimension size = vs.getPixelSize(info);

      return new Dimension(size.width, size.height);
   }

   /**
    * Get max component size in tab.
    */
   private Dimension getMaxSizeInTab(TabVSAssembly tassembly) {
      String[] assemblies = tassembly.getAssemblies();
      Viewsheet viewsheet = tassembly.getViewsheet();
      int maxWidth = 0;
      int maxHeight = 0;

      for(String assembly : assemblies) {
         VSAssembly vsAssembly = viewsheet.getAssembly(assembly);

         if(vsAssembly != null) {
            Dimension assemblySize = getComponentSize(vsAssembly);

            if(assemblySize.width > maxWidth) {
               maxWidth = assemblySize.width;
            }

            if(assemblySize.height > maxHeight) {
               maxHeight = assemblySize.height;
            }
         }
      }

      return new Dimension(maxWidth, maxHeight);
   }

   /**
    * Get the component position.
    */
   private Point getComponentPosition(VSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Viewsheet vs = info.getViewsheet();
      return vs.getPixelPosition(info);
   }

   /**
    * Apply group container assembly layout.
    */
   private void applyGroup(GroupContainerVSAssembly vsassembly,
                           Point npos, Dimension nsize)
   {
      Point opos = getComponentPosition(vsassembly);
      Dimension osize = getComponentSize(vsassembly);
      Point2D.Double scaleRadio = getScaleRatio(osize, nsize);
      String[] assemblies = vsassembly.getAssemblies();
      Viewsheet viewsheet = vsassembly.getViewsheet();

      for(String assembly : assemblies) {
         VSAssembly child = viewsheet.getAssembly(assembly);

         if(child != null) {
            Point childPos = getComponentPosition(child);
            Dimension childSize = getComponentSize(child);
            childPos = new Point(npos.x + (int) ((childPos.x - opos.x) * scaleRadio.x),
                                 npos.y + (int) ((childPos.y - opos.y) * scaleRadio.y));
            childSize = new Dimension((int) (childSize.width * scaleRadio.x),
                                      (int) (childSize.height * scaleRadio.y));

            if(child instanceof CalendarVSAssembly) {
               CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) child.getVSAssemblyInfo();

               if(info.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE) {
                  childSize.width /= 2;
               }
            }

            applyAssembly(child, childPos, childSize);
         }
      }

      applyBaseAssembly(vsassembly, npos, nsize);
   }

   /**
    * Apply tab assembly layout.
    */
   private void applyTab(TabVSAssembly vsassembly, Point npos, Dimension nsize) {
      Dimension tabSize = getSize(vsassembly);
      Dimension osize = getComponentSize(vsassembly);
      Point2D.Double scaleRadio = getScaleRatio(
         new Dimension(osize.width, osize.height - tabSize.height),
         new Dimension(nsize.width, nsize.height - tabSize.height));
      String[] assemblies = vsassembly.getAssemblies();
      Viewsheet viewsheet = vsassembly.getViewsheet();

      for(String assembly : assemblies) {
         VSAssembly child = viewsheet.getAssembly(assembly);

         if(child != null) {
            Dimension childSize = getComponentSize(child);
            childSize =
               new Dimension((int) (childSize.width * scaleRadio.x),
                             (int) (childSize.height * scaleRadio.y));
            Point childPos =
               new Point(npos.x, npos.y + tabSize.height);
            applyAssembly(child, childPos, childSize);
         }
      }

      tabSize.width = (int) (tabSize.width * scaleRadio.x);
      applyBaseAssembly(vsassembly, npos, tabSize);
   }

   /**
    * Apply current selection assembly layout.
    */
   private void applySelection(CurrentSelectionVSAssembly vsassembly,
                               Point npos, Dimension nsize)
   {
      Point opos = getComponentPosition(vsassembly);
      Dimension osize = getComponentSize(vsassembly);
      Point2D.Double scaleRadio = getScaleRatio(osize, nsize);
      int moveX = npos.x - opos.x;
      int moveY = npos.y - opos.y;
      String[] assemblies = vsassembly.getAssemblies();
      Viewsheet viewsheet = vsassembly.getViewsheet();

      for(String assembly : assemblies) {
         VSAssembly child = viewsheet.getAssembly(assembly);

         if(child != null) {
            Point childPos = getComponentPosition(child);
            childPos = new Point(childPos.x + moveX, childPos.y + moveY);
            Dimension childSize = getComponentSize(child);
            VSAssemblyInfo aInfo = child.getVSAssemblyInfo();

            // If selection is drop down type and hide, get it's list height.
            int height = -1;

            if(aInfo instanceof SelectionBaseVSAssemblyInfo) {
               height = ((SelectionBaseVSAssemblyInfo) aInfo).getListHeight();
            }
            else if(aInfo instanceof TimeSliderVSAssemblyInfo &&
               ((TimeSliderVSAssemblyInfo) aInfo).getTitleHeight() != childSize.getHeight())
            {
               height = ((TimeSliderVSAssemblyInfo) aInfo).getListHeight();
            }

            if(height != -1) {
               childSize.height = height * AssetUtil.defh;
            }

            applyAssembly(child, childPos, new Dimension(
               nsize.width, (int) (childSize.height * scaleRadio.y)));
         }
      }

      applyBaseAssembly(vsassembly, npos, nsize);
   }

   /**
    * Apply viewsheet assembly layout.
    */
   private void applyViewsheet(Viewsheet viewsheet, Point npos, Dimension nsize) {
      Rectangle bounds = viewsheet.getPreferredBounds();
      Point opos = new Point(bounds.x, bounds.y);
      Dimension osize = new Dimension(bounds.width, bounds.height);
      Point2D.Double scaleRatio = getScaleRatio(osize, nsize);
      Assembly[] assemblies = viewsheet.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly != null) {
            VSAssembly child = (VSAssembly) assembly;

            if(child.getContainer() != null) {
               continue;
            }

            Point childPos = getComponentPosition(child);
            childPos = new Point(npos.x + (int) ((childPos.x - opos.x) * scaleRatio.x),
                                 npos.y + (int) ((childPos.y - opos.y) * scaleRatio.y));
            Dimension childSize = getComponentSize(child);
            childSize = new Dimension((int) (childSize.width * scaleRatio.x),
                                      (int) (childSize.height * scaleRatio.y));

            if(child instanceof CalendarVSAssembly) {
               CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) child.getVSAssemblyInfo();

               if(info.getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE) {
                  childSize.width /= 2;
               }
            }

            applyAssembly(child, childPos, childSize);
         }
      }

      applyBaseAssembly(viewsheet, npos, nsize);
   }

   /**
    * Get scale ratio.
    */
   private Point2D.Double getScaleRatio(Dimension osize, Dimension nsize) {
      // avoid infinity
      if(osize.width == 0 || osize.height == 0 || nsize.width == 0 || nsize.height == 0) {
         return new Point2D.Double(1, 1);
      }

      return new Point2D.Double((double) nsize.width / (double) osize.width,
                                (double) nsize.height / (double) osize.height);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" id=\"" + id + "\"");
      writer.print(" scaleFont=\"" + scaleFont + "\"");
      writer.print(" horizontalScreen=\"" + horizontalScreen + "\"");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(vsAssemblyLayouts != null && vsAssemblyLayouts.size() > 0) {
         writer.print("<vsAssemblyLayouts>");

         for(VSAssemblyLayout vsAssemblyLayout : vsAssemblyLayouts) {
            vsAssemblyLayout.writeXML(writer);
         }

         writer.println("</vsAssemblyLayouts>");
      }

      if(objectList != null && objectList.length > 0) {
         writer.print("<objectList>");

         for(String objectName : objectList) {
            writer.print("<objectName>");
            writer.print("<![CDATA[" + objectName + "]]>");
            writer.print("</objectName>");
         }

         writer.println("</objectList>");
      }
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      this.id = Tool.getAttribute(elem, "id");
      String scaleFont, horizontalScreen;

      if((scaleFont = Tool.getAttribute(elem, "scaleFont")) != null) {
         this.scaleFont = Float.parseFloat(scaleFont);
      }

      if((horizontalScreen =
         Tool.getAttribute(elem, "horizontalScreen")) != null)
      {
         this.horizontalScreen = "true".equals(horizontalScreen);
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element vsAssemblyLayoutsNode =
         Tool.getChildNodeByTagName(elem, "vsAssemblyLayouts");
      vsAssemblyLayouts = new ArrayList<>();

      if(vsAssemblyLayoutsNode != null) {
         NodeList layoutsList = Tool.getChildNodesByTagName(vsAssemblyLayoutsNode,
                                                            "vsAssemblyLayout");

         for(int i = 0; i < layoutsList.getLength(); i++) {
            Element node = (Element) layoutsList.item(i);
            vsAssemblyLayouts.add(createAssemblyLayout(node));
         }
      }

      Element objectListNode = Tool.getChildNodeByTagName(elem, "objectList");
      ArrayList<String> objlist = new ArrayList<>();

      if(objectListNode != null) {
         NodeList nameNodeList = objectListNode.getChildNodes();

         for(int i = 0; i < nameNodeList.getLength(); i++) {
            String objectName = Tool.getValue(nameNodeList.item(i));

            if(objectName == null || "".equals(objectName)) {
               continue;
            }

            objlist.add(objectName);
         }
      }

      objectList = objlist.toArray(new String[0]);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public AbstractLayout clone() {
      try {
         AbstractLayout layout = (AbstractLayout) super.clone();

         if(vsAssemblyLayouts != null) {
            layout.vsAssemblyLayouts =
               Tool.deepCloneCollection(vsAssemblyLayouts);
         }

         if(objectList != null) {
            layout.objectList = objectList.clone();
         }

         return layout;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   @Override
   public String toString() {
      return "AbstractLayout{" +
         "scaleFont=" + scaleFont +
         ", horizontalScreen=" + horizontalScreen +
         ", vsAssemblyLayouts=" + vsAssemblyLayouts +
         ", objectList=" + Arrays.toString(objectList) +
         ", id='" + id + '\'' +
         '}';
   }

   private float scaleFont;
   private boolean horizontalScreen;
   private List<VSAssemblyLayout> vsAssemblyLayouts;
   private String[] objectList;
   private String id;

   private static final Logger LOG = LoggerFactory.getLogger(AbstractLayout.class);
}
