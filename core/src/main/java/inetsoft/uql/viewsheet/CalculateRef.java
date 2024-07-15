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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.*;
import java.util.Enumeration;

/**
 * Calculate Ref.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public class CalculateRef extends ColumnRef {
   /**
    * Create an CalculateRef.
    */
   public CalculateRef() {
      this(true);
   }

   /**
    * Create an CalculateRef.
    */
   public CalculateRef(boolean baseOnDetail) {
      this.baseOnDetail = baseOnDetail;
   }

   /**
    * Get the ref type.
    */
   @Override
   public int getRefType() {
      int reftype = super.getRefType();

      if(!baseOnDetail) {
         reftype |= DataRef.AGG_CALC;
      }

      return reftype;
   }

   /**
    * Check the calculate ref based on detail value.
    * @return true if the calculate ref based on detail value.
    */
   public boolean isBaseOnDetail() {
      return baseOnDetail;
   }

   /**
    * Get a list of all attributes that are referenced by contained expression.
    * @return an Enumeration containing AttributeRef objects.
    */
   @Override
   public Enumeration getExpAttributes() {
      Enumeration e = super.getExpAttributes();

      if(e == null || !isBaseOnDetail()) {
         return e;
      }

      ExpressionRef ref0 = (ExpressionRef) AssetUtil.getBaseAttribute(this);

      return ref0.getCalcAttributes();
   }

   @Override
   public boolean equals(Object obj, boolean strict) {
      if(!strict) {
         return super.equals(obj);
      }

      try {
         if(!super.equals(obj, strict)) {
            return false;
         }

         CalculateRef cref = (CalculateRef) obj;
         return baseOnDetail == cref.baseOnDetail;
      }
      catch(ClassCastException ex) {
         return false;
      }
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(!baseOnDetail) {
         writer.print(" baseOnDetail=\"" + baseOnDetail + "\"");
      }

      if(fake) {
         writer.print(" fake=\"" + fake + "\"");
      }

      if(dcRuntime) {
         writer.print(" dcRuntime=\"" + dcRuntime + "\"");
      }
   }

   @Override
   protected void writeAttributes2(DataOutputStream dos) {
      try {
         super.writeAttributes2(dos);
         dos.writeBoolean(baseOnDetail);
      }
      catch (IOException e) {
         // do nothing
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String val = Tool.getAttribute(tag, "baseOnDetail");
      baseOnDetail = val == null || !"false".equals(val);
      fake = "true".equals(Tool.getAttribute(tag, "fake"));
      dcRuntime = "true".equals(Tool.getAttribute(tag, "dcRuntime"));
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      setExpressionProperty();
   }

   /**
    * Set the base data ref.
    * @param ref the specified data ref.
    */
   @Override
   public void setDataRef(DataRef ref) {
      super.setDataRef(ref);
      setExpressionProperty();
   }

   /**
    * Set the base data ref.
    */
   private void setExpressionProperty() {
      DataRef ref = getDataRef();

      if(ref instanceof ExpressionRef) {
         ExpressionRef ref2 = (ExpressionRef) getDataRef();

         // cube aggregate will have aggregate function,
         // not use calcfieldformula to calculate
         if(!baseOnDetail) {
            ref2.setVirtual(true);
            ref2.setOnAggregate(true);
         }

         // make sure data type of expression matches user defined on gui
         ref2.setDataType(getDataType());
      }
   }

   /**
    * Set the name of the field.
    * @param name the name of the field
    */
   public void setName(String name) {
      DataRef ref = getDataRef();

      if(ref instanceof ExpressionRef) {
         ((ExpressionRef) ref).setName(name);
         cname = null;
         chash = Integer.MIN_VALUE;
      }
   }

   /**
    * Check if the formula field is a fake field.
    */
   public boolean isFake() {
      return fake;
   }

   /**
    * Set the formula field is a fake formula or not.
    */
   public void setFake(boolean fake) {
      this.fake = fake;
   }

   /**
    * Check if the field is created by date comparison.
    */
   public boolean isDcRuntime() {
      return dcRuntime;
   }

   /**
    * Set the field is created by date comparison.
    */
   public void setDcRuntime(boolean dcRuntime) {
      this.dcRuntime = dcRuntime;
   }

   private boolean baseOnDetail = true;
   private boolean fake = false;
   private boolean dcRuntime = false;
}
