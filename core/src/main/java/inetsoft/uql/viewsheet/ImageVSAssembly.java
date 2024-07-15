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

import inetsoft.graph.internal.DimensionD;
import inetsoft.uql.asset.AssemblyRef;
import inetsoft.uql.viewsheet.internal.*;

import java.util.*;

/**
 * ImageVSAssembly represents one image assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ImageVSAssembly extends OutputVSAssembly
   implements LockableVSAssembly
{
   /**
    * Constructor.
    */
   public ImageVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public ImageVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new ImageVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.IMAGE_ASSET;
   }

   /**
    * Get the image.
    * @return the image of the image assembly.
    */
   public String getImage() {
      return getImageInfo().getImage();
   }

   /**
    * Get the image value.
    * @return the image value of the image assembly.
    */
   public String getImageValue() {
      return getImageInfo().getImageValue();
   }

   /**
    * If it is a dynamic image.
    * @return if it is a dynamic image.
    */
   public boolean isDynamic() {
      return getImageInfo().isDynamic();
   }

   /**
    * Set if it is a dynamic image.
    * @param dynamic if it is a dynamic image.
    */
   public void setDynamic(boolean dynamic) {
      getImageInfo().setDynamic(dynamic);
   }

   /**
    * Set image assembly value.
    * @param val the image value.
    */
   @Override
   public void setValue(Object val) {
      // fixed bug1210057426875, setValue() should not use val.toString(), or
      // the val's type will be changed
      getImageInfo().setValue((val != null) ? val : "");
   }

   /**
    * Get image assembly value.
    * @return the image assembly value.
    */
   @Override
   public Object getValue() {
      return getImageInfo().getValue();
   }

   /**
    * Get image assembly info.
    * @return the image assembly info.
    */
   protected ImageVSAssemblyInfo getImageInfo() {
      return (ImageVSAssemblyInfo) getInfo();
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
    * Return stack order.
    */
   @Override
   public int getZIndex() {
      return getImageInfo().getZIndex();
   }

   /**
    * Check if supports tab.
    * @return <tt>true</tt> if this assembly may be laid as one sub component
    * of a tab, <tt>false</tt> otherwise.
    */
   @Override
   public boolean supportsTab() {
      return true;
   }

   /**
    * Check if supports container.
    * @return <tt>true</tt> if this assembly may be laid as one sub component
    * of a container, <tt>false</tt> otherwise.
    */
   @Override
   public boolean supportsContainer() {
      return false;
   }

   /**
    * Get scaling ratio.
    */
   public DimensionD getScalingRatio() {
      return getImageInfo().getScalingRatio();
   }

   /**
    * Set scaling ratio.
    */
   public void setScalingRatio(DimensionD ratio) {
      getImageInfo().setScalingRatio(ratio);
   }

   /**
    * if the object is locked ,then it can not be drag.
    */
   @Override
   public Boolean islocked() {
      return getImageInfo().getLocked();
   }

   /*
    * if the object is locked ,then it can not be drag.
    * this function is to set the value of whether locked.
    * @param locked,lock the object when the value is true.
    */
   @Override
   public void setLocked(Boolean locked) {
      getImageInfo().setLocked(locked);
   }
}
