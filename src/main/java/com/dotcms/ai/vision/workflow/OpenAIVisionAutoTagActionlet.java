package com.dotcms.ai.vision.workflow;

import com.dotcms.ai.app.AppConfig;
import com.dotcms.ai.app.ConfigService;
import com.dotcms.ai.vision.api.AIVisionAPI;
import com.dotcms.ai.workflow.OpenAIParams;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.workflows.actionlet.WorkFlowActionlet;
import com.dotmarketing.portlets.workflows.model.MultiKeyValue;
import com.dotmarketing.portlets.workflows.model.MultiSelectionWorkflowActionletParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowActionClassParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowActionFailureException;
import com.dotmarketing.portlets.workflows.model.WorkflowActionletParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowProcessor;
import io.vavr.control.Try;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class OpenAIVisionAutoTagActionlet extends WorkFlowActionlet {

    private static final long serialVersionUID = 1L;
    AIVisionAPI aiVisionAPI = AIVisionAPI.instance.get();

    @Override
    public List<WorkflowActionletParameter> getParameters() {
        return List.of(
                new WorkflowActionletParameter("contentTypes",
                        "A comma separated list of content types with images to Auto-Tag", "images", true)

        );
    }

    @Override
    public String getName() {
        return "Open AI Auto-Tag Images";
    }

    @Override
    public String getHowTo() {
        return "This will attempt to Auto-tag and add alt tag descriptions to your images. ";
    }


    @Override
    public void executeAction(WorkflowProcessor processor, Map<String, WorkflowActionClassParameter> params) throws WorkflowActionFailureException {



        String[] contentTypes = params.get("contentTypes").getValue().toLowerCase().split(",");
        String myType = processor.getContentlet().getContentType().variable().toLowerCase();
        if(processor.getAction().hasSaveActionlet() || processor.getAction().hasPublishActionlet()){
            if (contentTypes.length > 0 && Arrays.asList(contentTypes).contains(myType)) {
                aiVisionAPI.tagImageIfNeeded(processor.getContentlet());

                aiVisionAPI.addAltTextIfNeeded(processor.getContentlet());

            }



        }





    }


}
