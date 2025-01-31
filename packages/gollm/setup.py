from setuptools import setup, find_packages

setup(
    name="gollm",
    version="0.1.0",
    packages=find_packages(),
    install_requires=[
        "boto3",
        "httpx==0.28.1",
        "jsonschema",
        "openai",
        "pandas",
        "pydantic==2.10.4",
        "regex",
        "tiktoken"
    ],
    package_data={
        "schemas": ["*.json"]
    },
    entry_points={
        "console_scripts": [
            "gollm:compare_models=tasks.compare_models:main",
            "gollm:configure_model_from_dataset=tasks.configure_model_from_dataset:main",
            "gollm:configure_model_from_document=tasks.configure_model_from_document:main",
            "gollm:embedding=tasks.embedding:main",
            "gollm:enrich_model=tasks.enrich_model:main",
            "gollm:enrich_dataset=tasks.enrich_dataset:main",
            "gollm:latex_to_sympy=tasks.latex_to_sympy:main",
            "gollm:equations_cleanup=tasks.equations_cleanup:main",
            "gollm:equations_from_image=tasks.equations_from_image:main",
            "gollm:chart_annotation=tasks.chart_annotation:main",
            "gollm:generate_summary=tasks.general_query:main",
            "gollm:interventions_from_document=tasks.interventions_from_document:main",
            "gollm:interventions_from_dataset=tasks.interventions_from_dataset:main",
            "gollm:model_card=tasks.model_card:main",
            "gollm:dataset_statistics=tasks.dataset_statistics:main"
        ],
    },
    python_requires=">=3.11",
)
