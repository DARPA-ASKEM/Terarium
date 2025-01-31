import sys
import traceback

from chains import general_query_chain
from llms.openai.OpenAiTools import OpenAiTools
from taskrunner import TaskRunnerInterface


def cleanup():
    pass


def main():
    exitCode = 0
    try:
        taskrunner = TaskRunnerInterface(description="Generate Response CLI")
        taskrunner.on_cancellation(cleanup)

        input_str = taskrunner.read_input_str_with_timeout()

        taskrunner.log("Generating a response from input")

        taskrunner.log("Sending request to OpenAI API")
        llm = OpenAiTools()
        response = general_query_chain(llm, instruction=input_str)
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
