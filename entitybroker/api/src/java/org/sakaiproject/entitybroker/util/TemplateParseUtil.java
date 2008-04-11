/**
 * $Id$
 * $URL$
 * TemplateParseUtil.java - entity-broker - Apr 10, 2008 9:57:29 AM - azeckoski
 **************************************************************************
 * Copyright (c) 2008 Centre for Applied Research in Educational Technologies, University of Cambridge
 * Licensed under the Educational Community License version 1.0
 * 
 * A copy of the Educational Community License has been included in this 
 * distribution and is available at: http://www.opensource.org/licenses/ecl1.php
 *
 * Aaron Zeckoski (azeckoski@gmail.com) (aaronz@vt.edu) (aaron@caret.cam.ac.uk)
 */

package org.sakaiproject.entitybroker.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sakaiproject.entitybroker.EntityView;

/**
 * Utility class to handle the URL template parsing (entity template parsing)
 * 
 * @author Aaron Zeckoski (aaron@caret.cam.ac.uk)
 */
public class TemplateParseUtil {

   public static final char SEPARATOR = EntityView.SEPARATOR;
   public static final String BRACES = "[\\{\\}]";

   public static final String PREFIX = EntityView.PREFIX;
   public static final String ID = EntityView.ID;

   /**
    * Defines the parse template for the "list" operation,
    * return a list of all records,
    * typically /{prefix}
    */
   public static final String TEMPLATE_LIST = EntityView.VIEW_LIST;
   /**
    * Defines the parse template for the "show" operation,
    * access a record OR POST operations related to a record,
    * typically /{prefix}/{id}
    */
   public static final String TEMPLATE_SHOW = EntityView.VIEW_SHOW;
   /**
    * Defines the parse template for the "new" operation,
    * return a form for creating a new record,
    * typically /{prefix}/new
    */
   public static final String TEMPLATE_NEW  = EntityView.VIEW_NEW;
   /**
    * Defines the parse template for the "edit" operation,
    * access the data to modify a record,
    * typically /{prefix}/{id}/edit
    */
   public static final String TEMPLATE_EDIT = EntityView.VIEW_EDIT;
   /**
    * Defines the parse template for the "delete" operation,
    * access the data to remove a record,
    * typically /{prefix}/{id}/delete
    */
   public static final String TEMPLATE_DELETE = EntityView.VIEW_DELETE;

   /**
    * Defines the order that parse templates will be processed in and
    * the set of parse template types (keys) which must be defined,
    * the first one to match will be used when parsing in a path
    */
   public static String[] PARSE_TEMPLATE_KEYS = {
      TEMPLATE_EDIT,
      TEMPLATE_DELETE,
      TEMPLATE_NEW,
      TEMPLATE_SHOW,
      TEMPLATE_LIST
   };


   /**
    * Defines the valid chars for a replacement variable
    */
   public static String VALID_VAR_CHARS = "[A-Za-z0-9_\\-\\.=:;]";
   /**
    * Defines the valid chars for a parser input (e.g. entity reference)
    */
   public static String VALID_INPUT_CHARS = "[A-Za-z0-9_\\-\\.=:;"+SEPARATOR+"]";
   /**
    * Defines the valid chars for a template
    */
   public static String VALID_TEMPLATE_CHARS = "[A-Za-z0-9_\\-\\.=:;"+SEPARATOR+"\\{\\}]";

   /**
    * Stores the preloaded default templates
    */
   public static List<Template> defaultTemplates;
   /**
    * Stores the preloaded processed default templates
    */
   public static List<PreProcessedTemplate> defaultPreprocessedTemplates;

   // static initializer
   static {
      defaultTemplates = new ArrayList<Template>();
      // this load order should match the array above
      defaultTemplates.add( new Template(TEMPLATE_EDIT, SEPARATOR + "{"+PREFIX+"}" + SEPARATOR + "{"+ID+"}" + SEPARATOR + "edit") );
      defaultTemplates.add( new Template(TEMPLATE_DELETE, SEPARATOR + "{"+PREFIX+"}" + SEPARATOR + "{"+ID+"}" + SEPARATOR + "delete") );
      defaultTemplates.add( new Template(TEMPLATE_NEW,  SEPARATOR + "{"+PREFIX+"}" + SEPARATOR + "new") );
      defaultTemplates.add( new Template(TEMPLATE_SHOW, SEPARATOR + "{"+PREFIX+"}" + SEPARATOR + "{"+ID+"}") );
      defaultTemplates.add( new Template(TEMPLATE_LIST, SEPARATOR + "{"+PREFIX+"}") );

      defaultPreprocessedTemplates = preprocessTemplates(defaultTemplates);
   }

   /**
    * Check if a templateKey is valid, if not then throws {@link IllegalArgumentException}
    * @param templateKey a key from the set of template keys {@link #PARSE_TEMPLATE_KEYS}
    */
   public static void checkTemplateKey(String templateKey) {
      boolean found = false;
      for (int i = 0; i < PARSE_TEMPLATE_KEYS.length; i++) {
         if (PARSE_TEMPLATE_KEYS[i].equals(templateKey)) {
            found = true;
            break;
         }
      }
      if (! found) {
         throw new IllegalArgumentException("Invalid parse template key: " + templateKey);
      }
   }

   /**
    * Get a default template for a specific template key
    * @param templateKey a key from the set of template keys {@link #PARSE_TEMPLATE_KEYS}
    * @return the template
    * @throws IllegalArgumentException if the template key is invalid
    */
   public static String getDefaultTemplate(String templateKey) {
      String template = null;
      for (Template t : defaultTemplates) {
         if (t.templateKey.equals(templateKey)) {
            template = t.template;
         }
      }
      if (template == null) {
         throw new IllegalArgumentException("No default template available for this key: " + templateKey);
      }
      return template;
   }

   /**
    * Validate a template, if invalid then an exception is thrown
    * @param template a parse template
    */
   public static void validateTemplate(String template) {
      if (template == null || template == "") {
         throw new IllegalArgumentException("Template cannot be null or empty string");
      } else if (template.charAt(0) != SEPARATOR) {
         throw new IllegalArgumentException("Template must start with " + SEPARATOR);
      } else if (template.charAt(template.length()-1) == SEPARATOR) {
         throw new IllegalArgumentException("Template cannot end with " + SEPARATOR);
      } else if (template.indexOf("}{") != -1) {
         throw new IllegalArgumentException("Template replacement variables ({var}) " +
               "cannot be next to each other, " +
               "there must be something between each template variable");
      } else if (template.indexOf("{}") != -1) {
         throw new IllegalArgumentException("Template replacement variables ({var}) " +
               "cannot be empty ({}), there must be a value between them");
      } else if (! template.matches(VALID_TEMPLATE_CHARS+"+")) {
         // take out {} and check if the template uses valid chars
         throw new IllegalArgumentException("Template can only contain the following (not counting []): " + VALID_TEMPLATE_CHARS);
      }
   }

   /**
    * Takes a template and replaces the segment keys with the segment values,
    * keys should not have {} around them yet as these will be added around each key
    * in the segments map
    * 
    * @param template a parse template with {variables} in it
    * @param segments a map of all possible segment keys and values,
    * unused keys will be ignored
    * @return the template with replacement values filled in
    * @throws IllegalArgumentException if all template variables cannot be replaced or template is empty/null
    */
   public static String mergeTemplate(String template, Map<String, String> segments) {
      if (template == null || "".equals(template) || segments == null) {
         throw new IllegalArgumentException("Cannot operate on null template/segments, template must not be empty");
      }

      int vars = 0;
      char[] chars = template.toCharArray();
      for (int i = 0; i < chars.length; i++) {
         if (chars[i] == '{') {
            vars++;
         }
      }
      String reference = template;
      int replacements = 0;
      for (String key : segments.keySet()) {
         String keyBraces = "{"+key+"}";
         if (reference.contains(keyBraces)) {
            reference = reference.replace(keyBraces, segments.get(key));
            replacements++;
         }
      }
      if (replacements != vars) {
         throw new IllegalArgumentException("Failed merge, could not replace all variables ("+vars
               +") in the template, only replaced " + replacements);
      }

      return reference;
   }


   /**
    * Find the extension from a string and return the string without the extension and the extension,
    * an extension is a period (".") followed by any number of non-periods,
    * the original input is returned as the 0th item in the array
    * 
    * @param input any string
    * @return an array with the string without the extension or the original if it has none in position 1
    * and the extension in the position 2, position 0 holds the original input string
    */
   public static String[] findExtension(String input) {
      // regex pattern: ".*(\\.[^.]+|$)"
      String stripped = input;
      String extension = null;
      int extensionLoc = input.lastIndexOf('.', input.length());
      if (extensionLoc > 0 
            && extensionLoc < input.length()-1) {
         stripped = input.substring(0, extensionLoc);
         if (input.length() > extensionLoc) {
            extension = input.substring(extensionLoc + 1);
         }
      }
      return new String[] {input, stripped, extension};
   }

   /**
    * Parse a string and attempt to match it to a template and then 
    * return the key of the matching template
    * in the map as {@link #TEMPLATEKEY} -> key, 
    * along with all the parsed out keys and values<br/>
    * 
    * @param input a string which we want to attempt to match to one of the templates
    * @param preprocessed the analyzed templates to attempt to match in the order they should attempt the match, 
    * can be a single template or multiples, use {@link #preprocessTemplates(List)} to create this
    * (recommend caching the preprocessed templates to avoid reprocessing them over and over)
    * @return a map containing the matching template, templateKey, and all the replacement 
    * variable names and values OR empty map if no templates matched
    */
   public static ProcessedTemplate parseTemplate(String input, List<PreProcessedTemplate> preprocessed) {
      if (preprocessed == null) {
         preprocessed = defaultPreprocessedTemplates;
      }
      if (input == null || "".equals(input)) {
         throw new IllegalArgumentException("input cannot be null or empty");
      }
      if (! input.matches(VALID_INPUT_CHARS+"+")) {
         throw new IllegalArgumentException("input must consist of the following chars only (not counting []): " + VALID_INPUT_CHARS);         
      }
      ProcessedTemplate analysis = null;
      Map<String, String> segments = new HashMap<String, String>();
      // strip off the extension if there is one
      String[] ext = findExtension(input);
      input = ext[1];
      String extension = ext[2];
      // try to get matches
      for (PreProcessedTemplate ppt : preprocessed) {
         segments.clear();
         String regex = ppt.regex + "(?:/"+VALID_INPUT_CHARS+"+|$)"; // match extras if there are any (greedy match)
         Pattern p = Pattern.compile(regex);
         Matcher m = p.matcher(input);
         if ( m.matches() ) {
            if ( m.groupCount() == ppt.variableNames.size() ) {
               for (int j = 0; j < m.groupCount(); j++) {
                  String subseq = m.group(j+1); // ignore first group, it is the whole pattern
                  if (subseq != null) {
                     segments.put(ppt.variableNames.get(j), subseq);
                  }
               }
               // fill in the analysis object
               analysis = new ProcessedTemplate(ppt.templateKey, ppt.template, regex, 
                     new ArrayList<String>(ppt.variableNames), 
                     new HashMap<String, String>(segments), extension);
               break;
            }
         }
      }

      if (analysis == null) {
         // no matches so should we die?
      }
      return analysis;
   }

   /**
    * Process the templates before attempting to match them,
    * this is here so we can reduce the load of reprocessing the same templates over and over
    * @param templates the templates to attempt to preprocess, can be a single template or multiples
    * @return the list of preprocessed templates (in the same order as input)
    */
   public static List<PreProcessedTemplate> preprocessTemplates(List<Template> templates) {
      if (templates == null) {
         templates = defaultTemplates;
      }
      List<PreProcessedTemplate> analyzedTemplates = new ArrayList<PreProcessedTemplate>();
      for (Template template : templates) {
         List<String> vars = new ArrayList<String>();
         StringBuilder regex = new StringBuilder();
         String[] parts = template.template.split(BRACES);
         for (int j = 0; j < parts.length; j++) {
            String part = parts[j];
            if (j % 2 == 0) {
               // odd parts are textual breaks
               regex.append(part);
            } else {
               // even parts are replacement vars
               vars.add(part);
               regex.append("(");
               regex.append(VALID_VAR_CHARS);
               regex.append("+)");
            }
         }
         analyzedTemplates.add(new PreProcessedTemplate(template.templateKey, 
               template.template, regex.toString(), new ArrayList<String>(vars)) );
      }      
      return analyzedTemplates;
   }

   /**
    * Represents a parseable template
    * 
    * @author Aaron Zeckoski (aaron@caret.cam.ac.uk)
    */
   public static class Template {
      /**
       * the template key, from the set of template keys {@link #PARSE_TEMPLATE_KEYS}
       */
      public String templateKey;
      /**
       * the template itself
       */
      public String template;

      /**
       * Used to create a template for loading
       * @param templateKey template identifier, from the set of template keys {@link #PARSE_TEMPLATE_KEYS}
       * @param template the parseable template
       */
      public Template(String templateKey, String template) {
         this.templateKey = templateKey;
         this.template = template;
      }
   }

   /**
    * Contains the data for templates,
    * each template must have a template key and the template itself
    * 
    * @author Aaron Zeckoski (aaron@caret.cam.ac.uk)
    */
   public static class PreProcessedTemplate extends Template {
      /**
       * The regular expression to match this template exactly 
       */
      public String regex;
      /**
       * The list of variable names found in this template
       */
      public List<String> variableNames;

      protected PreProcessedTemplate(String templateKey, String template, String regex, List<String> variableNames) {
         super(templateKey, template);
         this.regex = regex;
         this.variableNames = variableNames;
      }
   }

   /**
    * Contains the processed template with the values from the processed input string
    * that was determined to be related to this template
    * 
    * @author Aaron Zeckoski (aaron@caret.cam.ac.uk)
    */
   public static class ProcessedTemplate extends PreProcessedTemplate {
      /**
       * The list of segment values (variableName -> matched value),
       * this will be filled in by the {@link TemplateParseUtil#parseTemplate(String, Map)} method
       * and will be null otherwise
       */
      public Map<String, String> segmentValues;
      /**
       * The extension found while processing the input string,
       * null if none could be found
       */
      public String extension;

      public ProcessedTemplate(String templateKey, String template, String regex,
            List<String> variableNames, Map<String, String> segmentValues, String extension) {
         super(templateKey, template, regex, variableNames);
         this.segmentValues = segmentValues;
         this.extension = extension;
      }
   }

}
