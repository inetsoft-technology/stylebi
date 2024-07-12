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

import inetsoft.uql.asset.AssemblyRef;
import inetsoft.uql.viewsheet.internal.*;

import java.util.*;

/**
 * TextVSAssembly represents one text assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TextVSAssembly extends OutputVSAssembly {
   /**
    * Constructor.
    */
   public TextVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public TextVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new TextVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.TEXT_ASSET;
   }

   /**
    * Get the text value.
    * @return the text value of the text assembly.
    */
   public String getTextValue() {
      return getTextInfo().getTextValue();
   }

   /**
    * Set the text value.
    * @param value the specified text value.
    */
   public void setTextValue(String value) {
      getTextInfo().setTextValue(value);
   }

   /**
    * Get the text.
    * @return the text of the text assembly.
    */
   public String getText() {
      return getTextInfo().getText();
   }

   /**
    * Get the value.
    * @return the value.
    */
   @Override
   public Object getValue() {
      return getTextInfo().getValue();
   }

   /**
    * Set the value.
    * @param val the specified value.
    */
   @Override
   public void setValue(Object val) {
      getTextInfo().setValue(val);
   }

   /**
    * Get text assembly info.
    * @return the text assembly info.
    */
   protected TextVSAssemblyInfo getTextInfo() {
      return (TextVSAssemblyInfo) getInfo();
   }

   /**
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      AssemblyRef[] refs = super.getDependedWSAssemblies();

      if(refs.length == 0) {
         List<DynamicValue> dvalues = getViewDynamicValues(true);
         Set<AssemblyRef> set = new HashSet<>();
         VSUtil.getDynamicValueDependeds(dvalues, set, getViewsheet().getBaseWorksheet(), this);
         refs = set.toArray(new AssemblyRef[0]);
      }

      return refs;
   }

   /**
    * check show details of the table.
    */
   public boolean isShowDetail() {
      return showDetail;
   }

   /**
    * Set show details of the table.
    */
   public void setShowDetail(boolean showDetail) {
      this.showDetail = showDetail;
   }

   private boolean showDetail;
}
