CONFIGURE_FROM_DOCUMENT_PROMPT = """
You are a helpful agent designed to find multiple model configurations for a given AMR model of various conditions described in a research paper and the initials and parameters that make up those conditions.
For context, initials represent the initial state of the system through the initial distribution of tokens across the places, known as the initial marking. Each place corresponds to a variable or state in the system, such as a species concentration in a reaction, and the number of tokens reflects the initial conditions of the ODEs. Parameters define the system's rules and rates of evolution, including transition rates (analogous to reaction rates in ODEs) that determine how quickly tokens move between places. These parameters also include stoichiometric relationships, represented by the weights of arcs connecting places and transitions, dictating how many tokens are consumed or produced when a transition occurs.

Use the following AMR model JSON file as a reference:

---START AMR MODEL JSON---
{amr}
---END AMR MODEL JSON---

Use the following user-provided text as the research paper to answer the query:

---START USER-PROVIDED TEXT---
{research_paper}
---END USER-PROVIDED TEXT---

Assume that the user-provided text describes multiple conditions to which the model can be applied. Create a model configuration for each condition.
Be sure to extract parameter values and initial values from the user-provided text, and do not use the default values from the AMR model.
Be sure to use consistent naming conventions for the conditions. Instead of 'condition_1' and 'condition_2', use descriptive names.
parameter values may be found in tables. Pay attention to the table structure when determining parameter and state values.

For each condition, create a model configuration JSON object that satisfies the JSON schema specified in the response format. To do this, follow the instructions below:
1.	Create a value for `name` and `description` from the user-provided text.
2.	For the description, provide a long-form description of the condition. If the description cannot be created from the user-provided text, set it to an empty string.
3.	`model_id` id a UUID. If the AMR model has an id, you can use it. Otherwise, you can set as the nil UUID "00000000-0000-0000-0000-000000000000".
4.	For each state specified in the AMR model, extract information from the text and create an initial semantic object. Do not create new initial semantic objects for states that are not included in the original AMR model. If you cannot find a value or expression for the initial state, do not create an initial semantic object.
5.	For each parameter specified in the AMR model, extract information from the text and create a parameter semantic object. Do not create new parameter semantic objects for parameters that are not included in the original AMR model. If you cannot find a value for the parameter, do not create a parameter semantic object. Use the following rules to determine if the parameter is a constant or a distribution:
    a.	Some parameters will be an initial value for a state in the AMR model. These will have an id of of the state they are associated with, with a 0 appended to the end (E.g. "S0" or "S_{{x}}0"). Search the document for an initial value for the state and create a constant parameter with the value.
    b.  Constant parameters will have a single value. To create a constant parameter, set the distribution type to "Constant" and add the value to the `value` field. Sometimes constants will be in the form of a ratio (E.g. 1/n), in these cases, evaluate the ratio and provide the resulting value.
    c.  Distributed parameters will usually appear as a range (E.g. 0.01 - 0.2) or a list of values (E.g. [0.01 0.02 0.03 0.04 0.05] or 0.01, 0.02, 0.03, 0.04, 0.05 ). To create a distribution parameter, set the distribution type to "Uniform" and add the smallest value in the range or list to the `minimum` field and the largest value to the `maximum` field.
6. `observableSemanticList` should be an empty list.
7. `inferredParameterList` should be an empty list.
8. Determine what page the information was extracted from and set the `extractionPage` value to that page number. If the page number cannot be determined, set it to 0. Only pick one page number. Do not provide a range.
"""
