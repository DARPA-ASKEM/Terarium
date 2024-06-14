## `[Operator Annotations]`
Please go through __every__ step of the test scenario.\
When blocked, an error, or a UI/UX anomaly occurs, please report which scenario and step to [\#askem-testing](https://unchartedsoftware.slack.com/archives/C06FGLXB2CE).

Estimated time to completion: [X] minutes

### 1. Begin test 
1. Login to https://app.staging.terarium.ai using the test account
    ```
    email: qa@test.io
    password: askem-quality-assurance
    ```
2. Create, or open, project named `QA [Your Name]`

### 2. Test Scenarios

`[Task 1: Adding a new annotation to an operator's workflow node']`
1. Add a model (e.g. [Configured SIR.json](https://github.com/DARPA-ASKEM/terarium/blob/main/testing/data/Configured%20SIR.json))to the project with Explorer.
2. Create a new workflow - open it.
3. Drag the model from the Resources panel onto the workflow canvas
4. Click the kebab icon to open the model node's menu, click "Add a note"
5. Enter some annotation text in the input field in the model node.
6. Tap ENTER when finished typing to accept changes.
7. Click the pencil icon button in the model node to re-enter edit mode.
8. Click the checkmark icon button to accept changes.
9. __Expected Result__: The annotation entered via the text field should be added to the operator successfully by clicking Enter and/or by clicking the checkmark icon button. 

`[Task 2: Editing an annotation in an operator's workflow node]`
1. Click the pencil icon button located at the end of the annotation text in the model node.
2. Make changes to the annotation text in the input field.
3. Tap ENTER or click the checkmark icon button.
4. __Expected Result__: Changes made to the annotation should be reflected in what is displayed in the node.

`[Task 3: Deleting an annotation from an operator's workflow node]`
1. Click the pencil icon button located at the end of the annotation text in the model node.
2. Click the trash can icon button.
3. __Expected Result__: The annotation should no longer be visible in the model node.

`[Task 4: Adding a new annotation to an operator's drill down]`
1. Click the Open button in the model node to view the operator's drill down.
2. Click the "Add a note" text button in the drill down header - right below the operator title.
3. Enter some annotation text in the newly created input field in the header.
4. Tap ENTER when finished typing to accept changes.
5. Click the pencil icon button located at the end of the newly added annotation in the header to re-enter edit mode.
6. Click the checkmark icon button to accept changes.
7. __Expected Result__: The annotation entered via the text field in the header should be added to the operator's header successfully by clicking Enter and/or by clicking the checkmark icon button.

`[Task 5: Editing an existing annotation in an operator's drill down]`
1. Click the pencil icon button located at the end of the annotation text in the drill down header.
2. Make changes to the annotation text in the input field.
3. Tap ENTER or click the checkmark icon button.
4. __Expected Result__: Changes made to the annotation should be reflected in what is displayed in the operator drill down header.

`[Task 6: Deleting an annotation from an operator's drill down]`
1. Click the pencil icon button located at the end of the annotation text in the drill down header.
2. Click the trash can icon button.
3. __Expected Result__: The annotation should no longer be visible in the model operator drill down header. The "Add a note" button should now be visible and functional.

### 3. End test
1. logout of the application 
