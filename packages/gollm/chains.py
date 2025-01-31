import base64
import imghdr
import json
import os
from typing import List, Optional

from common import LlmToolsInterface
from common.utils import validate_schema, model_config_adapter, get_image_format_string, extract_json_object, \
    unescape_curly_braces

# define a constant for the absolute path of the schemas directory
SCHEMAS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'schemas')


def enrich_model_chain(llm: LlmToolsInterface, amr: str, research_paper: str) -> dict:
    print("Uploading and validating model enrichment schema...")
    config_path = os.path.join(SCHEMAS_DIR, 'amr_enrichment.json')
    with open(config_path, 'r') as config_file:
        response_schema = json.load(config_file)
    validate_schema(response_schema)

    prompt = llm.create_enrich_model_prompt(amr, research_paper, response_schema)
    return llm.send_to_llm_with_json_output(prompt, response_schema)


def model_config_from_dataset_chain(llm: LlmToolsInterface, amr: str, dataset: List[str], matrix: str) -> dict:
    print("Uploading and validating model configuration schema...")
    config_path = os.path.join(SCHEMAS_DIR, 'configuration.json')
    with open(config_path, 'r') as config_file:
        response_schema = json.load(config_file)
    validate_schema(response_schema)

    prompt = llm.create_config_from_dataset_prompt(amr, dataset, matrix, response_schema)
    output = llm.send_to_llm_with_json_output(prompt, response_schema)

    print("There are ", len(output["conditions"]), "conditions identified from the datasets.")

    return model_config_adapter(output)


def model_config_from_document_chain(llm: LlmToolsInterface, research_paper: str, amr: str) -> dict:
    print("Uploading and validating model configuration schema...")
    config_path = os.path.join(SCHEMAS_DIR, 'configuration.json')
    with open(config_path, 'r') as config_file:
        response_schema = json.load(config_file)
    validate_schema(response_schema)

    prompt = llm.create_config_from_document_prompt(amr, research_paper, response_schema)
    output = llm.send_to_llm_with_json_output(prompt, response_schema)

    print("There are ", len(output["conditions"]), "conditions identified from the text.")

    return model_config_adapter(output)


def enrich_dataset_chain(llm: LlmToolsInterface, dataset: str, document: Optional[str]) -> dict:
    print("Uploading and validating dataset enrichment schema...")
    config_path = os.path.join(SCHEMAS_DIR, 'dataset_enrichment.json')
    with open(config_path, 'r') as config_file:
        response_schema = json.load(config_file)
    validate_schema(response_schema)

    prompt = llm.create_enrich_dataset_prompt(dataset, document, response_schema)
    return llm.send_to_llm_with_json_output(prompt, response_schema)


def cleanup_equations_chain(llm: LlmToolsInterface, equations: List[str]) -> dict:
    print("Uploading and validating equations schema...")
    config_path = os.path.join(SCHEMAS_DIR, 'equations.json')
    with open(config_path, 'r') as config_file:
        response_schema = json.load(config_file)
    validate_schema(response_schema)

    prompt = llm.create_cleanup_equations_prompt(equations, response_schema)
    return llm.send_to_llm_with_json_output(prompt, response_schema)


def equations_from_image_chain(llm: LlmToolsInterface, image: str) -> dict:
    print("Validating and encoding image...")
    image_format = get_image_format_string(
        imghdr.what(None, h=base64.b64decode(image))
    )

    base64_image_str = image_format + image

    print("Uploading and validating equations schema...")
    config_path = os.path.join(SCHEMAS_DIR, 'equations.json')
    with open(config_path, 'r') as config_file:
        response_schema = json.load(config_file)
    validate_schema(response_schema)

    prompt = llm.create_equations_from_image_prompt(image_format, response_schema)
    output = llm.send_image_to_llm_with_json_output(prompt, response_schema, base64_image_str)

    print("There are", len(output['equations']), "equations identified from the image.")

    return output


def interventions_from_document_chain(llm: LlmToolsInterface, research_paper: str, amr: str) -> dict:
    print("Uploading and validating intervention policy schema...")
    config_path = os.path.join(SCHEMAS_DIR, 'intervention_policy.json')
    with open(config_path, 'r') as config_file:
        response_schema = json.load(config_file)
    validate_schema(response_schema)

    prompt = llm.create_interventions_from_document_prompt(amr, research_paper, response_schema)
    output = llm.send_to_llm_with_json_output(prompt, response_schema)

    print("There are ", len(output["interventionPolicies"]), "intervention policies identified from the text.")

    return output


def interventions_from_dataset_chain(llm: LlmToolsInterface, dataset: List[str], amr: str) -> dict:
    print("Uploading and validating intervention policy schema...")
    config_path = os.path.join(SCHEMAS_DIR, 'intervention_policy.json')
    with open(config_path, 'r') as config_file:
        response_schema = json.load(config_file)
    validate_schema(response_schema)

    prompt = llm.create_interventions_from_dataset_prompt(amr, dataset, response_schema)
    output = llm.send_to_llm_with_json_output(prompt, response_schema)

    print("There are ", len(output["interventionPolicies"]), "intervention policies identified from the dataset.")

    return output


def model_card_chain(llm: LlmToolsInterface, amr: str, research_paper: str = None) -> dict:
    print("Uploading and validating model card schema...")
    config_path = os.path.join(SCHEMAS_DIR, 'model_card.json')
    with open(config_path, 'r') as config_file:
        response_schema = json.load(config_file)
    validate_schema(response_schema)

    prompt = llm.create_model_card_prompt(amr, research_paper, response_schema)
    return llm.send_to_llm_with_json_output(prompt, response_schema)


def compare_models_chain(llm: LlmToolsInterface, amrs: List[str], goal: str) -> dict:
    print("Uploading and validating compare models schema...")
    config_path = os.path.join(SCHEMAS_DIR, 'compare_models.json')
    with open(config_path, 'r') as config_file:
        response_schema = json.load(config_file)
    validate_schema(response_schema)

    prompt = llm.create_compare_models_prompt(amrs, goal, response_schema)
    return llm.send_to_llm_with_json_output(prompt, response_schema)


def general_query_chain(llm: LlmToolsInterface, instruction: str) -> str:
    prompt = llm.create_general_query_prompt(instruction)
    return llm.send_to_llm_with_string_output(prompt, None)


def chart_annotation_chain(llm: LlmToolsInterface, preamble: str, instruction: str) -> dict:
    prompt = llm.create_chart_annotation_prompt(preamble, instruction)
    chart_annotation_string = llm.send_to_llm_with_string_output(prompt)
    return unescape_curly_braces(extract_json_object(chart_annotation_string))



def latex_to_sympy_chain(llm: LlmToolsInterface, equations: List[str]) -> dict:
    print("Uploading and validating equations schema...")
    config_path = os.path.join(SCHEMAS_DIR, 'sympy.json')
    with open(config_path, 'r') as config_file:
        response_schema = json.load(config_file)
    validate_schema(response_schema)

    prompt = llm.create_latex_to_sympy_prompt(equations, response_schema)
    return llm.send_to_llm_with_json_output(prompt, response_schema)
