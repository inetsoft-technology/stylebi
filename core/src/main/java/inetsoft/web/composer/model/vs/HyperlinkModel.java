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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.graph.data.HRef;
import inetsoft.report.Hyperlink;
import inetsoft.report.filter.DCMergeCell;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Tool;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HyperlinkModel {
   public static HyperlinkModel createHyperlinkModel(HRef href) {
      HyperlinkModel hyperlinkModel = new HyperlinkModel();
      hyperlinkModel.setName(href.getName());

      // if default name, just use link as label
      if(!"hyperlink".equals(href.getName())) {
         hyperlinkModel.setLabel(Tool.localize(href.getName()));
      }
      else {
         String link = href.getLink();
         String label = href.getLink();

         if(href instanceof Hyperlink.Ref && link != null &&
            ((Hyperlink.Ref) href).getLinkType() == Hyperlink.VIEWSHEET_LINK)
         {
            try {
               AssetRepository repository = AssetUtil.getAssetRepository(false);
               AssetEntry viewsheetEntry = AssetEntry.createAssetEntry(link);

               if(viewsheetEntry != null) {
                  label = viewsheetEntry.getName();
               }

               viewsheetEntry = repository.getAssetEntry(viewsheetEntry);

              if(viewsheetEntry != null) {
                 label = viewsheetEntry.toView();
              }
            }
            catch(Exception e) {
               // use link as label.
            }
         }

         hyperlinkModel.setLabel(label);
      }

      hyperlinkModel.setLink(href.getLink());
      hyperlinkModel.setTargetFrame(href.getTargetFrame());
      hyperlinkModel.setTooltip(href.getToolTip());

      if(href instanceof Hyperlink.Ref) {
         Hyperlink.Ref href0 = (Hyperlink.Ref) href;
         hyperlinkModel.setQuery(href0.getQuery());
         hyperlinkModel.setWsIdentifier(href0.getWsIdentifier());
         hyperlinkModel.setBookmarkName(href0.getBookmarkName());
         hyperlinkModel.setBookmarkUser(href0.getBookmarkUser());
         hyperlinkModel.setSendReportParameters(href0.isSendReportParameters());
         hyperlinkModel.setSendSelectionParameters(href0.isSendSelectionParameters());
         hyperlinkModel.setDisablePrompting(href0.isDisablePrompting());
         hyperlinkModel.setLinkType(href0.getLinkType());
         String tooltip = href0.getToolTip();
         String localizeTooltip = Tool.localizeTextID(tooltip);

         if(localizeTooltip != null) {
            hyperlinkModel.setTooltip(localizeTooltip);
         }
         else {
            hyperlinkModel.setTooltip(tooltip);
         }
      }

      Enumeration<?> keys = href.getParameterNames();
      List<ParameterValueModel> parameterValueModels = new ArrayList<>();

      while(keys.hasMoreElements()) {
         String paramName = (String) keys.nextElement();
         Object paramValue = href.getParameter(paramName);

         // When adding parameters from selections, the parameter value can be an array
         if(Tool.getDataType(paramValue).equals(Tool.ARRAY)) {
            for(Object pvalue : (Object[]) paramValue) {
               parameterValueModels.add(getParameterValueModel(paramName, pvalue));
            }
         }
         else{
            parameterValueModels.add(getParameterValueModel(paramName, paramValue));
         }
      }

      hyperlinkModel.setParameterValues(parameterValueModels.toArray(
         new ParameterValueModel[parameterValueModels.size()]));

      return hyperlinkModel;
   }

   private static ParameterValueModel getParameterValueModel(String paramName, Object paramValue) {
      ParameterValueModel parameterValueModel = new ParameterValueModel();
      parameterValueModel.setName(paramName);
      parameterValueModel.setType(Tool.getDataType(paramValue));
      Object obj = null;

      if(paramValue instanceof DCMergeCell) {
         obj = ((DCMergeCell) paramValue).getOriginalData();
      }
      else {
         obj = Tool.getData(Tool.getDataType(paramValue), Tool.getDataString(paramValue), true);
      }

      parameterValueModel.setValue(obj == null ? "" : obj.toString());
      return parameterValueModel;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getLink() {
      return link;
   }

   public void setLink(String link) {
      this.link = link;
   }

   public String getQuery() {
      return query;
   }

   public void setQuery(String query) {
      this.query = query;
   }

   public String getWsIdentifier() {
      return wsIdentifier;
   }

   public void setWsIdentifier(String wsIdentifier) {
      this.wsIdentifier = wsIdentifier;
   }

   public String getTargetFrame() {
      return targetFrame;
   }

   public void setTargetFrame(String targetFrame) {
      this.targetFrame = targetFrame;
   }

   public String getTooltip() {
      return tooltip;
   }

   public void setTooltip(String tooltip) {
      this.tooltip = tooltip;
   }

   public String getBookmarkName() {
      return bookmarkName;
   }

   public void setBookmarkName(String bookmarkName) {
      this.bookmarkName = bookmarkName;
   }

   public String getBookmarkUser() {
      return bookmarkUser;
   }

   public void setBookmarkUser(String bookmarkUser) {
      this.bookmarkUser = bookmarkUser;
   }

   public ParameterValueModel[] getParameterValues() {
      return parameterValues;
   }

   public void setParameterValues(ParameterValueModel[] parameterValues) {
      this.parameterValues = parameterValues;
   }

   @JsonInclude(JsonInclude.Include.NON_DEFAULT)
   public boolean isSendReportParameters() {
      return sendReportParameters;
   }

   public void setSendReportParameters(boolean sendReportParameters) {
      this.sendReportParameters = sendReportParameters;
   }

   @JsonInclude(JsonInclude.Include.NON_DEFAULT)
   public boolean isSendSelectionParameters() {
      return sendSelectionParameters;
   }

   public void setSendSelectionParameters(boolean sendSelectionParameters) {
      this.sendSelectionParameters = sendSelectionParameters;
   }

   @JsonInclude(JsonInclude.Include.NON_DEFAULT)
   public boolean isDisablePrompting() {
      return disablePrompting;
   }

   public void setDisablePrompting(boolean disablePrompting) {
      this.disablePrompting = disablePrompting;
   }

   public int getLinkType() {
      return linkType;
   }

   public void setLinkType(int linkType) {
      this.linkType = linkType;
   }

   private String name;
   private String label;
   private String link;
   private String query;
   private String wsIdentifier;
   private String targetFrame;
   private String tooltip;
   private String bookmarkName;
   private String bookmarkUser;
   private ParameterValueModel[] parameterValues;
   private boolean sendReportParameters;
   private boolean sendSelectionParameters;
   private boolean disablePrompting;
   private int linkType;

   @JsonIgnoreProperties(ignoreUnknown = true)
   public static class ParameterValueModel {
      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      public String getType() {
         return type;
      }

      public void setType(String type) {
         this.type = type;
      }

      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }

      private String name;
      private String type;
      private String value;
   }
}
