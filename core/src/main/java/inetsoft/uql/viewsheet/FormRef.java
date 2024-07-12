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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Viewsheet Form Ref.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class FormRef extends ColumnRef {
   /**
    * Get the column option.
    */
   public ColumnOption getOption() {
      return option;
   }

   /**
    * Set the column option.
    */
   public void setOption(ColumnOption option) {
      this.option = option;
   }

   /**
    * Compare two column refs.
    * @param strict true to compare all properties of ColumnRef. Otherwise
    * only entity and attribute are compared.
    */
   @Override
   public boolean equals(Object obj, boolean strict) {
      if(!strict) {
         return super.equals(obj);
      }

      try {
         FormRef fref = (FormRef) obj;

         if(!super.equals(obj, strict)) {
            return false;
         }

         return Tool.equals(option, fref.option);
      }
      catch(ClassCastException ex) {
         return false;
      }
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      option.writeXML(writer);
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);

      Element node = Tool.getChildNodeByTagName(tag, "ColumnOption");

      if(node != null) {
         option = createColumnOption(node);
      }
   }

   /**
    * Create a <tt>DataRef</tt> from an xml element.
    * @param elem the specified xml element.
    * @return the created <tt>DataRef</tt>.
    */
   private ColumnOption createColumnOption(Element elem) throws Exception {
      String name = Tool.getAttribute(elem, "class");
      ColumnOption opt = (ColumnOption) Class.forName(name).newInstance();
      opt.parseXML(elem);

      return opt;
   }

   /**
    * Fix ColumnRef to FormRef.
    */
   public static FormRef toFormRef(DataRef ref) {
      if(ref instanceof FormRef) {
         return (FormRef) ref;
      }

      FormRef fref = new FormRef();
      fref.getOption().setForm(true);

      if(ref instanceof ColumnRef) {
         ColumnRef cref = (ColumnRef) ref;
         DataRef dref = cref.getDataRef();
         fref.setDataRef(dref);
         fref.copyAttributes(cref);
      }
      else {
         fref.setDataRef(ref);
      }

      return fref;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public FormRef clone() {
      try {
         FormRef col2 = (FormRef) super.clone();

         if(option != null) {
            col2.option = (ColumnOption) option.clone();
         }

         return col2;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone FormatRef", ex);
         return null;
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(FormRef.class);
   private ColumnOption option = new TextColumnOption();
}
