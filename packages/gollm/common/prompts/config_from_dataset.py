CONFIGURE_FROM_DATASET_PROMPT = """
You are a helpful agent designed to create a model configuration for a given AMR model from a user-supplied CSV dataset.

Create a condition for each dataset.

The user-supplied dataset may be either a time-series dataset or a model-mapping dataset.
One of your key tasks is to determine the type of dataset supplied. This can be done by examining the column headers in the first row and the values in the first column of the user-supplied CSV dataset.
    - Model-mapping datasets have the first row and column containing labels and the rest containing numerical values. Often, the first cell in the CSV is empty.
    - Time-series datasets usually have the first row as labels and a column representing sequential time steps. You can use the column headers to determine which column represents time steps. If the dataset does not have header information, look for columns with date strings or incrementally increasing timestamps or numbers. The other columns will represent the values of the AMR model's states.

If the dataset is time-series, follow the instructions in the TIME-SERIES EXTRACTION section.
If the dataset is model-mapping, follow the instructions in the MODEL-MAPPING EXTRACTION section.

Do not respond in full sentences; only create a JSON object that satisfies the JSON schema specified in the response format.

"""

CONFIGURE_FROM_DATASET_MAPPING_PROMPT = """
---MODEL-MAPPING EXTRACTION START---

Pay attention to the following example and use it to understand how to extract values from a model-mapping dataset:

---EXAMPLE START---

---MATRIX START---

subject-controllers of f

, I_1, I_2, I_3
S_1, f_0, f_1, f_2
S_2, f_4, f_3, f_5
S_3, f_7, f_8, f_6

---MATRIX END---

---SAMPLE DATASET START---

, I_1, I_2, I_3
S_1, 38.6, 20.5, 6.1
S_2, 20.5, 28.2, 11.5
S_3, 6.1, 11.5, 20.0

---SAMPLE DATASET END---

Since the subject of f_0 is S_1 and the controller of f_0 is I_1. We want to map the value from the dataset cell S_1, I_1 to f_0 which will be 38.6.

Based on this information, we do not know the initial values for I_1 and S_1. Do not misinterpret these interaction values as initials.

---EXAMPLE END---

If the user-supplied dataset is a model-mapping dataset, you must create a model configuration JSON object that satisfies the JSON schema specified in the response format. To do this, follow the instructions below:
    1.	Using metadata from the AMR model and the user-supplied dataset, create values for `name` and `description`.
    2.	Provide a long-form description for the description. Set it to an empty string if it cannot be created from the provided metadata.
    3.	`model_id` id a UUID. If the AMR model has an id, you can use it. Otherwise, you can set as the nil UUID "00000000-0000-0000-0000-000000000000".
    4.	Create a parameter semantic object for each parameter specified in the AMR model ODE semantics. Do not create new parameter semantic objects if they are not included in the original AMR model. You should set parameter semantic object fields using the following rules:
        a.	`reference_id` should reference the id of the parameter.
        b.	`source` should reference the title or file name of the user-supplied dataset.
        c.	`type` should be set to "parameter".
        d.	Be sure to extract parameter values from the user-supplied dataset, and do not use the default values from the AMR model. Set the parameter `value` to the constant value and set `type` to "Constant".
    5.	Create an initial semantic object for each initial specified in the AMR model ODE semantics. Use the default values found in the AMR model. Do not try to create new values.
    6. `observableSemanticList` should be an empty list.
    7. `inferredParameterList` should be an empty list.

---MODEL-MAPPING EXTRACTION END---

"""

CONFIGURE_FROM_DATASET_TIMESERIES_PROMPT = """
---TIME-SERIES EXTRACTION START---

If the user-supplied dataset is a time-series dataset, you must create a model configuration JSON object that satisfies the JSON schema specified in the response format. To do this, follow the instructions below:
    1.	Using metadata from the AMR model and the user-supplied dataset, create values for `name` and `description`.
    2.	Provide a long-form description for the description. If it cannot be created from the provided metadata, set it to an empty string.
    3.	`model_id` id a UUID. If the AMR model has an id, you can use it. Otherwise, you can set as the nil UUID "00000000-0000-0000-0000-000000000000".
    4.	Create an initial semantic object for each initial specified in the AMR model ODE semantics. Do not create new initial semantic objects if they are not included in the original AMR model. You should set initial semantic object fields using the following rules:
        a.	`target` should reference the id of the initial variable from the AMR model ODE semantics.
        b.	`source` should reference the title or file name of the user-supplied dataset.
        c.	`type` should be set to "initial”.
        d.	Find the value for `expression` in the user-supplied dataset that aligns with timepoint 0 or the earliest available timepoint.
        e.	`expression_mathml` should be the value of `expression` written in MathML format.
    5.	Create a parameter semantic object for each parameter specified in the AMR model ODE semantics. Use the default values found in the AMR model. Do not try to create new values. If the default value is a constant type, set the parameter `value` to the constant value and set `type` to "Constant". If the default value is a distribution with a maximum and minimum value, set `type` to only "StandardUniform1" and populate the `minimum` and `maximum` fields.
    6. `observableSemanticList` should be an empty list.
    7. `inferredParameterList` should be an empty list.

---TIME-SERIES EXTRACTION END---

"""

CONFIGURE_FROM_DATASET_DATASET_PROMPT = """
Use the following user-supplied dataset to answer the query:

---START USER-SUPPLIED CSV DATASET---

{data}

---END USER-SUPPLIED CSV DATASET---

"""

CONFIGURE_FROM_DATASET_AMR_PROMPT = """
Use the following JSON representation of an AMR model as a reference:

---START AMR MODEL JSON---

{amr}

---END AMR MODEL JSON---

"""

CONFIGURE_FROM_DATASET_MATRIX_PROMPT = """
Use the following contact matrix as a reference for model-mapping datasets:

---START MATRIX---

{matrix}

---END MATRIX---
"""
