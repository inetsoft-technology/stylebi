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
package inetsoft.uql.asset;

import inetsoft.uql.asset.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.DependencyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.util.*;

/**
 * Abstract worksheet assembly, implements most methods defined in WSAssembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractWSAssembly extends AbstractAssembly implements WSAssembly {
   /**
    * Create an assembly from an xml element.
    * @param elem the specified xml element.
    * @param ws the specified worksheet.
    * @return the created assembly.
    */
   public static WSAssembly createWSAssembly(Element elem, Worksheet ws) throws Exception {
      String cls = Tool.getAttribute(elem, "class");
      int idx = cls.indexOf(".");
      cls = idx < 0 ? "inetsoft.uql.asset." + cls : cls;
      WSAssembly assembly = (WSAssembly) Class.forName(cls).newInstance();
      assembly.parseXML(elem);
      assembly.setWorksheet(ws);

      return assembly;
   }

   /**
    * Constructor.
    */
   public AbstractWSAssembly() {
      super();

      info = createInfo();
      info.setClassName(getClass().getName());
   }

   /**
    * Constructor.
    */
   public AbstractWSAssembly(Worksheet ws, String name) {
      this();

      setName(name);
      setOldName(getName());
      this.ws = ws;
   }

   /**
    * Get the class name.
    */
   @Override
   protected String getClassName(boolean compact) {
      String name = getClass().getName();

      if(compact) {
         int idx = name.lastIndexOf(".");
         name = name.substring(idx + 1);
      }

      return name;
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   protected WSAssemblyInfo createInfo() {
      return new WSAssemblyInfo();
   }

   /**
    * Get the assembly info.
    * @return the associated assembly info.
    */
   @Override
   public AssemblyInfo getInfo() {
      return info;
   }

   /**
    * Get the name.
    * @return the name of the assembly.
    */
   @Override
   public String getName() {
      return info.getName();
   }

   /**
    * Set the name.
    * @param name the specified name.
    */
   protected void setName(String name) {
      info.setName(name);
   }

   /**
    * Get the worksheet assembly info.
    * @return the worksheet assembly info.
    */
   @Override
   public WSAssemblyInfo getWSAssemblyInfo() {
      return info;
   }

   /**
    * Check if is composed.
    * @return <tt>true</tt> if composed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isComposed() {
      return false;
   }

   /**
    * Get the description.
    * @return the description of the assembly.
    */
   @Override
   public String getDescription() {
      return info.getDescription();
   }

   /**
    * Set the description.
    * @param desc the specified description.
    */
   @Override
   public void setDescription(String desc) {
      info.setDescription(desc);
   }

   /**
    * Check if the assembly is iconized.
    * @return <tt>true</tt> if iconized, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isIconized() {
      return info.isIconized();
   }

   /**
    * Set iconized option.
    * @param iconized <tt>true</tt> indicated iconized.
    */
   @Override
   public void setIconized(boolean iconized) {
      info.setIconized(iconized);
   }

   /**
    * Check if the assembly is outer.
    * @return <tt>true</tt> if outer, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isOuter() {
      return info.isOuter();
   }

   /**
    * Set outer option.
    * @param outer <tt>true</tt> indicated outer.
    */
   @Override
   public void setOuter(boolean outer) {
      info.setOuter(outer);
   }

   /**
    * Check if the assembly is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      // do nothing
   }

   /**
    * Check if the dependency is valid.
    */
   @Override
   public void checkDependency() throws InvalidDependencyException {
      Assembly[] assemblies = AssetUtil.getDependedAssemblies(ws, this, false);

      for(int i = 0; i < assemblies.length; i++) {
         if(assemblies[i] == this) {
            InvalidDependencyException ex =
               new InvalidDependencyException(Catalog.getCatalog().getString(
                  "common.dependencyCycle"));
            throw ex;
         }
      }
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      // no-op
   }

   protected void addToDependencyTypes(
      Map<String, Set<DependencyType>> dependeds, Set<AssemblyRef> set,
      DependencyType type)
   {
      for(AssemblyRef assemblyRef : set) {
         addToDependencyTypes(dependeds, assemblyRef.getEntry().getAbsoluteName(), type);
      }
   }

   protected void addToDependencyTypes(
      Map<String, Set<DependencyType>> dependeds, String assemblyName,
      DependencyType type)
   {
      Set<DependencyType> dependencyTypes =
         dependeds.computeIfAbsent(assemblyName, (ref) -> new HashSet<>());
      dependencyTypes.add(type);
   }

   /**
    * Get pixel offset of the assembly.
    * @return the pixel offset of the assembly.
    */
   public Point getPixelOffset() {
      return getInfo().getPixelOffset();
   }

   /**
    * Set pixel offset of the assembly
    * @param pixelOffset the pixel offset of the assembly.
    */
   public void setPixelOffset(Point pixelOffset) {
      getInfo().setPixelOffset(pixelOffset);
   }

   /**
    * Get pixel size of the assembly.
    * @return the pixel size of the assembly.
    */
   public Dimension getPixelSize() {
      Dimension msize = getMinimumSize();
      Dimension size = info.getPixelSize();

      if(msize.width > size.width || msize.height > size.height) {
         int width = Math.max(msize.width, size.width);
         int height = Math.max(msize.height, size.height);
         size = new Dimension(width, height);
      }

      if(isIconized()) {
         return new Dimension(Math.min(size.width, 2 * AssetUtil.defw), AssetUtil.defh);
      }

      return size;
   }

   /**
    * Set pixel size of the assembly.
    * @param pixelSize the pixel size of the assembly.
    */
   public void setPixelSize(Dimension pixelSize) {
      Dimension msize = getMinimumSize();
      int width = Math.max(msize.width, pixelSize.width);
      int height = Math.max(msize.height, pixelSize.height);
      info.setPixelSize(new Dimension(width, height));
   }

   /**
    * Check if is a date condition assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isDateCondition() {
      return getAssemblyType() == Worksheet.DATE_RANGE_ASSET;
   }

   /**
    * Check if is a condition assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isCondition() {
      return getAssemblyType() == Worksheet.CONDITION_ASSET;
   }

   /**
    * Check if is a named group assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNamedGroup() {
      return getAssemblyType() == Worksheet.NAMED_GROUP_ASSET;
   }

   /**
    * Check if is a variable assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isVariable() {
      return getAssemblyType() == Worksheet.VARIABLE_ASSET;
   }

   /**
    * Check if is a table assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isTable() {
      return getAssemblyType() == Worksheet.TABLE_ASSET;
   }

   /**
    * Set the worksheet.
    * @param ws the specified worksheet.
    */
   @Override
   public void setWorksheet(Worksheet ws) {
      this.ws = ws;
   }

   /**
    * Get the worksheet.
    * @return the worksheet of the assembly.
    */
   @Override
   public Worksheet getWorksheet() {
      return ws;
   }

   /**
    * Set the visible flag.
    * @param visible <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   @Override
   public void setVisible(boolean visible) {
      info.setVisible(visible);
   }

   /**
    * Reset the assembly.
    */
   @Override
   public void reset() {
      // do nothing
   }

   /**
    * Copy the assembly.
    * @param name the specified new assembly name.
    * @return the copied assembly.
    */
   @Override
   public WSAssembly copyAssembly(String name) {
      try {
         AbstractWSAssembly assembly = (AbstractWSAssembly) clone();
         assembly.setName(name);
         assembly.setOuter(false);
         return assembly;
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         AbstractWSAssembly assembly = (AbstractWSAssembly) super.clone();
         assembly.info = (WSAssemblyInfo) info.clone();
         assembly.oldName = oldName;

         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Get the sheet container.
    * @return the sheet container.
    */
   @Override
   public AbstractSheet getSheet() {
      return ws;
   }

   /**
    * Update the assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean update() {
      return true;
   }

   @Override
   public void dependencyChanged(String depname) {
      update();
   }

   @Override
   public void pasted() {
      // ignored
   }

   /**
    * Set old name.
    */
   public void setOldName(String name) {
      this.oldName = name;
   }

   /**
    * Get old name.
    */
   public String getOldName() {
      return this.oldName;
   }

   protected Worksheet ws;
   protected WSAssemblyInfo info;
   private String oldName = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractWSAssembly.class);
}
