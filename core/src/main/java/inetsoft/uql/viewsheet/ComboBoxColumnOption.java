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

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.ListValueInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * ComboBoxOption stores column options for FormRef.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class ComboBoxColumnOption extends ColumnOption implements ListValueInfo
{
   /**
    * Constructor.
    */
   public ComboBoxColumnOption() {
   }

   /**
    * Constructor.
    */
   public ComboBoxColumnOption(int stype, String dtype,
                               String msg, boolean form)
   {
      this.stype = stype;
      this.dtype = dtype;
      this.msg = msg;
      this.form = form;
   }

   /**
    * Get column option type.
    */
   @Override
   public String getType() {
      return ColumnOption.COMBOBOX;
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" sourceType=\"" + stype + "\"");
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(data != null) {
         data.writeXML(writer);
      }

      if(binding != null) {
         binding.writeXML(writer);
      }
   }

   @Override
   protected void parseAttributes(Element elem) throws Exception{
      super.parseAttributes(elem);
      this.stype = Integer.parseInt(Tool.getAttribute(elem, "sourceType"));
   }

   @Override
   protected void parseContents(Element elem) throws Exception {
      Element dnode = Tool.getChildNodeByTagName(elem, "listData");

      if(dnode != null) {
         data = new ListData();
         data.parseXML(dnode);
      }

      Element bnode = Tool.getChildNodeByTagName(elem, "bindingInfo");

      if(bnode != null) {
         binding = new ListBindingInfo();
         binding.parseXML(bnode);
      }
   }

   /**
    * Get the list data.
    * @return the list data of this assembly info.
    */
   @Override
   public ListData getListData() {
      return data;
   }

   /**
    * Set the list data to this assembly info.
    * @param data the specified list data.
    */
   @Override
   public void setListData(ListData data) {
      this.data = data;
   }

   /**
    * Set the source type to this assembly info.
    * @param stype the specified source type.
    */
   @Override
   public void setSourceType(int stype) {
      this.stype = stype;
   }

   /**
    * Get the source type to this assembly info.
    * @return the type of the data source.
    */
   @Override
   public int getSourceType() {
      return stype;
   }

   /**
    * Get the binding info.
    * @return the binding info of this assembly info.
    */
   @Override
   public BindingInfo getBindingInfo() {
      return binding;
   }

   /**
    * Set the list binding info.
    * @param binding the list binding info.
    */
   public void setListBindingInfo(ListBindingInfo binding) {
      this.binding = binding;
   }

   /**
    * Get the list binding info.
    * @return the list binding info of this assembly info.
    */
   @Override
   public ListBindingInfo getListBindingInfo() {
      return binding;
   }

   /**
    * Get the target data type.
    * @return the target data type of this assembly info.
    */
   @Override
   public String getDataType() {
      ListBindingInfo info = getListBindingInfo();
      String dtype = XSchema.STRING;

      if(info != null && !info.isEmpty()) {
         dtype = info.getDataType();
      }

      ListData data = getListData();

      if(XSchema.STRING.equals(dtype) && data != null && !data.isEmpty()) {
         dtype = data.getDataType();
      }

      if(XSchema.STRING.equals(dtype) && this.dtype != null) {
         return this.dtype;
      }

      return dtype;
   }

   /**
    * Set the data type to this assembly info.
    * @param dtype the specified data type.
    */
   @Override
   public void setDataType(String dtype) {
      this.dtype = dtype == null ? XSchema.STRING : dtype;
      data.setDataType(dtype);
   }

   /**
    * Clone this object.
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         ComboBoxColumnOption option = (ComboBoxColumnOption)super.clone();

         if(binding != null) {
            option.binding = (ListBindingInfo)binding.clone();
         }

         if(data != null) {
            option.data = (ListData) data.clone();
         }

         return option;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ComboBoxColumnOption", ex);
      }

      return null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof ComboBoxColumnOption) || !super.equals(obj)) {
         return false;
      }

      ComboBoxColumnOption opt = (ComboBoxColumnOption) obj;
      return stype == opt.stype && Tool.equals(dtype, opt.dtype) &&
             Tool.equals(data, opt.data) && Tool.equals(binding, opt.binding);
   }

   private int stype;
   private ListData data;
   private ListBindingInfo binding;
   private String dtype;

   private static final Logger LOG =
      LoggerFactory.getLogger(ComboBoxColumnOption.class);
}
