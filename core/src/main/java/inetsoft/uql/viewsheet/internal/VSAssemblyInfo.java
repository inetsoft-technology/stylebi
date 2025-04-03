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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.css.*;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * VSAssemblyInfo, the assembly info of a view sheet assembly. It implements
 * the common APIs of all the descendant assembly infos.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSAssemblyInfo extends AssemblyInfo implements FloatableVSAssemblyInfo {
   /**
    * Constructor.
    */
   public VSAssemblyInfo() {
      super();

      this.enabledValue = new DynamicValue("true", XSchema.BOOLEAN);
      this.visibleValue = new DynamicValue("show", XSchema.INTEGER,
         new int[] {VSAssembly.ALWAYS_SHOW, VSAssembly.ALWAYS_SHOW,
                    VSAssembly.ALWAYS_HIDE, VSAssembly.ALWAYS_HIDE,
                    VSAssembly.HIDE_ON_PRINT},
         new String[] {"true", "show", "false", "hide",
                       "hide on print and export"});
      this.layoutVisibleValue = new DynamicValue("", XSchema.INTEGER,
         new Integer[] {VSAssembly.NONE_CHANGED, VSAssembly.ALWAYS_SHOW, VSAssembly.ALWAYS_HIDE});
      setPrimary(true);
      fmtInfo = new FormatInfo();

      // default Sort Column is invisible
      setActionVisible("D_Sort Column", false);
      // don't force toolbar to be always visible
      setActionVisible("Toolbar:fixed", false);

      setActionVisible("Condition", "true".equals(
         SreeEnv.getProperty("viewsheet.viewer.advancedFeatures")));
      setActionVisible("Highlight", "true".equals(
         SreeEnv.getProperty("viewsheet.viewer.advancedFeatures")));
      // For Bug #7866, WHO requires no selection on maps due to requirements on disputed
      // borders.  The borders of the shapes cannot have solid lines on them.  This
      // seemed to be the best available feature to satisfy this requirement in near term.
      setActionVisible("MapSelectionEnabled", "true".equals(
         SreeEnv.getProperty("map.selection.enabled")));
      boolean annotationsVisible = !"true".equals(SreeEnv.getProperty("annotations.disabled"));
      setActionVisible("Annotation", annotationsVisible);
      setActionVisible("Annotate Cell", annotationsVisible);
      setActionVisible("Annotate Component", annotationsVisible);
      setActionVisible("Annotate Point", annotationsVisible);
   }

   /**
    * Get the class.
    * @return the class of the assembly.
    */
   public String getClassName() {
      return cls;
   }

   /**
    * Set the class.
    * @param cls the specified class.
    */
   public void setClassName(String cls) {
      this.cls = cls;
   }

   /**
    * Check if script is enabled or not.
    */
   public boolean isScriptEnabled() {
      return scriptEnabled;
   }

   /**
    * Set the script is enabled or not.
    */
   public void setScriptEnabled(boolean enable) {
      this.scriptEnabled = enable;
   }

   /**
    * Check if the assembly is temporary added for vs wizard,
    * and will be removed after exit vs wizard.
    * @return <tt>true</tt> if temporary, <tt>false</tt> otherwise.
    */
   public boolean isWizardTemporary() {
      return wizardTemporary;
   }

   /**
    * Set if the assembly is temporary added for vs wizard,
    * and will be removed after exit vs wizard.
    */
   public void setWizardTemporary(boolean wizardTemporary) {
      this.wizardTemporary = wizardTemporary;
   }

   /**
    * Check if this assembly is being editing in vs object wizard.
    */
   public boolean isWizardEditing() {
      return wizardEditing;
   }

   /**
    * Set this assembly is being editing status in vs object wizard,
    * and will be removed after exit vs object wizard.
    */
   public void setWizardEditing(boolean wizardEditing) {
      this.wizardEditing = wizardEditing;
   }

   /**
    * Set the parent viewsheet.
    * @param vs the specified viewsheet.
    */
   public void setViewsheet(Viewsheet vs) {
      this.vs = vs;
   }

   /**
    * Get the parent viewsheet.
    * @return the parent viewsheet.
    */
   public Viewsheet getViewsheet() {
      return vs;
   }

   /**
    * Get the enabled value.
    * @return the enabled value of this assembly info.
    */
   public String getEnabledValue() {
      return enabledValue.getDValue();
   }

   /**
    * Set the enabled value to this assembly info.
    * @param enabledValue the specified enabled value.
    */
   public void setEnabledValue(String enabledValue) {
      this.enabledValue.setDValue(enabledValue);
   }

   /**
    * Get the javascript attached to this assembly.
    */
   public String getScript() {
      return script;
   }

   /**
    * Set the javascript attached to this chart. The script is executed to
    * modify the EGraph before the chart is rendered.
    */
   public void setScript(String script) {
      this.script = script;
   }

   /**
    * Get the Hyperlink.Ref.
    * @return the Hyperlink.Ref value.
    */
   public Hyperlink.Ref getHyperlinkRef() {
      return ref;
   }

   @Override
   public void setName(String name) {
      // clear cached name on renaming
      if(!Tool.equals(name, getName())) {
         setAbsoluteName2(null);
      }

      super.setName(name);
   }

   /**
    * Get the absolute name of this assembly.
    * @return the absolute name of this assembly.
    */
   @Override
   public String getAbsoluteName() {
      Viewsheet vs = getViewsheet();
      String pname = (vs == null) ? null : vs.getAbsoluteName();
      return (pname == null) ? getName() : pname + "." + getName();
   }

   /**
    * Get the absolute name of this assembly. By using this name, we may cache
    * the original name of this assembly when renaming it.
    * @return the absolute name of this assembly.
    */
   public String getAbsoluteName2() {
      return (aname != null) ? aname : getAbsoluteName();
   }

   /**
    * Update absoluteName2 for embedded viewsheet.
    */
   public void setAbsoluteName2(String name) {
      aname = name;
   }

   /**
    * Get the object css default type.
    */
   public String getObjCSSType() {
      // need to be override
      return null;
   }

   /**
    * Check if this assembly is embedded.
    * @return <tt>true</tt> if embedded, <tt>false</tt> otherwise.
    */
   public boolean isEmbedded() {
      Viewsheet vs = getViewsheet();
      return vs != null && vs.isEmbedded();
   }

   /**
    * Check if the assembly is enabled.
    * @return <tt>true</tt> if enabled, <tt>false</tt> otherwise.
    */
   public boolean isEnabled() {
      return (Boolean) enabledValue.getRuntimeValue(true);
   }

   /**
    * Set the enabled status. This is combined with the dynamic enabled property
    * to determine if an assembly is enabled. This assembly is enabled only if
    * both are true.
    */
   public void setEnabled(boolean enabled) {
      enabledValue.setRValue(enabled);
   }

   /**
    * Get the format.
    * @return the format of this assembly info.
    */
   public VSCompositeFormat getFormat() {
      return fmtInfo.getFormat(OBJECTPATH);
   }

   /**
    * Set the format to this assembly info.
    * @param fmt the specified format.
    */
   public void setFormat(VSCompositeFormat fmt) {
      fmtInfo.setFormat(OBJECTPATH, fmt);
   }

   /**
    * Get the format info.
    * @return the format info of this assembly info.
    */
   public FormatInfo getFormatInfo() {
      return fmtInfo;
   }

   /**
    * Set the format info to this assembly info.
    * @param info the specified format info.
    */
   public void setFormatInfo(FormatInfo info) {
      this.fmtInfo = info == null ? new FormatInfo() : info;
   }

   /**
    * Get run time hyperlink.
    * @return the run time hyperlink.
    */
   public Hyperlink getHyperlink() {
      return linkValue.getRValue();
   }

   /**
    * Set run time hyperlink.
    * @param link the runtime specified hyperlink.
    */
   public void setHyperlink(Hyperlink link) {
      this.linkValue.setRValue(link);
   }

   /**
    * Get design time hyperlink.
    * @return the design time hyperlink.
    */
   public Hyperlink getHyperlinkValue() {
      return linkValue.getDValue();
   }

   /**
    * Set design time hyperlink.
    * @param link the design time specified hyperlink.
    */
   public void setHyperlinkValue(Hyperlink link) {
      this.linkValue.setDValue(link);
   }

   /**
    * Set hyperlink ref.
    */
   public void setHyperlinkRef(Hyperlink.Ref ref) {
      this.ref = ref;
   }

   /**
    * Get the visible value.
    * @return the visible value of this assembly info.
    */
   public String getVisibleValue() {
      return visibleValue.getDValue();
   }

   /**
    * Set the visible value to this assembly info.
    * @param visibleValue the specified visible value.
    */
   public void setVisibleValue(String visibleValue) {
      this.visibleValue.setDValue(visibleValue);
   }

   /**
    * Set the visible value to this assembly info.
    * @param visibleValue the specified visible value when appling vslayout.
    */
   public void setLayoutVisible(int visibleValue) {
      this.layoutVisibleValue.setRValue(visibleValue);
   }

   /**
    * Check if the assembly is visible.
    */
   @Override
   public boolean isVisible() {
      return isVisible(false);
   }

   /**
    * Check if the assembly is visible.
    * @param print <tt>true</tt> if is print mode, <tt>false</tt> otherwise.
    * @return <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   public boolean isVisible(boolean print) {
      Object layoutVal = layoutVisibleValue.getRuntimeValue(true);

      if(layoutVal instanceof Integer && ((Integer) layoutVal) != VSAssembly.NONE_CHANGED) {
         return ((Integer) layoutVal) == VSAssembly.ALWAYS_SHOW;
      }

      // if the value is boolean, it must have been set by script or variable
      if(visibleValue.getRValue() instanceof Boolean) {
         return (Boolean) visibleValue.getRValue();
      }

      Object rval = visibleValue.getRuntimeValue(true);

      if(!(rval instanceof Integer)) {
         return rval != null;
      }

      int ival = (Integer) rval;

      if(ival == VSAssembly.ALWAYS_SHOW) {
         return print && vs != null && (VSUtil.isTipView(getAbsoluteName(), vs) ||
            VSUtil.isPopComponent(getAbsoluteName(), vs)) || super.isVisible();
      }
      else if(ival == VSAssembly.ALWAYS_HIDE) {
         return false;
      }
      else if(ival == VSAssembly.HIDE_ON_PRINT) {
         return !print && super.isVisible();
      }
      else {
         throw new RuntimeException("Unsupported value found: " + ival);
      }
   }

   /**
    * Set the visible flag.
    * @param visible String representation of visibility.
    */
   public void setVisible(String visible) {
      visibleValue.setRValue(visible);
   }

   /**
    * Check if the VSAssembly is resizable.
    * @return <tt>true</tt> of resizable, <tt>false</tt> otherwise.
    */
   public boolean isResizable() {
      return isEditable();
   }

   /**
    * Get the description.
    * @return the description of the assembly info.
    */
   public String getDescription() {
      return desc;
   }

   /**
    * Set the description to this assembly info.
    * @param desc the specified description.
    */
   public void setDescription(String desc) {
      this.desc = desc;
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   public List<DynamicValue> getDynamicValues() {
      return new ArrayList<>();
   }

   /**
    * Get the dynamic property values for output properties.
    * @return the dynamic values.
    */
   public List<DynamicValue> getOutputDynamicValues() {
      return new ArrayList<>();
   }

   /**
    * Get the view dynamic values.
    * @param all true to include all view dynamic values. Otherwise only the
    * dynamic values need to be executed are returned.
    * @return the view dynamic values.
    */
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = new ArrayList<>();

      TableDataPath[] paths = fmtInfo.getPaths();

      // only special paths will be processed
      for(TableDataPath path : paths) {
         if(!all && !isProcessFormat(path)) {
            continue;
         }

         VSCompositeFormat fmt = fmtInfo.getFormat(path);

         if(fmt != null){
            list.addAll(fmt.getDynamicValues());
         }
      }

      list.add(enabledValue);
      list.add(visibleValue);

      return list;
   }

   /**
    * Get the dynamic property values for hyperlink.
    * @return the dynamic values.
    */
   public List<DynamicValue> getHyperlinkDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      Hyperlink hyperlink = getHyperlink();

      if(hyperlink != null && VSUtil.isScriptValue(hyperlink.getLinkValue())) {
         list.add(hyperlink.getDLink());
      }

      return list;
   }

   /**
    * Check if the dynamic value of the format should be processed.
    */
   protected boolean isProcessFormat(TableDataPath path) {
      return !path.isSpecial() || path.getType() == TableDataPath.TITLE ||
         path.getType() == TableDataPath.OBJECT;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      VSUtil.renameDynamicValueDepended(oname, nname, enabledValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, visibleValue, vs);

      TableDataPath[] paths = fmtInfo.getPaths();

      for(TableDataPath path : paths) {
         VSCompositeFormat fmt = fmtInfo.getFormat(path);
         fmt.getUserDefinedFormat().renameDepended(oname, nname, vs);
         fmtInfo.setFormat(path, fmt);
      }

      if(getScript() != null) {
         setScript(Util.renameScriptDepended(oname, nname, getScript()));
      }
   }

   /**
    * Copy the assembly info.
    * @param info the specified viewsheet assembly info.
    * @return the hint to reset view, data or worksheet data.
    */
   public int copyInfo(VSAssemblyInfo info) {
      return copyInfo(info, true);
   }

   /**
    * Copy the assembly info.
    * @param info the specified viewsheet assembly info.
    * @param deep whether it is simply copy the properties of the parent.
    * @return the hint to reset view, data or worksheet data.
    */
   public int copyInfo(VSAssemblyInfo info, boolean deep) {
      int hint = VSAssembly.NONE_CHANGED;

      // view
      boolean result = copyViewInfo(info, deep);

      if(result) {
         hint = hint | VSAssembly.VIEW_CHANGED;
      }

      // input data
      hint = copyInputDataInfo(info, hint);

      // output data
      result = copyOutputDataInfo(info);

      if(result) {
         hint = hint | VSAssembly.OUTPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Indicates if the assembly's image is a gif or not.
    */
   public boolean isAnimateGIF() {
      return false;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   protected boolean copyViewInfo(VSAssemblyInfo info) {
      return copyViewInfo(info, true);
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @param deep whether it is simply copy the properties of the parent.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = false;

      if(!Tool.equals(visibleValue, info.visibleValue) ||
         !Tool.equals(isVisible(), info.isVisible()))
      {
         visibleValue = info.visibleValue;
         controlByScript = false;
         result = true;
      }

      if(!Tool.equals(desc, info.desc)) {
         desc = info.desc;
         // needn't reset view
      }

      if(isPrimary() != info.isPrimary()) {
         setPrimary(info.isPrimary());
         // needn't reset view
      }

      if(!Tool.equals(fmtInfo, info.fmtInfo)) {
         fmtInfo = info.fmtInfo;
         result = true;
      }

      if(!Tool.equals(enabledValue, info.enabledValue) ||
         !Tool.equals(isEnabled(), info.isEnabled()))
      {
         // copy is done int copyOutputData. View needs to be re-evaluated.
         result = true;
      }

      if(!Tool.equals(linkValue, info.linkValue)) {
         linkValue = info.linkValue;
         result = true;
      }

      if(!Tool.equals(pixelpos, info.pixelpos)) {
         pixelpos = info.pixelpos;
         result = true;
      }

      if(!Tool.equals(layoutPosition, info.layoutPosition)) {
         layoutPosition = info.layoutPosition;
         result = true;
      }

      if(!Tool.equals(layoutSize, info.layoutSize)) {
         layoutSize = info.layoutSize;
         result = true;
      }

      if(!Tool.equals(getPixelOffset(), info.getPixelOffset())) {
         setPixelOffset(info.getPixelOffset());
         result = true;
      }

      if(!Tool.equals(getPixelSize(), info.getPixelSize())) {
         setPixelSize(info.getPixelSize());
         result = true;
      }

      if(!Objects.equals(scaledPosition, info.scaledPosition)) {
         scaledPosition = info.scaledPosition;
         result = true;
      }

      if(!Objects.equals(scaledSize, info.scaledSize)) {
         scaledSize = info.scaledSize;
         result = true;
      }

      if(zIndex != info.zIndex) {
         zIndex = info.zIndex;
         result = true;
      }

      if(!Tool.equals(getActionNamesString(), info.getActionNamesString())) {
         actionNames = info.actionNames;
         result = true;
      }

      if(!Tool.equals(padding, info.padding)) {
         padding = info.padding;
         result = true;
      }

      return result;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      return hint;
   }

   /**
    * Copy the output data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   protected boolean copyOutputDataInfo(VSAssemblyInfo info) {
      boolean result = false;

      if(!Tool.equals(enabledValue, info.enabledValue) ||
         isEnabled() != info.isEnabled())
      {
         enabledValue = info.enabledValue;
         result = true;
      }

      if(!Tool.equals(script, info.script)) {
         script = info.script;
         result = true;
      }

      if(!Tool.equals(scriptEnabled, info.scriptEnabled)) {
         scriptEnabled = info.scriptEnabled;
         result = true;
      }

      return result;
   }

   /**
    * Copy the position/size information.
    */
   public void copyLayout(VSAssemblyInfo info) {
      this.setPixelOffset(info.getPixelOffset());
      this.setPixelSize(info.getPixelSize());
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   public void update(Viewsheet vs, ColumnSelection columns) throws Exception {
      // do nothing
   }

   /**
    * Set the pixel position of this object. If set, it overrides the
    * position determined by the grid.
    */
   public void setPixelPosition(Point pixelpos) {
      this.pixelpos = pixelpos;
   }

   /**
    * Get the pixel position of this object.
    * @return pixel position or null if the position is not explicitly set.
    */
   public Point getPixelPosition() {
      return pixelpos;
   }

   /**
    * Set the pixel position for vslayout.
    */
   public final void setLayoutPosition(Point layoutPosition) {
      this.layoutPosition = layoutPosition;
      this.scaledPosition = null;
   }

   /**
    * Get the pixel position for vslayout.
    */
   public final Point getLayoutPosition() {
      return getLayoutPosition(true);
   }

   public final Point getLayoutPosition(boolean scaled) {
      Point scaledPosition = this.scaledPosition;

      if(scaled && scaledPosition != null) {
         return scaledPosition;
      }

      return layoutPosition;
   }

   /**
    * Set the pixel size for vslayout.
    */
   public final void setLayoutSize(Dimension layoutSize) {
      this.layoutSize = layoutSize;
      this.scaledSize = null;
   }

   /**
    * Get the pixel size for vslayout.
    */
   public final Dimension getLayoutSize() {
      return getLayoutSize(true);
   }

   public final Dimension getLayoutSize(boolean scaled) {
      Dimension scaledSize = this.scaledSize;

      if(scaled && scaledSize != null) {
         return scaledSize;
      }

      return layoutSize;
   }

   public final void setScaledPosition(Point scaledPosition) {
      this.scaledPosition = scaledPosition;
   }

   public final void setScaledSize(Dimension scaledSize) {
      this.scaledSize = scaledSize;
   }

   /**
    * Get position scale ratio of this assembly.
    */
   public Point2D.Double getPositionScale(Point2D.Double scaleRatio) {
      return scaleRatio;
   }

   /**
    * Get size scale ratio of this assembly.
    */
   public Point2D.Double getSizeScale(Point2D.Double scaleRatio) {
      return scaleRatio;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(pixelpos != null) {
         writer.print(" pixelX=\"" + pixelpos.x + "\"");
         writer.print(" pixelY=\"" + pixelpos.y + "\"");
      }

      if(layoutPosition != null) {
         writer.print(" layoutX=\"" + layoutPosition.x + "\"");
         writer.print(" layoutY=\"" + layoutPosition.y + "\"");
      }

      if(layoutSize != null) {
         writer.print(" layoutWidth=\"" + layoutSize.width + "\"");
         writer.print(" layoutHeight=\"" + layoutSize.height + "\"");
      }

      if(padding != null) {
         writer.print(" paddingTop=\"" + padding.top +
                         "\" paddingLeft=\"" + padding.left +
                         "\" paddingBottom=\"" + padding.bottom +
                         "\" paddingRight=\"" + padding.right + "\"");
      }

      writer.print(" zIndex=\"" + zIndex + "\"");
      writer.print(" scriptEnabled=\"" + scriptEnabled + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String xstr = Tool.getAttribute(elem, "pixelX");
      String ystr = Tool.getAttribute(elem, "pixelY");

      if(xstr != null && ystr != null) {
         pixelpos = new Point(Integer.parseInt(xstr), Integer.parseInt(ystr));
      }

      String layoutX = Tool.getAttribute(elem, "layoutX");
      String layoutY = Tool.getAttribute(elem, "layoutY");

      if(layoutX != null && layoutY != null) {
         layoutPosition =
            new Point(Integer.parseInt(layoutX), Integer.parseInt(layoutY));
      }

      String layoutW = Tool.getAttribute(elem, "layoutWidth");
      String layoutH = Tool.getAttribute(elem, "layoutHeight");

      if(layoutW != null && layoutH != null) {
         layoutSize =
            new Dimension(Integer.parseInt(layoutW), Integer.parseInt(layoutH));
      }

      if(Tool.getAttribute(elem, "paddingTop") != null) {
         padding = new Insets(Integer.parseInt(Tool.getAttribute(elem, "paddingTop")),
                              Integer.parseInt(Tool.getAttribute(elem, "paddingLeft")),
                              Integer.parseInt(Tool.getAttribute(elem, "paddingBottom")),
                              Integer.parseInt(Tool.getAttribute(elem, "paddingRight")));
      }

      String idxStr = Tool.getAttribute(elem, "zIndex");
      zIndex = idxStr == null ? 0 : Integer.parseInt(idxStr);
      String prop = Tool.getAttribute(elem, "scriptEnabled");
      scriptEnabled = !"false".equals(prop);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(desc != null) {
         writer.print("<description>");
         writer.print("<![CDATA[" + desc + "]]>");
         writer.println("</description>");
      }

      if(visibleValue.getDValue() == null) {
         visibleValue.setDValue("" + VSAssembly.ALWAYS_SHOW);
      }

      writer.print("<visible value=\"" + super.isVisible() + "\"");

      if(visibleValue.getRValue() != null) {
         writer.print(" rvalue=\"" + visibleValue.getRValue() + "\"");
      }

      writer.print(">");

      writer.print("<![CDATA[" + visibleValue.getDValue() + "]]>");
      writer.println("</visible>");

      writer.print("<visible2 value=\"" + this.isVisible() + "\"/>");

      if(enabledValue.getDValue() != null) {
         writer.print("<enabled value=\"" + isEnabled() + "\">");
         writer.print("<![CDATA[" + enabledValue.getDValue() + "]]>");
         writer.println("</enabled>");
      }

      writer.print("<absoluteName>");
      writer.print("<![CDATA[" + getAbsoluteName() + "]]>");
      writer.println("</absoluteName>");
      writer.print("<embedded embedded=\"" + isEmbedded() + "\" />");

      if(linkValue != null && getHyperlinkValue() != null) {
         writer.print("<hyperLink>");
         getHyperlinkValue().writeXML(writer);
         writer.print("</hyperLink>");
      }

      if(ref != null) {
         writer.print("<hyperLinkRef>");
         ref.writeXML(writer);
         writer.print("</hyperLinkRef>");
      }

      String names = getActionNamesString();

      if(names != null && names.length() > 0) {
         writer.print("<actionNames>");
         writer.print("<![CDATA[" + names + "]]>");
         writer.println("</actionNames>");
      }

      if(script != null) {
         writer.print("<script><![CDATA[" + script + "]]></script>");
      }

      fmtInfo.writeXML(writer);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element dnode = Tool.getChildNodeByTagName(elem, "description");

      if(dnode != null) {
         desc = Tool.getValue(dnode);
      }

      Element vnode = Tool.getChildNodeByTagName(elem, "visible");

      if(vnode != null) {
         visibleValue.setDValue(Tool.getValue(vnode));
         String rstr = Tool.getAttribute(vnode, "rvalue");

         // restore the rvalue since the visibility may be checked as part of
         // layout before the dvalue is evaluated
         if(rstr != null) {
            try {
               visibleValue.setRValue(Integer.valueOf(rstr));
            }
            catch(Exception ex) {
               if(rstr.equals("false")) {
                  visibleValue.setRValue(Boolean.FALSE);
               }
            }
         }
      }

      Element enode = Tool.getChildNodeByTagName(elem, "enabled");

      if(enode != null) {
         enabledValue.setDValue(Tool.getValue(enode));
      }

      Element inode = Tool.getChildNodeByTagName(elem, "formatInfo");

      if(inode != null) {
         fmtInfo = new FormatInfo();
         fmtInfo.parseXML(inode);
      }

      Element anode = Tool.getChildNodeByTagName(elem, "absoluteName");
      aname = Tool.getValue(anode);

      Element lnode = Tool.getChildNodeByTagName(elem, "hyperLink");

      if(lnode != null) {
         Hyperlink link = new Hyperlink();
         link.parseXML((Element) lnode.getFirstChild());

         link = handleAssetLinkOrgMismatch(link);

         linkValue.setDValue(link);
      }

      Element rnode = Tool.getChildNodeByTagName(elem, "hyperLinkRef");

      if(rnode != null) {
         ref = new Hyperlink.Ref();
         ref.parseXML((Element) rnode.getFirstChild());
      }

      Element sNode = Tool.getChildNodeByTagName(elem, "script");

      if(sNode != null) {
         script = Tool.getValue(sNode, true);
      }
   }

   /**
    * Get the string representation.
    * @return the string representation of this assembly info.
    */
   public String toString() {
      return super.toString() + "[" + getAbsoluteName() + "]";
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   @SuppressWarnings("MethodDoesntCallSuperMethod")
   public final VSAssemblyInfo clone() {
      return clone(false);
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   public VSAssemblyInfo clone(boolean shallow) {
      try {
         VSAssemblyInfo info = (VSAssemblyInfo) super.clone();

         if(!shallow) {
            info.fmtInfo = fmtInfo.clone();

            if(enabledValue != null) {
               info.enabledValue = (DynamicValue) enabledValue.clone();
            }

            if(visibleValue != null) {
               info.visibleValue = (DynamicValue) visibleValue.clone();
            }

            if(layoutVisibleValue != null) {
               info.layoutVisibleValue = (DynamicValue) layoutVisibleValue.clone();
            }

            if(linkValue != null) {
                info.linkValue = linkValue.clone();
            }

            if(padding != null) {
               info.padding = (Insets) padding.clone();
            }

            info.actionNames = Tool.deepCloneCollection(info.actionNames);
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Initialize the default format.
    */
   public void initDefaultFormat() {
      setDefaultFormat(false);
   }

   /**
    * Set the default vsobject format.
    */
   protected void setDefaultFormat(boolean border) {
      setDefaultFormat(border, true);
   }

   /**
    * Set the default vsobject format.
    */
   protected void setDefaultFormat(boolean border, boolean setFormat) {
      setDefaultFormat(border, setFormat, false);
   }

   /**
    * Set the default vsobject format.
    */
   protected void setDefaultFormat(boolean border, boolean setFormat, boolean fill) {
      VSCompositeFormat format = new VSCompositeFormat();
      VSCompositeFormat tformat = new VSCompositeFormat();
      VSFormat objfmt = format.getDefaultFormat();
      VSFormat titlefmt = tformat.getDefaultFormat();

      Insets borders = null;
      BorderColors bcolors = new BorderColors(DEFAULT_BORDER_COLOR,
                                              DEFAULT_BORDER_COLOR,
                                              DEFAULT_BORDER_COLOR,
                                              DEFAULT_BORDER_COLOR);
      int borderRadius = 0;
      boolean table = this instanceof TableDataVSAssemblyInfo;
      CSSDictionary cssDictionary = CSSDictionary.getDictionary();
      CSSStyle style = cssDictionary.getStyle(new CSSParameter("TableStyle", null, null,
                                                               new CSSAttr("region", "Table")));

      if(border) {
         borders = new Insets(StyleConstants.THIN_LINE,
                              StyleConstants.THIN_LINE,
                              StyleConstants.THIN_LINE,
                              StyleConstants.THIN_LINE);

         titlefmt.setBordersValue(borders);
         titlefmt.setBorderColorsValue(bcolors);

         if(style != null && style.isBorderColorDefined() && table) {
            bcolors = style.getBorderColors();
         }

         if(style != null && style.isBorderRadiusDefined() && table) {
            borderRadius = style.getBorderRadius();
         }
      }

      if(setFormat) {
         objfmt.setBordersValue(borders);
         objfmt.setBorderColorsValue(bcolors);
         objfmt.setRoundCornerValue(borderRadius);
         objfmt.setFontValue(getDefaultFont(Font.PLAIN, 11));

         if(fill) {
            objfmt.setBackgroundValue("0xffffff");
         }

         VSCSSFormat objCssFmt = format.getCSSFormat();

         if(getObjCSSType() != null) {
            objCssFmt.setCSSType(getObjCSSType());
         }

         setFormat(format);
      }

      if(this instanceof TitledVSAssemblyInfo) {
         titlefmt.setBackgroundValue(DEFAULT_TITLE_BG);
         titlefmt.setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_CENTER);
         titlefmt.setFontValue(getTitleDefaultFont());
         VSCSSFormat cssfmt = tformat.getCSSFormat();

         if(getObjCSSType() != null) {
            cssfmt.setCSSType(getObjCSSType() + CSSConstants.TITLE);
         }

         fmtInfo.setFormat(TITLEPATH, tformat);
      }

      setCSSDefaults();
   }

   /**
    * Get the enabled status dynamic value.
    * @return the enabled status dynamic value.
    */
   protected DynamicValue getEnabledDynamicValue() {
      return enabledValue;
   }

   /**
    * Return stack order.
    */
   public int getZIndex() {
      return zIndex;
   }

   /**
    * Set stack order.
    */
   public void setZIndex(int zIndex) {
      this.zIndex = zIndex;
   }

   /**
    * Get the padding space around the chart (between border and chart).
    */
   public Insets getPadding() {
      return padding;
   }

   /**
    * Set the padding space around the chart (between border and chart).
    */
   public void setPadding(Insets padding) {
      this.padding = padding;
   }

   /**
    * Fire binding changed event.
    */
   protected void fireBindingEvent() {
      if(vs != null) {
         vs.fireEvent(Viewsheet.BIND_ASSEMBLY, getName());
      }
   }

   /**
    * Get the invisible names of the vs assembly actions.
    * @return the invisible action names of a vs assembly.
    */
   private String getActionNamesString() {
      if(actionNames.isEmpty()) {
         return null;
      }

      StringBuilder actions = new StringBuilder();

      for(String action : actionNames) {
         if(actions.length() > 0) {
            actions.append("^");
         }

         actions.append(action);
      }

      return actions.toString();
   }

   /**
    * Gets invisible actions names
    * @return action names
    */
   public Set<String> getActionNames() {
      return actionNames;
   }

   /**
    * Get the visibility of the specific action.
    * @param actionName the name of the specific action.
    * @return <tt>false</tt> if invisible, <tt>true</tt> otherwise.
    */
   public boolean isActionVisible(String actionName) {
      return actionName != null && actionName.length() > 0 &&
         !actionNames.contains(actionName);
   }

   /**
    * Set the visibility of a specific action.
    * @param actionName the name of the specific action.
    * @param visible <tt>false</tt> if invisible, <tt>true</tt> otherwise.
    */
   public void setActionVisible(String actionName, boolean visible) {
      if(actionName != null && actionName.length() > 0) {
         if(visible) {
            actionNames.remove(actionName);
         }
         else {
            actionNames.add(actionName);
         }

         actionNames.remove("D_" + actionName);
      }
   }

   /**
    * Reset runtime values.
    */
   public void resetRuntimeValues() {
      enabledValue.setRValue(null);
      visibleValue.setRValue(null);
      linkValue.setRValue(null);
      fmtInfo.resetRuntimeValues();
   }

   /**
    * Clear all action visibility setting.
    */
   public void resetActionVisible() {
      this.actionNames.clear();
   }

   /**
    * Is the visible control by script.
    */
   public boolean isControlByScript() {
      return controlByScript;
   }

   /**
    * Set the visible is control by script.
    */
   public void setControlByScript(boolean control) {
      this.controlByScript = control;
   }

   /**
    * Parse attribute properly.
    * @param elem the specified xml element.
    * @param prop the old property name.
    * @param def the default value.
    */
   protected String getAttributeStr(Element elem, String prop, String def) {
      return VSUtil.getAttributeStr(elem, prop, def);
   }

   /**
    * Parse contents properly.
    * @param elem the specified xml element.
    * @param prop the old property name.
    * @param def the default value.
    */
   protected String getContentsStr(Element elem, String prop, String def) {
      Element node = Tool.getChildNodeByTagName(elem, prop + "Value");
      node = node == null ? Tool.getChildNodeByTagName(elem, prop) : node;

      return node == null ? def : Tool.getValue(node);
   }

   protected void setCSSDefaults() {
      CSSDictionary cssDictionary = CSSDictionary.getDictionary();
      VSCompositeFormat format = getFormat();

      if(format == null) {
         return;
      }

      VSCSSFormat objCssFmt = format.getCSSFormat();

      if(objCssFmt != null) {
         Dimension size = (Dimension) getPixelSize().clone();

         if(cssDictionary.isWidthDefined(objCssFmt.getCSSParam())) {
            size.width = cssDictionary.getWidth(objCssFmt.getCSSParam());
         }

         if(cssDictionary.isHeightDefined(objCssFmt.getCSSParam())) {
            size.height = cssDictionary.getHeight(objCssFmt.getCSSParam());
         }

         setPixelSize(size);

         if(this instanceof LineVSAssemblyInfo) {
            ((LineVSAssemblyInfo) this).setStartPos(new Point(size.width, size.height));
         }

         if(cssDictionary.isPaddingDefined(objCssFmt.getCSSParam())) {
            setPadding(cssDictionary.getPadding(objCssFmt.getCSSParam()));
         }

         if(this instanceof TitledVSAssemblyInfo) {
            VSCompositeFormat titleFormat = getFormatInfo().getFormat(TITLEPATH);

            if(titleFormat != null) {
               CSSParameter objectCssParam = objCssFmt.getCSSParam();
               CSSParameter titleCssParam = titleFormat.getCSSFormat().getCSSParam();

               // set the default title visibility from css
               if(cssDictionary.isVisibleDefined(objectCssParam, titleCssParam)) {
                  ((TitledVSAssemblyInfo) this).setTitleVisibleValue(
                     cssDictionary.isVisible(objectCssParam, titleCssParam));
               }

               // set default css title height
               if(cssDictionary.isHeightDefined(objectCssParam, titleCssParam)) {
                  ((TitledVSAssemblyInfo) this).setTitleHeightValue(
                     cssDictionary.getHeight(objectCssParam, titleCssParam));
               }
            }
         }
      }
   }

   /**
    * Updates css values that exist outside of VSCSSFormat
    */
   public void updateCSSValues() {
      if(this instanceof TitledVSAssemblyInfo) {
         VSCompositeFormat titleFormat = getFormatInfo().getFormat(TITLEPATH);

         if(titleFormat != null) {
            VSCSSFormat cssTitleFormat = titleFormat.getCSSFormat();
            CSSParameter[] cssParams = CSSParameter.getAllCSSParams(
               cssTitleFormat.getParentCSSParams(), cssTitleFormat.getCSSParam());
            CSSDictionary cssDictionary = CSSDictionary.getDictionary();

            if(cssDictionary.isPaddingDefined(cssParams)) {
               Insets padding = cssDictionary.getPadding(cssParams);
               ((TitledVSAssemblyInfo) this).setTitlePadding(padding, CompositeValue.Type.CSS);
            }
         }
      }
   }

   /**
    * Get the default font of the tittle.
    * @return
    */
   public void updateTitleDefaultFontSize() {
      if(!(this instanceof TitledVSAssemblyInfo)) {
         return;
      }

      FormatInfo formatInfo = getFormatInfo();

      if(formatInfo == null) {
         return;
      }

      VSCompositeFormat format = formatInfo.getFormat(TITLEPATH);

      if(format == null) {
         return;
      }

      Font fontValue = format.getDefaultFormat().getFontValue();

      if(fontValue == null) {
         return;
      }

      Font titleDefaultFont = getTitleDefaultFont();
      format.getDefaultFormat().setFontValue(
         new StyleFont(fontValue.getFamily(), fontValue.getStyle(), titleDefaultFont.getSize()));
   }

   /**
    * Get the default font of the tittle.
    * @return
    */
   private Font getTitleDefaultFont() {
      return getDefaultFont(Font.BOLD, 11);
   }

   /**
    * Get the default font for viewsheet.
    */
   public static Font getDefaultFont(Font font) {
      return getDefaultFont(font.getStyle(), font.getSize());
   }

   /**
    * Get the default font for viewsheet.
    */
   public static Font getDefaultFont(int style, int size) {
      String DEFAULT_FONT = StyleFont.DEFAULT_FONT_FAMILY;
      // don't cache, so change will take effect immediately
      String FONT_SIZE = SreeEnv.getProperty("viewsheet.font.size");

      if(FONT_SIZE != null) {
         if(FONT_SIZE.startsWith("+")) {
            size += Integer.parseInt(FONT_SIZE.substring(1));
         }
         else if(FONT_SIZE.startsWith("-")) {
            size -= Integer.parseInt(FONT_SIZE.substring(1));
         }
         else {
            size = Integer.parseInt(FONT_SIZE);
         }
      }

      return new StyleFont(DEFAULT_FONT, style, size);
   }

   /**
    * In cases that hyperlink linked asset does not match current orgID, replace orgID to match
    */
   public Hyperlink handleAssetLinkOrgMismatch(Hyperlink link) {
      String linkPath = link.getLinkValue();
      String curOrgId = OrganizationManager.getInstance().getCurrentOrgID();
      int orgIdx = linkPath.lastIndexOf("^");

      if(orgIdx > 0) {
         String linkOrg = linkPath.substring(orgIdx + 1);

         if(!Tool.equals(linkOrg, curOrgId)) {
            String updatedLink = linkPath.substring(0,orgIdx + 1) + curOrgId;
            link.setLink(updatedLink);

            return link;
         }
      }

      return link;
   }

   /**
    * Whether the assembly is scaled.
    * @return <tt>true</tt> if scaled, <tt>false</tt> otherwise.
    */
   public boolean isScaled() {
      return this.scaledPosition != null;
   }

   /**
    * Title table data path.
    */
   public static final TableDataPath TITLEPATH = new TableDataPath(-1, TableDataPath.TITLE);
   public static final TableDataPath OBJECTPATH = new TableDataPath(-1, TableDataPath.OBJECT);
   public static final TableDataPath SHEETPATH = new TableDataPath(-1, TableDataPath.SHEET);

   /**
    * Default border color for all VS components.
    */
   public static final Color DEFAULT_BORDER_COLOR = new Color(0xDADADA);
   public static final String DEFAULT_TITLE_BG = "0xf5f5f5";

   private String cls;
   private DynamicValue enabledValue;
   private DynamicValue visibleValue;
   private DynamicValue layoutVisibleValue;
   private String desc;
   private FormatInfo fmtInfo;
   private ClazzHolder<Hyperlink> linkValue = new ClazzHolder<>();
   private Hyperlink.Ref ref;
   private String aname;
   private Point pixelpos; // pixel position
   private Point layoutPosition; // pixel position for vslayout
   private Dimension layoutSize; // pixel size for vslayout
   private Point scaledPosition;
   private Dimension scaledSize;
   protected transient Viewsheet vs;
   private int zIndex;
   private String script;
   private boolean scriptEnabled = true;
   private boolean wizardTemporary = false;
   private boolean wizardEditing = false;
   private ObjectOpenHashSet<String> actionNames = new ObjectOpenHashSet<>(0);
   private boolean controlByScript = false; // visible is control by script
   private Insets padding = new Insets(0, 0, 0, 0);

   private static final Logger LOG = LoggerFactory.getLogger(VSAssemblyInfo.class);
}
