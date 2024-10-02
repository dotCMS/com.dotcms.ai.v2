package com.dotcms.ai.vision.listener;

import com.dotcms.ai.app.AppKeys;
import com.dotcms.ai.vision.api.AIVisionAPI;
import com.dotcms.ai.vision.api.OpenAIVisionAPIImpl;
import com.dotcms.content.elasticsearch.business.event.ContentletArchiveEvent;
import com.dotcms.content.elasticsearch.business.event.ContentletDeletedEvent;
import com.dotcms.content.elasticsearch.business.event.ContentletPublishEvent;
import com.dotcms.security.apps.AppSecrets;
import com.dotcms.security.apps.Secret;
import com.dotcms.system.event.local.model.Subscriber;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.db.LocalTransaction;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.contentlet.model.ContentletListener;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.model.User;
import io.vavr.control.Try;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


public class OpenAIImageTaggingContentListener implements ContentletListener<Contentlet> {

    AIVisionAPI aiVisionAPI = AIVisionAPI.instance.get();


    boolean shouldAutoTag(Contentlet contentlet) {
        Host host = Try.of(() -> APILocator.getHostAPI().find(contentlet.getHost(), APILocator.systemUser(), true)).getOrNull();
        if (UtilMethods.isEmpty(() -> host.getIdentifier())) {
            return false;
        }
        Optional<AppSecrets> secrets = Try.of(
                        () -> APILocator.getAppsAPI().getSecrets(AppKeys.APP_KEY, true, host, APILocator.systemUser()))
                .getOrElse(Optional.empty());

        if (secrets.isEmpty()) {
            return false;
        }

        if(UtilMethods.isSet(()->secrets.get().getSecrets().get(AIVisionAPI.AI_VISION_AUTOTAG_CONTENTTYPES).getString())){
            String contentType = contentlet.getContentType().variable().toLowerCase();
            return secrets.get().getSecrets().get(AIVisionAPI.AI_VISION_AUTOTAG_CONTENTTYPES).getString().contains(contentType);

        }
        return false;
    }



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
        if (!shouldAutoTag(contentlet)) {
            return;
        }

        if (contentletPublishEvent.isPublish()) {
            try {
                LocalTransaction.wrap(() -> {
                    aiVisionAPI.tagImageIfNeeded(contentlet);
                });

                LocalTransaction.wrap(() -> {
                   if(aiVisionAPI.addAltTextIfNeeded(contentlet)) {
                       saveContentlet(contentlet, APILocator.systemUser());
                   }
                });

            } catch (Exception e) {
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

    private Contentlet saveContentlet(Contentlet contentlet, User user) {

        try {
            contentlet.setProperty(Contentlet.WORKFLOW_IN_PROGRESS, Boolean.TRUE);
            contentlet.setProperty(Contentlet.SKIP_RELATIONSHIPS_VALIDATION, Boolean.TRUE);
            contentlet.setProperty(Contentlet.DONT_VALIDATE_ME, Boolean.TRUE);

            final boolean isPublished = APILocator.getVersionableAPI().isLive(contentlet);
            final Contentlet savedContent = APILocator.getContentletAPI().checkin(contentlet, user, false);
            if (isPublished) {
                savedContent.setProperty(Contentlet.WORKFLOW_IN_PROGRESS, Boolean.TRUE);
                savedContent.setProperty(Contentlet.SKIP_RELATIONSHIPS_VALIDATION, Boolean.TRUE);
                savedContent.setProperty(Contentlet.DONT_VALIDATE_ME, Boolean.TRUE);
            }
            return savedContent;
        } catch (Exception e) {
            throw new DotRuntimeException(e);
        }
    }

    void logEvent(String eventType, Contentlet contentlet) {
        System.out.println(
                "GOT " + eventType + " for content: " + contentlet.getTitle() + " id:" + contentlet.getIdentifier());
        Logger.info(OpenAIImageTaggingContentListener.class,
                "GOT " + eventType + " for content: " + contentlet.getTitle() + " id:" + contentlet.getIdentifier());
    }


}
