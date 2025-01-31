## Model Stratification
Please go through __every__ step of the test scenario.\
Report any issues into GitHub: [open an issue](https://github.com/DARPA-ASKEM/terarium/issues/new?assignees=&labels=bug%2C+Q%26A&template=qa-issue.md&title=%5BBUG%5D%3A+).

### 1. Begin test
1. Login to https://app.staging.terarium.ai using the test account
    ```
    email: qa@test.io
    password: askem-quality-assurance
    ```
2. Create, or open, project named `QA [Your Name]`

### 2. Upload asset
1. Add the [SEIRHD model](https://github.com/DARPA-ASKEM/terarium/blob/3c80ae8f3ad012ebfbdd3a8f1883066eec4b48ac/testing/data/SEIRHD%20Q1b%20added%20params.json) to the project.

### 3. Stratify a model by many stratum groups

We want to stratified the model by several age groups to simulate how contact rates between age groups can affect the number of susceptible persons of one age group become exposed to the disease by infected persons from another age group.

1. Create a new workflow with the name `Stratify Test 1`
2. Drag and drop the model onto the canvas
3. Right-click on the canvas to add a `Stratify model` operator under **Modeling**
4. Connect the output port of the `Model` operator to the input port of the `Stratify model` operator
5. Click **Open** on the `Stratify model` operator to specify the stratification action
6. Apply these settings
   1. Name of strata: _age_
   2. Select variables and parameters to stratify: _S, E, I, c_
   3. Enter a comma-separated list of labels for each group: _0to4, 5to9, 10to14, 15to19, 20to24, 25to29, 30to34, 35to39, 40to44, 45to49, 50to54, 55to59, 60to64, 65to69, 70to74, 75to79, 80to84, 85above_
   4. Create new transitions between strata: _False_
   5. Allow existing interactions to involve multiple strata: _True_
7. Click **Stratify**
8. Expected result:
   1. `SEIRHD (stratified age with 18 groups)` is available in the output selection dropdown in the top-right corner
   2. A new model with the same structure as the SEIRHD model appears in the right half of the interface
   3. The states `S, E, I` of the new model have 18 nodes that are circle-packed within them, representing the new stratified states `S_0to4, S_5to9, ...`
9. Select and double-click the transition `template-2` between the states `E, I` to expand the matrix view of this transition
   1. The shape of the matrix `18 x 18`
   2. The rows are `E_0to4, E_10to14, ...` and the columns are `I_0to4, I_10to14, ...`
   3. All off-diagonal values are `n/a`
   4. Toggle **Evaluate expression** in the top-right corner to numerically evaluate the parameters in the math expressions
   5. Click **OK** to close this view
10. Confirm that only the `S, E, I` states are expandable in the **State variables** table, ditto for `c` in the **Parameters** table
   1. In the `Parameters` table, click on `Open matrix` on the right of the parameter `c`
   2. An expanded view of the `18 x 18` matrix of `c` is shown
   3. Select `subjectControllers` in the top-left dropdown
   4. Every entry of the matrix should have a value of type `c_*_*`
11. Click on the **Save for reuse** button in the top-right corner to save this stratified model into your project
   1. Confirm that it appears in the **Models** section of the **Resources** panel on the left of the interface
12. Click **X** in the top-right corner to exit
13. Right-click on the canvas to add a `Configure model` operator under **Config & Intervention**
14. Connect the output port of the `Stratify model` operator to the **Model** input port of the `Configure model` operator
15. Click **Open** to expand the operator
   1. Click **Open Matrix** on the `S` state in the `Initials` table
   2. Confirm that the value of every state in the `18 x 1` matrix is `S_0 / 18`


### 4. Customize a model stratification 
1. Create a new workflow with the name `Stratify Test 2`
2. Drag and drop the `SEIRHD` model onto the canvas
3. Right-click on the canvas to add a `Stratify model` operator under **Modeling**
4. Connect the output port of the `Model` operator to the input port of the `Stratify model` operator
5. Click **Open** on the `Stratify model` operator to specify the stratification action
6. Apply these settings
   1. Name of strata: _vaccination_
   2. Select variables and parameters to stratify: _S, v_
   3. Enter a comma-separated list of labels for each group: _unvaccinated, vaccinated_
   4. Create new transitions between strata: _True_
   5. Allow existing interactions to involve multiple strata: _False_
7. Click **Stratify**
8. Confirm that a new stratified model appears on the right
9. Click on the **Notebook** tab at the top of the interface to refine the stratification
10. Confirm that this function call is shown in the code editor
```python
model = stratify(
    template_model=model,
    key= "vaccination",
    strata=['unvaccinated', 'vaccinated'],
    structure= None,
    directed=False,
    cartesian_control=False,
    modify_names=True,
    concepts_to_stratify=['S'], #If none given, will stratify all concepts.
    concepts_to_preserve=None, #If none given, will stratify all concepts.
    params_to_stratify= ['v'], #If none given, will stratify all parameters.
    params_to_preserve= None, #If none given, will stratify all parameters.
    param_renaming_uses_strata_names = True
)
```
11. Edit the code as follows:
```python
model = stratify(
    ...
    structure= [['unvaccinated', 'vaccinated']],
    directed=True,
    ...
)
```
12. Click **Run**
13. Confirm that a new stratified model appears on the right
   1. The model structure is mostly unchanged
   2. Only the state `S` has two stratified states within it: `S_unvaccinated, S_vaccinated`
   3. One new transition named `t_conv_0_unvaccinated_vaccinated` with rate law `S_unvaccinated*p_unvaccinated_vaccinated`
   4. The transition `template-1` is now a `2 x 1` matrix
   5. There are 8 transitions in total
14. Click **X** to exit this operator

### 5. Chain-stratify model

Next, let's stratify the model again to introduce the dependency of the infection and vaccine processes on which vaccine is used. That is, how many people are vaccined by the Pfizer, Moderna, or JJ vaccines and how the rate of infection is modified.

1. Right-click on the canvas to add another `Stratify model` operator next to the previous one
2. Connect the output port of the previous `Stratify model` operator to the input port of the new one
3. Click **Edit** on the new `Stratify model` operator to specify the stratification action
4. Apply these settings
   1. Name of strata: _vaccine_
   2. Select variables and parameters to stratify: _S_vaccinated, v_vaccinated, p_unvaccinated_vaccinated_
   3. Enter a comma-separated list of labels for each group: _pfizer, moderna, jj_
   4. Create new transitions between strata: _False_
   5. Allow existing interactions to involve multiple strata: _False_
5. Click **Stratify**
6. Confirm that a new stratified model appears on the right
   1. The `template-7` transition is now a `1 x 3` matrix, representing the three different possible vaccination processes (from unvaccinated to vaccinated by any of the three vaccines)
   2. Check that there are now four stratified `S` states: `S_unvaccinated, S_vaccinated_jj, S_vaccinated_moderna, S_vaccinated_pfizer`
   3. The parameter `v` is a `4 x 1` matrix (in 'subjectOutcome' view) with those values `v_unvaccinated, v_vaccinated`

### 6. Confirm Auto-configuration

Lastly, let's check that the stratification assigned appropriate initial condition values to the new, stratified state variables.

1. Right-click on the canvas to add two `Configure model` operators, one below each of the `Stratify model` operators above
2. Connect the output port of both `Stratify model` operators to the respective input **model** port of the `Configure model` operators
3. In the first `Configure model` operator
   1. Confirm that the values of the `S` state in the **Initials** table are `S0/2`
4. In the second operator
   1. Confirm that the initial value of `S_unvaccinated` is `S0/2`, and `S0/6` for the other stratified `S` states
