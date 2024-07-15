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
package inetsoft.web.viewsheet.model.dialog;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.report.StyleFont;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.gui.GuiTool;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Data transfer object that represents the {@link EmailPaneModel} for the
 * email dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableEmailPaneModel.class)
@JsonDeserialize(as = ImmutableEmailPaneModel.class)
public interface EmailPaneModel {
   @Value.Default
   default String fromAddress() {
      return "reportserver@inetsoft.com";
   }

   boolean fromAddressEnabled();

   @Value.Default
   default String ccAddress() {
      return "";
   }

   @Value.Default
   default String bccAddress() {
      return "";
   }

   @Nullable
   String toAddress();

   @Nullable
   String subject();

   @Nullable
   String message();

   @Nullable
   Boolean userDialogEnabled();

   @Value.Default
   default EmailAddrDialogModel emailAddrDialogModel() {
      return ImmutableEmailAddrDialogModel.builder().build();
   }

   @Value.Default
   default List<IdentityID> users() {
      return new ArrayList<>(0);
   }

   @Value.Default
   default List<String> groups() {
      return new ArrayList<>(0);
   }

   @Value.Default
   default List<IdentityID> emailGroups() {
      return new ArrayList<>(0);
   }

   @Value.Default
   default List<String> fonts() {
      String[] fonts = GuiTool.getAllFonts();
      // web gui handles default fonts on the client side
      fonts[0] = StyleFont.getDefaultFontFamily();
      return Arrays.asList(fonts);
   }

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableEmailPaneModel.Builder {
   }
}
