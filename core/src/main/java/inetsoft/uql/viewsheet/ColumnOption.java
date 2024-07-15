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
package inetsoft.uql.viewsheet;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * ColumnOption stores column options for FormRef.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public abstract class ColumnOption implements XMLSerializable, Cloneable {
   /**
    * Combobox ColumnOption.
    */
   public static final String COMBOBOX = "ComboBox";

   /**
    * Text ColumnOption.
    */
   public static final String TEXT = "Text";

   /**
    * Date ColumnOption.
    */
   public static final String DATE = "Date";

   /**
    * Integer ColumnOption.
    */
   public static final String INTEGER = "Integer";

   /**
    * Float ColumnOption.
    */
   public static final String FLOAT = "Float";

   /**
    * Boolean ColumnOption.
    */
   public static final String BOOLEAN = "Boolean";

   /**
    * Password ColumnOption.
    */
   public static final String PASSWORD = "Password";

   /**
    * Get column option type.
    */
   public abstract String getType();

   /**
    * Get message if value doesn't meet the constraint defined for this column.
    */
   public String getErrorMessage() {
      return getMessage();
   }

   /**
    * Get message if data doesn't meet the constraint defined for this column.
    */
   public String getErrorMessage(Object data) {
      if(getErrorMessage() == null || "".equals(getErrorMessage()) || data == null) {
         return getErrorMessage();
      }

      return getErrorMessage().replace("{0}", data.toString());
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<ColumnOption class=\"" + getClass().getName() + "\"");
      writeAttributes(writer);
      writer.println(">");

      writeContents(writer);
      writer.println("</ColumnOption>");
   }

   protected void writeAttributes(PrintWriter writer) {
      writer.print(" form=\"" + form + "\"");
   }

   protected void writeContents(PrintWriter writer) {
      if(msg != null) {
         writer.println("<message><![CDATA[" + Tool.encodeCDATA(msg) +
            "]]></message>");
      }
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public final void parseXML(Element tag) throws Exception {
      parseAttributes(tag);
      parseContents(tag);
   }

   protected void parseAttributes(Element tag) throws Exception {
      form = Tool.getAttribute(tag, "form").equals("true");
      // for bc
      msg = Tool.getAttribute(tag, "msg");
   }

   protected void parseContents(Element tag) throws Exception {
      msg = Tool.decodeCDATA(Tool.getChildValueByTagName(tag, "message"));
   }

   /**
    * Get error meassge.
    */
   public String getMessage() {
      return msg;
   }

   /**
    * Check if the column option available.
    * @return <tt>true</tt> if available, <tt>false</tt> otherwise.
    */
   public boolean isForm() {
      return form;
   }

   /**
    * Set form flag.
    */
   public void setForm(boolean form) {
      this.form = form;
   }

   /**
    * Check whether the value is invalid by the range setting.
    */
   public boolean validate(Object val) {
      return true;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object to compare.
    * @return <tt>true</tt> if equals the specified object,
    * <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ColumnOption)) {
         return false;
      }

      ColumnOption columnOp = (ColumnOption) obj;

      return getType().equals(columnOp.getType()) &&
             Tool.equals(msg, columnOp.msg) && form == columnOp.form;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ColumnOption", ex);
         return null;
      }
   }

   protected String msg;
   protected boolean form = false;
   private static final Logger LOG =
      LoggerFactory.getLogger(ColumnOption.class);
}
