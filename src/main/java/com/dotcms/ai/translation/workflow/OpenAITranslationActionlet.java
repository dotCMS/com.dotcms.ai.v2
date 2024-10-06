package com.dotcms.ai.translation.workflow;

import com.dotcms.ai.util.AIUtil;
import com.dotcms.ai.util.VelocityContextFactory;
import com.dotcms.ai.vision.api.OpenAIVisionAPIImpl;
import com.dotcms.contenttype.model.field.Field;
import com.dotcms.contenttype.model.type.BaseContentType;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.languagesmanager.model.Language;
import com.dotmarketing.portlets.workflows.actionlet.TranslationActionlet;
import com.dotmarketing.portlets.workflows.model.WorkflowActionClassParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowActionFailureException;
import com.dotmarketing.portlets.workflows.model.WorkflowActionletParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowProcessor;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.VelocityUtil;
import com.dotmarketing.util.json.JSONObject;
import io.vavr.control.Try;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.velocity.context.Context;

public class OpenAITranslationActionlet extends TranslationActionlet {

    @Override
    public String getName() {
        return "Open AI - Translate Content";
    }

    static int MAX_LANGUAGE_VARIABLE_CONTEXT = 1000;

    static final String AI_TRANSLATION_PROMPT_KEY = "AI_TRANSLATION_PROMPT";
    static final String AI_TRANSLATION_MODEL_KEY = "AI_TRANSLATION_MODEL";
    static final String AI_TRANSLATIONS_MAX_TOKENS = "AI_TRANSLATIONS_MAX_TOKENS";
    static final String TRANSLATE_TO = "translateTo";
    static final String FIELD_TYPES = "fieldTypes";
    static final String TRANSLATE_FIELDS = "translateFields";
    static final String IGNORE_FIELDS = "ignoreFields";
    static final String CONTEXT_PREFIX = "contextPrefix";
    static final String COMMA_SPLITER="[,\\s]+";


    @Override
    public List<WorkflowActionletParameter> getParameters() {
        List<WorkflowActionletParameter> params = new ArrayList<>();
        params.add(new WorkflowActionletParameter(TRANSLATE_TO, "Translation to these languages (comma separated lang or lang-country codes or `*` for all )", "*", false));
        params.add(new WorkflowActionletParameter(FIELD_TYPES, "Always Translate these Field types (optional, comma separated)", "text,wysiwyg,textarea,storyblock", true));
        params.add(new WorkflowActionletParameter(TRANSLATE_FIELDS, "Then also always translate these Fields (optional, comma separated var names)", "", false));
        params.add(new WorkflowActionletParameter(IGNORE_FIELDS, "Finally, ignore these fields (optional, comma separated var names)", "", false));
        params.add(new WorkflowActionletParameter(CONTEXT_PREFIX, "Language variable prefix to include as glossary - this is the prefix of language variables that you want to include as glossary for the translation. Leave empty for none.  Set to `*` for all (up to " + MAX_LANGUAGE_VARIABLE_CONTEXT +").", "", false));

        return params;
    }


    @Override
    public String getHowTo() {
        return "This actionlet will attempt to translate the content of a field using OpenAI's model. "
                + "The translate to field should be a comma separated list of languages you want to translate "
                + "the content into - you can leave it blank to translate to all languages. The fields to translate are built from the (Always Field Types + Always Field Variable) - Always Ignore Fields. "
                + "dotCMS will add the language variables as a context for translations.  You can specify a prefix of the keys you wish to include to get the context variables. ";
    }


    @Override
    public void executeAction(WorkflowProcessor processor, Map<String, WorkflowActionClassParameter> params)
            throws WorkflowActionFailureException {

        final Contentlet contentlet = processor.getContentlet();

        final Optional<String> contextPrefix = Try.of(()->params.get(CONTEXT_PREFIX).getValue().trim()).toJavaOptional();
        final List<Language> languages = new ArrayList(languagesToTranslate(params.get(TRANSLATE_TO).getValue()));
        languages.removeIf(lang -> lang.getId() == contentlet.getLanguageId());
        Set<Field> fields = getIncludedFields(contentlet,
                params.get(FIELD_TYPES).getValue(),
                params.get(IGNORE_FIELDS).getValue(),
                params.get(TRANSLATE_FIELDS).getValue());



        for(Language lang : languages){


            translateContentlet(contentlet, lang, fields, contextPrefix);


        }


    }

    private void translateContentlet(Contentlet contentlet, Language targetLanguage, Set<Field> fields, Optional<String> contextPrefix) {


        Language sourceLang = APILocator.getLanguageAPI().getLanguage(contentlet.getLanguageId());
        Map<String,String> context = buildContext(contextPrefix, contentlet.getLanguageId(), targetLanguage.getId());
        JSONObject contextKeyValues = new JSONObject(context);
        JSONObject sourceJson = new JSONObject();
        fields.forEach(f->{
            String value = contentlet.getStringProperty(f.variable());
            if(UtilMethods.isSet(value)){
                sourceJson.put(f.variable(), (String) value);
            }
        });


        System.out.println("contextKeyValues: " + contextKeyValues.toString(2) + "\n\n");

        System.out.println("sourceJson: " + sourceJson.toString(2) + "\n\n");




        final Context ctx = VelocityContextFactory.getMockContext(contentlet, APILocator.systemUser());
        ctx.put("translationModel", getTranslationModel(contentlet.getHost()));
        ctx.put("sourceJson", sourceJson.toString());
        if(!contextKeyValues.isEmpty()) {
            ctx.put("contextKeyValues", contextKeyValues.toString());
        }
        ctx.put("sourceLanguage", sourceLang.getLanguage() + "(" + sourceLang.getCountry() +")");
        ctx.put("targetLanguage", targetLanguage.getLanguage() + "(" + targetLanguage.getCountry() +")" );
        ctx.put("maxTokens", getMaxTokens(contentlet.getHost()));
        Optional<String> prompt = Try.of(()->VelocityUtil.eval(getAITranslationTemplate(contentlet.getHost()), ctx)).toJavaOptional();

        if(prompt.isEmpty()){
            throw new DotRuntimeException("Could not get translation prompt");
        }

        System.out.println("prompt: " + prompt.get()+ "\n\n");
        JSONObject promptJson = new JSONObject(prompt.get());


        System.out.println("promptJson: " + promptJson.toString(2) + "\n\n");

    }


    List<Language> languagesToTranslate(String translateToIn) {
        String translateTo = Try.of(()->"all".equalsIgnoreCase(translateToIn.trim()) || "*".equalsIgnoreCase(translateToIn.trim()) ? "" : translateToIn ).getOrNull();

        if (UtilMethods.isEmpty(translateTo)) {
            return APILocator.getLanguageAPI().getLanguages();
        }

        List<Language> languages = new ArrayList<>();
        String[] langCodes = translateTo.split(COMMA_SPLITER);
        for (String langCode : langCodes) {
            String[] langCountry = langCode.split("[_|-]");
            Language lang = APILocator.getLanguageAPI().getLanguage(langCountry[0], langCountry.length > 1 ? langCountry[1] : null);
            if (lang != null) {
                languages.add(lang);
            }
        }
        return languages;




    }



    Set<Field> getIncludedFields(Contentlet contentlet, String fieldTypesStr, String ignoreFieldsStr, String translateFieldsStr) {


        final List<String> fieldTypes = Try.of(()-> Arrays.asList(fieldTypesStr.trim().split(COMMA_SPLITER))).getOrElse(List.of());

        final List<String> ignoreFields = Try.of(()->Arrays.asList(ignoreFieldsStr.trim().split(COMMA_SPLITER))).getOrElse(List.of());

        final List<String> translateFields = Try.of(()->Arrays.asList(translateFieldsStr.trim().split(COMMA_SPLITER))).getOrElse(List.of());

        Set<Field> fields = new HashSet<>();


        for(Field f : contentlet.getContentType().fields()){
            for(String type : fieldTypes){
                if(f.getClass().getSimpleName().toLowerCase().contains(type + "field")){
                    fields.add(f);
                }
            }
            for(String translate : translateFields){
                if(f.variable().equalsIgnoreCase(translate)){
                    fields.add(f);
                }
            }
            for(String ignore : ignoreFields){
                if(f.variable().equalsIgnoreCase(ignore)){
                    fields.remove(f);
                }
            }
        }

        return fields;
    }







    Map<String,String> buildContext(Optional<String> prefixIn, long originalLang,long langToTranslate)  {


        if (originalLang == langToTranslate) {
            throw new DotRuntimeException("Cannot translate contentlet to the same language: " + langToTranslate);
        }


        final StringBuilder query = new StringBuilder()
                .append("+baseType:" + BaseContentType.KEY_VALUE.getType());
        if(prefixIn.isPresent()){
            query.append(" +key_dotraw:");
        }
        if(prefixIn.isEmpty() ||"*".equals(prefixIn.get())) {
            query.append("*");
        }
        query.append(" +languageId:(")
                .append(originalLang)
                .append(" OR ")
                .append(langToTranslate)
                .append(") +deleted:false");


        String queryStr = query.toString();

        Map<String,String> context = new HashMap<>();
        int limit = 1000;
        int page = 0;
        while (context.size() < MAX_LANGUAGE_VARIABLE_CONTEXT) {
            int myPage = page;
            List<Contentlet> contentResults = Try.of(()-> APILocator.getContentletAPI().search(query.toString(), limit, myPage*limit, "identifier,languageid", APILocator.systemUser(),false)).getOrElse(List.of());
            if(contentResults.isEmpty()){
                break;
            }
            for(int i=0; i<contentResults.size(); i++){
                Contentlet workingCon = contentResults.get(i);
                final int iPlusOne = i+1;
                Contentlet nextCon = Try.of(()->contentResults.get(iPlusOne)).getOrNull();

                if(UtilMethods.isEmpty(()->workingCon.getIdentifier()) || UtilMethods.isEmpty(()->nextCon.getIdentifier())){
                    break;
                }
                if(workingCon.getIdentifier().equals(nextCon.getIdentifier())){
                    if(workingCon.getLanguageId()==originalLang) {
                        context.put(workingCon.getStringProperty("value"), nextCon.getStringProperty("value"));
                    }else{
                        context.put(nextCon.getStringProperty("value"), workingCon.getStringProperty("value"));
                    }
                    i++;
                }
            }

            page++;
        }


        return context;
    }

    String getAITranslationTemplate(String hostId) {
        if (UtilMethods.isSet(() -> AIUtil.getSecrets(hostId).get(AI_TRANSLATION_PROMPT_KEY).getString())) {
            return AIUtil.getSecrets(hostId).get(AI_TRANSLATION_PROMPT_KEY).getString();
        }


        return Try.of(()->{
            try (InputStream in = OpenAIVisionAPIImpl.class.getResourceAsStream("/default-translation-prompt.vtl")) {
                return new String(in.readAllBytes());
            }
        }).getOrNull();


    }

    int getMaxTokens(String hostId) {

        return Try.of(()->Integer.parseInt(AIUtil.getSecrets(hostId).get(AI_TRANSLATIONS_MAX_TOKENS).getString()))
                .getOrElse(10000);


    }

    String getTranslationModel(String hostId) {

        if (UtilMethods.isSet(() -> AIUtil.getSecrets(hostId).get(AI_TRANSLATION_MODEL_KEY).getString())) {
            return AIUtil.getSecrets(hostId).get(AI_TRANSLATION_MODEL_KEY).getString();
        }
        return "gpt-4o";
    }
}
