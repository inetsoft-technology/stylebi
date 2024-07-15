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
package inetsoft.web.admin.presentation.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.portal.FontFaceModel;
import inetsoft.web.admin.model.FileData;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableLookAndFeelSettingsModel.class)
@JsonDeserialize(as = ImmutableLookAndFeelSettingsModel.class)
public interface LookAndFeelSettingsModel {
   boolean ascending();
   boolean repositoryTree();
   boolean expand();
   boolean customLogoEnabled();
   boolean defaultLogo();
   @Nullable String logoName();
   @Nullable FileData logoFile();
   boolean defaultFavicon();
   @Nullable String faviconName();
   @Nullable FileData faviconFile();
   boolean defaultViewsheet();
   @Nullable String viewsheetName();
   @Nullable FileData viewsheetFile();
   @Nullable FileData userformatFile();
   boolean defaultFont();
   @Nullable List<String> userFonts();
   @Nullable List<FontFaceModel> fontFaces();
   @Nullable List<FontFaceModel> deleteFontFaces();
   @Nullable List<UserFontModel> newFontFaces();
   List<ViewsheetCSSEntry> viewsheetCSSEntries();
   boolean vsEnabled();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableLookAndFeelSettingsModel.Builder {
   }
}
