import os
import requests
from dotenv import load_dotenv
load_dotenv()

CLIENT_ID=os.getenv("CLIENT_ID")
CLIENT_SECRET=os.getenv("CLIENT_SECRET")
USERNAME=os.getenv("USERNAME")
PASSWORD=os.getenv("PASSWORD")
ACCESS_TOKEN_URL=os.getenv("ACCESS_TOKEN_URL")

def request_token():
    payload = {
        "client_id": CLIENT_ID,
        "username": USERNAME,
        "password": PASSWORD,
        "grant_type": "password"
    }
    response = requests.post(ACCESS_TOKEN_URL, data=payload)
    return response.json()["access_token"]


class CustomRequests:
    def __init__(self):
        token = request_token()
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "Application/json"
        }

    def print(self, *args, **kwargs):
        pid = os.getpid()
        print(f"[{pid}]", *args, **kwargs)

    def asset_2_route(self, v):
        if v == "document":
            return "document-asset"
        return v + "s"

    def get(self, url, **kwargs):
        return requests.get(url, headers=self.headers, **kwargs)

    def post(self, url, data=None, json=None, **kwargs):
        return requests.post(url, data=data, json=json, headers=self.headers, **kwargs)

    def put(self, url, data=None, **kwargs):
        return requests.put(url, data=data, headers=self.headers, **kwargs)

    def delete(self, url, **kwargs):
        return requests.delete(url, headers=self.headers, **kwargs)
