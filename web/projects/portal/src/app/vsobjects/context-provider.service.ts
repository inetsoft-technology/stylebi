/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
import { InjectionToken } from "@angular/core";

export const ComposerToken = new InjectionToken<boolean>("ComposerToken");

export function ViewerContextProviderFactory(composer: boolean): ContextProvider {
   // If the composer token is defined then this is a preview,
   // otherwise this is the viewer
   if(composer) {
      return new ContextProvider(false, false, true, false, false, false, false, false, false, false, false);
   }

   return new ContextProvider(true, false, false, false, false, false, false, false, false, false, false);
}

export function ComposerContextProviderFactory(): ContextProvider {
   return new ContextProvider(false, true, false, false, false, false, false, false, false, false, false);
}

export function VSWizardContextProviderFactory(): ContextProvider {
   return new ContextProvider(false, true, false, false, false, false, true, false, false, false, false);
}

export function VSWizardPreviewContextProviderFactory(): ContextProvider {
   return new ContextProvider(false, true, false, false, false, false, false, true, false, false, false);
}

export function BindingContextProviderFactory(composer: boolean): ContextProvider {
   // If the composer token is defined then binding was opened from composer
   if(composer) {
      return new ContextProvider(false, false, false, true, true, false, false, false, false, false, false);
   }

   return new ContextProvider(true, false, false, true, false, false, false, false, false, false, false);
}

export function EmbedContextProviderFactory(): ContextProvider {
   return new ContextProvider(true, false, false, false, false, false, false, false, false, false, true);
}

/**
 * Injectable service used to get the current app context
 */
export class ContextProvider {
   private _viewer: boolean;
   private _composer: boolean;
   private _preview: boolean;
   private _binding: boolean;
   private _composerBinding: boolean;
   private _reportViewer: boolean;
   private _vsWizard: boolean;
   private _vsWizardPreview: boolean;
   private _adhocPreviewParameter: boolean;
   private _parameterElementPreview: boolean;
   private _embed: boolean;

   constructor(viewer: boolean, composer: boolean, preview: boolean, binding: boolean,
               composerBinding: boolean, reportViewer: boolean, vsWizard: boolean,
               vsWizardPreview: boolean, adhocPreviewParameter: boolean,
               parameterElementPreview: boolean, embed: boolean)
   {
      this._viewer = viewer;
      this._composer = composer;
      this._preview = preview;
      this._binding = binding;
      this._composerBinding = composerBinding;
      this._reportViewer = reportViewer;
      this._vsWizard = vsWizard;
      this._vsWizardPreview = vsWizardPreview;
      this._adhocPreviewParameter = adhocPreviewParameter;
      this._parameterElementPreview = parameterElementPreview;
      this._embed = embed;
   }

   get vsWizardPreview(): boolean {
      return this._vsWizardPreview;
   }

   get vsWizard(): boolean {
      return this._vsWizard;
   }

   get viewer(): boolean {
      return this._viewer;
   }

   get composer(): boolean {
      return this._composer;
   }

   get preview(): boolean {
      return this._preview;
   }

   get binding(): boolean {
      return this._binding;
   }

   get composerBinding(): boolean {
      return this._composerBinding;
   }

   get embed(): boolean {
      return this._embed;
   }
}
