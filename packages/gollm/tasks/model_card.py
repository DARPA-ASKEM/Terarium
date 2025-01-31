import sys
import traceback

from chains import model_card_chain
from entities import ModelCardModel
from llms.openai.OpenAiTools import OpenAiTools
from taskrunner import TaskRunnerInterface


def cleanup():
    pass


def main():
    exitCode = 0
    try:
        taskrunner = TaskRunnerInterface(description="Model Card CLI")
        taskrunner.on_cancellation(cleanup)

        input_dict = taskrunner.read_input_dict_with_timeout()

        taskrunner.log("Creating ModelCardModel from input")
        input_model = ModelCardModel(**input_dict)

        taskrunner.log("Sending request to OpenAI API")
        llm = OpenAiTools()
        response = model_card_chain(llm, amr=input_model.amr, document=input_model.document)
        taskrunner.log("Received response from OpenAI API")

        taskrunner.write_output_dict_with_timeout({"response": response})

    except Exception as e:
        sys.stderr.write(traceback.format_exc())
        sys.stderr.flush()
        exitCode = 1

    taskrunner.log("Shutting down")
    taskrunner.shutdown()
    sys.exit(exitCode)


if __name__ == "__main__":
    main()
