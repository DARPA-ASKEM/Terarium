from typing import List


class LlmToolsInterface:

    def send_to_llm_with_json_output(self, prompt: str, schema: str, max_tokens: int) -> dict:
        """Send a request to an LLM API"""
        pass


    def send_to_llm_with_string_output(self, prompt: str, max_tokens: int) -> str:
        """Send a request to an LLM API"""
        pass


    def send_image_to_llm_with_json_output(self, prompt: str, schema: str, image_url: str, max_tokens: int) -> dict:
        """Send a request to an LLM API with an image"""
        pass


    def create_enrich_model_prompt(self, amr: str, document: str, schema: str) -> str:
        """Create a prompt to enrich a model's metadata from a document"""
        pass


    def create_config_from_dataset_prompt(self, amr: str, dataset: List[str], matrix: str, schema: str) -> str:
        """Create a prompt to extract model configurations from a dataset"""
        pass

    def create_config_from_document_prompt(self, amr: str, document: str, schema: str) -> str:
        """Create a prompt to extract model configurations from a document"""
        pass

    def create_enrich_dataset_prompt(self, dataset: str, document: str, schema: str) -> str:
        """Create a prompt to enrich a dataset's metadata from a document"""
        pass

    def create_cleanup_equations_prompt(self, equations: List[str], schema: str) -> str:
        """Create a prompt that formats latex equations into a format usable by Terarium"""
        pass

    def create_equations_from_image_prompt(self, image_url: str, schema: str) -> str:
        """Create a prompt that extracts latext equations from an image"""
        pass

    def create_interventions_from_document_prompt(self, amr: str, document: str, schema: str) -> str:
        """Create a prompt to extract interventions from a document"""
        pass

    def create_interventions_from_dataset_prompt(self, amr: str, dataset: List[str], schema: str) -> str:
        """Create a prompt to extract interventions from a dataset"""
        pass

    def create_model_card_prompt(self, amr: str, document: str, schema: str) -> str:
        """Create a prompt that produces a model card"""
        pass

    def create_compare_models_prompt(self, amrs: List[str], dataset: str, goal: str, schema: str) -> str:
        """Create a prompt that compares models"""
        pass

    def create_general_query_prompt(self, instruction: str) -> str:
        """Create a prompt that performs a general query"""
        pass

    def create_chart_annotation_prompt(self, preamble: str, instruction: str, schema: str) -> str:
        """Create a prompt that annotates a chart"""
        pass

    def create_latex_to_sympy_prompt(self, equations: List[str], schema: str) -> str:
        """Create a prompt that converts latex to sympy"""
        pass
