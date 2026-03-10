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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { Searchable } from "../../../searchable";
import { ContextHelp } from "../../../context-help";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { PresentationAISettingsModel } from "./presentation-ai-settings-model";

@Searchable({
   route: "/settings/presentation/settings#ai-integration",
   title: "AI Integration",
   keywords: ["em.settings", "em.settings.ai", "em.settings.ai.assistant", "em.settings.ai.chatAppServerUrl"]
})
@ContextHelp({
   route: "/settings/presentation/settings#ai-integration",
   link: "EMPresentationAIIntegration"
})
@Component({
   selector: "em-presentation-ai-settings-view",
   templateUrl: "./presentation-ai-settings-view.component.html"
})
export class PresentationAISettingsViewComponent {
   @Input() model: PresentationAISettingsModel;
   @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();

   emitModel(): void {
      this.modelChanged.emit({
         model: this.model,
         modelType: PresentationSettingsType.AI_SETTINGS_MODEL,
         valid: true
      });
   }
}
