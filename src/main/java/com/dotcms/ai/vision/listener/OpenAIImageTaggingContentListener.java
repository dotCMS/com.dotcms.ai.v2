package com.dotcms.ai.vision.listener;

import com.dotcms.ai.vision.api.AIVisionAPI;
import com.dotcms.ai.vision.api.OpenAIVisionAPIImpl;
import com.dotcms.content.elasticsearch.business.event.ContentletArchiveEvent;
import com.dotcms.content.elasticsearch.business.event.ContentletDeletedEvent;
import com.dotcms.content.elasticsearch.business.event.ContentletPublishEvent;
import com.dotcms.system.event.local.model.Subscriber;
import com.dotmarketing.db.LocalTransaction;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.contentlet.model.ContentletListener;
import com.dotmarketing.util.Logger;


public class OpenAIImageTaggingContentListener implements ContentletListener<Contentlet> {

    AIVisionAPI aiVisionAPI = new OpenAIVisionAPIImpl();

    @Override
    public String getId() {
        return this.getClass().getCanonicalName();
    }


    @Override
    public void onModified(ContentletPublishEvent<Contentlet> contentletPublishEvent) {
        Contentlet contentlet = contentletPublishEvent.getContentlet();
        logEvent("onModified ", contentlet);
    }


    @Subscriber
    public void onPublish(final ContentletPublishEvent<Contentlet> contentletPublishEvent) {

        Contentlet contentlet = contentletPublishEvent.getContentlet();
        if (contentletPublishEvent.isPublish()) {
            try {
                LocalTransaction.wrap(() -> {
                    aiVisionAPI.tagImageIfNeeded(contentlet);
                });
            }catch (Exception e){
                e.printStackTrace();
                Logger.error(this, "Error tagging contentlet", e);
            }

            logEvent("onPublish - PublishEvent:true", contentlet);
        } else {
            logEvent("onPublish - PublishEvent:false", contentlet);

        }
    }


    @Subscriber
    @Override
    public void onArchive(ContentletArchiveEvent<Contentlet> contentletArchiveEvent) {
        Contentlet contentlet = contentletArchiveEvent.getContentlet();
        logEvent("onArchive", contentlet);

    }

    @Subscriber
    @Override
    public void onDeleted(ContentletDeletedEvent<Contentlet> contentletDeletedEvent) {

        Contentlet contentlet = contentletDeletedEvent.getContentlet();
        logEvent("onDeleted", contentlet);

    }


    void logEvent(String eventType, Contentlet contentlet) {
        System.out.println("GOT " + eventType + " for content: " + contentlet.getTitle() + " id:" + contentlet.getIdentifier());
        Logger.info(OpenAIImageTaggingContentListener.class,
                "GOT " + eventType + " for content: " + contentlet.getTitle() + " id:" + contentlet.getIdentifier());
    }


}
