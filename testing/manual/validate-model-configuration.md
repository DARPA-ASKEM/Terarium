## Validate Model Configuration
Report any issues into GitHub: [open an issue](https://github.com/DARPA-ASKEM/terarium/issues/new?assignees=&labels=bug%2C+Q%26A&template=qa-issue.md&title=%5BBUG%5D%3A+).

### Preamble
Validation checks whether a given model-configuration satisfies specific constraints when it is simulated. Then if so, what are the feasible parameter value ranges one can use.

For example, one might have an `SEIRD` mode-configuration, where the infection rate is between `[0.1, 0.3]`, we want to know what is the maximum infection rate in order to keep the infected population under 1200 people across all times. Based on the rest of the model and its initial conditions, validation result may simply say this isn't feasible, or maybe it can be achieved if infection rate can be kept below `0.225`. 

### 1. Begin test
1. Login to https://app.staging.terarium.ai using the test account
    ```
    email: qa@test.io
    password: askem-quality-assurance
    ```
2. Create, or open, project named `QA [Your Name]`

### 2. Setup the workflow
1. Upload the [SIR to validate.json](https://drive.google.com/drive/folders/1j46RpsEflGzBz2yLnSZbmhs9gexpBnGk) model
2. Create a new workflow named `Validate Model Configuration`
3. Drag and drop the model from the Resources panel onto the workflow.
4. Attach the model to a _Configure model_ operator.
5. Open the _Configure Model_ operator to verify that the model is reasonably configured by inspecting the value of the initial conditions and parameters:
     - `S = 2000.0, I = 1.0, R = 0.0, beta = [0.01, 0.1], gamma = [0.01, 0.1]`
6. Attach the _Configure Model_ operator to a _Validate configuration_ operator.

### 3. Setup and run
1. Open the _Validate configuration_ operator.
2. Under settings in the _Parameters of interest_ dropdown, select `beta` and `gamma` (this determines for which inputs the operator should run the validation)
3. In the Settings section,  \
    - use the _Tolerance_ input field or the slider to set the value to `0.05` (this determines how granular the operator segment the input space of the model for validation, < 0.5 means high granularity, > 0.5 means low granularity)
    - set the _End time_ to `35` and _Number of timesteps_ to `35` such that there is a timepoint for every integer between 0 and 35 inclusively.
4. Ensure that the _Compartmental constraint_ is present (it requires the operator to check that the sum of the state variables at all times is equal to their sum at time = 0 and that each state variable is positive): `S >= 0, I >= 0, R >= 0, S + I + R = 2001.0`
5. Choose one of the following steps:
    - a. Add a constraint that ensures that state variables S and R should be greater than or equal to `180` persons from `30` to `34` days.
    - b. Add a constraint that ensures that state variables S and R should be less than `450` persons from `30` to `34` days.
6. Click _Run_

### 5. Inspect output
#### State charts
1. Once an output has been generated look at the `S` state chart
2. Note that the time-series chart shows the evolution of the model variables as a function of time, with one trajectory per sampled points in parameter space (coloured green when they satisfy all sanity checks, yellow otherwise)
3. Verify that:
   - The points of the trajectories are plotted as specified by _Number of timesteps_ (`= 35`)
   - The light blue model check box matches the constraint configuration (you won't see it yet if you chose step 3.5b)
4. In the Output settings slider check the checkbox that says _Only show furthest results_. See if the furthest true, false and ambiguous boxes are the only ones plotted (step 3.5a's constraint will just show you one true box).
5. Check the _Focus on model checks_ checkbox. See if the chart changes to show the large range that the model check covers.
6. Uncheck both checkboxes.

#### Parameter charts
1. Click on some ticks on the parameter charts.
2. Notice that the tick's value corresponds to ticks on other parameter charts and a line in the state chart as they are highlighted.
3. To show everything again click a space on the parameter bar that doesn't have a tick.

### 6. Test the other constraint
1. Delete your constraint and create the other constraint in Step 3.5, run it and redo Step 5.
