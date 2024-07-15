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
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.text.Format;
import java.util.List;
import java.util.*;

/**
 * OutputVSAssemblyInfo, the assembly info of an output assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class OutputVSAssemblyInfo extends VSAssemblyInfo
   implements ScalarBindableVSAssemblyInfo, BaseAnnotationVSAssemblyInfo
{
   /**
    * Set horizonatal style.
    */
   public static final int HORIZONTAL_STYLE = 0;
   /**
    * Set vertical style.
    */
   public static final int VERTICAL_STYLE = 1;

   /**
    * Constructor.
    */
   public OutputVSAssemblyInfo() {
      super();

      annotations = new ArrayList<>();
   }

   /**
    * Get the value.
    * @return the value.
    */
   public Object getValue() {
      return val;
   }

   /**
    * Set the value.
    * @param val the specified value.
    */
   public void setValue(Object val) {
      this.val = val;
   }

   /**
    * Called when the highlight or value is changed.
    */
   public void updateHighlight(VariableTable vars, Object querySandbox) {
      HighlightGroup highlightGroup = getHighlightGroup();
      highlightFg = null;
      highlightBg = null;
      highlightFont = null;

      if(highlightGroup != null) {
         Object val = getValue();

         if(val == null) {
            return;
         }

         updateHighlightCondition(Tool.getDataType(val.getClass()));
         highlightGroup.replaceVariables(vars);
         highlightGroup.setQuerySandbox(querySandbox);
         TextHighlight highlight = (TextHighlight) highlightGroup.findGroup(val);

         if(highlight != null) {
            highlightFg = highlight.getForeground();
            highlightBg = highlight.getBackground();
            highlightFont = highlight.getFont();
         }
      }
   }

   /**
    * Determines if a highlight is applied to the assembly.
    *
    * @param vars         the query variables.
    * @param querySandbox the query sandbox.
    *
    * @return <tt>true</tt> if a highlight is applied; <tt>false</tt> otherwise.
    */
   public boolean isHighlighted(VariableTable vars, Object querySandbox) {
      boolean result = false;
      HighlightGroup highlightGroup = getHighlightGroup();

      if(highlightGroup != null) {
         Object val = getValue();

         if(val != null) {
            updateHighlightCondition(Tool.getDataType(val.getClass()));
            highlightGroup.replaceVariables(vars);
            highlightGroup.setQuerySandbox(querySandbox);
            TextHighlight highlight =
               (TextHighlight) highlightGroup.findGroup(val);

            if(highlight != null) {
               result = true;
            }
         }
      }

      return result;
   }

   /**
    * Get the highlight foreground.
    */
   public Color getHighlightForeground() {
      return highlightFg;
   }

   /**
    * Get the highlight background.
    */
   public Color getHighlightBackground() {
      return highlightBg;
   }

   /**
    * Get the highlight font.
    */
   public Font getHighlightFont() {
      return highlightFont;
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
    * Get the scalar binding info.
    * @return the scalar binding info of this assembly info.
    */
   @Override
   public ScalarBindingInfo getScalarBindingInfo() {
      return binding;
   }

   /**
    * Set the scalar binding info to this assembly info.
    * @param binding the specified scalar binding info.
    */
   @Override
   public void setScalarBindingInfo(ScalarBindingInfo binding) {
      this.binding = binding;
   }

   /**
    * Get the default format.
    * @return the default format.
    */
   public Format getDefaultFormat() {
      return defaultFormat;
   }

   /**
    * Set the default format
    * @param format the specified default format.
    */
   public void setDefaultFormat(Format format) {
      this.defaultFormat = format;
   }

   /**
    * Set the xdrill info.
    * @param dinfo the specified xdrill info.
    */
   public void setXDrillInfo(XDrillInfo dinfo) {
      this.dinfo = dinfo;
   }

   /**
    * Get the xdrill info.
    * @return the xdrill info.
    */
   public XDrillInfo getXDrillInfo() {
      return dinfo;
   }

   /**
    * Set the uri.
    * @param uri the specified service request uri.
    */
   public void setLinkURI(String uri) {
      this.luri = uri;
   }

   /**
    * Get the specified service request uri.
    * @return the uri.
    */
   public String getLinkURI() {
      return this.luri;
   }

   /**
    * Set the highlight definitions for this assembly.
    */
   public void setHighlightGroup(HighlightGroup highlightGroup) {
      this.highlightGroup = highlightGroup;
   }

   /**
    * Get the highlight definitions for this assembly.
    */
   public HighlightGroup getHighlightGroup() {
      return highlightGroup;
   }

   /**
    * Set whether to draw drop shadow.
    */
   public void setShadow(boolean shadow) {
      this.shadow.setRValue(shadow);
   }

   /**
    * Check whether to draw drop shadow.
    */
   public boolean isShadow() {
      return "true".equals(shadow.getRuntimeValue(true) + "");
   }

   /**
    * Set whether to draw drop shadow.
    */
   public void setShadowValue(boolean shadow) {
      this.shadow.setDValue(shadow + "");
   }

   /**
    * Check whether to draw drop shadow.
    */
   public boolean getShadowValue() {
      return "true".equals(shadow.getDValue());
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();

      if(binding != null) {
         list.addAll(binding.getDynamicValues());
      }

      list.add(shadow);

      return list;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      super.renameDepended(oname, nname, vs);

      if(binding != null) {
         binding.renameDepended(oname, nname, vs);
      }
   }

   /**
    * Get hyperlink and drill info.
    */
   public abstract Hyperlink.Ref[] getHyperlinks();

   /**
    * Get hyperlink and drill info.
    */
   protected Hyperlink.Ref[] getHyperlinks(Object value) {
      Hyperlink.Ref[] refs = new Hyperlink.Ref[0];

      if(dinfo != null) {
         int size = dinfo.getDrillPathCount();
         refs = new Hyperlink.Ref[size];

         for(int k = 0; k < size; k++) {
            DrillPath path = dinfo.getDrillPath(k);
            DrillSubQuery query = path.getQuery();
            Map<String, String> drillMap = new Hashtable<>();
            Enumeration<String> pnames = path.getParameterNames();

            while(pnames.hasMoreElements()) {
               String name = pnames.nextElement();
               String field = path.getParameterField(name);
               String content =
                  field.equals(StyleConstants.COLUMN) ?
                  Tool.toString(value) :
                  field;
               drillMap.put(field, content);
            }

            drillMap.put(StyleConstants.COLUMN, Tool.toString(value));
            refs[k] = new Hyperlink.Ref(path, drillMap);

            if(query != null) {
               refs[k].setParameter(StyleConstants.SUB_QUERY_PARAM, value);
            }
         }
      }

      Hyperlink.Ref href = getHyperlinkRef(value);

      if(href == null) {
         return refs;
      }

      if(href.isSendReportParameters()) {
         addLinkParameter(href, linkVarTable);
      }

      if(href.isSendSelectionParameters()) {
         VSUtil.addSelectionParameter(href, selections);
      }

      Hyperlink.Ref[] hrefs = new Hyperlink.Ref[refs.length + 1];
      hrefs[0] = href;
      System.arraycopy(refs, 0, hrefs, 1, refs.length);

      return hrefs;
   }

   /**
    * Add hyperlink parameter.
    * @param hlink the hyperlink to be set parameters.
    * @param vtable the variable table from sand box.
    */
   private void addLinkParameter(Hyperlink.Ref hlink, VariableTable vtable) {
      if(vtable == null) {
         return;
      }

      Vector<String> exists = new Vector<>();
      Enumeration<String> pnames = hlink.getParameterNames();
      Enumeration<String> vnames = vtable.keys();

      while(pnames.hasMoreElements()) {
         exists.addElement(pnames.nextElement());
      }

      while(vnames.hasMoreElements()) {
         String name = vnames.nextElement();

         if(exists.contains(name) || VariableTable.isContextVariable(name)) {
            continue;
         }

         try {
            hlink.setParameter(name, vtable.get(name));
         }
         catch(Exception ignored) {
         }
      }
   }

   /**
    * Parse attributes.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      shadow.setDValue(Tool.getAttribute(elem, "shadowValue"));

      String tooltipVisible = Tool.getAttribute(elem, "tooltipVisible");

      if(!Tool.isEmptyString(tooltipVisible)) {
         setTooltipVisible("true".equals(tooltipVisible));
      }
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" shadow=\"" + isShadow() + "\"");
      writer.print(" shadowValue=\"" + getShadowValue() + "\"");

      if(highlightFg != null) {
         writer.print(" highlightFg=\"" + highlightFg.getRGB() + "\"");
      }

      if(highlightBg != null) {
         writer.print(" highlightBg=\"" + highlightBg.getRGB() + "\"");
      }

      if(highlightFont != null) {
         writer.print(" highlightFont=\"" +
                      StyleFont.toString(highlightFont) + "\"");
      }

      writer.print(" tooltipVisible=\"" + isTooltipVisible() + "\"");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      setHyperlinkRef(getHyperlinkRef());
      super.writeContents(writer);

      if(binding != null) {
         binding.writeXML(writer);
      }

      if(preconds != null && !preconds.isEmpty()) {
         preconds.writeXML(writer);
      }

      if(dinfo != null) {
         dinfo.writeXML(writer);
      }

      if(getValue() != null) {
         String str = Tool.toString(getValue());
         writer.print("<displayValue>");
         writer.print("<![CDATA[" + str + "]]>");
         writer.println("</displayValue>");
      }

      // write commands for vs assembly
      Hyperlink.Ref[] refs = getHyperlinks();
      writer.println("<hyperlinks>");

      for(Hyperlink.Ref ref : refs) {
         writer.println("<hyperlink>");

         writer.println("<name>");
         writer.println("<![CDATA[" + Tool.byteEncode2(ref.getName()) + "]]>");
         writer.println("</name>");

         String cmd = XUtil.getCommand(ref, getLinkURI());
         writer.println("<value>");
         writer.println("<![CDATA[" + Tool.byteEncode2(cmd) + "]]>");
         writer.println("</value>");

         String tooltip = ref.getToolTip();

         if(tooltip != null) {
            writer.print("<tooltip>");
            writer.println("<![CDATA[" + Tool.byteEncode2(tooltip) + "]]>");
            writer.print("</tooltip>");
         }

         writer.println("</hyperlink>");
      }

      writer.println("</hyperlinks>");

      if(highlightGroup != null) {
         writer.print("<highlightGroup>");
         highlightGroup.writeXML(writer);
         writer.println("</highlightGroup>");
      }

      if(annotations != null && !annotations.isEmpty()) {
         writer.print("<annotations>");

         for(String annotation : annotations) {
            writer.print("<annotation>");
            writer.print("<![CDATA[" + annotation + "]]>");
            writer.print("</annotation>");
         }

         writer.println("</annotations>");
      }

      if(customTooltipString != null) {
         writer.print("<customTooltipStr>");
         writer.print("<![CDATA[" + getCustomTooltipString() + "]]>");
         writer.println("</customTooltipStr>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element bnode = Tool.getChildNodeByTagName(elem, "bindingInfo");

      if(bnode != null) {
         binding = new ScalarBindingInfo();
         binding.parseXML(bnode);
      }

      Element cnode = Tool.getChildNodeByTagName(elem, "conditions");

      if(cnode != null) {
         preconds = new ConditionList();
         preconds.parseXML(cnode);
      }

      Element dnode = Tool.getChildNodeByTagName(elem, "XDrillInfo");

      if(dnode != null) {
         dinfo = new XDrillInfo();
         dinfo.parseXML(dnode);
      }

      Element hnode = Tool.getChildNodeByTagName(elem, "highlightGroup");

      if(hnode != null) {
         highlightGroup = new HighlightGroup();
         highlightGroup.parseXML((Element) hnode.getFirstChild());
      }

      Element anode = Tool.getChildNodeByTagName(elem, "annotations");

      if(anode != null) {
         NodeList alist = Tool.getChildNodesByTagName(anode, "annotation");

         if(alist != null && alist.getLength() > 0) {
            annotations = new ArrayList<>();

            for(int i = 0; i < alist.getLength(); i++) {
               annotations.add(Tool.getValue(alist.item(i)));
            }
         }
      }

      anode = Tool.getChildNodeByTagName(elem, "customTooltipStr");

      if(Tool.getValue(anode) != null) {
         setCustomTooltipString(Tool.getValue(anode));
      }
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public OutputVSAssemblyInfo clone(boolean shallow) {
      try {
         OutputVSAssemblyInfo info = (OutputVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(binding != null) {
               info.binding = (ScalarBindingInfo) binding.clone();
            }

            if(dinfo != null) {
               info.dinfo = (XDrillInfo) dinfo.clone();
            }

            if(highlightGroup != null) {
               info.highlightGroup = (HighlightGroup) highlightGroup.clone();
            }

            if(annotations != null) {
               info.annotations = Tool.deepCloneCollection(annotations);
            }

            info.shadow = (DynamicValue) shadow.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone OutputVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      OutputVSAssemblyInfo oinfo = (OutputVSAssemblyInfo) info;

      if(!Tool.equals(binding, oinfo.binding)) {
         binding = oinfo.binding;
         fireBindingEvent();
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      hint = setPreConditionList0(oinfo.preconds, hint);

      return hint;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      OutputVSAssemblyInfo oinfo = (OutputVSAssemblyInfo) info;

      if(!Tool.equals(shadow, oinfo.shadow)) {
         shadow = oinfo.shadow;
         result = true;
      }

      if(!Tool.equals(highlightGroup, oinfo.highlightGroup)) {
         highlightGroup = oinfo.highlightGroup;
         result = true;
      }

      if(!Tool.equals(tooltipVisible, oinfo.tooltipVisible)) {
         tooltipVisible = oinfo.tooltipVisible;
         result = true;
      }

      if(!Tool.equals(customTooltipString, oinfo.customTooltipString)) {
         customTooltipString = oinfo.customTooltipString;
         result = true;
      }

      return result;
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   @Override
   public void update(Viewsheet vs, ColumnSelection columns) throws Exception {
      super.update(vs, columns);

      if(binding != null) {
         binding.update(vs);
      }
   }

   /**
    * Get hyperlink ref.
    */
   protected Hyperlink.Ref getHyperlinkRef(Object value) {
      Hyperlink.Ref ref = null;
      Hyperlink link = getHyperlink();

      if(link != null) {
         ScalarBindingInfo info = getScalarBindingInfo();
         DataRef column = info == null ? null : info.getColumn();
         Map<String, Object> map = new HashMap<>();

         if(value != null && column != null) {
            map.put(column.getAttribute(), value);
         }

         map.put(StyleConstants.COLUMN, value);
         ref = new Hyperlink.Ref(link, map);
      }

      return ref;
   }

   /**
    * Change the highLight condition type from strng to double.
    * @param type the specified viewsheet.
    */
   private void updateHighlightCondition(String type) {
      String[] names = highlightGroup.getNames();

      if(names != null) {
         for(String name : names) {
            TextHighlight highlight = (TextHighlight) highlightGroup.getHighlight(name);
            ConditionList list = highlight.getConditionGroup().getConditionList();

            if(list == null) {
               break;
            }

            for(int j = 0; j < list.getSize(); j++) {
               if(list.getXCondition(j) != null &&
                  !type.equals(list.getXCondition(j).getType()))
               {
                  list.getXCondition(j).setType(type);
               }
            }
         }
      }
   }

   /**
    * Set hyperlink variable table. The variable is used when
    * "Send viewsheet parameter" is checked.
    */
   public void setLinkVarTable(VariableTable vtable) {
      this.linkVarTable = vtable;
   }

   /**
    * Get the hyperlink variable table.
    */
   public VariableTable getLinkVarTable() {
      return linkVarTable;
   }

   /**
    * Set selections.
    */
   public void setLinkSelections(Hashtable<String, SelectionVSAssembly> sel) {
      selections = sel;
   }

   /**
    * Get selections.
    * @return selection assembly map.
    */
   public Hashtable<String, SelectionVSAssembly> getLinkSelections() {
      return selections;
   }

   /**
    * Copy condition list defined in this data viewsheet assembly.
    */
   private int setPreConditionList0(ConditionList npreconds, int hint) {
      if(npreconds != null && npreconds.isEmpty()) {
         npreconds = null;
      }

      if(!Tool.equals(preconds, npreconds)) {
         preconds = npreconds;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Set the pre-condition list defined in this data viewsheet assembly.
    */
   public int setPreConditionList(ConditionList preconds) {
      return setPreConditionList0(preconds, 0);
   }

   /**
    * Get the pre-condition list.
    */
   public ConditionList getPreConditionList() {
      return preconds;
   }

   public boolean isTooltipVisible() {
      return tooltipVisible;
   }

   public void setTooltipVisible(boolean tooltipVisible) {
      this.tooltipVisible = tooltipVisible;
   }

   public String getCustomTooltipString() {
      return customTooltipString;
   }

   public void setCustomTooltipString(String customTooltipString) {
      this.customTooltipString = customTooltipString;
   }

   /**
    * Set enabled status with higher priority than normal enabled status.
    */
   public void setOutputEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   /**
    * Check if the assembly is enabled.
    * @return <tt>true</tt> if enabled, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEnabled() {
      return enabled && super.isEnabled();
   }

   /**
    * Get annotations.
    */
   @Override
   public List<String> getAnnotations() {
      return annotations;
   }

   /**
    * Add a annotation.
    * @param name the specified annotation name.
    */
   @Override
   public void addAnnotation(String name) {
      if(!annotations.contains(name)) {
         annotations.add(name);
      }
   }

   /**
    * Remove the annotation.
    * @param name the specified annotation name.
    */
   @Override
   public void removeAnnotation(String name) {
      annotations.remove(name);
   }

   /**
    * Remove all annotations.
    */
   @Override
   public void clearAnnotations() {
      annotations.clear();
   }

   // input data
   private ScalarBindingInfo binding;
   // view
   private DynamicValue shadow = new DynamicValue("false", XSchema.BOOLEAN);
   private HighlightGroup highlightGroup = new HighlightGroup();

   // runtime
   private Object val;
   private Format defaultFormat;
   private XDrillInfo dinfo;
   private String luri;
   // highlight attributes are kept separately so they don't interfere with
   // design-time settings
   private Color highlightFg;
   private Color highlightBg;
   private Font highlightFont;
   private VariableTable linkVarTable; // the variable for hyperlink
   private transient Hashtable<String, SelectionVSAssembly> selections;
   private ConditionList preconds; // pre filter
   private boolean enabled = true;
   private List<String> annotations;
   private boolean tooltipVisible = true;
   private String customTooltipString;

   private static final Logger LOG = LoggerFactory.getLogger(OutputVSAssemblyInfo.class);
}
