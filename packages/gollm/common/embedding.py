from typing import List

from openai import OpenAI


def bulk_embedding_chain(texts: List[str]) -> List:
    print("Creating embeddings for texts...")
    client = OpenAI()
    response = client.embeddings.create(
        model="text-embedding-ada-002",
        input=texts
    )

    embeddings = []
    for item in response.data:
        embeddings.append(item.embedding)

    return embeddings


def embedding_chain(text: str) -> List:
    print("Creating embeddings for text: {}".format(text[:100]))
    client = OpenAI()
    output = client.embeddings.create(model="text-embedding-ada-002", input=text)
    return output.data[0].embedding
