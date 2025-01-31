import sys
import os
import json
import random
import time

from util import CustomRequests

PROJECT_ID=os.getenv("PROJECT_ID")
SERVER_URL=os.getenv("SERVER_URL")
ITERATION=int(os.getenv("ITERATION"))

# This profile downloads datasets
if __name__ == "__main__":
    helper = CustomRequests()
    response = helper.get(f"{SERVER_URL}/projects/{PROJECT_ID}")
    project_assets = response.json()["projectAssets"]
    helper.print("getting project assets", len(project_assets))

    # Filter for datasets
    dataset_assets = list(filter(lambda x: x["assetType"] == "dataset", project_assets))
    if len(dataset_assets) == 0:
        sys.exit(0)

    n = 0
    while n < ITERATION:
        n = n + 1

        # Get dataset information
        item = random.choice(dataset_assets)
        item_type = helper.asset_2_route(item["assetType"])
        item_id = item["assetId"]
        url = f"{SERVER_URL}/{item_type}/{item_id}?project-id={PROJECT_ID}"
        response = helper.get(url)
        helper.print(url, response.status_code, len(response.content))
        file_names = response.json()["fileNames"]

        # Download and write to file
        helper.print("downloading", file_names[0])
        url = f"{SERVER_URL}/{item_type}/{item_id}/download-file?filename={file_names[0]}&project-id={PROJECT_ID}"
        download_filename = f"download-{os.getpid()}"
        response = helper.get(url, allow_redirects=True)
        open(download_filename, "wb").write(response.content)

    helper.print("Done")

