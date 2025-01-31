import sys
import os
import json
import random
import time

from util import CustomRequests

PROJECT_ID=os.getenv("PROJECT_ID")
SERVER_URL=os.getenv("SERVER_URL")
ITERATION=int(os.getenv("ITERATION"))


# This profile runs random forecast simulations from a given projects' model-configurations
if __name__ == "__main__":
    helper = CustomRequests()
    response = helper.get(f"{SERVER_URL}/projects/{PROJECT_ID}")
    project_assets = response.json()["projectAssets"]
    helper.print("getting project assets", len(project_assets))

    model_config_assets = list(filter(lambda x: x["assetType"] == "model-configuration", project_assets))

    n = 0
    while n < ITERATION:
        n = n + 1
        item = random.choice(model_config_assets)
        item_id = item["assetId"]
        url = f"{SERVER_URL}/simulation-request/ciemss/forecast?project-id={PROJECT_ID}"

        payload = {
            "metadata": {},
            "payload": {
                "modelConfigId": item_id,
                "timespan": {
                    "start": 0,
                    "end": 100
                },
                "engine": "ciemss",
                "extra": {
                    "solver_method": "dopri5",
                    "solver_step_size": 1,
                    "num_samples": 100
                }
            }
        }

        # Request
        helper.print(url, payload)
        request_response = helper.post(url, data=json.dumps(payload))
        simulation_id = request_response.json()["id"]

        # Polling
        while True:
            url = f"{SERVER_URL}/simulations/{simulation_id}?project-id={PROJECT_ID}"
            polling_response = helper.get(url)

            status = polling_response.json()["status"]

            helper.print(f"polling simulation result {simulation_id} - {status}")

            if status == "COMPLETE":
                break

            time.sleep(2)


        time.sleep(2)
    helper.print("Done forecasting")

