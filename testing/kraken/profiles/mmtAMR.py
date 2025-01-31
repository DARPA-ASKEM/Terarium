import sys
import os
import json
import random
import time
import string

from util import CustomRequests

PROJECT_ID=os.getenv("PROJECT_ID")
SERVER_URL=os.getenv("SERVER_URL")
ITERATION=int(os.getenv("ITERATION"))

# This profile simulate amr-to-mmt
if __name__ == "__main__":
    helper = CustomRequests()
    response = helper.get(f"{SERVER_URL}/projects/{PROJECT_ID}")
    project_assets = response.json()["projectAssets"]
    helper.print("getting project assets", len(project_assets))

    # Filter for models
    model_assets = list(filter(lambda x: x["assetType"] == "model", project_assets))
    if len(model_assets) == 0:
        sys.exit(0)

    n = 0
    while n < ITERATION:
        n = n + 1

        # Get model information
        item = random.choice(model_assets)
        item_type = helper.asset_2_route(item["assetType"])
        item_id = item["assetId"]
        url = f"{SERVER_URL}/{item_type}/{item_id}?project-id={PROJECT_ID}"
        response = helper.get(url)

        model_json = response.json()
        # Random name
        model_json["header"]["name"] = ''.join(random.choices(string.ascii_uppercase + string.digits, k=10))
        url = f"{SERVER_URL}/mira/amr-to-mmt?project-id={PROJECT_ID}"
        helper.print(url)
        response = helper.post(url, data=json.dumps(model_json))
        time.sleep(1)


    helper.print("Done")

