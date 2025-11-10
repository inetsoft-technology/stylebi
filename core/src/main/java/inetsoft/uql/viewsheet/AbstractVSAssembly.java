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

import inetsoft.report.TableDataPath;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * Abstract viewsheet assembly, implements most methods in VSAssembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractVSAssembly extends AbstractAssembly implements VSAssembly, FloatableVSAssembly {
   /**
    * Create an assembly from an xml element.
    * @param elem the specified xml element.
    * @param vs the specified viewsheet.
    * @return the created assembly.
    */
   public static VSAssembly createVSAssembly(Element elem, Viewsheet vs)
      throws Exception
   {
      String cls = Tool.getAttribute(elem, "class");
      VSAssembly assembly = null;

      try {
         assembly = (VSAssembly) Class.forName(cls).newInstance();
         assembly.setViewsheet(vs);
         assembly.parseXML(elem);
      }
      catch(InstantiationException ex) {
         // sometimes CalcTableVSAQuery.CrosstabVSAssembly may be not removed
         if(!ex.getMessage().contains("CalcTableVSAQuery")) {
            throw ex;
         }
      }

      return assembly;
   }

   /**
    * Constructor.
    */
   public AbstractVSAssembly() {
      super();

      info = createInfo();
      info.setClassName(getClass().getName());
   }

   /**
    * Constructor.
    */
   public AbstractVSAssembly(Viewsheet vs, String name) {
      this();

      setName(name);
      setViewsheet(vs);
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
    * Create assembly info.
    * @return the associated assembly info.
    */
   protected VSAssemblyInfo createInfo() {
      return new VSAssemblyInfo();
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
    * Get the viewsheet assembly info.
    * @return the viewsheet assembly info.
    */
   @Override
   public VSAssemblyInfo getVSAssemblyInfo() {
      return (VSAssemblyInfo) getInfo();
   }

   /**
    * Get the format info.
    * @return the format info of this assembly info.
    */
   @Override
   public FormatInfo getFormatInfo() {
      return getVSAssemblyInfo().getFormatInfo();
   }

   /**
    * Set the format info to this assembly info.
    * @param info the specified format info.
    */
   @Override
   public void setFormatInfo(FormatInfo info) {
      getVSAssemblyInfo().setFormatInfo(info);
   }

   /**
    * Get the minimum size.
    * @return the minimum size of the assembly.
    */
   @Override
   public Dimension getMinimumSize() {
      return new Dimension(1, 1);
   }

   /**
    * Check if is visible.
    * @return <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isVisible() {
      if(isEmbedded() && !isPrimary()) {
         return false;
      }

      VSAssembly container = getContainer();
      boolean cvis = true;
      Viewsheet vs = getViewsheet();
      boolean pvis = vs == null || vs.isVisible();
      boolean print = vs != null && vs.isPrintMode();

      if(container != null && (!container.getVSAssemblyInfo().isVisible(print) ||
         !container.isVisible()))
      {
         cvis = false;
      }

      return info.isVisible(print) && cvis && pvis;
   }

   /**
    * Check if the VSAssembly is resizable.
    * @return <tt>true</tt> of resizable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isResizable() {
      return info.isResizable();
   }

   /**
    * Check if the dependency is valid.
    */
   @Override
   public void checkDependency() throws InvalidDependencyException {
      Assembly[] assemblies = AssetUtil.getDependedAssemblies(getViewsheet(),
                                                              this, false, false, true);

      for(Assembly assembly : assemblies) {
         if(assembly == this && !allowsCycle()) {
            throw new InvalidDependencyException(Catalog.getCatalog().getString(
               "common.dependencyCycle"));
         }
      }
   }

   /**
    * Check if allows cycle.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean allowsCycle() {
      return false;
   }

   /**
    * Set the viewsheet.
    * @param vs the specified viewsheet.
    */
   @Override
   public void setViewsheet(Viewsheet vs) {
      info.setViewsheet(vs);
   }

   /**
    * Get the viewsheet.
    * @return the viewsheet of the assembly.
    */
   @Override
   public Viewsheet getViewsheet() {
      return info.getViewsheet();
   }

   /**
    * Check if is a primary assembly.
    * @return <tt>true</tt> if primary, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isPrimary() {
      return info.isPrimary();
   }

   /**
    * Set whether is a primary assembly.
    * @param primary <tt>true</tt> if primary, <tt>false</tt> otherwise.
    */
   @Override
   public void setPrimary(boolean primary) {
      info.setPrimary(primary);
   }

   /**
    * Get the absolute name of this assembly.
    * @return the absolute name of this assembly.
    */
   @Override
   public String getAbsoluteName() {
      return info.getAbsoluteName();
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      VSAssembly assembly = (VSAssembly) obj;
      return getAbsoluteName().equals(assembly.getAbsoluteName());
   }

   /**
    * Check if this assembly is embedded.
    * @return <tt>true</tt> if embedded, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmbedded() {
      return info.isEmbedded();
   }

   /**
    * Check if the assembly is enabled.
    * @return <tt>true</tt> if enabled, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEnabled() {
      Assembly cass = getContainer();

      if(cass instanceof ContainerVSAssembly) {
         return info.isEnabled() && ((ContainerVSAssembly) cass).isEnabled();
      }

      return info.isEnabled();
   }

   /**
    * Copy the assembly.
    * @param name the specified new assembly name.
    * @return the copied assembly.
    */
   @Override
   public VSAssembly copyAssembly(String name) {
      try {
         AbstractVSAssembly assembly = clone();
         assembly.setName(name);
         return assembly;
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * Get the container.
    * @return the container if existing & visible, <tt>null</tt> otherwise.
    */
   @Override
   public VSAssembly getContainer() {
      Assembly[] arr = getViewsheet().getAssemblies();

      for(int i = 0; i < arr.length; i++) {
         Assembly tassembly = arr[i];

         if(tassembly.getAssemblyType() != Viewsheet.TAB_ASSET &&
            tassembly.getAssemblyType() != Viewsheet.GROUPCONTAINER_ASSET &&
            tassembly.getAssemblyType() != Viewsheet.CURRENTSELECTION_ASSET)
         {
            continue;
         }

         ContainerVSAssembly container = (ContainerVSAssembly) tassembly;

         if(container.containsAssembly(getName())) {
            return container;
         }
      }

      return null;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public AbstractVSAssembly clone() {
      try {
         AbstractVSAssembly assembly = (AbstractVSAssembly) super.clone();
         assembly.info = info.clone();

         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone AbstractVSAssembly", ex);
         return null;
      }
   }

   /**
    * Get the worksheet.
    * @return the worksheet if any.
    */
   @Override
   public Worksheet getWorksheet() {
      return getViewsheet().getBaseWorksheet();
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      List<DynamicValue> list = getDynamicValues();
      VSUtil.getDynamicValueDependeds(list, set, getViewsheet(), this);
      AssemblyRef[] arr = getDependedWSAssemblies();

      set.addAll(Arrays.asList(arr));
   }

   /**
    * Get the assemblies depended on by its output values.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getOutputDependeds(Set<AssemblyRef> set) {
      List<DynamicValue> list = getOutputDynamicValues();
      VSUtil.getDynamicValueDependeds(list, set, getViewsheet(), this);
      Assembly cass = getContainer();

      if(cass != null) {
         set.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA, cass.getAssemblyEntry()));
      }
   }

   /**
    * Get the view assemblies depended on.
    * @param set the set stores the presentation assemblies depended on.
    * @param self <tt>true</tt> to include self, <tt>false</tt> otherwise.
    */
   @Override
   public void getViewDependeds(Set<AssemblyRef> set, boolean self) {
      List<DynamicValue> list = getViewDynamicValues(true);
      VSUtil.getDynamicValueDependeds(list, set, getViewsheet(), this);

      if(getScript() != null) {
         VSUtil.getReferencedAssets(getScript(), set, getViewsheet(), this);
      }

      // self view depends on self output data
      if(self) {
         set.add(new AssemblyRef(AssemblyRef.INPUT_DATA, getAssemblyEntry()));
      }
   }

   /**
    * Get assemblies depended on by the javascript attached to this assembly.
    * @param set the set stores the assemblies depended on.
    */
   public void getScriptReferencedAssets(Set<AssemblyRef> set) {
      if(getVSAssemblyInfo().isScriptEnabled()) {
         VSUtil.getReferencedAssets(getScript(), set, getViewsheet(), this);
      }
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      VSAssemblyInfo vinfo = getVSAssemblyInfo();
      return vinfo.getDynamicValues();
   }

   /**
    * Get the dynamic property values for output options.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getOutputDynamicValues() {
      VSAssemblyInfo vinfo = getVSAssemblyInfo();
      return vinfo.getOutputDynamicValues();
   }

   /**
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      VSAssemblyInfo vinfo = getVSAssemblyInfo();
      return vinfo.getViewDynamicValues(all);
   }

   /**
    * Get the hyperlink dynamic values.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getHyperlinkDynamicValues() {
      VSAssemblyInfo vinfo = getVSAssemblyInfo();
      return vinfo.getHyperlinkDynamicValues();
   }

   /**
    * Get the javascript attached to viewsheet assembly.
    * @return he javascript code.
    */
   public String getScript() {
      return getVSAssemblyInfo().getScript();
   }

   /**
    * Check if contains script.
    */
   @Override
   public boolean containsScript() {
      String script = getVSAssemblyInfo().getScript();
      return getVSAssemblyInfo().isScriptEnabled() &&
             script != null && script.length() > 0;
   }

   /**
    * Set the javascript attached to viewsheet assembly.
    * @param script the specified javascript code piece.
    */
   public void setScript(String script) {
      getVSAssemblyInfo().setScript(script);
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      info.renameDepended(oname, nname, getViewsheet());

      if(this instanceof DynamicBindableVSAssembly) {
         syncConditionList(oname, nname, ((DynamicBindableVSAssembly) this).getPreConditionList());
      }
   }

   protected void syncConditionList(String oname, String nname, ConditionList conds) {
      if(conds == null || conds.isEmpty()) {
         return;
      }

      for(int i = 0; i < conds.getSize(); i += 2) {
         XCondition xcond = conds.getXCondition(i);

         if(xcond instanceof Condition) {
            VSUtil.renameConditionDependeds(oname, nname, (Condition) xcond);
         }
      }
   }

   /**
    * Set the assembly info.
    * @param info the specified viewsheet assembly info.
    * @return the hint to reset view, input data or output data.
    */
   @Override
   public int setVSAssemblyInfo(VSAssemblyInfo info) {
      int hint = this.info.copyInfo(info);

      if(hint != NONE_CHANGED) {
         getViewsheet().resetWS();
      }

      return hint;
   }

   /**
    * Update the assembly to fill in runtime value.
    * @param columns the specified column selection.
    */
   @Override
   public void update(ColumnSelection columns) throws Exception {
      info.update(getViewsheet(), columns);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected final void writeContents(PrintWriter writer) {
      writeStateContent(writer, false);
      getInfo().writeXML(writer);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected final void parseContents(Element elem) throws Exception {
      Element inode = Tool.getChildNodeByTagName(elem, "assemblyInfo");
      getInfo().parseXML(inode);
      parseStateContent(elem, false);
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   public final void writeState(PrintWriter writer, boolean runtime) {
      writer.print("<assembly class=\"" + getClass().getName()+ "\">");
      writer.print("<name><![CDATA[" + getName() + "]]></name>");
      writeStateContent(writer, runtime);
      writer.print("</assembly>");
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    */
   @Override
   public final void parseState(Element elem) throws Exception {
      parseStateContent(elem);
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      // do nothing
   }

   /**
    * Parse the state, will delegate to call parseStateContent(elem, true).
    * @param elem the specified xml element.
    */
   protected final void parseStateContent(Element elem) throws Exception {
      parseStateContent(elem, true);
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    * @param runtime if is runtime mode, default is true.
    */
   protected void parseStateContent(Element elem, boolean runtime)
      throws Exception
   {
      // do nothing
   }

   /**
    * Get the sheet container.
    * @return the sheet container.
    */
   @Override
   public AbstractSheet getSheet() {
      return getViewsheet();
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "VS-" + super.toString();
   }

   /**
    * Check if supports tab.
    * @return <tt>true</tt> if this assembly may be laid as one sub component
    * of a tab, <tt>false</tt> otherwise.
    */
   @Override
   public boolean supportsTab() {
      return supportsContainer();
   }

   /**
    * Check if supports container.
    * @return <tt>true</tt> if this assembly may be laid as one sub component
    * of a container, <tt>false</tt> otherwise.
    */
   @Override
   public boolean supportsContainer() {
      return true;
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      info.initDefaultFormat();
   }

   /**
    * Check if this data assembly only depends on selection assembly.
    * @return <tt>true</tt> if it is only changed by the selection assembly,
    * <tt>false</tt> otherwise.
    */
   @Override
   public boolean isStandalone() {
      return false;
   }

   /**
    * Return stack order.
    */
   @Override
   public int getZIndex() {
      return info.getZIndex();
   }

   /**
    * Set stack order.
    */
   @Override
   public void setZIndex(int zIndex) {
      this.info.setZIndex(zIndex);
   }

   /**
    * Get the bound table.
    */
   @Override
   public String getTableName() {
      return null;
   }

   /**
    * Set the tip condition list.
    */
   @Override
   public void setTipConditionList(ConditionListWrapper wrapper) {
      this.tconds = wrapper;
   }

   /**
    * Get the tip condition list.
    */
   @Override
   public ConditionListWrapper getTipConditionList() {
      return tconds;
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return new UserVariable[0];
   }

   @Override
   public DataRef[] getBindingRefs() {
      return new DataRef[0];
   }

   /**
    * Write the annotations into bookmark.
    */
   public void writeAnnotations(VSAssemblyInfo info, PrintWriter writer) {
      if(!(info instanceof BaseAnnotationVSAssemblyInfo) || noAnnotation()) {
         return;
      }

      List<String> list =
         ((BaseAnnotationVSAssemblyInfo) info).getAnnotations();
      Viewsheet vs = VSUtil.getTopViewsheet(getViewsheet());

      for(int i = 0; list != null && i < list.size(); i++) {
         Assembly ass = vs.getAssembly(list.get(i));

         if(!(ass instanceof AnnotationVSAssembly)) {
            continue;
         }

         AnnotationVSAssemblyInfo ainfo =
            (AnnotationVSAssemblyInfo) ass.getInfo();

         if(ainfo.isAvailable() || !AnnotationVSUtil.isOvertime(ass) ||
            keepAnnoVisible())
         {
            if(!keepAnnoVisible()) {
               ainfo.setLastDisplay(System.currentTimeMillis());

               if(!(info instanceof EmbeddedTableVSAssemblyInfo)) {
                  ainfo.setRow(-1);
                  ainfo.setCol(-1);
               }
            }

            String line = ainfo.getLine();
            String rect = ainfo.getRectangle();
            Assembly lineObj = vs.getAssembly(line);
            Assembly rectObj = vs.getAssembly(rect);

            writer.println("<annotationAssembly>");
            ass.writeXML(writer);

            if(lineObj != null) {
               lineObj.writeXML(writer);
            }

            if(rectObj != null) {
               rectObj.writeXML(writer);
            }

            writer.println("</annotationAssembly>");
         }
      }
   }

   /**
    * Parse the annotations.
    */
   public void parseAnnotations(Element elem, VSAssemblyInfo info)
      throws Exception
   {
      if(!(info instanceof BaseAnnotationVSAssemblyInfo) || noAnnotation()) {
         return;
      }

      ((BaseAnnotationVSAssemblyInfo) info).clearAnnotations();
      String cls = Tool.getAttribute(elem, "class");

      if(!Tool.equals(cls, getClass().getName())) {
         return;
      }

      Viewsheet vs = VSUtil.getTopViewsheet(getViewsheet());
      NodeList list = Tool.getChildNodesByTagName(elem, "annotationAssembly");

      // add new annotation, sub line and rectangle assemblies
      for(int i = 0; i < list.getLength(); i++) {
         Element onenode = (Element) list.item(i);
         NodeList anodes = Tool.getChildNodesByTagName(onenode, "assembly");
         boolean isData = isDataAnnotation(onenode);

         if(info instanceof TableVSAssemblyInfo &&
            ((TableVSAssemblyInfo) info).isForm() && isData)
         {
            continue;
         }

         for(int j = 0; j < anodes.getLength(); j++) {
            Element anode = (Element) anodes.item(j);
            VSAssembly assembly = createVSAssembly(anode, vs);

            if(assembly == null) {
               continue;
            }

            vs.addAssembly(assembly);

            if(assembly instanceof AnnotationVSAssembly) {
               ((BaseAnnotationVSAssemblyInfo) info).addAnnotation(
                  assembly.getName());
               VSUtil.updateEmbeddedVSAnnotationZIndex((AnnotationVSAssembly) assembly, this);
            }

            // always set the data type annotation assembly visible to false
            VSAssemblyInfo ainfo = (VSAssemblyInfo) assembly.getInfo();

            if(!keepAnnoVisible()) {
               ainfo.setVisible(!(isData || !info.isVisible() ||
                                !info.isEnabled()));
            }
         }
      }
   }

   /**
    * Check if need write and parse annotation.
    */
   protected boolean noAnnotation() {
      if(getViewsheet() == null || getViewsheet().getRuntimeEntry() == null) {
         return false;
      }

      return "true".equals(getViewsheet().getRuntimeEntry().getProperty(
         "noAnnotation"));
   }

   /**
    * Check if need keep annotation visible.
    */
   private boolean keepAnnoVisible() {
      if(getViewsheet() == null || getViewsheet().getRuntimeEntry() == null) {
         return false;
      }

      return "true".equals(getViewsheet().getRuntimeEntry().getProperty(
         "keepAnnoVis"));
   }

   /**
    * Check if is data type annotation.
    */
   private boolean isDataAnnotation(Element onenode) {
      Element anode = Tool.getChildNodeByTagName(onenode, "assembly");
      String cls = Tool.getAttribute(anode, "class");

      if("inetsoft.uql.viewsheet.AnnotationVSAssembly".equals(cls)) {
         Element ainfo = Tool.getChildNodeByTagName(anode, "assemblyInfo");
         String type = Tool.getAttribute(ainfo, "type");

         if(Tool.equals(type, AnnotationVSAssemblyInfo.DATA + "")) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the binding datarefs.
    */
   protected List<DataRef> getBindingRefList() {
      List<DataRef> datarefs = new ArrayList<>();
      DataRef[] refs = getBindingRefs();
      Collections.addAll(datarefs, refs);
      return datarefs;
   }

   @Override
   public DataRef[] getAllBindingRefs() {
      return getBindingRefs();
   }

   /**
    * Clear the layout state.
    */
   @Override
   public void clearLayoutState() {
      VSAssemblyInfo info = getVSAssemblyInfo();
      info.setLayoutPosition(null);
      info.setLayoutSize(null);

      if(!info.isControlByScript()) {
         info.setLayoutVisible(VSAssembly.NONE_CHANGED);
      }

      FormatInfo formatInfo = getFormatInfo();
      TableDataPath[] paths = formatInfo.getPaths();

      for(TableDataPath path : paths) {
         VSCompositeFormat format = formatInfo.getFormat(path);
         format.setRScaleFont(1);
      }
   }

   @Override
   public void setPixelOffset(Point poff) {
      this.info.setPixelOffset(poff);
   }

   @Override
   public Point getPixelOffset() {
      return this.info.getPixelOffset();
   }

   @Override
   public void setPixelSize(Dimension pixelsize) {
      Dimension msize = getMinimumSize();

      int width = Math.max(msize.width, pixelsize.width);
      int height = Math.max(msize.height, pixelsize.height);

      this.info.setPixelSize(new Dimension(width, height));
   }

   @Override
   public Dimension getPixelSize() {
      Dimension msize = getMinimumSize();
      Dimension size = info.getPixelSize();

      if(msize.width > size.width || msize.height > size.height) {
         int width = Math.max(msize.width, size.width);
         int height = Math.max(msize.height, size.height);
         size = new Dimension(width, height);
      }

      return size;
   }

   /**
    * Is the assembly is wizardTemporary.
    */
   public boolean isWizardTemporary() {
      return info.isWizardTemporary();
   }

   /**
    * Set the assembly is wizardTemporary.
    */
   public void setWizardTemporary(boolean wizardTemporary) {
      info.setWizardTemporary(wizardTemporary);
   }

   @Override
   public boolean isWizardEditing() {
      return info.isWizardEditing();
   }

   @Override
   public void setWizardEditing(boolean wizardEditing) {
      info.setWizardEditing(wizardEditing);
   }

   protected VSAssemblyInfo info;
   private transient ConditionListWrapper tconds;

   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractVSAssembly.class);
}
