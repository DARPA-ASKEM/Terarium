from setuptools import setup, find_packages

setup(
    name="mira_task",
    version="0.1.0",
    packages=find_packages(),
    install_requires=[],
    entry_points={
        "console_scripts": [
            "mira_task:sbml_to_petrinet=tasks.sbml_to_petrinet:main",
            "mira_task:mdl_to_stockflow=tasks.mdl_to_stockflow:main",
            "mira_task:stella_to_stockflow=tasks.stella_to_stockflow:main",
            "mira_task:amr_to_mmt=tasks.amr_to_mmt:main",
        ],
    },
    python_requires=">=3.10",
)
