package com.dotcms.ai.util;

import com.dotcms.ai.app.AppKeys;
import com.dotcms.security.apps.AppSecrets;
import com.dotcms.security.apps.Secret;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.util.UtilMethods;
import io.vavr.control.Try;
import java.util.Map;
import java.util.Optional;

public class AIUtil {

    public static Map<String, Secret> getSecrets(Contentlet contentlet) {
        return getSecrets(contentlet.getHost());
    }

    public static Map<String, Secret> getSecrets(String hostId) {
        Host host = Try.of(() -> APILocator.getHostAPI().find(hostId, APILocator.systemUser(), true)).getOrNull();
        if (UtilMethods.isEmpty(() -> host.getIdentifier())) {
            return Map.of();
        }
        Optional<AppSecrets> secrets = Try.of(
                        () -> APILocator.getAppsAPI().getSecrets(AppKeys.APP_KEY, true, host, APILocator.systemUser()))
                .getOrElse(Optional.empty());
        if (secrets.isEmpty()) {
            return Map.of();
        }

        return secrets.get().getSecrets();
    }





}
