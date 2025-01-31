import sys
import json
import traceback
import sympy

from taskrunner import TaskRunnerInterface
from mira.sources.sympy_ode import template_model_from_sympy_odes
from mira.modeling.amr.petrinet import template_model_to_petrinet_json

from mira.metamodel.template_model import Initial, Parameter, Observable
from mira.metamodel.utils import safe_parse_expr
from mira.metamodel.units import Unit

def cleanup():
    pass

def main():
    exitCode = 0

    try:
        taskrunner = TaskRunnerInterface(description="Sympy to AMR")
        taskrunner.on_cancellation(cleanup)

        sympy_code = taskrunner.read_input_str_with_timeout()
        taskrunner.log("== input code")
        taskrunner.log(sympy_code)
        taskrunner.log("")

        globals = {}
        exec(sympy_code, globals) # output should be in placed into "equation_output"
        taskrunner.log("== equations")
        taskrunner.log(globals["equation_output"])
        taskrunner.log("")

        # SymPy to MMT
        mmt = template_model_from_sympy_odes(globals["equation_output"])

        # =======================
        # Apply missing defaults
        # =======================
        
        # Assume time unit = 'day'
        mmt.time.units = Unit(expression = sympy.Symbol('day'))
    
        # Assign default display names and units to all concepts in every template
        for t in mmt.templates:
            for i in t.get_concepts():
                i.display_name = f'{i.name}(t)'
                i.units = Unit(expression = sympy.Integer(1))

        # Ditto for all parameters
        for p, param in mmt.parameters.items():
            param.display_name = p
            param.units = Unit(expression = sympy.Integer(1))
    
        # Ditto for observables
        for o, obs in mmt.observables.items():
            obs.display_name = f'{obs.name}(t)'
            obs.units = Unit(expression = sympy.Integer(1))
    
        # Ensure model templates have unique names/ids and display names
        if len(mmt.templates) > 0:
            if len({t.name for t in mmt.templates}) < len(mmt.templates):
                for i, t in enumerate(mmt.templates):
                    t.name = f't{i}'
                    t.display_name = f'{t.type}'
                    if 'Production' in t.type:
                        t.display_name += f' of {t.outcome.display_name}'
                    elif 'Degradation' in t.type:
                        t.display_name += f' of {t.subject.display_name}'
                    elif 'Conversion' in t.type:
                        t.display_name += f' from {t.subject.display_name} to {t.outcome.display_name}'
    
                    if getattr(t, 'controller', False):
                        t.display_name += f' controlled by {t.controller.display_name}'
                    elif getattr(t, 'controllers', False):
                        t.display_name += f' controlled by {" and ".join([c.display_name for c in t.controllers])}'
                
        # Set default values for the model parameters
        for param in mmt.parameters.values():
            param.value = 0.0
    
        # Ensure every state variable has an initial condition parameter
        mmt.initials = mmt.initials | {c: Initial(concept = concept, expression = safe_parse_expr(f'0.0')) for c, concept in mmt.get_concepts_name_map().items()}
        # mmt.initials = mmt.initials | {c: Initial(concept = concept, expression = safe_parse_expr(f'{c}0')) for c, concept in mmt.get_concepts_name_map().items()}
    
        # Set default values for the initial condition parameters
        # mmt.parameters = mmt.parameters | {f'{c}0': Parameter(name = f'{c}0', display_name = f'{c}0', description = f'Initial value of state variable "{c}"', value = 0.0) for c in mmt.get_concepts_name_map().keys()}

        # =======================

        # MMT to AMR
        amr_json = template_model_to_petrinet_json(mmt)

        # Gather results
        response = {}
        response["amr"] = amr_json
        response["sympyCode"] = sympy_code
        response["sympyExprs"] = list(map(lambda x: str(x), globals["equation_output"]))

        taskrunner.log(f"Sympy to AMR conversion succeeded")
        taskrunner.write_output_dict_with_timeout({"response": response })
        
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
