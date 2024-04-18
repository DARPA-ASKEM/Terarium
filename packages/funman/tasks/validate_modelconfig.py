import sys
import os
import json
from taskrunner import TaskRunnerInterface

# Funman imports
from funman import Funman
from funman.config import FUNMANConfig
from funman.model.model import _wrap_with_internal_model
from funman.scenario.scenario import AnalysisScenario
from funman.model.petrinet import PetrinetModel
from funman.representation.parameter_space import ParameterSpace
from funman.server.query import (
    FunmanProgress,
    FunmanResults,
    FunmanWorkUnit,
)
import pydantic
from pydantic import TypeAdapter
from funman.model.generated_models.petrinet import Model as GeneratedPetrinet


# FIXME
dummy_id = "xyz"

adapter = TypeAdapter(GeneratedPetrinet)
current_results = None

def cleanup():
    print("Task cleanup")

def run_validate(model: PetrinetModel, request):
    current_results = FunmanResults(
        id=dummy_id,
        model=model,
        request=request,
        parameter_space=ParameterSpace(),
    )

    # Update callback
    def update_current_results(scenario: AnalysisScenario, results: ParameterSpace) -> FunmanProgress:
        progress = current_results.update_parameter_space(scenario, results)
        print("update hook", progress)


    # Invoke solver
    work = FunmanWorkUnit(id=dummy_id, model=model, request=request)
    f = Funman()
    scenario = work.to_scenario()
    config = (
        FUNMANConfig()
        if work.request.config is None
        else work.request.config
    )
    result = f.solve(
        scenario,
        config=config,
        # haltEvent=self._halt_event,
        resultsCallback=lambda results: update_current_results(scenario, results),
    )
    print("Done solver portion")
    current_results.finalize_result(scenario, result)
    print(current_results.model_dump_json(by_alias=False))

    return current_results.model_dump_json(by_alias=False)


def taskrunner_wrapper():
    print("Taskrunner wrapper")
    try:
        taskrunner = TaskRunnerInterface(description="AMR to MMT")
        taskrunner.on_cancellation(cleanup)

        # Input wrangling
        data = taskrunner.read_input_with_timeout()
        data_json = json.loads(data)

        # Create work unit
        model = adapter.validate_python(test["model"])
        model = _wrap_with_internal_model(model)
        request = data_json["request"]
        result = run_validate(model, request)
        taskrunner.write_output_with_timeout({"response": result})

    except Exception as e:
        sys.stderr.write(f"Error: {str(e)}\n")
        sys.stderr.flush()
        exitCode = 1


def debug_wrapper():
		f = open("funman-apr-12.json", "r")
		test = json.loads(f.read())
		model = adapter.validate_python(test["model"])
		model = _wrap_with_internal_model(model)
		request = test["request"]
		run_validate(model, request)


def main():
    if os.getenv("TASKRUNNER_DEBUG") == "1":
				debug_wrapper()
    else:
				taskrunner_wrapper()


if __name__ == "__main__":
    main()
