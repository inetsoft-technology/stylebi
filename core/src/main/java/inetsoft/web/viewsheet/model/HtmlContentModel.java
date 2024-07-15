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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import javax.annotation.Nullable;

/**
 * Wrapper class for cleaning html string to/from the server
 */
@Value.Immutable
@JsonSerialize(as = ImmutableHtmlContentModel.class)
public interface HtmlContentModel {
   @Nullable
   @Value.Parameter
   String getContent();

   /**
    * Factory function to create an HtmlContentModel from a given string
    *
    * @param unsafeContent the content to clean and use to create the model
    *
    * @return the new HtmlContentModel
    */
   static HtmlContentModel create(final String unsafeContent) {
      String cleanContent = null;

      if(unsafeContent != null) {
         final Document document = Jsoup.parse(unsafeContent);
         document.select("font").forEach(HtmlContentModel::convertFontToStyle);
         final Document.OutputSettings outputSettings =
            new Document.OutputSettings().prettyPrint(false);
         final Safelist whitelist = Safelist.basic()
            .addAttributes(":all", "style")
            .addTags("s", "h1", "h2", "h3", "h4");
         final String html = document.body().html().replaceAll("\n", "");
         cleanContent = Jsoup.clean(html, "", whitelist, outputSettings);
      }

      return ImmutableHtmlContentModel.of(cleanContent);
   }

   static void convertFontToStyle(Element fontElement) {
      fontElement.tagName("span");
      String style = "";
      final String color = fontElement.attr("color");
      final String fontFamily = fontElement.attr("face");
      final String fontSize = fontElement.attr("size");

      if(color != null && !color.isEmpty()) {
         style += "color: " + color + ';';
      }

      if(fontFamily != null && !fontFamily.isEmpty()) {
         style += "font-family: " + fontFamily + ';';
      }

      if(fontSize != null && !fontSize.isEmpty()) {
         style += "font-size: " + fontSize + "pt;";
      }

      fontElement.attr("style", style);
   }
}
