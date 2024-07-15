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
package inetsoft.web.reportviewer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ParameterPageModel implements ReportPageModel {
   public ParameterPageModel() {
   }

   /**
    * Set the report title
    */
   public void setReportTitle(String title) {
      this.reportTitle = title;
   }

   /**
    * Get the report title
    */
   public String getReportTitle() {
      return this.reportTitle;
   }

   /**
    * Set the report description.
    */
   public void setReportDesc(String desc) {
      this.reportDesc = desc;
   }

   /**
    * Get the report description.
    */
   public String getReportDesc() {
      return this.reportDesc;
   }

   /**
    * Set the report exist parameter values map which need be used as hidden values
    * in parameter page.
    */
   public void setParamValues(Map<String, Object> params) {
      this.paramValues = params;
   }

   /**
    * Get the report exist parameter values map which need be used as hidden values
    * in parameter page.
    */
   public Map<String, Object> getParamValues() {
      return this.paramValues;
   }

   /**
    * Set the replet parameters list.
    */
   public void setParams(List<RepletParameterModel> params) {
      this.params = params;
   }

   /**
    * Get the replet parameters list.
    */
   public List<RepletParameterModel> getParams() {
      return this.params;
   }

   public String getFooterText() {
      return footerText;
   }

   public void setFooterText(String footerText) {
      this.footerText = footerText;
   }

   private String reportTitle;
   private String reportDesc;
   private Map<String, Object> paramValues = new HashMap<>();
   private List<RepletParameterModel> params = new ArrayList<>();
   private String footerText;
}