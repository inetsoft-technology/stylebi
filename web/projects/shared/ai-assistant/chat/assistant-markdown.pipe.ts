/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { Pipe, PipeTransform, SecurityContext } from "@angular/core";
import { DomSanitizer, SafeHtml } from "@angular/platform-browser";
import { marked } from "marked";

/**
 * Converts a Markdown string to sanitized HTML for rendering assistant messages.
 * marked.parse() is called first to convert Markdown to HTML, then Angular's
 * DomSanitizer strips any dangerous tags/attributes (script, onclick, etc.) before
 * the result is trusted for innerHTML binding.
 */
@Pipe({ name: "assistantMarkdown" })
export class AssistantMarkdownPipe implements PipeTransform {
   constructor(private sanitizer: DomSanitizer) {}

   transform(value: string | null | undefined): SafeHtml {
      if(!value) {
         return "";
      }

      const raw = marked.parse(value) as string;
      const safe = this.sanitizer.sanitize(SecurityContext.HTML, raw) ?? "";
      return this.sanitizer.bypassSecurityTrustHtml(safe);
   }
}
