package com.dotcms.ai.vision;

import com.dotcms.ai.vision.listener.OpenAIImageTaggingContentListener;
import com.dotcms.ai.vision.workflow.OpenAIVisionAutoTagActionlet;
import com.dotcms.contenttype.model.type.ContentType;
import com.dotcms.contenttype.model.type.KeyValueContentType;
import com.dotcms.languagevariable.business.LanguageVariableAPI;
import com.dotcms.languagevariable.business.LanguageVariableAPIImpl;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.contentlet.model.IndexPolicy;
import com.dotmarketing.portlets.languagesmanager.business.LanguageAPI;
import com.dotmarketing.portlets.languagesmanager.business.LanguageAPIImpl;
import com.dotmarketing.portlets.workflows.actionlet.WorkFlowActionlet;
import com.dotmarketing.util.Logger;
import com.liferay.portal.model.User;
import java.util.List;
import java.util.Map;
import org.osgi.framework.BundleContext;

public class Activator extends GenericBundleActivator {

    private static final OpenAIImageTaggingContentListener LISTENER = new OpenAIImageTaggingContentListener();


    private final List<WorkFlowActionlet> actionlets = List.of(

            new OpenAIVisionAutoTagActionlet()
    );


    public void start(BundleContext context) throws Exception {

        // Register Embedding Actionlet
        actionlets.forEach(a -> this.registerActionlet(context, a));

        // Add the Embedding Listener (this does nothing right now)
        subscribeEmbeddingsListener();


    }

    public void stop(BundleContext context) throws Exception {

        unsubscribeEmbeddingsListener();
        //this.unregisterActionlets();

    }

    private void unsubscribeEmbeddingsListener() {
        APILocator.getLocalSystemEventsAPI().unsubscribe(LISTENER);
    }


    public void createLanguageVariables() {
        Logger.info(this, "Creating test Language Variable contents...");

        // Get system user and required APIs

        LanguageAPI languageAPI = new LanguageAPIImpl();

        // Get the default language ID
        long languageId = languageAPI.getDefaultLanguage().getId();

        // Define key-value pairs in a map
        Map<String, String> langVariableMap = Map.of("ai-text-box-key", "AI text box value", "ai-text-area-key",
                "AI text area value");

        // Iterate through the map and create language variables
        langVariableMap.forEach((key, value) -> {
            // Check if the language variable already exists
            if (!languageVariableExists(key, languageId, APILocator.systemUser())) {
                // If not, create it
                try {
                    createLanguageVariable(key, value, languageId);
                } catch (DotDataException | DotSecurityException e) {
                    Logger.error(this.getClass(), "Error creating language variable: " + e.getMessage());
                }
            } else {
                Logger.info(this, "Language variable already exists for key: " + key);
            }
        });
    }

    private void subscribeEmbeddingsListener() {

        APILocator.getLocalSystemEventsAPI().subscribe(LISTENER);

    }


    private boolean languageVariableExists(String key, long languageId, User systemUser) {
        LanguageVariableAPI languageVariableAPI = new LanguageVariableAPIImpl();
        String langVar = languageVariableAPI.getLanguageVariable(key, languageId, systemUser);
        return langVar != null && !langVar.equals(key);
    }

    private void createLanguageVariable(String key, String value, long languageId)
            throws DotDataException, DotSecurityException {

        if (languageVariableExists(key, languageId, APILocator.systemUser())) {
            return;
        }

        ContentType languageVariableContentType = APILocator.getContentTypeAPI(APILocator.systemUser())
                .find(LanguageVariableAPI.LANGUAGEVARIABLE_VAR_NAME);

        Contentlet languageVariable = new Contentlet();

        // Set content properties for the language variable
        languageVariable.setContentTypeId(languageVariableContentType.inode());
        languageVariable.setLanguageId(languageId);

        Logger.info(this, "Creating AI Language Variable content: " + key);

        // Set key and value fields
        languageVariable.setStringProperty(KeyValueContentType.KEY_VALUE_KEY_FIELD_VAR, key);
        languageVariable.setStringProperty(KeyValueContentType.KEY_VALUE_VALUE_FIELD_VAR, value);

        // Set index policy and disable workflow
        languageVariable.setIndexPolicy(IndexPolicy.FORCE);
        languageVariable.setBoolProperty(Contentlet.DISABLE_WORKFLOW, true);

        // Check in the language variable
        languageVariable = APILocator.getContentletAPI()
                .checkin(languageVariable, APILocator.systemUser(), Boolean.FALSE);

        // Publish the language variable
        languageVariable.setIndexPolicy(IndexPolicy.FORCE);
        languageVariable.setBoolProperty(Contentlet.DISABLE_WORKFLOW, true);
        APILocator.getContentletAPI().publish(languageVariable, APILocator.systemUser(), Boolean.FALSE);
        Logger.info(this, "Key/Value has been created for key: " + key);
    }


}
