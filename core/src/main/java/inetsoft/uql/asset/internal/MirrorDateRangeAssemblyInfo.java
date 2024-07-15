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
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Date;

/**
 * MirrroDateRangeAssemblyInfo stores basic mirror date range assembly
 * information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MirrorDateRangeAssemblyInfo extends WSAssemblyInfo
   implements MirrorAssemblyInfo
{
   /**
    * Constructor.
    */
   public MirrorDateRangeAssemblyInfo() {
      super();

      this.impl = new MirrorAssemblyImpl();
   }

   /**
    * Get the mirror assembly impl.
    * @return the mirror assembly impl.
    */
   @Override
   public MirrorAssemblyImpl getImpl() {
      return impl;
   }

   /**
    * Set the mirror assembly impl.
    * @param impl the specified mirror assembly impl.
    */
   @Override
   public void setImpl(MirrorAssemblyImpl impl) {
      this.impl = impl;
   }

   /**
    * Check if is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEditable() {
      return false;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.println("<mirrorAssembly>");
      impl.writeXML(writer);
      writer.println("</mirrorAssembly>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element mnode = Tool.getChildNodeByTagName(elem, "mirrorAssembly");

      if(mnode != null) {
         mnode = Tool.getFirstChildNode(mnode);
      }

      impl.parseXML(mnode);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         MirrorDateRangeAssemblyInfo info =
            (MirrorDateRangeAssemblyInfo) super.clone();
         info.impl = (MirrorAssemblyImpl) impl.clone();

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(super.toString());

      if(impl.isOuterMirror()) {
         sb.append("-[");
         AssetEntry entry = impl.getEntry();
         sb.append(entry.getDescription());

         if(impl.isOuterMirror()) {
            sb.append(" ");
            Date date = new Date(impl.getLastModified());
            sb.append(AssetUtil.getDateTimeFormat().format(date));
         }

         sb.append("]");
      }

      return sb.toString();
   }

   private MirrorAssemblyImpl impl;

   private static final Logger LOG =
      LoggerFactory.getLogger(MirrorDateRangeAssemblyInfo.class);
}
