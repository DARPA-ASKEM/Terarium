START_ENRICH_PROMPT = """
You are a helpful agent who is an expert in mathematical modelling and epidemiology. Your job is to enrich a dataset with metadata by inspecting statistics of the dataset.
You may have access to a document that relates to the given dataset. Use this document to determine sources, considerations for use, and additional information.

Focus on providing detailed and informative descriptions of the dataset and its columns. Do not make up information, only use the information provided in the document and dataset stats.
Focus on trying to determine which columns represent spatial or temporal data, and provide a detailed description of the data in these columns.
"""

DOCUMENT_EXTRACTION = """
Try to extract information for sourceData, considerationsForUsingTheData, and additionalInformation from the document.
"""

NO_DOCUMENT_EXTRACTION = """
sourceData, considerationsForUsingTheData, and additionalInformation should not be filled in for this task.
"""

DATASET_PROMPT = """
Use the following dataset statistics as a reference:

---START DATASET---
{dataset}
---END DATASET---
"""

DOCUMENT_PROMPT = """
Use the following document as a reference:

---DOCUMENT START---
{document}
---DOCUMENT END--
"""

DO_NOT_HALLUCINATE = """
Do not hallucinate. Do not make up information. Only use the information provided in the dataset and the document.
"""

DATASET_ENRICH_PROMPT_WITH_DOCUMENT = START_ENRICH_PROMPT + DOCUMENT_EXTRACTION + DATASET_PROMPT + DOCUMENT_PROMPT + DO_NOT_HALLUCINATE
DATASET_ENRICH_PROMPT_WITHOUT_DOCUMENT = START_ENRICH_PROMPT + NO_DOCUMENT_EXTRACTION + DATASET_PROMPT + DO_NOT_HALLUCINATE
