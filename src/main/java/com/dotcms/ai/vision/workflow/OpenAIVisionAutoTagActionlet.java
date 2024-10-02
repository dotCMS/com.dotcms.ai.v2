package com.dotcms.ai.vision.workflow;

import com.dotcms.ai.app.AppConfig;
import com.dotcms.ai.app.ConfigService;
import com.dotcms.ai.workflow.OpenAIParams;
import com.dotmarketing.portlets.workflows.actionlet.WorkFlowActionlet;
import com.dotmarketing.portlets.workflows.model.MultiKeyValue;
import com.dotmarketing.portlets.workflows.model.MultiSelectionWorkflowActionletParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowActionClassParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowActionFailureException;
import com.dotmarketing.portlets.workflows.model.WorkflowActionletParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowProcessor;
import io.vavr.control.Try;
import java.util.List;
import java.util.Map;

public class OpenAIVisionAutoTagActionlet extends WorkFlowActionlet {

    private static final long serialVersionUID = 1L;

    @Override
    public List<WorkflowActionletParameter> getParameters() {

        WorkflowActionletParameter overwriteParameter = new MultiSelectionWorkflowActionletParameter(OpenAIParams.OVERWRITE_FIELDS.key,
                "Overwrite  tags ", Boolean.toString(true), true,
                () -> List.of(
                        new MultiKeyValue(Boolean.toString(false), Boolean.toString(false)),
                        new MultiKeyValue(Boolean.toString(true), Boolean.toString(true)))
        );

        WorkflowActionletParameter limitTagsToHost = new MultiSelectionWorkflowActionletParameter(
                OpenAIParams.LIMIT_TAGS_TO_HOST.key,
                "Limit the keywords to pre-existing tags", "Limit", false,
                () -> List.of(
                        new MultiKeyValue(Boolean.toString(false), Boolean.toString(false)),
                        new MultiKeyValue(Boolean.toString(true), Boolean.toString(true))
                )
        );

        final AppConfig appConfig = ConfigService.INSTANCE.config();
        return List.of(
                overwriteParameter,
                limitTagsToHost,
                new WorkflowActionletParameter(
                        OpenAIParams.MODEL.key,
                        "The AI model to use, defaults to " + appConfig.getModel().getCurrentModel(),
                        appConfig.getModel().getCurrentModel(),
                        false),
                new WorkflowActionletParameter(
                        OpenAIParams.TEMPERATURE.key,
                        "The AI temperature for the response.  Between .1 and 2.0.",
                        ".1",
                        false)
        );
    }

    @Override
    public String getName() {
        return "Open AI Auto-Tag Images";
    }

    @Override
    public String getHowTo() {
        return "This will attempt to Auto-tag your images item based on their content ";
    }


    @Override
    public void executeAction(WorkflowProcessor processor, Map<String, WorkflowActionClassParameter> params) throws WorkflowActionFailureException {

        final String overwrite = params.get(OpenAIParams.OVERWRITE_FIELDS.key).getValue();
        final String limitTagsToHost = params.get(OpenAIParams.LIMIT_TAGS_TO_HOST.key).getValue();
        final String model = params.get(OpenAIParams.MODEL.key).getValue();
        final String temperature = params.get(OpenAIParams.TEMPERATURE.key).getValue();




    }


}
