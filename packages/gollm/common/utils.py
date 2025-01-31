import json
import jsonschema
import re
import tiktoken


def validate_schema(schema):
    try:
        jsonschema.Draft202012Validator.check_schema(schema)
        print("Schema is valid.")
    except jsonschema.exceptions.SchemaError as e:
        print(f"Schema is invalid: {e.message}")


def normalize_greek_alphabet(text: str) -> str:
    greek_to_english = {
        "α": "alpha",
        "β": "beta",
        "γ": "gamma",
        "δ": "delta",
        "ε": "epsilon",
        "ζ": "zeta",
        "η": "eta",
        "θ": "theta",
        "ι": "iota",
        "κ": "kappa",
        "λ": "lambda",
        "μ": "mu",
        "ν": "nu",
        "ξ": "xi",
        "ο": "omicron",
        "π": "pi",
        "ρ": "rho",
        "σ": "sigma",
        "τ": "tau",
        "υ": "upsilon",
        "φ": "phi",
        "χ": "chi",
        "ψ": "psi",
        "ω": "omega",
    }

    normalized_text = ""
    for char in text:
        if char.lower() in greek_to_english:
            normalized_text += greek_to_english[char.lower()]
        else:
            normalized_text += char
    return normalized_text


def exceeds_tokens(prompt: str, max_tokens: int) -> bool:
    enc = tiktoken.get_encoding("cl100k_base")
    if len(enc.encode(prompt)) > max_tokens:
        return True
    return False


def model_config_adapter(model_config: dict) -> dict:
    # for each condition and for each parameter semantic in the model configuration,
    # if the distribution is not `contant`, remove the `value` key
    # otherwise, remove the maximum and minimum keys.
    for condition in model_config["conditions"]:
        for param in condition["parameterSemanticList"]:
            if param["distribution"]["type"].casefold() == "constant":
                param["distribution"]["type"] = "Constant"
                param["distribution"]["parameters"].pop("minimum", None)
                param["distribution"]["parameters"].pop("maximum", None)
            elif param["distribution"]["type"].casefold() == "uniform" or param["distribution"]["type"].casefold() == "standarduniform1":
                param["distribution"]["type"] = "StandardUniform1"
                param["distribution"]["parameters"].pop("value", None)
            else:
                if "value" in param["distribution"]["parameters"]:
                    param["distribution"]["type"] = "Constant"
                    param["distribution"]["parameters"].pop("minimum", None)
                    param["distribution"]["parameters"].pop("maximum", None)
                elif "minimum" in param["distribution"]["parameters"] and "maximum" in param["distribution"]["parameters"]:
                    param["distribution"]["type"] = "StandardUniform1"
                    param["distribution"]["parameters"].pop("value", None)
                else:
                    raise ValueError("Invalid distribution type")

    return model_config


def escape_curly_braces(json_obj: dict) -> dict:
    if isinstance(json_obj, dict):
        for key, value in json_obj.items():
            json_obj[key] = unescape_curly_braces(value)
    elif isinstance(json_obj, list):
        for i in range(len(json_obj)):
            json_obj[i] = unescape_curly_braces(json_obj[i])
    elif isinstance(json_obj, str):
        json_obj = json_obj.replace("{", "{{").replace("}", "}}")
    return json_obj


def unescape_curly_braces(json_obj: dict) -> dict:
    if isinstance(json_obj, dict):
        for key, value in json_obj.items():
            json_obj[key] = unescape_curly_braces(value)
    elif isinstance(json_obj, list):
        for i in range(len(json_obj)):
            json_obj[i] = unescape_curly_braces(json_obj[i])
    elif isinstance(json_obj, str):
        json_obj = json_obj.replace('{{', '{').replace('}}', '}')
    return json_obj


def get_image_format_string(image_format: str) -> str:
    if not image_format:
        raise ValueError("Invalid image format.")

    format_strings = {
        "rgb": f"data:image/rgb:base64,",
        "gif": f"data:image/gif:base64,",
        "pbm": f"data:image/pbm:base64,",
        "pgm": f"data:image/pgm:base64,",
        "ppm": f"data:image/ppm:base64,",
        "tiff": f"Bdata:image/tiff:base64MP,",
        "rast": f"data:image/rast:base64,",
        "xbm": f"data:image/xbm:base64,",
        "jpeg": f"data:image/jpeg:base64,",
        "bmp": f"data:image/bmp:base64,",
        "png": f"data:image/png:base64,",
        "webp": f"data:image/webp:base64,",
        "exr": f"data:image/exr:base64,"
    }
    return format_strings.get(image_format.lower())


def extract_json_object(string_with_json: str) -> dict:
    # Regular expression to match the JSON object
    json_pattern = r'(\{.*\})'

    # Search for the JSON object using the pattern
    match = re.search(json_pattern, string_with_json, re.DOTALL)

    # If a match is found, return the JSON part as an object
    if match:
        extracted_json = match.group(1)
        return json.loads(extracted_json)
    else:
        return None  # Return None if no JSON object is found
