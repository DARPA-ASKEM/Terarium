import sys

from chains import cleanup_equations_chain
from entities import EquationsCleanup
from llms.openai.OpenAiTools import OpenAiTools

from taskrunner import TaskRunnerInterface


def cleanup():
    pass


def main():
    exitCode = 0
    try:
        taskrunner = TaskRunnerInterface(description="Reformat equations based on style guide")
        taskrunner.on_cancellation(cleanup)

        input_dict = taskrunner.read_input_dict_with_timeout()

        taskrunner.log("Creating EquationsCleanup from input")
        input_model = EquationsCleanup(**input_dict)

        taskrunner.log("Sending request to OpenAI API")
        llm = OpenAiTools()
        response = cleanup_equations_chain(llm, equations=input_model.equations)
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
