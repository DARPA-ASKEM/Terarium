import sys
import requests
import io

from taskrunner import TaskRunnerInterface


url = "http://localhost:8000/process_pdf"


def cleanup():
    pass


def main():
    exitCode = 0
    try:
        taskrunner = TaskRunnerInterface(description="Embedding CLI")
        taskrunner.on_cancellation(cleanup)

        bs = taskrunner.read_input_bytes_with_timeout()

        files = {"file": ("uploaded_file.pdf", io.BytesIO(bs), "application/pdf")}
        response = requests.post(url, files=files)

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
