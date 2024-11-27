import sys
from entities import EmbeddingModel
from gollm_openai.tool_utils import embedding_chain

from taskrunner import TaskRunnerInterface


def cleanup():
    pass


def main():
    exitCode = 0
    try:
        taskrunner = TaskRunnerInterface(description="Embedding CLI")
        taskrunner.on_cancellation(cleanup)

        input_dict = taskrunner.read_input_dict_with_timeout()

        taskrunner.log("Creating Embedding from input")
        input_model = EmbeddingModel(**input_dict)

        responses = []

        for text in input_model.text:
            taskrunner.log("Sending request to OpenAI API")
            response = embedding_chain(text=text)
            taskrunner.log("Received response from OpenAI API")
            responses.append(response)

        taskrunner.write_output_dict_with_timeout({"response": responses})

    except Exception as e:
        sys.stderr.write(f"Error: {str(e)}\n")
        sys.stderr.flush()
        exitCode = 1

    taskrunner.log("Shutting down")
    taskrunner.shutdown()
    sys.exit(exitCode)


if __name__ == "__main__":
    main()
