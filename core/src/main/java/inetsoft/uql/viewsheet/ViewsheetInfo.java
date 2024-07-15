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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.internal.VSCustomizedAction;
import inetsoft.util.OrderedMap;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import inetsoft.util.xml.VersionControlComparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * ViewsheetInfo stores the current viewsheet properties.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ViewsheetInfo implements AssetObject {
   /**
    * Generation on demand.
    */
   public static final int GENERATION_ON_DEMAND = 1;
   /**
    * Embedded mv.
    */
   public static final int EMBEDDED_MV = 2;

   /**
    * Constructor.
    */
   public ViewsheetInfo() {
      super();
      idmap = new OrderedMap<>();
      localMap = new HashMap<>();
      disVariables = new ArrayList<>();
      orderedVariables = new ArrayList<>();
      String prop = SreeEnv.getProperty("asset.sample.maxrows");

      if(prop.length() > 0) {
         try {
            maxrows = Integer.parseInt(prop);
         }
         catch(Exception ex) {
            LOG.error("Invalid asset.sample.maxrows: " + prop, ex);
         }
      }

      prop = SreeEnv.getProperty("mv.enabled.all");
      //bug1397630133880, mv type is for bc, so it need not init.
//      mv = "true".equals(prop) ? EMBEDDED_MV : NONE_MV;
      mvOnDemand = "true".equals(prop);
      cssFmt = new VSCSSFormat();
      cssFmt.setCSSType(CSSConstants.VIEWSHEET);
   }

   /**
    * Check if this viewsheet is created as a dashboard.
    */
   public boolean isComposedDashboard() {
      return composed;
   }

   /**
    * Set whether this viewsheet is created as a dashboard.
    */
   public void setComposedDashboard(boolean composed) {
      this.composed = composed;
   }

   /**
    * Get the type of materialized view.
    */
   public int getMVType() {
      return mv;
   }

   /**
    * Set the type of materialized view.
    */
   public void setMVType(int mv) {
      this.mv = mv;
   }

   /**
    * Return if create Materialized View for all users in the groups or not.
    */
   public boolean isGroupExpanded() {
      return groupExpanded;
   }

   /**
    * Set if create Materialized View for all users in the groups or not
    */
   public void setGroupExpanded(boolean expanded) {
      groupExpanded = expanded;
   }

   /**
    * Check if the VPM should be ignored when generating MV.
    */
   public boolean isBypassVPM() {
      return bypass;
   }

   /**
    * Set if the VPM should be ignored when generating MV.
    */
   public void setBypassVPM(boolean bypass) {
      this.bypass = bypass;
   }

   /**
    * Check if the MV should contain full data.
    */
   public boolean isFullData() {
      return full;
   }

   /**
    * Set if the MV should contain full data. If not, only data that's
    * necessary for the current viewsheet is included. If the viewsheet
    * is edited, the MV may not be usable anymore.
    */
   public void setFullData(boolean full) {
      this.full = full;
   }

   /**
    * Check if it is on report portal.
    * @return <tt>true</tt> if on report portal, <tt>false</tt> otherwise.
    */
   public boolean isOnReport() {
      return report;
   }

   /**
    * Set whether it is on report portal.
    * @param report <tt>true</tt> if on report potal, <tt>false</tt> otherwise.
    */
   public void setOnReport(boolean report) {
      this.report = report;
   }

   /**
    * Check if it is enable server-side update.
    * @return <tt>true</tt> if enable server-side update,
    * <tt>false</tt> otherwise.
    */
   public boolean isUpdateEnabled() {
      return updateFlag;
   }

   /**
    * Set whether it is enable server-side update.
    * @param updateFlag <tt>true</tt> if enable server-side update,
    *                   <tt>false</tt> otherwise.
    */
   public void setUpdateEnabled(boolean updateFlag) {
      this.updateFlag = updateFlag;
   }

   /**
    * Check if it is enable prompt parameterSheet.
    * @return <tt>true</tt> if enable prompt parameterSheet,
    * <tt>false</tt> otherwise.
    */
   public boolean isDisableParameterSheet() {
      return disableFlag;
   }

   /**
    * Set whether it is enable prompt parameterSheet.
    * @param dFlag <tt>true</tt> if enable prompt parameterSheet,
    *              <tt>false</tt> otherwise.
    */
   public void setDisableParameterSheet(boolean dFlag) {
      this.disableFlag = dFlag;
   }

   /**
    * Check whether to use metadata for editing.
    */
   public boolean isMetadata() {
      return meta;
   }

   /**
    * Set whether to use metadata for editing.
    */
   public void setMetadata(boolean meta) {
      this.meta = meta;
   }

   /**
    * Check whether to prompt not hit mv message.
    */
   public boolean isWarningIfNotHitMV() {
      return warningIfNotHitMV;
   }

   /**
    * Set whether to prompt not hit mv message.
    */
   public void setWarningIfNotHitMV(boolean warn) {
      this.warningIfNotHitMV = warn;
   }

   /**
    * Check if the max row warning should be added as a text at the bottom of viewsheet.
    */
   public boolean isMaxRowsWarning() {
      return maxRowsWarning;
   }

   /**
    * Set whether the max row warning should be added as a text at the bottom of viewsheet.
    */
   public void setMaxRowsWarning(boolean maxRowsWarning) {
      this.maxRowsWarning = maxRowsWarning;
   }

   /**
    * Get the interval time of fire TouchAssetEvent.
    */
   public int getTouchInterval() {
      return touchInterval;
   }

   /**
    * Set the interval time of fire TouchAssetEvent.
    */
   public void setTouchInterval(int touchInterval) {
      this.touchInterval = touchInterval;
   }

   /**
    * Get the description of the viewsheet.
    */
   public String getDescription() {
      return desc;
   }

   /**
    * Set the description of the viewsheet.
    */
   public void setDescription(String desc) {
      this.desc = desc;
   }

   /**
    * Get viewsheet generation type.
    */
   public int getGenerationType() {
      return gtype;
   }

   /**
    * Set viewsheet generation type defined in this class.
    */
   public void setGenerationType(int type) {
      this.gtype = type;
   }

   /**
    * Get local components.
    */
   public String[] getLocalComponents() {
      String[] arr = new String[localMap.size()];
      localMap.keySet().toArray(arr);

      return arr;
   }

   /**
    * Get local ID.
    */
   public String getLocalID(String component) {
      return localMap.get(component);
   }

   /**
    * Get all the local text ids defined in this viewsheet.
    */
   public Collection<String> getLocalIDs() {
      return localMap.values();
   }

   /**
    * Set local text ID.
    */
   public void setLocalID(String component, String id) {
      if(id == null || id.length() == 0) {
         localMap.remove(component);
      }
      else {
         localMap.put(component, id);
      }
   }

   /**
    * Remove the component in the local map when the specified name remove.
    * @param oname the old name.
    * @param all   remove all component which prefix start with oname.
    */
   public void removeLocalID(String oname, boolean all) {
      String[] onames = Tool.split(oname, "^_^", false);
      String[] components = getLocalComponents();

      for(String key : components) {
         String[] names = Tool.split(key, "^_^", false);

         if(onames.length == 1 && Tool.equals(names[0], onames[0])) {
            if(all) {
               localMap.remove(key);
               continue;
            }

            if(names.length == 1) {
               localMap.remove(key);
               break;
            }
         }
         else if(Tool.equals(onames, names)) {
            localMap.remove(key);
            break;
         }
      }
   }

   /**
    * Rename the component in the local map when the specified name rename.
    * @param oname the old name.
    * @param nname the new name.
    */
   public void renameLocalID(String oname, String nname) {
      String[] onames = Tool.split(oname, "^_^", false);

      for(String key : localMap.keySet()) {
         String value = localMap.get(key);
         String[] names = Tool.split(key, "^_^", false);

         if(onames.length == 1 && Tool.equals(names[0], onames[0])) {
            String nkey = names.length == 1 ? nname : nname + "^_^" + names[1];
            localMap.put(nkey, value);
            localMap.remove(key);
         }
         else if(Tool.equals(onames, names)) {
            String nkey = nname + "^_^" + names[1];
            localMap.put(nkey, value);
            localMap.remove(key);
            break;
         }
      }
   }

   /**
    * Get filter columns.
    */
   public String[] getFilterColumns() {
      String[] arr = new String[idmap.size()];
      idmap.keySet().toArray(arr);

      return arr;
   }

   /**
    * Get all the filter ids defined in this viewsheet.
    */
   public Collection<String> getFilterIDs() {
      return idmap.values();
   }

   /**
    * Get filter ID.
    */
   public String getFilterID(String column) {
      return idmap.get(column);
   }

   /**
    * Set filter ID.
    */
   public void setFilterID(String column, String id) {
      if(id == null || id.length() == 0) {
         idmap.remove(column);
      }
      else {
         idmap.put(column, id);
      }
   }

   /**
    * Get the filter column of a filter id.
    * @param id the filter id.
    * @return the filter columns with the filter id.
    */
   public List<String> getFilterColumns(String id) {
      if(id == null) {
         return null;
      }

      List<String> list = new ArrayList<>();

      for(String column : idmap.keySet()) {
         Object id2 = idmap.get(column);

         if(id.equals(id2)) {
            list.add(column);
         }
      }

      return list;
   }

   /**
    * Get refresh rate.
    */
   public int getRefreshRate() {
      return rate;
   }

   /**
    * Set refresh rate.
    */
   public void setRefreshRate(int rate) {
      this.rate = rate;
   }

   /**
    * Get pre-generation schedule cycle.
    */
   public String getScheduleCycle() {
      return cycle;
   }

   /**
    * Set pre-generation schedule cycle.
    */
   public void setScheduleCycle(String cycle) {
      this.cycle = cycle;
   }

   /**
    * Get schedule identities.
    */
   public Identity[] getScheduleIdentities() {
      return identities;
   }

   /**
    * Set schedule identities.
    */
   public void setScheduleIdentities(Identity[] identities) {
      this.identities = identities;
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
    * Get the oninit script.
    */
   public String getOnInit() {
      return initScript;
   }

   /**
    * Set the onit script.
    */
   public void setOnInit(String initScript) {
      this.initScript = initScript;
   }

   /**
    * Get the oninit script.
    */
   public String getOnLoad() {
      return loadScript;
   }

   /**
    * Set the onit script.
    */
   public void setOnLoad(String loadScript) {
      this.loadScript = loadScript;
   }

   /**
    * Get the maximum rows of detail table for design mode.
    */
   public int getDesignMaxRows() {
      return maxrows;
   }

   /**
    * Set the maximum rows of detail table for design mode. The max rows is
    * applied to the detail table to limit the data used for not worksheet.
    */
   public void setDesignMaxRows(int maxrows) {
      this.maxrows = maxrows;
   }

   public int getSnapGrid() {
      return snapGrid;
   }

   public void setSnapGrid(int snapGrid) {
      this.snapGrid = snapGrid;
   }

   /**
    * Check whether to use template.
    */
   public boolean isTemplateEnabled() {
      return template;
   }

   /**
    * Set whether to use template.
    */
   public void setTemplateEnabled(boolean template) {
      this.template = template;
   }

   /**
    * Get template width in pixel.
    */
   public int getTemplateWidth() {
      return templateWidth;
   }

   /**
    * Set template width in pixel.
    */
   public void setTemplateWidth(int width) {
      templateWidth = width;
   }

   /**
    * Get template height in pixel.
    */
   public int getTemplateHeight() {
      return templateHeight;
   }

   /**
    * Set template height in pixel.
    */
   public void setTemplateHeight(int height) {
      templateHeight = height;
   }

   /**
    * Add a disabled variable name.
    */
   public void addDisabledVariable(String variableName) {
      disVariables.add(variableName);
   }

   /**
    * Remove a disabled variable name.
    * @return <tt>true</tt> if remove success, <tt>false</tt> otherwise.
    */
   public boolean removeDisabledVariable(String variableName) {
      return disVariables.remove(variableName);
   }

   /**
    * Get all disabled variable names.
    */
   public String[] getDisabledVariables() {
      return disVariables.toArray(new String[0]);
   }

   /**
    * If the variable name is disabled.
    */
   public boolean isDisabledVariable(String variableName) {
      return disVariables.contains(variableName);
   }

   /**
    * Get all enabled variable names in the order they will appear.
    */
   public String[] getOrderedVariables() {
      return orderedVariables.toArray(new String[0]);
   }

   /**
    * Set the order the variable names should appear in.
    */
   public void setOrderedVariables(List<String> orderedVariableNames) {
      this.orderedVariables.clear();
      this.orderedVariables.addAll(orderedVariableNames);
   }

   /**
    * Check if this viewsheet should be scaled to screen.
    */
   public boolean isScaleToScreen() {
      return scaleToScreen;
   }

   /**
    * Set if this viewsheet should be scaled to screen.
    */
   public void setScaleToScreen(boolean scale) {
      this.scaleToScreen = scale;
   }

   /**
    * Check if this viewsheet should fit to width when scaled to screen.
    */
   public boolean isFitToWidth() {
      return fitToWidth;
   }

   /**
    * Set if this viewsheet should fit to width when scaled to screen.
    */
   public void setFitToWidth(boolean fit) {
      this.fitToWidth = fit;
   }

   /**
    * Set if this viewsheet should create MV on demand.
    */
   public void setMVOnDemand(boolean mvOnDemand) {
      this.mvOnDemand = mvOnDemand;
   }

   /**
    * Check if this viewsheet should create MV on demand.
    */
   public boolean isMVOnDemand() {
      return mvOnDemand;
   }

   /**
    * Set if the selection association should be enabled.
    */
   public void setAssociationEnabled(boolean flag) {
      this.associationEnabled = flag;
   }

   /**
    * Check if the selection association should be enabled.
    */
   public boolean isAssociationEnabled() {
      return associationEnabled;
   }

   public boolean isBalancePadding() {
      return balancePadding;
   }

   public void setBalancePadding(boolean balancePadding) {
      this.balancePadding = balancePadding;
   }

   public String[] getMessageLevels() {
      return messageLevels;
   }

   public void setMessageLevels(String[] messageLevels) {
      this.messageLevels = messageLevels;
   }

   public VSCSSFormat getCSSFormat() {
      return cssFmt;
   }

   /**
    * Add user action.
    */
   public void addUserAction(String icon, String label, String event) {
      VSCustomizedAction action = new VSCustomizedAction(icon, label, event);

      if(!actions.contains(action)) {
         actions.add(action);
      }
   }

   /**
    * Write the xml segment to print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<viewsheetInfo class=\"" + getClass().getName() + "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</viewsheetInfo>");
   }

   /**
    * Write attributes.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" composed=\"" + composed + "\"");
      writer.print(" report=\"" + report + "\"");
      writer.print(" updateFlag=\"" + updateFlag + "\"");
      writer.print(" disableFlag=\"" + disableFlag + "\"");
      writer.print(" meta=\"" + meta + "\"");
      writer.print(" maxRowsWarning=\"" + maxRowsWarning+ "\"");
      writer.print(" warningIfNotHitMV=\"" + warningIfNotHitMV + "\"");
      writer.print(" touchInterval=\"" + touchInterval + "\"");
      writer.print(" maxrows=\"" + maxrows + "\"");
      writer.print(" scaleToScreen=\"" + scaleToScreen + "\"");
      writer.print(" fitToWidth=\"" + fitToWidth + "\"");
      writer.print(" snapGrid=\"" + snapGrid + "\"");
      writer.print(" balancePadding=\"" + balancePadding + "\"");

      if(desc != null) {
         writer.print(" description=\"" +
                         Tool.escape(Tool.encodeNL(desc)) + "\"");
      }

      writer.print(" groupExpanded=\"" + groupExpanded + "\"");
      writer.print(" bypass=\"" + bypass + "\"");
      writer.print(" full=\"" + full + "\"");
      writer.print(" gtype=\"" + gtype + "\"");
      writer.print(" rate=\"" + rate + "\"");
      writer.print(" template=\"" + template + "\"");
      writer.print(" templateWidth=\"" + templateWidth + "\"");
      writer.print(" templateHeight=\"" + templateHeight + "\"");
      writer.print(" scriptEnabled=\"" + scriptEnabled + "\"");
      writer.print(" mvOnDemand=\"" + mvOnDemand + "\"");
      writer.print(" associationEnabled=\"" + associationEnabled + "\"");

      StringBuilder levels = new StringBuilder();

      for(int i = 0; i < messageLevels.length; i++) {
         levels.append(i != messageLevels.length - 1 ? messageLevels[i] + "," : messageLevels[i]);
      }

      writer.print(" messageLevels=\"" + levels + "\"");
   }

   /**
    * Parse attributes.
    */
   protected void parseAttributes(Element elem) {
      this.composed = "true".equals(Tool.getAttribute(elem, "composed"));
      this.report = "true".equals(Tool.getAttribute(elem, "report"));
      this.updateFlag = "true".equals(Tool.getAttribute(elem, "updateFlag"));
      this.disableFlag = !"false".equals(Tool.getAttribute(elem, "disableFlag"));
      this.meta = "true".equals(Tool.getAttribute(elem, "meta"));
      this.scaleToScreen = "true".equals(Tool.getAttribute(elem, "scaleToScreen"));
      this.fitToWidth = "true".equals(Tool.getAttribute(elem, "fitToWidth"));
      this.warningIfNotHitMV = !"false".equals(Tool.getAttribute( elem, "warningIfNotHitMV"));
      String prop;

      prop = Tool.getAttribute(elem, "maxRowsWarning");

      // for backward compatibility so different customers may choose different preference.
      // don't document. possible removal in the future. (added in 13.5)
      if(prop == null) {
         prop = SreeEnv.getProperty("viewsheet.maxrows.warning.text", "false");
      }

      this.maxRowsWarning= "true".equals(prop);

      if((prop = Tool.getAttribute(elem, "touchInterval")) != null) {
         this.touchInterval = Integer.parseInt(prop);

         if(touchInterval > 86400) {
            LOG.warn("Invalid touch interval ({}), maximum is 86400", touchInterval);
            touchInterval = 86400;
         }
      }

      if((prop = Tool.getAttribute(elem, "maxrows")) != null) {
         this.maxrows = Integer.parseInt(prop);
      }

      if((prop = Tool.getAttribute(elem, "snapGrid")) != null) {
         this.snapGrid = Integer.parseInt(prop);
      }

      this.desc = Tool.decodeNL(Tool.getAttribute(elem, "description"));

      if((prop = Tool.getAttribute(elem, "mv")) != null) {
         this.mv = Integer.parseInt(prop);
      }

      groupExpanded = "true".equals(Tool.getAttribute(elem, "groupExpanded"));
      bypass = "true".equals(Tool.getAttribute(elem, "bypass"));
      full = "true".equals(Tool.getAttribute(elem, "full"));
      this.gtype = Integer.parseInt(Tool.getAttribute(elem, "gtype"));
      this.rate = Integer.parseInt(Tool.getAttribute(elem, "rate"));

      if((prop = Tool.getAttribute(elem, "template")) != null) {
         this.template = "true".equals(prop);
      }

      if((prop = Tool.getAttribute(elem, "templateWidth")) != null) {
         this.templateWidth = Integer.parseInt(prop);
      }

      if((prop = Tool.getAttribute(elem, "templateHeight")) != null) {
         this.templateHeight = Integer.parseInt(prop);
      }

      if((prop = Tool.getAttribute(elem, "scriptEnabled")) != null) {
         this.scriptEnabled = !"false".equals(prop);
      }

      this.mvOnDemand = "true".equals(Tool.getAttribute(elem, "mvOnDemand"));
      this.associationEnabled =
         !"false".equals(Tool.getAttribute(elem, "associationEnabled"));

      //bug1397630133880, for bc.
      if(mv == EMBEDDED_MV) {
         mvOnDemand = true;
      }

      if((prop = Tool.getAttribute(elem, "balancePadding")) != null) {
         this.balancePadding = "true".equals(prop);
      }

      if((prop = Tool.getAttribute(elem, "messageLevels")) != null) {
         this.messageLevels = prop.split(",");
      }
   }

   /**
    * Write contents.
    */
   protected void writeContents(PrintWriter writer) {
      if(idmap != null && idmap.size() > 0) {
         writer.print("<idmap>");

         for(String key : idmap.keySet()) {
            writer.print("<filter>");
            writer.print("<column>");
            writer.print("<![CDATA[" + key + "]]>");
            writer.print("</column>");

            writer.print("<id>");
            writer.print("<![CDATA[" + idmap.get(key) + "]]>");
            writer.print("</id>");
            writer.println("</filter>");
         }

         writer.println("</idmap>");
      }

      if(localMap != null && localMap.size() > 0) {
         writer.print("<localMap>");

         for(Map.Entry<String, String> entry
            : VersionControlComparators.sortStringKeyMap(localMap)) {
            writer.print("<localize>");
            writer.print("<column>");
            writer.print("<![CDATA[" + entry.getKey() + "]]>");
            writer.print("</column>");

            writer.print("<textId>");
            writer.print("<![CDATA[" + entry.getValue() + "]]>");
            writer.print("</textId>");
            writer.println("</localize>");
         }

         writer.println("</localMap>");
      }

      if(identities != null && identities.length > 0) {
         writer.print("<identities>");

         for(Identity identity : identities) {
            DefaultIdentity id = new DefaultIdentity(identity);
            id.writeXML(writer);
         }

         writer.println("</identities>");
      }

      if(initScript != null) {
         writer.print("<initScript>");
         writer.print("<![CDATA[" + initScript + "]]>");
         writer.println("</initScript>");
      }

      if(loadScript != null) {
         writer.print("<loadScript>");
         writer.print("<![CDATA[" + loadScript + "]]>");
         writer.println("</loadScript>");
      }

      writer.print("<cycle>");
      writer.print("<![CDATA[" + cycle + "]]>");
      writer.println("</cycle>");

      if(disVariables != null && disVariables.size() > 0) {
         writer.print("<disVariables>");

         for(String disName : disVariables) {
            writer.print("<disName>");
            writer.print("<![CDATA[" + disName + "]]>");
            writer.print("</disName>");
         }

         writer.println("</disVariables>");
      }

      if(orderedVariables != null && orderedVariables.size() > 0) {
         writer.print("<orderedVariables>");

         for(String varName : orderedVariables) {
            writer.print("<varName>");
            writer.print("<![CDATA[" + varName + "]]>");
            writer.print("</varName>");
         }

         writer.println("</orderedVariables>");
      }

      writer.println("<actions>");

      for(VSCustomizedAction action : actions) {
         action.writeXML(writer);
      }

      writer.println("</actions>");

      if(cssFmt.isBackgroundValueDefined()) {
         cssFmt.writeXML(writer);
      }
   }

   /**
    * Parse contents.
    */
   protected void parseContents(Element elem) throws Exception {
      Element mapNode = Tool.getChildNodeByTagName(elem, "idmap");

      if(mapNode != null) {
         NodeList filterList = Tool.getChildNodesByTagName(mapNode, "filter");
         idmap.clear();

         if(filterList.getLength() > 0) {
            for(int i = 0; i < filterList.getLength(); i++) {
               Element filterNode = (Element) filterList.item(i);
               Element cnode = Tool.getChildNodeByTagName(filterNode, "column");
               Element idNode = Tool.getChildNodeByTagName(filterNode, "id");
               String column = Tool.getValue(cnode);
               String id = Tool.getValue(idNode);

               if(id == null) {
                  continue;
               }

               idmap.put(column, id);
            }
         }
      }

      Element localNode = Tool.getChildNodeByTagName(elem, "localMap");

      if(localNode != null) {
         NodeList localList = Tool.getChildNodesByTagName(
            localNode, "localize");
         localMap.clear();

         if(localList.getLength() > 0) {
            for(int i = 0; i < localList.getLength(); i++) {
               Element localMapNode = (Element) localList.item(i);
               Element cnode = Tool.getChildNodeByTagName(
                  localMapNode, "column");
               Element idNode = Tool.getChildNodeByTagName(
                  localMapNode, "textId");
               String column = Tool.getValue(cnode);
               String id = Tool.getValue(idNode);

               if(id == null) {
                  continue;
               }

               localMap.put(column, id);
            }
         }
      }

      Element identitiesNode = Tool.getChildNodeByTagName(elem, "identities");

      if(identitiesNode != null) {
         NodeList identitiesList =
            Tool.getChildNodesByTagName(identitiesNode, "defaultIdentity");

         if(identitiesList.getLength() > 0) {
            identities = new Identity[identitiesList.getLength()];

            for(int i = 0; i < identitiesList.getLength(); i++) {
               Element idNode = (Element) identitiesList.item(i);
               DefaultIdentity id = new DefaultIdentity();
               id.parseXML(idNode);
               identities[i] = id;
            }
         }
      }

      Element initNode = Tool.getChildNodeByTagName(elem, "initScript");

      if(initNode != null) {
         initScript = Tool.getValue(initNode, true);
      }

      Element loadNode = Tool.getChildNodeByTagName(elem, "loadScript");

      if(loadNode != null) {
         loadScript = Tool.getValue(loadNode, true);
      }

      Element cycleNode = Tool.getChildNodeByTagName(elem, "cycle");
      cycle = Tool.getValue(cycleNode);
      Element disVariableNode = Tool.getChildNodeByTagName(elem, "disVariables");

      if(disVariableNode != null) {
         NodeList disVariableList = Tool.getChildNodesByTagName(disVariableNode,
                                                                "disName");
         disVariables.clear();

         if(disVariableList.getLength() > 0) {
            for(int i = 0; i < disVariableList.getLength(); i++) {
               Element disNameNode = (Element) disVariableList.item(i);
               String name = Tool.getValue(disNameNode);

               if(name == null || "".equals(name)) {
                  continue;
               }

               disVariables.add(name);
            }
         }
      }

      Element orderedVariablesNode = Tool.getChildNodeByTagName(elem, "orderedVariables");

      if(orderedVariablesNode != null) {
         NodeList orderedVariablesList =
            Tool.getChildNodesByTagName(orderedVariablesNode, "varName");
         orderedVariables.clear();

         if(orderedVariablesList.getLength() > 0) {
            for(int i = 0; i < orderedVariablesList.getLength(); i++) {
               Element varNameNode = (Element) orderedVariablesList.item(i);
               String varName = Tool.getValue(varNameNode);

               if(varName == null || "".equals(varName)) {
                  continue;
               }

               orderedVariables.add(varName);
            }
         }
      }
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public final void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString() + "[" + composed + ", " + report + ", " +
         updateFlag + ", " + touchInterval + ", " +
         desc + ", " + idmap + ", " + localMap + ", " + gtype + ", " + rate + ", " +
         initScript + ", " + loadScript + ", " + cycle + ", " +
         Tool.arrayToString(identities) + "]";
   }

   /**
    * Clone this object.
    */
   @Override
   public Object clone() {
      try {
         ViewsheetInfo info = (ViewsheetInfo) super.clone();

         if(identities != null) {
            info.identities = new Identity[identities.length];

            for(int i = 0; i < identities.length; i++) {
               info.identities[i] = (Identity) identities[i].clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ViewsheetInfo", ex);
      }

      return null;
   }

   public List<VSCustomizedAction> getUserActions() {
      return actions;
   }

   private boolean report = true;
   private boolean updateFlag = false;
   private boolean disableFlag = false;
   private boolean composed = false;
   private boolean meta = false;
   private boolean warningIfNotHitMV = false;
   private boolean maxRowsWarning;
   private int touchInterval = 10; // the unit is second
   private String desc;
   private final Map<String, String> idmap;
   private int gtype = GENERATION_ON_DEMAND;
   private int rate = 0;
   private String cycle = "Weekly";
   private Identity[] identities = {};
   private int mv;
   private boolean groupExpanded = false;
   private boolean bypass = false;
   private boolean full = false;
   private String initScript;
   private String loadScript;
   private int maxrows = 0;
   private int snapGrid = 20;
   private boolean template = false;
   private boolean scaleToScreen = false;
   private boolean fitToWidth = false;
   private int templateWidth = 0;
   private int templateHeight = 0;
   private boolean scriptEnabled = true;
   private final Map<String, String> localMap;
   private final List<String> disVariables; // disable prompting variable names.
   private final List<String> orderedVariables; // Ordered list of non-disabled prompting variable names
   private boolean mvOnDemand;
   private boolean associationEnabled = true;
   private boolean balancePadding = true;
   private final VSCSSFormat cssFmt;
   private final List<VSCustomizedAction> actions = new ArrayList<>();
   private String[] messageLevels = new String[]{ "Error", "Warning", "Info" };
   private static final Logger LOG = LoggerFactory.getLogger(ViewsheetInfo.class);
}
