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

import java.io.Serializable;

/**
 * Data transfer object that represents the {@link TextPropertyDialogModel} for the
 * text property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TextPaneModel implements Serializable {
   public String getText() {
      return text;
   }

   public void setText(String text) {
      this.text = text;
   }

   public boolean isAutoSize() {
      return autoSize;
   }

   public void setAutoSize(boolean autoSize) {
      this.autoSize = autoSize;
   }

   public boolean isUrl() {
      return url;
   }

   public void setUrl(boolean url) {
      this.url = url;
   }

   private String text;
   private boolean autoSize;
   private boolean url;
}