package com.dotcms.ai.vision.api;

import com.dotcms.contenttype.model.field.Field;
import com.dotcms.contenttype.model.type.DotAssetContentType;
import com.dotmarketing.portlets.contentlet.business.exporter.ImageFilterExporter;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vavr.Lazy;
import io.vavr.Tuple2;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public interface AIVisionAPI {

    static final String AI_VISION_AUTOTAG_CONTENTTYPES ="AI_VISION_AUTOTAG_CONTENTTYPES";

    static final String AI_VISION_MODEL = "AI_VISION_MODEL";

    static final String AI_VISION_MAX_TOKENS = "AI_VISION_MAX_TOKENS";

    static final String AI_VISION_PROMPT = "AI_VISION_PROMPT";

    static final String AI_VISION_TAG_FIELD = DotAssetContentType.TAGS_FIELD_VAR;

    static final String AI_VISION_ALT_TEXT_VARIBLE = "altText";

    static final Lazy<AIVisionAPI> instance = Lazy.of(OpenAIVisionAPIImpl::new);



    boolean tagImageIfNeeded(Contentlet contentlet);

    boolean tagImageIfNeeded(Contentlet contentlet, Field binaryField);

    boolean addAltTextIfNeeded(Contentlet contentlet);


    boolean addAltTextIfNeeded(Contentlet contentlet, Field binaryField, Field altTextField);

    Optional<Tuple2<String, List<String>>> readImageTagsAndDescription(Contentlet contentlet, Field imageToProcess);



}
