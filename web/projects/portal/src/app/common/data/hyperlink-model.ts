/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
import { convertKeyToID } from "../../../../../em/src/app/settings/security/users/identity-id";
import { GuiTool } from "../util/gui-tool";
import { Tool } from "../../../../../shared/util/tool";

export enum LinkType {
   WEB_LINK = 1,
   ARCHIVE_LINK = 2,
   DRILL_LINK = 4,
   VIEWSHEET_LINK = 8,
   MESSAGE_LINK = 16
}

export class HyperlinkModel {
   name: string;
   label: string;
   link: string;
   query: string;
   wsIdentifier: string;
   targetFrame: string;
   tooltip: string;
   bookmarkName: string;
   bookmarkUser: string;
   parameterValues: ParameterValueModel[];
   sendReportParameters: boolean;
   sendSelectionParameters: boolean;
   disablePrompting: boolean;
   linkType: LinkType;
}

export class HyperlinkViewModel {
   constructor(public label: string, public url: string, public target: string,
               public parameters: any) {
   }

   static fromHyperlinkModel(hyperlink: HyperlinkModel,
                             linkUri: string,
                             paramValues: ParameterValueModel[],
                             runtimeId?: string,
                             inPortal?: boolean): HyperlinkViewModel
   {
      let url: string;
      let containsVSIdentifierRegex: RegExp = /^\d\^\d{1,5}\^[^^]+\^/;
      const parameters: any = {};

      if(hyperlink.disablePrompting) {
         hyperlink.parameterValues.push({
            name: "disableParameterSheet", type: "", value: "" + hyperlink.disablePrompting
         });
      }

      HyperlinkViewModel.handleURLEmbeddedHyperlinks(hyperlink, paramValues);

      if(hyperlink.linkType == LinkType.WEB_LINK) {
         url = hyperlink.link;

         if(url.startsWith("./") || url.startsWith("../")) {
            // relative url, as is
         }
         else if(!/^(((https?:)?\/\/)|(mailto:)|(\/[^/])).+$/.test(url)) {
            url = "//" + url;
         }

         if(hyperlink.parameterValues.length > 0 && url.indexOf("?") < 0) {
            url += "?";
         }
      }
      else if(hyperlink.linkType == LinkType.VIEWSHEET_LINK ||
              containsVSIdentifierRegex.test(hyperlink.link))
      {
         const newTab = hyperlink.targetFrame != "_self";
         const match = /^(\d+)\^(\d+)\^([^^]+)\^(.+)$/.exec(hyperlink.link);
         url = linkUri + (inPortal && !newTab ? "app/portal/tab/report/vs/view/"
                          : "app/viewer/view/");

         if(match[1] === "1") {
            url += "global/";
         }
         else {
            url += "user/" +  convertKeyToID(match[3]).name + "/";
         }

         url += Tool.encodeURIComponentExceptSlash(match[4]);

         if(hyperlink.sendSelectionParameters) {
            url += "?hyperlinkSourceId=" + encodeURIComponent(runtimeId);
            parameters["hyperlinkSourceId"] = runtimeId;
         }
         else if(hyperlink.parameterValues.length > 0) {
            url += "?";
         }
      }
      else if(hyperlink.linkType == LinkType.MESSAGE_LINK) {
         url = hyperlink.link;
      }

      if(!url) {
         return null;
      }

      if(paramValues != null && paramValues.length > 0) {
         url = HyperlinkViewModel.appendParameters(url, paramValues);
         paramValues.forEach(p => HyperlinkViewModel.addParameter(parameters, p.name, p.value));
      }

      if(hyperlink.parameterValues.length > 0 && hyperlink.linkType !== LinkType.MESSAGE_LINK) {
         const hparams = hyperlink.parameterValues
            // ignore paramters passed in through paramValues so don't duplicate the values(45304).
            .filter(p => !paramValues || paramValues.find(pv => pv.name == p.name) == null)
            // ignore builtin and drill parameters. (45283, 45282)
            .filter(p => p.name != "drillfrom" && p.name != "__principal__" &&
                   p.name != "_USER_" && p.name != "_ROLES_" && p.name != "_GROUPS_");
         url = HyperlinkViewModel.appendParameters(url, hparams);
         hparams.forEach(p => HyperlinkViewModel.addParameter(parameters, p.name, p.value));
      }

      if(hyperlink.linkType === LinkType.MESSAGE_LINK) {
         let paramMap = {};

         if(!!hyperlink.parameterValues) {
            hyperlink.parameterValues
               .forEach(p => HyperlinkViewModel.addParameter(paramMap, p.name, p.value));
         }

         let modelUrl = url.startsWith("message:") ? url.substring(8) : url;
         url = JSON.stringify({message: modelUrl, params: paramMap});
      }
      else {
         url = GuiTool.resolveUrl(url);
      }

      let targetFrame: string = hyperlink.targetFrame;

      if(targetFrame != null &&
         (targetFrame.toLowerCase() == "self" || targetFrame.toLowerCase() == "_self"))
      {
         targetFrame = hyperlink.linkType !== LinkType.VIEWSHEET_LINK ? "_self" : "";
      }
      else {
         targetFrame = targetFrame || "_blank";
      }

      return new HyperlinkViewModel(hyperlink.name, url, targetFrame, parameters);
   }

   //replace embedded parameter keys with values and strip any if empty
   private static handleURLEmbeddedHyperlinks(hyperlink: HyperlinkModel, paramValues: ParameterValueModel[]) {
      for(const param of hyperlink.parameterValues) {
         const name = encodeURIComponent(param.name);
         const value = encodeURIComponent(param.value);

         const toReplace = "$(" + name + ")";
         const link = hyperlink.link == null ? "" : hyperlink.link;

         if(link.indexOf(toReplace) >= 0) {
            hyperlink.link = link.replace(toReplace, value);
            hyperlink.parameterValues = hyperlink.parameterValues.filter(obj => {
               return obj.name != name;
            });
         }
      }

      while(hyperlink.link != null && hyperlink.link.indexOf("$(") >= 0) {
         const start = hyperlink.link.indexOf("$(");
         const end = hyperlink.link.substring(start).indexOf(")") + start;
         var baseLink = hyperlink.link == null ? "" : hyperlink.link;

         if (start - 1 >= 0 && "/" == (baseLink.substring(start - 1,start))) {
            baseLink = baseLink.substring(0,start-1) + baseLink.substring(end+1);
         }
         else {
            baseLink = baseLink.substring(0,start) + baseLink.substring(end+1);
         }

        hyperlink.link = baseLink;
      }
   }

   // set parameter value or append to array if already exist
   private static setOrAppend(params: any, name: string, val: any) {
      const pval = params[name];

      if(pval == null) {
         params[name] = val;
      }
      else if(Array.isArray(pval)) {
         pval.push(val);
      }
      else {
         params[name] = [pval, val];
      }
   }

   private static appendParameters(url: string, params: ParameterValueModel[]): string {
      return params.reduce((u, param) => {
         if(u.indexOf("?") == -1) {
            u = u + "?";
         }

         return u + (u[u.length - 1] === "?" ? "" : "&") +
            encodeURIComponent(param.name) + "=" + encodeURIComponent(param.value);
      }, url);
   }

   // add parameter to map. if already exist, create an array
   private static addParameter(params: any, key: string, value: any) {
      const v = params[key];

      if(Array.isArray(v)) {
         v.push(value);
      }
      else if(v != null) {
         params[key] = [v, value];
      }
      else {
         params[key] = value;
      }
   }
}

export class ParameterValueModel {
   constructor(public name: string, public type: string, public value: string) {}
}
