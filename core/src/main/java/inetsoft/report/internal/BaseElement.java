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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.report.css.CSSProcessor;
import inetsoft.report.internal.info.ElementInfo;

import java.awt.*;
import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

/**
 * This is the base class of all document element classes. It contains
 * all common attributes. The attributes are copied from the document
 * when the element is created, so if the attributes change after the
 * element is created, the element is not affected.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class BaseElement implements ReportElement, Cacheable {
   /**
    * Create an element.
    * @param block true if this element should occupy one whole line.
    */
   public BaseElement(ReportSheet report, boolean block) {
      this.einfo = createElementInfo();

      this.report = new WeakReference(report);
      this.hindent = report.hindent;

      setBlock(block);
      setAlignment(report.alignment);
      setIndent(report.indent);
      setFont(report.font);
      setForeground(report.foreground);
      setBackground(report.background);
      setSpacing(report.spacing);

      id = report.getNextID(getType());

      applyStyle0();
   }

   /**
    * Create an element.
    */
   protected BaseElement() {
      einfo = createElementInfo();
      this.report = null;

      this.hindent = 0;

      setBlock(false);
      setAlignment(StyleConstants.LEFT);
      setIndent(0.0);
      setFont(null);
      setForeground(Color.black);
      setBackground(null);
      setSpacing(0);

      id = "";
   }

   /**
    * Apply CSS Style to this report element.
    */
   @Override
   public void applyStyle() {
      CSSProcessor cssProcessor = new CSSProcessor();
      cssProcessor.applyStyle(this, cssProcessor.getCSSDictionary(getReport()));
   }

   protected void applyStyle0() {
      applyStyle();
   }

   /**
    * CSS Type to be used when styling this element.  Intended to be overridden.
    */
   @Override
   public String getCSSType() {
      return null;
   }

   /**
    * Create a proper element info to save the attribute of this element.
    */
   protected ElementInfo createElementInfo() {
      return new ElementInfo();
   }

   @Override
   public ReportSheet getReport() {
      if(report == null) {
         return null;
      }

      return (ReportSheet) report.get();
   }

   /**
    * Set the report this element is contained in.
    */
   public void setReport(ReportSheet report) {
      if(report != getReport()) {
         // scripting env is tied to a report
         resetScript();
      }

      this.report = new WeakReference(report);
   }

   /**
    * Get the id of this element.
    */
   @Override
   public String getID() {
      return id;
   }

   /**
    * Set the id of this element.
    */
   @Override
   public void setID(String id) {
      this.id = id;
   }

   /**
    * Get the full name of this element. If this element is inside a bean,
    * the name contains the bean ID appended with the element ID. It
    * can be used together with element ID to uniquely identify an element
    * in a report (elements in a bean may have same ID as the element in
    * the report that uses the bean).
    */
   @Override
   public String getFullName() {
      // for memory conservation, a fullname is not stored if it is same
      // as the ID
      return (fullname == null) ? id : fullname;
   }

   /**
    * Set the full name of this element. If the beans are nested, the name of
    * the bean (bean element ID) are concatenated using a dot, e.g.
    * Bean1.Bean2.Text1.
    */
   @Override
   public void setFullName(String name) {
      if(name.equals(id)) {
         fullname = null;
      }
      else {
         fullname = name;
      }
   }

   /**
    * Alignment, H_LEFT, H_CENTER, H_RIGHT, and V_TOP, V_CENTER, V_RIGHT.
    */
   @Override
   public int getAlignment() {
      return einfo.getAlignment();
   }

   /**
    * Alignment, H_LEFT, H_CENTER, H_RIGHT, and V_TOP, V_CENTER, V_RIGHT.
    */
   @Override
   public void setAlignment(int alignment) {
      einfo.setAlignment(alignment);
   }

   /**
    * Indentation in inches.
    */
   @Override
   public double getIndent() {
      return einfo.getIndent();
   }

   /**
    * Indentation in inches.
    */
   @Override
   public void setIndent(double indent) {
      einfo.setIndent(indent);
   }

   /**
    * Hanging indentation in pixels.
    */
   public int getHindent() {
      return hindent;
   }

   /**
    * Hanging indentation in pixels.
    */
   public void setHindent(int hindent) {
      this.hindent = hindent;
   }

   /**
    * Current font.
    */
   @Override
   public Font getFont() {
      return einfo.getFont();
   }

   /**
    * Current font.
    */
   @Override
   public void setFont(Font font) {
      einfo.setFont(font);
   }

   /**
    * Foreground color.
    */
   @Override
   public Color getForeground() {
      return einfo.getForeground();
   }

   /**
    * Foreground color.
    */
   @Override
   public void setForeground(Color foreground) {
      einfo.setForeground(foreground);
   }

   /**
    * Background color.
    */
   @Override
   public Color getBackground() {
      return einfo.getBackground();
   }

   /**
    * Background color.
    */
   @Override
   public void setBackground(Color background) {
      einfo.setBackground(background);
   }

   /**
    * Line spacing in pixels.
    */
   @Override
   public int getSpacing() {
      return einfo.getSpacing();
   }

   /**
    * Line spacing in pixels.
    */
   @Override
   public void setSpacing(int spacing) {
      einfo.setSpacing(spacing);
   }

   /**
    * Check if this element is visible. Non-visible elements are
    * not printed.
    * @return true if element is visible.
    */
   @Override
   public boolean isVisible() {
      return einfo.isVisible();
   }

   /**
    * Set the visibility of this element.
    * @param vis false to hide an element.
    */
   @Override
   public void setVisible(boolean vis) {
      einfo.setVisible(vis);
   }

   /**
    * Check the visibility. Take into account of design mode.
    */
   public boolean checkVisible() {
      // visibility control ignored in designer
      getReport();
      return isVisible() || !(report == null || true);
   }

   /**
    * Check if this element should be kept on the same page as the next
    * element.
    */
   @Override
   public boolean isKeepWithNext() {
      return einfo.isKeepWithNext();
   }

   /**
    * Set the keep with next flag of this element.
    */
   @Override
   public void setKeepWithNext(boolean keep) {
      einfo.setKeepWithNext(keep);
   }

   /**
    * Check whether the element is inside a section.
    */
   public boolean isInSection() {
      return einfo.isInSection();
   }

   /**
    * Set if the element is inside a section.
    */
   public void setInSection(boolean section) {
      einfo.setInSection(section);
   }

   /**
    * Return true if this element should occupy one block. It does
    * not share a line with other elements.
    */
   public boolean isBlock() {
      return einfo.isBlock();
   }

   /**
    * Set whether this element is block or inline.
    */
   public void setBlock(boolean block) {
      einfo.setBlock(block);
   }

   /**
    * Return true if this element should start a new line.
    */
   public boolean isNewline() {
      return isBlock();
   }

   /**
    * Return true if this element is the last one on a line.
    */
   public boolean isLastOnLine() {
      return isBlock();
   }

   /**
    * Return true if this element is a flow control element with
    * no concrete visible contents.
    */
   public boolean isFlowControl() {
      return false;
   }

   /**
    * Return true if the element can be broken into segments.
    */
   public boolean isBreakable() {
      return false;
   }

   /**
    * Set the continuation. If continuation is true, the hanging indent
    * is not reset.
    * @param conti continuation.
    */
   public void setContinuation(boolean conti) {
      this.conti = conti;
   }

   /**
    * Check if continuation is true.
    * @return continuation setting.
    */
   public boolean isContinuation() {
      return conti;
   }

   /**
    * Check if this element will cause an area break.
    */
   public boolean isBreakArea() {
      return false;
   }

   /**
    * Return the size that is needed for this element.
    */
   @Override
   public Size getPreferredSize() {
      return new Size(0, 0);
   }

   /**
    * Rewind a paintable. This is call if part of a element is undone
    * (in section). The paintable should be inserted in the next print.
    */
   public void rewind(Paintable pt) {
   }

   /**
    * Print the element at the printHead location.
    * @return true if the element is not completed printed and
    * need to be called again.
    */
   public boolean print(StylePage pg, ReportSheet report) {
       return print0(pg, report);
   }

   /**
    * Print the element at the printHead location.
    * @return true if the element is not completed printed and
    * need to be called again.
    */
   protected boolean print0(StylePage pg, ReportSheet report) {
      einfo.setPrinted(true); // mark element as already printed

      if(!checkVisible()) {
         return false;
      }

      // @by larryl, indentation ignored in section
      if(!isInSection()) {
         float indw = (float) getIndent() * 72 + this.hindent;

         // indentation can not be outside of parea
         if(indw >= report.printBox.width - 10) {
            indw = Math.max(0, report.printBox.width - 10);
         }

         // handle indent
         if(report.printHead.x == 0) {
            report.printHead.x = indw;
         }
      }

      report.advanceLine = 0;

      // @by larryl, optimization, frame is only used at design time
       if(false) {
         frame = new Rectangle(report.printBox);
      }


      return false;
   }

   /**
    * Ignore the remaining print task if any.
    */
   public void reset() {
      resetPrint();
      resetScript();
   }

   /**
    * Reset onload script to reexecute it.
    */
   public void resetOnLoad() {
   }

   /**
    * Restart printing to the beginning of this element.
    */
   public void resetPrint() {
      frame = null;
      einfo.setPrinted(false);
   }

   /**
    * Cause the next call to print this element to execute the script again.
    */
   public void resetScript() {
   }

   /**
    * Get an property of this element. The properties are used to extend
    * the report elements and allows additional information to be attached
    * to the elements.
    * @param name property name.
    * @return property value.
    */
   @Override
   public final String getProperty(String name) {
      return einfo.getProperty(name);
   }

   /**
    * Check if contains a property.
    * @param name the specified property name.
    * @return true if contains the specified property.
    */
   /*
   public final boolean containsProperty(String name) {
      return einfo.containsProperty(name);
   }
   */

   /**
    * Set an property value.
    * @param name property name.
    * @param attr property value. Use null value to remove an
    * property.
    */
   @Override
   public void setProperty(String name, String attr) {
      einfo.setProperty(name, attr);
   }

   /**
    * Get the bounding frame of this element.
    */
   public Rectangle getFrame() {
      return frame;
   }

   /**
    * Set the frame of the element. This should not be called since frame
    * is set as part of the printing. This should only be used when an
    * element is used in a context where frame is know but the printing
    * has not happened.
    */
   public void setFrame(Rectangle frame) {
      this.frame = frame;
   }

   /**
    * Check if two elements are the same. The equality is defined as two
    * elements referencing to the element, or two elements having same IDs.
    * @param v other element.
    * @return true if the two elements are equal.
    */
   public boolean equals(Object v) {
      try {
         // only same type elements can be equal (class is singleton)
         return getClass() == v.getClass() &&
            getFullName().equals(((ReportElement) v).getFullName());
      }
      catch(Exception e) {
      }

      return this == v;
   }

   /**
    * Set the attributes of this element.
    */
   @Override
   public void setContext(ReportElement elem) {
      if(elem != null) {
         setAlignment(elem.getAlignment());
         setIndent(elem.getIndent());
         setFont(elem.getFont());
         setForeground(elem.getForeground());
         setBackground(elem.getBackground());
         setSpacing(elem.getSpacing());
         setKeepWithNext(elem.isKeepWithNext());
      }
   }

   /**
    * Set the parent element or container of this element. This is used for
    * elements generated from a composite element and section.
    */
   public void setParent(Object parent) {
      this.parent = parent;
      setInSection(parent instanceof SectionBand);
   }

   /**
    * Get the parent element of this element.
    */
   public Object getParent() {
      return parent;
   }

   /**
    * Set whether this element is printed in a single frame (non-flow or
    * band).
    */
   public void setNonFlow(boolean nonflow) {
      this.nonflow = nonflow;
   }

   /**
    * Calculate hash code using element info.
    */
   public int hashCode() {
      String full = getFullName();
      return (full == null) ? super.hashCode() : full.hashCode();
   }

   /**
    * Get original hash code.
    */
   public int addr() {
      return super.hashCode();
   }

   /**
    * Make a copy of this element.
    */
   @Override
   public Object clone() {
      try {
         // optimize, avoid compiling script in identical copies
         // @by joec, getReport() will return null if this code is running on
         // the client side
         BaseElement elem = (BaseElement) super.clone();

         elem.einfo = (ElementInfo) einfo.clone();
         elem.setUserObject(null); // binding info should be reinit'ed
         elem.setInSection(false);

         return elem;
      }
      catch(CloneNotSupportedException ex) { // impossible
      }

      return null;
   }

   /**
    * Return the element type.
    */
   @Override
   public String getType() {
      return type;
   }

   /**
    * Set the type of this element. This is only used internally.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Set an user object. The object must be serializable.
    */
   @Override
   public void setUserObject(Object obj) {
      userObj = obj;
   }

   /**
    * Get the user object.
    */
   @Override
   public Object getUserObject() {
      return userObj;
   }

   /**
    * Set the css class of the element.
    * @param elementClass the class name as defined in the css style sheet.
    */
   @Override
   public void setCSSClass(String elementClass) {
      einfo.setCSSClass(elementClass);
   }

   /**
    * Get the css class of the element
    */
   @Override
   public String getCSSClass() {
      return einfo.getCSSClass();
   }

   /**
    * Get element info of the element.
    */
   public ElementInfo getElementInfo() {
      return this.einfo;
   }

   /*
    * This method replaces the original serialization code in DefaultContxt.
    * The scope is for the package only. It's not called readObject so that a
    * full serialization is still possible when it's needed.
    */
   void readObjectMin(ObjectInputStream s)
      throws IOException, ClassNotFoundException
   {
      setFont((Font) s.readObject());
      setAlignment(s.readInt());
      setIndent(s.readDouble());
      setSpacing(s.readInt());
      setVisible(s.readBoolean());
      setKeepWithNext(s.readBoolean());
      setForeground((Color) s.readObject());
      setBackground((Color) s.readObject());
      setID((String) s.readObject());
      setType((String) s.readObject());
      setUserObject(s.readObject());
      setInSection(s.readBoolean());
   }

   /*
    * See comments for readMinObject
    */
   void writeObjectMin(ObjectOutputStream s) throws IOException {
      // @by larryl 2003-9-29, using hashcode does not work in persistent pages
      s.writeObject(getFont());
      // use get method to catch possible script changes to the value.
      s.writeInt(getAlignment());
      s.writeDouble(getIndent());
      s.writeInt(getSpacing());
      s.writeBoolean(isVisible());
      s.writeBoolean(isKeepWithNext());
      s.writeObject(getForeground());
      s.writeObject(getBackground());
      s.writeObject(getID());
      s.writeObject(getType());
      s.writeObject(getUserObject());
      s.writeBoolean(isInSection());
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
   }

   /**
    * Set zindex for the report element.
    * Just for the report element which is converted by a vs component applied
    * the vs printlayout to make sure the converted element can be paintabled
    * with same hierarchy as the source component in the target vs.
    */
   public void setZIndex(int zindex) {
      this.zindex = zindex;
   }

   /**
    * Return the zindex of the element.
    */
   public int getZIndex() {
      return zindex;
   }

   private int zindex = 0;
   protected ElementInfo einfo; // element info
   // script executed flag for this elem in section
   transient boolean sexecuted = false;

   private transient Reference report = null; // report container
   private String id = null; // unique id
   private int hindent; // Hanging indentation in pixels.
   private String fullname = null;
   private String type = "Base";

   private boolean conti; // true if continuation, hindent not reset
   private Object userObj; // user object

   transient Object parent; // parent in a composite element
   transient boolean nonflow = false; // used in printing only

   private transient Rectangle frame;  // the enclosing frame of this element
}
