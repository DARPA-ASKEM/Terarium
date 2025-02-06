import boto3
import json
import os
from typing import List, Optional

from common.LlmToolsInterface import LlmToolsInterface
from common.prompts.chart_annotation import CHART_ANNOTATION_PROMPT
from common.prompts.config_from_dataset import (
    CONFIGURE_FROM_DATASET_PROMPT,
    CONFIGURE_FROM_DATASET_MAPPING_PROMPT,
    CONFIGURE_FROM_DATASET_TIMESERIES_PROMPT,
    CONFIGURE_FROM_DATASET_AMR_PROMPT,
    CONFIGURE_FROM_DATASET_DATASET_PROMPT,
    CONFIGURE_FROM_DATASET_MATRIX_PROMPT
)
from common.prompts.config_from_document import CONFIGURE_FROM_DOCUMENT_PROMPT
from common.prompts.dataset_enrichment import (
    DATASET_ENRICH_PROMPT_WITH_DOCUMENT,
    DATASET_ENRICH_PROMPT_WITHOUT_DOCUMENT
)
from common.prompts.equations_cleanup import EQUATIONS_CLEANUP_PROMPT
from common.prompts.equations_from_image import EQUATIONS_FROM_IMAGE_PROMPT
from common.prompts.general_query import GENERAL_QUERY_PROMPT
from common.prompts.interventions_from_dataset import INTERVENTIONS_FROM_DATASET_PROMPT
from common.prompts.interventions_from_document import INTERVENTIONS_FROM_DOCUMENT_PROMPT
from common.prompts.latex_style_guide import LATEX_STYLE_GUIDE
from common.prompts.latex_to_sympy import LATEX_TO_SYMPY_PROMPT
from common.prompts.model_card import MODEL_CARD_PROMPT
from common.prompts.model_enrichment import (MODEL_ENRICH_PROMPT_WITH_DOCUMENT, MODEL_ENRICH_PROMPT_WITHOUT_DOCUMENT)
from common.prompts.model_meta_compare import (
    MODEL_METADATA_COMPARE_PROMPT,
    MODEL_METADATA_COMPARE_GOAL_PROMPT,
    MODEL_METADATA_COMPARE_DATA_PROMPT,
    MODEL_METADATA_COMPARE_GOAL_AND_DATA_PROMPT
)
from common.utils import (
    escape_curly_braces,
    unescape_curly_braces
)
from llms.llama.prompts.llama_prompts import LLAMA_START_PROMPT, LLAMA_RETURN_INSTRUCTIONS, LLAMA_END_PROMPT

GPT_MODEL = "us.meta.llama3-2-90b-instruct-v1:0"


class LlamaTools(LlmToolsInterface):

    def __init__(self, bedrock_access_key=None, bedrock_secret_access_key=None):
        self.bedrock_access_key = bedrock_access_key
        self.bedrock_secret_access_key = bedrock_secret_access_key

    def send_to_llm_with_json_output(self, prompt: str, schema: str, max_tokens=2048) -> dict:
        print("Sending request to AWS Bedrock (Llama)...")
        #send prompt to AWS Bedrock

        client = boto3.client(
            "bedrock-runtime",
            region_name="us-west-2",
            aws_access_key_id = os.environ.get("BEDROCK_ACCESS_KEY") if self.bedrock_access_key is None else self.bedrock_access_key,
            aws_secret_access_key = os.environ.get("BEDROCK_SECRET_ACCESS_KEY") if self.bedrock_secret_access_key is None else self.bedrock_secret_access_key
        )

        request = json.dumps({
            "prompt": prompt,
            "max_gen_len": max_tokens,
            "temperature": 0,
            "top_p": 1,
        })

        response = client.invoke_model(
            modelId=GPT_MODEL,
            body=request,
            contentType="application/json"
        )

        print("Received response from AWS Bedrock (Llama)...")
        model_response = json.loads(response["body"].read())
        response_text = model_response["generation"]
        return unescape_curly_braces(response_text)


    def send_to_llm_with_string_output(self, prompt: str, max_tokens=2048) -> str:
        print("Sending request to AWS Bedrock (Llama)...")
        #send prompt to AWS Bedrock

        client = boto3.client(
            "bedrock-runtime",
            region_name="us-west-2",
            aws_access_key_id = os.environ.get("BEDROCK_ACCESS_KEY") if self.bedrock_access_key is None else self.bedrock_access_key,
            aws_secret_access_key = os.environ.get("BEDROCK_SECRET_ACCESS_KEY") if self.bedrock_secret_access_key is None else self.bedrock_secret_access_key
        )

        request = json.dumps({
            "prompt": prompt,
            "max_gen_len": max_tokens,
            "temperature": 0,
            "top_p": 1,
        })

        response = client.invoke_model(
            modelId=GPT_MODEL,
            body=request,
            contentType="application/json"
        )

        print("Received response from AWS Bedrock (Llama)...")
        model_response = json.loads(response["body"].read())
        return model_response["generation"]


    def send_image_to_llm_with_json_output(self, prompt: str, schema: str, image_url: str, max_tokens=2048) -> dict:
        print("Sending request to AWS Bedrock (Llama)...")
        #send prompt to AWS Bedrock

        client = boto3.client(
            "bedrock-runtime",
            region_name="us-west-2",
            aws_access_key_id = os.environ.get("BEDROCK_ACCESS_KEY") if self.bedrock_access_key is None else self.bedrock_access_key,
            aws_secret_access_key = os.environ.get("BEDROCK_SECRET_ACCESS_KEY") if self.bedrock_secret_access_key is None else self.bedrock_secret_access_key
        )

        request = json.dumps({
            "prompt": prompt,
            "max_gen_len": max_tokens,
            "temperature": 0,
            "top_p": 1,
        })

        response = client.invoke_model(
            modelId=GPT_MODEL,
            body=request,
            contentType="application/json"
        )

        print("Received response from AWS Bedrock (Llama)...")
        model_response = json.loads(response["body"].read())
        response_text = model_response["generation"]
        return unescape_curly_braces(response_text)


    def create_enrich_model_prompt(self, amr: str, document: str, schema: str) -> str:
        print("Building prompt to extract model enrichments from a document...")
        prompt = LLAMA_START_PROMPT

        if (document is None) or (document == ''):  # If no document is provided
            print("Building prompt to extract model enrichments without a document...")
            prompt += MODEL_ENRICH_PROMPT_WITHOUT_DOCUMENT.format(
                amr=escape_curly_braces(amr)
            )
        else:
            print("Building prompt to extract model enrichments from a document...")
            prompt += MODEL_ENRICH_PROMPT_WITH_DOCUMENT.format(
                amr=escape_curly_braces(amr),
                document=escape_curly_braces(document)
            )

        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT
        return prompt


    def create_config_from_dataset_prompt(self, amr: str, dataset: List[str], matrix: str, schema: str) -> str:
        print("Building prompt to extract model configurations from a dataset...")
        dataset_text = os.linesep.join(dataset)

        prompt = LLAMA_START_PROMPT
        prompt += (CONFIGURE_FROM_DATASET_PROMPT
                  + CONFIGURE_FROM_DATASET_MAPPING_PROMPT
                  + CONFIGURE_FROM_DATASET_TIMESERIES_PROMPT
                  + CONFIGURE_FROM_DATASET_AMR_PROMPT.format(amr=amr)
                  + CONFIGURE_FROM_DATASET_DATASET_PROMPT.format(data=dataset_text))

        if matrix:
            prompt += CONFIGURE_FROM_DATASET_MATRIX_PROMPT.format(matrix=matrix)

        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT
        return prompt


    def create_config_from_document_prompt(self, amr: str, document: str, schema: str) -> str:
        print("Building prompt to extract model configurations from a reasearch paper...")
        prompt = LLAMA_START_PROMPT
        prompt += CONFIGURE_FROM_DOCUMENT_PROMPT.format(
            amr=escape_curly_braces(amr),
            research_paper=escape_curly_braces(document)
        )
        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT
        return prompt


    def create_enrich_dataset_prompt(self, dataset: str, document: Optional[str], schema: str) -> str:
        prompt = LLAMA_START_PROMPT

        if (document is None) or (document == ''):  # If no document is provided
            print("Building prompt to extract dataset enrichments")
            prompt += DATASET_ENRICH_PROMPT_WITHOUT_DOCUMENT.format(dataset=dataset)
        else:
            print("Building prompt to extract dataset enrichments from a research paper...")
            prompt += DATASET_ENRICH_PROMPT_WITH_DOCUMENT.format(
                research_paper=escape_curly_braces(document),
                dataset=dataset
            )

        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT
        return prompt


    def create_cleanup_equations_prompt(self, equations: List[str], schema: str) -> str:
        print("Building prompt to reformat equations...")
        prompt = LLAMA_START_PROMPT
        prompt += EQUATIONS_CLEANUP_PROMPT.format(
            style_guide=LATEX_STYLE_GUIDE,
            equations="\n".join(equations)
        )
        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT
        return prompt


    def create_equations_from_image_prompt(self, image_url: str, schema: str) -> str:
        print("Building prompt to extract equations an image...")
        prompt = LLAMA_START_PROMPT
        prompt += EQUATIONS_FROM_IMAGE_PROMPT.format(
            style_guide=LATEX_STYLE_GUIDE
        )
        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT
        return prompt


    def create_interventions_from_document_prompt(self, amr: str, document: str, schema: str) -> str:
        print("Building prompt to extract interventions from a reasearch paper...")
        prompt = LLAMA_START_PROMPT
        prompt += INTERVENTIONS_FROM_DOCUMENT_PROMPT.format(
            amr=escape_curly_braces(amr),
            research_paper=escape_curly_braces(document)
        )
        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT
        return prompt


    def create_interventions_from_dataset_prompt(self, amr: str, dataset: List[str], schema: str) -> str:
        print("Building prompt to extract interventions from a dataset...")
        dataset_text = os.linesep.join(dataset)
        prompt = LLAMA_START_PROMPT
        prompt += INTERVENTIONS_FROM_DATASET_PROMPT.format(
            amr=escape_curly_braces(amr),
            dataset=escape_curly_braces(dataset_text)
        )
        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT
        return prompt


    def create_model_card_prompt(self, amr: str, document: str, schema: str) -> str:
        print("Building prompt to produce a model card...")
        if not document:
            document = "NO RESEARCH PAPER PROVIDED"

        prompt = LLAMA_START_PROMPT
        prompt += MODEL_CARD_PROMPT.format(
            research_paper=escape_curly_braces(document),
            amr=escape_curly_braces(amr)
        )
        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT
        return prompt


    def create_compare_models_prompt(self, amrs: List[str], dataset: str, goal: str, schema: str) -> str:
        print("Building prompt to compare models...")
        joined_escaped_amrs = "\n\n------\n\n".join([escape_curly_braces(amr) for amr in amrs])

        prompt = LLAMA_START_PROMPT
        prompt += MODEL_METADATA_COMPARE_PROMPT.format(
            amrs=joined_escaped_amrs
        )
        if dataset is not None and dataset != '':
            prompt += MODEL_METADATA_COMPARE_DATA_PROMPT.format(dataset=dataset)
        if goal is not None and goal != '':
            prompt += MODEL_METADATA_COMPARE_GOAL_PROMPT.format(goal=goal)
        if (dataset is not None and dataset != '') and (goal is not None and goal != ''):
            prompt += MODEL_METADATA_COMPARE_GOAL_AND_DATA_PROMPT

        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT
        return prompt


    def create_general_query_prompt(self, instruction: str) -> str:
        print("Building general query prompt...")
        prompt = LLAMA_START_PROMPT
        prompt += GENERAL_QUERY_PROMPT.format(
            instruction=instruction
        )
        prompt += LLAMA_END_PROMPT
        return prompt


    def create_chart_annotation_prompt(self, preamble: str, instruction: str, schema: str) -> str:
        print("Building chart annotation prompt...")
        prompt = LLAMA_START_PROMPT
        prompt += CHART_ANNOTATION_PROMPT.format(
            preamble=preamble,
            instruction=instruction
        )
        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT


    def create_latex_to_sympy_prompt(self, equations: List[str], schema: str) -> str:
        print("Building prompt to transform latex equations to sympy...")
        prompt = LLAMA_START_PROMPT
        prompt += LATEX_TO_SYMPY_PROMPT.format(
            latex_equations="\n".join(equations)
        )
        prompt += LLAMA_RETURN_INSTRUCTIONS.format(
            schema=schema
        )
        prompt += LLAMA_END_PROMPT
        return prompt
