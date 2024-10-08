package com.dotcms.ai.vision.api;

import com.dotcms.ai.app.AppKeys;
import com.dotcms.ai.util.AIUtil;
import com.dotcms.ai.util.VelocityContextFactory;
import com.dotcms.ai.vision.listener.OpenAIImageTaggingContentListener;
import com.dotcms.contenttype.model.field.BinaryField;
import com.dotcms.contenttype.model.field.Field;
import com.dotcms.contenttype.model.field.TagField;
import com.dotcms.contenttype.model.type.DotAssetContentType;
import com.dotcms.rendering.velocity.util.VelocityUtil;
import com.dotcms.security.apps.AppSecrets;
import com.dotcms.security.apps.Secret;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.portlets.contentlet.business.exporter.ImageFilterExporter;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.contentlet.model.ContentletVersionInfo;
import com.dotmarketing.portlets.fileassets.business.FileAssetAPI;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.json.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.velocity.context.Context;

public class OpenAIVisionAPIImpl implements AIVisionAPI {

    static final String AI_VISION_ALT_TEXT_VARIABLE = "altText";
    static final String AI_VISION_TAG_FIELD = DotAssetContentType.TAGS_FIELD_VAR;

    static final String TAGGED_BY_DOTAI = "dot:taggedByDotAI";

    static final ImageFilterExporter IMAGE_FILTER_EXPORTER = new ImageFilterExporter();

    static final Cache<String, Tuple2<String, List<String>>> promptCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    final Map<String, String[]> imageResizeParameters = Map.of(
            "resize_maxw", new String[]{"500"},
            "resize_maxh", new String[]{"500"},
            "webp_q", new String[]{"85"}
    );


    boolean shouldProcessTags(Contentlet contentlet, Field binaryField) {

        Optional<Field> tagFieldOpt = contentlet.getContentType().fields(TagField.class).stream().findFirst();

        Optional<File> fileToProcess = getFileToProcess(contentlet, binaryField);

        if (tagFieldOpt.isEmpty()) {
            return false;
        }

        boolean alreadyTagged = Try.of(() -> contentlet.getStringProperty(AI_VISION_TAG_FIELD))
                .map(tags -> tags.contains(TAGGED_BY_DOTAI))
                .getOrElse(false);

        //If the contentlet is already tagged by this AI, then we should not process it again
        if (alreadyTagged) {
            return false;
        }

        //If there is no image to process, then we should not process it
        if (fileToProcess.isEmpty() || fileToProcess.get().length() < 100 || !UtilMethods.isImage(
                fileToProcess.get().getName())) {
            return false;
        }

        return !AIUtil.getSecrets(contentlet).isEmpty();
    }

    boolean shouldProcessAltText(Contentlet contentlet, Field binaryField, Field altTextField) {

        if (UtilMethods.isSet(contentlet.getStringProperty(altTextField.variable()))) {
            return false;
        }

        Optional<File> fileToProcess = getFileToProcess(contentlet, binaryField);
        //If there is no image to process, then we should not process it
        if (fileToProcess.isEmpty() || fileToProcess.get().length() < 100 || !UtilMethods.isImage(
                fileToProcess.get().getName())) {
            return false;
        }

        return !AIUtil.getSecrets(contentlet).isEmpty();
    }


    @Override
    public boolean tagImageIfNeeded(Contentlet contentlet) {

        Optional<Field> tagField = contentlet.getContentType().fields().stream()
                .filter(f -> f.fieldVariablesMap().containsKey(AIVisionAPI.AI_VISION_TAG_FIELD_VAR)).findFirst();
        if (tagField.isEmpty()) {
            return false;
        }

        String binaryFieldVariable = tagField.get().fieldVariablesMap().get(AIVisionAPI.AI_VISION_TAG_FIELD_VAR)
                .value();
        Optional<Field> binaryField = contentlet.getContentType().fields().stream()
                .filter(f -> f.variable().equalsIgnoreCase(binaryFieldVariable)).findFirst();
        if (binaryField.isEmpty()) {
            return false;
        }
        return binaryField.filter(field -> tagImageIfNeeded(contentlet, binaryField.get())).isPresent();
    }

    public boolean tagImageIfNeeded(Contentlet contentlet, Field binaryField) {
        if (!shouldProcessTags(contentlet, binaryField)) {
            return false;
        }

        Optional<Tuple2<String, List<String>>> altAndTags = readImageTagsAndDescription(contentlet, binaryField);

        if (altAndTags.isEmpty()) {
            return false;
        }

        saveTags(contentlet, altAndTags.get()._2);
        return true;

    }

    @Override
    public boolean addAltTextIfNeeded(Contentlet contentlet) {

        List<Field> altTextFields = contentlet.getContentType().fields().stream()
                .filter(f -> f.fieldVariablesMap().containsKey(AIVisionAPI.AI_VISION_ALT_FIELD_VAR)).collect(
                        Collectors.toList());

        boolean valToReturn = false;
        for (Field field : altTextFields) {
            String binaryFieldVariable = field.fieldVariablesMap().get(AIVisionAPI.AI_VISION_ALT_FIELD_VAR).value();
            Optional<Field> binaryField = contentlet.getContentType().fields().stream()
                    .filter(f -> f.variable().equalsIgnoreCase(binaryFieldVariable)).findFirst();
            if (binaryField.isEmpty()) {
                continue;
            }
            if (addAltTextIfNeeded(contentlet, binaryField.get(), field)) {
                valToReturn = true;
            }
        }
        return valToReturn;
    }


    public boolean addAltTextIfNeeded(Contentlet contentlet, Field binaryField, Field altTextField) {

        Optional<Tuple2<String, List<String>>> altAndTags = readImageTagsAndDescription(contentlet, binaryField);

        if (altAndTags.isEmpty()) {
            return false;
        }

        Optional<Contentlet> contentToSave = setAltText(contentlet, altTextField, altAndTags.get()._1);
        return contentToSave.isPresent();
    }


    private Optional<String> getSha256(File imageFile) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            try (var in = new DigestInputStream(Files.newInputStream(imageFile.toPath()), md)) {
                while (in.read() != -1) {
                }
            }
            return Optional.of(new String(md.digest()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Tuple2<String, List<String>>> readImageTagsAndDescription(File imageFile) {

        String parsedPrompt = Try.of(() -> {
            final Context ctx = VelocityContextFactory.getMockContext();
            ctx.put("visionModel", getAiVisionModel(Host.SYSTEM_HOST));
            ctx.put("maxTokens", getAiVisionMaxTokens(Host.SYSTEM_HOST));
            ctx.put("base64Image", base64EncodeImage(imageFile));
            return VelocityUtil.eval(getAiVisionPrompt(Host.SYSTEM_HOST), ctx);
        }).getOrNull();
        if (parsedPrompt == null) {
            return Optional.empty();
        }

        return readImageTagsAndDescription(parsedPrompt);
    }


    private Optional<Tuple2<String, List<String>>> readImageTagsAndDescription(String parsedPrompt) {

        String promptHash = Try.of(
                () -> MessageDigest.getInstance("SHA-256").digest(parsedPrompt.getBytes()).toString()).getOrNull();
        if (UtilMethods.isEmpty(promptHash) || UtilMethods.isEmpty(parsedPrompt)) {
            return Optional.empty();
        }
        return Optional.ofNullable(promptCache.get(promptHash, k -> {
            try {
                JSONObject parsedPromptJson = new JSONObject(parsedPrompt);
                Logger.debug(this.getClass(), "parsedPromptJson: " + parsedPromptJson.toString());

                final JSONObject openAIResponse = APILocator.getDotAIAPI()
                        .getCompletionsAPI()
                        .raw(parsedPromptJson, APILocator.systemUser().getUserId());

                Logger.debug(OpenAIImageTaggingContentListener.class.getName(),
                        "OpenAI Response: " + openAIResponse.toString());

                final JSONObject parsedResponse = parseAIResponse(openAIResponse);
                Logger.debug(OpenAIImageTaggingContentListener.class.getName(),
                        "parsedResponse: " + parsedResponse.toString());

                return Tuple.of(parsedResponse.getString(AI_VISION_ALT_TEXT_VARIABLE),
                        parsedResponse.getJSONArray(AI_VISION_TAG_FIELD));
            } catch (Exception e) {
                Logger.warnAndDebug(OpenAIImageTaggingContentListener.class.getCanonicalName(), e.getMessage(), e);
                return null;
            }

        }));


    }

    @Override
    public Optional<Tuple2<String, List<String>>> readImageTagsAndDescription(Contentlet contentlet,
            Field imageOrBinaryField) {




        Optional<File> fileToProcess = getFileToProcess(contentlet, imageOrBinaryField);
        if (fileToProcess.isEmpty()) {
            return Optional.empty();
        }

        final Context ctx = VelocityContextFactory.getMockContext(contentlet, APILocator.systemUser());
        ctx.put("visionModel", getAiVisionModel(contentlet.getHost()));
        ctx.put("maxTokens", getAiVisionMaxTokens(contentlet.getHost()));
        ctx.put("base64Image", base64EncodeImage(fileToProcess.get()));

        final String prompt = Try.of(() -> VelocityUtil.eval(getAiVisionPrompt(contentlet.getHost()), ctx))
                .onFailure(e -> Logger.warnAndDebug(OpenAIVisionAPIImpl.class, e)).getOrNull();
        if (prompt == null) {
            return Optional.empty();
        }

        return readImageTagsAndDescription(prompt);

    }


    Optional<File> getFileToProcess(Contentlet contentlet, Field field) {

        return Try.of(() ->{
            if(field instanceof BinaryField) {
                return contentlet.getBinary(field.variable());
            }

            String id = contentlet.getStringProperty(field.variable());
            Optional<ContentletVersionInfo> cvi = APILocator.getVersionableAPI().getContentletVersionInfo(id, contentlet.getLanguageId());
            if(cvi.isEmpty() && contentlet.getLanguageId()== APILocator.getLanguageAPI().getDefaultLanguage().getId()) {
                cvi = APILocator.getVersionableAPI().getContentletVersionInfo(id, APILocator.getLanguageAPI().getDefaultLanguage().getId());
            }
            if(cvi.isEmpty()) {
                return null;
            }
            Contentlet fileOrDotAsset= APILocator.getContentletAPI().find(cvi.get().getWorkingInode(), APILocator.systemUser(), true);
            return fileOrDotAsset.isFileAsset() ? fileOrDotAsset.getBinary(FileAssetAPI.BINARY_FIELD) : fileOrDotAsset.getBinary(DotAssetContentType.ASSET_FIELD_VAR);

        }




        ).toJavaOptional();
    }


    JSONObject parseAIResponse(JSONObject response) {

        String aiJson = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

        // gets at the first json object
        while (!aiJson.isEmpty() && !aiJson.startsWith("{")) {
            aiJson = aiJson.substring(1);
        }
        while (!aiJson.isEmpty() && !aiJson.endsWith("}")) {
            aiJson = aiJson.substring(0, aiJson.length() - 1);
        }

        return new JSONObject(aiJson);

    }


    String base64EncodeImage(File imageFile) {
        File transformedFile = Try.of(
                () -> IMAGE_FILTER_EXPORTER.exportContent(imageFile, new HashMap<>(this.imageResizeParameters))
                        .getDataFile()).getOrElseThrow(DotRuntimeException::new);

        Logger.debug(OpenAIImageTaggingContentListener.class.getCanonicalName(),
                "Transformed file: " + transformedFile.getAbsolutePath());
        try {
            return java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(transformedFile.toPath()));
        } catch (Exception e) {
            Logger.error(this, "Error encoding image", e);
            throw new DotRuntimeException(e);
        }
    }


    String getAiVisionModel(String hostId) {

        if (UtilMethods.isSet(() -> AIUtil.getSecrets(hostId).get(AI_VISION_MODEL).getString())) {
            return AIUtil.getSecrets(hostId).get(AI_VISION_MODEL).getString();
        }
        return "gpt-4o";
    }

    String getAiVisionMaxTokens(String hostId) {
        if (UtilMethods.isSet(() -> AIUtil.getSecrets(hostId).get(AI_VISION_MAX_TOKENS).getString())) {
            return AIUtil.getSecrets(hostId).get(AI_VISION_MAX_TOKENS).getString();
        }
        return "500";
    }

    String getAiVisionPrompt(String hostId) {
        if (UtilMethods.isSet(() -> AIUtil.getSecrets(hostId).get(AI_VISION_PROMPT).getString())) {
            return AIUtil.getSecrets(hostId).get(AI_VISION_PROMPT).getString();
        }


        return Try.of(()->{
            try (InputStream in = OpenAIVisionAPIImpl.class.getResourceAsStream("/default-vision-prompt.json")) {
                return new String(in.readAllBytes());
            }
        }).getOrNull();
    }







    private void saveTags(Contentlet contentlet, List<String> tags) {
        Optional<Field> tagFieldOpt = contentlet.getContentType().fields(TagField.class).stream().findFirst();
        if (tagFieldOpt.isEmpty()) {
            return;
        }
        Try.run(() -> APILocator.getTagAPI()
                .addContentletTagInode(TAGGED_BY_DOTAI, contentlet.getInode(), contentlet.getHost(),
                        tagFieldOpt.get().variable())).getOrElseThrow(
                DotRuntimeException::new);

        for (final String tag : tags) {
            Try.run(() -> APILocator.getTagAPI().addContentletTagInode(tag, contentlet.getInode(), contentlet.getHost(),
                    tagFieldOpt.get().variable())).getOrElseThrow(
                    DotRuntimeException::new);
        }
    }

    private Optional<Contentlet> setAltText(Contentlet contentlet, Field altTextField, String altText) {
        if (UtilMethods.isEmpty(altText)) {
            return Optional.empty();
        }

        if (UtilMethods.isSet(() -> contentlet.getStringProperty(altTextField.variable()))) {
            return Optional.empty();
        }

        contentlet.setStringProperty(altTextField.variable(), altText);
        return Optional.of(contentlet);


    }


}
