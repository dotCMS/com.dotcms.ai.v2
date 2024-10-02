package com.dotcms.ai.vision.api;

import com.dotcms.contenttype.model.field.Field;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import io.vavr.Tuple2;
import java.util.List;
import java.util.Optional;

public interface AIVisionAPI {

    boolean tagImageIfNeeded(Contentlet contentlet);

    boolean tagImageIfNeeded(Contentlet contentlet, Field binaryField);

    Contentlet addAltTextIfNeeded(Contentlet contentlet);


    Contentlet addAltTextIfNeeded(Contentlet contentlet, Field binaryField, Field altTextField);

    Optional<Tuple2<String, List<String>>> readImageTagsAndDescription(Contentlet contentlet, Field imageToProcess);
}
