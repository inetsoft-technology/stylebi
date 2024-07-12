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
import {HttpClient} from "@angular/common/http";
import {
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import {
   FormGroupDirective,
   NgForm,
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   Validators
} from "@angular/forms";
import {ErrorStateMatcher} from "@angular/material/core";
import { Observable, Subject, Subscription } from "rxjs";
import {map} from "rxjs/operators";
import {IdentityType} from "../../../../../../../shared/data/identity-type";
import {FormValidators} from "../../../../../../../shared/util/form-validators";
import {Tool} from "../../../../../../../shared/util/tool";
import {PropertyModel} from "../../property-table-view/property-model";
import {IdentityModel} from "../../security-table-view/identity-model";
import {SecurityTreeNode} from "../../security-tree-view/security-tree-node";
import {
   EditGroupPaneModel,
   EditIdentityPaneModel,
   EditOrganizationPaneModel,
   EditRolePaneModel,
   EditUserPaneModel
} from "../edit-identity-pane/edit-identity-pane.model";
import {GetIdentityNameResponse} from "../get-identity-name-response";
import { convertToKey, IdentityId } from "../identity-id";

interface IdentityTheme {
   id: string;
   name: string;
}

interface IdentityThemeList {
   themes: IdentityTheme[];
}

@Component({
   selector: "em-edit-identity-view",
   templateUrl: "./edit-identity-view.component.html",
   styleUrls: ["./edit-identity-view.component.scss"]
})
export class EditIdentityViewComponent implements OnInit, OnChanges, OnDestroy {
   _model: EditIdentityPaneModel;
   @Input() set model(m: EditIdentityPaneModel) {
      this._model = m;
      this.identityEditable = m?.editable;
   }
   get model() {
      return this._model;
   }
   @Input() type: IdentityType;
   @Input() provider: string;
   @Input() smallDevice = false;
   @Input() treeData: SecurityTreeNode[] = [];
   @Input() isSysAdmin: boolean = false;
   @Input() isLoadingTemplate: boolean = false;
   @Input() identityEditableChanges: Subject<boolean>;
   @Output() cancel = new EventEmitter<void>();
   @Output() roleSettingsChanged = new EventEmitter<EditRolePaneModel>();
   @Output() groupSettingsChanged = new EventEmitter<EditGroupPaneModel>();
   @Output() userSettingsChanged = new EventEmitter<EditUserPaneModel>();
   @Output() organizationSettingsChanged = new EventEmitter<EditOrganizationPaneModel>();
   @Output() setTemplateOrganizationClicked = new EventEmitter<EditOrganizationPaneModel>();
   @Output() pageChanged = new EventEmitter<boolean>();
   public identityTypes = ["_#(js:Users)", "_#(js:Groups)", "_#(js:Roles)","Unknown Identity", "_#(js:Organizations)"];
   public userIdentityTypes = ["_#(js:Groups)", "_#(js:Roles)"];
   public identities: Observable<IdentityId[]>;
   identity: string;
   subIdentity: IdentityId = null;
   readonly: boolean = false;
   roles: IdentityModel[] = [];
   members: IdentityModel[] = [];
   permittedIdentities: IdentityModel[] = [];
   properties: PropertyModel[] = [];
   form: UntypedFormGroup;
   passwordErrorMatcher: ErrorStateMatcher;
   themes: IdentityTheme[] = [];
   isMultiTenant: boolean = false;
   identityEditable: boolean = false;

   private originalModel: EditIdentityPaneModel;
   private originalRoles: IdentityModel[];
   private originalMembers: IdentityModel[];
   private originalPermissions: IdentityModel[];
   private originalProperties: PropertyModel[];
   private formSubscription = Subscription.EMPTY;
   private changingPassword = false;
   private templateOrganization = false;
   private editableSubscription = Subscription.EMPTY;

   public get user(): boolean {
      return this.type === IdentityType.USER;
   }

   public get group(): boolean {
      return this.type === IdentityType.GROUP;
   }

   public get role(): boolean {
      return this.type === IdentityType.ROLE;
   }

   public get organization(): boolean {
      return this.type === IdentityType.ORGANIZATION;
   }

   public get isTemplateOrganization(): boolean {
      return this.templateOrganization;
   }

   get pwForm(): UntypedFormGroup {
      return <UntypedFormGroup> this.form.controls["changePassword"];
   }

   get userModel(): EditUserPaneModel {
      return this.model as EditUserPaneModel;
   }

   get organizationModel(): EditOrganizationPaneModel {
      return this.model as EditOrganizationPaneModel;
   }

   get currentUserName(): string {
      return this.organization ? this.organizationModel?.currentUserName : null;
   }

   get isOrgAdminRole(): boolean {
      if(this.role) {
         return (<EditRolePaneModel>this.model).isOrgAdmin;
      }
      else {
         return false;
      }
   }

   showDefaultRoleOption() {
      //show if not global
      return !this.isMultiTenant || !((<EditRolePaneModel>this.model).organization == null);
   }

   setIsEnterprise() {
      let orgRoot = this.treeData.filter(node =>
         node.type == IdentityType.ORGANIZATION);
      this.isMultiTenant = orgRoot.length != 0;
   }

   setIsTemplate() {
      if(this.organization) {
         const uri = "../api/em/security/providers/" + Tool.byteEncodeURLComponent(this.provider) + "/get-is-template/" + convertToKey({name:this.model.name, organization:this.model.name});
         let root = this.treeData;

         let templateCall = this.http.get<boolean>(uri).pipe(
            map(isTemp => {
               return isTemp;
            }));

         templateCall.subscribe((isTemp: boolean) => this.templateOrganization = isTemp);
      }
      else {
         this.templateOrganization = false;
      }
   }

   constructor(private fb: UntypedFormBuilder,
               private http: HttpClient,
               private changeDetector: ChangeDetectorRef,
               defaultErrorMatcher: ErrorStateMatcher)
   {
      this.passwordErrorMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.pwForm.errors && !!this.pwForm.errors.passwordsMatch ||
            defaultErrorMatcher.isErrorState(control, form)
      };
   }

   ngOnInit(): void {
      this.getThemes().subscribe(themes => this.themes = themes);
      this.editableSubscription = this.identityEditableChanges.subscribe((editable) => {
         this.identityEditable = editable;
      });
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.model && changes.model.previousValue == null && changes.model.currentValue != null) {
         this.init();
         this.setOriginalState();
      }
   }

   ngOnDestroy(): void {
      this.formSubscription.unsubscribe();
      this.editableSubscription.unsubscribe();
   }

   init(): void {
      this.setIsEnterprise();
      this.setIsTemplate();

      const nameValidator = this.type == IdentityType.USER ? FormValidators.validUserName
         : FormValidators.validGroupName;
      this.form = this.fb.group({
         name: [this.model.name, [Validators.required, nameValidator]],
         active: [true],
         alias: ["", FormValidators.containsDashboardSpecialCharsForName],
         organization: ["", FormValidators.containsDashboardSpecialCharsForName],
         id: ["", this.organization ? [Validators.required, FormValidators.validOrgID] : ""],
         changePasswordEnabled: [false],
         changePassword: this.fb.group({
               password: this.user ? [(<EditUserPaneModel>this.model).password, [Validators.required, FormValidators.passwordComplexity]] : [""],
               confirmPassword: this.user ? [(<EditUserPaneModel>this.model).password] : [""]
            },
            {
               validator: FormValidators.passwordsMatch("password", "confirmPassword")
            }
         ),
         addIdentity: this.fb.group({
            identityType: [""],
            identity: [""],
         }),
         defaultRole: [false],
         sysAdmin: [false],
         description: [""],
         email: [""],
         locale: [""],
         locales: [""],
         theme: [this.model.theme || ""]
      });

      // this.readonly = !this.model.editable;
      this.members = this.model.members.slice(0);
      this.roles = this.model.roles.map((role) =>
         <IdentityModel>{identityID: role, type: IdentityType.ROLE});

      if(this.model.permittedIdentities) {
         this.permittedIdentities = this.model.permittedIdentities.slice(0);
      }

      if(this.user) {
         this.initializeUserForm();
      }
      else {
         if(this.role) {
            this.initializeRoleForm();
         }

         if(this.group) {
            this.initializeGroupForm();
         }

         if(this.organization) {
            this.initializeOrganizationForm();
         }

         if(!this.identityTypes.find(x => x === "_#(js:Users)")) {
            this.identityTypes.unshift("_#(js:Users)");
         }
      }

      this.identity = this.identityTypes[0];
      this.loadIdentities();
      this.subIdentity = this.model && this.model.identityNames && this.model.identityNames.length > 0 ?
         this.model.identityNames[0] : null;

      if(!this.model.editable) {
         this.form.disable();
      }

      this.formSubscription.unsubscribe();
      // IE may trigger a change event immediately on populating the form
      setTimeout(() => {
         this.formSubscription = this.form.valueChanges.subscribe(() => this.updateModel());
      }, 200);
   }

   public loadIdentities(opened: boolean = true) {
      if(opened) {
         let index = this.identityTypes.indexOf(this.identity);

         if(this.user) {
            index += 1;
         }

         let encodeProvider = Tool.byteEncodeURLComponent(this.provider);
         const url = `../api/em/security/providers/${encodeProvider}/identities/${index}`;
         this.identities = this.http.get<GetIdentityNameResponse>(url).pipe(
            map(response => {
               return response.identityNames.filter(value =>
                  !(index == this.type && this.model.name === value.name));
            })
         );

         this.identities.subscribe((identities: IdentityId[]) => this.subIdentity = identities && identities.length ? identities[0] : null);
      }
   }

   private initializeUserForm() {
      const model = <EditUserPaneModel> this.model;
      this.identityTypes = this.userIdentityTypes.slice(0);
      this.form.get("active").setValue(model.status);
      this.form.get("alias").setValue(model.alias);
      this.form.get("organization").setValue(model.organization);
      this.form.get("email").setValue(model.email);
      this.form.get("locale").setValue(model.locale);

      if(model.currentUser) {
         this.form.get("active").disable();
      }

      this.updatePassword(false);
   }

   private initializeRoleForm() {
      const roleModel = <EditRolePaneModel> this.model;
      this.form.get("defaultRole").setValue(roleModel.defaultRole);
      this.form.get("organization").setValue(roleModel.organization);
      this.form.get("sysAdmin").setValue(roleModel.isSysAdmin);
      this.form.get("description").setValue(roleModel.description);
   }

   private initializeOrganizationForm() {
      const orgModel = <EditOrganizationPaneModel> this.model;

      if(this.form.get("id") != null) {
         this.form.get("id").setValue(orgModel.id);
      }

      this.form.get("locale").setValue(orgModel.locale);

      this.properties = orgModel.properties;
   }

   public initializeGroupForm() {
      const groupModel = <EditGroupPaneModel> this.model;
      this.form.get("organization").setValue(groupModel.organization);
   }

   updatePassword(enable: boolean) {
      // Set the password property to null if unchanged
      const passGroup: UntypedFormGroup = <UntypedFormGroup> this.form.get("changePassword");

      if(!this.user || !this.model.editable) {
         this.form.get("changePasswordEnabled").disable({emitEvent: false});

         return;
      }
      else {
         this.form.get("changePasswordEnabled").enable({emitEvent: false});
      }

      if(enable) {
         passGroup.enable({emitEvent: false});

         if(!this.changingPassword) {
            passGroup.controls.password.setValue("", {emitEvent: false});
            passGroup.controls.confirmPassword.setValue("", {emitEvent: false});
         }
      }
      else {
         passGroup.disable({emitEvent: false});
         passGroup.controls.password.setValue(null, {emitEvent: false});
      }
   }

   add() {
      let addedType = IdentityType.USER;

      switch(this.identity) {
      case "_#(js:Users)":
         addedType = IdentityType.USER;
         break;
      case "_#(js:Groups)":
         addedType = IdentityType.GROUP;
         break;
      case "_#(js:Roles)":
         addedType = IdentityType.ROLE;
         break;
      case "_#(js:Organizations)":
         addedType = IdentityType.ORGANIZATION;
         break;
      }

      const identityModel: IdentityModel = {
         identityID: this.subIdentity,
         type: addedType
      };

      if((addedType === IdentityType.USER || addedType === IdentityType.GROUP || addedType == IdentityType.ORGANIZATION) &&
         !this.members.some((member) => member.identityID === this.subIdentity))
      {
         this.members.push(identityModel);
         this.members = this.members.slice(0);
      }
      else if(addedType === IdentityType.ROLE &&
         !this.roles.some((role) => role.identityID === this.subIdentity)) {
         this.roles.push(identityModel);
         this.roles = this.roles.slice(0);
      }

      this.updateModel();
   }

   isModelChanged(): boolean {
      return !Tool.isEquals(this.model, this.originalModel);
   }

   reset() {
      if(this.smallDevice) {
         this.cancel.emit();
      }

      this.restoreState();
      this.setOriginalState();

      this.form.controls["name"].setValue(this.originalModel.name);
      this.form.controls["theme"].setValue(this.originalModel.theme || "");

      if(this.user) {
         (<UntypedFormGroup>this.form.controls["changePassword"])
            .controls["password"].setValue("");
         (<UntypedFormGroup>this.form.controls["changePassword"])
            .controls["confirmPassword"].setValue("");
         (<UntypedFormGroup>this.form).controls["changePasswordEnabled"].setValue(false);
      }

      if(this.role) {
         this.form.controls["defaultRole"].setValue((<EditRolePaneModel> this.originalModel).defaultRole);
         this.form.controls["description"].setValue((<EditRolePaneModel> this.originalModel).description);
         this.form.controls["sysAdmin"].setValue((<EditRolePaneModel> this.originalModel).isSysAdmin);
         this.form.get("organization").setValue((<EditRolePaneModel> this.originalModel).organization);

      }
      else if(this.user) {
         this.form.get("active").setValue((<EditUserPaneModel> this.originalModel).status);
         this.form.get("alias").setValue((<EditUserPaneModel> this.originalModel).alias);
         this.form.get("email").setValue((<EditUserPaneModel> this.originalModel).email);
         this.form.get("locale").setValue((<EditUserPaneModel> this.originalModel).locale);
         this.form.get("organization").setValue((<EditUserPaneModel> this.originalModel).organization);
      }
      else if(this.organization) {
         this.form.get("id").setValue((<EditOrganizationPaneModel> this.originalModel).id);
         this.form.get("locale").setValue((<EditOrganizationPaneModel> this.originalModel).locale);
      }
      else if(this.group) {
         this.form.get("organization").setValue((<EditGroupPaneModel> this.originalModel).organization);
      }

      this.members = this.originalModel.members.slice(0);
      this.permittedIdentities = this.originalModel.permittedIdentities.slice(0);
      this.roles = this.originalModel.roles.map((role) =>
         <IdentityModel>{identityID: role, type: IdentityType.ROLE});
      this.pageChanged.emit(false);
   }

   apply() {
      this.updateModel();
      this.model.oldName = this.originalModel.name;
      this.originalModel.oldName = this.model.oldName;

      if(this.type == IdentityType.ROLE) {
         const cmodel = <EditRolePaneModel> this.model;
         this.roleSettingsChanged.emit(cmodel);
      }
      else if(this.type == IdentityType.USER) {
         const cmodel = <EditUserPaneModel> this.model;
         this.userSettingsChanged.emit(cmodel);
      }
      else if(this.type === IdentityType.GROUP) {
         const cmodel = <EditGroupPaneModel> this.model;
         this.groupSettingsChanged.emit(cmodel);
      }
      else if(this.type == IdentityType.ORGANIZATION) {
         const cmodel = <EditOrganizationPaneModel> this.model;
         this.organizationSettingsChanged.emit(cmodel);
         this.identityEditable = false;
      }

      this.pageChanged.emit(false);
   }

   updateTemplate() {
      //prevent user from editing while saving
      if(this.type == IdentityType.ORGANIZATION) {
         const cmodel = <EditOrganizationPaneModel> this.model;
         this.setTemplateOrganizationClicked.emit(cmodel);
      }
      this.pageChanged.emit(false);
   }

   resetTemplate() {
      if(this.type == IdentityType.ORGANIZATION) {
         const cmodel = null;
         this.setTemplateOrganizationClicked.emit(cmodel);
      }
      this.pageChanged.emit(false);
   }

   updateModel(): void {
      if(!!this.form.value["name"]) {
         this.model.name = this.form.value["name"].trim();
      }

      this.model.theme = this.form.value["theme"];
      const changingPassword = !!this.form.get("changePasswordEnabled").value;

      if(this.user && changingPassword) {
         (<EditUserPaneModel>this.model).password = this.pwForm.value["password"];
      }
      else if(this.user && !this.changingPassword) {
         (<EditUserPaneModel>this.model).password = null;
      }

      this.model.roles = this.roles.map((role) => role.identityID);
      this.model.members = this.members;
      this.model.permittedIdentities = this.permittedIdentities.slice(0);
      const cmodel = <EditRolePaneModel> this.model;

      if(this.type == IdentityType.ROLE) {
         cmodel.defaultRole = this.form.value["defaultRole"];
         cmodel.isSysAdmin = this.form.value["sysAdmin"];
         cmodel.description = this.form.value["description"];
         cmodel.organization = this.form.value["organization"];

         if(this.form.value["organization"] == null) {
            cmodel.name = this.form.value["name"].trim();
         }
         else {
            cmodel.name = this.form.value["name"].trim();
         }
         this.model = cmodel;
      }
      else if(this.type == IdentityType.USER) {
         const cmodel = <EditUserPaneModel> this.model;
         cmodel.status = !!this.form.get("active").value;
         cmodel.alias = this.form.value["alias"];
         cmodel.email = this.form.value["email"];
         cmodel.locale = this.form.value["locale"];
         cmodel.organization = this.form.value["organization"];
         this.updatePassword(changingPassword);
         this.model = cmodel;
      }
      else if(this.type == IdentityType.ORGANIZATION) {
         const cmodel = <EditOrganizationPaneModel> this.model;
         cmodel.id = this.form.value["id"];
         cmodel.locale = this.form.value["locale"];
         cmodel.properties = this.properties;
         this.model = cmodel;
      }
      else if (this.type == IdentityType.GROUP) {
         const cmodel = <EditGroupPaneModel> this.model;
         cmodel.name = this.form.value["name"].trim();
         cmodel.organization = this.form.value["organization"];
      }

      this.changingPassword = changingPassword;
      this.pageChanged.emit(true);
   }

   private setOriginalState(): void {
      this.originalModel = Tool.clone(this.model);
      this.originalRoles = Tool.clone(this.roles);
      this.originalMembers = Tool.clone(this.members);
      this.originalPermissions = Tool.clone(this.permittedIdentities);
      this.originalProperties = Tool.clone(this.properties);
   }

   private restoreState(): void {
      this.model = this.originalModel;
      this.roles = this.originalRoles;
      this.members = this.originalMembers;
      this.permittedIdentities = this.originalPermissions;
      this.properties = this.originalProperties;
   }

   onMembersChanged(change: IdentityModel[]): void {
      this.members = change;
      this.updateModel();
   }

   onRolesChanged(change: IdentityModel[]): void {
      this.roles = change;
      this.updateModel();
   }

   onPermittedIdentitiesChanged(change: IdentityModel[]): void {
      this.permittedIdentities = change;
      this.updateModel();
   }

   onPropertyChanged(properties: PropertyModel[]): void {
      this.properties = [];

      if(this.properties != null) {
         for(let i = 0; i < properties.length; i++) {
            this.properties.push(properties[i]);
         }
      }

      this.updateModel();
   }

   getThemes(): Observable<IdentityTheme[]> {
      return this.http.get<IdentityThemeList>("../api/em/security/themes")
         .pipe(map(result => result.themes?.sort((a, b) => {
            return a.name.localeCompare(b.name);
         })));
   }
}
