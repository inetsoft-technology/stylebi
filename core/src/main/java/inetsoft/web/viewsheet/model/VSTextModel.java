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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.ParameterTool;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.HyperlinkModel;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSTextModel extends VSOutputModel<TextVSAssembly> {
   public VSTextModel(TextVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      TextVSAssemblyInfo info = (TextVSAssemblyInfo) assembly.getVSAssemblyInfo();
      text = rvs.isRuntime() ? info.getDisplayText() : info.getText();
      shadow = info.isShadow();
      autoSize = info.isAutoSize();
      url = info.isUrl();
      presenter = VSUtil.createPainter(assembly) != null;
      parameters = (new ParameterTool()).getParameters(rvs);
      this.paddingTop = info.getPadding().top;
      this.paddingLeft = info.getPadding().left;
      this.paddingBottom = info.getPadding().bottom;
      this.paddingRight = info.getPadding().right;
      expressionText = info.getTextValue() != null && info.getTextValue().startsWith("=");

      if("true".equals(SreeEnv.getProperty("text.wordwrap.122"))) {
         breakAll = info.isKeepSpace() || autoSize;
      }

      if(info.isKeepSpace()) {
         text = replaceSpace(text);
      }

      Hyperlink.Ref[] hrefs = info.getHyperlinks();

      if(hrefs == null) {
         this.hyperlinks = new HyperlinkModel[0];
      }
      else {
         this.hyperlinks = new HyperlinkModel[hrefs.length];

         for(int i = 0; i < hyperlinks.length; i++) {
            hyperlinks[i] = HyperlinkModel.createHyperlinkModel(hrefs[i]);
         }
      }

      if(info.isUrl()) {
         externalUrls = new HashMap<>();

         for(Assembly sibling : rvs.getViewsheet().getAssemblies(true)) {
            if(!assembly.getAbsoluteName().equals(sibling.getAbsoluteName())) {
               try {
                  externalUrls.put(
                     sibling.getAbsoluteName(),
                     "../api/vs/external?vs=" + URLEncoder.encode(rvs.getID(), "UTF-8") +
                     "&assembly=" + URLEncoder.encode(sibling.getAbsoluteName(), "UTF-8"));
               }
               catch(Exception e) {
                  throw new RuntimeException("Failed to encode IDs", e);
               }
            }
         }
      }
   }

   /**
    * Replace leading and trailing space with nbsp.
    */
   private static String replaceSpace(String str) {
      if(str == null) {
         return null;
      }

      StringBuilder builder = new StringBuilder();
      int i = 0;

      for(; i < str.length() && str.charAt(i) == ' '; i++) {
         builder.append("&nbsp;");
      }

      int last = str.length() - 1;
      StringBuilder trailing = new StringBuilder();

      for(; last > i && str.charAt(last) == ' '; last--) {
         trailing.append("&nbsp;");
      }

      builder.append(str, i, last + 1);
      builder.append(trailing);

      return builder.toString();
   }

   public String getText() {
      return text;
   }

   public boolean getShadow() {
      return shadow;
   }

   public boolean isAutoSize() {
      return autoSize;
   }

   public boolean isUrl() {
      return url;
   }

   public HyperlinkModel[] getHyperlinks() {
      return hyperlinks;
   }

   public boolean isPresenter() {
      return presenter;
   }

   public boolean isBreakAll() {
      return breakAll;
   }

   public int getPaddingTop() {
      return paddingTop;
   }

   public int getPaddingLeft() {
      return paddingLeft;
   }

   public int getPaddingBottom() {
      return paddingBottom;
   }

   public int getPaddingRight() {
      return paddingRight;
   }

   public List<String> getParameters() {
      return parameters;
   }

   public void setParameters(List<String> params) {
      this.parameters = params;
   }

   public boolean isExpressionText() {
      return expressionText;
   }

   public void setExpressionText(boolean expressionText) {
      this.expressionText = expressionText;
   }

   public Map<String, String> getExternalUrls() {
      return externalUrls;
   }

   public void setExternalUrls(Map<String, String> externalUrls) {
      this.externalUrls = externalUrls;
   }

   @Override
   protected VSFormatModel createFormatModel(VSCompositeFormat compositeFormat,
                                             VSAssemblyInfo assemblyInfo)
   {
      return new VSFormatModel(compositeFormat, assemblyInfo, true);
   }

   private String text;
   private final boolean shadow;
   private final boolean autoSize;
   private final boolean url;
   private final HyperlinkModel[] hyperlinks;
   private final boolean presenter;
   private boolean breakAll;
   private final int paddingTop;
   private final int paddingLeft;
   private final int paddingBottom;
   private final int paddingRight;
   private boolean expressionText;
   private Map<String, String> externalUrls;
   private List<String> parameters;

   @Component
   public static final class VSTextModelFactory
      extends VSObjectModelFactory<TextVSAssembly, VSTextModel>
   {
      public VSTextModelFactory() {
         super(TextVSAssembly.class);
      }

      @Override
      public VSTextModel createModel(TextVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSTextModel(assembly, rvs);
      }
   }
}
