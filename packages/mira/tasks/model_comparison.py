import os
import sys
import itertools
import traceback
import time
import base64
import json
from pandas import DataFrame
from pydantic import BaseModel
from typing import List
from taskrunner import TaskRunnerInterface
from mira.metamodel import *
from mira.sources.amr.petrinet import template_model_from_amr_json
from mira.metamodel.utils import is_ontological_child

def png_to_base64_and_delete(file_path: str) -> str:
    """Read a PNG file, return its Base64 string, and delete the original file.

    Parameters
    ----------
    file_path : str
        Path to the PNG file.

    Returns
    -------
    str
        Base64-encoded string of the PNG file.
    """
    # Read the file and encode it in Base64
    with open(file_path, "rb") as image_file:
        base64_string = base64.b64encode(image_file.read()).decode("utf-8")

    # Delete the original file
    os.remove(file_path)

    return base64_string

class CompareModelConcepts(BaseModel):
    amrs: List[str]  # expects AMRs to be a stringifies JSON object

def cleanup():
    pass

def main():
    exitCode = 0

    try:
        start = time.time()
        taskrunner = TaskRunnerInterface(description="Compare Model Concepts")
        taskrunner.on_cancellation(cleanup)

        input_dict = taskrunner.read_input_dict_with_timeout()
        amrs = CompareModelConcepts(**input_dict).amrs
        taskrunner.log(f"Received {len(amrs)} AMRs from input")

        ### Create MMT (MIRA Model Template) from input
        models = {}
        tags = []

        for i, amr_string in enumerate(amrs):
            amr = json.loads(amr_string)
            tags.append(f"{amr['header']['name']}")
            models[i] = template_model_from_amr_json(amr)

        end = time.time()
        taskrunner.log(f"Created {len(models)} MMTs from input — completed — {(end - start) * 1000} ms")

        concept_context_comparison = None
        tabular_comparison = None
        concept_graph_comparison = None

        ### Concept context comparison
        try:
            taskrunner.log("Concept context comparison — started")
            comp = TemplateModelComparison(models.values(), is_ontological_child, tags, run_on_init=False)
            concept_context_comparison = comp.compare_context().to_csv(encoding='utf-8')
            previous_end = end
            end = time.time()
            taskrunner.log(f"Concept context comparison — completed — {(end - previous_end) * 1000} ms")
        
        except Exception as e:
            sys.stderr.write(f"Error in concept context comparison: {str(e)}\n")
            sys.stderr.write(traceback.format_exc())
            sys.stderr.flush()

        ### Tabular concept comparison
        try:
            taskrunner.log("Tabular concept comparison — started")
            tabular_comparison = {}
            for i, j in itertools.combinations(models.keys(), 2):
                table = get_concept_comparison_table(models[i], models[j], name_only=True, refinement_func=is_ontological_child)
                taskrunner.log(f"Tabular concept comparison — between {tags[i]} and {tags[j]}")
                tabular_comparison[f"{tags[i]} — {tags[j]}"] = table.to_csv(encoding='utf-8')
            previous_end = end
            end = time.time()
            taskrunner.log("Tabular concept comparison — completed — {(end - previous_end) * 1000} ms")
        except Exception as e:
            sys.stderr.write(f"Error in tabular concept comparison: {str(e)}\n")
            sys.stderr.write(traceback.format_exc())
            sys.stderr.flush()

        ### Concept graph comparison
        try:
            taskrunner.log("Concept graph comparison — started")
            concept_graph_comparison = {}
            for i, j in itertools.combinations(models.keys(), 2):
                tmd = TemplateModelDelta(
                    template_model1=models[i],
                    template_model2=models[j],
                    refinement_function=is_ontological_child,
                    concepts_only=True,
                )
                taskrunner.log(f"Concept graph comparison — between {tags[i]} and {tags[j]}")
                tmd.draw_jupyter(name=f"{tags[i]}-{tags[j]}.png", args="-Grankdir=LR")
                image_base64 = png_to_base64_and_delete(f"{tags[i]}-{tags[j]}.png")
                concept_graph_comparison[f"{tags[i]} — {tags[j]}"] = image_base64
            previous_end = end
            end = time.time()
            taskrunner.log("Concept graph comparison — completed — {(end - previous_end) * 1000} ms")
        except Exception as e:
            sys.stderr.write(f"Error in concept graph comparison: {str(e)}\n")
            sys.stderr.write(traceback.format_exc())
            sys.stderr.flush()

        ### Send the results back
        result = {
            "concept_context_comparison": concept_context_comparison,
            "tabular_comparison": tabular_comparison,
            "concept_graph_comparison": concept_graph_comparison
        }
        taskrunner.write_output_dict_with_timeout({"response": result})

        # Log the time taken to compare model concepts
        end = time.time()
        taskrunner.log(f"Compare Model Concepts — completed {(end - start) * 1000} ms")

        print("Compare Model Concepts succeeded")
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
