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
package inetsoft.uql.tabular;

import inetsoft.uql.AdditionalConnectionDataSource;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.ThreadContext;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * This is the base class for defining a tabular data source.
 *
 * @version 12.0, 11/15/2013
 * @author InetSoft Technology Corp
 */
public abstract class TabularDataSource<SELF extends TabularDataSource<SELF>>
   extends AdditionalConnectionDataSource<SELF>
{
   public TabularDataSource(String type, Class<SELF> selfClass) {
      super(type, selfClass);
   }

   @Override
   public String getDescription() {
      String description = super.getDescription();

      if(description != null && !description.isEmpty()) {
         return description;
      }

      try {
         String baseName = getClass().getPackage().getName() + ".Bundle";
         Locale locale = ThreadContext.getLocale();
         ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale);

         String key = getClass().getName() + ".description";
         description = bundle.getString(key);
      }
      catch(MissingResourceException ignore) {
         description = "";
      }

      return description;
   }

   /**
    * Get the data source connection parameters.
    */
   @Override
   public UserVariable[] getParameters() {
      List<UserVariable> list = TabularUtil.findVariables(this);
      return list.toArray(new UserVariable[0]);
   }

   /**
    * Check if type conversion is supported. If true, the column type can be changed on the
    * GUI. Each tabular data source is responsible for converting the data to the specified
    * type.
    */
   public boolean isTypeConversionSupported() {
      return false;
   }

   /**
    * Check validity of the data source. May or may not test the connection.
    * Throws an exception if it fails.
    */
   public void checkValidity() {
      // no op
   }

   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<ds_" + getType() + " ");
      writeAttributes(writer);
      writer.println(">");

      super.writeXML(writer);
      writeContents(writer);
      writer.println("</ds_" + getType() + ">");
   }

   @Override
   public final void parseXML(Element root) throws Exception {
      super.parseXML(root);
      parseAttributes(root);
      parseContents(root);
   }

   /**
    * Write the attributes of the XML tag.
    */
   @SuppressWarnings("UnusedParameters")
   protected void writeAttributes(PrintWriter writer) {
   }

   /**
    * Write the contents of the XML tag.
    */
   protected void writeContents(PrintWriter writer) {
   }

   /**
    * Parse the attributes of the XML tag.
    */
   @SuppressWarnings("UnusedParameters")
   protected void parseAttributes(Element tag) throws Exception {
   }

   /**
    * Parse the contents of the XML tag.
    */
   protected void parseContents(Element tag) throws Exception {
   }
}
