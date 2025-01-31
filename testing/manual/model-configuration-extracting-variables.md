## Extract configurations from document and dataset
Please go through __every__ step of the test scenario.\
Report any issues into GitHub: [open an issue](https://github.com/DARPA-ASKEM/terarium/issues/new?assignees=&labels=bug%2C+Q%26A&template=qa-issue.md&title=%5BBUG%5D%3A+).

### 1. Begin test
1. Login to https://app.staging.terarium.ai using the test account
    ```
    email: qa@test.io
    password: askem-quality-assurance
    ```
2. Create, or open, project named `QA [Your Name]`

### 2. Upload assets
1. From the manual testing [data folder](https://drive.google.com/drive/folders/14z6WAldQky0xgOAM69f6hakXhyHobYMe), add these resources to your project by clicking the `Upload` button and dragging these downloaded files onto the popup:
    1. [SIR Document 1](https://drive.google.com/file/d/1ZQXSGhesif_G0bjQiB9nx0B_BJ1xV1Wx/view?usp=sharing)
    2. [SIR Document 2](https://drive.google.com/file/d/1U1h5YCBxom9k475QSOzkiIwyi5V159dV/view?usp=sharing)
    3. [SIR Model](https://drive.google.com/file/d/1F9UWuvwJPZY_XAGflsOIYy4Qrl56KS91/view?usp=sharing)
    4. [Stratified SIR model](https://drive.google.com/file/d/1r-zfKmHrOub7QT9BfdQEZabfZqveLhIq/view?usp=sharing)
    5. [SIDARTHE model](https://drive.google.com/drive/folders/14z6WAldQky0xgOAM69f6hakXhyHobYMe)
    6. [SIDARTHE paper](https://drive.google.com/file/d/1pOb1MuarNEyrDf6I6z96Pdf_hQ9Q3_v0/view?usp=drive_link)
    7. [SIR timeseries Dataset 1](https://drive.google.com/file/d/1QM2TL6XrBHIlFOVSavugKAQErls7XtY8/view?usp=sharing)
    8. [SIR timeseries Dataset 2](https://drive.google.com/file/d/1zKySIpyPN_aa-icxy1e8NHmVtkZs51Fn/view?usp=sharing)
    9. [SIR contact matrix dataset](https://drive.google.com/file/d/17TByirv-LwunB0gDq46-Ww-SyJZYaHKD/view?usp=sharing)
    10. [Prob 5 Model C](https://drive.google.com/file/d/1CF-n8OUroRaaNCaRTwvOgrPuASrktj1P/view?usp=drive_link)
    11. [Rosenblatt 2024](https://drive.google.com/file/d/1mGU2HwXG5ZWjgo24DNaNkq2oS5ipL6_V/view?usp=drive_link)
2. Wait a minute or two for the Document Intelligence service to finish extracting artifacts from the uploaded document.

### 3. Extracting configurations from multiple documents and timeseries datasets
1. Create a new workflow
2. Add the SIR model, both SIR documents, and both SIR timeseries datasets to the workflow
3. Create a new "Configure model" operator
4. Attach the SIR model to the "Configure model" operator
5. Attach the SIR documents and timeseries datasets to the "Configure model" operator
6. Extract configurations from the inputs
7. Ensure that the following configurations are extracted:
    - There are exactly 2 time-series configurations extracted from the SIR timeseries datasets
    - One should have the following values:
        - S = 10000
        - I = 100
        - R = 1
        - The source should be "SIR timeseries 1"
        - The parameters should be the same as the default configuration
    - The other should have the following values:
        - S = 1000
        - I = 1
        - R = 0
        - The source should be "SIR timeseries 2"
        - The parameters should be the same as the default configuration
    - There are several model configurations from the SIR Document 1
        - These will include countries such as Moldova, France, Spain, and Italy
        - There are several model configurations from the SIR Document 2
        - These will include countries such as China, South Korea, Australia, USA, and Italy
8. Ensure that the configuration sources are correctly populated and that they have reasonable titles and descriptions

### 4. Extracting configurations from model-mapping datasets
1. Add the Stratified SIR model and the SIR contact matrix dataset to the workflow
2. Create a new "Configure model" operator
3. Attach the Stratified SIR model to the "Configure model" operator
4. Attach the SIR contact matrix dataset to the "Configure model" operator
5. Extract configurations from the inputs
6. Ensure that the following configurations are extracted:
    - There is exactly 1 model-mapping configuration extracted from the contact matrix dataset
    - The configuration should have the following values:
        - Beta should have the following parameters:
            - β_0_18_0_18 = 0.4
            - β_0_18_19_45 = 0.09
            - β_0_18_46_89 = 0.08
            - β_19_45_0_18 = 0.07
            - β_19_45_19_45 = 0.3
            - β_19_45_46_89 = 0.06
            - β_46_89_0_18 = 0.05
            - β_46_89_19_45 = 0.04
            - β_46_89_46_89 = 0.2
        - Gamma and P should be blank
        - The source should be "SIR contact matrix"
        - The initials should be blank

### 5. The gold standard - SIDARTHE model
1. Add the SIDARTHE model to the workflow 
2. Create a new "Configure model" operator 
3. Attach `SIDARTHE model` and `SIDARTHE paper` to the "Configure model" operator
4. Extract configurations from the inputs 
5. Ensure that the following configurations are extracted:
    - There are 3 model configurations from the Document:
        - `Default configuration`
        - `High Transmission Scenario`
        - `Restrictive NPIs Scenario`
    - Initial expressions for S, I, D, A, R, T, H, and E should be extracted for all configurations
    - Parameter values for alpha, beta, delta, epsilon, eta, gamma, kappa, lambda, mu, nu, rho, theta, xi, zeta, tau, and sigma should be extracted for all configurations

### 6. Extracting configuration from a table with probability distributions (Not supported yet)

This is difficult test case since it involves extracting values from a table _and_ some of the parameters are probability distributions.

1. Add `Prob 5 Model C` model and `Rosenblatt 2024` document to the workflow
2. Create a new "Configure model" operator
3. Connect the model and document to this operator
4. Open the operator and click "Extract configuration from inputs"
5. There should be only one model configuration that is extracted
6. Confirm the values

   | Parameters | Type | Distribution Parameter(s) | 
   | --- | --- | --- |
   | α   | LogNormal1 | meanLog = 4.72, stdevLog  = 0.63 |
   | AER | Constant | 4      |
   | Aw  | Constant | 100    |
   | Ci  | Constant | 0.0014 |
   | Nw  | Constant | 1000   |
   | S   | Constant | 0.24 | 
   | Vair   | Constant | 7.03    |
   | Vdrop  | Constant | 0.01    | 
   | e      | Constant | 2.71828 | 
   | kappa  | Constant | 11.35   |
   | lambda | Constant | 0.63    |
   | q      | Constant | 0.34    | 
   | rhoA   | LogNormal1 | meanLog = 3.47, stdevLog = 0.23 |
   | theta  | LogNormal1 | meanLog = 0.28, stdevLog = 0.27 |
   | epsilonD | LogitNormal1 | location = -1.46, scale = 0.71 |
   | k | Constant | 410    |
   | y | Constant | 0.1666 |
   | IR_W_W | Constant | 0.85 |
   | IR_W_C | Constant | 0.85 |
   | IR_W_H | Constant | 0.85 |
   | IR_C_W | Constant | 0.85 |
   | IR_C_C | Constant | 0.85 |
   | IR_C_H | Constant | 0.85 |
   | IR_H_W | Constant | 0.53 |
   | IR_H_C | Constant | 0.53 |
   | IR_H_H | Constant | 0.53 |
   | omega_W_C | Constant | 0.00072 |
   | omega_C_W | Constant | 0.00072 |
   | omega_C_C | LogNormal1 | meanLog = 3.47,  stdevLog = 0.91 |
   | omega_H_W | LogNormal1 | meanLog = -1.59, stdevLog = 1.70 |
   | omega_W_H | LogNormal1 | meanLog = -1.59, stdevLog = 1.70 |
   | omega_H_C | LogNormal1 | meanLog = 2.52,  stdevLog = 1.13 |
   | omega_C_H | LogNormal1 | meanLog = 2.52,  stdevLog = 1.13 |
   | tContact_W_W | LogNormal1 | meanLog = 1.55,   stdevLog = 1.27 |
   | tContact_W_C | LogNormal1 | meanLog = 1.55,   stdevLog = 1.27 |
   | tContact_C_W | LogNormal1 | meanLog = 1.55,   stdevLog = 1.27 |
   | tContact_C_C | LogNormal1 | meanLog = 1.55,   stdevLog = 1.27 |
   | tContact_C_H | LogNormal1 | meanLog = 1.79,   stdevLog = 1.15 |
   | tContact_W_H | LogNormal1 | meanLog = -0.36,  stdevLog = 0.98 |
   | Cvs    | LogNormal1 | meanLog = 0.22, stdevLog = 0.34 |
   | Cv_C_C | LogNormal1 | meanLog = 0.22, stdevLog = 0.34 |
   | Cv_C_W | LogNormal1 | meanLog = 0.22, stdevLog = 0.34 |
   | Cv_W_C | LogNormal1 | meanLog = 0.22, stdevLog = 0.34 |
   | Cv_C_H | LogNormal1 | meanLog = 5.6 * log(10),  stdevLog = 1.2 * log(10) |
   
   | Cv_W_H | LogNormal1 | meanLog = 5.6 * log(10),  stdevLog = 1.2 * log(10) |
   | Vsputum | Constant | 100 |
