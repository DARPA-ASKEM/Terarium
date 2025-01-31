### kraken
A simple runner to stress test Terarium and related services.

Kraken works on the notion of `profiles`, where each profile performs some set of tasks against Terarium endpoints. Kraken will take a run definition and execute these profiles in separate processes, simulating a heavy load environment. For example, we can spin up 5 instances of doing forecast-simulations and 3 instances of creating model from equations.


#### Prereq
- Rename `env.example` to `.env` and fill in the fields
- You will need dotenv if not already installed `pip install python-dotenv`


#### Usage
```
python ./kraken.py <profile A>:<# instances A>:<iterations A> <profile B>:<# instances B>:<iterations B> ...
```

For example: spin up 
- 25 instances of running amr-to-mmt processes where each instance will run 50 amr-to-mmt conversions, 
- 10 instances of browser, where each instance will fetching random assets in a project 1000 times

```
python ./kraken.py mmtAMR:25:50 browser:10:1000
```


As well, to run each profile directly (ie for debugging), you can do this:

```
ITERATION=1 python profiles/browser.py
```

#### Example usage: two browser + 1 forecaster

```
python kraken.py browser:2:2 forecaster:1:2


[94041] getting project assets 109
[94042] getting project assets 109
[94043] getting project assets 109
[94043] http://localhost:3000/simulation-request/ciemss/forecast?project-id=5507858b-affd-4069-8371-599f22a9645c {'metadata': {}, 'payload': {'modelConfigId': 'df2296bd-f14f-450c-8a85-3e5c0d58a70f', 'timespan': {'start': 0, 'end': 100}, 'engine': 'ciemss', 'extra': {'solver_method': 'dopri5', 'solver_step_size': 1, 'num_samples': 100}}}
[94042] http://localhost:3000/workflows/fd43a0d2-d88d-43a4-b544-ecb4a9af97fa?project-id=5507858b-affd-4069-8371-599f22a9645c 200 24521
[94041] http://localhost:3000/model-configurations/8dd49f41-c75a-4a9d-8479-ecc21f1c0942?project-id=5507858b-affd-4069-8371-599f22a9645c 200 1033855
[94043] polling simulation result f1ac4f14-bdce-436f-a927-b9bf50078fba - QUEUED
[94042] http://localhost:3000/workflows/7432c65d-c90f-40f8-bf94-9186c1654c92?project-id=5507858b-affd-4069-8371-599f22a9645c 200 7392
[94041] http://localhost:3000/model-configurations/ad34bd0e-3aea-4f48-b7ae-312a53471a72?project-id=5507858b-affd-4069-8371-599f22a9645c 200 56093
[94042] Done browsing
[94041] Done browsing
[94043] polling simulation result f1ac4f14-bdce-436f-a927-b9bf50078fba - RUNNING
[94043] polling simulation result f1ac4f14-bdce-436f-a927-b9bf50078fba - COMPLETE
[94043] http://localhost:3000/simulation-request/ciemss/forecast?project-id=5507858b-affd-4069-8371-599f22a9645c {'metadata': {}, 'payload': {'modelConfigId': '71ab149b-78bc-4be9-9452-2dd5db54b015', 'timespan': {'start': 0, 'end': 100}, 'engine': 'ciemss', 'extra': {'solver_method': 'dopri5', 'solver_step_size': 1, 'num_samples': 100}}}
[94043] polling simulation result 67f502d1-0828-4da8-92e0-7a48c42658d5 - QUEUED
[94043] polling simulation result 67f502d1-0828-4da8-92e0-7a48c42658d5 - RUNNING
[94043] polling simulation result 67f502d1-0828-4da8-92e0-7a48c42658d5 - COMPLETE
[94043] Done forecasting
All done!!!
```



#### Profiles
Currently all profiles expect to receive these three environment variables
- PROJECT_ID: uuid of the project to test against
- SERVER_URL: server host
- ITERATION: a positive integer to indicate how many times to perform the action

