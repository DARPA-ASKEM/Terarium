## Optimize
Please go through __every__ step of the test scenario.\
Report any issues into GitHub: [open an issue](https://github.com/DARPA-ASKEM/terarium/issues/new?assignees=&labels=bug%2C+Q%26A&template=qa-issue.md&title=%5BBUG%5D%3A+).

### Note
Note: sampling combinations in PyCIEMSS can result in numerical instability, when this happens, you can:
- Retry the simulation, or
- Fiddle with the parameter distribution ranges, make the intervals larger or smaller

### 1. Begin test
1. Login to https://app.staging.terarium.ai using the test account
    ```
    email: qa@test.io
    password: askem-quality-assurance
    ```
2. Create, or open, project named `QA [Your Name]`
3. Create a workflow named `Optimize Test`

### 2. Upload assets
1. Use/upload the _SEIRHD_ model from [google drive](https://drive.google.com/drive/folders/1bllvuKt6ZA1vc36AW3Xet4y6ZAnwnaVN)
2. Use/upload the _LA county_ dataset from [google drive](https://drive.google.com/drive/folders/1bllvuKt6ZA1vc36AW3Xet4y6ZAnwnaVN)

### 3. Model setup
1. Create a default configuration with the `Configure model` operator
2. Calibrate the model with the dataset with the `Calibrate` operator.

### 4. Masking start time optimization
1. Create a Masking Policy operation with the `Intervention Policy` operator.

#### 4.a Static intervention
1. Set _NPI mult_ to `0.5` starting at _time_ `61` 
2. Optimize intervention: set _H_ to `< 20 000` in all time points in `95%` of simulated outcomes. 
3. Find a new start time for _NPI mult_ **upper** bound (how long we can delay masking). Start time `60`, end time `150`, initial guess `61`. 
4. Optimization settings: end time `150`, maxiter `3` max eval `30`

#### 4.b Dynamic intervention
1. Same as above but with a dynamic intervention 
2. replace 4.a.1 with _NPI mult_ to `0.5` when _H_ `> 16 000`.

### 5. Hospitalizations optimization
1. Create a Hospitalizations Policy operation with the `Intervention Policy` operator.

#### 5.a Static intervention
1. Set _NPI mult_ to `0.5` starting at _time_ `118`
2. Optimize intervention: set _H_ to `< 20 000` in all time points in `95%` of simulated outcomes.
3. Find a new start time for _NPI mult_ **upper** bound (minimal reduction in transmission). Min value = `.0002` intial guess `.5` max = `.9996`
4. Optimization settings: end time `150`, maxiter `3` max eval `30`

#### 5.b Dynamic intervention
1. Same as above but with a dynamic intervention
2. replace 5.a.1 with _NPI mult_ to `0.5` when _H_ `> 16 000`.

### 6. Vaccinations optimization
1. Create a Vaccinations operation with the `Intervention Policy` operator.
2. Set _r_sv_ to `20 000` starting at _time_ `61`
3. Optimize intervention: set _H_ to `< 13 000` in all time points in `95%` of simulated outcomes.
4. Find a new start time for _r_sv_ **lower** bound (minimal increase in daily vaccinations). Min value = `10 000` intial guess `20 000` max = `90 000`
5. Optimization settings: end time `150`, maxiter `3` max eval `30`
