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
package inetsoft.report;

import inetsoft.graph.data.DataSet;
import inetsoft.report.internal.*;
import inetsoft.report.lens.DefaultTextLens;
import inetsoft.report.painter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * FixedContainer is an element container. It can be used to create
 * fixed position and size elements in a non-flow page area. This class
 * is also the base class for section band. It manages the positioning
 * and size of elements in the container.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class FixedContainer implements Serializable, Cloneable {
   /**
    * Create a container. A report must be passed in. The report must
    * be the report where this container is going to be placed in.
    * @param parent ReportSheet the container is associated with.
    */
   public FixedContainer(ReportSheet parent) {
      this.parent = new WeakReference(parent);
   }

   /**
    * Get the report this container is associated with.
    */
   public ReportSheet getReport() {
      return (ReportSheet) parent.get();
   }

   /**
    * Set the report this element is contained in.
    */
   public void setReport(ReportSheet report) {
      this.parent = new WeakReference(report);

      for(Object elem : elements) {
         ((BaseElement) elem).setReport(report);
      }
   }

   /**
    * Add a text element to the document. The text string can be a simple
    * string, or contains multiple lines separated by the newline
    * character.
    * @param text text string.
    * @param bounds position and size of the element in the container.
    * @return element id.
    */
   public String addText(String text, Rectangle bounds) {
      return addText(new DefaultTextLens(text), bounds);
   }

   /**
    * Add a text element to the document. The TextLens provides an extra
    * level of indirection. For example, it can be used to refer to a
    * TextField on a GUI screen, the most up-to-date value in the text
    * field will be used when printing the document. This way a ReportSheet
    * can be created once, and don't need to be modified when the
    * text contents change.
    * <p>
    * The inetsoft.report.lens package also contains a StreamTextLens, which
    * allows retrieving text from a file, URL, or any input stream.
    * @param text text content lens.
    * @param bounds position and size of the element in the container.
    * @return element id.
    */
   public String addText(TextLens text, Rectangle bounds) {
      return addElement(new TextElementDef(getReport(), text), bounds);
   }

   /**
    * Add a text box to the document. A text box is a standalone area
    * on the document that contains a text string. It has a border around
    * by default. The text box element is similar to the painter elements
    * in that they both use the painter layout and wrapping options. The
    * current setting of the painter layout and wrapping are used by the
    * text box elements as well.
    * @param text text content.
    * @param bounds position and size of the element in the container.
    * @return element id.
    */
   public String addTextBox(TextLens text, Rectangle bounds) {
      return addElement(new TextBoxElementDef(getReport(), text), bounds);
   }

   /**
    * Add a text box to the document. A text box is a standalone area
    * on the document that contains a text string. It has a border around
    * by default. The text box element is similar to the painter elements
    * in that they both use the painter layout and wrapping options. The
    * current setting of the painter layout and wrapping are used by the
    * text box elements as well.
    * @param text text content.
    * @param border border line style. One of the line styles defined in
    * the StyleConstants class.
    * @param textalign text alignment within the box.
    * @param bounds position and size of the element in the container.
    * @return element id.
    */
   public String addTextBox(TextLens text, int border, int textalign,
                            Rectangle bounds) {
      TextBoxElementDef box = new TextBoxElementDef(getReport(), text);

      box.setBorder(border);
      box.setTextAlignment(textalign);
      return addElement(box, bounds);
   }

   /**
    * Add a text box to the document. A text box is a standalone area
    * on the document that contains a text string. It has a border around
    * by default. The text box element is similar to the painter elements
    * in that they both use the painter layout and wrapping options. The
    * current setting of the painter layout and wrapping are used by the
    * text box elements as well.
    * @param text text content.
    * @param border border line style. One of the line styles defined in
    * the StyleConstants class.
    * @param textalign text alignment within the box.
    * @param bounds position and size of the element in the container.
    * @return element id.
    */
   public String addTextBox(String text, int border, int textalign,
                            Rectangle bounds) {
      return addTextBox(new DefaultTextLens(text), border, textalign, bounds);
   }

   /**
    * Add a painter element to the document. A painter is a self contained
    * object that can paint a document area. It can be used to add any
    * content to the document, through which the program has full control
    * on exact presentation on the document. Painter is the general
    * mechanism used to support some of the more common data types. For
    * example, Component and Image are handled internally by a painter
    * object. The program is free to define its own painter.
    * @param area the painter element.
    * @param bounds position and size of the element in the container.
    * @return element id.
    */
   public String addPainter(Painter area, Rectangle bounds) {
      return addElement(new PainterElementDef(getReport(), area), bounds);
   }

   /**
    * Add a chart to the report. The chart behaves like a painter.
    * It reserves an area and paints a chart on the area. The current
    * PainterLayout value is also applied to the chart.
    * @param chart chart data model.
    * @param bounds position and size of the element in the container.
    * @return element id.
    */
   public String addChart(DataSet chart, Rectangle bounds) {
      return addElement(new ChartElementDef(getReport(), chart), bounds);
   }

   /**
    * Add an AWT component to the document. The onscreen image of the
    * component is 'copied' on to the document.
    * @param comp component.
    * @param bounds position and size of the element in the container.
    * @return element id.
    */
   public String addComponent(Component comp, Rectangle bounds) {
      return addPainter(new ComponentPainter(comp), bounds);
   }

   /**
    * Add an image to the document.
    * @param image image object.
    * @param bounds position and size of the element in the container.
    * @return element id.
    */
   public String addImage(Image image, Rectangle bounds) {
      return addPainter(new ImagePainter(image), bounds);
   }

   /**
    * Add a table to the document. The table lens object encapsulate the
    * table attributes and contents. Through the table lens, the print
    * discovers table attributes such as color, border, font, etc..
    * For more details, refer the TableLens document.
    * @param table table lens.
    * @param bounds position and size of the element in the container.
    * @return element id.
    */
   public String addTable(TableLens table, Rectangle bounds) {
      return addElement(new TableElementDef(getReport(), table), bounds);
   }

   /**
    * Add an element to the document. Classes extending the ReportSheet
    * can extend element classes from the Element, and use
    * this method for adding the element to the document.
    * @param e document element.
    * @param bounds position and size of the element in the container.
    * @return element id.
    */
   public String addElement(ReportElement e, Rectangle bounds) {
      return addElement(e, bounds,
         new SectionBand.Separator(bounds.x + bounds.width,
         StyleConstants.NO_BORDER, Color.black));
   }

   /**
    * Add an element to the document. Classes extending the ReportSheet
    * can extend element classes from the Element, and use
    * this method for adding the element to the document.
    * @param e document element.
    * @param bounds position and size of the element in the container.
    * @param vseparator position and vertical seperator line style of the
    * element in the container.
    * @return element id.
    */
   public String addElement(ReportElement e, Rectangle bounds,
                            SectionBand.Separator vseparator) {
      int index = elements.size();

      for(int i = 0; i < elements.size(); i++) {
         Rectangle rec = (Rectangle) boxes.get(i);

         if(bounds.y < rec.y) {
            index = i;
            break;
         }
      }

      ((BaseElement) e).setParent(this);
      elements.insertElementAt(e, index);
      boxes.insertElementAt(bounds, index);
      printBounds.insertElementAt(bounds, index);
      vlines.insertElementAt(vseparator, index);

      return e.getID();
   }

   /**
    * Return the number of elements in the document.
    * @return number of elements.
    */
   public int getElementCount() {
      return elements.size();
   }

   /**
    * Get the specified element.
    * @param idx element index.
    * @return document element.
    */
   public ReportElement getElement(int idx) {
      return (ReportElement) elements.elementAt(idx);
   }

   /**
    * Find an element with the specified ID.
    */
   public ReportElement getElement(String id) {
      for(int i = 0; i < getElementCount(); i++) {
         if(getElement(i).getID().equals(id)) {
            return getElement(i);
         }
      }

      return null;
   }

   /**
    * Remove the specified element.
    * @param idx element index.
    */
   public void removeElement(int idx) {
      elements.removeElementAt(idx);
      boxes.removeElementAt(idx);
      printBounds.removeElementAt(idx);
      vlines.removeElementAt(idx);
   }

   /**
    * Remove all elements.
    */
   public void removeAllElements() {
      elements.removeAllElements();
      boxes.removeAllElements();
      printBounds.removeAllElements();
      vlines.removeAllElements();
   }

   /**
    * Get the index of the specified element.
    * @param e element.
    * @return element index.
    */
   public int getElementIndex(ReportElement e) {
      return elements.indexOf(e, 0);
   }

   /**
    * Find an element with the specified ID.
    * @return element index or -1 if element is not found.
    */
   public int getElementIndex(String id) {
      int cnt = getElementCount(); // optimization

      for(int i = 0; i < cnt; i++) {
         if(getElement(i).getID().equals(id)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get the bounds of the specified element.
    */
   public Rectangle getBounds(int idx) {
      return (Rectangle) boxes.elementAt(idx);
   }

   /**
    * Set the bounds of the specified element.
    */
   public void setBounds(int idx, Rectangle bounds) {
      boxes.setElementAt(bounds, idx);
      SectionBand.Separator p = getVSeparator(idx);
      p.setPosition(bounds.x + bounds.width);
      setVSeparator(idx, p);
      setPrintBounds(idx, new Rectangle(bounds));
   }

   /**
    * Get the bounds of the specified element.
    */
   public Rectangle getPrintBounds(int idx) {
      return (Rectangle) printBounds.elementAt(idx);
   }

   /**
    * Set the bounds of the specified element.
    */
   public void setPrintBounds(int idx, Rectangle bounds) {
      printBounds.setElementAt(bounds, idx);
   }

   /**
    * Get the vline setting of the specified element.
    */
   public SectionBand.Separator[] getVSeparators() {
      SectionBand.Separator[] ins = new SectionBand.Separator[vlines.size()];

      vlines.copyInto(ins);
      return ins;
   }

   /**
    * Get the vline setting of the specified element.
    */
   public SectionBand.Separator getVSeparator(int idx) {
      SectionBand.Separator ins = (SectionBand.Separator) vlines.elementAt(idx);

      return ins == null ?
         new SectionBand.Separator() : (SectionBand.Separator) ins.clone();
   }

   /**
    * Set the vline setting of the specified element.
    */
   public void setVSeparator(int idx, SectionBand.Separator bounds) {
      if(idx >= vlines.size()) {
         vlines.setSize(idx + 1);
      }

      vlines.setElementAt(bounds, idx);
   }

   /**
    * Add a shape to the report. The shape is drawn as the background of
    * the report.
    * @param shape a shape (line, rectangle, or oval).
    */
   public void addShape(PageLayout.Shape shape) {
      shapes.addElement(shape);
   }

   /**
    * Get the number of shapes contained in this report.
    */
   public int getShapeCount() {
      return shapes.size();
   }

   /**
    * Get the specified shape.
    */
   public PageLayout.Shape getShape(int idx) {
      return (PageLayout.Shape) shapes.elementAt(idx);
   }

   /**
    * Remove the specified shape.
    */
   public void removeShape(int idx) {
      shapes.removeElementAt(idx);
   }

   /**
    * Find the index of the shape.
    * @return -1 if shape is not in the container.
    */
   public int getShapeIndex(PageLayout.Shape shape) {
      return shapes.indexOf(shape);
   }

   /**
    * Reset the internal state so it's ready for next printing.
    */
   public void reset() {
      int length = boxes.size();
      printBounds = new Vector(length);

      for(int i = 0; i < length; i++) {
         Rectangle rec = (Rectangle) boxes.get(i);
         printBounds.add(rec.clone());
      }
   }

   @Override
   public Object clone() {
      try {
         FixedContainer container = (FixedContainer) super.clone();

         container.elements = new Vector(elements.size());
         container.shapes = new Vector(shapes.size());
         container.boxes = (Vector) boxes.clone();
         container.printBounds = (Vector) printBounds.clone();
         container.vlines = (Vector) vlines.clone();

         for(int i = 0; i < shapes.size(); i++) {
            container.shapes.addElement(
               ((PageLayout.Shape) shapes.get(i)).clone());
         }

         for(Object elem : elements) {
            BaseElement elem0 = (BaseElement) ((BaseElement) elem).clone();

            elem0.setParent(container);
            container.elements.addElement(elem0);
         }

         return container;
      }
      catch(CloneNotSupportedException ex) {
         LOG.error("Failed to clone container", ex);
      }

      return null;
   }

   // the fixed containers are only serialized when referenced from
   // SectionBandInfo, the style sheet and elements are not used
   protected transient WeakReference parent = null;
   private transient Vector elements = new Vector();
   // element bounds, Rectangle
   private Vector boxes = new Vector();
   private Vector printBounds = new Vector();
   private Vector shapes = new Vector(); // PageLayout.Shape
   private Vector vlines = new Vector();

   private static final Logger LOG =
      LoggerFactory.getLogger(FixedContainer.class);
}
