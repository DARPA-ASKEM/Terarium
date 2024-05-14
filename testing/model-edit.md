## Model edit
Please go through __every__ step of the test scenario.\
When blocked, an error, or a UI/UX anomaly occurs, please report which scenario and step to [\#askem-testing](https://unchartedsoftware.slack.com/archives/C06FGLXB2CE).

Estimated time to completion: [X] minutes

### 1. Begin test
1. Login to https://app.staging.terarium.ai using the test account
    ```
    email: qa@test.io
    password: askem-quality-assurance
    ```
2. Create, or open, project named `Q&A [Your Name] [YYMMDD]`

### 2. Prepare the model and workflow
1. Upload _the model_ [Configured SIR.json](data/Configured SIR.json).
2. Create and open a new workflow.
3. Drag and drop _the model_ into the workflow.
4. Add an Edit model operator and link it to _the model_ operator.
5. Open the Edit model operator by clicking its 'Edit' button.

### 3a. Edit the model (decomposed view)
1. Drag and drop a Natural Conversion template into the canvas.
2. Link port R from the t1 template to port A in the Natural conversion template.
3. Open the flattened view or the Notebook tab to see if the model has updated as expected:
   ```
   (S) -> [t0] -> (I) -> [t1] -> (R) -> [NaturalConversion] -> (B2)
   ```

### 3b. Edit the model (flattened view)
1. Open the flattened view.
2. Drag and drop a Natural Conversion template into the canvas.
3. Link port R from the Configured SIR to port A in the Natural conversion template.
4. Open the decomposed view and verify that port R from the t1 template is connected to port R (or what used to be port A) in the Natural Conversion template.

### 4. Save as new model
1. Open the Notebook tab and click the 'Save as new model' button.
2. Give the new model a name and save it.
3. Close the operator and then check if your new model is in your project.

### 5. Running notebook code
1. If your model edit operator is open, close it then reopen it.
2. You will notice that your Wizard edits are gone but the code in the Notebook tab is still present.
3. In the Notebook tab click the 'Run' button. 
4. Verify if your model edits from step 3 are reapplied. 

### 6. Reset
1. Click the Reset button in the Notebook tab.
2. Verify that the code is erased and that the model is reverted to what it was originally.

### 7. AI coding assistant
1. In the Notebook tab click on the 'What do you want to do?' dropdown.
2. Choose an edit that you want to apply and click the send button. At the time of me writing this I chose "Add a new transition from S to R with the name vaccine with the rate of v and unit Days." as it was the first option.
3. Once you recieve the code click the 'Run' button.
4. Verify that the edit is applied correctly to the model and is reflected in the Wizard tab's decomposed view.
5. If it didn't work, notify the Beaker developers about the code that was generated. There is also a chance that the prompt that the AI came up with lacked accuracy and couldn't possibly be applied to the model.

### 7. Incompatible port connections 
The following port combos should not connect if...
- they are from the same template card
- they are already connected

### 8. End test
1. Close the operator and logout of the application.
