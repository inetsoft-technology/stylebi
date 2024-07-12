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
package inetsoft.sree.portal;

import inetsoft.sree.internal.HttpXMLSerializable;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * A portal tab is a part of a portal. It defines the portal's title & link uri.
 *
 * @version 8.5, 07/12/2006
 * @author InetSoft Technology Corp
 */
public class PortalTab implements Cloneable, HttpXMLSerializable {
   /**
    * Constructor.
    */
   public PortalTab() {
      this(null, null);
   }
   
   public PortalTab(String name, String uri) {
      this(name, uri, true);
   }

   /**
    * Constructor.
    * @param name the specified portal tab name.
    * @param uri the specified portal tab link uri.
    * @param visible the portal tab is visible if <code>true</code>.
    */
   public PortalTab(String name, String uri, boolean visible) {
      this(name, uri, visible, true);
   }

   /**
    * Constructor.
    * @param name the specified portal tab name.
    * @param uri the specified portal tab link uri.
    * @param visible the portal tab is visible if <code>true</code>.
    * @param editable the portal tab is editable if <code>true</code>.
    */
   public PortalTab(String name, String uri, boolean visible, boolean editable)
   {
      this(name, name, uri, visible, editable);
   }
   
   /**
    * Creates a new instance of <tt>PortalTab</tt>.
    *
    * @param name        the name of the tab.
    * @param label       the display label of the tab.
    * @param uri         the URI for the tab link.
    * @param visible     <tt>true</tt> if the tab is visible.
    * @param editable    <tt>true</tt> if the tab is editable.
    * 
    * @since 10.3
    */
   public PortalTab(String name, String label, String uri,
                    boolean visible, boolean editable)
   {
      this.name = name;
      this.label = label;
      this.uri = uri;
      this.visible = visible;
      this.editable = editable;
   }

   /**
    * Get portal tab name.
    * @return the portal tab name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set portal tab name.
    * @param name the specified portal tab name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the display label for this tab.
    * 
    * @return the label.
    */
   public String getLabel() {
      return label;
   }

   /**
    * Sets the display label for this tab.
    * 
    * @param label the label.
    */
   public void setLabel(String label) {
      this.label = label;
   }

   /**
    * Get portal tab link URI.
    * @return portal tab link URI.
    */
   public String getURI() {
      return uri;
   }

   /**
    * Set portal tab link URI.
    * @param uri the specified portal tab link URI.
    */
   public void setURI(String uri) {
      this.uri = uri;
   }

   /**
    * Check if the specified portal tab is visible.
    * @return <code>true</code> if visible, <code>false</code> otherwise.
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * Set portal tab visibility.
    * @param visible <code>true</code> if visible, <code>false</code> otherwise.
    */
   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   /**
    * Check if the specified portal tab is editable.
    * @return <code>true</code> if editable, <code>false</code> otherwise.
    */
   public boolean isEditable() {
      return editable;
   }

   /**
    * Set portal tab to editable or not.
    * @param editable <code>true</code> if editable,
    * <code>false</code> otherwise.
    */
   public void setEditable(boolean editable) {
      this.editable = editable;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<portalTab visible=\"" + visible +
                     "\" editable=\"" + editable + "\">");

      if(name != null) {
         writeCDATA(writer, "name", byteEncode(name));
      }
      
      if(label != null) {
         writeCDATA(writer, "label", byteEncode(label));
      }

      if(uri != null) {
         writeCDATA(writer, "uri", byteEncode(uri));
      }

      writer.println("</portalTab>");
   }

   /**
    * Method to parse an xml segment.
    * @param tag the specified xml element.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      visible = "true".equals(Tool.getAttribute(tag, "visible"));
      editable = "true".equals(Tool.getAttribute(tag, "editable"));

      Element node = Tool.getChildNodeByTagName(tag, "name");

      if(node != null) {
         name = byteDecode(Tool.getValue(node));
      }
      
      node = Tool.getChildNodeByTagName(tag, "label");

      if(node != null) {
         label = byteDecode(Tool.getValue(node));
      }

      if(label == null || "".equals(label)) {
         label = name;
      }

      node = Tool.getChildNodeByTagName(tag, "uri");

      if(node != null) {
         uri = byteDecode(Tool.getValue(node));
      }
   }

   /**
    * Clone current portal tab.
    * @return cloned portal tab.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(CloneNotSupportedException e) {
         return null;
      }
   }

   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source source string.
    * @return encoded string.
    */
   @Override
   public String byteEncode(String source) {
      return encoding ? Tool.byteEncode2(source) : source;
   }

   /**
    * Convert the encoded string to the original unencoded string.
    * @param encString a string encoded using the byteEncode method.
    * @return original string.
    */
   @Override
   public String byteDecode(String encString) {
      return encoding ? Tool.byteDecode(encString) : encString;
   }

   /**
    * Check if this object should encoded when writing.
    * @return <code>true</code> if should encoded, <code>false</code> otherwise.
    */
   @Override
   public boolean isEncoding() {
      return encoding;
   }

   /**
    * Set encoding flag.
    * @param encoding true to encode.
    */
   @Override
   public void setEncoding(boolean encoding) {
      this.encoding = encoding;
   }

   /**
    * Write CDATA.
    */
   private void writeCDATA(PrintWriter writer, String key, Object value) {
      writer.println("<" + key + "><![CDATA[" + value + "]]></" + key + ">");
   }

   private String name;
   private String label;
   private String uri;
   private boolean visible;
   private boolean editable;
   private transient boolean encoding = false;

}