/*
 * Copyright (c) 2022, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */

import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { BehaviorSubject, Observable } from "rxjs";
import { map } from "rxjs/operators";
import { CurrentUser } from "../../portal/src/app/portal/current-user";
import "./translations/af";
import "./translations/ar";
import "./translations/ast";
import "./translations/az";
import "./translations/bg";
import "./translations/bs";
import "./translations/ca";
import "./translations/cs";
import "./translations/da";
import "./translations/de";
import "./translations/de-ch";
import "./translations/el";
import "./translations/en-au";
import "./translations/en-gb";
import "./translations/eo";
import "./translations/es";
import "./translations/et";
import "./translations/eu";
import "./translations/fa";
import "./translations/fi";
import "./translations/fr";
import "./translations/gl";
import "./translations/gu";
import "./translations/he";
import "./translations/hi";
import "./translations/hr";
import "./translations/hu";
import "./translations/id";
import "./translations/it";
import "./translations/ja";
import "./translations/jv";
import "./translations/kk";
import "./translations/km";
import "./translations/kn";
import "./translations/ko";
import "./translations/ku";
import "./translations/lt";
import "./translations/lv";
import "./translations/ms";
import "./translations/nb";
import "./translations/ne";
import "./translations/nl";
import "./translations/no";
import "./translations/oc";
import "./translations/pl";
import "./translations/pt";
import "./translations/pt-br";
import "./translations/ro";
import "./translations/ru";
import "./translations/si";
import "./translations/sk";
import "./translations/sl";
import "./translations/sq";
import "./translations/sr";
import "./translations/sr-latn";
import "./translations/sv";
import "./translations/th";
import "./translations/tk";
import "./translations/tr";
import "./translations/tt";
import "./translations/ug";
import "./translations/uk";
import "./translations/ur";
import "./translations/uz";
import "./translations/vi";
import "./translations/zh";
import "./translations/zh-cn";

@Injectable({
   providedIn: "root"
})
export class CkeditorLanguageService {
   private readonly languages = [
      "af", "ar", "ast", "az", "bg", "bs", "ca", "cs", "da", "de", "de-ch", "el", "en-au", "en-gb",
      "eo", "es", "et", "eu", "fa", "fi", "fr", "gl", "gu", "he", "hi", "hr", "hu", "id", "it",
      "ja", "jv", "kk", "km", "kn", "ko", "ku", "lt", "lv", "ms", "nb", "ne", "nl", "no", "oc",
      "pl", "pt", "pt-br", "ro", "ru", "si", "sk", "sl", "sq", "sr", "sr-latn", "sv", "th", "tk",
      "tr", "tt", "ug", "uk", "ur", "uz", "vi", "zh", "zh-cn"
   ];

   private language$: BehaviorSubject<string> = new BehaviorSubject<string>(null);

   constructor(http: HttpClient) {
      http.get<CurrentUser>("../api/portal/get-current-user")
         .pipe(map(user => this.getUserLanguage(user)))
         .subscribe(language => this.language$.next(language));
   }

   getLanguage(): Observable<string> {
      return this.language$.asObservable();
   }

   private getUserLanguage(user: CurrentUser): string {
      const { localeLanguage, localeCountry } = user;
      let language: string;

      if(!!localeLanguage) {
         if(!!localeCountry) {
            const test = `${localeLanguage.toLowerCase()}-${localeCountry.toLowerCase()}`;
            language = this.languages.find(l => l === test);
         }

         if(!language) {
            const test = localeLanguage.toLowerCase();
            language = this.languages.find(l => l === test);
         }
      }

      return language;
   }
}
