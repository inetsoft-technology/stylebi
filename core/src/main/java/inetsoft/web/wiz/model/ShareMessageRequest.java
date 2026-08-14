/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request body shared by {@code POST /api/wiz/viewsheet/share-email}, {@code share-slack} and
 * {@code share-google-chat} — a plain, JSON-friendly stand-in for {@code inetsoft.web.share.
 * ShareMessage} (an Immutables interface, awkward to bind directly from a controller method).
 * {@code WizShareController} converts one of these into a real {@code ShareMessage} via its
 * builder before delegating to the corresponding {@code ShareController} method.
 *
 * <p>Only {@code link} and {@code message} are used by the Slack/Google Chat channels;
 * {@code viewsheetId}/{@code subject}/{@code recipients}/{@code ccs}/{@code bccs} are Email-only.
 *
 * <p>{@code link}/{@code message} are {@code @NotBlank}: they back {@code ShareMessage.link()}/
 * {@code message()}, which are non-{@code @Nullable} Immutables attributes. Without this
 * validation, an omitted {@code link}/{@code message} would reach the Immutables builder in
 * {@code WizShareController.toShareMessage()} and throw an unhandled {@code NullPointerException}
 * (surfacing as a generic 500) instead of the clean 400 that {@code @Valid} +
 * {@code GlobalExceptionHandler}'s default {@code MethodArgumentNotValidException} handling gives.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShareMessageRequest {
   public String getViewsheetId() {
      return viewsheetId;
   }

   public void setViewsheetId(String viewsheetId) {
      this.viewsheetId = viewsheetId;
   }

   public String getLink() {
      return link;
   }

   public void setLink(String link) {
      this.link = link;
   }

   public String getMessage() {
      return message;
   }

   public void setMessage(String message) {
      this.message = message;
   }

   public String getSubject() {
      return subject;
   }

   public void setSubject(String subject) {
      this.subject = subject;
   }

   public List<String> getRecipients() {
      return recipients;
   }

   public void setRecipients(List<String> recipients) {
      this.recipients = recipients;
   }

   public List<String> getCcs() {
      return ccs;
   }

   public void setCcs(List<String> ccs) {
      this.ccs = ccs;
   }

   public List<String> getBccs() {
      return bccs;
   }

   public void setBccs(List<String> bccs) {
      this.bccs = bccs;
   }

   private String viewsheetId;
   @NotBlank
   private String link;
   @NotBlank
   private String message;
   private String subject;
   private List<String> recipients;
   private List<String> ccs;
   private List<String> bccs;
}
