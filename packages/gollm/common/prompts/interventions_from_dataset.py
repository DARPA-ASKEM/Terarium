INTERVENTIONS_FROM_DATASET_PROMPT = """
You are a helpful agent designed to find intervention policies described in a CSV dataset for a given AMR model.
For context, intervention policies can include multiple interventions that include only static interventions or dynamic interventions.
Static interventions are applied at a specific point in time and permanently change the value of a specific parameter or state.
Dynamic interventions are applied when the value of a state crosses a threshold value.

Use the following AMR model JSON file as a reference:

---START AMR MODEL JSON---
{amr}
---END AMR MODEL JSON---

Use the following dataset to answer the query:

---START DATASET---
{dataset}
---END DATASET---

Assume that the dataset describes a single intervention policy.
For this intervention policy, create a list of interventions for each row in the dataset. You will need to inspect the names and ids of the initial states and parameters in the AMR model and attempt to map these states and parameters to columns in the dataset. Use column header information in the csv dataset to determine which states or parameters they align with.
Be sure to use a meaningful descriptive name for the intervention policy.

Create an interventionPolicy object. To do this, follow the instructions below:
1. Create a value for `name` and `description` from the AMR model and the dataset.
2. For the description, provide a long-form description of the condition. If the description cannot be created from the provided AMR and dataset, set it to an empty string.
3. `model_id` is a UUID. If the AMR model has an id, you can use it. Otherwise, you can set as the nil UUID "00000000-0000-0000-0000-000000000000".
4. For each row in the dataset create an intervention object with the following rules.
    a. Create a value for `name` from the dataset.
    b. Set the `extractionPage` value to 0.
    c. Create a list of either static or dynamic interventions, but not both. The other list should be empty.
        i. `appliedTo` should reference the id of a parameter or state in the AMR Model. If you cannot find a state or parameter in the AMR model that matches, do not create an intervention object.
        ii. `type` should be either "state" or "parameter" depending on what the intervention is applied to.
        iii. For dynamic interventions, `parameter` should be the id of a state that the threshold is applied to.
"""
