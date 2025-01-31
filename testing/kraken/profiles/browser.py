import sys
import os
import json
import random
import time

from util import CustomRequests

PROJECT_ID=os.getenv("PROJECT_ID")
SERVER_URL=os.getenv("SERVER_URL")
ITERATION=int(os.getenv("ITERATION"))


# This profile fetches project asset from the given projec,t and then randomly
# fetches assets for a while
if __name__ == "__main__":
    helper = CustomRequests()
    response = helper.get(f"{SERVER_URL}/projects/{PROJECT_ID}")
    project_assets = response.json()["projectAssets"]
    helper.print("getting project assets", len(project_assets))

    n = 0
    while n < ITERATION:
        n = n + 1
        item = random.choice(project_assets)
        item_type = helper.asset_2_route(item["assetType"])
        item_id = item["assetId"]
        url = f"{SERVER_URL}/{item_type}/{item_id}?project-id={PROJECT_ID}"

        response = helper.get(url)
        helper.print(url, response.status_code, len(response.content))
        time.sleep(1)
    helper.print("Done browsing")

