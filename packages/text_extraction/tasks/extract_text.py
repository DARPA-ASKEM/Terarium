import fitz
import os
import subprocess
import sys
import traceback

from taskrunner import TaskRunnerInterface


def cleanup():
    pass


def get_filename(id: str, ext : str):
    return f"/tmp/{id}.{ext}"


def create_temp_file(name, contents):
    with open(name, 'wb') as f:
        f.write(contents)


def delete_temp_file(name):
    try:
        os.remove(name)
    except:
        pass


def read_file_to_string(filename):
    with open(filename, 'r', encoding='utf-8') as file:
        content = file.read()
    return content


def extract_text_from_pdf(filename):
    reader = fitz.open(filename) # open a document
    text = []
    for page in reader:
        text.append(page.get_text())
    return text


def extract_text_already_in_pdf(taskrunner, ifilename):
    ofilename = get_filename("output_" + taskrunner.id, "txt")

    # Define the command and arguments
    command = ["pdftotext", ifilename, ofilename]

    # Run the command
    result = subprocess.run(command, capture_output=True, text=True)

    # Print the output
    if result.returncode != 0:
        taskrunner.log("Error running pdftotext")
        taskrunner.log("stderr:" + result.stderr)
        taskrunner.log("Return code:" + str(result.returncode))
        raise Exception("Error running pdftotext")

    if result.stdout != "":
        taskrunner.log("pdftotext stdout:")
        taskrunner.log(result.stdout)

    if result.stderr != "":
        taskrunner.log("pdftotext stderr:")
        taskrunner.log(result.stderr)

    taskrunner.log("Extracting text")
    return read_file_to_string(ofilename).split('\f')


def extract_text_with_ocr(taskrunner, ifilename):

    ofilename = get_filename("output_" + taskrunner.id, ".pdf")

    # Define the command and arguments
    command = ["ocrmypdf", "--force-ocr", "--skip-text", ifilename, ofilename]

    # Run the command
    result = subprocess.run(command, capture_output=True, text=True)

    # Print the output
    if result.returncode != 0:
        taskrunner.log("Error running ocrmypdf")
        taskrunner.log("stderr:" + result.stderr)
        taskrunner.log("Return code:" + str(result.returncode))
        raise Exception("Error running ocrmypdf")

    if result.stdout != "":
        taskrunner.log("ocrmypdf stdout:")
        taskrunner.log(result.stdout)

    if result.stderr != "":
        taskrunner.log("ocrmypdf stderr:")
        taskrunner.log(result.stderr)

    taskrunner.log("Extracting text")
    return extract_text_from_pdf(ofilename)

def main():
    exitCode = 0
    try:
        taskrunner = TaskRunnerInterface(description="Embedding CLI")
        taskrunner.on_cancellation(cleanup)

        bs = taskrunner.read_input_bytes_with_timeout()

        # create the temp input file
        ifilename = get_filename("input_" + taskrunner.id, "pdf")
        create_temp_file(ifilename, bs)

        pdfText = extract_text_already_in_pdf(taskrunner, ifilename)
        ocrText = extract_text_already_in_pdf(taskrunner, ifilename)

        result = []
        for index, text in enumerate(pdfText):
            if len(text) > 0:
                # if text is already present in the pdf, use that
                result.append(text)
            else:
                # otherwise use the ocr text
                result.append(ocrText[index])

        taskrunner.write_output_dict_with_timeout({"response": result})

    except Exception as e:
        sys.stderr.write(f"Error: {str(e)}\n")
        sys.stderr.write(traceback.format_exc())
        sys.stderr.flush()
        exitCode = 1

    taskrunner.log("Shutting down")
    taskrunner.shutdown()
    sys.exit(exitCode)


if __name__ == "__main__":
    main()
