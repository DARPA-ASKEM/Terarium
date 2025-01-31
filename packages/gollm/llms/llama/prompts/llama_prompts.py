LLAMA_START_PROMPT = """
<|begin_of_text|>
<|start_header_id|>user<|end_header_id|>

"""

LLAMA_END_PROMPT = """

<|eot_id|>
<|start_header_id|>assistant<|end_header_id|>
"""

LLAMA_RETURN_INSTRUCTIONS = """
Do not respond in full sentences; do not surround the answer in ``` or any other code block; only use the following json schema to create and populate a JSON object.

--- JSON SCHEMA START ---

{schema}

--- JSON SCHEMA END ---
"""
