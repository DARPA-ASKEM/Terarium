import sys
import json
import traceback
from taskrunner import TaskRunnerInterface
import sympy
from mira.sources.amr import model_from_json

def cleanup():
    pass

def main():
    exitCode = 0

    try:
        taskrunner = TaskRunnerInterface(description="Generate LaTeX")
        taskrunner.on_cancellation(cleanup)

        data = taskrunner.read_input_str_with_timeout()
        amr = json.loads(data)
        model = model_from_json(amr)

        # =========================================
        # Generate LaTeX code string from MMT model
        # =========================================

        odeterms = {var: 0 for var in model.get_concepts_name_map().keys()}

        for template in model.templates:
            if hasattr(template, "subject"):
                var = template.subject.name
                odeterms[var] -= template.rate_law.args[0]

            if hasattr(template, "outcome"):
                var = template.outcome.name
                odeterms[var] += template.rate_law.args[0]

        # Time
        if model.time and model.time.name:
            time = model.time.name
        else:
            time = "t"
        t = sympy.Symbol(time)

        # Construct Sympy equations
        odesys = []
        for var, terms in odeterms.items():
            lhs = sympy.diff(sympy.Function(var)(t), t)

            # Write (time-dependent) symbols with "(t)"
            rhs = terms
            if hasattr(terms, 'atoms'):
                for atom in terms.atoms(sympy.Symbol):
                    if str(atom) in odeterms.keys():
                        rhs = rhs.subs(atom, sympy.Function(str(atom))(t))

            odesys.append(sympy.latex(sympy.Eq(lhs, rhs)))

        # Observables
        if len(model.observables) > 0:

            # Write (time-dependent) symbols with "(t)"
            obs_eqs = []
            for obs in model.observables.values():
                lhs = sympy.Function(obs.name)(t)
                terms = obs.expression.args[0]
                rhs = terms
                if hasattr(terms, 'atoms'):
                    for atom in terms.atoms(sympy.Symbol):
                        if str(atom) in odeterms.keys():
                            rhs = rhs.subs(atom, sympy.Function(str(atom))(t))
                obs_eqs.append(sympy.latex(sympy.Eq(lhs, rhs)))

            # Add observables
            odesys += obs_eqs

        # Reformat:
        odesys = "\\begin{align} \n    " + " \\\\ \n    ".join([eq for eq in odesys]) + "\n\\end{align}"
        # =========================================

        taskrunner.write_output_dict_with_timeout({"response": odesys})
        print("Generate LaTeX succeeded")

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
