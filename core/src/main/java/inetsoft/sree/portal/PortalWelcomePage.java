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
package inetsoft.sree.portal;

import inetsoft.sree.internal.HttpXMLSerializable;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * A portal welcome page is a part of a portal. It defines the portal's welcome
 * page, and has a name for mapping.
 *
 * @version 8.5, 07/12/2006
 * @author InetSoft Technology Corp
 */
public class PortalWelcomePage implements Cloneable, HttpXMLSerializable {
   /**
    * No welcome page.
    */
   public static final int NONE = 0;
   /**
    * A URI welcome page.
    */
   public static final int URI = 1;
   /**
    * A local resource welcome page on server.
    */
   public static final int RESOURCE = 2;

   /**
    * Constructor.
    */
   public PortalWelcomePage() {
      this(0);
   }

   /**
    * Constructor.
    * @param type welcome page type.
    */
   public PortalWelcomePage(int type) {
      this(type, null);
   }

   /**
    * Constructor.
    * @param type welcome page type.
    * @param data welcome page data.
    */
   public PortalWelcomePage(int type, String data) {
      this.type = type;
      this.data = data;
   }

   /**
    * Get welcome page data.
    * @return the specified data of welcome page.
    */
   public String getData() {
      return data;
   }

   /**
    * Set welcome page data.
    * @param data the specified welcome page data.
    */
   public void setData(String data) {
      this.data = data;
   }

   /**
    * Get welcome page type.
    * @return welcome page type.
    */
   public int getType() {
      return type;
   }

   /**
    * Set welcome page type.
    * @param type the specified welcome page type.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get login banner
    * @return login banner.
    */
   public String getBanner() {
      return loginBanner;
   }

   /**
    * Set login banner.
    * @param loginBanner the specified login text.
    */
    public void setBanner(String loginBanner) {
      this.loginBanner = loginBanner;
    }

   /**
    * Get banner type
    * @return banner type.
    */
    public int getBannerType() {
      return bannerType;
    }

   /**
    * Set banner type.
    * @param bannerType the specified login banner type.
    */
    public void setBannerType(int bannerType) {
      this.bannerType = bannerType;
    }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<portalWelcomePage type=\"" + type + "\">");

      if(type != NONE && data != null) {
         writer.println("<data><![CDATA[" + byteEncode(data) + "]]></data>");
      }

      writer.println("<bannerType><![CDATA[" + bannerType + "]]></bannerType>");

      if(loginBanner != null) {
         writer.println("<loginBanner><![CDATA[" + byteEncode(loginBanner) +
            "]]></loginBanner>");
      }

      writer.println("</portalWelcomePage>");
   }

   /**
    * Method to parse an xml segment.
    * @param tag the specified xml element.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String typeStr = Tool.getAttribute(tag, "type");
      type = Integer.parseInt(typeStr);

      if(type != NONE) {
         Element elem = Tool.getChildNodeByTagName(tag, "data");
         data = byteDecode(Tool.getValue(elem));
      }

      Element elem = Tool.getChildNodeByTagName(tag, "loginBanner");
      loginBanner = byteDecode(Tool.getValue(elem));
      elem = Tool.getChildNodeByTagName(tag, "bannerType");
      String bannerTypeStr = Tool.getValue(elem);
      bannerType = bannerTypeStr == null ? 0 : Integer.parseInt(bannerTypeStr);
   }

   /**
    * Clone current welcome page.
    * @reurn cloned welcome page.
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

   private int type;
   private String data;
   private int bannerType;
   private String loginBanner;
   private transient boolean encoding = false;
}