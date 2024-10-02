package com.dotcms.ai.vision.workflow;

import com.dotcms.ai.vision.api.AIVisionAPI;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.portlets.workflows.actionlet.PublishContentActionlet;
import com.dotmarketing.portlets.workflows.actionlet.SaveContentActionlet;
import com.dotmarketing.portlets.workflows.actionlet.WorkFlowActionlet;
import com.dotmarketing.portlets.workflows.model.WorkflowActionClass;
import com.dotmarketing.portlets.workflows.model.WorkflowActionClassParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowActionFailureException;
import com.dotmarketing.portlets.workflows.model.WorkflowActionletParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowProcessor;
import io.vavr.control.Try;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OpenAIVisionAutoTagActionlet extends WorkFlowActionlet {

    private static final long serialVersionUID = 1L;
    AIVisionAPI aiVisionAPI = AIVisionAPI.instance.get();

    @Override
    public List<WorkflowActionletParameter> getParameters() {
        return List.of(
                new WorkflowActionletParameter("contentTypes",
                        "A comma separated list of content types with images to Auto-Tag",
                        AIVisionAPI.AI_VISION_AUTOTAG_CONTENTTYPES_DEFAULT, true)

        );
    }

    @Override
    public String getName() {
        return "Auto-Tag Images- Open AI";
    }

    @Override
    public String getHowTo() {
        return "This will attempt to Auto-tag and add alt tag descriptions to your images.  You will need to make sure this runs before the save/publish Content actionlet.";
    }


    @Override
    public void executeAction(WorkflowProcessor processor, Map<String, WorkflowActionClassParameter> params)
            throws WorkflowActionFailureException {

        String[] contentTypes = params.get("contentTypes").getValue().toLowerCase().split("[\\s,]+");
        String myType = processor.getContentlet().getContentType().variable().toLowerCase();

        Optional<WorkflowActionClass> clazz = Try.of(() ->
                        APILocator.getWorkflowAPI().findActionClasses(processor.getAction())
                                .stream()
                                .filter(ac -> ac.getActionlet() instanceof SaveContentActionlet
                                        || ac.getActionlet() instanceof PublishContentActionlet)
                                .findFirst())
                .getOrElse(Optional.empty());

        if (clazz.isPresent() && contentTypes.length > 0 && Arrays.asList(contentTypes).contains(myType)) {
            aiVisionAPI.tagImageIfNeeded(processor.getContentlet());
            aiVisionAPI.addAltTextIfNeeded(processor.getContentlet());

        }


    }


}
