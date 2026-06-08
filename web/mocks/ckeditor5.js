// Mock stub for ckeditor5 - ESM-only package can't be loaded directly in Jest (CommonJS mode).
// This stub provides empty implementations so components that import from ckeditor5
// can be loaded in the test environment without errors.
class Editor {}
Editor.create = () => Promise.resolve(new Editor());
Editor.builtinPlugins = [];
Editor.defaultConfig = {};

module.exports = {
   default: Editor,
   ClassicEditor: Editor,
   Alignment: class Alignment {},
   Autosave: class Autosave {},
   Base64UploadAdapter: class Base64UploadAdapter {},
   Bold: class Bold {},
   Essentials: class Essentials {},
   FontBackgroundColor: class FontBackgroundColor {},
   FontColor: class FontColor {},
   FontFamily: class FontFamily {},
   FontSize: class FontSize {},
   Heading: class Heading {},
   ImageBlock: class ImageBlock {},
   ImageInsert: class ImageInsert {},
   ImageInsertViaUrl: class ImageInsertViaUrl {},
   ImageResize: class ImageResize {},
   ImageToolbar: class ImageToolbar {},
   ImageUpload: class ImageUpload {},
   Indent: class Indent {},
   Italic: class Italic {},
   Link: class Link {},
   List: class List {},
   Paragraph: class Paragraph {},
   Strikethrough: class Strikethrough {},
   Subscript: class Subscript {},
   Superscript: class Superscript {},
   TextPartLanguage: class TextPartLanguage {},
   Underline: class Underline {},
};
