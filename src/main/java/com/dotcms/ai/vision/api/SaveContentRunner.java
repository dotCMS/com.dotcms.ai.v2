package com.dotcms.ai.vision.api;


import com.dotcms.contenttype.model.field.Field;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.db.LocalTransaction;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.liferay.portal.model.User;


public class SaveContentRunner implements Runnable {

    final Contentlet contentlet;
    final User user;


    SaveContentRunner(Contentlet contentlet, User user) {
        this.contentlet = contentlet;


        this.user = user;
    }


    void saveContent() {
        try {
            contentlet.setProperty(Contentlet.WORKFLOW_IN_PROGRESS, Boolean.TRUE);
            contentlet.setProperty(Contentlet.SKIP_RELATIONSHIPS_VALIDATION, Boolean.TRUE);
            contentlet.setProperty(Contentlet.DONT_VALIDATE_ME, Boolean.TRUE);

            final boolean isPublished = APILocator.getVersionableAPI().isLive(contentlet);
            final Contentlet checkedContentlet = APILocator.getContentletAPI().checkin(contentlet, user, false);
            if (isPublished) {
                checkedContentlet.setProperty(Contentlet.WORKFLOW_IN_PROGRESS, Boolean.TRUE);
                checkedContentlet.setProperty(Contentlet.SKIP_RELATIONSHIPS_VALIDATION, Boolean.TRUE);
                checkedContentlet.setProperty(Contentlet.DONT_VALIDATE_ME, Boolean.TRUE);
            }
        } catch (Exception e) {
            throw new DotRuntimeException(e);
        }
    }

    @Override
    public void run() {
        LocalTransaction.unchecked(this::saveContent);
    }


}
