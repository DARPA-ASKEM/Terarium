import json
import sys
from gollm.entities import ConfigureModelDataset
from gollm.openai.tool_utils import model_config_from_dataset

from taskrunner import TaskRunnerInterface


def cleanup():
    pass


def main():
    exitCode = 0
    try:
        taskrunner = TaskRunnerInterface(description="Configure Model from dataset CLI")
        taskrunner.on_cancellation(cleanup)

        input_dict = taskrunner.read_input_dict_with_timeout()

        taskrunner.log("Creating ConfigureModel from input")
        input_model = ConfigureModelDataset(**input_dict)
        amr = json.dumps(input_model.amr, separators=(",", ":"))

        taskrunner.log("Sending request to OpenAI API")
        response = model_config_from_dataset(dataset=input_model.dataset, amr=amr, matrix=input_model.matrix)
        taskrunner.log("Received response from OpenAI API")

        taskrunner.write_output_dict_with_timeout({"response": response})

    except Exception as e:
        sys.stderr.write(f"Error: {str(e)}\n")
        sys.stderr.flush()
        exitCode = 1

    taskrunner.log("Shutting down")
    taskrunner.shutdown()
    sys.exit(exitCode)


if __name__ == "__main__":
    main()
