package com.dotcms.ai.vision;

import com.dotcms.ai.translation.workflow.OpenAITranslationActionlet;
import com.dotcms.ai.vision.listener.OpenAIImageTaggingContentListener;
import com.dotcms.ai.vision.workflow.OpenAIVisionAutoTagActionlet;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.portlets.workflows.actionlet.WorkFlowActionlet;
import java.util.List;
import org.osgi.framework.BundleContext;

public class Activator extends GenericBundleActivator {

    private static final OpenAIImageTaggingContentListener LISTENER = new OpenAIImageTaggingContentListener();


    private final List<WorkFlowActionlet> actionlets = List.of(
            new OpenAIVisionAutoTagActionlet(),
            new OpenAITranslationActionlet()
    );


    public void start(BundleContext context) throws Exception {

        // Register Embedding Actionlet
        actionlets.forEach(a -> this.registerActionlet(context, a));

        // Add the Embedding Listener (this does nothing right now)
        subscribeEmbeddingsListener();


    }

    public void stop(BundleContext context) throws Exception {

        unsubscribeEmbeddingsListener();

        // unregistering the actionlets actually removes them and their config from the system
        //this.unregisterActionlets();

    }

    private void unsubscribeEmbeddingsListener() {
        APILocator.getLocalSystemEventsAPI().unsubscribe(LISTENER);
    }


    private void subscribeEmbeddingsListener() {

        APILocator.getLocalSystemEventsAPI().subscribe(LISTENER);

    }


}
