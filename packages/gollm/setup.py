from setuptools import setup, find_packages

setup(
    name="gollm",
    version="0.1.0",
    packages=find_packages(),
    install_requires=[
        "openai==1.50.1",
        "pandas",
        "pydantic==2.9.2",
        "regex",
        "tiktoken",
        "jsonschema"
    ],
    package_data={
        "gollm_openai.schemas": ["*.json"]
    },
    entry_points={
        "console_scripts": [
            "gollm:compare_models=tasks.compare_models:main",
            "gollm:configure_model_from_dataset=tasks.configure_model_from_dataset:main",
            "gollm:configure_model_from_document=tasks.configure_model_from_document:main",
            "gollm:embedding=tasks.embedding:main",
            "gollm:enrich_amr=tasks.enrich_amr:main",
            "gollm:equations_from_image=tasks.equations_from_image:main",
            "gollm:generate_response=tasks.generate_response:main",
            "gollm:generate_summary=tasks.generate_summary:main",
            "gollm:model_card=tasks.model_card:main",
        ],
    },
    python_requires=">=3.11",
)
