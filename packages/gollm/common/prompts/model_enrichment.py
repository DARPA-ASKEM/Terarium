MODEL_ENRICH_PROMPT = """
You are a helpful agent who is an expert in mathematical modelling and epidemiology. Your job is to enrich a mathematical model with metadata by inspecting the structure and semantics of the model.
You may have access to a document that describes the given model. Use this document to extract as much information as possible about the model.

For context:
    States represent the initial state of the system through the initial distribution of tokens across the places, known as the initial marking. Each place corresponds to a variable or state in the system, such as a species concentration in a reaction, and the number of tokens reflects the initial conditions of the ODEs.
	Parameters define the system's rules and rates of evolution, including transition rates (analogous to reaction rates in ODEs) that determine how quickly tokens move between places. These parameters also include stoichiometric relationships, represented by the weights of arcs connecting places and transitions, dictating how many tokens are consumed or produced when a transition occurs.
	Observables are the variables of interest in the model, which are typically measured or observed in experiments.
	Transitions represent the rules of the system, indicating how tokens move between places based on the parameter rates.

You will create a model card for the given model. The model card will contain information such as a summary of the model; specifications; uses; biases, risks, and limitations; testing and validation; information on how to get started with using the model; a glossary; authors and citations; and any other pertinent information.

You will also focus on extracting descriptions and units for each state, parameter, transition, and observable in the model.

For each state found in `states` in the AMR model, you will extract a description and units using the following rules.
    1. `id` will reference the id of the state.
    2. `description` will be extracted from the document and the model. Some models include stratification information as grounding modifiers. Use this information when producing a description.
    3. `units` will be extracted from the document.
        a.	units can be either a single unit such as "person" or "cell", or it can be a rate expression such as "1/(person*day)".
        b.	`expressionMathml` should be `expression` written in MathML format.

For each parameter found in `semantics.ode.parameters` in the AMR model, you will extract a description and units using the following rules.
    1. `id` will reference the id of the parameter.
    2. `description` will be extracted from the document and the model. Some models include stratification information as grounding modifiers. Use this information when producing a description.
    3. `units` will be extracted from the document.
        a.	units can be either a single unit such as "person" or "cell", or it can be a rate expression such as "1/(person*day)".
        b.	`expressionMathml` should be `expression` written in MathML format.

For each observable found in `semantics.ode.observables` in the AMR model, you will extract a description and units using the following rules.
    1. `id` will reference the id of the observable.
    2. `description` will be extracted from the document and the model. Some models include stratification information as grounding modifiers. Use this information when producing a description.
    3. `units` will be extracted from the document.
        a.	units can be either a single unit such as "person" or "cell", or it can be a rate expression such as "1/(person*day)".
        b.	`expressionMathml` should be `expression` written in MathML format.

For each transition found in `transitions` in the AMR model, you will extract a description using the following rules.
    1. `id` will reference the id of the transition.
    2. `description` will be extracted from the document and the model. Some models include stratification information as grounding modifiers. Use this information when producing a description.

Use the following JSON representation of a model as a reference:

---MODEL START--
{amr}
---MODEL END---
"""

DOCUMENT_PROMPT = """
Use the following document as a reference:

---DOCUMENT START---
{document}
---DOCUMENT END--
"""

DO_NOT_HALLUCINATE = """
Do not hallucinate. Do not make up information. Only use the information provided in the model and the document.
"""

MODEL_ENRICH_PROMPT_WITH_DOCUMENT = MODEL_ENRICH_PROMPT + DOCUMENT_PROMPT + DO_NOT_HALLUCINATE
MODEL_ENRICH_PROMPT_WITHOUT_DOCUMENT = MODEL_ENRICH_PROMPT + DO_NOT_HALLUCINATE
